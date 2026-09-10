# Architecture: Version 1

## Overview
The Job Scheduler V1 is a lightweight, sequential execution engine designed to manage and execute jobs based on their scheduled execution times. The architecture is intentionally minimal, utilizing standard Java libraries without external dependencies or heavy frameworks.

## Components and Responsibilities

### Domain Layer (`com.siva.jobscheduler.domain`)
- **`Job`**: An immutable record representing a scheduled unit of work. It encapsulates an `id`, `name`, `scheduledAt` (an absolute `Instant` in time), and a `JobTask`. It implements `Comparable<Job>` to dictate natural ordering by the `scheduledAt` timestamp.
- **`JobTask`**: A functional interface representing the executable behavior of a job. It allows tasks to be defined using simple lambda expressions or method references.

### Scheduler Layer (`com.siva.jobscheduler.scheduler`)
- **`JobScheduler`**: The core execution engine. It owns a `PriorityQueue` of registered jobs and controls the scheduling loop. It relies on a `Clock` for time queries, ensuring determinism and testability.

### Application Entry Point
- **`JobSchedulerApplication`**: Demonstrates the initialization of the scheduler, the registration of sample jobs, and the invocation of the scheduling loop.

## Scheduling Flow
1. **Registration**: Jobs are submitted to the `JobScheduler` via `registerJob()`. They are immediately added to the internal `PriorityQueue`.
2. **Evaluation**: When `start()` is invoked, the scheduler enters a sequential loop. It evaluates the head of the queue (`peek()`), which is guaranteed to be the job with the earliest scheduled time.
3. **Execution or Wait**: 
   - If the job's scheduled time is in the past or exactly now, it is removed from the queue (`poll()`) and executed synchronously.
   - If the job is scheduled in the future, the scheduler calculates the delay and suspends execution (`Thread.sleep()`) until the exact moment the job is due.
4. **Completion**: The loop continues until the queue is entirely empty, at which point the scheduler terminates gracefully.
