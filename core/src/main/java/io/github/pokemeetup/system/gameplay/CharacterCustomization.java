package io.github.pokemeetup.system.gameplay;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;

public class CharacterCustomization {
    public enum ClothingType {
        NONE(""),
        BASIC("basic"),
        DRESS("dress"),
        FLORAL("floral"),
        OVERALLS("overalls"),
        PANTS("pants"),
        PANTS_SUIT("pants_suit"),
        SAILOR("sailor"),
        SAILOR_BOW("sailor_bow"),
        SKIRT("skirt"),
        SKULL("skull"),
        SPAGHETTI("spaghetti"),
        SPORTY("sporty"),
        STRIPE("stripe"),
        SUIT("suit"),
        WITCH("witch"),
        CLOWN("clown"),
        PUMPKIN("pumpkin");

        private final String atlasKey;

        ClothingType(String atlasKey) {
            this.atlasKey = atlasKey;
        }

        public String getAtlasKey() {
            return atlasKey;
        }
    }

    private final TextureAtlas characterAtlas = TextureManager.characters;
    private final int SKIN_COLUMN_WIDTH = 256; // 4 frames per row
    private final TextureAtlas clothingAtlas = TextureManager.clothing;
    private ClothingType currentOutfit = ClothingType.NONE;
    private int currentSkinTone = 0;
        // Add frame dimensions
        private static final int SPRITE_WIDTH = 32;
        private static final int SPRITE_HEIGHT = 32;

        // Method to combine character and clothing frames
        private TextureRegion[] combineFrames(TextureRegion[] baseFrames, TextureRegion[] clothingFrames) {
            if (clothingFrames == null) return baseFrames;

            TextureRegion[] combinedFrames = new TextureRegion[baseFrames.length];

            for (int i = 0; i < baseFrames.length; i++) {
                // Create a new FrameBuffer to combine textures
                FrameBuffer frameBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, SPRITE_WIDTH, SPRITE_HEIGHT, false);
                SpriteBatch batch = new SpriteBatch();

                frameBuffer.begin();
                batch.begin();

                // Draw base character
                batch.draw(baseFrames[i], 0, 0, SPRITE_WIDTH, SPRITE_HEIGHT);

                // Draw clothing on top
                if (clothingFrames[i] != null) {
                    batch.draw(clothingFrames[i], 0, 0, SPRITE_WIDTH, SPRITE_HEIGHT);
                }

                batch.end();
                frameBuffer.end();

                // Convert to TextureRegion
                combinedFrames[i] = new TextureRegion(frameBuffer.getColorBufferTexture());
                combinedFrames[i].flip(false, true); // Correct texture orientation

                // Cleanup
                frameBuffer.dispose();
                batch.dispose();
            }

            return combinedFrames;
        }

    public void setOutfit(ClothingType outfit) {
        if (outfit != null) {
            this.currentOutfit = outfit;
            GameLogger.info("Changed outfit to: " + outfit.name());
        }
    }

    public ClothingType getCurrentOutfit() {
        return currentOutfit;
    }

    public void setSkinTone(int toneIndex) {
        if (toneIndex >= 0 && toneIndex < 8) {
            this.currentSkinTone = toneIndex;
            GameLogger.info("Changed skin tone to: " + toneIndex);
        }
    }


    private static final int SOURCE_SPRITE_SIZE = 32;
    private static final int DISPLAY_WIDTH = 32;
    private static final int DISPLAY_HEIGHT = 48;

    public TextureRegion[] getCharacterFrames(String action, int frameCount) {
        TextureRegion[] frames = new TextureRegion[frameCount];
        TextureRegion[] clothingFrames = new TextureRegion[frameCount];

        TextureRegion fullSheet = characterAtlas.findRegion("char_all");
        TextureRegion clothingSheet = currentOutfit != ClothingType.NONE ?
            clothingAtlas.findRegion(currentOutfit.getAtlasKey()) : null;

        for (int i = 0; i < frameCount; i++) {
            int frameX = (currentSkinTone * 256) + (i * SOURCE_SPRITE_SIZE);
            int frameY = getActionOffset(action);

            frames[i] = new TextureRegion(fullSheet,
                frameX, frameY,
                SOURCE_SPRITE_SIZE, SOURCE_SPRITE_SIZE);

            if (clothingSheet != null) {
                clothingFrames[i] = new TextureRegion(clothingSheet,
                    frameX, frameY,
                    SOURCE_SPRITE_SIZE, SOURCE_SPRITE_SIZE);
            }
        }

        return combineFrames(frames, clothingFrames);
    }
    private int getActionOffset(String action) {
        // Map actions to Y positions in spritesheet
        switch (action.toLowerCase()) {
            case "idle": return 0;
            case "walk": return 64;
            case "run": return 128;
            case "punch": return 192;
            case "sword": return 256;
            case "pickaxe": return 320;
            case "axe": return 384;
            case "water": return 448;
            case "emote_happy": return 512;
            case "emote_sad": return 576;
            default: return 0;
        }
    }

}
