package com.siva.jobscheduler.dto;

public record SchedulerStatusResponse(
        String status, // RUNNING, HALTED, STOPPED
        int totalJobs,
        int activeExecutions,
        int runningExecutions,
        int scheduledExecutions,
        int blockedExecutions,
        int failedExecutions,
        int completedExecutions,
        int activeWorkerThreads,
        int totalWorkerThreads,
        int pendingQueueSize,
        String dbStatus,
        String dbName,
        String version,
        String environment,
        String uptime
) {}
