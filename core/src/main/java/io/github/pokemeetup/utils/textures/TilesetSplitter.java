package io.github.pokemeetup.utils.textures;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Array;
import java.util.*;

public class TilesetSplitter {
    public static class TilePosition {
        public final int row;
        public final int col;
        public final int index;

        public TilePosition(int row, int col, int index) {
            this.row = row;
            this.col = col;
            this.index = index;
        }

        // Get coordinate-based name (e.g., "3_4" for row 3, col 4)
        public String getCoordName() {
            return row + "_" + col;
        }
    }

    public static class Tile {
        public final TextureRegion region;
        public final TilePosition position;

        public Tile(TextureRegion region, TilePosition position) {
            this.region = region;
            this.position = position;
        }
    }

    /**
     * Splits tileset and provides position information for each tile
     */
    public static Array<Tile> splitTilesetWithPosition(TextureAtlas atlas, String regionName,
                                                       int tileWidth, int tileHeight, int spacing) {

        TextureAtlas.AtlasRegion region = atlas.findRegion(regionName);
        if (region == null) {
            throw new IllegalArgumentException("Region '" + regionName + "' not found in atlas");
        }

        Array<Tile> tiles = new Array<>();

        int numRows = region.getRegionHeight() / (tileHeight + spacing);
        int numCols = region.getRegionWidth() / (tileWidth + spacing);

        int index = 0;
        for (int row = 0; row < numRows; row++) {
            for (int col = 0; col < numCols; col++) {
                TextureRegion tileRegion = new TextureRegion(region,
                    col * (tileWidth + spacing),
                    row * (tileHeight + spacing),
                    tileWidth,
                    tileHeight
                );

                TilePosition position = new TilePosition(row, col, index);
                tiles.add(new Tile(tileRegion, position));
                index++;
            }
        }

        return tiles;
    }

    /**
     * Custom naming strategy interface
     */
    public interface TileNamingStrategy {
        String getName(TilePosition position, String baseRegionName);
    }

    // Some predefined naming strategies
    public static final TileNamingStrategy COORD_NAMING = (pos, base) ->
        base + "_" + pos.row + "_" + pos.col;

    public static final TileNamingStrategy ROW_FIRST_NAMING = (pos, base) ->
        base + "_row" + pos.row + "_" + pos.col;

    public static final TileNamingStrategy LAYER_NAMING = (pos, base) ->
        String.format("%s_layer%d_tile%d", base, pos.row, pos.col);

    /**
     * Split tileset with custom naming strategy
     */
    public static Map<String, TextureRegion> splitTilesetCustomNaming(
        TextureAtlas atlas,
        String regionName,
        int tileWidth,
        int tileHeight,
        int spacing,
        TileNamingStrategy namingStrategy) {

        Array<Tile> tiles = splitTilesetWithPosition(atlas, regionName, tileWidth, tileHeight, spacing);
        Map<String, TextureRegion> namedTiles = new HashMap<>();

        for (Tile tile : tiles) {
            String name = namingStrategy.getName(tile.position, regionName);
            namedTiles.put(name, tile.region);
        }

        return namedTiles;
    }
}
