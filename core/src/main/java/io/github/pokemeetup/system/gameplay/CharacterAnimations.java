package io.github.pokemeetup.system.gameplay;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;

import java.util.*;

public class CharacterAnimations {  private static final int SOURCE_SPRITE_SIZE = 32;
    private static final int FRAMES_PER_SKIN = 8; // 8 columns per skin tone
    private static final int DIRECTIONS_PER_ROW = 4; // 4 directions (DOWN, LEFT, RIGHT, UP)


    private static final float DEFAULT_FRAME_DURATION = 0.15f;

    // Animation durations
    private static final float WALK_DURATION = 0.125f;  // 8 frames per tile
    private static final float RUN_DURATION = 0.1f;
    private static final float EMOTE_DURATION = 0.25f;
    private static final float ACTION_DURATION = 0.15f;

    private final Map<String, Animation<TextureRegion>> animations;
    private final Map<String, Map<String, Animation<TextureRegion>>> clothingAnimations;

    private final Map<String, Animation<TextureRegion>> hairAnimations;
    private int currentSkinTone = 0;


    public enum HairStyle {
        BOB("bob"),
        BRAIDS("braids"),
        EXTRA_LONG("extra_long"),
        EXTRA_LONG_SKIRT("extra_long_skirt"),
        FRENCH_CURL("french_curl"),
        GENTLEMAN("gentleman"),
        LONG_STRAIGHT("long_straight"),
        PONYTAIL("ponytail"),
        SPACEBUNS("spacebuns"),
        WAVY("wavy"),
        BUZZCUT("buzzcut"),
        CURLY("curly"),
        EMO("emo"),
        LONG_STRAIGHT_SKIRT("long_straight_skirt"),
        MIDIWAVE("midiwave");

        final String atlasKey;

        HairStyle(String atlasKey) {
            this.atlasKey = atlasKey;
        }
    }

    private final TextureAtlas hairAtlas;
    private static final float LONG_EMOTE_DURATION = 0.2f;
    private static final int IDLE_ROW = 0;
    private static final int WALK_ROW = 1;
    private static final int RUN_ROW = 2;
    private static final int SPRITE_SIZE = 32;
    private final TextureAtlas characterAtlas;
    private final Map<String, TextureRegion> directionalFrames;
    private final Map<String, Animation<TextureRegion>> walkAnimations;
    private final Map<String, Animation<TextureRegion>> runAnimations;
    private List<OutfitLayer> activeOutfitLayers = new ArrayList<>();
    public CharacterAnimations(TextureAtlas atlas) {
        this.characterAtlas = atlas;
        this.hairAtlas = TextureManager.hairstyles;
        this.walkAnimations = new HashMap<>();
        this.runAnimations = new HashMap<>();
        this.directionalFrames = new HashMap<>();
        this.clothingAnimations = new HashMap<>();
        this.hairAnimations = new HashMap<>();
        this.animations = new HashMap<>();
        initializeFrames();
        initializeAnimations();
        initializeDirectionalFrames();
    }
    public void render(SpriteBatch batch, float x, float y, float width, float height,
                       CharacterAction action, Direction dir, float stateTime) {
        // Base character
        TextureRegion baseFrame = getFrame(action, dir, stateTime);
        if (baseFrame != null) {
            batch.draw(baseFrame, x, y, width, height);
        }

        // Clothing layers (sorted by z-index)
        for (OutfitLayer layer : activeOutfitLayers) {
            TextureRegion clothing = getClothingOverlayForType(
                layer.type, action, dir, stateTime);
            if (clothing != null) {
                batch.draw(clothing, x, y, width, height);
            }
        }

        // Hair on top
        TextureRegion hair = getHairFrame(action, dir, stateTime);
        if (hair != null) {
            batch.draw(hair, x, y, width, height);
        }
    }


    private void initializeAnimations() {
        TextureRegion fullSheet = characterAtlas.findRegion("char_all");
        if (fullSheet == null) {
            GameLogger.error("Failed to load character sprite sheet!");
            return;
        }

        // Clear existing animations
        animations.clear();
        clothingAnimations.clear();
        hairAnimations.clear();

        // Initialize for each action and direction
        for (CharacterAction action : CharacterAction.values()) {
            for (Direction dir : Direction.values()) {
                String key = getAnimationKey(action, dir);
                Animation<TextureRegion> anim = createAnimation(fullSheet, action, dir);
                animations.put(key, anim);
            }
        }

        // Initialize hair animations if hair style is set
        if (currentHairStyle != null) {
            initializeHairAnimations();
        }
    }

