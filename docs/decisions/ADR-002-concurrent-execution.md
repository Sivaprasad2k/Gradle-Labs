# ADR-002: Bounded Concurrent Execution via ExecutorService

## Status
Accepted

## Context
In Version 1 of the Java Job Scheduler, job execution was strictly sequential and executed directly on the scheduling thread. If a job took a significant amount of time to execute, it blocked the main scheduling loop, causing subsequent due jobs to experience head-of-line blocking and execute far past their target `scheduledAt` times.

To support real-world workload requirements, the system must support concurrent job execution while maintaining control over system resource consumption.

## Decision
We will separate the responsibility of job scheduling from job execution by introducing a `JobExecutor` component backed by a bounded fixed-size thread pool (`Executors.newFixedThreadPool(workerCount)`).

- **`JobScheduler`** remains solely responsible for managing the `PriorityQueue<Job>`, evaluating job due times, and dispatching due jobs.
- **`JobExecutor`** accepts dispatched jobs and submits them to its worker thread pool for execution.

## Alternatives Considered

### 1. Sequential Execution (V1 Status Quo)
- **Pros**: Simple, zero thread safety or synchronization concerns.
- **Cons**: Severe head-of-line blocking. Slow jobs delay all subsequent jobs. Unsuited for multi-core processors.

### 2. Thread-per-Job (`new Thread(task).start()`)
- **Pros**: Simple concurrency without worker pool management.
- **Cons**: Unbounded thread creation causes resource exhaustion (OOM/CPU throttling) under heavy job load. Thread creation overhead is high.

### 3. Unbounded Thread Pool (`Executors.newCachedThreadPool()`)
- **Pros**: Dynamic worker creation and reuse.
- **Cons**: Lacks backpressure or resource upper bounds. High job spikes can exhaust system threads.

### 4. `ScheduledExecutorService`
- **Pros**: Handles both timing/scheduling and execution natively within the JDK.
- **Cons**: Replaces the custom priority-queue scheduling algorithm. Since this is an educational project designed to demonstrate priority-queue-based scheduling algorithms, replacing `JobScheduler` logic with JDK built-in timers would defeat the learning objective of building a custom scheduler.

## Rationale
Using a **bounded fixed-size `ExecutorService`** paired with a custom priority-queue scheduler provides the ideal balance:
1. **Clear Responsibility Boundary**: Keeps scheduling logic (`PriorityQueue`, time checking) separated from worker management.
2. **Resource Safety**: Bounded worker counts prevent resource exhaustion regardless of how many jobs are scheduled.
3. **Educational Transparency**: Retains the custom scheduling loop while introducing standard Java concurrency primitives (`ExecutorService`, `Future`, worker threads).
