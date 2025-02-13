package io.github.pokemeetup.utils;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

public class PokemonLevelCalculator {
    // Same variance value as used in the client
    public static final float LEVEL_VARIANCE = 2f;

    /**
     * Calculates a Pokémon’s level based on its pixel position using the client’s style.
     * <p>
     * The level is computed as:
     * <br>
     * &nbsp;&nbsp;level = round( clamp( 2 + (distanceFromCenter / (tileSize * 50))
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;+ random(-LEVEL_VARIANCE, LEVEL_VARIANCE), minLevel, maxLevel ) )
     * <br>
     * In this example, minLevel is 1 and maxLevel is 100.
     *
     * @param pixelX   The x–coordinate in pixels.
     * @param pixelY   The y–coordinate in pixels.
     * @param tileSize The size of a tile (in pixels).
     * @return The calculated level.
     */
    public static int calculateLevel(float pixelX, float pixelY, float tileSize) {
        // Calculate distance from the world center (assumed to be at 0,0)
        float distance = Vector2.dst(pixelX, pixelY, 0, 0);
        // Base level increases with distance; note that (tileSize * 50) is the scaling factor
        float baseLevel = 2 + (distance / (tileSize * 50));
        // Add random variance (this makes the calculation non–deterministic)
        float variance = MathUtils.random(-LEVEL_VARIANCE, LEVEL_VARIANCE);
        // Clamp the value between 1 and 100 and round it to an integer
        return MathUtils.round(MathUtils.clamp(baseLevel + variance, 1, 100));
    }
}
