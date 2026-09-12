package com.siva.jobscheduler.dto;

import com.siva.jobscheduler.domain.Job;
import com.siva.jobscheduler.recurrence.FixedRateRecurrence;

import java.util.Map;
import java.util.Set;

public record JobResponse(
        String id,
        String jobId,
        String name,
        String jobName,
        String scheduledAt,
        String taskType,
        Map<String, Object> taskPayload,
        Set<String> dependencyIds,
        String failurePolicy,
        boolean isRecurring,
        RecurrenceResponse recurrence
) {
    public record RecurrenceResponse(
            String type,
            long intervalMs,
            Integer maxOccurrences,
            String endTime
    ) {}

    public static JobResponse fromDomain(Job job) {
        RecurrenceResponse rec = null;
        if (job.isRecurring() && job.recurrencePolicy() instanceof FixedRateRecurrence fr) {
            rec = new RecurrenceResponse(
                    "FIXED_RATE",
                    fr.interval().toMillis(),
                    fr.maxOccurrences().isPresent() ? fr.maxOccurrences().getAsInt() : null,
                    fr.endTime().isPresent() ? fr.endTime().get().toString() : null
            );
        }

        return new JobResponse(
                job.id(),
                job.id(),
                job.name(),
                job.name(),
                job.scheduledAt().toString(),
                job.taskType(),
                job.taskPayload(),
                job.dependencyIds(),
                job.failurePolicy().name(),
                job.isRecurring(),
                rec
        );
    }
}
