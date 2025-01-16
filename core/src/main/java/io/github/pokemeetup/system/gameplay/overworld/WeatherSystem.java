package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import io.github.pokemeetup.managers.BiomeTransitionResult;
import io.github.pokemeetup.screens.GameScreen;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.badlogic.gdx.math.MathUtils.random;
import static io.github.pokemeetup.system.gameplay.overworld.WeatherSystem.WeatherType.HEAVY_RAIN;
import static io.github.pokemeetup.system.gameplay.overworld.WeatherSystem.WeatherType.RAIN;


public class WeatherSystem {
    private static final float SPAWN_MARGIN = 150f;
    private static final int MAX_PARTICLES = 300;
    private static final float MAX_PARTICLE_SPAWN_RATE = 300f; // Adjust as needed
    private static final float PARTICLE_DESPAWN_MARGIN = 150f;
    private static final float RAIN_SPEED = 800f;
    private static final float RAIN_ANGLE = 75f;
    private static final float RAIN_SCALE = 0.7f;
    private static final float RAIN_BASE_ALPHA = 0.7f;
    private static final float SNOW_SPEED = 300f;
    private static final float SAND_SPEED = 500f;
    private static final float WEATHER_CHECK_INTERVAL = 10f;
    private final List<WeatherParticle> particles;
    private final TextureRegion rainDrop;
    private final TextureRegion snowflake;
    private final TextureRegion sandParticle;
    private float fogOffsetX = 0f;
    private float fogOffsetY = 0f;
    private float particleSpawnAccumulator = 0f;
    private WeatherType currentWeather;
    private float intensity;
    private float accumulation;
    private float weatherCheckTimer = 0f;

    public WeatherSystem() {
        this.particles = new ArrayList<>();
        this.currentWeather = WeatherType.CLEAR;
        this.intensity = 0f;
        this.accumulation = 0f;
        this.rainDrop = TextureManager.effects.findRegion("rain_drop");
        this.snowflake = TextureManager.effects.findRegion("snowflake");
        this.sandParticle = TextureManager.effects.findRegion("sand_particle");
    }

    public List<WeatherParticle> getParticles() {
        return particles;
    }

    private void updateWeatherType(BiomeTransitionResult biomeTransition, float temperature, float timeOfDay) {
        Random random = new Random();
        float baseChance = random.nextFloat();

        if (baseChance > 0.3f) {
            switch (biomeTransition.getPrimaryBiome().getType()) {
                case RAIN_FOREST:
                    setWeather(WeatherType.HEAVY_RAIN, 0.8f);
                    break;

                case HAUNTED:
                    if (random.nextFloat() < 0.9f) {
                        setWeather(WeatherType.FOG, 0.9f);
                    } else {
                        setWeather(WeatherType.THUNDERSTORM, 0.9f);
                    }
                    break;


                case SNOW:
                    setWeather(temperature < 0 ? WeatherType.BLIZZARD : WeatherType.SNOW, 0.6f);
                    break;

                case DESERT:
                    if (temperature > 25) {
                        setWeather(WeatherType.SANDSTORM, 0.7f);
                    }
                    break;

                case FOREST:
                    if (random.nextFloat() > 0.5f) {
                        setWeather(WeatherType.RAIN, 0.5f);
                    }
                    break;

                default:
                    if (random.nextFloat() > 0.7f) {
                        setWeather(WeatherType.RAIN, 0.3f);
                    }
                    break;
            }
        } else {
            setWeather(WeatherType.CLEAR, 0f);
        }
    }

    public void update(float delta, BiomeTransitionResult biomeTransition, float temperature, float timeOfDay, GameScreen gameScreen) {
        weatherCheckTimer += delta;

        if (weatherCheckTimer >= WEATHER_CHECK_INTERVAL) {
            updateWeatherType(biomeTransition, temperature, timeOfDay);
            weatherCheckTimer = 0f;

            GameLogger.info(String.format(
                "Weather Updated - Type: %s, Intensity: %.2f, Temperature: %.1f",
                currentWeather, intensity, temperature
            ));
        }

        updateParticles(delta, gameScreen.getCamera());


        generateParticles(delta, gameScreen);

        updateAccumulation(delta);
        if (currentWeather == WeatherType.FOG) {
            float fogSpeedX = 20f;
            float fogSpeedY = 10f;
            fogOffsetX += fogSpeedX * delta;
            fogOffsetY += fogSpeedY * delta;
        }
    }