    public TextureRegion getFrame(CharacterAction action, Direction dir, float stateTime) {
        // Special handling for IDLE
        if (action == CharacterAction.IDLE) {
            return getDirectionalFrame(dir);
        }

        String key = getAnimationKey(action, dir);
        Animation<TextureRegion> animation = animations.get(key);

        if (animation == null) {
            return getDefaultFrame();
        }

        return animation.getKeyFrame(stateTime,
            action == CharacterAction.WALK || action == CharacterAction.RUN);
    }
    private Animation<TextureRegion> createAnimation(TextureRegion sheet,
                                                     CharacterAction action,
                                                     Direction dir) {
        TextureRegion[] frames = new TextureRegion[action.frameCount];

        for (int i = 0; i < action.frameCount; i++) {
            int frameIndex = action.startFrame + i;
            frames[i] = getFrameFromSheet(sheet, currentSkinTone, frameIndex, dir);
        }

        Animation<TextureRegion> animation = new Animation<>(action.frameDuration, frames);

        // Loop only walking and running
        if (action == CharacterAction.WALK || action == CharacterAction.RUN) {
            animation.setPlayMode(Animation.PlayMode.LOOP);
        } else {
            animation.setPlayMode(Animation.PlayMode.NORMAL);
        }

        return animation;
    }


    private TextureRegion getClothingOverlayForType(String clothingType,
                                                    CharacterAction action,
                                                    Direction dir,
                                                    float stateTime) {
        TextureRegion clothingSheet = TextureManager.clothing.findRegion(clothingType);
        if (clothingSheet == null) return null;

        // For IDLE, use the first frame of the walking animation
        if (action == CharacterAction.IDLE) {
            return new TextureRegion(
                clothingSheet,
                currentSkinTone * (SOURCE_SPRITE_SIZE * 8),  // Use first frame
                dir.rowOffset * SOURCE_SPRITE_SIZE,
                SOURCE_SPRITE_SIZE,
                SOURCE_SPRITE_SIZE
            );
        }

        // For running animations, ensure we're using the correct offset
        int frameIndex = getFrameIndexForAction(action, stateTime);
        int frameX = (currentSkinTone * (SOURCE_SPRITE_SIZE * 8)) +
            ((action.startFrame + frameIndex) * SOURCE_SPRITE_SIZE);

        return new TextureRegion(
            clothingSheet,
            frameX,
            dir.rowOffset * SOURCE_SPRITE_SIZE,
            SOURCE_SPRITE_SIZE,
            SOURCE_SPRITE_SIZE
        );
    }


    private int getFrameIndexForAction(CharacterAction action, float stateTime) {
        if (action == CharacterAction.IDLE) {
            return 0;
        }

        float animationTime = stateTime % (action.frameCount * action.frameDuration);
        int frameIndex = (int) (animationTime / action.frameDuration);
        return frameIndex % action.frameCount;
    }
    private TextureRegion getHairFrame(CharacterAction action, Direction dir, float stateTime) {
        if (currentHairStyle == null) return null;

        TextureRegion hairSheet = TextureManager.hairstyles.findRegion(currentHairStyle.atlasKey);
        if (hairSheet == null) return null;

        // For IDLE, use the first frame
        if (action == CharacterAction.IDLE) {
            return new TextureRegion(
                hairSheet,
                currentSkinTone * (SOURCE_SPRITE_SIZE * 8),
                dir.rowOffset * SOURCE_SPRITE_SIZE,
                SOURCE_SPRITE_SIZE,
                SOURCE_SPRITE_SIZE
            );
        }

        int frameIndex = getFrameIndexForAction(action, stateTime);
        int frameX = (currentSkinTone * (SOURCE_SPRITE_SIZE * 8)) +
            ((action.startFrame + frameIndex) * SOURCE_SPRITE_SIZE);

        return new TextureRegion(
            hairSheet,
            frameX,
            dir.rowOffset * SOURCE_SPRITE_SIZE,
            SOURCE_SPRITE_SIZE,
            SOURCE_SPRITE_SIZE
        );
    }

