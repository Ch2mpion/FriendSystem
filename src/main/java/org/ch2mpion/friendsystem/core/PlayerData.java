package org.ch2mpion.friendsystem.core;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors; // For stream operations

import org.bson.Document; // Import MongoDB Document

/**
 * Represents the in-game data for a player in the FriendSystem plugin.
 * This includes their name, online status, last seen timestamp, and friend list.
 * This class now supports conversion to and from MongoDB BSON Documents.
 */
public class PlayerData {

    private final String name;
    private boolean online;
    private Instant lastSeen;
    private final List<UUID> friends; // Using List interface for good practice

    /**
     * Constructs new PlayerData for a player with a given name.
     * Initializes online status to false and lastSeen to the current moment.
     * This constructor is used for new players.
     *
     * @param name The name of the player.
     */
    public PlayerData(String name) {
        this.name = name;
        this.online = false;
        this.lastSeen = Instant.now();
        this.friends = new ArrayList<>();
    }

    /**
     * Constructs PlayerData from a MongoDB Document.
     * This constructor is used when loading existing player data from the database.
     *
     * @param document The MongoDB Document representing player data.
     */
    public PlayerData(Document document) {
        // Retrieve data from the document, providing defaults for robustness
        this.name = document.getString("name");
        // Online status is not persistent; it's set on join/quit
        this.online = false;
        // Convert milliseconds epoch to Instant
        this.lastSeen = Instant.ofEpochMilli(document.getLong("last_seen_millis"));

        // Convert List<String> of UUIDs from DB to List<UUID>
        List<String> friendUuidsAsString = document.getList("friends", String.class);
        if (friendUuidsAsString != null) {
            this.friends = friendUuidsAsString.stream()
                    .map(UUID::fromString)
                    .collect(Collectors.toCollection(ArrayList::new));
        } else {
            this.friends = new ArrayList<>();
        }
    }

    /**
     * Converts this PlayerData object into a MongoDB Document.
     * This method is used when saving player data to the database.
     *
     * @param playerUuid The UUID of the player, used as the document's _id.
     * @return A MongoDB Document representing this player's data.
     */
    public Document toDocument(UUID playerUuid) {
        Document document = new Document();
        document.append("_id", playerUuid.toString()); // MongoDB uses _id as primary key
        document.append("player_uuid", playerUuid.toString()); // Also store as player_uuid for queries
        document.append("name", this.name);
        // Store Instant as epoch milliseconds (Long) for easier storage in MongoDB
        document.append("last_seen_millis", this.lastSeen.toEpochMilli());

        // Convert List<UUID> to List<String> for storage
        document.append("friends", this.friends.stream()
                .map(UUID::toString)
                .collect(Collectors.toList()));
        return document;
    }


    public String getName() {
        return name;
    }

    public List<UUID> getFriends() {
        return friends;
    }

    public void addFriend(UUID friendUuid) {
        if (!friends.contains(friendUuid)) {
            friends.add(friendUuid);
        }
    }

    public void removeFriend(UUID friendUuid) {
        friends.remove(friendUuid);
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public boolean isOnline() {
        return online;
    }

    public String getLastSeen() {
        if (online) {
            return "Online";
        }

        Duration duration = Duration.between(lastSeen, Instant.now());

        if (duration.toMinutes() < 1) {
            long seconds = duration.toSeconds();
            return seconds + " second" + (seconds != 1 ? "s" : "") + " ago";
        } else if (duration.toHours() < 1) {
            long minutes = duration.toMinutes();
            return minutes + " minute" + (minutes != 1 ? "s" : "") + " ago";
        } else if (duration.toDays() < 1) {
            long hours = duration.toHours();
            return hours + " hour" + (hours != 1 ? "s" : "") + " ago";
        } else if (duration.toDays() < 7) {
            long days = duration.toDays();
            return days + " day" + (days != 1 ? "s" : "") + " ago";
        } else {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd,yyyy HH:mm z");
            ZonedDateTime zonedDateTime = lastSeen.atZone(ZoneId.systemDefault());
            return formatter.format(zonedDateTime);
        }
    }

    public void setLastSeen(Instant lastSeen) {
        this.lastSeen = lastSeen;
    }
}