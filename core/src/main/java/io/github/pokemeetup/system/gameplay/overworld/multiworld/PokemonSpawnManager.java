package io.github.pokemeetup.system.gameplay.overworld.multiworld;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.DayNightCycle;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonAI;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.PokemonLevelCalculator;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static io.github.pokemeetup.system.gameplay.overworld.World.TILE_SIZE;

public class PokemonSpawnManager {
    public static final float POKEMON_DESPAWN_TIME = 300f;
    private static final float BASE_SPAWN_RATE = 0.275f;
    private static final float SPAWN_CHECK_INTERVAL = 2.5f;
    private static final Map<BiomeType, Map<TimeOfDay, String[]>> POKEMON_SPAWNS = new HashMap<>();

    // Enhanced spawn configuration
    private static final float MIN_SPAWN_DISTANCE_PIXELS = 10 * TILE_SIZE;
    private static final float MAX_SPAWN_DISTANCE_PIXELS = 20 * TILE_SIZE;
    private static final int MAX_POKEMON_PER_CHUNK = 6;
    private static final float MIN_POKEMON_SPACING = TILE_SIZE * 1.5f;

    // Pack spawning configuration
    private static final float PACK_SPAWN_CHANCE = 0.3f;
    private static final int MIN_PACK_SIZE = 2;
    private static final int MAX_PACK_SIZE = 5;
    private static final float PACK_BASE_RADIUS = TILE_SIZE * 2;
    private static final float PACK_MAX_RADIUS = TILE_SIZE * 4;
    private static final int MAX_PACK_SPAWN_ATTEMPTS = 15;

    // Species-specific pack behavior
    private static final Map<String, PackBehaviorInfo> PACK_SPECIES = new HashMap<>();

    static {
        // Initialize pack species with their behavior patterns
        PACK_SPECIES.put("rattata", new PackBehaviorInfo(0.4f, 2, 4, true));
        PACK_SPECIES.put("pidgey", new PackBehaviorInfo(0.3f, 3, 6, false));
        PACK_SPECIES.put("zubat", new PackBehaviorInfo(0.5f, 4, 8, true));
        PACK_SPECIES.put("caterpie", new PackBehaviorInfo(0.2f, 2, 3, false));
        PACK_SPECIES.put("weedle", new PackBehaviorInfo(0.2f, 2, 3, false));
        PACK_SPECIES.put("sentret", new PackBehaviorInfo(0.3f, 2, 4, true));
        PACK_SPECIES.put("hoppip", new PackBehaviorInfo(0.4f, 3, 5, false));
        PACK_SPECIES.put("poochyena", new PackBehaviorInfo(0.6f, 3, 6, true));
        PACK_SPECIES.put("zigzagoon", new PackBehaviorInfo(0.3f, 2, 4, false));
    }

    private final TextureAtlas atlas;
    private final Random random;
    private final Map<Vector2, List<WildPokemon>> pokemonByChunk;
    private final Map<UUID, WildPokemon> pokemonById;
    private final Map<UUID, NetworkSyncData> syncedPokemon = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> pokemonPacks = new ConcurrentHashMap<>();
    private float spawnTimer = 0;

    public PokemonSpawnManager(TextureAtlas atlas) {
        this.atlas = atlas;
        this.random = new Random();
        this.pokemonByChunk = new ConcurrentHashMap<>();
        this.pokemonById = new ConcurrentHashMap<>();
        initializePokemonSpawns();
    }

    private void checkSpawns(Vector2 playerPos) {
        if (random.nextFloat() > BASE_SPAWN_RATE) {
            return;
        }

        Set<Vector2> loadedChunks = getLoadedChunksAroundPlayer(playerPos);
        if (loadedChunks.isEmpty()) {
            GameLogger.error("No loaded chunks found around player");
            return;
        }

        SpawnAttemptResult result = attemptSpawn(playerPos, loadedChunks);
        if (!result.successful) {
            GameLogger.info("Failed to spawn after " + result.attempts + " attempts");
        }
    }

