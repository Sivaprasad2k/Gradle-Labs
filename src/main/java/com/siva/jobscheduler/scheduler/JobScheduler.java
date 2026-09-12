package com.siva.jobscheduler.scheduler;

import com.siva.jobscheduler.dependency.DependencyGraph;
import com.siva.jobscheduler.domain.*;
import com.siva.jobscheduler.execution.JobExecutor;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages job registration, dependencies, cancellation, dispatching, failure policies, and retries.
 * Maintains PriorityQueue for eligible jobs and DependencyGraph for workflow evaluation.
 */
public class JobScheduler {
    private final PriorityQueue<Job> queue;
    private final Map<String, JobExecution> executions;
    private final Map<String, RetryPolicy> retryPolicies;
    private final DependencyGraph dependencyGraph;
    private final Clock clock;
    private final JobExecutor jobExecutor;
    private final AtomicInteger pendingJobsCount;
    private volatile SchedulerState schedulerState;

    public JobScheduler(Clock clock, JobExecutor jobExecutor) {
        this.queue = new PriorityQueue<>();
        this.executions = new ConcurrentHashMap<>();
        this.retryPolicies = new ConcurrentHashMap<>();
        this.dependencyGraph = new DependencyGraph();
        this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
        this.jobExecutor = Objects.requireNonNull(jobExecutor, "JobExecutor cannot be null");
        this.jobExecutor.setOnExecutionFinished(this::handleExecutionFinished);
        this.pendingJobsCount = new AtomicInteger(0);
        this.schedulerState = SchedulerState.RUNNING;
    }

    public JobScheduler(Clock clock) {
        this.queue = new PriorityQueue<>();
        this.executions = new ConcurrentHashMap<>();
        this.retryPolicies = new ConcurrentHashMap<>();
        this.dependencyGraph = new DependencyGraph();
        this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
        this.pendingJobsCount = new AtomicInteger(0);
        this.schedulerState = SchedulerState.RUNNING;
        this.jobExecutor = new JobExecutor(1, clock, this::handleExecutionFinished);
    }

    public JobScheduler(JobExecutor jobExecutor) {
        this(Clock.systemUTC(), jobExecutor);
    }

    public JobExecution registerJob(Job job, RetryPolicy retryPolicy) {
        Objects.requireNonNull(job, "Job cannot be null");
        if (executions.containsKey(job.id())) {
            throw new IllegalArgumentException("Job with ID '" + job.id() + "' is already registered");
        }

        // Validate dependencies and DAG structure
        dependencyGraph.addJob(job);

        RetryPolicy policy = retryPolicy != null ? retryPolicy : RetryPolicy.noRetry();
        retryPolicies.put(job.id(), policy);

        JobExecution execution = new JobExecution(job);
        executions.put(job.id(), execution);
        pendingJobsCount.incrementAndGet();

        if (execution.getStatus() == JobStatus.SCHEDULED) {
            synchronized (queue) {
                queue.offer(job);
            }
        } else {
            System.out.printf("[%s] %s registered in BLOCKED state (Waiting for dependencies: %s)%n",
                    clock.instant(), job.name(), job.dependencyIds());
        }
        return execution;
    }

    public JobExecution registerJob(Job job) {
        return registerJob(job, RetryPolicy.noRetry());
    }

    public JobExecution register(Job job, RetryPolicy retryPolicy) {
        return registerJob(job, retryPolicy);
    }

    public JobExecution register(Job job) {
        return registerJob(job);
    }

    public boolean cancelJob(String jobId) {
        if (jobId == null) {
            return false;
        }
        JobExecution execution = executions.get(jobId);
        if (execution == null) {
            return false;
        }
        boolean cancelled = execution.markCancelled();
        if (cancelled) {
            pendingJobsCount.decrementAndGet();
            System.out.printf("[%s] %s %s -> CANCELLED%n", clock.instant(), execution.getJob().name(), execution.getStatus());
        }
        return cancelled;
    }

    public boolean cancel(String jobId) {
        return cancelJob(jobId);
    }

    public boolean cancel(JobExecution execution) {
        if (execution == null) {
            return false;
        }
        return cancelJob(execution.getJob().id());
    }

    public boolean resume() {
        if (schedulerState != SchedulerState.HALTED) {
            return false;
        }
        schedulerState = SchedulerState.RUNNING;
        System.out.printf("[%s] Scheduler resumed. Scheduler state -> RUNNING%n", clock.instant());
        reevaluateBlockedJobs();
        return true;
    }

    public SchedulerState getSchedulerState() {
        return schedulerState;
    }

    public JobExecution getExecution(String jobId) {
        return executions.get(jobId);
    }

    public void start() {
        System.out.println("Scheduler started.\n");
        while (pendingJobsCount.get() > 0 && schedulerState != SchedulerState.STOPPED) {
            if (schedulerState == SchedulerState.HALTED) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }

            Job nextJob;
            synchronized (queue) {
                nextJob = queue.peek();
            }

            if (nextJob == null) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }

