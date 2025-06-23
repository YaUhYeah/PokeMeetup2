package io.github.pokemeetup.pokemon;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;

public class PokemonDespawnAnimation {
    private final float duration;
    private float timer;
    private final float x, y;
    private final float frameWidth, frameHeight;

    public PokemonDespawnAnimation(float x, float y, float frameWidth, float frameHeight) {
        this.duration = 2.0f;
        this.timer = 0f;
        this.x = x;
        this.y = y;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
    }

    public boolean update(float delta) {
        timer += delta;
        return timer >= duration;
    }

    /**
     * Renders the current frame of the animation with a fading effect,
     * correctly preserving the day/night color tint from the SpriteBatch.
     */
    public void render(SpriteBatch batch, TextureRegion currentFrame) {
        // Save the batch's current color, which includes the day/night tint.
        Color originalColor = batch.getColor().cpy();

        // Calculate the fading alpha for the despawn effect.
        float despawnAlpha = MathUtils.clamp(1 - (timer / duration), 0, 1);

        // Modulate the batch's current alpha with our despawn alpha.
        float finalAlpha = originalColor.a * despawnAlpha;
        batch.setColor(originalColor.r, originalColor.g, originalColor.b, finalAlpha);

        // Draw the pokemon frame with the new faded color.
        if (currentFrame != null) {
            batch.draw(currentFrame, x, y, frameWidth, frameHeight);
        }

        // IMPORTANT: Restore the batch color to its original state.
        batch.setColor(originalColor);
    }
}
