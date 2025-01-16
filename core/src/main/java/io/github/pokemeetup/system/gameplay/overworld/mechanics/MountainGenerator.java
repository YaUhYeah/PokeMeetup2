package io.github.pokemeetup.system.gameplay.overworld.mechanics;

import com.badlogic.gdx.math.MathUtils;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.managers.BiomeManager;
import io.github.pokemeetup.utils.textures.TileType;

import java.util.Random;

/**
 * MountainGenerator: Responsible for creating Gen 3–style mountains.
 * Uses a 3x3 tile system for mountain faces, corners, and edges.
 */
public class MountainGenerator {
    // Elevation thresholds
    private static final double LEVEL_LOW = 0.4;
    private static final double LEVEL_MID = 0.6;
    private static final double LEVEL_HIGH = 0.8;

    // Probabilities
    private static final float STAIRS_CHANCE = 0.05f;
    private static final float CAVE_CHANCE = 0.02f;

    private final BiomeManager biomeManager;
    private final MountainTileManager mountainTileManager;
    private final long seed;

    public MountainGenerator(BiomeManager biomeManager, MountainTileManager mountainTileManager, long seed) {
        this.biomeManager = biomeManager;
        this.mountainTileManager = mountainTileManager;
        this.seed = seed;
    }

    public void generateMountains(Chunk chunk) {
        int[][] tileData = chunk.getTileData();
        if (tileData == null) {
            tileData = new int[Chunk.CHUNK_SIZE][Chunk.CHUNK_SIZE];
            chunk.setTileData(tileData);
        }

        // Step 1: Generate elevation map
        double[][] elevationMap = new double[Chunk.CHUNK_SIZE][Chunk.CHUNK_SIZE];
        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
                float worldX = (chunk.getChunkX() * Chunk.CHUNK_SIZE + x) * World.TILE_SIZE;
                float worldY = (chunk.getChunkY() * Chunk.CHUNK_SIZE + y) * World.TILE_SIZE;
                double height = biomeManager.getMountainHeight(worldX, worldY);
                elevationMap[x][y] = height;
            }
        }

        // Step 2: Assign base tiles by elevation band
        int[][] elevationBands = new int[Chunk.CHUNK_SIZE][Chunk.CHUNK_SIZE];
        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
                double h = elevationMap[x][y];
                int band;
                int tile;

                if (h < LEVEL_LOW) {
                    band = 0;
                    tile = TileType.GRASS;
                } else if (h < LEVEL_MID) {
                    band = 1;
                    tile = TileType.MOUNTAIN_BASE;
                } else if (h < LEVEL_HIGH) {
                    band = 2;
                    tile = TileType.MOUNTAIN_BASE;
                } else {
                    band = 3;
                    BiomeType biomeType = chunk.getBiome() != null ? chunk.getBiome().getType() : BiomeType.PLAINS;
                    tile = (biomeType == BiomeType.SNOW || biomeType == BiomeType.BIG_MOUNTAINS)
                        ? TileType.MOUNTAIN_SNOW_BASE
                        : TileType.MOUNTAIN_PEAK;
                }

                tileData[x][y] = tile;
                elevationBands[x][y] = band;
            }
        }

        // Step 3: Replace tiles on steep transitions with cliff/edge tiles
        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
                int currentBand = elevationBands[x][y];
                if (currentBand > 0 && hasLowerNeighbor(x, y, elevationBands)) {
                    int cliffTile = determineCliffTile(x, y, elevationBands);
                    tileData[x][y] = cliffTile;
                }
            }
        }

        // Step 4: Place stairs or caves
        Random random = new Random(seed + chunk.getChunkX() * 31L + chunk.getChunkY() * 17L);
        for (int x = 1; x < Chunk.CHUNK_SIZE - 1; x++) {
            for (int y = 1; y < Chunk.CHUNK_SIZE - 1; y++) {
                int tile = tileData[x][y];
                if (isCliffTile(tile)) {
                    if (random.nextFloat() < STAIRS_CHANCE && canPlaceStairs(x, y, elevationBands)) {
                        tileData[x][y] = TileType.STAIRS;
                        continue;
                    }
                    if (random.nextFloat() < CAVE_CHANCE && canPlaceCave(x, y, elevationBands)) {
                        tileData[x][y] = TileType.CAVE_ENTRANCE;
                    }
                }
            }
        }

        // Step 5: Auto-tiling / Smoothing if needed
        applyCliffAutotiling(tileData, elevationBands);

        chunk.setDirty(true);
    }

    private boolean hasLowerNeighbor(int x, int y, int[][] elevationBands) {
        int band = getBand(x, y, elevationBands);
        int n = getBand(x, y+1, elevationBands);
        int s = getBand(x, y-1, elevationBands);
        int e = getBand(x+1, y, elevationBands);
        int w = getBand(x-1, y, elevationBands);

        return (n < band) || (s < band) || (e < band) || (w < band);
    }

    private int determineCliffTile(int x, int y, int[][] elevationBands) {
        int band = getBand(x, y, elevationBands);
        int bandN = getBand(x, y+1, elevationBands);
        int bandS = getBand(x, y-1, elevationBands);
        int bandE = getBand(x+1, y, elevationBands);
        int bandW = getBand(x-1, y, elevationBands);

        boolean topLower = bandN < band;
        boolean bottomLower = bandS < band;
        boolean leftLower = bandW < band;
        boolean rightLower = bandE < band;

        boolean topSame = bandN == band;
        boolean bottomSame = bandS == band;
        boolean leftSame = bandW == band;
        boolean rightSame = bandE == band;

        // Determine tile type:
        // 1. Corners (two perpendicular directions lower):
        if (topLower && leftLower) {
            return pickCornerTile(TileType.MOUNTAIN_TILE_TOP_LEFT_ROCK_BG, TileType.MOUNTAIN_TILE_TOP_LEFT_GRASS_BG,
                bandN, bandW);
        } else if (topLower && rightLower) {
            return pickCornerTile(TileType.MOUNTAIN_TILE_TOP_RIGHT_ROCK_BG, TileType.MOUNTAIN_TILE_TOP_RIGHT_GRASS_BG,
                bandN, bandE);
        } else if (bottomLower && leftLower) {
            return pickCornerTile(TileType.MOUNTAIN_TILE_BOT_LEFT_ROCK_BG, TileType.MOUNTAIN_TILE_BOT_LEFT_GRASS_BG,
                bandS, bandW);
        } else if (bottomLower && rightLower) {
            return pickCornerTile(TileType.MOUNTAIN_TILE_BOT_RIGHT_ROCK_BG, TileType.MOUNTAIN_TILE_BOT_RIGHT_GRASS_BG,
                bandS, bandE);
        }

        // 2. Edges (only one direction lower):
        if (topLower && !leftLower && !rightLower) {
            return TileType.MOUNTAIN_TILE_TOP_MID;
        } else if (bottomLower && !leftLower && !rightLower) {
            return TileType.MOUNTAIN_TILE_BOT_MID;
        } else if (leftLower && !topLower && !bottomLower) {
            return TileType.MOUNTAIN_TILE_MID_LEFT;
        } else if (rightLower && !topLower && !bottomLower) {
            return TileType.MOUNTAIN_TILE_MID_RIGHT;
        }

        // 3. If surrounded by same or no lower neighbors (plateau/center):
        if (topSame && bottomSame && leftSame && rightSame) {
            return TileType.MOUNTAIN_TILE_CENTER;
        }

        // Fallback to center if no condition fits perfectly
        return TileType.MOUNTAIN_TILE_CENTER;
    }

    /**
     * Picks the appropriate corner tile variant based on whether the adjacent lower tiles
     * lead down to a lowest band area (band 0). If any lower side is band 0, use grass_bg,
     * else use rock_bg.
     */
    private int pickCornerTile(int rockBgTile, int grassBgTile, int bandA, int bandB) {
        // If either adjacent lower band is the lowest band (0), pick grass_bg
        if (bandA == 0 || bandB == 0) {
            return grassBgTile;
        }
        // Otherwise, pick rock_bg
        return rockBgTile;
    }

    private boolean isCliffTile(int tile) {
        // Now we consider "cliff tiles" as any of these mountain tiles that are not just base or center.
        // Since we used new tile types, let's consider all non-center 3x3 tiles as "cliff" or "edge".
        // Adjust if you want a stricter definition.
        if (tile == TileType.MOUNTAIN_TILE_CENTER || tile == TileType.GRASS || tile == TileType.MOUNTAIN_BASE
            || tile == TileType.MOUNTAIN_PEAK || tile == TileType.MOUNTAIN_SNOW_BASE) {
            return false;
        }
        return true;
    }

    private boolean canPlaceStairs(int x, int y, int[][] elevationBands) {
        int band = getBand(x, y, elevationBands);
        int up = getBand(x, y+1, elevationBands);
        return up > band;
    }

    private boolean canPlaceCave(int x, int y, int[][] elevationBands) {
        int band = getBand(x, y, elevationBands);
        return band == 1 || band == 2; // mid or high elevation cliff
    }

    private void applyCliffAutotiling(int[][] tileData, int[][] elevationBands) {
        // Placeholder for further refinements if needed.
        for (int x = 1; x < Chunk.CHUNK_SIZE - 1; x++) {
            for (int y = 1; y < Chunk.CHUNK_SIZE - 1; y++) {
                int tile = tileData[x][y];
                // If needed, add logic to refine corners or edges further.
                tileData[x][y] = refineCliffTile(x, y, tileData, elevationBands);
            }
        }
    }

    private int refineCliffTile(int x, int y, int[][] tileData, int[][] elevationBands) {
        // Add logic if certain corner tiles don't fit conditions
        // For now, return as is.
        return tileData[x][y];
    }

    private int getBand(int x, int y, int[][] elevationBands) {
        if (x < 0 || x >= Chunk.CHUNK_SIZE || y < 0 || y >= Chunk.CHUNK_SIZE) {
            return -1; // Out of bounds considered lower
        }
        return elevationBands[x][y];
    }
}
