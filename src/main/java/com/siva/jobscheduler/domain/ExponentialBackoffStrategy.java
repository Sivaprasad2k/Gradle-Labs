package com.siva.jobscheduler.domain;

import java.time.Duration;
import java.util.Objects;

/**
 * Calculates exponential backoff delay: delay = initialBackoff * 2^(attemptNumber - 1).
 * Clamps maximum delay to maxBackoff and protects against duration overflow.
 */
public class ExponentialBackoffStrategy implements BackoffStrategy {
    private final Duration initialBackoff;
    private final Duration maxBackoff;

    public ExponentialBackoffStrategy(Duration initialBackoff, Duration maxBackoff) {
        this.initialBackoff = Objects.requireNonNull(initialBackoff, "initialBackoff cannot be null");
        this.maxBackoff = Objects.requireNonNull(maxBackoff, "maxBackoff cannot be null");
        if (initialBackoff.isNegative() || maxBackoff.isNegative()) {
            throw new IllegalArgumentException("Backoff durations cannot be negative");
        }
        if (initialBackoff.compareTo(maxBackoff) > 0) {
            throw new IllegalArgumentException("initialBackoff cannot be greater than maxBackoff");
        }
    }

    public ExponentialBackoffStrategy(Duration initialBackoff) {
        this(initialBackoff, Duration.ofSeconds(300)); // Default max backoff 5 minutes
    }

    @Override
    public Duration calculateBackoff(int attemptNumber) {
        if (attemptNumber <= 0) {
            throw new IllegalArgumentException("Attempt number must be greater than zero");
        }

        int shift = Math.min(attemptNumber - 1, 30); // Prevent integer overflow in 2^shift
        long multiplier = 1L << shift;

        try {
            Duration calculated = initialBackoff.multipliedBy(multiplier);
            if (calculated.compareTo(maxBackoff) > 0) {
                return maxBackoff;
            }
            return calculated;
        } catch (ArithmeticException e) {
            return maxBackoff;
        }
    }

    public Duration getInitialBackoff() {
        return initialBackoff;
    }

    public Duration getMaxBackoff() {
        return maxBackoff;
    }
}
