package io.github.pokemeetup.multiplayer;

import io.github.pokemeetup.multiplayer.server.PlayerEvents;
import io.github.pokemeetup.multiplayer.server.ServerStorageSystem;
import io.github.pokemeetup.multiplayer.server.events.EventManager;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.utils.GameLogger;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerManager {
    private final Map<String, ServerPlayer> onlinePlayers = new ConcurrentHashMap<>();
    private final Map<UUID, String> uuidToUsername = new ConcurrentHashMap<>();
    private final ServerStorageSystem storage;
    private final EventManager eventManager;

    public PlayerManager(ServerStorageSystem storage, EventManager eventManager) {
        this.storage = storage;
        this.eventManager = eventManager;
        GameLogger.info("PlayerManager initialized with ServerStorageSystem");
    }

    public ServerPlayer createOrLoadPlayer(String username) {
        try {
            UUID playerUUID = UUID.nameUUIDFromBytes(username.getBytes());

            // Load player data from storage
            PlayerData playerData = storage.getPlayerDataManager().loadPlayerData(playerUUID);
            if (playerData == null) {
                // Create new player data if none exists
                playerData = new PlayerData(username);
                // Set default starting position (e.g., x=0, y=0)
                playerData.setX(0);
                playerData.setY(0);
                playerData.setDirection("down");
                playerData.setMoving(false);
                // Save the new player data
                storage.getPlayerDataManager().savePlayerData(playerUUID, playerData);
                GameLogger.info("Created new player data for " + username + " (UUID: " + playerUUID + ")");
            } else {
                GameLogger.info("Loaded existing player data for " + username + " (UUID: " + playerUUID + ")");
            }

            // Create a ServerPlayer instance using the loaded data
            ServerPlayer player = new ServerPlayer(username, playerData);

            // Add player to online players and UUID mapping
            onlinePlayers.put(username, player);
            uuidToUsername.put(playerUUID, username);

            GameLogger.info("Player loaded/created successfully: " + username +
                " (UUID: " + playerUUID + ") at position (" + playerData.getX() + "," + playerData.getY() + ")");

            // Fire player login event
            eventManager.fireEvent(new PlayerEvents.PlayerLoginEvent(player));

            return player;

        } catch (Exception e) {
            GameLogger.error("Error creating/loading player: " + e.getMessage());
            return null;
        }
    }


    public ServerPlayer getPlayer(String username) {
        return onlinePlayers.get(username);
    }

    public ServerPlayer getPlayerByUUID(UUID uuid) {
        String username = uuidToUsername.get(uuid);
        return username != null ? onlinePlayers.get(username) : null;
    }

    public Collection<ServerPlayer> getOnlinePlayers() {
        return onlinePlayers.values();
    }

    public void removePlayer(String username) {
        ServerPlayer player = onlinePlayers.remove(username);
        if (player != null) {
            try {
                UUID playerUUID = player.getUUID();
                // Save player data before removing
                storage.getPlayerDataManager().savePlayerData(playerUUID, player.getData());
                uuidToUsername.remove(playerUUID);
                GameLogger.info("Saved player data for " + username + " (UUID: " + playerUUID + ") upon removal");
            } catch (Exception e) {
                GameLogger.error("Error saving player data for " + username + ": " + e.getMessage());
            }
        }
    }

    public void dispose() {
        for (Map.Entry<String, ServerPlayer> entry : onlinePlayers.entrySet()) {
            String username = entry.getKey();
            ServerPlayer player = entry.getValue();
            try {
                UUID playerUUID = player.getUUID();
                // Save player data using storage
                storage.getPlayerDataManager().savePlayerData(playerUUID, player.getData());
                GameLogger.info("Saved player data for " + username + " (UUID: " + playerUUID + ") during dispose");
            } catch (Exception e) {
                GameLogger.error("Error saving player data for " + username + ": " + e.getMessage());
            }
        }
        onlinePlayers.clear();
        uuidToUsername.clear();
        GameLogger.info("PlayerManager disposed");
    }
}
