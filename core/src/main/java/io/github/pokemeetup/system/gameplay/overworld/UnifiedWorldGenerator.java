package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import io.github.pokemeetup.managers.BiomeManager;
import io.github.pokemeetup.managers.BiomeTransitionResult;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.system.gameplay.overworld.mechanics.AutoTileSystem;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.NoiseCache;
import io.github.pokemeetup.utils.OpenSimplex2;
import io.github.pokemeetup.utils.textures.TileType;

import java.util.*;

public class UnifiedWorldGenerator {

    public static final int CHUNK_SIZE = 16; // Must match Chunk.CHUNK_SIZE
    private static final int BIOME_SAMPLE_RESOLUTION = 1; // How many tiles between biome samples
    private static final int TEMP_SIZE = CHUNK_SIZE;  // Used for smoothing arrays

    private static final ThreadLocal<int[][]> smoothingTemp = ThreadLocal.withInitial(() -> {
        int[][] arr = new int[TEMP_SIZE][TEMP_SIZE];
        for (int i = 0; i < TEMP_SIZE; i++) {
            arr[i] = new int[TEMP_SIZE];
        }
        return arr;
    });

    /**
     * Generates a new Chunk given chunk coordinates, the world seed, and the shared BiomeManager.
     */
    public static Chunk generateChunk(int chunkX, int chunkY, long worldSeed, BiomeManager biomeManager) {
        float tileSize = World.TILE_SIZE;
        int size = CHUNK_SIZE;
        float centerX = (chunkX * size + size / 2f) * tileSize;
        float centerY = (chunkY * size + size / 2f) * tileSize;
        BiomeTransitionResult centerTransition = biomeManager.getBiomeAt(centerX, centerY);
        Biome primaryBiome = centerTransition.getPrimaryBiome();
        if (primaryBiome == null) {
            primaryBiome = biomeManager.getBiome(BiomeType.PLAINS);
        }
        Chunk chunk = new Chunk(chunkX, chunkY, primaryBiome, worldSeed, biomeManager);
        populateChunkData(chunk, worldSeed, biomeManager);
        return chunk;
    }

    /**
     * Populates the chunk with tile data, elevation/mountain data, and world objects.
     */
    private static void populateChunkData(Chunk chunk, long worldSeed, BiomeManager biomeManager) {
        int size = CHUNK_SIZE;
        int chunkX = chunk.getChunkX();
        int chunkY = chunk.getChunkY();
        int[][] tiles = new int[size][size];
        int[][] elevationBands = new int[size][size];

        // Use a per-chunk seed for deterministic generation.
        long chunkSeed = generateChunkSeed(worldSeed, chunkX, chunkY);
        Random rand = new Random(chunkSeed);
        float tileSize = World.TILE_SIZE;
        int baseX = chunkX * size;
        int baseY = chunkY * size;

        // Sample the biome at a coarser resolution.
        BiomeTransitionResult[][] biomeCache =
            BiomeSampler.sampleBiomeCache(biomeManager, baseX, baseY, size, tileSize, BIOME_SAMPLE_RESOLUTION);
        // Generate tile data based on biome transitions.
        TileDataGenerator.generateTileData(tiles, biomeCache, baseX, baseY, tileSize, worldSeed, rand);

        // Special features for certain biomes.
        if (chunk.getBiome().getType() == BiomeType.RAIN_FOREST) {
            RainForestGenerator.generateRainForestPonds(chunk, chunkX, chunkY, worldSeed, tiles);
        }

        // Generate mountain/elevation layers if applicable.
        int maxLayers = ElevationGenerator.determineNumberOfLayers(rand, chunk.getBiome().getType());
        if (maxLayers > 0) {
            ElevationGenerator.generateMountainShape(maxLayers, rand, elevationBands, chunkX, chunkY, worldSeed);
            ElevationGenerator.smoothElevationBands(elevationBands, rand);
            ElevationGenerator.applyMountainTiles(tiles, elevationBands);
            ElevationGenerator.autotileCliffs(elevationBands, tiles);
            ElevationGenerator.addStairsBetweenLayers(elevationBands, tiles);
            ElevationGenerator.finalizeStairAccess(tiles, elevationBands);
            ElevationGenerator.maybeAddCaveEntrance(elevationBands, tiles, rand);
        } else {
            for (int i = 0; i < size; i++) {
                Arrays.fill(elevationBands[i], 0);
            }
        }

        chunk.setTileData(tiles);
        chunk.setElevationBands(elevationBands);
        chunk.setDirty(true);

        // Generate world objects (e.g., trees, rocks).
        List<WorldObject> objects = WorldObjectGenerator.generateWorldObjects(chunk, worldSeed);
        chunk.setWorldObjects(objects);
    }

