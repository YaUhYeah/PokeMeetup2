package io.github.pokemeetup.system.gameplay;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;

public class PlayerAnimations {
    public static final float BASE_MOVE_TIME = 0.45f;
    public static final float RUN_SPEED_MULTIPLIER = 1.5f;
    public static final float WALK_FRAME_DURATION = BASE_MOVE_TIME / 3f;
    public static final float RUN_FRAME_DURATION  = (BASE_MOVE_TIME / RUN_SPEED_MULTIPLIER) / 3f;
    public static final float PUNCH_ANIMATION_DURATION = 0.8f;
    public static final float PUNCH_FRAME_DURATION     = PUNCH_ANIMATION_DURATION / 4f;
    public static final float CHOP_ANIMATION_DURATION  = 0.6f;
    public static final float CHOP_FRAME_DURATION      = CHOP_ANIMATION_DURATION / 4f;

    private volatile boolean isInitialized = false;
    private volatile boolean isDisposed    = false;

    // Standing frames for each direction (up, down, left, right)
    private TextureRegion[] standingFrames;
    // Movement animations
    private Animation<TextureRegion> walkUpAnimation;
    private Animation<TextureRegion> walkDownAnimation;
    private Animation<TextureRegion> walkLeftAnimation;
    private Animation<TextureRegion> walkRightAnimation;
    private Animation<TextureRegion> runUpAnimation;
    private Animation<TextureRegion> runDownAnimation;
    private Animation<TextureRegion> runLeftAnimation;
    private Animation<TextureRegion> runRightAnimation;
    // Punch/Chop animations for each direction
    private Animation<TextureRegion>[] punchAnimations;
    private Animation<TextureRegion>[] chopAnimations;

    private boolean isPunching  = false;
    private boolean isChopping  = false;
    private float punchAnimationTime = 0f;
    private float chopAnimationTime  = 0f;

    // Store the character type used to load animations ("boy" or "girl")
    private final String characterType;

    // Constructor that takes the character type.
    public PlayerAnimations(String characterType) {
        this.characterType = characterType != null ? characterType.toLowerCase() : "boy";
        loadAnimations(this.characterType);
    }

    // Default constructor falls back to "boy"
    public PlayerAnimations() {
        this("boy");
    }

    public void startPunching() {
        isPunching = true;
        punchAnimationTime = 0f;
        GameLogger.info("Started punching animation");
    }

    public void stopPunching() {
        isPunching = false;
        punchAnimationTime = 0f;
        if (punchAnimations != null) {
            for (Animation<TextureRegion> anim : punchAnimations) {
                if (anim != null) {
                    anim.setPlayMode(Animation.PlayMode.NORMAL);
                }
            }
        }
        GameLogger.info("Punching animation stopped");
    }

    public void startChopping() {
        isChopping = true;
        chopAnimationTime = 0f;
        GameLogger.info("Started chopping animation");
    }

    public void stopChopping() {
        isChopping = false;
        chopAnimationTime = 0f;
        if (chopAnimations != null) {
            for (Animation<TextureRegion> anim : chopAnimations) {
                if (anim != null) {
                    anim.setPlayMode(Animation.PlayMode.NORMAL);
                }
            }
        }
        GameLogger.info("Chopping animation stopped");
    }

