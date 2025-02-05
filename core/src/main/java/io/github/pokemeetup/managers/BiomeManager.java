package io.github.pokemeetup.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.system.gameplay.overworld.mechanics.MountainTileManager;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.OpenSimplex2;
import io.github.pokemeetup.utils.storage.GameFileSystem;
import io.github.pokemeetup.utils.textures.TileType;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The BiomeManager computes biome values via noise functions (with domain‐warp)
 * and caches per‑chunk downsampled biome data to avoid excessive noise evaluations.
 * <p>
 * In this implementation, each chunk’s biome data is computed at a lower resolution
 * (one sample per BIOME_DOWNSAMPLE×BIOME_DOWNSAMPLE block) and stored in a matrix.
 * The public method {@code getBiomeAt(float worldX, float worldY)} automatically uses
 * the cached matrix if no chunk data is available.
 */
public class BiomeManager {
    // ─── CONSTANTS ─────────────────────────────────────────────────────────────
    private static final double RUINS_THRESHOLD = 0.65;
    private static final double COLD_THRESHOLD = 0.35;
    private static final double HOT_THRESHOLD = 0.65;
    private static final double DRY_THRESHOLD = 0.35;
    private static final double WET_THRESHOLD = 0.65;

    private static final float TEMPERATURE_SCALE = 0.00005f;
    private static final float MOISTURE_SCALE = 0.00005f;
    private static final float WARP_SCALE = 0.00001f;
    private static final float WARP_STRENGTH = 30f;
    private static final float MOUNTAIN_BASE_SCALE = 0.001f;
    private static final int BASE_TILE_MASK = 0x0000FFFF;
    private static final int OVERLAY_SHIFT = 16;
    private static final float MOUNTAIN_DETAIL_SCALE = 0.002f;
    private static final float MOUNTAIN_RIDGE_SCALE = 0.0015f;
    private static final float MOUNTAIN_THRESHOLD = 0.65f;
    private static final double HEIGHT_EPSILON = 0.05;
    // Downsampling factor (e.g., 4 means one biome sample per 4x4 block of tiles)
    private static final int BIOME_DOWNSAMPLE = 4;
    private static final float NOISE_OFFSET = 10000f;
    // ─── NOISE SEEDS & BIOME DATA ─────────────────────────────────────────────
    private final long temperatureSeed;
    private final long moistureSeed;
    private final long warpSeed;
    private final long baseSeed;
    private final long mountainSeed;
    private final long detailSeed;
    private final Map<BiomeType, Biome> biomes;
    // ─── CACHING FOR BIOME DATA ─────────────────────────────────────────────────
    // Cache per chunk: key is a Vector2 (chunkX,chunkY), value is a downsampled biome matrix.
    private final Map<Vector2, BiomeTransitionResult[][]> chunkBiomeCache = new ConcurrentHashMap<>();

    // ─── CONSTRUCTOR ──────────────────────────────────────────────────────────
    public BiomeManager(long baseSeed) {
        this.baseSeed = baseSeed;
        this.biomes = new HashMap<>();
        this.temperatureSeed = baseSeed + 1000;
        this.moistureSeed = baseSeed + 2000;
        this.mountainSeed = baseSeed + 3000;
        this.warpSeed = baseSeed + 4000;
        this.detailSeed = baseSeed + 5000;
        loadBiomesFromJson();
    }

    // ─── DOMAIN WARP & NOISE UTILS ─────────────────────────────────────────────
    private float[] domainWarp(float x, float y) {
        float[] warped = new float[]{x, y};
        float amplitude = WARP_STRENGTH;
        float frequency = WARP_SCALE;
        for (int i = 0; i < 3; i++) {
            float warpX = OpenSimplex2.noise2(warpSeed + i, warped[0] * frequency, warped[1] * frequency) * amplitude;
            float warpY = OpenSimplex2.noise2(warpSeed + i + 1000, warped[0] * frequency, warped[1] * frequency) * amplitude;
            warped[0] += warpX;
            warped[1] += warpY;
            amplitude *= 0.5f;
            frequency *= 1.8f;
        }
        return warped;
    }

