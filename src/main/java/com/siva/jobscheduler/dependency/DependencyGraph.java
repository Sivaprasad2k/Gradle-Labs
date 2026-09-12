package com.siva.jobscheduler.dependency;

import com.siva.jobscheduler.domain.Job;
import com.siva.jobscheduler.domain.JobExecution;
import com.siva.jobscheduler.domain.JobStatus;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages dependency relationships between jobs using stable Job IDs.
 * Validates DAG property using DFS cycle detection and evaluates dependency satisfaction.
 */
public class DependencyGraph {
    private final Map<String, Set<String>> dependencies = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> dependents = new ConcurrentHashMap<>();
    private final Map<String, Job> registeredJobs = new ConcurrentHashMap<>();

    /**
     * Adds a job and its declared dependencies to the graph.
     * Throws IllegalArgumentException if a dependency is unknown, self-referential, or creates a cycle.
     */
    public synchronized void addJob(Job job) {
        Objects.requireNonNull(job, "Job cannot be null");
        String jobId = job.id();

        // 1. Self-dependency check
        if (job.dependencyIds().contains(jobId)) {
            throw new IllegalArgumentException("Self-dependency detected: Job " + jobId + " cannot depend on itself");
        }

        // 2. Unknown dependency check
        for (String depId : job.dependencyIds()) {
            if (!registeredJobs.containsKey(depId)) {
                throw new IllegalArgumentException("Unknown dependency ID '" + depId + "' declared for job " + jobId);
            }
        }

        // Add to graph temporarily for cycle validation
        registeredJobs.put(jobId, job);
        Set<String> normalizedDeps = Set.copyOf(job.dependencyIds());
        dependencies.put(jobId, normalizedDeps);

        for (String depId : normalizedDeps) {
            dependents.computeIfAbsent(depId, k -> ConcurrentHashMap.newKeySet()).add(jobId);
        }

        // 3. Cycle detection
        if (containsCycle()) {
            // Rollback temporary insertion
            dependencies.remove(jobId);
            registeredJobs.remove(jobId);
            for (String depId : normalizedDeps) {
                Set<String> depSet = dependents.get(depId);
                if (depSet != null) {
                    depSet.remove(jobId);
                }
            }
            throw new IllegalArgumentException("Dependency cycle detected involving job " + jobId);
        }
    }

    /**
     * Evaluates if all dependencies of a job have completed successfully.
     */
    public synchronized boolean isSatisfied(String jobId, Map<String, JobExecution> executions) {
        Set<String> depIds = dependencies.getOrDefault(jobId, Collections.emptySet());
        if (depIds.isEmpty()) {
            return true;
        }
        for (String depId : depIds) {
            JobExecution depExecution = executions.get(depId);
            if (depExecution == null || depExecution.getStatus() != JobStatus.COMPLETED) {
                return false;
            }
        }
        return true;
    }

    public synchronized Set<String> getDependents(String jobId) {
        Set<String> deps = dependents.get(jobId);
        return deps != null ? Set.copyOf(deps) : Collections.emptySet();
    }

    public synchronized Set<String> getDependencies(String jobId) {
        Set<String> deps = dependencies.get(jobId);
        return deps != null ? Set.copyOf(deps) : Collections.emptySet();
    }

    private boolean containsCycle() {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (String node : registeredJobs.keySet()) {
            if (dfsCycle(node, visited, recursionStack)) {
                return true;
            }
        }
        return false;
    }

    private boolean dfsCycle(String current, Set<String> visited, Set<String> recursionStack) {
        if (recursionStack.contains(current)) {
            return true; // Cycle found
        }
        if (visited.contains(current)) {
            return false;
        }

        visited.add(current);
        recursionStack.add(current);

        Set<String> deps = dependencies.getOrDefault(current, Collections.emptySet());
        for (String dep : deps) {
            if (dfsCycle(dep, visited, recursionStack)) {
                return true;
            }
        }

        recursionStack.remove(current);
        return false;
    }
}
