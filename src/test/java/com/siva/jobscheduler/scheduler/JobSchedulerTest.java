package com.siva.jobscheduler.scheduler;

import com.siva.jobscheduler.domain.Job;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import static org.junit.jupiter.api.Assertions.*;

class JobSchedulerTest {

    @Test
    void testEmptySchedulerCompletesInstantly() {
        // Arrange
        Clock clock = Clock.fixed(Instant.now(), ZoneId.of("UTC"));
        JobScheduler scheduler = new JobScheduler(clock);

        // Act & Assert
        assertDoesNotThrow(scheduler::start);
    }

    @Test
    void testDueJobExecutes() {
        // Arrange
        Clock clock = Clock.fixed(Instant.now(), ZoneId.of("UTC"));
        JobScheduler scheduler = new JobScheduler(clock);
        
        boolean[] executed = {false};
        Job job = new Job("1", "due-job", clock.instant().minusSeconds(1), () -> executed[0] = true);
        scheduler.registerJob(job);

        // Act
        scheduler.start();

        // Assert
        assertTrue(executed[0], "Job should have been executed");
    }

    @Test
    void testMultipleJobsExecuteInScheduledOrder() {
        // Arrange
        Clock clock = Clock.fixed(Instant.now(), ZoneId.of("UTC"));
        JobScheduler scheduler = new JobScheduler(clock);
        
        StringBuilder executionOrder = new StringBuilder();
        
        Instant now = clock.instant();
        Job job1 = new Job("1", "job-1", now.minusSeconds(10), () -> executionOrder.append("1"));
        Job job2 = new Job("2", "job-2", now.minusSeconds(5), () -> executionOrder.append("2"));
        Job job3 = new Job("3", "job-3", now.minusSeconds(1), () -> executionOrder.append("3"));
        
        // Register out of order
        scheduler.registerJob(job2);
        scheduler.registerJob(job3);
        scheduler.registerJob(job1);

        // Act
        scheduler.start();

        // Assert
        assertEquals("123", executionOrder.toString(), "Jobs should execute in scheduledAt order");
    }

    @Test
    void testFutureSchedulingBehavior() {
        // Arrange
        Clock clock = Clock.systemUTC();
        JobScheduler scheduler = new JobScheduler(clock);
        
        boolean[] executed = {false};
        
        // Schedule a job 50ms in the future to keep test fast but verify wait logic
        Instant futureTime = clock.instant().plusMillis(50);
        Job job = new Job("1", "future-job", futureTime, () -> executed[0] = true);
        scheduler.registerJob(job);
        
        // Act
        scheduler.start();
        
        // Assert
        assertTrue(executed[0], "Future job should have been executed after waiting");
        assertFalse(clock.instant().isBefore(futureTime), "Scheduler should have waited until the scheduled time");
    }
}
