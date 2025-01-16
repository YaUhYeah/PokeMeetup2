package io.github.pokemeetup.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;

public class ResponsiveLayout {
    // Screen size breakpoints
    public static final int SMALL_SCREEN_WIDTH = 800;
    public static final int MEDIUM_SCREEN_WIDTH = 1280;

    public static ScreenSize getScreenSize() {
        int width = Gdx.graphics.getWidth();
        if (width <= SMALL_SCREEN_WIDTH) return ScreenSize.SMALL;
        if (width <= MEDIUM_SCREEN_WIDTH) return ScreenSize.MEDIUM;
        return ScreenSize.LARGE;
    }

    public static float getFontScale() {
        switch (getScreenSize()) {
            case SMALL:
                return 0.5f;
            case MEDIUM:
                return 0.75f;
            default:
                return 1f;
        }
    }

    public static float getPadding() {
        switch (getScreenSize()) {
            case SMALL:
                return 10f;
            case MEDIUM:
                return 15f;
            default:
                return 20f;
        }
    }

    public static Vector2 getElementSize(float baseWidth, float baseHeight) {
        float scale = getFontScale();
        return new Vector2(baseWidth * scale, baseHeight * scale);
    }

    // Screen size categories
    public enum ScreenSize {
        SMALL, MEDIUM, LARGE
    }
}
