package io.github.pokemeetup.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.system.gameplay.overworld.mechanics.MountainTileManager;
import io.github.pokemeetup.utils.GameLogger;

import io.github.pokemeetup.utils.OpenSimplex2;
import io.github.pokemeetup.utils.storage.GameFileSystem;
import io.github.pokemeetup.utils.textures.TextureManager;
import io.github.pokemeetup.utils.textures.TileType;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

public class BiomeManager {
    private static final double RUINS_THRESHOLD = 0.65; // Lowered from 0.85
    private static final double COLD_THRESHOLD = 0.35; // Adjusted for better distribution
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
    private final long temperatureSeed;
    private final long moistureSeed;
    private final long warpSeed;
    private final long baseSeed;
    private final long mountainSeed;
    private final long detailSeed;
    private final Map<BiomeType, Biome> biomes;

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

    private float[] domainWarp(float x, float y) {
        float[] warped = new float[]{x, y};
        float amplitude = WARP_STRENGTH;
        float frequency = WARP_SCALE;

        for (int i = 0; i < 3; i++) {
            float warpX = (float) OpenSimplex2.noise2(warpSeed + i,
                warped[0] * frequency,
                warped[1] * frequency) * amplitude;

            float warpY = (float) OpenSimplex2.noise2(warpSeed + i + 1000,
                warped[0] * frequency,
                warped[1] * frequency) * amplitude;

            warped[0] += warpX;
            warped[1] += warpY;

            amplitude *= 0.5f;
            frequency *= 1.8f;
        }
        return warped;
    }


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

    private boolean shouldGenerateMountains(double temp, double moisture, double mountainNoise) {
        double mountainProbability = (mountainNoise + 1.0) / 2.0;

        if (temp < COLD_THRESHOLD) {
            mountainProbability *= 1.3;
        }
        if (moisture > WET_THRESHOLD) {
            mountainProbability *= 1.2;
        }

        double ridgeNoise = Math.abs(OpenSimplex2.noise2(mountainSeed + 1000,
            temp * MOUNTAIN_RIDGE_SCALE,
            moisture * MOUNTAIN_RIDGE_SCALE));

        double detailNoise = OpenSimplex2.noise2(mountainSeed + 2000,
            temp * MOUNTAIN_DETAIL_SCALE,
            moisture * MOUNTAIN_DETAIL_SCALE);

        double combinedProbability = mountainProbability * 0.5 +
            ridgeNoise * 0.3 +
            Math.abs(detailNoise) * 0.2;

        return combinedProbability > MOUNTAIN_THRESHOLD;
    }

    private void addMountainOverlay(Chunk chunk, int x, int y, double height,
                                    MountainTileManager tileManager, Random random) {
        int[][] tileData = chunk.getTileData();
        BiomeType biomeType = chunk.getBiome().getType();

        // Determine overlay type based on height and biome
        if (height > 0.8 && (biomeType == BiomeType.SNOW || biomeType == BiomeType.BIG_MOUNTAINS)) {
            // Heavy snow overlay for high altitudes
            TextureRegion snowOverlay = tileManager.getTile(
                random.nextBoolean() ?
                    MountainTileManager.MountainTileType.SNOW_OVERLAY_1 :
                    MountainTileManager.MountainTileType.SNOW_OVERLAY_2
            );
            applyOverlay(chunk, x, y, snowOverlay);
        } else if (height > 0.6 && random.nextFloat() < 0.3) {
            // Occasional grass patches on lower slopes
            TextureRegion grassOverlay = tileManager.getTile(
                MountainTileManager.MountainTileType.GRASS_OVERLAY
            );
            applyOverlay(chunk, x, y, grassOverlay);
        }

        // Add random rocks with decreasing probability as height increases
        if (random.nextFloat() < (1.0 - height) * 0.2) {
            TextureRegion rockOverlay = tileManager.getRandomRock(random);
            applyOverlay(chunk, x, y, rockOverlay);
        }
    }


