package com.siva.jobscheduler.persistence.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import com.siva.jobscheduler.domain.Job;
import com.siva.jobscheduler.persistence.JobRepository;
import com.siva.jobscheduler.persistence.mapper.DocumentMapper;
import com.siva.jobscheduler.task.TaskRegistry;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.mongodb.client.model.Filters.eq;

public class MongoJobRepository implements JobRepository {
    private final MongoCollection<Document> collection;
    private final TaskRegistry taskRegistry;

    public MongoJobRepository(MongoDatabase database, TaskRegistry taskRegistry) {
        Objects.requireNonNull(database, "MongoDatabase cannot be null");
        this.collection = database.getCollection("jobs");
        this.taskRegistry = taskRegistry != null ? taskRegistry : TaskRegistry.getInstance();
    }

    @Override
    public void save(Job job) {
        Objects.requireNonNull(job, "Job cannot be null");
        Document doc = DocumentMapper.toDocument(job);
        collection.replaceOne(eq("_id", job.id()), doc, new ReplaceOptions().upsert(true));
    }

    @Override
    public Optional<Job> findById(String jobId) {
        if (jobId == null) {
            return Optional.empty();
        }
        Document doc = collection.find(eq("_id", jobId)).first();
        if (doc == null) {
            return Optional.empty();
        }
        return Optional.of(DocumentMapper.toJob(doc, taskRegistry));
    }

    @Override
    public List<Job> findAll() {
        List<Job> jobs = new ArrayList<>();
        for (Document doc : collection.find()) {
            jobs.add(DocumentMapper.toJob(doc, taskRegistry));
        }
        return jobs;
    }

    @Override
    public boolean deleteById(String jobId) {
        if (jobId == null) {
            return false;
        }
        return collection.deleteOne(eq("_id", jobId)).getDeletedCount() > 0;
    }
}
