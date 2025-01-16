package io.github.pokemeetup.system.gameplay.overworld.multiworld;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.multiplayer.server.ServerStorageSystem;
import io.github.pokemeetup.system.data.*;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.storage.GameFileSystem;
import io.github.pokemeetup.utils.storage.JsonConfig;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WorldManager {
    private static final long AUTO_SAVE_INTERVAL = 100000;
    private static final String WORLDS_BASE_DIR = "worlds/";
    private static final String SINGLE_PLAYER_DIR = WORLDS_BASE_DIR + "singleplayer/";
    private static final String MULTI_PLAYER_DIR = WORLDS_BASE_DIR + "multiplayer/";
    private static WorldManager instance;
    private final Map<String, WorldData> worlds;
    private final ServerStorageSystem storage;
    private final Object worldLock = new Object();
    private final String baseDirectory;
    private final GameFileSystem fs;
    private final boolean isMultiplayerMode;
    private final Map<String, WorldData> worldCache = new ConcurrentHashMap<>();
    private final Object saveLock = new Object();
    private boolean isInitialized = false;
    private WorldData currentWorld;
    private long lastAutoSave = 0;

    private WorldManager(ServerStorageSystem storage, boolean isMultiplayerMode) {
        this.storage = storage;
        this.isMultiplayerMode = isMultiplayerMode;
        this.worlds = new ConcurrentHashMap<>();
        this.baseDirectory = isMultiplayerMode ? MULTI_PLAYER_DIR : SINGLE_PLAYER_DIR;
        this.fs = GameFileSystem.getInstance();
        createDirectoryStructure();
    }

    public static synchronized WorldManager getInstance(ServerStorageSystem storage, boolean isMultiplayerMode) {
        if (instance == null) {
            instance = new WorldManager(storage, isMultiplayerMode);
        }
        return instance;
    }

    public synchronized void init() {
        if (isInitialized) {
            GameLogger.info("WorldManager already initialized");
            return;
        }

        synchronized (worldLock) {
            try {
                worlds.clear();

                if (isMultiplayerMode) {
                    initializeMultiplayerMode();
                } else {
                    initializeSingleplayerMode();
                }

                isInitialized = true;
                GameLogger.info("WorldManager initialized in " +
                    (isMultiplayerMode ? "multiplayer" : "singleplayer") +
                    " mode with " + worlds.size() + " worlds");

            } catch (Exception e) {
                GameLogger.error("Failed to initialize WorldManager: " + e.getMessage());
                throw new RuntimeException("WorldManager initialization failed", e);
            }
        }
    }


    private void initializeMultiplayerMode() {
        try {
            if (storage != null) {
                // Server-side initialization
                Map<String, WorldData> serverWorlds = storage.getAllWorlds();
                if (serverWorlds != null && !serverWorlds.isEmpty()) {
                    worlds.putAll(serverWorlds);
                    GameLogger.info("Loaded " + serverWorlds.size() + " worlds from server");
                }

                WorldData multiplayerWorld = worlds.get(CreatureCaptureGame.MULTIPLAYER_WORLD_NAME);
                if (multiplayerWorld == null) {
                    createDefaultMultiplayerWorld();
                } else {
                    currentWorld = multiplayerWorld;
                }
            } else {
                // Client-side: Do NOT create local storage
                GameLogger.info("Client-side multiplayer initialization - deferring to server");
                // Clear any local world data to ensure we only use server data
                worlds.clear();
                worldCache.clear();
            }
        } catch (Exception e) {
            GameLogger.error("Error initializing multiplayer mode: " + e.getMessage());
            throw new RuntimeException("Multiplayer initialization failed", e);
        }
    }

    private void createDefaultMultiplayerWorld() {
        try {
            WorldData defaultWorld = createWorld(
                CreatureCaptureGame.MULTIPLAYER_WORLD_NAME,
                CreatureCaptureGame.MULTIPLAYER_WORLD_SEED,
                0.15f,
                0.05f
            );

            storage.saveWorld(defaultWorld);
            worlds.put(defaultWorld.getName(), defaultWorld);
            currentWorld = defaultWorld;

            GameLogger.info("Created and saved default multiplayer world");

        } catch (Exception e) {
            GameLogger.error("Failed to create default multiplayer world: " + e.getMessage());
            throw new RuntimeException("Failed to create multiplayer world", e);
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

    public void checkAutoSave() {
        if (isMultiplayerMode && storage == null) {
            // Client should not auto-save
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAutoSave >= AUTO_SAVE_INTERVAL) {
            synchronized (worldLock) {
                for (WorldData world : worlds.values()) {
                    if (world.isDirty()) {
                        saveWorld(world);
                    }
                }
            }
            lastAutoSave = currentTime;
            GameLogger.info("Auto-saved dirty worlds.");
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

                // Don't set commands flag here - let caller handle it

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
        if (worldData == null) return;

        synchronized (saveLock) {
            try {
                // Log pre-save state
                GameLogger.info("Saving world: " + worldData.getName() +
                    " with commands: " + worldData.commandsAllowed());

                // Create deep copy for saving
                WorldData saveData = worldData.copy();

                // Verify copy worked
                if (saveData.commandsAllowed() != worldData.commandsAllowed()) {
                    GameLogger.error("Command state mismatch in copy! Fixing...");
                    saveData.setCommandsAllowed(worldData.commandsAllowed());
                }

                // Convert to JSON
                Json json = JsonConfig.getInstance();
                String jsonStr = json.toJson(saveData);

                if (isMultiplayerMode && storage != null) {
                    // Multiplayer server mode - use ServerStorageSystem
                    storage.saveWorld(saveData);
                    GameLogger.info("World saved using ServerStorageSystem: " + worldData.getName());
                } else {
                    // Singleplayer mode - save to local file system
                    String worldDirPath = baseDirectory + worldData.getName();
                    if (!fs.exists(worldDirPath)) {
                        fs.createDirectory(worldDirPath);
                    }

                    String tempFilePath = worldDirPath + "/world.json.temp";
                    fs.writeString(tempFilePath, jsonStr);

                    // Verify saved data
                    WorldData verification = json.fromJson(WorldData.class, fs.readString(tempFilePath));
                    if (verification != null) {
                        if (verification.commandsAllowed() != worldData.commandsAllowed()) {
                            GameLogger.error("Command state lost in save! Original: " +
                                worldData.commandsAllowed() + ", Saved: " + verification.commandsAllowed());
                            // Try to fix
                            verification.setCommandsAllowed(worldData.commandsAllowed());
                            fs.writeString(tempFilePath, json.toJson(verification));
                        }
                    }

                    // Move temp file to final location
                    String worldFilePath = worldDirPath + "/world.json";
                    if (fs.exists(worldFilePath)) {
                        fs.deleteFile(worldFilePath);
                    }
                    fs.moveFile(tempFilePath, worldFilePath);

                    GameLogger.info("Successfully saved world with commands state: " +
                        worldData.commandsAllowed());
                }

            } catch (Exception e) {
                GameLogger.error("Failed to save world: " + worldData.getName() +
                    " - " + e.getMessage());
            }
        }
    }


    public WorldData getWorld(String name) {
        synchronized (worldLock) {
            try {
                if (isMultiplayerMode && storage == null) {
                    return worlds.get(name);
                }

                WorldData world = worlds.get(name);
                if (world == null && !isMultiplayerMode) {
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
            // Only create directories for singleplayer mode
            if (!isMultiplayerMode) {
                fs.createDirectory(WORLDS_BASE_DIR);
                fs.createDirectory(baseDirectory);
                fs.createDirectory(baseDirectory + "backups/");
                GameLogger.info("Directory structure initialized: " + baseDirectory);
            }
        } catch (Exception e) {
            GameLogger.error("Failed to create directory structure: " + e.getMessage());
            throw new RuntimeException("Failed to initialize directory structure", e);
        }
    }

    public void deleteWorld(String name) {
        synchronized (worldLock) {
            WorldData removed = worlds.remove(name);
            if (removed != null) {
                if (isMultiplayerMode) {
                    storage.deleteWorld(name);
                } else {
                    deleteSingleplayerWorld(name);
                }
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

    public WorldData getCurrentWorld() {
        return currentWorld;
    }

    private void cleanupCorruptedWorlds() {
        if (!fs.exists("worlds/singleplayer/")) return;

        String[] directories = fs.list("worlds/singleplayer/");
        for (String dirName : directories) {
            String dirPath = "worlds/singleplayer/" + dirName;
            if (!fs.isDirectory(dirPath)) continue;

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
                    // Load and validate the world
                    WorldData world = loadAndValidateWorld(dirName);
                    if (world != null) {
                        // Important: Add valid worlds to the worlds map
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
                // Check for cached world first
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

                // Log the loaded data state
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

                // Cache valid world
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
