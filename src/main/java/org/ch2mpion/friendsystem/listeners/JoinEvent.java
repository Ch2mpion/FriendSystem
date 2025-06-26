package org.ch2mpion.friendsystem.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.ch2mpion.friendsystem.FriendSystem;
import org.ch2mpion.friendsystem.core.PlayerData;
import org.ch2mpion.friendsystem.core.PlayersManager;

import java.time.Instant;
import java.util.UUID;

/**
 * Handles player join events, managing their PlayerData and online status.
 * Data is loaded from MongoDB on join.
 */
public class JoinEvent implements Listener {

    /**
     * Handles the PlayerJoinEvent.
     * When a player joins, this method asynchronously loads their PlayerData from MongoDB.
     * After loading, their online status is set to true and lastSeen timestamp is refreshed.
     *
     * @param event The PlayerJoinEvent.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();
        PlayersManager playersManager = FriendSystem.getInstance().getPlayersManager();

        // Load player data asynchronously
        playersManager.loadPlayerData(playerUuid, playerName).thenAccept(playerData -> {
            // This code runs when the Future completes, potentially on an async thread.
            // Bukkit API calls (like setOnline, setLastSeen on an online player) might need to be on main thread.
            // However, PlayerData is a custom object, so modifying it async is generally fine.
            // The PlayerData object itself can't directly interact with Bukkit API.
            // The updates to PlayerData are safe as it's within the CompletableFuture's thread.

            // Ensure the PlayerData in the cache is updated with current online status and last seen
            // (PlayerData constructor might set lastSeen, but this ensures it's fresh for existing players)
            // This update is already handled by `loadPlayerData` adding it to `playerDataByUUID`,
            // we just need to ensure the online/lastSeen status is correct after it's in the cache.
            playerData.setOnline(true);
            playerData.setLastSeen(Instant.now());

            // You might want to run this task on the main thread if you send messages or
            // interact with Bukkit API based on successful load:
            // Bukkit.getScheduler().runTask(FriendSystem.getInstance(), () -> {
            //    player.sendMessage(FriendSystem.color("&aYour data has been loaded!"));
            // });
        }).exceptionally(e -> {
            // Handle any exceptions during data loading
            FriendSystem.getInstance().getLogger().severe("Error loading player data for " + playerName + ": " + e.getMessage());
            return null; // Return null to complete the exceptionally stage
        });
    }
}