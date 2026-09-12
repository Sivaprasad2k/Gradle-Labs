# Java Job Scheduler (V5)

## Project Overview
A lightweight, in-memory Java 17 job scheduler designed to demonstrate core engineering concepts including clean architecture, bounded concurrent execution using `ExecutorService`, explicit thread-safe job lifecycle management, controlled failure recovery with exponential backoff retries, job dependency DAG workflow control, time-dependent unit testing, and fundamental scheduling algorithms without relying on heavy enterprise frameworks.

## V5 Scope & Architectural Evolution
Version 5 (V5) introduces **job dependencies, DAG workflow validation, and scheduler state controls**. Building upon V4's retry engine, V5 adds `Set<String> dependencyIds` to job definitions, introduces the `BLOCKED` lifecycle state, validates acyclic dependency graphs (DFS cycle detection), implements `FailurePolicy` (`CONTINUE`, `HALT_SCHEDULER`), and provides domain-level `resume()` controls.

## Architecture & Core Components
```text
com.siva.jobscheduler
├── domain
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
├── dependency
│   └── DependencyGraph.java
├── scheduler
│   └── JobScheduler.java
├── execution
│   └── JobExecutor.java
└── JobSchedulerApplication.java
```

- **`domain.Job`**: Immutable record with `dependencyIds` (stable Job IDs) and `FailurePolicy`.
- **`domain.JobStatus`**: Includes `BLOCKED` status.
- **`domain.JobExecution`**: Manages `BLOCKED -> SCHEDULED` and `BLOCKED -> CANCELLED` transitions.
- **`dependency.DependencyGraph`**: Validates DAG acyclicity (DFS cycle detection) and evaluates dependency satisfaction.
- **`scheduler.JobScheduler`**: Keeps blocked jobs out of `PriorityQueue` until dependencies complete. Manages `SchedulerState` (`RUNNING`, `HALTED`, `STOPPED`) and `resume()`.

## Dependency & Workflow Model
- **Stable ID References**: Dependencies refer to stable Job IDs, preserving job immutability.
- **PriorityQueue Isolation**: Blocked jobs start in `BLOCKED` and bypass `PriorityQueue` to avoid busy-polling.
- **Unblocking**: When a prerequisite job completes (`COMPLETED`), satisfied dependent jobs transition `BLOCKED -> SCHEDULED` and enter `PriorityQueue`.
- **Retry Integration**: Retrying prerequisite jobs (`FAILED -> SCHEDULED`) keep dependents `BLOCKED` until final completion.
- **FailurePolicy**:
  - `CONTINUE`: Unrelated independent jobs continue running; dependents of the failed job remain `BLOCKED`.
  - `HALT_SCHEDULER`: Permanent failure of a critical job halts new dispatches (`HALTED`).
- **Resumption (`resume()`)**: Restores `SchedulerState` from `HALTED` to `RUNNING` and re-evaluates blocked jobs.

## Technology Stack
- **Language**: Java 17
- **Build Tool**: Gradle 9.7.1
- **Testing**: JUnit 5 (Jupiter)
- **Standard Library Only**: Standard JDK concurrency primitives (`ExecutorService`, `CountDownLatch`, `AtomicInteger`, `AtomicBoolean`).

## Build and Run Instructions

**To clean and run the test suite:**
```bash
./gradlew clean test
```

**To build the project:**
```bash
./gradlew build
```

**To execute the demonstration application:**
```bash
./gradlew run --console=plain
```