    private boolean shouldGenerateRuins(double temp, double moisture) {
        // Expanded temperature and moisture ranges for ruins
        boolean moderateClimate = temp > 0.3 && temp < 0.7 &&
            moisture > 0.3 && moisture < 0.7;

        if (!moderateClimate) return false;

        // Multiple noise layers for natural ruins placement with adjusted weights
        double ruinsNoise = OpenSimplex2.noise2(detailSeed + 2000,
            temp * 2, moisture * 2);
        double structureNoise = OpenSimplex2.noise2(detailSeed + 2500,
            temp * 4, moisture * 4);
        double historyNoise = OpenSimplex2.noise2(detailSeed + 3000,
            temp * 1.5, moisture * 1.5);

        // Adjusted weights to increase ruins frequency
        double combinedNoise = ruinsNoise * 0.4 +
            structureNoise * 0.4 +
            historyNoise * 0.2;

        return combinedNoise > RUINS_THRESHOLD;
    }

    private int convertTextureToTileId(MountainTileManager.MountainTileType tileType) {
        // Convert mountain tile types to game tile IDs
        switch (tileType) {
            case BASE_SNOW:
                return TileType.MOUNTAIN_SNOW_BASE;
            case SLOPE_LEFT:
                return TileType.MOUNTAIN_SLOPE_LEFT;
            case SLOPE_RIGHT:
                return TileType.MOUNTAIN_SLOPE_RIGHT;
            default:
                return TileType.MOUNTAIN_BASE;
        }
    }

    private void applyOverlay(Chunk chunk, int x, int y, TextureRegion overlay) {
        try {
            int[][] tileData = chunk.getTileData();
            if (tileData == null) return;

            // Get current tile value
            int currentTile = tileData[x][y];

            // Get overlay tile ID
            int overlayId = getOverlayTileId(overlay);
            if (overlayId == -1) return;

            // Clear any existing overlay
            currentTile &= BASE_TILE_MASK;

            // Apply new overlay
            currentTile |= (overlayId << OVERLAY_SHIFT);

            // Update tile data
            tileData[x][y] = currentTile;
            chunk.setDirty(true);

        } catch (Exception e) {
            GameLogger.error("Failed to apply overlay: " + e.getMessage());
        }
    }

    private int getOverlayTileId(TextureRegion overlay) {
        // Match MountainTileManager's tile types to game tile IDs
        if (overlay == null) return 1;


        GameLogger.error("Unknown overlay texture - no matching tile ID found");
        return 1;
    }

    private BiomeType determineTemperateBiomes(double temperature, double moisture) {
        // Calculate local variation for more interesting distribution
        double varietyNoise = OpenSimplex2.noise2(detailSeed + 1000,
            temperature * 3, moisture * 3) * 0.5 + 0.5;

        if (moisture > WET_THRESHOLD) {
            // Wet temperate regions favor forests
            return varietyNoise > 0.5 ? BiomeType.RAIN_FOREST : BiomeType.FOREST;
        } else if (moisture < DRY_THRESHOLD) {
            // Dry temperate regions favor plains
            return BiomeType.PLAINS;
        }

        // Moderate moisture creates varied landscape
        if (varietyNoise > 0.6) {
            return BiomeType.FOREST;
        } else if (varietyNoise > 0.3) {
            return BiomeType.PLAINS;
        } else {
            return BiomeType.HAUNTED; // Haunted biome is now rarer
        }
    }


    private BiomeType determineColderBiomes(double moisture) {
        // Adjusted variety noise calculation
        double varietyNoise = OpenSimplex2.noise2(detailSeed + 3000,
            moisture * 4, moisture * 4) * 0.5 + 0.5;

        if (moisture > WET_THRESHOLD) {
            // Cold and wet regions are snowy
            return BiomeType.SNOW;
        } else if (moisture < DRY_THRESHOLD) {
            // Cold and dry regions tend to be plains
            return BiomeType.PLAINS;
        }

        // Moderate moisture in cold regions
        return varietyNoise > 0.2 ? BiomeType.SNOW : BiomeType.HAUNTED; // Haunted biome is less likely
    }

    private BiomeType determineHotterBiomes(double moisture) {
        if (moisture < DRY_THRESHOLD) {
            return BiomeType.DESERT; // Hot and dry
        } else if (moisture > WET_THRESHOLD) {
            return BiomeType.RAIN_FOREST; // Hot and wet
        }

        // Moderate moisture in hot regions
        double varietyNoise = OpenSimplex2.noise2(detailSeed + 2000,
            moisture * 4, moisture * 4) * 0.5 + 0.5;

        return varietyNoise > 0.5 ? BiomeType.PLAINS : BiomeType.DESERT;
    }

