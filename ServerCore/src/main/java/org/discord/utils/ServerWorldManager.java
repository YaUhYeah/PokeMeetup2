package org.discord.utils;

import io.github.pokemeetup.multiplayer.server.ServerStorageSystem;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.data.PokemonData;
import io.github.pokemeetup.system.data.WorldData;
import io.github.pokemeetup.utils.GameLogger;

import java.util.*;
import java.util.concurrent.*;

public class ServerWorldManager {
    private static final long SAVE_INTERVAL = 300000; // 5 minutes
    private static ServerWorldManager instance;

    private final Map<String, WorldData> activeWorlds;
    private final ServerStorageSystem storage;
    private final Object worldLock = new Object();
    private final Map<String, Long> lastWorldAccess;
    private final ScheduledExecutorService scheduler;
    private final Map<UUID, PlayerData> playerDataCache;

    private ServerWorldManager(ServerStorageSystem storage) {
        this.storage = storage;
        this.activeWorlds = new ConcurrentHashMap<>();
        this.lastWorldAccess = new ConcurrentHashMap<>();
        this.playerDataCache = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(2);
        initializeAutoSave();
    }

    public static synchronized ServerWorldManager getInstance(ServerStorageSystem storage) {
        if (instance == null) {
            instance = new ServerWorldManager(storage);
        }
        return instance;
    }
    public void savePlayerData(String username, PlayerData data) {
        try {
            UUID playerUUID = UUID.nameUUIDFromBytes(username.getBytes());
            storage.getPlayerDataManager().savePlayerData(playerUUID, data);
            playerDataCache.put(playerUUID, data.copy());
            GameLogger.info("Saved player data for: " + username);

            // Ensure data is flushed to disk
            storage.flushPlayerData();

        } catch (Exception e) {
            GameLogger.error("Failed to save player data: " + e.getMessage());
        }
    }
    private void initializeAutoSave() {
        scheduler.scheduleAtFixedRate(() -> {
            synchronized (worldLock) {
                try {
                    // Save worlds that are marked dirty
                    for (Map.Entry<String, WorldData> entry : activeWorlds.entrySet()) {
                        if (entry.getValue().isDirty()) {
                            saveWorld(entry.getValue());
                            GameLogger.info("Auto-saved world: " + entry.getKey());
                        }
                    }

                    // Save all player data
                    for (Map.Entry<UUID, PlayerData> entry : playerDataCache.entrySet()) {
                        storage.getPlayerDataManager().savePlayerData(entry.getKey(), entry.getValue());
                    }

                    // Ensure data is flushed
                    storage.flushPlayerData();
                    GameLogger.info("Auto-saved player data");

                } catch (Exception e) {
                    GameLogger.error("Error during auto-save: " + e.getMessage());
                }
            }
        }, SAVE_INTERVAL, SAVE_INTERVAL, TimeUnit.MILLISECONDS);
    }
    public PlayerData loadPlayerData(String username) {
        try {
            UUID playerUUID = UUID.nameUUIDFromBytes(username.getBytes());

            // Check cache first
            PlayerData cached = playerDataCache.get(playerUUID);
            if (cached != null) {
                return cached.copy();
            }

            // Load from storage
            PlayerData data = storage.getPlayerDataManager().loadPlayerData(playerUUID);
            if (data != null) {
                // Validate and repair if needed
                if (data.validateAndRepairState()) {
                    GameLogger.info("Repaired player data for: " + username);
                    // Save the repaired data
                    savePlayerData(username, data);
                }
                playerDataCache.put(playerUUID, data.copy());
                GameLogger.info("Loaded player data for: " + username);
            } else {
                GameLogger.info("No existing player data found for: " + username);
            }
            return data;
        } catch (Exception e) {
            GameLogger.error("Failed to load player data: " + e.getMessage());
            return null;
        }
    }

