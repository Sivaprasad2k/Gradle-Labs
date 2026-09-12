package com.siva.jobscheduler.persistence.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;

import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import com.siva.jobscheduler.domain.*;
import com.siva.jobscheduler.persistence.ExecutionRepository;
import com.siva.jobscheduler.persistence.JobRepository;
import com.siva.jobscheduler.persistence.mapper.DocumentMapper;
import org.bson.Document;

import java.time.Instant;
import java.util.*;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;

public class MongoExecutionRepository implements ExecutionRepository {
    private final MongoCollection<Document> collection;
    private final JobRepository jobRepository;

    public MongoExecutionRepository(MongoDatabase database, JobRepository jobRepository) {
        Objects.requireNonNull(database, "MongoDatabase cannot be null");
        this.jobRepository = Objects.requireNonNull(jobRepository, "JobRepository cannot be null");
        this.collection = database.getCollection("executions");

        // Indexes
        this.collection.createIndex(Indexes.compoundIndex(Indexes.ascending("jobId"), Indexes.ascending("occurrenceNumber")),
                new IndexOptions().unique(true));
        this.collection.createIndex(Indexes.ascending("status"));
        this.collection.createIndex(Indexes.ascending("scheduledAt"));
    }

    @Override
    public void save(JobExecution execution) {
        Objects.requireNonNull(execution, "JobExecution cannot be null");
        Document doc = DocumentMapper.toDocument(execution);
        collection.replaceOne(eq("_id", execution.getExecutionId()), doc, new ReplaceOptions().upsert(true));
    }

    @Override
    public Optional<JobExecution> findByExecutionId(String executionId) {
        if (executionId == null) {
            return Optional.empty();
        }
        Document doc = collection.find(eq("_id", executionId)).first();
        if (doc == null) {
            return Optional.empty();
        }
        return mapToExecution(doc);
    }

    @Override
    public List<JobExecution> findByJobId(String jobId) {
        if (jobId == null) {
            return Collections.emptyList();
        }
        List<JobExecution> list = new ArrayList<>();
        for (Document doc : collection.find(eq("jobId", jobId))) {
            mapToExecution(doc).ifPresent(list::add);
        }
        return list;
    }

    @Override
    public List<JobExecution> findActiveExecutions() {
        List<JobExecution> list = new ArrayList<>();
        List<String> activeStatuses = List.of(JobStatus.BLOCKED.name(), JobStatus.SCHEDULED.name(), JobStatus.RUNNING.name());
        for (Document doc : collection.find(in("status", activeStatuses))) {
            mapToExecution(doc).ifPresent(list::add);
        }
        return list;
    }

    @Override
    public List<JobExecution> findAll() {
        List<JobExecution> list = new ArrayList<>();
        for (Document doc : collection.find()) {
            mapToExecution(doc).ifPresent(list::add);
        }
        return list;
    }

    private Optional<JobExecution> mapToExecution(Document doc) {
        String jobId = doc.getString("jobId");
        int occurrenceNumber = doc.getInteger("occurrenceNumber", 1);
        Optional<Job> jobOpt = jobRepository.findById(jobId);
        if (jobOpt.isEmpty()) {
            return Optional.empty();
        }

        Job job = jobOpt.get();
        JobExecution execution = new JobExecution(job, occurrenceNumber);

        String statusStr = doc.getString("status");
        if (statusStr != null) {
            JobStatus targetStatus = JobStatus.valueOf(statusStr);
            Date startedDate = doc.getDate("startedAt");
            Date completedDate = doc.getDate("completedAt");
            String failureMsg = doc.getString("failure");
            Throwable failureError = failureMsg != null ? new RuntimeException(failureMsg) : null;

            Instant startedAt = startedDate != null ? startedDate.toInstant() : null;
            Instant completedAt = completedDate != null ? completedDate.toInstant() : null;

            // Direct transition for reconstruction
            if (targetStatus == JobStatus.RUNNING && startedAt != null) {
                execution.markRunning(startedAt);
            } else if (targetStatus == JobStatus.CANCELLED) {
                execution.markCancelled();
            } else if (targetStatus == JobStatus.COMPLETED && completedAt != null) {
                execution.markRunning(startedAt != null ? startedAt : completedAt);
                execution.markCompleted(completedAt);
            } else if (targetStatus == JobStatus.FAILED && completedAt != null) {
                execution.markRunning(startedAt != null ? startedAt : completedAt);
                execution.markFailed(completedAt, failureError);
            }
        }

        return Optional.of(execution);
    }
}
