package com.siva.jobscheduler.domain;

/**
 * Represents the lifecycle status of a job execution.
 */
public enum JobStatus {
    SCHEDULED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED;

    /**
     * Checks if the status is a terminal state (cannot transition to any other state).
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
