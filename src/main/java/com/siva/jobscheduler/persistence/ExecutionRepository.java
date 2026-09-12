package com.siva.jobscheduler.persistence;

import com.siva.jobscheduler.domain.JobExecution;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for managing JobExecution occurrences and attempt history.
 */
public interface ExecutionRepository {
    void save(JobExecution execution);
    Optional<JobExecution> findByExecutionId(String executionId);
    List<JobExecution> findByJobId(String jobId);
    List<JobExecution> findActiveExecutions();
    List<JobExecution> findAll();
}
