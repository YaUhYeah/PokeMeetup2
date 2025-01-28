package io.github.pokemeetup.multiplayer.server;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.data.WorldData;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.storage.GameFileSystem;
import io.github.pokemeetup.utils.storage.JsonConfig;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ServerStorageSystem {
    public static final String SERVER_BASE_DIR = "";
    public static final String SERVER_WORLD_DIR = SERVER_BASE_DIR + "worlds/";
    private final Json json;
    private final Map<String, WorldData> worldCache;
    private final GameFileSystem fs;
    private final PlayerDataManager playerDataManager;
    public synchronized void flushPlayerData() {
        try {
            GameLogger.info("Flushing player data to disk...");
            playerDataManager.flush();
        } catch (Exception e) {
            GameLogger.error("Failed to flush player data: " + e.getMessage());
        }
    }
    public ServerStorageSystem() {
        this.json = JsonConfig.getInstance();
        this.json.setOutputType(JsonWriter.OutputType.json);
        this.worldCache = new ConcurrentHashMap<>();
        this.fs = GameFileSystem.getInstance();
        this.playerDataManager = new PlayerDataManager();
        initializeDirectories();
    }

    public GameFileSystem getFileSystem() {
        return fs;
    }

    public boolean worldExists(String name) {
        try {
            String worldPath = SERVER_WORLD_DIR + name + "/world.json";
            return fs.exists(worldPath);
        } catch (Exception e) {
            GameLogger.error("Error checking world existence: " + e.getMessage());
            return false;
        }
    }

    private void initializeDirectories() {
        try {
            fs.createDirectory(SERVER_BASE_DIR);
            fs.createDirectory(SERVER_WORLD_DIR);
            fs.createDirectory(SERVER_WORLD_DIR + "backups/");

            GameLogger.info("Server storage directories initialized");
        } catch (Exception e) {
            GameLogger.error("Failed to create server storage directories: " + e.getMessage());
            throw new RuntimeException("Server storage initialization failed", e);
        }
    }

    public synchronized WorldData loadWorld(String name) {
        // Check cache first
        WorldData cached = worldCache.get(name);
        if (cached != null) {
            return cached;
        }

        try {
            String worldPath = SERVER_WORLD_DIR + name + "/world.json";
            if (!fs.exists(worldPath)) {
                GameLogger.info("World file not found: " + name);
                return null;
            }

            String content = fs.readString(worldPath);
            WorldData world = json.fromJson(WorldData.class, content);

            if (world != null) {
                worldCache.put(name, world);
                GameLogger.info("Loaded world from server storage: " + name);
            }

            return world;
        } catch (Exception e) {
            GameLogger.error("Failed to load world: " + name + " - " + e.getMessage());
            return null;
        }
    }

    public synchronized void saveWorld(WorldData world) {
        if (world == null) return;

        try {
            String worldPath = SERVER_WORLD_DIR + world.getName() + "/";
            fs.createDirectory(worldPath);


            // Save using temporary file
            String tempPath = worldPath + "world.json.temp";
            String finalPath = worldPath + "world.json";

            String jsonData = json.prettyPrint(world);
            fs.writeString(tempPath, jsonData);

            // If temporary file was written successfully, move it to the actual file
            if (fs.exists(finalPath)) {
                fs.deleteFile(finalPath);
            }
            fs.moveFile(tempPath, finalPath);

            // Update cache
            worldCache.put(world.getName(), world);

            GameLogger.info("Saved world to server storage: " + world.getName());
        } catch (Exception e) {
            GameLogger.error("Failed to save world: " + world.getName() + " - " + e.getMessage());
            throw new RuntimeException("World save failed", e);
        }
    }

    public void savePlayerData(String username, PlayerData data) {
        UUID playerUUID = UUID.nameUUIDFromBytes(username.getBytes());
        playerDataManager.savePlayerData(playerUUID, data);
    }


    public Map<String, WorldData> getAllWorlds() {
        String[] worldDirs = fs.list(SERVER_WORLD_DIR);
        Map<String, WorldData> worlds = new HashMap<>();

        if (worldDirs != null) {
            for (String dir : worldDirs) {
                WorldData world = loadWorld(dir);
                if (world != null) {
                    worlds.put(world.getName(), world);
                }
            }
        }

        return worlds;
    }


    public void deletePlayerData(UUID uuid) {
        playerDataManager.deletePlayerData(uuid);
    }



    public void deleteWorld(String name) {
        String worldPath = SERVER_WORLD_DIR + name;
        if (fs.exists(worldPath)) {
            fs.deleteDirectory(worldPath);
            worldCache.remove(name);
            GameLogger.info("Deleted world from server storage: " + name);
        }
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public void shutdown() throws InterruptedException {
        // Save all cached data
        for (WorldData world : worldCache.values()) {
            saveWorld(world);
        }
        Thread.sleep(1000);
        GameLogger.info("Storage system shutdown complete");
    }
}
