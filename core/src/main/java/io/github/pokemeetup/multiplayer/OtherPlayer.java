package io.github.pokemeetup.multiplayer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
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


    // Network interpolation settings
    // Network updates arrive every ~0.05s (20 times/sec), so interpolate faster than update rate
    private static final float NETWORK_INTERPOLATION_TIME = 0.15f; // Smooth interpolation between network updates

    // Full tile-to-tile movement durations (for animation timing reference)
    private static final float TILE_WALK_DURATION = PlayerAnimations.WALK_FRAME_DURATION * 4; // 0.32f
    private static final float TILE_RUN_DURATION = PlayerAnimations.RUN_FRAME_DURATION * 4;   // 0.24f

    @Override
    public boolean wasOnWater() {
        return wasOnWater;
    }

    private float movementProgress;
    private float animationTime = 0f;
    private int prevTileX, prevTileY;
    private float distanceTraveled = 0f; // Track actual distance for animation timing

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
        this.serverPosition = new Vector2(x, y); // Initialize server position to the start
        this.inventory = new Inventory();
        this.direction = "down";
        this.movementProgress = 1f; // Start as "finished"
        this.animationTime = 0f;
        this.distanceTraveled = 0f;
        this.isMoving = new AtomicBoolean(false);
        this.wantsToRun = false;
        this.animations = new PlayerAnimations();
        GameLogger.info("Created OtherPlayer: " + this.username + " at (" + x + ", " + y + ")");
        prevTileX = pixelToTileX(position.x);
        prevTileY = pixelToTileY(position.y);
    }
    private final Vector2 serverPosition; // The authoritative position from the server
    private String serverDirection;
    private boolean serverIsMoving;
    private boolean serverWantsToRun;
    public void updateFromNetwork(NetworkProtocol.PlayerUpdate update) {
        synchronized (positionLock) {
            if (update == null) return;

            // Store the server's authoritative state
            this.serverPosition.set(update.x, update.y);
            this.serverDirection = update.direction;
            this.serverIsMoving = update.isMoving;
            this.serverWantsToRun = update.wantsToRun;

            // Update character type if it has changed
            if (update.characterType != null && !update.characterType.equalsIgnoreCase(animations.getCharacterType())) {
                animations.dispose();
                this.animations = new PlayerAnimations(update.characterType);
            }

            // SOLUTION: Start a new interpolation if the server position is new
            if (targetPosition.dst(serverPosition) > 0.1f) { // Use a small threshold to prevent jitter
                startPosition.set(position); // Start from the current visual position
                targetPosition.set(serverPosition); // The new goal is the server position
                movementProgress = 0f; // Reset the interpolation timer
                animationCycleTime = 0f; // Reset animation cycle for smooth start
            }

            // Update state flags
            this.direction = serverDirection;
            boolean wasMoving = this.isMoving.get();
            this.isMoving.set(serverIsMoving);
            this.wantsToRun = serverWantsToRun;

            // Reset distance tracking when stopping
            if (wasMoving && !serverIsMoving) {
                distanceTraveled = 0f;
            }
        }
    }

    private float smoothStep(float t) {
        t = MathUtils.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private int pixelToTileX(float pixelX) {
        return (int) Math.floor(pixelX / World.TILE_SIZE);
    }

    private float enhancedSmoothstep(float x) {
        x = MathUtils.clamp(x, 0f, 1f);
        // Smoother curve with better acceleration
        return x * x * x * (x * (x * 6 - 15) + 10);
    }
    private int pixelToTileY(float pixelY) {
        return (int) Math.floor(pixelY / World.TILE_SIZE);
    }

    private float animationCycleTime = 0f;
    private float choppingAnimationTime = 0f;
    private float punchingAnimationTime = 0f;

    public void update(float deltaTime) {
        synchronized (positionLock) {
            // Use fixed interpolation time for smooth network position updates
            float interpolationDuration = NETWORK_INTERPOLATION_TIME;

            // Track previous position for distance calculation
            float prevX = position.x;
            float prevY = position.y;

            // Only interpolate if we haven't reached target
            if (movementProgress < 1.0f) {
                movementProgress = Math.min(1f, movementProgress + deltaTime / interpolationDuration);

                // Use same smoothing as Player
                float smoothProgress = smoothStep(movementProgress);
                position.x = MathUtils.lerp(startPosition.x, targetPosition.x, smoothProgress);
                position.y = MathUtils.lerp(startPosition.y, targetPosition.y, smoothProgress);

                // Track distance traveled this frame for animation
                float dx = position.x - prevX;
                float dy = position.y - prevY;
                float frameDistance = (float) Math.sqrt(dx * dx + dy * dy);
                distanceTraveled += frameDistance;

                if (movementProgress >= 1.0f) {
                    position.set(targetPosition);

                    // Check if we need to continue interpolating to server position
                    if (targetPosition.dst(serverPosition) > 2f) {
                        startPosition.set(position);
                        targetPosition.set(serverPosition);
                        movementProgress = 0f;
                        // Don't reset distanceTraveled - let animation continue smoothly
                    } else {
                        // Reached destination, ready for animation cycle mode
                        animationCycleTime = 0f;
                    }
                }
            }

            // Update animation time based on action state
            if (animations.isChopping()) {
                choppingAnimationTime += deltaTime;
                animationTime = choppingAnimationTime;
            } else if (animations.isPunching()) {
                punchingAnimationTime += deltaTime;
                animationTime = punchingAnimationTime;
            } else if (isMoving.get()) {
                // FIXED: Base animation on actual distance traveled at walk/run speed
                // One tile (32 pixels) = one full animation cycle
                float tileDuration = wantsToRun ? TILE_RUN_DURATION : TILE_WALK_DURATION;
                float tilesMovedFraction = distanceTraveled / World.TILE_SIZE;

                // Animation time = (tiles moved) * (time per tile)
                // This makes animation speed match the actual movement speed
                animationTime = tilesMovedFraction * tileDuration;

                // Wrap animation time to prevent overflow on long movements
                float fullCycleDuration = tileDuration; // One full cycle per tile
                animationTime = animationTime % fullCycleDuration;
            } else {
                // Not moving - reset everything
                animationTime = 0f;
                animationCycleTime = 0f;
                distanceTraveled = 0f;
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
            // Get current frame based on state
            if (animations.isChopping()) {
                currentFrame = animations.getCurrentFrame(direction, false, false, animationTime);
            } else if (animations.isPunching()) {
                currentFrame = animations.getCurrentFrame(direction, false, false, animationTime);
            } else if (isMoving.get()) {
                currentFrame = animations.getCurrentFrame(direction, true, isWantsToRun(), animationTime);
            } else {
                currentFrame = animations.getStandingFrame(direction);
            }

            if (currentFrame == null) {
                GameLogger.error("OtherPlayer " + username + " has null currentFrame");
                return;
            }

            float regionW = currentFrame.getRegionWidth();
            float regionH = currentFrame.getRegionHeight();
            float drawX = position.x - (regionW / 2f);  // Use position directly, not renderPosition
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
                choppingAnimationTime = 0f;
                break;
            case CHOP_STOP:
                animations.stopChopping();
                choppingAnimationTime = 0f;
                animationTime = 0f;
                break;
            case PUNCH_START:
                animations.startPunching();
                punchingAnimationTime = 0f;
                break;
            case PUNCH_STOP:
                animations.stopPunching();
                punchingAnimationTime = 0f;
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
            this.distanceTraveled = 0f; // Reset animation distance
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
