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
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.OpenSimplex2;
import io.github.pokemeetup.utils.storage.GameFileSystem;
import io.github.pokemeetup.utils.textures.TileType;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Refactored BiomeManager
 *
 * <p>This class is responsible for determining biome values using noise functions,
 * domain–warping to smooth boundaries, and caching a low–resolution biome matrix.
 *
 * <p>The refactored class is organized as follows:
 * <ol>
 *   <li><b>Constants & Fields:</b> Tuning parameters, seeds, caches, and Voronoi sites.</li>
 *   <li><b>Initialization:</b> Loading JSON biome definitions and generating fallback Voronoi sites.</li>
 *   <li><b>Noise & Domain–Warp Utilities:</b> Encapsulated in the inner {@code NoiseUtil} class.</li>
 *   <li><b>Biome Classification:</b> Encapsulated in the inner {@code BiomeClassifier} class.</li>
 *   <li><b>Biome Sampling & Blending:</b> A low–resolution biome matrix is built per–chunk and its
 *       samples are bilinearly blended via {@code blendBiomeSamples()}.</li>
 *   <li><b>JSON Loading & Serialization:</b> Remains largely as before.</li>
 * </ol>
 */
public class BiomeManager {

    // -------------------------------
    // Tuning and Sampling Constants
    // -------------------------------
    private static final double RUINS_THRESHOLD = 0.65;
    private static final double COLD_THRESHOLD = 0.35;
    private static final double HOT_THRESHOLD = 0.65;
    private static final double DRY_THRESHOLD = 0.35;
    private static final double WET_THRESHOLD = 0.65;

    private static final float TEMPERATURE_SCALE = 0.00005f;
    private static final float MOISTURE_SCALE = 0.00005f;
    private static final float WARP_SCALE = 0.00001f;
    private static final float WARP_STRENGTH = 30f;
    private static final float MOUNTAIN_DETAIL_SCALE = 0.002f;
    private static final float MOUNTAIN_RIDGE_SCALE = 0.0015f;
    private static final float MOUNTAIN_THRESHOLD = 0.85f;
    private static final int BIOME_DOWNSAMPLE = 1;
    private static final float BIOME_JITTER_FACTOR = 0.2f;
    private static final float NOISE_OFFSET = 10000f;

    // -------------------------------
    // Voronoi Site Data for Fallback
    // -------------------------------
    private static final int NUM_BIOME_SITES = 800;
    private static final float WORLD_RADIUS = 100000f;

    // -------------------------------
    // Fields and Caches
    // -------------------------------
    private final long temperatureSeed;
    private final long moistureSeed;
    private final long warpSeed;
    private final long baseSeed;
    private final long mountainSeed;
    private final long detailSeed;
    private final Map<BiomeType, Biome> biomes;
    // Cache: key is chunk coordinates (as Vector2), value is a low–res biome matrix.
    private final Map<Vector2, BiomeTransitionResult[][]> chunkBiomeCache = new ConcurrentHashMap<>();
    // List of Voronoi sites used as fallback in domain–warped biome transitions.
    private final List<BiomeSite> biomeSites = new ArrayList<>();

    // -------------------------------
    // Constructor and Initialization
    // -------------------------------
    public BiomeManager(long baseSeed) {
        this.baseSeed = baseSeed;
        this.biomes = new HashMap<>();
        this.temperatureSeed = baseSeed + 1000;
        this.moistureSeed = baseSeed + 2000;
        this.mountainSeed = baseSeed + 3000;
        this.warpSeed = baseSeed + 4000;
        this.detailSeed = baseSeed + 5000;
        loadBiomesFromJson();
        generateBiomeSites(baseSeed);
    }

