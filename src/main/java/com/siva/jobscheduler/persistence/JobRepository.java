package com.siva.jobscheduler.persistence;

import com.siva.jobscheduler.domain.Job;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for managing Job definition records.
 */
public interface JobRepository {
    void save(Job job);
    Optional<Job> findById(String jobId);
    List<Job> findAll();
    boolean deleteById(String jobId);
}
