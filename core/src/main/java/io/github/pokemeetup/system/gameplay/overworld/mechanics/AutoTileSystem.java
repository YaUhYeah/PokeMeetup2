// File: src/main/java/io/github/pokemeetup/system/gameplay/overworld/mechanics/AutoTileSystem.java
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
     * We do a 4-bit adjacency mask:
     *   bit 1 => Up
     *   bit 2 => Right
     *   bit 4 => Down
     *   bit 8 => Left
     * Then pass that mask to TextureManager.getAutoTileRegion("sand_shore", mask, animFrame)
     * which returns a 32×32 region scaled from the correct 16×16 piece.
     */
    public void applyShorelineAutotiling(Chunk chunk, int animFrame, World world) {
        final int size = Chunk.CHUNK_SIZE;

        // Step 1: Identify "shore" tiles. A shore tile is any non-water tile with a water neighbor.
        boolean[][] shoreMap = new boolean[size][size];
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                int worldX = chunk.getChunkX() * size + x;
                int worldY = chunk.getChunkY() * size + y;
                if (world.getTileTypeAt(worldX, worldY) != TileType.WATER) { // Any land tile
                    if (hasWaterNeighbor(world, worldX, worldY)) {
                        shoreMap[x][y] = true;
                    }
                }
            }
        }

        // Step 2: Prepare the chunk's overlay array
        TextureRegion[][] overlay = chunk.getAutotileRegions();
        if (overlay == null) {
            overlay = new TextureRegion[size][size];
            chunk.setAutotileRegions(overlay);
        }

        // Step 3: For each "shore" tile, build the 32×32 tile from a 4–bit edge mask.
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                if (!shoreMap[x][y]) {
                    overlay[x][y] = null;
                    continue;
                }
                int worldX = chunk.getChunkX() * size + x;
                int worldY = chunk.getChunkY() * size + y;
                int mask = computeEdgeMask(world, worldX, worldY);
                TextureRegion base32 = TextureManager.getAutoTileRegion("sand_shore", mask, animFrame);
                // FIX: Check for null base region before using it.
                if (base32 == null) {
                    System.err.println("AutoTileSystem.applyShorelineAutotiling: base32 is null for mask " + mask + " at (" + x + "," + y + ")");
                    overlay[x][y] = null;
                    continue;
                }
                CompositeRegion comp = new CompositeRegion(base32);
                overlay[x][y] = comp;
            }
        }

        // Step 4: Apply inside-corner overlays (if the sub–tile sheet is available)
        TextureRegion cornerSheet = TextureManager.getSubTile("sand_shore", animFrame, 2, 0);
        if (cornerSheet != null) {
            TextureRegion miniTL = new TextureRegion(cornerSheet, 0, 0, 16, 16);
            TextureRegion miniTR = new TextureRegion(cornerSheet, 16, 0, 16, 16);
            TextureRegion miniBL = new TextureRegion(cornerSheet, 0, 16, 16, 16);
            TextureRegion miniBR = new TextureRegion(cornerSheet, 16, 16, 16, 16);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    if (!shoreMap[x][y]) continue;
                    TextureRegion reg = overlay[x][y];
                    if (!(reg instanceof CompositeRegion)) continue;
                    CompositeRegion comp = (CompositeRegion) reg;
                    int worldX = chunk.getChunkX() * size + x;
                    int worldY = chunk.getChunkY() * size + y;
                    if (isInnerCornerTopLeft(world, worldX, worldY)) {
                        comp.addMiniOverlay(miniTL, 0, 16);
                    }
                    if (isInnerCornerTopRight(world, worldX, worldY)) {
                        comp.addMiniOverlay(miniTR, 16, 16);
                    }
                    if (isInnerCornerBottomLeft(world, worldX, worldY)) {
                        comp.addMiniOverlay(miniBL, 0, 0);
                    }
                    if (isInnerCornerBottomRight(world, worldX, worldY)) {
                        comp.addMiniOverlay(miniBR, 16, 0);
                    }
                }
            }
        }
    }

    /** 4-bit mask => bit 1=Up, bit2=Right, bit4=Down, bit8=Left */
    private int computeEdgeMask(World world, int worldX, int worldY) {
        int mask = 0;
        if (isWater(world, worldX, worldY + 1)) mask |= 1;  // up
        if (isWater(world, worldX + 1, worldY)) mask |= 2;  // right
        if (isWater(world, worldX, worldY - 1)) mask |= 4;  // down
        if (isWater(world, worldX - 1, worldY)) mask |= 8;  // left
        return mask;
    }

    // Inside corner checks: diagonal is water, but the two orthogonal neighbors are beach
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

    // Basic checks
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


    private boolean isWaterSafe(int[][] t, int x, int y) {
        if (x < 0 || y < 0 || x >= t.length || y >= t[0].length) return false;
        return (t[x][y] == TileType.WATER);
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

        public void addMiniOverlay(TextureRegion mini, int offX, int offY) {
            overlays.add(new MiniOverlay(mini, offX, offY));
        }

        public TextureRegion getBase32() {return base32;}
        public List<MiniOverlay> getOverlays() {return overlays;}
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