    /**
     * Generates Voronoi sites (with a randomly chosen biome type for each site) that are used
     * to blend biomes when no chunk data is available.
     */
    private void generateBiomeSites(long seed) {
        Random rng = new Random(seed ^ 0xDEADBEEF);
        biomeSites.clear();
        for (int i = 0; i < NUM_BIOME_SITES; i++) {
            float x = (rng.nextFloat() - 0.5f) * 2f * WORLD_RADIUS;
            float y = (rng.nextFloat() - 0.5f) * 2f * WORLD_RADIUS;
            BiomeType randomBiome = pickRandomBiomeType(rng);
            biomeSites.add(new BiomeSite(x, y, randomBiome));
        }
        GameLogger.info("BiomeManager - Generated " + biomeSites.size() + " Voronoi sites for domain-warped transitions.");
    }

    // -------------------------------
    // Public Biome Sampling Methods
    // -------------------------------

    /**
     * Returns the biome transition result at the given world coordinates.
     * If a chunk exists, its biome is returned; otherwise the low–resolution biome matrix is used.
     */
    public BiomeTransitionResult getBiomeAt(float worldX, float worldY) {
        int chunkX = (int) Math.floor(worldX / (Chunk.CHUNK_SIZE * World.TILE_SIZE));
        int chunkY = (int) Math.floor(worldY / (Chunk.CHUNK_SIZE * World.TILE_SIZE));
        Vector2 chunkPos = new Vector2(chunkX, chunkY);
        try {
            World world = GameContext.get().getWorld();
            if (world != null) {
                Chunk existing = world.getChunks().get(chunkPos);
                if (existing != null) {
                    // If chunk data is present, return its biome (no blending required)
                    return new BiomeTransitionResult(existing.getBiome(), null, 1.0f);
                }
            }
        } catch (IllegalStateException ignored) {
        }
        return getBiomeAtCached(worldX, worldY);
    }

    /**
     * Returns a biome transition result for the given world coordinates using a low–resolution biome matrix.
     * This version blends four nearby samples to yield a smoother transition.
     */
    public BiomeTransitionResult getBiomeAtCached(float worldX, float worldY) {
        int chunkX = (int) Math.floor(worldX / (Chunk.CHUNK_SIZE * World.TILE_SIZE));
        int chunkY = (int) Math.floor(worldY / (Chunk.CHUNK_SIZE * World.TILE_SIZE));
        BiomeTransitionResult[][] matrix = computeBiomeMatrixForChunk(chunkX, chunkY);
        // Compute local coordinates within the chunk
        float baseWorldX = chunkX * Chunk.CHUNK_SIZE * World.TILE_SIZE;
        float baseWorldY = chunkY * Chunk.CHUNK_SIZE * World.TILE_SIZE;
        float localX = worldX - baseWorldX;
        float localY = worldY - baseWorldY;
        int gridWidth = matrix.length;
        int gridHeight = matrix[0].length;
        float gridSpacingX = (Chunk.CHUNK_SIZE * World.TILE_SIZE) / (gridWidth - 1);
        float gridSpacingY = (Chunk.CHUNK_SIZE * World.TILE_SIZE) / (gridHeight - 1);
        // Compute “grid coordinates” for blending
        float gx = localX / gridSpacingX;
        float gy = localY / gridSpacingY;
        return blendBiomeSamples(matrix, gx, gy);
    }

    /**
     * Computes a low–resolution biome matrix for the specified chunk.
     * Each sample is jittered slightly (via BIOME_JITTER_FACTOR) so that boundaries are less grid–like.
     */
    private BiomeTransitionResult[][] computeBiomeMatrixForChunk(int chunkX, int chunkY) {
        Vector2 key = new Vector2(chunkX, chunkY);
        if (chunkBiomeCache.containsKey(key)) {
            return chunkBiomeCache.get(key);
        }
        int size = Chunk.CHUNK_SIZE;
        int outWidth = (size + BIOME_DOWNSAMPLE - 1) / BIOME_DOWNSAMPLE;
        int outHeight = (size + BIOME_DOWNSAMPLE - 1) / BIOME_DOWNSAMPLE;
        BiomeTransitionResult[][] matrix = new BiomeTransitionResult[outWidth][outHeight];
        float baseWorldX = chunkX * size * World.TILE_SIZE;
        float baseWorldY = chunkY * size * World.TILE_SIZE;
        for (int bx = 0; bx < outWidth; bx++) {
            for (int by = 0; by < outHeight; by++) {
                float gridSampleX = baseWorldX + bx * World.TILE_SIZE;
                float gridSampleY = baseWorldY + by * World.TILE_SIZE;
                // Add a small jitter to each sample
                float jitterX = (float) (OpenSimplex2.noise2(baseSeed + 123, gridSampleX * 0.01f, gridSampleY * 0.01f) * (World.TILE_SIZE * BIOME_JITTER_FACTOR));
                float jitterY = (float) (OpenSimplex2.noise2(baseSeed + 456, gridSampleX * 0.01f, gridSampleY * 0.01f) * (World.TILE_SIZE * BIOME_JITTER_FACTOR));
                float sampleX = gridSampleX + jitterX;
                float sampleY = gridSampleY + jitterY;
                // Use the fallback biome sampling (which blends via domain warp & Voronoi) for each sample
                matrix[bx][by] = fallbackNoiseBiome(sampleX, sampleY);
            }
        }
        chunkBiomeCache.put(key, matrix);
        return matrix;
    }

