
package io.github.pokemeetup.managers;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Array;

public class FootstepEffectManager {
    private final Array<FootstepEffect> effects;

    public FootstepEffectManager() {
        effects = new Array<>();
    }

    public void addEffect(FootstepEffect effect) {
        effects.add(effect);
    }

    public void update(float delta) {
        for (int i = effects.size - 1; i >= 0; i--) {
            FootstepEffect effect = effects.get(i);
            effect.update(delta);
            if (effect.isFinished()) {
                effects.removeIndex(i);
            }
        }
    }

    public void render(SpriteBatch batch) {
        for(FootstepEffect effect : effects) {
            effect.render(batch);
        }
    }
}
