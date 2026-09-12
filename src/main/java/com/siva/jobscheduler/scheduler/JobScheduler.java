package com.siva.jobscheduler.scheduler;

import com.siva.jobscheduler.dependency.DependencyGraph;
import com.siva.jobscheduler.domain.*;
import com.siva.jobscheduler.execution.JobExecutor;
import com.siva.jobscheduler.recurrence.RecurrencePolicy;
import com.siva.jobscheduler.recurrence.RecurrenceState;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Core scheduling engine managing job registration, DAG dependencies, recurrence scheduling,
 * cancellation, worker dispatching, failure policies, and retries.
 */
public class JobScheduler {
    private final PriorityQueue<Job> queue;
    private final Map<String, JobExecution> activeExecutions;
    private final Map<String, JobExecution> executionHistory;
    private final Map<String, RecurrenceState> recurrenceStates;
    private final Map<String, RetryPolicy> retryPolicies;
    private final DependencyGraph dependencyGraph;
    private final Clock clock;
    private final JobExecutor jobExecutor;
    private final AtomicInteger pendingJobsCount;
    private volatile SchedulerState schedulerState;

    public JobScheduler(Clock clock, JobExecutor jobExecutor) {
        this.queue = new PriorityQueue<>();
        this.activeExecutions = new ConcurrentHashMap<>();
        this.executionHistory = new ConcurrentHashMap<>();
        this.recurrenceStates = new ConcurrentHashMap<>();
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
        this.activeExecutions = new ConcurrentHashMap<>();
        this.executionHistory = new ConcurrentHashMap<>();
        this.recurrenceStates = new ConcurrentHashMap<>();
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
        if (activeExecutions.containsKey(job.id())) {
            throw new IllegalArgumentException("Job with ID '" + job.id() + "' already has an active execution");
        }

        // Validate dependencies and DAG structure
        dependencyGraph.addJob(job);

        RetryPolicy policy = retryPolicy != null ? retryPolicy : RetryPolicy.noRetry();
        retryPolicies.put(job.id(), policy);

        int occurrenceNumber = 1;
        if (job.isRecurring()) {
            RecurrenceState recState = recurrenceStates.computeIfAbsent(job.id(),
                    id -> new RecurrenceState(job, job.recurrencePolicy()));
            occurrenceNumber = recState.getOccurrenceCount();
        }

        JobExecution execution = new JobExecution(job, occurrenceNumber);
        activeExecutions.put(job.id(), execution);
        executionHistory.put(execution.getExecutionId(), execution);
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

    public boolean cancelRecurrence(String jobId) {
        if (jobId == null) {
            return false;
        }
        RecurrenceState recState = recurrenceStates.get(jobId);
        if (recState == null || recState.isScheduleCancelled()) {
            return false;
        }
        recState.cancelSchedule();
        System.out.printf("[%s] Recurrence schedule cancelled for job id '%s'%n", clock.instant(), jobId);
        return true;
    }

    public boolean cancelJob(String jobId) {
        if (jobId == null) {
            return false;
        }
        JobExecution execution = activeExecutions.get(jobId);
        if (execution == null) {
            return false;
        }
        boolean cancelled = execution.markCancelled();
        if (cancelled) {
            System.out.printf("[%s] %s %s -> CANCELLED (Execution %s)%n",
                    clock.instant(), execution.getJob().name(), execution.getStatus(), execution.getExecutionId());
            handleExecutionFinished(execution);
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
        evaluateRecurringSchedulesOnResume();
        return true;
    }

    public SchedulerState getSchedulerState() {
        return schedulerState;
    }

    public JobExecution getExecution(String jobId) {
        JobExecution active = activeExecutions.get(jobId);
        if (active != null) {
            return active;
        }
        // Fallback to most recent in execution history
        return executionHistory.values().stream()
                .filter(e -> e.getJob().id().equals(jobId))
                .reduce((first, second) -> second)
                .orElse(null);
    }

    public JobExecution getExecutionByExecutionId(String executionId) {
        return executionHistory.get(executionId);
    }

    public RecurrenceState getRecurrenceState(String jobId) {
        return recurrenceStates.get(jobId);
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

                JobExecution execution = activeExecutions.get(nextJob.id());
                if (execution != null) {
                    if (schedulerState == SchedulerState.HALTED) {
                        synchronized (queue) {
                            queue.offer(nextJob);
                        }
                        continue;
                    }

                    boolean transitionedToRunning = execution.markRunning(now);
                    if (transitionedToRunning) {
                        System.out.printf("[%s] %s SCHEDULED -> RUNNING (Execution %s, Attempt %d)%n",
                                now, nextJob.name(), execution.getExecutionId(), execution.getAttemptCount());
                        try {
                            jobExecutor.submit(execution);
                        } catch (Throwable t) {
                            execution.markFailed(now, t);
                            System.out.printf("[%s] %s RUNNING -> FAILED (dispatch error)%n", now, nextJob.name());
                            handleExecutionFinished(execution);
                        }
                    } else {
                        System.out.printf("[%s] Skipped execution for non-scheduled job %s (Status: %s)%n",
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
     * Post-execution completion callback to evaluate retries, recurrence, dependents, or scheduler halt.
     */
    public void handleExecutionFinished(JobExecution execution) {
        if (execution == null) {
            return;
        }

        String jobId = execution.getJob().id();
        JobStatus status = execution.getStatus();

        if (status == JobStatus.COMPLETED) {
            activeExecutions.remove(jobId, execution);
            pendingJobsCount.decrementAndGet();
            reevaluateBlockedDependents(jobId);
            scheduleNextRecurrenceIfEligible(execution);
            return;
        }

        if (status == JobStatus.CANCELLED) {
            activeExecutions.remove(jobId, execution);
            pendingJobsCount.decrementAndGet();
            scheduleNextRecurrenceIfEligible(execution);
            return;
        }

        if (status == JobStatus.FAILED) {
            RetryPolicy policy = retryPolicies.getOrDefault(jobId, RetryPolicy.noRetry());
            Throwable failure = execution.getFailure();

            if (schedulerState == SchedulerState.RUNNING && policy.shouldRetry(execution, failure)) {
                Instant failureTime = execution.getCompletedAt() != null ? execution.getCompletedAt() : clock.instant();
                Instant nextRunTime = policy.calculateNextAttemptTime(execution.getAttemptCount(), failureTime);

                boolean retryScheduled = execution.markRetryScheduled();
                if (retryScheduled) {
                    Job retryJob = new Job(execution.getJob().id(), execution.getJob().name(), nextRunTime,
                            execution.getJob().task(), execution.getJob().dependencyIds(),
                            execution.getJob().failurePolicy(), execution.getJob().recurrencePolicy());
                    synchronized (queue) {
                        queue.offer(retryJob);
                    }
                    System.out.printf("[%s] %s Execution %s Attempt %d FAILED (Transient). Retry scheduled for %s%n",
                            clock.instant(), execution.getJob().name(), execution.getExecutionId(), execution.getAttemptCount(), nextRunTime);
                    return;
                }
            }

            // Permanent failure
            activeExecutions.remove(jobId, execution);
            pendingJobsCount.decrementAndGet();
            System.out.printf("[%s] %s Execution %s Attempt %d FAILED (Permanent). Final status: FAILED%n",
                    clock.instant(), execution.getJob().name(), execution.getExecutionId(), execution.getAttemptCount());

            if (execution.getJob().failurePolicy() == FailurePolicy.HALT_SCHEDULER) {
                schedulerState = SchedulerState.HALTED;
                System.out.printf("[%s] Critical job %s FAILED with HALT_SCHEDULER policy. Scheduler state -> HALTED%n",
                        clock.instant(), execution.getJob().name());
            } else {
                // FailurePolicy.CONTINUE allows recurrence to continue
                scheduleNextRecurrenceIfEligible(execution);
            }
        }
    }

    private void scheduleNextRecurrenceIfEligible(JobExecution completedExecution) {
        Job job = completedExecution.getJob();
        if (!job.isRecurring()) {
            return;
        }

        String jobId = job.id();
        RecurrenceState recState = recurrenceStates.get(jobId);
        if (recState == null || recState.isScheduleCancelled()) {
            return;
        }

        if (schedulerState != SchedulerState.RUNNING) {
            return;
        }

        RecurrencePolicy policy = job.recurrencePolicy();
        Instant now = clock.instant();
        Instant nextScheduledTime = policy.calculateNextOccurrence(recState.getLastScheduledTime(), now);

        if (!policy.hasNextOccurrence(recState.getOccurrenceCount(), nextScheduledTime)) {
            System.out.printf("[%s] Recurrence completed for job '%s' after %d occurrences%n",
                    now, jobId, recState.getOccurrenceCount());
            return;
        }

        // Advance recurrence state
        recState.recordOccurrence(nextScheduledTime);
        int nextOccurrenceNumber = recState.getOccurrenceCount();

        Job nextJob = new Job(jobId, job.name(), nextScheduledTime, job.task(),
                job.dependencyIds(), job.failurePolicy(), job.recurrencePolicy());

        JobExecution nextExecution = new JobExecution(nextJob, nextOccurrenceNumber);
        activeExecutions.put(jobId, nextExecution);
        executionHistory.put(nextExecution.getExecutionId(), nextExecution);
        pendingJobsCount.incrementAndGet();

        if (nextExecution.getStatus() == JobStatus.SCHEDULED) {
            synchronized (queue) {
                queue.offer(nextJob);
            }
            System.out.printf("[%s] %s Recurrence occurrence %d scheduled for %s (Execution %s)%n",
                    now, nextJob.name(), nextOccurrenceNumber, nextScheduledTime, nextExecution.getExecutionId());
        }
    }

    private void evaluateRecurringSchedulesOnResume() {
        for (RecurrenceState recState : recurrenceStates.values()) {
            if (!recState.isScheduleCancelled() && !activeExecutions.containsKey(recState.getInitialJob().id())) {
                Job job = recState.getInitialJob();
                RecurrencePolicy policy = job.recurrencePolicy();
                Instant now = clock.instant();
                Instant nextScheduledTime = policy.calculateNextOccurrence(recState.getLastScheduledTime(), now);

                if (policy.hasNextOccurrence(recState.getOccurrenceCount(), nextScheduledTime)) {
                    recState.recordOccurrence(nextScheduledTime);
                    int nextOccurrenceNumber = recState.getOccurrenceCount();

                    Job nextJob = new Job(job.id(), job.name(), nextScheduledTime, job.task(),
                            job.dependencyIds(), job.failurePolicy(), job.recurrencePolicy());

                    JobExecution nextExecution = new JobExecution(nextJob, nextOccurrenceNumber);
                    activeExecutions.put(job.id(), nextExecution);
                    executionHistory.put(nextExecution.getExecutionId(), nextExecution);
                    pendingJobsCount.incrementAndGet();

                    synchronized (queue) {
                        queue.offer(nextJob);
                    }
                    System.out.printf("[%s] %s Resumed recurrence occurrence %d scheduled for %s%n",
                            now, nextJob.name(), nextOccurrenceNumber, nextScheduledTime);
                }
            }
        }
    }

    private void reevaluateBlockedDependents(String completedJobId) {
        Set<String> dependentIds = dependencyGraph.getDependents(completedJobId);
        for (String dependentId : dependentIds) {
            JobExecution depExecution = activeExecutions.get(dependentId);
            if (depExecution != null && depExecution.getStatus() == JobStatus.BLOCKED) {
                if (dependencyGraph.isSatisfied(dependentId, activeExecutions)) {
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
        for (JobExecution execution : activeExecutions.values()) {
            if (execution.getStatus() == JobStatus.BLOCKED) {
                if (dependencyGraph.isSatisfied(execution.getJob().id(), activeExecutions)) {
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
