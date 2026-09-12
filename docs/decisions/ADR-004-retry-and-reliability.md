# ADR-004: Centralized Retry Policy and Exponential Backoff Engine

## Status
Accepted

## Context
In Version 3 of the Job Scheduler, any job task throwing an unhandled exception immediately transitioned to `FAILED` permanently. In real-world software systems, transient failures (such as temporary network glitches or database lock timeouts) occur frequently and can be resolved by retrying the operation after a brief delay.

We need a reliability mechanism to retry transient failures automatically while preserving resource safety, state consistency, and thread pool efficiency.

## Decision
We will introduce a centralized retry engine managed by `JobScheduler` and `PriorityQueue`, using:
- **`ExecutionAttempt`**: To record individual attempt outcomes under the same `JobExecution`.
- **`RetryPolicy`**: To define `maxAttempts` (total execution attempts allowed), failure classification, and backoff strategy.
- **`FailureClassifier`**: To differentiate `TRANSIENT` (retryable) from `PERMANENT` (non-retryable) errors.
- **`ExponentialBackoffStrategy`**: To calculate exponential backoff delay ($\text{initialBackoff} \times 2^{\text{attempt}-1}$) clamped to `maxBackoff`.

When an attempt fails transiently, `JobScheduler` transitions `JobExecution` `FAILED -> SCHEDULED` and re-enqueues the job into `PriorityQueue` for the calculated future retry timestamp.

## Alternatives Considered

### 1. Retry Loops inside Worker Threads (`JobExecutor`)
- **Pros**: Easy to implement using a `while` loop inside `executeJob()`.
- **Cons**: Worker pool thread exhaustion! Sleeping inside worker threads holds thread pool capacity hostage during backoff delays. Bypasses priority queue ordering and prevents safe job cancellation during backoff waits.

### 2. Creating New `Job` Objects for Every Retry (`job-1-retry-1`)
- **Pros**: Treats every retry as a new scheduled entity.
- **Cons**: Destroys job identity and pollutes queue metrics. Makes tracking the overall status of a job execution fragmented across multiple independent objects.

### 3. Infinite Retries
- **Pros**: Guarantees retries continue until eventual success.
- **Cons**: Causes thread and queue starvation on permanent failures. `maxAttempts` must be strictly enforced.

## Rationale
Centralizing retries through `JobScheduler` and `PriorityQueue` ensures that worker threads are never blocked during backoff delays, job cancellation remains responsive during retry waits, and job identity is perfectly preserved across attempts.
