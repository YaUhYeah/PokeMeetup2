package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.PokemonLevelCalculator;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static io.github.pokemeetup.system.gameplay.overworld.World.TILE_SIZE;

public class PokemonSpawnManager {
    public static final float POKEMON_DESPAWN_TIME = 300; // Increased from 120 to 300 seconds
    private static final float BASE_SPAWN_RATE = 0.25f;  // Base 30% chance per check
    private static final float SPAWN_CHECK_INTERVAL = 2.5f;
    private static final Map<BiomeType, Map<TimeOfDay, String[]>> POKEMON_SPAWNS = new HashMap<>();

    private static final float MIN_SPAWN_DISTANCE_PIXELS = 15 * World.TILE_SIZE;
    private static final float MAX_SPAWN_DISTANCE_PIXELS = 30 * World.TILE_SIZE;
    private static final int MAX_POKEMON_PER_CHUNK = 6;
    private static final float MIN_POKEMON_SPACING = World.TILE_SIZE * 2;
    private static final float PACK_SPAWN_CHANCE = 0.3f; // 30% chance that a spawn will be a pack
    private static final int MIN_PACK_SIZE = 2;
    private static final int MAX_PACK_SIZE = 5;
    private static final float PACK_SPACING = TILE_SIZE; // How far apart pack members appear
    private final TextureAtlas atlas;
    private final Random random;
    private final Map<Vector2, List<WildPokemon>> pokemonByChunk;
    private final Map<UUID, WildPokemon> pokemonById;
    private final Map<UUID, NetworkSyncData> syncedPokemon = new ConcurrentHashMap<>();
    private float spawnTimer = 0;

    public PokemonSpawnManager(TextureAtlas atlas) {
        this.atlas = atlas;
        this.random = new Random();
        this.pokemonByChunk = new ConcurrentHashMap<>();
        this.pokemonById = new ConcurrentHashMap<>();
        initializePokemonSpawns();

    }

    private void spawnPokemonPack(float centerPixelX, float centerPixelY, Vector2 chunkPos, int packSize) {
        GameLogger.info("Spawning pack of size " + packSize + " at (" + centerPixelX + ", " + centerPixelY + ")");
        // Spawn the central Pokémon first
        spawnPokemon(centerPixelX, centerPixelY, chunkPos);

        for (int i = 1; i < packSize; i++) {
            float offsetAngle = random.nextFloat() * MathUtils.PI2;
            float offsetDistance = MathUtils.random(PACK_SPACING * 0.5f, PACK_SPACING);
            float memberX = centerPixelX + MathUtils.cos(offsetAngle) * offsetDistance;
            float memberY = centerPixelY + MathUtils.sin(offsetAngle) * offsetDistance;
            if (!isValidSpawnPosition(memberX, memberY)) {
                GameLogger.error("Pack member spawn at (" + memberX + ", " + memberY + ") invalid, skipping");
                continue;
            }
            spawnPokemon(memberX, memberY, chunkPos);
            // (Inside spawnPokemon, each Pokémon will set its own lifespan.)
        }
    }


    public GameClient getGameClient() {
        return GameContext.get().getGameClient();
    }

    private void checkSpawns(Vector2 playerPos) {
        // Only attempt a spawn if the random chance passes.
        if (random.nextFloat() > BASE_SPAWN_RATE) {
            return;
        }

        Set<Vector2> loadedChunks = getLoadedChunksAroundPlayer(playerPos);
        if (loadedChunks.isEmpty()) {
            GameLogger.error("No loaded chunks found around player");
            return;
        }

        int attempts = 10;
        while (attempts > 0) {
            attempts--;

            // Calculate a potential spawn position (in pixels)
            float angle = random.nextFloat() * MathUtils.PI2;
            float distance = MathUtils.random(MIN_SPAWN_DISTANCE_PIXELS, MAX_SPAWN_DISTANCE_PIXELS);
            float spawnPixelX = playerPos.x * TILE_SIZE + MathUtils.cos(angle) * distance;
            float spawnPixelY = playerPos.y * TILE_SIZE + MathUtils.sin(angle) * distance;

            Vector2 chunkPos = getChunkPosition(spawnPixelX, spawnPixelY);
            if (!loadedChunks.contains(chunkPos)) {
                GameLogger.error("Chunk not loaded, skipping");
                continue;
            }

            // Check chunk capacity
            List<WildPokemon> chunkPokemon = pokemonByChunk.getOrDefault(chunkPos, new ArrayList<>());
            if (chunkPokemon.size() >= MAX_POKEMON_PER_CHUNK) {
                continue;
            }

            // Validate spawn position
            if (!isValidSpawnPosition(spawnPixelX, spawnPixelY)) {
                GameLogger.error("Invalid spawn position, retrying");
                continue;
            }

            // NEW: Decide whether to spawn a pack or a single Pokémon.
            if (random.nextFloat() < PACK_SPAWN_CHANCE) {
                int packSize = MathUtils.random(MIN_PACK_SIZE, MAX_PACK_SIZE);
                spawnPokemonPack(spawnPixelX, spawnPixelY, chunkPos, packSize);
            } else {
                spawnPokemon(spawnPixelX, spawnPixelY, chunkPos);
            }
            break; // A spawn has occurred
        }

        if (attempts == 0) {
            GameLogger.error("Failed to find valid spawn position after all attempts");
        }
    }

