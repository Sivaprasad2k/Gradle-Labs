package com.siva.jobscheduler.domain;

/**
 * Defines the scheduler action when a job reaches a permanent FAILED state.
 */
public enum FailurePolicy {
    /**
     * The failed job does not stop unrelated job scheduling.
     * Dependents of the failed job remain BLOCKED.
     */
    CONTINUE,

    /**
     * The terminal failure of this job causes the scheduler to enter the HALTED state.
     */
    HALT_SCHEDULER
}
