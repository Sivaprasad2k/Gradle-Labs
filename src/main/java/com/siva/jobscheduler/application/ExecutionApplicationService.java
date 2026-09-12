package com.siva.jobscheduler.application;

import com.siva.jobscheduler.domain.JobExecution;
import com.siva.jobscheduler.dto.ExecutionResponse;
import com.siva.jobscheduler.dto.PageResponse;
import com.siva.jobscheduler.persistence.ExecutionRepository;
import com.siva.jobscheduler.scheduler.JobScheduler;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ExecutionApplicationService {
    private final JobScheduler scheduler;
    private final ExecutionRepository executionRepository;

    public ExecutionApplicationService(JobScheduler scheduler, ExecutionRepository executionRepository) {
        this.scheduler = Objects.requireNonNull(scheduler, "JobScheduler cannot be null");
        this.executionRepository = executionRepository;
    }

    public PageResponse<ExecutionResponse> listExecutions(String statusFilter, String jobIdFilter, int page, int size) {
        List<JobExecution> allExecutions = Collections.emptyList();
        if (executionRepository != null) {
            if (jobIdFilter != null && !jobIdFilter.isBlank()) {
                allExecutions = executionRepository.findByJobId(jobIdFilter);
            } else {
                allExecutions = executionRepository.findAll();
            }
        }

        if (allExecutions.isEmpty()) {
            allExecutions = scheduler.getAllExecutions();
            if (jobIdFilter != null && !jobIdFilter.isBlank()) {
                allExecutions = allExecutions.stream()
                        .filter(e -> e.getJob() != null && jobIdFilter.equals(e.getJob().id()))
                        .toList();
            }
        }

        if (statusFilter != null && !statusFilter.isBlank()) {
            allExecutions = allExecutions.stream()
                    .filter(e -> e.getStatus().name().equalsIgnoreCase(statusFilter.trim()))
                    .toList();
        }

        List<ExecutionResponse> responseList = allExecutions.stream()
                .map(ExecutionResponse::fromDomain)
                .toList();

        return PageResponse.of(responseList, page, size);
    }

    public Optional<ExecutionResponse> getExecution(String executionId) {
        if (executionId == null) return Optional.empty();

        if (executionRepository != null) {
            Optional<JobExecution> loaded = executionRepository.findByExecutionId(executionId);
            if (loaded.isPresent()) {
                return loaded.map(ExecutionResponse::fromDomain);
            }
        }

        JobExecution fromMem = scheduler.getExecutionByExecutionId(executionId);
        if (fromMem != null) {
            return Optional.of(ExecutionResponse.fromDomain(fromMem));
        }

        return Optional.empty();
    }

    public List<ExecutionResponse> getJobExecutions(String jobId) {
        if (jobId == null) return Collections.emptyList();

        if (executionRepository != null) {
            List<JobExecution> list = executionRepository.findByJobId(jobId);
            if (!list.isEmpty()) {
                return list.stream().map(ExecutionResponse::fromDomain).toList();
            }
        }
        return Collections.emptyList();
    }
}