## Example Output (V5 Dependencies & Workflow Control)
```text
==================================================
 Java Job Scheduler - Version 5
 Job Dependencies & Workflow Control
==================================================

Registering workflow jobs...
Registered job A: job-a-prereq [Initial: SCHEDULED]
Registered job B: job-b-step2 [Initial: BLOCKED, Depends: A]
Registered job C: job-c-step3 [Initial: BLOCKED, Depends: B]
Registered job D: job-d-failing [Initial: SCHEDULED]
Registered job E: job-e-dependent-on-d [Initial: BLOCKED, Depends: D]
Registered job F: job-f-independent [Initial: SCHEDULED]

Scheduler started.

[2026-09-12T08:39:15.010Z] job-a-prereq SCHEDULED -> RUNNING (Attempt 1)
[2026-09-12T08:39:15.010Z] job-d-failing SCHEDULED -> RUNNING (Attempt 1)
[2026-09-12T08:39:15.010Z] job-f-independent SCHEDULED -> RUNNING (Attempt 1)
[2026-09-12T08:39:15.050Z] [pool-1-thread-3] job-f-independent Attempt 1 RUNNING -> COMPLETED
[2026-09-12T08:39:15.050Z] [pool-1-thread-2] job-d-failing Attempt 1 RUNNING -> FAILED: Data validation error
[2026-09-12T08:39:15.050Z] job-d-failing Attempt 1 FAILED (Permanent / Retries Exhausted). Final status: FAILED
[2026-09-12T08:39:15.050Z] [pool-1-thread-1] job-a-prereq Attempt 1 RUNNING -> FAILED: Connection timed out on attempt 1
[2026-09-12T08:39:15.050Z] job-a-prereq Attempt 1 FAILED (Transient). Retry scheduled for 2026-09-12T08:39:15.100Z
[2026-09-12T08:39:15.100Z] job-a-prereq SCHEDULED -> RUNNING (Attempt 2)
[2026-09-12T08:39:15.140Z] [pool-1-thread-1] job-a-prereq Attempt 2 RUNNING -> COMPLETED
[2026-09-12T08:39:15.140Z] job-b-step2 BLOCKED -> SCHEDULED (Dependencies satisfied)
[2026-09-12T08:39:15.140Z] job-b-step2 SCHEDULED -> RUNNING (Attempt 1)
[2026-09-12T08:39:15.180Z] [pool-1-thread-1] job-b-step2 Attempt 1 RUNNING -> COMPLETED
[2026-09-12T08:39:15.180Z] job-c-step3 BLOCKED -> SCHEDULED (Dependencies satisfied)
[2026-09-12T08:39:15.180Z] job-c-step3 SCHEDULED -> RUNNING (Attempt 1)
[2026-09-12T08:39:15.220Z] [pool-1-thread-1] job-c-step3 Attempt 1 RUNNING -> COMPLETED

All jobs dispatched to executor.
Scheduler stopped.

==================================================
 Execution Summary
==================================================
job-a-prereq              Attempts: 2 | Status: COMPLETED 
job-b-step2               Attempts: 1 | Status: COMPLETED 
job-c-step3               Attempts: 1 | Status: COMPLETED 
job-d-failing             Attempts: 1 | Status: FAILED     (Reason: Data validation error)
job-e-dependent-on-d      Attempts: 0 | Status: BLOCKED   
job-f-independent         Attempts: 1 | Status: COMPLETED 
--------------------------------------------------
Completed : 4
Failed    : 1
Blocked   : 1
==================================================
```

## Architecture Documentation
Detailed decision records and version documentation are located in `/docs`:
- `docs/architecture/architecture.md`
- `docs/versions/v1.md`
- `docs/versions/v2.md`
- `docs/versions/v3.md`
- `docs/versions/v4.md`
- `docs/versions/v5.md`
- `docs/decisions/ADR-001-priority-queue.md`
- `docs/decisions/ADR-002-concurrent-execution.md`
- `docs/decisions/ADR-003-job-lifecycle.md`
- `docs/decisions/ADR-004-retry-and-reliability.md`
- `docs/decisions/ADR-005-job-dependencies-and-workflow-control.md`

## Version Roadmap
- **V1**: Foundational, in-memory, sequential priority-queue scheduler.
- **V2**: Decoupled architecture with bounded concurrent execution via `ExecutorService`.
- **V3**: Explicit thread-safe job lifecycle state machine, cancellation semantics, and failure metadata.
- **V4**: Controlled failure recovery, retry policy, failure classification, and exponential backoff engine.
- **V5 (Current)**: Job dependencies, DAG cycle detection, BLOCKED state, FailurePolicy, and HALT/RESUME workflow controls.
- **Future Versions**: May explore recurring cron jobs (V6), persistence (V7), REST APIs (V8), and observability (V9).
