package com.siva.jobscheduler.execution;

import com.siva.jobscheduler.domain.Job;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class JobExecutorTest {

    private JobExecutor executor;

    @AfterEach
    void tearDown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }

    @Test
    void testExecutorSubmitsAndExecutesJob() throws InterruptedException {
        executor = new JobExecutor(2);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean executed = new AtomicBoolean(false);

        Job job = new Job("1", "test-executor-job", Instant.now(), () -> {
            executed.set(true);
            latch.countDown();
        });

        executor.submit(job);
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Job should execute within timeout");
        assertTrue(executed.get());
    }

    @Test
    void testExecutorHandlesTaskExceptionGracefully() throws InterruptedException {
        executor = new JobExecutor(1);
        CountDownLatch latch = new CountDownLatch(2);
        AtomicBoolean secondExecuted = new AtomicBoolean(false);

        Job failingJob = new Job("1", "failing-job", Instant.now(), () -> {
            latch.countDown();
            throw new RuntimeException("Task failed intentionally");
        });

        Job succeedingJob = new Job("2", "succeeding-job", Instant.now(), () -> {
            secondExecuted.set(true);
            latch.countDown();
        });

        executor.submit(failingJob);
        executor.submit(succeedingJob);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Subsequent job should execute despite previous failure");
        assertTrue(secondExecuted.get());
    }

    @Test
    void testExecutorShutdownGracefully() throws InterruptedException {
        executor = new JobExecutor(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(1);

        Job job = new Job("1", "slow-job", Instant.now(), () -> {
            startLatch.countDown();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            finishLatch.countDown();
        });

        executor.submit(job);
        assertTrue(startLatch.await(2, TimeUnit.SECONDS));

        executor.shutdown();
        assertTrue(executor.isShutdown());
        assertTrue(finishLatch.await(2, TimeUnit.SECONDS), "Submitted job should finish before shutdown completes");
        assertTrue(executor.isTerminated());
    }

    @Test
    void testConstructorValidatesWorkerCount() {
        assertThrows(IllegalArgumentException.class, () -> new JobExecutor(0));
        assertThrows(IllegalArgumentException.class, () -> new JobExecutor(-1));
    }
}
