package org.ch2mpion.friendsystem;

import dev.velix.imperat.BukkitImperat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor; // Using org.bukkit.ChatColor for consistency
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.ch2mpion.friendsystem.commands.FriendHelpProvider;
import org.ch2mpion.friendsystem.commands.Friends;
import org.ch2mpion.friendsystem.commands.Messages;
import org.ch2mpion.friendsystem.core.MongoDBManager;
import org.ch2mpion.friendsystem.core.PlayersManager;
import org.ch2mpion.friendsystem.listeners.JoinEvent;
import org.ch2mpion.friendsystem.listeners.QuitEvent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Main plugin class for the FriendSystem.
 * Handles plugin startup, shutdown, database initialization, command registration,
 * event registration, and scheduled tasks.
 */
public final class FriendSystem extends JavaPlugin {

    // Singleton instance of the plugin
    private static FriendSystem instance;
    // Manages player data, friend relationships (MongoDB), and in-memory friend requests
    private static PlayersManager playersManager;
    // Manages the MongoDB connection
    private MongoDBManager mongoDBManager;
    private ExecutorService asyncExecutor;

    /**
     * Translates '&' color codes to Minecraft's internal color codes.
     * This method is static for easy access throughout the plugin.
     *
     * @param message The string containing '&' color codes.
     * @return The colored string.
     */
    public static String color(final String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    @Override
    public void onEnable() {
        // Set the singleton instance
        instance = this;
        this.asyncExecutor = Executors.newCachedThreadPool();

        // Save the default configuration file if it doesn't exist
        saveDefaultConfig();

        // --- Database Initialization ---
        setupMongoDB();

        // If MongoDB connection fails, disable the plugin
        if (mongoDBManager == null || !mongoDBManager.isConnected()) {
            getLogger().severe("Failed to connect to MongoDB! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return; // Stop plugin loading
        }

        // Initialize PlayersManager, passing the MongoDBManager for friend relationships
        // CRITICAL FIX: Pass mongoDBManager to PlayersManager constructor
        playersManager = new PlayersManager(mongoDBManager);

        // --- Imperat Command Registration ---
        // Build and register Imperat with the custom help provider
        BukkitImperat imperat = BukkitImperat.builder(this)
                .helpProvider(new FriendHelpProvider())
                .build();
        // Register all command classes
        imperat.registerCommands(new Friends(), new Messages());

        // --- Event Listener Registration ---
        getServer().getPluginManager().registerEvents(new JoinEvent(), this);
        getServer().getPluginManager().registerEvents(new QuitEvent(), this);

        // Log plugin enable success message
        getLogger().info(ChatColor.GREEN + "FriendSystem was enabled!");

        // --- Scheduled Task for In-Memory Request Cleanup ---
        // This task runs asynchronously to prevent server lag.
        // It checks for and removes expired in-memory friend requests.
        // Initial delay: 5 minutes (20 ticks/sec * 60 sec/min * 5 min = 6000 ticks)
        // Repeat period: Every 20 minutes (20 ticks/sec * 60 sec/min * 20 min = 24000 ticks)
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            // Call the correct cleanup method in PlayersManager
            playersManager.cleanUpExpiredRequests();
        }, 20L * 60 * 5, 20L * 60 * 20);
    }

    @Override
    public void onDisable() {
        // --- Database Shutdown ---
        // Close the MongoDB connection if it was established
        if (mongoDBManager != null) {
            mongoDBManager.close();
        }

        if (asyncExecutor != null) {
            asyncExecutor.shutdown();
            try {
                if (!asyncExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    getLogger().warning("Asynchronous executor did not terminate gracefully. Forcing shutdown.");
                    asyncExecutor.shutdownNow();
                    if (!asyncExecutor.awaitTermination(10, TimeUnit.SECONDS))
                        getLogger().severe("Asynchronous executor did not terminate.");
                }
            } catch (InterruptedException ie) {
                asyncExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Log plugin disable success message
        getLogger().info(ChatColor.RED + "FriendSystem was disabled!");
    }

    /**
     * Initializes the MongoDB connection based on values from config.yml.
     */
    private void setupMongoDB() {
        FileConfiguration config = getConfig();
        String uri = config.getString("mongodb.uri", "mongodb://localhost:27017");
        // Using "friendsDB" as default database name as per your previous code
        String dbName = config.getString("mongodb.database", "friendsDB");

        this.mongoDBManager = new MongoDBManager(this, uri, dbName);
    }

    /**
     * Gets the singleton instance of the PlayersManager.
     *
     * @return The PlayersManager instance.
     */
    public PlayersManager getPlayersManager() {
        return playersManager;
    }

    /**
     * Gets the singleton instance of the FriendSystem plugin.
     *
     * @return The FriendSystem instance.
     */
    public static FriendSystem getInstance() {
        return instance;
    }

    public ExecutorService getAsyncExecutor() {
        return asyncExecutor;
    }

}