package org.discord.utils;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.data.PokemonData;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.DayNightCycle;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.PokemonSpawnManager;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.PokemonLevelCalculator;
import org.discord.context.ServerGameContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static io.github.pokemeetup.CreatureCaptureGame.MULTIPLAYER_WORLD_NAME;

/**
 * This class is responsible for generating and updating wild Pokémon spawns on the server.
 * Instead of using textures (which are client–side), it creates WildPokemon instances with a null sprite.
 * It obtains the loaded chunks from ServerWorldManager.getLoadedChunks(worldName) and then,
 * if a chunk has fewer than MAX_POKEMON_PER_CHUNK, it attempts to spawn a new Pokémon.
 *
 * When a spawn occurs a serializable PokemonData object is built and a WildPokemonSpawn network message
 * is broadcast so that clients update their local world.
 */
public class ServerPokemonSpawnManager {
    private static final float MOVEMENT_UPDATE_INTERVAL = 0.1f; // Send updates 10 times per second maximum
    private static final float MOVEMENT_THRESHOLD = 1.0f;
    private final Map<UUID, Vector2> lastSentPositions = new ConcurrentHashMap<>();
    private float movementUpdateTimer = 0f;
    private static final float SPAWN_INTERVAL = 3.0f; // Spawn every 3 seconds (realistic for open world)
    private static final int MAX_POKEMON_PER_CHUNK = 4; // 4 Pokemon per chunk (balanced for open world)
    private static final int TILE_SIZE = 32;

    private final String worldName;
    private float spawnTimer = 0f;
    private final Map<UUID, WildPokemon> activePokemon = new HashMap<>();
    private final Random random = new Random();
    private final ServerWorldAdapter serverWorld;

    /**
     * Constructs a server spawn manager for the given world.
     *
     * @param worldName The name (ID) of the world (e.g. "multiplayer_world").
     */
    public ServerPokemonSpawnManager(String worldName) {
        this.worldName = worldName;
        this.serverWorld = new ServerWorldAdapter(worldName);
        initializePokemonSpawns();
    }