    private void initializeHairAnimations() {
        TextureRegion hairSheet = hairAtlas.findRegion(currentHairStyle.atlasKey);
        if (hairSheet == null) {
            GameLogger.error("Failed to load hair style: " + currentHairStyle.atlasKey);
            return;
        }

        for (CharacterAction action : CharacterAction.values()) {
            for (Direction dir : Direction.values()) {
                String key = getAnimationKey(action, dir);
                Animation<TextureRegion> anim = createHairAnimation(hairSheet, action, dir);
                hairAnimations.put(key, anim);
            }
        }
    }
    public TextureRegion getClothingOverlay(String clothingType, CharacterAction action,
                                            Direction dir, float stateTime) {
        if (clothingType == null) return null;

        TextureRegion clothingSheet = TextureManager.clothing.findRegion(clothingType);
        if (clothingSheet == null) return null;

        // Match the same frame as the character animation
        Animation<TextureRegion> baseAnim = animations.get(getAnimationKey(action, dir));
        if (baseAnim == null) return null;

        int frameIndex = baseAnim.getKeyFrameIndex(stateTime);

        return new TextureRegion(
            clothingSheet,
            currentSkinTone * (SOURCE_SPRITE_SIZE * 8) + (frameIndex * SOURCE_SPRITE_SIZE),
            dir.rowOffset * SOURCE_SPRITE_SIZE,
            SOURCE_SPRITE_SIZE,
            SOURCE_SPRITE_SIZE
        );
    }


    private Animation<TextureRegion> createHairAnimation(TextureRegion sheet,
                                                         CharacterAction action,
                                                         Direction dir) {
        TextureRegion[] frames = new TextureRegion[action.frameCount];

        int startX = currentSkinTone * (SOURCE_SPRITE_SIZE * 8);
        int startY = dir.rowOffset * SOURCE_SPRITE_SIZE;

        for (int i = 0; i < action.frameCount; i++) {
            int frameX = startX + ((action.startFrame + i) * SOURCE_SPRITE_SIZE);
            frames[i] = new TextureRegion(sheet,
                frameX,
                startY,
                SOURCE_SPRITE_SIZE,
                SOURCE_SPRITE_SIZE);
        }

        Animation<TextureRegion> animation = new Animation<>(action.frameDuration, frames);
        if (action == CharacterAction.WALK || action == CharacterAction.RUN) {
            animation.setPlayMode(Animation.PlayMode.LOOP);
        }

        return animation;
    }

    public void setHairStyle(HairStyle style) {
        if (style != currentHairStyle) {
            currentHairStyle = style;
            initializeHairAnimations();
            GameLogger.info("Changed hair style to: " + style.name());
        }
    }

    private HairStyle currentHairStyle;


    private void initializeDirectionalFrames() {
        TextureRegion fullSheet = characterAtlas.findRegion("char_all");
        if (fullSheet == null) {
            GameLogger.error("Failed to load character sprite sheet!");
            return;
        }

        // For each direction, get the standing frame
        for (Direction dir : Direction.values()) {
            // Calculate base position in sprite sheet
            int x = currentSkinTone * (SPRITE_SIZE * 8); // 8 frames per skin tone
            int y = dir.rowOffset * SPRITE_SIZE;

            // Create frame for this direction
            TextureRegion frame = new TextureRegion(
                fullSheet,
                x,
                y,
                SPRITE_SIZE,
                SPRITE_SIZE
            );

            directionalFrames.put(dir.name(), frame);
        }
    }

    // Helper method to get clothing overlay for a direction
    public TextureRegion getClothingOverlay(Direction dir, String clothingType, TextureAtlas clothingAtlas) {
        if (clothingType == null || clothingAtlas == null) return null;

        TextureRegion clothingSheet = clothingAtlas.findRegion(clothingType);
        if (clothingSheet == null) return null;

        // Match clothing coordinates to character coordinates
        return new TextureRegion(
            clothingSheet,
            currentSkinTone * (SPRITE_SIZE * 8),
            dir.rowOffset * SPRITE_SIZE,
            SPRITE_SIZE,
            SPRITE_SIZE
        );
    }


    private float getFrameDuration(CharacterAction action) {
        switch (action) {
            case WALK:
                return WALK_DURATION;
            case RUN:
                return RUN_DURATION;
            default:
                return ACTION_DURATION;
        }
    }

