package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;

public class WeatherAudioSystem {
    private static final float THUNDER_MIN_INTERVAL = 5f;
    private static final float THUNDER_MAX_INTERVAL = 15f;

    private float thunderTimer;
    private float nextThunderTime;
    private boolean isThundering;
    private float lightningAlpha;
    private final AudioManager audioManager;

    public WeatherAudioSystem(AudioManager audioManager) {
        this.audioManager = audioManager;
        resetThunderTimer();
    }

    private void resetThunderTimer() {
        nextThunderTime = MathUtils.random(THUNDER_MIN_INTERVAL, THUNDER_MAX_INTERVAL);
        thunderTimer = 0;
    }

    public void update(float delta, WeatherSystem.WeatherType currentWeather, float intensity) {
        updateThunderAndLightning(delta, currentWeather, intensity);
        updateWeatherSounds(currentWeather, intensity);
    }
    private void updateThunderAndLightning(float delta, WeatherSystem.WeatherType currentWeather, float intensity) {
        if (currentWeather == WeatherSystem.WeatherType.THUNDERSTORM) {
            thunderTimer += delta;

            if (isThundering) {
                lightningTimer += delta;
                // For the first 0.05 seconds, keep the flash at full intensity.
                if (lightningTimer < 0.05f) {
                    lightningAlpha = 1.0f; // full flash
                }
                // Then fade out over the remainder of the flash duration.
                else if (lightningTimer < lightningDuration) {
                    float t = (lightningTimer - 0.05f) / (lightningDuration - 0.05f);
                    lightningAlpha = MathUtils.lerp(1.0f, 0f, t);
                } else {
                    // End of flash effect.
                    isThundering = false;
                    lightningAlpha = 0;
                    lightningTimer = 0;
                }
            }

            // Trigger a new thunder flash if the timer has reached the next scheduled thunder.
            if (thunderTimer >= nextThunderTime) {
                triggerThunderAndLightning(intensity);
                resetThunderTimer();
            }
        } else {
            // Reset lightning variables if the weather changes.
            lightningAlpha = 0;
            isThundering = false;
            lightningTimer = 0;
            resetThunderTimer();
        }
    }
private float lightningTimer = 0f;
    private final float lightningDuration = 0.2f; // total flash duration of 200 ms

    private void triggerThunderAndLightning(float intensity) {
        isThundering = true;
        lightningTimer = 0;
        // Start with a full-intensity flash.
        lightningAlpha = 1.0f;

        // (Optionally, if you want to modulate by intensity, you could do:
        // lightningAlpha = Math.min(1.0f, 0.7f * intensity);
        // but a full flash often looks best.)

        // Play thunder sound with random variation.
        float volume = 0.5f + (intensity * 0.5f);
        float pitch = 0.9f + (MathUtils.random() * 0.2f);
        audioManager.playWeatherSound(AudioManager.WeatherSoundEffect.THUNDER, volume, pitch);
    }

    private void updateWeatherSounds(WeatherSystem.WeatherType currentWeather, float intensity) {
        // Update looping weather sounds
        switch (currentWeather) {
            case RAIN:
                audioManager.updateWeatherLoop(AudioManager.WeatherSoundEffect.LIGHT_RAIN, intensity * 0.6f);
                break;

            case HEAVY_RAIN:
            case THUNDERSTORM:
                audioManager.stopWeatherLoop(AudioManager.WeatherSoundEffect.LIGHT_RAIN);
                break;

            case SNOW:
            case BLIZZARD:
                audioManager.updateWeatherLoop(AudioManager.WeatherSoundEffect.WIND, intensity * 0.4f);
                break;

            case SANDSTORM:
                audioManager.updateWeatherLoop(AudioManager.WeatherSoundEffect.SAND_WIND, intensity * 0.7f);
                break;

            default:
                // Stop all weather loops for clear weather
                audioManager.stopAllWeatherLoops();
                break;
        }
    }

    public void renderLightningEffect(SpriteBatch batch, float screenWidth, float screenHeight) {
        if (lightningAlpha > 0) {
            Color oldColor = batch.getColor();
            batch.setColor(1, 1, 1, lightningAlpha);

            // Save blend function
            int srcFunc = batch.getBlendSrcFunc();
            int dstFunc = batch.getBlendDstFunc();

            // Use additive blending for lightning
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);

            // Draw full screen white rectangle for lightning flash
            batch.draw(TextureManager.getWhitePixel(), 0, 0, screenWidth, screenHeight);

            // Restore original blend function and color
            batch.setBlendFunction(srcFunc, dstFunc);
            batch.setColor(oldColor);
        }
    }
}