    /**
     * Called periodically by the server update loop.
     *
     * @param delta Elapsed time in seconds.
     */
    public void update(float delta) {
        // Update all active Pokemon (AI, movement, animations)
        // Pass null for world - the Pokemon AI uses the ServerWorldAdapter (serverPassabilityChecker)
        // for tile passability checks instead of requiring a full World instance

        // Get all player positions from server for AI behaviors
        Map<String, Vector2> playerPositions = ServerGameContext.get().getGameServer().getAllPlayerPositions();

        int updatedCount = 0;
        for (WildPokemon pokemon : activePokemon.values()) {
            if (pokemon != null) {
                try {
                    // Update nearest player position for AI behaviors (flee, approach, etc.)
                    Vector2 nearestPlayer = findNearestPlayer(pokemon, playerPositions);
                    if (nearestPlayer != null && pokemon.getAi() != null) {
                        // Use reflection to call setNearestPlayerPosition (AI is Object to avoid circular deps)
                        try {
                            java.lang.reflect.Method method = pokemon.getAi().getClass()
                                .getMethod("setNearestPlayerPosition", Vector2.class);
                            method.invoke(pokemon.getAi(), nearestPlayer);
                        } catch (Exception aiEx) {
                            GameLogger.error("Failed to update AI player position: " + aiEx.getMessage());
                        }
                    }

                    pokemon.update(delta, null);
                    updatedCount++;
                } catch (Exception e) {
                    GameLogger.error("Error updating Pokemon " + pokemon.getName() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        if (updatedCount > 0 && System.currentTimeMillis() % 10000 < 100) { // Log every 10 seconds
            GameLogger.info("Updated " + updatedCount + " active Pokemon AI with " + playerPositions.size() + " player positions");
        }

        spawnTimer += delta;
        if (spawnTimer >= SPAWN_INTERVAL) {
            spawnTimer = 0f;
            trySpawnPokemon();
            removeExpiredPokemon();
        }
        movementUpdateTimer += delta;
        if (movementUpdateTimer >= MOVEMENT_UPDATE_INTERVAL) {
            movementUpdateTimer = 0f;
            checkForMovementUpdates();
        }
    }

    private void checkForMovementUpdates() {
        List<NetworkProtocol.PokemonUpdate> updates = new ArrayList<>();

        for (WildPokemon pokemon : activePokemon.values()) {
            // Get last position, defaulting to current position if not set
            Vector2 lastPos = lastSentPositions.get(pokemon.getUuid());
            if (lastPos == null) {
                // First update for this Pokemon, initialize position
                lastPos = new Vector2(pokemon.getX(), pokemon.getY());
                lastSentPositions.put(pokemon.getUuid(), lastPos);
            }

            float distance = Vector2.dst(lastPos.x, lastPos.y, pokemon.getX(), pokemon.getY());

            PokemonSpawnManager.NetworkSyncData syncData = syncedPokemonData.get(pokemon.getUuid());
            boolean directionChanged = syncData == null || !pokemon.getDirection().equals(syncData.direction);
            boolean movingChanged = syncData == null || pokemon.isMoving() != syncData.isMoving;

            if (distance > MOVEMENT_THRESHOLD || directionChanged || movingChanged) {
                GameLogger.info("Pokemon " + pokemon.getName() + " moved " + distance + " pixels, broadcasting update");
                NetworkProtocol.PokemonUpdate update = createPokemonUpdate(pokemon);
                updates.add(update);
                lastSentPositions.put(pokemon.getUuid(), new Vector2(pokemon.getX(), pokemon.getY()));

                PokemonSpawnManager.NetworkSyncData updatedSyncData = syncedPokemonData.computeIfAbsent(pokemon.getUuid(), k -> new PokemonSpawnManager.NetworkSyncData());
                updatedSyncData.direction = pokemon.getDirection();
                updatedSyncData.isMoving = pokemon.isMoving();
            }
        }
        if (!updates.isEmpty()) {
            GameLogger.info("Broadcasting " + updates.size() + " Pokemon movement updates");
            broadcastPokemonUpdates(updates);
        }
    }  private NetworkProtocol.PokemonUpdate createPokemonUpdate(WildPokemon pokemon) {
        NetworkProtocol.PokemonUpdate update = new NetworkProtocol.PokemonUpdate();
        update.uuid = pokemon.getUuid();
        update.x = pokemon.getX();
        update.y = pokemon.getY();
        update.direction = pokemon.getDirection();
        update.isMoving = pokemon.isMoving();
        update.level = pokemon.getLevel();
        update.timestamp = System.currentTimeMillis();
        return update;
    }// Add this inner class at the bottom of ServerPokemonSpawnManager
    private static class NetworkSyncData {
        Vector2 targetPosition;
        String direction;
        boolean isMoving;
        long lastUpdateTime;

        NetworkSyncData() {
            this.lastUpdateTime = System.currentTimeMillis();
            this.direction = "down";
            this.isMoving = false;
        }
    }
    private final Map<UUID, PokemonSpawnManager.NetworkSyncData> syncedPokemonData = new ConcurrentHashMap<>();
    /**
     * Broadcast a batch of Pokemon updates to all clients
     */
    private void broadcastPokemonUpdates(List<NetworkProtocol.PokemonUpdate> updates) {
        if (updates.isEmpty()) return;
        NetworkProtocol.PokemonBatchUpdate batchUpdate = new NetworkProtocol.PokemonBatchUpdate();
        batchUpdate.updates = updates;
        ServerGameContext.get().getGameServer().getNetworkServer().sendToAllTCP(batchUpdate);
    }
    /**
     * Iterates over all active wild Pokémon and builds a batch update message,
     * then broadcasts it to all clients.
     */
    public void broadcastPokemonUpdates() {
        List<NetworkProtocol.PokemonUpdate> updates = new ArrayList<>();
        for (WildPokemon pokemon : activePokemon.values()) {
            NetworkProtocol.PokemonUpdate update = new NetworkProtocol.PokemonUpdate();
            update.uuid = pokemon.getUuid();
            update.x = pokemon.getX();
            update.y = pokemon.getY();
            update.direction = pokemon.getDirection();
            update.isMoving = pokemon.isMoving();
            update.level = pokemon.getLevel();
            update.timestamp = System.currentTimeMillis();
            updates.add(update);
        }
        if (!updates.isEmpty()) {
            NetworkProtocol.PokemonBatchUpdate batchUpdate = new NetworkProtocol.PokemonBatchUpdate();
            batchUpdate.updates = updates;
            ServerGameContext.get().getGameServer().getNetworkServer().sendToAllTCP(batchUpdate);
        }
    }


    private void trySpawnPokemon() {
        Map<Vector2, Chunk> loadedChunks =
            ServerGameContext.get().getWorldManager().getLoadedChunks(worldName);

        if (loadedChunks == null || loadedChunks.isEmpty()) {
            return; // Don't spam logs
        }
        Set<Vector2> playerChunks =
            ServerGameContext.get().getGameServer().getPlayerOccupiedChunks();

        if (playerChunks.isEmpty()) {
            return;
        }

        int totalPokemon = activePokemon.size();
        GameLogger.info("Spawn tick: " + totalPokemon + " active Pokemon, checking " + playerChunks.size() + " player chunks");

        // Spawn in multiple chunks per tick for balanced world population
        int spawnAttempts = 0;
        int maxSpawnAttemptsPerTick = 2; // Try to spawn in up to 2 chunks per tick (balanced)

        for (Vector2 chunkPos : playerChunks) {
            if (spawnAttempts >= maxSpawnAttemptsPerTick) {
                break; // Limit spawn attempts per tick to avoid lag
            }
            Chunk chunk = loadedChunks.get(chunkPos);
            if (chunk == null) {
                GameLogger.info("Chunk " + chunkPos + " not loaded, skipping");
                continue;
            }

            int count = getPokemonCountInChunk(chunkPos);

            if (count < MAX_POKEMON_PER_CHUNK) {
                // Aggressive spawning - always attempt if below max
                GameLogger.info("Attempting to spawn Pokemon in chunk " + chunkPos + " (current count: " + count + "/" + MAX_POKEMON_PER_CHUNK + ")");
                spawnPokemonInChunk(chunkPos, chunk);
                spawnAttempts++;
            } else {
                GameLogger.info("Chunk " + chunkPos + " is full (" + count + "/" + MAX_POKEMON_PER_CHUNK + " Pokemon)");
            }
        }
        GameLogger.info("Spawn tick complete: attempted " + spawnAttempts + " spawns, total active: " + activePokemon.size());
    }

    private int getPokemonCountInChunk(Vector2 chunkPos) {
        int count = 0;
        for (WildPokemon pokemon : activePokemon.values()) {
            Vector2 pos = new Vector2(pokemon.getX(), pokemon.getY());
            Vector2 computedChunk = getChunkPosition(pos);
            if (computedChunk.equals(chunkPos)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Converts a pixel position into chunk coordinates.
     *
     * @param pixelPos The position in pixels.
     * @return The chunk coordinates.
     */
    private Vector2 getChunkPosition(Vector2 pixelPos) {
        int chunkX = (int)Math.floor(pixelPos.x / (TILE_SIZE * Chunk.CHUNK_SIZE));
        int chunkY = (int)Math.floor(pixelPos.y / (TILE_SIZE * Chunk.CHUNK_SIZE));
        return new Vector2(chunkX, chunkY);
    }

    /**
     * Finds the nearest player to a Pokemon from the current player positions.
     * This enables Pokemon to react to players on the server (flee, approach, etc.)
     */
    private Vector2 findNearestPlayer(WildPokemon pokemon, Map<String, Vector2> playerPositions) {
        if (playerPositions == null || playerPositions.isEmpty()) {
            return null;
        }

        Vector2 nearestPos = null;
        float nearestDist = Float.MAX_VALUE;

        for (Vector2 playerPos : playerPositions.values()) {
            float dist = Vector2.dst(pokemon.getX(), pokemon.getY(), playerPos.x, playerPos.y);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearestPos = playerPos;
            }
        }

        return nearestPos;
    }

    /**
     * Determines if a Pokemon species should spawn as a pack.
     * Pack species have a 30% chance to spawn in groups of 2-4.
     */
    private boolean shouldSpawnAsPack(String species) {
        String lowerSpecies = species.toLowerCase();
        // Common pack species
        boolean isPackSpecies = lowerSpecies.equals("rattata") || lowerSpecies.equals("pidgey") ||
                               lowerSpecies.equals("zubat") || lowerSpecies.equals("caterpie") ||
                               lowerSpecies.equals("weedle") || lowerSpecies.equals("sentret") ||
                               lowerSpecies.equals("hoppip") || lowerSpecies.equals("poochyena") ||
                               lowerSpecies.equals("zigzagoon") || lowerSpecies.equals("spinarak");

        if (!isPackSpecies) {
            return false;
        }

        // 30% chance for pack spawn
        return random.nextFloat() < 0.3f;
    }
    private void spawnPokemonInChunk(Vector2 chunkPos, Chunk chunk) {
        // Try up to 15 times to find a passable location (increased for better spawn success rate)
        for (int attempt = 0; attempt < 15; attempt++) {
            int localX = random.nextInt(Chunk.CHUNK_SIZE);
            int localY = random.nextInt(Chunk.CHUNK_SIZE);

            boolean passable = chunk.isPassable(localX, localY);
            if (!passable) {
                continue; // Try again
            }

            try {
                int worldTileX = (int)(chunkPos.x * Chunk.CHUNK_SIZE + localX);
                int worldTileY = (int)(chunkPos.y * Chunk.CHUNK_SIZE + localY);
                float pixelX = worldTileX * TILE_SIZE;
                float pixelY = worldTileY * TILE_SIZE;
                Biome biome = chunk.getBiome();
                if (biome == null) {
                    GameLogger.error("Null biome at chunk " + chunkPos + ", cannot spawn Pokemon");
                    return;
                }
                String pokemonName = selectRandomPokemonForBiome(biome);
                int level = calculatePokemonLevel(pixelX, pixelY);

                // Check if this should spawn as a pack (30% chance for pack species)
                boolean shouldSpawnPack = shouldSpawnAsPack(pokemonName);
                int spawnCount = 1;

                if (shouldSpawnPack) {
                    // Spawn pack of 2-4 Pokemon
                    spawnCount = random.nextInt(3) + 2; // 2 to 4 Pokemon
                    GameLogger.info("Spawning pack of " + spawnCount + " " + pokemonName);
                }

                for (int i = 0; i < spawnCount; i++) {
                    // For pack members after first, offset position slightly
                    float spawnX = pixelX;
                    float spawnY = pixelY;
                    if (i > 0) {
                        // Offset by 1-2 tiles in random direction
                        int offsetTiles = random.nextInt(2) + 1;
                        float angle = random.nextFloat() * (float)(2 * Math.PI);
                        spawnX += (float)Math.cos(angle) * offsetTiles * TILE_SIZE;
                        spawnY += (float)Math.sin(angle) * offsetTiles * TILE_SIZE;

                        // Verify new position is passable
                        int testTileX = (int)(spawnX / TILE_SIZE);
                        int testTileY = (int)(spawnY / TILE_SIZE);
                        int testLocalX = testTileX - (int)(chunkPos.x * Chunk.CHUNK_SIZE);
                        int testLocalY = testTileY - (int)(chunkPos.y * Chunk.CHUNK_SIZE);

                        if (testLocalX < 0 || testLocalX >= Chunk.CHUNK_SIZE ||
                            testLocalY < 0 || testLocalY >= Chunk.CHUNK_SIZE ||
                            !chunk.isPassable(testLocalX, testLocalY)) {
                            continue; // Skip this pack member if position isn't passable
                        }
                    }

                    WildPokemon pokemon = new WildPokemon(
                        pokemonName,
                        level + random.nextInt(2), // Slight level variation in pack
                        (int) spawnX,
                        (int) spawnY,
                        true // noTexture mode on the server
                    );

                    // Initialize AI for server-side Pokemon
                    io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonAI ai =
                        new io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonAI(pokemon);
                    ai.setServerPassabilityChecker(serverWorld);
                    pokemon.setAi(ai);

                    activePokemon.put(pokemon.getUuid(), pokemon);

                    // Initialize last sent position to prevent Infinity distance calculation
                    lastSentPositions.put(pokemon.getUuid(), new Vector2(pokemon.getX(), pokemon.getY()));

                    NetworkProtocol.WildPokemonSpawn spawnMsg = new NetworkProtocol.WildPokemonSpawn();
                    spawnMsg.uuid = pokemon.getUuid();
                    spawnMsg.x = pokemon.getX();
                    spawnMsg.y = pokemon.getY();
                    spawnMsg.timestamp = System.currentTimeMillis();
                    spawnMsg.data = createPokemonData(pokemon);
                    ServerGameContext.get()
                        .getGameServer()
                        .getNetworkServer()
                        .sendToAllTCP(spawnMsg);

                    GameLogger.info("Successfully spawned " + pokemonName + " level " + level +
                        " in chunk " + chunkPos + " at (" + (int)(spawnX/TILE_SIZE) + "," + (int)(spawnY/TILE_SIZE) + ")" +
                        (shouldSpawnPack ? " (pack " + (i+1) + "/" + spawnCount + ")" : ""));
                }
                return; // Success!

            } catch (Exception ex) {
                GameLogger.error("spawnPokemonInChunk: Unexpected error => " + ex.getMessage());
                ex.printStackTrace();
                return;
            }
        }
        GameLogger.info("Failed to find passable location in chunk " + chunkPos + " after 5 attempts");
    }



    private static void initializePokemonSpawns() {
        Map<PokemonSpawnManager.TimeOfDay, String[]> plainsSpawns = new HashMap<>();
        plainsSpawns.put(PokemonSpawnManager.TimeOfDay.DAY, new String[]{
            "Rattata", "Pidgey", "Sentret", "Hoppip", "Sunkern",
            "Caterpie", "Weedle", "Oddish", "Bellsprout", "Zigzagoon", "Spinarak"
        });
        plainsSpawns.put(PokemonSpawnManager.TimeOfDay.NIGHT, new String[]{
            "Zubat", "Hoothoot", "Rattata", "Caterpie", "Weedle",
            "Hoppip", "Sunkern", "Spinarak", "Skitty"
        });
        POKEMON_SPAWNS.put(BiomeType.PLAINS, plainsSpawns);
        Map<PokemonSpawnManager.TimeOfDay, String[]> forestSpawns = new HashMap<>();
        forestSpawns.put(PokemonSpawnManager.TimeOfDay.DAY, new String[]{
            "Caterpie", "Weedle", "Oddish", "Bellsprout", "Treecko",
            "Shroomish", "Seedot", "Lotad", "Nincada", "Poochyena",
            "Hoppip", "Sunkern"
        });
        forestSpawns.put(PokemonSpawnManager.TimeOfDay.NIGHT, new String[]{
            "Hoothoot", "Caterpie", "Weedle", "Oddish", "Bellsprout",
            "Treecko", "Shroomish", "Seedot", "Lotad", "Poochyena",
            "Hoppip", "Nincada"
        });
        POKEMON_SPAWNS.put(BiomeType.FOREST, forestSpawns);
        Map<PokemonSpawnManager.TimeOfDay, String[]> snowSpawns = new HashMap<>();
        snowSpawns.put(PokemonSpawnManager.TimeOfDay.DAY, new String[]{
            "Swinub", "Snorunt", "Snover", "Spheal", "Cubchoo",
            "Sneasel", "Vanillite", "Snom"
        });
        snowSpawns.put(PokemonSpawnManager.TimeOfDay.NIGHT, new String[]{
            "Swinub", "Snorunt", "Snover", "Spheal", "Cubchoo",
            "Sneasel", "Vanillite", "Snom"
        });
        POKEMON_SPAWNS.put(BiomeType.SNOW, snowSpawns);
        Map<PokemonSpawnManager.TimeOfDay, String[]> desertSpawns = new HashMap<>();
        desertSpawns.put(PokemonSpawnManager.TimeOfDay.DAY, new String[]{
            "Sandshrew", "Trapinch", "Cacnea", "Sandile", "Diglett",
            "Vulpix", "Ekans", "Spinarak", "Poochyena"
        });
        desertSpawns.put(PokemonSpawnManager.TimeOfDay.NIGHT, new String[]{
            "Sandshrew", "Trapinch", "Cacnea", "Sandile", "Diglett",
            "Vulpix", "Ekans", "Zubat", "Spinarak"
        });
        POKEMON_SPAWNS.put(BiomeType.DESERT, desertSpawns);
        Map<PokemonSpawnManager.TimeOfDay, String[]> hauntedSpawns = new HashMap<>();
        hauntedSpawns.put(PokemonSpawnManager.TimeOfDay.DAY, new String[]{
            "Gastly", "Misdreavus", "Shuppet", "Duskull", "Sableye",
            "Litwick", "Murkrow", "Yamask"
        });
        hauntedSpawns.put(PokemonSpawnManager.TimeOfDay.NIGHT, new String[]{
            "Gastly", "Misdreavus", "Shuppet", "Duskull", "Sableye",
            "Litwick", "Murkrow", "Yamask"
        });
        POKEMON_SPAWNS.put(BiomeType.HAUNTED, hauntedSpawns);
        Map<PokemonSpawnManager.TimeOfDay, String[]> rainforestSpawns = new HashMap<>();
        rainforestSpawns.put(PokemonSpawnManager.TimeOfDay.DAY, new String[]{
            "Treecko", "Mudkip", "Torchic", "Lotad", "Seedot",
            "Shroomish", "Sunkern", "Hoppip", "Caterpie", "Weedle",
            "Nincada", "Poochyena"
        });
        rainforestSpawns.put(PokemonSpawnManager.TimeOfDay.NIGHT, new String[]{
            "Treecko", "Mudkip", "Torchic", "Lotad", "Seedot",
            "Shroomish", "Sunkern", "Hoppip", "Caterpie", "Weedle",
            "Nincada", "Poochyena"
        });
        POKEMON_SPAWNS.put(BiomeType.RAIN_FOREST, rainforestSpawns);
        Map<PokemonSpawnManager.TimeOfDay, String[]> mountainSpawns = new HashMap<>();
        mountainSpawns.put(PokemonSpawnManager.TimeOfDay.DAY, new String[]{
            "Geodude", "Machop", "Onix", "Rhyhorn", "Nosepass",
            "Larvitar", "Meditite", "Riolu", "Rockruff", "Swinub"
        });
        mountainSpawns.put(PokemonSpawnManager.TimeOfDay.NIGHT, new String[]{
            "Geodude", "Machop", "Onix", "Rhyhorn", "Nosepass",
            "Larvitar", "Meditite", "Riolu", "Rockruff", "Swinub"
        });
        POKEMON_SPAWNS.put(BiomeType.BIG_MOUNTAINS, mountainSpawns);
        Map<PokemonSpawnManager.TimeOfDay, String[]> ruinsSpawns = new HashMap<>();
        ruinsSpawns.put(PokemonSpawnManager.TimeOfDay.DAY, new String[]{
            "Zubat", "Geodude", "Kabuto", "Omanyte", "Aerodactyl",
            "Rattata", "Gastly", "Onix", "Abra", "Cubone"
        });
        ruinsSpawns.put(PokemonSpawnManager.TimeOfDay.NIGHT, new String[]{
            "Zubat", "Geodude", "Kabuto", "Omanyte", "Aerodactyl",
            "Rattata", "Gastly", "Onix", "Abra", "Cubone"
        });
        POKEMON_SPAWNS.put(BiomeType.RUINS, ruinsSpawns);
    }
    private String selectRandomPokemonForBiome(Biome biome) {
        if (biome == null) {
            return "Rattata";
        }
        BiomeType biomeType = biome.getType();
        Map<PokemonSpawnManager.TimeOfDay, String[]> spawnOptions = POKEMON_SPAWNS.get(biomeType);
        if (spawnOptions == null) {
            GameLogger.error("selectRandomPokemonForBiome: No spawn table for biome " + biomeType
                + ", defaulting to Rattata");
            return "Rattata";
        }

        double worldTimeInMinutes = ServerGameContext.get()
            .getWorldManager()
            .loadWorld(MULTIPLAYER_WORLD_NAME)
            .getWorldTimeInMinutes();
        float hourOfDay = DayNightCycle.getHourOfDay(worldTimeInMinutes);
        PokemonSpawnManager.TimeOfDay timeOfDay = (hourOfDay >= 6 && hourOfDay < 18)
            ? PokemonSpawnManager.TimeOfDay.DAY
            : PokemonSpawnManager.TimeOfDay.NIGHT;

        String[] options = spawnOptions.get(timeOfDay);
        if (options == null || options.length == 0) {
            return "Rattata";
        }
        int index = random.nextInt(options.length);
        return options[index];
    }


    private static final Map<BiomeType, Map<PokemonSpawnManager.TimeOfDay, String[]>> POKEMON_SPAWNS = new HashMap<>();

    private int calculatePokemonLevel(float pixelX, float pixelY) {
        return PokemonLevelCalculator.calculateLevel(pixelX, pixelY, TILE_SIZE);
    }
    private void removeExpiredPokemon() {
        activePokemon.entrySet().removeIf(entry -> {
            WildPokemon pokemon = entry.getValue();
            if (pokemon.isExpired()) {
                NetworkProtocol.WildPokemonDespawn despawnMsg = new NetworkProtocol.WildPokemonDespawn();
                despawnMsg.uuid = pokemon.getUuid();
                despawnMsg.timestamp = System.currentTimeMillis();
                ServerGameContext.get().getGameServer().getNetworkServer().sendToAllTCP(despawnMsg);
                GameLogger.info("Server despawned Pokémon with UUID: " + pokemon.getUuid());
                return true;
            }
            return false;
        });
    }

    /**
     * Creates a serializable PokemonData object from the given WildPokemon.
     * This replaces any client–only method that would otherwise return a sprite-based summary.
     *
     * @param pokemon The wild Pokémon.
     * @return A new PokemonData object with its properties.
     */
    private PokemonData createPokemonData(WildPokemon pokemon) {
        PokemonData data = new PokemonData();
        data.setName(pokemon.getName());
        data.setLevel(pokemon.getLevel());
        data.setPrimaryType(pokemon.getPrimaryType());
        data.setSecondaryType(pokemon.getSecondaryType());
        if (pokemon.getStats() != null) {
            PokemonData.Stats stats = new PokemonData.Stats(pokemon.getStats());
            data.setStats(stats);
        }
        return data;
    }

    /**
     * Returns a collection of all currently active wild Pokémon.
     *
     * @return The active wild Pokémon.
     */
    public Collection<WildPokemon> getActivePokemon() {
        return activePokemon.values();
    }
}
