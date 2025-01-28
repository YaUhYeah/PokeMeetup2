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
 * Manages loading & saving of worlds and chunks on the server side.
 * Prevents storing chunk data on the client.
 */
public class ServerWorldManager {

    private static final long AUTO_SAVE_INTERVAL_MS = 300_000;  // e.g. 5 minutes
    private static final long CHUNK_EVICT_TIMEOUT_MS = 600_000; // e.g. 10 minutes
    private static final int CHUNK_SAVE_BATCH_SIZE = 50;
    private static ServerWorldManager instance;
    private final Map<String, BiomeManager> worldBiomeManagers = new ConcurrentHashMap<>();
    private final ServerStorageSystem storageSystem;
    private final Map<String, WorldData> activeWorlds = new ConcurrentHashMap<>();
    private final Map<String, Map<Vector2, TimedChunk>> chunkCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final ExecutorService loadExecutor = Executors.newFixedThreadPool(4);
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

    private BiomeManager getBiomeManager(String worldName) {
        return worldBiomeManagers.computeIfAbsent(worldName, k -> {
            WorldData world = activeWorlds.get(k);
            return new BiomeManager(world.getConfig().getSeed());
        });
    }

    /**
     * Periodic tasks: auto-save worlds and evict idle chunks.
     */
    private void initScheduledTasks() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                // Auto-save any dirty worlds
                for (WorldData wd : activeWorlds.values()) {
                    if (wd.isDirty()) {
                        saveWorld(wd);
                    }
                }
                // Evict idle chunks from memory
                evictIdleChunks();
            } catch (Exception e) {
                GameLogger.error("Error in scheduled task: " + e.getMessage());
            }
        }, AUTO_SAVE_INTERVAL_MS, AUTO_SAVE_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }
    public Chunk generateNewChunk(WorldData wd, int chunkX, int chunkY) {
        BiomeManager biomeManager = getBiomeManager(wd.getName());

        // Use consistent seed derivation
        long chunkSeed = generateChunkSeed(wd.getConfig().getSeed(), chunkX, chunkY);

        // Calculate deterministic biome
        float worldX = (chunkX * Chunk.CHUNK_SIZE + Chunk.CHUNK_SIZE / 2f) * World.TILE_SIZE;
        float worldY = (chunkY * Chunk.CHUNK_SIZE + Chunk.CHUNK_SIZE / 2f) * World.TILE_SIZE;

        BiomeType type = getChunkBiomeType(worldX, worldY, chunkSeed);
        Chunk chunk = new Chunk(chunkX, chunkY, biomeManager.getBiome(type), chunkSeed, biomeManager);

        // Generate deterministic tile data using the chunk seed
        int[][] tiles = generateDeterministicTileData(chunk, biomeManager.getBiome(type), chunkSeed);
        chunk.setTileData(tiles);
        chunk.setDirty(true);

        return chunk;
    }



    private BiomeType getChunkBiomeType(float worldX, float worldY, long chunkSeed) {
        // Use deterministic noise generation with the chunk seed
        double temperature = deterministicNoise(chunkSeed, worldX * 0.05, worldY * 0.05);
        double moisture = deterministicNoise(chunkSeed + 1, worldX * 0.05, worldY * 0.05);

        // Map temperature and moisture to biome type deterministically
        return mapToBiomeType(temperature, moisture);
    }


    private BiomeType mapToBiomeType(double temperature, double moisture) {
        // Temperature and moisture thresholds for biome mapping
        if (temperature < 0.2) {
            return BiomeType.SNOW;
        } else if (temperature > 0.8) {
            if (moisture < 0.3) {
                return BiomeType.DESERT;
            } else {
                return BiomeType.RAIN_FOREST;
            }
        } else if (temperature > 0.6) {
            if (moisture > 0.6) {
                return BiomeType.FOREST;
            }
        } else if (moisture < 0.3) {
            return BiomeType.PLAINS;
        } else if (moisture > 0.7) {
            return BiomeType.HAUNTED;
        }

        return BiomeType.PLAINS; // Default biome
    }


    private int getConsistentTileType(Random random, Map<Integer, Integer> distribution, int total) {
        int roll = random.nextInt(total);
        int sum = 0;

        for (Map.Entry<Integer, Integer> entry : distribution.entrySet()) {
            sum += entry.getValue();
            if (roll < sum) {
                return entry.getKey();
            }
        }
        return distribution.keySet().iterator().next();
    }
    // ------------------------------------------------------------------------
    //                            WORLD LOADING
    // ------------------------------------------------------------------------

    /**
     * Loads a world (or returns from memory if already loaded).
     */
    public synchronized WorldData loadWorld(String worldName) {
        // Already loaded?
        if (activeWorlds.containsKey(worldName)) {
            return activeWorlds.get(worldName);
        }

        // Otherwise, load from storage
        WorldData wd = storageSystem.loadWorld(worldName);
        if (wd == null) {
            GameLogger.error("World not found in server storage: " + worldName);
            return null;
        }
        // Cache it
        activeWorlds.put(worldName, wd);
        // Create a chunk cache for it
        chunkCache.put(worldName, new ConcurrentHashMap<>());

        GameLogger.info("Loaded world '" + worldName + "' from server storage.");
        return wd;
    }

    /**
     * Saves the top-level world data (not chunk files).
     * Typically you call this when `isDirty()` is true.
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
     * Creates a brand-new world on the server,
     * stored at `server/data/worlds/<worldName>/world.json`
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

        // Save it so it exists on disk
        saveWorld(wd);
        // Keep it active
        activeWorlds.put(worldName, wd);
        chunkCache.put(worldName, new ConcurrentHashMap<>());

        return wd;
    }

    // ------------------------------------------------------------------------
    //                            CHUNK LOADING
    // ------------------------------------------------------------------------

    /**
     * Loads (or generates) a chunk from the server side for the given world and chunk coords.
     */

    private static long hashCoordinates(long seed, int x, int y) {
        long hash = seed;
        hash = hash * 31 + x;
        hash = hash * 31 + y;
        return hash;
    }

    // Modify generateChunkSeed to be platform-independent
    private long generateChunkSeed(long worldSeed, int chunkX, int chunkY) {
        // Use simple mathematical operations instead of bit shifts
        // to ensure consistent behavior across platforms
        return hashCoordinates(worldSeed, chunkX, chunkY);
    }

    private double deterministicNoise(long seed, double x, double y) {
        // Use fixed-point math to ensure consistent results
        int fixedX = (int)(x * 10000);
        int fixedY = (int)(y * 10000);
        long noiseSeed = hashCoordinates(seed, fixedX, fixedY);

        // Use consistent noise implementation
        return OpenSimplex2.noise2_ImproveX(noiseSeed, x, y);
    }

    private int[][] generateDeterministicTileData(Chunk chunk, Biome biome, long seed) {
        int[][] tiles = new int[Chunk.CHUNK_SIZE][Chunk.CHUNK_SIZE];

        // Use platform-independent random number generation
        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
                // Generate deterministic random value for this tile
                long tileSeed = hashCoordinates(seed,
                    chunk.getChunkX() * Chunk.CHUNK_SIZE + x,
                    chunk.getChunkY() * Chunk.CHUNK_SIZE + y);

                // Use the tile seed to determine tile type
                tiles[x][y] = getConsistentTileType(tileSeed, biome.getTileDistribution());
            }
        }
        return tiles;
    }

    private int getConsistentTileType(long seed, Map<Integer, Integer> distribution) {
        // Platform-independent random selection
        int total = distribution.values().stream().mapToInt(Integer::intValue).sum();
        int roll = Math.abs((int)(seed % total));

        int sum = 0;
        // Sort entries to ensure consistent iteration order
        List<Map.Entry<Integer, Integer>> sortedEntries =
            new ArrayList<>(distribution.entrySet());
        sortedEntries.sort(Map.Entry.comparingByKey());

        for (Map.Entry<Integer, Integer> entry : sortedEntries) {
            sum += entry.getValue();
            if (roll < sum) {
                return entry.getKey();
            }
        }
        return sortedEntries.get(0).getKey();
    }

    public Chunk loadChunk(String worldName, int chunkX, int chunkY) {
        try {
            WorldData wd = loadWorld(worldName);
            if (wd == null) return null;

            Vector2 pos = new Vector2(chunkX, chunkY);
            Map<Vector2, TimedChunk> worldChunkMap = chunkCache.computeIfAbsent(worldName, k -> new ConcurrentHashMap<>());

            return worldChunkMap.computeIfAbsent(pos, p -> {
                Chunk chunk = loadChunkFromDisk(worldName, chunkX, chunkY);
                if (chunk == null) {
                    chunk = generateNewChunk(wd, chunkX, chunkY);
                    // Make sure to set the generation seed before saving
                    chunk.setGenerationSeed(wd.getConfig().getSeed());
                    saveChunk(worldName, chunk);
                }
                return new TimedChunk(chunk);
            }).chunk;
        } catch (Exception e) {
            GameLogger.error("Failed to load/generate chunk: " + e.getMessage());
            return null;
        }
    }

    /**
     * Attempt to load chunk_x_y.json from the server filesystem. Returns null if not found.
     */
    private Chunk loadChunkFromDisk(String worldName, int chunkX, int chunkY) {
        Path path = getChunkFilePath(worldName, chunkX, chunkY);
        try {
            if (!storageSystem.getFileSystem().exists(path.toString())) {
                return null;
            }
            String jsonContent = storageSystem.getFileSystem().readString(path.toString());
            if (jsonContent == null || jsonContent.isEmpty()) {
                return null;
            }

            Json json = new Json();
            ChunkData cd = json.fromJson(ChunkData.class, jsonContent);
            return cd.toChunk();

        } catch (IOException e) {
            GameLogger.error("Error reading chunk from disk: " + e.getMessage());
            return null;
        }
    }

    private Path getChunkFilePath(String worldName, int chunkX, int chunkY) {
        return Paths.get(
            "server", "data", "worlds",
            worldName, "chunks",
            "chunk_" + chunkX + "_" + chunkY + ".json"
        );
    }

    /**
     * Generates a brand-new chunk if no chunk file exists.
     * Uses your BiomeManager logic to figure out tile data, etc.
     */


    private int[][] generateTileData(Chunk chunk, io.github.pokemeetup.system.gameplay.overworld.biomes.Biome biome, long seed) {
        int[][] tiles = new int[Chunk.CHUNK_SIZE][Chunk.CHUNK_SIZE];
        Random random = new Random(seed + (chunk.getChunkX() * 31L + chunk.getChunkY() * 17L));

        Map<Integer, Integer> distribution = biome.getTileDistribution();
        int total = distribution.values().stream().mapToInt(Integer::intValue).sum();

        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
                int roll = random.nextInt(total);
                int sum = 0;
                int selectedTile = biome.getAllowedTileTypes().get(0); // Default

                for (Map.Entry<Integer, Integer> entry : distribution.entrySet()) {
                    sum += entry.getValue();
                    if (roll < sum) {
                        selectedTile = entry.getKey();
                        break;
                    }
                }
                tiles[x][y] = selectedTile;
            }
        }
        return tiles;
    }

    public void saveChunk(String worldName, Chunk chunk) {
        Path chunksDir = Paths.get("server", "data", "worlds", worldName, "chunks");
        try {
            storageSystem.getFileSystem().createDirectory(chunksDir.toString());
            Path chunkPath = getChunkFilePath(worldName, chunk.getChunkX(), chunk.getChunkY());

            Json json = new Json();
            json.setOutputType(JsonWriter.OutputType.json);
            ChunkData cd = ChunkData.fromChunk(chunk);

            String jsonData = json.prettyPrint(cd);
            storageSystem.getFileSystem().writeString(chunkPath.toString(), jsonData);

            chunk.setDirty(false);
        } catch (Exception e) {
            GameLogger.error("Failed to save chunk: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------------
    //                           CHUNK EVICTION
    // ------------------------------------------------------------------------

    private void evictIdleChunks() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Map<Vector2, TimedChunk>> e : chunkCache.entrySet()) {
            String worldName = e.getKey();
            Map<Vector2, TimedChunk> chunkMap = e.getValue();

            Iterator<Map.Entry<Vector2, TimedChunk>> it = chunkMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Vector2, TimedChunk> ce = it.next();
                TimedChunk container = ce.getValue();
                if (now - container.lastAccess >= CHUNK_EVICT_TIMEOUT_MS) {
                    // Save if dirty, then remove from memory
                    if (container.chunk.isDirty()) {
                        saveChunk(worldName, container.chunk);
                    }
                    it.remove();
                    GameLogger.info("Evicted chunk (" + ce.getKey().x + "," + ce.getKey().y + ") from world '" + worldName + "'.");
                }
            }
        }
    }

    // ------------------------------------------------------------------------
    //                            SHUTDOWN
    // ------------------------------------------------------------------------

    /**
     * Shut down gracefully: save dirty worlds/chunks, stop threads.
     */
    public void shutdown() {
        GameLogger.info("Shutting down ServerWorldManager...");
        // save all loaded chunks
        for (Map.Entry<String, Map<Vector2, TimedChunk>> e : chunkCache.entrySet()) {
            String worldName = e.getKey();
            for (TimedChunk container : e.getValue().values()) {
                if (container.chunk.isDirty()) {
                    saveChunk(worldName, container.chunk);
                }
            }
        }
        // save all worlds
        for (WorldData wd : activeWorlds.values()) {
            if (wd.isDirty()) {
                saveWorld(wd);
            }
        }
        loadExecutor.shutdown();
        scheduler.shutdown();
        GameLogger.info("ServerWorldManager shutdown complete.");
    }

    // ------------------------------------------------------------------------
    //                            DATA CLASSES
    // ------------------------------------------------------------------------

    /**
     * Container that includes the chunk plus a last-access time for eviction.
     */
    private static class TimedChunk {
        final Chunk chunk;
        long lastAccess;

        TimedChunk(Chunk chunk) {
            this.chunk = chunk;
            this.lastAccess = System.currentTimeMillis();
        }
    }

    public static class ChunkData {
        public int chunkX;
        public int chunkY;
        public BiomeType biomeType;
        public int[][] tileData;
        public List<BlockSaveData.BlockData> blockData = new ArrayList<>();
        public long generationSeed;

        public static ChunkData fromChunk(Chunk chunk) {
            ChunkData cd = new ChunkData();
            cd.chunkX = chunk.getChunkX();
            cd.chunkY = chunk.getChunkY();
            cd.biomeType = chunk.getBiome().getType();
            cd.tileData = chunk.getTileData().clone();
            cd.blockData = new ArrayList<>(chunk.getBlockDataForSave());
            return cd;
        }

        public Chunk toChunk() {
            BiomeManager biomeManager = new BiomeManager(generationSeed);
            Chunk chunk = new Chunk(chunkX, chunkY, biomeManager.getBiome(biomeType), generationSeed, biomeManager);
            chunk.setTileData(tileData.clone());

            for (BlockSaveData.BlockData blockData : this.blockData) {
                Vector2 pos = new Vector2(blockData.x, blockData.y);
                PlaceableBlock.BlockType type = PlaceableBlock.BlockType.fromId(blockData.type);
                if (type != null) {
                    PlaceableBlock block = new PlaceableBlock(type, pos, null, blockData.isFlipped);
                    block.setChestOpen(blockData.isChestOpen);
                    block.setChestData(blockData.chestData);
                    chunk.addBlock(block);
                }
            }
            return chunk;
        }
    }
}