    private MountainTileManager.MountainTileType determineSlopeTileType(int x, int y, Chunk chunk) {
        int[][] heightMap = chunk.getTileData();

        // Check surrounding tiles to determine slope direction
        boolean higherLeft = isHigherTile(x - 1, y, heightMap);
        boolean higherRight = isHigherTile(x + 1, y, heightMap);
        boolean higherTop = isHigherTile(x, y + 1, heightMap);
        boolean higherBottom = isHigherTile(x, y - 1, heightMap);

        // Determine corners first
        if (higherTop && higherLeft) {
            return MountainTileManager.MountainTileType.CORNER_TOP_LEFT;
        } else if (higherTop && higherRight) {
            return MountainTileManager.MountainTileType.CORNER_TOP_RIGHT;
        } else if (higherBottom && higherLeft) {
            return MountainTileManager.MountainTileType.CORNER_BOTTOM_LEFT;
        } else if (higherBottom && higherRight) {
            return MountainTileManager.MountainTileType.CORNER_BOTTOM_RIGHT;
        }

        // Determine slopes
        if (higherLeft) {
            return MountainTileManager.MountainTileType.SLOPE_LEFT;
        } else if (higherRight) {
            return MountainTileManager.MountainTileType.SLOPE_RIGHT;
        }

        // Default to cliff face if no specific direction is determined
        return MountainTileManager.MountainTileType.CLIFF_FACE;
    }

