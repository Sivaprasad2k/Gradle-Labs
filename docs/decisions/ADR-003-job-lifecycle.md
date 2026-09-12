# ADR-003: Explicit Thread-Safe Job Lifecycle and Execution State Model

## Status
Accepted

## Context
In Version 2 of the Job Scheduler, job execution state was implicit and tracked primarily through stdout log lines. The system lacked an explicit model to query runtime status (`RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`), record task execution failures, or cancel scheduled jobs before they started execution.

As the scheduler grows, applications require an explicit lifecycle model to observe runtime execution metadata and manage scheduled jobs safely across concurrent thread operations.

## Decision
We will introduce `JobStatus` (enum) and `JobExecution` (class in `com.siva.jobscheduler.domain`) to explicitly encapsulate job execution lifecycle and metadata.

- **`Job`** remains an immutable record representing the static definition of work.
- **`JobExecution`** manages the dynamic runtime state (`status`, `startedAt`, `completedAt`, `failure`) and enforces valid state transitions using `synchronized` atomic methods.

State transitions are strictly constrained to:
- `SCHEDULED -> RUNNING`
- `SCHEDULED -> CANCELLED`
- `RUNNING -> COMPLETED`
- `RUNNING -> FAILED`

Terminal states (`COMPLETED`, `FAILED`, `CANCELLED`) cannot transition to any other state.

## Alternatives Considered

### 1. Mutable `Job` Record / Object
- **Pros**: Kept domain model to a single class.
- **Cons**: Conflates static job definition attributes (`id`, `name`, `scheduledAt`, `task`) with transient runtime execution metadata. Violates immutability principles of the core domain model established in V1.

### 2. Unrestricted Status Setters (`setStatus(JobStatus)`)
- **Pros**: Simple bean getters and setters.
- **Cons**: Lacks state machine validation. Allows illegal transitions (e.g., `COMPLETED -> RUNNING`, `CANCELLED -> RUNNING`) and creates subtle concurrency bugs when multiple threads mutate state without synchronization.

### 3. External Manager (`JobStateManager` / `LifecycleManager`)
- **Pros**: Moves state management out of domain classes into service layers.
- **Cons**: Introduces unnecessary artificial abstraction layers and scatters lifecycle invariants across multiple packages.

### 4. Encapsulated State Machine inside `JobExecution` (Selected Decision)
- **Pros**: Co-locates state data and transition validation rules directly within the domain entity (`JobExecution`). Atomic synchronized transition methods guarantee thread safety during concurrent dispatch/cancellation races.

## Rationale
Encapsulating atomic state transitions inside `JobExecution` keeps the domain model clean, self-validating, and thread-safe without requiring external locking frameworks or complex state manager services.