    /**
     * Blends four adjacent biome samples (from the low–res matrix) using bilinear weights.
     * The result is a smooth biome transition based on weighted “votes” for each biome.
     */
    private BiomeTransitionResult blendBiomeSamples(BiomeTransitionResult[][] matrix, float gx, float gy) {
        int gridWidth = matrix.length;
        int gridHeight = matrix[0].length;
        int ix = MathUtils.floor(gx);
        int iy = MathUtils.floor(gy);
        ix = MathUtils.clamp(ix, 0, gridWidth - 2);
        iy = MathUtils.clamp(iy, 0, gridHeight - 2);
        float fx = gx - ix;
        float fy = gy - iy;
        float w00 = (1 - fx) * (1 - fy);
        float w10 = fx * (1 - fy);
        float w01 = (1 - fx) * fy;
        float w11 = fx * fy;

        // Use weighted voting for primary and secondary biome “votes”
        Map<Biome, Float> primaryWeights = new HashMap<>();
        Map<Biome, Float> secondaryWeights = new HashMap<>();

        BiomeTransitionResult[] samples = new BiomeTransitionResult[]{
            matrix[ix][iy],
            matrix[ix + 1][iy],
            matrix[ix][iy + 1],
            matrix[ix + 1][iy + 1]
        };
        float[] weights = new float[]{w00, w10, w01, w11};

        for (int i = 0; i < 4; i++) {
            BiomeTransitionResult btr = samples[i];
            float w = weights[i];
            // btr.getTransitionFactor() is defined as: 1 = fully primary; 0 = fully secondary.
            if (btr.getPrimaryBiome() != null) {
                float pWeight = w * btr.getTransitionFactor();
                primaryWeights.merge(btr.getPrimaryBiome(), pWeight, Float::sum);
            }
            if (btr.getSecondaryBiome() != null) {
                float sWeight = w * (1 - btr.getTransitionFactor());
                secondaryWeights.merge(btr.getSecondaryBiome(), sWeight, Float::sum);
            } else {
                // If no secondary exists, add the full weight to primary.
            }
        }

        // Combine votes from primary and secondary into a total weight per biome.
        Map<Biome, Float> totalWeights = new HashMap<>();
        for (Map.Entry<Biome, Float> entry : primaryWeights.entrySet()) {
            totalWeights.put(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<Biome, Float> entry : secondaryWeights.entrySet()) {
            totalWeights.merge(entry.getKey(), entry.getValue(), Float::sum);
        }

        // Determine dominant (highest vote) biome.
        Biome dominant = null;
        float dominantWeight = 0;
        for (Map.Entry<Biome, Float> entry : totalWeights.entrySet()) {
            if (entry.getValue() > dominantWeight) {
                dominant = entry.getKey();
                dominantWeight = entry.getValue();
            }
        }
        // Sum weights for all other biomes.
        float otherWeight = 0;
        for (Map.Entry<Biome, Float> entry : totalWeights.entrySet()) {
            if (!entry.getKey().equals(dominant)) {
                otherWeight += entry.getValue();
            }
        }
        // If no other biome contributes, the cell is fully the dominant biome.
        if (otherWeight == 0) {
            return new BiomeTransitionResult(dominant, null, 1f);
        }
        // Otherwise, choose the secondary biome as the one with the highest weight among non–dominant ones.
        Biome secondary = null;
        float secondaryWeight = 0;
        for (Map.Entry<Biome, Float> entry : totalWeights.entrySet()) {
            if (!entry.getKey().equals(dominant) && entry.getValue() > secondaryWeight) {
                secondary = entry.getKey();
                secondaryWeight = entry.getValue();
            }
        }
        // Compute a blended transition factor (0 means fully secondary, 1 fully dominant)
        float t = dominantWeight / (dominantWeight + secondaryWeight);
        t = smoothStep(t); // apply a smoothing curve to further ease transitions
        return new BiomeTransitionResult(dominant, secondary, t);
    }

    /**
     * Fallback method: computes a biome transition result at (worldX, worldY) using a domain–warped noise approach
     * and a set of Voronoi sites. (This is used both for each low–res sample and when no chunk exists.)
     */
    private BiomeTransitionResult fallbackNoiseBiome(float worldX, float worldY) {
        if (biomeSites.isEmpty()) {
            GameLogger.error("BiomeManager: biomeSites is empty! Falling back to PLAINS.");
            return new BiomeTransitionResult(getBiome(BiomeType.PLAINS), null, 1f);
        }
        float[] warped = NoiseUtil.multiLayerDomainWarp(worldX, worldY, warpSeed);
        float wx = warped[0];
        float wy = warped[1];
        // Find the two nearest Voronoi sites.
        BiomeSite nearest = null, secondNearest = null;
        double nearestDist = Double.MAX_VALUE, secondDist = Double.MAX_VALUE;
        for (BiomeSite site : biomeSites) {
            double dx = wx - site.x;
            double dy = wy - site.y;
            double distSq = dx * dx + dy * dy;
            if (distSq < nearestDist) {
                secondNearest = nearest;
                secondDist = nearestDist;
                nearest = site;
                nearestDist = distSq;
            } else if (distSq < secondDist) {
                secondNearest = site;
                secondDist = distSq;
            }
        }
        if (nearest == null) {
            GameLogger.error("Fallback biome: no nearest site found. Using PLAINS.");
            return new BiomeTransitionResult(getBiome(BiomeType.PLAINS), null, 1f);
        }
        if (secondNearest == null) {
            return new BiomeTransitionResult(getBiome(nearest.biomeType), null, 1f);
        }
        float d1 = (float) Math.sqrt(nearestDist);
        float d2 = (float) Math.sqrt(secondDist);
        if (d2 < 1e-5f) {
            return new BiomeTransitionResult(getBiome(nearest.biomeType), null, 1f);
        }
        // Compute a normalized blend value (raw between 0 and 1) then smooth it.
        float raw = 1f - (d1 / d2);
        float t = smoothStep(MathUtils.clamp((raw - 0.45f) / 0.1f, 0f, 1f));
        return new BiomeTransitionResult(getBiome(nearest.biomeType), getBiome(secondNearest.biomeType), t);
    }

    /**
     * A standard smoothstep interpolation (t*t*(3-2*t)).
     */
    private static float smoothStep(float t) {
        return t * t * (3f - 2f * t);
    }

    // -------------------------------
    // Biome Type Determination (Delegated)
    // -------------------------------

    /**
     * Determines the biome type based on temperature, moisture, and additional noise.
     */
    private BiomeType determineBiomeType(double temperature, double moisture) {
        return BiomeClassifier.determineBiomeType(temperature, moisture, detailSeed, mountainSeed);
    }

    // -------------------------------
    // JSON Loading and Default Biomes
    // -------------------------------
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
            List<String> spawnable = new ArrayList<>();
            JsonArray array = json.getAsJsonArray("spawnableObjects");
            for (JsonElement element : array) {
                spawnable.add(element.getAsString());
            }
            data.setSpawnableObjects(spawnable);
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

    public Biome getBiome(BiomeType type) {
        Biome biome = biomes.get(type);
        if (biome == null) {
            GameLogger.error("Missing biome type: " + type + ", falling back to PLAINS");
            return biomes.get(BiomeType.PLAINS);
        }
        return biome;
    }

    // -------------------------------
    // Debug Methods (Optional)
    // -------------------------------
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

    // -------------------------------
    // Inner Classes
    // -------------------------------

    /**
     * Encapsulates noise and domain–warping utilities.
     */
    private static class NoiseUtil {

        /**
         * Applies several passes of noise–based warping to (x,y).
         */
        public static float[] domainWarp(float x, float y, long warpSeed) {
            float[] warped = new float[]{x, y};
            float amplitude = WARP_STRENGTH;
            float frequency = WARP_SCALE;
            for (int i = 0; i < 3; i++) {
                float warpX = (float) OpenSimplex2.noise2(warpSeed + i, warped[0] * frequency, warped[1] * frequency) * amplitude;
                float warpY = (float) OpenSimplex2.noise2(warpSeed + i + 1000, warped[0] * frequency, warped[1] * frequency) * amplitude;
                warped[0] += warpX;
                warped[1] += warpY;
                amplitude *= 0.5f;
                frequency *= 1.8f;
            }
            return warped;
        }

        /**
         * Applies an additional multi–layer warping pass.
         */
        public static float[] multiLayerDomainWarp(float x, float y, long warpSeed) {
            float[] warp = domainWarp(x, y, warpSeed);
            float amplitude2 = WARP_STRENGTH * 0.35f;
            float frequency2 = WARP_SCALE * 2.0f;
            float wx = warp[0];
            float wy = warp[1];
            for (int i = 0; i < 2; i++) {
                float warpX = (float) OpenSimplex2.noise2(warpSeed + 999 + i, wx * frequency2, wy * frequency2) * amplitude2;
                float warpY = (float) OpenSimplex2.noise2(warpSeed + 1999 + i, wx * frequency2, wy * frequency2) * amplitude2;
                wx += warpX;
                wy += warpY;
                amplitude2 *= 0.55f;
                frequency2 *= 1.8f;
            }
            float amplitude3 = WARP_STRENGTH * 0.1f;
            float frequency3 = WARP_SCALE * 4.0f;
            wx += (float) OpenSimplex2.noise2(warpSeed + 3000, wx * frequency3, wy * frequency3) * amplitude3;
            wy += (float) OpenSimplex2.noise2(warpSeed + 4000, wx * frequency3, wy * frequency3) * amplitude3;
            return new float[]{wx, wy};
        }
    }

    /**
     * Encapsulates biome classification logic based on temperature, moisture, and noise.
     */
    private static class BiomeClassifier {

        public static BiomeType determineBiomeType(double temperature, double moisture, long detailSeed, long mountainSeed) {
            // Introduce a slight regional variation.
            double regionalVar = OpenSimplex2.noise2(detailSeed + 3000, temperature * 2, moisture * 2) * 0.05;
            double adjustedTemp = temperature + regionalVar;
            double adjustedMoist = moisture + regionalVar;
            if (shouldGenerateRuins(adjustedTemp, adjustedMoist, detailSeed)) {
                return BiomeType.RUINS;
            }
            BiomeType chosen;
            if (adjustedTemp < COLD_THRESHOLD) {
                chosen = determineColderBiomes(adjustedMoist, detailSeed);
            } else if (adjustedTemp > HOT_THRESHOLD) {
                chosen = determineHotterBiomes(adjustedMoist, detailSeed);
            } else {
                chosen = determineTemperateBiomes(adjustedTemp, adjustedMoist, detailSeed);
            }
            double mountainNoise = OpenSimplex2.noise2(mountainSeed, adjustedTemp * 5, adjustedMoist * 5);
            if (shouldGenerateMountains(adjustedTemp, adjustedMoist, mountainNoise, mountainSeed)) {
                chosen = BiomeType.BIG_MOUNTAINS;
            }
            return chosen;
        }

        private static boolean shouldGenerateRuins(double temp, double moist, long detailSeed) {
            boolean moderate = (temp > 0.3 && temp < 0.7 && moist > 0.3 && moist < 0.7);
            if (!moderate) return false;
            double ruinsNoise = OpenSimplex2.noise2(detailSeed + 2000, temp * 2, moist * 2);
            double structureNoise = OpenSimplex2.noise2(detailSeed + 2500, temp * 4, moist * 4);
            double historyNoise = OpenSimplex2.noise2(detailSeed + 3000, temp * 1.5, moist * 1.5);
            double combined = ruinsNoise * 0.4 + structureNoise * 0.4 + historyNoise * 0.2;
            return combined > RUINS_THRESHOLD;
        }

        private static BiomeType determineTemperateBiomes(double temp, double moist, long detailSeed) {
            double variety = OpenSimplex2.noise2(detailSeed + 1000, temp * 3, moist * 3) * 0.5 + 0.5;
            if (moist > WET_THRESHOLD) {
                return variety > 0.5 ? BiomeType.RAIN_FOREST : BiomeType.FOREST;
            } else if (moist < DRY_THRESHOLD) {
                return BiomeType.PLAINS;
            }
            return variety > 0.6 ? BiomeType.FOREST : (variety > 0.3 ? BiomeType.PLAINS : BiomeType.HAUNTED);
        }

        private static BiomeType determineColderBiomes(double moist, long detailSeed) {
            double variety = OpenSimplex2.noise2(detailSeed + 3000, moist * 4, moist * 4) * 0.5 + 0.5;
            if (moist > WET_THRESHOLD) {
                return BiomeType.SNOW;
            } else if (moist < DRY_THRESHOLD) {
                return BiomeType.PLAINS;
            }
            return variety > 0.2 ? BiomeType.SNOW : BiomeType.HAUNTED;
        }

        private static BiomeType determineHotterBiomes(double moist, long detailSeed) {
            if (moist < DRY_THRESHOLD) {
                return BiomeType.DESERT;
            } else if (moist > WET_THRESHOLD) {
                return BiomeType.RAIN_FOREST;
            }
            double variety = OpenSimplex2.noise2(detailSeed + 2000, moist * 4, moist * 4) * 0.5 + 0.5;
            return variety > 0.5 ? BiomeType.PLAINS : BiomeType.DESERT;
        }

        private static boolean shouldGenerateMountains(double temp, double moist, double mountainNoise, long mountainSeed) {
            double mountainProb = (mountainNoise + 1.0) / 2.0;
            if (temp < COLD_THRESHOLD) {
                mountainProb *= 1.1;
            }
            if (moist > WET_THRESHOLD) {
                mountainProb *= 1.1;
            }
            double ridge = Math.abs(OpenSimplex2.noise2(mountainSeed + 1000, temp * MOUNTAIN_RIDGE_SCALE, moist * MOUNTAIN_RIDGE_SCALE));
            double detail = OpenSimplex2.noise2(mountainSeed + 2000, temp * MOUNTAIN_DETAIL_SCALE, moist * MOUNTAIN_DETAIL_SCALE);
            double combined = mountainProb * 0.5 + ridge * 0.3 + Math.abs(detail) * 0.2;
            return combined > MOUNTAIN_THRESHOLD;
        }
    }

    /**
     * Simple inner class to hold a Voronoi biome site.
     */
    private static class BiomeSite {
        public float x, y;
        public BiomeType biomeType;

        public BiomeSite(float x, float y, BiomeType type) {
            this.x = x;
            this.y = y;
            this.biomeType = type;
        }
    }

    /**
     * Picks a random biome type from the candidate list.
     */
    private BiomeType pickRandomBiomeType(Random rng) {
        BiomeType[] candidates = {
            BiomeType.PLAINS, BiomeType.DESERT, BiomeType.SNOW,
            BiomeType.FOREST, BiomeType.RAIN_FOREST, BiomeType.HAUNTED,
            BiomeType.RUINS
        };
        return candidates[rng.nextInt(candidates.length)];
    }

    // -------------------------------
    // Inner Classes for Data Serialization
    // -------------------------------
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
