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
        float alpha = 1.0f - (stateTime / duration);
        TextureRegion region = null;
        if(direction.equalsIgnoreCase("down")) {
            region = TextureManager.steps.findRegion("stepsDown");
        } else if(direction.equalsIgnoreCase("up")) {
            region = TextureManager.steps.findRegion("stepsUp");
        } else if(direction.equalsIgnoreCase("left")) {
            region = TextureManager.steps.findRegion("stepsLeft");
        } else if(direction.equalsIgnoreCase("right")) {
            region = TextureManager.steps.findRegion("stepsRight");
        }
        if(region != null) {
            Color oldColor = batch.getColor();
            batch.setColor(oldColor.r, oldColor.g, oldColor.b, oldColor.a * alpha);
            // Draw the region centered horizontally; adjust Y as needed.
            batch.draw(region, position.x - region.getRegionWidth() / 2f, position.y);
            batch.setColor(oldColor);
        }
    }
}
