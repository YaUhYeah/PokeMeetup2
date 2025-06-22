package io.github.pokemeetup.utils;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

public class PokemonLevelCalculator {
    public static final float LEVEL_VARIANCE = 2f;

    public static int calculateLevel(float pixelX, float pixelY, float tileSize) {
        float distance = Vector2.dst(pixelX, pixelY, 0, 0);
        float baseLevel = 2 + (distance / (tileSize * 50));
        float variance = MathUtils.random(-LEVEL_VARIANCE, LEVEL_VARIANCE);
        return MathUtils.round(MathUtils.clamp(baseLevel + variance, 1, 100));
    }
}
