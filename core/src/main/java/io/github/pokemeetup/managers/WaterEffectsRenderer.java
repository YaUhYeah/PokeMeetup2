package io.github.pokemeetup.managers;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;
import io.github.pokemeetup.utils.textures.TileType;

public class WaterEffectsRenderer {
    private static final float FRAME_DURATION = 0.15f;
    private static final float SCALE_FACTOR = 1.5f;
    private static final float SOUND_INTERVAL = 0.4f; // Time between splash sounds

    private Animation<TextureRegion> movingAnimation;
    private TextureRegion standingTexture;
    private float stateTime = 0;
    private boolean initialized = false;
    private float soundTimer = 0;
    private boolean wasOnWater = false;

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
                movingFrames[i] = new TextureRegion(movingRegion,
                    i * frameWidth, 0, frameWidth, frameHeight);
            }

            movingAnimation = new Animation<>(FRAME_DURATION, movingFrames);
            initialized = true;
            GameLogger.info("Water effects animations initialized successfully");

        } catch (Exception e) {
            GameLogger.error("Error initializing water effects: " + e.getMessage());
            initialized = false;
        }
    }

    public void update(float deltaTime) {
        if (!initialized) {
            initializeAnimations();
            return;
        }
        stateTime += deltaTime;
        if (soundTimer > 0) {
            soundTimer -= deltaTime;
        }
    }

    public void render(SpriteBatch batch, Player player) {
        if (!initialized || batch == null || player == null) {
            return;
        }

        try {
            boolean onWater = isPlayerOnWater(player);

            // Handle water splash sounds
            if (onWater) {
                if (!wasOnWater) {
                    // Just stepped into water
                    playWaterSound();
                } else if (player.isMoving() && soundTimer <= 0) {
                    // Continue playing splash sounds while moving in water
                    playWaterSound();
                    soundTimer = SOUND_INTERVAL;
                }
            }
            wasOnWater = onWater;

            if (!onWater) {
                return;
            }

            float playerX = player.getX();
            float playerY = player.getY();
            float effectX = playerX - (World.TILE_SIZE * 0.25f);
            float effectY = playerY - (World.TILE_SIZE * 0.25f);
            float effectWidth = World.TILE_SIZE * SCALE_FACTOR;
            float effectHeight = World.TILE_SIZE * 0.75f;

            TextureRegion currentFrame = player.isMoving() ?
                movingAnimation.getKeyFrame(stateTime, true) :
                standingTexture;

            if (currentFrame != null) {
                batch.draw(currentFrame, effectX, effectY, effectWidth, effectHeight);
            }

        } catch (Exception e) {
            GameLogger.error("Error rendering water effects: " + e.getMessage());
        }
    }

    private void playWaterSound() {
        AudioManager.getInstance().playSound(AudioManager.SoundEffect.PUDDLE);
    }

    private boolean isPlayerOnWater(Player player) {
        if (player == null || player.getWorld() == null) {
            return false;
        }

        World world = player.getWorld();
        int tileX = player.getTileX();
        int tileY = player.getTileY();

        // Get chunk coordinates
        int chunkX = Math.floorDiv(tileX, World.CHUNK_SIZE);
        int chunkY = Math.floorDiv(tileY, World.CHUNK_SIZE);
        Vector2 chunkPos = new Vector2(chunkX, chunkY);

        // Check if chunk exists
        if (!world.getChunks().containsKey(chunkPos)) {
            return false;
        }

        // Get local coordinates within chunk
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
