package com.siva.jobscheduler.recurrence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FixedRateRecurrence Unit Tests")
class FixedRateRecurrenceTest {

    @Test
    @DisplayName("Should calculate simple next occurrence")
    void testSimpleNextOccurrence() {
        FixedRateRecurrence policy = new FixedRateRecurrence(Duration.ofMinutes(5));
        Instant lastScheduled = Instant.parse("2026-09-12T10:00:00Z");
        Instant now = Instant.parse("2026-09-12T10:01:00Z");

        Instant next = policy.calculateNextOccurrence(lastScheduled, now);
        assertEquals(Instant.parse("2026-09-12T10:05:00Z"), next);
        assertTrue(policy.hasNextOccurrence(1, next));
    }

    @Test
    @DisplayName("Should coalesce missed occurrences when execution is delayed past multiple intervals")
    void testCoalescingMissedOccurrences() {
        FixedRateRecurrence policy = new FixedRateRecurrence(Duration.ofMinutes(1));
        Instant lastScheduled = Instant.parse("2026-09-12T10:00:00Z");
        // Execution finished at 10:03:30Z (missed 10:01:00Z, 10:02:00Z, 10:03:00Z)
        Instant now = Instant.parse("2026-09-12T10:03:30Z");

        Instant next = policy.calculateNextOccurrence(lastScheduled, now);
        assertEquals(Instant.parse("2026-09-12T10:04:00Z"), next);
    }

    @Test
    @DisplayName("Should respect maxOccurrences constraint")
    void testMaxOccurrencesConstraint() {
        FixedRateRecurrence policy = new FixedRateRecurrence(Duration.ofMinutes(5), 3);
        Instant next = Instant.parse("2026-09-12T10:15:00Z");

        assertTrue(policy.hasNextOccurrence(2, next));
        assertFalse(policy.hasNextOccurrence(3, next));
        assertFalse(policy.hasNextOccurrence(4, next));
    }

    @Test
    @DisplayName("Should respect endTime cutoff constraint")
    void testEndTimeConstraint() {
        Instant endTime = Instant.parse("2026-09-12T10:30:00Z");
        FixedRateRecurrence policy = new FixedRateRecurrence(Duration.ofMinutes(10), endTime);

        assertTrue(policy.hasNextOccurrence(1, Instant.parse("2026-09-12T10:20:00Z")));
        assertFalse(policy.hasNextOccurrence(2, Instant.parse("2026-09-12T10:30:00Z")));
        assertFalse(policy.hasNextOccurrence(2, Instant.parse("2026-09-12T10:40:00Z")));
    }

    @Test
    @DisplayName("Should throw exception for non-positive intervals")
    void testInvalidInterval() {
        assertThrows(IllegalArgumentException.class, () -> new FixedRateRecurrence(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new FixedRateRecurrence(Duration.ofMinutes(-5)));
    }
}
