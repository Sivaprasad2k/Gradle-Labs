package com.siva.jobscheduler.application;

import com.siva.jobscheduler.dependency.DependencyGraph;
import com.siva.jobscheduler.domain.Job;
import com.siva.jobscheduler.domain.JobExecution;
import com.siva.jobscheduler.domain.JobStatus;
import com.siva.jobscheduler.dto.WorkflowResponse;
import com.siva.jobscheduler.dto.WorkflowResponse.WorkflowNodeDto;
import com.siva.jobscheduler.persistence.JobRepository;
import com.siva.jobscheduler.scheduler.JobScheduler;

import java.util.*;

public class WorkflowApplicationService {
    private final JobScheduler scheduler;
    private final JobRepository jobRepository;
    private final DependencyGraph dependencyGraph;

    public WorkflowApplicationService(JobScheduler scheduler, JobRepository jobRepository, DependencyGraph dependencyGraph) {
        this.scheduler = Objects.requireNonNull(scheduler, "JobScheduler cannot be null");
        this.jobRepository = jobRepository;
        this.dependencyGraph = dependencyGraph != null ? dependencyGraph : new DependencyGraph();
    }

    public Optional<WorkflowResponse> getWorkflow(String jobId) {
        if (jobId == null) return Optional.empty();

        List<Job> allJobs = jobRepository != null ? jobRepository.findAll() : Collections.emptyList();
        Optional<Job> rootOpt = allJobs.stream().filter(j -> j.id().equals(jobId)).findFirst();
        if (rootOpt.isEmpty()) {
            JobExecution exec = scheduler.getExecution(jobId);
            if (exec != null) {
                rootOpt = Optional.of(exec.getJob());
            }
        }

        if (rootOpt.isEmpty()) {
            return Optional.empty();
        }

        List<WorkflowNodeDto> nodes = new ArrayList<>();
        int blockedCount = 0;
        int failedCount = 0;

        for (Job job : allJobs) {
            Set<String> deps = job.dependencyIds();
            Set<String> dependents = dependencyGraph.getDependents(job.id());

            JobExecution exec = scheduler.getExecution(job.id());
            String status = exec != null ? exec.getStatus().name() : JobStatus.SCHEDULED.name();

            if (JobStatus.BLOCKED.name().equals(status)) blockedCount++;
            if (JobStatus.FAILED.name().equals(status)) failedCount++;

            nodes.add(new WorkflowNodeDto(job.id(), job.name(), status, deps, dependents));
        }

        return Optional.of(new WorkflowResponse(jobId, nodes, nodes.size(), blockedCount, failedCount));
    }
}
