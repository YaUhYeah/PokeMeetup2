package io.github.pokemeetup.multiplayer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.managers.FootstepEffect;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.system.Positionable;
import io.github.pokemeetup.system.gameplay.PlayerAnimations;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TileType;

import java.util.concurrent.atomic.AtomicBoolean;

public class OtherPlayer implements Positionable {

    @Override
    public boolean wasOnWater() {
        return wasOnWater;
    }

    @Override
    public void setWasOnWater(boolean onWater) {
        this.wasOnWater = onWater;
    }

    @Override
    public float getWaterSoundTimer() {
        return waterSoundTimer;
    }

    @Override
    public void setWaterSoundTimer(float timer) {
        this.waterSoundTimer = timer;
    }

    @Override
    public void updateWaterSoundTimer(float delta) {
        if (this.waterSoundTimer > 0) {
            this.waterSoundTimer -= delta;
        }
    }
    private boolean wasOnWater = false;
    private float waterSoundTimer = 0f;
    private static final float ANIMATION_SPEED_MULTIPLIER = 0.75f;
    // Basic player data.
    private final String username;
    private final Inventory inventory;
    private PlayerAnimations animations;
    private final AtomicBoolean isMoving;
    // Positioning and interpolation.
    private final Object positionLock = new Object();
    private final Vector2 startPosition = new Vector2();
    private final Vector2 targetPosition = new Vector2();
    private boolean wantsToRun;
    private Vector2 position;
    private float interpolationProgress; // From 0 to 1.
    private float stateTime; // Used both for movement and action animations.
    private String direction;
    // For footstep effect (tracking tile changes).
    private int prevTileX;
    private int prevTileY;
    // For rendering text.
    private BitmapFont font;
    private int ping;
    private float animationTime = 0f;

    public OtherPlayer(String username, float x, float y) {
        this.username = (username != null && !username.isEmpty()) ? username : "Unknown";
        this.position = new Vector2(x, y);
        // Initialize start and target positions as the current position.
        this.startPosition.set(x, y);
        this.targetPosition.set(x, y);
        this.inventory = new Inventory();
        this.direction = "down";
        this.isMoving = new AtomicBoolean(false);
        this.wantsToRun = false;
        this.stateTime = 0f;
        this.interpolationProgress = 0f;
        // Default animations (for example, “boy” animations)
        this.animations = new PlayerAnimations();
        GameLogger.info("Created OtherPlayer: " + this.username + " at (" + x + ", " + y + ")");
        prevTileX = pixelToTileX(position.x);
        prevTileY = pixelToTileY(position.y);
    }

    /**
     * Update from a network update. When a new target position is received,
     * if it differs from the current target, we reset the interpolation.
     */
    public void updateFromNetwork(NetworkProtocol.PlayerUpdate update) {
        synchronized (this) {
            if (update == null) return;

            // If the target position changes, reset the interpolation.
            if (!targetPosition.epsilonEquals(update.x, update.y, 0.1f)) {
                synchronized (positionLock) {
                    startPosition.set(position);
                    interpolationProgress = 0f;
                }
            }
            targetPosition.set(update.x, update.y);
            this.direction = update.direction;
            this.isMoving.set(update.isMoving);
            this.wantsToRun = update.wantsToRun;

            // *** NEW: Check for character type update ***
            if (update.characterType != null) {
                // Suppose our PlayerAnimations class exposes a getter for its character type:
                if (!update.characterType.equalsIgnoreCase(animations.getCharacterType())) {
                    // Reinitialize animations with the new character type.
                    animations.dispose();
                    // Now create a new instance using the network-sent character type.
                    // (This requires that your PlayerAnimations(String) constructor is available.)
                    // Also, you might want to store the current character type in a field.
                    this.animations = new PlayerAnimations(update.characterType);
                }
            }
            // (Optionally, update a network–provided stateTime if available.)
        }
    }


    /**
     * The smoothstep function to ease the interpolation.
     */
    private float smoothstep(float x) {
        x = MathUtils.clamp(x, 0f, 1f);
        return x * x * (3 - 2 * x);
    }

    // Helper methods to convert between tile and pixel coordinates.
    private float tileToPixelX(int tileX) {
        return tileX * World.TILE_SIZE + (World.TILE_SIZE / 2f);
    }

