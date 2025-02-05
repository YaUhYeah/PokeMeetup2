package io.github.pokemeetup.multiplayer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.system.Player; // For access to Player.RUN_SPEED_MULTIPLIER and FRAME_* constants.
import io.github.pokemeetup.system.Positionable;
import io.github.pokemeetup.system.gameplay.PlayerAnimations;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.utils.GameLogger;

import java.util.concurrent.atomic.AtomicBoolean;

public class OtherPlayer implements Positionable {
    private static final float INTERPOLATION_SPEED = 10f;

    private final String username;
    private final Inventory inventory;
    private final PlayerAnimations animations;
    private final Object positionLock = new Object();
    private final Object inventoryLock = new Object();
    private final AtomicBoolean isMoving;
    private int ping;

    public void setPing(int ping) {
        this.ping = ping;
    }

    private final Vector2 targetPosition = new Vector2();
    private final Vector2 startPosition = new Vector2();
    private boolean wantsToRun;
    private Vector2 position;
    private String direction;

    // The animation timer.
    private float stateTime;
    private BitmapFont font;
    private float interpolationProgress;

    public OtherPlayer(String username, float x, float y) {
        this.username = (username != null && !username.isEmpty()) ? username : "Unknown";
        this.position = new Vector2(x, y);
        // Initialize startPosition as the current position
        this.startPosition.set(x, y);
        this.targetPosition.set(x, y);
        this.inventory = new Inventory();
        this.direction = "down";
        this.isMoving = new AtomicBoolean(false);
        this.wantsToRun = false;
        this.stateTime = 0f;
        this.animations = new PlayerAnimations();
        GameLogger.info("Created OtherPlayer: " + this.username + " at (" + x + ", " + y + ")");
    }

    /**
     * When a network update arrives, if the target position has changed we reset our
     * interpolation by storing the current position as the start.
     */
    public void updateFromNetwork(NetworkProtocol.PlayerUpdate update) {
        synchronized (this) {
            if (update == null) return;
            // If the target position changes, reset the interpolation:
            if (!targetPosition.epsilonEquals(update.x, update.y, 0.1f)) {
                startPosition.set(position);
                interpolationProgress = 0f;
            }
            targetPosition.set(update.x, update.y);
            this.direction = update.direction;
            this.isMoving.set(update.isMoving);
            // Use the network’s running flag directly:
            this.wantsToRun = update.wantsToRun;
        }
    }


    /**
     * A smoothstep function for nicer interpolation.
     */
    private float smoothstep(float x) {
        x = MathUtils.clamp(x, 0f, 1f);
        return x * x * (3 - 2 * x);
    }

    public void update(float deltaTime) {
        // Clamp deltaTime to a maximum of 1/60 sec (or whatever your Player uses)
        float clampedDelta = Math.min(deltaTime, 1f / 60f);

        synchronized (positionLock) {
            if (!position.epsilonEquals(targetPosition, 0.1f)) {
                interpolationProgress = Math.min(1.0f, interpolationProgress + deltaTime * INTERPOLATION_SPEED);
                float smoothProgress = smoothstep(interpolationProgress);
                // Interpolate between start and target positions
                position.x = MathUtils.lerp(startPosition.x, targetPosition.x, smoothProgress);
                position.y = MathUtils.lerp(startPosition.y, targetPosition.y, smoothProgress);
                isMoving.set(true);
            } else {
                interpolationProgress = 0f;
                isMoving.set(false);
                // When reached, reset the start position
                startPosition.set(position);
            }
            // IMPORTANT: Update the animation timer using the clamped deltaTime so that
            // OtherPlayer’s animations don’t advance faster than in Player.
            stateTime += clampedDelta;
        }
    }

    /**
     * Render the OtherPlayer using bottom‐center anchoring.
     */
    public void render(SpriteBatch batch) {
        TextureRegion currentFrame = animations.getCurrentFrame(
            direction,
            isMoving.get(),
            wantsToRun,
            stateTime
        );
        if (currentFrame == null) {
            GameLogger.error("OtherPlayer " + username + " has null currentFrame");
            return;
        }
        synchronized (positionLock) {
            float regionW = currentFrame.getRegionWidth();
            float regionH = currentFrame.getRegionHeight();
            float drawX = position.x - (regionW / 2f);
            float drawY = position.y;  // Bottom-center anchoring
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

    public void dispose() {
        animations.dispose();
        GameLogger.info("Disposed animations for OtherPlayer: " + username);
        if (font != null) {
            font.dispose();
            font = null;
        }
    }

    public void updateAction(NetworkProtocol.PlayerAction action) {
        switch (action.actionType) {
            case CHOP_START:
                animations.startChopping();
                break;
            case CHOP_STOP:
                animations.stopChopping();
                // Reset the animation state – for example, reset stateTime to zero.
                stateTime = 0f;
                break;
            case PUNCH_START:
                animations.startPunching();
                break;
            case PUNCH_STOP:
                animations.stopPunching();
                // Reset the animation state so the idle frame is displayed.
                stateTime = 0f;
                break;
        }
    }

    // Additional getters/setters (synchronized where appropriate)
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
        synchronized (inventoryLock) {
            return inventory;
        }
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

    public boolean isWantsToRun() {
        return wantsToRun;
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
}
