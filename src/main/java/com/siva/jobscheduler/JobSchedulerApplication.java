package com.siva.jobscheduler;

import com.siva.jobscheduler.domain.Job;
import com.siva.jobscheduler.scheduler.JobScheduler;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class JobSchedulerApplication {
    public static void main(String[] args) {
        Clock clock = Clock.systemUTC();
        JobScheduler scheduler = new JobScheduler(clock);
        Instant now = clock.instant();

        Job job1 = new Job("1", "job-001", now.plus(1, ChronoUnit.SECONDS), () -> {
            // Simulating short work
        });
        Job job2 = new Job("2", "job-002", now, () -> {
            // Simulating short work
        });
        Job job3 = new Job("3", "job-003", now.plus(2, ChronoUnit.SECONDS), () -> {
            // Simulating short work
        });

        System.out.println("Registered job: " + job1.name());
        System.out.println("Registered job: " + job2.name());
        System.out.println("Registered job: " + job3.name());
        System.out.println();

        scheduler.registerJob(job1);
        scheduler.registerJob(job2);
        scheduler.registerJob(job3);

        scheduler.start();
    }
}
