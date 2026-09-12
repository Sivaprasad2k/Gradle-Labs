package com.siva.jobscheduler.recurrence;

import com.siva.jobscheduler.domain.Job;

import java.time.Instant;
import java.util.Objects;

/**
 * Tracks runtime recurrence metadata for a recurring Job.
 * Encapsulates occurrence counts, last scheduled time, and schedule cancellation status.
 * Thread-safe for atomic queries and state updates.
 */
public class RecurrenceState {
    private final Job initialJob;
    private final RecurrencePolicy recurrencePolicy;
    private int occurrenceCount;
    private Instant lastScheduledTime;
    private volatile boolean scheduleCancelled;

    public RecurrenceState(Job initialJob, RecurrencePolicy recurrencePolicy) {
        this.initialJob = Objects.requireNonNull(initialJob, "Initial job cannot be null");
        this.recurrencePolicy = Objects.requireNonNull(recurrencePolicy, "RecurrencePolicy cannot be null");
        this.occurrenceCount = 1;
        this.lastScheduledTime = initialJob.scheduledAt();
        this.scheduleCancelled = false;
    }

    public Job getInitialJob() {
        return initialJob;
    }

    public RecurrencePolicy getRecurrencePolicy() {
        return recurrencePolicy;
    }

    public synchronized int getOccurrenceCount() {
        return occurrenceCount;
    }

    public synchronized Instant getLastScheduledTime() {
        return lastScheduledTime;
    }

    public boolean isScheduleCancelled() {
        return scheduleCancelled;
    }

    public void cancelSchedule() {
        this.scheduleCancelled = true;
    }

    public synchronized void recordOccurrence(Instant newScheduledTime) {
        this.occurrenceCount++;
        this.lastScheduledTime = Objects.requireNonNull(newScheduledTime, "Scheduled time cannot be null");
    }

    @Override
    public synchronized String toString() {
        return "RecurrenceState{" +
                "jobId='" + initialJob.id() + '\'' +
                ", occurrenceCount=" + occurrenceCount +
                ", lastScheduledTime=" + lastScheduledTime +
                ", scheduleCancelled=" + scheduleCancelled +
                '}';
    }
}
