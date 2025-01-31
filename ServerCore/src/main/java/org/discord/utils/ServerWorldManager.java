package org.discord.utils;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.managers.BiomeManager;
import io.github.pokemeetup.managers.BiomeTransitionResult;
import io.github.pokemeetup.system.data.BlockSaveData;
import io.github.pokemeetup.system.data.WorldData;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.multiplayer.server.ServerStorageSystem;
import io.github.pokemeetup.utils.OpenSimplex2;
import io.github.pokemeetup.utils.storage.JsonConfig;
import org.discord.context.ServerGameContext;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

import static io.github.pokemeetup.CreatureCaptureGame.MULTIPLAYER_WORLD_NAME;

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


    public ServerBiomeManager getServerBiomeManager() {
        return serverBiomeManager;
    }

    private final ServerBiomeManager serverBiomeManager;

    // Update the constructor:

    private final BiomeManager biomeManager;  // Add this field

    // Update constructor:
    private ServerWorldManager(ServerStorageSystem storageSystem) {
        this.storageSystem = storageSystem;
        this.biomeManager = new BiomeManager(System.currentTimeMillis());
        this.serverBiomeManager = new ServerBiomeManager(this.biomeManager);
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

    private static long hashCoordinates(long seed, int x, int y) {
        long hash = seed;
        hash = hash * 31 + x;
        hash = hash * 31 + y;
        return hash;
    }

    // ------------------------------------------------------------------------------------
    // WORLD LOADING & SAVING
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

    // ------------------------------------------------------------------------------------
    // CHUNK LOADING & GENERATION
    // ------------------------------------------------------------------------------------

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

    /**
     * Loads a chunk from memory if cached, otherwise from disk, otherwise
     * generates a new one if not found on disk.
     */
    public Chunk loadChunk(String worldName, int chunkX, int chunkY) {
        WorldData wd = loadWorld(worldName);
        if (wd == null) return null;

        GameLogger.info("Loading chunk (" + chunkX + "," + chunkY + ") for world: " + worldName);
        Map<Vector2, TimedChunk> worldChunkMap = chunkCache.get(worldName);
        Vector2 pos = new Vector2(chunkX, chunkY);

        TimedChunk timed = worldChunkMap.computeIfAbsent(pos, (p) -> {
            // First try to load from disk
            Chunk loaded = loadChunkFromDisk(worldName, chunkX, chunkY);
            if (loaded == null) {
                // Generate new chunk if not found on disk
                loaded = generateNewChunk(wd, chunkX, chunkY);
                if (loaded != null) {
                    // Check if objects were generated and stored
                    List<WorldObject> objects = wd.getChunkObjects().get(pos);
                    if (objects == null || objects.isEmpty()) {
                        GameLogger.info("No objects found for new chunk, generating...");
                        objects = spawnBiomeObjectsForNewChunk(loaded, loaded.getBiome(), loaded.getGenerationSeed());
                        if (objects != null && !objects.isEmpty()) {
                            GameLogger.info("Generated " + objects.size() + " objects for chunk " + pos);
                            wd.getChunkObjects().put(pos, objects);
                            // Mark chunk as dirty to ensure it gets saved
                            loaded.setDirty(true);
                        } else {
                            GameLogger.info("No objects generated for chunk " + pos);
                        }
                    }
                }
            }
            return new TimedChunk(loaded);
        });

        // Save the chunk if it's newly generated and dirty
        if (timed.chunk.isDirty()) {
            saveChunk(worldName, timed.chunk);
            timed.chunk.setDirty(false);
        }

        // Debug log the chunk's objects
        Vector2 chunkPos = new Vector2(chunkX, chunkY);
        List<WorldObject> objects = wd.getChunkObjects().get(chunkPos);
        if (objects != null) {
            GameLogger.info("Chunk (" + chunkX + "," + chunkY + ") has " + objects.size() + " objects:");
            for (WorldObject obj : objects) {
                GameLogger.info("- " + obj.getType() + " at (" + obj.getTileX() + "," + obj.getTileY() + ")");
            }
        } else {
            GameLogger.info("Chunk (" + chunkX + "," + chunkY + ") has NO objects");
        }

        return timed.chunk;
    }
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

            Json json = JsonConfig.getInstance();
            ChunkData cd = json.fromJson(ChunkData.class, jsonContent);

            // Create chunk with biome
            Biome biome = biomeManager.getBiome(cd.biomeType);
            if (biome == null) biome = biomeManager.getBiome(BiomeType.PLAINS);

            Chunk chunk = new Chunk(chunkX, chunkY, biome, cd.generationSeed, biomeManager);
            chunk.setTileData(cd.tileData);

            // Handle blocks
            if (cd.blockData != null) {
                for (BlockSaveData.BlockData bd : cd.blockData) {
                    processBlockData(chunk, bd);
                }
            }

            // Process and store world objects
            Vector2 chunkPos = new Vector2(chunkX, chunkY);
            List<WorldObject> objectList = new ArrayList<>();
            if (cd.worldObjects != null) {
                for (Map<String, Object> objData : cd.worldObjects) {
                    try {
                        WorldObject obj = new WorldObject();
                        obj.updateFromData(objData);
                        obj.ensureTexture();
                        objectList.add(obj);
                    } catch (Exception e) {
                        GameLogger.error("Failed to load object: " + e.getMessage());
                    }
                }
            }

            // Store in ObjectManager
            ServerGameContext.get().getWorldObjectManager()
                .setObjectsForChunk(worldName, chunkPos, objectList);

            return chunk;

        } catch (Exception e) {
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
    private void processBlockData(Chunk chunk, BlockSaveData.BlockData blockData) {
        try {
            PlaceableBlock.BlockType blockType = PlaceableBlock.BlockType.fromId(blockData.type);
            if (blockType == null) {
                GameLogger.error("Unknown block type: " + blockData.type);
                return;
            }

            Vector2 pos = new Vector2(blockData.x, blockData.y);
            PlaceableBlock block = new PlaceableBlock(blockType, pos, null, blockData.isFlipped);

            // Handle chest-specific data
            if (blockType == PlaceableBlock.BlockType.CHEST) {
                block.setChestOpen(blockData.isChestOpen);
                if (blockData.chestData != null) {
                    block.setChestData(blockData.chestData);
                    GameLogger.info("Loaded chest at " + pos + " with " +
                        blockData.chestData.items.stream().filter(Objects::nonNull).count() + " items");
                }
            }

            chunk.addBlock(block);
            GameLogger.info("Added block " + blockType + " at position " + pos);

        } catch (Exception e) {
            GameLogger.error("Failed to process block data: " + e.getMessage());
        }
    }

    public Chunk generateNewChunk(WorldData wd, int chunkX, int chunkY) {
        Vector2 chunkPos = new Vector2(chunkX, chunkY);
        BiomeData biomeData = serverBiomeManager.getBiomeData(wd.getName(), chunkPos);

        // Get core biome data
        Biome primaryBiome = serverBiomeManager.getBiomeManager().getBiome(biomeData.getPrimaryBiomeType());
        long chunkSeed = generateChunkSeed(wd.getConfig().getSeed(), chunkX, chunkY);

        // Construct the chunk
        Chunk chunk = new Chunk(chunkX, chunkY, primaryBiome, chunkSeed, serverBiomeManager.getBiomeManager());

        // Generate tile data with consideration for transitions
        int[][] tiles = generateDeterministicTileData(chunk, primaryBiome, chunkSeed);
        if (biomeData.getSecondaryBiomeType() != null) {
            Biome secondaryBiome = serverBiomeManager.getBiomeManager().getBiome(biomeData.getSecondaryBiomeType());
            blendBiomeTiles(tiles, primaryBiome, secondaryBiome, biomeData.getTransitionFactor());
        }
        chunk.setTileData(tiles);

        // Generate and store objects
        List<WorldObject> objects = spawnBiomeObjectsForNewChunk(chunk, primaryBiome, chunkSeed);
        ServerGameContext.get().getWorldObjectManager()
            .setObjectsForChunk(wd.getName(), chunkPos, objects);

        chunk.setDirty(true);
        wd.setDirty(true);

        return chunk;
    }

    private void blendBiomeTiles(int[][] tiles, Biome primary, Biome secondary, float transitionFactor) {
        Random random = new Random();
        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
                if (random.nextFloat() > transitionFactor) {
                    // Use secondary biome tile distribution
                    tiles[x][y] = pickTileType(random.nextLong(), secondary.getTileDistribution());
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------
    // CHUNK HELPER METHODS
    // ------------------------------------------------------------------------------------

    private Path getChunkFilePath(String worldName, int chunkX, int chunkY) {
        return Paths.get("server", "data", "worlds", worldName, "chunks",
            "chunk_" + chunkX + "_" + chunkY + ".json");
    }

    private long generateChunkSeed(long worldSeed, int chunkX, int chunkY) {
        return hashCoordinates(worldSeed, chunkX, chunkY);
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

    public List<WorldObject> spawnBiomeObjectsForNewChunk(Chunk chunk, Biome biome, long chunkSeed) {
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
        if (chunk == null) return;

        Path chunkPath = getChunkFilePath(worldName, chunk.getChunkX(), chunk.getChunkY());
        Vector2 chunkPos = new Vector2(chunk.getChunkX(), chunk.getChunkY());

        try {
            Path chunksDir = chunkPath.getParent();
            if (chunksDir != null) {
                storageSystem.getFileSystem().createDirectory(chunksDir.toString());
            }

            ChunkData cd = new ChunkData();
            cd.chunkX = chunk.getChunkX();
            cd.chunkY = chunk.getChunkY();
            cd.biomeType = chunk.getBiome().getType();
            cd.tileData = chunk.getTileData().clone();
            cd.blockData = new ArrayList<>(chunk.getBlockDataForSave());
            cd.generationSeed = chunk.getGenerationSeed();

            // Get objects from ServerWorldObjectManager
            List<WorldObject> objects = ServerGameContext.get().getWorldObjectManager()
                .getObjectsForChunk(worldName, chunkPos);

            if (objects != null) {
                cd.worldObjects = new ArrayList<>();
                for (WorldObject obj : objects) {
                    if (obj != null) {
                        Map<String, Object> objData = obj.getSerializableData();
                        if (objData != null) {
                            cd.worldObjects.add(objData);
                        }
                    }
                }
            }

            // Serialize and save
            Json json = new Json();
            json.setOutputType(JsonWriter.OutputType.json);
            String jsonData = json.prettyPrint(cd);
            storageSystem.getFileSystem().writeString(chunkPath.toString(), jsonData);

            chunk.setDirty(false);
            Map<Vector2, TimedChunk> worldChunkMap = chunkCache.get(worldName);
            if (worldChunkMap != null) {
                worldChunkMap.put(chunkPos, new TimedChunk(chunk));
            }

        } catch (Exception e) {
            GameLogger.error("Failed to save chunk at " + chunkPos + ": " + e.getMessage());
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

        // Save chunks
        for (Map.Entry<String, Map<Vector2, TimedChunk>> entry : chunkCache.entrySet()) {
            String worldName = entry.getKey();
            for (TimedChunk tchunk : entry.getValue().values()) {
                if (tchunk.chunk.isDirty()) {
                    saveChunk(worldName, tchunk.chunk);
                }
            }
        }

        // Save worlds
        for (WorldData wd : activeWorlds.values()) {
            if (wd.isDirty()) {
                saveWorld(wd);
            }
        }

        // Clean up executors
        loadExecutor.shutdown();
        scheduler.shutdown();

        GameLogger.info("ServerWorldManager shutdown complete.");
    }
    // ------------------------------------------------------------------------------------
    // INNER CLASSES
    // ------------------------------------------------------------------------------------

    /**
     * For storing chunk + lastAccess time so we can evict if idle.
     */
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
        public BiomeData biomeData;
        public int[][] tileData;
        public List<BlockSaveData.BlockData> blockData = new ArrayList<>();
        public long generationSeed;
        public List<Map<String, Object>> worldObjects = new ArrayList<>();

        public static ChunkData fromChunk(Chunk chunk, List<WorldObject> objects) {
            ChunkData cd = new ChunkData();
            cd.chunkX = chunk.getChunkX();
            cd.chunkY = chunk.getChunkY();
            cd.biomeType = chunk.getBiome().getType();
            cd.tileData = chunk.getTileData().clone();
            cd.blockData = new ArrayList<>(chunk.getBlockDataForSave());
            cd.generationSeed = chunk.getGenerationSeed();

            // Properly serialize world objects
            if (objects != null && !objects.isEmpty()) {
                cd.worldObjects = new ArrayList<>();
                for (WorldObject obj : objects) {
                    if (obj != null) {
                        Map<String, Object> objData = obj.getSerializableData();
                        if (objData != null) {
                            cd.worldObjects.add(objData);
                        }
                    }
                }
            }

            return cd;
        }
        public Chunk toChunk() {
            BiomeManager tmpBiomeMgr = new BiomeManager(generationSeed);
            BiomeType type = (biomeData != null) ? biomeData.getPrimaryBiomeType() : biomeType;
            Biome b = tmpBiomeMgr.getBiome(type);

            Chunk chunk = new Chunk(chunkX, chunkY, b, generationSeed, tmpBiomeMgr);
            chunk.setTileData(tileData.clone());

            // Process blocks and objects
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

            // Store biome data
            Vector2 chunkPos = new Vector2(chunkX, chunkY);
            if (biomeData != null && biomeData.getSecondaryBiomeType() != null) {
                BiomeTransitionResult transition = new BiomeTransitionResult(
                    b,
                    tmpBiomeMgr.getBiome(biomeData.getSecondaryBiomeType()),
                    biomeData.getTransitionFactor()
                );

            }

            return chunk;
        }
    }
}
