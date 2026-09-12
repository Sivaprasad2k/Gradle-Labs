package com.siva.jobscheduler.domain;

import java.util.Collections;
import java.util.Set;

/**
 * Default implementation of FailureClassifier.
 * Conservatively classifies failures as PERMANENT unless explicitly matched against a set of retryable exception types.
 */
public class DefaultFailureClassifier implements FailureClassifier {
    private final Set<Class<? extends Throwable>> retryableExceptions;

    public DefaultFailureClassifier() {
        this(Collections.emptySet());
    }

    public DefaultFailureClassifier(Set<Class<? extends Throwable>> retryableExceptions) {
        this.retryableExceptions = retryableExceptions != null ? Set.copyOf(retryableExceptions) : Collections.emptySet();
    }

    @Override
    public FailureType classify(Throwable failure) {
        if (failure == null) {
            return FailureType.PERMANENT;
        }
        for (Class<? extends Throwable> clazz : retryableExceptions) {
            if (clazz.isInstance(failure)) {
                return FailureType.TRANSIENT;
            }
        }
        return FailureType.PERMANENT;
    }
}
