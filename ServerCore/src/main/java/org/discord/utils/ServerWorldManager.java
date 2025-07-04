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


/**
 * Manages loading & saving of worlds and chunks on the server side,
 * including chunk-specific WorldObjects, tile data, and block data.
 */
public class ServerWorldManager {

    private static final long AUTO_SAVE_INTERVAL_MS = 300_000;   // e.g. 5 minutes
    private static final long CHUNK_EVICT_TIMEOUT_MS = 600_000;  // e.g. 10 minutes
    private static ServerWorldManager instance;
    private final ServerStorageSystem storageSystem;// In ServerWorldManager, add:
    private final Map<String, WorldData> activeWorlds = new ConcurrentHashMap<>();
    private final Map<String, Map<Vector2, TimedChunk>> chunkCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final ExecutorService loadExecutor = Executors.newFixedThreadPool(4);
    private final BiomeManager biomeManager;
    private ServerWorldManager(ServerStorageSystem storageSystem) {
        this.storageSystem = storageSystem;
        this.biomeManager = new BiomeManager(System.currentTimeMillis());
        initScheduledTasks();
    }

    public static synchronized ServerWorldManager getInstance(ServerStorageSystem storageSystem) {
        if (instance == null) {
            instance = new ServerWorldManager(storageSystem);
        }
        return instance;
    }

    public BiomeTransitionResult getBiomeTransitionAt(float worldX, float worldY) {
        return biomeManager.getBiomeAt(worldX, worldY);
    }

