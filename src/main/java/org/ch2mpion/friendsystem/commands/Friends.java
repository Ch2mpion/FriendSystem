package org.ch2mpion.friendsystem.commands;

import dev.velix.imperat.BukkitSource;
import dev.velix.imperat.annotations.Command;
import dev.velix.imperat.annotations.Description;
import dev.velix.imperat.annotations.Named;
import dev.velix.imperat.annotations.SubCommand;
import dev.velix.imperat.annotations.Usage;
import dev.velix.imperat.help.CommandHelp;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.ch2mpion.friendsystem.FriendSystem;
import org.ch2mpion.friendsystem.core.PlayerData;
import org.ch2mpion.friendsystem.core.PlayersManager;
import org.ch2mpion.friendsystem.core.Request;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.stream.Collectors;

@Command({"friend", "f", "fri"})
public class Friends {

    private final FriendSystem plugin;
    private final PlayersManager playersManager;

    public Friends() {
        this.plugin = FriendSystem.getInstance();
        this.playersManager = plugin.getPlayersManager();
    }

    @Usage
    @Description("Friend commands.")
    public void friend(BukkitSource source, CommandHelp help) {
        help.display(source);
    }

    @SubCommand("add")
    @Description("Send a friend request.")
    public void add(Player sender, @Named("player") Player targetPlayer) {
        UUID senderUuid = sender.getUniqueId();
        UUID targetUuid = targetPlayer.getUniqueId();

        if (senderUuid.equals(targetUuid)) {
            sender.sendMessage(plugin.color("&cYou cannot send a friend request to yourself!"));
            return;
        }

        playersManager.loadPlayerData(targetUuid, targetPlayer.getName())
                .thenAccept(targetPlayerData -> {
                    Bukkit.getScheduler().runTask(plugin, () -> { // Ensure messages are on main thread
                        if (playersManager.areFriendsInCache(senderUuid, targetUuid)) {
                            sender.sendMessage(plugin.color("&aYou are already friends with &b&l" + targetPlayer.getName() + "&a."));
                            return;
                        }

                        if (playersManager.hasIncomingRequest(senderUuid, targetUuid)) {
                            sender.sendMessage(plugin.color("&7You have already sent a friend request to &b&l" + targetPlayer.getName() + "&7."));
                            return;
                        }

                        if (playersManager.hasIncomingRequest(targetUuid, senderUuid)) {
                            sender.sendMessage(plugin.color("&a" + targetPlayer.getName() + " &7has already sent you a friend request. &aAccepting now!"));
                            targetPlayer.sendMessage(plugin.color("&a" + sender.getName() + " &7has accepted your friend request! You are now friends!"));

                            playersManager.removeRequest(targetUuid, senderUuid);
                            playersManager.addFriend(senderUuid, targetUuid);

                            CompletableFuture.allOf(
                                    playersManager.savePlayerData(senderUuid),
                                    playersManager.savePlayerData(targetUuid)
                            ).exceptionally(e -> {
                                plugin.getLogger().log(Level.SEVERE, "Failed to auto-accept friend request and save data: " + e.getMessage(), e);
                                Bukkit.getScheduler().runTask(plugin, () ->
                                        sender.sendMessage(plugin.color("&cAn error occurred while accepting request. Please try again.")));
                                return null;
                            });
                            return;
                        }

                        playersManager.addRequest(senderUuid, targetUuid);
                        sender.sendMessage(plugin.color("&aYou sent a friend request to &b" + targetPlayer.getName() + "&a."));

                        TextComponent message = new TextComponent(plugin.color("&a" + sender.getName() + " &7has sent you a friend request! "));
                        TextComponent acceptButton = new TextComponent(plugin.color("&a&l[ACCEPT]"));
                        acceptButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/friend accept " + sender.getName()));
                        acceptButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(plugin.color("&aClick to accept request from " + sender.getName())).create()));
                        TextComponent separator = new TextComponent(plugin.color(" &7| "));
                        TextComponent rejectButton = new TextComponent(plugin.color("&c&l[REJECT]"));
                        rejectButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/friend reject " + sender.getName()));
                        rejectButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(plugin.color("&cClick to reject request from " + sender.getName())).create()));

                        message.addExtra(acceptButton);
                        message.addExtra(separator);
                        message.addExtra(rejectButton);

                        targetPlayer.spigot().sendMessage(message);
                    });
                }).exceptionally(e -> {
                    plugin.getLogger().log(Level.SEVERE, "Error in /friend add command for " + sender.getName() + " to " + targetPlayer.getName() + ": " + e.getMessage(), e);
                    Bukkit.getScheduler().runTask(plugin, () ->
                            sender.sendMessage(plugin.color("&cAn internal error occurred. Please try again later.")));
                    return null;
                });
    }

    @SubCommand({"remove","rem"})
    @Description("Remove a friend.")
    public void remove(Player sender, @Named("player") OfflinePlayer targetPlayer) {
        UUID senderUuid = sender.getUniqueId();
        UUID targetUuid = targetPlayer.getUniqueId();
        String targetName = targetPlayer.getName() != null ? targetPlayer.getName() : targetUuid.toString().substring(0, 8);

        playersManager.loadPlayerData(targetUuid, targetName)
                .thenAccept(targetPlayerData -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!playersManager.areFriendsInCache(senderUuid, targetUuid)) {
                            sender.sendMessage(plugin.color("&cYou are not friends with &b&l" + targetName + "&c."));
                            return;
                        }

                        playersManager.removeFriend(senderUuid, targetUuid);

                        CompletableFuture.allOf(
                                playersManager.savePlayerData(senderUuid),
                                playersManager.savePlayerData(targetUuid)
                        ).thenRun(() -> {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                sender.sendMessage(plugin.color("&aYou removed &b" + targetName + " &afrom your friend list."));
                                if (targetPlayer.isOnline()) {
                                    targetPlayer.getPlayer().sendMessage(plugin.color("&7" + sender.getName() + " &c has removed you from their friend list."));
                                }
                            });
                        }).exceptionally(e -> {
                            plugin.getLogger().log(Level.SEVERE, "Failed to remove friend and save data: " + e.getMessage(), e);
                            Bukkit.getScheduler().runTask(plugin, () ->
                                    sender.sendMessage(plugin.color("&cAn error occurred while removing friend. Please try again.")));
                            return null;
                        });
                    });
                }).exceptionally(e -> {
                    plugin.getLogger().log(Level.SEVERE, "Error in /friend remove command for " + sender.getName() + " to " + targetName + ": " + e.getMessage(), e);
                    Bukkit.getScheduler().runTask(plugin, () ->
                            sender.sendMessage(plugin.color("&cAn internal error occurred. Please try again later.")));
                    return null;
                });
    }

    @SubCommand({"accept","acc"})
    @Description("Accept a friend request.")
    public void accept(Player sender, @Named("player") Player targetPlayer) {
        UUID senderUuid = sender.getUniqueId();
        UUID targetUuid = targetPlayer.getUniqueId();

        playersManager.loadPlayerData(targetUuid, targetPlayer.getName())
                .thenAccept(targetPlayerData -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!playersManager.hasIncomingRequest(targetUuid, senderUuid)) {
                            sender.sendMessage(plugin.color("&cYou don't have a pending friend request from &b&l" + targetPlayer.getName() + "&c."));
                            return;
                        }

                        if (playersManager.areFriendsInCache(senderUuid, targetUuid)) {
                            sender.sendMessage(plugin.color("&aYou are already friends with &b&l" + targetPlayer.getName() + "&a."));
                            return;
                        }

                        playersManager.removeRequest(targetUuid, senderUuid);
                        playersManager.addFriend(senderUuid, targetUuid);

                        CompletableFuture.allOf(
                                playersManager.savePlayerData(senderUuid),
                                playersManager.savePlayerData(targetUuid)
                        ).thenRun(() -> {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                sender.sendMessage(plugin.color("&aYou are now friends with &b" + targetPlayer.getName() + "&a!"));
                                targetPlayer.sendMessage(plugin.color("&a" + sender.getName() + " &7accepted your friend request! You are now friends!"));
                            });
                        }).exceptionally(e -> {
                            plugin.getLogger().log(Level.SEVERE, "Failed to accept friend request and save data: " + e.getMessage(), e);
                            Bukkit.getScheduler().runTask(plugin, () ->
                                    sender.sendMessage(plugin.color("&cAn error occurred while accepting request. Please try again.")));
                            return null;
                        });
                    });
                }).exceptionally(e -> {
                    plugin.getLogger().log(Level.SEVERE, "Error in /friend accept command for " + sender.getName() + " from " + targetPlayer.getName() + ": " + e.getMessage(), e);
                    Bukkit.getScheduler().runTask(plugin, () ->
                            sender.sendMessage(plugin.color("&cAn internal error occurred. Please try again later.")));
                    return null;
                });
    }

    @SubCommand({"reject","rej"})
    @Description("Reject a friend request.")
    public void reject(Player sender, @Named("player") OfflinePlayer targetPlayer) {
        UUID senderUuid = sender.getUniqueId();
        UUID targetUuid = targetPlayer.getUniqueId();
        String targetName = targetPlayer.getName() != null ? targetPlayer.getName() : targetUuid.toString().substring(0, 8);

        playersManager.loadPlayerData(targetUuid, targetName)
                .thenAccept(targetPlayerData -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!playersManager.hasIncomingRequest(targetUuid, senderUuid)) {
                            sender.sendMessage(plugin.color("&cYou don't have a pending friend request from &b&l" + targetName + "&c."));
                            return;
                        }

                        boolean removed = playersManager.removeRequest(targetUuid, senderUuid);

                        if (removed) {
                            sender.sendMessage(plugin.color("&7You have rejected &b&l" + targetName + "&7's friend request."));
                            if (targetPlayer.isOnline()) {
                                targetPlayer.getPlayer().sendMessage(plugin.color("&7Your friend request to &b&l" + sender.getName() + "&7 has been &cRejected&7."));
                            }
                        } else {
                            sender.sendMessage(plugin.color("&cFailed to reject friend request. It might have already expired or been removed."));
                        }
                    });
                }).exceptionally(e -> {
                    plugin.getLogger().log(Level.SEVERE, "Error in /friend reject command for " + sender.getName() + " from " + targetName + ": " + e.getMessage(), e);
                    Bukkit.getScheduler().runTask(plugin, () ->
                            sender.sendMessage(plugin.color("&cAn internal error occurred. Please try again later.")));
                    return null;
                });
    }

    @SubCommand("list")
    @Description("View your friend list.")
    public void list(Player sender) {
        PlayerData senderPD = playersManager.getPlayerData(sender.getUniqueId());

        if (senderPD == null) {
            sender.sendMessage(plugin.color("&cYour player data could not be loaded. Please relog."));
            return;
        }

        List<UUID> friendUuids = senderPD.getFriends();

        sender.sendMessage(plugin.color("&b&lYOUR FRIENDS &7(" + friendUuids.size() + ")"));
        sender.sendMessage(plugin.color("&7------------------------------------------"));

        if (friendUuids.isEmpty()) {
            sender.sendMessage(plugin.color("&7You don't have any friends yet! Use &b/friend add &e<player> &7to make new friends!"));
            sender.sendMessage(plugin.color("&7------------------------------------------")); // Send bottom separator immediately if no friends
        } else {
            List<CompletableFuture<Void>> friendLoadFutures = new ArrayList<>();

            for (UUID friendUUID : friendUuids) {
                CompletableFuture<Void> future = playersManager.loadPlayerData(friendUUID, null)
                        .thenAccept(friendPD -> {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                String friendName = friendPD.getName();
                                String lastSeenStatus = friendPD.getLastSeen();
                                String location = "";

                                if (friendPD.isOnline()) {
                                    Player onlineFriend = Bukkit.getPlayer(friendUUID);
                                    if (onlineFriend != null && onlineFriend.isOnline() && onlineFriend.getWorld() != null) {
                                        String worldName = onlineFriend.getWorld().getName();

                                        location = " &7at &e" + worldName;
                                    }
                                    sender.sendMessage(plugin.color("&a" + friendName + " &a[ONLINE]" + location));
                                } else {
                                    sender.sendMessage(plugin.color("&7" + friendName + " &c[OFFLINE] &7Last seen: &f" + lastSeenStatus));
                                }
                            });
                        })
                        .exceptionally(e -> {
                            plugin.getLogger().log(Level.SEVERE, "Error loading data for friend " + friendUUID + " for " + sender.getName() + ": " + e.getMessage(), e);
                            Bukkit.getScheduler().runTask(plugin, () ->
                                    sender.sendMessage(plugin.color("&cError: Could not load data for a friend.")));
                            return null;
                        });
                friendLoadFutures.add(future);
            }

            CompletableFuture.allOf(friendLoadFutures.toArray(new CompletableFuture[0]))
                    .whenComplete((v, ex) -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (ex != null) {
                                plugin.getLogger().log(Level.SEVERE, "One or more friend data loads completed exceptionally: " + ex.getMessage(), ex);
                            }
                            sender.sendMessage(plugin.color("&7------------------------------------------"));
                        });
                    });
        }
    }

    @SubCommand({"requests","req"})
    @Description("View pending friend requests.")
    public void requests(Player sender) {
        UUID senderUuid = sender.getUniqueId();
        Set<Request> incomingRequests = playersManager.getRequests(senderUuid);

        sender.sendMessage(plugin.color("&b&lYOUR FRIEND REQUESTS &7(" + incomingRequests.size() + ")"));
        sender.sendMessage(plugin.color("&7------------------------------------------"));

        if (incomingRequests.isEmpty()) {
            sender.sendMessage(plugin.color("&7You have no pending friend requests."));
            sender.sendMessage(plugin.color("&7------------------------------------------")); // Send separator immediately
        } else {
            List<CompletableFuture<Void>> requestDisplayFutures = new ArrayList<>();

            for (Request request : incomingRequests) {
                UUID requesterUuid = request.getRequester();

                // Load PlayerData to get the requester's name
                CompletableFuture<Void> future = playersManager.loadPlayerData(requesterUuid, null)
                        .thenAccept(requesterPD -> {
                            Bukkit.getScheduler().runTask(plugin, () -> { // Schedule message on main thread
                                String requesterName = requesterPD.getName();

                                TextComponent message = new TextComponent(plugin.color("&7From: &b" + requesterName + " "));
                                TextComponent acceptButton = new TextComponent(plugin.color("&a&l[ACCEPT]"));
                                acceptButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/friend accept " + requesterName));
                                acceptButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(plugin.color("&aClick to accept request from " + requesterName)).create()));

                                TextComponent separator = new TextComponent(plugin.color(" &7| "));

                                TextComponent rejectButton = new TextComponent(plugin.color("&c&l[REJECT]"));
                                rejectButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/friend reject " + requesterName));
                                rejectButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(plugin.color("&cClick to reject request from " + requesterName)).create()));

                                message.addExtra(acceptButton);
                                message.addExtra(separator);
                                message.addExtra(rejectButton);

                                sender.spigot().sendMessage(message);
                            });
                        }).exceptionally(e -> {
                            plugin.getLogger().log(Level.SEVERE, "Error loading requester data for requests list for " + sender.getName() + ": " + e.getMessage(), e);
                            Bukkit.getScheduler().runTask(plugin, () ->
                                    sender.sendMessage(plugin.color("&cError: Could not load details for a pending request.")));
                            return null;
                        });
                requestDisplayFutures.add(future);
            }

            // Wait for all request details to be displayed before sending the final separator
            CompletableFuture.allOf(requestDisplayFutures.toArray(new CompletableFuture[0]))
                    .whenComplete((v, ex) -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (ex != null) {
                                plugin.getLogger().log(Level.SEVERE, "One or more request display tasks failed: " + ex.getMessage(), ex);
                            }
                            sender.sendMessage(plugin.color("&7------------------------------------------")); // THIS LINE IS NOW SENT LAST
                        });
                    });
        }
    }
}