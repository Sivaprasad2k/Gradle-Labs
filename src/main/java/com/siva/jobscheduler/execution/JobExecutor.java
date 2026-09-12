package com.siva.jobscheduler.execution;

import com.siva.jobscheduler.domain.Job;
import com.siva.jobscheduler.domain.JobExecution;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Handles the concurrent execution of jobs using a bounded worker pool.
 * Updates JobExecution status to COMPLETED or FAILED based on execution outcome and notifies completion listener.
 */
public class JobExecutor {
    private final ExecutorService executorService;
    private final Clock clock;
    private final int workerCount;
    private volatile Consumer<JobExecution> onExecutionFinished;

    public JobExecutor(int workerCount) {
        this(workerCount, Clock.systemUTC(), null);
    }

    public JobExecutor(int workerCount, Clock clock) {
        this(workerCount, clock, null);
    }

    public JobExecutor(int workerCount, Clock clock, Consumer<JobExecution> onExecutionFinished) {
        if (workerCount <= 0) {
            throw new IllegalArgumentException("Worker count must be greater than zero");
        }
        this.workerCount = workerCount;
        this.executorService = Executors.newFixedThreadPool(workerCount);
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.onExecutionFinished = onExecutionFinished;
    }

    public int getWorkerCount() {
        return workerCount;
    }

    public void setOnExecutionFinished(Consumer<JobExecution> onExecutionFinished) {
        this.onExecutionFinished = onExecutionFinished;
    }

    public void submit(JobExecution execution) {
        Objects.requireNonNull(execution, "JobExecution cannot be null");
        executorService.submit(() -> executeJob(execution));
    }

    public void submit(Job job) {
        Objects.requireNonNull(job, "Job cannot be null");
        JobExecution execution = new JobExecution(job);
        execution.markRunning(clock.instant());
        submit(execution);
    }

    private void executeJob(JobExecution execution) {
        Job job = execution.getJob();
        String threadName = Thread.currentThread().getName();
        Instant startTime = clock.instant();
        int attemptNum = execution.getAttemptCount();

        System.out.printf("[%s] [%s] %s Attempt %d RUNNING%n", startTime, threadName, job.name(), attemptNum);
        try {
            job.task().execute();
            Instant completedTime = clock.instant();
            execution.markCompleted(completedTime);
            System.out.printf("[%s] [%s] %s Attempt %d RUNNING -> COMPLETED%n", completedTime, threadName, job.name(), attemptNum);
        } catch (Throwable e) {
            Instant failedTime = clock.instant();
            execution.markFailed(failedTime, e);
            System.out.printf("[%s] [%s] %s Attempt %d RUNNING -> FAILED: %s%n", failedTime, threadName, job.name(), attemptNum, e.getMessage());
        } finally {
            Consumer<JobExecution> listener = onExecutionFinished;
            if (listener != null) {
                try {
                    listener.accept(execution);
                } catch (Exception ignored) {
                }
            }
        }
    }

    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return executorService.awaitTermination(timeout, unit);
    }

    public boolean isShutdown() {
        return executorService.isShutdown();
    }

    public boolean isTerminated() {
        return executorService.isTerminated();
    }
}
