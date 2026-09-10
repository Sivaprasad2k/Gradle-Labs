package com.siva.jobscheduler.domain;

/**
 * Functional interface representing a task to be executed by the job scheduler.
 */
@FunctionalInterface
public interface JobTask {
    void execute();
}