    /**
     * Returns the current frame based on direction, movement state, and any active punch/chop animations.
     */
    public TextureRegion getCurrentFrame(String direction, boolean isMoving, boolean isRunning, float stateTime) {
        if (!isInitialized || isDisposed) {
            loadAnimations(characterType);
        }
        // 1) Chopping has priority if active.
        if (isChopping) {
            int dirIndex = getDirectionIndex(direction);
            if (chopAnimations != null && dirIndex >= 0 && dirIndex < chopAnimations.length) {
                chopAnimationTime += Gdx.graphics.getDeltaTime();
                TextureRegion frame = chopAnimations[dirIndex].getKeyFrame(chopAnimationTime, true);
                if (chopAnimationTime >= CHOP_ANIMATION_DURATION) {
                    chopAnimationTime = 0f;
                }
                return (frame != null) ? frame : getStandingFrame(direction);
            }
        }
        // 2) Then punching if active.
        if (isPunching) {
            int dirIndex = getDirectionIndex(direction);
            if (punchAnimations != null && dirIndex >= 0 && dirIndex < punchAnimations.length) {
                punchAnimationTime += Gdx.graphics.getDeltaTime();
                TextureRegion frame = punchAnimations[dirIndex].getKeyFrame(punchAnimationTime, true);
                if (punchAnimationTime >= PUNCH_ANIMATION_DURATION) {
                    punchAnimationTime = 0f;
                }
                return (frame != null) ? frame : getStandingFrame(direction);
            }
        }
        // 3) Otherwise, use normal movement.
        if (!isMoving) {
            return getStandingFrame(direction);
        }
        Animation<TextureRegion> currentAnimation = getAnimation(direction, isRunning);
        return currentAnimation.getKeyFrame(stateTime, true);
    }

    @SuppressWarnings("unchecked")
    private synchronized void loadAnimations(String characterType) {
        try {
            // Choose atlas based on character type.
            TextureAtlas atlas;
            if ("girl".equalsIgnoreCase(characterType)) {
                atlas = TextureManager.getGirl();
            } else {
                atlas = TextureManager.getBoy();
            }
            if (atlas == null) {
                throw new RuntimeException("TextureAtlas is null for character type: " + characterType);
            }
            // Prepare arrays.
            chopAnimations  = new Animation[4];
            punchAnimations = new Animation[4];
            standingFrames  = new TextureRegion[4];
            String[] directions = {"up", "down", "left", "right"};
            // -- 1) Load Chop animations (4 frames each)
            int[][] chopIndices = {
                {1, 3, 0, 2}, // up
                {2, 0, 1, 3}, // down
                {1, 3, 0, 2}, // left
                {1, 3, 0, 2}  // right
            };
            for (int i = 0; i < directions.length; i++) {
                TextureRegion[] frames = new TextureRegion[4];
                for (int f = 0; f < 4; f++) {
                    // Region name uses the prefix based on character type.
                    TextureRegion reg = atlas.findRegion(characterType + "_axe_" + directions[i], chopIndices[i][f]);
                    if (reg == null) {
                        throw new RuntimeException("Missing chop frame: " + characterType + "_axe_" + directions[i] + " idx=" + chopIndices[i][f]);
                    }
                    frames[f] = reg;
                }
                chopAnimations[i] = new Animation<>(CHOP_FRAME_DURATION, frames);
                chopAnimations[i].setPlayMode(Animation.PlayMode.NORMAL);
            }
            // -- 2) Load Punch animations (4 frames each)
            int[][] punchIndices = {
                {1, 3, 2, 0}, // up
                {1, 3, 0, 2}, // down
                {0, 1, 3, 2}, // left
                {1, 3, 0, 2}  // right
            };
            for (int i = 0; i < directions.length; i++) {
                TextureRegion[] frames = new TextureRegion[4];
                for (int f = 0; f < 4; f++) {
                    TextureRegion reg = atlas.findRegion(characterType + "_punch_" + directions[i], punchIndices[i][f]);
                    if (reg == null) {
                        throw new RuntimeException("Missing punch frame: " + characterType + "_punch_" + directions[i] + " idx=" + punchIndices[i][f]);
                    }
                    frames[f] = reg;
                }
                punchAnimations[i] = new Animation<>(PUNCH_FRAME_DURATION, frames);
                punchAnimations[i].setPlayMode(Animation.PlayMode.NORMAL);
            }
            // -- 3) Load Walk animations (3 frames each)
            int[][] walkIndices = {
                {1, 0, 2}, // up
                {1, 0, 2}, // down
                {0, 2, 1}, // left
                {0, 1, 2}  // right
            };
            for (int i = 0; i < directions.length; i++) {
                TextureRegion[] frames = new TextureRegion[3];
                for (int f = 0; f < 3; f++) {
                    TextureRegion reg = atlas.findRegion(characterType + "_walk_" + directions[i], walkIndices[i][f]);
                    if (reg == null) {
                        throw new RuntimeException("Missing walk frame: " + characterType + "_walk_" + directions[i] + " idx=" + walkIndices[i][f]);
                    }
                    frames[f] = reg;
                }
                Animation<TextureRegion> walkAnim = new Animation<>(WALK_FRAME_DURATION, frames);
                walkAnim.setPlayMode(Animation.PlayMode.LOOP);
                assignWalkAnimation(i, walkAnim);
                if (standingFrames[i] == null) {
                    standingFrames[i] = frames[0];
                }
            }
            // -- 4) Load Run animations (3 frames each)
            int[][] runIndices = {
                {0, 2, 1}, // up
                {2, 0, 1}, // down
                {2, 1, 0}, // left
                {2, 0, 1}  // right
            };
            for (int i = 0; i < directions.length; i++) {
                TextureRegion[] frames = new TextureRegion[3];
                for (int f = 0; f < 3; f++) {
                    TextureRegion reg = atlas.findRegion(characterType + "_run_" + directions[i], runIndices[i][f]);
                    if (reg == null) {
                        throw new RuntimeException("Missing run frame: " + characterType + "_run_" + directions[i] + " idx=" + runIndices[i][f]);
                    }
                    frames[f] = reg;
                }
                Animation<TextureRegion> runAnim = new Animation<>(RUN_FRAME_DURATION, frames);
                runAnim.setPlayMode(Animation.PlayMode.LOOP);
                assignRunAnimation(i, runAnim);
            }
            isInitialized = true;
            isDisposed = false;
        } catch (Exception e) {
            GameLogger.error("Failed to load animations: " + e.getMessage());
            isInitialized = false;
            throw new RuntimeException("Animation loading failed", e);
        }
    }

