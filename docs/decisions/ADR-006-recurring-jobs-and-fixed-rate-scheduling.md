# ADR-006: Recurring Jobs and Fixed-Rate Scheduling

## Context
In Version 5, job scheduling was expanded to include DAG dependencies and workflow control policies (`CONTINUE`, `HALT_SCHEDULER`). In Version 6, we need to introduce recurring job support without replacing `JobScheduler` with `ScheduledExecutorService` or compromising existing V1–V5 invariants.

## Decision
1. **Recurrence as Optional Capability**: Recurrence is modeled via `RecurrencePolicy` interface and `FixedRateRecurrence` record attached to `Job`.
2. **Explicit Execution Identity**: `JobExecution` includes `executionId` (`jobId#occurrenceNumber`) and `occurrenceNumber` to differentiate separate occurrences of the same job.
3. **Fixed-Rate Calculation & Coalescing**: Next scheduled time is calculated from previous *scheduled* time $T_{scheduled}$. If execution is delayed past interval boundaries, missed occurrences are coalesced into a single future occurrence.
4. **No-Overlap Constraint**: At most one active `JobExecution` for a recurring job exists at any time.
5. **Isolation of Retry and Recurrence**: Retries execute within a single `JobExecution` occurrence. Recurrence spawns fresh `JobExecution` occurrences.
6. **Schedule Cancellation**: `JobScheduler.cancelRecurrence(jobId)` stops future occurrence generation without terminating active running executions.

## Status
Accepted & Implemented in V6.
