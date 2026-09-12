package com.siva.jobscheduler.dto;

import com.siva.jobscheduler.domain.ExecutionAttempt;

public record AttemptResponse(
        int attemptNumber,
        String startedAt,
        String completedAt,
        String outcome,
        String failureError,
        Long durationMillis
) {
    public static AttemptResponse fromDomain(ExecutionAttempt attempt) {
        return new AttemptResponse(
                attempt.attemptNumber(),
                attempt.startedAt().toString(),
                attempt.completedAt() != null ? attempt.completedAt().toString() : null,
                attempt.outcome().name(),
                attempt.failure() != null ? attempt.failure().getMessage() : null,
                attempt.getDuration() != null ? attempt.getDuration().toMillis() : null
        );
    }
}
