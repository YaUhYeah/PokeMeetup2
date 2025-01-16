package io.github.pokemeetup.multiplayer.server;

import com.badlogic.gdx.utils.Json;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.storage.GameFileSystem;
import io.github.pokemeetup.utils.storage.JsonConfig;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {
    private static final String PLAYER_DATA_DIR = "server/playerdata/";
    private final Map<UUID, PlayerData> playerCache;
    private final GameFileSystem fs;
    private final Json json;

    public PlayerDataManager() {
        this.playerCache = new ConcurrentHashMap<>();
        this.fs = GameFileSystem.getInstance();
        this.json = JsonConfig.getInstance();
        initializeDirectory();
    }

    private void initializeDirectory() {
        try {
            fs.createDirectory(PLAYER_DATA_DIR);
            GameLogger.info("Player data directory initialized");
        } catch (Exception e) {
            GameLogger.error("Failed to create player data directory: " + e.getMessage());
            throw new RuntimeException("Player data storage initialization failed", e);
        }
    }

    public PlayerData loadPlayerData(UUID uuid) {
        if (playerCache.containsKey(uuid)) {
            return playerCache.get(uuid);
        }

        try {
            String path = getPlayerFilePath(uuid);
            if (!fs.exists(path)) {
                return null;
            }

            String jsonData = fs.readString(path);
            PlayerData playerData = json.fromJson(PlayerData.class, jsonData);
            if (playerData != null) {
                playerCache.put(uuid, playerData);
                GameLogger.info("Loaded player data for UUID: " + uuid);
            }
            return playerData;
        } catch (Exception e) {
            GameLogger.error("Failed to load player data for UUID: " + uuid + " - " + e.getMessage());
            return null;
        }
    }

    public void savePlayerData(UUID uuid, PlayerData playerData) {
        if (uuid == null || playerData == null) {
            return;
        }

        try {
            String tempPath = getPlayerFilePath(uuid) + ".temp";
            String finalPath = getPlayerFilePath(uuid);

            String jsonData = json.prettyPrint(playerData);
            fs.writeString(tempPath, jsonData);

            if (fs.exists(finalPath)) {
                fs.deleteFile(finalPath);
            }
            fs.moveFile(tempPath, finalPath);

            playerCache.put(uuid, playerData);
            GameLogger.info("Saved player data for UUID: " + uuid);
        } catch (Exception e) {
            GameLogger.error("Failed to save player data for UUID: " + uuid + " - " + e.getMessage());
            throw new RuntimeException("Player data save failed", e);
        }
    }

    public void deletePlayerData(UUID uuid) {
        String path = getPlayerFilePath(uuid);
        if (fs.exists(path)) {
            fs.deleteFile(path);
            playerCache.remove(uuid);
            GameLogger.info("Deleted player data for UUID: " + uuid);
        }
    }

    private String getPlayerFilePath(UUID uuid) {
        return PLAYER_DATA_DIR + uuid.toString() + ".json";
    }

    public void shutdown() {
        for (Map.Entry<UUID, PlayerData> entry : playerCache.entrySet()) {
            savePlayerData(entry.getKey(), entry.getValue());
        }
        GameLogger.info("Player data manager shutdown complete");
    }
}