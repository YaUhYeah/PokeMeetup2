package io.github.pokemeetup.system;

import com.badlogic.gdx.graphics.Color;

public class UITransitionManager {
    public static final float DEFAULT_TRANSITION_TIME = 0.3f;
    private final Color color = new Color(1, 1, 1, 1);
    private float alpha = 1f;
    private TransitionAction currentTransition;

    public void startShowTransition(float duration, Runnable onComplete) {
        clearTransitions();
        currentTransition = new TransitionAction(
            duration,
            0f,
            1f,
            onComplete
        );
    }

    public void startHideTransition(float duration, Runnable onComplete) {
        clearTransitions();
        currentTransition = new TransitionAction(
            duration,
            1f,
            0f,
            onComplete
        );
    }

    public void update(float delta) {
        if (currentTransition != null) {
            currentTransition.update(delta);
        }
    }

    public void clearTransitions() {
        if (currentTransition != null) {
            currentTransition.complete();
            currentTransition = null;
        }
    }

    public Color getColor() {
        return color;
    }

    public class TransitionAction {
        private final float duration;
        private final float startAlpha;
        private final float targetAlpha;
        private final Runnable onComplete;
        private float currentTime = 0;
        private boolean isComplete = false;

        public TransitionAction(float duration, float startAlpha, float targetAlpha, Runnable onComplete) {
            this.duration = duration;
            this.startAlpha = startAlpha;
            this.targetAlpha = targetAlpha;
            this.onComplete = onComplete;
        }

        public void update(float delta) {
            if (isComplete) return;

            currentTime += delta;
            float progress = Math.min(currentTime / duration, 1f);
            float smoothProgress = smoothStep(progress);
            alpha = startAlpha + (targetAlpha - startAlpha) * smoothProgress;
            color.a = alpha;

            if (progress >= 1f) {
                complete();
            }
        }

        public void complete() {
            if (isComplete) return;
            isComplete = true;
            alpha = targetAlpha;
            color.a = alpha;
            if (onComplete != null) {
                onComplete.run();
            }
        }

        private float smoothStep(float x) {
            return x * x * (3 - 2 * x);
        }
    }
}
