package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.managers.BiomeManager;
import io.github.pokemeetup.managers.BiomeTransitionResult;
import io.github.pokemeetup.system.data.BlockSaveData;
import io.github.pokemeetup.system.data.ChestData;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.system.gameplay.overworld.mechanics.AutoTileSystem;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.OpenSimplex2;
import io.github.pokemeetup.utils.textures.TileType;

import java.util.*;

public class UnifiedWorldGenerator {

    public static final int CHUNK_SIZE = 16; // Must match your Chunk.CHUNK_SIZE

    /**
     * Main entry–point. Generates a new Chunk with all advanced features.
     *
     * @param chunkX       the chunk’s X coordinate
     * @param chunkY       the chunk’s Y coordinate
     * @param worldSeed    the world’s seed (shared between client and server)
     * @param biomeManager the shared BiomeManager
     * @return a fully generated Chunk
     */
    public static Chunk generateChunk(int chunkX, int chunkY, long worldSeed, BiomeManager biomeManager) {
        // Determine the world coordinates for the center of this chunk.
        float centerX = (chunkX * CHUNK_SIZE + CHUNK_SIZE / 2f) * World.TILE_SIZE;
        float centerY = (chunkY * CHUNK_SIZE + CHUNK_SIZE / 2f) * World.TILE_SIZE;
        BiomeTransitionResult centerTransition = biomeManager.getBiomeAt(centerX, centerY);
        Biome primaryBiome = centerTransition.getPrimaryBiome();

        // Create a new chunk (its constructor sets up basic arrays)
        Chunk chunk = new Chunk(chunkX, chunkY, primaryBiome, worldSeed, biomeManager);
        // Now generate the full tile data, elevation bands, and extra features.
        generateChunkData(chunk, worldSeed, biomeManager);
        return chunk;
    }

    /**
     * Generates the complete data for a chunk:
     * <ul>
     *   <li>Base tile generation (with biome transitions and ecosystem rules)</li>
     *   <li>Optional rainforest pond generation</li>
     *   <li>Mountain generation: shapes, smoothing, cliffs, stairs, cave entrances</li>
     *   <li>Block data generation (e.g. placing a chest at center)</li>
     *   <li>World object generation (e.g. trees, bushes, etc.)</li>
     * </ul>
     */
    public static void generateChunkData(Chunk chunk, long worldSeed, BiomeManager biomeManager) {
        int size = CHUNK_SIZE;
        int chunkX = chunk.getChunkX();
        int chunkY = chunk.getChunkY();
        int[][] tiles = new int[size][size];
        int[][] elevationBands = new int[size][size];

        // Create a deterministic seed for this chunk.
        long chunkSeed = generateChunkSeed(worldSeed, chunkX, chunkY);
        Random rand = new Random(chunkSeed);

        // Generate base tile data using the biome manager.
        for (int lx = 0; lx < size; lx++) {
            for (int ly = 0; ly < size; ly++) {
                float worldX = (chunkX * size + lx) * World.TILE_SIZE;
                float worldY = (chunkY * size + ly) * World.TILE_SIZE;
                BiomeTransitionResult localTransition = biomeManager.getBiomeAt(worldX, worldY);
                int baseTile = determineTileTypeForBiome(localTransition.getPrimaryBiome(), worldX, worldY, worldSeed);
                // If a secondary biome is present with a transition, blend the tile type.
                if (localTransition.getSecondaryBiome() != null && localTransition.getTransitionFactor() < 1.0f) {
                    baseTile = generateTransitionTile(lx, ly, localTransition, worldX, worldY, worldSeed);
                }
                tiles[lx][ly] = baseTile;
            }
        }

        // Generate additional water features in rain forest biomes.
        if (chunk.getBiome().getType() == BiomeType.RAIN_FOREST) {
            generateRainForestPonds(tiles, chunkX, chunkY, worldSeed);
        }

        // Decide on mountain generation based on biome.
        int maxLayers = determineNumberOfLayers(rand, chunk.getBiome().getType());
        if (maxLayers > 0) {
            // Generate mountain elevation bands with noise and multiple peaks.
            generateMountainShape(maxLayers, rand, elevationBands, chunkX, chunkY, worldSeed);
            smoothBandsForCohesion(elevationBands);
            // Apply mountain–specific tiles.
            applyMountainTiles(tiles, elevationBands);
            autotileCliffs(elevationBands, tiles);
            addStairsBetweenLayers(elevationBands, tiles);
            finalizeStairAccess(tiles, elevationBands);
            maybeAddCaveEntrance(elevationBands, tiles, rand);
        } else {
            for (int i = 0; i < size; i++) {
                Arrays.fill(elevationBands[i], 0);
            }
        }

        // Update the chunk.
        chunk.setTileData(tiles);
        chunk.setElevationBands(elevationBands);
        chunk.setDirty(true);

        // Generate world objects and blocks.
        List<WorldObject> objects = generateWorldObjects(chunk, biomeManager, worldSeed);
        chunk.setWorldObjects(objects);
    }

