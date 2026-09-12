package com.siva.jobscheduler.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Represents a single execution attempt of a JobExecution.
 * Captures attempt index, start/completion timestamps, outcome, and failure metadata.
 */
public record ExecutionAttempt(
        int attemptNumber,
        Instant startedAt,
        Instant completedAt,
        AttemptOutcome outcome,
        Throwable failure
) {
    public ExecutionAttempt {
        if (attemptNumber <= 0) {
            throw new IllegalArgumentException("Attempt number must be greater than zero");
        }
    }

    public Duration getDuration() {
        if (startedAt != null && completedAt != null) {
            return Duration.between(startedAt, completedAt);
        }
        return null;
    }
}
