package io.github.pokemeetup.pokemon;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;

/**
 * A simple fade–out despawn animation.
 */
public class PokemonDespawnAnimation {
    private final float duration; // seconds for fade–out
    private float timer;
    private final float x, y;
    private final float frameWidth, frameHeight;

    public PokemonDespawnAnimation(float x, float y, float frameWidth, float frameHeight) {
        this.duration = 2.0f; // for example, 2 seconds fade out
        this.timer = 0f;
        this.x = x;
        this.y = y;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
    }

    /**
     * Update the animation. Returns true if the animation is finished.
     */
    public boolean update(float delta) {
        timer += delta;
        return timer >= duration;
    }

    /**
     * Render the current frame of the animation with a fading effect.
     */
    public void render(SpriteBatch batch, TextureRegion currentFrame) {
        float alpha = MathUtils.clamp(1 - (timer / duration), 0, 1);
        batch.setColor(1f, 1f, 1f, alpha);
        batch.draw(currentFrame, x, y, frameWidth, frameHeight);
        batch.setColor(1f, 1f, 1f, 1f);
    }
}
