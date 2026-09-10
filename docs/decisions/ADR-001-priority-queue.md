# ADR 001: PriorityQueue for Job Scheduling

## Context
The job scheduler requires a data structure to hold registered jobs and efficiently retrieve the next due job based on its absolute scheduled time.

## Decision
We elected to use `java.util.PriorityQueue` as the core data structure backing the `JobScheduler`. 

The `Job` domain object implements `Comparable<Job>`, comparing instances purely on their `scheduledAt` timestamp (ascending).

## Rationale
A priority queue naturally aligns with the fundamental requirement of scheduling: we always need to inspect or extract the item with the highest priority (the earliest scheduled time) before any others. 

### Complexity Characteristics
- **`peek()`**: `O(1)` - Retrieving the earliest scheduled job without removing it is a constant-time operation. This allows the scheduler to rapidly evaluate if the next job is due.
- **`offer()` / `add()`**: `O(log n)` - Inserting a new job maintains the heap structure efficiently.
- **`poll()`**: `O(log n)` - Removing the earliest scheduled job requires a structural readjustment of the heap, remaining highly performant even for large job volumes.

## Alternatives Considered
- **Sorted `List` (e.g., `ArrayList` + `Collections.sort`)**: Would require `O(n log n)` sorting upon every insertion or `O(n)` shifting during manual sorted insertion. Retrieving the head would be `O(1)` (or `O(n)` if removing from the front of an ArrayList). This is vastly inefficient for a dynamic scheduling queue.
- **`TreeSet`**: Provides `O(log n)` for addition and removal. However, it requires elements to be strictly unique according to their comparator (or `equals`). If two different jobs are scheduled at the exact same millisecond, standard `TreeSet` comparators might overwrite or reject one unless the comparator falls back to a secondary unique identifier (like the job ID). `PriorityQueue` easily allows duplicates and is generally faster due to its array-based heap structure.

## Consequences
- The scheduler correctly and efficiently processes jobs in strict chronological order.
- The heap structure handles overlapping schedules (jobs scheduled at the exact same time) without data loss or exceptions.
