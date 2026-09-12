package com.siva.jobscheduler;

import com.siva.jobscheduler.dependency.DependencyGraph;
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
        System.out.println(" Java Job Scheduler - Version 5");
        System.out.println(" Job Dependencies & Workflow Control");
        System.out.println("==================================================\n");

        Clock clock = Clock.systemUTC();
        JobExecutor executor = new JobExecutor(3, clock);
        JobScheduler scheduler = new JobScheduler(clock, executor);
        Instant now = clock.instant();

        // Failure Policy & Retry Policy setup
        FailureClassifier classifier = new DefaultFailureClassifier(Set.of(NetworkTimeoutException.class));
        BackoffStrategy backoff = new ExponentialBackoffStrategy(Duration.ofMillis(50), Duration.ofMillis(200));
        RetryPolicy retryPolicy = new RetryPolicy(3, classifier, backoff);

        // 1. Dependency Chain: A -> B -> C
        AtomicInteger jobAAttempts = new AtomicInteger(0);
        Job jobA = new Job("A", "job-a-prereq", now, () -> {
            int attempt = jobAAttempts.incrementAndGet();
            simulateWork(40);
            if (attempt < 2) {
                throw new NetworkTimeoutException("Transient failure on attempt 1");
            }
        });

        Job jobB = new Job("B", "job-b-step2", now, () -> simulateWork(40), Set.of("A"));
        Job jobC = new Job("C", "job-c-step3", now, () -> simulateWork(40), Set.of("B"));

        // 2. Permanent Failure Dependency: D (fails) -> E (remains BLOCKED), F (Independent)
        Job jobD = new Job("D", "job-d-failing", now, () -> {
            simulateWork(40);
            throw new InvalidDataException("Data validation error");
        }, FailurePolicy.CONTINUE);

        Job jobE = new Job("E", "job-e-dependent-on-d", now, () -> simulateWork(40), Set.of("D"));
        Job jobF = new Job("F", "job-f-independent", now, () -> simulateWork(40));

        System.out.println("Registering workflow jobs...");
        JobExecution execA = scheduler.registerJob(jobA, retryPolicy);
        JobExecution execB = scheduler.registerJob(jobB);
        JobExecution execC = scheduler.registerJob(jobC);

        JobExecution execD = scheduler.registerJob(jobD, retryPolicy);
        JobExecution execE = scheduler.registerJob(jobE);
        JobExecution execF = scheduler.registerJob(jobF);

        System.out.println("Registered job A: " + jobA.name() + " [Initial: " + execA.getStatus() + "]");
        System.out.println("Registered job B: " + jobB.name() + " [Initial: " + execB.getStatus() + ", Depends: A]");
        System.out.println("Registered job C: " + jobC.name() + " [Initial: " + execC.getStatus() + ", Depends: B]");
        System.out.println("Registered job D: " + jobD.name() + " [Initial: " + execD.getStatus() + "]");
        System.out.println("Registered job E: " + jobE.name() + " [Initial: " + execE.getStatus() + ", Depends: D]");
        System.out.println("Registered job F: " + jobF.name() + " [Initial: " + execF.getStatus() + "]");
        System.out.println();

        // Execute workflow
        scheduler.start();

        // Display Execution Summary
        List<JobExecution> executions = List.of(execA, execB, execC, execD, execE, execF);
        long completedCount = executions.stream().filter(e -> e.getStatus() == JobStatus.COMPLETED).count();
        long failedCount = executions.stream().filter(e -> e.getStatus() == JobStatus.FAILED).count();
        long blockedCount = executions.stream().filter(e -> e.getStatus() == JobStatus.BLOCKED).count();

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
        System.out.printf("Blocked   : %d%n", blockedCount);
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
