package io.github.pokemeetup.managers;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import io.github.pokemeetup.utils.textures.TextureManager;

public class WaterEffectManager {
    private static final float RIPPLE_DURATION = 0.5f;
    private static final float RIPPLE_MAX_SCALE = 1.5f;

    private final Pool<Ripple> ripplePool;
    private final Array<Ripple> activeRipples;
    private final TextureRegion rippleTexture;

    public WaterEffectManager() {
        rippleTexture = TextureManager.tiles.findRegion("water_puddle");
        activeRipples = new Array<>();

        ripplePool = new Pool<Ripple>() {
            @Override
            protected Ripple newObject() {
                return new Ripple();
            }
        };
    }

    public void createRipple(float x, float y) {
        Ripple ripple = ripplePool.obtain();
        ripple.init(x, y);
        activeRipples.add(ripple);
    }

    public void update(float delta) {
        for (int i = activeRipples.size - 1; i >= 0; i--) {
            Ripple ripple = activeRipples.get(i);
            ripple.update(delta);

            if (ripple.isComplete()) {
                activeRipples.removeIndex(i);
                ripplePool.free(ripple);
            }
        }
    }

    public void render(SpriteBatch batch) {
        Color originalColor = batch.getColor().cpy();

        for (Ripple ripple : activeRipples) {
            float alpha = 1f - (ripple.stateTime / RIPPLE_DURATION);
            batch.setColor(1, 1, 1, alpha * 0.5f);

            float scale = 1f + ((RIPPLE_MAX_SCALE - 1f) * (ripple.stateTime / RIPPLE_DURATION));
            float width = rippleTexture.getRegionWidth() * scale;
            float height = rippleTexture.getRegionHeight() * scale;

            batch.draw(rippleTexture,
                ripple.x - width/2,
                ripple.y - height/2,
                width, height);
        }

        batch.setColor(originalColor);
    }

    private static class Ripple {
        float x, y;
        float stateTime;

        void init(float x, float y) {
            this.x = x;
            this.y = y;
            this.stateTime = 0;
        }

        void update(float delta) {
            stateTime += delta;
        }

        boolean isComplete() {
            return stateTime >= RIPPLE_DURATION;
        }
    }
}
