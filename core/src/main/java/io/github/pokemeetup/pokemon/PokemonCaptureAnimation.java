package io.github.pokemeetup.pokemon;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;

public class PokemonCaptureAnimation extends Actor {
    private final Animation<TextureRegion> throwAnimation;
    private final float throwDuration;
    private final TextureRegion openRegion;
    private final CaptureListener listener;
    private final Vector2 startPos;
    private final Vector2 targetPos;
    private final float captureChance; // Value between 0 and 1
    private float stateTime;
    private boolean reachedTarget;
    private boolean wiggleStarted;
    // This field will store the outcome once decided (null = not decided yet)
    private Boolean captureSuccess = null;

    // New field for the enemy Pokémon actor (e.g. its Image) that will be animated.
    private final Actor enemyPokemonActor;
    // Store the enemy actor’s original position so we can restore it on failure.
    private final float enemyOrigX, enemyOrigY;

    // Constant for arc height – adjust this value to suit your scene.
    private final float ARC_HEIGHT = 50f;

    /**
     * Updated constructor includes an enemy actor parameter.
     */
    public PokemonCaptureAnimation(TextureAtlas capsuleThrowAtlas, Vector2 startPos, Vector2 targetPos,
                                   float throwDuration, float captureChance, CaptureListener listener,
                                   Actor enemyPokemonActor) {
        this.startPos = new Vector2(startPos);
        this.targetPos = new Vector2(targetPos);
        this.throwDuration = throwDuration;
        this.listener = listener;
        this.stateTime = 0f;
        this.reachedTarget = false;
        this.wiggleStarted = false;
        this.captureChance = captureChance;
        this.enemyPokemonActor = enemyPokemonActor;
        // Store original enemy position so we can restore it if capture fails.
        if (enemyPokemonActor != null) {
            this.enemyOrigX = enemyPokemonActor.getX();
            this.enemyOrigY = enemyPokemonActor.getY();
        } else {
            this.enemyOrigX = 0;
            this.enemyOrigY = 0;
        }

        // Retrieve throw frames from atlas.
        Array<TextureRegion> throwFrames = new Array<>();
        Array<? extends TextureRegion> regions = capsuleThrowAtlas.findRegions("ball_POKEBALL");
        if (regions != null && regions.size > 1) {
            throwFrames.addAll(regions, 0, regions.size);
        } else {
            TextureRegion region = capsuleThrowAtlas.findRegion("ball_POKEBALL");
            if (region != null) {
                int frameCount = 8;
                for (int i = 0; i < frameCount; i++) {
                    // Slice region into 8 frames (each 32x64)
                    TextureRegion frame = new TextureRegion(region, i * 32, 0, 32, 64);
                    throwFrames.add(frame);
                }
            } else {
                throw new IllegalArgumentException("Pokéball throw region not found in capsuleThrow atlas.");
            }
        }
        float frameDuration = throwDuration / throwFrames.size;
        throwAnimation = new Animation<>(frameDuration, throwFrames, Animation.PlayMode.LOOP);

        // The open ball image (could be used for an extra flourish)
        openRegion = capsuleThrowAtlas.findRegion("ball_POKEBALL_open");
        if (openRegion == null) {
            throw new IllegalArgumentException("Pokéball open region not found in capsuleThrow atlas.");
        }

        setSize(32, 64);
        // Adjust position so that the actor’s center matches the provided start position.
        setPosition(startPos.x - getWidth() / 2, startPos.y - getHeight() / 2);
    }

