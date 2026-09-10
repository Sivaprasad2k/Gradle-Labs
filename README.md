# Job Scheduler V1

## What the project is
A lightweight, understandable Java 17 job scheduler learning project that demonstrates core scheduling concepts using standard Java libraries, without heavy frameworks.

## V1 Scope
This is Version 1 of the job scheduler. Its purpose is to demonstrate:
- Basic job structure with scheduled execution times.
- Scheduling jobs using a `PriorityQueue`.
- Sequential execution based on schedule time.
- Unit testing time-dependent code.

## Architecture
The project follows a simple layered structure:
- `com.siva.jobscheduler.domain`: Contains immutable domain models (`Job` record) and behavior contracts (`JobTask`).
- `com.siva.jobscheduler.scheduler`: Contains the `JobScheduler` responsible for the scheduling algorithm.
- `com.siva.jobscheduler`: Contains the `JobSchedulerApplication` entry point.

## Why PriorityQueue is used
A `PriorityQueue` is an ideal data structure for scheduling because it naturally orders elements based on their priority. By making `Job` implement `Comparable<Job>` (ordering by `scheduledAt`), the priority queue ensures that the earliest scheduled job is always at the head of the queue (`peek()`), providing O(log n) insertion and O(1) retrieval of the next due job.

## Scheduling Flow
1. Jobs are registered and added to the `PriorityQueue`.
2. The scheduler loops while the queue is not empty.
3. It checks the next due job (`peek()`).
4. If the job's scheduled time is in the past or now, it is removed (`poll()`) and executed.
5. If the job's scheduled time is in the future, the scheduler waits (`Thread.sleep()`) until it becomes due.

## Why execution is sequential in V1
V1 intentionally avoids thread pools and concurrency frameworks to keep the scheduling algorithm clear and understandable. Sequential execution ensures that we can easily trace the flow of execution and verify the core logic before introducing the complexities of concurrent state management.

## How Time is Handled and JUnit is used
To make the scheduler testable without slow `Thread.sleep` calls in every test, it depends on a `java.time.Clock`. In production, `Clock.systemUTC()` is used. In tests, a fixed clock (`Clock.fixed(...)`) is passed to the scheduler, allowing tests to instantly verify behavior at specific points in time. 

The project uses JUnit 5 for testing. Tests cover job creation, property validation, priority ordering, empty queue handling, and execution order.

## How to run tests
```bash
./gradlew test
```

## How to run the application
```bash
./gradlew run
```

## Known Limitations
- V1 is strictly sequential. If a job takes a long time to execute, it will delay all subsequent jobs.
- The scheduler cannot be interrupted gracefully during execution.
- No persistence; all jobs are held in memory and lost if the application stops.
- No recurring jobs (cron) or retry logic.

## What future versions may introduce
- Worker threads / `ExecutorService` for concurrent job execution.
- Persistence mechanisms (e.g., database) to survive restarts.
- Recurring jobs and cron expression support.
- Retry logic for failed jobs.
