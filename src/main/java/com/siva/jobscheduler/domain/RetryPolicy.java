package com.siva.jobscheduler.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Defines retry configuration, including maximum total attempts, failure classification rules, and backoff delay calculation.
 */
public class RetryPolicy {
    private final int maxAttempts;
    private final FailureClassifier failureClassifier;
    private final BackoffStrategy backoffStrategy;

    public RetryPolicy(int maxAttempts, FailureClassifier failureClassifier, BackoffStrategy backoffStrategy) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        this.maxAttempts = maxAttempts;
        this.failureClassifier = Objects.requireNonNull(failureClassifier, "failureClassifier cannot be null");
        this.backoffStrategy = Objects.requireNonNull(backoffStrategy, "backoffStrategy cannot be null");
    }

    public static RetryPolicy noRetry() {
        return new RetryPolicy(1, new DefaultFailureClassifier(), new ExponentialBackoffStrategy(Duration.ofSeconds(1)));
    }

    public boolean shouldRetry(JobExecution execution, Throwable failure) {
        if (execution == null || failure == null) {
            return false;
        }
        int currentAttemptCount = execution.getAttemptCount();
        if (currentAttemptCount >= maxAttempts) {
            return false;
        }
        return failureClassifier.classify(failure) == FailureType.TRANSIENT;
    }

    public Instant calculateNextAttemptTime(int attemptNumber, Instant failureTime) {
        Instant baseTime = failureTime != null ? failureTime : Instant.now();
        Duration delay = backoffStrategy.calculateBackoff(attemptNumber);
        return baseTime.plus(delay);
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public FailureClassifier getFailureClassifier() {
        return failureClassifier;
    }

    public BackoffStrategy getBackoffStrategy() {
        return backoffStrategy;
    }
}
