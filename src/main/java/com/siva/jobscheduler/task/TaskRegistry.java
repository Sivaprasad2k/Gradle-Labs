package com.siva.jobscheduler.task;

import com.siva.jobscheduler.domain.JobTask;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry mapping task types to executable TaskHandler instances.
 * Enables persistent jobs to resolve their execution logic upon application restart.
 */
public class TaskRegistry {
    private final Map<String, TaskHandler> handlers;
    private static final TaskRegistry INSTANCE = new TaskRegistry();

    public TaskRegistry() {
        this.handlers = new ConcurrentHashMap<>();
        // Default fallback handlers for in-memory or generic tasks
        registerHandler("IN_MEMORY", (type, payload) -> {});
        registerHandler("NO_OP", (type, payload) -> {});
    }

    public static TaskRegistry getInstance() {
        return INSTANCE;
    }

    public void registerHandler(String taskType, TaskHandler handler) {
        Objects.requireNonNull(taskType, "taskType cannot be null");
        Objects.requireNonNull(handler, "TaskHandler cannot be null");
        handlers.put(taskType, handler);
    }

    public TaskHandler getHandler(String taskType) {
        if (taskType == null) {
            return handlers.get("NO_OP");
        }
        return handlers.getOrDefault(taskType, handlers.get("NO_OP"));
    }

    /**
     * Resolves a JobTask instance for execution from a taskType and payload.
     */
    public JobTask resolveTask(String taskType, Map<String, Object> payload) {
        TaskHandler handler = getHandler(taskType);
        return () -> {
            try {
                handler.execute(taskType, payload);
            } catch (Exception e) {
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new RuntimeException("Task execution failed for type: " + taskType, e);
            }
        };
    }
}