    private void generateParticles(float delta, GameScreen gameScreen) {
        if (currentWeather == WeatherType.CLEAR || currentWeather == WeatherType.FOG) {
            return;
        }

        float particleSpawnRate = intensity * MAX_PARTICLE_SPAWN_RATE;

        // Adjust spawn rate for heavy rain
        if (currentWeather == WeatherType.HEAVY_RAIN || currentWeather == WeatherType.THUNDERSTORM) {
            particleSpawnRate *= 1.5f;
        }

        particleSpawnAccumulator += delta * particleSpawnRate;

        int particlesToGenerate = (int) particleSpawnAccumulator;
        particleSpawnAccumulator -= particlesToGenerate;

        // Limit the maximum number of particles to prevent exceeding MAX_PARTICLES
        particlesToGenerate = Math.min(particlesToGenerate, MAX_PARTICLES - particles.size());

        for (int i = 0; i < particlesToGenerate; i++) {
            WeatherParticle particle = createParticle(gameScreen);
            if (particle != null) {
                particles.add(particle);
            }
        }
    }

    private WeatherParticle createParticle(GameScreen gameScreen) {
        OrthographicCamera camera = gameScreen.getCamera();
        float cameraX = camera.position.x;
        float cameraY = camera.position.y;
        float viewWidth = camera.viewportWidth * camera.zoom;
        float viewHeight = camera.viewportHeight * camera.zoom;

        float spawnX, spawnY;
        if (currentWeather == WeatherType.RAIN ||
            currentWeather == WeatherType.HEAVY_RAIN ||
            currentWeather == WeatherType.THUNDERSTORM) {


            spawnX = MathUtils.random(
                cameraX - viewWidth / 2 - SPAWN_MARGIN,
                cameraX + viewWidth / 2 + SPAWN_MARGIN
            );
            spawnY = cameraY + viewHeight / 2 + SPAWN_MARGIN;

            // Calculate rain velocity using consistent angle
            float speed = RAIN_SPEED * (currentWeather == WeatherType.HEAVY_RAIN ? 1.2f : 1f);
            float velocityX = -speed * MathUtils.cosDeg(RAIN_ANGLE);
            float velocityY = -speed * MathUtils.sinDeg(RAIN_ANGLE);

            return new WeatherParticle(
                spawnX, spawnY,
                velocityX, velocityY,
                rainDrop,
                false,
                RAIN_SCALE
            );
        }
        // Other weather types
        else {
            spawnX = cameraX + MathUtils.random(-viewWidth / 2 - SPAWN_MARGIN, viewWidth / 2 + SPAWN_MARGIN);
            spawnY = cameraY + viewHeight / 2 + SPAWN_MARGIN;

            switch (currentWeather) {
                case SNOW:
                case BLIZZARD:
                    return new WeatherParticle(
                        spawnX, spawnY,
                        MathUtils.random(-50, 50),
                        -SNOW_SPEED - MathUtils.random(0, 50),
                        snowflake,
                        true,
                        0.6f + MathUtils.random() * 0.8f
                    );

                case SANDSTORM:
                    spawnX = cameraX + viewWidth / 2 + SPAWN_MARGIN;
                    spawnY = cameraY + MathUtils.random(-viewHeight / 2 - SPAWN_MARGIN, viewHeight / 2 + SPAWN_MARGIN);
                    return new WeatherParticle(
                        spawnX, spawnY,
                        -SAND_SPEED - MathUtils.random(0, 100),
                        MathUtils.random(-60, 60),
                        sandParticle,
                        true,
                        0.7f + MathUtils.random() * 0.6f
                    );

                default:
                    return null;
            }
        }
    }

    public void updateParticles(float delta, OrthographicCamera camera) {
        float left = camera.position.x - (camera.viewportWidth / 2 + PARTICLE_DESPAWN_MARGIN) * camera.zoom;
        float right = camera.position.x + (camera.viewportWidth / 2 + PARTICLE_DESPAWN_MARGIN) * camera.zoom;
        float bottom = camera.position.y - (camera.viewportHeight / 2 + PARTICLE_DESPAWN_MARGIN) * camera.zoom;
        float top = camera.position.y + (camera.viewportHeight / 2 + PARTICLE_DESPAWN_MARGIN) * camera.zoom;

        particles.removeIf(particle -> {
            particle.update(delta);
            return particle.isOutOfBounds(left, right, bottom, top);
        });
    }


