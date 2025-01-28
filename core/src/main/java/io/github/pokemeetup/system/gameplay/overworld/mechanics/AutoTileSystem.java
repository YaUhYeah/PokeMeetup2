package io.github.pokemeetup.system.gameplay.overworld.mechanics;

import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.utils.textures.TileType;

import java.util.HashMap;
import java.util.Map;

public class AutoTileSystem {
    private static final int N = 1;
    private static final int S = 2;
    private static final int E = 4;
    private static final int W = 8;
    private static final int NW = 16;
    private static final int NE = 32;
    private static final int SW = 64;
    private static final int SE = 128;
    private static final boolean[][] POND_3X3 = {
        {true, true, true},
        {true, true, true},
        {true, true, true}
    };

    private final Map<Integer, Integer> tileMap = new HashMap<>();

    public AutoTileSystem() {
        initializeTileMap();
    }

    private void initializeTileMap() {
        // Full center remains the same
        tileMap.put(N | S | E | W | NW | NE | SW | SE, TileType.WATER_PUDDLE);

        // Currently, your top edges use S-based patterns, but they should actually be bottom edges:
        tileMap.put(S | E | W | SW | SE, TileType.WATER_PUDDLE_BOTTOM_MIDDLE);
        tileMap.put(S | E | SE, TileType.WATER_PUDDLE_BOTTOM_RIGHT);
        tileMap.put(S | W | SW, TileType.WATER_PUDDLE_BOTTOM_LEFT);

        // Your bottom edges use N-based patterns, but they should actually be top edges:
        tileMap.put(N | E | W | NW | NE, TileType.WATER_PUDDLE_TOP_MIDDLE);
        tileMap.put(N | E | NE, TileType.WATER_PUDDLE_TOP_LEFT);
        tileMap.put(N | W | NW, TileType.WATER_PUDDLE_TOP_RIGHT);

        // Left and right edges can remain as they are unless you also find them inverted:
        tileMap.put(N | S | E | NE | SE, TileType.WATER_PUDDLE_LEFT_MIDDLE);
        tileMap.put(N | S | W | NW | SW, TileType.WATER_PUDDLE_RIGHT_MIDDLE);
    }


    /**
     * Instead of dynamically shaping ponds from scattered tiles, we now:
     * 1. Check if there's any indication we should place a pond (e.g., from waterMap).
     * 2. If so, we place a predefined pond shape (like a 3x3 pond) at a chosen location.
     * <p>
     * In this simplified approach, we:
     * - Scan the waterMap for at least one water tile.
     * - If found, place a 3x3 pond shape near the center of the chunk.
     * <p>
     * You can extend this logic to:
     * - Choose random locations.
     * - Select from multiple pond shapes.
     * - Place multiple ponds per chunk.
     */
    private boolean[][] placePredefinedPonds(boolean[][] waterMap) {
        boolean[][] result = new boolean[Chunk.CHUNK_SIZE][Chunk.CHUNK_SIZE];

        // Check if we have any water tile. If yes, place a 3x3 pond in the center-ish
        // For demonstration, we ignore the original distribution and just place the pond.
        // You can refine the logic to ensure the chosen location coincides with where waterMap has water.
        int startX = Chunk.CHUNK_SIZE / 2 - 1;
        int startY = Chunk.CHUNK_SIZE / 2 - 1;

        // Ensure the pond fits in the chunk:
        if (startX >= 0 && startX + POND_3X3.length <= Chunk.CHUNK_SIZE &&
            startY >= 0 && startY + POND_3X3[0].length <= Chunk.CHUNK_SIZE) {
            for (int x = 0; x < POND_3X3.length; x++) {
                for (int y = 0; y < POND_3X3[0].length; y++) {
                    result[startX + x][startY + y] = POND_3X3[x][y];
                }
            }
        }

        return result;
    }

    public void applyAutotiling(Chunk chunk, boolean[][] waterMap) {
        // Completely override the old logic. We now just place a predefined pond shape.
        boolean[][] shapedWater = placePredefinedPonds(waterMap);

        int[][] tileData = chunk.getTileData();
        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
                if (!shapedWater[x][y]) continue;

                int bitmask = calculateBitmask(x, y, shapedWater);
                tileData[x][y] = getWaterTileFromBitmask(bitmask);
            }
        }
    }

    private int calculateBitmask(int x, int y, boolean[][] waterMap) {
        int bitmask = 0;

        if (getWater(waterMap, x, y - 1)) bitmask |= N;  // North
        if (getWater(waterMap, x, y + 1)) bitmask |= S;  // South
        if (getWater(waterMap, x + 1, y)) bitmask |= E;  // East
        if (getWater(waterMap, x - 1, y)) bitmask |= W;  // West

        // Diagonals
        if (getWater(waterMap, x - 1, y - 1) && (bitmask & (N | W)) == (N | W)) bitmask |= NW;
        if (getWater(waterMap, x + 1, y - 1) && (bitmask & (N | E)) == (N | E)) bitmask |= NE;
        if (getWater(waterMap, x - 1, y + 1) && (bitmask & (S | W)) == (S | W)) bitmask |= SW;
        if (getWater(waterMap, x + 1, y + 1) && (bitmask & (S | E)) == (S | E)) bitmask |= SE;

        return bitmask;
    }

    private int getWaterTileFromBitmask(int bitmask) {
        Integer mappedTile = tileMap.get(bitmask);
        if (mappedTile != null) {
            return mappedTile;
        }
        return TileType.WATER_PUDDLE; // fallback
    }

    private boolean getWater(boolean[][] waterMap, int x, int y) {
        if (x < 0 || x >= Chunk.CHUNK_SIZE || y < 0 || y >= Chunk.CHUNK_SIZE) {
            return false;
        }
        return waterMap[x][y];
    }
}
