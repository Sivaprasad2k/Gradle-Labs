# Java Job Scheduler (V6)

## Project Overview
A lightweight, in-memory Java 17 job scheduler designed to demonstrate core engineering concepts including clean architecture, bounded concurrent execution using `ExecutorService`, explicit thread-safe job lifecycle management, controlled failure recovery with exponential backoff retries, job dependency DAG workflow control, recurring fixed-rate scheduling with coalescing, time-dependent unit testing, and fundamental scheduling algorithms without relying on heavy enterprise frameworks.

## V6 Scope & Architectural Evolution
Version 6 (V6) introduces **recurring job support and fixed-rate scheduling**. Building upon V5's DAG dependency and workflow control engine, V6 introduces `RecurrencePolicy` and `FixedRateRecurrence`, explicit execution occurrence identities (`executionId`), missed-occurrence coalescing, schedule cancellation (`cancelRecurrence`), and strict isolation between retry attempts and recurrence occurrences.

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
├── recurrence
│   ├── RecurrencePolicy.java
│   ├── FixedRateRecurrence.java
│   └── RecurrenceState.java
├── dependency
│   └── DependencyGraph.java
├── scheduler
│   └── JobScheduler.java
├── execution
│   └── JobExecutor.java
└── JobSchedulerApplication.java
```

- **`domain.Job`**: Immutable record with optional `RecurrencePolicy`, `dependencyIds`, and `FailurePolicy`.
- **`domain.JobExecution`**: Manages explicit execution occurrence identity (`executionId`) and lifecycle state transitions.
- **`recurrence.FixedRateRecurrence`**: Calculates next scheduled occurrence from previous scheduled time $T_{scheduled}$ and coalesces missed occurrences.
- **`scheduler.JobScheduler`**: Schedules recurring jobs, maintains execution history, enforces no-overlapping-execution invariants, and supports schedule cancellation (`cancelRecurrence`).

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

## Architecture Documentation
Detailed decision records and version documentation are located in `/docs`:
- `docs/architecture/architecture.md`
- `docs/versions/v1.md`
- `docs/versions/v2.md`
- `docs/versions/v3.md`
- `docs/versions/v4.md`
- `docs/versions/v5.md`
- `docs/versions/v6.md`
- `docs/decisions/ADR-001-priority-queue.md`
- `docs/decisions/ADR-002-concurrent-execution.md`
- `docs/decisions/ADR-003-job-lifecycle.md`
- `docs/decisions/ADR-004-retry-and-reliability.md`
- `docs/decisions/ADR-005-job-dependencies-and-workflow-control.md`
- `docs/decisions/ADR-006-recurring-jobs-and-fixed-rate-scheduling.md`

## Version Roadmap
- **V1**: Foundational, in-memory, sequential priority-queue scheduler.
- **V2**: Decoupled architecture with bounded concurrent execution via `ExecutorService`.
- **V3**: Explicit thread-safe job lifecycle state machine, cancellation semantics, and failure metadata.
- **V4**: Controlled failure recovery, retry policy, failure classification, and exponential backoff engine.
- **V5**: Job dependencies, DAG cycle detection, BLOCKED state, FailurePolicy, and HALT/RESUME workflow controls.
- **V6 (Current)**: Recurring jobs, fixed-rate scheduling, missed-occurrence coalescing, and execution occurrence identity.
- **Future Versions**: May explore persistence (V7), REST APIs (V8), and observability (V9).