    // ─── BASE TILE GENERATION ──────────────────────────────────────────────

    private static int determineTileTypeForBiome(Biome biome, float worldX, float worldY, long worldSeed) {
        int initialTile = getInitialTileType(biome, worldX, worldY, worldSeed);
        return applyEcosystemRules(initialTile, worldX, worldY);
    }

    private static int getInitialTileType(Biome biome, float worldX, float worldY, long worldSeed) {
        Map<Integer, Integer> distribution = biome.getTileDistribution();
        List<Integer> allowedTypes = biome.getAllowedTileTypes();
        if (distribution == null || distribution.isEmpty() || allowedTypes == null || allowedTypes.isEmpty()) {
            GameLogger.error("Missing tile distribution or allowed types for biome: " + biome.getName());
            return TileType.GRASS; // fallback
        }
        double totalWeight = distribution.values().stream().mapToDouble(Integer::doubleValue).sum();
        double noiseValue = (OpenSimplex2.noise2(worldSeed + 1000, worldX * 0.5f, worldY * 0.5f) + 1.0) / 2.0;
        double roll = noiseValue * totalWeight;
        double currentTotal = 0;
        for (Map.Entry<Integer, Integer> entry : distribution.entrySet()) {
            currentTotal += entry.getValue();
            if (roll <= currentTotal) return entry.getKey();
        }
        return allowedTypes.get(0);
    }

    private static int applyEcosystemRules(int currentTile, float worldX, float worldY) {
        int localX = Math.floorMod((int) (worldX / World.TILE_SIZE), CHUNK_SIZE);
        int localY = Math.floorMod((int) (worldY / World.TILE_SIZE), CHUNK_SIZE);
        Map<Integer, Integer> neighborCounts = getNeighborTileCounts(localX, localY);
        switch (currentTile) {
            case TileType.SAND:
            case TileType.DESERT_SAND:
                if (hasWaterNeighbor(neighborCounts)) return currentTile;
                if (getRandomChance(0.75f) &&
                    (hasMatchingNeighbor(neighborCounts, TileType.SAND) ||
                        hasMatchingNeighbor(neighborCounts, TileType.DESERT_SAND))) {
                    return currentTile;
                }
                break;
            case TileType.GRASS:
            case TileType.GRASS_2:
            case TileType.GRASS_3:
                if (hasWaterNeighbor(neighborCounts))
                    return getRandomChance(0.25f) ? TileType.TALL_GRASS : currentTile;
                if (getRandomChance(0.55f) && hasAnyGrassNeighbor(neighborCounts)) {
                    if (getRandomChance(0.08f)) return TileType.FLOWER;
                    if (getRandomChance(0.08f))
                        return getRandomChance(0.5f) ? TileType.FLOWER_1 : TileType.FLOWER_2;
                    return currentTile;
                }
                break;
            case TileType.TALL_GRASS:
            case TileType.TALL_GRASS_2:
            case TileType.TALL_GRASS_3:
                if (!hasAnyGrassNeighbor(neighborCounts)) return TileType.GRASS;
                break;
        }
        return currentTile;
    }

    // Dummy implementation – in a complete version, you’d inspect the surrounding tile data.
    private static Map<Integer, Integer> getNeighborTileCounts(int x, int y) {
        return new HashMap<>();
    }