    @Override
    public void act(float delta) {
        super.act(delta);

        // If we haven’t reached the enemy yet, update our position along an arc.
        if (!reachedTarget) {
            stateTime += delta;
            float progress = Math.min(stateTime / throwDuration, 1f);
            // Compute an arcing trajectory: linear interpolation plus a sine-based vertical offset.
            float x = MathUtils.lerp(startPos.x, targetPos.x, progress);
            float baseY = MathUtils.lerp(startPos.y, targetPos.y, progress);
            float y = baseY + ARC_HEIGHT * MathUtils.sin(MathUtils.PI * progress);
            setPosition(x - getWidth() / 2, y - getHeight() / 2);

            if (progress >= 1f) {
                reachedTarget = true;
                stateTime = 0f;
                // Snap to the closed ball frame and reset rotation.
                clearActions();
                setRotation(0);
                // Begin the wiggle sequence, then suck in the enemy, and finally decide outcome.
                addAction(Actions.sequence(
                    Actions.delay(0.1f),
                    wiggleSequence(),
                    Actions.run(this::suckInEnemy),
                    Actions.delay(0.3f),
                    Actions.run(this::decideOutcome)
                ));
            }
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        TextureRegion frame;
        // Use the throwing animation while en route.
        if (!reachedTarget) {
            frame = throwAnimation.getKeyFrame(stateTime, true);
        } else {
            // Once reached, display the closed Pokéball (first frame).
            frame = throwAnimation.getKeyFrame(0);
        }
        // Draw using the center as the origin.
        batch.draw(frame, getX(), getY(), getWidth() / 2, getHeight() / 2,
            getWidth(), getHeight(), getScaleX(), getScaleY(), getRotation());
    }

    /**
     * Creates a sequence of three wiggles using rotation actions.
     */
    private com.badlogic.gdx.scenes.scene2d.Action wiggleSequence() {
        // One wiggle: rotate right, then left, then back to center.
        com.badlogic.gdx.scenes.scene2d.Action wiggle = Actions.sequence(
            Actions.rotateBy(10, 0.1f, Interpolation.sine),
            Actions.rotateBy(-20, 0.2f, Interpolation.sine),
            Actions.rotateBy(10, 0.1f, Interpolation.sine)
        );
        return Actions.sequence(wiggle, wiggle, wiggle);
    }

    /**
     * Animates the enemy Pokémon actor being sucked into the center of the ball.
     */
    private void suckInEnemy() {
        if (enemyPokemonActor != null && enemyPokemonActor.getParent() != null) {
            enemyPokemonActor.clearActions();
            // Ensure the enemy actor's origin is centered.
            enemyPokemonActor.setOrigin(Align.center);
            // Get the center of the ball in stage coordinates.
            Vector2 ballCenterStage = new Vector2(getX() + getWidth() / 2, getY() + getHeight() / 2);
            // Convert the ball center into the enemy actor's parent's coordinate system.
            Vector2 targetPos = enemyPokemonActor.getParent().stageToLocalCoordinates(ballCenterStage);
            // Move the enemy actor to the target, offsetting so that its center matches.
            enemyPokemonActor.addAction(Actions.parallel(
                Actions.moveTo(targetPos.x - enemyPokemonActor.getWidth() / 2,
                    targetPos.y - enemyPokemonActor.getHeight() / 2,
                    0.3f, Interpolation.fade),
                Actions.scaleTo(0, 0, 0.3f, Interpolation.fade),
                Actions.fadeOut(0.3f)
            ));
        }
    }




    /**
     * Animates the enemy Pokémon actor breaking out of the ball (only called on capture failure).
     */
    private void enemyBreakOut() {
        if (enemyPokemonActor != null) {
            enemyPokemonActor.clearActions();
            // Reset enemy actor properties to start the breakout animation.
            enemyPokemonActor.setColor(1, 1, 1, 0); // start transparent
            enemyPokemonActor.setScale(0);
            enemyPokemonActor.addAction(Actions.sequence(
                Actions.delay(0.1f),
                Actions.parallel(
                    Actions.fadeIn(0.3f),
                    Actions.scaleTo(1, 1, 0.3f, Interpolation.fade),
                    Actions.moveTo(enemyOrigX, enemyOrigY, 0.3f, Interpolation.sineOut)
                )
            ));
        }
    }

    /**
     * Decides the outcome of the capture (success/failure) after the enemy has been sucked in.
     * On success, the ball shrinks (keeping the enemy hidden). On failure, the enemy breaks out and the ball bounces.
     */
    private void decideOutcome() {
        // Determine capture outcome based on chance.
        boolean success = MathUtils.random() < captureChance;
        captureSuccess = success;
        clearActions();
        if (success) {
            // On success, animate the ball shrinking.
            addAction(Actions.sequence(
                Actions.scaleTo(0, 0, 0.3f, Interpolation.fade),
                Actions.run(() -> {
                    if (listener != null) listener.onCaptureComplete(true);
                    remove();
                })
            ));
        } else {
            // On failure, animate the enemy breaking out.
            enemyBreakOut();
            // Also animate the ball bouncing upward.
            addAction(Actions.sequence(
                Actions.moveBy(0, 20, 0.2f, Interpolation.sineOut),
                Actions.moveBy(0, -20, 0.2f, Interpolation.sineIn),
                Actions.run(() -> {
                    if (listener != null) listener.onCaptureComplete(false);
                    remove();
                })
            ));
        }
    }

    public interface CaptureListener {
        void onCaptureComplete(boolean success);
    }
}