    private double getNoiseValue(float x, float y, long seed, float scale) {
        // Offset the coordinates to ensure they are positive.
        x += NOISE_OFFSET;
        y += NOISE_OFFSET;
        double value = 0;
        double amplitude = 1.0;
        double frequency = 1.0;
        double maxAmplitude = 0;

        for (int i = 0; i < 3; i++) {
            value += amplitude * OpenSimplex2.noise2(seed + i,
                x * scale * frequency,
                y * scale * frequency);
            value += amplitude * 0.2 * OpenSimplex2.noise2(seed + i + 500,
                x * scale * frequency * 2,
                y * scale * frequency * 2);
            maxAmplitude += amplitude * 1.2;
            amplitude *= 0.5;
            frequency *= 2.0;
        }
        value = value / maxAmplitude;
        value = (value + 1) / 2;
        value = Math.pow(value, 1.1);
        return clamp(value);
    }


    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    // ─── BIOME TYPE DETERMINATION ─────────────────────────────────────────────
    private BiomeType determineBiomeType(double temperature, double moisture) {
        double regionalVariation = OpenSimplex2.noise2(detailSeed + 3000, temperature * 2, moisture * 2) * 0.05;
        double adjustedTemp = temperature + regionalVariation;
        double adjustedMoist = moisture + regionalVariation;

        if (shouldGenerateRuins(adjustedTemp, adjustedMoist)) {
            return BiomeType.RUINS;
        }

        BiomeType chosenBiome;
        if (adjustedTemp < COLD_THRESHOLD) {
            chosenBiome = determineColderBiomes(adjustedMoist);
        } else if (adjustedTemp > HOT_THRESHOLD) {
            chosenBiome = determineHotterBiomes(adjustedMoist);
        } else {
            chosenBiome = determineTemperateBiomes(adjustedTemp, adjustedMoist);
        }

        double mountainNoise = OpenSimplex2.noise2(mountainSeed, adjustedTemp * 5, adjustedMoist * 5);
        if (shouldGenerateMountains(adjustedTemp, adjustedMoist, mountainNoise)) {
            chosenBiome = BiomeType.BIG_MOUNTAINS;
        }

        return chosenBiome;
    }

    private boolean shouldGenerateRuins(double temp, double moisture) {
        boolean moderateClimate = temp > 0.3 && temp < 0.7 && moisture > 0.3 && moisture < 0.7;
        if (!moderateClimate) return false;
        double ruinsNoise = OpenSimplex2.noise2(detailSeed + 2000, temp * 2, moisture * 2);
        double structureNoise = OpenSimplex2.noise2(detailSeed + 2500, temp * 4, moisture * 4);
        double historyNoise = OpenSimplex2.noise2(detailSeed + 3000, temp * 1.5, moisture * 1.5);
        double combinedNoise = ruinsNoise * 0.4 + structureNoise * 0.4 + historyNoise * 0.2;
        return combinedNoise > RUINS_THRESHOLD;
    }

    // ─── MOUNTAIN OVERLAY & SLOPE METHODS (unchanged) ───────────────────────────
    private void addMountainOverlay(Chunk chunk, int x, int y, double height,
                                    MountainTileManager tileManager, Random random) {
        int[][] tileData = chunk.getTileData();
        BiomeType biomeType = chunk.getBiome().getType();
        if (height > 0.8 && (biomeType == BiomeType.SNOW || biomeType == BiomeType.BIG_MOUNTAINS)) {
            TextureRegion snowOverlay = tileManager.getTile(
                random.nextBoolean() ?
                    MountainTileManager.MountainTileType.SNOW_OVERLAY_1 :
                    MountainTileManager.MountainTileType.SNOW_OVERLAY_2);
            applyOverlay(chunk, x, y, snowOverlay);
        } else if (height > 0.6 && random.nextFloat() < 0.3) {
            TextureRegion grassOverlay = tileManager.getTile(MountainTileManager.MountainTileType.GRASS_OVERLAY);
            applyOverlay(chunk, x, y, grassOverlay);
        }
        if (random.nextFloat() < (1.0 - height) * 0.2) {
            TextureRegion rockOverlay = tileManager.getRandomRock(random);
            applyOverlay(chunk, x, y, rockOverlay);
        }
    }

    private void applyOverlay(Chunk chunk, int x, int y, TextureRegion overlay) {
        try {
            int[][] tileData = chunk.getTileData();
            if (tileData == null) return;
            int currentTile = tileData[x][y];
            int overlayId = getOverlayTileId(overlay);
            if (overlayId == -1) return;
            currentTile &= BASE_TILE_MASK;
            currentTile |= (overlayId << OVERLAY_SHIFT);
            tileData[x][y] = currentTile;
            chunk.setDirty(true);
        } catch (Exception e) {
            GameLogger.error("Failed to apply overlay: " + e.getMessage());
        }
    }

    private int getOverlayTileId(TextureRegion overlay) {
        if (overlay == null) return 1;
        GameLogger.error("Unknown overlay texture - no matching tile ID found");
        return 1;
    }

