# ADR-005: Job Dependencies, DAG Validation, and Workflow Control

## Status
Accepted

## Context
In Version 4 of the Job Scheduler, all registered jobs executed independently based solely on scheduled execution timestamps. As workloads grow in complexity, applications require multi-stage workflow pipelines where job execution depends on the successful completion of prerequisite jobs.

We need a workflow control mechanism that supports inter-job dependencies, validates Directed Acyclic Graph (DAG) structures, isolates blocked jobs from priority scheduling, and provides critical failure halt/resume controls.

## Decision
We will introduce:
- **ID-Based Dependencies**: Jobs declare immutable `Set<String> dependencyIds`.
- **`BLOCKED` State**: Jobs with unsatisfied dependencies enter `BLOCKED` status and are kept out of `PriorityQueue`.
- **`DependencyGraph`**: Manages dependency relationships and enforces DAG acyclicity using Depth-First Search (DFS) cycle detection during registration.
- **`FailurePolicy`**: Configures whether a permanent job failure allows independent scheduling (`CONTINUE`) or suspends new dispatches (`HALT_SCHEDULER`).
- **`SchedulerState` & `resume()`**: Supports `HALTED` state for critical failures and a domain-level `resume()` operation.

## Alternatives Considered

### 1. Storing Mutable Job Object References in `Job`
- **Pros**: Direct object navigation.
- **Cons**: Violates `Job` immutability, complicates persistent storage (V7), and creates circular reference headaches during serialization. Stable String IDs are much cleaner.

### 2. Repeatedly Polling Blocked Jobs in `PriorityQueue`
- **Pros**: Keeps all jobs in a single queue.
- **Cons**: Massive CPU waste due to repeated busy-polling of ineligible jobs. `PriorityQueue` should only contain eligible `SCHEDULED` jobs.

### 3. Overriding Dependencies with `FailurePolicy.CONTINUE`
- **Pros**: Allows downstream jobs to run even if prerequisite failed.
- **Cons**: Corrupts workflow integrity. Dependent jobs expect prerequisites to be `COMPLETED`. `CONTINUE` only allows *unrelated independent* jobs to run.

### 4. Forcibly Terminating Running Threads on HALT
- **Pros**: Immediately stops all activity.
- **Cons**: Leaves external resources in corrupted states. `HALTED` suspends *new* dispatches while allowing active worker threads to finish cleanly.

## Rationale
Decoupling dependency evaluation (`DependencyGraph`) from execution ordering (`PriorityQueue`) ensures zero busy-polling overhead, guarantees DAG safety, and provides clean workflow controls without compromising thread safety or resource stability.
