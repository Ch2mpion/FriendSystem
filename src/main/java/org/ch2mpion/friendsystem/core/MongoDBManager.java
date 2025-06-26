package org.ch2mpion.friendsystem.core; // Keeping original package, or you can change to .database

import com.mongodb.MongoException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Manages the connection to a MongoDB database for the FriendSystem plugin.
 * Handles client initialization, database connection, and provides access to
 * the 'player_data' collection for persistent player data storage.
 */
public class MongoDBManager {

    private final JavaPlugin plugin;
    private MongoClient mongoClient;
    private MongoDatabase mongoDatabase;

    private final String connectionURI;
    private final String databaseName;

    /**
     * Constructs a new MongoDBManager.
     * Initializes the MongoDB connection upon creation.
     *
     * @param plugin The main JavaPlugin instance.
     * @param connectionURI The MongoDB connection URI (e.g., "mongodb://localhost:27017").
     * @param databaseName The name of the database to use (e.g., "friendsDB").
     */
    public MongoDBManager(JavaPlugin plugin, String connectionURI, String databaseName) {
        this.plugin = plugin;
        this.connectionURI = connectionURI;
        this.databaseName = databaseName;
        initializeDatabase();
    }

    /**
     * Initializes the MongoDB client and database connection.
     * Attempts to connect and run a ping command to verify connectivity.
     */
    private void initializeDatabase() {
        try {
            mongoClient = MongoClients.create(connectionURI);
            mongoDatabase = mongoClient.getDatabase(databaseName);

            mongoDatabase.runCommand(new Document("ping", 1));

            plugin.getLogger().info("Successfully connected to MongoDB database: " + databaseName);

            // After a successful connection, ensure necessary collections and indexes are set up.
            createIndexes();

        } catch (MongoTimeoutException e) {
            plugin.getLogger().log(Level.SEVERE, "MongoDB connection timed out! Is the MongoDB server running and accessible? URI: " + connectionURI, e);
            mongoClient = null;
            mongoDatabase = null;
        } catch (MongoException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to MongoDB! Check URI, authentication, or server status. URI: " + connectionURI, e);
            mongoClient = null;
            mongoDatabase = null;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "An unexpected error occurred during MongoDB initialization with URI: " + connectionURI, e);
            mongoClient = null;
            mongoDatabase = null;
        }
    }

    /**
     * Ensures that necessary indexes are created for collections.
     * This improves query performance.
     * This method now focuses on the 'player_data' collection.
     */
    private void createIndexes() {
        if (mongoDatabase == null) {
            plugin.getLogger().warning("MongoDB database not initialized, skipping index creation.");
            return;
        }

        // --- Player Data Collection Indexes ---
        MongoCollection<Document> playerDataCollection = getPlayerDataCollection();
        if (playerDataCollection != null) {
            try {
                // Create a unique index on '_id' (which is the player's UUID string)
                // This ensures each player has only one data document.
                playerDataCollection.createIndex(new Document("_id", 1));
                // You might also want an index on 'name' if you frequently search players by name
                playerDataCollection.createIndex(new Document("name", 1));

                plugin.getLogger().info("MongoDB 'player_data' collection indexes checked/created.");

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to create indexes for 'player_data' collection: " + e.getMessage());
            }
        } else {
            plugin.getLogger().warning("Could not get 'player_data' collection, skipping index creation for it.");
        }
    }

    /**
     * Gets the current MongoDatabase instance.
     *
     * @return The MongoDatabase instance, or null if not connected.
     */
    public MongoDatabase getMongoDatabase() {
        return mongoDatabase;
    }

    /**
     * Gets the 'player_data' MongoCollection.
     * This collection is used to store persistent player data documents.
     *
     * @return The MongoCollection for 'player_data', or null if the database is not initialized.
     */
    public MongoCollection<Document> getPlayerDataCollection() {
        if (mongoDatabase == null) {
            plugin.getLogger().warning("Attempted to get 'player_data' collection, but MongoDB database is not initialized.");
            return null;
        }
        return mongoDatabase.getCollection("player_data"); // Changed from "friends"
    }

    /**
     * Closes the MongoDB client connection.
     * This should be called when the plugin is disabled to release resources.
     */
    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
            plugin.getLogger().info("MongoDB connection closed.");
        }
    }

    /**
     * Checks if the MongoDB client and database are successfully connected.
     *
     * @return true if connected, false otherwise.
     */
    public boolean isConnected() {
        return mongoClient != null && mongoDatabase != null;
    }
}