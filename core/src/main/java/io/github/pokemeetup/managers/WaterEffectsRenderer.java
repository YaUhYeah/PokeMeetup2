package io.github.pokemeetup.managers;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.system.Positionable;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;
import io.github.pokemeetup.utils.textures.TileType;

public class WaterEffectsRenderer {

    private static final float FRAME_DURATION = 0.15f;
    private static final float SOUND_INTERVAL = 0.4f;

    private Animation<TextureRegion> movingAnimation;
    private TextureRegion standingTexture;
    private float stateTime = 0;
    private boolean initialized = false;

    public WaterEffectsRenderer() {
        initializeAnimations();
    }

    private void initializeAnimations() {
        try {
            if (TextureManager.effects == null) {
                GameLogger.error("Effects atlas is null");
                return;
            }
            TextureRegion movingRegion = TextureManager.effects.findRegion("water_player_moving");
            standingTexture = TextureManager.effects.findRegion("water_player_standing");
            if (movingRegion == null || standingTexture == null) {
                GameLogger.error("Failed to load water effect textures");
                return;
            }
            int frameWidth = movingRegion.getRegionWidth() / 3;
            int frameHeight = movingRegion.getRegionHeight();
            TextureRegion[] movingFrames = new TextureRegion[3];
            for (int i = 0; i < 3; i++) {
                movingFrames[i] = new TextureRegion(movingRegion, i * frameWidth, 0, frameWidth, frameHeight);
            }
            movingAnimation = new Animation<>(FRAME_DURATION, movingFrames);
            initialized = true;
            GameLogger.info("Water effects animations initialized successfully");
        } catch (Exception e) {
            GameLogger.error("Error initializing water effects: " + e.getMessage());
            initialized = false;
        }
    }

    /**
     * Updates the animation timer.
     */
    public void update(float deltaTime) {
        if (!initialized) {
            initializeAnimations();
            return;
        }
        stateTime += deltaTime;
    }

    public void render(SpriteBatch batch, Positionable entity, World world) {
        if (!initialized || batch == null || entity == null || world == null) {
            return;
        }
        try {
            boolean onWater = isEntityOnWater(entity, world);

            if (onWater) {
                if (!entity.wasOnWater()) {
                    playWaterSound();
                } else if (entity.isMoving() && entity.getWaterSoundTimer() <= 0) {
                    if (!(entity instanceof io.github.pokemeetup.pokemon.WildPokemon)) {
                        playWaterSound();
                        entity.setWaterSoundTimer(SOUND_INTERVAL);
                    }
                }
            }
            entity.setWasOnWater(onWater);

            if (!onWater) {
                return;
            }

            // Calculate width and height based on the tile size and texture's aspect ratio.
            float effectWidth = World.TILE_SIZE;

            // [FIX] The tile calculation for a center-origin entity is a simple division.
            // The previous calculation was incorrect and caused the effect to trigger on adjacent tiles.
            int tileX = MathUtils.floor(entity.getX() / World.TILE_SIZE);
            int tileY = MathUtils.floor(entity.getY() / World.TILE_SIZE);

            float tileCenterX = tileX * World.TILE_SIZE + World.TILE_SIZE / 2f;
            float effectY = tileY * World.TILE_SIZE;

            float effectX = tileCenterX - (effectWidth / 2f);

            TextureRegion currentFrame = entity.isMoving() ?
                movingAnimation.getKeyFrame(stateTime, true) :
                standingTexture;

            if (currentFrame != null) {
                // Calculate height based on the frame's aspect ratio to prevent distortion.
                if (currentFrame.getRegionWidth() > 0) {
                    float aspectRatio = (float) currentFrame.getRegionHeight() / (float) currentFrame.getRegionWidth();
                    float effectHeight = effectWidth * aspectRatio;
                    batch.draw(currentFrame, effectX, effectY, effectWidth, effectHeight);
                }
            }
        } catch (Exception e) {
            GameLogger.error("Error rendering water effects: " + e.getMessage());
        }
    }
    private void playWaterSound() {
        AudioManager.getInstance().playSound(AudioManager.SoundEffect.PUDDLE);
    }

    /**
     * Checks whether the given entity is currently on a water tile.
     * The entity's X coordinate is at its horizontal center, and Y is at its feet.
     *
     * @param entity The Positionable entity.
     * @param world  The current World.
     * @return true if the underlying tile is a water tile.
     */
    private boolean isEntityOnWater(Positionable entity, World world) {
        // [FIX] The tile calculation for a center-origin entity is a simple division.
        // The previous calculation was incorrect and caused the check to be off by a tile
        // when the player was on the left half of a tile space.
        int tileX = MathUtils.floor(entity.getX() / World.TILE_SIZE);
        int tileY = MathUtils.floor(entity.getY() / World.TILE_SIZE);

        int chunkX = Math.floorDiv(tileX, World.CHUNK_SIZE);
        int chunkY = Math.floorDiv(tileY, World.CHUNK_SIZE);
        Vector2 chunkPos = new Vector2(chunkX, chunkY);
        if (!world.getChunks().containsKey(chunkPos)) {
            return false;
        }
        int localX = Math.floorMod(tileX, World.CHUNK_SIZE);
        int localY = Math.floorMod(tileY, World.CHUNK_SIZE);
        Chunk chunk = world.getChunks().get(chunkPos);
        if (chunk == null) {
            return false;
        }
        int tileType = chunk.getTileType(localX, localY);
        return isWaterTile(tileType);
    }

    private boolean isWaterTile(int tileType) {
        return tileType == TileType.WATER_PUDDLE ||
            tileType == TileType.WATER_PUDDLE_TOP_LEFT ||
            tileType == TileType.WATER_PUDDLE_TOP_RIGHT ||
            tileType == TileType.WATER_PUDDLE_BOTTOM_LEFT ||
            tileType == TileType.WATER_PUDDLE_BOTTOM_RIGHT ||
            tileType == TileType.WATER_PUDDLE_TOP_MIDDLE ||
            tileType == TileType.WATER_PUDDLE_BOTTOM_MIDDLE ||
            tileType == TileType.WATER_PUDDLE_LEFT_MIDDLE ||
            tileType == TileType.WATER_PUDDLE_RIGHT_MIDDLE;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void dispose() {
        movingAnimation = null;
        standingTexture = null;
        initialized = false;
    }
}
