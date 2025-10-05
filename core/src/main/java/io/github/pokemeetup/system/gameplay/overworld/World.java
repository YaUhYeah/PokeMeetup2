package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.badlogic.gdx.utils.Pool;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.blocks.BlockManager;
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.managers.*;
import io.github.pokemeetup.multiplayer.OtherPlayer;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.screens.GameScreen;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.Positionable;
import io.github.pokemeetup.system.data.BlockSaveData;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.data.WorldData;
import io.github.pokemeetup.system.gameplay.inventory.ItemEntityManager;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.system.gameplay.overworld.mechanics.AutoTileSystem;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.PokemonSpawnManager;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldManager;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.storage.JsonConfig;
import io.github.pokemeetup.utils.textures.BlockTextureManager;
import io.github.pokemeetup.utils.textures.TextureManager;
import io.github.pokemeetup.utils.textures.TileType;

import java.util.*;
import java.util.concurrent.*;

import static io.github.pokemeetup.system.gameplay.overworld.EnhancedWorldObjectSpawner.isTreeType;

public class World {
    private final ArrayDeque<Vector2> autotileQueue = new ArrayDeque<>();

    private final Color currentWorldColor = new Color(1, 1, 1, 1);
    private final Color targetWorldColor = new Color(1, 1, 1, 1);
    private boolean worldColorInitialized = false;
    private DayNightCycle.TimePeriod lastTimePeriod = null;
    public static final int INITIAL_LOAD_RADIUS = 4;
    public static final int TILE_SIZE = 32;
    public static final int WORLD_SIZE = 100000 * TILE_SIZE;
    public static final int HALF_WORLD_SIZE = WORLD_SIZE / 2;
    public static final int CHUNK_SIZE = 16;
    public static final float INTERACTION_RANGE = TILE_SIZE * 1.6f;
    private static final float COLOR_TRANSITION_SPEED = 2.0f;
    private static final int MAX_CHUNK_LOAD_RADIUS = 10;
    // Increased to accommodate larger load radius (7 chunks = ~225 chunks in a circle)
    private static final int MAX_LOADED_CHUNKS = 250;
    private static final long UNLOAD_IDLE_THRESHOLD_MS = 30000;
    private static final int MAX_CHUNK_RETRY = 3;
    public static int DEFAULT_X_POSITION = 0;
    public static int DEFAULT_Y_POSITION = 0;
    private final Map<Vector2, Long> lastChunkAccess = new ConcurrentHashMap<>();
    private final Set<Vector2> dirtyChunks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final BiomeRenderer biomeRenderer;
    private final WeatherSystem weatherSystem;
    private final WeatherAudioSystem weatherAudioSystem;
    private final Map<Vector2, BiomeTransitionResult> biomeTransitions = new ConcurrentHashMap<>();
    private final Rectangle tempChunkRect = new Rectangle();
    private final WaterEffectsRenderer waterEffectsRendererForOthers = new WaterEffectsRenderer();
    private final ConcurrentLinkedQueue<Map.Entry<Vector2, Chunk>> integrationQueue = new ConcurrentLinkedQueue<>();
    public Map<Vector2, Chunk> chunks;
    private FootstepEffectManager footstepEffectManager;
    private final Color previousWorldColor = null;
    private boolean ySortDirty = true;
    private final float colorTransitionProgress = 1.0f;
    private volatile boolean initialChunksRequested = false;
    private Map<Vector2, Future<Chunk>> loadingChunks;
    private Queue<Vector2> initialChunkLoadQueue = new LinkedList<>();
    private ExecutorService chunkLoadExecutor = Executors.newFixedThreadPool(2); // limit to 2 threads
    private PlayerData currentPlayerData;
    private WorldObject nearestPokeball;
    private long lastPlayed;
    private BlockManager blockManager;
    private String name;
    private WorldData worldData;
    private PokemonSpawnManager pokemonSpawnManager;
    private long worldSeed;
    private WorldObject.WorldObjectManager objectManager;
    private BiomeTransitionResult currentBiomeTransition;
    private final Map<Vector2, Float> lightLevelMap = new HashMap<>();
    private boolean isDisposed = false;
    private final WaterEffectsRenderer waterEffects;
    private final ItemEntityManager itemEntityManager;
    private float lightLevelUpdateTimer = 0f;
    private float manageChunksTimer = 0f;
    private long initialChunkRequestTime;
    private BitmapFont loadingFont;

