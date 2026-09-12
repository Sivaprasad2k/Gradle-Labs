package com.siva.jobscheduler.persistence;

import com.siva.jobscheduler.recurrence.RecurrenceState;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for managing RecurrenceState metadata per job.
 */
public interface RecurrenceRepository {
    void save(RecurrenceState recurrenceState);
    Optional<RecurrenceState> findByJobId(String jobId);
    List<RecurrenceState> findAll();
}
