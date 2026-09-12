package com.siva.jobscheduler.persistence.mapper;

import com.siva.jobscheduler.domain.*;
import com.siva.jobscheduler.recurrence.FixedRateRecurrence;
import com.siva.jobscheduler.recurrence.RecurrencePolicy;
import com.siva.jobscheduler.recurrence.RecurrenceState;
import com.siva.jobscheduler.task.TaskRegistry;
import org.bson.Document;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Utility class converting domain objects (Job, JobExecution, RecurrenceState, SchedulerState)
 * to and from BSON Document representations.
 */
public class DocumentMapper {

    public static Document toDocument(Job job) {
        Document doc = new Document("_id", job.id())
                .append("name", job.name())
                .append("scheduledAt", Date.from(job.scheduledAt()))
                .append("taskType", job.taskType())
                .append("taskPayload", new Document(job.taskPayload()))
                .append("dependencyIds", new ArrayList<>(job.dependencyIds()))
                .append("failurePolicy", job.failurePolicy().name());

        if (job.isRecurring() && job.recurrencePolicy() instanceof FixedRateRecurrence fr) {
            Document recDoc = new Document("type", "FIXED_RATE")
                    .append("intervalMs", fr.interval().toMillis());
            fr.maxOccurrences().ifPresent(max -> recDoc.append("maxOccurrences", max));
            fr.endTime().ifPresent(end -> recDoc.append("endTime", Date.from(end)));
            doc.append("recurrencePolicy", recDoc);
        }

        return doc;
    }

    public static Job toJob(Document doc, TaskRegistry taskRegistry) {
        String id = doc.getString("_id");
        String name = doc.getString("name");
        Date scheduledAtDate = doc.getDate("scheduledAt");
        Instant scheduledAt = scheduledAtDate != null ? scheduledAtDate.toInstant() : Instant.now();

        String taskType = doc.getString("taskType");
        Document payloadDoc = doc.get("taskPayload", Document.class);
        Map<String, Object> taskPayload = payloadDoc != null ? new HashMap<>(payloadDoc) : Collections.emptyMap();

        JobTask task = taskRegistry != null ? taskRegistry.resolveTask(taskType, taskPayload) : () -> {};

        List<String> deps = doc.getList("dependencyIds", String.class);
        Set<String> dependencyIds = deps != null ? new HashSet<>(deps) : Collections.emptySet();

        String fpStr = doc.getString("failurePolicy");
        FailurePolicy failurePolicy = fpStr != null ? FailurePolicy.valueOf(fpStr) : FailurePolicy.CONTINUE;

        RecurrencePolicy recurrencePolicy = RecurrencePolicy.none();
        Document recDoc = doc.get("recurrencePolicy", Document.class);
        if (recDoc != null && "FIXED_RATE".equals(recDoc.getString("type"))) {
            long intervalMs = recDoc.getLong("intervalMs");
            Integer maxOccurrences = recDoc.getInteger("maxOccurrences");
            Date endDate = recDoc.getDate("endTime");

            if (maxOccurrences != null) {
                recurrencePolicy = new FixedRateRecurrence(Duration.ofMillis(intervalMs), maxOccurrences);
            } else if (endDate != null) {
                recurrencePolicy = new FixedRateRecurrence(Duration.ofMillis(intervalMs), endDate.toInstant());
            } else {
                recurrencePolicy = new FixedRateRecurrence(Duration.ofMillis(intervalMs));
            }
        }

        return new Job(id, name, scheduledAt, task, taskType, taskPayload, dependencyIds, failurePolicy, recurrencePolicy);
    }

    public static Document toDocument(JobExecution execution) {
        Document doc = new Document("_id", execution.getExecutionId())
                .append("jobId", execution.getJob().id())
                .append("occurrenceNumber", execution.getOccurrenceNumber())
                .append("status", execution.getStatus().name());

        if (execution.getStartedAt() != null) {
            doc.append("startedAt", Date.from(execution.getStartedAt()));
        }
        if (execution.getCompletedAt() != null) {
            doc.append("completedAt", Date.from(execution.getCompletedAt()));
        }
        if (execution.getFailure() != null) {
            doc.append("failure", execution.getFailure().getMessage());
        }

        List<Document> attemptDocs = new ArrayList<>();
        for (ExecutionAttempt attempt : execution.getAttempts()) {
            Document attDoc = new Document("attemptNumber", attempt.attemptNumber())
                    .append("startedAt", Date.from(attempt.startedAt()))
                    .append("outcome", attempt.outcome().name());
            if (attempt.completedAt() != null) {
                attDoc.append("completedAt", Date.from(attempt.completedAt()));
            }
            if (attempt.failure() != null) {
                attDoc.append("failureError", attempt.failure().getMessage());
            }
            attemptDocs.add(attDoc);
        }
        doc.append("attempts", attemptDocs);

        return doc;
    }

    public static Document toDocument(RecurrenceState recState) {
        return new Document("_id", recState.getInitialJob().id())
                .append("occurrenceCount", recState.getOccurrenceCount())
                .append("lastScheduledTime", Date.from(recState.getLastScheduledTime()))
                .append("scheduleCancelled", recState.isScheduleCancelled());
    }

    public static Document toDocument(SchedulerState state) {
        return new Document("_id", "SINGLETON_SCHEDULER_STATE")
                .append("state", state.name())
                .append("updatedAt", Date.from(Instant.now()));
    }
}