    private void initScheduledTasks() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                for (WorldData wd : activeWorlds.values()) {
                    if (wd.isDirty()) {
                        saveWorld(wd);
                    }
                }
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
        saveWorld(wd);
        activeWorlds.put(worldName, wd);
        chunkCache.put(worldName, new ConcurrentHashMap<>());
        return wd;
    }
    public Chunk loadChunk(String worldName, int chunkX, int chunkY) {
        WorldData wd = loadWorld(worldName);
        if (wd == null) {
            GameLogger.error("WorldData is null for: " + worldName);
            return null;
        }

        Map<Vector2, TimedChunk> worldChunkMap =
            chunkCache.computeIfAbsent(worldName, k -> new ConcurrentHashMap<>());
        Vector2 pos = new Vector2(chunkX, chunkY);
        TimedChunk timed = worldChunkMap.get(pos);

        if (timed == null || timed.chunk == null) {
            Chunk loaded = loadChunkFromDisk(worldName, chunkX, chunkY);
            if (loaded == null) {
                long determinSeed = wd.getConfig().getSeed() +
                    (((long)chunkX << 32) | ((long)chunkY & 0xFFFFFFFFL));

                loaded = generateNewChunk(chunkX, chunkY, determinSeed);
                List<WorldObject> generatedObjects = loaded.getWorldObjects();
                if (generatedObjects != null && !generatedObjects.isEmpty()) {
                    ServerGameContext.get().getWorldObjectManager().setObjectsForChunk(worldName, pos, generatedObjects);
                    GameLogger.info("Populated WorldObjectManager with " + generatedObjects.size() + " new objects for chunk " + pos);
                }
                if (loaded.getBlocks() != null && !loaded.getBlocks().isEmpty()){
                    for(PlaceableBlock block : loaded.getBlocks().values()){
                        ServerGameContext.get().getServerBlockManager().getPlacedBlocks().put(block.getPosition(), block);
                    }
                    GameLogger.info("Populated ServerBlockManager with " + loaded.getBlocks().size() + " new blocks for chunk " + pos);
                }
                saveChunk(worldName, loaded);
            }
            timed = new TimedChunk(loaded);
            worldChunkMap.put(pos, timed);
            if (loaded.isDirty()) {
                saveChunk(worldName, loaded);
            }
        }
        if (timed.chunk.isDirty()) {
            saveChunk(worldName, timed.chunk);
            timed.chunk.setDirty(false);
        }

        timed.lastAccess = System.currentTimeMillis();
        return timed.chunk;
    }
    /**
     * Retrieves a Biome object by its type from the internal BiomeManager.
     * @param type The BiomeType to retrieve.
     * @return The Biome object, or a fallback if not found.
     */
    public Biome getBiome(BiomeType type) {
        return biomeManager.getBiome(type);
    }
    private Chunk generateNewChunk(int chunkX, int chunkY, long seed) {
        int worldTileX = chunkX * Chunk.CHUNK_SIZE;
        int worldTileY = chunkY * Chunk.CHUNK_SIZE;
        float centerPixelX = (chunkX * Chunk.CHUNK_SIZE + Chunk.CHUNK_SIZE * 0.5f) * World.TILE_SIZE;
        float centerPixelY = (chunkY * Chunk.CHUNK_SIZE + Chunk.CHUNK_SIZE * 0.5f) * World.TILE_SIZE;

        BiomeTransitionResult btr = biomeManager.getBiomeAt(centerPixelX, centerPixelY);
        if (btr == null || btr.getPrimaryBiome() == null) {
            btr = new BiomeTransitionResult(
                biomeManager.getBiome(BiomeType.PLAINS),
                null,
                1.0f
            );
        }
        Chunk chunk = UnifiedWorldGenerator.generateChunkForServer(
            chunkX, chunkY, seed, biomeManager);
        chunk.setBiome(btr.getPrimaryBiome());

        return chunk;
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
            Biome biome = biomeManager.getBiome(cd.biomeType);
            if (biome == null) {
                biome = biomeManager.getBiome(BiomeType.PLAINS);
            }
            Chunk chunk = new Chunk(chunkX, chunkY, biome, cd.generationSeed);
            chunk.setTileData(cd.tileData);
            if (cd.blockData != null) {
                for (BlockSaveData.BlockData bd : cd.blockData) {
                    processBlockData(chunk, bd);
                }
            }
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
            if (blockType == PlaceableBlock.BlockType.CHEST) {
                block.setChestOpen(blockData.isChestOpen);
                if (blockData.chestData != null) {
                    block.setChestData(blockData.chestData);
                    GameLogger.info("Loaded chest at " + pos + " with " +
                        blockData.chestData.items.stream().filter(Objects::nonNull).count() + " items");
                }
            }

            chunk.addBlock(block);
            ServerGameContext.get().getServerBlockManager().getPlacedBlocks().put(pos, block);

        } catch (Exception e) {
            GameLogger.error("Failed to process block data: " + e.getMessage());
        }
    }

    /**
     * Returns a map of all currently loaded chunks (as Chunk objects)
     * for the given world name. This is used by the server to determine
     * which chunks are active so that wild Pokémon can be spawned in them.
     *
     * @param worldName The name (ID) of the world.
     * @return A Map keyed by chunk position (Vector2) to the loaded Chunk.
     */
    public Map<Vector2, Chunk> getLoadedChunks(String worldName) {
        Map<Vector2, TimedChunk> cache = chunkCache.get(worldName);
        Map<Vector2, Chunk> loadedChunks = new HashMap<>();
        if (cache != null) {
            for (Map.Entry<Vector2, TimedChunk> entry : cache.entrySet()) {
                if (entry.getValue().chunk != null) {
                    entry.getValue().lastAccess = System.currentTimeMillis();
                    loadedChunks.put(entry.getKey(), entry.getValue().chunk);
                }
            }
        }
        return loadedChunks;
    }

    private Path getChunkFilePath(String worldName, int chunkX, int chunkY) {
        return Paths.get("server", "data", "worlds", worldName, "chunks",
            "chunk_" + chunkX + "_" + chunkY + ".json");
    }


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

            List<WorldObject> objects = chunk.getWorldObjects();
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
            chunk.setDirty(false);
            WorldData wd = loadWorld(worldName);
            if (wd != null) {
                Vector2 chunkKey = new Vector2(chunk.getChunkX(), chunk.getChunkY());
                wd.getChunks().put(chunkKey, chunk);
                wd.addChunkObjects(chunkKey, objects);
                wd.setDirty(true);
                saveWorld(wd);
            } else {
                GameLogger.error("Could not load WorldData for " + worldName + " to update chunk " + chunk.getChunkX() + "," + chunk.getChunkY());
            }

            GameLogger.info("Chunk saved successfully for chunk " + new Vector2(chunk.getChunkX(), chunk.getChunkY()));
        } catch (Exception e) {
            GameLogger.error("Failed to save chunk: " + e.getMessage());
        }
    }

    private void evictIdleChunks() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Map<Vector2, TimedChunk>> entry : chunkCache.entrySet()) {
            String worldName = entry.getKey();
            Map<Vector2, TimedChunk> chunkMap = entry.getValue();

            Iterator<Map.Entry<Vector2, TimedChunk>> it = chunkMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Vector2, TimedChunk> e = it.next();
                TimedChunk container = e.getValue();
                if ((now - container.lastAccess) >= CHUNK_EVICT_TIMEOUT_MS) {
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

    public void shutdown() {
        GameLogger.info("Shutting down ServerWorldManager...");
        for (Map.Entry<String, Map<Vector2, TimedChunk>> entry : chunkCache.entrySet()) {
            String worldName = entry.getKey();
            for (TimedChunk tchunk : entry.getValue().values()) {
                if (tchunk.chunk.isDirty()) {
                    saveChunk(worldName, tchunk.chunk);
                }
            }
        }
        for (WorldData wd : activeWorlds.values()) {
            if (wd.isDirty()) {
                saveWorld(wd);
            }
        }
        loadExecutor.shutdown();
        scheduler.shutdown();

        GameLogger.info("ServerWorldManager shutdown complete.");
    }

    /**
     * For storing chunk + lastAccess time so we can evict if idle.
     */
    static class TimedChunk {
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
        public List<HashMap<String, Object>> worldObjects = new ArrayList<>();


    }
}