    private float tileToPixelY(int tileY) {
        return tileY * World.TILE_SIZE;
    }

    private int pixelToTileX(float pixelX) {
        return (int) Math.floor(pixelX / World.TILE_SIZE);
    }

    private int pixelToTileY(float pixelY) {
        return (int) Math.floor(pixelY / World.TILE_SIZE);
    }

    /**
     * Update method called each frame.
     * This method interpolates the remote player’s position toward the latest network target.
     * It also updates the internal stateTime even when the player is performing an action
     * (such as chopping or punching) so that the proper frame advances.
     */
    public void update(float deltaTime) {
        float clampedDelta = Math.min(deltaTime, 1f / 60f);
        updateWaterSoundTimer(deltaTime);
        synchronized (positionLock) {
            float moveDuration = wantsToRun
                ? PlayerAnimations.SLOW_RUN_ANIMATION_DURATION
                : PlayerAnimations.SLOW_WALK_ANIMATION_DURATION;

            // If the position is not yet at the target, interpolate movement.
            if (!position.epsilonEquals(targetPosition, 0.1f)) {
                interpolationProgress = Math.min(1f, interpolationProgress + deltaTime / moveDuration);
                float smoothProgress = smoothstep(interpolationProgress);
                position.x = MathUtils.lerp(startPosition.x, targetPosition.x, smoothProgress);
                position.y = MathUtils.lerp(startPosition.y, targetPosition.y, smoothProgress);
                isMoving.set(true);

                // Update stateTime for actions (if any)
                stateTime += clampedDelta;
                // **Update animationTime for movement frame selection**
                animationTime += clampedDelta * ANIMATION_SPEED_MULTIPLIER;

                // If we have reached (or nearly reached) the target...
                if (interpolationProgress >= 1f) {
                    position.set(targetPosition);
                    interpolationProgress = 0f;
                    // Reset both timers when movement completes.
                    stateTime = 0f;
                    animationTime = 0f;
                    startPosition.set(position);
                    isMoving.set(false);
                    // --- Extrapolation for continuous movement (if applicable) ---
                    int currentTileX = pixelToTileX(position.x);
                    int currentTileY = pixelToTileY(position.y);
                    int nextTileX = currentTileX;
                    int nextTileY = currentTileY;
                    switch (direction.toLowerCase()) {
                        case "up":
                            nextTileY++;
                            break;
                        case "down":
                            nextTileY--;
                            break;
                        case "left":
                            nextTileX--;
                            break;
                        case "right":
                            nextTileX++;
                            break;
                    }
                    if (GameContext.get().getWorld().isPassable(nextTileX, nextTileY)) {
                        startPosition.set(position);
                        targetPosition.set(tileToPixelX(nextTileX), tileToPixelY(nextTileY));
                        interpolationProgress = 0f;
                        isMoving.set(true);
                    }
                }
            } else {
                // Not moving: if currently in an action (chopping or punching), still update stateTime.
                if (animations.isChopping() || animations.isPunching()) {
                    stateTime += clampedDelta;
                }
                isMoving.set(false);
                interpolationProgress = 0f;
                animationTime = 0f;  // Reset animationTime when not moving.
                startPosition.set(position);
            }

            // (Optional) Spawn footstep effects when the tile changes.
            int currentTileX = pixelToTileX(position.x);
            int currentTileY = pixelToTileY(position.y);
            if (currentTileX != prevTileX || currentTileY != prevTileY) {
                int tileType = GameContext.get().getWorld().getTileTypeAt(currentTileX, currentTileY);
                if (tileType == TileType.SAND ||
                    tileType == TileType.SNOW ||
                    tileType == TileType.DESERT_GRASS ||
                    tileType == TileType.DESERT_SAND ||
                    tileType == TileType.SNOW_2 ||
                    tileType == TileType.SNOW_3 ||
                    tileType == TileType.SNOW_TALL_GRASS) {
                    GameContext.get().getWorld().getFootstepEffectManager()
                        .addEffect(new FootstepEffect(new Vector2(position.x, position.y), direction, 1.0f));
                }
                prevTileX = currentTileX;
                prevTileY = currentTileY;
            }
        }
    }

