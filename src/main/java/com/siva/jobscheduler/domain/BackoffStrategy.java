package com.siva.jobscheduler.domain;

import java.time.Duration;

/**
 * Strategy interface for calculating backoff delay before a retry attempt.
 */
@FunctionalInterface
public interface BackoffStrategy {
    Duration calculateBackoff(int attemptNumber);
}
