package com.siva.jobscheduler.persistence;

import com.siva.jobscheduler.domain.*;
import com.siva.jobscheduler.persistence.mapper.DocumentMapper;
import com.siva.jobscheduler.recurrence.FixedRateRecurrence;
import com.siva.jobscheduler.recurrence.RecurrenceState;
import com.siva.jobscheduler.task.TaskRegistry;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("V7 Persistence & DocumentMapper Unit Tests")
class MongoPersistenceTest {

    @Test
    @DisplayName("Should serialize and deserialize Job to BSON Document")
    void testJobDocumentMapping() {
        TaskRegistry registry = TaskRegistry.getInstance();
        registry.registerHandler("DATA_SYNC", (type, payload) -> {});

        Instant now = Instant.now();
        FixedRateRecurrence recurrence = new FixedRateRecurrence(Duration.ofMinutes(5), 10);
        Job originalJob = new Job("job-100", "Sync Job", now, () -> {}, "DATA_SYNC",
                Map.of("target", "db_backup"), Set.of("job-pre"), FailurePolicy.CONTINUE, recurrence);

        Document doc = DocumentMapper.toDocument(originalJob);

        assertEquals("job-100", doc.getString("_id"));
        assertEquals("Sync Job", doc.getString("name"));
        assertEquals("DATA_SYNC", doc.getString("taskType"));
        assertEquals("CONTINUE", doc.getString("failurePolicy"));
        assertTrue(doc.containsKey("recurrencePolicy"));

        Job reconstructed = DocumentMapper.toJob(doc, registry);

        assertEquals(originalJob.id(), reconstructed.id());
        assertEquals(originalJob.name(), reconstructed.name());
        assertEquals(originalJob.taskType(), reconstructed.taskType());
        assertEquals(originalJob.failurePolicy(), reconstructed.failurePolicy());
        assertTrue(reconstructed.isRecurring());
    }

    @Test
    @DisplayName("Should serialize and deserialize JobExecution to BSON Document")
    void testExecutionDocumentMapping() {
        Instant now = Instant.now();
        Job job = new Job("job-200", "Exec Job", now, () -> {});
        JobExecution exec = new JobExecution(job, 2);

        exec.markRunning(now);
        exec.markCompleted(now.plusSeconds(5));

        Document doc = DocumentMapper.toDocument(exec);

        assertEquals("job-200#2", doc.getString("_id"));
        assertEquals("job-200", doc.getString("jobId"));
        assertEquals(2, doc.getInteger("occurrenceNumber"));
        assertEquals("COMPLETED", doc.getString("status"));
        assertNotNull(doc.get("attempts"));

        List<Document> attempts = doc.getList("attempts", Document.class);
        assertEquals(1, attempts.size());
        assertEquals("SUCCESS", attempts.get(0).getString("outcome"));
    }

    @Test
    @DisplayName("Should serialize RecurrenceState to BSON Document")
    void testRecurrenceStateDocumentMapping() {
        Instant now = Instant.now();
        Job job = new Job("job-300", "Rec State Job", now, () -> {});
        RecurrenceState recState = new RecurrenceState(job, new FixedRateRecurrence(Duration.ofMinutes(1)));

        recState.recordOccurrence(now.plusSeconds(60));
        recState.cancelSchedule();

        Document doc = DocumentMapper.toDocument(recState);

        assertEquals("job-300", doc.getString("_id"));
        assertEquals(2, doc.getInteger("occurrenceCount"));
        assertTrue(doc.getBoolean("scheduleCancelled"));
    }

    @Test
    @DisplayName("Should serialize SchedulerState to BSON Document")
    void testSchedulerStateDocumentMapping() {
        Document doc = DocumentMapper.toDocument(SchedulerState.HALTED);

        assertEquals("SINGLETON_SCHEDULER_STATE", doc.getString("_id"));
        assertEquals("HALTED", doc.getString("state"));
        assertNotNull(doc.getDate("updatedAt"));
    }
}