    private static boolean hasWaterNeighbor(Map<Integer, Integer> neighborCounts) {
        return neighborCounts.containsKey(TileType.WATER) ||
            neighborCounts.containsKey(TileType.WATER_PUDDLE) ||
            neighborCounts.containsKey(TileType.WATER_PUDDLE_TOP_LEFT) ||
            neighborCounts.containsKey(TileType.WATER_PUDDLE_TOP_MIDDLE) ||
            neighborCounts.containsKey(TileType.WATER_PUDDLE_TOP_RIGHT) ||
            neighborCounts.containsKey(TileType.WATER_PUDDLE_LEFT_MIDDLE) ||
            neighborCounts.containsKey(TileType.WATER_PUDDLE_RIGHT_MIDDLE) ||
            neighborCounts.containsKey(TileType.WATER_PUDDLE_BOTTOM_LEFT) ||
            neighborCounts.containsKey(TileType.WATER_PUDDLE_BOTTOM_MIDDLE) ||
            neighborCounts.containsKey(TileType.WATER_PUDDLE_BOTTOM_RIGHT);
    }

    private static boolean hasMatchingNeighbor(Map<Integer, Integer> neighborCounts, int tileType) {
        return neighborCounts.getOrDefault(tileType, 0) > 0;
    }

    private static boolean hasAnyGrassNeighbor(Map<Integer, Integer> neighborCounts) {
        return neighborCounts.containsKey(TileType.GRASS) ||
            neighborCounts.containsKey(TileType.GRASS_2) ||
            neighborCounts.containsKey(TileType.GRASS_3);
    }

    private static boolean getRandomChance(float probability) {
        return MathUtils.random() < probability;
    }

    /**
     * Blends tile type selection between the primary and secondary biome using noise.
     */
    private static int generateTransitionTile(int lx, int ly, BiomeTransitionResult transition,
                                              float worldX, float worldY, long worldSeed) {
        float factor = transition.getTransitionFactor();
        float noise = (float) ((OpenSimplex2.noise2(worldSeed + 700, worldX * 0.1f, worldY * 0.1f) + 1.0) / 2.0);
        factor += noise * 0.2f;
        factor = Math.max(0, Math.min(1, factor));
        double selectionValue = (OpenSimplex2.noise2(worldSeed + 1100, worldX * 0.5f, worldY * 0.5f) + 1.0) / 2.0;
        if (selectionValue > factor) {
            return determineTileTypeForBiome(transition.getSecondaryBiome(), worldX, worldY, worldSeed);
        } else {
            return determineTileTypeForBiome(transition.getPrimaryBiome(), worldX, worldY, worldSeed);
        }
    }

    // ─── MOUNTAIN & ELEVATION GENERATION ────────────────────────────────────

    private static int determineNumberOfLayers(Random rand, BiomeType biomeType) {
        float baseChance;
        switch (biomeType) {
            case SNOW: baseChance = 0.50f; break;
            case DESERT: baseChance = 0.85f; break;
            case PLAINS: baseChance = 0.80f; break;
            default: baseChance = 0.82f;
        }
        float r = rand.nextFloat();
        if (r < baseChance) return 0;
        if (r < baseChance + 0.10f) return 1;
        if (r < baseChance + 0.14f) return 2;
        return 3;
    }

    private static void generateMountainShape(int maxLayers, Random rand, int[][] elevationBands,
                                              int chunkX, int chunkY, long worldSeed) {
        if (maxLayers <= 0) return;
        int numPeaks = rand.nextInt(2) + 1;
        List<Point> peaks = new ArrayList<>();
        for (int i = 0; i < numPeaks; i++) {
            int px = CHUNK_SIZE / 4 + rand.nextInt(CHUNK_SIZE / 2);
            int py = CHUNK_SIZE / 4 + rand.nextInt(CHUNK_SIZE / 2);
            peaks.add(new Point(px, py));
        }
        float baseRadius = CHUNK_SIZE * (0.3f + 0.1f * maxLayers);
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_SIZE; y++) {
                float worldCoordX = (chunkX * CHUNK_SIZE + x) * 0.5f;
                float worldCoordY = (chunkY * CHUNK_SIZE + y) * 0.5f;
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
        applyErosionToBands(rand, elevationBands);
        smoothBandsForCohesion(elevationBands);
        applyErosionToBands(rand, elevationBands);
        smoothBandsForCohesion(elevationBands);
    }

