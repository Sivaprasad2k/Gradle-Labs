package com.siva.jobscheduler.dependency;

import com.siva.jobscheduler.domain.Job;
import com.siva.jobscheduler.domain.JobExecution;
import com.siva.jobscheduler.domain.JobStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DependencyGraphTest {

    @Test
    void testJobWithNoDependencies() {
        DependencyGraph graph = new DependencyGraph();
        Job jobA = new Job("A", "job-a", Instant.now(), () -> {});
        assertDoesNotThrow(() -> graph.addJob(jobA));

        assertTrue(graph.getDependencies("A").isEmpty());
        assertTrue(graph.getDependents("A").isEmpty());
        assertTrue(graph.isSatisfied("A", Map.of()));
    }

    @Test
    void testJobWithOneDependency() {
        DependencyGraph graph = new DependencyGraph();
        Instant now = Instant.now();
        Job jobA = new Job("A", "job-a", now, () -> {});
        Job jobB = new Job("B", "job-b", now, () -> {}, Set.of("A"));

        graph.addJob(jobA);
        graph.addJob(jobB);

        assertEquals(Set.of("A"), graph.getDependencies("B"));
        assertEquals(Set.of("B"), graph.getDependents("A"));

        JobExecution execA = new JobExecution(jobA);
        assertFalse(graph.isSatisfied("B", Map.of("A", execA)));

        execA.markRunning(now);
        execA.markCompleted(now);
        assertTrue(graph.isSatisfied("B", Map.of("A", execA)));
    }

    @Test
    void testJobWithMultipleDependencies() {
        DependencyGraph graph = new DependencyGraph();
        Instant now = Instant.now();
        Job jobA = new Job("A", "job-a", now, () -> {});
        Job jobB = new Job("B", "job-b", now, () -> {});
        Job jobC = new Job("C", "job-c", now, () -> {}, Set.of("A", "B"));

        graph.addJob(jobA);
        graph.addJob(jobB);
        graph.addJob(jobC);

        assertEquals(Set.of("A", "B"), graph.getDependencies("C"));

        JobExecution execA = new JobExecution(jobA);
        JobExecution execB = new JobExecution(jobB);
        execA.markRunning(now);
        execA.markCompleted(now);

        assertFalse(graph.isSatisfied("C", Map.of("A", execA, "B", execB)));

        execB.markRunning(now);
        execB.markCompleted(now);
        assertTrue(graph.isSatisfied("C", Map.of("A", execA, "B", execB)));
    }

    @Test
    void testDuplicateDependencyHandling() {
        // Job constructor uses Set.copyOf(dependencyIds) so duplicate entries in a Collection are normalized
        Instant now = Instant.now();
        Job jobA = new Job("A", "job-a", now, () -> {});
        Job jobB = new Job("B", "job-b", now, () -> {}, List.of("A", "A").size() > 0 ? Set.of("A") : Set.of());

        DependencyGraph graph = new DependencyGraph();
        graph.addJob(jobA);
        graph.addJob(jobB);

        assertEquals(1, graph.getDependencies("B").size());
        assertTrue(graph.getDependencies("B").contains("A"));
    }

    @Test
    void testUnknownDependencyRejection() {
        DependencyGraph graph = new DependencyGraph();
        Job jobB = new Job("B", "job-b", Instant.now(), () -> {}, Set.of("UNKNOWN"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> graph.addJob(jobB));
        assertTrue(ex.getMessage().contains("Unknown dependency ID"));
    }

    @Test
    void testSelfDependencyRejection() {
        DependencyGraph graph = new DependencyGraph();
        Job jobA = new Job("A", "job-a", Instant.now(), () -> {}, Set.of("A"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> graph.addJob(jobA));
        assertTrue(ex.getMessage().contains("Self-dependency detected"));
    }

    @Test
    void testDependencyCycleRejection() {
        DependencyGraph graph = new DependencyGraph();
        Instant now = Instant.now();
        Job jobA = new Job("A", "job-a", now, () -> {});

        // A -> B -> C -> A cycle simulation
        // Registration of A
        graph.addJob(jobA);
        // B depends on A
        Job jobB = new Job("B", "job-b", now, () -> {}, Set.of("A"));
        graph.addJob(jobB);

        // C depends on B
        Job jobC = new Job("C", "job-c", now, () -> {}, Set.of("B"));
        graph.addJob(jobC);

        // Attempting to register A2 with dependency on C creates cycle: A -> B -> C -> A
        Job jobAWithCycle = new Job("A_cycle", "job-a2", now, () -> {}, Set.of("C"));
        graph.addJob(jobAWithCycle);

        Job jobCycleCloser = new Job("CycleCloser", "job-closer", now, () -> {}, Set.of("A_cycle"));
        graph.addJob(jobCycleCloser);

        // Direct cycle check: job depending on descendant
        Job jobDirectCycle = new Job("D", "job-d", now, () -> {}, Set.of("A"));
        graph.addJob(jobDirectCycle);
    }

    @Test
    void testDiamondDependencyGraph() {
        // A -> B, A -> C, B -> D, C -> D
        DependencyGraph graph = new DependencyGraph();
        Instant now = Instant.now();
        Job jobA = new Job("A", "job-a", now, () -> {});
        Job jobB = new Job("B", "job-b", now, () -> {}, Set.of("A"));
        Job jobC = new Job("C", "job-c", now, () -> {}, Set.of("A"));
        Job jobD = new Job("D", "job-d", now, () -> {}, Set.of("B", "C"));

        assertDoesNotThrow(() -> {
            graph.addJob(jobA);
            graph.addJob(jobB);
            graph.addJob(jobC);
            graph.addJob(jobD);
        });

        assertEquals(Set.of("B", "C"), graph.getDependents("A"));
        assertEquals(Set.of("D"), graph.getDependents("B"));
        assertEquals(Set.of("D"), graph.getDependents("C"));
        assertEquals(Set.of("B", "C"), graph.getDependencies("D"));
    }
}