    private static long generateChunkSeed(long worldSeed, int chunkX, int chunkY) {
        long hash = worldSeed;
        hash = hash * 31 + chunkX;
        hash = hash * 31 + chunkY;
        return hash;
    }

    // ─── NESTED HELPER CLASSES FOR ORGANIZATION ──────────────────────────────

    private static boolean collidesWithAny(WorldObject candidate, List<WorldObject> existingObjects) {
        Rectangle candidateBounds = candidate.getBoundingBox();
        for (WorldObject other : existingObjects) {
            if (candidateBounds.overlaps(other.getBoundingBox())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Handles sampling of biome data over a chunk.
     */
    private static class BiomeSampler {
        public static BiomeTransitionResult[][] sampleBiomeCache(BiomeManager biomeManager,
                                                                 int baseX, int baseY, int size,
                                                                 float tileSize, int resolution) {
            int outWidth = size / resolution;
            int outHeight = size / resolution;
            BiomeTransitionResult[][] cache = new BiomeTransitionResult[outWidth][outHeight];
            for (int bx = 0; bx < outWidth; bx++) {
                for (int by = 0; by < outHeight; by++) {
                    float worldX = (baseX + bx * resolution + resolution / 2f) * tileSize;
                    float worldY = (baseY + by * resolution + resolution / 2f) * tileSize;
                    cache[bx][by] = biomeManager.getBiomeAt(worldX, worldY);
                }
            }
            return cache;
        }
    }

    /**
     * Handles tile generation based on biome distributions and transitions.
     */
    private static class TileDataGenerator {
        public static void generateTileData(int[][] tiles,
                                            BiomeTransitionResult[][] biomeCache,
                                            int baseX, int baseY, float tileSize,
                                            long worldSeed, Random rand) {
            int size = tiles.length;
            int cacheWidth = biomeCache.length;
            int cacheHeight = biomeCache[0].length;
            // Use a simple nested loop instead of a parallel stream.
            for (int lx = 0; lx < size; lx++) {
                for (int ly = 0; ly < size; ly++) {
                    int cacheX = Math.min(lx / (size / cacheWidth), cacheWidth - 1);
                    int cacheY = Math.min(ly / (size / cacheHeight), cacheHeight - 1);
                    BiomeTransitionResult localTransition = biomeCache[cacheX][cacheY];
                    int tileType = determineTileTypeForTransition(localTransition,
                        (baseX + lx) * tileSize, (baseY + ly) * tileSize, worldSeed, rand);
                    tiles[lx][ly] = tileType;
                }
            }
        }

        private static int determineTileTypeForTransition(BiomeTransitionResult transition,
                                                          float worldX, float worldY,
                                                          long worldSeed, Random rand) {
            Biome primary = transition.getPrimaryBiome();
            Biome secondary = transition.getSecondaryBiome();
            float rawBlend = transition.getTransitionFactor();
            float blend = smoothStep(rawBlend);  // existing smoothing

            // Add a high-frequency noise to perturb the final roll slightly.
            double microNoise = OpenSimplex2.noise2(worldSeed + 9999, worldX * 0.2f, worldY * 0.2f) * 0.05;
            blend = MathUtils.clamp(blend + (float)microNoise, 0f, 1f);
            if (secondary == null || blend >= 0.99f) {
                return determineTileTypeForBiome(primary, worldX, worldY, worldSeed, rand);
            }

            Map<Integer, Integer> distPrimary = primary.getTileDistribution();
            Map<Integer, Integer> distSecondary = secondary.getTileDistribution();
            Map<Integer, Double> blendedDist = new HashMap<>();

            Set<Integer> allKeys = new HashSet<>();
            allKeys.addAll(distPrimary.keySet());
            allKeys.addAll(distSecondary.keySet());

            double totalWeight = 0.0;
            for (Integer key : allKeys) {
                double weightPrimary = distPrimary.getOrDefault(key, 0);
                double weightSecondary = distSecondary.getOrDefault(key, 0);
                double blendedWeight = blend * weightPrimary + (1 - blend) * weightSecondary;
                blendedDist.put(key, blendedWeight);
                totalWeight += blendedWeight;
            }
            double noiseValue = NoiseCache.getNoise(worldSeed + 1000, worldX, worldY, 0.5f);
            double roll = noiseValue * totalWeight;
            double currentTotal = 0;
            for (Map.Entry<Integer, Double> entry : blendedDist.entrySet()) {
                currentTotal += entry.getValue();
                if (roll <= currentTotal) {
                    return entry.getKey();
                }
            }
            return primary.getAllowedTileTypes().get(0);
        }

        private static int determineTileTypeForBiome(Biome biome, float worldX, float worldY,
                                                     long worldSeed, Random rand) {
            Map<Integer, Integer> distribution = biome.getTileDistribution();
            List<Integer> allowedTypes = biome.getAllowedTileTypes();
            if (distribution == null || distribution.isEmpty() || allowedTypes == null || allowedTypes.isEmpty()) {
                GameLogger.error("Missing tile distribution or allowed types for biome: " + biome.getName());
                return TileType.GRASS; // Fallback value
            }
            double totalWeight = distribution.values().stream().mapToDouble(Integer::doubleValue).sum();
            double noiseValue = NoiseCache.getNoise(worldSeed + 1000, worldX, worldY, 0.5f);
            double roll = noiseValue * totalWeight;
            double currentTotal = 0;
            for (Map.Entry<Integer, Integer> entry : distribution.entrySet()) {
                currentTotal += entry.getValue();
                if (roll <= currentTotal) {
                    return entry.getKey();
                }
            }
            return allowedTypes.get(0);
        }

        /**
         * A smoothstep interpolation (t*t*(3-2*t)) to ease biome blending.
         */
        private static float smoothStep(float t) {
            return t * t * (3 - 2 * t);
        }
    }

    /**
     * Handles elevation, mountain shaping, smoothing, and related features (e.g. stairs, cave entrances).
     */
    private static class ElevationGenerator {

        public static int determineNumberOfLayers(Random rand, BiomeType biomeType) {
            float baseChance;
            switch (biomeType) {
                case SNOW:
                    baseChance = 0.78f;
                    break;
                case DESERT:
                    baseChance = 0.90f;
                    break;
                case PLAINS:
                    baseChance = 0.85f;
                    break;
                default:
                    baseChance = 0.88f;
            }
            float r = rand.nextFloat();
            if (r < baseChance) return 0;
            if (r < baseChance + 0.10f) return 1;
            if (r < baseChance + 0.14f) return 2;
            return 0;
        }

        public static void generateMountainShape(int maxLayers, Random rand,
                                                 int[][] elevationBands, int chunkX, int chunkY,
                                                 long worldSeed) {
            int size = CHUNK_SIZE;
            int numPeaks = rand.nextInt(2) + 1;
            List<Point> peaks = new ArrayList<>();
            for (int i = 0; i < numPeaks; i++) {
                int px = size / 4 + rand.nextInt(size / 2);
                int py = size / 4 + rand.nextInt(size / 2);
                peaks.add(new Point(px, py));
            }
            float baseRadius = size * (0.3f + 0.1f * maxLayers);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    float worldCoordX = (chunkX * size + x) * 0.5f;
                    float worldCoordY = (chunkY * size + y) * 0.5f;
                    double minDist = Double.MAX_VALUE;
                    for (Point p : peaks) {
                        double dx = x - p.x;
                        double dy = y - p.y;
                        double dist = Math.sqrt(dx * dx + dy * dy);
                        if (dist < minDist) minDist = dist;
                    }
                    double distFactor = minDist / baseRadius;
                    double baseElevation = Math.max(0, 1.0 - distFactor);
                    double coarse = OpenSimplex2.noise2(worldSeed + 200, worldCoordX * 0.04f, worldCoordY * 0.04f);
                    baseElevation += coarse * 0.2;
                    double detail = OpenSimplex2.noise2(worldSeed + 300, worldCoordX * 0.15f, worldCoordY * 0.15f);
                    baseElevation += detail * 0.1;
                    double ridgeRaw = OpenSimplex2.noise2(worldSeed + 400, worldCoordX * 0.08f, worldCoordY * 0.08f);
                    double ridge = 1.0 - Math.abs(ridgeRaw);
                    ridge = ridge * ridge;
                    baseElevation += (ridge - 0.5) * 0.2;
                    baseElevation = Math.max(0, Math.min(1, baseElevation));
                    double bandNoise = OpenSimplex2.noise2(worldSeed + 500, worldCoordX * 0.1f, worldCoordY * 0.1f);
                    bandNoise = (bandNoise + 1.0) / 2.0;
                    double topBandThreshold = 0.4 + bandNoise * 0.1;
                    double midBandThreshold = 0.7 + bandNoise * 0.1;
                    int band = 0;
                    if (maxLayers == 1) {
                        if (baseElevation > 0.2) band = 1;
                    } else if (maxLayers == 2) {
                        if (baseElevation > midBandThreshold) band = 2;
                        else if (baseElevation > 0.2) band = 1;
                    } else {
                        if (baseElevation > midBandThreshold) band = 3;
                        else if (baseElevation > topBandThreshold) band = 2;
                        else if (baseElevation > 0.2) band = 1;
                    }
                    elevationBands[x][y] = band;
                }
            }
            // Apply smoothing and erosion passes.
            applyErosionToBands(rand, elevationBands);
            smoothBandsForCohesion(elevationBands);
            applyErosionToBands(rand, elevationBands);
            smoothBandsForCohesion(elevationBands);
        }

        public static void smoothElevationBands(int[][] bands, Random rand) {
            applyErosionToBands(rand, bands);
            smoothBandsForCohesion(bands);
            applyErosionToBands(rand, bands);
            smoothBandsForCohesion(bands);
        }

        private static void applyErosionToBands(Random rand, int[][] bands) {
            int size = bands.length;
            int[][] temp = smoothingTemp.get();
            for (int x = 1; x < size - 1; x++) {
                for (int y = 1; y < size - 1; y++) {
                    int band = bands[x][y];
                    if (band == 0) {
                        temp[x][y] = 0;
                        continue;
                    }
                    int higherCount = 0, lowerCount = 0, sameCount = 0;
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            if (dx == 0 && dy == 0) continue;
                            int nx = x + dx, ny = y + dy;
                            if (nx >= size || ny >= size) continue;
                            int nb = bands[nx][ny];
                            if (nb > band) higherCount++;
                            else if (nb < band) lowerCount++;
                            else sameCount++;
                        }
                    }
                    if (band > 1 && lowerCount > higherCount + sameCount)
                        temp[x][y] = band - 1;
                    else if (band > 0 && higherCount > lowerCount + sameCount && rand.nextFloat() < 0.1f)
                        temp[x][y] = band + 1;
                    else
                        temp[x][y] = band;
                }
            }
            for (int x = 1; x < size - 1; x++) {
                System.arraycopy(temp[x], 1, bands[x], 1, size - 2);
            }
        }