    private SpawnAttemptResult attemptSpawn(Vector2 playerPos, Set<Vector2> loadedChunks) {
        int maxAttempts = 15;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            SpawnPosition spawnPos = generateSpawnPosition(playerPos, loadedChunks);
            if (spawnPos == null) continue;

            String selectedSpecies = selectPokemonForBiome(spawnPos.biome);
            if (selectedSpecies == null) continue;

            // Determine if this should be a pack spawn
            boolean shouldSpawnPack = shouldSpawnAsPack(selectedSpecies);

            if (shouldSpawnPack) {
                PackSpawnResult packResult = spawnPokemonPack(
                    spawnPos.pixelX, spawnPos.pixelY, spawnPos.chunkPos, selectedSpecies);
                if (packResult.spawned > 0) {
                    GameLogger.info("Successfully spawned pack of " + packResult.spawned +
                        " " + selectedSpecies + " at (" + spawnPos.pixelX + ", " + spawnPos.pixelY + ")");
                    return new SpawnAttemptResult(true, attempt + 1);
                }
            } else {
                if (spawnSinglePokemon(spawnPos.pixelX, spawnPos.pixelY, spawnPos.chunkPos, selectedSpecies)) {
                    GameLogger.info("Successfully spawned " + selectedSpecies +
                        " at (" + spawnPos.pixelX + ", " + spawnPos.pixelY + ")");
                    return new SpawnAttemptResult(true, attempt + 1);
                }
            }
        }

