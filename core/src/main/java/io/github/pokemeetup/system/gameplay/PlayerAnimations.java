package io.github.pokemeetup.system.gameplay;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;

public class PlayerAnimations {
    public static final float PUNCH_ANIMATION_DURATION = 1.1f;
    public static final float PUNCH_FRAME_DURATION = PUNCH_ANIMATION_DURATION / 4f;
    public static final float CHOP_ANIMATION_DURATION = 1.2f;
    public static final float CHOP_FRAME_DURATION = CHOP_ANIMATION_DURATION / 4f;
    public static final float BASE_MOVE_TIME = 0.25f;
    public static final float RUN_SPEED_MULTIPLIER = 2.5f;
    public static final float SLOW_WALK_ANIMATION_DURATION = BASE_MOVE_TIME * 1.4f;
    public static final float SLOW_RUN_ANIMATION_DURATION = (BASE_MOVE_TIME / RUN_SPEED_MULTIPLIER) * 2f;
    public static final float WALK_FRAME_DURATION = 0.12f;
    public static final float RUN_FRAME_DURATION  = 0.08f;


    private final String characterType;
    private volatile boolean isInitialized = false;
    private volatile boolean isDisposed = false;
    private TextureRegion[] standingFrames;
    private Animation<TextureRegion> walkUpAnimation;
    private Animation<TextureRegion> walkDownAnimation;
    private Animation<TextureRegion> walkLeftAnimation;
    private Animation<TextureRegion> walkRightAnimation;
    private Animation<TextureRegion> runUpAnimation;
    private Animation<TextureRegion> runDownAnimation;
    private Animation<TextureRegion> runLeftAnimation;
    private Animation<TextureRegion> runRightAnimation;
    private Animation<TextureRegion>[] punchAnimations;
    private Animation<TextureRegion>[] chopAnimations;
    private boolean isPunching = false;
    private boolean isChopping = false;
    private float punchAnimationTime = 0f;
    private float chopAnimationTime = 0f;
    public PlayerAnimations(String characterType) {
        this.characterType = characterType != null ? characterType.toLowerCase() : "boy";
        loadAnimations(this.characterType);
    }

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
     * [MODIFIED] Returns the current frame based on state. This is the central logic for both local and remote players.
     * The `time` parameter is a continuously increasing timer that dictates the animation frame.
     */
    public TextureRegion getCurrentFrame(String direction, boolean isMoving, boolean isRunning, float time) {
        if (!isInitialized || isDisposed) {
            loadAnimations(characterType);
        }
        if (isChopping) {
            int dirIndex = getDirectionIndex(direction);
            if (chopAnimations != null && dirIndex >= 0 && dirIndex < chopAnimations.length) {
                chopAnimationTime += Gdx.graphics.getDeltaTime();
                return chopAnimations[dirIndex].getKeyFrame(chopAnimationTime, true);
            }
        }
        if (isPunching) {
            int dirIndex = getDirectionIndex(direction);
            if (punchAnimations != null && dirIndex >= 0 && dirIndex < punchAnimations.length) {
                punchAnimationTime += Gdx.graphics.getDeltaTime();
                return punchAnimations[dirIndex].getKeyFrame(punchAnimationTime, true);
            }
        }
        if (!isMoving) {
            return getStandingFrame(direction);
        }
        Animation<TextureRegion> currentAnimation = getAnimation(direction, isRunning);
        return currentAnimation.getKeyFrame(time, true);
    }
    public boolean isChopping() {
        return isChopping;
    }

    public boolean isPunching() {
        return isPunching;
    }