    private BiomeType determineTemperateBiomes(double temperature, double moisture) {
        double varietyNoise = OpenSimplex2.noise2(detailSeed + 1000, temperature * 3, moisture * 3) * 0.5 + 0.5;
        if (moisture > WET_THRESHOLD) {
            return varietyNoise > 0.5 ? BiomeType.RAIN_FOREST : BiomeType.FOREST;
        } else if (moisture < DRY_THRESHOLD) {
            return BiomeType.PLAINS;
        }
        return varietyNoise > 0.6 ? BiomeType.FOREST : (varietyNoise > 0.3 ? BiomeType.PLAINS : BiomeType.HAUNTED);
    }

    private BiomeType determineColderBiomes(double moisture) {
        double varietyNoise = OpenSimplex2.noise2(detailSeed + 3000, moisture * 4, moisture * 4) * 0.5 + 0.5;
        if (moisture > WET_THRESHOLD) {
            return BiomeType.SNOW;
        } else if (moisture < DRY_THRESHOLD) {
            return BiomeType.PLAINS;
        }
        return varietyNoise > 0.2 ? BiomeType.SNOW : BiomeType.HAUNTED;
    }

    private BiomeType determineHotterBiomes(double moisture) {
        if (moisture < DRY_THRESHOLD) {
            return BiomeType.DESERT;
        } else if (moisture > WET_THRESHOLD) {
            return BiomeType.RAIN_FOREST;
        }
        double varietyNoise = OpenSimplex2.noise2(detailSeed + 2000, moisture * 4, moisture * 4) * 0.5 + 0.5;
        return varietyNoise > 0.5 ? BiomeType.PLAINS : BiomeType.DESERT;
    }

    public BiomeTransitionResult getBiomeAt(float worldX, float worldY) {
        // Use Math.floor to correctly compute negative coordinates.
        int chunkX = (int) Math.floor(worldX / (Chunk.CHUNK_SIZE * World.TILE_SIZE));
        int chunkY = (int) Math.floor(worldY / (Chunk.CHUNK_SIZE * World.TILE_SIZE));
        Vector2 chunkPos = new Vector2(chunkX, chunkY);

        // Try to retrieve an existing chunk from the world.
        try {
            World world = GameContext.get().getWorld();
            if (world != null) {
                Chunk existingChunk = world.getChunks().get(chunkPos);
                if (existingChunk != null) {
                    return new BiomeTransitionResult(existingChunk.getBiome(), null, 1.0f);
                }
            }
        } catch (IllegalStateException ignored) {
        }

        // Fallback: use cached noise‐based biome generation.
        return getBiomeAtCached(worldX, worldY);
    }
    // In your BiomeManager class, change the downsample factor

    // --- In getBiomeAtCached, perform bilinear “interpolation” by recomputing noise at the interpolated coordinate:
    public BiomeTransitionResult getBiomeAtCached(float worldX, float worldY) {
        // Compute which chunk these coordinates belong to.
        int chunkX = (int) Math.floor(worldX / (Chunk.CHUNK_SIZE * World.TILE_SIZE));
        int chunkY = (int) Math.floor(worldY / (Chunk.CHUNK_SIZE * World.TILE_SIZE));

        // Get the cached matrix for that chunk.
        BiomeTransitionResult[][] matrix = computeBiomeMatrixForChunk(chunkX, chunkY);

        // Determine the local coordinate within the chunk (in pixels).
        float baseWorldX = chunkX * Chunk.CHUNK_SIZE * World.TILE_SIZE;
        float baseWorldY = chunkY * Chunk.CHUNK_SIZE * World.TILE_SIZE;
        float localX = worldX - baseWorldX;
        float localY = worldY - baseWorldY;

        // Calculate the grid spacing (since BIOME_DOWNSAMPLE==1, gridWidth equals CHUNK_SIZE).
        int gridWidth = matrix.length;    // equals CHUNK_SIZE
        int gridHeight = matrix[0].length;  // equals CHUNK_SIZE
        // Here the grid covers the whole chunk: width = CHUNK_SIZE * TILE_SIZE.
        float gridSpacingX = (Chunk.CHUNK_SIZE * World.TILE_SIZE) / (gridWidth - 1);
        float gridSpacingY = (Chunk.CHUNK_SIZE * World.TILE_SIZE) / (gridHeight - 1);

        // Compute fractional indices into the biome grid.
        float gx = localX / gridSpacingX;
        float gy = localY / gridSpacingY;
        int ix = MathUtils.floor(gx);
        int iy = MathUtils.floor(gy);
        float fx = gx - ix;
        float fy = gy - iy;

        // Clamp indices so that we can safely sample ix+1 and iy+1.
        ix = MathUtils.clamp(ix, 0, gridWidth - 2);
        iy = MathUtils.clamp(iy, 0, gridHeight - 2);

        // Instead of trying to “blend” discrete biome transition results,
        // we simply compute an interpolated world coordinate:
        float interpWorldX = baseWorldX + gx * gridSpacingX;
        float interpWorldY = baseWorldY + gy * gridSpacingY;

        // Recompute the biome using the fallback noise method at the interpolated coordinate.
        return fallbackNoiseBiome(interpWorldX, interpWorldY);
    }

