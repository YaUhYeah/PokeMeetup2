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
    private float animationSpeedMultiplier = 0.75f;
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
    private final String username;
    private final Inventory inventory;
    private PlayerAnimations animations;
    private final AtomicBoolean isMoving;
    private final Object positionLock = new Object();
    private final Vector2 startPosition = new Vector2();
    private final Vector2 targetPosition = new Vector2();
    private boolean wantsToRun;
    private Vector2 position;
    private String direction;
    private BitmapFont font;
    private int ping;

    public OtherPlayer(String username, float x, float y) {
        this.username = (username != null && !username.isEmpty()) ? username : "Unknown";
        this.position = new Vector2(x, y);
        this.startPosition.set(x, y);
        this.targetPosition.set(x, y);
        this.inventory = new Inventory();
        this.direction = "down";
        this.movementProgress = 1f; // Start as "finished"
        this.animationTime = 0f;
        this.isMoving = new AtomicBoolean(false);
        this.wantsToRun = false;
        this.animations = new PlayerAnimations();
        GameLogger.info("Created OtherPlayer: " + this.username + " at (" + x + ", " + y + ")");
        prevTileX = pixelToTileX(position.x);
        prevTileY = pixelToTileY(position.y);
    }

    public void updateFromNetwork(NetworkProtocol.PlayerUpdate update) {
        synchronized (positionLock) {
            if (update == null) return;

            if (!targetPosition.epsilonEquals(update.x, update.y, 0.1f)) {
                this.startPosition.set(this.position); // Start lerp from current position
                this.targetPosition.set(update.x, update.y);
                this.movementProgress = 0f; // Reset interpolation progress
            }

            this.direction = update.direction;
            this.isMoving.set(update.isMoving);
            this.wantsToRun = update.wantsToRun;

            if (update.characterType != null && !update.characterType.equalsIgnoreCase(animations.getCharacterType())) {
                animations.dispose();
                this.animations = new PlayerAnimations(update.characterType);
            }
        }
    }

    private float smoothstep(float x) {
        x = MathUtils.clamp(x, 0f, 1f);
        return x * x * (3 - 2 * x);
    }

    private int pixelToTileX(float pixelX) {
        return (int) Math.floor(pixelX / World.TILE_SIZE);
    }

    private int pixelToTileY(float pixelY) {
        return (int) Math.floor(pixelY / World.TILE_SIZE);
    }

    // [FIX] The update logic is now cleaner and focuses only on interpolation.
    public void update(float deltaTime) {
        synchronized (positionLock) {
            // Determine move duration based on running state
            float moveDuration = wantsToRun
                ? PlayerAnimations.SLOW_RUN_ANIMATION_DURATION
                : PlayerAnimations.SLOW_WALK_ANIMATION_DURATION;

            // Only interpolate if progress is not complete
            if (movementProgress < 1.0f) {
                movementProgress = Math.min(1f, movementProgress + deltaTime / moveDuration);
                float smoothProgress = smoothstep(movementProgress);
                position.x = MathUtils.lerp(startPosition.x, targetPosition.x, smoothProgress);
                position.y = MathUtils.lerp(startPosition.y, targetPosition.y, smoothProgress);

                if (movementProgress >= 1.0f) {
                    position.set(targetPosition); // Snap to final position
                }
            }

            // Update animation time only when the server says we are moving
            if (isMoving.get()) {
                animationTime += deltaTime * animationSpeedMultiplier;
            } else {
                animationTime = 0f; // Reset when not moving
            }

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


    public void render(SpriteBatch batch) {
        TextureRegion currentFrame;
        synchronized (positionLock) {
            if (animations.isChopping()) {
                currentFrame = animations.getCurrentFrame(direction, false, false, animationTime);
            }
            else if (animations.isPunching()) {
                currentFrame = animations.getCurrentFrame(direction, false, false, animationTime);
            }
            else if (isMoving.get()) {
                currentFrame = animations.getCurrentFrame(direction, true, isWantsToRun(), animationTime);
            }
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
            float drawY = position.y;
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

    public void updateAction(NetworkProtocol.PlayerAction action) {
        switch (action.actionType) {
            case CHOP_START:
                animations.startChopping();
                break;
            case CHOP_STOP:
                animations.stopChopping();
                animationTime = 0f;
                break;
            case PUNCH_START:
                animations.startPunching();
                break;
            case PUNCH_STOP:
                animations.stopPunching();
                animationTime = 0f;
                break;
            default:
                GameLogger.error("Unhandled action type: " + action.actionType);
                break;
        }
    }
    public Vector2 getPosition() {
        synchronized (positionLock) {
            return new Vector2(position);
        }
    }

    public void setPosition(Vector2 position) {
        synchronized (positionLock) {
            this.position.set(position);
            this.startPosition.set(position);
            this.targetPosition.set(position);
            this.movementProgress = 1.0f; // Mark as finished at this new position
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
        // This is handled in updateFromNetwork
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
