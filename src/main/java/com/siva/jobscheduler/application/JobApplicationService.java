package com.siva.jobscheduler.application;

import com.siva.jobscheduler.domain.*;
import com.siva.jobscheduler.dto.CreateJobRequest;
import com.siva.jobscheduler.dto.JobResponse;
import com.siva.jobscheduler.persistence.JobRepository;
import com.siva.jobscheduler.recurrence.FixedRateRecurrence;
import com.siva.jobscheduler.recurrence.RecurrencePolicy;
import com.siva.jobscheduler.scheduler.JobScheduler;
import com.siva.jobscheduler.task.TaskRegistry;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class JobApplicationService {
    private final JobScheduler scheduler;
    private final JobRepository jobRepository;
    private final TaskRegistry taskRegistry;

    public JobApplicationService(JobScheduler scheduler, JobRepository jobRepository, TaskRegistry taskRegistry) {
        this.scheduler = Objects.requireNonNull(scheduler, "JobScheduler cannot be null");
        this.jobRepository = jobRepository;
        this.taskRegistry = taskRegistry != null ? taskRegistry : TaskRegistry.getInstance();
    }

    public JobResponse createJob(CreateJobRequest request) {
        Objects.requireNonNull(request, "Request cannot be null");
        if (request.id() == null || request.id().isBlank()) {
            throw new IllegalArgumentException("Job id cannot be blank");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Job name cannot be blank");
        }

        Instant scheduledAt = request.scheduledAt() != null && !request.scheduledAt().isBlank() ?
                Instant.parse(request.scheduledAt()) : Instant.now();

        String taskType = request.taskType() != null ? request.taskType() : "IN_MEMORY";
        Map<String, Object> taskPayload = request.taskPayload() != null ? request.taskPayload() : Collections.emptyMap();

        JobTask task = taskRegistry.resolveTask(taskType, taskPayload);

        Set<String> dependencyIds = request.dependencyIds() != null ? request.dependencyIds() : Collections.emptySet();

        FailurePolicy failurePolicy = FailurePolicy.CONTINUE;
        if (request.failurePolicy() != null) {
            try {
                failurePolicy = FailurePolicy.valueOf(request.failurePolicy().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid failurePolicy: " + request.failurePolicy());
            }
        }

        RecurrencePolicy recurrencePolicy = RecurrencePolicy.none();
        if (request.recurrence() != null && "FIXED_RATE".equalsIgnoreCase(request.recurrence().type())) {
            long intervalMs = request.recurrence().intervalMs();
            if (intervalMs <= 0) {
                throw new IllegalArgumentException("Recurrence intervalMs must be > 0");
            }

            Integer maxOccurrences = request.recurrence().maxOccurrences();
            Instant endTime = request.recurrence().endTime() != null ? Instant.parse(request.recurrence().endTime()) : null;

            if (maxOccurrences != null) {
                recurrencePolicy = new FixedRateRecurrence(Duration.ofMillis(intervalMs), maxOccurrences);
            } else if (endTime != null) {
                recurrencePolicy = new FixedRateRecurrence(Duration.ofMillis(intervalMs), endTime);
            } else {
                recurrencePolicy = new FixedRateRecurrence(Duration.ofMillis(intervalMs));
            }
        }

        Job job = new Job(request.id(), request.name(), scheduledAt, task, taskType, taskPayload, dependencyIds, failurePolicy, recurrencePolicy);

        JobExecution execution = scheduler.registerJob(job);
        return JobResponse.fromDomain(execution.getJob());
    }

    public Optional<JobResponse> getJob(String jobId) {
        if (jobId == null) return Optional.empty();

        if (jobRepository != null) {
            Optional<Job> fromDb = jobRepository.findById(jobId);
            if (fromDb.isPresent()) {
                return fromDb.map(JobResponse::fromDomain);
            }
        }

        JobExecution exec = scheduler.getExecution(jobId);
        if (exec != null) {
            return Optional.of(JobResponse.fromDomain(exec.getJob()));
        }

        return Optional.empty();
    }

    public List<JobResponse> listJobs() {
        if (jobRepository != null) {
            List<Job> all = jobRepository.findAll();
            if (!all.isEmpty()) {
                return all.stream().map(JobResponse::fromDomain).toList();
            }
        }
        return Collections.emptyList();
    }

    public boolean cancelJobExecution(String jobId) {
        return scheduler.cancelJob(jobId);
    }

    public boolean cancelJobRecurrence(String jobId) {
        return scheduler.cancelRecurrence(jobId);
    }
}
