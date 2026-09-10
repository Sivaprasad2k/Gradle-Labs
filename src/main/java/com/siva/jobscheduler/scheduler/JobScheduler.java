package com.siva.jobscheduler.scheduler;

import com.siva.jobscheduler.domain.Job;
import java.time.Clock;
import java.time.Instant;
import java.util.PriorityQueue;

/**
 * Manages the registration and execution of jobs.
 * Jobs are ordered by their scheduled execution time.
 * Execution is sequential.
 */
public class JobScheduler {
    private final PriorityQueue<Job> queue;
    private final Clock clock;

    public JobScheduler(Clock clock) {
        this.queue = new PriorityQueue<>();
        this.clock = clock;
    }

    public void registerJob(Job job) {
        queue.offer(job);
    }

    public void start() {
        System.out.println("Scheduler started.\n");
        while (!queue.isEmpty()) {
            Job nextJob = queue.peek();
            Instant now = clock.instant();

            if (!nextJob.scheduledAt().isAfter(now)) {
                // Job is due
                queue.poll();
                executeJob(nextJob, now);
            } else {
                // Wait until the job is due
                long delayMillis = nextJob.scheduledAt().toEpochMilli() - now.toEpochMilli();
                if (delayMillis > 0) {
                    try {
                        Thread.sleep(delayMillis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Scheduler interrupted", e);
                    }
                }
            }
        }
        System.out.println("\nAll jobs completed.");
        System.out.println("Scheduler stopped.");
    }

    private void executeJob(Job job, Instant startTime) {
        System.out.printf("[%s] STARTED %s%n", startTime, job.name());
        try {
            job.task().execute();
        } catch (Exception e) {
            System.out.printf("[%s] ERROR executing %s: %s%n", clock.instant(), job.name(), e.getMessage());
        }
        System.out.printf("[%s] COMPLETED %s%n", clock.instant(), job.name());
    }
}
