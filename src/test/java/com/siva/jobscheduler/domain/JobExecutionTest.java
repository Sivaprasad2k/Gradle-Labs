package com.siva.jobscheduler.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class JobExecutionTest {

    @Test
    void testInitialStateIsScheduled() {
        Job job = new Job("1", "test-job", Instant.now(), () -> {});
        JobExecution execution = new JobExecution(job);

        assertEquals(JobStatus.SCHEDULED, execution.getStatus());
        assertEquals(job, execution.getJob());
        assertEquals(0, execution.getAttemptCount());
        assertTrue(execution.getAttempts().isEmpty());
        assertNull(execution.getStartedAt());
        assertNull(execution.getCompletedAt());
        assertNull(execution.getFailure());
        assertNull(execution.getDuration());
    }

    @Test
    void testExecutionAttemptTracking() {
        Instant now = Instant.now();
        Job job = new Job("1", "test-job", now, () -> {});
        JobExecution execution = new JobExecution(job);

        // Attempt 1 (First attempt starts at attempt number 1)
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
        assertEquals(failure1, attempt1.failure());

        // Retry scheduled (FAILED -> SCHEDULED)
        assertTrue(execution.markRetryScheduled());
        assertEquals(JobStatus.SCHEDULED, execution.getStatus());

        // Attempt 2 (Subsequent retry becomes attempt 2)
        Instant attempt2Start = now.plusSeconds(1);
        assertTrue(execution.markRunning(attempt2Start));
        assertEquals(2, execution.getAttemptCount());
        assertTrue(execution.markCompleted(attempt2Start.plusMillis(50)));

        List<ExecutionAttempt> attemptsUpdated = execution.getAttempts();
        assertEquals(2, attemptsUpdated.size());
        ExecutionAttempt attempt2 = attemptsUpdated.get(1);
        assertEquals(2, attempt2.attemptNumber());
        assertEquals(attempt2Start, attempt2.startedAt());
        assertEquals(attempt2Start.plusMillis(50), attempt2.completedAt());
        assertEquals(AttemptOutcome.SUCCESS, attempt2.outcome());
        assertNull(attempt2.failure());
    }

    @Test
    void testValidTransitions() {
        Instant now = Instant.now();
        Job job = new Job("1", "test-job", now, () -> {});

        // SCHEDULED -> RUNNING
        JobExecution exec1 = new JobExecution(job);
        assertTrue(exec1.markRunning(now));
        assertEquals(JobStatus.RUNNING, exec1.getStatus());

        // RUNNING -> COMPLETED
        Instant completedTime = now.plusMillis(100);
        assertTrue(exec1.markCompleted(completedTime));
        assertEquals(JobStatus.COMPLETED, exec1.getStatus());

        // FAILED -> SCHEDULED
        JobExecution exec2 = new JobExecution(job);
        exec2.markRunning(now);
        exec2.markFailed(now.plusMillis(100), new RuntimeException("Error"));
        assertEquals(JobStatus.FAILED, exec2.getStatus());
        assertTrue(exec2.markRetryScheduled());
        assertEquals(JobStatus.SCHEDULED, exec2.getStatus());
    }

    @Test
    void testInvalidTransitionsThrowExceptionOrReturnFalse() {
        Instant now = Instant.now();
        Job job = new Job("1", "test-job", now, () -> {});

        // SCHEDULED -> COMPLETED or FAILED directly
        JobExecution exec1 = new JobExecution(job);
        assertThrows(IllegalStateException.class, () -> exec1.transitionTo(JobStatus.COMPLETED, now, null));
        assertThrows(IllegalStateException.class, () -> exec1.transitionTo(JobStatus.FAILED, now, new Exception()));

        // COMPLETED -> RUNNING, FAILED, CANCELLED
        JobExecution execCompleted = new JobExecution(job);
        execCompleted.markRunning(now);
        execCompleted.markCompleted(now.plusMillis(50));
        assertThrows(IllegalStateException.class, () -> execCompleted.transitionTo(JobStatus.RUNNING, now, null));
        assertThrows(IllegalStateException.class, () -> execCompleted.transitionTo(JobStatus.FAILED, now, new Exception()));
        assertThrows(IllegalStateException.class, () -> execCompleted.transitionTo(JobStatus.CANCELLED, now, null));
        assertFalse(execCompleted.markRunning(now));
        assertFalse(execCompleted.markCancelled());

        // CANCELLED -> RUNNING, COMPLETED, FAILED
        JobExecution execCancelled = new JobExecution(job);
        execCancelled.markCancelled();
        assertThrows(IllegalStateException.class, () -> execCancelled.transitionTo(JobStatus.RUNNING, now, null));
        assertThrows(IllegalStateException.class, () -> execCancelled.transitionTo(JobStatus.COMPLETED, now, null));
        assertThrows(IllegalStateException.class, () -> execCancelled.transitionTo(JobStatus.FAILED, now, new Exception()));
        assertFalse(execCancelled.markRunning(now));
        assertFalse(execCancelled.markCancelled());
    }

    @Test
    void testTerminalStatesCannotTransition() {
        assertTrue(JobStatus.COMPLETED.isTerminal());
        assertTrue(JobStatus.FAILED.isTerminal());
        assertTrue(JobStatus.CANCELLED.isTerminal());
        assertFalse(JobStatus.SCHEDULED.isTerminal());
        assertFalse(JobStatus.RUNNING.isTerminal());
    }

    @Test
    void testCancellationBehavior() {
        Instant now = Instant.now();
        Job job = new Job("1", "test-job", now, () -> {});

        // A scheduled job can be cancelled
        JobExecution exec1 = new JobExecution(job);
        assertTrue(exec1.markCancelled());
        assertEquals(JobStatus.CANCELLED, exec1.getStatus());

        // A cancelled job cannot execute
        assertFalse(exec1.markRunning(now));
        assertEquals(JobStatus.CANCELLED, exec1.getStatus());

        // A running job cannot be cancelled
        JobExecution exec2 = new JobExecution(job);
        assertTrue(exec2.markRunning(now));
        assertFalse(exec2.markCancelled());
        assertEquals(JobStatus.RUNNING, exec2.getStatus());
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
