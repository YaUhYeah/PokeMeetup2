package io.github.pokemeetup.utils.textures;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.utils.GameLogger;

import java.util.HashMap;
import java.util.Map;

public class BlockTextureManager {
    private static final float FRAME_DURATION = 0.35f; // Adjust animation speed
    private static final Map<String, Animation<TextureRegion>> blockAnimations = new HashMap<>();
    private static final Map<String, TextureRegion[]> blockFrames = new HashMap<>();
    private final Map<String, TextureRegion> itemIcons;
    public BlockTextureManager() {
        this.itemIcons = new HashMap<>();
        initializeBlockTextures();
    }


    private static int getCustomAnimationFrame(float stateTime) {
        float totalCycleDuration = FRAME_DURATION * 4; // Time for complete cycle
        float cycleTime = stateTime % totalCycleDuration;
        int frame = (int) (cycleTime / FRAME_DURATION);
        switch (frame) {
            case 0: return 0; // First frame
            case 1: return 1; // Second frame
            case 2: return 2; // Third frame
            case 3: return 1; // Back to second frame
            default: return 0;
        }
    }

    public static TextureRegion getBlockFrame(PlaceableBlock block, float stateTime) {
        String blockId = block.getType().id;

        if (blockId.equals("chest")) {
            TextureRegion[] frames = blockFrames.get(blockId);
            if (frames != null) {
                boolean isOpen = block.isChestOpen();
                return frames[isOpen ? 1 : 0];
            }
            return null;
        }

        Animation<TextureRegion> animation = blockAnimations.get(blockId);
        if (animation != null) {
            if (blockId.equals("craftingtable") || blockId.equals("furnace")) {
                int frameIndex = getCustomAnimationFrame(stateTime);
                TextureRegion[] frames = blockFrames.get(blockId);
                if (frames != null && frameIndex < frames.length) {
                    return frames[frameIndex];
                }
            }
            return animation.getKeyFrame(stateTime, true);
        }
        return null;
    }



    private void initializeBlockTextures() {
        initializeExistingBlocks();
        initializeHouseBlocks();
        initializeRoofBlocks();

        GameLogger.info("Initialized block textures - Blocks loaded: " + blockFrames.keySet());
    }



    private void initializeHouseBlocks() {
        initializeSingleFrameBlock("house_middlesection");
        initializeSingleFrameBlock("house_middlesection_part");
        initializeSingleFrameBlock("house_midsection_part");
        initializeSingleFrameBlock("house_part");
        initializeSingleFrameBlock("house_planks");
        initializeSingleFrameBlock("wooden_door");
    }
    private void initializeExistingBlocks() {
        TextureRegion chestRegion = TextureManager.blocks.findRegion("chest");
        if (chestRegion != null) {
            TextureRegion[] chestFrames = new TextureRegion[2];
            int frameWidth = chestRegion.getRegionWidth() / 2;

            chestFrames[0] = new TextureRegion(
                chestRegion.getTexture(),
                chestRegion.getRegionX(),
                chestRegion.getRegionY(),
                frameWidth,
                chestRegion.getRegionHeight()
            );
            chestFrames[1] = new TextureRegion(
                chestRegion.getTexture(),
                chestRegion.getRegionX() + frameWidth,
                chestRegion.getRegionY(),
                frameWidth,
                chestRegion.getRegionHeight()
            );

            blockFrames.put("chest", chestFrames);
            blockAnimations.put("chest", new Animation<>(FRAME_DURATION, chestFrames));
            itemIcons.put("chest", chestFrames[0]);
        }
        TextureRegion craftingTableRegion = TextureManager.blocks.findRegion("craftingtable");
        if (craftingTableRegion != null) {
            TextureRegion[] craftingFrames = new TextureRegion[3];
            int frameWidth = craftingTableRegion.getRegionWidth() / 3;

            for (int i = 0; i < 3; i++) {
                craftingFrames[i] = new TextureRegion(
                    craftingTableRegion.getTexture(),
                    craftingTableRegion.getRegionX() + (i * frameWidth),
                    craftingTableRegion.getRegionY(),
                    frameWidth,
                    craftingTableRegion.getRegionHeight()
                );
            }
            blockFrames.put("craftingtable", craftingFrames);
            blockAnimations.put("craftingtable", new Animation<>(FRAME_DURATION, craftingFrames));
            itemIcons.put("craftingtable", craftingFrames[0]);
        }
        TextureRegion furnaceRegion = TextureManager.blocks.findRegion("furnace");
        if (furnaceRegion != null) {
            TextureRegion[] furnaceFrames = new TextureRegion[3];
            int frameWidth = furnaceRegion.getRegionWidth() / 3;

            for (int i = 0; i < 3; i++) {
                furnaceFrames[i] = new TextureRegion(
                    furnaceRegion.getTexture(),
                    furnaceRegion.getRegionX() + (i * frameWidth),
                    furnaceRegion.getRegionY(),
                    frameWidth,
                    furnaceRegion.getRegionHeight()
                );
            }
            blockFrames.put("furnace", furnaceFrames);
            blockAnimations.put("furnace", new Animation<>(FRAME_DURATION, furnaceFrames));
            itemIcons.put("furnace", furnaceFrames[0]);
        }
        TextureRegion woodenPlanks = TextureManager.blocks.findRegion("wooden_planks");
        if (woodenPlanks != null) {
            TextureRegion[] planksFrames = {new TextureRegion(woodenPlanks)};
            blockFrames.put("wooden_planks", planksFrames);
            blockAnimations.put("wooden_planks", new Animation<>(FRAME_DURATION, planksFrames));
            itemIcons.put("wooden_planks", planksFrames[0]);
        }
    }
    private void initializeRoofBlocks() {
        initializeSingleFrameBlock("roof_corner");
        initializeSingleFrameBlock("roof_middle");
        initializeSingleFrameBlock("roof_middle_outer");
        initializeSingleFrameBlock("roof_middle_outerside");
        initializeSingleFrameBlock("roof_middle_outside");
        initializeSingleFrameBlock("roof_middle_part");
        initializeSingleFrameBlock("roofinner");
    }

    private void initializeSingleFrameBlock(String blockId) {
        TextureRegion region = TextureManager.blocks.findRegion(blockId);
        if (region != null) {
            TextureRegion[] frames = {new TextureRegion(region)};
            blockFrames.put(blockId, frames);
            blockAnimations.put(blockId, new Animation<>(FRAME_DURATION, frames));
            itemIcons.put(blockId, frames[0]);
        }
    }
    public TextureRegion getItemIcon(String blockId) {
        return itemIcons.get(blockId);
    }

}
