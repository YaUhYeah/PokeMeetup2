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
    private float animationSpeedMultiplier = 0.75f; // [NEW] Same as Player

    // [MODIFIED] Replace interpolationProgress with the same state variables as Player
    private float movementProgress;
    private float animationTime = 0f;
    private int prevTileX, prevTileY;

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
    // For rendering text.
    private BitmapFont font;
    private int ping;

    public OtherPlayer(String username, float x, float y) {
        this.username = (username != null && !username.isEmpty()) ? username : "Unknown";
        this.position = new Vector2(x, y);
        // Initialize start and target positions as the current position.
        this.startPosition.set(x, y);
        this.targetPosition.set(x, y);
        this.inventory = new Inventory();
        this.direction = "down";
        // [MODIFIED] Initialize new state variables
        this.movementProgress = 0f;
        this.animationTime = 0f;
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

    // [MODIFIED] The network update handler now sets state, it doesn't drive movement directly.
    public void updateFromNetwork(NetworkProtocol.PlayerUpdate update) {
        synchronized (this) {
            if (update == null) return;

            // Update the target position for interpolation
            this.targetPosition.set(update.x, update.y);
            this.direction = update.direction;
            this.isMoving.set(update.isMoving);
            this.wantsToRun = update.wantsToRun;

            // [NEW] Check for character type update
            if (update.characterType != null && !update.characterType.equalsIgnoreCase(animations.getCharacterType())) {
                // Reinitialize animations with the new character type.
                animations.dispose();
                this.animations = new PlayerAnimations(update.characterType);
            }

            // [NEW] If the player is moving but we are not, start a new movement cycle
            if (update.isMoving && movementProgress >= 1.0f) {
                startPosition.set(position);
                movementProgress = 0f;
            }
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


    // [REWRITTEN] The update logic now mirrors the local player's logic perfectly.
    public void update(float deltaTime) {
        synchronized (positionLock) {
            // Determine the correct movement duration based on running state
            float moveDuration = wantsToRun
                ? PlayerAnimations.SLOW_RUN_ANIMATION_DURATION
                : PlayerAnimations.SLOW_WALK_ANIMATION_DURATION;

            // If the current position is not yet at the target, interpolate.
            if (!position.epsilonEquals(targetPosition, 0.1f)) {
                movementProgress = Math.min(1f, movementProgress + deltaTime / moveDuration);

                // Use a smoothstep function for easing
                float smoothProgress = smoothstep(movementProgress);
                position.x = MathUtils.lerp(startPosition.x, targetPosition.x, smoothProgress);
                position.y = MathUtils.lerp(startPosition.y, targetPosition.y, smoothProgress);
            }

            // [NEW] Extrapolation Logic
            if (movementProgress >= 1f) {
                position.set(targetPosition); // Snap to final position
                startPosition.set(position);
                movementProgress = 0f;

                // If the last network update said the player is still moving, predict the next tile.
                if (isMoving.get()) {
                    int nextTileX = pixelToTileX(targetPosition.x);
                    int nextTileY = pixelToTileY(targetPosition.y);

                    switch (direction.toLowerCase()) {
                        case "up":    nextTileY++; break;
                        case "down":  nextTileY--; break;
                        case "left":  nextTileX--; break;
                        case "right": nextTileX++; break;
                    }

                    // Check if the extrapolated tile is valid before moving.
                    if (GameContext.get().getWorld().isPassable(nextTileX, nextTileY)) {
                        targetPosition.set(tileToPixelX(nextTileX), tileToPixelY(nextTileY));
                    }
                }
            }

            // [MODIFIED] Unconditionally update animationTime if moving.
            if (isMoving.get()) {
                animationTime += deltaTime * animationSpeedMultiplier;
            } else {
                animationTime = 0f; // Reset when not moving
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