    @SuppressWarnings("unchecked")
    private synchronized void loadAnimations(String characterType) {
        try {
            TextureAtlas atlas;
            if ("girl".equalsIgnoreCase(characterType)) {
                atlas = TextureManager.getGirl();
            } else {
                atlas = TextureManager.getBoy();
            }
            if (atlas == null) {
                throw new RuntimeException("TextureAtlas is null for character type: " + characterType);
            }
            chopAnimations = new Animation[4];
            punchAnimations = new Animation[4];
            standingFrames = new TextureRegion[4];
            String[] directions = {"up", "down", "left", "right"};
            int[][] chopIndices = {
                {1, 3, 0, 2},
                {2, 0, 1, 3},
                {1, 3, 0, 2},
                {1, 3, 0, 2}
            };
            for (int i = 0; i < directions.length; i++) {
                TextureRegion[] frames = new TextureRegion[4];
                for (int f = 0; f < 4; f++) {
                    TextureRegion reg = atlas.findRegion(characterType + "_axe_" + directions[i], chopIndices[i][f]);
                    if (reg == null) {
                        throw new RuntimeException("Missing chop frame: " + characterType + "_axe_" + directions[i] + " idx=" + chopIndices[i][f]);
                    }
                    frames[f] = reg;
                }
                chopAnimations[i] = new Animation<>(CHOP_FRAME_DURATION, frames);
                chopAnimations[i].setPlayMode(Animation.PlayMode.NORMAL);
            }
            int[][] punchIndices = {
                {1, 3, 2, 0},
                {1, 3, 0, 2},
                {0, 1, 3, 2},
                {1, 3, 0, 2}
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

            int[][] walkIndices = {
                {1, 2, 3, 2},
                {1, 2, 3, 2},
                {1, 2, 3, 2},
                {1, 2, 3, 2}
            };
            for (int i = 0; i < directions.length; i++) {
                TextureRegion[] frames = new TextureRegion[4];
                for (int f = 0; f < 4; f++) {
                    TextureRegion reg = atlas.findRegion(characterType + "_walk_" + directions[i], walkIndices[i][f]);
                    if (reg == null) {
                        throw new RuntimeException("Missing walk frame: " + characterType + "_walk_" + directions[i] + " idx=" + walkIndices[i][f]);
                    }
                    frames[f] = reg;
                }
                Animation<TextureRegion> walkAnim = new Animation<>(WALK_FRAME_DURATION, frames);
                walkAnim.setPlayMode(Animation.PlayMode.NORMAL);
                assignWalkAnimation(i, walkAnim);
                if (standingFrames[i] == null) {
                    standingFrames[i] = frames[3];
                }
            }

            int[][] runIndices = {
                {1, 2, 3, 2},
                {1, 2, 3, 2},
                {1, 2, 3, 2},
                {1, 2, 3, 2}
            };
            for (int i = 0; i < directions.length; i++) {
                TextureRegion[] frames = new TextureRegion[4];
                for (int f = 0; f < 4; f++) {
                    TextureRegion reg = atlas.findRegion(characterType + "_run_" + directions[i], runIndices[i][f]);
                    if (reg == null) {
                        throw new RuntimeException("Missing run frame: " + characterType + "_run_" + directions[i] + " idx=" + runIndices[i][f]);
                    }
                    frames[f] = reg;
                }
                Animation<TextureRegion> runAnim = new Animation<>(RUN_FRAME_DURATION, frames);
                runAnim.setPlayMode(Animation.PlayMode.NORMAL);
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
            case 0:
                walkUpAnimation = animation;
                break;
            case 1:
                walkDownAnimation = animation;
                break;
            case 2:
                walkLeftAnimation = animation;
                break;
            case 3:
                walkRightAnimation = animation;
                break;
        }
    }

    private void assignRunAnimation(int index, Animation<TextureRegion> animation) {
        switch (index) {
            case 0:
                runUpAnimation = animation;
                break;
            case 1:
                runDownAnimation = animation;
                break;
            case 2:
                runLeftAnimation = animation;
                break;
            case 3:
                runRightAnimation = animation;
                break;
        }
    }

    /**
     * Returns an index (0: up, 1: down, 2: left, 3: right) for a given direction.
     */
    private int getDirectionIndex(String dir) {
        if (dir == null) return 1;  // default to "down"
        switch (dir.toLowerCase()) {
            case "up":
                return 0;
            case "left":
                return 2;
            case "right":
                return 3;
            default:
                return 1;
        }
    }

    private Animation<TextureRegion> getAnimation(String direction, boolean isRunning) {
        int dirIndex = getDirectionIndex(direction);
        switch (dirIndex) {
            case 0:
                return isRunning ? runUpAnimation : walkUpAnimation;
            case 1:
                return isRunning ? runDownAnimation : walkDownAnimation;
            case 2:
                return isRunning ? runLeftAnimation : walkLeftAnimation;
            case 3:
                return isRunning ? runRightAnimation : walkRightAnimation;
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

    public String getCharacterType() {
        return characterType;
    }
}
