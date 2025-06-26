package org.ch2mpion.friendsystem.core;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.ch2mpion.friendsystem.FriendSystem;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService; // Add this import
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Manages all player-related data, including in-memory friend requests and
 * persistent player data (name, last seen, friend list) stored in MongoDB.
 */
public class PlayersManager {

    // --- Configuration Constants ---
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(1);

    // --- Plugin Instance ---
    private final FriendSystem friendSystem;

    // --- Data Storage (In-Memory) ---
    private final Map<UUID, PlayerData> playerDataByUUID = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Request>> incomingRequests = new ConcurrentHashMap<>();

    // --- Database Manager ---
    private final MongoDBManager mongoDBManager;

    /**
     * Constructs a new PlayersManager.
     *
     * @param mongoDBManager The MongoDBManager instance for persistent data.
     */
    public PlayersManager(MongoDBManager mongoDBManager) {
        this.friendSystem = FriendSystem.getInstance();
        this.mongoDBManager = mongoDBManager;
    }

    // Helper to get the player_data collection
    private MongoCollection<Document> getPlayerDataCollection() {
        return mongoDBManager.getPlayerDataCollection();
    }

    // --- Player Data Management (Loading/Saving from MongoDB) ---

    /**
     * Loads a player's data from MongoDB asynchronously.
     * Prioritizes the in-memory cache. If data is not in cache, it attempts to load from DB.
     * If loaded from DB, it populates the in-memory cache. If not found in DB, a new PlayerData is created.
     *
     * @param playerUuid The UUID of the player to load.
     * @param playerName The current name of the player (used for new PlayerData if not found in DB, or for logging).
     * @return A CompletableFuture that completes with the loaded or new PlayerData.
     */
    public CompletableFuture<PlayerData> loadPlayerData(UUID playerUuid, String playerName) {
        // 1. Check if data is already in cache (synchronous check)
        PlayerData cachedData = playerDataByUUID.get(playerUuid);
        if (cachedData != null) {
            return CompletableFuture.completedFuture(cachedData);
        }

        // 2. If not in cache, asynchronously load from MongoDB using the custom executor
        ExecutorService executor = friendSystem.getAsyncExecutor(); // Get the custom executor
        if (executor == null || executor.isShutdown()) {
            friendSystem.getLogger().severe("Asynchronous executor is not available or shut down. Cannot load player data for " + playerName + ".");
            // Fallback: return a CompletableFuture that completes exceptionally or with empty data.
            // For now, let's create a new PlayerData and complete the future immediately.
            // In a production environment, you might want a more robust error handling.
            PlayerData newPlayerData = new PlayerData(playerName);
            playerDataByUUID.put(playerUuid, newPlayerData); // Cache immediately as a fallback
            return CompletableFuture.completedFuture(newPlayerData);
        }


        return CompletableFuture.supplyAsync(() -> {
                    if (!mongoDBManager.isConnected()) {
                        friendSystem.getLogger().warning("MongoDB not connected. Cannot load player data for " + playerName + ". Creating new in-memory data.");
                        return new PlayerData(playerName);
                    }

                    MongoCollection<Document> collection = getPlayerDataCollection();
                    if (collection == null) {
                        friendSystem.getLogger().severe("Player data collection is null. Cannot load player data for " + playerName + ". Creating new in-memory data.");
                        return new PlayerData(playerName);
                    }

                    // Find the document by its _id (which is the player's UUID string)
                    Document doc = collection.find(Filters.eq("_id", playerUuid.toString())).first();

                    PlayerData playerData;
                    if (doc != null) {
                        playerData = new PlayerData(doc);
                        // Optionally update name if it changed (e.g., Mojang name change).
                        // Ensure PlayerData.name is mutable or handle replacement.
                        // For now, the DB's name is authoritative unless explicitly updated and saved.
                        friendSystem.getLogger().fine("Loaded player data for " + playerData.getName() + " from MongoDB.");
                    } else {
                        playerData = new PlayerData(playerName); // Use provided playerName for new data
                        friendSystem.getLogger().fine("No existing player data found for " + playerName + ". Creating new in-memory data.");
                    }
                    return playerData; // Return the loaded/new PlayerData, don't cache it here yet.
                }, executor) // Use the custom executor here
                .thenApply(playerData -> {
                    // 3. Cache the loaded/new PlayerData AFTER the asynchronous operation completes successfully.
                    // This ensures thread safety and that data is only added when fully prepared.
                    playerDataByUUID.put(playerUuid, playerData);
                    return playerData;
                });
    }

