# Architecture: Version 4

## Overview
The Job Scheduler V4 builds upon V3's explicit state machine by introducing **controlled failure recovery** and **retry policy integration**. It records individual execution attempts (`ExecutionAttempt`), classifies failures (`TRANSIENT` vs `PERMANENT`), calculates exponential backoff delays, and re-queues retryable jobs through the centralized `JobScheduler` and `PriorityQueue` without creating new job identities or blocking worker threads.

## Package Architecture & Component Boundaries

```text
com.siva.jobscheduler/
│
├── domain/
│   ├── Job.java
│   ├── JobTask.java
│   ├── JobStatus.java
│   ├── JobExecution.java
│   ├── AttemptOutcome.java
│   ├── ExecutionAttempt.java
│   ├── FailureType.java
│   ├── FailureClassifier.java
│   ├── DefaultFailureClassifier.java
│   ├── BackoffStrategy.java
│   ├── ExponentialBackoffStrategy.java
│   └── RetryPolicy.java
│
├── scheduler/
│   └── JobScheduler.java
│
├── execution/
│   └── JobExecutor.java
│
└── JobSchedulerApplication.java
```

### 1. Domain Layer (`com.siva.jobscheduler.domain`)
- **`Job`**: Immutable record representing static work parameters (`id`, `name`, `scheduledAt`, `task`). Identity remains constant across retries.
- **`JobExecution`**: Thread-safe wrapper managing overall lifecycle status (`JobStatus`) and retaining a list of `ExecutionAttempt` records.
- **`ExecutionAttempt`**: Record of an individual execution attempt (`attemptNumber`, `startedAt`, `completedAt`, `outcome`, `failure`).
- **`RetryPolicy`**: Defines `maxAttempts`, `FailureClassifier`, and `BackoffStrategy`. Defaults to `maxAttempts = 1` (no retry).
- **`ExponentialBackoffStrategy`**: Calculates backoff delay ($\text{initialBackoff} \times 2^{\text{attempt}-1}$) clamped to `maxBackoff`.

### 2. Scheduler Layer (`com.siva.jobscheduler.scheduler`)
- **`JobScheduler`**: Single-threaded priority queue owner and retry coordinator.
  - Receives execution completion notifications.
  - Evaluates `RetryPolicy.shouldRetry(execution, failure)`.
  - Calculates `nextRunTime = policy.calculateNextAttemptTime(attemptCount, failureTime)`.
  - Atomically transitions `JobExecution` `FAILED -> SCHEDULED`.
  - Re-queues `Job` with `nextRunTime` into `PriorityQueue`.

### 3. Execution Layer (`com.siva.jobscheduler.execution`)
- **`JobExecutor`**: Bounded worker thread engine (`Executors.newFixedThreadPool`).
  - Executes single attempt per submission.
  - Updates `JobExecution` attempt history and status to `COMPLETED` or `FAILED`.
  - Returns control to `JobScheduler` (no retry loops inside worker threads).

## Retry Lifecycle & State Machine Flow

```text
                  ┌──────────────┐
                  │  SCHEDULED   │
                  └──────┬───────┘
                         │
         ┌───────────────┴───────────────┐
         │                               │
 (cancelJob prior to start)   (Scheduler poll & due time)
         │                               │
         v                               v
 ┌──────────────┐                ┌──────────────┐
 │  CANCELLED   │                │   RUNNING    │
 └──────────────┘                └──────┬───────┘
                                        │
                        ┌───────────────┴───────────────┐
                        │                               │
               (Task Success)                    (Task Exception)
                        │                               │
                        v                               v
                ┌──────────────┐                ┌──────────────┐
                │  COMPLETED   │                │    FAILED    │
                └──────────────┘                └──────┬───────┘
                                                       │
                                          (Transient Failure & Attempt < maxAttempts)
                                                       │
                                                       v
                                          ┌────────────────────────┐
                                          │ Re-queued SCHEDULED    │
                                          │ (FAILED -> SCHEDULED)  │
                                          └────────────────────────┘
```
