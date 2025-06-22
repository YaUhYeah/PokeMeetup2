package io.github.pokemeetup.multiplayer.server;

import com.badlogic.gdx.utils.Json;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.storage.GameFileSystem;
import io.github.pokemeetup.utils.storage.JsonConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PlayerDataManager {
    private static final String PLAYER_DATA_DIR = "players/";
    public final Map<UUID, PlayerData> playerCache;
    private final GameFileSystem fs;
    private final Json json;
    private volatile boolean isFlushInProgress = false;

    public PlayerDataManager() {
        this.playerCache = new ConcurrentHashMap<>();
        this.fs = GameFileSystem.getInstance();
        this.json = JsonConfig.getInstance();
        initializeDirectory();
    }

    private void initializeDirectory() {
        try {
            fs.createDirectory(PLAYER_DATA_DIR);
            GameLogger.info("Player data directory initialized at: " + PLAYER_DATA_DIR);
        } catch (Exception e) {
            GameLogger.error("Failed to create player data directory: " + e.getMessage());
            throw new RuntimeException("Player data storage initialization failed", e);
        }
    }

    public synchronized PlayerData loadPlayerData(UUID uuid) {
        PlayerData cached = playerCache.get(uuid);
        if (cached != null) {
            return cached.copy(); // Return copy to prevent direct cache modification
        }

        try {
            String path = getPlayerDataPath(uuid);
            if (!fs.exists(path)) {
                GameLogger.info("No existing data found for UUID: " + uuid);
                return null;
            }

            String jsonData = fs.readString(path);
            PlayerData playerData = json.fromJson(PlayerData.class, jsonData);

            if (playerData != null) {
                // Validate and repair if needed
                if (playerData.validateAndRepairState()) {
                    GameLogger.info("Repaired loaded player data for UUID: " + uuid);
                    savePlayerData(uuid, playerData); // Save repaired data
                }
                playerCache.put(uuid, playerData.copy());
                GameLogger.info("Successfully loaded player data for UUID: " + uuid);
                return playerData.copy();
            }
            return null;
        } catch (Exception e) {
            GameLogger.error("Failed to load player data for UUID: " + uuid + " - " + e.getMessage());
            return null;
        }
    }
    public synchronized void savePlayerData(UUID uuid, PlayerData playerData) {
        if (uuid == null || playerData == null) {
            GameLogger.error("Invalid save attempt with null UUID or PlayerData");
            return;
        }

        try {
            // Validate data before saving
            if (!playerData.validateAndRepairState()) {
                GameLogger.error("Player data validation failed for UUID: " + uuid);
                return;
            }

            String tempPath = getPlayerDataPath(uuid) + ".temp";
            String finalPath = getPlayerDataPath(uuid);
            fs.writeString(tempPath, json.toJson(playerData));

            // Verify temp file exists and is valid
            if (!fs.exists(tempPath)) {
                throw new RuntimeException("Failed to write temporary player data file");
            }

            // Atomic move to final location
            fs.moveFile(tempPath, finalPath);

            // Update cache with a deep copy
            playerCache.put(uuid, playerData.copy());

        } catch (Exception e) {
            GameLogger.error("Failed to save player data for UUID: " + uuid + " - " + e.getMessage());
            throw new RuntimeException("Player data save failed", e);
        }
    }
    private String getPlayerDataPath(UUID uuid) {
        return PLAYER_DATA_DIR + uuid.toString() + ".json";
    }


    public synchronized void flush() {
        if (isFlushInProgress) {
            return; // Prevent recursive flush
        }

        try {
            isFlushInProgress = true;
            Map<UUID, PlayerData> dataToSave = new HashMap<>(playerCache);

            for (Map.Entry<UUID, PlayerData> entry : dataToSave.entrySet()) {
                try {
                    savePlayerData(entry.getKey(), entry.getValue());
                } catch (Exception e) {
                    GameLogger.error("Failed to flush player data for UUID " +
                        entry.getKey() + ": " + e.getMessage());
                }
            }


        } finally {
            isFlushInProgress = false;
        }
    }


    public void deletePlayerData(UUID uuid) {
        try {
            String path = getPlayerDataPath(uuid);
            if (fs.exists(path)) {
                fs.deleteFile(path);
                playerCache.remove(uuid);
                GameLogger.info("Deleted player data for UUID: " + uuid);
            }
        } catch (Exception e) {
            GameLogger.error("Failed to delete player data for UUID: " + uuid + " - " + e.getMessage());
        }
    }

    public void shutdown() {
        try {
            GameLogger.info("Starting PlayerDataManager shutdown...");
            flush(); // Ensure all cached data is saved
            playerCache.clear();
            GameLogger.info("PlayerDataManager shutdown complete");
        } catch (Exception e) {
            GameLogger.error("Error during PlayerDataManager shutdown: " + e.getMessage());
        }
    }
}
