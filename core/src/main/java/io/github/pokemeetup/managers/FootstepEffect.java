package io.github.pokemeetup.managers;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.utils.textures.TextureManager;

public class FootstepEffect {
    private Vector2 position;
    private float stateTime;
    private final float duration;
    private final String direction; // "up", "down", "left", or "right"

    /**
     * @param position  the world position where the effect is drawn
     * @param direction the direction of the footstep (to choose the correct region)
     * @param duration  how long (in seconds) the effect takes to fade out
     */
    public FootstepEffect(Vector2 position, String direction, float duration) {
        // Create a copy of the position so it won’t be modified externally.
        this.position = new Vector2(position);
        this.direction = direction;
        this.duration = duration;
        this.stateTime = 0f;
    }

    public void update(float delta) {
        stateTime += delta;
    }

    public boolean isFinished() {
        return stateTime >= duration;
    }
    public void render(SpriteBatch batch) {
        // Calculate fading alpha: goes from 1 to 0 over the duration.
        float computedAlpha = 1.0f - (stateTime / duration);
        TextureRegion region = null;
        if (direction.equalsIgnoreCase("down")) {
            region = TextureManager.steps.findRegion("stepsDown");
        } else if (direction.equalsIgnoreCase("up")) {
            region = TextureManager.steps.findRegion("stepsUp");
        } else if (direction.equalsIgnoreCase("left")) {
            region = TextureManager.steps.findRegion("stepsLeft");
        } else if (direction.equalsIgnoreCase("right")) {
            region = TextureManager.steps.findRegion("stepsRight");
        }
        if (region != null) {
            // Instead of overriding with white, modulate the current batch color.
            Color ambient = batch.getColor(); // this is the current world tint (e.g. dark at night)
            // Multiply the current alpha by the computed fading factor.
            float newAlpha = ambient.a * computedAlpha;
            // Save the current color.
            Color previous = new Color(ambient);
            // Set the batch color to the current r, g, b with the new alpha.
            batch.setColor(ambient.r, ambient.g, ambient.b, newAlpha);
            // Draw the region centered horizontally at the given position.
            batch.draw(region, position.x - region.getRegionWidth() / 2f, position.y);
            // Restore the previous color.
            batch.setColor(previous);
        }
    }

}
