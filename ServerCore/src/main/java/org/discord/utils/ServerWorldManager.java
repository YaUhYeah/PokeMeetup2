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
import io.github.pokemeetup.system.gameplay.overworld.UnifiedWorldGenerator;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.multiplayer.server.ServerStorageSystem;
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
    private final ServerBiomeManager serverBiomeManager;
    private final BiomeManager biomeManager;


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



    // The new load–or–generate method for the server.
    public Chunk loadChunk(String worldName, int chunkX, int chunkY) {
        // First, ensure the world is loaded.
        WorldData wd = loadWorld(worldName);
        if (wd == null) {
            GameLogger.error("WorldData is null for world: " + worldName);
            return null;
        }

        // Get (or create) the per–world chunk cache.
        Map<Vector2, TimedChunk> worldChunkMap =
            chunkCache.computeIfAbsent(worldName, k -> new ConcurrentHashMap<>());
        Vector2 pos = new Vector2(chunkX, chunkY);
        TimedChunk timed = worldChunkMap.get(pos);

        if (timed == null || timed.chunk == null) {
            // Try to load the chunk from disk.
            Chunk loaded = loadChunkFromDisk(worldName, chunkX, chunkY);
            if (loaded == null) {
                // If not found on disk, generate a new chunk.
                GameLogger.info("Chunk at " + pos + " not found on disk; generating new chunk.");
                // Compute world coordinates (in tiles) for the chunk’s origin.
                int worldX = chunkX * Chunk.CHUNK_SIZE;
                int worldY = chunkY * Chunk.CHUNK_SIZE;
                // Use runtime biome lookup (ignoring any saved biome data).
                BiomeTransitionResult biomeTransition = biomeManager.getBiomeAt(
                    worldX * World.TILE_SIZE, worldY * World.TILE_SIZE);
                Biome biome = biomeTransition.getPrimaryBiome();
                if (biome == null) {
                    GameLogger.error("Null biome at " + worldX + "," + worldY);
                    biome = biomeManager.getBiome(BiomeType.PLAINS); // Fallback
                }
                // Generate the new chunk using your unified generator.
                loaded = UnifiedWorldGenerator.generateChunk(chunkX, chunkY, wd.getConfig().getSeed(), biomeManager);
                // Generate world objects for the chunk.
                ServerGameContext.get().getWorldObjectManager().setObjectsForChunk(
                    worldName, pos, ServerGameContext.get().getWorldObjectManager().generateObjectsForChunk(worldName, pos, loaded)
                );
                // Save the chunk to disk.
                saveChunk(worldName, loaded);
            }
            timed = new TimedChunk(loaded);
            worldChunkMap.put(pos, timed);
        }

        // If the chunk is dirty, save it.
        if (timed.chunk.isDirty()) {
            saveChunk(worldName, timed.chunk);
            timed.chunk.setDirty(false);
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
            // Use the saved biome type if available; however, you may choose to override it.
            Biome biome = biomeManager.getBiome(cd.biomeType);
            if (biome == null) {
                biome = biomeManager.getBiome(BiomeType.PLAINS);
            }
            Chunk chunk = new Chunk(chunkX, chunkY, biome, cd.generationSeed, biomeManager);
            chunk.setTileData(cd.tileData);
            // Process blocks.
            if (cd.blockData != null) {
                for (BlockSaveData.BlockData bd : cd.blockData) {
                    processBlockData(chunk, bd);
                }
            }
            // Process world objects.
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
            // Register the objects in the ServerWorldObjectManager.
            ServerGameContext.get().getWorldObjectManager().setObjectsForChunk(worldName, chunkPos, objectList);
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
        Chunk newChunk = UnifiedWorldGenerator.generateChunk(chunkX, chunkY, wd.getConfig().getSeed(), biomeManager);
        Vector2 pos = new Vector2(chunkX, chunkY);
        if (newChunk.getWorldObjects() != null && !newChunk.getWorldObjects().isEmpty()) {
            wd.getChunkObjects().put(pos, newChunk.getWorldObjects());
        }
        return newChunk;
    }


    // ------------------------------------------------------------------------------------
    // CHUNK HELPER METHODS
    // ------------------------------------------------------------------------------------

    private Path getChunkFilePath(String worldName, int chunkX, int chunkY) {
        return Paths.get("server", "data", "worlds", worldName, "chunks",
            "chunk_" + chunkX + "_" + chunkY + ".json");
    }


    // ------------------------------------------------------------------------------------
    // CHUNK SAVING
    // ------------------------------------------------------------------------------------

    public void saveChunk(String worldName, Chunk chunk) {
        if (chunk == null) return;
        try {
            Path chunkPath = getChunkFilePath(worldName, chunk.getChunkX(), chunk.getChunkY());
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
            List<WorldObject> objects = ServerGameContext.get().getWorldObjectManager().getObjectsForChunk(worldName,
                new Vector2(chunk.getChunkX(), chunk.getChunkY()));
            if (objects != null) {
                cd.worldObjects = new ArrayList<>();
                for (WorldObject obj : objects) {
                    if (obj != null) {
                        Map<String, Object> objData = obj.getSerializableData();
                        if (objData != null) {
                            cd.worldObjects.add(new HashMap<>(objData));
                        }
                    }
                }
            }
            Json json = JsonConfig.getInstance();
            json.setOutputType(JsonWriter.OutputType.json);
            String jsonData = json.prettyPrint(cd);
            storageSystem.getFileSystem().writeString(chunkPath.toString(), jsonData);
            // Mark the chunk as clean.
            chunk.setDirty(false);
        } catch (Exception e) {
            GameLogger.error("Failed to save chunk: " + e.getMessage());
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
        public List<HashMap<String, Object>> worldObjects = new ArrayList<>();


    }
}
