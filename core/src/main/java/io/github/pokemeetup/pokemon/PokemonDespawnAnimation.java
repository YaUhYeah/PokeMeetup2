package io.github.pokemeetup.pokemon;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.utils.textures.TextureManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PokemonDespawnAnimation {
    private static final float DESPAWN_DURATION = 1.0f; // Total animation time in seconds
    private static final float SPARKLE_DURATION = 0.3f; // Duration for each sparkle
    private static final int NUM_SPARKLES = 6; // Number of sparkle particles

    private float animationTime = 0f;
    private final List<Sparkle> sparkles;
    private boolean isComplete = false;
    private final Vector2 position;
    private final Random random;

    private static class Sparkle {
        float x, y;
        float angle;
        float lifeTime;

        Sparkle(float x, float y, float angle) {
            this.x = x;
            this.y = y;
            this.angle = angle;
            this.lifeTime = 0f;
        }
    }

    public PokemonDespawnAnimation(float x, float y) {
        this.position = new Vector2(x, y);
        this.sparkles = new ArrayList<>();
        this.random = new Random();
        initializeSparkles();
    }

    private void initializeSparkles() {
        for (int i = 0; i < NUM_SPARKLES; i++) {
            float angle = (360f / NUM_SPARKLES) * i;
            sparkles.add(new Sparkle(position.x, position.y, angle));
        }
    }

    public boolean update(float delta) {
        animationTime += delta;

        // Update sparkles
        for (Sparkle sparkle : sparkles) {
            sparkle.lifeTime += delta;
            // Move sparkles outward in a spiral pattern
            float radius = (sparkle.lifeTime / DESPAWN_DURATION) * 32f;
            float rotationSpeed = 360f * (sparkle.lifeTime / DESPAWN_DURATION);
            sparkle.angle += rotationSpeed * delta;
            sparkle.x = position.x + radius * MathUtils.cosDeg(sparkle.angle);
            sparkle.y = position.y + radius * MathUtils.sinDeg(sparkle.angle);
        }

        return animationTime >= DESPAWN_DURATION;
    }

    public void render(SpriteBatch batch, TextureRegion pokemonSprite, float width, float height) {
        if (isComplete) return;

        float progress = animationTime / DESPAWN_DURATION;
        float alpha = 1.0f - progress;
        float scale = 1.0f - (progress * 0.5f);

        // Save batch color
        Color prevColor = batch.getColor();

        // Render fading Pokémon
        batch.setColor(prevColor.r, prevColor.g, prevColor.b, alpha);
        float scaledWidth = width * scale;
        float scaledHeight = height * scale;
        float xOffset = (width - scaledWidth) / 2;
        float yOffset = (height - scaledHeight) / 2;

        batch.draw(pokemonSprite,
            position.x + xOffset, position.y + yOffset,
            scaledWidth / 2, scaledHeight / 2,
            scaledWidth, scaledHeight,
            1f, 1f, 0);

        // Render sparkles
        TextureRegion sparkleTexture = TextureManager.ui.findRegion("sparkle");
        if (sparkleTexture != null) {
            for (Sparkle sparkle : sparkles) {
                float sparkleProgress = sparkle.lifeTime / SPARKLE_DURATION;
                float sparkleAlpha = 1.0f - (sparkleProgress > 1f ? 1f : sparkleProgress);
                float sparkleScale = 0.5f - (sparkleProgress * 0.3f);

                batch.setColor(1f, 1f, 1f, sparkleAlpha);
                batch.draw(sparkleTexture,
                    sparkle.x - (8f * sparkleScale), sparkle.y - (8f * sparkleScale),
                    8f, 8f,
                    16f * sparkleScale, 16f * sparkleScale,
                    1f, 1f, sparkle.angle);
            }
        }

        // Restore batch color
        batch.setColor(prevColor);
    }

    public boolean isComplete() {
        return isComplete;
    }
}
