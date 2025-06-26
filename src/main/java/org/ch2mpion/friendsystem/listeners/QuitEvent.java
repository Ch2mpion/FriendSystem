package org.ch2mpion.friendsystem.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.ch2mpion.friendsystem.FriendSystem;
import org.ch2mpion.friendsystem.core.PlayersManager;

import java.time.Instant;
import java.util.UUID;

/**
 * Handles player quit events, saving their PlayerData to MongoDB and updating status.
 * Data is saved to MongoDB on quit.
 */
public class QuitEvent implements Listener {

    /**
     * Handles the PlayerQuitEvent.
     * When a player quits, this method first updates their in-memory PlayerData
     * (setting offline and recording last seen), then asynchronously saves this data to MongoDB.
     * Finally, it removes the player's data from the in-memory cache.
     *
     * @param event The PlayerQuitEvent.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        PlayersManager playersManager = FriendSystem.getInstance().getPlayersManager();

        // First, update the in-memory PlayerData (set offline and update last seen)
        // This is done synchronously to ensure the in-memory data is ready for saving.
        playersManager.updatePlayerData(playerUuid, (playerData) -> {
            playerData.setOnline(false);
            playerData.setLastSeen(Instant.now());
        });

        // Now, asynchronously save the updated PlayerData to MongoDB
        playersManager.savePlayerData(playerUuid).thenRun(() -> {
            // This code runs when the save operation completes, on an async thread.
            // After successful save, remove the player's data from the in-memory cache.
            playersManager.removePlayerFromCache(playerUuid);
        }).exceptionally(e -> {
            // Handle any exceptions during data saving
            FriendSystem.getInstance().getLogger().severe("Error saving player data for " + player.getName() + " on quit: " + e.getMessage());
            // Even if save fails, remove from cache to prevent stale data for next join, or log more severely.
            playersManager.removePlayerFromCache(playerUuid);
            return null; // Complete the exceptionally stage
        });
    }
}