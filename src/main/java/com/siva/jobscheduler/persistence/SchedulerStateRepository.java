package com.siva.jobscheduler.persistence;

import com.siva.jobscheduler.domain.SchedulerState;

import java.util.Optional;

/**
 * Persistence contract for managing singleton global SchedulerState.
 */
public interface SchedulerStateRepository {
    void save(SchedulerState state);
    Optional<SchedulerState> load();
}
