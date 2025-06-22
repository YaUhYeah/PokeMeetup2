package io.github.pokemeetup.system.gameplay;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.utils.GameLogger;

public class PokemonAnimations {
    public static final float IDLE_BOUNCE_DURATION = 1.0f;

    private static final int SPRITE_SHEET_SIZE = 256;
    private static final int FRAMES_PER_DIRECTION = 4;
    private static final int FRAME_WIDTH = SPRITE_SHEET_SIZE / FRAMES_PER_DIRECTION;  // 64
    private static final int FRAME_HEIGHT = SPRITE_SHEET_SIZE / FRAMES_PER_DIRECTION; // 64
    private static final float FRAME_DURATION = 0.2f;
    // The first frame in each row is used as the “standing” (idle) frame.
    private final TextureRegion[] standingFrames;
    // Animations for each direction (rows in the sprite sheet)
    private Animation<TextureRegion> walkDownAnimation;  // Row 0
    private Animation<TextureRegion> walkLeftAnimation;  // Row 1
    private Animation<TextureRegion> walkRightAnimation; // Row 2
    private Animation<TextureRegion> walkUpAnimation;    // Row 3
    // Internal clock that tracks animation progress.
    private float stateTime;
    private TextureRegion defaultFrame;

    // Used to track whether the animation should be running.
    private boolean isMoving;
    private String currentDirection;
    private boolean isInitialized;

    public PokemonAnimations(TextureRegion spriteSheet) {
        this.standingFrames = new TextureRegion[4];
        this.stateTime = 0f;

        if (spriteSheet == null) {
            GameLogger.error("Sprite sheet is null");
            createDefaultFrame();
            return;
        }

        try {
            if (spriteSheet.getRegionWidth() != SPRITE_SHEET_SIZE ||
                spriteSheet.getRegionHeight() != SPRITE_SHEET_SIZE) {
                GameLogger.error(String.format(
                    "Invalid sprite sheet dimensions. Expected %dx%d, got %dx%d",
                    SPRITE_SHEET_SIZE, SPRITE_SHEET_SIZE,
                    spriteSheet.getRegionWidth(), spriteSheet.getRegionHeight()
                ));
            }
            initializeAnimations(spriteSheet);
            isInitialized = true;
        } catch (Exception e) {
            GameLogger.error("Failed to initialize animations: " + e.getMessage());
            e.printStackTrace();
            createDefaultFrame();
        }
    }

    public boolean isMoving() {
        return isMoving;
    }

    private void initializeAnimations(TextureRegion spriteSheet) {
        // Split the sprite sheet into individual frames.
        TextureRegion[][] allFrames = new TextureRegion[4][FRAMES_PER_DIRECTION];
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < FRAMES_PER_DIRECTION; col++) {
                int x = col * FRAME_WIDTH;
                int y = row * FRAME_HEIGHT;
                allFrames[row][col] = new TextureRegion(spriteSheet, x, y, FRAME_WIDTH, FRAME_HEIGHT);

                // Store the first frame of each row for the idle/standing pose.
                if (col == 0) {
                    standingFrames[row] = new TextureRegion(allFrames[row][0]);
                }
            }
        }

        // Create the walking animations.
        walkDownAnimation = new Animation<>(FRAME_DURATION, allFrames[0]);
        walkLeftAnimation = new Animation<>(FRAME_DURATION, allFrames[1]);
        walkRightAnimation = new Animation<>(FRAME_DURATION, allFrames[2]);
        walkUpAnimation = new Animation<>(FRAME_DURATION, allFrames[3]);

        defaultFrame = standingFrames[0];
    }

    private void createDefaultFrame() {
        Pixmap pixmap = new Pixmap(FRAME_WIDTH, FRAME_HEIGHT, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.MAGENTA);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        defaultFrame = new TextureRegion(texture);
        for (int i = 0; i < 4; i++) {
            standingFrames[i] = new TextureRegion(defaultFrame);
        }
    }

    /**
     * Retrieves the current frame. Note that the animation’s internal clock (stateTime)
     * is updated via the update() method (not here).
     *
     * @param direction The current facing direction ("up", "down", etc.).
     * @param isMoving  Whether the Pokémon is moving.
     * @return The TextureRegion to render.
     */
    public TextureRegion getCurrentFrame(String direction, boolean isMoving) {
        if (!isInitialized) {
            return defaultFrame;
        }

        this.isMoving = isMoving;
        this.currentDirection = direction;

        if (isMoving) {
            Animation<TextureRegion> currentAnimation = getAnimationForDirection(direction);
            return currentAnimation.getKeyFrame(stateTime, true);
        } else {
            return getStandingFrame(direction);
        }
    }

    private TextureRegion getStandingFrame(String direction) {
        switch (direction.toLowerCase()) {
            case "down":
                return standingFrames[0];
            case "left":
                return standingFrames[1];
            case "right":
                return standingFrames[2];
            case "up":
                return standingFrames[3];
            default:
                return standingFrames[0]; // default to down
        }
    }

    private Animation<TextureRegion> getAnimationForDirection(String direction) {
        switch (direction.toLowerCase()) {
            case "down":
                return walkDownAnimation;
            case "left":
                return walkLeftAnimation;
            case "right":
                return walkRightAnimation;
            case "up":
                return walkUpAnimation;
            default:
                return walkDownAnimation;
        }
    }

    public void startMoving(String direction) {
        if (!isMoving || !direction.equals(currentDirection)) {
            this.isMoving = true;
            this.currentDirection = direction;
        }
    }

    public void stopMoving() {
        this.isMoving = false;
    }

    /**
     * Updates the animation’s state time. When moving, the animation plays at normal speed.
     * When idle, it can be slowed down for a more subtle effect.
     *
     * @param delta Time elapsed since the last update.
     */
    public void update(float delta) {
        if (isMoving) {
            stateTime += delta;
        } else {
            // When idle, update stateTime more slowly.
            stateTime += delta * 0.5f;
        }
    }
}