    /**
     * Saves a player's data to MongoDB asynchronously.
     * Uses upsert to either insert new data or update existing data.
     *
     * @param playerUuid The UUID of the player to save.
     * @return A CompletableFuture that completes when the save operation is done.
     */
    public CompletableFuture<Void> savePlayerData(UUID playerUuid) {
        ExecutorService executor = friendSystem.getAsyncExecutor(); // Get the custom executor
        if (executor == null || executor.isShutdown()) {
            friendSystem.getLogger().severe("Asynchronous executor is not available or shut down. Cannot save player data for " + playerUuid + ".");
            return CompletableFuture.completedFuture(null); // Return immediately if executor not ready
        }

        return CompletableFuture.runAsync(() -> {
            if (!mongoDBManager.isConnected()) {
                friendSystem.getLogger().warning("MongoDB not connected. Cannot save player data for " + playerUuid + ".");
                return;
            }

            PlayerData playerData = playerDataByUUID.get(playerUuid);
            if (playerData == null) {
                friendSystem.getLogger().warning("Attempted to save player data for " + playerUuid + " but it's not in memory cache. Skipping save.");
                return;
            }

            MongoCollection<Document> collection = getPlayerDataCollection();
            if (collection == null) {
                friendSystem.getLogger().severe("Player data collection is null. Cannot save player data for " + playerData.getName() + ".");
                return;
            }

            // Convert PlayerData to a MongoDB Document
            Document docToSave = playerData.toDocument(playerUuid);

            try {
                collection.replaceOne(Filters.eq("_id", playerUuid.toString()), docToSave,
                        new ReplaceOptions().upsert(true));
                friendSystem.getLogger().fine("Saved player data for " + playerData.getName() + " to MongoDB.");
            } catch (Exception e) {
                friendSystem.getLogger().log(Level.SEVERE, "Failed to save player data for " + playerData.getName() + " to MongoDB.", e);
            }
        }, executor); // Use the custom executor here
    }

    /**
     * Removes a player's data from the in-memory cache.
     * This should be called after saving on quit or after temporary loads for offline players.
     *
     * @param playerUuid The UUID of the player to remove.
     */
    public void removePlayerFromCache(UUID playerUuid) {
        playerDataByUUID.remove(playerUuid);
        // Also remove any pending incoming requests for this player
        incomingRequests.remove(playerUuid);
        friendSystem.getLogger().fine("Removed player " + playerUuid + " from in-memory cache.");
    }

    // --- In-Memory Player Data Access ---

    public boolean playerExists(UUID uuid) {
        return playerDataByUUID.containsKey(uuid);
    }

    public void addPlayer(UUID uuid, PlayerData playerData) {
        playerDataByUUID.put(uuid, playerData);
    }

    public PlayerData getPlayerData(UUID uuid) {
        return playerDataByUUID.get(uuid);
    }

    public void updatePlayerData(UUID uuid, Consumer<PlayerData> updateAction) {
        playerDataByUUID.computeIfPresent(uuid, (k, oldData) -> {
            updateAction.accept(oldData);
            return oldData;
        });
    }

    // --- In-Memory Friend Request Management ---

    /**
     * Adds a friend request from requester to requested.
     * Requests are only stored in-memory.
     *
     * @param requesterId The UUID of the player sending the request.
     * @param requestedId The UUID of the player receiving the request.
     */
    public void addRequest(UUID requesterId, UUID requestedId) {
        incomingRequests.computeIfAbsent(requestedId, k -> ConcurrentHashMap.newKeySet())
                .add(new Request(requesterId, requestedId, Instant.now()));
    }

    /**
     * Gets all incoming friend requests for a specific player.
     *
     * @param uuid The UUID of the player whose requests are to be retrieved.
     * @return A Set of Request objects, or an empty set if none.
     */
    public Set<Request> getRequests(UUID uuid) {
        return incomingRequests.getOrDefault(uuid, Collections.emptySet());
    }

