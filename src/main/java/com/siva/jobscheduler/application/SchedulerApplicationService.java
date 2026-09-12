package com.siva.jobscheduler.application;

import com.siva.jobscheduler.domain.Job;
import com.siva.jobscheduler.domain.JobExecution;
import com.siva.jobscheduler.domain.JobStatus;
import com.siva.jobscheduler.domain.SchedulerState;
import com.siva.jobscheduler.dto.SchedulerStatusResponse;
import com.siva.jobscheduler.execution.JobExecutor;
import com.siva.jobscheduler.persistence.ExecutionRepository;
import com.siva.jobscheduler.persistence.JobRepository;
import com.siva.jobscheduler.scheduler.JobScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class SchedulerApplicationService {
    private final JobScheduler scheduler;
    private final JobExecutor jobExecutor;
    private final JobRepository jobRepository;
    private final ExecutionRepository executionRepository;
    private final Instant startTime;

    public SchedulerApplicationService(JobScheduler scheduler, JobExecutor jobExecutor,
                                       JobRepository jobRepository, ExecutionRepository executionRepository) {
        this.scheduler = Objects.requireNonNull(scheduler, "JobScheduler cannot be null");
        this.jobExecutor = jobExecutor;
        this.jobRepository = jobRepository;
        this.executionRepository = executionRepository;
        this.startTime = Instant.now();
    }

    public SchedulerStatusResponse getStatus() {
        SchedulerState state = scheduler.getSchedulerState();
        
        List<Job> allJobs = jobRepository != null ? jobRepository.findAll() : List.of();
        int totalJobs = !allJobs.isEmpty() ? allJobs.size() :
                (int) scheduler.getAllExecutions().stream().map(e -> e.getJob().id()).distinct().count();

        List<JobExecution> allExecutions = executionRepository != null ? executionRepository.findAll() : List.of();
        if (allExecutions.isEmpty()) {
            allExecutions = scheduler.getAllExecutions();
        }

        int running = (int) allExecutions.stream().filter(e -> e.getStatus() == JobStatus.RUNNING).count();
        int scheduled = (int) allExecutions.stream().filter(e -> e.getStatus() == JobStatus.SCHEDULED).count();
        int blocked = (int) allExecutions.stream().filter(e -> e.getStatus() == JobStatus.BLOCKED).count();
        int failed = (int) allExecutions.stream().filter(e -> e.getStatus() == JobStatus.FAILED).count();
        int completed = (int) allExecutions.stream().filter(e -> e.getStatus() == JobStatus.COMPLETED).count();

        int activeThreads = jobExecutor != null ? jobExecutor.getWorkerCount() : 1;
        int totalThreads = jobExecutor != null ? jobExecutor.getWorkerCount() : 1;

        Duration uptimeDur = Duration.between(startTime, Instant.now());
        String uptime = String.format("%dd %dh %dm", uptimeDur.toDaysPart(), uptimeDur.toHoursPart(), uptimeDur.toMinutesPart());

        return new SchedulerStatusResponse(
                state.name(),
                totalJobs,
                running + scheduled + blocked,
                running,
                scheduled,
                blocked,
                failed,
                completed,
                activeThreads,
                totalThreads,
                scheduled + blocked,
                "Connected",
                "job_scheduler_db",
                "v8.0.0",
                "Development",
                uptime
        );
    }

    public boolean resume() {
        return scheduler.resume();
    }

    public boolean halt() {
        return scheduler.halt();
    }

    public boolean shutdown() {
        return scheduler.shutdown();
    }
}
