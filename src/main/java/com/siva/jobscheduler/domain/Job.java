package com.siva.jobscheduler.domain;

import com.siva.jobscheduler.recurrence.RecurrencePolicy;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a job to be scheduled and executed.
 * Implements Comparable to allow ordering by scheduledAt time in a PriorityQueue.
 * Supports persistent taskType/taskPayload, dependency IDs, FailurePolicy, and RecurrencePolicy.
 */
public record Job(
        String id,
        String name,
        Instant scheduledAt,
        JobTask task,
        String taskType,
        Map<String, Object> taskPayload,
        Set<String> dependencyIds,
        FailurePolicy failurePolicy,
        RecurrencePolicy recurrencePolicy
) implements Comparable<Job> {

    public Job(String id, String name, Instant scheduledAt, JobTask task) {
        this(id, name, scheduledAt, task, "IN_MEMORY", Collections.emptyMap(), Collections.emptySet(), FailurePolicy.CONTINUE, RecurrencePolicy.none());
    }

    public Job(String id, String name, Instant scheduledAt, JobTask task, Set<String> dependencyIds) {
        this(id, name, scheduledAt, task, "IN_MEMORY", Collections.emptyMap(), dependencyIds, FailurePolicy.CONTINUE, RecurrencePolicy.none());
    }

    public Job(String id, String name, Instant scheduledAt, JobTask task, FailurePolicy failurePolicy) {
        this(id, name, scheduledAt, task, "IN_MEMORY", Collections.emptyMap(), Collections.emptySet(), failurePolicy, RecurrencePolicy.none());
    }

    public Job(String id, String name, Instant scheduledAt, JobTask task, RecurrencePolicy recurrencePolicy) {
        this(id, name, scheduledAt, task, "IN_MEMORY", Collections.emptyMap(), Collections.emptySet(), FailurePolicy.CONTINUE, recurrencePolicy);
    }

    public Job(String id, String name, Instant scheduledAt, JobTask task, Set<String> dependencyIds, FailurePolicy failurePolicy) {
        this(id, name, scheduledAt, task, "IN_MEMORY", Collections.emptyMap(), dependencyIds, failurePolicy, RecurrencePolicy.none());
    }

    public Job(String id, String name, Instant scheduledAt, JobTask task, Set<String> dependencyIds, FailurePolicy failurePolicy, RecurrencePolicy recurrencePolicy) {
        this(id, name, scheduledAt, task, "IN_MEMORY", Collections.emptyMap(), dependencyIds, failurePolicy, recurrencePolicy);
    }

    public Job {
        Objects.requireNonNull(id, "Job id cannot be null");
        Objects.requireNonNull(name, "Job name cannot be null");
        Objects.requireNonNull(scheduledAt, "Job scheduledAt cannot be null");
        Objects.requireNonNull(task, "Job task cannot be null");
        taskType = taskType != null ? taskType : "IN_MEMORY";
        taskPayload = taskPayload != null ? Map.copyOf(taskPayload) : Collections.emptyMap();
        dependencyIds = dependencyIds != null ? Set.copyOf(dependencyIds) : Collections.emptySet();
        failurePolicy = failurePolicy != null ? failurePolicy : FailurePolicy.CONTINUE;
        recurrencePolicy = recurrencePolicy != null ? recurrencePolicy : RecurrencePolicy.none();
    }

    public boolean isRecurring() {
        return recurrencePolicy != null && !(recurrencePolicy instanceof RecurrencePolicy.NoneRecurrence);
    }

    @Override
    public int compareTo(Job other) {
        return this.scheduledAt.compareTo(other.scheduledAt);
    }
}