    /**
     * Checks if a specific friend request exists from a requester to a requested player.
     *
     * @param requesterId The UUID of the player who sent the request.
     * @param requestedId The UUID of the player who received the request.
     * @return true if the request exists, false otherwise.
     */
    public boolean hasIncomingRequest(UUID requesterId, UUID requestedId) {
        Set<Request> requestsForTarget = incomingRequests.get(requestedId);
        if (requestsForTarget == null || requestsForTarget.isEmpty()) {
            return false;
        }
        for (Request request : requestsForTarget) {
            if (request.getRequester().equals(requesterId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes a specific friend request from the in-memory store.
     *
     * @param requesterId The UUID of the player who sent the request.
     * @param requestedId The UUID of the player who received the request.
     * @return true if the request was successfully removed, false otherwise.
     */
    public boolean removeRequest(UUID requesterId, UUID requestedId) {
        Set<Request> requestsForRequested = incomingRequests.get(requestedId);
        if (requestsForRequested == null) {
            return false;
        }
        // Create a dummy Request object for removal; Request's equals() must check requester/requested
        Request requestToRemove = new Request(requesterId, requestedId, Instant.now()); // Time doesn't matter for equals
        boolean removed = requestsForRequested.remove(requestToRemove);
        if (requestsForRequested.isEmpty()) {
            incomingRequests.remove(requestedId); // Clean up empty sets
        }
        return removed;
    }

    /**
     * Cleans up expired friend requests from the in-memory store.
     * Notifies players if they are online.
     */
    public void cleanUpExpiredRequests() {
        Instant now = Instant.now();
        Iterator<Map.Entry<UUID, Set<Request>>> mapIterator = incomingRequests.entrySet().iterator();
        while (mapIterator.hasNext()) {
            Map.Entry<UUID, Set<Request>> entry = mapIterator.next();
            Set<Request> requests = entry.getValue();

            Iterator<Request> requestIterator = requests.iterator();
            while (requestIterator.hasNext()) {
                Request request = requestIterator.next();
                if (Duration.between(request.getRequestTime(), now).compareTo(REQUEST_TIMEOUT) > 0) {
                    requestIterator.remove();

                    // Notify players on the main thread
                    Bukkit.getScheduler().runTask(friendSystem, () -> {
                        Player requesterPlayer = Bukkit.getPlayer(request.getRequester());
                        Player requestedPlayer = Bukkit.getPlayer(request.getRequestedId());

                        if (requesterPlayer != null && requesterPlayer.isOnline()) {
                            String targetName = (requestedPlayer != null) ? requestedPlayer.getName() : "a player";
                            requesterPlayer.sendMessage(friendSystem.color("&7Your friend request to &b&l" + targetName + "&7 has expired and was automatically removed."));
                        }
                        if (requestedPlayer != null && requestedPlayer.isOnline()) {
                            String requesterName = (requesterPlayer != null) ? requesterPlayer.getName() : "a player";
                            requestedPlayer.sendMessage(friendSystem.color("&7The friend request from &b&l" + requesterName + "&7 has expired and was automatically removed."));
                        }
                    });
                }
            }
            if (requests.isEmpty()) {
                mapIterator.remove();
            }
        }
    }

    // --- Friend Relationship Operations (Operating on in-memory cache, then saved via savePlayerData) ---

    /**
     * Adds two players as friends in the in-memory cache.
     * This change must be followed by a call to savePlayerData for both players to persist.
     *
     * @param player1Id The UUID of the first player.
     * @param player2Id The UUID of the second player.
     * @return true if friendship was established/updated, false if player data not found in cache.
     */
    public boolean addFriend(UUID player1Id, UUID player2Id) {
        PlayerData data1 = playerDataByUUID.get(player1Id);
        PlayerData data2 = playerDataByUUID.get(player2Id);

        if (data1 == null || data2 == null) {
            friendSystem.getLogger().warning("Attempted to add friends but PlayerData not found for one or both in cache: " + player1Id + ", " + player2Id);
            return false;
        }

        data1.addFriend(player2Id);
        data2.addFriend(player1Id);
        friendSystem.getLogger().fine(data1.getName() + " and " + data2.getName() + " are now friends (in-memory).");
        return true;
    }

    /**
     * Removes friendship between two players in the in-memory cache.
     * This change must be followed by a call to savePlayerData for both players to persist.
     *
     * @param player1Id The UUID of the first player.
     * @param player2Id The UUID of the second player.
     * @return true if friendship was removed/updated, false if player data not found in cache.
     */
    public boolean removeFriend(UUID player1Id, UUID player2Id) {
        PlayerData data1 = playerDataByUUID.get(player1Id);
        PlayerData data2 = playerDataByUUID.get(player2Id);

        if (data1 == null || data2 == null) {
            friendSystem.getLogger().warning("Attempted to remove friends but PlayerData not found for one or both in cache: " + player1Id + ", " + player2Id);
            return false;
        }

        data1.removeFriend(player2Id);
        data2.removeFriend(player1Id);
        friendSystem.getLogger().fine(data1.getName() + " and " + data2.getName() + " are no longer friends (in-memory).");
        return true;
    }

    /**
     * Gets the list of friend UUIDs for a given player from the in-memory cache.
     *
     * @param playerId The UUID of the player.
     * @return A List of friend UUIDs, or an empty list if data not found or no friends.
     */
    public List<UUID> getFriendList(UUID playerId) {
        PlayerData data = playerDataByUUID.get(playerId);
        if (data != null) {
            return data.getFriends();
        }
        return Collections.emptyList();
    }

    /**
     * Checks if two players are friends based on the in-memory cache.
     * Assumes friendship is reciprocal.
     *
     * @param player1Id The UUID of the first player.
     * @param player2Id The UUID of the second player.
     * @return true if they are friends, false otherwise or if player data is not loaded.
     */
    public boolean areFriendsInCache(UUID player1Id, UUID player2Id) {
        PlayerData data1 = playerDataByUUID.get(player1Id);
        // Only need to check one direction if friendship is always reciprocal in the list
        return data1 != null && data1.getFriends().contains(player2Id);
    }
}