    private boolean isValidSpawnPosition(float pixelX, float pixelY) {
        // Convert to tile coordinates for passability check
        int tileX = (int) (pixelX / TILE_SIZE);
        int tileY = (int) (pixelY / TILE_SIZE);

        if (GameContext.get().getWorld() == null) {
            GameLogger.error("World reference is null in spawn validation");
            return false;
        }

        // Check if tile is passable
        if (!GameContext.get().getWorld().isPassable(tileX, tileY)) {
            return false;
        }

        // Check distance from other Pokemon
        Collection<WildPokemon> nearby = getPokemonInRange(pixelX, pixelY, MIN_POKEMON_SPACING);
        if (!nearby.isEmpty()) {
            return false;
        }

        // Check chunk loaded
        Vector2 chunkPos = getChunkPosition(pixelX, pixelY);
        return GameContext.get().getWorld().getChunks().containsKey(chunkPos);
    }



    private int calculatePokemonLevel(float pixelX, float pixelY) {
        // Assuming World.TILE_SIZE is defined on the client (and is the same as the server’s TILE_SIZE)
        return PokemonLevelCalculator.calculateLevel(pixelX, pixelY, World.TILE_SIZE);
    }


    public Collection<WildPokemon> getPokemonInRange(float centerPixelX, float centerPixelY, float rangePixels) {
        List<WildPokemon> inRange = new ArrayList<>();
        float rangeSquared = rangePixels * rangePixels;

        // Iterate through all Pokémon in the global pokemonById map
        for (WildPokemon pokemon : pokemonById.values()) {
            float dx = pokemon.getX() - centerPixelX;
            float dy = pokemon.getY() - centerPixelY;

            if (dx * dx + dy * dy <= rangeSquared) {
                inRange.add(pokemon);
            }
        }

        return inRange;
    }


