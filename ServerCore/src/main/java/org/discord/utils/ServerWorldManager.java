package org.discord.utils;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.managers.BiomeManager;
import io.github.pokemeetup.system.data.BlockSaveData;
import io.github.pokemeetup.system.data.WorldData;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.multiplayer.server.ServerStorageSystem;
import io.github.pokemeetup.utils.OpenSimplex2;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

/**
 * Manages loading & saving of worlds and chunks on the server side,
 * including chunk-specific WorldObjects, tile data, and block data.
 */
public class ServerWorldManager {

    private static final long AUTO_SAVE_INTERVAL_MS = 300_000;   // e.g. 5 minutes
    private static final long CHUNK_EVICT_TIMEOUT_MS = 600_000;  // e.g. 10 minutes
    private static ServerWorldManager instance;

    // Biome & storage references
    private final Map<String, BiomeManager> worldBiomeManagers = new ConcurrentHashMap<>();
    private final ServerStorageSystem storageSystem;

    // Worlds & Chunks in memory
    private final Map<String, WorldData> activeWorlds = new ConcurrentHashMap<>();
    private final Map<String, Map<Vector2, TimedChunk>> chunkCache = new ConcurrentHashMap<>();

    // Schedulers
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final ExecutorService loadExecutor = Executors.newFixedThreadPool(4);

    // ------------------------------------------------------------------------------------
    // SINGLETON INIT
    // ------------------------------------------------------------------------------------

    private ServerWorldManager(ServerStorageSystem storageSystem) {
        this.storageSystem = storageSystem;
        initScheduledTasks();
    }

    public static synchronized ServerWorldManager getInstance(ServerStorageSystem storageSystem) {
        if (instance == null) {
            instance = new ServerWorldManager(storageSystem);
        }
        return instance;
    }

    // ------------------------------------------------------------------------------------
    // PERIODIC TASKS
    // ------------------------------------------------------------------------------------

