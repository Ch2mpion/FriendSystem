package org.ch2mpion.friendsystem.commands;

import dev.velix.imperat.annotations.Command;
import dev.velix.imperat.annotations.Greedy;
import dev.velix.imperat.annotations.Named;
import dev.velix.imperat.annotations.Usage;
import org.bukkit.entity.Player;
import org.ch2mpion.friendsystem.FriendSystem;
import org.ch2mpion.friendsystem.core.PlayerData; // Make sure this import is present
import org.ch2mpion.friendsystem.core.PlayersManager; // Make sure this import is present
import java.util.UUID; // Make sure this import is present


@Command({"message","msg"})
public class Messages {

    // It's good practice to get plugin instance and playersManager once
    // instead of calling getInstance() every time within the methods.
    private final FriendSystem plugin = FriendSystem.getInstance();
    private final PlayersManager playersManager = plugin.getPlayersManager();


    @Usage
    public void usage(Player sender){ // Renamed 'p' to 'sender' for clarity
        // Using plugin.color() for consistent styling
        sender.sendMessage(plugin.color("&eUsage: &b/msg <friend> <message>"));
    }

    @Usage
    public void message(Player sender, @Named("friend") Player receiver, @Greedy @Named("message") String text) {

        // Get sender's PlayerData from the manager's cache.
        // For an online player, their data should always be loaded.
        PlayerData senderData = playersManager.getPlayerData(sender.getUniqueId());

        // Basic check, though senderData should usually not be null for an online player.
        if (senderData == null) {
            sender.sendMessage(plugin.color("&cYour player data could not be loaded. Please relog."));
            return;
        }

        // Check if the sender is friends with the receiver.
        // This relies on the in-memory friend list in PlayerData.
        if (senderData.getFriends().contains(receiver.getUniqueId())) {

            // --- Message for the RECEIVER (the friend) ---
            // Example: [PM] Ch2mpion -> You: Hello there!
            String messageToReceiver = plugin.color("&9[PM] &b" + sender.getName() + " &7-> &f" + text);
            receiver.sendMessage(messageToReceiver);

            // --- Confirmation message for the SENDER ---
            // Example: [PM] You -> OtherPlayer: Hello there!
            String confirmationToSender = plugin.color("&9[PM] &7You &b-> " + receiver.getName() + ": &f" + text);
            sender.sendMessage(confirmationToSender);

        } else {
            // If they are not friends
            sender.sendMessage(plugin.color("&cYou are not friends with &b" + receiver.getName() + "&c!"));
            sender.sendMessage(plugin.color("&7You can only send private messages to players on your friend list."));
        }
    }

}