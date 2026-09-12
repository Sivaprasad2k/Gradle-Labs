package com.siva.jobscheduler.scheduler;

import com.siva.jobscheduler.domain.Job;
import com.siva.jobscheduler.domain.JobExecution;
import com.siva.jobscheduler.domain.JobStatus;
import com.siva.jobscheduler.domain.RetryPolicy;
import com.siva.jobscheduler.execution.JobExecutor;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages the registration, cancellation, dispatching, and retry scheduling of jobs.
 * Enforces atomic state transitions and centralizes retry backoff calculation.
 */
public class JobScheduler {
    private final PriorityQueue<Job> queue;
    private final Map<String, JobExecution> executions;
    private final Map<String, RetryPolicy> retryPolicies;
    private final Clock clock;
    private final JobExecutor jobExecutor;
    private final AtomicInteger pendingJobsCount;

    public JobScheduler(Clock clock, JobExecutor jobExecutor) {
        this.queue = new PriorityQueue<>();
        this.executions = new ConcurrentHashMap<>();
        this.retryPolicies = new ConcurrentHashMap<>();
        this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
        this.jobExecutor = Objects.requireNonNull(jobExecutor, "JobExecutor cannot be null");
        this.jobExecutor.setOnExecutionFinished(this::handleExecutionFinished);
        this.pendingJobsCount = new AtomicInteger(0);
    }

    public JobScheduler(Clock clock) {
        this(clock, new JobExecutor(1, clock));
    }

    public JobScheduler(JobExecutor jobExecutor) {
        this(Clock.systemUTC(), jobExecutor);
    }

    public JobExecution registerJob(Job job, RetryPolicy retryPolicy) {
        Objects.requireNonNull(job, "Job cannot be null");
        RetryPolicy policy = retryPolicy != null ? retryPolicy : RetryPolicy.noRetry();
        retryPolicies.put(job.id(), policy);

        JobExecution execution = new JobExecution(job);
        executions.put(job.id(), execution);
        pendingJobsCount.incrementAndGet();
        synchronized (queue) {
            queue.offer(job);
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
            System.out.printf("[%s] %s SCHEDULED -> CANCELLED%n", clock.instant(), execution.getJob().name());
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

    public JobExecution getExecution(String jobId) {
        return executions.get(jobId);
    }

    public void start() {
        System.out.println("Scheduler started.\n");
        while (pendingJobsCount.get() > 0) {
            Job nextJob;
            synchronized (queue) {
                nextJob = queue.peek();
            }

            if (nextJob == null) {
                // Queue temporarily empty while active workers execute
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
                // Job is due
                synchronized (queue) {
                    queue.poll();
                }

                JobExecution execution = executions.get(nextJob.id());
                if (execution != null) {
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
                // Wait until the earliest job is due
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
        System.out.println("\nAll jobs dispatched to executor.");
        jobExecutor.shutdown();
        System.out.println("Scheduler stopped.");
    }

    /**
     * Post-execution completion callback to evaluate retries.
     */
    public void handleExecutionFinished(JobExecution execution) {
        if (execution == null) {
            return;
        }

        if (execution.getStatus() == JobStatus.COMPLETED) {
            pendingJobsCount.decrementAndGet();
            return;
        }

        if (execution.getStatus() == JobStatus.FAILED) {
            String jobId = execution.getJob().id();
            RetryPolicy policy = retryPolicies.getOrDefault(jobId, RetryPolicy.noRetry());
            Throwable failure = execution.getFailure();

            if (policy.shouldRetry(execution, failure)) {
                Instant failureTime = execution.getCompletedAt() != null ? execution.getCompletedAt() : clock.instant();
                Instant nextRunTime = policy.calculateNextAttemptTime(execution.getAttemptCount(), failureTime);

                boolean retryScheduled = execution.markRetryScheduled();
                if (retryScheduled) {
                    Job retryJob = new Job(execution.getJob().id(), execution.getJob().name(), nextRunTime, execution.getJob().task());
                    synchronized (queue) {
                        queue.offer(retryJob);
                    }
                    System.out.printf("[%s] %s Attempt %d FAILED (Transient). Retry scheduled for %s%n",
                            clock.instant(), execution.getJob().name(), execution.getAttemptCount(), nextRunTime);
                    return;
                }
            }

            // Retry not allowed or max attempts exhausted
            pendingJobsCount.decrementAndGet();
            System.out.printf("[%s] %s Attempt %d FAILED (Permanent / Retries Exhausted). Final status: FAILED%n",
                    clock.instant(), execution.getJob().name(), execution.getAttemptCount());
        }
    }
}
