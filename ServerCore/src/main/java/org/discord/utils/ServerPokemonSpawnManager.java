package org.discord.utils;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.data.PokemonData;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.DayNightCycle;
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

    // Movement update accumulator
    private float movementUpdateTimer = 0f;
    // Check for new spawns every 5 seconds.
    private static final float SPAWN_INTERVAL = 5f;
    // Maximum wild Pokémon per chunk.
    private static final int MAX_POKEMON_PER_CHUNK = 5;
    // These constants must match those used in your World class.
    private static final int TILE_SIZE = 32;
    // Assume Chunk.CHUNK_SIZE is defined in the Chunk class.

    private final String worldName;
    private float spawnTimer = 0f;
    // Map of active wild Pokémon keyed by their UUID.
    private final Map<UUID, WildPokemon> activePokemon = new HashMap<>();
    private final Random random = new Random();

    /**
     * Constructs a server spawn manager for the given world.
     *
     * @param worldName The name (ID) of the world (e.g. "multiplayer_world").
     */
    public ServerPokemonSpawnManager(String worldName) {
        this.worldName = worldName;
        initializePokemonSpawns();
    }

    /**
     * Called periodically by the server update loop.
     *
     * @param delta Elapsed time in seconds.
     */
    public void update(float delta) {
        spawnTimer += delta;
        if (spawnTimer >= SPAWN_INTERVAL) {
            spawnTimer = 0f;
            trySpawnPokemon();
            removeExpiredPokemon();
        }
        movementUpdateTimer += delta;

        // If it's time to check for movement updates
        if (movementUpdateTimer >= MOVEMENT_UPDATE_INTERVAL) {
            movementUpdateTimer = 0f;
            checkForMovementUpdates();
        }
    }

    private void checkForMovementUpdates() {
        List<NetworkProtocol.PokemonUpdate> updates = new ArrayList<>();

        for (WildPokemon pokemon : activePokemon.values()) {
            Vector2 lastPos = lastSentPositions.getOrDefault(pokemon.getUuid(), new Vector2(Float.MAX_VALUE, Float.MAX_VALUE));

            // Check if the Pokemon has moved enough to warrant an update
            float distance = Vector2.dst(lastPos.x, lastPos.y, pokemon.getX(), pokemon.getY());
            boolean directionChanged = !pokemon.getDirection().equals(syncedPokemonData
                .getOrDefault(pokemon.getUuid(), new PokemonSpawnManager.NetworkSyncData()).direction);
            boolean movingChanged = pokemon.isMoving() != syncedPokemonData
                .getOrDefault(pokemon.getUuid(), new PokemonSpawnManager.NetworkSyncData()).isMoving;

            // Send update if position changed significantly or direction/moving state changed
            if (distance > MOVEMENT_THRESHOLD || directionChanged || movingChanged) {
                NetworkProtocol.PokemonUpdate update = createPokemonUpdate(pokemon);
                updates.add(update);

                // Update the last sent position and state
                lastSentPositions.put(pokemon.getUuid(), new Vector2(pokemon.getX(), pokemon.getY()));

                // Update the synced data
                PokemonSpawnManager.NetworkSyncData syncData = syncedPokemonData.computeIfAbsent(pokemon.getUuid(), k -> new PokemonSpawnManager.NetworkSyncData());
                syncData.direction = pokemon.getDirection();
                syncData.isMoving = pokemon.isMoving();
            }
        }

        // If there are updates to send, send them as a batch
        if (!updates.isEmpty()) {
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

        // Use batch update to reduce network overhead
        NetworkProtocol.PokemonBatchUpdate batchUpdate = new NetworkProtocol.PokemonBatchUpdate();
        batchUpdate.updates = updates;

        // Broadcast to all connected clients
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
            // Broadcast to all connected clients.
            ServerGameContext.get().getGameServer().getNetworkServer().sendToAllTCP(batchUpdate);
        }
    }


    private void trySpawnPokemon() {
        // (A) Grab the loaded chunks from the world manager
        Map<Vector2, Chunk> loadedChunks =
            ServerGameContext.get().getWorldManager().getLoadedChunks(worldName);

        if (loadedChunks == null || loadedChunks.isEmpty()) {
            GameLogger.error("No loaded chunks for world " + worldName + "; cannot spawn Pokémon.");
            return;
        }

        // (B) Find which chunks are actually occupied by players
        Set<Vector2> playerChunks =
            ServerGameContext.get().getGameServer().getPlayerOccupiedChunks();

        if (playerChunks.isEmpty()) {
            return;
        }

        // (C) Try to spawn in each chunk that has a player
        for (Vector2 chunkPos : playerChunks) {
            Chunk chunk = loadedChunks.get(chunkPos);
            if (chunk == null) {
                // might not be loaded yet
                continue;
            }

            int count = getPokemonCountInChunk(chunkPos);

            float chance = random.nextFloat();

            if (count < MAX_POKEMON_PER_CHUNK && chance < 0.4f) {
                spawnPokemonInChunk(chunkPos, chunk);
            }
        }
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
    private void spawnPokemonInChunk(Vector2 chunkPos, Chunk chunk) {
        // pick random local tile
        int localX = random.nextInt(Chunk.CHUNK_SIZE);
        int localY = random.nextInt(Chunk.CHUNK_SIZE);

        boolean passable = chunk.isPassable(localX, localY);

        // If the tile isn't passable, we skip
        if (!passable) {
            GameLogger.info("Spawn location not passable in chunk " + chunkPos);
            return;
        }

        try {
            // convert local tile coords to pixel coords
            int worldTileX = (int)(chunkPos.x * Chunk.CHUNK_SIZE + localX);
            int worldTileY = (int)(chunkPos.y * Chunk.CHUNK_SIZE + localY);
            float pixelX = worldTileX * TILE_SIZE;
            float pixelY = worldTileY * TILE_SIZE;

            // ensure we have a non‐null biome
            Biome biome = chunk.getBiome();
            if (biome == null) {
                // fallback:
                GameLogger.error("Null biome at chunk " + chunkPos + ", defaulting to PLAINS biome.");
            }

            // pick a random Pokémon for this biome
            String pokemonName = selectRandomPokemonForBiome(biome);
            int level = calculatePokemonLevel(pixelX, pixelY);
            WildPokemon pokemon = new WildPokemon(
                pokemonName,
                level,
                (int) pixelX,
                (int) pixelY,
                true // noTexture mode on the server
            );


            // store in active map
            activePokemon.put(pokemon.getUuid(), pokemon);

            // build a spawn message
            NetworkProtocol.WildPokemonSpawn spawnMsg = new NetworkProtocol.WildPokemonSpawn();
            spawnMsg.uuid = pokemon.getUuid();
            spawnMsg.x = pokemon.getX();
            spawnMsg.y = pokemon.getY();
            spawnMsg.timestamp = System.currentTimeMillis();
            spawnMsg.data = createPokemonData(pokemon); // might throw if dictionary is missing?

            // broadcast to all clients
            ServerGameContext.get()
                .getGameServer()
                .getNetworkServer()
                .sendToAllTCP(spawnMsg);


        } catch (Exception ex) {
            // Catch anything that might silently prevent the spawn
            GameLogger.error("spawnPokemonInChunk: Unexpected error => " + ex.getMessage());
            ex.printStackTrace();
        }
    }



    private static void initializePokemonSpawns() {
        // PLAINS biome
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

        // FOREST biome
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

        // SNOW biome
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

        // DESERT biome
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

        // HAUNTED biome
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

        // RAIN FOREST biome
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

        // BIG MOUNTAINS biome
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

        // RUINS biome
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
        // extra check
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
        // Copy moves if available.
        // (Assume WildPokemon.getMoves() returns a list of move objects.)
        // If not available on the server, leave the move list empty.
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
