package com.siva.jobscheduler.domain;

/**
 * Strategy interface to classify execution failures.
 */
@FunctionalInterface
public interface FailureClassifier {
    FailureType classify(Throwable failure);
}