    /**
     * Performs a multi-layer domain warp for a given (x,y).
     * This warps the coordinate twice, with different seeds/frequencies,
     * to create more “curly” edges and reduce blocky corners.
     */
    private float[] multiLayerDomainWarp(float x, float y) {
        float[] warp1 = domainWarp(x, y);
        // domainWarp is your existing function that does 3 passes with warpSeed.

        // Now apply a second domain warp to the result, with a different frequency/amplitude
        // Increase amplitude slightly for the second warp, or use a new seed offset.
        float amplitude2 = WARP_STRENGTH * 0.35f;  // smaller amplitude
        float frequency2 = WARP_SCALE * 2.0f;      // or bigger frequency
        float wx = warp1[0];
        float wy = warp1[1];
        for (int i = 0; i < 2; i++) {
            float warpX = OpenSimplex2.noise2((warpSeed + 999) + i, wx * frequency2, wy * frequency2) * amplitude2;
            float warpY = OpenSimplex2.noise2((warpSeed + 1999) + i, wx * frequency2, wy * frequency2) * amplitude2;
            wx += warpX;
            wy += warpY;
            amplitude2 *= 0.55f;
            frequency2 *= 1.8f;
        }

        // final warped coordinates
        return new float[]{wx, wy};
    }

    private boolean shouldGenerateMountains(double temp, double moisture, double mountainNoise) {
        double mountainProbability = (mountainNoise + 1.0) / 2.0;
        // previously multiplied up to 1.3 or 1.2, let’s reduce that a bit:
        if (temp < COLD_THRESHOLD) {
            mountainProbability *= 1.1;  // was 1.3
        }
        if (moisture > WET_THRESHOLD) {
            mountainProbability *= 1.1;  // was 1.2
        }
        double ridgeNoise = Math.abs(OpenSimplex2.noise2(mountainSeed + 1000, temp * MOUNTAIN_RIDGE_SCALE, moisture * MOUNTAIN_RIDGE_SCALE));
        double detailNoise = OpenSimplex2.noise2(mountainSeed + 2000, temp * MOUNTAIN_DETAIL_SCALE, moisture * MOUNTAIN_DETAIL_SCALE);
        double combinedProbability = mountainProbability * 0.5 + ridgeNoise * 0.3 + Math.abs(detailNoise) * 0.2;

        // CHANGED: we require combinedProbability > MOUNTAIN_THRESHOLD ( now 0.75f ), up from 0.65
        return combinedProbability > MOUNTAIN_THRESHOLD;
    }

    // --- computeBiomeMatrixForChunk remains similar ---
    private BiomeTransitionResult[][] computeBiomeMatrixForChunk(int chunkX, int chunkY) {
        Vector2 key = new Vector2(chunkX, chunkY);
        if (chunkBiomeCache.containsKey(key)) {
            return chunkBiomeCache.get(key);
        }
        int size = Chunk.CHUNK_SIZE;
        int outWidth = (size + BIOME_DOWNSAMPLE - 1) / BIOME_DOWNSAMPLE;   // with BIOME_DOWNSAMPLE==1, outWidth == size
        int outHeight = (size + BIOME_DOWNSAMPLE - 1) / BIOME_DOWNSAMPLE;  // same for height
        BiomeTransitionResult[][] matrix = new BiomeTransitionResult[outWidth][outHeight];
        float baseWorldX = chunkX * size * World.TILE_SIZE;
        float baseWorldY = chunkY * size * World.TILE_SIZE;
        for (int bx = 0; bx < outWidth; bx++) {
            for (int by = 0; by < outHeight; by++) {
                // Sample exactly at the center of each tile (since BIOME_DOWNSAMPLE==1)
                float sampleX = baseWorldX + bx * World.TILE_SIZE;
                float sampleY = baseWorldY + by * World.TILE_SIZE;
                BiomeTransitionResult btr = fallbackNoiseBiome(sampleX, sampleY);
                matrix[bx][by] = btr;
            }
        }
        chunkBiomeCache.put(key, matrix);
        return matrix;
    }

