# Architecture: Version 7

## Overview
The Job Scheduler V7 introduces **durable MongoDB persistence, execution history tracking, and restart recovery**. MongoDB functions as the durable single source of truth (`jobs`, `executions`, `recurrence_states`, `scheduler_state`), while in-memory structures (`PriorityQueue`, `DependencyGraph`, `activeExecutions`) function as runtime projections reconstructed during startup.

## Package Architecture & Component Boundaries

```text
com.siva.jobscheduler/
│
├── domain/
│   ├── Job.java
│   ├── JobTask.java
│   ├── JobStatus.java
│   ├── JobExecution.java
│   ├── ExecutionAttempt.java
│   ├── FailurePolicy.java
│   └── SchedulerState.java
│
├── task/
│   ├── TaskHandler.java
│   └── TaskRegistry.java
│
├── persistence/
│   ├── JobRepository.java
│   ├── ExecutionRepository.java
│   ├── RecurrenceRepository.java
│   ├── SchedulerStateRepository.java
│   ├── MongoConnectionManager.java
│   ├── mapper/
│   │   └── DocumentMapper.java
│   └── mongo/
│       ├── MongoJobRepository.java
│       ├── MongoExecutionRepository.java
│       ├── MongoRecurrenceRepository.java
│       └── MongoSchedulerStateRepository.java
│
├── recurrence/
│   ├── RecurrencePolicy.java
│   ├── FixedRateRecurrence.java
│   └── RecurrenceState.java
│
├── dependency/
│   └── DependencyGraph.java
│
├── scheduler/
│   └── JobScheduler.java
│
├── execution/
│   └── JobExecutor.java
│
└── JobSchedulerApplication.java
```

### 1. Persistence Layer (`com.siva.jobscheduler.persistence`)
- **`DocumentMapper`**: Converts domain entities to/from BSON `Document` format.
- **`MongoConnectionManager`**: Manages `MongoClient` connection lifecycle to local or Atlas MongoDB instances.
- **`MongoRepositories`**: Implement clean Java interfaces (`JobRepository`, `ExecutionRepository`, `RecurrenceRepository`, `SchedulerStateRepository`).

### 2. Task Layer (`com.siva.jobscheduler.task`)
- **`TaskRegistry`**: Resolves persistent `taskType` String identifiers to executable `TaskHandler` lambdas upon JVM restart.

### 3. Startup Recovery Flow

```text
JVM Restart
     │
     ▼
Load SchedulerState (HALTED -> Stay HALTED)
     │
     ▼
Load Durable Jobs & Build TaskHandlers
     │
     ▼
Populate DependencyGraph & Validate DAG
     │
     ▼
Recover RUNNING Executions -> FAILED (InterruptedWorkerException)
     │
     ▼
Reconstruct RecurrenceStates & Coalesce Overdue Schedules
     │
     ▼
Rebuild PriorityQueue & Start Dispatch Loop
```
