package com.siva.jobscheduler.domain;

/**
 * Represents the operational state of the JobScheduler engine.
 */
public enum SchedulerState {
    /**
     * Normal scheduling operations are active.
     */
    RUNNING,

    /**
     * Scheduling of new work is suspended due to a critical job failure.
     * Active worker execution continues until completion.
     */
    HALTED,

    /**
     * Scheduler is explicitly shut down.
     */
    STOPPED
}
