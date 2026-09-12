package com.siva.jobscheduler.dto;

import java.util.Map;
import java.util.Set;

public record CreateJobRequest(
        String id,
        String name,
        String scheduledAt, // ISO-8601 string or null for immediate
        String taskType,
        Map<String, Object> taskPayload,
        Set<String> dependencyIds,
        String failurePolicy, // CONTINUE or HALT_SCHEDULER
        RecurrenceDto recurrence
) {
    public record RecurrenceDto(
            String type, // FIXED_RATE
            long intervalMs,
            Integer maxOccurrences,
            String endTime
    ) {}
}
