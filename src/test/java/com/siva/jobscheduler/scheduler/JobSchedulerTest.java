package com.siva.jobscheduler.scheduler;

import com.siva.jobscheduler.domain.*;
import com.siva.jobscheduler.execution.JobExecutor;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class JobSchedulerTest {

    static class TransientTestException extends RuntimeException {
        public TransientTestException(String msg) { super(msg); }
    }

    static class PermanentTestException extends RuntimeException {
        public PermanentTestException(String msg) { super(msg); }
    }

    @Test
    void testEmptySchedulerCompletesInstantly() {
        Clock clock = Clock.fixed(Instant.now(), ZoneId.of("UTC"));
        JobScheduler scheduler = new JobScheduler(clock);

        assertDoesNotThrow(scheduler::start);
    }

    @Test
    void testSuccessfulFirstAttemptReachesCompleted() {
        Clock clock = Clock.fixed(Instant.now(), ZoneId.of("UTC"));
        JobScheduler scheduler = new JobScheduler(clock);

        AtomicBoolean executed = new AtomicBoolean(false);
        Job job = new Job("1", "due-job", clock.instant().minusSeconds(1), () -> executed.set(true));
        JobExecution execution = scheduler.registerJob(job);

        scheduler.start();

        assertTrue(executed.get());
        assertEquals(JobStatus.COMPLETED, execution.getStatus());
        assertEquals(1, execution.getAttemptCount());
    }

    @Test
    void testRetryableFailureFollowedBySuccessReachesCompleted() {
        Clock clock = Clock.systemUTC();
        JobScheduler scheduler = new JobScheduler(clock);

        AtomicInteger attemptCounter = new AtomicInteger(0);
        Job job = new Job("1", "retry-success-job", clock.instant(), () -> {
            int attempt = attemptCounter.incrementAndGet();
            if (attempt < 3) {
                throw new TransientTestException("Transient error attempt " + attempt);
            }
        });

        FailureClassifier classifier = new DefaultFailureClassifier(Set.of(TransientTestException.class));
        BackoffStrategy backoff = new ExponentialBackoffStrategy(Duration.ofMillis(10));
        RetryPolicy policy = new RetryPolicy(3, classifier, backoff);

        JobExecution execution = scheduler.registerJob(job, policy);

        scheduler.start();

        assertEquals(JobStatus.COMPLETED, execution.getStatus());
        assertEquals(3, execution.getAttemptCount());
        assertEquals(3, attemptCounter.get());

        List<ExecutionAttempt> attempts = execution.getAttempts();
        assertEquals(AttemptOutcome.FAILURE, attempts.get(0).outcome());
        assertEquals(AttemptOutcome.FAILURE, attempts.get(1).outcome());
        assertEquals(AttemptOutcome.SUCCESS, attempts.get(2).outcome());
    }

    @Test
    void testPermanentFailureDoesNotRetry() {
        Clock clock = Clock.fixed(Instant.now(), ZoneId.of("UTC"));
        JobScheduler scheduler = new JobScheduler(clock);

        AtomicInteger attemptCounter = new AtomicInteger(0);
        Job job = new Job("1", "permanent-failure-job", clock.instant(), () -> {
            attemptCounter.incrementAndGet();
            throw new PermanentTestException("Fatal failure");
        });

        FailureClassifier classifier = new DefaultFailureClassifier(Set.of(TransientTestException.class));
        RetryPolicy policy = new RetryPolicy(3, classifier, new ExponentialBackoffStrategy(Duration.ofMillis(10)));

        JobExecution execution = scheduler.registerJob(job, policy);

        scheduler.start();

        assertEquals(JobStatus.FAILED, execution.getStatus());
        assertEquals(1, execution.getAttemptCount());
        assertEquals(1, attemptCounter.get());
    }

    @Test
    void testMaxAttemptsIsRespectedAndExhaustedRetriesEndInFailed() {
        Clock clock = Clock.systemUTC();
        JobScheduler scheduler = new JobScheduler(clock);

        AtomicInteger attemptCounter = new AtomicInteger(0);
        Job job = new Job("1", "exhausted-job", clock.instant(), () -> {
            attemptCounter.incrementAndGet();
            throw new TransientTestException("Transient error that persists");
        });

        FailureClassifier classifier = new DefaultFailureClassifier(Set.of(TransientTestException.class));
        RetryPolicy policy = new RetryPolicy(3, classifier, new ExponentialBackoffStrategy(Duration.ofMillis(10)));

        JobExecution execution = scheduler.registerJob(job, policy);

        scheduler.start();

        assertEquals(JobStatus.FAILED, execution.getStatus());
        assertEquals(3, execution.getAttemptCount());
        assertEquals(3, attemptCounter.get());
        assertEquals("Transient error that persists", execution.getFailure().getMessage());
    }

    @Test
    void testCancellationDuringRetryWaitingPreventsExecution() throws InterruptedException {
        Clock clock = Clock.systemUTC();
        JobScheduler scheduler = new JobScheduler(clock);

        AtomicInteger attemptCounter = new AtomicInteger(0);
        Job job = new Job("1", "cancellation-retry-job", clock.instant(), () -> {
            attemptCounter.incrementAndGet();
            throw new TransientTestException("Fail attempt 1");
        });

        FailureClassifier classifier = new DefaultFailureClassifier(Set.of(TransientTestException.class));
        // Backoff delay of 500ms allows cancellation window while waiting for Attempt 2
        RetryPolicy policy = new RetryPolicy(3, classifier, new ExponentialBackoffStrategy(Duration.ofMillis(500)));

        JobExecution execution = scheduler.registerJob(job, policy);

        Thread schedulerThread = new Thread(scheduler::start);
        schedulerThread.start();

        // Wait for attempt 1 to fail and schedule retry
        Thread.sleep(100);

        // Cancel job during retry backoff wait
        boolean cancelled = scheduler.cancelJob("1");
        assertTrue(cancelled);
        assertEquals(JobStatus.CANCELLED, execution.getStatus());

        schedulerThread.join(3000);

        assertEquals(1, attemptCounter.get(), "Attempt 2 must never execute after cancellation");
        assertEquals(JobStatus.CANCELLED, execution.getStatus());
    }

    @Test
    void testIndependentJobsMaintainIndependentRetryState() {
        Clock clock = Clock.systemUTC();
        JobScheduler scheduler = new JobScheduler(clock);

        AtomicInteger job1Attempts = new AtomicInteger(0);
        AtomicInteger job2Attempts = new AtomicInteger(0);

        Job job1 = new Job("1", "job-1-transient", clock.instant(), () -> {
            if (job1Attempts.incrementAndGet() < 2) {
                throw new TransientTestException("Job 1 fail");
            }
        });

        Job job2 = new Job("2", "job-2-permanent", clock.instant(), () -> {
            job2Attempts.incrementAndGet();
            throw new PermanentTestException("Job 2 fail");
        });

        FailureClassifier classifier = new DefaultFailureClassifier(Set.of(TransientTestException.class));
        RetryPolicy policy = new RetryPolicy(3, classifier, new ExponentialBackoffStrategy(Duration.ofMillis(10)));

        JobExecution exec1 = scheduler.registerJob(job1, policy);
        JobExecution exec2 = scheduler.registerJob(job2, policy);

        scheduler.start();

        assertEquals(JobStatus.COMPLETED, exec1.getStatus());
        assertEquals(2, exec1.getAttemptCount());

        assertEquals(JobStatus.FAILED, exec2.getStatus());
        assertEquals(1, exec2.getAttemptCount());
    }

    @Test
    void testV2ConcurrentExecutionRegression() throws InterruptedException {
        Clock clock = Clock.fixed(Instant.now(), ZoneId.of("UTC"));
        JobExecutor executor = new JobExecutor(2, clock);
        JobScheduler scheduler = new JobScheduler(clock, executor);

        CountDownLatch startLatch = new CountDownLatch(2);
        CountDownLatch releaseLatch = new CountDownLatch(1);

        Instant now = clock.instant();
        Job job1 = new Job("1", "concurrent-1", now, () -> {
            startLatch.countDown();
            try {
                releaseLatch.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Job job2 = new Job("2", "concurrent-2", now, () -> {
            startLatch.countDown();
            try {
                releaseLatch.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        JobExecution exec1 = scheduler.registerJob(job1);
        JobExecution exec2 = scheduler.registerJob(job2);

        Thread schedulerThread = new Thread(scheduler::start);
        schedulerThread.start();

        try {
            boolean bothStarted = startLatch.await(2, TimeUnit.SECONDS);
            assertTrue(bothStarted);
            assertEquals(JobStatus.RUNNING, exec1.getStatus());
            assertEquals(JobStatus.RUNNING, exec2.getStatus());
        } finally {
            releaseLatch.countDown();
            schedulerThread.join(3000);
        }

        assertEquals(JobStatus.COMPLETED, exec1.getStatus());
        assertEquals(JobStatus.COMPLETED, exec2.getStatus());
    }
}
