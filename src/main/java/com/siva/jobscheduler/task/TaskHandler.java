package com.siva.jobscheduler.task;

import java.util.Map;

/**
 * Defines the contract for an executable task handler bound to a persistent taskType.
 */
@FunctionalInterface
public interface TaskHandler {

    /**
     * Executes the task given its type and parameter payload.
     *
     * @param taskType the type identifier of the task
     * @param payload  parameter map associated with the task execution
     * @throws Exception if execution fails
     */
    void execute(String taskType, Map<String, Object> payload) throws Exception;
}
