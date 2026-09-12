package com.siva.jobscheduler.persistence;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

import java.util.Objects;

/**
 * Manages the MongoDB MongoClient lifecycle and provides access to MongoDatabase collections.
 * Supports environment-driven URI configuration (MONGODB_URI) for local and Atlas connections.
 */
public class MongoConnectionManager implements AutoCloseable {
    private final MongoClient mongoClient;
    private final MongoDatabase database;
    private static final String DEFAULT_URI = "mongodb://localhost:27017";
    private static final String DEFAULT_DATABASE = "job_scheduler_db";

    public MongoConnectionManager(String connectionUri, String databaseName) {
        String uri = connectionUri != null && !connectionUri.isBlank() ? connectionUri :
                System.getenv().getOrDefault("MONGODB_URI", DEFAULT_URI);
        String dbName = databaseName != null && !databaseName.isBlank() ? databaseName : DEFAULT_DATABASE;

        this.mongoClient = MongoClients.create(uri);
        this.database = mongoClient.getDatabase(dbName);
    }

    public MongoConnectionManager() {
        this(null, null);
    }

    public MongoDatabase getDatabase() {
        return database;
    }

    public MongoClient getMongoClient() {
        return mongoClient;
    }

    @Override
    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }
}
