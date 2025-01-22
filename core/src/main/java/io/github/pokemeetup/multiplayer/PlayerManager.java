package io.github.pokemeetup.multiplayer;

import io.github.pokemeetup.multiplayer.server.ServerStorageSystem;
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

    public PlayerManager(ServerStorageSystem storage) {
        this.storage = storage;
        GameLogger.info("PlayerManager initialized with ServerStorageSystem");
    }


    public ServerPlayer getPlayer(String username) {
        return onlinePlayers.get(username);
    }


    public Collection<ServerPlayer> getOnlinePlayers() {
        return onlinePlayers.values();
    }

    public void removePlayer(String username) {
        ServerPlayer player = onlinePlayers.get(username);
        if (player != null) {
            try {
                UUID playerUUID = player.getUUID();
                // Get fresh copy of data
                PlayerData finalState = player.getData();

                // Save player data before removing
                storage.getPlayerDataManager().savePlayerData(playerUUID, finalState);
                storage.getPlayerDataManager().flush(); // Force immediate save

                // Remove after successful save
                onlinePlayers.remove(username);
                uuidToUsername.remove(playerUUID);

                GameLogger.info("Saved and removed player: " + username + " (UUID: " + playerUUID + ")");
            } catch (Exception e) {
                GameLogger.error("Error saving player data for " + username + ": " + e.getMessage());
            }
        }
    }



    public void dispose() {
        GameLogger.info("Starting PlayerManager disposal...");

        // Save each player's data
        for (Map.Entry<String, ServerPlayer> entry : onlinePlayers.entrySet()) {
            try {
                String username = entry.getKey();
                ServerPlayer player = entry.getValue();
                UUID playerUUID = player.getUUID();

                // Get fresh copy of data
                PlayerData finalState = player.getData();

                // Save with immediate flush
                storage.getPlayerDataManager().savePlayerData(playerUUID, finalState);
                storage.getPlayerDataManager().flush();

                GameLogger.info("Saved final state for: " + username + " (UUID: " + playerUUID + ")");
            } catch (Exception e) {
                GameLogger.error("Error saving player during dispose: " + e.getMessage());
            }
        }

        // Force final storage flush
        storage.flushPlayerData();

        // Clear collections
        onlinePlayers.clear();
        uuidToUsername.clear();

        GameLogger.info("PlayerManager disposal complete");
    }
}