    private void assignWalkAnimation(int index, Animation<TextureRegion> animation) {
        switch (index) {
            case 0: walkUpAnimation = animation; break;
            case 1: walkDownAnimation = animation; break;
            case 2: walkLeftAnimation = animation; break;
            case 3: walkRightAnimation = animation; break;
        }
    }

    private void assignRunAnimation(int index, Animation<TextureRegion> animation) {
        switch (index) {
            case 0: runUpAnimation = animation; break;
            case 1: runDownAnimation = animation; break;
            case 2: runLeftAnimation = animation; break;
            case 3: runRightAnimation = animation; break;
        }
    }

    /**
     * Returns an index (0: up, 1: down, 2: left, 3: right) for a given direction.
     */
    private int getDirectionIndex(String dir) {
        if (dir == null) return 1;
        switch (dir.toLowerCase()) {
            case "up":    return 0;
            case "down":  return 1;
            case "left":  return 2;
            case "right": return 3;
            default:      return 1;
        }
    }

    private Animation<TextureRegion> getAnimation(String direction, boolean isRunning) {
        int dirIndex = getDirectionIndex(direction);
        switch (dirIndex) {
            case 0: return isRunning ? runUpAnimation : walkUpAnimation;
            case 1: return isRunning ? runDownAnimation : walkDownAnimation;
            case 2: return isRunning ? runLeftAnimation : walkLeftAnimation;
            case 3: return isRunning ? runRightAnimation : walkRightAnimation;
        }
        return walkDownAnimation;
    }

    public TextureRegion getStandingFrame(String direction) {
        if (!isInitialized || isDisposed) {
            loadAnimations(characterType);
        }
        return standingFrames[getDirectionIndex(direction)];
    }

    public synchronized void dispose() {
        isDisposed = true;
        isInitialized = false;
    }

    public boolean isDisposed() {
        return isDisposed;
    }
}
