package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.managers.BiomeManager;
import io.github.pokemeetup.managers.BiomeTransitionResult;
import io.github.pokemeetup.system.data.BlockSaveData;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.system.gameplay.overworld.mechanics.AutoTileSystem;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.OpenSimplex2;
import io.github.pokemeetup.utils.textures.TileType;

import java.util.*;

public class Chunk {
    public static final int CHUNK_SIZE = 16;

    private final BiomeManager biomeManager;
    private final long worldSeed;
    private final int chunkX;
    private final int chunkY;
    public boolean isDirty = false;
    private Biome biome;
    private Map<Vector2, PlaceableBlock> blocks = new HashMap<>();
    private int[][] tileData;
    private int[][] elevationBands;

    public Chunk() {
        this.chunkX = 0;
        this.chunkY = 0;
        this.worldSeed = 5;
        this.biomeManager = new BiomeManager(this.worldSeed);
    }

    public Chunk(int chunkX, int chunkY, Biome biome, long worldSeed, BiomeManager biomeManager) {
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.biome = biome;
        this.worldSeed = worldSeed;
        this.biomeManager = biomeManager;
        this.tileData = new int[CHUNK_SIZE][CHUNK_SIZE];
        generateChunkData();
    }

    public void addBlock(PlaceableBlock block) {
        if (block != null) {
            blocks.put(block.getPosition(), block);
            isDirty = true;
        }
    }

    public void removeBlock(Vector2 position) {
        blocks.remove(position);
        isDirty = true;
    }

    public PlaceableBlock getBlock(Vector2 position) {
        return blocks.get(position);
    }

    public Map<Vector2, PlaceableBlock> getBlocks() {
        return new HashMap<>(blocks);
    }

    public boolean isDirty() {
        return isDirty;
    }

    public void setDirty(boolean dirty) {
        this.isDirty = dirty;
    }

    public Biome getBiome() {
        return biome;
    }

    public int getTileType(int localX, int localY) {
        if (localX < 0 || localX >= CHUNK_SIZE || localY < 0 || localY >= CHUNK_SIZE) {
            return -1;
        }
        return tileData[localX][localY];
    }

    public int[][] getTileData() {
        return tileData;
    }

