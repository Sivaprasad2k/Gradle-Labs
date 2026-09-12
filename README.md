# Java Job Scheduler (V7)

## Project Overview
A lightweight, durable Java 17 job scheduler designed to demonstrate core engineering concepts including clean architecture, bounded concurrent execution using `ExecutorService`, explicit thread-safe job lifecycle management, controlled failure recovery with exponential backoff retries, job dependency DAG workflow control, recurring fixed-rate scheduling with coalescing, durable MongoDB persistence, restart recovery, time-dependent unit testing, and fundamental scheduling algorithms without relying on heavy enterprise frameworks.

## V7 Scope & Architectural Evolution
Version 7 (V7) introduces **durable MongoDB persistence, execution history tracking, and restart recovery**. Building upon V6's recurrence engine, V7 shifts the runtime source of truth to MongoDB while preserving in-memory projections (`PriorityQueue`, `DependencyGraph`) for dispatching. V7 introduces `TaskType` / `TaskRegistry` abstractions, BSON document mappers, decoupled repository interfaces, and crash recovery for interrupted executions.

## Architecture & Core Components
```text
com.siva.jobscheduler
├── domain
│   ├── Job.java
│   ├── JobTask.java
│   ├── JobStatus.java
│   ├── JobExecution.java
│   ├── ExecutionAttempt.java
│   ├── RetryPolicy.java
│   ├── FailurePolicy.java
│   └── SchedulerState.java
├── task
│   ├── TaskHandler.java
│   └── TaskRegistry.java
├── persistence
│   ├── JobRepository.java
│   ├── ExecutionRepository.java
│   ├── RecurrenceRepository.java
│   ├── SchedulerStateRepository.java
│   ├── MongoConnectionManager.java
│   ├── mapper
│   │   └── DocumentMapper.java
│   └── mongo
│       ├── MongoJobRepository.java
│       ├── MongoExecutionRepository.java
│       ├── MongoRecurrenceRepository.java
│       └── MongoSchedulerStateRepository.java
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

- **`domain.Job`**: Immutable record with persistent `taskType` / `taskPayload`, `RecurrencePolicy`, `dependencyIds`, and `FailurePolicy`.
- **`task.TaskRegistry`**: Resolves persistent `taskType` to executable `TaskHandler` upon JVM restart.
- **`persistence.mongo`**: Implements durable persistence using official MongoDB Java Driver (`org.mongodb:mongodb-driver-sync`).
- **`scheduler.JobScheduler`**: Recovers state, jobs, recurrence states, and interrupted executions from MongoDB upon startup.

## Technology Stack
- **Language**: Java 17
- **Build Tool**: Gradle 9.7.1
- **Database**: MongoDB Sync Driver 5.1.0
- **Testing**: JUnit 5 (Jupiter)

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
- `docs/versions/v1.md` ... `docs/versions/v7.md`
- `docs/decisions/ADR-001-priority-queue.md` ... `docs/decisions/ADR-007-mongodb-persistence-and-restart-recovery.md`

## Version Roadmap
- **V1–V6**: Sequential queue, concurrency, lifecycle state machine, retries, DAG workflow control, fixed-rate recurrence.
- **V7 (Current)**: Persistent jobs, execution history, MongoDB driver, and restart recovery.
- **Future Versions**: May explore REST/CLI & Web Console (V8), and observability (V9).
