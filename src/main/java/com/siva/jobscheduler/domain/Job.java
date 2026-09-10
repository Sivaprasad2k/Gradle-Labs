package com.siva.jobscheduler.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a job to be scheduled and executed.
 * Implements Comparable to allow ordering by scheduledAt time in a PriorityQueue.
 */
public record Job(
        String id,
        String name,
        Instant scheduledAt,
        JobTask task
) implements Comparable<Job> {

    public Job {
        Objects.requireNonNull(id, "Job id cannot be null");
        Objects.requireNonNull(name, "Job name cannot be null");
        Objects.requireNonNull(scheduledAt, "Job scheduledAt cannot be null");
        Objects.requireNonNull(task, "Job task cannot be null");
    }

    @Override
    public int compareTo(Job other) {
        return this.scheduledAt.compareTo(other.scheduledAt);
    }
}
