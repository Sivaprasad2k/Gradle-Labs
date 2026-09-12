package com.siva.jobscheduler;

import com.siva.jobscheduler.domain.*;
import com.siva.jobscheduler.execution.JobExecutor;
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
        System.out.println(" Java Job Scheduler - Version 4");
        System.out.println(" Controlled Failure Recovery & Retry Engine");
        System.out.println("==================================================\n");

        Clock clock = Clock.systemUTC();
        JobExecutor executor = new JobExecutor(3, clock);
        JobScheduler scheduler = new JobScheduler(clock, executor);
        Instant now = clock.instant();

        // Failure Classifier: NetworkTimeoutException is TRANSIENT, InvalidDataException is PERMANENT
        FailureClassifier classifier = new DefaultFailureClassifier(Set.of(NetworkTimeoutException.class));
        BackoffStrategy backoff = new ExponentialBackoffStrategy(Duration.ofMillis(100), Duration.ofMillis(500));
        RetryPolicy retryPolicy = new RetryPolicy(3, classifier, backoff);

        // Job A: Succeeds immediately
        Job jobA = new Job("1", "job-a-immediate-success", now, () -> simulateWork(50));

        // Job B: Fails transiently on Attempts 1 & 2, succeeds on Attempt 3
        AtomicInteger jobBAttempts = new AtomicInteger(0);
        Job jobB = new Job("2", "job-b-transient-retry", now, () -> {
            int attempt = jobBAttempts.incrementAndGet();
            simulateWork(50);
            if (attempt < 3) {
                throw new NetworkTimeoutException("Connection timed out on attempt " + attempt);
            }
        });

        // Job C: Fails permanently on Attempt 1
        Job jobC = new Job("3", "job-c-permanent-failure", now, () -> {
            simulateWork(50);
            throw new InvalidDataException("Unrecoverable data validation error");
        });

        System.out.println("Registering jobs...");
        JobExecution execA = scheduler.registerJob(jobA, RetryPolicy.noRetry());
        System.out.println("Registered job: " + jobA.name() + " [Policy: noRetry]");

        JobExecution execB = scheduler.registerJob(jobB, retryPolicy);
        System.out.println("Registered job: " + jobB.name() + " [Policy: maxAttempts=3, transient=NetworkTimeoutException]");

        JobExecution execC = scheduler.registerJob(jobC, retryPolicy);
        System.out.println("Registered job: " + jobC.name() + " [Policy: maxAttempts=3, permanent=InvalidDataException]");
        System.out.println();

        // Start scheduler loop
        scheduler.start();

        // Display Execution Summary
        List<JobExecution> executions = List.of(execA, execB, execC);
        long completedCount = executions.stream().filter(e -> e.getStatus() == JobStatus.COMPLETED).count();
        long failedCount = executions.stream().filter(e -> e.getStatus() == JobStatus.FAILED).count();

        System.out.println("\n==================================================");
        System.out.println(" Execution Summary");
        System.out.println("==================================================");
        for (JobExecution exec : executions) {
            String details = String.format("Attempts: %d | Status: %-10s", exec.getAttemptCount(), exec.getStatus());
            if (exec.getStatus() == JobStatus.FAILED && exec.getFailure() != null) {
                details += " (Reason: " + exec.getFailure().getMessage() + ")";
            }
            System.out.printf("%-25s %s%n", exec.getJob().name(), details);
        }
        System.out.println("--------------------------------------------------");
        System.out.printf("Completed : %d%n", completedCount);
        System.out.printf("Failed    : %d%n", failedCount);
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
