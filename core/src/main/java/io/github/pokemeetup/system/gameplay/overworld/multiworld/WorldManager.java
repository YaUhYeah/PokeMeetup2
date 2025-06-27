package io.github.pokemeetup.system.gameplay.overworld.multiworld;

import com.badlogic.gdx.utils.Json;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.system.data.*;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.storage.GameFileSystem;
import io.github.pokemeetup.utils.storage.JsonConfig;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class WorldManager {// In WorldManager (or a new StorageManager utility)
    private static final String WORLDS_BASE_DIR = "worlds/";
    private static final String SINGLE_PLAYER_DIR = WORLDS_BASE_DIR + "singleplayer/";
    private static WorldManager instance;
    private final ExecutorService storageExecutor = Executors.newSingleThreadExecutor();
    private final Map<String, WorldData> worlds;
    private final Object worldLock = new Object();
    private final String baseDirectory;
    private final GameFileSystem fs;
    private final Map<String, WorldData> worldCache = new ConcurrentHashMap<>();
    private final Object saveLock = new Object();
    private boolean isInitialized = false;
    private WorldManager() {
        this.worlds = new ConcurrentHashMap<>();
        this.baseDirectory = SINGLE_PLAYER_DIR;
        this.fs = GameFileSystem.getInstance();
        createDirectoryStructure();
    }

    public static synchronized WorldManager getInstance() {
        if (instance == null) {
            instance = new WorldManager();
        }
        return instance;
    }
    public void disposeStorage() {
        storageExecutor.shutdown();
        try {
            if (!storageExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                storageExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            storageExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public synchronized void init() {
        if (isInitialized) {
            return;
        }

        synchronized (worldLock) {
            try {
                worlds.clear();

                initializeSingleplayerMode();


                isInitialized = true;

            } catch (Exception e) {
                GameLogger.error("Failed to initialize WorldManager: " + e.getMessage());
                throw new RuntimeException("WorldManager initialization failed", e);
            }
        }
    }

    private void initializeSingleplayerMode() {
        try {
            cleanupCorruptedWorlds();
            loadSingleplayerWorlds();
        } catch (Exception e) {
            GameLogger.error("Error initializing singleplayer mode: " + e.getMessage());
            throw e;
        }
    }

    public WorldData createWorld(String name, long seed, float treeSpawnRate, float pokemonSpawnRate) {
        synchronized (worldLock) {
            try {
                if (name == null || name.trim().isEmpty()) {
                    throw new IllegalArgumentException("World name cannot be null or empty");
                }

                if (worlds.containsKey(name)) {
                    GameLogger.info("Cleaning up existing world: " + name);
                    deleteWorld(name);
                }

                GameLogger.info("Creating new world: " + name + " with seed: " + seed);

                WorldData.WorldConfig config = new WorldData.WorldConfig();
                config.setSeed(seed);
                config.setTreeSpawnRate(treeSpawnRate);
                config.setPokemonSpawnRate(pokemonSpawnRate);

                WorldData world = new WorldData(name);
                world.setName(name);
                world.setLastPlayed(System.currentTimeMillis());
                world.setConfig(config);
                world.setPlayers(new HashMap<>());
                world.setPokemonData(new PokemonData());

                worlds.put(name, world);
                GameLogger.info("Created world data object");

                return world;

            } catch (Exception e) {
                GameLogger.error("Failed to create world: " + name + " - " + e.getMessage());
                throw new RuntimeException("World creation failed: " + e.getMessage(), e);
            }
        }
    }

    public void saveWorld(WorldData worldData) {
        if (GameContext.get().isMultiplayer()){
            return;
        }
        GameLogger.info("Saving world: " + worldData.getName());
        synchronized (saveLock) {
            try {
                GameLogger.info("Saving world: " + worldData.getName() +
                    " with commands: " + worldData.commandsAllowed());
                WorldData saveData = worldData.copy();
                if (saveData.commandsAllowed() != worldData.commandsAllowed()) {
                    GameLogger.error("Command state mismatch in copy! Fixing...");
                    saveData.setCommandsAllowed(worldData.commandsAllowed());
                }
                Json json = JsonConfig.getInstance();
                String jsonStr = json.toJson(saveData);
                String worldDirPath = baseDirectory + worldData.getName();
                if (!fs.exists(worldDirPath)) {
                    fs.createDirectory(worldDirPath);
                }

                String tempFilePath = worldDirPath + "/world.json.temp";
                fs.writeString(tempFilePath, jsonStr);
                WorldData verification = json.fromJson(WorldData.class, fs.readString(tempFilePath));
                if (verification != null) {
                    if (verification.commandsAllowed() != worldData.commandsAllowed()) {
                        GameLogger.error("Command state lost in save! Original: " +
                            worldData.commandsAllowed() + ", Saved: " + verification.commandsAllowed());
                        verification.setCommandsAllowed(worldData.commandsAllowed());
                        fs.writeString(tempFilePath, json.toJson(verification));
                    }
                }
                String worldFilePath = worldDirPath + "/world.json";
                if (fs.exists(worldFilePath)) {
                    fs.deleteFile(worldFilePath);
                }
                fs.moveFile(tempFilePath, worldFilePath);

                GameLogger.info("Successfully saved world with commands state: " +
                    worldData.commandsAllowed());
                worldCache.remove(worldData.getName());

            } catch (Exception e) {
                GameLogger.error("Failed to save world: " + worldData.getName() +
                    " - " + e.getMessage());
            }
        }
    }


    public WorldData getWorld(String name) {
        synchronized (worldLock) {
            try {

                WorldData world = worlds.get(name);
                if (world == null && !GameContext.get().isMultiplayer()) {
                    world = JsonConfig.loadWorldData(name);
                    if (world != null) {
                        applyWorldData(world);
                    }
                }
                return world;
            } catch (Exception e) {
                GameLogger.error("Error loading world: " + name + " - " + e.getMessage());
                throw new RuntimeException("Failed to load world", e);
            }
        }
    }

    private void createDirectoryStructure() {
        try {
            fs.createDirectory(WORLDS_BASE_DIR);
            fs.createDirectory(baseDirectory);
            fs.createDirectory(baseDirectory + "backups/");
            GameLogger.info("Directory structure initialized: " + baseDirectory);

        } catch (Exception e) {
            GameLogger.error("Failed to create directory structure: " + e.getMessage());
            throw new RuntimeException("Failed to initialize directory structure", e);
        }
    }

    public void deleteWorld(String name) {
        synchronized (worldLock) {
            WorldData removed = worlds.remove(name);
            if (removed != null) {
                deleteSingleplayerWorld(name);

                GameLogger.info("Deleted world: " + name);
            } else {
                GameLogger.info("Attempted to delete non-existent world: " + name);
            }
        }
    }

    private void applyWorldData(WorldData world) {
        if (world == null) return;

        try {
            world.validateAndRepair();
            worlds.put(world.getName(), world);

        } catch (Exception e) {
            GameLogger.error("Failed to apply world data: " + e.getMessage());
            throw new RuntimeException("World data application failed", e);
        }
    }

    public Map<String, WorldData> getWorlds() {
        return Collections.unmodifiableMap(worlds);
    }

    private void cleanupCorruptedWorlds() {
        if (!fs.exists("worlds/singleplayer/")) return;

        String[] directories = fs.list("worlds/singleplayer/");
        for (String dirName : directories) {
            String dirPath = "worlds/singleplayer/" + dirName;
            if (fs.isDirectory(dirPath)) continue;

            String worldFilePath = dirPath + "/world.json";
            if (fs.exists(worldFilePath)) {
                try {
                    Json json = JsonConfig.getInstance();
                    String content = fs.readString(worldFilePath);
                    WorldData world = json.fromJson(WorldData.class, content);

                    if (world == null) {
                        String backupPath = dirPath + "/world.json.corrupted";
                        fs.writeString(backupPath, content);
                        fs.deleteFile(worldFilePath);
                        world = new WorldData(dirName);
                        world.setLastPlayed(System.currentTimeMillis());
                        fs.writeString(worldFilePath, json.toJson(world));
                        GameLogger.info("Repaired corrupted world: " + dirName);
                    }
                } catch (Exception e) {
                    GameLogger.error("Failed to parse world file: " + dirName + " - " + e.getMessage());
                }
            }
        }
    }

    private void loadSingleplayerWorlds() {
        try {
            if (!fs.exists("worlds/singleplayer/")) {
                fs.createDirectory("worlds/singleplayer/");
                GameLogger.info("Created worlds directory.");
                return;
            }

            String[] worldFolders = fs.list("worlds/singleplayer/");
            if (worldFolders == null || worldFolders.length == 0) {
                GameLogger.info("No singleplayer worlds found.");
                return;
            }

            for (String dirName : worldFolders) {
                String dirPath = "worlds/singleplayer/" + dirName;
                if (!fs.isDirectory(dirPath)) continue;

                String worldFilePath = dirPath + "/world.json";
                if (!fs.exists(worldFilePath)) {
                    GameLogger.info("Missing 'world.json' in: " + dirPath);
                    continue;
                }

                try {
                    WorldData world = loadAndValidateWorld(dirName);
                    if (world != null) {
                        worlds.put(dirName, world);
                        GameLogger.info("Successfully loaded world: " + dirName);
                    }
                } catch (Exception e) {
                    GameLogger.error("Error loading world: " + dirName + " - " + e.getMessage());
                }
            }

            GameLogger.info("Loaded " + worlds.size() + " singleplayer worlds.");
        } catch (Exception e) {
            GameLogger.error("Error loading singleplayer worlds: " + e.getMessage());
        }
    }


    public WorldData loadAndValidateWorld(String worldName) {
        synchronized (saveLock) {
            try {
                WorldData cached = worldCache.get(worldName);
                if (cached != null) {
                    GameLogger.info("Found cached world: " + worldName);
                    return cached;
                }

                String worldPath = SINGLE_PLAYER_DIR + worldName + "/world.json";
                if (!fs.exists(worldPath)) {
                    GameLogger.error("World file not found: " + worldPath);
                    return null;
                }

                String jsonContent = fs.readString(worldPath);
                if (jsonContent == null || jsonContent.isEmpty()) {
                    GameLogger.error("World file is empty: " + worldPath);
                    return null;
                }

                WorldData worldData = JsonConfig.getInstance().fromJson(WorldData.class, jsonContent);
                if (worldData == null) {
                    GameLogger.error("Failed to parse world data from JSON");
                    return null;
                }
                GameLogger.info("Loaded world data - Players: " +
                    (worldData.getPlayers() != null ? worldData.getPlayers().size() : 0));

                if (worldData.getPlayers() != null) {
                    for (Map.Entry<String, PlayerData> entry : worldData.getPlayers().entrySet()) {
                        PlayerData playerData = entry.getValue();
                        if (playerData != null) {
                            GameLogger.info("Loaded player: " + entry.getKey() +
                                " Items: " + playerData.getInventoryItems().size() +
                                " Pokemon: " + playerData.getPartyPokemon().size());
                        }
                    }
                }

                worldCache.put(worldName, worldData);
                return worldData;

            } catch (Exception e) {
                GameLogger.error("Error loading world: " + worldName + " - " + e.getMessage());
                return null;
            }
        }
    }


    private void deleteSingleplayerWorld(String name) {
        try {
            String worldPath = "worlds/singleplayer/" + name;
            if (fs.exists(worldPath)) {
                fs.deleteDirectory(worldPath);
                GameLogger.info("Deleted singleplayer world directory: " + name);
            } else {
                GameLogger.info("Singleplayer world directory does not exist: " + name);
            }
        } catch (Exception e) {
            GameLogger.error("Failed to delete singleplayer world: " + name + " - " + e.getMessage());
            throw new RuntimeException("Singleplayer world deletion failed", e);
        }
    }
}
