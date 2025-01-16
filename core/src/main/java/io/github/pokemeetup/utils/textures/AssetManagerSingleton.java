// src/io/github/pokemeetup/managers/AssetManagerSingleton.java

package io.github.pokemeetup.utils.textures;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;

/**
 * Singleton class for managing shared assets like Skin and TextureAtlas.
 */
public class AssetManagerSingleton {
    private static Skin skin;
    private static TextureAtlas textureAtlas;

    /**
     * Retrieves the singleton instance of the Skin.
     *
     * @return The Skin instance.
     */
    public static Skin getSkin() {
        if (skin == null) {
            skin = new Skin(Gdx.files.internal("Skins/uiskin.json"));
        }
        return skin;
    }

    /**
     * Retrieves the singleton instance of the TextureAtlas.
     *
     * @return The TextureAtlas instance.
     */
    public static TextureAtlas getTextureAtlas() {
        if (textureAtlas == null) {
            textureAtlas = new TextureAtlas(Gdx.files.internal("atlas/game-atlas"));
        }
        return textureAtlas;
    }

    /**
     * Disposes of the Skin and TextureAtlas when they're no longer needed.
     */
    public static void dispose() {
        if (skin != null) {
            skin.dispose();
            skin = null;
        }
        if (textureAtlas != null) {
            textureAtlas.dispose();
            textureAtlas = null;
        }
    }
}
