package io.github.pokemeetup.multiplayer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.gameplay.PlayerAnimations;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.utils.GameLogger;

import java.util.concurrent.atomic.AtomicBoolean;

public class OtherPlayer {
    private static final float INTERPOLATION_SPEED = 10f;
    private final String username;
    private final Inventory inventory;
    private final PlayerAnimations animations;
    private final Object positionLock = new Object();
    private final Object inventoryLock = new Object();
    private final AtomicBoolean isMoving;
    private final Vector2 targetPosition = new Vector2();
    private final Vector2 velocity = new Vector2();
    private boolean wantsToRun;
    private Vector2 position;
    private String direction;
    private float stateTime;
    private BitmapFont font;
    private float interpolationProgress;

    public OtherPlayer(String username, float x, float y) {
        this.username = (username != null && !username.isEmpty()) ? username : "Unknown";
        this.position = new Vector2(x, y);
        this.inventory = new Inventory();
        this.direction = "down";
        this.isMoving = new AtomicBoolean(false);
        this.wantsToRun = false;
        this.stateTime = 0;
        this.animations = new PlayerAnimations();

        GameLogger.info("Created OtherPlayer: " + this.username + " at (" + x + ", " + y + ")");
    }

    public void updateAction(NetworkProtocol.PlayerAction action) {
        if (action.actionType == NetworkProtocol.ActionType.CHOP_START) {
            animations.startChopping();
        } else if (action.actionType == NetworkProtocol.ActionType.CHOP_STOP) {
            animations.stopChopping();
        }
    }

    public void updateFromNetwork(NetworkProtocol.PlayerUpdate update) {
        synchronized (this) {
            if (update == null) return;
            targetPosition.set(update.x, update.y);
            this.direction = update.direction;
            this.isMoving.set(update.isMoving);
            this.wantsToRun = update.wantsToRun;
            if (update.isMoving) {
                float dx = update.x - position.x;
                float dy = update.y - position.y;
                float distance = Vector2.len(dx, dy);

                if (distance > 0) {
                    velocity.set(dx / distance, dy / distance);
                    velocity.scl(update.wantsToRun ? 1.75f : 1f);
                }
            } else {
                velocity.setZero();
            }
        }
    }

    public void update(float deltaTime) {
        if (!position.epsilonEquals(targetPosition, 0.1f)) {
            interpolationProgress = Math.min(1.0f, interpolationProgress + deltaTime * INTERPOLATION_SPEED);
            position.x = MathUtils.lerp(position.x, targetPosition.x, interpolationProgress);
            position.y = MathUtils.lerp(position.y, targetPosition.y, interpolationProgress);
            isMoving.set(!position.epsilonEquals(targetPosition, 0.1f));
        } else {
            interpolationProgress = 0f;
            isMoving.set(false);
        }

        if (isMoving.get()) {
            stateTime += deltaTime;
        }
    }

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
            if (currentFrame instanceof Sprite) {
                Sprite sprite = (Sprite) currentFrame;
                sprite.setPosition(position.x, position.y);
                sprite.setOrigin(0, 0);
                sprite.draw(batch);
            } else {
                batch.draw(currentFrame,
                    position.x,
                    position.y,
                    currentFrame.getRegionWidth(),
                    currentFrame.getRegionHeight()
                );
            }
        }

        renderUsername(batch);
    }



    private void renderUsername(SpriteBatch batch) {
        if (username == null || username.isEmpty()) return;

        ensureFontLoaded();
        GlyphLayout layout = new GlyphLayout(font, username);
        float textWidth = layout.width;

        synchronized (positionLock) {
            font.draw(batch, username,
                position.x + (Player.FRAME_WIDTH - textWidth) / 2,
                position.y + Player.FRAME_HEIGHT + 15);
        }
    }

    private void ensureFontLoaded() {
        if (font == null) {
            try {
                font = new BitmapFont(Gdx.files.internal("Skins/default.fnt"));
                font.getData().setScale(0.8f);
                GameLogger.error("Loaded font for OtherPlayer: " + username);
            } catch (Exception e) {
                GameLogger.error("Failed to load font for OtherPlayer: " + username + " - " + e.getMessage());
                font = new BitmapFont();
            }
        }
    }

    public void dispose() {

        animations.dispose();
        GameLogger.error(
            ("Disposed animations for OtherPlayer: " + username));
    }

    public Vector2 getPosition() {
        synchronized (positionLock) {
            return new Vector2(position);
        }
    }

    public void setPosition(Vector2 position) {
        this.position = position;
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
