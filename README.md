# FriendSystem 👥

**A simple and efficient friends system for your Spigot Minecraft server, built using the [Imperat](https://github.com/VelixDevelopments/Imperat) command framework, and powered by MongoDB for persistent player data.**

[![GitHub license](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
## ✨ Features

* **Command System:** Built with the powerful and performant Imperat command framework.
* **Friend Management:**
    * Send and receive friend requests.
    * Accept and reject friend requests with interactive chat messages.
    * Add and remove friends from your list.
* **Persistent Data:** Utilizes MongoDB to store friend lists and player data, ensuring data survives server restarts.
* **In-Memory Caching:** Efficiently manages player data and requests in memory for fast access and smooth gameplay.
* **Private Messaging:** Send private messages to your friends with clear, colored formatting.
* **Request Cleanup:** Automatically cleans up expired friend requests.
* **User-Friendly Commands:** Intuitive commands with helpful usage messages and interactive elements.

## 🚀 Installation

1.  **Prerequisites:**
    * A running **Spigot 1.8.8** server.
    * Access to a MongoDB instance (local or remote).
2.  **Download:**
    * Download the latest `FriendSystem.jar` from the [releases page](https://github.com/Ch2mpion/FriendSystem/releases/tag/Plugin).
3.  **Place in Plugins Folder:**
    * Drag and drop the downloaded `FriendSystem.jar` file into your server's `plugins/` directory.
4.  **Configure MongoDB:**
    * Start your server once to generate the `config.yml` file in `plugins/FriendSystem/`.
    * Open `plugins/FriendSystem/config.yml` and configure your MongoDB connection details:
        ```yaml
        mongodb:
          uri: "mongodb://localhost:27017" # Your MongoDB connection URI
          database: "friendsDB"           # The name of the database to use
        ```
        **Important:** Ensure there is a space after the colon for `uri:` and `database:`.
5.  **Restart/Reload:**
    * Restart your server, or use a plugin manager to load `FriendSystem`.

## ⚙️ Configuration

The `config.yml` file allows you to define your MongoDB connection:

```yaml
# config.yml located in plugins/FriendSystem/

mongodb:
  uri: "mongodb://localhost:27017" # REQUIRED: Your MongoDB connection URI.
                                   # Example for a local MongoDB: "mongodb://localhost:27017"
                                   # Example for a remote MongoDB Atlas: "mongodb+srv://user:password@cluster.mongodb.net/retryWrites=true&w=majority"
  database: "friendsDB"           # REQUIRED: The name of the database where FriendSystem data will be stored.
                                   # Ensure there is a space after the colon for both 'uri:' and 'database:'.

```
## 🎮 Commands

This section details all available commands. All commands are open for all players by default.

| Command                   | Alias    | Description                            |
| :------------------------ | :------- | :------------------------------------- |
| `/friend add <player>`    | `/f add` | Sends a friend request to a player.    |
| `/friend remove <player>` | `/f rem` | Removes a player from your friend list. |
| `/friend accept <player>` | `/f acc` | Accepts a pending friend request.      |
| `/friend reject <player>` | `/f rej` | Rejects a pending friend request.      |
| `/friend list`            | `/f list`| Displays your current friend list.     |
| `/friend requests`        | `/f req` | Shows your pending friend requests.    |
| `/message <player> <text>`| `/msg`   | Sends a private message to a friend.   |


## 🚧 Planned Features & Future Development

* **BungeeCord Support:** A BungeeCord release is planned to allow cross-server friend lists and private messaging.
* More customization options in the configuration.
* Additional friend-related features (e.g., friend toggles, status messages).

### A Message from the Author:

Remember, this project is only made to improve my skills, so it's kind of for fun. You can use it if you want, in case you want to learn or you're a server owner. So, please be respectful and no criticism.

## ❤️ Made with Love & Support

This project was crafted with passion and dedication.

**Special Thanks to:**
* **@M7MEDpro**
* **@Mqzen**
