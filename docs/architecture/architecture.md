# Architecture: Version 6

## Overview
The Job Scheduler V6 introduces **recurring jobs, fixed-rate scheduling, and missed-occurrence coalescing**. It decouples recurring job definitions from execution occurrences (`executionId`), introduces `FixedRateRecurrence`, enforces no-overlapping-execution invariants, and isolates retry attempts from recurrence occurrences while preserving V1–V5 DAG dependencies and workflow control policies.

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
│   ├── RetryPolicy.java
│   ├── FailurePolicy.java
│   └── SchedulerState.java
│
├── recurrence/
│   ├── RecurrencePolicy.java
│   ├── FixedRateRecurrence.java
│   └── RecurrenceState.java
│
├── dependency/
│   └── DependencyGraph.java
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
- **`Job`**: Immutable record including optional `RecurrencePolicy`, `dependencyIds`, and `FailurePolicy`.
- **`JobExecution`**: Manages explicit execution occurrence identity (`executionId = jobId#occurrenceNumber`) and lifecycle transitions.

### 2. Recurrence Layer (`com.siva.jobscheduler.recurrence`)
- **`RecurrencePolicy`**: Interface defining occurrence eligibility and next scheduled time calculations.
- **`FixedRateRecurrence`**: Concrete record implementing fixed-rate calculations and missed-occurrence coalescing.
- **`RecurrenceState`**: Tracks occurrence count, last scheduled time, and schedule cancellation status per job.

### 3. Scheduler Layer (`com.siva.jobscheduler.scheduler`)
- **`JobScheduler`**: Core scheduling engine managing PriorityQueue, DependencyGraph, RecurrenceStates, and execution history.
  - Generates next recurrence occurrence upon completion of current execution.
  - Enforces no-overlapping-execution invariant (at most 1 active execution per job).
  - Supports schedule cancellation (`cancelRecurrence`).

## Recurrence & Execution Hierarchy

```text
Job (Definition)
  └── RecurrencePolicy (e.g. FixedRateRecurrence: interval = 1m)
        ├── JobExecution #1 (executionId: job-1#1, occurrence: 1)
        ├── JobExecution #2 (executionId: job-1#2, occurrence: 2)
        └── JobExecution #3 (executionId: job-1#3, occurrence: 3)
```
