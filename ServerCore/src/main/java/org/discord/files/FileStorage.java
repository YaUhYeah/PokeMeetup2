package org.discord.files;

import com.badlogic.gdx.utils.Json;
import io.github.pokemeetup.multiplayer.server.storage.StorageSystem;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.data.WorldData;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.storage.JsonConfig;

import java.io.IOException;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

public class FileStorage implements StorageSystem {
    private static final String MULTIPLAYER_ROOT = "multiplayer/";
    private final Path baseDir;
    private final Path worldsDir;
    private final Path playersDir;
    private final Json json;
    private final ConcurrentHashMap<String, PlayerData> playerCache;
    private final ConcurrentHashMap<String, WorldData> worldCache;
    // Cache for multiplayer data
    private long serverSeed; // Add this to ensure consistent generation
    public FileStorage(String baseDirectory) {
        // Always use multiplayer root
        this.baseDir = Paths.get(baseDirectory, MULTIPLAYER_ROOT);
        this.worldsDir = baseDir.resolve("worlds");
        this.playersDir = baseDir.resolve("players");

        this.json = JsonConfig.getInstance();
        this.serverSeed = System.currentTimeMillis(); // Initialize with a default

        this.playerCache = new ConcurrentHashMap<>();
        this.worldCache = new ConcurrentHashMap<>();

        GameLogger.info("Initializing multiplayer storage at: " + baseDir);
    }

    public void setServerSeed(long seed) {
        this.serverSeed = seed;
    }

    private void createWorldBackup(String worldName) {
        try {
            Path worldFile = worldsDir.resolve(worldName + "/world.json");
            Path backupDir = worldsDir.resolve(worldName + "/backups");
            Files.createDirectories(backupDir);
            Path backupFile = backupDir.resolve("world_backup.json"); // fixed name
            Files.copy(worldFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            GameLogger.info("Created backup of world: " + worldName + " at " + backupFile.toAbsolutePath());
        } catch (Exception e) {
            GameLogger.error("Failed to create backup of world: " + worldName + " - " + e.getMessage());
        }
    }

    @Override
    public void initialize() throws IOException {
        // Create directory structure
        Files.createDirectories(worldsDir);
        Files.createDirectories(playersDir);

        // Load existing multiplayer data
        loadExistingData();
        worldCache.forEach((worldName, data) -> {
            createWorldBackup(worldName);
        });

        GameLogger.info("Multiplayer storage initialized");
    }

    private void loadExistingData() throws IOException {
        // Load player data
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(playersDir, "*.json")) {
            for (Path file : stream) {
                String username = file.getFileName().toString().replace(".json", "");
                PlayerData data = loadPlayerData(username);
                if (data != null) {
                    playerCache.put(username, data);
                    GameLogger.info("Loaded multiplayer player data: " + username);
                }
            }
        }

        // Load world data
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(worldsDir, "*.json")) {
            for (Path file : stream) {
                String worldName = file.getFileName().toString().replace(".json", "");
                WorldData data = loadWorldData(worldName);
                if (data != null) {
                    worldCache.put(worldName, data);
                    GameLogger.info("Loaded multiplayer world: " + worldName);
                }
            }
        }
    }

    @Override
    public void savePlayerData(String username, PlayerData data) throws IOException {
        try {
            if (data != null) {
                // Validate before saving
                data.validateAndRepairState();

                Path file = playersDir.resolve(username + ".json");

                // Create backup if file exists
                if (Files.exists(file)) {
                    Path backup = file.resolveSibling(username + "_" +
                        new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".bak");
                    Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
                }

                String jsonData = json.prettyPrint(data);
                Files.writeString(file, jsonData, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
                playerCache.put(username, data);

                GameLogger.info("Saved multiplayer player data: " + username);
            }
        } catch (Exception e) {
            GameLogger.error("Failed to save player data: " + username + " - " + e.getMessage());
            throw e;
        }
    }

    @Override
    public PlayerData loadPlayerData(String username) {
        // Check cache first
        PlayerData cached = playerCache.get(username);
        if (cached != null) {
            return cached;
        }

        try {
            Path file = playersDir.resolve(username + ".json");
            if (Files.exists(file)) {
                String jsonData = Files.readString(file);
                PlayerData data = json.fromJson(PlayerData.class, jsonData);
                if (data != null) {
                    playerCache.put(username, data);
                }
                return data;
            }
        } catch (IOException e) {
            GameLogger.error("Error loading multiplayer player data: " + username + " - " + e.getMessage());
        }
        return null;
    }

    @Override
    public void saveWorldData(String worldName, WorldData data) throws IOException {
        Path worldFile = worldsDir.resolve(worldName + "/world.json");
        Path backupDir = worldsDir.resolve(worldName + "/backups");

        try {
            Files.createDirectories(worldFile.getParent());
            Files.createDirectories(backupDir);

            // Ensure world config uses server seed
            if (data.getConfig() == null) {
                data.setConfig(new WorldData.WorldConfig(serverSeed));
            } else {
                data.getConfig().setSeed(serverSeed);
            }

            // Create backup
            if (Files.exists(worldFile)) {
                Path backup = backupDir.resolve("world_backup_" +
                    new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".json");
                Files.copy(worldFile, backup, StandardCopyOption.REPLACE_EXISTING);
            }

            // Save world data with validated player data
            for (PlayerData playerData : data.getPlayers().values()) {
                if (playerData != null) {
                    playerData.validateAndRepairState();
                }
            }

            String jsonData = json.prettyPrint(data);
            Files.writeString(worldFile, jsonData, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
            worldCache.put(worldName, data);

            GameLogger.info("Saved multiplayer world: " + worldName +
                " with seed: " + serverSeed);

        } catch (Exception e) {
            GameLogger.error("Failed to save world: " + worldName + " - " + e.getMessage());
            throw e;
        }
    }


    @Override
    public WorldData loadWorldData(String worldName) {
        try {
            WorldData cached = worldCache.get(worldName);
            if (cached != null) {
                return cached;
            }

            Path worldFile = worldsDir.resolve(worldName + "/world.json");
            if (Files.exists(worldFile)) {
                String jsonData = Files.readString(worldFile);
                WorldData data = json.fromJson(WorldData.class, jsonData);

                // Ensure server seed is used
                if (data != null) {
                    if (data.getConfig() == null) {
                        data.setConfig(new WorldData.WorldConfig(serverSeed));
                    } else {
                        data.getConfig().setSeed(serverSeed);
                    }

                    // Validate player data
                    for (PlayerData playerData : data.getPlayers().values()) {
                        if (playerData != null) {
                            playerData.validateAndRepairState();
                        }
                    }

                    worldCache.put(worldName, data);
                }
                return data;
            }
            return null;
        } catch (Exception e) {
            GameLogger.error("Error loading world: " + worldName + " - " + e.getMessage());
            return null;
        }
    }


    @Override
    public void clearCache() {
        playerCache.clear();
        worldCache.clear();
    }

    @Override
    public void shutdown() {
        GameLogger.info("Shutting down multiplayer storage...");

        // Save all cached data
        playerCache.forEach((username, data) -> {
            try {
                savePlayerData(username, data);
            } catch (IOException e) {
                GameLogger.error("Error saving player data during shutdown: " + e.getMessage());
            }
        });

        worldCache.forEach((worldName, data) -> {
            try {
                saveWorldData(worldName, data);
            } catch (IOException e) {
                GameLogger.error("Error saving world data during shutdown: " + e.getMessage());
            }
        });

        clearCache();
        GameLogger.info("Multiplayer storage shutdown complete");
    }
}
