package com.siva.jobscheduler.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class JobTest {

    @Test
    void testJobCreation() {
        // Arrange
        Instant now = Instant.now();
        JobTask task = () -> {};
        
        // Act
        Job job = new Job("id-1", "test-job", now, task);

        // Assert
        assertEquals("id-1", job.id());
        assertEquals("test-job", job.name());
        assertEquals(now, job.scheduledAt());
        assertEquals(task, job.task());
    }

    @Test
    void testJobRequiresNonNullProperties() {
        // Arrange
        Instant now = Instant.now();
        JobTask task = () -> {};

        // Act & Assert
        assertThrows(NullPointerException.class, () -> new Job(null, "name", now, task));
        assertThrows(NullPointerException.class, () -> new Job("id", null, now, task));
        assertThrows(NullPointerException.class, () -> new Job("id", "name", null, task));
        assertThrows(NullPointerException.class, () -> new Job("id", "name", now, null));
    }

    @Test
    void testJobOrdering() {
        // Arrange
        Instant now = Instant.now();
        Job jobPast = new Job("1", "past", now.minusSeconds(10), () -> {});
        Job jobNow = new Job("2", "now", now, () -> {});
        Job jobFuture = new Job("3", "future", now.plusSeconds(10), () -> {});

        // Act & Assert
        assertTrue(jobPast.compareTo(jobNow) < 0);
        assertTrue(jobFuture.compareTo(jobNow) > 0);
        assertEquals(0, jobNow.compareTo(new Job("4", "now-dup", now, () -> {})));
    }
}
