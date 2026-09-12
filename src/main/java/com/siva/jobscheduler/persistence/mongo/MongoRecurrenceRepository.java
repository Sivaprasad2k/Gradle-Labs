package com.siva.jobscheduler.persistence.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import com.siva.jobscheduler.domain.Job;
import com.siva.jobscheduler.persistence.JobRepository;
import com.siva.jobscheduler.persistence.RecurrenceRepository;
import com.siva.jobscheduler.persistence.mapper.DocumentMapper;
import com.siva.jobscheduler.recurrence.RecurrenceState;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.mongodb.client.model.Filters.eq;

public class MongoRecurrenceRepository implements RecurrenceRepository {
    private final MongoCollection<Document> collection;
    private final JobRepository jobRepository;

    public MongoRecurrenceRepository(MongoDatabase database, JobRepository jobRepository) {
        Objects.requireNonNull(database, "MongoDatabase cannot be null");
        this.jobRepository = Objects.requireNonNull(jobRepository, "JobRepository cannot be null");
        this.collection = database.getCollection("recurrence_states");
    }

    @Override
    public void save(RecurrenceState recurrenceState) {
        Objects.requireNonNull(recurrenceState, "RecurrenceState cannot be null");
        Document doc = DocumentMapper.toDocument(recurrenceState);
        collection.replaceOne(eq("_id", recurrenceState.getInitialJob().id()), doc, new ReplaceOptions().upsert(true));
    }

    @Override
    public Optional<RecurrenceState> findByJobId(String jobId) {
        if (jobId == null) {
            return Optional.empty();
        }
        Document doc = collection.find(eq("_id", jobId)).first();
        if (doc == null) {
            return Optional.empty();
        }
        return mapToRecurrenceState(doc);
    }

    @Override
    public List<RecurrenceState> findAll() {
        List<RecurrenceState> list = new ArrayList<>();
        for (Document doc : collection.find()) {
            mapToRecurrenceState(doc).ifPresent(list::add);
        }
        return list;
    }

    private Optional<RecurrenceState> mapToRecurrenceState(Document doc) {
        String jobId = doc.getString("_id");
        Optional<Job> jobOpt = jobRepository.findById(jobId);
        if (jobOpt.isEmpty()) {
            return Optional.empty();
        }

        Job job = jobOpt.get();
        RecurrenceState recState = new RecurrenceState(job, job.recurrencePolicy());

        int count = doc.getInteger("occurrenceCount", 1);
        Date lastSchedDate = doc.getDate("lastScheduledTime");
        boolean cancelled = doc.getBoolean("scheduleCancelled", false);

        if (lastSchedDate != null) {
            recState.recordOccurrence(lastSchedDate.toInstant());
        }
        // Sync count if doc contains higher occurrence count
        while (recState.getOccurrenceCount() < count && lastSchedDate != null) {
            recState.recordOccurrence(lastSchedDate.toInstant());
        }

        if (cancelled) {
            recState.cancelSchedule();
        }

        return Optional.of(recState);
    }
}