    private BiomeTransitionResult fallbackNoiseBiome(float worldX, float worldY) {
        float[] warped = multiLayerDomainWarp(worldX, worldY);

        double temperature = getNoiseValue(warped[0], warped[1], temperatureSeed, TEMPERATURE_SCALE);
        double moisture    = getNoiseValue(warped[0], warped[1], moistureSeed,    MOISTURE_SCALE);

        // CHANGED: baseThreshold is now 0.25 (was 0.20). We add up to +0.1 (was +0.07).
        double edge = getNoiseValue(worldX, worldY, warpSeed, TEMPERATURE_SCALE * 2);
        double baseThreshold = 0.25;              // was 0.20
        double transitionThreshold = baseThreshold + (edge * 0.10); // was 0.07

        BiomeType primaryType = determineBiomeType(temperature, moisture);

        List<BiomeType> candidates = new ArrayList<>();
        candidates.add(primaryType);

        // offsets for possible second or third biome
        float[][] offsets = { {100f, 100f}, {200f, -250f} };

        if (Math.abs(temperature - 0.5) < transitionThreshold ||
            Math.abs(moisture - 0.5)   < transitionThreshold)
        {
            for (float[] off : offsets) {
                float[] warped2 = multiLayerDomainWarp(worldX + off[0], worldY + off[1]);
                double t2 = getNoiseValue(warped2[0], warped2[1], temperatureSeed, TEMPERATURE_SCALE);
                double m2 = getNoiseValue(warped2[0], warped2[1], moistureSeed,    MOISTURE_SCALE);
                BiomeType alt = determineBiomeType(t2, m2);

                if (alt != primaryType && !candidates.contains(alt) && areCompatibleBiomes(primaryType, alt)) {
                    candidates.add(alt);
                }
            }
        }
        if (candidates.size() == 1) {
            return new BiomeTransitionResult(getBiome(primaryType), null, 1.0f);
        }
        BiomeType secondaryType = candidates.get(1);
        float distFromCenter = (float)(Math.abs(temperature - 0.5) + Math.abs(moisture - 0.5));
        // scale by new transitionThreshold range
        float transitionFactor = distFromCenter / (float)(transitionThreshold * 2.0);
        transitionFactor = MathUtils.clamp(transitionFactor, 0f, 1f);

        return new BiomeTransitionResult(getBiome(primaryType), getBiome(secondaryType), transitionFactor);
    }


    public void debugBiomeDistribution(int samples) {
        Map<BiomeType, Integer> distribution = new HashMap<>();
        Random random = new Random(baseSeed);
        for (int i = 0; i < samples; i++) {
            float x = random.nextFloat() * 1000;
            float y = random.nextFloat() * 1000;
            BiomeTransitionResult result = getBiomeAt(x, y);
            BiomeType type = result.getPrimaryBiome().getType();
            distribution.merge(type, 1, Integer::sum);
        }
        GameLogger.info("=== Biome Distribution Analysis ===");
        GameLogger.info("Samples: " + samples);
        GameLogger.info("World Seed: " + baseSeed);
        for (Map.Entry<BiomeType, Integer> entry : distribution.entrySet()) {
            double percentage = (entry.getValue() * 100.0) / samples;
            GameLogger.info(String.format("%s: %d occurrences (%.2f%%)", entry.getKey(), entry.getValue(), percentage));
        }
        GameLogger.info("===============================");
    }

    public void debugNoiseDistribution(int samples) {
        GameLogger.info("=== Noise Distribution Analysis ===");
        double minTemp = 1.0, maxTemp = 0.0, avgTemp = 0.0;
        double minMoist = 1.0, maxMoist = 0.0, avgMoist = 0.0;
        Random random = new Random(baseSeed);
        for (int i = 0; i < samples; i++) {
            float x = random.nextFloat() * 1000;
            float y = random.nextFloat() * 1000;
            double tempNoise = OpenSimplex2.noise2(temperatureSeed, x * TEMPERATURE_SCALE, y * TEMPERATURE_SCALE);
            double moistNoise = OpenSimplex2.noise2(moistureSeed, x * MOISTURE_SCALE, y * MOISTURE_SCALE);
            double temp = (tempNoise + 1.0) / 2.0;
            double moist = (moistNoise + 1.0) / 2.0;
            minTemp = Math.min(minTemp, temp);
            maxTemp = Math.max(maxTemp, temp);
            avgTemp += temp;
            minMoist = Math.min(minMoist, moist);
            maxMoist = Math.max(maxMoist, moist);
            avgMoist += moist;
        }
        avgTemp /= samples;
        avgMoist /= samples;
        GameLogger.info(String.format("Temperature - Min: %.3f, Max: %.3f, Avg: %.3f", minTemp, maxTemp, avgTemp));
        GameLogger.info(String.format("Moisture - Min: %.3f, Max: %.3f, Avg: %.3f", minMoist, maxMoist, avgMoist));
        GameLogger.info("===============================");
    }

