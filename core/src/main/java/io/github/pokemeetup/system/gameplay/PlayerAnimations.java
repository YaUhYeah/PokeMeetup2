package io.github.pokemeetup.system.gameplay;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;

public class PlayerAnimations {
    public static final float PUNCH_FRAME_DURATION = 0.2f; // Slightly slower than chop
    public static final float BASE_MOVE_TIME = 0.45f;
    public static final float RUN_SPEED_MULTIPLIER = 1.5f;
    public static final float CHOP_FRAME_DURATION = 0.15f;
    public static final float WALK_FRAME_DURATION = BASE_MOVE_TIME / 4;
    public static final float RUN_FRAME_DURATION = (BASE_MOVE_TIME / RUN_SPEED_MULTIPLIER) / 4;
    private Animation<TextureRegion>[] chopAnimations;
    private boolean isChopping = false;
    private TextureRegion[] standingFrames;
    private Animation<TextureRegion> walkUpAnimation;
    private Animation<TextureRegion> walkDownAnimation;
    private Animation<TextureRegion> walkLeftAnimation;
    private Animation<TextureRegion> walkRightAnimation;
    private Animation<TextureRegion> runUpAnimation;
    private Animation<TextureRegion> runDownAnimation;
    private Animation<TextureRegion> runLeftAnimation;
    private Animation<TextureRegion> runRightAnimation;
    private volatile boolean isInitialized = false;
    private volatile boolean isDisposed = false;

    public PlayerAnimations() {
        loadAnimations();
    }

    public void startPunching() {
        isPunching = true;
        punchAnimationTime = 0f;
        GameLogger.info("Started punching animation");
    }
    private boolean isPunching = false;
    private float punchAnimationTime = 0f;
    private float chopAnimationTime = 0f;
    private static final float PUNCH_ANIMATION_DURATION = 0.8f; // Full punch cycle

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
    public TextureRegion getCurrentFrame(String direction, boolean isMoving, boolean isRunning, float stateTime) {
        if (!isInitialized || isDisposed) {
            loadAnimations();
        }

        if (isChopping) {
            int dirIndex = getDirectionIndex(direction);
            if (chopAnimations != null && dirIndex >= 0 && dirIndex < chopAnimations.length) {
                chopAnimationTime += Gdx.graphics.getDeltaTime();
                TextureRegion frame = chopAnimations[dirIndex].getKeyFrame(chopAnimationTime, true);

                if (chopAnimationTime >= CHOP_ANIMATION_DURATION) {
                    chopAnimationTime = 0f;
                }

                return frame != null ? frame : getStandingFrame(direction);
            }
        }

        if (isPunching) {
            int dirIndex = getDirectionIndex(direction);
            if (punchAnimations != null && dirIndex >= 0 && dirIndex < punchAnimations.length) {
                punchAnimationTime += Gdx.graphics.getDeltaTime();
                TextureRegion frame = punchAnimations[dirIndex].getKeyFrame(punchAnimationTime, true);

                if (punchAnimationTime >= PUNCH_ANIMATION_DURATION) {
                    punchAnimationTime = 0f;
                }

                return frame != null ? frame : getStandingFrame(direction);
            }
        }

        // Regular movement handling
        if (!isMoving) {
            return getStandingFrame(direction);
        }

        Animation<TextureRegion> currentAnimation = getAnimation(direction, isRunning);
        return currentAnimation.getKeyFrame(stateTime, true);
    }

    private Animation<TextureRegion>[] punchAnimations;
    private static final float CHOP_ANIMATION_DURATION = 0.6f; // Full animation cycle duration

