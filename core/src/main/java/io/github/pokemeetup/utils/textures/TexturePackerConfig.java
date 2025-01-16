package io.github.pokemeetup.utils.textures;

import com.badlogic.gdx.tools.texturepacker.TexturePacker;
import com.badlogic.gdx.graphics.Texture.TextureFilter;

public class TexturePackerConfig {
    // Flag to enable/disable packing - set to false for production
    public static final boolean SHOULD_PACK = false;

    // Pack your textures - call this from your desktop launcher
    public static void pack() {
        if (!SHOULD_PACK) return;

        TexturePacker.Settings settings = new TexturePacker.Settings();

        // Basic settings
        settings.maxWidth = 2048;
        settings.maxHeight = 2048;
        settings.pot = true;
        settings.combineSubdirectories = true;

        // Pixel-perfect settings for tilesets
        settings.filterMin = TextureFilter.Nearest;
        settings.filterMag = TextureFilter.Nearest;
        settings.paddingX = 2;
        settings.paddingY = 2;

        // Enable this if you need to debug atlas packing
        settings.debug = false;

        try {

            // You can have multiple atlases for different purposes
            TexturePacker.process(settings,
                "assets/raw/tilesets",
                "assets/packed/tilesets",
                "tileset-atlas");

        } catch (Exception e) {
            System.err.println("Error packing textures: " + e.getMessage());
        }
    }
}