    public Biome getBiome(BiomeType type) {
        Biome biome = biomes.get(type);
        if (biome == null) {
            GameLogger.error("Missing biome type: " + type + ", falling back to PLAINS");
            return biomes.get(BiomeType.PLAINS);
        }
        return biome;
    }

    private void loadBiomesFromJson() {
        try {
            String jsonContent = GameFileSystem.getInstance().getDelegate().readString("Data/biomes.json");
            if (jsonContent == null) {
                initializeDefaultBiomes();
                return;
            }
            JsonParser parser = new JsonParser();
            JsonArray biomesArray = parser.parse(jsonContent).getAsJsonArray();
            for (JsonElement element : biomesArray) {
                JsonObject biomeObject = element.getAsJsonObject();
                BiomeData data = parseBiomeData(biomeObject);
                if (validateBiomeData(data)) {
                    Biome biome = createBiomeFromData(data);
                    biomes.put(biome.getType(), biome);
                } else {
                    GameLogger.error("Invalid biome data for: " + data.getName());
                }
            }
        } catch (Exception e) {
            GameLogger.error("Failed to load biomes: " + e.getMessage());
            initializeDefaultBiomes();
        }
    }

    private BiomeData parseBiomeData(JsonObject json) {
        BiomeData data = new BiomeData();
        data.setName(json.get("name").getAsString());
        data.setType(json.get("type").getAsString());
        JsonArray allowedTypes = json.getAsJsonArray("allowedTileTypes");
        List<Integer> tileTypes = new ArrayList<>();
        for (JsonElement type : allowedTypes) {
            tileTypes.add(type.getAsInt());
        }
        data.setAllowedTileTypes(tileTypes);
        JsonObject distObject = json.getAsJsonObject("tileDistribution");
        Map<Integer, Integer> distribution = new HashMap<>();
        double total = 0;
        for (Map.Entry<String, JsonElement> entry : distObject.entrySet()) {
            int tileType = Integer.parseInt(entry.getKey());
            double weight = entry.getValue().getAsDouble();
            distribution.put(tileType, (int) Math.round(weight));
            total += weight;
        }
        if (json.has("spawnableObjects")) {
            List<String> spawnableObjects = new ArrayList<>();
            JsonArray objectsArray = json.getAsJsonArray("spawnableObjects");
            for (JsonElement element : objectsArray) {
                spawnableObjects.add(element.getAsString());
            }
            data.setSpawnableObjects(spawnableObjects);
        }
        if (json.has("spawnChances")) {
            Map<String, Double> chances = new HashMap<>();
            JsonObject chancesObj = json.getAsJsonObject("spawnChances");
            for (Map.Entry<String, JsonElement> entry : chancesObj.entrySet()) {
                chances.put(entry.getKey(), entry.getValue().getAsDouble());
            }
            data.setSpawnChances(chances);
        }
        if (Math.abs(total - 100.0) > 0.01) {
            GameLogger.error("Tile distribution does not sum to 100% for biome: " + data.getName());
            distribution = getDefaultDistribution(BiomeType.valueOf(data.getType()));
        }
        data.setTileDistribution(distribution);
        return data;
    }

    private boolean validateBiomeData(BiomeData data) {
        if (data.getName() == null || data.getType() == null) return false;
        if (data.getAllowedTileTypes() == null || data.getAllowedTileTypes().isEmpty()) return false;
        Map<Integer, Integer> dist = data.getTileDistribution();
        if (dist == null || dist.isEmpty()) return false;
        double total = dist.values().stream().mapToDouble(Integer::intValue).sum();
        return Math.abs(total - 100.0) < 0.01;
    }

    private Biome createBiomeFromData(BiomeData data) {
        Biome biome = new Biome(data.getName(), BiomeType.valueOf(data.getType()));
        biome.setAllowedTileTypes(data.getAllowedTileTypes());
        biome.setTileDistribution(data.getTileDistribution());
        if (data.getSpawnableObjects() != null) {
            biome.loadSpawnableObjects(data.getSpawnableObjects());
        }
        if (data.getSpawnChances() != null) {
            biome.loadSpawnChances(data.getSpawnChances());
        }
        return biome;
    }