    public World(WorldData worldData) {
        try {
            GameLogger.info("Initializing multiplayer world: " + worldData.getName());
            this.worldData = worldData;
            this.name = worldData.getName();
            GameLogger.info("World instance created - this.name = '" + this.name + "'");
            this.worldSeed = worldData.getConfig().getSeed();
            this.blockManager = new BlockManager();
            GameContext.get().setBiomeManager(new BiomeManager(this.worldSeed));
            this.biomeRenderer = new BiomeRenderer();
            this.chunks = new ConcurrentHashMap<>();
            this.loadingChunks = new ConcurrentHashMap<>();
            this.initialChunkLoadQueue = new LinkedList<>();
            this.chunkLoadExecutor = Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "ChunkLoader-" + System.identityHashCode(r));
                t.setDaemon(true);
                try {
                    t.setPriority(Math.max(Thread.NORM_PRIORITY - 2, Thread.MIN_PRIORITY));
                } catch (Throwable ignore) {
                }
                return t;
            });

            // Initialize objectManager BEFORE loading chunks so objects can be created
            this.objectManager = new WorldObject.WorldObjectManager(worldSeed);
            this.pokemonSpawnManager = new PokemonSpawnManager(TextureManager.pokemonoverworld);
            this.weatherSystem = new WeatherSystem();

            if (!GameContext.get().isMultiplayer()) {
                loadChunksFromWorldData();

            }
            this.weatherSystem.setWorld(this);
            this.weatherAudioSystem = new WeatherAudioSystem(AudioManager.getInstance());

            GameLogger.info("Multiplayer world initialization complete");

        } catch (Exception e) {
            GameLogger.error("Failed to initialize multiplayer world: " + e.getMessage());
            throw new RuntimeException("World initialization failed", e);
        }
        footstepEffectManager = new FootstepEffectManager();
        this.itemEntityManager = new ItemEntityManager();
        waterEffects = new WaterEffectsRenderer();
    }

    public World(String name, long seed) {
        try {
            GameLogger.info("Initializing singleplayer world: " + name);
            this.name = name;
            GameLogger.info("World instance created (singleplayer) - this.name = '" + this.name + "'");
            this.itemEntityManager = new ItemEntityManager();
            this.worldSeed = seed;

            this.worldData = new WorldData(name);
            WorldData.WorldConfig config = new WorldData.WorldConfig(seed);
            this.worldData.setConfig(config);
            this.blockManager = new BlockManager();
            this.biomeRenderer = new BiomeRenderer();
            loadingFont = new BitmapFont();

            footstepEffectManager = new FootstepEffectManager();
            this.chunks = new ConcurrentHashMap<>();
            this.loadingChunks = new ConcurrentHashMap<>();

            WorldData existingData = JsonConfig.loadWorldData(name);
            if (existingData != null) {
                GameLogger.info("Found existing world data for: " + name);
                this.worldData = existingData;
                if (worldData.getBlockData() != null && worldData.getBlockData().getPlacedBlocks() != null) {
                    GameLogger.info("Migrating legacy block data from world.json to chunks...");

                    for (Map.Entry<String, List<BlockSaveData.BlockData>> entry : worldData.getBlockData().getPlacedBlocks().entrySet()) {
                        String[] parts = entry.getKey().split("_");
                        if (parts.length == 2) {
                            try {
                                int chunkX = Integer.parseInt(parts[0]);
                                int chunkY = Integer.parseInt(parts[1]);
                                Vector2 chunkPos = new Vector2(chunkX, chunkY);

                                if (!this.chunks.containsKey(chunkPos)) {
                                    Biome biome = findDominantBiomeInChunk(chunkX, chunkY, getBiomeManager());
                                    this.chunks.put(chunkPos, new Chunk(chunkX, chunkY, biome, this.worldSeed));
                                }

                                Chunk targetChunk = this.chunks.get(chunkPos);
                                for (BlockSaveData.BlockData blockDataItem : entry.getValue()) {
                                    PlaceableBlock.BlockType blockType = PlaceableBlock.BlockType.fromId(blockDataItem.type);
                                    if (blockType != null) {
                                        Vector2 blockPos = new Vector2(blockDataItem.x, blockDataItem.y);
                                        PlaceableBlock block = new PlaceableBlock(blockType, blockPos, null, blockDataItem.isFlipped);
                                        // Restore chest data if present
                                        if (blockType == PlaceableBlock.BlockType.CHEST && blockDataItem.chestData != null) {
                                            block.setChestData(blockDataItem.chestData);
                                        }
                                        targetChunk.addBlock(block);
                                    }
                                }
                                targetChunk.setDirty(true); // Mark for saving
                            } catch (NumberFormatException e) {
                                GameLogger.error("Could not parse chunk key from legacy block data: " + entry.getKey());
                            }
                        }
                    }

                    // CRITICAL FIX: Save migrated chunks IMMEDIATELY before clearing legacy data
                    GameLogger.info("Saving migrated chunks to disk...");
                    for (Map.Entry<Vector2, Chunk> entry : chunks.entrySet()) {
                        if (entry.getValue().isDirty()) {
                            saveChunkData(entry.getKey(), entry.getValue());
                            entry.getValue().setDirty(false);
                        }
                    }

                    // Only clear legacy data AFTER successful migration save
                    worldData.setBlockData(null);
                    worldData.setDirty(true);
                    WorldManager.getInstance().saveWorld(worldData);
                    GameLogger.info("Legacy block migration complete and saved");
                }

                // Initialize objectManager BEFORE loading chunks so objects can be created
                this.objectManager = new WorldObject.WorldObjectManager(worldSeed);
                this.pokemonSpawnManager = new PokemonSpawnManager(TextureManager.pokemonoverworld);

                loadChunksFromWorldData();

            }

            this.weatherSystem = new WeatherSystem();
            this.weatherSystem.setWorld(this);
            this.weatherAudioSystem = new WeatherAudioSystem(AudioManager.getInstance());

            footstepEffectManager = new FootstepEffectManager();
            GameLogger.info("After loadChunksFromWorldData, chunks.size() = " + chunks.size() + ", isEmpty? " + chunks.isEmpty());
            if (chunks.isEmpty()) {
                GameLogger.info("Chunks map is empty, calling initializeChunksAroundOrigin()");
                initializeChunksAroundOrigin();
            } else {
                GameLogger.info("Chunks map is NOT empty (" + chunks.size() + " chunks), skipping initializeChunksAroundOrigin()");
            }

        } catch (Exception e) {
            GameLogger.error("Failed to initialize singleplayer world: " + e.getMessage());
            throw new RuntimeException("World initialization failed", e);
        }

        waterEffects = new WaterEffectsRenderer();
    }

    // Add to World.java class fields
    private List<Map.Entry<Vector2, Chunk>> cachedSortedChunks = null;
    private int cachedChunkCount = 0;
    private final List<RenderableEntity> ySortQueue = new ArrayList<>(512);
    private final Pool<RenderableEntity> renderableEntityPool = new Pool<RenderableEntity>() {
        @Override
        protected RenderableEntity newObject() {
            return new RenderableEntity();
        }
    };
    private final Comparator<RenderableEntity> ySortComparator = (o1, o2) -> {
        int yCompare = Float.compare(o2.y, o1.y);
        if (yCompare != 0) return yCompare;
        return Integer.compare(o1.type.layerIndex, o2.type.layerIndex);
    };

    private static final Color LIGHT_COLOR = new Color(1f, 0.8f, 0.6f, 1f);

    private void renderBlock(SpriteBatch batch, PlaceableBlock block) {
        double worldTimeInMinutes = worldData.getWorldTimeInMinutes();
        TextureRegion currentFrame = BlockTextureManager.getBlockFrame(block, (float) worldTimeInMinutes);
        if (currentFrame == null) return;

        float tileX = block.getPosition().x * TILE_SIZE;
        float tileY = block.getPosition().y * TILE_SIZE;

        float blockWidth = currentFrame.getRegionWidth();
        float blockHeight = currentFrame.getRegionHeight();
        float offsetX = (TILE_SIZE - blockWidth) / 2;

        // The batch color is already set to the world's ambient color.
        // We only need to adjust it if there's a local light source.
        Color finalColor = batch.getColor();
        Float lightLevel = getLightLevelAtTile(block.getPosition());
        if (lightLevel != null && lightLevel > 0) {
            // Use a temporary color object to avoid modifying the batch's main color
            Color tempColor = new Color(finalColor);
            tempColor.lerp(LIGHT_COLOR, lightLevel * 0.7f);
            finalColor = tempColor;
        }

        Color originalColor = batch.getColor().cpy();
        batch.setColor(finalColor);

        if (block.isFlipped()) {
            batch.draw(currentFrame,
                tileX + offsetX + blockWidth, tileY,
                -blockWidth, blockHeight);
        } else {
            batch.draw(currentFrame,
                tileX + offsetX, tileY,
                blockWidth, blockHeight);
        }

        batch.setColor(originalColor); // Restore for the next entity
    }

    // Add this new enum and inner class inside your World.java
    private enum RenderableType {
        PLAYER(2), OTHER_PLAYER(2), WILD_POKEMON(2),
        TREE_BASE(0), TREE_TOP(4),
        TALL_GRASS_BACKGROUND(1), TALL_GRASS_FOREGROUND(3),
        WORLD_OBJECT(1),

        BLOCK(1);
        final int layerIndex;

        RenderableType(int index) {
            this.layerIndex = index;
        }
    }

    private static class RenderableEntity implements Pool.Poolable {
        Object entity;
        float y;
        RenderableType type;

        public RenderableEntity init(Object entity, float y, RenderableType type) {
            this.entity = entity;
            this.y = y;
            this.type = type;
            return this;
        }

        @Override
        public void reset() {
            this.entity = null;
            this.y = 0;
            this.type = null;
        }
    }

    /**
     * Returns true if the given tile coordinates are within the world border.
     * This implementation assumes that the world is centered at (0,0).
     */
    public boolean isWithinWorldBounds(int tileX, int tileY) {
        int halfTiles = (HALF_WORLD_SIZE) / TILE_SIZE;
        return tileX >= -halfTiles && tileX < halfTiles && tileY >= -halfTiles && tileY < halfTiles;
    }

    public int getTileTypeAt(int tileX, int tileY) {
        int chunkX = Math.floorDiv(tileX, Chunk.CHUNK_SIZE);
        int chunkY = Math.floorDiv(tileY, Chunk.CHUNK_SIZE);
        Vector2 chunkPos = new Vector2(chunkX, chunkY);
        Chunk chunk = chunks.get(chunkPos);
        if (chunk != null) {
            int localX = Math.floorMod(tileX, Chunk.CHUNK_SIZE);
            int localY = Math.floorMod(tileY, Chunk.CHUNK_SIZE);
            return chunk.getTileType(localX, localY);
        }
        return -1;
    }

    public FootstepEffectManager getFootstepEffectManager() {
        return footstepEffectManager;
    }

    public ItemEntityManager getItemEntityManager() {
        return itemEntityManager;
    }

    public Float getLightLevelAtTile(Vector2 tilePos) {
        return lightLevelMap.get(tilePos);
    }

    public Color getCurrentWorldColor() {
        return currentWorldColor;
    }

    private void loadChunksFromWorldData() {
        if (GameContext.get().isMultiplayer()) {
            GameLogger.info("Multiplayer mode: skipping local chunk loading.");
            return;
        }

        String chunksDirPath = "worlds/singleplayer/" + this.name + "/chunks/";
        FileHandle chunksDir = Gdx.files.local(chunksDirPath);

        if (chunksDir.exists() && chunksDir.isDirectory()) {
            FileHandle[] chunkFiles = chunksDir.list(".json");
            GameLogger.info("Found " + chunkFiles.length + " chunk files to load for world '" + this.name + "'.");

            for (FileHandle chunkFile : chunkFiles) {
                try {
                    String[] nameParts = chunkFile.nameWithoutExtension().split("_");
                    int chunkX = Integer.parseInt(nameParts[1]);
                    int chunkY = Integer.parseInt(nameParts[2]);
                    Vector2 chunkPos = new Vector2(chunkX, chunkY);

                    // The loadChunkData method already reads the individual file
                    Chunk loadedChunk = loadChunkData(chunkPos);
                    if (loadedChunk != null) {
                        if (loadedChunk.getBlocks().size() > 0) {
                            GameLogger.info("loadChunksFromWorldData: Adding chunk " + chunkPos + " to chunks map with " + loadedChunk.getBlocks().size() + " blocks");
                        }
                        this.chunks.put(chunkPos, loadedChunk);

                        // Register world objects with objectManager
                        if (loadedChunk.getWorldObjects() != null && !loadedChunk.getWorldObjects().isEmpty()) {
                            objectManager.setObjectsForChunk(chunkPos, loadedChunk.getWorldObjects());
                            GameLogger.info("Registered " + loadedChunk.getWorldObjects().size() + " objects for chunk " + chunkPos);
                        }
                    }
                } catch (Exception e) {
                    GameLogger.error("Failed to parse chunk file name or load data for: " + chunkFile.name() + " - " + e.getMessage());
                }
            }
            GameLogger.info("Finished loading " + this.chunks.size() + " chunks from files.");
        } else {
            GameLogger.info("No 'chunks' directory found for world '" + this.name + "'. No chunks loaded from disk.");
        }

    }

    public WeatherSystem getWeatherSystem() {
        return weatherSystem;
    }

    private List<Vector2> generateSpiralChunkOrder(int centerX, int centerY, int radius) {
        List<Vector2> order = new ArrayList<>();
        int x = 0, y = 0, dx = 0, dy = -1;
        int maxSteps = (2 * radius + 1) * (2 * radius + 1);
        int steps = 0;

        while (steps < maxSteps) {
            if ((-radius <= x && x <= radius) && (-radius <= y && y <= radius)) {
                order.add(new Vector2(centerX + x, centerY + y));
                steps++;
            }

            if (x == y || (x < 0 && x == -y) || (x > 0 && x == 1 - y)) {
                int temp = dx;
                dx = -dy;
                dy = temp;
            }
            x += dx;
            y += dy;
        }

        return order;
    }


    private boolean isMultiplayerOperation() {
        return GameContext.get().isMultiplayer();
    }

    public void processChunkData(NetworkProtocol.ChunkData chunkData) {
        if (chunkData == null) {
            GameLogger.error("Received null chunk data from server.");
            return;
        }

        Vector2 chunkPos = new Vector2(chunkData.chunkX, chunkData.chunkY);
        GameLogger.info("processChunkData called for chunk " + chunkPos + " in singleplayer mode!");
        try {
            Biome primaryBiome = getBiomeManager().getBiome(chunkData.primaryBiomeType);
            if (primaryBiome == null) {
                GameLogger.error("Client using fallback PLAINS biome for null type: " + chunkData.primaryBiomeType);
                primaryBiome = getBiomeManager().getBiome(BiomeType.PLAINS);
            }

            Biome finalPrimaryBiome = primaryBiome;
            Chunk chunk = chunks.computeIfAbsent(chunkPos, k -> new Chunk(chunkData.chunkX, chunkData.chunkY, finalPrimaryBiome, chunkData.generationSeed));
            chunk.setBiome(primaryBiome);
            chunk.setTileData(chunkData.tileData);

            // Clear blocks
            chunk.getBlocks().clear();

            // Process blocks
            if (chunkData.blockData != null) {
                for (BlockSaveData.BlockData bd : chunkData.blockData) {
                    processBlockData(chunk, bd);
                }
            }

            // Build new objects list BEFORE clearing
            List<WorldObject> newObjects = new ArrayList<>();
            if (chunkData.worldObjects != null) {
                for (Map<String, Object> objData : chunkData.worldObjects) {
                    if (objData != null) {
                        WorldObject obj = new WorldObject();
                        obj.updateFromData(objData);
                        obj.ensureTexture(); // Ensure texture immediately
                        newObjects.add(obj);
                    }
                }
            }

            // Now update both in one go
            chunk.setWorldObjects(newObjects);
            getObjectManager().setObjectsForChunk(chunkPos, newObjects);

            BiomeTransitionResult transition = new BiomeTransitionResult(
                primaryBiome,
                chunkData.secondaryBiomeType != null ? getBiomeManager().getBiome(chunkData.secondaryBiomeType) : null,
                chunkData.biomeTransitionFactor
            );
            storeBiomeTransition(chunkPos, transition);

            new AutoTileSystem().applyShorelineAutotiling(chunk, this);
            chunk.setDirty(true);

            // Force immediate cache update
            invalidateRenderCaches();
            updateChunkLightMap(chunk, true);

            GameLogger.info("Client processed chunk " + chunkPos + " with " + newObjects.size() + " objects.");

        } catch (Exception e) {
            GameLogger.error("Client error processing chunk " + chunkPos + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void onChunkObjectsUpdated(Vector2 chunkPos) {
        markYSortDirty();
        // Drop caches so next render rebuilds immediately
        cachedSortedEntities.clear();
        lastVisibleObjects.clear();
        cachedSortedChunks = null;
        cachedChunkCount = -1;
    }


    public void storeBiomeTransition(Vector2 chunkPos, BiomeTransitionResult transition) {
        if (chunkPos != null && transition != null && transition.getPrimaryBiome() != null) {
            biomeTransitions.put(chunkPos, transition);
        } else {
            GameLogger.error("Attempted to store invalid biome transition for chunk: " + chunkPos);
        }
    }

    private void processBlockData(Chunk chunk, BlockSaveData.BlockData bd) {
        PlaceableBlock.BlockType blockType = PlaceableBlock.BlockType.fromId(bd.type);
        if (blockType != null) {
            Vector2 pos = new Vector2(bd.x, bd.y);
            PlaceableBlock block = new PlaceableBlock(blockType, pos, null, bd.isFlipped);
            if (blockType == PlaceableBlock.BlockType.CHEST && bd.chestData != null) {
                block.setChestData(bd.chestData.copy());
            }


            chunk.addBlock(block);
        }
    }


    public boolean areAllChunksLoaded() {
        int playerTileX = GameContext.get().getPlayer().getTileX();
        int playerTileY = GameContext.get().getPlayer().getTileY();
        int playerChunkX = Math.floorDiv(playerTileX, Chunk.CHUNK_SIZE);
        int playerChunkY = Math.floorDiv(playerTileY, Chunk.CHUNK_SIZE);

        int totalChunks = (INITIAL_LOAD_RADIUS * 2 + 1) * (INITIAL_LOAD_RADIUS * 2 + 1);
        int loadedChunks = 0;
        for (int dx = -INITIAL_LOAD_RADIUS; dx <= INITIAL_LOAD_RADIUS; dx++) {
            for (int dy = -INITIAL_LOAD_RADIUS; dy <= INITIAL_LOAD_RADIUS; dy++) {
                Vector2 expectedKey = new Vector2(playerChunkX + dx, playerChunkY + dy);
                if (chunks.containsKey(expectedKey)) {
                    loadedChunks++;
                }
            }
        }

        return loadedChunks == totalChunks;
    }

    public void clearChunks() {
        if (chunks != null) {
            chunks.clear();
            initialChunksRequested = false;
            GameLogger.info("Cleared all loaded chunks and reset initialChunksRequested flag.");
        }
    }

    public void loadChunksAroundPlayer() {
        Player player = GameContext.get().getPlayer();
        if (player == null) return;

        int playerTileX = player.getTileX();
        int playerTileY = player.getTileY();
        int playerChunkX = Math.floorDiv(playerTileX, CHUNK_SIZE);
        int playerChunkY = Math.floorDiv(playerTileY, CHUNK_SIZE);

        GameLogger.info("Loading chunks around player at tile (" + playerTileX + "," + playerTileY +
            "), chunk (" + playerChunkX + "," + playerChunkY + ")");

        List<Vector2> spiralOrder = generateOptimizedSpiralOrder(playerChunkX, playerChunkY, INITIAL_LOAD_RADIUS);

        Vector2 currentChunk = new Vector2(playerChunkX, playerChunkY);
        if (!chunks.containsKey(currentChunk)) {
            if (GameContext.get().isMultiplayer()) pendingChunkRequests.add(currentChunk);
            else loadChunkAsync(currentChunk);
        }

        for (Vector2 chunkKey : spiralOrder) {
            if (!chunks.containsKey(chunkKey)) {
                if (GameContext.get().isMultiplayer()) {
                    pendingChunkRequests.add(chunkKey); // NO sleep, request later per-frame
                } else {
                    loadChunkAsync(chunkKey);
                }
            }
        }
    }


    /**
     * Generates an optimized spiral loading order that prioritizes the closest chunks
     * but still follows a spiral pattern for aesthetic loading
     */
    private List<Vector2> generateOptimizedSpiralOrder(int centerX, int centerY, int radius) {
        List<Vector2> order = new ArrayList<>();
        int x = 0, y = 0, dx = 0, dy = -1;
        int maxSteps = (2 * radius + 1) * (2 * radius + 1);
        int steps = 0;

        while (steps < maxSteps) {
            if ((-radius <= x && x <= radius) && (-radius <= y && y <= radius)) {
                order.add(new Vector2(centerX + x, centerY + y));
                steps++;
            }
            if (x == y || (x < 0 && x == -y) || (x > 0 && x == 1 - y)) {
                int temp = dx;
                dx = -dy;
                dy = temp;
            }
            x += dx;
            y += dy;
        }
        order.removeIf(v -> v.x == centerX && v.y == centerY);
        order.sort((a, b) -> {
            float distA = Math.abs(a.x - centerX) + Math.abs(a.y - centerY);
            float distB = Math.abs(b.x - centerX) + Math.abs(b.y - centerY);
            return Float.compare(distA, distB);
        });

        return order;
    }

    /**
     * Forces loading of missing chunks in the area around the player
     * with intelligent prioritization and error recovery
     */
    public void forceLoadMissingChunks() {
        GameLogger.info("Forcing load of any missing chunks...");
        Player player = GameContext.get().getPlayer();
        if (player == null) return;

        int playerChunkX = Math.floorDiv(player.getTileX(), Chunk.CHUNK_SIZE);
        int playerChunkY = Math.floorDiv(player.getTileY(), Chunk.CHUNK_SIZE);
        Vector2 playerChunkPos = new Vector2(playerChunkX, playerChunkY);
        List<Vector2> missingChunks = new ArrayList<>();
        for (int dx = -INITIAL_LOAD_RADIUS; dx <= INITIAL_LOAD_RADIUS; dx++) {
            for (int dy = -INITIAL_LOAD_RADIUS; dy <= INITIAL_LOAD_RADIUS; dy++) {
                Vector2 chunkPos = new Vector2(playerChunkX + dx, playerChunkY + dy);
                if (!chunks.containsKey(chunkPos)) {
                    missingChunks.add(chunkPos);
                }
            }
        }
        missingChunks.sort((a, b) -> {
            float distA = Vector2.dst(a.x, a.y, playerChunkPos.x, playerChunkPos.y);
            float distB = Vector2.dst(b.x, b.y, playerChunkPos.x, playerChunkPos.y);
            return Float.compare(distA, distB);
        });
        if (!chunks.containsKey(playerChunkPos)) {
            Chunk playerChunk = loadOrGenerateChunk(playerChunkPos);
            if (playerChunk != null) {
                chunks.put(playerChunkPos, playerChunk);
                GameLogger.info("Force-loaded player's current chunk " + playerChunkPos);
            }
        }
        final int MAX_CHUNKS_PER_SYNC = 4;
        int loadedThisFrame = 0;
        Iterator<Vector2> iter = missingChunks.iterator();

        while (iter.hasNext() && loadedThisFrame < MAX_CHUNKS_PER_SYNC) {
            Vector2 chunkPos = iter.next();
            if (chunkPos.equals(playerChunkPos)) continue;

            try {
                Chunk chunk;
                if (GameContext.get().isMultiplayer()) {
                    GameContext.get().getGameClient().requestChunk(chunkPos);
                    GameLogger.info("Requested missing chunk " + chunkPos + " from server");
                } else {
                    chunk = loadOrGenerateChunk(chunkPos);
                    if (chunk != null) {
                        chunks.put(chunkPos, chunk);
                        loadedThisFrame++;
                        GameLogger.info("Force-loaded chunk " + chunkPos);
                    }
                }
            } catch (Exception e) {
                GameLogger.error("Failed to force-load chunk " + chunkPos + ": " + e.getMessage());
            }
        }
    }


    public WildPokemon getNearestInteractablePokemon(Player player) {
        float playerPixelX = player.getTileX() * TILE_SIZE + (Player.FRAME_WIDTH / 2f);
        float playerPixelY = player.getTileY() * TILE_SIZE + (Player.FRAME_HEIGHT / 2f);

        float checkX = playerPixelX;
        float checkY = playerPixelY;
        float interactionDistance = TILE_SIZE * 1.5f;
        switch (player.getDirection()) {
            case "up":
                checkY += interactionDistance;
                break;
            case "down":
                checkY -= interactionDistance;
                break;
            case "left":
                checkX -= interactionDistance;
                break;
            case "right":
                checkX += interactionDistance;
                break;
        }

        WildPokemon nearest = null;
        float shortestDistance = interactionDistance;

        Collection<WildPokemon> nearbyPokemon = pokemonSpawnManager.getPokemonInRange(checkX, checkY, interactionDistance);

        for (WildPokemon pokemon : nearbyPokemon) {
            float distance = Vector2.dst(checkX, checkY, pokemon.getX(), pokemon.getY());

            if (distance < shortestDistance) {
                shortestDistance = distance;
                nearest = pokemon;
            }
        }

        return nearest;
    }

    public BlockManager getBlockManager() {

        return blockManager;
    }
    public void save() {
        if (isMultiplayerOperation()) {
            return;
        }

        try {
            GameLogger.info("Saving world data for '" + name + "'...");

            // Save player data
            if (GameContext.get().getPlayer() != null) {
                PlayerData currentState = new PlayerData(GameContext.get().getPlayer().getUsername());
                currentState.updateFromPlayer(GameContext.get().getPlayer());
                worldData.savePlayerData(GameContext.get().getPlayer().getUsername(), currentState, false);
            }

            // Flush dirty chunks first
            flushDirtyChunks();

            // CRITICAL: Force save ALL chunks with blocks, even if not marked dirty
            // This ensures blocks are never lost
            for (Map.Entry<Vector2, Chunk> entry : chunks.entrySet()) {
                if (!entry.getValue().getBlocks().isEmpty() || entry.getValue().isDirty()) {
                    saveChunkData(entry.getKey(), entry.getValue());
                    entry.getValue().setDirty(false);
                }
            }

            worldData.setLastPlayed(System.currentTimeMillis());
            worldData.setDirty(true);
            WorldManager.getInstance().saveWorld(worldData);

            GameLogger.info("Successfully saved world: " + name + " with " + chunks.size() + " chunks");

        } catch (Exception e) {
            GameLogger.error("Failed to save world '" + name + "': " + e.getMessage());
            e.printStackTrace();
        }
    }
    public void saveChunkImmediate(Vector2 chunkPos) {
        if (GameContext.get().isMultiplayer()) return;

        Chunk chunk = chunks.get(chunkPos);
        if (chunk != null && chunk.isDirty()) {
            saveChunkData(chunkPos, chunk);
            chunk.setDirty(false);
            saveDebounce.put(chunkPos, System.currentTimeMillis());
            dirtyChunks.remove(chunkPos); // Remove from dirty set if it was there
            GameLogger.info("Immediately saved chunk " + chunkPos);
        }
    }

    private float dirtyChunkFlushTimer = 0f;


    public void dispose() {
        try {
            GameLogger.info("Disposing world: " + name);

            flushDirtyChunks();

            save();
            chunks.clear();
            biomeTransitions.clear();
            loadingChunks.clear();
            lastChunkAccess.clear();
            dirtyChunks.clear();
            if (waterEffects != null) {
                waterEffects.dispose();
            }
            if (chunkLoadExecutor != null) {
                chunkLoadExecutor.shutdown();
                try {
                    if (!chunkLoadExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        chunkLoadExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    chunkLoadExecutor.shutdownNow();
                }
            }

            isDisposed = true;
        } catch (Exception e) {
            GameLogger.error("Error disposing world: " + e.getMessage());
        }
    }

    public long getLastPlayed() {
        return lastPlayed;
    }

    public void setLastPlayed(long lastPlayed) {
        this.lastPlayed = lastPlayed;
    }

    public Player getPlayer() {
        return GameContext.get().getPlayer();
    }

    public void setPlayer(Player player) {
        GameContext.get().setPlayer(player);
        if (player != null) {
            this.currentPlayerData = new PlayerData(player.getUsername());
            this.currentPlayerData.updateFromPlayer(player);
        }
    }

    public Chunk getChunkAtTile(int tileX, int tileY) {
        int chunkX = Math.floorDiv(tileX, Chunk.CHUNK_SIZE);
        int chunkY = Math.floorDiv(tileY, Chunk.CHUNK_SIZE);
        return chunks.get(new Vector2(chunkX, chunkY));
    }

    private final Map<Vector2, Long> saveDebounce = new ConcurrentHashMap<>();
    private static final long CHUNK_SAVE_DEBOUNCE_MS = 1000L; // 1s throttle

    public void queueChunkSave(Vector2 chunkPos) {
        if (GameContext.get().isMultiplayer()) {
            GameLogger.info("queueChunkSave: Skipping " + chunkPos + " (multiplayer mode)");
            return; // server is source of truth
        }
        long now = System.currentTimeMillis();
        Long last = saveDebounce.get(chunkPos);
        if (last != null && now - last < CHUNK_SAVE_DEBOUNCE_MS) {
            GameLogger.info("queueChunkSave: Debouncing " + chunkPos + ", adding to dirtyChunks for later flush");
            dirtyChunks.add(new Vector2(chunkPos)); // will be flushed later
            return;
        }
        Chunk ch = chunks.get(chunkPos);
        if (ch != null) {
            GameLogger.info("queueChunkSave: Immediately saving chunk " + chunkPos);
            saveChunkData(chunkPos, ch);
            ch.setDirty(false);
            saveDebounce.put(chunkPos, now);
        } else {
            GameLogger.info("queueChunkSave: Chunk " + chunkPos + " not found in chunks map!");
        }
    }

    /**
     * Unloads any chunks that are far away and old, or if we exceed budget.
     */
    private void unloadDistantChunks(int playerChunkX, int playerChunkY) {
        if (chunks.isEmpty()) return;

        long now = System.currentTimeMillis();
        List<Vector2> candidates = new ArrayList<>(chunks.keySet());
        candidates.sort(Comparator.comparingDouble(cp -> Vector2.dst2(cp.x, cp.y, playerChunkX, playerChunkY)));
        List<Vector2> toUnload = new ArrayList<>();

        int maxRadius = MAX_CHUNK_LOAD_RADIUS;

        for (Vector2 chunkPos : candidates) {
            float dist2 = Vector2.dst2(chunkPos.x, chunkPos.y, playerChunkX, playerChunkY);
            Long lastAccessTime = lastChunkAccess.getOrDefault(chunkPos, 0L);
            long idleTime = now - lastAccessTime;

            boolean beyondDistance = dist2 > (maxRadius * maxRadius);
            boolean oldEnough = (idleTime >= UNLOAD_IDLE_THRESHOLD_MS);
            if (beyondDistance && oldEnough) {
                toUnload.add(chunkPos);
            }
        }
        int currentCount = chunks.size();
        if (currentCount > MAX_LOADED_CHUNKS) {
            candidates.sort((a, b) -> {
                long aTime = lastChunkAccess.getOrDefault(a, 0L);
                long bTime = lastChunkAccess.getOrDefault(b, 0L);
                return Long.compare(aTime, bTime);
            });
            for (Vector2 chunkPos : candidates) {
                if (currentCount <= MAX_LOADED_CHUNKS) break;
                float dist2 = Vector2.dst2(chunkPos.x, chunkPos.y, playerChunkX, playerChunkY);
                if (dist2 <= (maxRadius * maxRadius)) {
                    continue;
                }
                if (!toUnload.contains(chunkPos)) {
                    toUnload.add(chunkPos);
                    currentCount--;
                }
            }
        }
        for (Vector2 chunkPos : toUnload) {
            Chunk chunk = chunks.get(chunkPos);
            if (chunk != null) {
                if (!GameContext.get().isMultiplayer() && chunk.isDirty()) {
                    saveChunkData(chunkPos, chunk);
                }
            }
            chunks.remove(chunkPos);
            biomeTransitions.remove(chunkPos);
            loadingChunks.remove(chunkPos);
            lastChunkAccess.remove(chunkPos);
            onChunksMutated();
        }

        if (!toUnload.isEmpty()) {
            GameLogger.info("Unloaded " + toUnload.size() + " distant/old chunks. " + "Remaining loaded: " + chunks.size());
        }
    }

    public void saveChunkData(Vector2 chunkPos, Chunk chunk) {
        if (GameContext.get().getGameClient() != null && GameContext.get().isMultiplayer()) {
            return;
        }

        try {
            ChunkData data = new ChunkData();
            data.x = (int) chunkPos.x;
            data.y = (int) chunkPos.y;
            data.biomeType = chunk.getBiome().getType();
            data.tileData = chunk.getTileData();
            data.blocks = chunk.getBlockDataForSave();
            GameLogger.info("saveChunkData: Saving chunk " + chunkPos + " from world '" + name + "' with " + data.blocks.size() + " blocks to disk");

            // Save complete object state
            List<WorldObject> objects = objectManager.getObjectsForChunk(chunkPos);
            if (objects != null && !objects.isEmpty()) {
                data.objects = new ArrayList<>();
                for (WorldObject obj : objects) {
                    WorldObjectData objData = new WorldObjectData();
                    objData.x = obj.getPixelX();
                    objData.y = obj.getPixelY();
                    objData.type = obj.getType();
                    data.objects.add(objData);
                }
            } else {
                data.objects = new ArrayList<>(); // Empty list, not null
            }

            Json json = new Json();
            json.setOutputType(JsonWriter.OutputType.json);
            registerCustomSerializers(json);

            String baseDir = "worlds/singleplayer/" + name + "/chunks/";
            String filename = String.format("chunk_%d_%d.json", (int) chunkPos.x, (int) chunkPos.y);
            FileHandle chunkFile = Gdx.files.local(baseDir + filename);

            chunkFile.parent().mkdirs();
            String jsonString = json.prettyPrint(data);
            chunkFile.writeString(jsonString, false);
            GameLogger.info("saveChunkData: Wrote chunk to file: " + baseDir + filename);

            // Update world data
            chunks.put(chunkPos, chunk);
            worldData.setDirty(true);

            GameLogger.info("Saved chunk " + chunkPos + " with " +
                (objects != null ? objects.size() : 0) + " objects");

        } catch (Exception e) {
            GameLogger.error("Failed to save chunk at " + chunkPos + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    private Chunk loadChunkData(Vector2 chunkPos) {
        if (GameContext.get().isMultiplayer()) {
            GameLogger.info("loadChunkData: skipping " + chunkPos + " (multiplayer mode)");
            return null;
        }

        try {
            String baseDir = "worlds/singleplayer/" + name + "/chunks/";
            String filename = String.format("chunk_%d_%d.json", (int) chunkPos.x, (int) chunkPos.y);
            FileHandle chunkFile = Gdx.files.local(baseDir + filename);

            if (!chunkFile.exists()) {
                GameLogger.info("loadChunkData: file not found for " + chunkPos + " at " + baseDir + filename);
                return null;
            }
            GameLogger.info("loadChunkData: loading " + chunkPos + " from world '" + name + "', file: " + filename);

            String jsonContent = chunkFile.readString();
            Json json = new Json();
            registerCustomSerializers(json);
            ChunkData chunkData = json.fromJson(ChunkData.class, jsonContent);

            BiomeType biomeType = chunkData.biomeType;
            Biome biome = GameContext.get().getBiomeManager().getBiome(biomeType);
            Chunk chunk = new Chunk((int) chunkPos.x, (int) chunkPos.y, biome, worldSeed);
            chunk.setTileData(chunkData.tileData);

            // Load blocks with validation
            int loadedBlockCount = 0;
            if (chunkData.blocks != null) {
                for (BlockSaveData.BlockData blockDataItem : chunkData.blocks) {
                    try {
                        PlaceableBlock.BlockType blockType = PlaceableBlock.BlockType.fromId(blockDataItem.type);
                        if (blockType == null) {
                            GameLogger.error("Unknown block type: " + blockDataItem.type);
                            continue;
                        }

                        Vector2 pos = new Vector2(blockDataItem.x, blockDataItem.y);
                        PlaceableBlock block = new PlaceableBlock(blockType, pos, null, blockDataItem.isFlipped);

                        if (blockType == PlaceableBlock.BlockType.CHEST) {
                            block.setChestOpen(blockDataItem.isChestOpen);
                            if (blockDataItem.chestData != null) {
                                block.setChestData(blockDataItem.chestData);
                            }
                        }

                        chunk.addBlock(block);
                        loadedBlockCount++;
                    } catch (Exception e) {
                        GameLogger.error("Failed to load block at " + blockDataItem.x + "," + blockDataItem.y + ": " + e.getMessage());
                    }
                }
            }

            int loadedObjectCount = 0;
            if (chunkData.objects != null && !chunkData.objects.isEmpty()) {
                List<WorldObject> worldObjects = new ArrayList<>();
                for (WorldObjectData objData : chunkData.objects) {
                    try {
                        // Use the objectManager to correctly create and texture the object
                        WorldObject obj = objectManager.createObject(objData.type, objData.x, objData.y);
                        worldObjects.add(obj);
                        loadedObjectCount++;
                    } catch (Exception e) {
                        GameLogger.error("Failed to load object " + objData.type + " at " + objData.x + "," + objData.y + ": " + e.getMessage());
                    }
                }
                chunk.setWorldObjects(worldObjects);
            }

            // IMPORTANT: Set dirty to false since we just loaded from disk
            chunk.setDirty(false);

            GameLogger.info("Loaded chunk " + chunkPos + " with " + loadedBlockCount + " blocks and " + loadedObjectCount + " objects");
            return chunk;

        } catch (Exception e) {
            GameLogger.error("Error loading chunk at " + chunkPos + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private void registerCustomSerializers(Json json) {
        json.setSerializer(UUID.class, new Json.Serializer<>() {
            @Override
            public void write(Json json, UUID object, Class knownType) {
                json.writeValue(object.toString());
            }

            @Override
            public UUID read(Json json, JsonValue jsonData, Class type) {
                return UUID.fromString(jsonData.asString());
            }
        });

        json.setSerializer(Vector2.class, new Json.Serializer<>() {
            @Override
            public void write(Json json, Vector2 object, Class knownType) {
                json.writeObjectStart();
                json.writeValue("x", object.x);
                json.writeValue("y", object.y);
                json.writeObjectEnd();
            }

            @Override
            public Vector2 read(Json json, JsonValue jsonData, Class type) {
                float x = jsonData.getFloat("x");
                float y = jsonData.getFloat("y");
                return new Vector2(x, y);
            }
        });


        json.setSerializer(ItemData.class, new Json.Serializer<>() {
            @Override
            public void write(Json json, ItemData object, Class knownType) {
                json.writeObjectStart();
                json.writeValue("itemId", object.getItemId());
                json.writeValue("count", object.getCount());
                json.writeValue("uuid", object.getUuid().toString());
                json.writeValue("durability", object.getDurability());
                json.writeValue("maxDurability", object.getMaxDurability());
                json.writeObjectEnd();
            }

            @Override
            public ItemData read(Json json, JsonValue jsonData, Class type) {
                ItemData item = new ItemData();
                if (jsonData.has("itemId")) {
                    item.setItemId(jsonData.getString("itemId"));
                } else if (jsonData.has("id")) {
                    item.setItemId(jsonData.getString("id"));
                } else {
                    GameLogger.error("ItemData missing 'itemId' or 'id' field during deserialization. Skipping item.");
                    return null; // Skip this item or set a default value
                }
                item.setCount(jsonData.getInt("count", 1));
                String uuidStr = jsonData.getString("uuid", null);
                if (uuidStr != null) {
                    item.setUuid(UUID.fromString(uuidStr));
                } else {
                    item.setUuid(UUID.randomUUID());
                }
                item.setDurability(jsonData.getInt("durability", -1));
                item.setMaxDurability(jsonData.getInt("maxDurability", -1));
                return item;
            }
        });
        json.setSerializer(ChunkData.class, new Json.Serializer<>() {
            @Override
            public void write(Json json, ChunkData object, Class knownType) {
                object.write(json);
            }

            @Override
            public ChunkData read(Json json, JsonValue jsonData, Class type) {
                ChunkData data = new ChunkData();
                data.read(jsonData, json);
                return data;
            }
        });

    }

    public Map<Vector2, Chunk> getChunks() {
        return chunks;
    }

    public void updatePlayerData() {
        if (GameContext.get().getPlayer() != null && currentPlayerData != null) {
            currentPlayerData.updateFromPlayer(GameContext.get().getPlayer());
        }
    }

    public PlayerData getPlayerData() {
        updatePlayerData();
        return currentPlayerData;
    }

    public String getName() {
        return name;
    }

    public WorldData getWorldData() {
        return worldData;
    }

    public void setWorldData(WorldData data) {
        if (data == null) {
            throw new IllegalArgumentException("WorldData cannot be null");
        }
        // CORE FIX: Only update the world data object and essential properties.
        // DO NOT clear and reload the chunks map, as it's already correctly loaded by the constructor.
        this.worldData = data;
        this.name = data.getName();
        if (data.getConfig() != null) {
            this.worldSeed = data.getConfig().getSeed();
        } else {
            // Fallback if config is somehow null
            this.worldSeed = System.currentTimeMillis();
            data.setConfig(new WorldData.WorldConfig(this.worldSeed));
        }
        GameContext.get().setBiomeManager(new BiomeManager(this.worldSeed));

        GameLogger.info("Set WorldData for world: " + name + " Time: " + data.getWorldTimeInMinutes() + " Played: " + data.getPlayedTime());
    }

    public BiomeManager getBiomeManager() {
        return GameContext.get().getBiomeManager();
    }


    public Biome getBiomeAt(int tileX, int tileY) {
        int chunkX = Math.floorDiv(tileX, CHUNK_SIZE);
        int chunkY = Math.floorDiv(tileY, CHUNK_SIZE);
        Vector2 chunkPos = new Vector2(chunkX, chunkY);

        // In multiplayer, always prioritize the stored biome transition from server
        BiomeTransitionResult storedTransition = biomeTransitions.get(chunkPos);
        if (storedTransition != null && storedTransition.getPrimaryBiome() != null) {
            return storedTransition.getPrimaryBiome();
        }

        // Check if chunk exists and has biome set
        Chunk chunk = chunks.get(chunkPos);
        if (chunk != null && chunk.getBiome() != null) {
            if (!biomeTransitions.containsKey(chunkPos)) {
                storeBiomeTransition(chunkPos, new BiomeTransitionResult(chunk.getBiome(), null, 1f));
            }
            return chunk.getBiome();
        }

        // Only calculate biome locally in singleplayer mode
        // In multiplayer, wait for server to send biome data
        if (!GameContext.get().isMultiplayer()) {
            BiomeManager bm = GameContext.get().getBiomeManager();
            if (bm != null) {
                BiomeTransitionResult calculatedResult = bm.getBiomeAtTile(tileX, tileY);
                if (calculatedResult != null && calculatedResult.getPrimaryBiome() != null) {
                    storeBiomeTransition(chunkPos, calculatedResult);
                    return calculatedResult.getPrimaryBiome();
                }
            }
        }

        // Fallback to PLAINS if no biome data available
        return GameContext.get().getBiomeManager() != null
            ? GameContext.get().getBiomeManager().getBiome(BiomeType.PLAINS)
            : null;
    }


    private void initializeChunksAroundOrigin() {
        validateChunkState();
        GameLogger.info("Starting chunk initialization around player");
        Player player = GameContext.get().getPlayer();
        if (player == null) return;
        int playerChunkX = Math.floorDiv(player.getTileX(), Chunk.CHUNK_SIZE);
        int playerChunkY = Math.floorDiv(player.getTileY(), Chunk.CHUNK_SIZE);
        for (int radius = 0; radius <= INITIAL_LOAD_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) == radius) {
                        Vector2 chunkPos = new Vector2(playerChunkX + dx, playerChunkY + dy);
                        if (GameContext.get().isMultiplayer()) {
                            GameContext.get().getGameClient().requestChunk(chunkPos);
                        } else {
                            loadChunkAsync(chunkPos);
                        }

                    }
                }
            }
        }
    }

    public Chunk loadOrGenerateChunk(Vector2 chunkPos) {
        if (GameContext.get().isMultiplayer()) {
            if (!chunks.containsKey(chunkPos)) {
                GameContext.get().getGameClient().requestChunk(chunkPos);
            }
            return chunks.get(chunkPos);
        }

        // CRITICAL FIX: Check if chunk already exists in memory first
        // This prevents overwriting unsaved changes when reloading from disk
        Chunk existing = chunks.get(chunkPos);
        if (isChunkValid(existing)) {
            GameLogger.info("loadOrGenerateChunk: Chunk " + chunkPos + " already in memory with " + existing.getBlocks().size() + " blocks, skipping disk load");
            return existing;
        }

        // Try loading from disk only if not in memory
        Chunk loaded = loadChunkData(chunkPos);
        if (chunkPos.x == -1 && chunkPos.y == 1) {
            GameLogger.info("loadOrGenerateChunk called for (-1,1): loaded chunk is " + (loaded == null ? "NULL" : "valid with " + loaded.getBlocks().size() + " blocks") + ", isValid? " + isChunkValid(loaded));
        }
        if (isChunkValid(loaded)) {
            // Chunk was loaded successfully - DO NOT REGENERATE!
            List<WorldObject> loadedObjects = loaded.getWorldObjects();
            if (loadedObjects != null) {
                for (WorldObject obj : loadedObjects) {
                    obj.ensureTexture();
                }
                objectManager.setObjectsForChunk(chunkPos, loadedObjects);
            }
            GameLogger.info("Loaded existing chunk " + chunkPos + " with " +
                (loadedObjects != null ? loadedObjects.size() : 0) + " objects and " +
                loaded.getBlocks().size() + " blocks");
            return loaded; // RETURN HERE - DON'T REGENERATE!
        }

        // ONLY generate if loading failed (file doesn't exist)
        if (chunkPos.x == -1 && chunkPos.y == 1) {
            GameLogger.info("loadOrGenerateChunk: (-1,1) failed validation, generating new chunk");
        }
        GameLogger.info("No saved chunk found at " + chunkPos + ", generating new chunk");
        Chunk generated = UnifiedWorldGenerator.generateChunk(
            (int) chunkPos.x,
            (int) chunkPos.y,
            this.worldSeed,
            GameContext.get().getBiomeManager()
        );

        if (generated != null) {
            List<WorldObject> generatedObjects = generated.getWorldObjects();
            if (generatedObjects != null) {
                for (WorldObject obj : generatedObjects) {
                    obj.ensureTexture();
                }
                objectManager.setObjectsForChunk(chunkPos, generatedObjects);
            }

            // Save the newly generated chunk
            saveChunkData(chunkPos, generated);
            GameLogger.info("Generated and saved new chunk " + chunkPos);
        }

        return generated;
    }

    public void requestInitialChunks() {
        if (GameContext.get().isMultiplayer()) {
            if (initialChunksRequested) return;
            initialChunksRequested = true;
        }
        initialChunkRequestTime = System.currentTimeMillis();

        int playerTileX = GameContext.get().getPlayer().getTileX();
        int playerTileY = GameContext.get().getPlayer().getTileY();
        int playerChunkX = Math.floorDiv(playerTileX, CHUNK_SIZE);
        int playerChunkY = Math.floorDiv(playerTileY, CHUNK_SIZE);

        List<Vector2> chunkOrder = generateSpiralChunkOrder(playerChunkX, playerChunkY, INITIAL_LOAD_RADIUS);
        for (Vector2 chunkPos : chunkOrder) {
            if (!chunks.containsKey(chunkPos)) {
                if (GameContext.get().isMultiplayer()) {
                    pendingChunkRequests.add(chunkPos); // paced in update()
                } else {
                    loadChunkAsync(chunkPos);
                }
            }
        }
    }


    public void markAllChunksLightDirty() {
        for (Chunk chunk : chunks.values()) {
            if (chunk != null) {
                chunk.setLightMapDirty(true);
            }
        }
    }

    /**
     * Robust ambient color update that drives seamless per-chunk lighting.
     */
    public void updateWorldColor() {
        if (worldData == null) return;

        // Compute target based on time of day
        float hourOfDay = DayNightCycle.getHourOfDay(worldData.getWorldTimeInMinutes());
        targetWorldColor.set(DayNightCycle.getWorldColor(hourOfDay));
        DayNightCycle.TimePeriod newTimePeriod = DayNightCycle.getTimePeriod(hourOfDay);

        // First frame: snap to target to avoid flashes
        if (!worldColorInitialized) {
            currentWorldColor.set(targetWorldColor);
            lastTimePeriod = newTimePeriod;
            worldColorInitialized = true;
            lastAmbientColor.set(currentWorldColor);
            ambientChangedThisFrame = true;
            markAllChunksLightDirty();
        } else {
            // Smoothly lerp towards target
            currentWorldColor.lerp(targetWorldColor, Gdx.graphics.getDeltaTime() * COLOR_TRANSITION_SPEED);

            // Did our ambient color change enough this frame to warrant refreshing visible chunks?
            ambientChangedThisFrame = Math.abs(currentWorldColor.r - lastAmbientColor.r) > AMBIENT_EPSILON
                || Math.abs(currentWorldColor.g - lastAmbientColor.g) > AMBIENT_EPSILON
                || Math.abs(currentWorldColor.b - lastAmbientColor.b) > AMBIENT_EPSILON
                || Math.abs(currentWorldColor.a - lastAmbientColor.a) > AMBIENT_EPSILON;

            if (ambientChangedThisFrame) {
                lastAmbientColor.set(currentWorldColor);
            }

            // If the coarse time period changed (e.g., Day→Dusk), force all chunks to recompute
            if (newTimePeriod != lastTimePeriod) {
                lastTimePeriod = newTimePeriod;
                markAllChunksLightDirty();
            }
        }

        // Update visible chunks’ light maps.
        OrthographicCamera camera = GameContext.get().getGameScreen().getCamera();
        if (camera != null) {
            Rectangle viewBounds = new Rectangle(
                camera.position.x - camera.viewportWidth * camera.zoom / 2,
                camera.position.y - camera.viewportHeight * camera.zoom / 2,
                camera.viewportWidth * camera.zoom,
                camera.viewportHeight * camera.zoom
            );
            Rectangle expandedBounds = new Rectangle(
                viewBounds.x - TILE_SIZE * 2, viewBounds.y - TILE_SIZE * 2,
                viewBounds.width + TILE_SIZE * 4, viewBounds.height + TILE_SIZE * 4
            );

            for (Map.Entry<Vector2, Chunk> entry : chunks.entrySet()) {
                if (!isChunkVisible(entry.getKey(), expandedBounds)) continue;
                // Key change: if ambient changed this frame, force-refresh this visible chunk.
                updateChunkLightMap(entry.getValue(), ambientChangedThisFrame);
            }
        }
    }


    private void validateChunkState() {
        if (chunks == null) {
            chunks = new ConcurrentHashMap<>();
        }
        if (loadingChunks == null) {
            loadingChunks = new ConcurrentHashMap<>();
        }
        if (initialChunkLoadQueue == null) {
            initialChunkLoadQueue = new LinkedList<>();
        }
    }

    private static final long AUTOTILE_BUDGET_NANOS = 1_200_000L; // ~1.2 ms

    private void processAutotileQueue(int fallbackMaxPerFrame) {
        AutoTileSystem auto = new AutoTileSystem();
        long start = System.nanoTime();
        int processed = 0;

        // Compute current (expanded) view once for visibility tests
        OrthographicCamera cam = GameContext.get().getGameScreen() != null
            ? GameContext.get().getGameScreen().getCamera() : null;
        Rectangle view = null, expanded = null;
        if (cam != null) {
            view = new Rectangle(
                cam.position.x - cam.viewportWidth * cam.zoom / 2f,
                cam.position.y - cam.viewportHeight * cam.zoom / 2f,
                cam.viewportWidth * cam.zoom,
                cam.viewportHeight * cam.zoom
            );
            expanded = getExpandedViewBounds(view);
        }

        while (!autotileQueue.isEmpty()) {
            // Soft time budget so we never hitch
            if (System.nanoTime() - start > AUTOTILE_BUDGET_NANOS && processed >= 1) break;
            // Also keep your old numeric cap as a fallback
            if (processed >= fallbackMaxPerFrame) break;

            Vector2 pos = autotileQueue.pollFirst();
            if (pos == null) break;
            Chunk ch = chunks.get(pos);
            if (ch == null) continue;

            try {
                auto.applyShorelineAutotiling(ch, this);
                ch.setDirty(true);

                // Rebuild light *only if* it’s on/near screen; otherwise delay
                if (expanded == null || isChunkVisible(pos, expanded)) {
                    updateChunkLightMap(ch, false); // respect dirty/last-update guard
                } else {
                    ch.setLightMapDirty(true);
                }
            } catch (Exception e) {
                GameLogger.error("Autotile queue failed for " + pos + ": " + e.getMessage());
            }
            processed++;
        }
    }

    public void loadChunkAsync(final Vector2 chunkPos) {
        if (isDisposed || (chunkLoadExecutor != null && chunkLoadExecutor.isShutdown())) {
            return;
        }
        if (loadingChunks.containsKey(chunkPos)) return;
        if (chunkPos.x == -1 && chunkPos.y == 1) {
            GameLogger.info("loadChunkAsync for (-1,1): chunks.containsKey? " + chunks.containsKey(chunkPos) + ", chunks.size=" + chunks.size());
        }
        if (chunks.containsKey(chunkPos)) {
            GameLogger.info("loadChunkAsync: chunk " + chunkPos + " already loaded, skipping");
            return;
        }
        GameLogger.info("loadChunkAsync: loading chunk " + chunkPos);

        loadingChunks.put(chunkPos, CompletableFuture.completedFuture(null));

        CompletableFuture.supplyAsync(() -> {
                try {
                    // Try loading first
                    Chunk chunk = loadChunkData(chunkPos);

                    if (chunkPos.x == -1 && chunkPos.y == 1) {
                        GameLogger.info("loadChunkAsync for (-1,1): chunk from loadChunkData is " + (chunk == null ? "NULL" : "valid with " + chunk.getBlocks().size() + " blocks") + ", isValid? " + isChunkValid(chunk));
                    }

                    // Only generate if loading failed
                    if (!isChunkValid(chunk)) {
                        if (chunkPos.x == -1 && chunkPos.y == 1) {
                            GameLogger.info("loadChunkAsync: (-1,1) failed validation, generating...");
                        }
                        chunk = UnifiedWorldGenerator.generateChunk(
                            (int) chunkPos.x,
                            (int) chunkPos.y,
                            worldSeed,
                            getBiomeManager()
                        );

                        if (chunk != null && chunk.getBiome() == null) {
                            Biome biome = findDominantBiomeInChunk((int) chunkPos.x, (int) chunkPos.y, getBiomeManager());
                            chunk.setBiome(biome);
                        }

                        // Only spawn objects for newly generated chunks
                        if (chunk != null && (chunk.getWorldObjects() == null || chunk.getWorldObjects().isEmpty())) {
                            List<WorldObject> objects = EnhancedWorldObjectSpawner.spawnWorldObjects(
                                chunk,
                                chunk.getTileData(),
                                worldSeed
                            );
                            chunk.setWorldObjects(objects);
                        }
                    }

                    GameLogger.info("Async loaded chunk " + chunkPos +
                        " (loaded from disk: " + (loadChunkData(chunkPos) != null) + ")");
                    return chunk;
                } catch (Exception e) {
                    GameLogger.error("Failed to load chunk " + chunkPos + ": " + e.getMessage());
                    e.printStackTrace();
                    return null;
                }
            }, chunkLoadExecutor)
            .thenAccept(chunk -> {
                Gdx.app.postRunnable(() -> {
                    if (chunk != null && !chunks.containsKey(chunkPos)) {
                        integrationQueue.add(new AbstractMap.SimpleEntry<>(chunkPos, chunk));
                    }
                    loadingChunks.remove(chunkPos);
                });
            });
    }

    public Chunk getChunkAtPosition(float x, float y) {
        final int chunkPixelSize = Chunk.CHUNK_SIZE * TILE_SIZE;
        int chunkX = MathUtils.floor(x / (float) chunkPixelSize);
        int chunkY = MathUtils.floor(y / (float) chunkPixelSize);

        Vector2 pos = new Vector2(chunkX, chunkY);
        Chunk chunk = chunks.get(pos);
        if (!isChunkValid(chunk)) {
            chunk = loadOrGenerateChunk(pos);
            if (chunk != null) chunks.put(pos, chunk);
        }
        return chunk;
    }


    private final ArrayDeque<Vector2> pendingChunkRequests = new ArrayDeque<>();
    private static final int MAX_REQUESTS_PER_FRAME = 8;

    // World.java
    public void update(float delta, Vector2 playerPosition, float viewportWidth, float viewportHeight, GameScreen gameScreen) {
        if (isDisposed) return;
// Updated integration queue processing in update() method
        int integratedThisFrame = 0;
        final long INTEGRATE_BUDGET_NANOS = 2_000_000L; // ~2 ms
        long integrateStart = System.nanoTime();

        while (!integrationQueue.isEmpty()) {
            if (System.nanoTime() - integrateStart > INTEGRATE_BUDGET_NANOS && integratedThisFrame >= 1) break;

            Map.Entry<Vector2, Chunk> entry = integrationQueue.poll();
            if (entry == null) break;

            Vector2 cpos = entry.getKey();
            Chunk chunk = entry.getValue();
            if (cpos.x == -1 && cpos.y == 1) {
                GameLogger.info("Integration queue: Processing chunk (-1,1), chunk null? " + (chunk == null) + ", already in map? " + chunks.containsKey(cpos));
            }
            if (chunk == null || chunks.containsKey(cpos)) continue;

            // (unchanged) validate + set biome + store transition ...
            validateChunkTileData(chunk);
            if (chunk.getBiome() == null) {
                Biome fb = findDominantBiomeInChunk((int) cpos.x, (int) cpos.y, getBiomeManager());
                if (fb == null) fb = getBiomeManager().getBiome(BiomeType.PLAINS);
                chunk.setBiome(fb);
            }
            BiomeTransitionResult transition = biomeTransitions.get(cpos);
            if (transition == null || transition.getPrimaryBiome() == null) {
                biomeTransitions.put(cpos, new BiomeTransitionResult(chunk.getBiome(), null, 1.0f));
            }

            // Add to world; no autotile or lighting right now
            // CRITICAL FIX: Don't overwrite chunks that were already loaded from disk
            if (chunks.containsKey(cpos)) {
                Chunk existingChunk = chunks.get(cpos);
                GameLogger.info("WARNING: Skipping chunk " + cpos + " - already exists in chunks map! Existing blocks: " + existingChunk.getBlocks().size() + ", New blocks: " + chunk.getBlocks().size());
                continue;
            }
            GameLogger.info("Integrating chunk " + cpos + " with " + chunk.getBlocks().size() + " blocks into chunks map");
            chunks.put(cpos, chunk);

            // Ensure light map exists but don’t compute yet
            if (chunk.getLightMap() == null) {
                Color[][] lm = new Color[Chunk.CHUNK_SIZE][Chunk.CHUNK_SIZE];
                for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
                    for (int y = 0; y < Chunk.CHUNK_SIZE; y++) lm[x][y] = new Color(1, 1, 1, 1);
                }
                chunk.setLightMap(lm);
            }
            chunk.setLightMapDirty(true);

            // Register objects (unchanged)
            List<WorldObject> objs = chunk.getWorldObjects();
            if (objs != null) {
                for (WorldObject obj : objs) if (obj != null) obj.ensureTexture();
                objectManager.setObjectsForChunk(cpos, objs);
            }

            // Queue autotile with priority for this chunk; neighbors follow (see Fix #1)
            autotileQueue.addFirst(cpos);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    Vector2 np = new Vector2(cpos.x + dx, cpos.y + dy);
                    if (chunks.containsKey(np)) autotileQueue.addLast(np);
                }
            }

            lastChunkAccess.put(cpos, System.currentTimeMillis());
            onChunksMutated();
            invalidateRenderCaches();
            GameLogger.info("Integrated chunk " + cpos + " successfully");
            integratedThisFrame++;
        }
        dirtyChunkFlushTimer += delta;
        if (dirtyChunkFlushTimer >= 2.0f) {  // Changed from 5.0f to 2.0f
            flushDirtyChunks();
            dirtyChunkFlushTimer = 0f;
        }
        flushPendingChunkRequests();
        processAutotileQueue(2);

        // --- 2) Keep state sane & update item systems ---
        validateChunkState();
        if (itemEntityManager != null) itemEntityManager.update(delta);

        if (objectManager != null) {
            boolean objectsChanged = objectManager.update(chunks);
            if (objectsChanged) onChunkObjectsUpdated(null);
        }
        // --- 3) Make sure the player’s current chunk exists (singleplayer safety net) ---
        if (!GameContext.get().isMultiplayer() && !isPlayerChunkLoaded()) {
            if (initialChunkRequestTime == 0) {
                requestInitialChunks();
            }
            // If it takes too long, synchronously load the player chunk to avoid soft-lock
            if (System.currentTimeMillis() - initialChunkRequestTime > 5000) {
                int playerTileX = GameContext.get().getPlayer().getTileX();
                int playerTileY = GameContext.get().getPlayer().getTileY();
                int playerChunkX = Math.floorDiv(playerTileX, Chunk.CHUNK_SIZE);
                int playerChunkY = Math.floorDiv(playerTileY, Chunk.CHUNK_SIZE);
                Vector2 currentChunk = new Vector2(playerChunkX, playerChunkY);
                Chunk chunk = loadOrGenerateChunk(currentChunk);
                if (chunk != null) {
                    chunks.put(currentChunk, chunk);
                    autotileChunkAndNeighbors(currentChunk);
                    updateChunkLightMap(chunk, true);
                    lastChunkAccess.put(currentChunk, System.currentTimeMillis());
                }
            }
        }

        // --- 4) Advance world time (drives day/night, weather, etc.) ---
        if (worldData != null) {
            worldData.updateTime(delta);
        }

        // --- 5) Ambient color & per-chunk lighting refresh (visible chunks only) ---
        // Smoothly lerps world tint and refreshes visible chunks when ambient changes.
        updateWorldColor();

        // --- 6) Periodic point-light levels (night only, visible chunks only) ---
        lightLevelUpdateTimer += delta;
        if (lightLevelUpdateTimer >= 2.0f) {
            updateLightLevels();
            lightLevelUpdateTimer = 0f;
        }

        // --- 7) Weather & audio ---
        updateWeather(delta, playerPosition, gameScreen);

        // --- 8) Footstep FX ---
        if (footstepEffectManager != null) footstepEffectManager.update(delta);

        // --- 8.5) Mark Y-sort dirty every frame to ensure proper layering ---
        // This ensures entities are always sorted correctly as player/objects move
        ySortDirty = true;

        // --- 9) Chunk streaming (ONE pass per frame, no sleeps) ---
        // Only start managing when initial loads are done/allowed.
        manageChunksTimer += delta;
        if (initialChunksComplete && manageChunksTimer >= 0.2f) {
            // Use the player’s current chunk (tile coords -> chunk coords)
            int currentChunkX = Math.floorDiv(GameContext.get().getPlayer().getTileX(), CHUNK_SIZE);
            int currentChunkY = Math.floorDiv(GameContext.get().getPlayer().getTileY(), CHUNK_SIZE);
            manageChunks(currentChunkX, currentChunkY); // internally capped (MAX_CHUNKS_PER_FRAME)
            manageChunksTimer = 0f;
        }
        // --- 10) Game systems (music, spawns, objects, interactions) ---
        updateGameSystems(delta, playerPosition);
    }

    public void forceSaveAllBlocks() {
        if (GameContext.get().isMultiplayer()) return;

        GameLogger.info("Force saving all blocks...");

        // First flush any pending
        flushDirtyChunks();

        // Then save all chunks with blocks
        for (Map.Entry<Vector2, Chunk> entry : chunks.entrySet()) {
            Chunk chunk = entry.getValue();
            if (!chunk.getBlocks().isEmpty()) {
                saveChunkData(entry.getKey(), chunk);
                chunk.setDirty(false);
            }
        }

        GameLogger.info("Force save complete");
    }

    private void flushDirtyChunks() {
        if (GameContext.get().isMultiplayer()) return;

        for (Vector2 chunkPos : dirtyChunks) {
            Chunk chunk = chunks.get(chunkPos);
            if (chunk != null && chunk.isDirty()) {
                saveChunkData(chunkPos, chunk);
                chunk.setDirty(false);
                saveDebounce.put(chunkPos, System.currentTimeMillis());
            }
        }
        dirtyChunks.clear();
    }

    private Biome findDominantBiomeInChunk(int chunkX, int chunkY, BiomeManager biomeManager) {
        // Sample the center of the chunk
        float centerWorldX = (chunkX * CHUNK_SIZE + (float) CHUNK_SIZE / 2) * TILE_SIZE;
        float centerWorldY = (chunkY * CHUNK_SIZE + (float) CHUNK_SIZE / 2) * TILE_SIZE;

        BiomeTransitionResult result = biomeManager.getBiomeAt(centerWorldX, centerWorldY);
        if (result != null && result.getPrimaryBiome() != null) {
            return result.getPrimaryBiome();
        }

        // Fallback to PLAINS if something goes wrong
        return biomeManager.getBiome(BiomeType.PLAINS);
    }

    private void validateChunkTileData(Chunk chunk) {
        int[][] tiles = chunk.getTileData();
        boolean corrected = false;

        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
                int tileType = tiles[x][y];
                if (tileType < 0) {
                    GameLogger.error("Invalid tile type " + tileType + " at local (" + x + "," + y +
                        ") in chunk (" + chunk.getChunkX() + "," + chunk.getChunkY() + ")");

                    if (chunk.getBiome() != null &&
                        chunk.getBiome().getAllowedTileTypes() != null &&
                        !chunk.getBiome().getAllowedTileTypes().isEmpty()) {
                        tiles[x][y] = chunk.getBiome().getAllowedTileTypes().get(0); // SAFE fallback
                    } else {
                        tiles[x][y] = TileType.GRASS;
                    }
                    corrected = true;
                }
            }
        }
        if (corrected) {
            chunk.setDirty(true);
            GameLogger.info("Corrected invalid tile data in chunk (" +
                chunk.getChunkX() + "," + chunk.getChunkY() + ")");
        }
    }

    private final List<RenderableEntity> cachedSortedEntities = new ArrayList<>();

    public void updateChunkLightMap(Chunk chunk) {
        updateChunkLightMap(chunk, false);
    }

    /**
     * Recomputes the per-tile light map. If forceAmbientRefresh is true,
     * we bypass the "recently updated" early exit so visible chunks follow
     * the ambient lerp seamlessly.
     */
    public void updateChunkLightMap(Chunk chunk, boolean forceAmbientRefresh) {
        if (chunk == null) return;

        // Short-circuit if recently updated and nothing is forcing a refresh
        if (!forceAmbientRefresh
            && !chunk.isLightMapDirty()
            && System.currentTimeMillis() - chunk.getLastLightUpdate() < 250) { // was 5000
            return;
        }

        Color[][] lightMap = chunk.getLightMap();
        if (lightMap == null) {
            lightMap = new Color[Chunk.CHUNK_SIZE][Chunk.CHUNK_SIZE];
            chunk.setLightMap(lightMap);
        }

        Color baseColor = getCurrentWorldColor();
        boolean hasLightSources = false;

        // Quick check for local light sources
        for (PlaceableBlock block : chunk.getBlocks().values()) {
            if ("furnace".equalsIgnoreCase(block.getId())) {
                hasLightSources = true;
                break;
            }
        }

        float hour = DayNightCycle.getHourOfDay(worldData.getWorldTimeInMinutes());
        boolean isNight = DayNightCycle.getTimePeriod(hour) == DayNightCycle.TimePeriod.NIGHT;

        // Fast path: no local lights OR not night → uniform ambient per tile
        if (!hasLightSources || !isNight) {
            for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
                for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
                    if (lightMap[x][y] == null) lightMap[x][y] = new Color();
                    lightMap[x][y].set(baseColor);
                }
            }
        } else {
            // Start with ambient
            for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
                for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
                    if (lightMap[x][y] == null) lightMap[x][y] = new Color();
                    lightMap[x][y].set(baseColor);
                }
            }
            // Blend in local warm lights
            for (PlaceableBlock block : chunk.getBlocks().values()) {
                if (!"furnace".equalsIgnoreCase(block.getId())) continue;

                Vector2 pos = block.getPosition();
                int centerX = Math.floorMod((int) pos.x, Chunk.CHUNK_SIZE);
                int centerY = Math.floorMod((int) pos.y, Chunk.CHUNK_SIZE);
                int radius = 7;
                Color torchColor = new Color(1f, 0.9f, 0.7f, 1f);

                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dy = -radius; dy <= radius; dy++) {
                        int lx = centerX + dx;
                        int ly = centerY + dy;
                        if (lx < 0 || lx >= Chunk.CHUNK_SIZE || ly < 0 || ly >= Chunk.CHUNK_SIZE) continue;

                        float dist = (float) Math.sqrt(dx * dx + dy * dy);
                        if (dist > radius) continue;

                        float intensity = 1.0f - (dist / radius);
                        // lerp ambient towards warm light
                        lightMap[lx][ly].lerp(torchColor, intensity * 0.8f);
                    }
                }
            }
        }

        chunk.setLightMapDirty(false);
        chunk.setLastLightUpdate(System.currentTimeMillis());
    }// World.java

    public void invalidateRenderCaches() {
        markYSortDirty();
        cachedSortedEntities.clear();
        lastVisibleObjects.clear();
        cachedSortedChunks = null;
        cachedChunkCount = -1;
    }


    public void forceLightAllChunks() {
        if (worldData == null) {
            return;
        }
        // Ensure the world's ambient color state is set correctly first.
        float hourOfDay = DayNightCycle.getHourOfDay(worldData.getWorldTimeInMinutes());
        currentWorldColor.set(DayNightCycle.getWorldColor(hourOfDay));
        targetWorldColor.set(currentWorldColor); // Synchronize target and current
        worldColorInitialized = true; // Mark as initialized to prevent transitions

        // Now, iterate and apply the correct lighting to EVERY loaded chunk.
        for (Chunk chunk : chunks.values()) {
            if (chunk != null) {
                updateChunkLightMap(chunk);
            }
        }
        GameLogger.info("Forced a one-time lighting update on all " + chunks.size() + " loaded chunks.");
    }


    public boolean isPlayerChunkLoaded() {
        int playerTileX = GameContext.get().getPlayer().getTileX();
        int playerTileY = GameContext.get().getPlayer().getTileY();
        int playerChunkX = Math.floorDiv(playerTileX, Chunk.CHUNK_SIZE);
        int playerChunkY = Math.floorDiv(playerTileY, Chunk.CHUNK_SIZE);
        Vector2 currentChunk = new Vector2(playerChunkX, playerChunkY);
        return chunks.containsKey(currentChunk);
    }

    public long getInitialChunkRequestTime() {
        return initialChunkRequestTime;
    }


    public void updateGameSystems(float delta, Vector2 playerPosition) {
        BiomeTransitionResult currentBiomeTransition = GameContext.get().getBiomeManager().getBiomeAt(playerPosition.x * TILE_SIZE, playerPosition.y * TILE_SIZE);

        Biome currentBiome = currentBiomeTransition.getPrimaryBiome();

        if (AudioManager.getInstance() != null) {
            AudioManager.getInstance().updateBiomeMusic(currentBiome.getType());
            AudioManager.getInstance().update(delta);
        }
        pokemonSpawnManager.update(delta, playerPosition);
        checkPlayerInteractions(playerPosition);
    }// --- Modified manageChunks method in World.java ---

    private void manageChunks(int playerChunkX, int playerChunkY) {
        // Increased load radius to prevent objects from popping in during exploration
        // 7 chunks = ~224 tiles = ~7168 pixels of view distance (plenty of buffer)
        int loadRadius = 7;
        PriorityQueue<Vector2> chunkQueue = new PriorityQueue<>(Comparator.comparingDouble(
            cp -> Vector2.dst2(cp.x, cp.y, playerChunkX, playerChunkY)
        ));

        for (int dx = -loadRadius; dx <= loadRadius; dx++) {
            for (int dy = -loadRadius; dy <= loadRadius; dy++) {
                chunkQueue.add(new Vector2(playerChunkX + dx, playerChunkY + dy));
            }
        }
        // Increased to load chunks faster and reduce pop-in
        final int MAX_CHUNKS_PER_FRAME = 12;
        int loadedThisFrame = 0;
        long now = System.currentTimeMillis();

        while (!chunkQueue.isEmpty() && loadedThisFrame < MAX_CHUNKS_PER_FRAME) {
            Vector2 chunkPos = chunkQueue.poll();
            Chunk existing = chunks.get(chunkPos);
            if (!isChunkValid(existing)) {
                if (GameContext.get().isMultiplayer()) {
                    GameContext.get().getGameClient().requestChunk(chunkPos);
                } else {
                    if (!loadingChunks.containsKey(chunkPos)) {
                        loadChunkAsync(chunkPos);
                    }
                }
                loadedThisFrame++;
            }
            lastChunkAccess.put(chunkPos, now);
        }
        unloadDistantChunks(playerChunkX, playerChunkY);
    }

    private void renderLoadingOverlay(SpriteBatch batch) {

    }

    public PokemonSpawnManager getPokemonSpawnManager() {
        return pokemonSpawnManager;
    }

    private boolean isTeleporting = false;
    private boolean isReadyForRender = false;
    private final Set<Positionable> activeEntities = new HashSet<>();
    private final List<Positionable> ySortedRenderList = new ArrayList<>();

    public void handleTeleport(Player player) {
        GameLogger.info("Handling teleport for player " + player.getUsername());
        isTeleporting = true;
        isReadyForRender = false;

        // Clear all local data
        chunks.clear();
        activeEntities.clear();
        ySortedRenderList.clear();

        if (GameContext.get().isMultiplayer()) {
            GameContext.get().getGameClient().getOtherPlayers().clear();
        }

        // >>> NEW: drop all caches
        cachedSortedEntities.clear();
        lastVisibleObjects.clear();
        cachedSortedChunks = null;
        cachedChunkCount = 0;
        ySortDirty = true;

        // Add the player back
        activeEntities.add(player);
        markYSortDirty();

        requestChunksAroundPlayer();
        isTeleporting = false;
    }


    private void requestChunksAroundPlayer() {
        if (GameContext.get().isMultiplayer()) {
            // In multiplayer, the GameClient is responsible for queuing chunk requests
            // This call will now trigger the client to load chunks around the player's NEW position
            GameContext.get().getGameClient().requestChunksAroundPlayer();
        }
    }

    public void render(SpriteBatch batch, Rectangle viewBounds, Player player) {

        if (chunks.isEmpty()) {
            renderLoadingOverlay(batch);
            return;
        }
        Color originalColor = batch.getColor().cpy();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        try {   // Set color BEFORE any rendering occurs
            if (currentWorldColor != null) {
                batch.setColor(currentWorldColor.r, currentWorldColor.g, currentWorldColor.b, 1f);
            }

            Rectangle expandedBounds = getExpandedViewBounds(viewBounds);
            List<Map.Entry<Vector2, Chunk>> sortedChunks = getSortedChunks();


            sortedChunks.sort(Comparator.comparingDouble(entry -> entry.getKey().y));
            renderTerrainLayer(batch, sortedChunks, expandedBounds);

            renderLowObjects(batch, expandedBounds);

            itemEntityManager.render(batch);
            footstepEffectManager.render(batch);
            renderMidLayer(batch, player, expandedBounds);
            renderHighObjects(batch, expandedBounds);

            if (waterEffects != null && waterEffects.isInitialized()) {
                waterEffects.update(Gdx.graphics.getDeltaTime());
                waterEffects.render(batch, player, this);
            }
            if (weatherAudioSystem != null) {
                weatherAudioSystem.renderLightningEffect(batch, viewBounds.width, viewBounds.height);
            }

        } finally {
            batch.setColor(originalColor);
        }
    }

    /**
     * [REPLACEMENT] A restructured render method to apply lighting correctly.
     */
    public void render(SpriteBatch batch, Rectangle viewBounds, Player player, GameScreen gameScreen) {
        if (isDisposed) return;
        if (chunks.isEmpty()) {
            renderLoadingOverlay(batch);
            return;
        }

        Color originalColor = batch.getColor().cpy();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        try {
            Rectangle expandedBounds = getExpandedViewBounds(viewBounds);
            List<Map.Entry<Vector2, Chunk>> sortedChunks = getSortedChunks();
            biomeRenderer.updateAnimations();

            // Pass 1: Render terrain using its detailed, per-tile light maps.
            // Do not apply a global tint before this.
            renderTerrainLayer(batch, sortedChunks, expandedBounds);

            // Pass 2: Apply the general world ambient color as a tint for everything else.
            // This affects players, blocks, items, etc., that don't have per-tile lighting.
            batch.setColor(currentWorldColor);

            itemEntityManager.render(batch);
            footstepEffectManager.render(batch);

            // Pass 3: Render all y-sorted entities, which will inherit the ambient tint.
            renderYSortedEntities(batch, player, expandedBounds, sortedChunks);

            // Pass 4: Render weather and other overlays without tint.
            batch.setColor(Color.WHITE);
            if (weatherSystem != null && gameScreen != null) {
                weatherSystem.render(batch, gameScreen.getCamera());
            }
            if (weatherAudioSystem != null) {
                weatherAudioSystem.renderLightningEffect(batch, viewBounds.width, viewBounds.height);
            }
            renderWorldBorderWithBatch(batch, gameScreen.getCamera());

        } finally {
            batch.setColor(originalColor);
        }
    }


    private boolean isTallGrassType(WorldObject.ObjectType type) {
        return type == WorldObject.ObjectType.TALL_GRASS ||
            type == WorldObject.ObjectType.FOREST_TALL_GRASS ||
            type == WorldObject.ObjectType.RAIN_FOREST_TALL_GRASS ||
            type == WorldObject.ObjectType.SNOW_TALL_GRASS ||
            type == WorldObject.ObjectType.TALL_GRASS_2 ||
            type == WorldObject.ObjectType.TALL_GRASS_3 ||
            type == WorldObject.ObjectType.RUINS_TALL_GRASS ||
            type == WorldObject.ObjectType.DESERT_TALL_GRASS ||
            type == WorldObject.ObjectType.HAUNTED_TALL_GRASS;
    }

    /**
     * [REPLACEMENT] Optimized method to only update light levels for visible chunks.
     */
    public void updateLightLevels() {
        lightLevelMap.clear();
        float hour = DayNightCycle.getHourOfDay(worldData.getWorldTimeInMinutes());
        if (DayNightCycle.getTimePeriod(hour) != DayNightCycle.TimePeriod.NIGHT) return;

        // Get the camera's current view to determine which chunks are visible
        OrthographicCamera camera = GameContext.get().getGameScreen().getCamera();
        Rectangle viewBounds = new Rectangle(
            camera.position.x - camera.viewportWidth * camera.zoom / 2,
            camera.position.y - camera.viewportHeight * camera.zoom / 2,
            camera.viewportWidth * camera.zoom,
            camera.viewportHeight * camera.zoom
        );
        Rectangle expandedBounds = getExpandedViewBounds(viewBounds);

        // Iterate ONLY through the currently loaded chunks
        for (Map.Entry<Vector2, Chunk> entry : chunks.entrySet()) {
            Vector2 chunkPos = entry.getKey();

            // --- THE CORE OPTIMIZATION ---
            // If the chunk is not visible on screen, skip its lighting calculation entirely.
            if (!isChunkVisible(chunkPos, expandedBounds)) {
                continue;
            }

            Chunk chunk = entry.getValue();
            for (PlaceableBlock block : chunk.getBlocks().values()) {
                if ("furnace".equalsIgnoreCase(block.getId())) { // Check for furnace ID
                    Vector2 pos = block.getPosition();
                    int radius = 7;
                    float maxLevel = 1.0f;
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dy = -radius; dy <= radius; dy++) {
                            Vector2 tilePos = new Vector2(pos.x + dx, pos.y + dy);
                            float dist = pos.dst(tilePos);
                            if (dist <= radius) {
                                float level = maxLevel * (1 - (dist / radius));
                                lightLevelMap.merge(tilePos, level, Math::max);
                            }
                        }
                    }
                }
            }
        }
    }


    private void renderOtherPlayers(SpriteBatch batch, Rectangle viewBounds) {
        if (GameContext.get().getGameClient() == null || GameContext.get().getGameClient().isSinglePlayer()) {
            return;
        }

        Map<String, OtherPlayer> otherPlayers = GameContext.get().getGameClient().getOtherPlayers();

        synchronized (otherPlayers) {
            List<OtherPlayer> sortedPlayers = new ArrayList<>(otherPlayers.values());
            sortedPlayers.sort((p1, p2) -> Float.compare(p2.getY(), p1.getY()));

            for (OtherPlayer otherPlayer : sortedPlayers) {
                if (otherPlayer == null) continue;

                float playerX = otherPlayer.getX();
                float playerY = otherPlayer.getY();
                if (viewBounds.contains(playerX, playerY)) {
                    otherPlayer.render(batch);
                    waterEffectsRendererForOthers.render(batch, otherPlayer, GameContext.get().getWorld());
                }
            }
        }
    }


    private List<Map.Entry<Vector2, Chunk>> getSortedChunks() {
        if (cachedSortedChunks != null && lastSortedModCount == chunkModCount) {
            return cachedSortedChunks;
        }
        cachedSortedChunks = new ArrayList<>(chunks.entrySet());
        cachedSortedChunks.sort((a, b) -> Float.compare(b.getKey().y, a.getKey().y));
        cachedChunkCount = chunks.size();
        lastSortedModCount = chunkModCount;
        return cachedSortedChunks;
    }

    private void renderTerrainLayer(SpriteBatch batch, java.util.List<java.util.Map.Entry<Vector2, Chunk>> sortedChunks, Rectangle expandedBounds) {
        for (java.util.Map.Entry<Vector2, Chunk> entry : sortedChunks) {
            Vector2 chunkPos = entry.getKey();
            if (isChunkVisible(chunkPos, expandedBounds)) {
                Chunk chunk = entry.getValue();
                biomeRenderer.renderChunk(batch, chunk, this);
            }
        }
    }

    private void flushPendingChunkRequests() {
        if (!GameContext.get().isMultiplayer()) return;
        GameClient client = GameContext.get().getGameClient();
        if (client == null) return;

        int sent = 0;
        while (sent < MAX_REQUESTS_PER_FRAME && !pendingChunkRequests.isEmpty()) {
            Vector2 pos = pendingChunkRequests.poll();
            if (pos == null) break;
            if (!chunks.containsKey(pos) && !loadingChunks.containsKey(pos)) {
                client.requestChunk(pos);
                sent++;
            }
        }
    }

    private void autotileChunkAndNeighbors(Vector2 chunkPos) {
        AutoTileSystem autoTileSystem = new AutoTileSystem();
        // Iterate over the 3x3 grid centered on the newly added chunk
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                Vector2 pos = new Vector2(chunkPos.x + dx, chunkPos.y + dy);
                Chunk chunkToTile = chunks.get(pos);
                if (chunkToTile != null) {
                    // Apply shoreline autotiling which depends on neighbor data
                    autoTileSystem.applyShorelineAutotiling(chunkToTile, this);
                }
            }
        }
    }


    private Rectangle getExpandedViewBounds(Rectangle viewBounds) {
        float buffer = TILE_SIZE * 8; // Was TILE_SIZE * 2
        return new Rectangle(viewBounds.x - buffer, viewBounds.y - buffer, viewBounds.width + (buffer * 2), viewBounds.height + (buffer * 2));
    }


    private void renderLowObjects(SpriteBatch batch, Rectangle expandedBounds) {
        for (Map.Entry<Vector2, Chunk> entry : chunks.entrySet()) {
            Vector2 pos = entry.getKey();
            if (isChunkVisible(pos, expandedBounds)) {
                List<WorldObject> chunkObjects = objectManager.getObjectsForChunk(pos);
                for (WorldObject obj : chunkObjects) {
                    if (obj.getType().renderLayer == WorldObject.ObjectType.RenderLayer.BELOW_PLAYER) {
                        objectManager.renderObject(batch, obj, this);
                    }
                }
            }
        }
    }

    private void renderHighObjects(SpriteBatch batch, Rectangle expandedBounds) {
        List<WorldObject> treeTopsToRender = new ArrayList<>();
        for (Map.Entry<Vector2, Chunk> entry : chunks.entrySet()) {
            if (isChunkVisible(entry.getKey(), expandedBounds)) {
                List<WorldObject> chunkObjects = objectManager.getObjectsForChunk(entry.getKey());
                for (WorldObject obj : chunkObjects) {
                    if (isTreeObject(obj)) {
                        treeTopsToRender.add(obj);
                    }
                }
            }
        }
        treeTopsToRender.sort(Comparator.comparingDouble(WorldObject::getPixelY));
        for (WorldObject tree : treeTopsToRender) {
            objectManager.renderTreeTop(batch, tree, this);
        }
    }

    private boolean isTreeObject(WorldObject obj) {
        return obj.getType() == WorldObject.ObjectType.TREE_0 || obj.getType() == WorldObject.ObjectType.TREE_1 || obj.getType() == WorldObject.ObjectType.SNOW_TREE || obj.getType() == WorldObject.ObjectType.HAUNTED_TREE || obj.getType() == WorldObject.ObjectType.RAIN_TREE || obj.getType() == WorldObject.ObjectType.RUINS_TREE || obj.getType() == WorldObject.ObjectType.APRICORN_TREE || obj.getType() == WorldObject.ObjectType.BEACH_TREE || obj.getType() == WorldObject.ObjectType.CHERRY_TREE;
    }


    private void renderMidLayer(SpriteBatch batch, Player player, Rectangle expandedBounds) {
        List<ObjectWithYPosition> behindPlayerQueue = new ArrayList<>();
        List<ObjectWithYPosition> frontPlayerQueue = new ArrayList<>();

        Color originalColor = batch.getColor().cpy();
        for (Map.Entry<Vector2, Chunk> entry : chunks.entrySet()) {
            if (isChunkVisible(entry.getKey(), expandedBounds)) {
                List<WorldObject> chunkObjects = objectManager.getObjectsForChunk(entry.getKey());
                for (WorldObject obj : chunkObjects) {
                    if (isTreeObject(obj)) {
                        behindPlayerQueue.add(new ObjectWithYPosition(obj.getPixelY(), obj, RenderType.TREE_BASE));
                        frontPlayerQueue.add(new ObjectWithYPosition(obj.getPixelY() + World.TILE_SIZE * 2, obj, RenderType.TREE_TOP));
                    } else if (obj.getType().renderLayer == WorldObject.ObjectType.RenderLayer.BELOW_PLAYER) {
                        behindPlayerQueue.add(new ObjectWithYPosition(obj.getPixelY(), obj, RenderType.REGULAR_OBJECT));
                    }
                }
            }
        }
        behindPlayerQueue.sort(Comparator.comparingDouble(a -> a.y));
        frontPlayerQueue.sort(Comparator.comparingDouble(a -> a.y));
        for (ObjectWithYPosition item : behindPlayerQueue) {
            switch (item.renderType) {
                case TREE_BASE:
                    objectManager.renderTreeBase(batch, (WorldObject) item.object, this);
                    break;
                case REGULAR_OBJECT:
                    objectManager.renderObject(batch, (WorldObject) item.object, this);
                    break;
            }
        }

        renderWildPokemon(batch);
        if (GameContext.get().getGameClient() != null && GameContext.get().isMultiplayer()) {
            renderOtherPlayers(GameContext.get().getBatch(), expandedBounds);
        }
        List<WorldObject> tallGrass = getTallGrassObjects();
        for (WorldObject grass : tallGrass) {
            boolean sameTile = (grass.getTileX() == player.getTileX()
                && grass.getTileY() == player.getTileY());
            if (sameTile) {
                renderTallGrassUpperHalf(batch, Collections.singletonList(grass));
            } else {
                renderTallGrassUpperHalf(batch, Collections.singletonList(grass));
                renderTallGrassLowerHalf(batch, Collections.singletonList(grass));
            }
        }
        player.render(batch);
        for (WorldObject grass : tallGrass) {
            boolean sameTile = (grass.getTileX() == player.getTileX()
                && grass.getTileY() == player.getTileY());
            if (sameTile) {
                renderTallGrassLowerHalf(batch, Collections.singletonList(grass));
            }
        }

        batch.setColor(originalColor);
    }

    List<WorldObject> getTallGrassObjects() {
        List<WorldObject> tallGrass = new ArrayList<>();
        tallGrass.addAll(objectManager.getObjectsForChunkType(WorldObject.ObjectType.TALL_GRASS));
        tallGrass.addAll(objectManager.getObjectsForChunkType(WorldObject.ObjectType.TALL_GRASS_2));
        tallGrass.addAll(objectManager.getObjectsForChunkType(WorldObject.ObjectType.TALL_GRASS_3));
        tallGrass.addAll(objectManager.getObjectsForChunkType(WorldObject.ObjectType.FOREST_TALL_GRASS));
        tallGrass.addAll(objectManager.getObjectsForChunkType(WorldObject.ObjectType.RAIN_FOREST_TALL_GRASS));
        tallGrass.addAll(objectManager.getObjectsForChunkType(WorldObject.ObjectType.HAUNTED_TALL_GRASS));
        tallGrass.addAll(objectManager.getObjectsForChunkType(WorldObject.ObjectType.DESERT_TALL_GRASS));
        tallGrass.addAll(objectManager.getObjectsForChunkType(WorldObject.ObjectType.SNOW_TALL_GRASS));
        tallGrass.addAll(objectManager.getObjectsForChunkType(WorldObject.ObjectType.RUINS_TALL_GRASS));
        return tallGrass;
    }


    private Map<BiomeRenderer.Direction, Biome> getNeighboringBiomes(Vector2 chunkPos) {
        Map<BiomeRenderer.Direction, Biome> neighbors = new EnumMap<>(BiomeRenderer.Direction.class);

        for (BiomeRenderer.Direction dir : BiomeRenderer.Direction.values()) {
            Vector2 neighborPos = new Vector2(chunkPos.x + (dir == BiomeRenderer.Direction.EAST ? 1 : dir == BiomeRenderer.Direction.WEST ? -1 : 0), chunkPos.y + (dir == BiomeRenderer.Direction.NORTH ? 1 : dir == BiomeRenderer.Direction.SOUTH ? -1 : 0));

            Chunk neighborChunk = chunks.get(neighborPos);
            if (neighborChunk != null) {
                neighbors.put(dir, neighborChunk.getBiome());
            }
        }

        return neighbors;
    }

    private boolean isChunkVisible(Vector2 chunkPos, Rectangle viewBounds) {
        float chunkWorldX = chunkPos.x * CHUNK_SIZE * TILE_SIZE;
        float chunkWorldY = chunkPos.y * CHUNK_SIZE * TILE_SIZE;
        float chunkSize = CHUNK_SIZE * TILE_SIZE;
        tempChunkRect.set(chunkWorldX, chunkWorldY, chunkSize, chunkSize);
        return viewBounds.overlaps(tempChunkRect);
    }

    public WorldObject.WorldObjectManager getObjectManager() {
        return objectManager;
    }

    public boolean isPokemonAt(int worldX, int worldY) {
        for (WildPokemon pokemon : pokemonSpawnManager.getAllWildPokemon()) {
            if (pokemon.getTileX() == worldX && pokemon.getTileY() == worldY) {
                return true;
            }
        }
        return false;
    }

    public void loadChunksAroundPositionSynchronously(Vector2 tilePosition, int radius) {
        int chunkX = Math.floorDiv((int) tilePosition.x, CHUNK_SIZE);
        int chunkY = Math.floorDiv((int) tilePosition.y, CHUNK_SIZE);

        GameLogger.info("Loading chunks around tile position " + tilePosition + " with radius " + radius);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                Vector2 chunkPos = new Vector2(chunkX + dx, chunkY + dy);
                if (!chunks.containsKey(chunkPos)) {
                    try {
                        if (GameContext.get().isMultiplayer()) {
                            GameContext.get().getGameClient().requestChunk(chunkPos);
                        } else {
                            loadChunkAsync(chunkPos);
                        }

                    } catch (Exception e) {
                        GameLogger.error("Error loading chunk at " + chunkPos + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    public boolean isPassable(int tileX, int tileY) {
        if (!isWithinWorldBounds(tileX, tileY) || !isPositionLoaded(tileX, tileY)) {
            return false;
        }

        try {
            Player player = GameContext.get().getPlayer();
            if (player == null) return false;

            int currentElevation = getElevationAt(player.getTileX(), player.getTileY());
            int targetElevation = getElevationAt(tileX, tileY);
            int targetTileType = getTileTypeAt(tileX, tileY);

            // 1. Check for inherently impassable tiles (water, solid mountain walls, etc.)
            if (!TileType.isPassableTile(targetTileType)) {
                handleCollision(player.getDirection());
                return false;
            }

            // 2. Check for collision with placed blocks
            if (blockManager != null && blockManager.hasCollisionAt(tileX, tileY)) {
                handleCollision(player.getDirection());
                return false;
            }

            // 3. Mountain Layering and Stair Logic
            if (TileType.isMountainTile(targetTileType) || TileType.isMountainTile(getTileTypeAt(player.getTileX(), player.getTileY()))) {
                if (targetTileType == TileType.STAIRS) {
                    // Allow moving onto stairs if it's a one-level change
                    return Math.abs(currentElevation - targetElevation) == 1;
                } else if (getTileTypeAt(player.getTileX(), player.getTileY()) == TileType.STAIRS) {
                    // Allow moving off stairs if it's a one-level change
                    return Math.abs(currentElevation - targetElevation) == 1;
                } else {
                    // For all other mountain tiles, must be on the same level to move
                    if (targetElevation != currentElevation) {
                        return false;
                    }
                }
            }

            // 4. Generic object collision check (trees, rocks, etc.)
            Rectangle movementBounds = new Rectangle(
                tileX * TILE_SIZE,
                tileY * TILE_SIZE,
                TILE_SIZE * 0.5f,
                TILE_SIZE * 0.5f);
            if (checkObjectCollision(movementBounds, player.getDirection())) {
                return false;
            }

            // 5. Pokémon collision check
            return !checkPokemonCollision(tileX, tileY, player.getDirection());

        } catch (Exception e) {
            GameLogger.error("Error in isPassable: " + e.getMessage());
            return false;
        }
    }

    public int getElevationAt(int tileX, int tileY) {
        if (!isPositionLoaded(tileX, tileY)) {
            return -1; // Indicate not loaded or out of bounds
        }
        Chunk chunk = getChunkAtPosition(tileX * TILE_SIZE, tileY * TILE_SIZE);
        if (chunk != null) {
            int localX = Math.floorMod(tileX, CHUNK_SIZE);
            int localY = Math.floorMod(tileY, CHUNK_SIZE);
            return chunk.getElevation(localX, localY);
        }
        return 0; // Default to 0 if chunk is somehow null
    }

    private void renderWorldBorderWithBatch(SpriteBatch batch, OrthographicCamera camera) {
        TextureRegion borderPixel = new TextureRegion(TextureManager.getWhitePixel());
        Color originalColor = batch.getColor().cpy();
        batch.setColor(Color.RED);
        float left = -HALF_WORLD_SIZE;
        float bottom = -HALF_WORLD_SIZE;
        float worldSize = WORLD_SIZE;
        float thickness = 2f;
        batch.draw(borderPixel, left, bottom + worldSize - thickness, worldSize, thickness);
        batch.draw(borderPixel, left, bottom, worldSize, thickness);
        batch.draw(borderPixel, left, bottom, thickness, worldSize);
        batch.draw(borderPixel, left + worldSize - thickness, bottom, thickness, worldSize);
        batch.setColor(originalColor);
    }

    private void renderTallGrassLowerHalf(SpriteBatch batch, List<WorldObject> tallGrassObjects) {
        for (WorldObject obj : tallGrassObjects) {
            TextureRegion region = obj.getTexture();
            if (region == null) continue;

            int regionWidth = region.getRegionWidth();
            int regionHeight = region.getRegionHeight();

            // Create a new TextureRegion for the bottom half.
            // The source Y is relative to the original region's top-left corner.
            TextureRegion bottomHalf = new TextureRegion(region, 0, regionHeight / 2, regionWidth, regionHeight / 2);

            float destX = obj.getPixelX();
            float destY = obj.getPixelY(); // Draw at the bottom of the tile.

            batch.draw(bottomHalf, destX, destY, World.TILE_SIZE, World.TILE_SIZE / 2f);
        }
    }

    private void renderTallGrassUpperHalf(SpriteBatch batch, List<WorldObject> tallGrassObjects) {
        for (WorldObject obj : tallGrassObjects) {
            TextureRegion region = obj.getTexture();
            if (region == null) continue;

            int regionWidth = region.getRegionWidth();
            int regionHeight = region.getRegionHeight();

            // Create a new TextureRegion for the top half.
            TextureRegion topHalf = new TextureRegion(region, 0, 0, regionWidth, regionHeight / 2);

            float destX = obj.getPixelX();
            float destY = obj.getPixelY() + World.TILE_SIZE / 2f; // Draw on the top half of the tile.

            batch.draw(topHalf, destX, destY, World.TILE_SIZE, World.TILE_SIZE / 2f);
        }
    }

    private final Set<WorldObject> lastVisibleObjects = new HashSet<>();

    public void markYSortDirty() {
        ySortDirty = true;
    }

    private void renderEntity(SpriteBatch batch, RenderableEntity item) {
        switch (item.type) {
            case PLAYER:
                ((Player) item.entity).render(batch);
                if (waterEffects != null) waterEffects.render(batch, (Player) item.entity, this);
                break;
            case OTHER_PLAYER:
                ((OtherPlayer) item.entity).render(batch);
                if (waterEffectsRendererForOthers != null)
                    waterEffectsRendererForOthers.render(batch, (OtherPlayer) item.entity, this);
                break;
            case WILD_POKEMON:
                ((WildPokemon) item.entity).render(batch);
                if (waterEffectsRendererForOthers != null)
                    waterEffectsRendererForOthers.render(batch, (WildPokemon) item.entity, this);
                break;
            case TREE_BASE:
                objectManager.renderTreeBase(batch, (WorldObject) item.entity, this);
                break;
            case TREE_TOP:
                objectManager.renderTreeTop(batch, (WorldObject) item.entity, this);
                break;
            case WORLD_OBJECT:
                objectManager.renderObject(batch, (WorldObject) item.entity, this);
                break;
            case TALL_GRASS_BACKGROUND:
                renderTallGrassBackground(batch, (WorldObject) item.entity);
                break;
            case TALL_GRASS_FOREGROUND:
                renderTallGrassForeground(batch, (WorldObject) item.entity);
                break;
            case BLOCK: // Add this new case
                renderBlock(batch, (PlaceableBlock) item.entity);
                break;
        }
    }// In World.java, replace the renderYSortedEntities method with this corrected version

    private void renderYSortedEntities(SpriteBatch batch, Player player, Rectangle expandedBounds, List<Map.Entry<Vector2, Chunk>> sortedChunks) {
        // START OF FIX: Only rebuild the list if the world state has changed.
        if (ySortDirty) {
            // Clear the pool and the render queue before rebuilding
            for (RenderableEntity entity : ySortQueue) {
                renderableEntityPool.free(entity);
            }
            ySortQueue.clear();

            // Add player
            if (player != null) {
                ySortQueue.add(renderableEntityPool.obtain().init(player, player.getRenderPosition().y, RenderableType.PLAYER));
            }

            // Add other players
            if (GameContext.get().isMultiplayer()) {
                for (OtherPlayer other : GameContext.get().getGameClient().getOtherPlayers().values()) {
                    if (expandedBounds.contains(other.getX(), other.getY())) {
                        ySortQueue.add(renderableEntityPool.obtain().init(other, other.getY(), RenderableType.OTHER_PLAYER));
                    }
                }
            }

            // Add wild pokemon
            for (WildPokemon pokemon : pokemonSpawnManager.getAllWildPokemon()) {
                if (expandedBounds.contains(pokemon.getX(), pokemon.getY())) {
                    ySortQueue.add(renderableEntityPool.obtain().init(pokemon, pokemon.getY(), RenderableType.WILD_POKEMON));
                }
            }

            // Collect ALL world objects AND BLOCKS from visible chunks
            for (Map.Entry<Vector2, Chunk> entry : sortedChunks) {
                if (!isChunkVisible(entry.getKey(), expandedBounds)) continue;
                Chunk chunk = entry.getValue();

                // Add blocks from the chunk to the render queue
                if (chunk.getBlocks() != null) {
                    for (PlaceableBlock block : chunk.getBlocks().values()) {
                        ySortQueue.add(renderableEntityPool.obtain().init(block, block.getPosition().y * TILE_SIZE, RenderableType.BLOCK));
                    }
                }

                List<WorldObject> chunkObjects = objectManager.getObjectsForChunk(entry.getKey());
                if (chunkObjects == null) continue;

                for (WorldObject obj : chunkObjects) {
                    obj.ensureTexture();
                    if (!isObjectInView(obj, expandedBounds)) continue;

                    if (isTallGrassType(obj.getType())) {
                        // AAA-quality tall grass layering: Split grass based on player position
                        // The bottom/stalk part (FOREGROUND) appears below player (renders first)
                        // The top/fluffy part (BACKGROUND) appears above player (renders last)
                        // Use grass base Y for stalks, and slightly higher Y for top foliage
                        float grassBaseY = obj.getPixelY();
                        float grassTopY = obj.getPixelY() + (TILE_SIZE * 0.5f);

                        // Render bottom stalks first (lower Y = renders earlier = behind player)
                        ySortQueue.add(renderableEntityPool.obtain().init(obj, grassBaseY, RenderableType.TALL_GRASS_FOREGROUND));
                        // Render top foliage last (higher Y = renders later = in front of player)
                        ySortQueue.add(renderableEntityPool.obtain().init(obj, grassTopY, RenderableType.TALL_GRASS_BACKGROUND));
                    } else if (obj.getType().renderLayer == WorldObject.ObjectType.RenderLayer.LAYERED) {
                        // Trees: Base renders at object Y, top renders at visual top position
                        // This ensures the player can walk "between" the trunk and leaves
                        float treeBaseY = obj.getPixelY();
                        float treeTopY = obj.getPixelY() + (TILE_SIZE * 2); // Top is 2 tiles up from base

                        ySortQueue.add(renderableEntityPool.obtain().init(obj, treeBaseY, RenderableType.TREE_BASE));
                        ySortQueue.add(renderableEntityPool.obtain().init(obj, treeTopY, RenderableType.TREE_TOP));
                    } else if (obj.getType().renderLayer == WorldObject.ObjectType.RenderLayer.BELOW_PLAYER) {
                        // Regular objects render at their base Y position
                        ySortQueue.add(renderableEntityPool.obtain().init(obj, obj.getPixelY(), RenderableType.WORLD_OBJECT));
                    }
                }
            }

            // Sort the list and mark the cache as clean
            ySortQueue.sort(ySortComparator);
            ySortDirty = false;
        }
        // END OF FIX

        // Render all entities from the (now up-to-date) queue
        for (RenderableEntity item : ySortQueue) {
            renderEntity(batch, item);
        }
    }
    // Add this helper method
    private boolean isObjectInView(WorldObject obj, Rectangle viewBounds) {
        if (obj == null) return false;

        // Get proper bounds for the object type
        float width = obj.getType().widthInTiles * World.TILE_SIZE;
        float height = obj.getType().heightInTiles * World.TILE_SIZE;

        // Trees need special handling due to their offset
        if (isTreeType(obj.getType())) {
            if (obj.getType() == WorldObject.ObjectType.APRICORN_TREE) {
                width = World.TILE_SIZE * 3;
                height = World.TILE_SIZE * 3;
            } else {
                width = World.TILE_SIZE * 2;
                height = World.TILE_SIZE * 3;
            }
        }

        Rectangle objBounds = new Rectangle(
            obj.getPixelX() - (isTreeType(obj.getType()) ? World.TILE_SIZE : 0),
            obj.getPixelY(),
            width,
            height
        );

        return viewBounds.overlaps(objBounds);
    }


    private boolean initialChunksComplete = false;

    public void setInitialLoadComplete(boolean complete) {
        this.initialChunksComplete = complete;
    }

    /**
     * Renders the TOP half of the grass sprite. This is the part that appears
     * BEHIND the player when they are standing on the tile.
     */
    private void renderTallGrassBackground(SpriteBatch batch, WorldObject grass) {
        TextureRegion region = grass.getTexture();
        if (region == null) return;

        int regionWidth = region.getRegionWidth();
        int regionHeight = region.getRegionHeight();

        // Create a new TextureRegion for the top half.
        TextureRegion topHalf = new TextureRegion(region, 0, 0, regionWidth, regionHeight / 2);

        float destX = grass.getPixelX();
        float destY = grass.getPixelY() + TILE_SIZE / 2f; // Draw on the top half of the tile.

        batch.draw(topHalf, destX, destY, TILE_SIZE, TILE_SIZE / 2f);
    }

    /**
     * Renders the BOTTOM half of the grass sprite. This is the part that appears
     * IN FRONT OF the player's feet.
     */
    private void renderTallGrassForeground(SpriteBatch batch, WorldObject grass) {
        TextureRegion region = grass.getTexture();
        if (region == null) return;

        int regionWidth = region.getRegionWidth();
        int regionHeight = region.getRegionHeight();

        // Create a new TextureRegion for the bottom half.
        TextureRegion bottomHalf = new TextureRegion(region, 0, regionHeight / 2, regionWidth, regionHeight / 2);

        float destX = grass.getPixelX();
        float destY = grass.getPixelY(); // Draw at the bottom of the tile.

        batch.draw(bottomHalf, destX, destY, TILE_SIZE, TILE_SIZE / 2f);
    }


    // Call this in render method when F4 is pressed (add to handleInput()):
    public GameClient getGameClient() {
        if (GameContext.get().getGameClient() == null) {
            throw new IllegalStateException("GameClient is null - World not properly initialized");
        }
        return GameContext.get().getGameClient();
    }

    boolean isPositionLoaded(int worldX, int worldY) {
        int chunkX = Math.floorDiv(worldX, Chunk.CHUNK_SIZE);
        int chunkY = Math.floorDiv(worldY, Chunk.CHUNK_SIZE);
        return chunks.containsKey(new Vector2(chunkX, chunkY));
    }

    private void handleCollision(String direction) {
        if (GameContext.get().getPlayer() != null) {
            switch (direction) {
                case "up":
                    GameContext.get().getPlayer().setDirection("up");
                    break;
                case "down":
                    GameContext.get().getPlayer().setDirection("down");
                    break;
                case "left":
                    GameContext.get().getPlayer().setDirection("left");
                    break;
                case "right":
                    GameContext.get().getPlayer().setDirection("right");
                    break;
            }
            GameContext.get().getPlayer().setMoving(false);
        }
    }

    private boolean checkObjectCollision(Rectangle movementBounds, String direction) {
        List<WorldObject> nearbyObjects = objectManager.getObjectsNearPosition(movementBounds.x + movementBounds.width / 2, movementBounds.y + movementBounds.height / 2);

        for (WorldObject obj : nearbyObjects) {
            Rectangle collisionBox = obj.getCollisionBox();
            if (collisionBox != null && collisionBox.overlaps(movementBounds)) {
                if (GameContext.get().getPlayer() != null) {
                    GameContext.get().getPlayer().setDirection(direction);
                    GameContext.get().getPlayer().setMoving(false);
                }
                return true;
            }
        }
        return false;
    }

    private boolean checkPokemonCollision(int worldX, int worldY, String direction) {
        if (isPokemonAt(worldX, worldY)) {
            if (GameContext.get().getPlayer() != null) {
                GameContext.get().getPlayer().setDirection(direction);
                GameContext.get().getPlayer().setMoving(false);
            }
            return true;
        }
        return false;
    }

    private void checkPlayerInteractions(Vector2 playerPosition) {
        float playerPixelX = playerPosition.x * TILE_SIZE;
        float playerPixelY = playerPosition.y * TILE_SIZE;

        nearestPokeball = null;
        float closestDistance = Float.MAX_VALUE;
        int chunkX = (int) Math.floor(playerPixelX / (Chunk.CHUNK_SIZE * TILE_SIZE));
        int chunkY = (int) Math.floor(playerPixelY / (Chunk.CHUNK_SIZE * TILE_SIZE));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                Vector2 chunkPos = new Vector2(chunkX + dx, chunkY + dy);
                List<WorldObject> objects = objectManager.getObjectsForChunk(chunkPos);

                if (objects != null) {
                    for (WorldObject obj : objects) {
                        if (obj.getType() == WorldObject.ObjectType.POKEBALL) {
                            float dx2 = playerPixelX - obj.getPixelX();
                            float dy2 = playerPixelY - obj.getPixelY();
                            float distance = (float) Math.sqrt(dx2 * dx2 + dy2 * dy2);
                            if (distance <= INTERACTION_RANGE && distance < closestDistance) {
                                closestDistance = distance;
                                nearestPokeball = obj;
                            }
                        }
                    }
                }
            }
        }
    }


    public void initializeFromServer(long seed, double worldTimeInMinutes, float dayLength) {
        try {
            GameLogger.info("Initializing multiplayer world with seed: " + seed);
            if (worldData == null) {
                worldData = new WorldData(name);
            }
            WorldData.WorldConfig config = new WorldData.WorldConfig(seed);
            worldData.setConfig(config);
            worldData.setWorldTimeInMinutes(worldTimeInMinutes);
            worldData.setDayLength(dayLength);
            this.worldSeed = seed;
            BiomeManager biomeManager = new BiomeManager(seed);
            GameContext.get().setBiomeManager(biomeManager);
            if (blockManager == null) blockManager = new BlockManager();
            if (objectManager == null) objectManager = new WorldObject.WorldObjectManager(worldSeed);
            if (pokemonSpawnManager == null)
                pokemonSpawnManager = new PokemonSpawnManager(TextureManager.pokemonoverworld);
            chunks.clear();
            biomeTransitions.clear(); // Clear biome transitions as well

            GameLogger.info("Multiplayer world initialization complete - " +
                "Time: " + worldTimeInMinutes + " Day Length: " + dayLength + " Seed: " + seed);

        } catch (Exception e) {
            GameLogger.error("Failed to initialize multiplayer world: " + e.getMessage());
            throw new RuntimeException("Multiplayer world initialization failed", e);
        }
    }

    private volatile int chunkModCount = 0;
    private volatile int lastSortedModCount = -1;

    // call this whenever chunks are added/removed/cleared
    private void onChunksMutated() {
        chunkModCount++;
        cachedSortedChunks = null;
        cachedChunkCount = -1; // legacy guard, keep it
        ySortDirty = true;     // make sure entities rebuild next frame
    }

    private void renderWildPokemon(SpriteBatch batch) {
        Collection<WildPokemon> allPokemon = pokemonSpawnManager.getAllWildPokemon();

        List<WildPokemon> sortedPokemon = new ArrayList<>(allPokemon);
        sortedPokemon.sort((p1, p2) -> Float.compare(p2.getY(), p1.getY()));

        for (WildPokemon pokemon : sortedPokemon) {
            if (pokemon == null || pokemon.getAnimations() == null) {
                continue;
            }


            pokemon.render(batch);
        }
    }

    public void removeWorldObject(WorldObject obj) {
        if (obj == null) {
            GameLogger.error("Attempted to remove a null WorldObject.");
            return;
        }

        // Determine the chunk the object belongs to
        int chunkX = MathUtils.floor(obj.getPixelX() / (Chunk.CHUNK_SIZE * TILE_SIZE));
        int chunkY = MathUtils.floor(obj.getPixelY() / (Chunk.CHUNK_SIZE * TILE_SIZE));
        Vector2 chunkPos = new Vector2(chunkX, chunkY);

        // Remove the object from the central object manager
        List<WorldObject> objectsInManager = objectManager.getObjectsForChunk(chunkPos);
        boolean removedFromManager = false;
        if (objectsInManager != null) {
            // Use the object's unique ID for reliable removal
            removedFromManager = objectsInManager.removeIf(o -> o.getId().equals(obj.getId()));
        }

        // Ensure the object is also removed from the chunk's internal list
        Chunk chunk = getChunks().get(chunkPos);
        boolean removedFromChunkList = false;
        if (chunk != null && chunk.getWorldObjects() != null) {
            removedFromChunkList = chunk.getWorldObjects().removeIf(o -> o.getId().equals(obj.getId()));
        }

        // If the object was successfully removed from either list, handle persistence
        if (removedFromManager || removedFromChunkList) {
            GameLogger.info("Removed WorldObject " + obj.getId() + " (" + obj.getType() + ") from chunk " + chunkPos);

            // *** THE FIX: Mark the chunk as dirty so the change is saved to disk. ***
            if (chunk != null) {
                chunk.setDirty(true);
            }

            // Handle multiplayer synchronization
            if (GameContext.get().isMultiplayer() && GameContext.get().getGameClient() != null) {
                NetworkProtocol.WorldObjectUpdate update = new NetworkProtocol.WorldObjectUpdate();
                update.objectId = obj.getId();
                update.type = NetworkProtocol.NetworkObjectUpdateType.REMOVE;
                update.data = obj.getSerializableData(); // Send data for logging or state confirmation
                GameContext.get().getGameClient().sendWorldObjectUpdate(update);
                GameLogger.info("Sent network update for removed object.");
            }
        } else {
            GameLogger.error("Failed to find and remove WorldObject " + obj.getId() + " in chunk " + chunkPos);
        }

        // Force caches to rebuild to reflect the visual change immediately
        invalidateRenderCaches();
    }

    private static final float AMBIENT_EPSILON = 0.0025f;
    private final Color lastAmbientColor = new Color(-1f, -1f, -1f, -1f);
    private boolean ambientChangedThisFrame = false;

    private void updateWeather(float delta, Vector2 playerPosition, GameScreen gameScreen) {
        float worldX = playerPosition.x * TILE_SIZE;
        float worldY = playerPosition.y * TILE_SIZE;
        currentBiomeTransition = GameContext.get().getBiomeManager().getBiomeAt(worldX, worldY);
        float temperature = calculateTemperature();
        float timeOfDay = (float) (worldData.getWorldTimeInMinutes() % (24 * 60)) / 60f;
        weatherSystem.update(delta, currentBiomeTransition, temperature, timeOfDay, gameScreen);
        weatherAudioSystem.update(delta, weatherSystem.getCurrentWeather(), weatherSystem.getIntensity());
    }

    private float calculateTemperature() {
        BiomeType biomeType = currentBiomeTransition.getPrimaryBiome().getType();
        float baseTemp;

        switch (biomeType) {
            case SNOW:
                baseTemp = 0.0f;
                break;
            case DESERT:
                baseTemp = 35.0f;
                break;
            case HAUNTED:
                baseTemp = 15.0f;
                break;
            case RAIN_FOREST:
                baseTemp = 28.0f;
                break;
            case FOREST:
                baseTemp = 22.0f;
                break;
            default:
                baseTemp = 20.0f;
        }

        float timeOfDay = (float) (worldData.getWorldTimeInMinutes() % (24 * 60)) / 60f;
        float timeVariation = (float) Math.sin((timeOfDay - 6) * Math.PI / 12) * 5.0f;

        return baseTemp + timeVariation;
    }

    private boolean isChunkValid(Chunk chunk) {
        if (chunk == null) return false;
        int[][] data = chunk.getTileData();
        if (data == null || data.length != CHUNK_SIZE) return false;
        for (int[] row : data) {
            if (row == null || row.length != CHUNK_SIZE) return false;
        }
        // Chunk is valid even if it has blocks
        return true;
    }


    public WorldObject getNearestPokeball() {
        return nearestPokeball;
    }

    private enum RenderType {
        TREE_BASE, TREE_TOP, REGULAR_OBJECT, PLAYER
    }

    private static class ObjectWithYPosition {
        public float y;
        public Object object;
        public RenderType renderType;

        public ObjectWithYPosition(float y, Object object, RenderType renderType) {
            this.y = y;
            this.object = object;
            this.renderType = renderType;
        }
    }

    public static class ChunkData {
        public int x;
        public int y;
        public BiomeType biomeType;
        public int[][] tileData;
        public List<WorldObjectData> objects;
        public List<BlockSaveData.BlockData> blocks;
        public long lastModified;
        public boolean isMultiplayer;


        public ChunkData() {
            this.tileData = new int[Chunk.CHUNK_SIZE][Chunk.CHUNK_SIZE];
            this.objects = new ArrayList<>();
            this.blocks = new ArrayList<>();
        }

        public void write(Json json) {
            json.writeObjectStart();
            json.writeValue("x", x);
            json.writeValue("y", y);
            json.writeValue("biomeType", biomeType.name());
            json.writeValue("tileData", tileData);
            json.writeValue("objects", objects);
            json.writeValue("blocks", blocks, ArrayList.class, BlockSaveData.BlockData.class);
            json.writeValue("lastModified", lastModified);
            json.writeValue("isMultiplayer", isMultiplayer);
            json.writeObjectEnd();
        }

        public void read(JsonValue jsonData, Json json) {
            x = jsonData.getInt("x");
            y = jsonData.getInt("y");
            biomeType = BiomeType.valueOf(jsonData.getString("biomeType"));
            tileData = json.readValue(int[][].class, jsonData.get("tileData"));
            objects = json.readValue(ArrayList.class, WorldObjectData.class, jsonData.get("objects"));

            JsonValue blocksValue = jsonData.get("blocks");
            if (blocksValue != null && blocksValue.isArray()) {
                blocks = json.readValue(ArrayList.class, BlockSaveData.BlockData.class, blocksValue);
            } else {
                blocks = new ArrayList<>();
            }

            lastModified = jsonData.getLong("lastModified", System.currentTimeMillis());
            isMultiplayer = jsonData.getBoolean("isMultiplayer", false);
        }
    }

    public static class WorldObjectData {
        public float x;
        public float y;
        public WorldObject.ObjectType type;

        public WorldObjectData() {
        }

        public WorldObjectData(WorldObject obj) {
            this.x = obj.getPixelX();
            this.y = obj.getPixelY();
            this.type = obj.getType();
        }
    }

}