        return new SpawnAttemptResult(false, maxAttempts);
    }

    private SpawnPosition generateSpawnPosition(Vector2 playerPos, Set<Vector2> loadedChunks) {
        float angle = random.nextFloat() * MathUtils.PI2;
        float distance = MathUtils.random(MIN_SPAWN_DISTANCE_PIXELS, MAX_SPAWN_DISTANCE_PIXELS);
        float spawnPixelX = playerPos.x * TILE_SIZE + MathUtils.cos(angle) * distance;
        float spawnPixelY = playerPos.y * TILE_SIZE + MathUtils.sin(angle) * distance;

        Vector2 chunkPos = getChunkPosition(spawnPixelX, spawnPixelY);
        if (!loadedChunks.contains(chunkPos)) {
            return null;
        }

        // Check chunk capacity
        List<WildPokemon> chunkPokemon = pokemonByChunk.getOrDefault(chunkPos, new ArrayList<>());
        if (chunkPokemon.size() >= MAX_POKEMON_PER_CHUNK) {
            return null;
        }

        // Snap to grid for consistency
        float snappedX = Math.round(spawnPixelX / TILE_SIZE) * TILE_SIZE;
        float snappedY = Math.round(spawnPixelY / TILE_SIZE) * TILE_SIZE;

        if (!isValidSpawnPosition(snappedX, snappedY)) {
            return null;
        }

        // Get biome for this position
        int tileX = (int)(snappedX / TILE_SIZE);
        int tileY = (int)(snappedY / TILE_SIZE);
        Biome biome = GameContext.get().getWorld().getBiomeAt(tileX, tileY);

        return new SpawnPosition(snappedX, snappedY, chunkPos, biome);
    }

    private boolean shouldSpawnAsPack(String species) {
        PackBehaviorInfo packInfo = PACK_SPECIES.get(species.toLowerCase());
        if (packInfo == null) return false;

        return random.nextFloat() < (PACK_SPAWN_CHANCE * packInfo.packChance);
    }

    private PackSpawnResult spawnPokemonPack(float centerX, float centerY, Vector2 chunkPos, String species) {
        PackBehaviorInfo packInfo = PACK_SPECIES.get(species.toLowerCase());
        if (packInfo == null) {
            packInfo = new PackBehaviorInfo(0.3f, MIN_PACK_SIZE, MAX_PACK_SIZE, false);
        }

        int packSize = MathUtils.random(packInfo.minPackSize, packInfo.maxPackSize);
        List<WildPokemon> packMembers = new ArrayList<>();
        Set<UUID> packMemberIds = new HashSet<>();

        GameLogger.info("Attempting to spawn pack of " + packSize + " " + species +
            " at (" + centerX + ", " + centerY + ")");

        // Spawn the pack leader first
        WildPokemon leader = createPokemon(species, centerX, centerY, chunkPos);
        if (leader != null) {
            // Set leader personality
            PokemonAI leaderAI = (PokemonAI) leader.getAi();
            if (leaderAI != null) {
                // Leaders are typically more territorial/aggressive
                leaderAI.addPackMember(leader.getUuid());
            }

            packMembers.add(leader);
            packMemberIds.add(leader.getUuid());

            // Spawn pack members around the leader
            List<Vector2> spawnPositions = generatePackPositions(centerX, centerY, packSize - 1);

            for (Vector2 pos : spawnPositions) {
                WildPokemon member = createPokemon(species, pos.x, pos.y, chunkPos);
                if (member != null) {
                    packMembers.add(member);
                    packMemberIds.add(member.getUuid());

                    // Set up pack relationships
                    PokemonAI memberAI = (PokemonAI) member.getAi();
                    if (memberAI != null && leaderAI != null) {
                        memberAI.setPackLeader(leader.getUuid());
                        leaderAI.addPackMember(member.getUuid());
                    }
                }
            }

            // Register the pack
            if (packMembers.size() > 1) {
                UUID packId = UUID.randomUUID();
                pokemonPacks.put(packId, packMemberIds);

                GameLogger.info("Created pack " + packId + " with " + packMembers.size() +
                    " members of species " + species);
            }
        }

        return new PackSpawnResult(packMembers.size(), packMemberIds);
    }

    private List<Vector2> generatePackPositions(float centerX, float centerY, int memberCount) {
        List<Vector2> positions = new ArrayList<>();
        Set<Vector2> occupiedTiles = new HashSet<>();

        // Add center position as occupied
        occupiedTiles.add(new Vector2(
            Math.round(centerX / TILE_SIZE) * TILE_SIZE,
            Math.round(centerY / TILE_SIZE) * TILE_SIZE
        ));

        for (int i = 0; i < memberCount; i++) {
            Vector2 position = findValidPackMemberPosition(centerX, centerY, occupiedTiles);
            if (position != null) {
                positions.add(position);
                occupiedTiles.add(position);
            } else {
                GameLogger.info("Could not find valid position for pack member " + (i + 1));
            }
        }

        return positions;
    }

    private Vector2 findValidPackMemberPosition(float centerX, float centerY, Set<Vector2> occupiedTiles) {
        // Try multiple strategies for positioning pack members

        // Strategy 1: Try concentric circles around center
        for (float radius = PACK_BASE_RADIUS; radius <= PACK_MAX_RADIUS; radius += TILE_SIZE) {
            Vector2 position = tryCircularPattern(centerX, centerY, radius, occupiedTiles);
            if (position != null) return position;
        }

        // Strategy 2: Try grid pattern around center
        Vector2 position = tryGridPattern(centerX, centerY, occupiedTiles);
        if (position != null) return position;

        // Strategy 3: Try random positions within pack area
        return tryRandomPattern(centerX, centerY, occupiedTiles);
    }

    private Vector2 tryCircularPattern(float centerX, float centerY, float radius, Set<Vector2> occupied) {
        int numPoints = Math.max(8, (int)(radius / TILE_SIZE) * 4);

        for (int i = 0; i < numPoints; i++) {
            float angle = (i * MathUtils.PI2) / numPoints;
            float x = centerX + MathUtils.cos(angle) * radius;
            float y = centerY + MathUtils.sin(angle) * radius;

            // Snap to grid
            float snappedX = Math.round(x / TILE_SIZE) * TILE_SIZE;
            float snappedY = Math.round(y / TILE_SIZE) * TILE_SIZE;
            Vector2 testPos = new Vector2(snappedX, snappedY);

            if (!occupied.contains(testPos) && isValidSpawnPosition(snappedX, snappedY)) {
                return testPos;
            }
        }

        return null;
    }

    private Vector2 tryGridPattern(float centerX, float centerY, Set<Vector2> occupied) {
        int maxOffset = (int)(PACK_MAX_RADIUS / TILE_SIZE);

        // Try in spiral order for better distribution
        for (int offset = 1; offset <= maxOffset; offset++) {
            for (int dx = -offset; dx <= offset; dx++) {
                for (int dy = -offset; dy <= offset; dy++) {
                    if (Math.abs(dx) != offset && Math.abs(dy) != offset) continue;

                    float x = centerX + dx * TILE_SIZE;
                    float y = centerY + dy * TILE_SIZE;
                    Vector2 testPos = new Vector2(x, y);

                    if (!occupied.contains(testPos) && isValidSpawnPosition(x, y)) {
                        return testPos;
                    }
                }
            }
        }

        return null;
    }

    private Vector2 tryRandomPattern(float centerX, float centerY, Set<Vector2> occupied) {
        for (int attempt = 0; attempt < 20; attempt++) {
            float angle = random.nextFloat() * MathUtils.PI2;
            float distance = MathUtils.random(TILE_SIZE, PACK_MAX_RADIUS);
            float x = centerX + MathUtils.cos(angle) * distance;
            float y = centerY + MathUtils.sin(angle) * distance;

            // Snap to grid
            float snappedX = Math.round(x / TILE_SIZE) * TILE_SIZE;
            float snappedY = Math.round(y / TILE_SIZE) * TILE_SIZE;
            Vector2 testPos = new Vector2(snappedX, snappedY);

            if (!occupied.contains(testPos) && isValidSpawnPosition(snappedX, snappedY)) {
                return testPos;
            }
        }

        return null;
    }

    private WildPokemon createPokemon(String species, float x, float y, Vector2 chunkPos) {
        try {
            TextureRegion sprite = atlas.findRegion(species.toUpperCase() + "_overworld");
            if (sprite == null) {
                GameLogger.error("Failed to load sprite for " + species);
                return null;
            }

            WildPokemon pokemon = new WildPokemon(
                species,
                calculatePokemonLevel(x, y),
                (int) x,
                (int) y,
                sprite
            );

            pokemon.setWorld(GameContext.get().getWorld());

            // Replace old AI with enhanced AI
            PokemonAI enhancedAI = new PokemonAI(pokemon);
            pokemon.setAi(enhancedAI);

            enhancedAI.enterIdleState();

            // Add to collections
            pokemonById.put(pokemon.getUuid(), pokemon);
            pokemonByChunk.computeIfAbsent(chunkPos, k -> new ArrayList<>()).add(pokemon);
            pokemon.updateBoundingBox();

            return pokemon;
        } catch (Exception e) {
            GameLogger.error("Failed to create Pokemon " + species + ": " + e.getMessage());
            return null;
        }
    }

    private boolean spawnSinglePokemon(float x, float y, Vector2 chunkPos, String species) {
        return createPokemon(species, x, y, chunkPos) != null;
    }

    private boolean isValidSpawnPosition(float pixelX, float pixelY) {
        // Convert to tile coordinates for passability check
        int tileX = (int)(pixelX / TILE_SIZE);
        int tileY = (int)(pixelY / TILE_SIZE);

        if (GameContext.get().getWorld() == null) {
            GameLogger.error("World reference is null in spawn validation");
            return false;
        }

        // Check if tile is passable
        if (!GameContext.get().getWorld().isPassable(tileX, tileY)) {
            return false;
        }

        // Check distance from other Pokemon with improved spacing
        Collection<WildPokemon> nearby = getPokemonInRange(pixelX, pixelY, MIN_POKEMON_SPACING);
        if (!nearby.isEmpty()) {
            return false;
        }

        // Check chunk loaded
        Vector2 chunkPos = getChunkPosition(pixelX, pixelY);
        return GameContext.get().getWorld().getChunks().containsKey(chunkPos);
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

        // Update all Pokemon with enhanced AI
        for (WildPokemon pokemon : pokemonById.values()) {
            try {
                pokemon.update(delta, GameContext.get().getWorld());
            } catch (Exception e) {
                GameLogger.error("Error updating " + pokemon.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    // [Rest of the methods remain the same but use enhanced AI]
    private int calculatePokemonLevel(float pixelX, float pixelY) {
        return PokemonLevelCalculator.calculateLevel(pixelX, pixelY, TILE_SIZE);
    }

    public Collection<WildPokemon> getPokemonInRange(float centerPixelX, float centerPixelY, float rangePixels) {
        List<WildPokemon> inRange = new ArrayList<>();
        float rangeSquared = rangePixels * rangePixels;

        for (WildPokemon pokemon : pokemonById.values()) {
            float dx = pokemon.getX() - centerPixelX;
            float dy = pokemon.getY() - centerPixelY;

            if (dx * dx + dy * dy <= rangeSquared) {
                inRange.add(pokemon);
            }
        }

        return inRange;
    }

    private Set<Vector2> getLoadedChunksAroundPlayer(Vector2 playerPixelPos) {
        Set<Vector2> loadedChunks = new HashSet<>();

        Vector2 playerChunk = getChunkPosition(playerPixelPos.x * TILE_SIZE, playerPixelPos.y * TILE_SIZE);

        if (GameContext.get().getWorld() == null) {
            GameLogger.error("World reference is null!");
            return loadedChunks;
        }

        Map<Vector2, Chunk> worldChunks = GameContext.get().getWorld().getChunks();
        int radius = 1;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                Vector2 checkPos = new Vector2(playerChunk.x + dx, playerChunk.y + dy);
                if (worldChunks.containsKey(checkPos)) {
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

            // Remove from packs
            removeFromPacks(pokemonId);

            if (!GameContext.get().getGameClient().isSinglePlayer()) {
                GameContext.get().getGameClient().sendPokemonDespawn(pokemonId);
            }
        }
    }

    private void removeFromPacks(UUID pokemonId) {
        pokemonPacks.entrySet().removeIf(entry -> {
            entry.getValue().remove(pokemonId);
            return entry.getValue().isEmpty();
        });
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

    // Initialize Pokemon spawns (same as before)

    private void initializePokemonSpawns() {
        // PLAINS biome
        Map<PokemonSpawnManager.TimeOfDay, String[]> plainsSpawns = new HashMap<>();
        plainsSpawns.put(PokemonSpawnManager.TimeOfDay.DAY, new String[]{
            "Rattata", "Pidgey", "Sentret", "Hoppip", "Sunkern",
            "Caterpie", "Weedle", "Oddish", "Bellsprout", "Zigzagoon", "Spinarak", "Abra"
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

        // CHERRY_GROVE biome (Cherry blossom–themed; Pokémon up to Gen 6)
        Map<PokemonSpawnManager.TimeOfDay, String[]> cherryGroveSpawns = new HashMap<>();
        cherryGroveSpawns.put(PokemonSpawnManager.TimeOfDay.DAY, new String[]{
            "Cherrim",  // A flower–themed Pokémon with different forms
            "Budew",    // The pre–evolution of Roselia
            "Roselia",  // Often depicted amid blossoms
            "Floette",
            "Jigglypuff",
            "Cleffa",
            "Wooper",
            "Litleo"
        });
        cherryGroveSpawns.put(PokemonSpawnManager.TimeOfDay.NIGHT, new String[]{
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
        Map<PokemonSpawnManager.TimeOfDay, String[]> beachSpawns = new HashMap<>();
        beachSpawns.put(PokemonSpawnManager.TimeOfDay.DAY, new String[]{
            "Krabby", "Corphish", "Wingull", "Staryu", "Corsola",
            "Shellder", "Goldeen", "Surskit"
        });
        beachSpawns.put(PokemonSpawnManager.TimeOfDay.NIGHT, new String[]{
            "Krabby", "Wingull", "Chinchou", "Staryu",
            "Corsola", "Shellder", "Goldeen"
        });
        POKEMON_SPAWNS.put(BiomeType.BEACH, beachSpawns);

        // OCEAN biome (Deep water environments)
        Map<PokemonSpawnManager.TimeOfDay, String[]> oceanSpawns = new HashMap<>();
        oceanSpawns.put(PokemonSpawnManager.TimeOfDay.DAY, new String[]{
            "Magikarp", "Tentacruel", "Horsea", "Seadra", "Staryu",
            "Starmie", "Chinchou", "Wishiwashi"
        });
        oceanSpawns.put(PokemonSpawnManager.TimeOfDay.NIGHT, new String[]{
            "Magikarp", "Tentacruel", "Horsea", "Seadra", "Staryu",
            "Starmie", "Lanturn", "Pyukumuku"
        });
        POKEMON_SPAWNS.put(BiomeType.OCEAN, oceanSpawns);
    }

    private String selectPokemonForBiome(Biome biome) {
        GameLogger.info("Selecting Pokemon for biome: " + biome.getType());

        double worldTimeInMinutes = GameContext.get().getWorld().getWorldData().getWorldTimeInMinutes();
        float hourOfDay = DayNightCycle.getHourOfDay(worldTimeInMinutes);
        TimeOfDay timeOfDay = (hourOfDay >= 6 && hourOfDay < 18) ? TimeOfDay.DAY : TimeOfDay.NIGHT;

        Map<TimeOfDay, String[]> biomeSpawns = POKEMON_SPAWNS.get(biome.getType());
        if (biomeSpawns == null) {
            return getDefaultPokemon(timeOfDay);
        }

        String[] possiblePokemon = biomeSpawns.get(timeOfDay);
        if (possiblePokemon == null || possiblePokemon.length == 0) {
            return getDefaultPokemon(timeOfDay);
        }

        return possiblePokemon[random.nextInt(possiblePokemon.length)];
    }

    private String getDefaultPokemon(TimeOfDay timeOfDay) {
        return timeOfDay == TimeOfDay.DAY ? "Rattata" : "Hoothoot";
    }

    public Collection<WildPokemon> getAllWildPokemon() {
        return pokemonById.values();
    }

    public void addPokemonToChunk(WildPokemon pokemon, Vector2 chunkPos) {
        try {
            List<WildPokemon> pokemonList = pokemonByChunk.computeIfAbsent(chunkPos, k -> new ArrayList<>());

            if (!pokemonList.contains(pokemon)) {
                pokemonList.add(pokemon);
                pokemonById.put(pokemon.getUuid(), pokemon);
                GameLogger.info("Added Pokémon " + pokemon.getName() + " to chunk at " + chunkPos);
            }
        } catch (Exception e) {
            GameLogger.error("Error adding Pokémon to chunk at " + chunkPos + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public GameClient getGameClient() {
        return GameContext.get().getGameClient();
    }

    // Helper classes
    private static class PackBehaviorInfo {
        final float packChance;
        final int minPackSize;
        final int maxPackSize;
        final boolean aggressive;

        PackBehaviorInfo(float packChance, int minPackSize, int maxPackSize, boolean aggressive) {
            this.packChance = packChance;
            this.minPackSize = minPackSize;
            this.maxPackSize = maxPackSize;
            this.aggressive = aggressive;
        }
    }

    private static class SpawnPosition {
        final float pixelX, pixelY;
        final Vector2 chunkPos;
        final Biome biome;

        SpawnPosition(float pixelX, float pixelY, Vector2 chunkPos, Biome biome) {
            this.pixelX = pixelX;
            this.pixelY = pixelY;
            this.chunkPos = chunkPos;
            this.biome = biome;
        }
    }

    private static class SpawnAttemptResult {
        final boolean successful;
        final int attempts;

        SpawnAttemptResult(boolean successful, int attempts) {
            this.successful = successful;
            this.attempts = attempts;
        }
    }

    private static class PackSpawnResult {
        final int spawned;
        final Set<UUID> memberIds;

        PackSpawnResult(int spawned, Set<UUID> memberIds) {
            this.spawned = spawned;
            this.memberIds = memberIds;
        }
    }

    public enum TimeOfDay {
        DAY, NIGHT
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