    public TextureRegion getFrame(Direction dir, boolean isMoving, boolean isRunning, float stateTime) {
        if (isMoving) {
            Animation<TextureRegion> animation = isRunning ?
                runAnimations.get(dir.name()) :
                walkAnimations.get(dir.name());
            return animation != null ? animation.getKeyFrame(stateTime, true) : getDirectionalFrame(dir);
        }
        return getDirectionalFrame(dir);
    }




    public List<TextureRegion> getClothingOverlays(CharacterAction action, Direction dir, float stateTime) {
        List<TextureRegion> overlays = new ArrayList<>();

        for (OutfitLayer layer : activeOutfitLayers) {
            TextureRegion overlay = getClothingOverlayForType(layer.type, action, dir, stateTime);
            if (overlay != null) {
                overlays.add(overlay);
            }
        }

        return overlays;
    }




    public static class OutfitLayer {
        public final String type;
        public final int zIndex;

        public OutfitLayer(String type, int zIndex) {
            this.type = type;
            this.zIndex = zIndex;
        }
    }


    public void addOutfitLayer(String outfitType, int zIndex) {
        activeOutfitLayers.add(new OutfitLayer(outfitType, zIndex));
        // Sort layers by z-index (lower numbers render first)
        activeOutfitLayers.sort(Comparator.comparingInt(layer -> layer.zIndex));
    }

    public void removeOutfitLayer(String outfitType) {
        activeOutfitLayers.removeIf(layer -> layer.type.equals(outfitType));
    }

    public void clearOutfitLayers() {
        activeOutfitLayers.clear();
    }

    private TextureRegion getClothingOverlayForType(Direction dir, String clothingType,
                                                    TextureAtlas clothingAtlas, boolean isMoving,
                                                    boolean isRunning, float stateTime) {
        if (clothingType == null || clothingAtlas == null) return null;

        TextureRegion clothingSheet = clothingAtlas.findRegion(clothingType);
        if (clothingSheet == null) return null;

        int rowOffset = dir.rowOffset;
        if (isMoving) {
            rowOffset += isRunning ? (RUN_ROW * 4) : (WALK_ROW * 4);
        }

        int frameIndex = 0;
        if (isMoving) {
            float duration = isRunning ? RUN_DURATION : WALK_DURATION;
            frameIndex = (int) ((stateTime % (duration * 4)) / duration);
        }

        return new TextureRegion(
            clothingSheet,
            currentSkinTone * (SOURCE_SPRITE_SIZE * 8) + (frameIndex * SOURCE_SPRITE_SIZE),
            rowOffset * SOURCE_SPRITE_SIZE,
            SOURCE_SPRITE_SIZE,
            SOURCE_SPRITE_SIZE
        );
    }

    private TextureRegion getDirectionalFrame(Direction dir) {
        return directionalFrames.getOrDefault(dir.name(), directionalFrames.get(Direction.DOWN.name()));
    }



    private void initializeFrames() {
        TextureRegion fullSheet = characterAtlas.findRegion("char_all");
        if (fullSheet == null) {
            GameLogger.error("Failed to load character sprite sheet!");
            return;
        }

        for (Direction dir : Direction.values()) {
            initializeDirectionFrames(fullSheet, dir);
        }
    }

    public TextureRegion getFrame(Direction dir, boolean isMoving, float stateTime) {
        if (isMoving) {
            Animation<TextureRegion> walkAnim = walkAnimations.get(dir.name());
            return walkAnim != null ? walkAnim.getKeyFrame(stateTime, true) : getDirectionalFrame(dir);
        }
        return getDirectionalFrame(dir);
    }
    public void dispose() {
        animations.clear();
        clothingAnimations.clear();
        directionalFrames.clear();
        walkAnimations.clear();
        runAnimations.clear();
        activeOutfitLayers.clear();
    }

