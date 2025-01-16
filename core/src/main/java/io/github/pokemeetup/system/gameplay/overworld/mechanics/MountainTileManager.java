package io.github.pokemeetup.system.gameplay.overworld.mechanics;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.utils.GameLogger;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class MountainTileManager {
    private final TextureAtlas atlas;
    private final Map<String, TextureRegion> mountainTiles;

    public enum MountainTileType {
        BASE_BROWN,
        BASE_SNOW,
        SLOPE_LEFT,
        SLOPE_RIGHT,
        CORNER_TOP_LEFT,
        CORNER_TOP_RIGHT,
        CORNER_BOTTOM_LEFT,
        CORNER_BOTTOM_RIGHT,
        PEAK_SNOW,
        PEAK_ROCK,
        CAVE_ENTRANCE,
        CLIFF_FACE,
        ROCK_SMALL,
        ROCK_MEDIUM,
        ROCK_LARGE,
        SNOW_OVERLAY_1,
        SNOW_OVERLAY_2,
        GRASS_OVERLAY
    }

    public MountainTileManager(TextureAtlas atlas) {
        this.atlas = atlas;
        this.mountainTiles = new HashMap<>();
        loadTiles();
    }

    private void loadTiles() {
        // Load all mountain tiles from atlas
        for (MountainTileType type : MountainTileType.values()) {
            String tileName = "mountain_" + type.name().toLowerCase();
            TextureRegion region = atlas.findRegion(tileName);
            if (region != null) {
                mountainTiles.put(tileName, region);
            } else {
                GameLogger.error("Failed to load mountain tile: " + tileName);
            }
        }
    }

    public TextureRegion getTile(MountainTileType type) {
        String tileName = "mountain_" + type.name().toLowerCase();
        return mountainTiles.get(tileName);
    }

    public TextureRegion getRandomRock(Random random) {
        MountainTileType[] rockTypes = {
            MountainTileType.ROCK_SMALL,
            MountainTileType.ROCK_MEDIUM,
            MountainTileType.ROCK_LARGE
        };
        return getTile(rockTypes[random.nextInt(rockTypes.length)]);
    }



    private boolean isHigherThan(int height1, int height2) {
        if (height2 == -1) return true;
        return height1 > height2;
    }

    // Method to determine if a tile should have snow overlay
    public boolean shouldHaveSnowOverlay(int height, Random random) {
        return height > 2 && random.nextFloat() < 0.7f;
    }

    // Get appropriate overlay based on height and neighbors
    public TextureRegion getOverlay(int height, Random random) {
        if (height > 3) {
            return getTile(random.nextBoolean() ?
                MountainTileType.SNOW_OVERLAY_1 :
                MountainTileType.SNOW_OVERLAY_2);
        }
        return getTile(MountainTileType.GRASS_OVERLAY);
    }
}