    public void setTileData(int[][] tileData) {
        this.tileData = tileData;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkY() {
        return chunkY;
    }

    public List<BlockSaveData.BlockData> getBlockDataForSave() {
        List<BlockSaveData.BlockData> blockDataList = new ArrayList<>();
        for (PlaceableBlock b : blocks.values()) {
            BlockSaveData.BlockData data = new BlockSaveData.BlockData();
            data.type = b.getId();
            data.x = (int) b.getPosition().x;
            data.y = (int) b.getPosition().y;
            data.isFlipped = b.isFlipped();
            data.isChestOpen = b.isChestOpen();
            if (b.getType() == PlaceableBlock.BlockType.CHEST && b.getChestData() != null) {
                data.chestData = b.getChestData();
            }
            blockDataList.add(data);
        }
        return blockDataList;
    }

    private void assignMountainTile(int x, int y, int[][] data, int[][] elevationBands) {
        int currentBand = elevationBands[x][y];
        if (currentBand <= 0) return;

        boolean isLowestBand = (currentBand == 1);

        int bandN = getBand(x, y + 1, elevationBands);
        int bandS = getBand(x, y - 1, elevationBands);
        int bandE = getBand(x + 1, y, elevationBands);
        int bandW = getBand(x - 1, y, elevationBands);

        int bandNE = getBand(x + 1, y + 1, elevationBands);
        int bandNW = getBand(x - 1, y + 1, elevationBands);
        int bandSE = getBand(x + 1, y - 1, elevationBands);
        int bandSW = getBand(x - 1, y - 1, elevationBands);

        boolean topLower = bandN < currentBand;
        boolean bottomLower = bandS < currentBand;
        boolean leftLower = bandW < currentBand;
        boolean rightLower = bandE < currentBand;

        // Decide which background set to use: GRASS_BG for band=1, ROCK_BG otherwise
        int topLeftCorner     = isLowestBand ? TileType.MOUNTAIN_TILE_TOP_LEFT_GRASS_BG : TileType.MOUNTAIN_TILE_TOP_LEFT_ROCK_BG;
        int topRightCorner    = isLowestBand ? TileType.MOUNTAIN_TILE_TOP_RIGHT_GRASS_BG : TileType.MOUNTAIN_TILE_TOP_RIGHT_ROCK_BG;
        int bottomLeftCorner  = isLowestBand ? TileType.MOUNTAIN_TILE_BOT_LEFT_GRASS_BG : TileType.MOUNTAIN_TILE_BOT_LEFT_ROCK_BG;
        int bottomRightCorner = isLowestBand ? TileType.MOUNTAIN_TILE_BOT_RIGHT_GRASS_BG : TileType.MOUNTAIN_TILE_BOT_RIGHT_ROCK_BG;

        // If no side is lower, it's a plateau center.
        if (!topLower && !bottomLower && !leftLower && !rightLower) {
            data[x][y] = TileType.MOUNTAIN_TILE_CENTER;
            return;
        }

        // Check for standard corners first
        // Perfect corners: top-left, top-right, bottom-left, bottom-right
        if (topLower && leftLower && !rightLower && !bottomLower) {
            data[x][y] = topLeftCorner;
            return;
        }
        if (topLower && rightLower && !bottomLower && !leftLower) {
            data[x][y] = topRightCorner;
            return;
        }
        if (bottomLower && leftLower && !topLower && !rightLower) {
            data[x][y] = bottomLeftCorner;
            return;
        }
        if (bottomLower && rightLower && !topLower && !leftLower) {
            data[x][y] = bottomRightCorner;
            return;
        }

        // Check for single-direction cliffs (no corner)
        if (topLower && !(leftLower || rightLower || bottomLower)) {
            data[x][y] = TileType.MOUNTAIN_TILE_TOP_MID;
            return;
        }
        if (bottomLower && !(leftLower || rightLower || topLower)) {
            data[x][y] = TileType.MOUNTAIN_TILE_BOT_MID;
            return;
        }
        if (leftLower && !(topLower || bottomLower || rightLower)) {
            data[x][y] = TileType.MOUNTAIN_TILE_MID_LEFT;
            return;
        }
        if (rightLower && !(topLower || bottomLower || leftLower)) {
            data[x][y] = TileType.MOUNTAIN_TILE_MID_RIGHT;
            return;
        }
        if (!topLower && !leftLower && bandNW < currentBand) {
            data[x][y] = TileType.MOUNTAIN_TILE_CONNECTING_CORNER_TOP_LEFT;
            return;
        }

        // TOP-RIGHT CONNECTING CORNER:
        if (!topLower && !rightLower && bandNE < currentBand) {
            data[x][y] = TileType.MOUNTAIN_TILE_CONNECTING_CORNER_TOP_RIGHT;
            return;
        }

        // BOTTOM-LEFT CONNECTING CORNER:
        if (!bottomLower && !leftLower && bandSW < currentBand) {
            data[x][y] = TileType.MOUNTAIN_TILE_CONNECTING_CORNER_BOTTOM_LEFT;
            return;
        }

        // BOTTOM-RIGHT CONNECTING CORNER:
        if (!bottomLower && !rightLower && bandSE < currentBand) {
            data[x][y] = TileType.MOUNTAIN_TILE_CONNECTING_CORNER_BOTTOM_RIGHT;
            return;
        }

        // If multiple lowers but no neat configuration:
        // Default back to a logical side tile or a corner tile.
        // Prioritize top/bottom edges over side edges for a consistent look,
        // and if no single direction chosen, choose a side as fallback.

        if (topLower) {
            data[x][y] = TileType.MOUNTAIN_TILE_TOP_MID;
            return;
        }
        if (bottomLower) {
            data[x][y] = TileType.MOUNTAIN_TILE_BOT_MID;
            return;
        }
        if (leftLower) {
            data[x][y] = TileType.MOUNTAIN_TILE_MID_LEFT;
            return;
        }
        if (rightLower) {
            data[x][y] = TileType.MOUNTAIN_TILE_MID_RIGHT;
            return;
        }

        // If somehow none of the above conditions triggered (very unlikely),
        // default to center.
        data[x][y] = TileType.MOUNTAIN_TILE_CENTER;
    }

    public int[][] getElevationBands() {
        return this.elevationBands;
    }


    private void generateMountainShape(int maxLayers, Random rand) {
        if (maxLayers <= 0) return;

        // We will use multiple peaks and multiple noise layers for more natural shapes.
        int numPeaks = rand.nextInt(2) + 1; // 1 or 2 peaks per chunk
        List<Point> peaks = new ArrayList<>();
        for (int i = 0; i < numPeaks; i++) {
            int px = CHUNK_SIZE / 4 + rand.nextInt(CHUNK_SIZE / 2);
            int py = CHUNK_SIZE / 4 + rand.nextInt(CHUNK_SIZE / 2);
            peaks.add(new Point(px, py));
        }

        // Base radius for conceptual scaling; we won't rely solely on distance now.
        float baseRadius = CHUNK_SIZE * (0.3f + 0.1f * maxLayers);

        // Introduce multiple noise frequencies for terrain variation
        // For example, a coarse noise for general big shapes, and a finer noise for details.
        // We’ll use OpenSimplex2 noise again:
        // coarseNoise: large-scale shape
        // detailNoise: smaller-scale bumps
        // ridgeNoise: ridged noise to create sharper transitions.

        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_SIZE; y++) {
                // Compute world coordinates if needed
                // Not mandatory if we want each chunk pattern unique:
                float worldCoordX = (chunkX * CHUNK_SIZE + x) * 0.5f;
                float worldCoordY = (chunkY * CHUNK_SIZE + y) * 0.5f;

                // Distance to closest peak to add a slight gradient
                double minDist = Double.MAX_VALUE;
                for (Point p : peaks) {
                    double dx = x - p.x;
                    double dy = y - p.y;
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    if (dist < minDist) minDist = dist;
                }

                double distFactor = minDist / baseRadius;

                // Basic elevation from distance: closer to a peak = higher band
                // Invert distFactor so close to peak = near 0.0, far = near 1.0
                double baseElevation = Math.max(0, 1.0 - distFactor);

                // Add coarse noise for large shape variation
                double coarse = OpenSimplex2.noise2(worldSeed + 200, worldCoordX * 0.04f, worldCoordY * 0.04f);
                // Scale and bias so that coarse noise slightly reshapes the mountain
                baseElevation += coarse * 0.2; // +20% variation

                // Add detail noise for local bumps
                double detail = OpenSimplex2.noise2(worldSeed + 300, worldCoordX * 0.15f, worldCoordY * 0.15f);
                baseElevation += detail * 0.1; // small bumps

                // Create a ridged noise for more cliff-like features
                double ridgeRaw = OpenSimplex2.noise2(worldSeed + 400, worldCoordX * 0.08f, worldCoordY * 0.08f);
                double ridge = 1.0 - Math.abs(ridgeRaw); // ridged: invert absolute value
                ridge = ridge * ridge; // square to sharpen peaks
                baseElevation += (ridge - 0.5) * 0.2; // integrate ridge, centered around 0.5

                // Clamp elevation between 0 and 1
                baseElevation = Math.max(0, Math.min(1, baseElevation));

                // Now we determine bands from this elevation.
                // Instead of fixed thresholds, we let thresholds vary per tile with noise.
                // For example:
                double bandNoise = OpenSimplex2.noise2(worldSeed + 500, worldCoordX * 0.1f, worldCoordY * 0.1f);
                // bandNoise in [-1,1], shift to [0,1]
                bandNoise = (bandNoise + 1.0) / 2.0;

                // For maxLayers (1 to 3 or more), define thresholds dynamically:
                // Example for 3 layers: band3 if elevation > 0.7 + small random offset,
                // band2 if elevation > 0.4 + offset, else band1.
                // Offsets vary per tile by bandNoise.
                double topBandThreshold = 0.4 + bandNoise * 0.1;    // vary top threshold 0.4-0.5
                double midBandThreshold = 0.7 + bandNoise * 0.1;    // vary mid threshold 0.7-0.8

                // If maxLayers=1, all elevation >0 is band 1
                // If maxLayers=2, elevation > midBandThreshold = band 2 else band 1
                // If maxLayers=3, elevation > midBandThreshold=band3, >topBandThreshold=band2 else band1

                int band = 0;
                if (maxLayers == 1) {
                    if (baseElevation > 0.2) band = 1; // just a simple cutoff
                } else if (maxLayers == 2) {
                    if (baseElevation > midBandThreshold) band = 2;
                    else if (baseElevation > 0.2) band = 1; // ensure some base level
                } else {
                    // 3 or more layers - we treat similarly to 3-layer logic:
                    // For more than 3, you can add more thresholds or distribute evenly
                    if (maxLayers >= 3) {
                        // band 3 if elevation very high
                        // band 2 if medium
                        // band 1 if above a low threshold
                        if (baseElevation > midBandThreshold) band = 3;
                        else if (baseElevation > topBandThreshold) band = 2;
                        else if (baseElevation > 0.2) band = 1; // basic lower band
                    }

                    // If you have more than 3 layers, you can interpolate further:
                    // For layer 4, 5, etc., create additional thresholds:
                    // Example:
                    if (maxLayers > 3) {
                        // Add an extra layer between band 2 and 3:
                        double extraLayerThreshold = midBandThreshold + (1 - midBandThreshold)*0.5;
                        // If elevation > extraLayerThreshold and maxLayers=4: band=4, else band=3
                        if (maxLayers == 4 && baseElevation > extraLayerThreshold) {
                            band = 4;
                        }
                    }
                }

                elevationBands[x][y] = band;
            }
        }

