package io.github.pokemeetup.pokemon;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
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
    // This field will store the outcome once decided (null = not decided yet)
    private Boolean captureSuccess = null;
    public PokemonCaptureAnimation(TextureAtlas capsuleThrowAtlas, Vector2 startPos, Vector2 targetPos,
                                   float throwDuration, float captureChance, CaptureListener listener) {
        this.startPos = new Vector2(startPos);
        this.targetPos = new Vector2(targetPos);
        this.throwDuration = throwDuration;
        this.listener = listener;
        this.stateTime = 0f;
        this.reachedTarget = false;
        this.captureChance = captureChance;

        // Try to get pre–split regions. If there aren’t multiple frames, manually slice the region.
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
        stateTime += delta;
        if (!reachedTarget) {
            float progress = Math.min(stateTime / throwDuration, 1f);
            // Lerp from startPos to targetPos (offset by half the actor's size)
            float newX = MathUtils.lerp(startPos.x, targetPos.x, progress) - getWidth() / 2;
            float newY = MathUtils.lerp(startPos.y, targetPos.y, progress) - getHeight() / 2;
            setPosition(newX, newY);
            if (progress >= 1f) {
                reachedTarget = true;
                // Start a horizontal shake (Gen 3–style)
                addAction(Actions.sequence(
                    Actions.moveBy(-4, 0, 0.05f),
                    Actions.moveBy(8, 0, 0.1f),
                    Actions.moveBy(-8, 0, 0.1f),
                    Actions.moveBy(8, 0, 0.1f),
                    Actions.moveBy(-4, 0, 0.05f),
                    Actions.run(() -> {
                        stateTime = 0f; // Reset timer for outcome decision
                    })
                ));
            }
        } else {
            // After shaking, wait 0.5 seconds then decide the outcome.
            if (stateTime >= 0.5f && captureSuccess == null) {
                boolean success = MathUtils.random() < captureChance;
                captureSuccess = success;
                if (success) {
                    addAction(Actions.sequence(
                        Actions.fadeOut(0.3f),
                        Actions.run(() -> {
                            if (listener != null) listener.onCaptureComplete(true);
                            remove();
                        })
                    ));
                } else {
                    // Bounce the ball upward if capture fails.
                    addAction(Actions.sequence(
                        Actions.moveBy(0, 10, 0.2f),
                        Actions.moveBy(0, -10, 0.2f),
                        Actions.run(() -> {
                            if (listener != null) listener.onCaptureComplete(false);
                            remove();
                        })
                    ));
                }
            }
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        TextureRegion frame;
        if (!reachedTarget) {
            frame = throwAnimation.getKeyFrame(stateTime, true);
        } else {
            // Show open ball only if capture was successful; otherwise, keep showing the closed frames.
            frame = (captureSuccess != null && captureSuccess) ? openRegion : throwAnimation.getKeyFrame(stateTime, true);
        }
        batch.draw(frame, getX(), getY(), getWidth(), getHeight());
    }


    public interface CaptureListener {
        void onCaptureComplete(boolean success);
    }
}