    private Map<Integer, Integer> getDefaultDistribution(BiomeType type) {
        Map<Integer, Integer> dist = new HashMap<>();
        switch (type) {
            case DESERT:
                dist.put(16, 70);
                dist.put(2, 20);
                dist.put(1, 10);
                break;
            case SNOW:
                dist.put(4, 70);
                dist.put(1, 20);
                dist.put(3, 10);
                break;
            case HAUNTED:
                dist.put(8, 70);
                dist.put(2, 20);
                dist.put(3, 10);
                break;
            default:
                dist.put(1, 70);
                dist.put(2, 20);
                dist.put(3, 10);
        }
        return dist;
    }

    private void initializeDefaultBiomes() {
        if (!biomes.containsKey(BiomeType.PLAINS)) {
            Biome plains = new Biome("Plains", BiomeType.PLAINS);
            plains.setAllowedTileTypes(Arrays.asList(1, 2, 3));
            plains.getTileDistribution().put(1, 70);
            plains.getTileDistribution().put(2, 20);
            plains.getTileDistribution().put(3, 10);
            biomes.put(BiomeType.PLAINS, plains);
        }
        if (!biomes.containsKey(BiomeType.FOREST)) {
            Biome forest = new Biome("Forest", BiomeType.FOREST);
            forest.setAllowedTileTypes(Arrays.asList(1, 2, 3));
            forest.getTileDistribution().put(1, 60);
            forest.getTileDistribution().put(2, 30);
            forest.getTileDistribution().put(3, 10);
            biomes.put(BiomeType.FOREST, forest);
        }
        BiomeType[] requiredTypes = {BiomeType.SNOW, BiomeType.DESERT, BiomeType.HAUNTED};
        for (BiomeType type : requiredTypes) {
            if (!biomes.containsKey(type)) {
                Biome biome = new Biome(type.name(), type);
                biome.setAllowedTileTypes(Arrays.asList(1, 2, 3));
                biome.getTileDistribution().put(1, 80);
                biome.getTileDistribution().put(2, 15);
                biome.getTileDistribution().put(3, 5);
                biomes.put(type, biome);
            }
        }
        GameLogger.info("Default biomes initialized");
    }


    public boolean areCompatibleBiomes(BiomeType a, BiomeType b) {
        if (a == b) return true;

        // First check if either biome is RUINS, as it has special transition rules
        if (a == BiomeType.RUINS || b == BiomeType.RUINS) {
            BiomeType other = (a == BiomeType.RUINS) ? b : a;
            return isCompatibleWithRuins(other);
        }

        switch (a) {
            case PLAINS:
                return b == BiomeType.FOREST || b == BiomeType.DESERT ||
                    b == BiomeType.HAUNTED;

            case FOREST:
                return b == BiomeType.PLAINS || b == BiomeType.RAIN_FOREST ||
                    b == BiomeType.SNOW || b == BiomeType.HAUNTED;

            case DESERT:
                return b == BiomeType.PLAINS;

            case SNOW:
                return b == BiomeType.FOREST || b == BiomeType.HAUNTED;

            case RAIN_FOREST:
                return b == BiomeType.FOREST;

            case HAUNTED:
                return b == BiomeType.FOREST || b == BiomeType.PLAINS ||
                    b == BiomeType.SNOW;

            case BIG_MOUNTAINS:
                return b == BiomeType.SNOW || b == BiomeType.FOREST;

            default:
                return false;
        }
    }

    private boolean isCompatibleWithRuins(BiomeType other) {
        // Ruins can transition to most moderate biomes
        return other == BiomeType.PLAINS ||
            other == BiomeType.FOREST ||
            other == BiomeType.HAUNTED;
    }

    public double getBiomeHeightInfluence(BiomeType biomeType) {
        switch (biomeType) {
            case BIG_MOUNTAINS:
                return 1.5;     // Highest elevation

            case SNOW:
                return 1.2;     // Tall snowy peaks

            case RUINS:
                return 0.9;     // Slightly elevated with occasional ruins

            case HAUNTED:
                return 1.1;     // Moderately high for atmosphere

            case DESERT:
                return 0.7;     // Generally flat with dunes

            case FOREST:
            case RAIN_FOREST:
                return 0.8;     // Rolling hills

            case PLAINS:
                return 0.6;     // Flattest terrain

            default:
                return 1.0;     // Default height
        }
    }

    // ─── INNER CLASSES: BiomeData & TypeAdapter ─────────────────────────────
    public static class BiomeData implements Serializable {
        private String name;
        private String type;
        private ArrayList<Integer> allowedTileTypes;
        private HashMap<Integer, Integer> tileDistribution;
        private int chunkX;
        private List<String> spawnableObjects;
        private Map<String, Double> spawnChances;
        private int chunkY;
        private BiomeType primaryBiomeType;
        private long lastModified;

