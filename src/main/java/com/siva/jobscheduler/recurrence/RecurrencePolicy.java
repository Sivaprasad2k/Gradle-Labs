package com.siva.jobscheduler.recurrence;

import java.time.Instant;

/**
 * Defines the contract for calculating job recurrence occurrences.
 */
public interface RecurrencePolicy {

    /**
     * Determines whether another recurrence occurrence is allowed.
     *
     * @param completedOccurrences the number of completed occurrences so far
     * @param nextScheduledTime    the calculated next scheduled time candidate
     * @return true if another occurrence is permitted; false otherwise
     */
    boolean hasNextOccurrence(int completedOccurrences, Instant nextScheduledTime);

    /**
     * Calculates the next scheduled occurrence time given the previous scheduled time and the current clock time.
     * Implements missed-occurrence coalescing if execution was delayed past scheduled intervals.
     *
     * @param lastScheduledTime the scheduled time of the previous occurrence
     * @param now               the current clock time
     * @return the calculated next scheduled occurrence time
     */
    Instant calculateNextOccurrence(Instant lastScheduledTime, Instant now);

    /**
     * Factory method for non-recurring jobs.
     */
    static RecurrencePolicy none() {
        return NoneRecurrence.INSTANCE;
    }

    enum NoneRecurrence implements RecurrencePolicy {
        INSTANCE;

        @Override
        public boolean hasNextOccurrence(int completedOccurrences, Instant nextScheduledTime) {
            return false;
        }

        @Override
        public Instant calculateNextOccurrence(Instant lastScheduledTime, Instant now) {
            return lastScheduledTime;
        }
    }
}
