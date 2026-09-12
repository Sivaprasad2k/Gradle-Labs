package com.siva.jobscheduler.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class JobExecutionTest {

    @Test
    void testInitialStateIsScheduledWhenNoDependencies() {
        Job job = new Job("1", "test-job", Instant.now(), () -> {});
        JobExecution execution = new JobExecution(job);

        assertEquals(JobStatus.SCHEDULED, execution.getStatus());
        assertEquals(job, execution.getJob());
        assertEquals(0, execution.getAttemptCount());
        assertTrue(execution.getAttempts().isEmpty());
    }

    @Test
    void testInitialStateIsBlockedWhenDependenciesPresent() {
        Job job = new Job("2", "dependent-job", Instant.now(), () -> {}, Set.of("1"));
        JobExecution execution = new JobExecution(job);

        assertEquals(JobStatus.BLOCKED, execution.getStatus());
    }

    @Test
    void testBlockedToScheduledTransition() {
        Job job = new Job("2", "dependent-job", Instant.now(), () -> {}, Set.of("1"));
        JobExecution execution = new JobExecution(job);

        assertEquals(JobStatus.BLOCKED, execution.getStatus());
        assertTrue(execution.markUnblocked());
        assertEquals(JobStatus.SCHEDULED, execution.getStatus());
    }

    @Test
    void testBlockedToCancelledTransition() {
        Job job = new Job("2", "dependent-job", Instant.now(), () -> {}, Set.of("1"));
        JobExecution execution = new JobExecution(job);

        assertEquals(JobStatus.BLOCKED, execution.getStatus());
        assertTrue(execution.markCancelled());
        assertEquals(JobStatus.CANCELLED, execution.getStatus());
        assertFalse(execution.markUnblocked(), "Cancelled blocked job cannot be unblocked later");
    }

    @Test
    void testInvalidBlockedTransitionsThrowExceptionOrReturnFalse() {
        Instant now = Instant.now();
        Job job = new Job("2", "dependent-job", now, () -> {}, Set.of("1"));
        JobExecution execBlocked = new JobExecution(job);

        // BLOCKED -> RUNNING, COMPLETED, FAILED directly must throw or fail
        assertThrows(IllegalStateException.class, () -> execBlocked.transitionTo(JobStatus.RUNNING, now, null));
        assertThrows(IllegalStateException.class, () -> execBlocked.transitionTo(JobStatus.COMPLETED, now, null));
        assertThrows(IllegalStateException.class, () -> execBlocked.transitionTo(JobStatus.FAILED, now, new Exception()));

        // RUNNING -> BLOCKED, COMPLETED -> BLOCKED, FAILED -> BLOCKED, CANCELLED -> BLOCKED
        JobExecution execRunning = new JobExecution(new Job("1", "job-1", now, () -> {}));
        execRunning.markRunning(now);
        assertThrows(IllegalStateException.class, () -> execRunning.transitionTo(JobStatus.BLOCKED, now, null));

        JobExecution execCompleted = new JobExecution(new Job("1", "job-1", now, () -> {}));
        execCompleted.markRunning(now);
        execCompleted.markCompleted(now);
        assertThrows(IllegalStateException.class, () -> execCompleted.transitionTo(JobStatus.BLOCKED, now, null));
    }

    @Test
    void testExecutionAttemptTracking() {
        Instant now = Instant.now();
        Job job = new Job("1", "test-job", now, () -> {});
        JobExecution execution = new JobExecution(job);

        assertTrue(execution.markRunning(now));
        assertEquals(1, execution.getAttemptCount());
        Throwable failure1 = new RuntimeException("Attempt 1 failed");
        assertTrue(execution.markFailed(now.plusMillis(100), failure1));

        List<ExecutionAttempt> attempts = execution.getAttempts();
        assertEquals(1, attempts.size());
        ExecutionAttempt attempt1 = attempts.get(0);
        assertEquals(1, attempt1.attemptNumber());
        assertEquals(now, attempt1.startedAt());
        assertEquals(now.plusMillis(100), attempt1.completedAt());
        assertEquals(AttemptOutcome.FAILURE, attempt1.outcome());

        assertTrue(execution.markRetryScheduled());
        assertEquals(JobStatus.SCHEDULED, execution.getStatus());

        Instant attempt2Start = now.plusSeconds(1);
        assertTrue(execution.markRunning(attempt2Start));
        assertEquals(2, execution.getAttemptCount());
        assertTrue(execution.markCompleted(attempt2Start.plusMillis(50)));

        List<ExecutionAttempt> attemptsUpdated = execution.getAttempts();
        assertEquals(2, attemptsUpdated.size());
    }

    @Test
    void testConcurrentTransitionRace() throws InterruptedException {
        Job job = new Job("1", "race-job", Instant.now(), () -> {});
        JobExecution execution = new JobExecution(job);

        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicBoolean runningResult = new AtomicBoolean(false);
        AtomicBoolean cancelResult = new AtomicBoolean(false);

        Thread threadA = new Thread(() -> {
            try {
                startLatch.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            runningResult.set(execution.markRunning(Instant.now()));
        });

        Thread threadB = new Thread(() -> {
            try {
                startLatch.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            cancelResult.set(execution.markCancelled());
        });

        threadA.start();
        threadB.start();

        startLatch.countDown();

        threadA.join(3000);
        threadB.join(3000);

        assertTrue(runningResult.get() ^ cancelResult.get(), "Exactly one transition operation must succeed");
    }
}
