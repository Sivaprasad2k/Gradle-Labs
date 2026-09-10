# Java Job Scheduler (V1)

## Project Overview
A lightweight, in-memory Java 17 job scheduler designed to demonstrate core engineering concepts including clean architecture, time-dependent unit testing, and fundamental scheduling algorithms without relying on heavy enterprise frameworks.

## V1 Scope
Version 1 is an intentional foundational release. It establishes the core domain model and a functional priority-based scheduling loop. It executes strictly sequentially on a single thread and holds all state in memory.

## Architecture & Core Components
The project is built on a clean, layered architecture:
- **`domain.Job`**: An immutable Java 17 record representing the scheduled entity.
- **`domain.JobTask`**: A functional interface representing the executable workload.
- **`scheduler.JobScheduler`**: The execution engine that evaluates and dispatches due jobs.

## Scheduling Model & PriorityQueue Rationale
The scheduler operates using a **PriorityQueue**. Because `Job` implements `Comparable<Job>` (sorting by absolute scheduled time), the queue guarantees the earliest job is always at the head. 

**Complexity Characteristics:**
- **Insert (`offer`)**: O(log n)
- **Inspect Next (`peek`)**: O(1)
- **Extract Next (`poll`)**: O(log n)

This makes the algorithm highly efficient for dynamic registration and sequential extraction.

## Time Handling & Execution Model
Execution is purely sequential. The scheduler peeks at the next job; if the job is due, it executes it synchronously. If the job is scheduled in the future, the scheduler suspends execution using `Thread.sleep()` until the precise moment the job is due.

To ensure deterministic behavior and testability, time is evaluated through an injected `java.time.Clock`. In production, this uses `Clock.systemUTC()`, while unit tests can utilize `Clock.fixed(...)` to manipulate time seamlessly without requiring actual thread sleep delays in most scenarios.

## Project Structure
```text
com.siva.jobscheduler
├── domain
│   ├── Job.java
│   └── JobTask.java
├── scheduler
│   └── JobScheduler.java
└── JobSchedulerApplication.java
```

## Technology Stack
- **Language**: Java 17
- **Build Tool**: Gradle 9.7.1
- **Testing**: JUnit 5 (Jupiter)

## Build and Run Instructions

**To run the test suite:**
```bash
./gradlew test
```

**To build the project:**
```bash
./gradlew build
```

**To execute the demonstration application:**
```bash
./gradlew run
```

## Example Output
```text
Registered job: job-001
Registered job: job-002
Registered job: job-003

Scheduler started.

[2026-09-11T00:00:00Z] STARTED job-002
[2026-09-11T00:00:00Z] COMPLETED job-002
[2026-09-11T00:00:01Z] STARTED job-001
[2026-09-11T00:00:01Z] COMPLETED job-001
[2026-09-11T00:00:02Z] STARTED job-003
[2026-09-11T00:00:02Z] COMPLETED job-003

All jobs completed.
Scheduler stopped.
```

## Engineering Decisions
For detailed architectural choices, please review the documentation in the `/docs` directory.
- `docs/architecture/architecture.md`
- `docs/versions/v1.md`
- `docs/decisions/ADR-001-priority-queue.md`

## Version Roadmap
- **V1 (Current)**: In-memory, sequential, priority-queue-based execution.
- **Future Versions**: May explore worker threads (`ExecutorService`), persistence, and recurring cron-based execution.