    public void startChopping() {
        isChopping = true;
        chopAnimationTime = 0f;
        GameLogger.info("Started chopping animation");
    } public void stopChopping() {
        isChopping = false;
        chopAnimationTime = 0f;

        if (chopAnimations != null) {
            for (Animation<TextureRegion> anim : chopAnimations) {
                if (anim != null) {
                    anim.setPlayMode(Animation.PlayMode.NORMAL); // Reset play mode
                }
            }
        }
        GameLogger.info("Chopping animation stopped");
    }@SuppressWarnings("unchecked")
    private synchronized void loadAnimations() {
        try {
            TextureAtlas atlas = TextureManager.getBoy();
            if (atlas == null) {
                throw new RuntimeException("TextureAtlas is null");
            }

            chopAnimations = new Animation[4];
            punchAnimations = new Animation[4];

            String[] directions = {"up", "down", "left", "right"};
            String[] punchDirections = {"up", "down", "left", "right"};

            for (int i = 0; i < directions.length; i++) {
                // Load chop frames (unchanged)
                TextureRegion[] chopFrames = new TextureRegion[4];
                for (int frame = 0; frame < 4; frame++) {
                    String regionName = "boy_axe_chop_" + directions[i];
                    chopFrames[frame] = atlas.findRegion(regionName, frame);
                    if (chopFrames[frame] == null) {
                        GameLogger.error("Missing chop frame: " + regionName + " " + frame);
                        throw new RuntimeException("Missing chop animation frame");
                    }
                }
                chopAnimations[i] = new Animation<>(CHOP_FRAME_DURATION, chopFrames);
                chopAnimations[i].setPlayMode(Animation.PlayMode.NORMAL);

                // Load punch frames with correct indices per direction
                TextureRegion[] punchFrames = new TextureRegion[4];
                String punchDirection = punchDirections[i];

                // Choose the right index sequence based on the punch direction
                int[] directionIndices;
                if (punchDirection.equals("down")) {
                    directionIndices = new int[]{0, 1, 3, 4}; // Special sequence for down
                } else {
                    directionIndices = new int[]{0, 1, 2, 3}; // Standard sequence for others
                }

                for (int frame = 0; frame < 4; frame++) {
                    String regionName = "punch_" + punchDirection;
                    TextureRegion region = atlas.findRegion(regionName, directionIndices[frame]);
                    if (region == null) {
                        GameLogger.error("Missing punch frame: " + regionName + " index: " + directionIndices[frame]);
                        throw new RuntimeException("Missing punch animation frame");
                    }
                    punchFrames[frame] = region;
                }
                punchAnimations[i] = new Animation<>(PUNCH_FRAME_DURATION, punchFrames);
                punchAnimations[i].setPlayMode(Animation.PlayMode.NORMAL);
            }

            // Load other animations (unchanged)
            standingFrames = new TextureRegion[4];
            loadDirectionalFrames("boy_walk", atlas, true);
            loadDirectionalFrames("boy_run", atlas, false);

            isInitialized = true;
            isDisposed = false;

        } catch (Exception e) {
            GameLogger.error("Failed to load animations: " + e.getMessage());
            isInitialized = false;
            throw new RuntimeException("Animation loading failed", e);
        }
    }


    private void loadDirectionalFrames(String prefix, TextureAtlas atlas, boolean isWalk) {
        String[] directions = {"up", "down", "left", "right"};

        for (String direction : directions) {
            TextureRegion[] frames = new TextureRegion[4];
            String baseRegionName = prefix + "_" + direction;

            // Load all frames for this direction
            for (int i = 0; i < 4; i++) {
                TextureAtlas.AtlasRegion region = atlas.findRegion(baseRegionName, i + 1);
                if (region == null) {
                    GameLogger.error("Missing frame: " + baseRegionName + " index: " + (i + 1));
                    throw new RuntimeException("Missing animation frame");
                }
                frames[i] = new TextureRegion(region);
            }

            // Create animation for this direction
            float frameDuration = isWalk ? WALK_FRAME_DURATION : RUN_FRAME_DURATION;
            Animation<TextureRegion> animation = new Animation<>(frameDuration, frames);
            animation.setPlayMode(Animation.PlayMode.LOOP);
            int dirIndex = getDirectionIndex(direction);
            if (isWalk) {
                assignWalkAnimation(dirIndex, animation);
                if (standingFrames[dirIndex] == null) {
                    standingFrames[dirIndex] = new TextureRegion(frames[0]);
                }
            } else {
                assignRunAnimation(dirIndex, animation);
            }
        }
    }

    private int getDirectionIndex(String direction) {
        switch (direction.toLowerCase()) {
            case "up":
                return 0;
            case "down":
                return 1;
            case "left":
                return 2;
            case "right":
                return 3;
            default:
                return 1;
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


    private Animation<TextureRegion> getAnimation(String direction, boolean isRunning) {
        if (direction == null) {
            return walkDownAnimation;
        }

        switch (direction.toLowerCase()) {
            case "up":
                return isRunning ? runUpAnimation : walkUpAnimation;
            case "down":
                return isRunning ? runDownAnimation : walkDownAnimation;
            case "left":
                return isRunning ? runLeftAnimation : walkLeftAnimation;
            case "right":
                return isRunning ? runRightAnimation : walkRightAnimation;
            default:
                return walkDownAnimation;
        }
    }

    public TextureRegion getStandingFrame(String direction) {
        if (!isInitialized || isDisposed) {
            loadAnimations();
        }

        if (direction == null) {
            return standingFrames[1]; // Default to down
        }

        switch (direction.toLowerCase()) {
            case "up":
                return standingFrames[0];
            case "down":
                return standingFrames[1];
            case "left":
                return standingFrames[2];
            case "right":
                return standingFrames[3];
            default:
                return standingFrames[1];
        }
    }

    public synchronized void dispose() {
        isDisposed = true;
        isInitialized = false;
    }

    public boolean isDisposed() {
        return isDisposed;
    }
}