    private void initScheduledTasks() {
        // Periodically auto-save worlds & evict idle chunks
        scheduler.scheduleAtFixedRate(() -> {
            try {
                // Save any dirty worlds
                for (WorldData wd : activeWorlds.values()) {
                    if (wd.isDirty()) {
                        saveWorld(wd);
                    }
                }
                // Evict idle chunks
                evictIdleChunks();
            } catch (Exception e) {
                GameLogger.error("Error in scheduled task: " + e.getMessage());
            }
        }, AUTO_SAVE_INTERVAL_MS, AUTO_SAVE_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    // ------------------------------------------------------------------------------------
    // WORLD LOADING & SAVING
    // ------------------------------------------------------------------------------------

    /**
     * Loads the specified world from disk if not already in memory,
     * and caches it in activeWorlds.
     */
    public synchronized WorldData loadWorld(String worldName) {
        if (activeWorlds.containsKey(worldName)) {
            return activeWorlds.get(worldName);
        }
        WorldData wd = storageSystem.loadWorld(worldName);
        if (wd == null) {
            GameLogger.error("World not found in server storage: " + worldName);
            return null;
        }
        activeWorlds.put(worldName, wd);
        // Create a chunk cache for that world
        chunkCache.put(worldName, new ConcurrentHashMap<>());

        GameLogger.info("Loaded world '" + worldName + "' from server storage.");
        return wd;
    }

    /**
     * Saves high-level WorldData only (not chunk data) to server storage.
     */
    public synchronized void saveWorld(WorldData worldData) {
        if (worldData == null) return;
        try {
            storageSystem.saveWorld(worldData);
            worldData.setDirty(false);
            GameLogger.info("Saved world: " + worldData.getName());
        } catch (Exception e) {
            GameLogger.error("Failed to save world: " + e.getMessage());
        }
    }

    /**
     * Creates a brand-new world, stored under server/data/worlds/<name>/world.json,
     * then caches it in memory.
     */
    public synchronized WorldData createWorld(String worldName, long seed, float treeRate, float pokeRate) {
        if (activeWorlds.containsKey(worldName) || storageSystem.worldExists(worldName)) {
            GameLogger.error("World '" + worldName + "' already exists.");
            return null;
        }
        WorldData wd = new WorldData(worldName);
        WorldData.WorldConfig config = new WorldData.WorldConfig(seed);
        config.setTreeSpawnRate(treeRate);
        config.setPokemonSpawnRate(pokeRate);
        wd.setConfig(config);

        // Save to disk
        saveWorld(wd);

        // Cache in memory
        activeWorlds.put(worldName, wd);
        chunkCache.put(worldName, new ConcurrentHashMap<>());
        return wd;
    }

    private BiomeManager getBiomeManager(String worldName) {
        return worldBiomeManagers.computeIfAbsent(worldName, k -> {
            WorldData wd = activeWorlds.get(k);
            if (wd == null) {
                // fallback if loaded incorrectly
                return new BiomeManager(System.currentTimeMillis());
            }
            return new BiomeManager(wd.getConfig().getSeed());
        });
    }

    // ------------------------------------------------------------------------------------
    // CHUNK LOADING & GENERATION
    // ------------------------------------------------------------------------------------

    /**
     * Loads a chunk from memory if cached, otherwise from disk, otherwise
     * generates a new one if not found on disk.
     */
    public Chunk loadChunk(String worldName, int chunkX, int chunkY) {
        WorldData wd = loadWorld(worldName);
        if (wd == null) return null;

        Map<Vector2, TimedChunk> worldChunkMap = chunkCache.get(worldName);
        Vector2 pos = new Vector2(chunkX, chunkY);

        TimedChunk timed = worldChunkMap.computeIfAbsent(pos, (p) -> {
            Chunk loaded = loadChunkFromDisk(worldName, chunkX, chunkY);
            if (loaded == null) {
                // Generate new chunk if not found on disk
                loaded = generateNewChunk(wd, chunkX, chunkY);
                loaded.setDirty(true); // Mark as dirty to ensure it gets saved
            }
            return new TimedChunk(loaded);
        });

        // Save the chunk only if it's newly generated and dirty
        if (timed.chunk.isDirty()) {
            saveChunk(worldName, timed.chunk);
            timed.chunk.setDirty(false);
        }

        return timed.chunk;
    }
    private Chunk loadChunkFromDisk(String worldName, int chunkX, int chunkY) {
        Path path = getChunkFilePath(worldName, chunkX, chunkY);

        try {
            // Check if the chunk file exists
            if (!storageSystem.getFileSystem().exists(path.toString())) {
                return null; // Not found
            }
            // Read JSON
            String jsonContent = storageSystem.getFileSystem().readString(path.toString());
            if (jsonContent == null || jsonContent.isEmpty()) {
                return null;
            }

            Json json = new Json();
            // Convert to ChunkData
            ChunkData cd = json.fromJson(ChunkData.class, jsonContent);
            // Build an actual Chunk from it
            Chunk chunk = cd.toChunk();

            // Re-inject the chunk’s WorldObjects
            List<WorldObject> objectList = new ArrayList<>();
            if (cd.worldObjects != null) {
                for (Map<String, Object> objData : cd.worldObjects) {
                    WorldObject obj = new WorldObject();
                    obj.updateFromData(objData);
                    objectList.add(obj);
                }
            }

            // Put objects in the worldData’s chunkObjects map
            Vector2 chunkPos = new Vector2(chunkX, chunkY);
            WorldData wd = activeWorlds.get(worldName);
            wd.getChunkObjects().put(chunkPos, objectList);

            return chunk;

        } catch (IOException e) {
            GameLogger.error("Error reading chunk from disk: " + e.getMessage());
            return null;
        }
    }

    /**
     * Generates a new chunk if it doesn’t exist on disk. Basic flow:
     * 1) Determine biome
     * 2) Generate tile data
     * 3) Place default objects
     */
    public Chunk generateNewChunk(WorldData wd, int chunkX, int chunkY) {
        BiomeManager biomeManager = getBiomeManager(wd.getName());
        long chunkSeed = generateChunkSeed(wd.getConfig().getSeed(), chunkX, chunkY);

        // Determine biome from noise
        float centerX = (chunkX * Chunk.CHUNK_SIZE + Chunk.CHUNK_SIZE / 2f) * World.TILE_SIZE;
        float centerY = (chunkY * Chunk.CHUNK_SIZE + Chunk.CHUNK_SIZE / 2f) * World.TILE_SIZE;
        BiomeType biomeType = getChunkBiomeType(centerX, centerY, chunkSeed);
        Biome biome = biomeManager.getBiome(biomeType);

        // Construct the chunk
        Chunk chunk = new Chunk(chunkX, chunkY, biome, chunkSeed, biomeManager);

        // Generate tile data
        int[][] tiles = generateDeterministicTileData(chunk, biome, chunkSeed);
        chunk.setTileData(tiles);

        // Spawn some initial objects
        Vector2 chunkPos = new Vector2(chunkX, chunkY);
        List<WorldObject> spawned = spawnBiomeObjectsForNewChunk(chunk, biome, chunkSeed);

        // Put in WorldData’s chunk-objects
        wd.getChunkObjects().put(chunkPos, spawned);

        // Mark as dirty
        chunk.setDirty(true);
        wd.setDirty(true);

        return chunk;
    }

    private Path getChunkFilePath(String worldName, int chunkX, int chunkY) {
        return Paths.get("server", "data", "worlds", worldName, "chunks",
            "chunk_" + chunkX + "_" + chunkY + ".json");
    }

    // ------------------------------------------------------------------------------------
    // CHUNK HELPER METHODS
    // ------------------------------------------------------------------------------------

    private long generateChunkSeed(long worldSeed, int chunkX, int chunkY) {
        return hashCoordinates(worldSeed, chunkX, chunkY);
    }

    private static long hashCoordinates(long seed, int x, int y) {
        long hash = seed;
        hash = hash * 31 + x;
        hash = hash * 31 + y;
        return hash;
    }

    private BiomeType getChunkBiomeType(float worldX, float worldY, long chunkSeed) {
        double temperature = deterministicNoise(chunkSeed, worldX * 0.05, worldY * 0.05);
        double moisture = deterministicNoise(chunkSeed + 1, worldX * 0.05, worldY * 0.05);
        return mapToBiomeType(temperature, moisture);
    }

    private double deterministicNoise(long seed, double x, double y) {
        int fx = (int) (x * 10000);
        int fy = (int) (y * 10000);
        long noiseSeed = hashCoordinates(seed, fx, fy);
        return OpenSimplex2.noise2_ImproveX(noiseSeed, x, y);
    }

    private BiomeType mapToBiomeType(double temperature, double moisture) {
        // Example logic
        if (temperature < 0.2) {
            return BiomeType.SNOW;
        } else if (temperature > 0.8) {
            if (moisture < 0.3) {
                return BiomeType.DESERT;
            }
            return BiomeType.RAIN_FOREST;
        } else if (temperature > 0.6) {
            if (moisture > 0.6) return BiomeType.FOREST;
        } else if (moisture < 0.3) {
            return BiomeType.PLAINS;
        } else if (moisture > 0.7) {
            return BiomeType.HAUNTED;
        }
        return BiomeType.PLAINS;
    }

    private int[][] generateDeterministicTileData(Chunk chunk, Biome biome, long seed) {
        int[][] tiles = new int[Chunk.CHUNK_SIZE][Chunk.CHUNK_SIZE];
        for (int lx = 0; lx < Chunk.CHUNK_SIZE; lx++) {
            for (int ly = 0; ly < Chunk.CHUNK_SIZE; ly++) {
                long tileSeed = hashCoordinates(seed,
                    chunk.getChunkX() * Chunk.CHUNK_SIZE + lx,
                    chunk.getChunkY() * Chunk.CHUNK_SIZE + ly
                );
                tiles[lx][ly] = pickTileType(tileSeed, biome.getTileDistribution());
            }
        }
        return tiles;
    }

    private int pickTileType(long seed, Map<Integer, Integer> distribution) {
        int total = distribution.values().stream().mapToInt(Integer::intValue).sum();
        int roll = Math.abs((int) (seed % total));
        int sum = 0;

        // sorted iteration for consistency
        List<Map.Entry<Integer, Integer>> sorted = new ArrayList<>(distribution.entrySet());
        sorted.sort(Map.Entry.comparingByKey());

        for (Map.Entry<Integer, Integer> entry : sorted) {
            sum += entry.getValue();
            if (roll < sum) {
                return entry.getKey();
            }
        }
        // fallback
        return sorted.get(0).getKey();
    }

    private List<WorldObject> spawnBiomeObjectsForNewChunk(Chunk chunk, Biome biome, long chunkSeed) {
        List<WorldObject> objects = new ArrayList<>();
        Random rand = new Random(chunkSeed);

        for (WorldObject.ObjectType objType : biome.getSpawnableObjects()) {
            double chance = biome.getSpawnChanceForObject(objType);
            if (chance <= 0.0) continue;

            for (int lx = 0; lx < Chunk.CHUNK_SIZE; lx++) {
                for (int ly = 0; ly < Chunk.CHUNK_SIZE; ly++) {
                    int tileType = chunk.getTileType(lx, ly);
                    if (!biome.getAllowedTileTypes().contains(tileType)) {
                        continue;
                    }
                    if (rand.nextDouble() < chance) {
                        int wTileX = chunk.getChunkX() * Chunk.CHUNK_SIZE + lx;
                        int wTileY = chunk.getChunkY() * Chunk.CHUNK_SIZE + ly;
                        WorldObject obj = new WorldObject(
                            wTileX, wTileY, null, objType
                        );
                        objects.add(obj);
                    }
                }
            }
        }

        return objects;
    }

    // ------------------------------------------------------------------------------------
    // CHUNK SAVING
    // ------------------------------------------------------------------------------------

    public void saveChunk(String worldName, Chunk chunk) {
        if (chunk == null) {
            GameLogger.error("Cannot save null chunk");
            return;
        }

        Path chunkPath = getChunkFilePath(worldName, chunk.getChunkX(), chunk.getChunkY());
        GameLogger.info("Saving chunk at (" + chunk.getChunkX() + "," + chunk.getChunkY() + ") to: " + chunkPath);

        try {
            // Ensure parent directories exist
            Path chunksDir = chunkPath.getParent();
            if (chunksDir != null) {
                storageSystem.getFileSystem().createDirectory(chunksDir.toString());
            }

            // Get WorldData and validate
            WorldData wd = activeWorlds.get(worldName);
            if (wd == null) {
                GameLogger.error("Could not find WorldData for: " + worldName);
                return;
            }

            // Build chunk data
            ChunkData cd = ChunkData.fromChunk(chunk);
            cd.chunkX = chunk.getChunkX();
            cd.chunkY = chunk.getChunkY();
            cd.biomeType = chunk.getBiome().getType();
            cd.tileData = chunk.getTileData().clone();
            cd.blockData = new ArrayList<>(chunk.getBlockDataForSave());
            cd.generationSeed = chunk.getGenerationSeed();

            // Get chunk objects
            Vector2 chunkPos = new Vector2(chunk.getChunkX(), chunk.getChunkY());
            List<WorldObject> objectsInChunk = wd.getChunkObjects().get(chunkPos);
            if (objectsInChunk == null) {
                objectsInChunk = Collections.emptyList();
            }

            // Convert objects to serializable format
            List<Map<String, Object>> objMaps = new ArrayList<>();
            for (WorldObject obj : objectsInChunk) {
                if (obj != null) {
                    Map<String, Object> objData = obj.getSerializableData();
                    if (objData != null) {
                        objMaps.add(objData);
                        GameLogger.info("Saving " + obj.getType() + " at (" + obj.getTileX() + "," + obj.getTileY() + ")");
                    }
                }
            }
            cd.worldObjects = objMaps;

            // Serialize to JSON
            Json json = new Json();
            json.setOutputType(JsonWriter.OutputType.json);
            String jsonData = json.prettyPrint(cd);

            // Write to disk
            storageSystem.getFileSystem().writeString(chunkPath.toString(), jsonData);

            // Update cache states
            chunk.setDirty(false);
            Map<Vector2, TimedChunk> worldChunkMap = chunkCache.get(worldName);
            if (worldChunkMap != null) {
                worldChunkMap.put(chunkPos, new TimedChunk(chunk));
            }

            // Update world's chunk map
            wd.getChunks().put(chunkPos, chunk);
            GameLogger.info("Successfully saved chunk with " + objMaps.size() + " objects to " + chunkPath);

        } catch (Exception e) {
            GameLogger.error("Failed to save chunk at (" + chunk.getChunkX() + "," + chunk.getChunkY() + "): " + e.getMessage());
            e.printStackTrace();
        }
    }
    // ------------------------------------------------------------------------------------
    // CHUNK EVICTION
    // ------------------------------------------------------------------------------------

    private void evictIdleChunks() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Map<Vector2, TimedChunk>> entry : chunkCache.entrySet()) {
            String worldName = entry.getKey();
            Map<Vector2, TimedChunk> chunkMap = entry.getValue();

            Iterator<Map.Entry<Vector2, TimedChunk>> it = chunkMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Vector2, TimedChunk> e = it.next();
                TimedChunk container = e.getValue();
                // If chunk is idle for too long, evict it
                if ((now - container.lastAccess) >= CHUNK_EVICT_TIMEOUT_MS) {
                    // Save if dirty
                    if (container.chunk.isDirty()) {
                        saveChunk(worldName, container.chunk);
                    }
                    it.remove();
                    GameLogger.info("Evicted chunk (" + e.getKey().x + "," + e.getKey().y +
                        ") from world '" + worldName + "'");
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------
    // SHUTDOWN
    // ------------------------------------------------------------------------------------

    public void shutdown() {
        GameLogger.info("Shutting down ServerWorldManager...");
        // Save all loaded chunks
        for (Map.Entry<String, Map<Vector2, TimedChunk>> entry : chunkCache.entrySet()) {
            String worldName = entry.getKey();
            for (TimedChunk tchunk : entry.getValue().values()) {
                if (tchunk.chunk.isDirty()) {
                    saveChunk(worldName, tchunk.chunk);
                }
            }
        }
        // Save all dirty worlds
        for (WorldData wd : activeWorlds.values()) {
            if (wd.isDirty()) {
                saveWorld(wd);
            }
        }
        loadExecutor.shutdown();
        scheduler.shutdown();
        GameLogger.info("ServerWorldManager shutdown complete.");
    }

    // ------------------------------------------------------------------------------------
    // INNER CLASSES
    // ------------------------------------------------------------------------------------

    /** For storing chunk + lastAccess time so we can evict if idle. */
    private static class TimedChunk {
        final Chunk chunk;
        long lastAccess;

        TimedChunk(Chunk chunk) {
            this.chunk = chunk;
            this.lastAccess = System.currentTimeMillis();
        }
    }

    /**
     * Represents chunk data in chunk_<x>_<y>.json, including tileData,
     * block data, and a list of serialized world objects.
     */
    public static class ChunkData {
        public int chunkX;
        public int chunkY;
        public BiomeType biomeType;
        public int[][] tileData;
        public List<BlockSaveData.BlockData> blockData = new ArrayList<>();
        public long generationSeed;

        // Store objects as a list of Maps
        public List<Map<String, Object>> worldObjects = new ArrayList<>();

        /** Converts an in-memory chunk to our ChunkData POJO. */
        public static ChunkData fromChunk(Chunk chunk) {
            ChunkData cd = new ChunkData();
            cd.chunkX = chunk.getChunkX();
            cd.chunkY = chunk.getChunkY();
            cd.biomeType = chunk.getBiome().getType();
            cd.tileData = chunk.getTileData().clone();
            cd.blockData = new ArrayList<>(chunk.getBlockDataForSave());
            cd.generationSeed = chunk.getGenerationSeed();
            return cd;
        }

        /** Converts this ChunkData back to a real Chunk object. */
        public Chunk toChunk() {
            BiomeManager tmpBiomeMgr = new BiomeManager(generationSeed);
            Biome b = tmpBiomeMgr.getBiome(biomeType);

            Chunk chunk = new Chunk(chunkX, chunkY, b, generationSeed, tmpBiomeMgr);
            chunk.setTileData(tileData.clone());
            for (BlockSaveData.BlockData bd : blockData) {
                Vector2 pos = new Vector2(bd.x, bd.y);
                PlaceableBlock.BlockType bt = PlaceableBlock.BlockType.fromId(bd.type);
                if (bt != null) {
                    PlaceableBlock block = new PlaceableBlock(bt, pos, null, bd.isFlipped);
                    block.setChestOpen(bd.isChestOpen);
                    block.setChestData(bd.chestData);
                    chunk.addBlock(block);
                }
            }
            chunk.setGenerationSeed(generationSeed);
            return chunk;
        }
    }
}
