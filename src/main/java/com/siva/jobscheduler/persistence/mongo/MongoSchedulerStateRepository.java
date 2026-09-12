package com.siva.jobscheduler.persistence.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import com.siva.jobscheduler.domain.SchedulerState;
import com.siva.jobscheduler.persistence.SchedulerStateRepository;
import com.siva.jobscheduler.persistence.mapper.DocumentMapper;
import org.bson.Document;

import java.util.Objects;
import java.util.Optional;

import static com.mongodb.client.model.Filters.eq;

public class MongoSchedulerStateRepository implements SchedulerStateRepository {
    private final MongoCollection<Document> collection;
    private static final String SINGLETON_ID = "SINGLETON_SCHEDULER_STATE";

    public MongoSchedulerStateRepository(MongoDatabase database) {
        Objects.requireNonNull(database, "MongoDatabase cannot be null");
        this.collection = database.getCollection("scheduler_state");
    }

    @Override
    public void save(SchedulerState state) {
        Objects.requireNonNull(state, "SchedulerState cannot be null");
        Document doc = DocumentMapper.toDocument(state);
        collection.replaceOne(eq("_id", SINGLETON_ID), doc, new ReplaceOptions().upsert(true));
    }

    @Override
    public Optional<SchedulerState> load() {
        Document doc = collection.find(eq("_id", SINGLETON_ID)).first();
        if (doc == null) {
            return Optional.empty();
        }
        String stateStr = doc.getString("state");
        if (stateStr == null) {
            return Optional.empty();
        }
        return Optional.of(SchedulerState.valueOf(stateStr));
    }
}