        private static void smoothBandsForCohesion(int[][] bands) {
            int size = bands.length;
            int[][] temp = new int[size][size];
            for (int x = 1; x < size - 1; x++) {
                for (int y = 1; y < size - 1; y++) {
                    int band = bands[x][y];
                    if (band == 0) {
                        temp[x][y] = 0;
                        continue;
                    }
                    int sameCount = 0, total = 0;
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            int nx = x + dx, ny = y + dy;
                            if (nx < 0 || ny < 0 || nx >= size || ny >= size) continue;
                            total++;
                            if (bands[nx][ny] == band) sameCount++;
                        }
                    }
                    temp[x][y] = (sameCount < 5 && band > 1) ? band - 1 : band;
                }
            }
            for (int x = 1; x < size - 1; x++) {
                System.arraycopy(temp[x], 1, bands[x], 1, size - 2);
            }
        }

        public static void applyMountainTiles(int[][] tiles, int[][] bands) {
            int size = tiles.length;
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    if (bands[x][y] > 0) {
                        assignMountainTile(x, y, tiles, bands);
                    }
                }
            }
        }

        private static void assignMountainTile(int x, int y, int[][] tiles, int[][] bands) {
            int currentBand = bands[x][y];
            if (currentBand <= 0) return;
            boolean isLowest = (currentBand == 1);
            int bandN = getBand(x, y + 1, bands);
            int bandS = getBand(x, y - 1, bands);
            int bandE = getBand(x + 1, y, bands);
            int bandW = getBand(x - 1, y, bands);
            int bandNE = getBand(x + 1, y + 1, bands);
            int bandNW = getBand(x - 1, y + 1, bands);
            int bandSE = getBand(x + 1, y - 1, bands);
            int bandSW = getBand(x - 1, y - 1, bands);
            boolean topLower = bandN < currentBand;
            boolean bottomLower = bandS < currentBand;
            boolean leftLower = bandW < currentBand;
            boolean rightLower = bandE < currentBand;
            int topLeftCorner = isLowest ? TileType.MOUNTAIN_TILE_TOP_LEFT_GRASS_BG : TileType.MOUNTAIN_TILE_TOP_LEFT_ROCK_BG;
            int topRightCorner = isLowest ? TileType.MOUNTAIN_TILE_TOP_RIGHT_GRASS_BG : TileType.MOUNTAIN_TILE_TOP_RIGHT_ROCK_BG;
            int bottomLeftCorner = isLowest ? TileType.MOUNTAIN_TILE_BOT_LEFT_GRASS_BG : TileType.MOUNTAIN_TILE_BOT_LEFT_ROCK_BG;
            int bottomRightCorner = isLowest ? TileType.MOUNTAIN_TILE_BOT_RIGHT_GRASS_BG : TileType.MOUNTAIN_TILE_BOT_RIGHT_ROCK_BG;
            if (!topLower && !bottomLower && !leftLower && !rightLower) {
                tiles[x][y] = TileType.MOUNTAIN_TILE_CENTER;
                return;
            }
            if (topLower && leftLower && !rightLower && !bottomLower) {
                tiles[x][y] = topLeftCorner;
                return;
            }
            if (topLower && rightLower && !bottomLower && !leftLower) {
                tiles[x][y] = topRightCorner;
                return;
            }
            if (bottomLower && leftLower && !topLower && !rightLower) {
                tiles[x][y] = bottomLeftCorner;
                return;
            }
            if (bottomLower && rightLower && !topLower && !leftLower) {
                tiles[x][y] = bottomRightCorner;
                return;
            }
            if (topLower && !(leftLower || rightLower || bottomLower)) {
                tiles[x][y] = TileType.MOUNTAIN_TILE_TOP_MID;
                return;
            }
            if (bottomLower && !(leftLower || rightLower || topLower)) {
                tiles[x][y] = TileType.MOUNTAIN_TILE_BOT_MID;
                return;
            }
            if (leftLower && !(topLower || bottomLower || rightLower)) {
                tiles[x][y] = TileType.MOUNTAIN_TILE_MID_LEFT;
                return;
            }
            if (rightLower && !(topLower || bottomLower || leftLower)) {
                tiles[x][y] = TileType.MOUNTAIN_TILE_MID_RIGHT;
                return;
            }
            if (!topLower && !leftLower && bandNW < currentBand) {
                tiles[x][y] = TileType.MOUNTAIN_TILE_CONNECTING_CORNER_TOP_LEFT;
                return;
            }
            if (!topLower && !rightLower && bandNE < currentBand) {
                tiles[x][y] = TileType.MOUNTAIN_TILE_CONNECTING_CORNER_TOP_RIGHT;
                return;
            }
            if (!bottomLower && !leftLower && bandSW < currentBand) {
                tiles[x][y] = TileType.MOUNTAIN_TILE_CONNECTING_CORNER_BOTTOM_LEFT;
                return;
            }
            if (!bottomLower && !rightLower && bandSE < currentBand) {
                tiles[x][y] = TileType.MOUNTAIN_TILE_CONNECTING_CORNER_BOTTOM_RIGHT;
                return;
            }
            if (topLower) {
                tiles[x][y] = TileType.MOUNTAIN_TILE_TOP_MID;
                return;
            }
            if (bottomLower) {
                tiles[x][y] = TileType.MOUNTAIN_TILE_BOT_MID;
                return;
            }
            if (leftLower) {
                tiles[x][y] = TileType.MOUNTAIN_TILE_MID_LEFT;
                return;
            }
            if (rightLower) {
                tiles[x][y] = TileType.MOUNTAIN_TILE_MID_RIGHT;
                return;
            }
            tiles[x][y] = TileType.MOUNTAIN_TILE_CENTER;
        }

        public static void autotileCliffs(int[][] bands, int[][] tiles) {
            int size = bands.length;
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    int band = getBand(x, y, bands);
                    if (band <= 0) continue;
                    int up = getBand(x, y + 1, bands);
                    int down = getBand(x, y - 1, bands);
                    int left = getBand(x - 1, y, bands);
                    int right = getBand(x + 1, y, bands);
                    boolean topLower = (up < band);
                    boolean bottomLower = (down < band);
                    boolean leftLower = (left < band);
                    boolean rightLower = (right < band);
                    tiles[x][y] = chooseCliffTile(topLower, bottomLower, leftLower, rightLower);
                }
            }
        }

        private static int chooseCliffTile(boolean topLower, boolean bottomLower, boolean leftLower, boolean rightLower) {
            int lowers = 0;
            if (topLower) lowers++;
            if (bottomLower) lowers++;
            if (leftLower) lowers++;
            if (rightLower) lowers++;
            if (topLower && leftLower && !bottomLower && !rightLower) return TileType.MOUNTAIN_TILE_TOP_LEFT_ROCK_BG;
            if (topLower && rightLower && !bottomLower && !leftLower) return TileType.MOUNTAIN_TILE_TOP_RIGHT_ROCK_BG;
            if (bottomLower && leftLower && !topLower && !rightLower) return TileType.MOUNTAIN_TILE_BOT_LEFT_ROCK_BG;
            if (bottomLower && rightLower && !topLower && !leftLower) return TileType.MOUNTAIN_TILE_BOT_RIGHT_ROCK_BG;
            if (topLower && lowers == 1) return TileType.MOUNTAIN_TILE_TOP_MID;
            if (bottomLower && lowers == 1) return TileType.MOUNTAIN_TILE_BOT_MID;
            if (leftLower && lowers == 1) return TileType.MOUNTAIN_TILE_MID_LEFT;
            if (rightLower && lowers == 1) return TileType.MOUNTAIN_TILE_MID_RIGHT;
            if (lowers == 0) return TileType.MOUNTAIN_TILE_CENTER;
            if (topLower) return TileType.MOUNTAIN_TILE_TOP_MID;
            if (bottomLower) return TileType.MOUNTAIN_TILE_BOT_MID;
            return TileType.MOUNTAIN_TILE_MID_LEFT;
        }

        public static void addStairsBetweenLayers(int[][] bands, int[][] tiles) {
            int size = bands.length;
            for (int fromBand = 0; fromBand < 3; fromBand++) {
                int toBand = fromBand + 1;
                if (!layerExists(bands, toBand)) continue;
                int stairsPlaced = 0;
                int requiredStairs = (fromBand == 0) ? 4 : 2;
                if (placeStairsOnSide(bands, tiles, fromBand, toBand, "north")) stairsPlaced++;
                if (placeStairsOnSide(bands, tiles, fromBand, toBand, "south")) stairsPlaced++;
                if (placeStairsOnSide(bands, tiles, fromBand, toBand, "east")) stairsPlaced++;
                if (placeStairsOnSide(bands, tiles, fromBand, toBand, "west")) stairsPlaced++;
                while (stairsPlaced < requiredStairs) {
                    if (placeAdditionalStairs(bands, tiles, fromBand, toBand)) stairsPlaced++;
                    else break;
                }
            }
        }

        public static void finalizeStairAccess(int[][] tiles, int[][] bands) {
            int size = tiles.length;
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    if (tiles[x][y] == TileType.STAIRS) {
                        int stairBand = getBand(x, y, bands);
                        int nx = x, ny = y + 1;
                        if (ny < size) {
                            int nextBand = getBand(nx, ny, bands);
                            if (nextBand == stairBand + 1) {
                                tiles[nx][ny] = TileType.MOUNTAIN_TILE_CENTER;
                            }
                        }
                    }
                }
            }
        }

        public static void maybeAddCaveEntrance(int[][] bands, int[][] tiles, Random rand) {
            int size = bands.length;
            if (rand.nextFloat() > 0.02f) return;
            for (int x = 1; x < size - 1; x++) {
                for (int y = 1; y < size - 1; y++) {
                    int band = bands[x][y];
                    if (band >= 2 && isCliffTile(tiles[x][y])) {
                        if (isStraightEdgeCliff(x, y, bands, tiles)) {
                            tiles[x][y] = TileType.CAVE_ENTRANCE;
                            return;
                        }
                    }
                }
            }
        }

        private static int getBand(int x, int y, int[][] bands) {
            int size = bands.length;
            if (x < 0 || y < 0 || x >= size || y >= size) return -1;
            return bands[x][y];
        }

        private static boolean isStraightEdgeCliff(int x, int y, int[][] bands, int[][] tiles) {
            int band = bands[x][y];
            int up = getBand(x, y + 1, bands);
            int down = getBand(x, y - 1, bands);
            int left = getBand(x - 1, y, bands);
            int right = getBand(x + 1, y, bands);
            if (up < band && down == band && left == band && right == band) return true;
            if (down < band && up == band && left == band && right == band) return true;
            if (left < band && right == band && up == band && down == band) return true;
            if (right < band && left == band && up == band && down == band) return true;
            return false;
        }

        private static boolean canPlaceStairsHere(int x, int y, int[][] bands, int[][] tiles, int fromBand, int toBand) {
            if (bands[x][y] != fromBand) return false;
            if (!isCliffTile(tiles[x][y])) return false;
            if (!hasAdjacentBand(x, y, toBand, bands)) return false;
            return !hasNearbyStairs(x, y, tiles, 3);
        }

        private static boolean hasAdjacentBand(int x, int y, int targetBand, int[][] bands) {
            return getBand(x + 1, y, bands) == targetBand ||
                getBand(x - 1, y, bands) == targetBand ||
                getBand(x, y + 1, bands) == targetBand ||
                getBand(x, y - 1, bands) == targetBand;
        }

        private static boolean hasNearbyStairs(int x, int y, int[][] tiles, int radius) {
            int size = tiles.length;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx >= 0 && nx < size && ny >= 0 && ny < size) {
                        if (tiles[nx][ny] == TileType.STAIRS) return true;
                    }
                }
            }
            return false;
        }

        private static boolean placeStairsOnSide(int[][] bands, int[][] tiles, int fromBand, int toBand, String side) {
            int size = bands.length;
            int startX, startY, endX, endY;
            switch (side) {
                case "north":
                    startX = 1;
                    endX = size - 1;
                    startY = size - 2;
                    endY = size - 1;
                    break;
                case "south":
                    startX = 1;
                    endX = size - 1;
                    startY = 1;
                    endY = 2;
                    break;
                case "east":
                    startX = size - 2;
                    endX = size - 1;
                    startY = 1;
                    endY = size - 1;
                    break;
                case "west":
                    startX = 1;
                    endX = 2;
                    startY = 1;
                    endY = size - 1;
                    break;
                default:
                    return false;
            }
            for (int x = startX; x < endX; x++) {
                for (int y = startY; y < endY; y++) {
                    if (canPlaceStairsHere(x, y, bands, tiles, fromBand, toBand)) {
                        tiles[x][y] = TileType.STAIRS;
                        return true;
                    }
                }
            }
            return false;
        }

        private static boolean placeAdditionalStairs(int[][] bands, int[][] tiles, int fromBand, int toBand) {
            int size = bands.length;
            for (int x = 1; x < size - 1; x++) {
                for (int y = 1; y < size - 1; y++) {
                    if (canPlaceStairsHere(x, y, bands, tiles, fromBand, toBand)) {
                        tiles[x][y] = TileType.STAIRS;
                        return true;
                    }
                }
            }
            return false;
        }

        private static boolean layerExists(int[][] bands, int targetBand) {
            int size = bands.length;
            for (int[] bandRow : bands) {
                for (int y = 0; y < size; y++) {
                    if (bandRow[y] == targetBand) return true;
                }
            }
            return false;
        }

        private static boolean isCliffTile(int tile) {
            return tile != TileType.MOUNTAIN_TILE_CENTER &&
                tile != TileType.GRASS &&
                tile != TileType.MOUNTAIN_PEAK &&
                tile != TileType.MOUNTAIN_SNOW_BASE &&
                tile != TileType.STAIRS &&
                tile != TileType.CAVE_ENTRANCE &&
                tile != TileType.FLOWER && tile != TileType.FLOWER_1 && tile != TileType.FLOWER_2 &&
                tile != TileType.GRASS_2 && tile != TileType.GRASS_3 &&
                tile != TileType.TALL_GRASS && tile != TileType.TALL_GRASS_2 && tile != TileType.TALL_GRASS_3 &&
                tile != TileType.SAND && tile != TileType.DESERT_SAND &&
                tile != TileType.HAUNTED_GRASS && tile != TileType.HAUNTED_TALL_GRASS &&
                tile != TileType.RAIN_FOREST_GRASS && tile != TileType.RAIN_FOREST_TALL_GRASS &&
                tile != TileType.FOREST_GRASS && tile != TileType.FOREST_TALL_GRASS &&
                tile != TileType.SNOW && tile != TileType.SNOW_2 && tile != TileType.SNOW_3 && tile != TileType.SNOW_TALL_GRASS &&
                tile != TileType.RUINS_GRASS && tile != TileType.RUINS_GRASS_0 && tile != TileType.RUINS_TALL_GRASS &&
                tile != TileType.RUINS_BRICKS;
        }
    }

    /**
     * Handles special features for rainforest biomes (e.g. pond generation).
     */
    private static class RainForestGenerator {
        public static void generateRainForestPonds(Chunk chunk, int chunkX, int chunkY, long worldSeed, int[][] tiles) {
            int size = CHUNK_SIZE;
            boolean[][] waterMap = new boolean[size][size];
            Random random = new Random(worldSeed + chunkX * 31L + chunkY * 17L);
            float tileSize = World.TILE_SIZE;
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    float worldX = (chunkX * size + x) * tileSize;
                    float worldY = (chunkY * size + y) * tileSize;
                    double baseNoise = OpenSimplex2.noise2(worldSeed + 500, worldX * 0.08f, worldY * 0.08f);
                    double shapeNoise = OpenSimplex2.noise2(worldSeed + 1000, worldX * 0.15f, worldY * 0.15f);
                    double detailNoise = OpenSimplex2.noise2(worldSeed + 1500, worldX * 0.25f, worldY * 0.25f);
                    double combinedNoise = baseNoise * 0.6 + shapeNoise * 0.3 + detailNoise * 0.1;
                    waterMap[x][y] = combinedNoise > 0.45 && random.nextFloat() > 0.3;
                }
            }
            AutoTileSystem autoTileSystem = new AutoTileSystem();
            autoTileSystem.applyAutotiling(chunk, waterMap);
        }
    }
    private static class WorldObjectGenerator {
        public static List<WorldObject> generateWorldObjects(Chunk chunk, long worldSeed) {
            List<WorldObject> objects = new ArrayList<>();
            Random random = new Random(worldSeed + chunk.getChunkX() * 31L + chunk.getChunkY());
            Biome biome = chunk.getBiome();
            List<WorldObject.ObjectType> spawnable = biome.getSpawnableObjects();

            if (spawnable == null || spawnable.isEmpty()) {
                return objects;
            }

            // Global multiplier to reduce overall spawn rates.
            // Adjust this value as needed.
            final double GLOBAL_SPAWN_MULTIPLIER = 0.1;

            for (WorldObject.ObjectType type : spawnable) {
                // Get the base spawn chance from the biome (loaded from biomes.json)
                double baseSpawnChance = biome.getSpawnChanceForObject(type);
                // Apply the global multiplier to reduce the chance
                double spawnChance = baseSpawnChance * GLOBAL_SPAWN_MULTIPLIER;

                // (Optional) If you want to enforce a minimum chance even if the JSON value is very low:
                // spawnChance = Math.max(spawnChance, 0.001);

                // Determine the number of attempts based on the chunk size
                int attempts = (int) (CHUNK_SIZE * CHUNK_SIZE * spawnChance);
                for (int i = 0; i < attempts; i++) {
                    int lx = random.nextInt(CHUNK_SIZE);
                    int ly = random.nextInt(CHUNK_SIZE);

                    // Ensure the tile is valid for object placement (check allowed tile types, passability, etc.)
                    int tileType = chunk.getTileType(lx, ly);
                    if (!biome.getAllowedTileTypes().contains(tileType)) continue;
                    if (!chunk.isPassable(lx, ly)) continue;

                    // Convert local chunk coordinates to world coordinates
                    int worldTileX = chunk.getChunkX() * CHUNK_SIZE + lx;
                    int worldTileY = chunk.getChunkY() * CHUNK_SIZE + ly;

                    // Create the world object candidate
                    WorldObject candidate = new WorldObject(worldTileX, worldTileY, null, type);
                    candidate.ensureTexture();

                    // Only add the candidate if it does not collide with an already spawned object
                    if (!collidesWithAny(candidate, objects)) {
                        objects.add(candidate);
                    }
                }
            }
            return objects;
        }
    }

        // ─── HELPER CLASS FOR POINTS (e.g. mountain peaks) ─────────────────────────────

    private static class Point {
        int x, y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
