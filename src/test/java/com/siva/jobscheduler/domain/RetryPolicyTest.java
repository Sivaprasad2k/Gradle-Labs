package com.siva.jobscheduler.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RetryPolicyTest {

    static class TransientException extends RuntimeException {
        public TransientException(String msg) { super(msg); }
    }

    static class PermanentException extends RuntimeException {
        public PermanentException(String msg) { super(msg); }
    }

    @Test
    void testNoRetryDefaultPolicy() {
        RetryPolicy policy = RetryPolicy.noRetry();
        assertEquals(1, policy.getMaxAttempts());

        Job job = new Job("1", "test-job", Instant.now(), () -> {});
        JobExecution execution = new JobExecution(job);
        execution.markRunning(Instant.now());
        execution.markFailed(Instant.now(), new TransientException("Error"));

        // With maxAttempts = 1 and current attempts = 1, shouldRetry must be false
        assertFalse(policy.shouldRetry(execution, new TransientException("Error")));
    }

    @Test
    void testMaxAttemptsSemanticsAndFailureClassification() {
        FailureClassifier classifier = new DefaultFailureClassifier(Set.of(TransientException.class));
        BackoffStrategy backoff = new ExponentialBackoffStrategy(Duration.ofSeconds(1), Duration.ofSeconds(5));
        RetryPolicy policy = new RetryPolicy(3, classifier, backoff); // Max 3 total attempts

        Job job = new Job("1", "test-job", Instant.now(), () -> {});
        JobExecution execution = new JobExecution(job);

        // Attempt 1 fails transiently
        execution.markRunning(Instant.now());
        execution.markFailed(Instant.now(), new TransientException("Network error"));
        assertTrue(policy.shouldRetry(execution, new TransientException("Network error")), "Attempt 1 of 3 (transient) should retry");

        // Attempt 1 fails permanently -> should NOT retry
        assertFalse(policy.shouldRetry(execution, new PermanentException("Invalid config")), "Permanent failure should not retry");

        // Prepare Attempt 2
        execution.markRetryScheduled();
        execution.markRunning(Instant.now());
        execution.markFailed(Instant.now(), new TransientException("Network error"));
        assertTrue(policy.shouldRetry(execution, new TransientException("Network error")), "Attempt 2 of 3 (transient) should retry");

        // Prepare Attempt 3
        execution.markRetryScheduled();
        execution.markRunning(Instant.now());
        execution.markFailed(Instant.now(), new TransientException("Network error"));
        assertFalse(policy.shouldRetry(execution, new TransientException("Network error")), "Attempt 3 of 3 reached maxAttempts, should NOT retry");
    }

    @Test
    void testNextAttemptTimeCalculation() {
        RetryPolicy policy = new RetryPolicy(3, failure -> FailureType.TRANSIENT, new ExponentialBackoffStrategy(Duration.ofSeconds(2)));
        Instant now = Instant.now();

        // Attempt 1 failure -> delay 2s
        Instant nextTime = policy.calculateNextAttemptTime(1, now);
        assertEquals(now.plusSeconds(2), nextTime);

        // Attempt 2 failure -> delay 4s
        Instant nextTime2 = policy.calculateNextAttemptTime(2, now);
        assertEquals(now.plusSeconds(4), nextTime2);
    }
}
