# ADR-007: MongoDB Persistence and Restart Recovery

## Context
In Version 6, job scheduling supported fixed-rate recurring jobs and coalescing in memory. In Version 7, we need to transition from memory as the source of truth to MongoDB as the durable source of truth without breaking V1–V6 abstractions.

## Decision
1. **MongoDB as Single Source of Truth**: MongoDB stores `jobs`, `executions`, `recurrence_states`, and `scheduler_state`. In-memory structures act as runtime projections.
2. **Task Persistence Abstraction**: `Job` includes `taskType` and `taskPayload`. `TaskRegistry` resolves executable `TaskHandler` implementations upon application startup.
3. **Interrupted Worker Recovery**: Executions left in `RUNNING` status upon JVM crash are recovered to `FAILED` with `InterruptedWorkerException`, triggering retry evaluation.
4. **Decoupled Repositories**: `JobScheduler` depends on Java repository interfaces (`JobRepository`, `ExecutionRepository`, `RecurrenceRepository`, `SchedulerStateRepository`), completely decoupling domain logic from MongoDB driver APIs.

## Status
Accepted & Implemented in V7.