        public BiomeData() {
            this.tileDistribution = new HashMap<>();
            this.allowedTileTypes = new ArrayList<>();
            this.spawnableObjects = new ArrayList<>();
            this.spawnChances = new HashMap<>();
        }

        public int getChunkX() {
            return chunkX;
        }

        public List<String> getSpawnableObjects() {
            return spawnableObjects;
        }

        public void setSpawnableObjects(List<String> objects) {
            this.spawnableObjects = objects;
        }

        public Map<String, Double> getSpawnChances() {
            return spawnChances;
        }

        public void setSpawnChances(Map<String, Double> chances) {
            this.spawnChances = chances;
        }

        public int getChunkY() {
            return chunkY;
        }

        public long getLastModified() {
            return lastModified;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public ArrayList<Integer> getAllowedTileTypes() {
            return allowedTileTypes;
        }

        public void setAllowedTileTypes(List<Integer> types) {
            if (types != null) {
                this.allowedTileTypes.clear();
                this.allowedTileTypes.addAll(types);
            }
        }

        public HashMap<Integer, Integer> getTileDistribution() {
            return tileDistribution;
        }

        public void setTileDistribution(Map<Integer, Integer> distribution) {
            if (distribution != null) {
                this.tileDistribution.clear();
                this.tileDistribution.putAll(distribution);
            }
        }

        public BiomeType getPrimaryBiomeType() {
            return primaryBiomeType;
        }

        public void setPrimaryBiomeType(BiomeType type) {
            this.primaryBiomeType = type;
        }

        public void validate() {
            if (tileDistribution == null) tileDistribution = new HashMap<>();
            if (allowedTileTypes == null) allowedTileTypes = new ArrayList<>();
            if (spawnableObjects == null) spawnableObjects = new ArrayList<>();
            if (spawnChances == null) spawnChances = new HashMap<>();
        }
    }

    private static class BiomeDataTypeAdapter extends TypeAdapter<BiomeData> {
        @Override
        public void write(JsonWriter out, BiomeData value) throws IOException {
            out.beginObject();
            if (value.getName() != null) out.name("name").value(value.getName());
            if (value.getType() != null) out.name("type").value(value.getType());
            if (value.getAllowedTileTypes() != null && !value.getAllowedTileTypes().isEmpty()) {
                out.name("allowedTileTypes");
                out.beginArray();
                for (Integer type : value.getAllowedTileTypes()) {
                    if (type != null) out.value(type);
                }
                out.endArray();
            }
            if (value.getTileDistribution() != null && !value.getTileDistribution().isEmpty()) {
                out.name("tileDistribution");
                out.beginObject();
                for (Map.Entry<Integer, Integer> entry : value.getTileDistribution().entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        out.name(entry.getKey().toString()).value(entry.getValue());
                    }
                }
                out.endObject();
            }
            if (value.getPrimaryBiomeType() != null) {
                out.name("primaryBiomeType").value(value.getPrimaryBiomeType().name());
            }
            out.endObject();
        }

        @Override
        public BiomeData read(JsonReader in) throws IOException {
            BiomeData data = new BiomeData();
            in.beginObject();
            while (in.hasNext()) {
                String fieldName = in.nextName();
                try {
                    switch (fieldName) {
                        case "name":
                            data.setName(in.nextString());
                            break;
                        case "type":
                            data.setType(in.nextString());
                            break;
                        case "allowedTileTypes":
                            in.beginArray();
                            while (in.hasNext()) {
                                try {
                                    data.getAllowedTileTypes().add(in.nextInt());
                                } catch (Exception e) {
                                    in.skipValue();
                                    GameLogger.error("Skipped invalid allowed tile type");
                                }
                            }
                            in.endArray();
                            break;
                        case "tileDistribution":
                            in.beginObject();
                            while (in.hasNext()) {
                                try {
                                    String key = in.nextName();
                                    int value = in.nextInt();
                                    data.getTileDistribution().put(Integer.parseInt(key), value);
                                } catch (Exception e) {
                                    in.skipValue();
                                    GameLogger.error("Skipped invalid tile distribution entry");
                                }
                            }
                            in.endObject();
                            break;
                        case "primaryBiomeType":
                            try {
                                data.setPrimaryBiomeType(BiomeType.valueOf(in.nextString()));
                            } catch (Exception e) {
                                in.skipValue();
                            }
                            break;
                        default:
                            in.skipValue();
                            break;
                    }
                } catch (Exception e) {
                    GameLogger.error("Error parsing field " + fieldName + ": " + e.getMessage());
                    in.skipValue();
                }
            }
            in.endObject();
            data.validate();
            return data;
        }
    }
}
