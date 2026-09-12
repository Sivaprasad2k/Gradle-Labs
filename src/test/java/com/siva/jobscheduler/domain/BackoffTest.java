package com.siva.jobscheduler.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class BackoffTest {

    @Test
    void testExponentialBackoffCalculation() {
        // Initial = 1s, Max = 5s
        ExponentialBackoffStrategy backoff = new ExponentialBackoffStrategy(Duration.ofSeconds(1), Duration.ofSeconds(5));

        // Attempt 1 -> 1s * 2^0 = 1s
        assertEquals(Duration.ofSeconds(1), backoff.calculateBackoff(1));

        // Attempt 2 -> 1s * 2^1 = 2s
        assertEquals(Duration.ofSeconds(2), backoff.calculateBackoff(2));

        // Attempt 3 -> 1s * 2^2 = 4s
        assertEquals(Duration.ofSeconds(4), backoff.calculateBackoff(3));

        // Attempt 4 -> 1s * 2^3 = 8s -> clamped to 5s
        assertEquals(Duration.ofSeconds(5), backoff.calculateBackoff(4));

        // Attempt 5 -> 5s (clamped)
        assertEquals(Duration.ofSeconds(5), backoff.calculateBackoff(5));
    }

    @Test
    void testOverflowProtection() {
        ExponentialBackoffStrategy backoff = new ExponentialBackoffStrategy(Duration.ofSeconds(1), Duration.ofSeconds(60));

        // High attempt number should not overflow or throw exception
        assertDoesNotThrow(() -> backoff.calculateBackoff(100));
        assertEquals(Duration.ofSeconds(60), backoff.calculateBackoff(100));
    }

    @Test
    void testConstructorValidations() {
        ExponentialBackoffStrategy backoff = new ExponentialBackoffStrategy(Duration.ofSeconds(1), Duration.ofSeconds(5));

        assertThrows(IllegalArgumentException.class, () -> new ExponentialBackoffStrategy(Duration.ofSeconds(-1), Duration.ofSeconds(5)));
        assertThrows(IllegalArgumentException.class, () -> new ExponentialBackoffStrategy(Duration.ofSeconds(10), Duration.ofSeconds(5)));
        assertThrows(IllegalArgumentException.class, () -> backoff.calculateBackoff(0));
    }
}
