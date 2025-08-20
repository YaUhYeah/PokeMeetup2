package io.github.pokemeetup.system.gameplay.overworld.mechanics;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.textures.TextureManager;
import io.github.pokemeetup.utils.textures.TileType;

import java.util.ArrayList;
import java.util.List;

public class AutoTileSystem {

    /**
     * --- OPTIMIZATION: This method is now called only when a chunk is loaded.
     * It computes a byte for each tile encoding all necessary shoreline information.
     * Lower 4 bits: 4-way edge mask for main tile.
     * Upper 4 bits: Flags for each of the 4 possible inner corners.
     */
    public void applyShorelineAutotiling(Chunk chunk, World world) {
        final int size = Chunk.CHUNK_SIZE;
        byte[][] shorelineData = new byte[size][size];

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                int worldX = chunk.getChunkX() * size + x;
                int worldY = chunk.getChunkY() * size + y;

                if (world.getTileTypeAt(worldX, worldY) != TileType.WATER && hasWaterNeighbor(world, worldX, worldY)) {
                    byte mask = 0;
                    // Edge mask (lower 4 bits)
                    mask |= (byte) computeEdgeMask(world, worldX, worldY);

                    // Inner corner mask (upper 4 bits)
                    if (isInnerCornerTopLeft(world, worldX, worldY)) mask |= 0x10;
                    if (isInnerCornerTopRight(world, worldX, worldY)) mask |= 0x20;
                    if (isInnerCornerBottomLeft(world, worldX, worldY)) mask |= 0x40;
                    if (isInnerCornerBottomRight(world, worldX, worldY)) mask |= 0x80;

                    shorelineData[x][y] = mask;
                } else {
                    shorelineData[x][y] = 0;
                }
            }
        }
        chunk.setShorelineData(shorelineData);
    }

    private int computeEdgeMask(World world, int worldX, int worldY) {
        int mask = 0;
        if (isWater(world, worldX, worldY + 1)) mask |= 1;  // up
        if (isWater(world, worldX + 1, worldY)) mask |= 2;  // right
        if (isWater(world, worldX, worldY - 1)) mask |= 4;  // down
        if (isWater(world, worldX - 1, worldY)) mask |= 8;  // left
        return mask;
    }
    private boolean isInnerCornerTopLeft(World world, int worldX, int worldY) {
        return isBeach(world, worldX, worldY + 1) && isBeach(world, worldX - 1, worldY) && isWater(world, worldX - 1, worldY + 1);
    }

    private boolean isInnerCornerTopRight(World world, int worldX, int worldY) {
        return isBeach(world, worldX, worldY + 1) && isBeach(world, worldX + 1, worldY) && isWater(world, worldX + 1, worldY + 1);
    }

    private boolean isInnerCornerBottomLeft(World world, int worldX, int worldY) {
        return isBeach(world, worldX, worldY - 1) && isBeach(world, worldX - 1, worldY) && isWater(world, worldX - 1, worldY - 1);
    }

    private boolean isInnerCornerBottomRight(World world, int worldX, int worldY) {
        return isBeach(world, worldX, worldY - 1) && isBeach(world, worldX + 1, worldY) && isWater(world, worldX + 1, worldY - 1);
    }
    private boolean isBeach(int tileID) {
        return (tileID == TileType.BEACH_SAND ||
            tileID == TileType.BEACH_GRASS ||
            tileID == TileType.BEACH_GRASS_2 ||
            tileID == TileType.BEACH_STARFISH ||
            tileID == TileType.BEACH_SHELL);
    }

    private boolean isBeach(World world, int worldX, int worldY) {
        return isBeach(world.getTileTypeAt(worldX, worldY));
    }

    private boolean isWater(World world, int worldX, int worldY) {
        return world.getTileTypeAt(worldX, worldY) == TileType.WATER;
    }

    private boolean hasWaterNeighbor(World world, int worldX, int worldY) {
        int[][] offsets = {
            {0, 1}, {1, 0}, {0, -1}, {-1, 0},  // orthogonal
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}   // diagonal
        };
        for (int[] off : offsets) {
            if (isWater(world, worldX + off[0], worldY + off[1])) return true;
        }
        return false;
    }





    /**
     * A "composite region" stores:
     *  - a 32×32 base tile
     *  - zero or more 16×16 corner overlays
     * This allows us to place inside-corner pieces on top of the base tile.
     */
    public static class CompositeRegion extends TextureRegion {
        private final TextureRegion base32;
        private final List<MiniOverlay> overlays = new ArrayList<>();

        public CompositeRegion(TextureRegion base) {
            super(base);
            this.base32 = base;
        }
    }

    /** A single 16×16 corner overlay with a local offset. */
    public static class MiniOverlay {
        public final TextureRegion region16;
        public final int offsetX, offsetY;
        public MiniOverlay(TextureRegion r, int x, int y) {
            region16 = r;
            offsetX  = x;
            offsetY  = y;
        }
    }
}
