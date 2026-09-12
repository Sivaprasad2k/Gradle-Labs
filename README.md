# Java Job Scheduler (V4)

## Project Overview
A lightweight, in-memory Java 17 job scheduler designed to demonstrate core engineering concepts including clean architecture, bounded concurrent execution using `ExecutorService`, explicit thread-safe job lifecycle management, controlled failure recovery with exponential backoff retries, time-dependent unit testing, and fundamental scheduling algorithms without relying on heavy enterprise frameworks.

## V4 Scope & Architectural Evolution
Version 4 (V4) introduces **controlled failure recovery** and **retry policy integration**. Building upon V3's explicit state machine, V4 adds attempt history tracking (`ExecutionAttempt`), failure classification (`TRANSIENT` vs `PERMANENT`), exponential backoff delay calculation (`ExponentialBackoffStrategy`), and scheduler-managed retry re-queuing while preserving exact job identities and V1-V3 baselines.

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
│   └── RetryPolicy.java
├── scheduler
│   └── JobScheduler.java
├── execution
│   └── JobExecutor.java
└── JobSchedulerApplication.java
```

- **`domain.Job`**: An immutable Java 17 record representing static job parameters (`id`, `name`, `scheduledAt`, `task`). Identity remains constant across all retries.
- **`domain.JobExecution`**: Thread-safe runtime state wrapper that manages atomic state transitions and retains a list of `ExecutionAttempt` records.
- **`domain.ExecutionAttempt`**: Record of an individual execution attempt (`attemptNumber`, `startedAt`, `completedAt`, `outcome`, `failure`).
- **`domain.RetryPolicy`**: Encapsulates `maxAttempts` (total execution attempts limit, default 1), `FailureClassifier`, and `BackoffStrategy`.
- **`scheduler.JobScheduler`**: Owns `PriorityQueue<Job>`, evaluates retries upon task failure, calculates backoff delay, transitions `JobExecution` `FAILED -> SCHEDULED`, and re-enqueues jobs.
- **`execution.JobExecutor`**: Bounded worker pool (`Executors.newFixedThreadPool`), executes individual attempts on background threads, and updates attempt history.

## Retry Model & Backoff Rules
- **Job Identity**: A retry is **NOT** a new `Job` object. The `job.id()` remains constant.
- **`maxAttempts`**: Defines the **TOTAL** execution attempts allowed (e.g., `maxAttempts = 3` means Attempt 1, Attempt 2, and Attempt 3).
- **Default Behavior**: Unconfigured jobs default to `maxAttempts = 1` (`RetryPolicy.noRetry()`), preserving V3 behavior.
- **Exponential Backoff**: Delay $\text{initialBackoff} \times 2^{(\text{attempt}-1)}$, clamped to `maxBackoff`.
- **Centralized Scheduling**: Worker threads never sleep during backoff delays. Retries are scheduled centrally via `JobScheduler` and `PriorityQueue`.

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

## Example Output (V4 Failure Recovery & Retries)
```text
==================================================
 Java Job Scheduler - Version 4
 Controlled Failure Recovery & Retry Engine
==================================================

Registering jobs...
Registered job: job-a-immediate-success [Policy: noRetry]
Registered job: job-b-transient-retry [Policy: maxAttempts=3, transient=NetworkTimeoutException]
Registered job: job-c-permanent-failure [Policy: maxAttempts=3, permanent=InvalidDataException]

Scheduler started.

[2026-09-12T08:00:00Z] job-a-immediate-success SCHEDULED -> RUNNING (Attempt 1)
[2026-09-12T08:00:00Z] job-b-transient-retry SCHEDULED -> RUNNING (Attempt 1)
[2026-09-12T08:00:00Z] job-c-permanent-failure SCHEDULED -> RUNNING (Attempt 1)
[2026-09-12T08:00:00.050Z] [pool-1-thread-1] job-a-immediate-success Attempt 1 RUNNING -> COMPLETED
[2026-09-12T08:00:00.050Z] [pool-1-thread-3] job-c-permanent-failure Attempt 1 RUNNING -> FAILED: Unrecoverable data validation error
[2026-09-12T08:00:00.050Z] job-c-permanent-failure Attempt 1 FAILED (Permanent / Retries Exhausted). Final status: FAILED
[2026-09-12T08:00:00.050Z] [pool-1-thread-2] job-b-transient-retry Attempt 1 RUNNING -> FAILED: Connection timed out on attempt 1
[2026-09-12T08:00:00.050Z] job-b-transient-retry Attempt 1 FAILED (Transient). Retry scheduled for 2026-09-12T08:00:00.150Z
[2026-09-12T08:00:00.150Z] job-b-transient-retry SCHEDULED -> RUNNING (Attempt 2)
[2026-09-12T08:00:00.200Z] [pool-1-thread-1] job-b-transient-retry Attempt 2 RUNNING -> FAILED: Connection timed out on attempt 2
[2026-09-12T08:00:00.200Z] job-b-transient-retry Attempt 2 FAILED (Transient). Retry scheduled for 2026-09-12T08:00:00.400Z
[2026-09-12T08:00:00.400Z] job-b-transient-retry SCHEDULED -> RUNNING (Attempt 3)
[2026-09-12T08:00:00.450Z] [pool-1-thread-1] job-b-transient-retry Attempt 3 RUNNING -> COMPLETED

All jobs dispatched to executor.
Scheduler stopped.

==================================================
 Execution Summary
==================================================
job-a-immediate-success   Attempts: 1 | Status: COMPLETED 
job-b-transient-retry     Attempts: 3 | Status: COMPLETED 
job-c-permanent-failure   Attempts: 1 | Status: FAILED     (Reason: Unrecoverable data validation error)
--------------------------------------------------
Completed : 2
Failed    : 1
==================================================
```

## Architecture Documentation
Detailed decision records and version documentation are located in `/docs`:
- `docs/architecture/architecture.md`
- `docs/versions/v1.md`
- `docs/versions/v2.md`
- `docs/versions/v3.md`
- `docs/versions/v4.md`
- `docs/decisions/ADR-001-priority-queue.md`
- `docs/decisions/ADR-002-concurrent-execution.md`
- `docs/decisions/ADR-003-job-lifecycle.md`
- `docs/decisions/ADR-004-retry-and-reliability.md`

## Version Roadmap
- **V1**: Foundational, in-memory, sequential priority-queue scheduler.
- **V2**: Decoupled architecture with bounded concurrent execution via `ExecutorService`.
- **V3**: Explicit thread-safe job lifecycle state machine, cancellation semantics, and failure metadata.
- **V4 (Current)**: Controlled failure recovery, retry policy, failure classification, and exponential backoff engine.
- **Future Versions**: May explore job dependencies (V5), recurring cron jobs (V6), persistence (V7), REST APIs (V8), and observability (V9).