    public WorldData loadWorld(String worldName) {
        synchronized (worldLock) {
            try {
                // Check if world is already loaded
                WorldData world = activeWorlds.get(worldName);
                if (world != null) {
                    lastWorldAccess.put(worldName, System.currentTimeMillis());
                    return world;
                }

                // Load from storage
                world = storage.loadWorld(worldName);
                if (world != null) {
                    world.validateAndRepair();
                    activeWorlds.put(worldName, world);
                    lastWorldAccess.put(worldName, System.currentTimeMillis());
                    GameLogger.info("Loaded world: " + worldName);
                }
                return world;
            } catch (Exception e) {
                GameLogger.error("Failed to load world: " + worldName + " - " + e.getMessage());
                return null;
            }
        }
    }
    public WorldData getWorld(String name) {
        synchronized (worldLock) {
            try {
                // First check active worlds
                WorldData world = activeWorlds.get(name);
                if (world != null) {
                    lastWorldAccess.put(name, System.currentTimeMillis());
                    return world;
                }

                // If not active, try loading it
                world = loadWorld(name);
                if (world != null) {
                    return world;
                }

                GameLogger.info("World '" + name + "' not found");
                return null;

            } catch (Exception e) {
                GameLogger.error("Error getting world: " + name + " - " + e.getMessage());
                return null;
            }
        }
    }

    public WorldData createWorld(String name, long seed, float treeSpawnRate, float pokemonSpawnRate) {
        synchronized (worldLock) {
            try {
                // Validate parameters
                if (name == null || name.trim().isEmpty()) {
                    throw new IllegalArgumentException("World name cannot be null or empty");
                }

                // Check if world already exists
                if (activeWorlds.containsKey(name) || storage.worldExists(name)) {
                    GameLogger.error("World '" + name + "' already exists");
                    return null;
                }

                GameLogger.info("Creating new world: " + name + " with seed: " + seed);

                // Create world configuration
                WorldData.WorldConfig config = new WorldData.WorldConfig();
                config.setSeed(seed);
                config.setTreeSpawnRate(treeSpawnRate);
                config.setPokemonSpawnRate(pokemonSpawnRate);

                // Create new world
                WorldData world = new WorldData(name);
                world.setName(name);
                world.setLastPlayed(System.currentTimeMillis());
                world.setConfig(config);
                world.setPlayers(new HashMap<>());
                world.setPokemonData(new PokemonData());

                // Save immediately
                storage.saveWorld(world);

                // Add to active worlds
                activeWorlds.put(name, world);
                lastWorldAccess.put(name, System.currentTimeMillis());

                GameLogger.info("Successfully created world: " + name);
                return world;

            } catch (Exception e) {
                GameLogger.error("Failed to create world: " + name + " - " + e.getMessage());
                return null;
            }
        }
    }

    public void saveWorld(WorldData world) {
        if (world == null) return;

        synchronized (worldLock) {
            try {
                // Create backup before saving
                storage.createWorldBackup(WorldData.fromJson(world.getName()));

                // Save world state
                storage.saveWorld(world);

                // Save player data
                if (world.getPlayers() != null) {
                    for (Map.Entry<String, PlayerData> entry : world.getPlayers().entrySet()) {
                        savePlayerData(entry.getKey(), entry.getValue());
                    }
                }

                world.setDirty(false);
                GameLogger.info("Saved world: " + world.getName());
            } catch (Exception e) {
                GameLogger.error("Failed to save world: " + world.getName() + " - " + e.getMessage());
            }
        }
    }

    public void unloadInactiveWorlds() {
        synchronized (worldLock) {
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<String, Long>> it = lastWorldAccess.entrySet().iterator();

            while (it.hasNext()) {
                Map.Entry<String, Long> entry = it.next();
                if (now - entry.getValue() > SAVE_INTERVAL * 2) {
                    String worldName = entry.getKey();
                    WorldData world = activeWorlds.remove(worldName);
                    if (world != null && world.isDirty()) {
                        saveWorld(world);
                    }
                    it.remove();
                    GameLogger.info("Unloaded inactive world: " + worldName);
                }
            }
        }
    }

    public void shutdown() {
        try {
            GameLogger.info("Shutting down ServerWorldManager...");

            // Save all active worlds and player data
            synchronized (worldLock) {
                // Save worlds
                for (WorldData world : activeWorlds.values()) {
                    if (world.isDirty()) {
                        saveWorld(world);
                    }
                }

                // Save all player data
                for (Map.Entry<UUID, PlayerData> entry : playerDataCache.entrySet()) {
                    storage.getPlayerDataManager().savePlayerData(entry.getKey(), entry.getValue());
                }

                // Final flush
                storage.flushPlayerData();
            }

            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }

            // Clear collections
            activeWorlds.clear();
            lastWorldAccess.clear();
            playerDataCache.clear();

            GameLogger.info("ServerWorldManager shutdown complete");
        } catch (Exception e) {
            GameLogger.error("Error during shutdown: " + e.getMessage());
        }
    }
}