            Instant now = clock.instant();
            if (!nextJob.scheduledAt().isAfter(now)) {
                synchronized (queue) {
                    queue.poll();
                }

                JobExecution execution = executions.get(nextJob.id());
                if (execution != null) {
                    if (schedulerState == SchedulerState.HALTED) {
                        synchronized (queue) {
                            queue.offer(nextJob);
                        }
                        continue;
                    }

                    boolean transitionedToRunning = execution.markRunning(now);
                    if (transitionedToRunning) {
                        System.out.printf("[%s] %s SCHEDULED -> RUNNING (Attempt %d)%n", now, nextJob.name(), execution.getAttemptCount());
                        try {
                            jobExecutor.submit(execution);
                        } catch (Throwable t) {
                            execution.markFailed(now, t);
                            System.out.printf("[%s] %s RUNNING -> FAILED (dispatch error)%n", now, nextJob.name());
                            handleExecutionFinished(execution);
                        }
                    } else {
                        System.out.printf("[%s] Skipped execution for cancelled/non-scheduled job %s (Status: %s)%n",
                                now, nextJob.name(), execution.getStatus());
                    }
                }
            } else {
                long delayMillis = nextJob.scheduledAt().toEpochMilli() - now.toEpochMilli();
                if (delayMillis > 0) {
                    try {
                        Thread.sleep(Math.min(delayMillis, 50));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Scheduler interrupted", e);
                    }
                }
            }
        }
        System.out.println("\nNo further executable jobs remain.");
        jobExecutor.shutdown();
        schedulerState = SchedulerState.STOPPED;
        System.out.println("Scheduler stopped.");
    }

    /**
     * Post-execution completion callback to evaluate retries, unblock dependents, or halt scheduler.
     */
    public void handleExecutionFinished(JobExecution execution) {
        if (execution == null) {
            return;
        }

        if (execution.getStatus() == JobStatus.COMPLETED) {
            pendingJobsCount.decrementAndGet();
            reevaluateBlockedDependents(execution.getJob().id());
            return;
        }

        if (execution.getStatus() == JobStatus.FAILED) {
            String jobId = execution.getJob().id();
            RetryPolicy policy = retryPolicies.getOrDefault(jobId, RetryPolicy.noRetry());
            Throwable failure = execution.getFailure();

            if (schedulerState == SchedulerState.RUNNING && policy.shouldRetry(execution, failure)) {
                Instant failureTime = execution.getCompletedAt() != null ? execution.getCompletedAt() : clock.instant();
                Instant nextRunTime = policy.calculateNextAttemptTime(execution.getAttemptCount(), failureTime);

                boolean retryScheduled = execution.markRetryScheduled();
                if (retryScheduled) {
                    Job retryJob = new Job(execution.getJob().id(), execution.getJob().name(), nextRunTime,
                            execution.getJob().task(), execution.getJob().dependencyIds(), execution.getJob().failurePolicy());
                    synchronized (queue) {
                        queue.offer(retryJob);
                    }
                    System.out.printf("[%s] %s Attempt %d FAILED (Transient). Retry scheduled for %s%n",
                            clock.instant(), execution.getJob().name(), execution.getAttemptCount(), nextRunTime);
                    return;
                }
            }

            // Retry not allowed or max attempts exhausted -> Permanent failure
            pendingJobsCount.decrementAndGet();
            System.out.printf("[%s] %s Attempt %d FAILED (Permanent / Retries Exhausted). Final status: FAILED%n",
                    clock.instant(), execution.getJob().name(), execution.getAttemptCount());

            if (execution.getJob().failurePolicy() == FailurePolicy.HALT_SCHEDULER) {
                schedulerState = SchedulerState.HALTED;
                System.out.printf("[%s] Critical job %s FAILED with HALT_SCHEDULER policy. Scheduler state -> HALTED%n",
                        clock.instant(), execution.getJob().name());
            }
        }
    }

    private void reevaluateBlockedDependents(String completedJobId) {
        Set<String> dependentIds = dependencyGraph.getDependents(completedJobId);
        for (String dependentId : dependentIds) {
            JobExecution depExecution = executions.get(dependentId);
            if (depExecution != null && depExecution.getStatus() == JobStatus.BLOCKED) {
                if (dependencyGraph.isSatisfied(dependentId, executions)) {
                    boolean unblocked = depExecution.markUnblocked();
                    if (unblocked) {
                        System.out.printf("[%s] %s BLOCKED -> SCHEDULED (Dependencies satisfied)%n",
                                clock.instant(), depExecution.getJob().name());
                        synchronized (queue) {
                            queue.offer(depExecution.getJob());
                        }
                    }
                }
            }
        }
    }

    private void reevaluateBlockedJobs() {
        for (JobExecution execution : executions.values()) {
            if (execution.getStatus() == JobStatus.BLOCKED) {
                if (dependencyGraph.isSatisfied(execution.getJob().id(), executions)) {
                    boolean unblocked = execution.markUnblocked();
                    if (unblocked) {
                        System.out.printf("[%s] %s BLOCKED -> SCHEDULED (Resumed dependencies satisfied)%n",
                                clock.instant(), execution.getJob().name());
                        synchronized (queue) {
                            queue.offer(execution.getJob());
                        }
                    }
                }
            }
        }
    }
}