    private static void applyErosionToBands(Random rand, int[][] bands) {
        int[][] temp = new int[CHUNK_SIZE][CHUNK_SIZE];
        for (int x = 1; x < CHUNK_SIZE - 1; x++) {
            for (int y = 1; y < CHUNK_SIZE - 1; y++) {
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
                        if (nx < 0 || ny < 0 || nx >= CHUNK_SIZE || ny >= CHUNK_SIZE) continue;
                        int nb = bands[nx][ny];
                        if (nb > band) higherCount++;
                        else if (nb < band) lowerCount++;
                        else sameCount++;
                    }
                }
                if (band > 1 && lowerCount > higherCount + sameCount) temp[x][y] = band - 1;
                else if (band > 0 && higherCount > lowerCount + sameCount && rand.nextFloat() < 0.1f) temp[x][y] = band + 1;
                else temp[x][y] = band;
            }
        }
        for (int x = 1; x < CHUNK_SIZE - 1; x++) {
            System.arraycopy(temp[x], 1, bands[x], 1, CHUNK_SIZE - 2);
        }
    }

    private static void smoothBandsForCohesion(int[][] bands) {
        int[][] temp = new int[CHUNK_SIZE][CHUNK_SIZE];
        for (int x = 1; x < CHUNK_SIZE - 1; x++) {
            for (int y = 1; y < CHUNK_SIZE - 1; y++) {
                int band = bands[x][y];
                if (band == 0) {
                    temp[x][y] = 0;
                    continue;
                }
                int sameCount = 0, total = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= CHUNK_SIZE || ny >= CHUNK_SIZE) continue;
                        total++;
                        if (bands[nx][ny] == band) sameCount++;
                    }
                }
                if (sameCount < 5 && band > 1) temp[x][y] = band - 1;
                else temp[x][y] = band;
            }
        }
        for (int x = 1; x < CHUNK_SIZE - 1; x++) {
            System.arraycopy(temp[x], 1, bands[x], 1, CHUNK_SIZE - 2);
        }
    }

    private static void applyMountainTiles(int[][] tiles, int[][] bands) {
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_SIZE; y++) {
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
        if (topLower && leftLower && !rightLower && !bottomLower) { tiles[x][y] = topLeftCorner; return; }
        if (topLower && rightLower && !bottomLower && !leftLower) { tiles[x][y] = topRightCorner; return; }
        if (bottomLower && leftLower && !topLower && !rightLower) { tiles[x][y] = bottomLeftCorner; return; }
        if (bottomLower && rightLower && !topLower && !leftLower) { tiles[x][y] = bottomRightCorner; return; }
        if (topLower && !(leftLower || rightLower || bottomLower)) { tiles[x][y] = TileType.MOUNTAIN_TILE_TOP_MID; return; }
        if (bottomLower && !(leftLower || rightLower || topLower)) { tiles[x][y] = TileType.MOUNTAIN_TILE_BOT_MID; return; }
        if (leftLower && !(topLower || bottomLower || rightLower)) { tiles[x][y] = TileType.MOUNTAIN_TILE_MID_LEFT; return; }
        if (rightLower && !(topLower || bottomLower || leftLower)) { tiles[x][y] = TileType.MOUNTAIN_TILE_MID_RIGHT; return; }
        if (!topLower && !leftLower && bandNW < currentBand) { tiles[x][y] = TileType.MOUNTAIN_TILE_CONNECTING_CORNER_TOP_LEFT; return; }
        if (!topLower && !rightLower && bandNE < currentBand) { tiles[x][y] = TileType.MOUNTAIN_TILE_CONNECTING_CORNER_TOP_RIGHT; return; }
        if (!bottomLower && !leftLower && bandSW < currentBand) { tiles[x][y] = TileType.MOUNTAIN_TILE_CONNECTING_CORNER_BOTTOM_LEFT; return; }
        if (!bottomLower && !rightLower && bandSE < currentBand) { tiles[x][y] = TileType.MOUNTAIN_TILE_CONNECTING_CORNER_BOTTOM_RIGHT; return; }
        if (topLower) { tiles[x][y] = TileType.MOUNTAIN_TILE_TOP_MID; return; }
        if (bottomLower) { tiles[x][y] = TileType.MOUNTAIN_TILE_BOT_MID; return; }
        if (leftLower) { tiles[x][y] = TileType.MOUNTAIN_TILE_MID_LEFT; return; }
        if (rightLower) { tiles[x][y] = TileType.MOUNTAIN_TILE_MID_RIGHT; return; }
        tiles[x][y] = TileType.MOUNTAIN_TILE_CENTER;
    }

    private static void autotileCliffs(int[][] bands, int[][] tiles) {
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_SIZE; y++) {
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

    private static void addStairsBetweenLayers(int[][] bands, int[][] tiles) {
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

    private static boolean layerExists(int[][] bands, int targetBand) {
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_SIZE; y++) {
                if (bands[x][y] == targetBand) return true;
            }
        }
        return false;
    }

    private static boolean placeAdditionalStairs(int[][] bands, int[][] tiles, int fromBand, int toBand) {
        for (int x = 1; x < CHUNK_SIZE - 1; x++) {
            for (int y = 1; y < CHUNK_SIZE - 1; y++) {
                if (canPlaceStairsHere(x, y, bands, tiles, fromBand, toBand)) {
                    tiles[x][y] = TileType.STAIRS;
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean canPlaceStairsHere(int x, int y, int[][] bands, int[][] tiles, int fromBand, int toBand) {
        if (bands[x][y] != fromBand) return false;
        if (!isCliffTile(tiles[x][y])) return false;
        if (!hasAdjacentBand(x, y, toBand, bands)) return false;
        if (hasNearbyStairs(x, y, tiles, 3)) return false;
        return true;
    }

    private static boolean hasAdjacentBand(int x, int y, int targetBand, int[][] bands) {
        return getBand(x + 1, y, bands) == targetBand ||
            getBand(x - 1, y, bands) == targetBand ||
            getBand(x, y + 1, bands) == targetBand ||
            getBand(x, y - 1, bands) == targetBand;
    }

    private static boolean hasNearbyStairs(int x, int y, int[][] tiles, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                int nx = x + dx, ny = y + dy;
                if (nx >= 0 && nx < CHUNK_SIZE && ny >= 0 && ny < CHUNK_SIZE) {
                    if (tiles[nx][ny] == TileType.STAIRS) return true;
                }
            }
        }
        return false;
    }

    private static boolean placeStairsOnSide(int[][] bands, int[][] tiles, int fromBand, int toBand, String side) {
        int startX, startY, endX, endY;
        switch (side) {
            case "north":
                startX = 1; endX = CHUNK_SIZE - 1; startY = CHUNK_SIZE - 2; endY = CHUNK_SIZE - 1;
                break;
            case "south":
                startX = 1; endX = CHUNK_SIZE - 1; startY = 1; endY = 2;
                break;
            case "east":
                startX = CHUNK_SIZE - 2; endX = CHUNK_SIZE - 1; startY = 1; endY = CHUNK_SIZE - 1;
                break;
            case "west":
                startX = 1; endX = 2; startY = 1; endY = CHUNK_SIZE - 1;
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

    private static void finalizeStairAccess(int[][] tiles, int[][] bands) {
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_SIZE; y++) {
                if (tiles[x][y] == TileType.STAIRS) {
                    int stairBand = getBand(x, y, bands);
                    int nx = x, ny = y + 1;
                    if (ny < CHUNK_SIZE) {
                        int nextBand = getBand(nx, ny, bands);
                        if (nextBand == stairBand + 1)
                            tiles[nx][ny] = TileType.MOUNTAIN_TILE_CENTER;
                    }
                }
            }
        }
    }

    private static int getBand(int x, int y, int[][] bands) {
        if (x < 0 || x >= CHUNK_SIZE || y < 0 || y >= CHUNK_SIZE) return -1;
        return bands[x][y];
    }

    private static void maybeAddCaveEntrance(int[][] bands, int[][] tiles, Random rand) {
        if (rand.nextFloat() > 0.02f) return;
        for (int x = 1; x < CHUNK_SIZE - 1; x++) {
            for (int y = 1; y < CHUNK_SIZE - 1; y++) {
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

    /**
     * Checks whether the given tile value is considered a “cliff” tile.
     */
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

    // ─── RAIN FOREST POND GENERATION ─────────────────────────────────────────

    private static void generateRainForestPonds(int[][] tiles, int chunkX, int chunkY, long worldSeed) {
        boolean[][] waterMap = new boolean[CHUNK_SIZE][CHUNK_SIZE];
        Random random = new Random(worldSeed + chunkX * 31L + chunkY * 17L);
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_SIZE; y++) {
                float worldX = (chunkX * CHUNK_SIZE + x) * World.TILE_SIZE;
                float worldY = (chunkY * CHUNK_SIZE + y) * World.TILE_SIZE;
                double baseNoise = OpenSimplex2.noise2(worldSeed + 500, worldX * 0.08f, worldY * 0.08f);
                double shapeNoise = OpenSimplex2.noise2(worldSeed + 1000, worldX * 0.15f, worldY * 0.15f);
                double detailNoise = OpenSimplex2.noise2(worldSeed + 1500, worldX * 0.25f, worldY * 0.25f);
                double combinedNoise = baseNoise * 0.6 + shapeNoise * 0.3 + detailNoise * 0.1;
                waterMap[x][y] = combinedNoise > 0.45 && random.nextFloat() > 0.3;
            }
        }
        AutoTileSystem autoTileSystem = new AutoTileSystem();
        // Pass your chunk instance to the autotiler if needed; here we pass null.
        autoTileSystem.applyAutotiling(null, waterMap);
    }

    // ─── BLOCK AND WORLD OBJECT GENERATION ───────────────────────────────────

    private static List<BlockSaveData.BlockData> generateBlockData(Chunk chunk, long worldSeed) {
        List<BlockSaveData.BlockData> blocks = new ArrayList<>();
        int center = CHUNK_SIZE / 2;
        BlockSaveData.BlockData chest = new BlockSaveData.BlockData();
        chest.type = "chest";
        chest.x = chunk.getChunkX() * CHUNK_SIZE + center;
        chest.y = chunk.getChunkY() * CHUNK_SIZE + center;
        chest.chestData = new ChestData(chunk.getChunkX() * CHUNK_SIZE + center,chunk.getChunkY() * CHUNK_SIZE + center);
        chest.isFlipped = false;
        chest.isChestOpen = false;
        blocks.add(chest);
        return blocks;
    }

    private static List<WorldObject> generateWorldObjects(Chunk chunk, BiomeManager biomeManager, long worldSeed) {
        List<WorldObject> objects = new ArrayList<>();
        Biome biome = chunk.getBiome();
        List<WorldObject.ObjectType> spawnable = biome.getSpawnableObjects();
        int size = CHUNK_SIZE;
        long chunkSeed = generateChunkSeed(worldSeed, chunk.getChunkX(), chunk.getChunkY());
        Random localRand = new Random(chunkSeed + 1234);
        for (WorldObject.ObjectType type : spawnable) {
            double baseChance = biome.getSpawnChanceForObject(type);
            for (int lx = 0; lx < size; lx++) {
                for (int ly = 0; ly < size; ly++) {
                    int tileType = chunk.getTileType(lx, ly);
                    if (!biome.getAllowedTileTypes().contains(tileType)) continue;
                    if (localRand.nextDouble() < baseChance) {
                        int worldTileX = chunk.getChunkX() * size + lx;
                        int worldTileY = chunk.getChunkY() * size + ly;
                        WorldObject obj = new WorldObject(worldTileX, worldTileY, null, type);
                        obj.ensureTexture();
                        objects.add(obj);
                    }
                }
            }
        }
        return objects;
    }

    // ─── HELPER METHODS FOR SEEDS, ETC. ───────────────────────────────────────

    private static long generateChunkSeed(long worldSeed, int chunkX, int chunkY) {
        return hashCoordinates(worldSeed, chunkX, chunkY);
    }

    private static long hashCoordinates(long seed, int x, int y) {
        long hash = seed;
        hash = hash * 31 + x;
        hash = hash * 31 + y;
        return hash;
    }

    // Simple helper point class.
    private static class Point {
        int x, y;
        Point(int x, int y) { this.x = x; this.y = y; }
    }
}