    public void render(SpriteBatch batch, OrthographicCamera camera) {
        if (currentWeather == WeatherType.CLEAR) return;

        Color prevColor = batch.getColor().cpy();
        int prevSrcFunc = batch.getBlendSrcFunc();
        int prevDstFunc = batch.getBlendDstFunc();

        if (currentWeather == WeatherType.RAIN ||
            currentWeather == WeatherType.HEAVY_RAIN ||
            currentWeather == WeatherType.THUNDERSTORM) {

            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            batch.setColor(1, 1, 1, RAIN_BASE_ALPHA * intensity);
        } else {
            batch.setColor(1, 1, 1, intensity);
        }

        // Render all particles
        for (WeatherParticle particle : particles) {
            particle.render(batch);
        }

        // Restore batch state
        batch.setBlendFunction(prevSrcFunc, prevDstFunc);
        batch.setColor(prevColor);

        // Render fog if applicable
        if (currentWeather == WeatherType.FOG) {
            renderFog(batch,
                camera.position.x - camera.viewportWidth / 2,
                camera.position.y - camera.viewportHeight / 2,
                camera.viewportWidth,
                camera.viewportHeight
            );
        }
    }

    private void renderFog(SpriteBatch batch, float x, float y, float width, float height) {
        batch.setColor(1, 1, 1, 0.3f * intensity);

        TextureRegion fogTexture = TextureManager.effects.findRegion("fog");
        float fogTextureWidth = fogTexture.getRegionWidth();
        float fogTextureHeight = fogTexture.getRegionHeight();

        float startX = x - (fogOffsetX % (fogTextureWidth)) - fogTextureWidth;
        float startY = y - (fogOffsetY % (fogTextureHeight)) - fogTextureHeight;
        for (float posX = startX; posX < x + width; posX += fogTextureWidth) {
            for (float posY = startY; posY < y + height; posY += fogTextureHeight) {
                batch.draw(
                    fogTexture,
                    posX,
                    posY,
                    fogTextureWidth,
                    fogTextureHeight
                );
            }
        }

        batch.setColor(1, 1, 1, 1);
    }

    private void updateAccumulation(float delta) {
        float accumulationRate;

        if (currentWeather == WeatherType.SNOW || currentWeather == WeatherType.BLIZZARD) {
            accumulationRate = 0.1f * intensity;
        } else if (currentWeather.equals(RAIN)) {
            accumulationRate = 0.05f * intensity;
        } else if (currentWeather == HEAVY_RAIN || currentWeather == WeatherType.THUNDERSTORM) {
            accumulationRate = 0.15f * intensity;
        } else {
            accumulationRate = -0.05f;
        }

        accumulation = MathUtils.clamp(accumulation + accumulationRate * delta, 0, 1);
    }

    public void setWeather(WeatherType type, float intensity) {
        this.currentWeather = type;
        this.intensity = intensity;
    }

    public WeatherType getCurrentWeather() {
        return currentWeather;
    }

    public float getIntensity() {
        return intensity;
    }

    public float getAccumulation() {
        return accumulation;
    }

    public enum WeatherType {
        CLEAR,
        RAIN,
        HEAVY_RAIN,
        SNOW,
        BLIZZARD,
        SANDSTORM,
        FOG,
        THUNDERSTORM
    }
}

class WeatherParticle {
    private final TextureRegion texture;
    private float x, y;
    private float velocityX, velocityY;
    private float rotation;
    private float rotationSpeed;
    private float scale;
    private boolean rotating;
    public WeatherParticle(float x, float y, float velocityX, float velocityY,
                           TextureRegion texture, boolean rotating, float scale) {
        this.x = x;
        this.y = y;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.texture = texture;
        this.rotating = rotating;
        this.scale = scale;
        this.rotation = 0f;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public void update(float delta) {
        x += velocityX * delta;
        y += velocityY * delta;

        if (rotating) {
            rotation += rotationSpeed * delta;
        }
    }

    public void render(SpriteBatch batch) {
        if (rotating) {
            batch.draw(
                texture,
                x, y,
                texture.getRegionWidth() / 2f,
                texture.getRegionHeight() / 2f,
                texture.getRegionWidth(),
                texture.getRegionHeight(),
                scale, scale,
                rotation
            );
        } else {
            batch.draw(
                texture,
                x, y,
                texture.getRegionWidth() * scale,
                texture.getRegionHeight() * scale
            );
        }
    }

    public boolean isOutOfBounds(float left, float right, float bottom, float top) {
        return x < left || x > right || y < bottom || y > top;
    }

    public boolean isInView(float left, float right, float bottom, float top) {
        float margin = 100;
        return x >= left - margin && x <= right + margin &&
            y >= bottom - margin && y <= top + margin;
    }

}
