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

    // Punch and Chop keep 4 frames:
    public static final float PUNCH_ANIMATION_DURATION = 0.8f; // total cycle ~0.8
    public static final float PUNCH_FRAME_DURATION     = PUNCH_ANIMATION_DURATION / 4f; // => 0.2
    public static final float CHOP_ANIMATION_DURATION  = 0.6f; // total cycle ~0.6
    public static final float CHOP_FRAME_DURATION      = CHOP_ANIMATION_DURATION / 4f; // => 0.15

    private volatile boolean isInitialized = false;
    private volatile boolean isDisposed    = false;

    // Standing frames [up=0, down=1, left=2, right=3]
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

    // Punch/Chop animations [up=0, down=1, left=2, right=3]
    private Animation<TextureRegion>[] punchAnimations;
    private Animation<TextureRegion>[] chopAnimations;

    private boolean isPunching  = false;
    private boolean isChopping  = false;
    private float   punchAnimationTime = 0f;
    private float   chopAnimationTime  = 0f;

    public PlayerAnimations() {
        loadAnimations();
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
     * Returns the current frame (TextureRegion) based on direction, movement state, punching, chopping, etc.
     */
    public TextureRegion getCurrentFrame(String direction, boolean isMoving, boolean isRunning, float stateTime) {
        if (!isInitialized || isDisposed) {
            loadAnimations();
        }

        // 1) Chop animation has priority if active
        if (isChopping) {
            int dirIndex = getDirectionIndex(direction);
            if (chopAnimations != null && dirIndex >= 0 && dirIndex < chopAnimations.length) {
                chopAnimationTime += Gdx.graphics.getDeltaTime();
                TextureRegion frame = chopAnimations[dirIndex].getKeyFrame(chopAnimationTime, true);

                // If you want a one-loop chop, you can check isAnimationFinished here,
                // but currently it loops:
                if (chopAnimationTime >= CHOP_ANIMATION_DURATION) {
                    chopAnimationTime = 0f;
                }

                return (frame != null) ? frame : getStandingFrame(direction);
            }
        }

        // 2) Punch animation if active
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

        // 3) Normal walk/run
        if (!isMoving) {
            return getStandingFrame(direction);
        }
        Animation<TextureRegion> currentAnimation = getAnimation(direction, isRunning);
        return currentAnimation.getKeyFrame(stateTime, true);
    }

    @SuppressWarnings("unchecked")
    private synchronized void loadAnimations() {
        try {
            TextureAtlas atlas = TextureManager.getBoy();
            if (atlas == null) {
                throw new RuntimeException("TextureAtlas is null");
            }

            // Prepare arrays
            chopAnimations  = new Animation[4];
            punchAnimations = new Animation[4];
            standingFrames  = new TextureRegion[4];

            // -- 1) Load Chop (4 frames each)
            // The index order below matches your atlas listing for each direction.
            // Adjust if your actual order is different in the final atlas.
            int[][] chopIndices = {
                /* up=0    */ {1, 3, 0, 2},
                /* down=1  */ {2, 0, 1, 3},
                /* left=2  */ {1, 3, 0, 2},
                /* right=3 */ {1, 3, 0, 2},
            };
            String[] directions = {"up", "down", "left", "right"};
            for (int i = 0; i < directions.length; i++) {
                TextureRegion[] frames = new TextureRegion[4];
                for (int f = 0; f < 4; f++) {
                    // e.g. "boy_axe_up", index= chopIndices[0][f]
                    TextureRegion reg = atlas.findRegion("boy_axe_" + directions[i], chopIndices[i][f]);
                    if (reg == null) {
                        throw new RuntimeException("Missing chop frame: boy_axe_" + directions[i] + " idx=" + chopIndices[i][f]);
                    }
                    frames[f] = reg;
                }
                chopAnimations[i] = new Animation<>(CHOP_FRAME_DURATION, frames);
                chopAnimations[i].setPlayMode(Animation.PlayMode.NORMAL);
            }

            // -- 2) Load Punch (4 frames each)
            int[][] punchIndices = {
                /* up=0    */ {1, 3, 2, 0},
                /* down=1  */ {1, 3, 0, 2},
                /* left=2  */ {0, 1, 3, 2},
                /* right=3 */ {1, 3, 0, 2},
            };
            for (int i = 0; i < directions.length; i++) {
                TextureRegion[] frames = new TextureRegion[4];
                for (int f = 0; f < 4; f++) {
                    TextureRegion reg = atlas.findRegion("boy_punch_" + directions[i], punchIndices[i][f]);
                    if (reg == null) {
                        throw new RuntimeException("Missing punch frame: boy_punch_" + directions[i] + " idx=" + punchIndices[i][f]);
                    }
                    frames[f] = reg;
                }
                punchAnimations[i] = new Animation<>(PUNCH_FRAME_DURATION, frames);
                punchAnimations[i].setPlayMode(Animation.PlayMode.NORMAL);
            }

            // -- 3) Load Walk (3 frames each)
            // According to your listing, e.g. boy_walk_down index: 1,0,2, etc.
            int[][] walkIndices = {
                /* up=0    */ {1, 0, 2},  // boy_walk_up:    index 1,0,2
                /* down=1  */ {1, 0, 2},  // boy_walk_down:  index 1,0,2
                /* left=2  */ {0, 2, 1},  // boy_walk_left:  index 0,2,1
                /* right=3 */ {0, 1, 2},  // boy_walk_right: index 0,1,2
            };
            for (int i = 0; i < directions.length; i++) {
                TextureRegion[] frames = new TextureRegion[3];
                for (int f = 0; f < 3; f++) {
                    TextureRegion reg = atlas.findRegion("boy_walk_" + directions[i], walkIndices[i][f]);
                    if (reg == null) {
                        throw new RuntimeException("Missing walk frame: boy_walk_" + directions[i] + " idx=" + walkIndices[i][f]);
                    }
                    frames[f] = reg;
                }
                Animation<TextureRegion> walkAnim = new Animation<>(WALK_FRAME_DURATION, frames);
                walkAnim.setPlayMode(Animation.PlayMode.LOOP);

                assignWalkAnimation(i, walkAnim);

                // Pick one as the standing frame (often the “middle” or “first”):
                if (standingFrames[i] == null) {
                    standingFrames[i] = frames[0];
                }
            }

            // -- 4) Load Run (3 frames each)
            // According to your listing, e.g. boy_run_down index:2,0,1, etc.
            int[][] runIndices = {
                /* up=0    */ {0, 2, 1}, // boy_run_up:    index 0,2,1
                /* down=1  */ {2, 0, 1}, // boy_run_down:  index 2,0,1
                /* left=2  */ {2, 1, 0}, // boy_run_left:  index 2,1,0
                /* right=3 */ {2, 0, 1}, // boy_run_right: index 2,0,1
            };
            for (int i = 0; i < directions.length; i++) {
                TextureRegion[] frames = new TextureRegion[3];
                for (int f = 0; f < 3; f++) {
                    TextureRegion reg = atlas.findRegion("boy_run_" + directions[i], runIndices[i][f]);
                    if (reg == null) {
                        throw new RuntimeException("Missing run frame: boy_run_" + directions[i] + " idx=" + runIndices[i][f]);
                    }
                    frames[f] = reg;
                }
                Animation<TextureRegion> runAnim = new Animation<>(RUN_FRAME_DURATION, frames);
                runAnim.setPlayMode(Animation.PlayMode.LOOP);

                assignRunAnimation(i, runAnim);
            }

            isInitialized = true;
            isDisposed    = false;

        } catch (Exception e) {
            GameLogger.error("Failed to load animations: " + e.getMessage());
            isInitialized = false;
            throw new RuntimeException("Animation loading failed", e);
        }
    }

    private void assignWalkAnimation(int index, Animation<TextureRegion> animation) {
        switch (index) {
            case 0: walkUpAnimation    = animation; break;
            case 1: walkDownAnimation  = animation; break;
            case 2: walkLeftAnimation  = animation; break;
            case 3: walkRightAnimation = animation; break;
        }
    }

    private void assignRunAnimation(int index, Animation<TextureRegion> animation) {
        switch (index) {
            case 0: runUpAnimation    = animation; break;
            case 1: runDownAnimation  = animation; break;
            case 2: runLeftAnimation  = animation; break;
            case 3: runRightAnimation = animation; break;
        }
    }

    /**
     * Returns the index 0..3 for up/down/left/right.
     */
    private int getDirectionIndex(String dir) {
        if (dir == null) return 1; // default 'down'
        switch (dir.toLowerCase()) {
            case "up":    return 0;
            case "down":  return 1;
            case "left":  return 2;
            case "right": return 3;
            default:      return 1; // fallback 'down'
        }
    }

    private Animation<TextureRegion> getAnimation(String direction, boolean isRunning) {
        int dirIndex = getDirectionIndex(direction);
        switch (dirIndex) {
            case 0: return isRunning ? runUpAnimation    : walkUpAnimation;
            case 1: return isRunning ? runDownAnimation  : walkDownAnimation;
            case 2: return isRunning ? runLeftAnimation  : walkLeftAnimation;
            case 3: return isRunning ? runRightAnimation : walkRightAnimation;
        }
        return walkDownAnimation; // fallback
    }

    public TextureRegion getStandingFrame(String direction) {
        if (!isInitialized || isDisposed) {
            loadAnimations();
        }
        return standingFrames[getDirectionIndex(direction)];
    }

    public synchronized void dispose() {
        isDisposed    = true;
        isInitialized = false;
    }

    public boolean isDisposed() {
        return isDisposed;
    }
}
