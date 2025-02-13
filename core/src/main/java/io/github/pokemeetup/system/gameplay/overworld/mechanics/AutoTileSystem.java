package io.github.pokemeetup.system.gameplay.overworld.mechanics;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.utils.textures.TextureManager;
import io.github.pokemeetup.utils.textures.TileType;

import java.util.ArrayList;
import java.util.List;

/**
 * Pokémon Essentials–style “sand_shore” autotile:
 * - Uses a 4-bit mask for Up/Right/Down/Left
 * - Maps that bitmask to one of 47 mini-tiles in the RMXP format
 * - Also applies "inside corner" overlays if needed
 */
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
    public void applyShorelineAutotiling(Chunk chunk, int animFrame) {
        final int size = Chunk.CHUNK_SIZE;
        int[][] tiles = chunk.getTileData();

        // Step 1: Identify "beach" vs. "shore"
        boolean[][] beachMap = new boolean[size][size];
        boolean[][] shoreMap = new boolean[size][size];
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                if (isBeach(tiles[x][y])) {
                    beachMap[x][y] = true;
                    if (hasWaterNeighbor(tiles, x, y)) {
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
                int mask = computeEdgeMask(tiles, x, y);
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
                    if (isInnerCornerTopLeft(tiles, x, y)) {
                        comp.addMiniOverlay(miniTL, 0, 16);
                    }
                    if (isInnerCornerTopRight(tiles, x, y)) {
                        comp.addMiniOverlay(miniTR, 16, 16);
                    }
                    if (isInnerCornerBottomLeft(tiles, x, y)) {
                        comp.addMiniOverlay(miniBL, 0, 0);
                    }
                    if (isInnerCornerBottomRight(tiles, x, y)) {
                        comp.addMiniOverlay(miniBR, 16, 0);
                    }
                }
            }
        }
    }

    /** 4-bit mask => bit 1=Up, bit2=Right, bit4=Down, bit8=Left */
    private int computeEdgeMask(int[][] tiles, int x, int y) {
        int mask=0;
        if (isWaterSafe(tiles, x,   y+1)) mask |= 1;  // up
        if (isWaterSafe(tiles, x+1, y  )) mask |= 2;  // right
        if (isWaterSafe(tiles, x,   y-1)) mask |= 4;  // down
        if (isWaterSafe(tiles, x-1, y  )) mask |= 8;  // left
        return mask;
    }

    // Inside corner checks: diagonal is water, but the two orthogonal neighbors are beach
    private boolean isInnerCornerTopLeft(int[][] t, int x, int y) {
        return isBeachSafe(t,x,y+1) && isBeachSafe(t,x-1,y) && isWater(t,x-1,y+1);
    }
    private boolean isInnerCornerTopRight(int[][] t, int x, int y) {
        return isBeachSafe(t,x,y+1) && isBeachSafe(t,x+1,y) && isWater(t,x+1,y+1);
    }
    private boolean isInnerCornerBottomLeft(int[][] t, int x, int y) {
        return isBeachSafe(t,x,y-1) && isBeachSafe(t,x-1,y) && isWater(t,x-1,y-1);
    }
    private boolean isInnerCornerBottomRight(int[][] t, int x, int y) {
        return isBeachSafe(t,x,y-1) && isBeachSafe(t,x+1,y) && isWater(t,x+1,y-1);
    }

    // Basic checks
    private boolean isBeach(int tileID) {
        return (tileID == TileType.BEACH_SAND ||
            tileID == TileType.BEACH_GRASS ||
            tileID == TileType.BEACH_GRASS_2 ||
            tileID == TileType.BEACH_STARFISH ||
            tileID == TileType.BEACH_SHELL );
    }
    private boolean isBeachSafe(int[][] t, int x, int y) {
        if (x<0||y<0||x>=t.length||y>=t[0].length) return false;
        return isBeach(t[x][y]);
    }
    private boolean isWater(int[][] t, int x, int y) {
        if (x<0||y<0||x>=t.length||y>=t[0].length) return false;
        return (t[x][y] == TileType.WATER);
    }
    private boolean isWaterSafe(int[][] t,int x,int y){
        if (x<0||y<0||x>=t.length||y>=t[0].length) return false;
        return (t[x][y] == TileType.WATER);
    }
    private boolean hasWaterNeighbor(int[][] t, int x, int y) {
        int[][] offsets = {
            { 0,  1}, { 1,  0}, { 0, -1}, {-1,  0},  // orthogonal
            { 1,  1}, { 1, -1}, {-1,  1}, {-1, -1}   // diagonal
        };
        for (int[] off : offsets) {
            int nx = x + off[0];
            int ny = y + off[1];
            if (nx < 0 || ny < 0 || nx >= t.length || ny >= t[0].length) continue;
            if (t[nx][ny] == TileType.WATER) return true;
        }
        return false;
    }
    public void applySeaAutotiling(Chunk chunk, int animFrame) {
        final int size = Chunk.CHUNK_SIZE;
        int[][] tiles = chunk.getTileData();
        // Retrieve or create a separate sea overlay array (you’ll need to add these in your Chunk class)
        TextureRegion[][] seaOverlay = chunk.getSeatileRegions();
        if (seaOverlay == null) {
            seaOverlay = new TextureRegion[size][size];
            chunk.setAutotileRegions(seaOverlay);
        }

        // Process each tile in the chunk.
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                // We only process water tiles.
                if (tiles[x][y] != TileType.WATER) {
                    seaOverlay[x][y] = null;
                    continue;
                }

                // Determine if this water tile is on an edge.
                // (An edge tile has at least one orthogonal neighbor that is not water
                // and is not a beach tile.)
                boolean isEdge = false;
                int[][] offsets = { {0, 1}, {1, 0}, {0, -1}, {-1, 0} };
                for (int[] off : offsets) {
                    int nx = x + off[0], ny = y + off[1];
                    if (nx < 0 || ny < 0 || nx >= size || ny >= size) continue;
                    int neighborTile = tiles[nx][ny];
                    // If neighbor is land and not a beach tile, then this is an edge.
                    if (neighborTile != TileType.WATER && !isBeach(neighborTile)) {
                        isEdge = true;
                        break;
                    }
                }
                if (isEdge) {
                    int mask = computeSeaEdgeMask(tiles, x, y);
                    TextureRegion seaTile = TextureManager.getAutoTileRegion("sea", mask, animFrame);
                    seaOverlay[x][y] = seaTile;
                } else {
                    // Center water – no overlay needed.
                    seaOverlay[x][y] = null;
                }
            }
        }
    }

    /**
     * Computes a 4–bit mask for a water tile at (x,y) based on its orthogonal neighbors.
     * For each neighbor that is water, a bit is set.
     */
    private int computeSeaEdgeMask(int[][] tiles, int x, int y) {
        int mask = 0;
        if (isWaterSafe(tiles, x, y + 1)) mask |= 1;  // Up
        if (isWaterSafe(tiles, x + 1, y)) mask |= 2;  // Right
        if (isWaterSafe(tiles, x, y - 1)) mask |= 4;  // Down
        if (isWaterSafe(tiles, x - 1, y)) mask |= 8;  // Left
        return mask;
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
