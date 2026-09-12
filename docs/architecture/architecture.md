# Architecture: Version 5

## Overview
The Job Scheduler V5 introduces **job dependencies, DAG workflow validation, and scheduler state controls**. It separates dependency evaluation (`DependencyGraph`) from priority execution ordering (`PriorityQueue`), adds the `BLOCKED` status, supports failure policies (`CONTINUE`, `HALT_SCHEDULER`), and provides domain-level `resume()` controls.

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
- **`Job`**: Immutable record including `dependencyIds` (Set of stable Job IDs) and `FailurePolicy`.
- **`JobStatus`**: Extended to include `BLOCKED` status.
- **`FailurePolicy`**: Enum (`CONTINUE`, `HALT_SCHEDULER`).
- **`SchedulerState`**: Enum (`RUNNING`, `HALTED`, `STOPPED`).
- **`JobExecution`**: Thread-safe state machine managing `BLOCKED -> SCHEDULED` and `BLOCKED -> CANCELLED` transitions.

### 2. Dependency Layer (`com.siva.jobscheduler.dependency`)
- **`DependencyGraph`**: Tracks dependency relationships using stable Job IDs. Validates DAG acyclicity via DFS cycle detection during registration. Evaluates eligibility (`isSatisfied`).

### 3. Scheduler Layer (`com.siva.jobscheduler.scheduler`)
- **`JobScheduler`**: Manages PriorityQueue, DependencyGraph, and SchedulerState.
  - Places jobs without dependencies in `SCHEDULED` and offers to `PriorityQueue`.
  - Places jobs with unsatisfied dependencies in `BLOCKED` (bypasses `PriorityQueue`).
  - Upon job completion, unblocks satisfied dependents (`BLOCKED -> SCHEDULED`) and offers them to `PriorityQueue`.
  - Upon critical job failure (`HALT_SCHEDULER`), transitions `SchedulerState` to `HALTED`.
  - Exposes `resume()` to transition `HALTED -> RUNNING`.

### 4. Execution Layer (`com.siva.jobscheduler.execution`)
- **`JobExecutor`**: Bounded worker thread pool executing attempts and notifying completion.

## Dependency Workflow & PriorityQueue Isolation

```text
Registered Job
      │
  Has Dependencies?
      ├───── No ──────> SCHEDULED ───> PriorityQueue ───> JobExecutor
      │                                     ▲
      └───── Yes ─────> BLOCKED             │
                           │                │
                (Prerequisites Complete)    │
                           │                │
                           └────────────────┘
```

## Failure Policy & HALT / RESUME Flow

```text
Job Execution FAILED (Permanent)
      │
  FailurePolicy?
      ├───── CONTINUE ───────> Dependents remain BLOCKED; Independent jobs continue
      │
      └───── HALT_SCHEDULER ──> SchedulerState = HALTED
                                    │
                            (Suspend new dispatches)
                                    │
                                 resume()
                                    │
                                    v
                            SchedulerState = RUNNING
                            (Re-evaluate & resume eligible jobs)
```
