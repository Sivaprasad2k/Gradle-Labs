package com.siva.jobscheduler.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a job to be scheduled and executed.
 * Implements Comparable to allow ordering by scheduledAt time in a PriorityQueue.
 * Supports optional dependency IDs and FailurePolicy for workflow management.
 */
public record Job(
        String id,
        String name,
        Instant scheduledAt,
        JobTask task,
        Set<String> dependencyIds,
        FailurePolicy failurePolicy
) implements Comparable<Job> {

    public Job(String id, String name, Instant scheduledAt, JobTask task) {
        this(id, name, scheduledAt, task, Collections.emptySet(), FailurePolicy.CONTINUE);
    }

    public Job(String id, String name, Instant scheduledAt, JobTask task, Set<String> dependencyIds) {
        this(id, name, scheduledAt, task, dependencyIds, FailurePolicy.CONTINUE);
    }

    public Job(String id, String name, Instant scheduledAt, JobTask task, FailurePolicy failurePolicy) {
        this(id, name, scheduledAt, task, Collections.emptySet(), failurePolicy);
    }

    public Job {
        Objects.requireNonNull(id, "Job id cannot be null");
        Objects.requireNonNull(name, "Job name cannot be null");
        Objects.requireNonNull(scheduledAt, "Job scheduledAt cannot be null");
        Objects.requireNonNull(task, "Job task cannot be null");
        dependencyIds = dependencyIds != null ? Set.copyOf(dependencyIds) : Collections.emptySet();
        failurePolicy = failurePolicy != null ? failurePolicy : FailurePolicy.CONTINUE;
    }

    @Override
    public int compareTo(Job other) {
        return this.scheduledAt.compareTo(other.scheduledAt);
    }
}