    private boolean isHigherTile(int x, int y, int[][] heightMap) {
        // Validate coordinates
        if (x < 0 || x >= Chunk.CHUNK_SIZE || y < 0 || y >= Chunk.CHUNK_SIZE) {
            return false;
        }

        int currentHeight = heightMap[x][y];
        if (currentHeight == -1) return false;  // Not a mountain tile

        // Check all adjacent tiles (including diagonals)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;  // Skip self

                int nx = x + dx;
                int ny = y + dy;

                // Skip out of bounds neighbors
                if (nx < 0 || nx >= Chunk.CHUNK_SIZE ||
                    ny < 0 || ny >= Chunk.CHUNK_SIZE) {
                    continue;
                }

                // Get neighbor's height
                int neighborHeight = heightMap[nx][ny];
                if (neighborHeight == -1) {
                    // Consider non-mountain tiles as lower
                    return true;
                }

                // Check if current tile is significantly higher
                if (neighborHeight - currentHeight > HEIGHT_EPSILON) {
                    return false;
                }
            }
        }

        // Additional checks for mountain peaks
        boolean isPeak = true;
        int higherNeighbors = 0;
        int totalValidNeighbors = 0;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;

                int nx = x + dx;
                int ny = y + dy;

                if (nx < 0 || nx >= Chunk.CHUNK_SIZE ||
                    ny < 0 || ny >= Chunk.CHUNK_SIZE) {
                    continue;
                }

                int neighborHeight = heightMap[nx][ny];
                if (neighborHeight != -1) {
                    totalValidNeighbors++;
                    if (neighborHeight >= currentHeight) {
                        higherNeighbors++;
                    }
                }
            }
        }

        // Consider special cases for peaks and ridges
        if (totalValidNeighbors > 0) {
            // Peak condition - significantly higher than most neighbors
            if (higherNeighbors == 0) {
                return true;
            }

            // Ridge condition - higher than neighbors in at least one direction
            return higherNeighbors <= totalValidNeighbors / 2;
        }

        // Default case - not higher
        return false;
    }


    public void applyMountainFeatures(Chunk chunk, MountainTileManager tileManager) {
        int[][] tileData = chunk.getTileData();
        Random random = new Random(baseSeed + chunk.getChunkX() * 31L + chunk.getChunkY() * 17L);

        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
                float worldX = (chunk.getChunkX() * Chunk.CHUNK_SIZE + x) * World.TILE_SIZE;
                float worldY = (chunk.getChunkY() * Chunk.CHUNK_SIZE + y) * World.TILE_SIZE;

                double height = getMountainHeight(worldX, worldY);
                if (height > 0.6) {
                    // Determine appropriate mountain tile type
                    MountainTileManager.MountainTileType tileType =
                        determineMountainTileType(height, x, y, chunk);

                    // Apply tile
                    TextureRegion tileTexture = tileManager.getTile(tileType);
                    if (tileTexture != null) {
                        tileData[x][y] = convertTextureToTileId(tileType);
                    }

                    // Add overlays where appropriate
                    if (tileManager.shouldHaveSnowOverlay((int) height, random)) {
                        addMountainOverlay(chunk, x, y, height, tileManager, random);
                    }
                }
            }
        }
    }

    private MountainTileManager.MountainTileType determineMountainTileType(
        double height, int x, int y, Chunk chunk) {
        BiomeType biomeType = chunk.getBiome().getType();

        if (height > 0.85) {
            return biomeType == BiomeType.SNOW ?
                MountainTileManager.MountainTileType.PEAK_SNOW :
                MountainTileManager.MountainTileType.PEAK_ROCK;
        } else if (height > 0.75) {
            return determineSlopeTileType(x, y, chunk);
        } else {
            return biomeType == BiomeType.SNOW ?
                MountainTileManager.MountainTileType.BASE_SNOW :
                MountainTileManager.MountainTileType.BASE_BROWN;
        }
    }



    public double getMountainHeight(float worldX, float worldY) {
        // Base mountain height
        double baseHeight = (OpenSimplex2.noise2(mountainSeed,
            worldX * MOUNTAIN_BASE_SCALE,
            worldY * MOUNTAIN_BASE_SCALE) + 1.0) / 2.0;

        // Ridge formation
        double ridgeNoise = Math.abs(OpenSimplex2.noise2(mountainSeed + 1000,
            worldX * MOUNTAIN_RIDGE_SCALE,
            worldY * MOUNTAIN_RIDGE_SCALE));
        ridgeNoise = Math.pow(ridgeNoise, 2.0); // Sharper ridges

        // Detail noise
        double detailNoise = (OpenSimplex2.noise2(detailSeed,
            worldX * MOUNTAIN_DETAIL_SCALE,
            worldY * MOUNTAIN_DETAIL_SCALE) + 1.0) / 2.0;

        // Get current biome and its influence
        BiomeTransitionResult biomeTransition = getBiomeAt(worldX, worldY);
        double biomeInfluence = getBiomeHeightInfluence(biomeTransition.getPrimaryBiome().getType());

        // Combine factors with different weights
        double height = baseHeight * 0.5 +
            ridgeNoise * 0.3 +
            detailNoise * 0.2;

        // Apply biome influence and peak sharpening
        height *= biomeInfluence;
        height = Math.pow(height, 1.5);

        return clamp(height);
    }


    public BiomeTransitionResult getBiomeAt(float worldX, float worldY) {
        // Apply domain warping for more natural transitions
        float[] warped = domainWarp(worldX, worldY);

        // Get base temperature and moisture values
        double temperature = getNoiseValue(warped[0], warped[1], temperatureSeed, TEMPERATURE_SCALE);
        double moisture = getNoiseValue(warped[0], warped[1], moistureSeed, MOISTURE_SCALE);

        // Calculate edge noise for transition variation
        double edge = getNoiseValue(worldX, worldY, warpSeed, TEMPERATURE_SCALE * 2);
        double transitionThreshold = 0.15 + edge * 0.05;

        // Determine primary biome
        BiomeType primaryType = determineBiomeType(temperature, moisture);

        // Check for transition zones
        if (Math.abs(temperature - 0.5) < transitionThreshold ||
            Math.abs(moisture - 0.5) < transitionThreshold) {

            // Get offset coordinates for second biome check
            float[] offset = domainWarp(worldX + 64, worldY + 64);
            double temp2 = getNoiseValue(offset[0], offset[1], temperatureSeed, TEMPERATURE_SCALE);
            double moist2 = getNoiseValue(offset[0], offset[1], moistureSeed, MOISTURE_SCALE);
            BiomeType secondaryType = determineBiomeType(temp2, moist2);

            // Only create transition if biomes are compatible
            if (primaryType != secondaryType && areCompatibleBiomes(primaryType, secondaryType)) {
                float transitionFactor = (float) (Math.abs(temperature - 0.5) / transitionThreshold);
                return new BiomeTransitionResult(getBiome(primaryType), getBiome(secondaryType), transitionFactor);
            }
        }

        return new BiomeTransitionResult(getBiome(primaryType), null, 1.0f);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }


    public void saveChunkBiomeData(Vector2 chunkPos, Chunk chunk, String worldName, boolean isMultiplayer) {
        if (isMultiplayer) {
            return;
        }

        try {
            String baseDir = "worlds/singleplayer/" + worldName + "/biomes/";

            FileHandle saveDir = Gdx.files.local(baseDir);
            if (!saveDir.exists()) {
                saveDir.mkdirs();
            }

            BiomeData biomeData = new BiomeData();
            biomeData.chunkX = (int) chunkPos.x;
            biomeData.chunkY = (int) chunkPos.y;
            biomeData.primaryBiomeType = chunk.getBiome().getType();
            biomeData.lastModified = System.currentTimeMillis();

            // Create new mutable collections
            HashMap<Integer, Integer> distribution = new HashMap<>(chunk.getBiome().getTileDistribution());
            biomeData.setTileDistribution(distribution);

            ArrayList<Integer> allowedTypes = new ArrayList<>(chunk.getBiome().getAllowedTileTypes());
            biomeData.setAllowedTileTypes(allowedTypes);

            String filename = String.format("biome_%d_%d.json", (int) chunkPos.x, (int) chunkPos.y);
            FileHandle biomeFile = saveDir.child(filename);

            // Use Gson for serialization
            Gson gson = new GsonBuilder()
                .registerTypeAdapter(BiomeData.class, new BiomeDataTypeAdapter())
                .setPrettyPrinting()
                .create();

            String jsonContent = gson.toJson(biomeData);
            biomeFile.writeString(jsonContent, false);

            GameLogger.info("Saved biome data for chunk at: " + chunkPos);

        } catch (Exception e) {
            GameLogger.error("Failed to save biome data: " + e.getMessage());
        }
    }

    public BiomeType loadChunkBiomeData(Vector2 chunkPos, String worldName, boolean isMultiplayer) {
        if (isMultiplayer) {
            return null;
        }
        try {
            String baseDir = isMultiplayer ?
                "worlds/" + worldName + "/biomes/" :
                "worlds/singleplayer/" + worldName + "/biomes/";

            String filename = String.format("biome_%d_%d.json", (int) chunkPos.x, (int) chunkPos.y);
            FileHandle biomeFile = Gdx.files.local(baseDir + filename);

            if (!biomeFile.exists()) {
                return null;
            }

            Gson gson = new GsonBuilder()
                .registerTypeAdapter(BiomeData.class, new BiomeDataTypeAdapter())
                .create();

            BiomeData biomeData = gson.fromJson(biomeFile.readString(), BiomeData.class);

            if (biomeData != null && biomeData.primaryBiomeType != null) {
                return biomeData.primaryBiomeType;
            }

        } catch (Exception e) {
            GameLogger.error("Failed to load biome data: " + e.getMessage());
        }
        return null;
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

    // Helper method to get noise value with octaves
    private double getNoiseValue(float x, float y, long seed, float scale) {
        double value = 0;
        double amplitude = 1.0;
        double frequency = 1.0;
        double maxAmplitude = 0;

        for (int i = 0; i < 3; i++) {
            value += amplitude * OpenSimplex2.noise2(seed + i,
                x * scale * frequency,
                y * scale * frequency);

            // Add subtle detail layer
            value += amplitude * 0.2 * OpenSimplex2.noise2(seed + i + 500,
                x * scale * frequency * 2,
                y * scale * frequency * 2);

            maxAmplitude += amplitude * 1.2;
            amplitude *= 0.5;    // Steeper falloff for smoother transitions
            frequency *= 2.0;    // Slower frequency scaling
        }

        // Normalize and adjust range
        value = value / maxAmplitude;
        value = (value + 1) / 2;
        value = Math.pow(value, 1.1); // Slightly sharpen transitions

        return clamp(value);
    }

    public void debugBiomeDistribution(int samples) {
        Map<BiomeType, Integer> distribution = new HashMap<>();
        Random random = new Random(baseSeed); // Use world seed for consistent debugging

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
            GameLogger.info(String.format("%s: %d occurrences (%.2f%%)",
                entry.getKey(), entry.getValue(), percentage));
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

            // Convert to 0-1 range
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

        GameLogger.info(String.format("Temperature - Min: %.3f, Max: %.3f, Avg: %.3f",
            minTemp, maxTemp, avgTemp));
        GameLogger.info(String.format("Moisture - Min: %.3f, Max: %.3f, Avg: %.3f",
            minMoist, maxMoist, avgMoist));
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

                    GameLogger.info("Loaded biome: " + biome.getName());
                    GameLogger.info("- Spawnable objects: " + biome.getSpawnableObjects());
                    GameLogger.info("- Spawn chances: " + biome.getSpawnChances());
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


        // Parse spawnable objects
        if (json.has("spawnableObjects")) {
            List<String> spawnableObjects = new ArrayList<>();
            JsonArray objectsArray = json.getAsJsonArray("spawnableObjects");
            for (JsonElement element : objectsArray) {
                spawnableObjects.add(element.getAsString());
            }
            data.setSpawnableObjects(spawnableObjects);
            GameLogger.info("Parsed spawnable objects for " + data.getName() + ": " + spawnableObjects);
        }

        // Parse spawn chances
        if (json.has("spawnChances")) {
            Map<String, Double> chances = new HashMap<>();
            JsonObject chancesObj = json.getAsJsonObject("spawnChances");
            for (Map.Entry<String, JsonElement> entry : chancesObj.entrySet()) {
                chances.put(entry.getKey(), entry.getValue().getAsDouble());
            }
            data.setSpawnChances(chances);
            GameLogger.info("Parsed spawn chances for " + data.getName() + ": " + chances);
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

        double total = dist.values().stream()
            .mapToDouble(Integer::intValue)
            .sum();

        return Math.abs(total - 100.0) < 0.01;
    }


    private Biome createBiomeFromData(BiomeData data) {
        Biome biome = new Biome(data.getName(), BiomeType.valueOf(data.getType()));
        biome.setAllowedTileTypes(data.getAllowedTileTypes());
        biome.setTileDistribution(data.getTileDistribution());

        // Load spawnable objects and chances
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
                dist.put(16, 70); // sand
                dist.put(2, 20);  // dirt
                dist.put(1, 10);  // grass
                break;
            case SNOW:
                dist.put(4, 70);  // snow
                dist.put(1, 20);  // grass
                dist.put(3, 10);  // stone
                break;
            case HAUNTED:
                dist.put(8, 70);  // dark grass
                dist.put(2, 20);  // dirt
                dist.put(3, 10);  // stone
                break;
            default:
                dist.put(1, 70);  // grass
                dist.put(2, 20);  // dirt
                dist.put(3, 10);  // stone
        }
        return dist;
    }

    private void initializeDefaultBiomes() {
        // Ensure we have basic biomes even if loading fails
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

        // Add other default biomes
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

        public double getSpawnChanceForObject(WorldObject.ObjectType objectType) {
            return spawnChances != null ? spawnChances.getOrDefault(objectType, 0.0) : 0.0;
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

    // Custom TypeAdapter for BiomeData
    private static class BiomeDataTypeAdapter extends TypeAdapter<BiomeData> {
        @Override
        public void write(JsonWriter out, BiomeData value) throws IOException {
            out.beginObject();

            // Write basic properties
            if (value.getName() != null) out.name("name").value(value.getName());
            if (value.getType() != null) out.name("type").value(value.getType());

            // Write allowed types
            if (value.getAllowedTileTypes() != null && !value.getAllowedTileTypes().isEmpty()) {
                out.name("allowedTileTypes");
                out.beginArray();
                for (Integer type : value.getAllowedTileTypes()) {
                    if (type != null) out.value(type);
                }
                out.endArray();
            }

            // Write distribution
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

            // Write optional properties
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
