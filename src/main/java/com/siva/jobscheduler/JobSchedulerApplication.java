package com.siva.jobscheduler;

import com.siva.jobscheduler.domain.*;
import com.siva.jobscheduler.execution.JobExecutor;
import com.siva.jobscheduler.recurrence.FixedRateRecurrence;
import com.siva.jobscheduler.scheduler.JobScheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class JobSchedulerApplication {

    static class NetworkTimeoutException extends RuntimeException {
        public NetworkTimeoutException(String message) {
            super(message);
        }
    }

    static class InvalidDataException extends RuntimeException {
        public InvalidDataException(String message) {
            super(message);
        }
    }

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println(" Java Job Scheduler - Version 6");
        System.out.println(" Recurring Jobs & Fixed-Rate Scheduling");
        System.out.println("==================================================\n");

        Clock clock = Clock.systemUTC();
        JobExecutor executor = new JobExecutor(3, clock);
        JobScheduler scheduler = new JobScheduler(clock, executor);
        Instant now = clock.instant();

        // 1. Recurring Job R1: Executes 3 times at 50ms intervals
        AtomicInteger recurringCount = new AtomicInteger(0);
        FixedRateRecurrence recurrencePolicy = new FixedRateRecurrence(Duration.ofMillis(50), 3);
        Job jobR1 = new Job("R1", "recurring-heartbeat", now, () -> {
            int current = recurringCount.incrementAndGet();
            simulateWork(20);
            System.out.printf("   --> Heartbeat pulse #%d executed%n", current);
        }, recurrencePolicy);

        // 2. Dependency Chain: A -> B
        AtomicInteger jobAAttempts = new AtomicInteger(0);
        FailureClassifier classifier = new DefaultFailureClassifier(Set.of(NetworkTimeoutException.class));
        BackoffStrategy backoff = new ExponentialBackoffStrategy(Duration.ofMillis(20), Duration.ofMillis(100));
        RetryPolicy retryPolicy = new RetryPolicy(3, classifier, backoff);

        Job jobA = new Job("A", "job-a-prereq", now, () -> {
            int attempt = jobAAttempts.incrementAndGet();
            simulateWork(20);
            if (attempt < 2) {
                throw new NetworkTimeoutException("Transient failure on attempt 1");
            }
        });

        Job jobB = new Job("B", "job-b-step2", now, () -> simulateWork(20), Set.of("A"));

        System.out.println("Registering jobs...");
        JobExecution execR1 = scheduler.registerJob(jobR1);
        JobExecution execA = scheduler.registerJob(jobA, retryPolicy);
        JobExecution execB = scheduler.registerJob(jobB);

        System.out.println("Registered Job R1 (Recurring): " + jobR1.name() + " [Policy: 3 occurrences @ 50ms]");
        System.out.println("Registered Job A (Workflow Prereq): " + jobA.name() + " [Initial: " + execA.getStatus() + "]");
        System.out.println("Registered Job B (Dependent): " + jobB.name() + " [Initial: " + execB.getStatus() + ", Depends: A]");
        System.out.println();

        // Execute workflow & recurring schedules
        scheduler.start();

        System.out.println("\n==================================================");
        System.out.println(" Execution Summary");
        System.out.println("==================================================");
        List<JobExecution> executions = List.of(
                scheduler.getExecutionByExecutionId("R1#1"),
                scheduler.getExecutionByExecutionId("R1#2"),
                scheduler.getExecutionByExecutionId("R1#3"),
                scheduler.getExecutionByExecutionId("A#1"),
                scheduler.getExecutionByExecutionId("B#1")
        );

        for (JobExecution exec : executions) {
            if (exec != null) {
                String details = String.format("ExecutionID: %-8s | Attempts: %d | Status: %-10s",
                        exec.getExecutionId(), exec.getAttemptCount(), exec.getStatus());
                System.out.printf("%-25s %s%n", exec.getJob().name(), details);
            }
        }
        System.out.println("==================================================");
    }

    private static void simulateWork(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