    /**
     * Render the OtherPlayer.
     * Now the method checks if an action is active (chopping or punching) and uses the appropriate frame.
     */
    public void render(SpriteBatch batch) {
        TextureRegion currentFrame;
        synchronized (positionLock) {
            // First priority: if chopping, use the chopping animation.
            if (animations.isChopping()) {
                currentFrame = animations.getCurrentFrame(direction, false, false, stateTime);
            }
            // Second priority: if punching, use the punching animation.
            else if (animations.isPunching()) {
                currentFrame = animations.getCurrentFrame(direction, false, false, stateTime);
            }
            // Third: if moving, use the movement animation based on animationTime.
            else if (isMoving.get()) {
                currentFrame = animations.getCurrentFrame(direction, true, isWantsToRun(), animationTime);
            }
            // Otherwise, use the standing frame.
            else {
                currentFrame = animations.getStandingFrame(direction);
            }
            if (currentFrame == null) {
                GameLogger.error("OtherPlayer " + username + " has null currentFrame");
                return;
            }
            float regionW = currentFrame.getRegionWidth();
            float regionH = currentFrame.getRegionHeight();
            float drawX = position.x - (regionW / 2f);
            float drawY = position.y; // bottom-center anchoring
            batch.draw(currentFrame, drawX, drawY, regionW, regionH);
            renderUsername(batch, drawX, regionW, drawY, regionH);
        }
    }


    private void renderUsername(SpriteBatch batch, float drawX, float regionW, float drawY, float regionH) {
        if (username == null || username.isEmpty()) return;
        ensureFontLoaded();
        GlyphLayout layout = new GlyphLayout(font, username);
        float textWidth = layout.width;
        float nameX = drawX + (regionW - textWidth) / 2f;
        float nameY = drawY + regionH + 15;
        font.draw(batch, username, nameX, nameY);
    }

    private void ensureFontLoaded() {
        if (font == null) {
            try {
                font = new BitmapFont(Gdx.files.internal("Skins/default.fnt"));
                font.getData().setScale(0.8f);
                GameLogger.info("Loaded font for OtherPlayer: " + username);
            } catch (Exception e) {
                GameLogger.error("Failed to load font for OtherPlayer: " + username + " - " + e.getMessage());
                font = new BitmapFont();
            }
        }
    }

    /**
     * Called when an action message is received from the network.
     * These calls trigger the appropriate animations.
     */
    public void updateAction(NetworkProtocol.PlayerAction action) {
        switch (action.actionType) {
            case CHOP_START:
                animations.startChopping();
                break;
            case CHOP_STOP:
                animations.stopChopping();
                stateTime = 0f;
                break;
            case PUNCH_START:
                animations.startPunching();
                break;
            case PUNCH_STOP:
                animations.stopPunching();
                stateTime = 0f;
                break;
            default:
                GameLogger.error("Unhandled action type: " + action.actionType);
                break;
        }
    }

    // Synchronized getters and setters.
    public Vector2 getPosition() {
        synchronized (positionLock) {
            return new Vector2(position);
        }
    }

    public void setPosition(Vector2 position) {
        synchronized (positionLock) {
            this.position = position;
            this.startPosition.set(position);
            this.targetPosition.set(position);
        }
    }

    public Inventory getInventory() {
        return inventory;
    }

    public String getUsername() {
        return username;
    }

    public String getDirection() {
        synchronized (positionLock) {
            return direction;
        }
    }

    public boolean isMoving() {
        return isMoving.get();
    }

    @Override
    public void setCharacterType(String characterType) {

    }

    public boolean isWantsToRun() {
        return wantsToRun;
    }

    public void setWantsToRun(boolean wantsToRun) {
        this.wantsToRun = wantsToRun;
    }

    public float getX() {
        synchronized (positionLock) {
            return position.x;
        }
    }

    public void setX(float x) {
        synchronized (positionLock) {
            this.position.x = x;
        }
    }

    public float getY() {
        synchronized (positionLock) {
            return position.y;
        }
    }

    public void setY(float y) {
        synchronized (positionLock) {
            this.position.y = y;
        }
    }

    public void setPing(int ping) {
        this.ping = ping;
    }

    public void dispose() {
        animations.dispose();
        GameLogger.info("Disposed animations for OtherPlayer: " + username);
        if (font != null) {
            font.dispose();
            font = null;
        }
    }
}