    private void spawnPokemon(float pixelX, float pixelY, Vector2 chunkPos) {
        try {
            int tileX = (int) (pixelX / TILE_SIZE);
            int tileY = (int) (pixelY / TILE_SIZE);

            Biome biome = GameContext.get().getWorld().getBiomeAt(tileX, tileY);
            String pokemonName = selectPokemonForBiome(biome);
            if (pokemonName == null) return;

            TextureRegion sprite = atlas.findRegion(pokemonName.toUpperCase() + "_overworld");
            if (sprite == null) {
                GameLogger.error("Failed to load sprite for " + pokemonName);
                return;
            }

            // Snap to grid for consistency
            float snappedX = Math.round(pixelX / TILE_SIZE) * TILE_SIZE;
            float snappedY = Math.round(pixelY / TILE_SIZE) * TILE_SIZE;
            WildPokemon pokemon = new WildPokemon(
                pokemonName,
                calculatePokemonLevel(snappedX, snappedY),
                (int) snappedX,
                (int) snappedY,
                sprite
            );


            pokemon.setWorld(GameContext.get().getWorld());
            pokemon.getAi().enterIdleState();

            // Add to global collections
            pokemonById.put(pokemon.getUuid(), pokemon);
            pokemonByChunk.computeIfAbsent(chunkPos, k -> new ArrayList<>()).add(pokemon);
            pokemon.updateBoundingBox();
        } catch (Exception e) {
            GameLogger.error("Failed to spawn Pokemon: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public void update(float delta, Vector2 playerPosition) {
        spawnTimer += delta;
        if (!GameContext.get().isMultiplayer()) {
            if (spawnTimer >= SPAWN_CHECK_INTERVAL) {
                spawnTimer = 0;
                checkSpawns(playerPosition);
                removeExpiredPokemon();
            }
        }
        for (WildPokemon pokemon : pokemonById.values()) {
            try {
                pokemon.update(delta, GameContext.get().getWorld());
            } catch (Exception e) {
                GameLogger.error("Error updating " + pokemon.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public void removePokemon(UUID pokemonId) {
        WildPokemon pokemon = pokemonById.remove(pokemonId);
        if (pokemon != null) {
            Vector2 chunkPos = getChunkPosition(pokemon.getX(), pokemon.getY());
            List<WildPokemon> pokemonList = pokemonByChunk.get(chunkPos);
            if (pokemonList != null) {
                pokemonList.remove(pokemon);
                if (pokemonList.isEmpty()) {
                    pokemonByChunk.remove(chunkPos);
                }
            }
            syncedPokemon.remove(pokemonId);

            // Network update if multiplayer
            if (!GameContext.get().getGameClient().isSinglePlayer()) {
                GameContext.get().getGameClient().sendPokemonDespawn(pokemonId);
            }
        }
    }

    private Set<Vector2> getLoadedChunksAroundPlayer(Vector2 playerPixelPos) {
        Set<Vector2> loadedChunks = new HashSet<>();

        int playerTileX = (int) (playerPixelPos.x / TILE_SIZE);
        int playerTileY = (int) (playerPixelPos.y / TILE_SIZE);

        Vector2 playerChunk = getChunkPosition(playerPixelPos.x * TILE_SIZE, playerPixelPos.y * TILE_SIZE);


        // Debug world state
        if (GameContext.get().getWorld() == null) {
            GameLogger.error("World reference is null!");
            return loadedChunks;
        }

        Map<Vector2, Chunk> worldChunks = GameContext.get().getWorld().getChunks();
        int radius = 1;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                Vector2 checkPos = new Vector2(playerChunk.x + dx, playerChunk.y + dy);
                boolean isLoaded = worldChunks.containsKey(checkPos);

                if (isLoaded) {
                    loadedChunks.add(checkPos);
                }
            }
        }

        return loadedChunks;
    }

    private Vector2 getChunkPosition(float pixelX, float pixelY) {
        int chunkX = Math.floorDiv((int) pixelX, World.CHUNK_SIZE * TILE_SIZE);
        int chunkY = Math.floorDiv((int) pixelY, World.CHUNK_SIZE * TILE_SIZE);


        return new Vector2(chunkX, chunkY);
    }

    public Vector2 getSpawnPoint() {
        return new Vector2(
            World.HALF_WORLD_SIZE * World.TILE_SIZE,
            World.HALF_WORLD_SIZE * World.TILE_SIZE
        );
    }


    private String selectPokemonForBiome(Biome biome) {
        GameLogger.info("Selecting Pokemon for biome: " + biome.getType());

        double worldTimeInMinutes = GameContext.get().getWorld().getWorldData().getWorldTimeInMinutes();
        float hourOfDay = DayNightCycle.getHourOfDay(worldTimeInMinutes);
        TimeOfDay timeOfDay = (hourOfDay >= 6 && hourOfDay < 18) ? TimeOfDay.DAY : TimeOfDay.NIGHT;

        GameLogger.info("Time of day: " + timeOfDay + " (Hour: " + hourOfDay + ")");

        Map<TimeOfDay, String[]> biomeSpawns = POKEMON_SPAWNS.get(biome.getType());
        if (biomeSpawns == null) {
            GameLogger.info("No spawns defined for biome, using default");
            return getDefaultPokemon(timeOfDay);
        }

        String[] possiblePokemon = biomeSpawns.get(timeOfDay);
        if (possiblePokemon == null || possiblePokemon.length == 0) {
            GameLogger.info("No Pokemon available for time of day, using default");
            return getDefaultPokemon(timeOfDay);
        }

        String selected = possiblePokemon[random.nextInt(possiblePokemon.length)];
        GameLogger.info("Selected Pokemon: " + selected);
        return selected;
    }

    private void initializePokemonSpawns() {
        // PLAINS biome
        Map<TimeOfDay, String[]> plainsSpawns = new HashMap<>();
        plainsSpawns.put(TimeOfDay.DAY, new String[]{
            "Rattata", "Pidgey", "Sentret", "Hoppip", "Sunkern",
            "Caterpie", "Weedle", "Oddish", "Bellsprout", "Zigzagoon", "Spinarak", "Abra"
        });
        plainsSpawns.put(TimeOfDay.NIGHT, new String[]{
            "Zubat", "Hoothoot", "Rattata", "Caterpie", "Weedle",
            "Hoppip", "Sunkern", "Spinarak", "Skitty"
        });
        POKEMON_SPAWNS.put(BiomeType.PLAINS, plainsSpawns);

        // FOREST biome
        Map<TimeOfDay, String[]> forestSpawns = new HashMap<>();
        forestSpawns.put(TimeOfDay.DAY, new String[]{
            "Caterpie", "Weedle", "Oddish", "Bellsprout", "Treecko",
            "Shroomish", "Seedot", "Lotad", "Nincada", "Poochyena",
            "Hoppip", "Sunkern"
        });
        forestSpawns.put(TimeOfDay.NIGHT, new String[]{
            "Hoothoot", "Caterpie", "Weedle", "Oddish", "Bellsprout",
            "Treecko", "Shroomish", "Seedot", "Lotad", "Poochyena",
            "Hoppip", "Nincada"
        });
        POKEMON_SPAWNS.put(BiomeType.FOREST, forestSpawns);

        // SNOW biome
        Map<TimeOfDay, String[]> snowSpawns = new HashMap<>();
        snowSpawns.put(TimeOfDay.DAY, new String[]{
            "Swinub", "Snorunt", "Snover", "Spheal", "Cubchoo",
            "Sneasel", "Vanillite", "Snom"
        });
        snowSpawns.put(TimeOfDay.NIGHT, new String[]{
            "Swinub", "Snorunt", "Snover", "Spheal", "Cubchoo",
            "Sneasel", "Vanillite", "Snom"
        });
        POKEMON_SPAWNS.put(BiomeType.SNOW, snowSpawns);

        // DESERT biome
        Map<TimeOfDay, String[]> desertSpawns = new HashMap<>();
        desertSpawns.put(TimeOfDay.DAY, new String[]{
            "Sandshrew", "Trapinch", "Cacnea", "Sandile", "Diglett",
            "Vulpix", "Ekans", "Spinarak", "Poochyena"
        });
        desertSpawns.put(TimeOfDay.NIGHT, new String[]{
            "Sandshrew", "Trapinch", "Cacnea", "Sandile", "Diglett",
            "Vulpix", "Ekans", "Zubat", "Spinarak"
        });
        POKEMON_SPAWNS.put(BiomeType.DESERT, desertSpawns);

        // HAUNTED biome
        Map<TimeOfDay, String[]> hauntedSpawns = new HashMap<>();
        hauntedSpawns.put(TimeOfDay.DAY, new String[]{
            "Gastly", "Misdreavus", "Shuppet", "Duskull", "Sableye",
            "Litwick", "Murkrow", "Yamask"
        });
        hauntedSpawns.put(TimeOfDay.NIGHT, new String[]{
            "Gastly", "Misdreavus", "Shuppet", "Duskull", "Sableye",
            "Litwick", "Murkrow", "Yamask"
        });
        POKEMON_SPAWNS.put(BiomeType.HAUNTED, hauntedSpawns);

        // RAIN FOREST biome
        Map<TimeOfDay, String[]> rainforestSpawns = new HashMap<>();
        rainforestSpawns.put(TimeOfDay.DAY, new String[]{
            "Treecko", "Mudkip", "Torchic", "Lotad", "Seedot",
            "Shroomish", "Sunkern", "Hoppip", "Caterpie", "Weedle",
            "Nincada", "Poochyena"
        });
        rainforestSpawns.put(TimeOfDay.NIGHT, new String[]{
            "Treecko", "Mudkip", "Torchic", "Lotad", "Seedot",
            "Shroomish", "Sunkern", "Hoppip", "Caterpie", "Weedle",
            "Nincada", "Poochyena"
        });
        POKEMON_SPAWNS.put(BiomeType.RAIN_FOREST, rainforestSpawns);

        // BIG MOUNTAINS biome
        Map<TimeOfDay, String[]> mountainSpawns = new HashMap<>();
        mountainSpawns.put(TimeOfDay.DAY, new String[]{
            "Geodude", "Machop", "Onix", "Rhyhorn", "Nosepass",
            "Larvitar", "Meditite", "Riolu", "Rockruff", "Swinub"
        });
        mountainSpawns.put(TimeOfDay.NIGHT, new String[]{
            "Geodude", "Machop", "Onix", "Rhyhorn", "Nosepass",
            "Larvitar", "Meditite", "Riolu", "Rockruff", "Swinub"
        });
        POKEMON_SPAWNS.put(BiomeType.BIG_MOUNTAINS, mountainSpawns);

        // RUINS biome
        Map<TimeOfDay, String[]> ruinsSpawns = new HashMap<>();
        ruinsSpawns.put(TimeOfDay.DAY, new String[]{
            "Zubat", "Geodude", "Kabuto", "Omanyte", "Aerodactyl",
            "Rattata", "Gastly", "Onix", "Abra", "Cubone"
        });
        ruinsSpawns.put(TimeOfDay.NIGHT, new String[]{
            "Zubat", "Geodude", "Kabuto", "Omanyte", "Aerodactyl",
            "Rattata", "Gastly", "Onix", "Abra", "Cubone"
        });
        POKEMON_SPAWNS.put(BiomeType.RUINS, ruinsSpawns);

        // CHERRY_GROVE biome (Cherry blossom–themed; Pokémon up to Gen 6)
        Map<TimeOfDay, String[]> cherryGroveSpawns = new HashMap<>();
        cherryGroveSpawns.put(TimeOfDay.DAY, new String[]{
            "Cherrim",  // A flower–themed Pokémon with different forms
            "Budew",    // The pre–evolution of Roselia
            "Roselia",  // Often depicted amid blossoms
            "Floette",
            "Jigglypuff",
            "Cleffa",
            "Wooper",
            "Litleo"
        });
        cherryGroveSpawns.put(TimeOfDay.NIGHT, new String[]{
            "Cherrim",  // Its Overcast form may be seen at night
            "Roselia",
            "Floette",
            "Jigglypuff",
            "Cleffa",
            "Delibird",
            "Abra",
            "Marill",
            "Clefairy"
        });
        POKEMON_SPAWNS.put(BiomeType.CHERRY_GROVE, cherryGroveSpawns);

        // BEACH biome (Coastal areas with shallow water)
        Map<TimeOfDay, String[]> beachSpawns = new HashMap<>();
        beachSpawns.put(TimeOfDay.DAY, new String[]{
            "Krabby", "Corphish", "Wingull", "Staryu", "Corsola",
            "Shellder", "Goldeen", "Surskit"
        });
        beachSpawns.put(TimeOfDay.NIGHT, new String[]{
            "Krabby", "Wingull", "Chinchou", "Staryu",
            "Corsola", "Shellder", "Goldeen"
        });
        POKEMON_SPAWNS.put(BiomeType.BEACH, beachSpawns);

        // OCEAN biome (Deep water environments)
        Map<TimeOfDay, String[]> oceanSpawns = new HashMap<>();
        oceanSpawns.put(TimeOfDay.DAY, new String[]{
            "Magikarp", "Tentacruel", "Horsea", "Seadra", "Staryu",
            "Starmie", "Chinchou", "Wishiwashi"
        });
        oceanSpawns.put(TimeOfDay.NIGHT, new String[]{
            "Magikarp", "Tentacruel", "Horsea", "Seadra", "Staryu",
            "Starmie", "Lanturn", "Pyukumuku"
        });
        POKEMON_SPAWNS.put(BiomeType.OCEAN, oceanSpawns);
    }

    private void removeExpiredPokemon() {
        List<UUID> toRemove = new ArrayList<>();
        for (WildPokemon pokemon : pokemonById.values()) {
            if (pokemon.isExpired()) {
                toRemove.add(pokemon.getUuid());
                Vector2 chunkPos = getChunkPosition(pokemon.getX(), pokemon.getY());
                List<WildPokemon> pokemonList = pokemonByChunk.get(chunkPos);
                if (pokemonList != null) {
                    pokemonList.remove(pokemon);
                }
            }
        }

        for (UUID id : toRemove) {
            pokemonById.remove(id);
        }
    }

    private String getDefaultPokemon(TimeOfDay timeOfDay) {
        return timeOfDay == TimeOfDay.DAY ? "Rattata" : "Hoothoot";
    }

    public Collection<WildPokemon> getAllWildPokemon() {

        return pokemonById.values();
    }


    public void addPokemonToChunk(WildPokemon pokemon, Vector2 chunkPos) {
        try {
            // Initialize the list for the chunk if it doesn't already exist
            List<WildPokemon> pokemonList = pokemonByChunk.computeIfAbsent(chunkPos, k -> new ArrayList<>());

            // Ensure the Pokémon is not already added to the chunk
            if (!pokemonList.contains(pokemon)) {
                pokemonList.add(pokemon);  // Add the Pokémon to the chunk
                pokemonById.put(pokemon.getUuid(), pokemon);  // Add to the global list
                GameLogger.info("Added Pokémon " + pokemon.getName() + " to chunk at " + chunkPos);
            } else {
                GameLogger.info("Pokémon " + pokemon.getName() + " is already in chunk " + chunkPos);
            }
        } catch (Exception e) {
            GameLogger.error("Error adding Pokémon to chunk at " + chunkPos + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public enum TimeOfDay {
        DAY,
        NIGHT
    }

    public static class NetworkSyncData {
        public long lastUpdateTime;
        public Vector2 targetPosition;
        public String direction;
        public boolean isMoving;

        public NetworkSyncData() {
            this.lastUpdateTime = System.currentTimeMillis();
        }
    }
}
