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
    void testDependencyChainExecutionOrder() {
        Clock clock = Clock.fixed(Instant.now(), ZoneId.of("UTC"));
        JobScheduler scheduler = new JobScheduler(clock);

        List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());
        Instant now = clock.instant();

        Job jobA = new Job("A", "job-a", now, () -> executionOrder.add("A"));
        Job jobB = new Job("B", "job-b", now, () -> executionOrder.add("B"), Set.of("A"));
        Job jobC = new Job("C", "job-c", now, () -> executionOrder.add("C"), Set.of("B"));

        JobExecution execA = scheduler.registerJob(jobA);
        JobExecution execB = scheduler.registerJob(jobB);
        JobExecution execC = scheduler.registerJob(jobC);

        assertEquals(JobStatus.SCHEDULED, execA.getStatus());
        assertEquals(JobStatus.BLOCKED, execB.getStatus());
        assertEquals(JobStatus.BLOCKED, execC.getStatus());

        scheduler.start();

        assertEquals(List.of("A", "B", "C"), executionOrder);
        assertEquals(JobStatus.COMPLETED, execA.getStatus());
        assertEquals(JobStatus.COMPLETED, execB.getStatus());
        assertEquals(JobStatus.COMPLETED, execC.getStatus());
    }

    @Test
    void testFailedDependencyKeepsDependentBlocked() {
        Clock clock = Clock.fixed(Instant.now(), ZoneId.of("UTC"));
        JobScheduler scheduler = new JobScheduler(clock);

        Instant now = clock.instant();
        Job jobA = new Job("A", "job-a-failing", now, () -> {
            throw new PermanentTestException("A failed");
        });
        Job jobB = new Job("B", "job-b-dependent", now, () -> {}, Set.of("A"));

        JobExecution execA = scheduler.registerJob(jobA);
        JobExecution execB = scheduler.registerJob(jobB);

        scheduler.start();

        assertEquals(JobStatus.FAILED, execA.getStatus());
        assertEquals(JobStatus.BLOCKED, execB.getStatus(), "Dependent job B must remain BLOCKED when A fails permanently");
    }

    @Test
    void testFailurePolicyContinueDoesNotOverrideDependency() {
        Clock clock = Clock.fixed(Instant.now(), ZoneId.of("UTC"));
        JobScheduler scheduler = new JobScheduler(clock);

        List<String> executionLog = Collections.synchronizedList(new ArrayList<>());
        Instant now = clock.instant();

        // Job A fails with FailurePolicy.CONTINUE
        Job jobA = new Job("A", "job-a-fail", now, () -> {
            throw new PermanentTestException("A failed");
        }, FailurePolicy.CONTINUE);

        // Job B depends on A
        Job jobB = new Job("B", "job-b-dep", now, () -> executionLog.add("B"), Set.of("A"));

        // Job C is independent
        Job jobC = new Job("C", "job-c-indep", now, () -> executionLog.add("C"));

        scheduler.registerJob(jobA);
        JobExecution execB = scheduler.registerJob(jobB);
        JobExecution execC = scheduler.registerJob(jobC);

        scheduler.start();

        assertEquals(JobStatus.BLOCKED, execB.getStatus(), "Job B must remain BLOCKED despite FailurePolicy.CONTINUE");
        assertEquals(JobStatus.COMPLETED, execC.getStatus(), "Independent Job C is allowed to complete");
        assertEquals(List.of("C"), executionLog);
    }

    @Test
    void testRetryableDependencyKeepsDependentBlockedUntilSuccess() {
        Clock clock = Clock.systemUTC();
        JobScheduler scheduler = new JobScheduler(clock);

        AtomicInteger attemptCounter = new AtomicInteger(0);
        List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());
        Instant now = clock.instant();

        Job jobA = new Job("A", "job-a-transient", now, () -> {
            int attempt = attemptCounter.incrementAndGet();
            if (attempt < 3) {
                throw new TransientTestException("Transient error attempt " + attempt);
            }
            executionOrder.add("A");
        });

        Job jobB = new Job("B", "job-b-dependent", now, () -> executionOrder.add("B"), Set.of("A"));

        FailureClassifier classifier = new DefaultFailureClassifier(Set.of(TransientTestException.class));
        RetryPolicy policy = new RetryPolicy(3, classifier, new ExponentialBackoffStrategy(Duration.ofMillis(10)));

        JobExecution execA = scheduler.registerJob(jobA, policy);
        JobExecution execB = scheduler.registerJob(jobB);

        scheduler.start();

        assertEquals(JobStatus.COMPLETED, execA.getStatus());
        assertEquals(3, execA.getAttemptCount());
        assertEquals(JobStatus.COMPLETED, execB.getStatus());
        assertEquals(List.of("A", "B"), executionOrder);
    }

    @Test
    void testHaltSchedulerPolicyHaltsNewDispatch() throws InterruptedException {
        Clock clock = Clock.systemUTC();
        JobExecutor executor = new JobExecutor(2, clock);
        JobScheduler scheduler = new JobScheduler(clock, executor);

        CountDownLatch criticalJobLatch = new CountDownLatch(1);
        Instant now = clock.instant();

        // Job A: Critical job with FailurePolicy.HALT_SCHEDULER
        Job jobA = new Job("A", "critical-job", now, () -> {
            criticalJobLatch.countDown();
            throw new PermanentTestException("Critical failure");
        }, FailurePolicy.HALT_SCHEDULER);

        // Job B: Independent job registered after A
        AtomicBoolean jobBExecuted = new AtomicBoolean(false);
        Job jobB = new Job("B", "queued-job", now.plusMillis(100), () -> jobBExecuted.set(true));

        scheduler.registerJob(jobA);
        JobExecution execB = scheduler.registerJob(jobB);

        Thread schedulerThread = new Thread(scheduler::start);
        schedulerThread.start();

        assertTrue(criticalJobLatch.await(2, TimeUnit.SECONDS));
        Thread.sleep(150); // Wait for scheduler state transition to HALTED

        assertEquals(SchedulerState.HALTED, scheduler.getSchedulerState());
        assertFalse(jobBExecuted.get(), "Halted scheduler must not dispatch pending jobs");
        assertEquals(JobStatus.SCHEDULED, execB.getStatus());

        // Resume scheduler
        boolean resumed = scheduler.resume();
        assertTrue(resumed);
        assertEquals(SchedulerState.RUNNING, scheduler.getSchedulerState());

        schedulerThread.join(3000);

        assertTrue(jobBExecuted.get(), "Job B should execute after scheduler resumption");
        assertEquals(JobStatus.COMPLETED, execB.getStatus());
    }

    @Test
    void testCancelledBlockedJobIsNeverRescheduledWhenDependencyCompletes() {
        Clock clock = Clock.fixed(Instant.now(), ZoneId.of("UTC"));
        JobScheduler scheduler = new JobScheduler(clock);

        AtomicBoolean jobBExecuted = new AtomicBoolean(false);
        Instant now = clock.instant();

        Job jobA = new Job("A", "job-a", now, () -> {});
        Job jobB = new Job("B", "job-b", now, () -> jobBExecuted.set(true), Set.of("A"));

        scheduler.registerJob(jobA);
        JobExecution execB = scheduler.registerJob(jobB);

        assertEquals(JobStatus.BLOCKED, execB.getStatus());

        // Cancel B while blocked
        boolean cancelled = scheduler.cancelJob("B");
        assertTrue(cancelled);
        assertEquals(JobStatus.CANCELLED, execB.getStatus());

        scheduler.start();

        assertFalse(jobBExecuted.get(), "Cancelled blocked job must never execute when dependency completes");
        assertEquals(JobStatus.CANCELLED, execB.getStatus());
    }
}
