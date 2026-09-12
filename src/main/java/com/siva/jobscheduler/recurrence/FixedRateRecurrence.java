package com.siva.jobscheduler.recurrence;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Concrete implementation of RecurrencePolicy for fixed-rate interval scheduling.
 * Calculates next occurrence times based on previous scheduled times and coalesces missed occurrences.
 */
public record FixedRateRecurrence(
        Duration interval,
        OptionalInt maxOccurrences,
        Optional<Instant> endTime
) implements RecurrencePolicy {

    public FixedRateRecurrence {
        Objects.requireNonNull(interval, "Interval cannot be null");
        if (interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("Interval must be strictly positive");
        }
        maxOccurrences = maxOccurrences != null ? maxOccurrences : OptionalInt.empty();
        endTime = endTime != null ? endTime : Optional.empty();
    }

    public FixedRateRecurrence(Duration interval) {
        this(interval, OptionalInt.empty(), Optional.empty());
    }

    public FixedRateRecurrence(Duration interval, int maxOccurrences) {
        this(interval, OptionalInt.of(maxOccurrences), Optional.empty());
    }

    public FixedRateRecurrence(Duration interval, Instant endTime) {
        this(interval, OptionalInt.empty(), Optional.ofNullable(endTime));
    }

    @Override
    public boolean hasNextOccurrence(int completedOccurrences, Instant nextScheduledTime) {
        if (maxOccurrences.isPresent() && completedOccurrences >= maxOccurrences.getAsInt()) {
            return false;
        }
        if (endTime.isPresent() && !nextScheduledTime.isBefore(endTime.get())) {
            return false;
        }
        return true;
    }

    @Override
    public Instant calculateNextOccurrence(Instant lastScheduledTime, Instant now) {
        Objects.requireNonNull(lastScheduledTime, "lastScheduledTime cannot be null");
        Objects.requireNonNull(now, "now cannot be null");

        Instant next = lastScheduledTime.plus(interval);
        // Coalesce missed occurrences: step forward until next >= now
        while (next.isBefore(now)) {
            next = next.plus(interval);
        }
        return next;
    }
}