    private void initializeDirectionFrames(TextureRegion fullSheet, Direction dir) {
        int baseX = currentSkinTone * (SOURCE_SPRITE_SIZE * 8);

        // Standing frame (first frame of idle row)
        TextureRegion standingFrame = new TextureRegion(
            fullSheet,
            baseX,
            (dir.rowOffset + IDLE_ROW * 4) * SOURCE_SPRITE_SIZE,
            SOURCE_SPRITE_SIZE,
            SOURCE_SPRITE_SIZE
        );
        directionalFrames.put(dir.name(), standingFrame);

        // Walking animation
        TextureRegion[] walkFrames = createAnimationFrames(
            fullSheet,
            baseX,
            (dir.rowOffset + WALK_ROW * 4) * SOURCE_SPRITE_SIZE,
            4
        );
        walkAnimations.put(dir.name(), new Animation<>(WALK_DURATION, walkFrames));

        // Running animation
        TextureRegion[] runFrames = createAnimationFrames(
            fullSheet,
            baseX,
            (dir.rowOffset + RUN_ROW * 4) * SOURCE_SPRITE_SIZE,
            4
        );
        runAnimations.put(dir.name(), new Animation<>(RUN_DURATION, runFrames));
    }

    private TextureRegion[] createAnimationFrames(TextureRegion sheet, int baseX, int baseY, int frameCount) {
        TextureRegion[] frames = new TextureRegion[frameCount];
        for (int i = 0; i < frameCount; i++) {
            frames[i] = new TextureRegion(
                sheet,
                baseX + (i * SOURCE_SPRITE_SIZE),
                baseY,
                SOURCE_SPRITE_SIZE,
                SOURCE_SPRITE_SIZE
            );
        }
        return frames;
    }
    private String getAnimationKey(CharacterAction action, Direction dir) {
        return action.name() + "_" + dir.name();
    }

    public TextureRegion getClothingOverlay(Direction dir, String clothingType, TextureAtlas clothingAtlas, boolean isMoving, float stateTime) {
        if (clothingType == null || clothingAtlas == null) return null;

        TextureRegion clothingSheet = clothingAtlas.findRegion(clothingType);
        if (clothingSheet == null) return null;

        int frameIndex = isMoving ?
            (int) ((stateTime % (WALK_DURATION * 4)) / WALK_DURATION) : 0;

        return new TextureRegion(
            clothingSheet,
            currentSkinTone * (SOURCE_SPRITE_SIZE * 8) + (frameIndex * SOURCE_SPRITE_SIZE),
            dir.rowOffset * SOURCE_SPRITE_SIZE,
            SOURCE_SPRITE_SIZE,
            SOURCE_SPRITE_SIZE
        );
    }

    private TextureRegion getDefaultFrame() {
        return getFrame(CharacterAction.WALK, Direction.DOWN, 0);
    }

    public enum Direction {
        DOWN(0), UP(1), RIGHT(2), LEFT(3);

        final int rowOffset;

        Direction(int rowOffset) {
            this.rowOffset = rowOffset;
        }
    }   private TextureRegion getFrameFromSheet(TextureRegion sheet, int skinIndex,
                                                int frameIndex, Direction dir) {
        // Calculate position in sprite sheet
        int x = (skinIndex * FRAMES_PER_SKIN + frameIndex) * SOURCE_SPRITE_SIZE;
        int y = dir.rowOffset * SOURCE_SPRITE_SIZE;

        return new TextureRegion(sheet, x, y, SOURCE_SPRITE_SIZE, SOURCE_SPRITE_SIZE);
    }

    public void setSkinTone(int toneIndex) {
        if (toneIndex >= 0 && toneIndex < 8) {
            this.currentSkinTone = toneIndex;
            initializeAnimations();
        }
    }    public enum CharacterAction {
        IDLE(0, 1, 0f),              // Single frame, first frame of walk animation
        WALK(0, 8, 0.125f),          // Walking animation starts at same position
        RUN(8, 5, 0.1f),
        EMOTE(13, 4, 0.25f),
        LONG_EMOTE(17, 8, 0.2f),
        SWORD(25, 5, 0.15f),
        SHIELD(30, 4, 0.15f),
        SIT(34, 2, 0.25f),
        SLEEP(36, 2, 0.5f),
        PICKAXE(38, 5, 0.15f),
        AXE(43, 5, 0.15f),
        WATER(48, 2, 0.25f),
        FARM(50, 5, 0.15f),
        TORCH(55, 5, 0.15f);

        final int startFrame;
        final int frameCount;
        final float frameDuration;

        CharacterAction(int startFrame, int frameCount, float frameDuration) {
            this.startFrame = startFrame;
            this.frameCount = frameCount;
            this.frameDuration = frameDuration;
        }
    }

}
