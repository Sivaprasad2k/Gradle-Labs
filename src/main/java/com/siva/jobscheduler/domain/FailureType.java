package com.siva.jobscheduler.domain;

/**
 * Classification of a failure to determine whether it is retryable.
 */
public enum FailureType {
    TRANSIENT,
    PERMANENT
}
