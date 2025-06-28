package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
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
import io.github.pokemeetup.system.data.*;
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

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class World {
    public static final int INITIAL_LOAD_RADIUS = 2;
    public static final int TILE_SIZE = 32;
    public static final int WORLD_SIZE = 100000 * TILE_SIZE;
    public static final int HALF_WORLD_SIZE = WORLD_SIZE / 2;
    public static final int CHUNK_SIZE = 16;
    public static final float INTERACTION_RANGE = TILE_SIZE * 1.6f;
    private static final float COLOR_TRANSITION_SPEED = 2.0f;
    private static final int MAX_CHUNK_LOAD_RADIUS = 10;
    private static final int MAX_LOADED_CHUNKS = 150;
    private static final long UNLOAD_IDLE_THRESHOLD_MS = 30000;
    private static final int MAX_CHUNKS_INTEGRATED_PER_FRAME = 16;
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
    private Color currentWorldColor = new Color(1, 1, 1, 1);
    private Color previousWorldColor = null;
    private float colorTransitionProgress = 1.0f;
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
    private Map<Vector2, Float> lightLevelMap = new HashMap<>();
    private boolean isDisposed = false;
    private WaterEffectManager waterEffectManager;
    private WaterEffectsRenderer waterEffects;
    private ItemEntityManager itemEntityManager;
    private List<Map.Entry<Vector2, Chunk>> cachedSortedChunks = null;
    private int cachedChunkCount = 0;
    private float lightLevelUpdateTimer = 0f;
    private float manageChunksTimer = 0f;
    private long initialChunkRequestTime;
    private BitmapFont loadingFont;

    public World(WorldData worldData) {
        try {
            GameLogger.info("Initializing multiplayer world: " + worldData.getName());
            this.worldData = worldData;
            this.name = worldData.getName();
            this.worldSeed = worldData.getConfig().getSeed();
            this.blockManager = new BlockManager();
            GameContext.get().setBiomeManager(new BiomeManager(this.worldSeed));
            this.biomeRenderer = new BiomeRenderer();
            this.chunks = new ConcurrentHashMap<>();
            this.loadingChunks = new ConcurrentHashMap<>();
            this.initialChunkLoadQueue = new LinkedList<>();
            this.chunkLoadExecutor = Executors.newFixedThreadPool(2);
            if (!GameContext.get().isMultiplayer()) {
                loadChunksFromWorldData();
                if (worldData.getBlockData() != null) {
                    migrateBlocksToChunks();
                }
            }

            this.objectManager = new WorldObject.WorldObjectManager(worldSeed);
            this.pokemonSpawnManager = new PokemonSpawnManager(TextureManager.pokemonoverworld);
            this.weatherSystem = new WeatherSystem();
            this.weatherSystem.setWorld(this);
            this.weatherAudioSystem = new WeatherAudioSystem(AudioManager.getInstance());

            GameLogger.info("Multiplayer world initialization complete");

        } catch (Exception e) {
            GameLogger.error("Failed to initialize multiplayer world: " + e.getMessage());
            throw new RuntimeException("World initialization failed", e);
        }
        footstepEffectManager = new FootstepEffectManager();
        this.itemEntityManager = new ItemEntityManager();
        this.waterEffectManager = new WaterEffectManager();
        waterEffects = new WaterEffectsRenderer();
    }

    public World(String name, long seed) {
        try {
            GameLogger.info("Initializing singleplayer world: " + name);
            this.name = name;
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
                loadChunksFromWorldData();
                if (worldData != null && worldData.getBlockData() != null) {
                    migrateBlocksToChunks();
                }
            }

            this.weatherSystem = new WeatherSystem();
            this.weatherSystem.setWorld(this);
            this.weatherAudioSystem = new WeatherAudioSystem(AudioManager.getInstance());
            this.objectManager = new WorldObject.WorldObjectManager(worldSeed);
            this.pokemonSpawnManager = new PokemonSpawnManager(TextureManager.pokemonoverworld);

            footstepEffectManager = new FootstepEffectManager();
            if (chunks.isEmpty()) {
                initializeChunksAroundOrigin();
            }

        } catch (Exception e) {
            GameLogger.error("Failed to initialize singleplayer world: " + e.getMessage());
            throw new RuntimeException("World initialization failed", e);
        }
        this.waterEffectManager = new WaterEffectManager();

        waterEffects = new WaterEffectsRenderer();
    }


    private enum RenderableType {
        TREE_BASE(0),
        WORLD_OBJECT(1),
        PLAYER(2),
        OTHER_PLAYER(2),
        WILD_POKEMON(2),
        TALL_GRASS_TOP(3),
        TREE_TOP(4);

        final int layerIndex;

        RenderableType(int index) {
            this.layerIndex = index;
        }
    }

    private static class RenderableEntity implements Comparable<RenderableEntity> {
        final Object entity;
        final float y;
        final RenderableType type;

        RenderableEntity(Object entity, float y, RenderableType type) {
            this.entity = entity;
            this.y = y;
            this.type = type;
        }

        @Override
        public int compareTo(RenderableEntity other) {
            int yCompare = Float.compare(other.y, this.y);
            if (yCompare != 0) {
                return yCompare;
            }

            return Integer.compare(this.type.layerIndex, other.type.layerIndex);
        }
    }

    private void renderTallGrassLowerHalf(SpriteBatch batch, List<WorldObject> tallGrassObjects) {
        for (WorldObject obj : tallGrassObjects) {
            TextureRegion region = obj.getTexture();
            if (region == null) continue;
            int regionWidth = region.getRegionWidth();
            int regionHeight = region.getRegionHeight();
            int srcX = region.getRegionX();
            int srcY = region.getRegionY() + regionHeight / 2;
            int srcHeight = regionHeight / 2;
            float destX = obj.getPixelX();
            float destY = obj.getPixelY();
            batch.draw(region.getTexture(), destX, destY, World.TILE_SIZE, World.TILE_SIZE / 2f,
                srcX, srcY, regionWidth, srcHeight, false, false);
        }
    }

    private void renderTallGrassUpperHalf(SpriteBatch batch, List<WorldObject> tallGrassObjects) {
        for (WorldObject obj : tallGrassObjects) {
            TextureRegion region = obj.getTexture();
            if (region == null) continue;
            int regionWidth = region.getRegionWidth();
            int regionHeight = region.getRegionHeight();
            int srcX = region.getRegionX();
            int srcY = region.getRegionY();
            int srcHeight = regionHeight / 2;
            float destX = obj.getPixelX();
            float destY = obj.getPixelY() + World.TILE_SIZE / 2f;
            batch.draw(region.getTexture(), destX, destY, World.TILE_SIZE, World.TILE_SIZE / 2f,
                srcX, srcY, regionWidth, srcHeight, false, false);
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
        if (GameContext.get().getGameClient() != null && GameContext.get().isMultiplayer()) {
            GameLogger.info("Multiplayer mode: skipping local chunk loading.");
            return;
        }
        try {
            Map<Vector2, Chunk> worldChunks = worldData.getChunks();
            if (worldChunks != null) {
                chunks.clear();
                this.chunks.putAll(worldChunks);
                GameLogger.info("Loaded " + chunks.size() + " chunks from disk.");
            }

            Map<Vector2, List<WorldObject>> worldObjects = worldData.getChunkObjects();
            if (worldObjects != null) {
                int objectCount = 0;
                for (Map.Entry<Vector2, List<WorldObject>> entry : worldObjects.entrySet()) {
                    Vector2 chunkPos = entry.getKey();
                    List<WorldObject> objects = entry.getValue();
                    if (objects != null) {
                        objectManager.setObjectsForChunk(chunkPos, objects);
                        objectCount += objects.size();
                    }
                }
                GameLogger.info("Loaded " + objectCount + " world objects from disk.");
            }
        } catch (Exception e) {
            GameLogger.error("Failed to load world data: " + e.getMessage());
            throw new RuntimeException("World data loading failed", e);
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
            chunk.getBlocks().clear();
            chunk.getWorldObjects().clear();
            getObjectManager().getObjectsForChunk(chunkPos).clear();

            if (chunkData.blockData != null) {
                for (BlockSaveData.BlockData bd : chunkData.blockData) {
                    processBlockData(chunk, bd);
                }
            }

            List<WorldObject> newObjects = new ArrayList<>();
            if (chunkData.worldObjects != null) {
                for (Map<String, Object> objData : chunkData.worldObjects) {
                    if (objData != null) {
                        WorldObject obj = new WorldObject();
                        obj.updateFromData(objData);
                        obj.ensureTexture();
                        newObjects.add(obj);
                    }
                }
            }
            chunk.setWorldObjects(newObjects);
            getObjectManager().setObjectsForChunk(chunkPos, newObjects);

            BiomeTransitionResult transition = new BiomeTransitionResult(
                primaryBiome,
                chunkData.secondaryBiomeType != null ? getBiomeManager().getBiome(chunkData.secondaryBiomeType) : null,
                chunkData.biomeTransitionFactor
            );
            storeBiomeTransition(chunkPos, transition);
            new AutoTileSystem().applyShorelineAutotiling(chunk, 0, this);

            chunk.setDirty(true);
            GameLogger.info("Client processed chunk " + chunkPos + " with " + newObjects.size() + " objects.");

        } catch (Exception e) {
            GameLogger.error("Client error processing chunk " + chunkPos + ": " + e.getMessage());
            e.printStackTrace();
        }
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
            if (GameContext.get().isMultiplayer()) {
                GameLogger.info("Requesting player's current chunk at: " + currentChunk + " (PRIORITY)");
                GameContext.get().getGameClient().requestChunk(currentChunk);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                loadChunkAsync(currentChunk);
            }
        }
        int requestCount = 0;
        for (Vector2 chunkKey : spiralOrder) {
            if (!chunks.containsKey(chunkKey)) {
                if (GameContext.get().isMultiplayer()) {
                    GameLogger.info("Requesting chunk at: " + chunkKey);
                    GameContext.get().getGameClient().requestChunk(chunkKey);
                    requestCount++;
                    if (requestCount % 3 == 0) {
                        try {
                            Thread.sleep(30);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
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
            if (GameContext.get().getPlayer() != null) {
                PlayerData currentState = new PlayerData(GameContext.get().getPlayer().getUsername());
                currentState.updateFromPlayer(GameContext.get().getPlayer());
                worldData.savePlayerData(GameContext.get().getPlayer().getUsername(), currentState, false);
            }
            for (Map.Entry<Vector2, Chunk> entry : chunks.entrySet()) {
                if (entry.getValue().isDirty()) {
                    saveChunkData(entry.getKey(), entry.getValue());
                    entry.getValue().setDirty(false); // Mark as clean after saving
                }
            }
            worldData.setLastPlayed(System.currentTimeMillis());
            worldData.setDirty(true);
            WorldManager.getInstance().saveWorld(worldData);

            GameLogger.info("Successfully saved world: " + name);

        } catch (Exception e) {
            GameLogger.error("Failed to save world '" + name + "': " + e.getMessage());
            e.printStackTrace();
        }
    }


    public void dispose() {
        try {
            GameLogger.info("Disposing world: " + name);
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
            List<WorldObject> objects = objectManager.getObjectsForChunk(chunkPos);
            data.objects = objects.stream().map(WorldObjectData::new).collect(Collectors.toList());

            Json json = new Json();
            json.setOutputType(JsonWriter.OutputType.json);
            registerCustomSerializers(json);

            String baseDir = "worlds/singleplayer/" + name + "/chunks/";
            String filename = String.format("chunk_%d_%d.json", (int) chunkPos.x, (int) chunkPos.y);
            FileHandle chunkFile = Gdx.files.local(baseDir + filename);

            chunkFile.parent().mkdirs();
            String jsonString = json.prettyPrint(data);
            chunkFile.writeString(jsonString, false);
            chunks.put(chunkPos, chunk);
            worldData.getChunks().put(chunkPos, chunk);
            worldData.addChunkObjects(chunkPos, objects);
            worldData.setDirty(true);

        } catch (Exception e) {
            GameLogger.error("Failed to save chunk at " + chunkPos + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Chunk loadChunkData(Vector2 chunkPos) {
        if (GameContext.get().isMultiplayer()) {
            return null;
        }
        try {
            String baseDir = "worlds/singleplayer/" + name + "/chunks/";

            String filename = String.format("chunk_%d_%d.json", (int) chunkPos.x, (int) chunkPos.y);
            FileHandle chunkFile = Gdx.files.local(baseDir + filename);

            if (!chunkFile.exists()) {
                return null;
            }
            String jsonContent = chunkFile.readString();
            Json json = new Json();
            registerCustomSerializers(json);
            ChunkData chunkData = json.fromJson(ChunkData.class, jsonContent);
            BiomeType biomeType = chunkData.biomeType;
            Biome biome = GameContext.get().getBiomeManager().getBiome(biomeType);
            Chunk chunk = new Chunk((int) chunkPos.x, (int) chunkPos.y, biome, worldSeed);
            chunk.setTileData(chunkData.tileData);
            if (chunkData.blocks != null) {

                for (BlockSaveData.BlockData blockDataItem : chunkData.blocks) {
                    try {
                        PlaceableBlock.BlockType blockType = PlaceableBlock.BlockType.fromId(blockDataItem.type);
                        if (blockType == null) {
                            GameLogger.error("Unknown block type: " + blockDataItem.type);
                            continue;
                        }

                        int x = blockDataItem.x;
                        int y = blockDataItem.y;
                        Vector2 pos = new Vector2(x, y);
                        PlaceableBlock block = new PlaceableBlock(blockType, pos, null, blockDataItem.isFlipped);
                        if (blockType == PlaceableBlock.BlockType.CHEST) {
                            block.setChestOpen(blockDataItem.isChestOpen);
                            if (blockDataItem.chestData != null) {
                                block.setChestData(blockDataItem.chestData);
                                GameLogger.info("Loaded chest at " + pos + " with " + blockDataItem.chestData.items.stream().filter(Objects::nonNull).count() + " items");
                            }
                        }
                        TextureRegion baseRegion = BlockTextureManager.getBlockFrame(block, 0);
                        if (baseRegion != null) {
                            TextureRegion texture = new TextureRegion(baseRegion);
                            block.setTexture(texture);
                        } else {
                            GameLogger.error("No texture found for block type: " + block.getId());
                        }
                        chunk.addBlock(block);


                    } catch (Exception e) {
                        GameLogger.error("Failed to load block: " + e.getMessage());
                    }
                }
            }
            if (chunkData.objects != null) {
                List<WorldObject> objects = new ArrayList<>();
                for (WorldObjectData objData : chunkData.objects) {
                    try {
                        WorldObject.ObjectType type = objData.type;
                        float x = objData.x;
                        float y = objData.y;
                        WorldObject obj = objectManager.createObject(type, x, y);
                        if (obj != null) {
                            objects.add(obj);
                        }
                    } catch (Exception e) {
                        GameLogger.error("Failed to load object: " + e.getMessage());
                    }
                }
                objectManager.setObjectsForChunk(chunkPos, objects);
            }

            return chunk;

        } catch (Exception e) {
            GameLogger.error("Error loading chunk at " + chunkPos + ": " + e.getMessage());
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
        this.worldData = data;
        this.name = data.getName();
        this.worldSeed = data.getConfig().getSeed();
        GameContext.get().setBiomeManager(new BiomeManager(this.worldSeed));
        this.chunks.clear();
        if (GameContext.get().getGameClient() == null || !GameContext.get().isMultiplayer()) {
            loadChunksFromWorldData();
            if (worldData.getBlockData() != null) {
                migrateBlocksToChunks();
            }
        }

        GameLogger.info("Set WorldData for world: " + name + " Time: " + data.getWorldTimeInMinutes() + " Played: " + data.getPlayedTime());
    }

    public BiomeManager getBiomeManager() {
        return GameContext.get().getBiomeManager();
    }


    public Biome getBiomeAt(int tileX, int tileY) {
        int chunkX = Math.floorDiv(tileX, CHUNK_SIZE);
        int chunkY = Math.floorDiv(tileY, CHUNK_SIZE);
        Vector2 chunkPos = new Vector2(chunkX, chunkY);
        BiomeTransitionResult storedTransition = biomeTransitions.get(chunkPos);
        if (storedTransition != null && storedTransition.getPrimaryBiome() != null) {
            return storedTransition.getPrimaryBiome();
        }
        Chunk chunk = chunks.get(chunkPos);
        if (chunk != null && chunk.getBiome() != null) {
            if (!biomeTransitions.containsKey(chunkPos)) {
                storeBiomeTransition(chunkPos, new BiomeTransitionResult(chunk.getBiome(), null, 1f));
            }
            return chunk.getBiome();
        }
        BiomeManager bm = GameContext.get().getBiomeManager();
        if (bm != null) {
            BiomeTransitionResult calculatedResult = bm.getBiomeAtTile(tileX, tileY); // Use tile-based query
            if (calculatedResult != null && calculatedResult.getPrimaryBiome() != null) {
                storeBiomeTransition(chunkPos, calculatedResult);
                return calculatedResult.getPrimaryBiome();
            }
        }
        return GameContext.get().getBiomeManager() != null
            ? GameContext.get().getBiomeManager().getBiome(BiomeType.PLAINS)
            : null; // Or handle null case appropriately
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
        Chunk loaded = loadChunkData(chunkPos);
        if (isChunkValid(loaded)) {
            return loaded;
        }

        Chunk generated = UnifiedWorldGenerator.generateChunk(
            (int) chunkPos.x,
            (int) chunkPos.y,
            this.worldSeed,
            GameContext.get().getBiomeManager()
        );
        getObjectManager().setObjectsForChunk(chunkPos, generated.getWorldObjects());
        saveChunkData(chunkPos, generated);
        return generated;
    }

    public void requestInitialChunks() {
        if (GameContext.get().isMultiplayer()) {
            if (initialChunksRequested) {
                return;
            }
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
                if (GameContext.get().getGameClient() != null && GameContext.get().isMultiplayer()) {
                    GameContext.get().getGameClient().requestChunk(chunkPos);
                } else {
                    loadChunkAsync(chunkPos);
                }
            }
        }
    }


    private void updateWorldColor() {
        float hourOfDay = DayNightCycle.getHourOfDay(worldData.getWorldTimeInMinutes());
        Color targetColor = DayNightCycle.getWorldColor(hourOfDay);

        if (currentWorldColor == null) {
            currentWorldColor = new Color(targetColor);
        }
        if (previousWorldColor == null) {
            previousWorldColor = new Color(targetColor);
            colorTransitionProgress = 1.0f;
        }


        if (!targetColor.equals(currentWorldColor) && colorTransitionProgress >= 1.0f) {
            previousWorldColor.set(currentWorldColor);
            colorTransitionProgress = 0.0f;
        }
        if (colorTransitionProgress < 1.0f) {
            colorTransitionProgress = Math.min(1.0f, colorTransitionProgress + Gdx.graphics.getDeltaTime() * COLOR_TRANSITION_SPEED);
            currentWorldColor.r = previousWorldColor.r + (targetColor.r - previousWorldColor.r) * colorTransitionProgress;
            currentWorldColor.g = previousWorldColor.g + (targetColor.g - previousWorldColor.g) * colorTransitionProgress;
            currentWorldColor.b = previousWorldColor.b + (targetColor.b - previousWorldColor.b) * colorTransitionProgress;
            currentWorldColor.a = 1f;
        } else if (!currentWorldColor.equals(targetColor)) {
            currentWorldColor.set(targetColor);
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

    public void loadChunkAsync(final Vector2 chunkPos) {
        loadChunkAsyncWithRetry(chunkPos, 0);
    }

    private void loadChunkAsyncWithRetry(final Vector2 chunkPos, int retryCount) {
        if (isDisposed || (chunkLoadExecutor != null && chunkLoadExecutor.isShutdown())) {
            return;
        }
        if (loadingChunks.containsKey(chunkPos)) return;
        loadingChunks.put(chunkPos, CompletableFuture.completedFuture(null));

        try {
            CompletableFuture.supplyAsync(() -> {
                    return loadOrGenerateChunk(chunkPos);
                }, chunkLoadExecutor)
                .thenApply(chunk -> {
                    if (chunk == null || !isChunkValid(chunk)) {
                        if (retryCount < MAX_CHUNK_RETRY) {
                            throw new RuntimeException("Chunk invalid on retry " + retryCount);
                        } else {
                            return loadOrGenerateChunk(chunkPos);
                        }
                    }
                    return chunk;
                })
                .thenAccept(chunk -> {
                    Gdx.app.postRunnable(() -> {
                        if (chunk != null) {
                            integrationQueue.add(new AbstractMap.SimpleEntry<>(chunkPos, chunk));
                        }
                        loadingChunks.remove(chunkPos);
                    });
                })
                .exceptionally(ex -> {
                    GameLogger.error("Error loading chunk at " + chunkPos + " on retry " + retryCount + ": " + ex.getMessage());
                    loadingChunks.remove(chunkPos);
                    if (retryCount < MAX_CHUNK_RETRY) {
                        Gdx.app.postRunnable(() -> loadChunkAsyncWithRetry(chunkPos, retryCount + 1));
                    } else {
                        Gdx.app.postRunnable(() -> {
                            Chunk chunk = loadOrGenerateChunk(chunkPos);
                            if (chunk != null) {
                                integrationQueue.add(new AbstractMap.SimpleEntry<>(chunkPos, chunk));
                            }
                        });
                    }
                    return null;
                });
        } catch (RejectedExecutionException e) {
            GameLogger.error("Rejected execution for chunk " + chunkPos + ": " + e.getMessage());
            loadingChunks.remove(chunkPos);
        }
    }
    public Chunk getChunkAtPosition(float x, float y) {
        int chunkX = Math.floorDiv((int) x, Chunk.CHUNK_SIZE);
        int chunkY = Math.floorDiv((int) y, Chunk.CHUNK_SIZE);
        Vector2 pos = new Vector2(chunkX, chunkY);
        Chunk chunk = chunks.get(pos);

        if (chunk == null) {
            chunk = loadOrGenerateChunk(pos);
            if (chunk != null) {
                chunks.put(pos, chunk);
            }
        } else if (!isChunkValid(chunk)) {
            chunk = loadOrGenerateChunk(pos);
            if (chunk != null) {
                chunks.put(pos, chunk);
            }
        }
        return chunk;
    }

    public boolean areInitialChunksLoaded() {
        int playerTileX = GameContext.get().getPlayer().getTileX();
        int playerTileY = GameContext.get().getPlayer().getTileY();
        int playerChunkX = Math.floorDiv(playerTileX, Chunk.CHUNK_SIZE);
        int playerChunkY = Math.floorDiv(playerTileY, Chunk.CHUNK_SIZE);
        Vector2 currentChunk = new Vector2(playerChunkX, playerChunkY);
        return chunks.containsKey(currentChunk);
    }


    public void update(float delta, Vector2 playerPosition, float viewportWidth, float viewportHeight, GameScreen gameScreen) {
        if (isDisposed) {
            return;
        }
        int integratedThisFrame = 0;
        while (!integrationQueue.isEmpty() && integratedThisFrame < MAX_CHUNKS_INTEGRATED_PER_FRAME) {
            Map.Entry<Vector2, Chunk> entry = integrationQueue.poll();
            if (entry != null && entry.getValue() != null) {
                chunks.put(entry.getKey(), entry.getValue());
            }
            integratedThisFrame++;
        }

        validateChunkState();
        itemEntityManager.update(delta);

        if (!GameContext.get().isMultiplayer() && !isPlayerChunkLoaded()) {
            if (initialChunkRequestTime == 0) {
                requestInitialChunks();
            }
            if (System.currentTimeMillis() - initialChunkRequestTime > 5000) {
                GameLogger.info("Forcing synchronous load for player's current chunk");
                int playerTileX = GameContext.get().getPlayer().getTileX();
                int playerTileY = GameContext.get().getPlayer().getTileY();
                int playerChunkX = Math.floorDiv(playerTileX, Chunk.CHUNK_SIZE);
                int playerChunkY = Math.floorDiv(playerTileY, Chunk.CHUNK_SIZE);
                Vector2 currentChunk = new Vector2(playerChunkX, playerChunkY);
                Chunk chunk = loadOrGenerateChunk(currentChunk);
                if (chunk != null) {
                    chunks.put(currentChunk, chunk);
                }
            }
        }
        if (worldData != null) {
            worldData.updateTime(delta);
        }
        updateWorldColor();
        lightLevelUpdateTimer += delta;
        if (lightLevelUpdateTimer >= 2.0f) {
            updateLightLevels();
            lightLevelUpdateTimer = 0f;
        }
        updateWeather(delta, playerPosition, gameScreen);
        footstepEffectManager.update(delta);
        manageChunksTimer += delta;
        if (manageChunksTimer >= 0.2f) {
            int currentChunkX = GameContext.get().getPlayer().getTileX() / CHUNK_SIZE;
            int currentChunkY = GameContext.get().getPlayer().getTileY() / CHUNK_SIZE;
            manageChunks(currentChunkX, currentChunkY);
            manageChunksTimer = 0f;
        }

        waterEffectManager.update(delta);
        int playerTileX = (int) playerPosition.x;
        int playerTileY = (int) playerPosition.y;
        Chunk currentChunk = getChunkAtPosition(playerTileX, playerTileY);
        if (currentChunk != null) {
            int localX = Math.floorMod(playerTileX, CHUNK_SIZE);
            int localY = Math.floorMod(playerTileY, CHUNK_SIZE);
            if (TileType.isWaterPuddle(currentChunk.getTileType(localX, localY))) {
                if (GameContext.get().getPlayer() != null && GameContext.get().getPlayer().isMoving()) {
                    waterEffectManager.createRipple(
                        playerTileX * TILE_SIZE + (float) Player.FRAME_WIDTH / 2,
                        playerTileY * TILE_SIZE + (float) Player.FRAME_HEIGHT / 2
                    );
                }
            }
        }
        updateGameSystems(delta, playerPosition);
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

        objectManager.update(chunks);
        checkPlayerInteractions(playerPosition);
    }// --- Modified manageChunks method in World.java ---

    private void manageChunks(int playerChunkX, int playerChunkY) {
        int loadRadius = INITIAL_LOAD_RADIUS + 1;
        PriorityQueue<Vector2> chunkQueue = new PriorityQueue<>(Comparator.comparingDouble(
            cp -> Vector2.dst2(cp.x, cp.y, playerChunkX, playerChunkY)
        ));
        for (int dx = -loadRadius; dx <= loadRadius; dx++) {
            for (int dy = -loadRadius; dy <= loadRadius; dy++) {
                chunkQueue.add(new Vector2(playerChunkX + dx, playerChunkY + dy));
            }
        }
        final int MAX_CHUNKS_PER_FRAME = 8;
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

    public void render(SpriteBatch batch, Rectangle viewBounds, Player player) {

        if (chunks.isEmpty()) {
            renderLoadingOverlay(batch);
            return;
        }
        Color originalColor = batch.getColor().cpy();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        try {   // Set color BEFORE any rendering occurs
            if (currentWorldColor != null) {
                batch.setColor(currentWorldColor);
            }
            Rectangle expandedBounds = getExpandedViewBounds(viewBounds);
            List<Map.Entry<Vector2, Chunk>> sortedChunks = getSortedChunks();


            sortedChunks.sort(Comparator.comparingDouble(entry -> entry.getKey().y));
            renderTerrainLayer(batch, sortedChunks, expandedBounds);

            if (blockManager != null) {
                blockManager.render(batch, worldData.getWorldTimeInMinutes());
            }
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

            if (currentWorldColor != null) {
                batch.setColor(currentWorldColor);
            }
            waterEffectManager.render(batch);
        } finally {
            batch.setColor(originalColor);
        }
    }

    public void render(SpriteBatch batch, Rectangle viewBounds, Player player, GameScreen gameScreen) {
        if (isDisposed) return;
        if (chunks.isEmpty()) {
            renderLoadingOverlay(batch); // Keep your loading screen logic
            return;
        }

        Color originalColor = batch.getColor().cpy();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        try {
            if (currentWorldColor != null) {
                batch.setColor(currentWorldColor);
            }
            Rectangle expandedBounds = getExpandedViewBounds(viewBounds);
            List<Map.Entry<Vector2, Chunk>> sortedChunks = getSortedChunks();
            renderTerrainLayer(batch, sortedChunks, expandedBounds);
            if (blockManager != null) {
                blockManager.render(batch, worldData.getWorldTimeInMinutes());
            }
            itemEntityManager.render(batch);
            footstepEffectManager.render(batch);
            renderSortedEntities(batch, player, expandedBounds);
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

    private void renderSortedEntities(SpriteBatch batch, Player player, Rectangle expandedBounds) {
        List<RenderableEntity> renderQueue = new ArrayList<>();
        renderQueue.add(new RenderableEntity(player, player.getY(), RenderableType.PLAYER));

        if (GameContext.get().isMultiplayer()) {
            if (GameContext.get() != null) {
                for (OtherPlayer other : GameContext.get().getGameClient().getOtherPlayers().values()) {
                    if (expandedBounds.contains(other.getX(), other.getY())) {
                        renderQueue.add(new RenderableEntity(other, other.getY(), RenderableType.OTHER_PLAYER));
                    }
                }
            }
        }

        for (WildPokemon pokemon : pokemonSpawnManager.getAllWildPokemon()) {
            if (expandedBounds.contains(pokemon.getX(), pokemon.getY())) {
                renderQueue.add(new RenderableEntity(pokemon, pokemon.getY(), RenderableType.WILD_POKEMON));
            }
        }

        for (Map.Entry<Vector2, Chunk> entry : chunks.entrySet()) {
            if (isChunkVisible(entry.getKey(), expandedBounds)) {
                for (WorldObject obj : objectManager.getObjectsForChunk(entry.getKey())) {
                    if (isTreeObject(obj)) {
                        renderQueue.add(new RenderableEntity(obj, obj.getPixelY(), RenderableType.TREE_BASE));
                        renderQueue.add(new RenderableEntity(obj, obj.getPixelY(), RenderableType.TREE_TOP));
                    } else if (isTallGrassType(obj.getType())) {
                        renderQueue.add(new RenderableEntity(obj, obj.getPixelY(), RenderableType.WORLD_OBJECT));
                        renderQueue.add(new RenderableEntity(obj, obj.getPixelY(), RenderableType.TALL_GRASS_TOP));
                    } else {
                        renderQueue.add(new RenderableEntity(obj, obj.getPixelY(), RenderableType.WORLD_OBJECT));
                    }
                }
            }
        }
        Collections.sort(renderQueue);
        for (RenderableEntity item : renderQueue) {
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
                case WORLD_OBJECT:
                    WorldObject obj = (WorldObject) item.entity;
                    if (isTallGrassType(obj.getType())) {
                        renderTallGrassUpperHalf(batch, Collections.singletonList(obj));
                    } else {
                        objectManager.renderObject(batch, obj, this);
                    }
                    break;
                case TREE_TOP:
                    objectManager.renderTreeTop(batch, (WorldObject) item.entity, this);
                    break;
                case TALL_GRASS_TOP:
                    renderTallGrassLowerHalf(batch, Collections.singletonList((WorldObject) item.entity));
                    break;
            }
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

    public void updateLightLevels() {
        lightLevelMap.clear();
        float hour = DayNightCycle.getHourOfDay(worldData.getWorldTimeInMinutes());
        if (DayNightCycle.getTimePeriod(hour) != DayNightCycle.TimePeriod.NIGHT) return;
        int playerTileX = GameContext.get().getPlayer().getTileX();
        int playerTileY = GameContext.get().getPlayer().getTileY();
        int playerChunkX = Math.floorDiv(playerTileX, Chunk.CHUNK_SIZE);
        int playerChunkY = Math.floorDiv(playerTileY, Chunk.CHUNK_SIZE);
        int updateRadius = 5;  // Only update chunks within 5 chunks from the player.

        for (Map.Entry<Vector2, Chunk> entry : chunks.entrySet()) {
            Vector2 chunkPos = entry.getKey();
            if (Math.abs(chunkPos.x - playerChunkX) > updateRadius || Math.abs(chunkPos.y - playerChunkY) > updateRadius)
                continue;
            Chunk chunk = entry.getValue();
            for (PlaceableBlock block : chunk.getBlocks().values()) {
                if (block.getId().equalsIgnoreCase("furnace")) {
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

    public void migrateBlocksToChunks() {
        if (worldData == null || worldData.getBlockData() == null) return;

        try {
            BlockSaveData blockData = worldData.getBlockData();
            Map<String, List<BlockSaveData.BlockData>> oldBlocks = blockData.getPlacedBlocks();

            for (Map.Entry<String, List<BlockSaveData.BlockData>> entry : oldBlocks.entrySet()) {
                for (BlockSaveData.BlockData blockDataItem : entry.getValue()) {
                    try {
                        int chunkX = Math.floorDiv(blockDataItem.x, CHUNK_SIZE);
                        int chunkY = Math.floorDiv(blockDataItem.y, CHUNK_SIZE);
                        Vector2 chunkPos = new Vector2(chunkX, chunkY);
                        Chunk chunk = chunks.get(chunkPos);
                        if (chunk == null) continue;
                        PlaceableBlock.BlockType type = PlaceableBlock.BlockType.fromId(blockDataItem.type);
                        if (type == null) {
                            GameLogger.error("Failed to find block type: " + blockDataItem.type);
                            continue;
                        }
                        Vector2 pos = new Vector2(blockDataItem.x, blockDataItem.y);
                        PlaceableBlock block = new PlaceableBlock(type, pos, null, blockDataItem.isFlipped);
                        if (type == PlaceableBlock.BlockType.CHEST) {
                            block.setChestOpen(blockDataItem.isChestOpen);
                        }
                        TextureRegion texture = BlockTextureManager.getBlockFrame(block, 0);
                        if (texture != null) {
                            block.setTexture(texture);
                            chunk.addBlock(block);
                            chunk.setDirty(true);
                        }
                    } catch (Exception e) {
                        GameLogger.error("Failed to migrate block: " + e.getMessage());
                    }
                }
            }
            worldData.setBlockData(null);
            for (Map.Entry<Vector2, Chunk> entry : chunks.entrySet()) {
                if (entry.getValue().isDirty()) {
                    saveChunkData(entry.getKey(), entry.getValue());
                }
            }

            GameLogger.info("Block migration complete");
        } catch (Exception e) {
            GameLogger.error("Block migration failed: " + e.getMessage());
        }
    }

    private List<Map.Entry<Vector2, Chunk>> getSortedChunks() {
        int currentCount = chunks.size();
        if (cachedSortedChunks != null && currentCount == cachedChunkCount) {
            return cachedSortedChunks;
        }
        cachedSortedChunks = new ArrayList<>(chunks.entrySet());
        cachedSortedChunks.sort(Comparator.comparingDouble(entry -> entry.getKey().y));
        cachedChunkCount = currentCount;
        return cachedSortedChunks;
    }

    private void renderTerrainLayer(SpriteBatch batch, List<Map.Entry<Vector2, Chunk>> sortedChunks, Rectangle expandedBounds) {
        for (Map.Entry<Vector2, Chunk> entry : sortedChunks) {
            Vector2 chunkPos = entry.getKey();
            if (isChunkVisible(chunkPos, expandedBounds)) {
                Chunk chunk = entry.getValue();
                getNeighboringBiomes(chunkPos);
                biomeRenderer.renderChunk(batch, chunk, this);
            }
        }
    }

    private Rectangle getExpandedViewBounds(Rectangle viewBounds) {
        float buffer = TILE_SIZE * 2;
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
        if (!isWithinWorldBounds(tileX, tileY)) {
            GameLogger.info("Tile (" + tileX + "," + tileY + ") is outside world bounds.");
            return false;
        }
        if (!isPositionLoaded(tileX, tileY)) {
            return false;
        }
        try {

            int chunkX = Math.floorDiv(tileX, Chunk.CHUNK_SIZE);
            int chunkY = Math.floorDiv(tileY, Chunk.CHUNK_SIZE);
            Vector2 chunkPos = new Vector2(chunkX, chunkY);
            Chunk chunk = chunks.get(chunkPos);
            if (chunk == null) return false;
            int localX = Math.floorMod(tileX, Chunk.CHUNK_SIZE);
            int localY = Math.floorMod(tileY, Chunk.CHUNK_SIZE);


            String currentDirection = GameContext.get().getPlayer() != null ? GameContext.get().getPlayer().getDirection() : "down";
            if (!chunk.isPassable(localX, localY)) {
                handleCollision(currentDirection);
                return false;
            }
            if (blockManager != null && blockManager.hasCollisionAt(tileX, tileY)) {
                return false;
            }
            Rectangle movementBounds = new Rectangle(tileX * TILE_SIZE,  // Now using actual pixel position
                tileY * TILE_SIZE, TILE_SIZE * 0.5f,    // Half tile collision size
                TILE_SIZE * 0.5f);
            return !checkObjectCollision(movementBounds, currentDirection) && !checkPokemonCollision(tileX, tileY, currentDirection);

        } catch (Exception e) {
            GameLogger.error("Error checking passability: " + e.getMessage());
            return false;
        }
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
        int chunkX = (int) Math.floor(obj.getPixelX() / (Chunk.CHUNK_SIZE * TILE_SIZE));
        int chunkY = (int) Math.floor(obj.getPixelY() / (Chunk.CHUNK_SIZE * TILE_SIZE));
        Vector2 chunkPos = new Vector2(chunkX, chunkY);

        List<WorldObject> objects = objectManager.getObjectsForChunk(chunkPos);
        if (objects != null) {
            objects.remove(obj);
        }
    }

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