        // After assigning bands, apply a more aggressive smoothing/erosion step to break patterns:
        applyErosionToBands(rand);
        smoothBandsForCohesion();
        // Another pass to further break uniformity
        applyErosionToBands(rand);
        smoothBandsForCohesion();
    }

    private void applyErosionToBands(Random rand) {
        // A simple erosion step: if a tile is a high band tile but surrounded mostly by lower bands, degrade it.
        // If a tile is lower but surrounded by higher, sometimes raise it. This breaks uniform patterns.
        int[][] temp = new int[CHUNK_SIZE][CHUNK_SIZE];
        for (int x = 1; x < CHUNK_SIZE - 1; x++) {
            for (int y = 1; y < CHUNK_SIZE - 1; y++) {
                int band = elevationBands[x][y];
                if (band == 0) {
                    temp[x][y] = 0;
                    continue;
                }

                int higherCount = 0;
                int lowerCount = 0;
                int sameCount = 0;
                int totalNeighbors = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = x+dx, ny = y+dy;
                        if (nx < 0 || ny < 0 || nx >= CHUNK_SIZE || ny >= CHUNK_SIZE) continue;
                        totalNeighbors++;
                        int nbBand = elevationBands[nx][ny];
                        if (nbBand > band) higherCount++;
                        else if (nbBand < band) lowerCount++;
                        else sameCount++;
                    }
                }

                // Erosion rules:
                // If a band is high and surrounded by many lower tiles, degrade it:
                if (band > 1 && lowerCount > higherCount + sameCount) {
                    temp[x][y] = band - 1;
                }
                // If a band is low but surrounded by higher tiles, sometimes raise it:
                else if (band > 0 && higherCount > lowerCount + sameCount && rand.nextFloat() < 0.1f) {
                    temp[x][y] = band + 1;
                } else {
                    temp[x][y] = band;
                }
            }
        }

        // Copy back
        for (int x = 1; x < CHUNK_SIZE - 1; x++) {
            System.arraycopy(temp[x], 1, elevationBands[x], 1, CHUNK_SIZE - 2);
        }
    }

    private int generateTransitionTileTile(int x, int y, BiomeTransitionResult biomeResult, float worldX, float worldY) {
        Biome primaryBiome = biomeResult.getPrimaryBiome();
        Biome secondaryBiome = biomeResult.getSecondaryBiome();
        float transitionFactor = biomeResult.getTransitionFactor();

        float noise = OpenSimplex2.noise2(worldSeed + 700, worldX * 0.1f, worldY * 0.1f);
        transitionFactor += noise * 0.2f;
        transitionFactor = Math.max(0, Math.min(1, transitionFactor));
        double selectionValue = (OpenSimplex2.noise2(worldSeed + 1100, worldX * 0.5f, worldY * 0.5f) + 1.0) / 2.0;

        if (selectionValue > transitionFactor) {
            return determineTileTypeForBiome(secondaryBiome, worldX, worldY);
        } else {
            return determineTileTypeForBiome(primaryBiome, worldX, worldY);
        }
    }



    private int determineTileTypeForBiome(Biome biome, float worldX, float worldY) {
        int initialTile = getInitialTileType(biome, worldX, worldY);
        return applyEcosystemRules(initialTile, worldX, worldY);
    }

    private int getInitialTileType(Biome biome, float worldX, float worldY) {
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
            if (roll <= currentTotal) {
                return entry.getKey();
            }
        }

        return allowedTypes.get(0);
    }


    private boolean hasLowerNeighbor(int x, int y, int[][] elevationBands) {
        int band = getBand(x, y, elevationBands);
        int n = getBand(x, y + 1, elevationBands);
        int s = getBand(x, y - 1, elevationBands);
        int e = getBand(x + 1, y, elevationBands);
        int w = getBand(x - 1, y, elevationBands);
        return (n < band) || (s < band) || (e < band) || (w < band);
    }


    private boolean isCliffTile(int tile) {
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



    private boolean stairsExistBetween(int[][] elevationBands, int fromBand, int toBand) {
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_SIZE; y++) {
                if (tileData[x][y] == TileType.STAIRS) {
                    int band = getBand(x, y, elevationBands);
                    if (band == fromBand) {
                        // Check adjacent tiles for toBand
                        if (getBand(x, y + 1, elevationBands) == toBand ||
                            getBand(x, y - 1, elevationBands) == toBand ||
                            getBand(x + 1, y, elevationBands) == toBand ||
                            getBand(x - 1, y, elevationBands) == toBand) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
    private boolean getRandomChance(float probability) {
        return MathUtils.random() < probability;
    }

    private Map<Integer, Integer> getNeighborTileCounts(int x, int y) {
        Map<Integer, Integer> counts = new HashMap<>();

        // Check all 8 surrounding tiles
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;

                int nx = x + dx;
                int ny = y + dy;

                // Handle chunk boundaries
                if (nx >= 0 && nx < CHUNK_SIZE && ny >= 0 && ny < CHUNK_SIZE) {
                    int neighborTile = tileData[nx][ny];
                    counts.merge(neighborTile, 1, Integer::sum);
                }
            }
        }

        return counts;
    }

    private boolean hasWaterNeighbor(Map<Integer, Integer> neighborCounts) {
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

    private boolean hasMatchingNeighbor(Map<Integer, Integer> neighborCounts, int tileType) {
        return neighborCounts.getOrDefault(tileType, 0) > 0;
    }

    private int applyEcosystemRules(int currentTile, float worldX, float worldY) {
        // Original ecosystem logic as provided by user
        int localX = Math.floorMod((int) (worldX / World.TILE_SIZE), CHUNK_SIZE);
        int localY = Math.floorMod((int) (worldY / World.TILE_SIZE), CHUNK_SIZE);

        Map<Integer, Integer> neighborCounts = getNeighborTileCounts(localX, localY);

        switch (currentTile) {
            case TileType.SAND:
            case TileType.DESERT_SAND:
                if (hasWaterNeighbor(neighborCounts)) {
                    return currentTile;
                }
                if (getRandomChance(0.75f) && (hasMatchingNeighbor(neighborCounts, TileType.SAND) ||
                    hasMatchingNeighbor(neighborCounts, TileType.DESERT_SAND))) {
                    return currentTile;
                }
                break;

            case TileType.GRASS:
            case TileType.GRASS_2:
            case TileType.GRASS_3:
                if (hasWaterNeighbor(neighborCounts)) {
                    return getRandomChance(0.25f) ? TileType.TALL_GRASS : currentTile; // Adjusted from original
                }
                if (getRandomChance(0.55f) && hasAnyGrassNeighbor(neighborCounts)) {
                    if (getRandomChance(0.08f)) {
                        return TileType.FLOWER;
                    }
                    if (getRandomChance(0.08f)) {
                        return getRandomChance(0.5f) ? TileType.FLOWER_1 : TileType.FLOWER_2;
                    }
                    return currentTile;
                }
                break;

            case TileType.SNOW:
            case TileType.SNOW_2:
            case TileType.SNOW_3:
                if (hasAnySnowNeighbor(neighborCounts)) {
                    if (getRandomChance(0.25f)) {
                        if (getRandomChance(0.20f)) {
                            return TileType.SNOW_TALL_GRASS;
                        } else {
                            float roll = MathUtils.random();
                            if (roll < 0.70f) {
                                return TileType.SNOW;
                            } else if (roll < 0.85f) {
                                return TileType.SNOW_2;
                            } else {
                                return TileType.SNOW_3;
                            }
                        }
                    }
                }
                break;

            // Include other biome ecosystem variants (FOREST_GRASS, RAIN_FOREST_GRASS, HAUNTED_GRASS, RUINS) as originally shown

            case TileType.FOREST_GRASS:
                if (getRandomChance(0.35f) && hasMatchingNeighbor(neighborCounts, TileType.FOREST_GRASS)) {
                    return getRandomChance(0.25f) ? TileType.FOREST_TALL_GRASS : TileType.FOREST_GRASS;
                }
                break;

            case TileType.RAIN_FOREST_GRASS:
                if (hasWaterNeighbor(neighborCounts)) {
                    return getRandomChance(0.35f) ? TileType.RAIN_FOREST_TALL_GRASS : TileType.RAIN_FOREST_GRASS;
                }
                if (getRandomChance(0.45f) && hasMatchingNeighbor(neighborCounts, TileType.RAIN_FOREST_GRASS)) {
                    return currentTile;
                }
                break;

            case TileType.HAUNTED_GRASS:
                if (getRandomChance(0.30f) && hasMatchingNeighbor(neighborCounts, TileType.HAUNTED_GRASS)) {
                    if (getRandomChance(0.25f)) {
                        return TileType.HAUNTED_TALL_GRASS;
                    } else if (getRandomChance(0.15f)) {
                        return getRandomChance(0.5f) ? TileType.HAUNTED_SHROOM : TileType.HAUNTED_SHROOMS;
                    }
                    return TileType.HAUNTED_GRASS;
                }
                break;

            case TileType.RUINS_GRASS:
            case TileType.RUINS_GRASS_0:
                if (getRandomChance(0.35f) && hasAnyRuinsNeighbor(neighborCounts)) {
                    if (getRandomChance(0.25f)) {
                        return TileType.RUINS_TALL_GRASS;
                    } else if (getRandomChance(0.20f)) {
                        return TileType.RUINS_BRICKS;
                    }
                    return currentTile;
                }
                break;

            case TileType.TALL_GRASS:
            case TileType.TALL_GRASS_2:
            case TileType.TALL_GRASS_3:
                if (!hasAnyGrassNeighbor(neighborCounts)) {
                    return TileType.GRASS;
                }
                break;
        }

        return currentTile;
    }

    private boolean hasAnyGrassNeighbor(Map<Integer, Integer> neighborCounts) {
        return neighborCounts.containsKey(TileType.GRASS) ||
            neighborCounts.containsKey(TileType.GRASS_2) ||
            neighborCounts.containsKey(TileType.GRASS_3);
    }

    private boolean hasAnySnowNeighbor(Map<Integer, Integer> neighborCounts) {
        return neighborCounts.containsKey(TileType.SNOW) ||
            neighborCounts.containsKey(TileType.SNOW_2) ||
            neighborCounts.containsKey(TileType.SNOW_3);
    }

    private boolean hasAnyRuinsNeighbor(Map<Integer, Integer> neighborCounts) {
        return neighborCounts.containsKey(TileType.RUINS_GRASS) ||
            neighborCounts.containsKey(TileType.RUINS_GRASS_0) ||
            neighborCounts.containsKey(TileType.RUINS_BRICKS) ||
            neighborCounts.containsKey(TileType.RUINS_TALL_GRASS);
    }

    private void generateChunkData() {
        Random rand = new Random(worldSeed + chunkX * 31L + chunkY * 17L);

        // First, get the biome for this chunk
        float worldX = (chunkX * CHUNK_SIZE + CHUNK_SIZE / 2) * World.TILE_SIZE;
        float worldY = (chunkY * CHUNK_SIZE + CHUNK_SIZE / 2) * World.TILE_SIZE;
        BiomeTransitionResult biomeResult = biomeManager.getBiomeAt(worldX, worldY);
        this.biome = biomeResult.getPrimaryBiome();

        // Determine mountain generation based on biome
        int maxLayers = determineNumberOfLayers(rand, biome.getType());
        elevationBands = new int[CHUNK_SIZE][CHUNK_SIZE];

        // Generate base tiles first using biome information
        tileData = new int[CHUNK_SIZE][CHUNK_SIZE];
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_SIZE; y++) {
                worldX = (chunkX * CHUNK_SIZE + x) * World.TILE_SIZE;
                worldY = (chunkY * CHUNK_SIZE + y) * World.TILE_SIZE;
                BiomeTransitionResult localBiome = biomeManager.getBiomeAt(worldX, worldY);
                tileData[x][y] = determineTileTypeForBiome(localBiome.getPrimaryBiome(), worldX, worldY);

                // Handle biome transitions
                if (localBiome.getSecondaryBiome() != null && localBiome.getTransitionFactor() < 1.0f) {
                    tileData[x][y] = generateTransitionTileTile(x, y, localBiome, worldX, worldY);
                }
            }
        }
        if (biome != null && biome.getType() == BiomeType.RAIN_FOREST) {
            generateRainForestPonds();
        }
        // If no mountain, we're done
        if (maxLayers == 0) {
            return;
        }

        // Generate varied mountain shapes
        generateMountainShape(maxLayers, rand);

        // Apply mountain tiles based on elevation bands
        applyMountainTiles();

        // Add features
        autotileCliffs(elevationBands, tileData);
        addStairsBetweenLayers(elevationBands, tileData);
        finalizeStairAccess(tileData, elevationBands);
        maybeAddCaveEntrance(elevationBands, tileData, rand);

        isDirty = true;
    }


    private void addStairsBetweenLayers(int[][] bands, int[][] tiles) {
        // For each elevation transition (0->1, 1->2, 2->3)
        for (int fromBand = 0; fromBand < 3; fromBand++) {
            int toBand = fromBand + 1;
            if (!layerExists(bands, toBand)) continue;

            // Place multiple stairs for each transition
            int stairsPlaced = 0;
            int requiredStairs = (fromBand == 0) ? 4 : 2; // More stairs at ground level

            // Try to place stairs on each side
            if (placeStairsOnSide(bands, tiles, fromBand, toBand, "north")) stairsPlaced++;
            if (placeStairsOnSide(bands, tiles, fromBand, toBand, "south")) stairsPlaced++;
            if (placeStairsOnSide(bands, tiles, fromBand, toBand, "east")) stairsPlaced++;
            if (placeStairsOnSide(bands, tiles, fromBand, toBand, "west")) stairsPlaced++;

            // If we couldn't place enough stairs on sides, try placing additional stairs
            while (stairsPlaced < requiredStairs) {
                if (placeAdditionalStairs(bands, tiles, fromBand, toBand)) {
                    stairsPlaced++;
                } else {
                    break; // No more valid positions found
                }
            }
        }
    }

    private boolean placeAdditionalStairs(int[][] bands, int[][] tiles, int fromBand, int toBand) {
        // Try to place stairs anywhere valid
        for (int x = 1; x < CHUNK_SIZE-1; x++) {
            for (int y = 1; y < CHUNK_SIZE-1; y++) {
                if (canPlaceStairsHere(x, y, bands, tiles, fromBand, toBand)) {
                    tiles[x][y] = TileType.STAIRS;
                    return true;
                }
            }
        }
        return false;
    }
    private boolean canPlaceStairsHere(int x, int y, int[][] bands, int[][] tiles, int fromBand, int toBand) {
        // Must be at the right elevation
        if (bands[x][y] != fromBand) return false;

        // Must be a cliff tile
        if (!isCliffTile(tiles[x][y])) return false;

        // Must have the higher band adjacent
        if (!hasAdjacentBand(x, y, toBand, bands)) return false;

        // Check for nearby stairs to prevent clustering
        if (hasNearbyStairs(x, y, tiles, 3)) return false;

        return true;
    }

    private boolean hasAdjacentBand(int x, int y, int targetBand, int[][] bands) {
        return getBand(x+1, y, bands) == targetBand ||
            getBand(x-1, y, bands) == targetBand ||
            getBand(x, y+1, bands) == targetBand ||
            getBand(x, y-1, bands) == targetBand;
    }

    private boolean hasNearbyStairs(int x, int y, int[][] tiles, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                int nx = x + dx;
                int ny = y + dy;
                if (nx >= 0 && nx < CHUNK_SIZE && ny >= 0 && ny < CHUNK_SIZE) {
                    if (tiles[nx][ny] == TileType.STAIRS) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    private boolean placeStairsOnSide(int[][] bands, int[][] tiles, int fromBand, int toBand, String side) {
        int startX, startY, endX, endY;

        switch(side) {
            case "north":
                startX = 1; endX = CHUNK_SIZE-1;
                startY = CHUNK_SIZE-2; endY = CHUNK_SIZE-1;
                break;
            case "south":
                startX = 1; endX = CHUNK_SIZE-1;
                startY = 1; endY = 2;
                break;
            case "east":
                startX = CHUNK_SIZE-2; endX = CHUNK_SIZE-1;
                startY = 1; endY = CHUNK_SIZE-1;
                break;
            case "west":
                startX = 1; endX = 2;
                startY = 1; endY = CHUNK_SIZE-1;
                break;
            default:
                return false;
        }

        // Look for valid positions along this side
        for (int x = startX; x < endX; x++) {
            for (int y = startY; y < endY; y++) {
                if (canPlaceStairsHere(x, y, bands, tiles, fromBand, toBand)) {
                    tiles[x][y] = TileType.STAIRS;
                    return true;
                }
            }
        }
        return false;
    }private void finalizeStairAccess(int[][] tiles, int[][] bands) {
        // After all stairs are placed, ensure that the tile above the stairs is MOUNTAIN_TILE_CENTER if it’s in the higher band.
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_SIZE; y++) {
                if (tiles[x][y] == TileType.STAIRS) {
                    int stairBand = getBand(x, y, bands);
                    // Check the tile above (y+1) or whichever direction you consider "up"
                    int nx = x, ny = y + 1;
                    if (ny < CHUNK_SIZE) {
                        int nextBand = getBand(nx, ny, bands);
                        if (nextBand == stairBand + 1) {
                            // Ensure it's a MOUNTAIN_TILE_CENTER to allow walking up
                            tiles[nx][ny] = TileType.MOUNTAIN_TILE_CENTER;
                        }
                    }
                }
            }
        }
    }

    private void smoothBandsForCohesion() {
        int[][] temp = new int[CHUNK_SIZE][CHUNK_SIZE];

        for (int x = 1; x < CHUNK_SIZE - 1; x++) {
            for (int y = 1; y < CHUNK_SIZE - 1; y++) {
                int band = elevationBands[x][y];
                if (band == 0) {
                    temp[x][y] = 0;
                    continue;
                }

                int sameCount = 0;
                int total = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= CHUNK_SIZE || ny >= CHUNK_SIZE) continue;
                        total++;
                        if (elevationBands[nx][ny] == band) {
                            sameCount++;
                        }
                    }
                }

                // If fewer than half of neighbors match this band,
                // reduce the band by 1 to integrate it better or flatten it.
                if (sameCount < 5 && band > 1) {
                    temp[x][y] = band - 1;
                } else {
                    temp[x][y] = band;
                }
            }
        }

        // Copy changes back
        for (int x = 1; x < CHUNK_SIZE - 1; x++) {
            System.arraycopy(temp[x], 1, elevationBands[x], 1, CHUNK_SIZE - 2);
        }
    }


    private int determineNumberOfLayers(Random rand, BiomeType biomeType) {
        // Adjusted to make mountains slightly more common
        float baseChance;
        switch(biomeType) {
            case SNOW:
                baseChance = 0.50f;  // 75% no mountain, so 25% mountain
                break;
            case DESERT:
                baseChance = 0.85f;  // 15% chance of mountain
                break;
            case PLAINS:
                baseChance = 0.80f;  // 20% chance
                break;
            default:
                baseChance = 0.82f;
        }

        float r = rand.nextFloat();
        if (r < baseChance) return 0;
        // Make sure at least 1 layer if mountain exists
        if (r < baseChance + 0.10f) return 1;
        if (r < baseChance + 0.14f) return 2;
        return 3;
    }
    private void applyMountainTiles() {
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_SIZE; y++) {
                if (elevationBands[x][y] > 0) {
                    assignMountainTile(x, y, tileData, elevationBands);
                }
            }
        }
    }
    private void autotileCliffs(int[][] bands, int[][] tiles) {
        // We look for places where a tile is in a higher band but has a neighboring tile in a lower band.
        // Those are edges and should be replaced with appropriate cliff tiles.
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_SIZE; y++) {
                int band = getBand(x, y, bands);
                if (band <= 0) continue; // no cliff on ground level

                // Check neighbors
                int up = getBand(x, y + 1, bands);
                int down = getBand(x, y - 1, bands);
                int left = getBand(x - 1, y, bands);
                int right = getBand(x + 1, y, bands);

                boolean topLower = (up < band);
                boolean bottomLower = (down < band);
                boolean leftLower = (left < band);
                boolean rightLower = (right < band);

                // Determine appropriate cliff tile based on which sides are lower
                tiles[x][y] = chooseCliffTile(topLower, bottomLower, leftLower, rightLower);
            }
        }
    }

    private int chooseCliffTile(boolean topLower, boolean bottomLower, boolean leftLower, boolean rightLower) {
        int lowers = 0;
        if (topLower) lowers++;
        if (bottomLower) lowers++;
        if (leftLower) lowers++;
        if (rightLower) lowers++;

        // Corners
        if (topLower && leftLower && !bottomLower && !rightLower) return TileType.MOUNTAIN_TILE_TOP_LEFT_ROCK_BG;
        if (topLower && rightLower && !bottomLower && !leftLower) return TileType.MOUNTAIN_TILE_TOP_RIGHT_ROCK_BG;
        if (bottomLower && leftLower && !topLower && !rightLower) return TileType.MOUNTAIN_TILE_BOT_LEFT_ROCK_BG;
        if (bottomLower && rightLower && !topLower && !leftLower) return TileType.MOUNTAIN_TILE_BOT_RIGHT_ROCK_BG;

        // Single-direction cliffs
        if (topLower && lowers == 1) return TileType.MOUNTAIN_TILE_TOP_MID;
        if (bottomLower && lowers == 1) return TileType.MOUNTAIN_TILE_BOT_MID;
        if (leftLower && lowers == 1) return TileType.MOUNTAIN_TILE_MID_LEFT;
        if (rightLower && lowers == 1) return TileType.MOUNTAIN_TILE_MID_RIGHT;

        // If no lowers (plateau) or fallback
        if (lowers == 0) return TileType.MOUNTAIN_TILE_CENTER;

        // If multiple lowers but not forming a neat corner,
        // just default to a side tile, e.g. top_mid if top is lower
        // This is a fallback scenario.
        if (topLower) return TileType.MOUNTAIN_TILE_TOP_MID;
        if (bottomLower) return TileType.MOUNTAIN_TILE_BOT_MID;
        return TileType.MOUNTAIN_TILE_MID_LEFT;
    }



    private boolean layerExists(int[][] bands, int targetBand) {
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_SIZE; y++) {
                if (bands[x][y] == targetBand) return true;
            }
        }
        return false;
    }

    private boolean isAdjacentBand(int x, int y, int targetBand, int[][] bands) {
        return getBand(x, y + 1, bands) == targetBand ||
            getBand(x, y - 1, bands) == targetBand ||
            getBand(x + 1, y, bands) == targetBand ||
            getBand(x - 1, y, bands) == targetBand;
    }


    private void maybeAddCaveEntrance(int[][] bands, int[][] tiles, Random rand) {
        // On upper layers, sometimes replace a suitable cliff tile with a cave entrance.
        // Rare chance, as in Pokémon. Let's say only band 2 or 3.
        if (rand.nextFloat() > 0.02f) return; // 2% chance per chunk

        for (int x = 1; x < CHUNK_SIZE - 1; x++) {
            for (int y = 1; y < CHUNK_SIZE - 1; y++) {
                int band = bands[x][y];
                if (band >= 2 && isCliffTile(tiles[x][y])) {
                    // Prefer a straight edge tile facing outward
                    if (isStraightEdgeCliff(x, y, bands, tiles)) {
                        tiles[x][y] = TileType.CAVE_ENTRANCE;
                        return;
                    }
                }
            }
        }
    }

    private boolean isStraightEdgeCliff(int x, int y, int[][] bands, int[][] tiles) {
        // A "straight edge" cliff might be a vertical or horizontal cliff facing a lower band.
        // Check if one side is lower and opposite side is equal band:
        int band = bands[x][y];
        int up = getBand(x, y + 1, bands);
        int down = getBand(x, y - 1, bands);
        int left = getBand(x - 1, y, bands);
        int right = getBand(x + 1, y, bands);

        // For cave entrances, top-facing or bottom-facing cliffs are common in Pokémon.
        // Let's say a top-facing cliff: the tile above is lower.
        // Adjust logic as you see fit.
        if (up < band && down == band && left == band && right == band) return true;
        if (down < band && up == band && left == band && right == band) return true;
        // Similarly for left/right facing
        if (left < band && right == band && up == band && down == band) return true;
        if (right < band && left == band && up == band && down == band) return true;

        return false;
    }

    private int getBand(int x, int y, int[][] bands) {
        if (x < 0 || x >= CHUNK_SIZE || y < 0 || y >= CHUNK_SIZE) return -1;
        return bands[x][y];
    }

    private void generateRainForestPonds() {
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
        autoTileSystem.applyAutotiling(this, waterMap);
    }

    public boolean isPassable(int localX, int localY) {
        localX = (localX + CHUNK_SIZE) % CHUNK_SIZE;
        localY = (localY + CHUNK_SIZE) % CHUNK_SIZE;
        int tType = tileData[localX][localY];
        return TileType.isPassableTile(tType);
    }

    private static class Point {
        int x, y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

}
