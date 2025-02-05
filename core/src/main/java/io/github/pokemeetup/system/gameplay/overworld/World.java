package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.blocks.BlockManager;
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.managers.*;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.screens.GameScreen;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.data.*;
import io.github.pokemeetup.system.gameplay.inventory.ItemEntityManager;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.InventorySlotData;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldManager;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.PerformanceProfiler;
import io.github.pokemeetup.utils.storage.JsonConfig;
import io.github.pokemeetup.utils.textures.BlockTextureManager;
import io.github.pokemeetup.utils.textures.TextureManager;
import io.github.pokemeetup.utils.textures.TileType;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class World {
    public static final int INITIAL_LOAD_RADIUS = 1;
    public static final int WORLD_SIZE = 100000;
    public static final int TILE_SIZE = 32;
    public static final int CHUNK_SIZE = 16;
    public static final float INTERACTION_RANGE = TILE_SIZE * 1.6f;
    public static final int HALF_WORLD_SIZE = WORLD_SIZE / 2;
    /**
     * Immediately loads all chunks around the current player position
     * within the INITIAL_LOAD_RADIUS. This is useful after a teleport.
     */
    public static final int CONTINUOUS_LOAD_RADIUS = 2; // For continuous updating around the player
    private static final float COLOR_TRANSITION_SPEED = 2.0f;
    public static int DEFAULT_X_POSITION = 0;
    public static int DEFAULT_Y_POSITION = 0;
    private final Map<Vector2, Long> lastChunkAccess = new ConcurrentHashMap<>();
    private final Set<Vector2> dirtyChunks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final BiomeRenderer biomeRenderer;
    private final WeatherSystem weatherSystem;
    private final WeatherAudioSystem weatherAudioSystem;
    private final Map<Vector2, BiomeTransitionResult> biomeTransitions = new ConcurrentHashMap<>();
    private Color currentWorldColor = new Color(1, 1, 1, 1);
    private Color previousWorldColor = null;
    private float colorTransitionProgress = 1.0f;
    private volatile boolean initialChunksRequested = false;
    private Map<Vector2, Chunk> chunks;
    private Map<Vector2, Future<Chunk>> loadingChunks;
    private Queue<Vector2> initialChunkLoadQueue = new LinkedList<>();
    private ExecutorService chunkLoadExecutor = Executors.newFixedThreadPool(4);
    private BiomeManager biomeManager;
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

    public World(WorldData worldData) {
        try {
            GameLogger.info("Initializing multiplayer world: " + worldData.getName());
            this.worldData = worldData;
            this.name = worldData.getName();
            this.worldSeed = worldData.getConfig().getSeed();
            this.blockManager = new BlockManager(this);
            this.biomeManager = new BiomeManager(this.worldSeed);
            this.biomeRenderer = new BiomeRenderer();
            this.chunks = new ConcurrentHashMap<>();
            this.loadingChunks = new ConcurrentHashMap<>();
            this.initialChunkLoadQueue = new LinkedList<>();
            this.chunkLoadExecutor = Executors.newFixedThreadPool(4);

            loadChunksFromWorldData();
            if (worldData.getBlockData() != null) {
                migrateBlocksToChunks();
            }

            this.objectManager = new WorldObject.WorldObjectManager(worldSeed);
            this.pokemonSpawnManager = new PokemonSpawnManager(TextureManager.pokemonoverworld);
            this.weatherSystem = new WeatherSystem();
            this.weatherAudioSystem = new WeatherAudioSystem(AudioManager.getInstance());

            GameLogger.info("Multiplayer world initialization complete");

        } catch (Exception e) {
            GameLogger.error("Failed to initialize multiplayer world: " + e.getMessage());
            throw new RuntimeException("World initialization failed", e);
        }
        this.itemEntityManager = new ItemEntityManager();
        this.waterEffectManager = new WaterEffectManager();
        waterEffects = new WaterEffectsRenderer();
    }

    public World(String name, long seed, BiomeManager manager) {
        try {
            GameLogger.info("Initializing singleplayer world: " + name);
            this.name = name;
            this.itemEntityManager = new ItemEntityManager();
            this.biomeManager = manager;
            this.worldSeed = seed;

            this.worldData = new WorldData(name);
            WorldData.WorldConfig config = new WorldData.WorldConfig(seed);
            this.worldData.setConfig(config);
            // Initialize basic managers
            this.blockManager = new BlockManager(this);
            this.biomeRenderer = new BiomeRenderer();

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
            this.weatherAudioSystem = new WeatherAudioSystem(AudioManager.getInstance());
            this.objectManager = new WorldObject.WorldObjectManager(worldSeed);
            this.pokemonSpawnManager = new PokemonSpawnManager(TextureManager.pokemonoverworld);

            // Generate initial chunks if needed
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
        if (GameContext.get().getGameClient() != null &&
            GameContext.get().isMultiplayer()) {
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

    public void requestInitialChunks(Vector2 playerPosition) {
        if (initialChunksRequested) {
            GameLogger.info("Initial chunks already requested");
            return;
        }

        initialChunksRequested = true;
        GameLogger.info("Requesting initial chunks around: " + playerPosition);


        int playerTileX = GameContext.get().getPlayer().getTileX();
        int playerTileY = GameContext.get().getPlayer().getTileY();
        int playerChunkX = Math.floorDiv(playerTileX, Chunk.CHUNK_SIZE);
        int playerChunkY = Math.floorDiv(playerTileY, Chunk.CHUNK_SIZE);
        // Request chunks in a spiral pattern for better loading visuals
        List<Vector2> chunkOrder = generateSpiralChunkOrder(playerChunkX, playerChunkY, INITIAL_LOAD_RADIUS);
        for (Vector2 chunkPos : chunkOrder) {
            if (!chunks.containsKey(chunkPos)) {
                if (GameContext.get().getGameClient() != null && GameContext.get().isMultiplayer()) {
                    GameContext.get().getGameClient().requestChunk(chunkPos);
                } else {
                    loadChunkAsync(chunkPos);
                }
                GameLogger.info("Requested chunk at: " + chunkPos);
            }
        }
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

    public void savePlayerData(String username, PlayerData data) {
        if (worldData != null) {
            boolean isMultiplayer = false;
            if (GameContext.get().getGameClient() != null) {
                isMultiplayer = GameContext.get().isMultiplayer();
            }

            if (isMultiplayer) {
                UUID playerUUID = UUID.nameUUIDFromBytes(username.getBytes());
                GameContext.get().getGameClient().savePlayerData(playerUUID, data);
            } else {
                worldData.savePlayerData(username, data, false);
            }
        }
    }

    private boolean isMultiplayerOperation() {
        return GameContext.get().isMultiplayer();
    }

    public void storeBiomeTransition(Vector2 chunkPos, BiomeTransitionResult transition) {
        if (transition == null) {
            biomeTransitions.remove(chunkPos);
            return;
        }
        biomeTransitions.put(chunkPos, transition);
    }

    public void processChunkData(NetworkProtocol.ChunkData chunkData) {
        Vector2 chunkPos = new Vector2(chunkData.chunkX, chunkData.chunkY);

        Gdx.app.postRunnable(() -> {
            try {
                World world = GameContext.get().getWorld();
                if (world == null) return;

                // Create the chunk with proper biome
                Biome biome = world.getBiomeManager().getBiome(chunkData.primaryBiomeType);
                if (biome == null) {
                    biome = world.getBiomeManager().getBiome(BiomeType.PLAINS);
                }

                Chunk chunk = new Chunk(chunkData.chunkX, chunkData.chunkY,
                    biome, world.getWorldData().getConfig().getSeed(), world.getBiomeManager());

                // Set tile data
                chunk.setTileData(chunkData.tileData);

                // Process blocks
                if (chunkData.blockData != null) {
                    for (BlockSaveData.BlockData bd : chunkData.blockData) {
                        processBlockData(chunk, bd);
                    }
                }

                // Process world objects
                List<WorldObject> objects = new ArrayList<>();
                if (chunkData.worldObjects != null) {
                    for (Map<String, Object> objData : chunkData.worldObjects) {
                        WorldObject obj = new WorldObject();
                        obj.updateFromData(objData);
                        obj.ensureTexture();
                        objects.add(obj);
                    }
                }

                // Update chunk and object storage
                world.getChunks().put(chunkPos, chunk);
                world.getObjectManager().setObjectsForChunk(chunkPos, objects);

            } catch (Exception e) {
                GameLogger.error("Error processing chunk data: " + e.getMessage());
            }
        });

    }

    private void processBlockData(Chunk chunk, BlockSaveData.BlockData bd) {
        PlaceableBlock.BlockType blockType = PlaceableBlock.BlockType.fromId(bd.type);
        if (blockType != null) {
            Vector2 pos = new Vector2(bd.x, bd.y);
            PlaceableBlock block = new PlaceableBlock(blockType, pos, null, bd.isFlipped);
            block.setChestOpen(bd.isChestOpen);
            block.setChestData(bd.chestData);
            chunk.addBlock(block);
        }
    }


    public boolean areInitialChunksLoaded() {
        if (!initialChunksRequested) {
            GameLogger.error("Initial chunks not yet requested");
            return false;
        }

        int totalRequired = (INITIAL_LOAD_RADIUS * 2 + 1) * (INITIAL_LOAD_RADIUS * 2 + 1);
        int loadedCount = chunks.size();

        boolean allLoaded = loadedCount >= totalRequired;

        if (!allLoaded) {
            GameLogger.info("Still loading chunks: " + loadedCount + "/" + totalRequired);
            // Log missing chunk positions
            if (GameContext.get().getPlayer() != null) {
                int playerChunkX = (int) Math.floor(GameContext.get().getPlayer().getX() / (CHUNK_SIZE * TILE_SIZE));
                int playerChunkY = (int) Math.floor(GameContext.get().getPlayer().getY() / (CHUNK_SIZE * TILE_SIZE));

                for (int dx = -INITIAL_LOAD_RADIUS; dx <= INITIAL_LOAD_RADIUS; dx++) {
                    for (int dy = -INITIAL_LOAD_RADIUS; dy <= INITIAL_LOAD_RADIUS; dy++) {
                        Vector2 chunkPos = new Vector2(playerChunkX + dx, playerChunkY + dy);
                        if (!chunks.containsKey(chunkPos)) {
                            GameLogger.error("Missing chunk at: " + chunkPos);
                        }
                    }
                }
            }
        }

        return allLoaded;
    }

    public void initializeWorldFromData(WorldData worldData) {
        setWorldData(worldData);
        loadChunksFromWorldData();
    }

    public boolean areAllChunksLoaded() {
        // Get the player's current chunk coordinates:
        int playerTileX = GameContext.get().getPlayer().getTileX();
        int playerTileY = GameContext.get().getPlayer().getTileY();
        int playerChunkX = Math.floorDiv(playerTileX, Chunk.CHUNK_SIZE);
        int playerChunkY = Math.floorDiv(playerTileY, Chunk.CHUNK_SIZE);

        int totalChunks = (INITIAL_LOAD_RADIUS * 2 + 1) * (INITIAL_LOAD_RADIUS * 2 + 1);
        int loadedChunks = 0;

        // Check chunks relative to the player's chunk coordinate.
        for (int dx = -INITIAL_LOAD_RADIUS; dx <= INITIAL_LOAD_RADIUS; dx++) {
            for (int dy = -INITIAL_LOAD_RADIUS; dy <= INITIAL_LOAD_RADIUS; dy++) {
                Vector2 expectedKey = new Vector2(playerChunkX + dx, playerChunkY + dy);
                if (chunks.containsKey(expectedKey)) {
                    loadedChunks++;
                }
            }
        }
        if (chunks.size() != loadedChunks) {
            GameLogger.error("Chunk count mismatch - Map size: " + chunks.size() +
                ", Counted: " + loadedChunks);
        }

        GameLogger.info("Chunks loaded: " + loadedChunks + "/" + totalChunks);
        return loadedChunks == totalChunks;
    }

    public void clearChunks() {
        if (chunks != null) {
            chunks.clear();
            // Reset the flag so that new initial chunk requests will occur
            initialChunksRequested = false;
            GameLogger.info("Cleared all loaded chunks and reset initialChunksRequested flag.");
        }
    }

    public void loadChunksAroundPlayer() {
        Player player = GameContext.get().getPlayer();
        if (player == null) return;

        int playerTileX = player.getTileX();
        int playerTileY = player.getTileY();
        int playerChunkX = Math.floorDiv(playerTileX, Chunk.CHUNK_SIZE);
        int playerChunkY = Math.floorDiv(playerTileY, Chunk.CHUNK_SIZE);

        for (int dx = -INITIAL_LOAD_RADIUS; dx <= INITIAL_LOAD_RADIUS; dx++) {
            for (int dy = -INITIAL_LOAD_RADIUS; dy <= INITIAL_LOAD_RADIUS; dy++) {
                Vector2 chunkKey = new Vector2(playerChunkX + dx, playerChunkY + dy);
                if (!chunks.containsKey(chunkKey)) {
                    // Either request this chunk from the server or load it locally.
                    loadChunkAsync(chunkKey);
                    GameLogger.info("Requested chunk at: " + chunkKey);
                }
            }
        }
    }

    public void updateChunksAroundPlayer() {
        Player player = GameContext.get().getPlayer();
        if (player == null) return;

        // Convert player's tile position to chunk coordinates.
        int playerChunkX = MathUtils.floor((float) player.getTileX() / CHUNK_SIZE);
        int playerChunkY = MathUtils.floor((float) player.getTileY() / CHUNK_SIZE);
        int loadRadius = CONTINUOUS_LOAD_RADIUS;

        // Create a priority queue so that chunks closest to the player get loaded first.
        PriorityQueue<Vector2> missingQueue = new PriorityQueue<>(Comparator.comparingDouble(
            cp -> Vector2.dst2(cp.x, cp.y, playerChunkX, playerChunkY)
        ));

        for (int dx = -loadRadius; dx <= loadRadius; dx++) {
            for (int dy = -loadRadius; dy <= loadRadius; dy++) {
                Vector2 chunkKey = new Vector2(playerChunkX + dx, playerChunkY + dy);
                if (!chunks.containsKey(chunkKey) && !loadingChunks.containsKey(chunkKey)) {
                    missingQueue.add(chunkKey);
                }
            }
        }

        final int MAX_CHUNKS_PER_FRAME = 4;
        long startTime = System.nanoTime();
        int loadedThisFrame = 0;
        while (!missingQueue.isEmpty() && loadedThisFrame < MAX_CHUNKS_PER_FRAME) {
            // Impose a time budget of 5ms per frame for requesting new chunks.
            if (System.nanoTime() - startTime > 5_000_000) {
                break;
            }
            Vector2 chunkKey = missingQueue.poll();
            loadChunkAsync(chunkKey);
            loadedThisFrame++;
        }
    }


    public void forceLoadMissingChunks() {
        GameLogger.info("Forcing load of any missing chunks...");
        int loaded = 0;
        Player player = GameContext.get().getPlayer();
        if (player == null) return;
        int playerChunkX = Math.floorDiv(player.getTileX(), Chunk.CHUNK_SIZE);
        int playerChunkY = Math.floorDiv(player.getTileY(), Chunk.CHUNK_SIZE);

        for (int dx = -INITIAL_LOAD_RADIUS; dx <= INITIAL_LOAD_RADIUS; dx++) {
            for (int dy = -INITIAL_LOAD_RADIUS; dy <= INITIAL_LOAD_RADIUS; dy++) {
                Vector2 chunkPos = new Vector2(playerChunkX + dx, playerChunkY + dy);
                if (!chunks.containsKey(chunkPos)) {
                    try {
                        Chunk chunk = loadOrGenerateChunk(chunkPos);
                        if (chunk != null) {
                            chunks.put(chunkPos, chunk);
                            loaded++;
                        }
                    } catch (Exception e) {
                        GameLogger.error("Failed to force load chunk at " + chunkPos + ": " + e.getMessage());
                    }
                }
            }
        }
        GameLogger.info("Force loaded " + loaded + " missing chunks");
    }

    public WildPokemon getNearestInteractablePokemon(Player player) {
        // Convert player position to pixels
        float playerPixelX = player.getTileX() * TILE_SIZE + (Player.FRAME_WIDTH / 2f);
        float playerPixelY = player.getTileY() * TILE_SIZE + (Player.FRAME_HEIGHT / 2f);

        float checkX = playerPixelX;
        float checkY = playerPixelY;
        float interactionDistance = TILE_SIZE * 1.5f;

        // Adjust check position based on facing direction
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

        Collection<WildPokemon> nearbyPokemon = pokemonSpawnManager.getPokemonInRange(
            checkX, checkY, interactionDistance
        );

        for (WildPokemon pokemon : nearbyPokemon) {
            float distance = Vector2.dst(
                checkX,
                checkY,
                pokemon.getX(),
                pokemon.getY()
            );

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
            GameLogger.info("Skipping world save in multiplayer mode");
            return;
        }

        try {
            if (GameContext.get().getPlayer() != null) {
                PlayerData currentState = new PlayerData(GameContext.get().getPlayer().getUsername());
                currentState.updateFromPlayer(GameContext.get().getPlayer());
                worldData.savePlayerData(GameContext.get().getPlayer().getUsername(), currentState, false);
            }

            // Only save dirty chunks in singleplayer
            if (!isMultiplayerOperation()) {
                for (Map.Entry<Vector2, Chunk> entry : chunks.entrySet()) {
                    if (entry.getValue().isDirty()) {
                        saveChunkData(entry.getKey(), entry.getValue());
                    }
                }
            }

            worldData.setLastPlayed(System.currentTimeMillis());
            worldData.setDirty(true);

            if (!isMultiplayerOperation()) {
                WorldManager.getInstance().saveWorld(worldData);
            }

        } catch (Exception e) {
            GameLogger.error("Failed to save world: " + name + " - " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void dispose() {
        try {
            GameLogger.info("Disposing world: " + name);

            // Final save
            save();

            // Clear collections
            chunks.clear();
            biomeTransitions.clear();
            loadingChunks.clear();
            lastChunkAccess.clear();
            dirtyChunks.clear();
            if (waterEffects != null) {
                waterEffects.dispose();
            }
            // Dispose executors
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
        // Initialize player data
        if (player != null) {
            this.currentPlayerData = new PlayerData(player.getUsername());
            this.currentPlayerData.updateFromPlayer(player);
        }
    }

    public void saveChunkData(Vector2 chunkPos, Chunk chunk) {
        if (GameContext.get().getGameClient() != null && GameContext.get().isMultiplayer()) {
            // Skip saving in multiplayer.
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
            data.objects = objects.stream()
                .map(WorldObjectData::new)
                .collect(Collectors.toList());

            Json json = new Json();
            json.setOutputType(JsonWriter.OutputType.json);
            registerCustomSerializers(json);

            String baseDir =
                "worlds/singleplayer/" + name + "/chunks/";
            String filename = String.format("chunk_%d_%d.json", (int) chunkPos.x, (int) chunkPos.y);
            FileHandle chunkFile = Gdx.files.local(baseDir + filename);

            chunkFile.parent().mkdirs();

            // Write json
            String jsonString = json.prettyPrint(data);
            chunkFile.writeString(jsonString, false);

            // Update in-memory data
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

            // Load and parse raw JSON
            String jsonContent = chunkFile.readString();
            Json json = new Json();
            registerCustomSerializers(json);

            // Read the ChunkData object
            ChunkData chunkData = json.fromJson(ChunkData.class, jsonContent);

            // Create chunk with original biome
            BiomeType biomeType = chunkData.biomeType;
            Biome biome = biomeManager.getBiome(biomeType);
            Chunk chunk = new Chunk((int) chunkPos.x, (int) chunkPos.y, biome, worldSeed, biomeManager);

            // Load tile data
            chunk.setTileData(chunkData.tileData);

            // Load blocks
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

                        // Create the PlaceableBlock instance without texture
                        PlaceableBlock block = new PlaceableBlock(blockType, pos, null, blockDataItem.isFlipped);

                        // Set isChestOpen
                        if (blockType == PlaceableBlock.BlockType.CHEST) {
                            block.setChestOpen(blockDataItem.isChestOpen);

                            // Handle chest data
                            if (blockDataItem.chestData != null) {
                                block.setChestData(blockDataItem.chestData);
                                GameLogger.info("Loaded chest at " + pos + " with " +
                                    blockDataItem.chestData.items.stream().filter(Objects::nonNull).count() + " items");
                            }
                        }

                        // In loadChunkData or processChunkData when setting the block texture:
                        TextureRegion baseRegion = BlockTextureManager.getBlockFrame(block, 0);
                        if (baseRegion != null) {
                            // Create a new TextureRegion to avoid flipping the shared atlas region
                            TextureRegion texture = new TextureRegion(baseRegion);
                            // Don't call texture.flip(). We'll rely solely on PlaceableBlock.render() for flipping visuals.
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
        // Serializer for UUID
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
                // Handle both "itemId" and "id" fields for backward compatibility
                if (jsonData.has("itemId")) {
                    item.setItemId(jsonData.getString("itemId"));
                } else if (jsonData.has("id")) {
                    // Backward compatibility with old data
                    item.setItemId(jsonData.getString("id"));
                } else {
                    // Handle missing itemId/id
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
        this.biomeManager = new BiomeManager(this.worldSeed);

        // Clear existing chunks and objects
        this.chunks.clear();
        if (GameContext.get().getGameClient() == null ||
            !GameContext.get().isMultiplayer()) {
            loadChunksFromWorldData();
            if (worldData.getBlockData() != null) {
                migrateBlocksToChunks();
            }
        }

        GameLogger.info("Set WorldData for world: " + name +
            " Time: " + data.getWorldTimeInMinutes() +
            " Played: " + data.getPlayedTime());
    }

    public BiomeManager getBiomeManager() {
        return biomeManager;
    }

    public Biome getBiomeAt(int tileX, int tileY) {
        // In multiplayer mode, use the biome from the chunk provided by the server.
        if (GameContext.get().isMultiplayer()) {
            // Compute the chunk coordinates from the tile coordinates:
            int chunkX = tileX / Chunk.CHUNK_SIZE;
            int chunkY = tileY / Chunk.CHUNK_SIZE;
            Vector2 chunkKey = new Vector2(chunkX, chunkY);
            Chunk chunk = chunks.get(chunkKey);
            if (chunk != null) {
                return chunk.getBiome();
            }
        }

        // Otherwise (or if no chunk found) fall back to our local biome calculation.
        float worldX = tileX * TILE_SIZE;
        float worldY = tileY * TILE_SIZE;
        BiomeTransitionResult transition = biomeManager.getBiomeAt(worldX, worldY);
        return transition.getPrimaryBiome();
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
                        loadChunkAsync(chunkPos);
                    }
                }
            }
        }
    }


    public Chunk loadOrGenerateChunk(Vector2 chunkPos) {
        if (GameContext.get().isMultiplayer()) return null;
        Chunk chunk = loadChunkData(chunkPos);
        if (chunk == null) {
            int worldX = (int) (chunkPos.x * Chunk.CHUNK_SIZE);
            int worldY = (int) (chunkPos.y * Chunk.CHUNK_SIZE);
            BiomeTransitionResult biomeTransition = biomeManager.getBiomeAt(worldX * World.TILE_SIZE,
                worldY * World.TILE_SIZE);
            Biome biome = biomeTransition.getPrimaryBiome();
            if (biome == null) {
                GameLogger.error("Null biome at " + worldX + "," + worldY);
                biome = biomeManager.getBiome(BiomeType.PLAINS); // Fallback
            }
            chunk = UnifiedWorldGenerator.generateChunk((int) chunkPos.x, (int) chunkPos.y, worldSeed, biomeManager);
            objectManager.generateObjectsForChunk(chunkPos, chunk, biome);
            saveChunkData(chunkPos, chunk);
        }
        return chunk;
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

        // Update transition
        if (colorTransitionProgress < 1.0f) {
            colorTransitionProgress = Math.min(1.0f, colorTransitionProgress +
                Gdx.graphics.getDeltaTime() * COLOR_TRANSITION_SPEED);

            // Interpolate between colors
            currentWorldColor.r = previousWorldColor.r + (targetColor.r - previousWorldColor.r) * colorTransitionProgress;
            currentWorldColor.g = previousWorldColor.g + (targetColor.g - previousWorldColor.g) * colorTransitionProgress;
            currentWorldColor.b = previousWorldColor.b + (targetColor.b - previousWorldColor.b) * colorTransitionProgress;
            currentWorldColor.a = 1f;
        } else if (!currentWorldColor.equals(targetColor)) {
            // Ensure we reach the target color exactly
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

    public void loadChunkAsync(Vector2 chunkPos) {
        if (isDisposed) {
            return;
        }
        try {
            if (loadingChunks.containsKey(chunkPos)) {
                return;
            }

            CompletableFuture<Chunk> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return loadOrGenerateChunk(chunkPos);
                } catch (Exception e) {
                    GameLogger.error("Failed to load chunk at " + chunkPos + ": " + e.getMessage());
                    return null;
                }
            }, chunkLoadExecutor).thenApplyAsync(chunk -> {
                if (chunk != null) {
                    chunks.put(chunkPos, chunk);
                    loadingChunks.remove(chunkPos);
                }
                return chunk;
            });

            loadingChunks.put(chunkPos, future);
        } catch (Exception e) {
            GameLogger.error("Error initiating chunk load: " + e.getMessage());
        }
    }

    public void update(float delta, Vector2 playerPosition, float viewportWidth, float viewportHeight, GameScreen gameScreen) {
        if (isDisposed) {
            return;
        }
        validateChunkState();
        itemEntityManager.update(delta);

        if (!initialChunksRequested) {
            requestInitialChunks(playerPosition);
            return;
        }

        if (worldData != null) {
            worldData.updateTime(delta);
        }
        updateWorldColor();
        updateLightLevels();
        updateWeather(delta, playerPosition, gameScreen);


        // Profile the chunk management routines
        PerformanceProfiler.start("manageChunks");
        manageChunks(playerPosition);
        PerformanceProfiler.end("manageChunks");

        // Apply a time budget to chunk requests around the player.
        PerformanceProfiler.start("updateChunksAroundPlayer");
        updateChunksAroundPlayer();
        PerformanceProfiler.end("updateChunksAroundPlayer");
        waterEffectManager.update(delta);

        // Check if player is on water tile
        int playerTileX = (int) (playerPosition.x / TILE_SIZE);
        int playerTileY = (int) (playerPosition.y / TILE_SIZE);

        Chunk currentChunk = getChunkAtPosition(playerTileX, playerTileY);
        if (currentChunk != null) {
            int localX = Math.floorMod(playerTileX, Chunk.CHUNK_SIZE);
            int localY = Math.floorMod(playerTileY, Chunk.CHUNK_SIZE);

            if (TileType.isWaterPuddle(currentChunk.getTileType(localX, localY))) {
                if (GameContext.get().getPlayer() != null && GameContext.get().getPlayer().isMoving()) {
                    waterEffectManager.createRipple(
                        playerPosition.x * TILE_SIZE + (float) Player.FRAME_WIDTH / 2,
                        playerPosition.y * TILE_SIZE + (float) Player.FRAME_HEIGHT / 2
                    );
                }
            }
        }
        // Update game systems
        updateGameSystems(delta, playerPosition);
    }

    public void updateGameSystems(float delta, Vector2 playerPosition) {
        BiomeTransitionResult currentBiomeTransition = biomeManager.getBiomeAt(
            playerPosition.x * TILE_SIZE,
            playerPosition.y * TILE_SIZE
        );

        Biome currentBiome = currentBiomeTransition.getPrimaryBiome();

        if (AudioManager.getInstance() != null) {
            AudioManager.getInstance().updateBiomeMusic(currentBiome.getType());
            AudioManager.getInstance().update(delta);
        }
        // Update other systems
        pokemonSpawnManager.update(delta, playerPosition);

        objectManager.update(chunks);
        checkPlayerInteractions(playerPosition);
    }

    private void manageChunks(Vector2 playerPosition) {
        // Since playerPosition is already in tile coordinates:
        int playerChunkX = MathUtils.floor(playerPosition.x / CHUNK_SIZE);
        int playerChunkY = MathUtils.floor(playerPosition.y / CHUNK_SIZE);
        int loadRadius = INITIAL_LOAD_RADIUS + 1;

        // Create a priority queue where chunks nearer the player's chunk get higher priority.
        PriorityQueue<Vector2> chunkQueue = new PriorityQueue<>(Comparator.comparingDouble(
            cp -> Vector2.dst2(cp.x, cp.y, playerChunkX, playerChunkY)
        ));

        for (int dx = -loadRadius; dx <= loadRadius; dx++) {
            for (int dy = -loadRadius; dy <= loadRadius; dy++) {
                // Use the player's chunk coordinate as the base.
                chunkQueue.add(new Vector2(playerChunkX + dx, playerChunkY + dy));
            }
        }

        final int MAX_CHUNKS_PER_FRAME = 4;
        int loadedThisFrame = 0;
        while (!chunkQueue.isEmpty() && loadedThisFrame < MAX_CHUNKS_PER_FRAME) {
            Vector2 chunkPos = chunkQueue.poll();
            if (!chunks.containsKey(chunkPos) && !loadingChunks.containsKey(chunkPos)) {
                loadChunkAsync(chunkPos);
                loadedThisFrame++;
            }
            lastChunkAccess.put(chunkPos, System.currentTimeMillis());
        }
    }


    public PokemonSpawnManager getPokemonSpawnManager() {
        return pokemonSpawnManager;
    }

    public void render(SpriteBatch batch, Rectangle viewBounds, Player player) {
        if (chunks.isEmpty()) {
            GameLogger.error("No chunks available for rendering!");
            return;
        }
        Color originalColor = batch.getColor().cpy();

        // Set the blending function to the default before rendering
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // Save the original color

        try {   // Set color BEFORE any rendering occurs
            if (currentWorldColor != null) {
                // Debug the color being applied
                batch.setColor(currentWorldColor);
            }
            Rectangle expandedBounds = getExpandedViewBounds(viewBounds);
            List<Map.Entry<Vector2, Chunk>> sortedChunks = getSortedChunks();


            sortedChunks.sort(Comparator.comparingDouble(entry -> entry.getKey().y));
            // === RENDER PASS 1: Ground and Terrain ===
            renderTerrainLayer(batch, sortedChunks, expandedBounds);

            if (blockManager != null) {
                blockManager.render(batch, worldData.getWorldTimeInMinutes());
            }

            // === RENDER PASS 2: Object Bases and Low Objects ===
            renderLowObjects(batch, expandedBounds);

            itemEntityManager.render(batch);

            // === RENDER PASS 3: Characters and Mid-Layer Objects ===
            renderMidLayer(batch, player, expandedBounds);

            // === RENDER PASS 4: High Objects and Tree Tops ===

            // === RENDER PASS 5: Effects and Overlays ===
            //            weatherSystem.render(batch, gameScreen.getCamera());

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
        } finally {      // IMPORTANT: Always restore the original color when done
            batch.setColor(originalColor);
        }
    }

    public void render(SpriteBatch batch, Rectangle viewBounds, Player player, GameScreen gameScreen) {
        if (chunks.isEmpty()) {
            GameLogger.error("No chunks available for rendering!");
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

            sortedChunks.sort(Comparator.comparingDouble(entry -> entry.getKey().y));

            renderTerrainLayer(batch, sortedChunks, expandedBounds);

            if (blockManager != null) {
                blockManager.render(batch, worldData.getWorldTimeInMinutes());
            }

            itemEntityManager.render(batch);
            // **Render Objects Behind Player**
            renderLowObjects(batch, expandedBounds);

            // **Render Wild Pokémon Behind Player**
            renderWildPokemon(batch);

            // **Render Bottom Part of Tall Grass Over Player**
//            renderTallGrassOverPlayer(batch, playerTileX, playerTileY);

            // **Render Player and Mid-Layer Objects**
            renderMidLayer(batch, player, expandedBounds);

            // **Render Water Effects (e.g., puddle splashes)**
            if (waterEffects != null && waterEffects.isInitialized()) {
                waterEffects.update(Gdx.graphics.getDeltaTime());
                waterEffects.render(batch, player, this);
            }

            // **Render High Objects (Trees, etc.)**
            renderHighObjects(batch, expandedBounds);

            // **Render Weather Effects, Water Effects, etc.**
            if (weatherSystem != null && gameScreen != null) {
                weatherSystem.render(batch, gameScreen.getCamera());
            }

            if (weatherAudioSystem != null) {
                weatherAudioSystem.renderLightningEffect(batch, viewBounds.width, viewBounds.height);
            }

        } finally {
            batch.setColor(originalColor);
        }
    }

    public void updateLightLevels() {
        lightLevelMap.clear();
        // Only recalc lighting during night to reduce overhead.
        float hour = DayNightCycle.getHourOfDay(worldData.getWorldTimeInMinutes());
        if (DayNightCycle.getTimePeriod(hour) != DayNightCycle.TimePeriod.NIGHT) return;
        for (Chunk chunk : chunks.values()) {
            // Iterate only over chunks that are visible or recently accessed.
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

    public void migrateBlocksToChunks() {
        if (worldData == null || worldData.getBlockData() == null) return;

        try {
            BlockSaveData blockData = worldData.getBlockData();
            Map<String, List<BlockSaveData.BlockData>> oldBlocks = blockData.getPlacedBlocks();

            for (Map.Entry<String, List<BlockSaveData.BlockData>> entry : oldBlocks.entrySet()) {
                for (BlockSaveData.BlockData blockDataItem : entry.getValue()) {
                    try {
                        // Get chunk for this block
                        int chunkX = Math.floorDiv(blockDataItem.x, CHUNK_SIZE);
                        int chunkY = Math.floorDiv(blockDataItem.y, CHUNK_SIZE);
                        Vector2 chunkPos = new Vector2(chunkX, chunkY);

                        // Get or create chunk
                        Chunk chunk = chunks.get(chunkPos);
                        if (chunk == null) continue;
                        PlaceableBlock.BlockType type = PlaceableBlock.BlockType.fromId(blockDataItem.type);
                        if (type == null) {
                            GameLogger.error("Failed to find block type: " + blockDataItem.type);
                            continue;
                        }
                        Vector2 pos = new Vector2(blockDataItem.x, blockDataItem.y);

                        // Create the PlaceableBlock instance without texture
                        PlaceableBlock block = new PlaceableBlock(type, pos, null, blockDataItem.isFlipped);

                        // Set isChestOpen for chests
                        if (type == PlaceableBlock.BlockType.CHEST) {
                            block.setChestOpen(blockDataItem.isChestOpen);
                        }

                        // Now get the texture using the new getBlockFrame method
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

            // Clear old block data
            worldData.setBlockData(null);

            // Save all modified chunks
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
        // If the number of chunks is unchanged, return our cached list.
        int currentCount = chunks.size();
        if (cachedSortedChunks != null && currentCount == cachedChunkCount) {
            return cachedSortedChunks;
        }
        // Otherwise, re-create and sort the list.
        cachedSortedChunks = new ArrayList<>(chunks.entrySet());
        cachedSortedChunks.sort(Comparator.comparingDouble(entry -> entry.getKey().y));
        cachedChunkCount = currentCount;
        return cachedSortedChunks;
    }

    private void renderTerrainLayer(SpriteBatch batch,
                                    List<Map.Entry<Vector2, Chunk>> sortedChunks,
                                    Rectangle expandedBounds) {
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
        return new Rectangle(
            viewBounds.x - buffer,
            viewBounds.y - buffer,
            viewBounds.width + (buffer * 2),
            viewBounds.height + (buffer * 2)
        );
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

        // Sort tree tops by Y position
        treeTopsToRender.sort(Comparator.comparingDouble(WorldObject::getPixelY));

        // Render tree tops
        for (WorldObject tree : treeTopsToRender) {
            objectManager.renderTreeTop(batch, tree, this);
        }
    }

    private boolean isTreeObject(WorldObject obj) {
        return obj.getType() == WorldObject.ObjectType.TREE_0 ||
            obj.getType() == WorldObject.ObjectType.TREE_1 ||
            obj.getType() == WorldObject.ObjectType.SNOW_TREE ||
            obj.getType() == WorldObject.ObjectType.HAUNTED_TREE ||
            obj.getType() == WorldObject.ObjectType.RAIN_TREE || obj.getType() == WorldObject.ObjectType.RUINS_TREE || obj.getType() == WorldObject.ObjectType.APRICORN_TREE;
    }

    private void renderMidLayer(SpriteBatch batch, Player player, Rectangle expandedBounds) {
        List<ObjectWithYPosition> behindPlayerQueue = new ArrayList<>();
        List<ObjectWithYPosition> frontPlayerQueue = new ArrayList<>();

        Color originalColor = batch.getColor().cpy();
        // First pass: Separate objects into behind and front queues
        for (Map.Entry<Vector2, Chunk> entry : chunks.entrySet()) {
            if (isChunkVisible(entry.getKey(), expandedBounds)) {
                List<WorldObject> chunkObjects = objectManager.getObjectsForChunk(entry.getKey());
                for (WorldObject obj : chunkObjects) {
                    if (isTreeObject(obj)) {
                        // Tree bases always go behind player
                        behindPlayerQueue.add(new ObjectWithYPosition(obj.getPixelY(), obj, RenderType.TREE_BASE));
                        // Tree tops always go in front
                        frontPlayerQueue.add(new ObjectWithYPosition(
                            obj.getPixelY() + World.TILE_SIZE * 2,
                            obj,
                            RenderType.TREE_TOP
                        ));
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

        // 2. Render the player
        player.render(batch);
        batch.setColor(originalColor);

        for (ObjectWithYPosition item : frontPlayerQueue) {
            if (item.renderType == RenderType.TREE_TOP) {
                objectManager.renderTreeTop(batch, (WorldObject) item.object, this);
            }
        }
        batch.setColor(originalColor);
    }

    private Map<BiomeRenderer.Direction, Biome> getNeighboringBiomes(Vector2 chunkPos) {
        Map<BiomeRenderer.Direction, Biome> neighbors = new EnumMap<>(BiomeRenderer.Direction.class);

        for (BiomeRenderer.Direction dir : BiomeRenderer.Direction.values()) {
            Vector2 neighborPos = new Vector2(
                chunkPos.x + (dir == BiomeRenderer.Direction.EAST ? 1 : dir == BiomeRenderer.Direction.WEST ? -1 : 0),
                chunkPos.y + (dir == BiomeRenderer.Direction.NORTH ? 1 : dir == BiomeRenderer.Direction.SOUTH ? -1 : 0)
            );

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

        Rectangle chunkRect = new Rectangle(chunkWorldX, chunkWorldY, chunkSize, chunkSize);

        return viewBounds.overlaps(chunkRect);
    }

    public WorldObject.WorldObjectManager getObjectManager() {
        return objectManager;
    }

    public boolean isPokemonAt(int tileX, int tileY) {
        float pixelX = tileX * TILE_SIZE;
        float pixelY = tileY * TILE_SIZE;

        // Create a collision box for the tile
        Rectangle tileBox = new Rectangle(
            pixelX,
            pixelY,
            TILE_SIZE,
            TILE_SIZE
        );
        Collection<WildPokemon> nearbyPokemon = pokemonSpawnManager.getPokemonInRange(
            pixelX + ((float) TILE_SIZE / 2),
            pixelY + ((float) TILE_SIZE / 2),
            TILE_SIZE * 2
        );

        for (WildPokemon pokemon : nearbyPokemon) {
            if (pokemon.getBoundingBox().overlaps(tileBox)) {
                return true;
            }
        }
        return false;
    }

    public void loadChunksAroundPositionSynchronously(Vector2 tilePosition, int radius) {
        // Now tilePosition is already in tile coordinates.
        int chunkX = Math.floorDiv((int) tilePosition.x, CHUNK_SIZE);
        int chunkY = Math.floorDiv((int) tilePosition.y, CHUNK_SIZE);

        GameLogger.info("Loading chunks around tile position " + tilePosition + " with radius " + radius);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                Vector2 chunkPos = new Vector2(chunkX + dx, chunkY + dy);
                if (!chunks.containsKey(chunkPos)) {
                    try {
                        Chunk chunk = loadOrGenerateChunk(chunkPos);
                        if (chunk != null) {
                            chunks.put(chunkPos, chunk);
                            GameLogger.info("Loaded chunk at " + chunkPos);
                        } else {
                            GameLogger.error("Failed to load/generate chunk at " + chunkPos);
                        }
                    } catch (Exception e) {
                        GameLogger.error("Error loading chunk at " + chunkPos + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    public boolean isPassable(int worldX, int worldY) {
        if (!isPositionLoaded(worldX, worldY)) {
            return false;
        }

        try {
            // Convert pixel coordinates to chunk coordinates
            int chunkX = Math.floorDiv(worldX, Chunk.CHUNK_SIZE);
            int chunkY = Math.floorDiv(worldY, Chunk.CHUNK_SIZE);
            Vector2 chunkPos = new Vector2(chunkX, chunkY);

            Chunk chunk = chunks.get(chunkPos);
            if (chunk == null) return false;

            int localX = Math.floorMod(worldX, Chunk.CHUNK_SIZE);
            int localY = Math.floorMod(worldY, Chunk.CHUNK_SIZE);

            String currentDirection = GameContext.get().getPlayer() != null ? GameContext.get().getPlayer().getDirection() : "down";

            // Basic tile passability
            if (!chunk.isPassable(localX, localY)) {
                handleCollision(currentDirection);
                return false;
            }
            // Check block collision
            if (blockManager != null && blockManager.hasCollisionAt(worldX, worldY)) {
                return false;
            }

            // Calculate pixel-based collision box
            Rectangle movementBounds = new Rectangle(
                worldX * TILE_SIZE,  // Now using actual pixel position
                worldY * TILE_SIZE,
                TILE_SIZE * 0.5f,    // Half tile collision size
                TILE_SIZE * 0.5f
            );

            // Check collisions with objects and Pokemon
            return !checkObjectCollision(movementBounds, currentDirection) &&
                !checkPokemonCollision(worldX, worldY, currentDirection);

        } catch (Exception e) {
            GameLogger.error("Error checking passability: " + e.getMessage());
            return false;
        }
    }

    public GameClient getGameClient() {
        if (GameContext.get().getGameClient() == null) {
            throw new IllegalStateException("GameClient is null - World not properly initialized");
        }
        return GameContext.get().getGameClient();
    }

    private boolean isPositionLoaded(int worldX, int worldY) {
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
        List<WorldObject> nearbyObjects = objectManager.getObjectsNearPosition(
            movementBounds.x + movementBounds.width / 2,
            movementBounds.y + movementBounds.height / 2
        );

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
                // Set player direction and stop moving
                GameContext.get().getPlayer().setDirection(direction);
                GameContext.get().getPlayer().setMoving(false);
            }
            return true;
        }
        return false;
    }

    private void checkPlayerInteractions(Vector2 playerPosition) {
        // Convert player position from tile coordinates to pixel coordinates
        float playerPixelX = playerPosition.x * TILE_SIZE;
        float playerPixelY = playerPosition.y * TILE_SIZE;

        nearestPokeball = null;
        float closestDistance = Float.MAX_VALUE;

        // Check in current chunk and adjacent chunks
        int chunkX = (int) Math.floor(playerPixelX / (Chunk.CHUNK_SIZE * TILE_SIZE));
        int chunkY = (int) Math.floor(playerPixelY / (Chunk.CHUNK_SIZE * TILE_SIZE));

        // Check current and surrounding chunks
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                Vector2 chunkPos = new Vector2(chunkX + dx, chunkY + dy);
                List<WorldObject> objects = objectManager.getObjectsForChunk(chunkPos);

                if (objects != null) {
                    for (WorldObject obj : objects) {
                        if (obj.getType() == WorldObject.ObjectType.POKEBALL) {
                            // Calculate distance using pixel coordinates
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

            // Create/update WorldData
            if (worldData == null) {
                worldData = new WorldData(name);
            }

            // Configure world settings
            WorldData.WorldConfig config = new WorldData.WorldConfig(seed);
            worldData.setConfig(config);
            worldData.setWorldTimeInMinutes(worldTimeInMinutes);
            worldData.setDayLength(dayLength);

            // Update core properties
            this.worldSeed = seed;
            this.biomeManager = new BiomeManager(seed);

            // Initialize managers if needed
            if (blockManager == null) blockManager = new BlockManager(this);
            if (objectManager == null) objectManager = new WorldObject.WorldObjectManager(worldSeed);
            if (pokemonSpawnManager == null)
                pokemonSpawnManager = new PokemonSpawnManager(TextureManager.pokemonoverworld);

            // Clear any existing chunks to prevent singleplayer data persistence
            chunks.clear();

            GameLogger.info("Multiplayer world initialization complete - " +
                "Time: " + worldTimeInMinutes +
                " Day Length: " + dayLength +
                " Seed: " + seed);

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
        // Calculate world position in pixels
        float worldX = playerPosition.x * TILE_SIZE;
        float worldY = playerPosition.y * TILE_SIZE;

        // Get current biome and calculate temperature
        currentBiomeTransition = biomeManager.getBiomeAt(worldX, worldY);
        float temperature = calculateTemperature();


        // Update weather systems
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

    public Chunk getChunkAtPosition(float x, float y) {
        int chunkX = Math.floorDiv((int) x, Chunk.CHUNK_SIZE);
        int chunkY = Math.floorDiv((int) y, Chunk.CHUNK_SIZE);
        return chunks.get(new Vector2(chunkX, chunkY));
    }

    public WorldObject getNearestPokeball() {
        return nearestPokeball;
    }

    private enum RenderType {
        TREE_BASE,
        TREE_TOP,
        REGULAR_OBJECT,
        PLAYER
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
