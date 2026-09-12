package com.siva.jobscheduler.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents the runtime execution state and metadata of a Job.
 * Encapsulates status transitions, execution attempts, timestamps, and failure information.
 * Thread-safe for concurrent state queries and transitions.
 */
public class JobExecution {
    private final Job job;
    private JobStatus status;
    private Instant startedAt;
    private Instant completedAt;
    private Throwable failure;
    private final List<ExecutionAttempt> attempts;
    private Instant currentAttemptStart;

    public JobExecution(Job job) {
        this.job = Objects.requireNonNull(job, "Job cannot be null");
        this.status = job.dependencyIds().isEmpty() ? JobStatus.SCHEDULED : JobStatus.BLOCKED;
        this.attempts = new ArrayList<>();
    }

    public Job getJob() {
        return job;
    }

    public synchronized JobStatus getStatus() {
        return status;
    }

    public synchronized Instant getStartedAt() {
        return startedAt;
    }

    public synchronized Instant getCompletedAt() {
        return completedAt;
    }

    public synchronized Throwable getFailure() {
        return failure;
    }

    public synchronized List<ExecutionAttempt> getAttempts() {
        return List.copyOf(attempts);
    }

    public synchronized int getAttemptCount() {
        return attempts.size() + (status == JobStatus.RUNNING ? 1 : 0);
    }

    public synchronized Duration getDuration() {
        if (startedAt != null && completedAt != null) {
            return Duration.between(startedAt, completedAt);
        }
        return null;
    }

    /**
     * Records the start of an execution attempt.
     */
    public synchronized void recordAttemptStart(Instant timestamp) {
        this.currentAttemptStart = Objects.requireNonNull(timestamp, "Attempt start timestamp cannot be null");
    }

    /**
     * Records the completion of an execution attempt.
     */
    public synchronized ExecutionAttempt recordAttemptOutcome(Instant timestamp, AttemptOutcome outcome, Throwable failureError) {
        Instant startTime = currentAttemptStart != null ? currentAttemptStart : timestamp;
        int nextAttemptNumber = attempts.size() + 1;
        ExecutionAttempt attempt = new ExecutionAttempt(nextAttemptNumber, startTime, timestamp, outcome, failureError);
        attempts.add(attempt);
        this.currentAttemptStart = null;
        return attempt;
    }

    /**
     * Atomically transitions state from BLOCKED to SCHEDULED when dependencies are satisfied.
     */
    public synchronized boolean markUnblocked() {
        if (this.status != JobStatus.BLOCKED) {
            return false;
        }
        this.status = JobStatus.SCHEDULED;
        return true;
    }

    /**
     * Atomically transitions state from SCHEDULED to RUNNING.
     */
    public synchronized boolean markRunning(Instant timestamp) {
        if (this.status != JobStatus.SCHEDULED) {
            return false;
        }
        this.status = JobStatus.RUNNING;
        this.startedAt = Objects.requireNonNull(timestamp, "Started timestamp cannot be null");
        recordAttemptStart(timestamp);
        return true;
    }

    /**
     * Atomically transitions state from SCHEDULED or BLOCKED to CANCELLED.
     */
    public synchronized boolean markCancelled() {
        if (this.status != JobStatus.SCHEDULED && this.status != JobStatus.BLOCKED) {
            return false;
        }
        this.status = JobStatus.CANCELLED;
        return true;
    }

    /**
     * Atomically transitions state from RUNNING to COMPLETED.
     */
    public synchronized boolean markCompleted(Instant timestamp) {
        validateTransition(JobStatus.COMPLETED);
        this.status = JobStatus.COMPLETED;
        this.completedAt = Objects.requireNonNull(timestamp, "Completed timestamp cannot be null");
        recordAttemptOutcome(timestamp, AttemptOutcome.SUCCESS, null);
        return true;
    }

    /**
     * Atomically transitions state from RUNNING to FAILED.
     */
    public synchronized boolean markFailed(Instant timestamp, Throwable failureError) {
        validateTransition(JobStatus.FAILED);
        this.status = JobStatus.FAILED;
        this.completedAt = Objects.requireNonNull(timestamp, "Completed timestamp cannot be null");
        this.failure = failureError;
        recordAttemptOutcome(timestamp, AttemptOutcome.FAILURE, failureError);
        return true;
    }

    /**
     * Atomically transitions state from FAILED to SCHEDULED for a retry attempt.
     */
    public synchronized boolean markRetryScheduled() {
        if (this.status != JobStatus.FAILED) {
            return false;
        }
        this.status = JobStatus.SCHEDULED;
        this.startedAt = null;
        this.completedAt = null;
        this.failure = null;
        return true;
    }

    /**
     * Performs an explicit state transition with strict validation.
     * Throws IllegalStateException if the transition is invalid.
     */
    public synchronized void transitionTo(JobStatus targetStatus, Instant timestamp, Throwable failureError) {
        if (!isValidTransition(this.status, targetStatus)) {
            throw new IllegalStateException(String.format("Invalid state transition from %s to %s for job id %s", this.status, targetStatus, job.id()));
        }

        switch (targetStatus) {
            case SCHEDULED -> {
                if (this.status == JobStatus.BLOCKED) {
                    markUnblocked();
                } else if (this.status == JobStatus.FAILED) {
                    markRetryScheduled();
                }
            }
            case RUNNING -> markRunning(timestamp != null ? timestamp : Instant.now());
            case CANCELLED -> markCancelled();
            case COMPLETED -> markCompleted(timestamp != null ? timestamp : Instant.now());
            case FAILED -> markFailed(timestamp != null ? timestamp : Instant.now(), failureError);
            default -> throw new IllegalArgumentException("Unsupported target status: " + targetStatus);
        }
    }

    private void validateTransition(JobStatus targetStatus) {
        if (!isValidTransition(this.status, targetStatus)) {
            throw new IllegalStateException(String.format("Invalid state transition from %s to %s for job id %s", this.status, targetStatus, job.id()));
        }
    }

    /**
     * Enforces valid V5 lifecycle state transitions:
     * - BLOCKED -> SCHEDULED
     * - BLOCKED -> CANCELLED
     * - SCHEDULED -> RUNNING
     * - SCHEDULED -> CANCELLED
     * - RUNNING -> COMPLETED
     * - RUNNING -> FAILED
     * - FAILED -> SCHEDULED (for retries)
     */
    public static boolean isValidTransition(JobStatus current, JobStatus target) {
        if (current == null || target == null) {
            return false;
        }
        if (current == JobStatus.BLOCKED) {
            return target == JobStatus.SCHEDULED || target == JobStatus.CANCELLED;
        }
        if (current == JobStatus.SCHEDULED) {
            return target == JobStatus.RUNNING || target == JobStatus.CANCELLED;
        }
        if (current == JobStatus.RUNNING) {
            return target == JobStatus.COMPLETED || target == JobStatus.FAILED;
        }
        if (current == JobStatus.FAILED) {
            return target == JobStatus.SCHEDULED;
        }
        return false;
    }

    @Override
    public synchronized String toString() {
        return "JobExecution{" +
                "jobId='" + job.id() + '\'' +
                ", jobName='" + job.name() + '\'' +
                ", status=" + status +
                ", attemptCount=" + getAttemptCount() +
                ", startedAt=" + startedAt +
                ", completedAt=" + completedAt +
                ", failure=" + (failure != null ? failure.getMessage() : "null") +
                '}';
    }
}
