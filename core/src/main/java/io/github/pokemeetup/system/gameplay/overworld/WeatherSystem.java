package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Rectangle; // Import Rectangle
import com.badlogic.gdx.utils.Pool;
import io.github.pokemeetup.managers.BiomeTransitionResult;
import io.github.pokemeetup.screens.GameScreen;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;
import io.github.pokemeetup.utils.textures.TileType;

import java.util.*;

import static com.badlogic.gdx.math.MathUtils.random;

public class WeatherSystem {
    private static final float SPAWN_MARGIN = 150f;
    private static final int MAX_PARTICLES = 200; // Reduced for better performance
    private static final float MAX_PARTICLE_SPAWN_RATE = 150f; // Reduced spawn rate
    private static final float PARTICLE_DESPAWN_MARGIN = 150f;
    private static final float RAIN_SPEED = 600f; // Slightly slower for realism
    private static final float RAIN_ANGLE = 82f; // More vertical
    private static final float RAIN_SCALE = 0.5f; // Smaller droplets
    private static final float RAIN_BASE_ALPHA = 0.6f;
    private static final float SNOW_SPEED = 150f; // Much slower for realistic snow
    private static final float SNOW_SWAY_AMPLITUDE = 30f; // Gentle swaying
    private static final float SNOW_SWAY_FREQUENCY = 1.5f;
    private static final float SAND_SPEED = 400f;
    private static final float WEATHER_CHECK_INTERVAL = 45f; // Check every 45 seconds instead of 10
    private static final float WEATHER_TRANSITION_DURATION = 5f; // Smooth transitions
    private static final int LANDING_EFFECT_POOL_SIZE = 50;
    private static final float LANDING_EFFECT_DURATION = 0.3f;

    public void updateServerState(float delta, BiomeTransitionResult biomeTransition, float temperature, float timeOfDay) {
        // Handle manual override
        if (manualOverrideTimer > 0) {
            manualOverrideTimer -= delta;
        } else {
            weatherCheckTimer += delta;
            if (weatherCheckTimer >= WEATHER_CHECK_INTERVAL) {
                updateWeatherType(biomeTransition, temperature, timeOfDay);
                weatherCheckTimer = 0f;
                GameLogger.info(String.format("Server Weather Updated - Type: %s, Intensity: %.2f",
                    currentWeather, intensity));
            }
        }

        // Handle smooth weather transitions for state
        if (transitionProgress < 1f) {
            transitionProgress = Math.min(1f, transitionProgress + delta / WEATHER_TRANSITION_DURATION);
            intensity = MathUtils.lerp(intensity, targetIntensity, transitionProgress);
            if (transitionProgress >= 0.5f && currentWeather != targetWeather) {
                currentWeather = targetWeather;
            }
        }

        updateAccumulation(delta);
    }
    private final List<WeatherParticle> particles;
    private final Pool<WeatherParticle> particlePool;
    private final List<LandingEffect> activeLandingEffects;
    private final Pool<LandingEffect> landingEffectPool;
    private final TextureRegion rainDrop;
    private final TextureRegion snowflake;
    private final TextureRegion sandParticle;
    private final TextureRegion rainSplash;
    private final TextureRegion snowPoof;
    private float fogOffsetX = 0f;
    private float fogOffsetY = 0f;
    private float particleSpawnAccumulator = 0f;
    private WeatherType currentWeather;
    private WeatherType targetWeather;
    private float intensity;
    private float targetIntensity;
    private float transitionProgress = 1f;
    private float accumulation;
    private float weatherCheckTimer = 0f;
    private float manualOverrideTimer = 0f;
    private World world;

    public WeatherSystem() {
        this.particles = new ArrayList<>();
        this.activeLandingEffects = new ArrayList<>();
        this.currentWeather = WeatherType.CLEAR;
        this.targetWeather = WeatherType.CLEAR;
        this.intensity = 0f;
        this.targetIntensity = 0f;
        this.accumulation = 0f;

        // Initialize textures
        if (TextureManager.effects != null) {
            this.rainDrop = TextureManager.effects.findRegion("rain_drop");
            this.snowflake = TextureManager.effects.findRegion("snowflake");
            this.sandParticle = TextureManager.effects.findRegion("sand_particle");
            this.rainSplash = TextureManager.owFx.findRegion("rain_splash");
            this.snowPoof = TextureManager.owFx.findRegion("snowflake");
        } else {
            this.rainDrop = null;
            this.snowflake = null;
            this.sandParticle = null;
            this.rainSplash = null;
            this.snowPoof = null;
        }

        // Initialize particle pool for performance
        this.particlePool = new Pool<WeatherParticle>() {
            @Override
            protected WeatherParticle newObject() {
                return new WeatherParticle();
            }
        };

        // Initialize landing effect pool
        this.landingEffectPool = new Pool<LandingEffect>(LANDING_EFFECT_POOL_SIZE) {
            @Override
            protected LandingEffect newObject() {
                return new LandingEffect();
            }
        };
    }

    public void setWorld(World world) {
        this.world = world;
    }

    public void setManualOverrideTimer(float duration) {
        this.manualOverrideTimer = duration;
    }

    /**
     * Enhanced weather decision logic with more realistic patterns and rarer weather
     */
    private void updateWeatherType(BiomeTransitionResult biomeTransition, float temperature, float timeOfDay) {
        float randomValue = MathUtils.random();

        // Apply weather rarity modifiers
        float weatherChance = 0.3f; // Base 30% chance of weather vs clear

        // Time of day modifiers
        if (timeOfDay >= 6 && timeOfDay < 10) {
            weatherChance *= 0.7f; // Less weather in morning
        } else if (timeOfDay >= 16 && timeOfDay < 20) {
            weatherChance *= 1.2f; // More weather in evening
        } else if (timeOfDay >= 20 || timeOfDay < 6) {
            weatherChance *= 1.1f; // Slightly more at night
        }

        // Temperature modifiers
        if (temperature > 30) {
            weatherChance *= 0.8f; // Less weather when hot
        } else if (temperature < 10) {
            weatherChance *= 1.1f; // More weather when cold
        }

        boolean shouldHaveWeather = randomValue < weatherChance;

        switch (biomeTransition.getPrimaryBiome().getType()) {
            case RAIN_FOREST:
                if (shouldHaveWeather) {
                    if (randomValue < 0.2f) {
                        setWeatherWithTransition(WeatherType.HEAVY_RAIN, 0.7f);
                    } else {
                        setWeatherWithTransition(WeatherType.RAIN, 0.5f);
                    }
                } else {
                    setWeatherWithTransition(WeatherType.CLEAR, 0f);
                }
                break;

            case HAUNTED:
                if (timeOfDay >= 18 || timeOfDay < 6) {
                    if (randomValue < 0.5f) {
                        setWeatherWithTransition(WeatherType.FOG, 0.7f);
                    } else if (randomValue < 0.6f) {
                        setWeatherWithTransition(WeatherType.THUNDERSTORM, 0.8f);
                    } else {
                        setWeatherWithTransition(WeatherType.CLEAR, 0f);
                    }
                } else {
                    if (randomValue < 0.3f) {
                        setWeatherWithTransition(WeatherType.FOG, 0.5f);
                    } else {
                        setWeatherWithTransition(WeatherType.CLEAR, 0f);
                    }
                }
                break;

            case SNOW:
                if (temperature < -5) {
                    if (shouldHaveWeather && randomValue < 0.3f) {
                        setWeatherWithTransition(WeatherType.BLIZZARD, 0.6f);
                    } else if (shouldHaveWeather) {
                        setWeatherWithTransition(WeatherType.SNOW, 0.4f);
                    } else {
                        setWeatherWithTransition(WeatherType.CLEAR, 0f);
                    }
                } else {
                    if (shouldHaveWeather && randomValue < 0.5f) {
                        setWeatherWithTransition(WeatherType.SNOW, 0.3f);
                    } else {
                        setWeatherWithTransition(WeatherType.CLEAR, 0f);
                    }
                }
                break;

            case DESERT:
                if (temperature > 35 && randomValue < 0.15f) { // Rare sandstorms
                    setWeatherWithTransition(WeatherType.SANDSTORM, 0.6f);
                } else {
                    setWeatherWithTransition(WeatherType.CLEAR, 0f);
                }
                break;

            case FOREST:
                if (shouldHaveWeather && randomValue < 0.3f) {
                    setWeatherWithTransition(WeatherType.RAIN, 0.3f);
                } else {
                    setWeatherWithTransition(WeatherType.CLEAR, 0f);
                }
                break;

            default:
                setWeatherWithTransition(WeatherType.CLEAR, 0f);
                break;
        }
    }

    private void setWeatherWithTransition(WeatherType type, float newIntensity) {
        if (this.currentWeather != type || Math.abs(this.intensity - newIntensity) > 0.1f) {
            this.targetWeather = type;
            this.targetIntensity = newIntensity;
            this.transitionProgress = 0f;
        }
    }

    public void update(float delta, BiomeTransitionResult biomeTransition, float temperature, float timeOfDay, GameScreen gameScreen) {
        // Handle manual override
        if (manualOverrideTimer > 0) {
            manualOverrideTimer -= delta;
        } else {
            weatherCheckTimer += delta;
            if (weatherCheckTimer >= WEATHER_CHECK_INTERVAL) {
                updateWeatherType(biomeTransition, temperature, timeOfDay);
                weatherCheckTimer = 0f;
                GameLogger.info(String.format("Weather Updated - Type: %s, Intensity: %.2f, Temperature: %.1f",
                    currentWeather, intensity, temperature));
            }
        }

        // Handle smooth weather transitions
        if (transitionProgress < 1f) {
            transitionProgress = Math.min(1f, transitionProgress + delta / WEATHER_TRANSITION_DURATION);

            // Smooth intensity transition
            intensity = MathUtils.lerp(intensity, targetIntensity, transitionProgress);

            // Switch weather type at midpoint
            if (transitionProgress >= 0.5f && currentWeather != targetWeather) {
                currentWeather = targetWeather;
                // Clear particles when switching weather types
                clearParticles();
            }
        }

        updateParticles(delta, gameScreen.getCamera());
        updateLandingEffects(delta);
        generateParticles(delta, gameScreen);
        updateAccumulation(delta);

        if (currentWeather == WeatherType.FOG) {
            float fogSpeedX = 15f;
            float fogSpeedY = 8f;
            fogOffsetX += fogSpeedX * delta;
            fogOffsetY += fogSpeedY * delta;
        }
    }

    private void clearParticles() {
        for (WeatherParticle particle : particles) {
            particlePool.free(particle);
        }
        particles.clear();
    }

    public void setWeather(WeatherType type, float intensity) {
        if (this.currentWeather != type) {
            clearParticles();
        }
        this.currentWeather = type;
        this.targetWeather = type;
        this.intensity = intensity;
        this.targetIntensity = intensity;
        this.transitionProgress = 1f;
    }

    public WeatherType getCurrentWeather() {
        return currentWeather;
    }

    public void updateParticles(float delta, OrthographicCamera camera) {
        float left = camera.position.x - (camera.viewportWidth / 2 + PARTICLE_DESPAWN_MARGIN) * camera.zoom;
        float right = camera.position.x + (camera.viewportWidth / 2 + PARTICLE_DESPAWN_MARGIN) * camera.zoom;
        float bottom = camera.position.y - (camera.viewportHeight / 2 + PARTICLE_DESPAWN_MARGIN) * camera.zoom;
        float top = camera.position.y + (camera.viewportHeight / 2 + PARTICLE_DESPAWN_MARGIN) * camera.zoom;

        Iterator<WeatherParticle> iter = particles.iterator();
        while (iter.hasNext()) {
            WeatherParticle particle = iter.next();
            particle.update(delta);

            // Check if particle has landed
            if (world != null && shouldCheckLanding(particle)) {
                int tileX = (int)(particle.x / World.TILE_SIZE);
                int tileY = Math.floorDiv((int)particle.y, World.TILE_SIZE);

                if (world.isPositionLoaded(tileX, tileY)) {
                    int tileType = world.getTileTypeAt(tileX, tileY);

                    // Check if particle should land on this tile
                    if (particle.y <= tileY * World.TILE_SIZE + getParticleLandingHeight(tileType)) {
                        createLandingEffect(particle);
                        iter.remove();
                        particlePool.free(particle);
                        continue;
                    }
                }
            }

            // Remove if out of bounds
            if (particle.isOutOfBounds(left, right, bottom, top)) {
                iter.remove();
                particlePool.free(particle);
            }
        }
    }

    private boolean shouldCheckLanding(WeatherParticle particle) {
        // Only check landing for rain and snow
        return currentWeather == WeatherType.RAIN ||
            currentWeather == WeatherType.HEAVY_RAIN ||
            currentWeather == WeatherType.SNOW ||
            currentWeather == WeatherType.BLIZZARD;
    }

    private float getParticleLandingHeight(int tileType) {
        // Different landing heights for different tile types
        if (TileType.isWaterPuddle(tileType) || tileType==TileType.WATER) {
            return 2f; // Land slightly above water
        } else if (tileType == TileType.TALL_GRASS || tileType == TileType.FOREST_TALL_GRASS) {
            return 8f; // Land on top of grass
        }
        return 0f; // Land on ground level
    }

    private void createLandingEffect(WeatherParticle particle) {
        if (activeLandingEffects.size() >= LANDING_EFFECT_POOL_SIZE) {
            return; // Don't create more effects if pool is full
        }

        LandingEffect effect = landingEffectPool.obtain();
        effect.init(particle.x, particle.y, currentWeather);
        activeLandingEffects.add(effect);
    }

    private void updateLandingEffects(float delta) {
        Iterator<LandingEffect> iter = activeLandingEffects.iterator();
        while (iter.hasNext()) {
            LandingEffect effect = iter.next();
            effect.update(delta);

            if (effect.isFinished()) {
                iter.remove();
                landingEffectPool.free(effect);
            }
        }
    }

    private void generateParticles(float delta, GameScreen gameScreen) {
        if (currentWeather == WeatherType.CLEAR || currentWeather == WeatherType.FOG) {
            return;
        }

        float particleSpawnRate = intensity * MAX_PARTICLE_SPAWN_RATE;

        // Adjust spawn rate for heavy weather types
        if (currentWeather == WeatherType.HEAVY_RAIN || currentWeather == WeatherType.THUNDERSTORM) {
            particleSpawnRate *= 1.3f; // Less aggressive multiplier
        } else if (currentWeather == WeatherType.BLIZZARD) {
            particleSpawnRate *= 1.2f;
        }

        particleSpawnAccumulator += delta * particleSpawnRate;
        int particlesToGenerate = (int) particleSpawnAccumulator;
        particleSpawnAccumulator -= particlesToGenerate;
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
        // 1. Define the landing area on the ground (the camera's view)
        Rectangle landingZone = new Rectangle(
            camera.position.x - camera.viewportWidth * camera.zoom / 2,
            camera.position.y - camera.viewportHeight * camera.zoom / 2,
            camera.viewportWidth * camera.zoom,
            camera.viewportHeight * camera.zoom
        );

        // 2. Pick a random landing spot (target) within this zone
        float targetX = MathUtils.random(landingZone.x, landingZone.x + landingZone.width);
        float targetY = MathUtils.random(landingZone.y, landingZone.y + landingZone.height);

        float spawnX, spawnY;
        float velocityX, velocityY;
        float fallDuration;

        WeatherParticle particle = particlePool.obtain();

        switch (currentWeather) {
            case RAIN:
            case HEAVY_RAIN:
            case THUNDERSTORM:
                float speed = RAIN_SPEED * (currentWeather == WeatherType.HEAVY_RAIN ? 1.15f : 1f);
                velocityX = -speed * MathUtils.cosDeg(RAIN_ANGLE);
                velocityY = -speed * MathUtils.sinDeg(RAIN_ANGLE);

                if (velocityY >= 0) { // Should be negative, but as a safeguard
                    particlePool.free(particle);
                    return null;
                }

                // Randomize the fall duration to de-synchronize particles
                fallDuration = MathUtils.random(0.1f, 1.5f);

                // Calculate spawn position based on target and randomized fall duration
                spawnX = targetX - (velocityX * fallDuration);
                spawnY = targetY - (velocityY * fallDuration);

                particle.init(spawnX, spawnY, velocityX, velocityY, rainDrop, false, RAIN_SCALE);
                break;

            case SNOW:
            case BLIZZARD:
                float baseVelocityX = (currentWeather == WeatherType.BLIZZARD) ? random(-100, -50) : random(-30, 30);
                velocityY = -SNOW_SPEED - random(0, 30);

                if (velocityY >= 0) {
                    particlePool.free(particle);
                    return null;
                }

                // Randomize fall duration for snow as well
                fallDuration = MathUtils.random(1.0f, 4.0f); // Snow falls slower, so longer duration range

                spawnX = targetX - (baseVelocityX * fallDuration);
                spawnY = targetY - (velocityY * fallDuration);

                float scale = 0.4f + random() * 0.6f;
                particle.init(spawnX, spawnY, baseVelocityX, velocityY, snowflake, true, scale);
                particle.swayOffset = random(0, MathUtils.PI2);
                break;

            case SANDSTORM:
                // Sandstorms blow horizontally, so this logic is different
                float viewWidth = camera.viewportWidth * camera.zoom;
                float viewHeight = camera.viewportHeight * camera.zoom;

                spawnX = camera.position.x + viewWidth / 2 + SPAWN_MARGIN;
                spawnY = MathUtils.random(
                    camera.position.y - viewHeight / 2 - SPAWN_MARGIN,
                    camera.position.y + viewHeight / 2 + SPAWN_MARGIN);

                particle.init(spawnX, spawnY,
                    -SAND_SPEED - random(0, 100),
                    random(-60, 60),
                    sandParticle, true, 0.5f + random() * 0.5f);
                break;

            default:
                particlePool.free(particle);
                return null;
        }

        return particle;
    }

    public void render(SpriteBatch batch, OrthographicCamera camera) {
        if (currentWeather == WeatherType.CLEAR) return;

        Color prevColor = batch.getColor().cpy();
        int prevSrcFunc = batch.getBlendSrcFunc();
        int prevDstFunc = batch.getBlendDstFunc();

        // Set appropriate blending for weather
        if (currentWeather == WeatherType.RAIN ||
            currentWeather == WeatherType.HEAVY_RAIN ||
            currentWeather == WeatherType.THUNDERSTORM) {
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            batch.setColor(1, 1, 1, RAIN_BASE_ALPHA * intensity);
        } else {
            batch.setColor(1, 1, 1, intensity * 0.8f);
        }

        // Render particles
        for (WeatherParticle particle : particles) {
            particle.render(batch);
        }

        // Render landing effects
        for (LandingEffect effect : activeLandingEffects) {
            effect.render(batch);
        }

        batch.setBlendFunction(prevSrcFunc, prevDstFunc);
        batch.setColor(prevColor);

        // Render fog
        if (currentWeather == WeatherType.FOG) {
            renderFog(batch,
                camera.position.x - camera.viewportWidth / 2,
                camera.position.y - camera.viewportHeight / 2,
                camera.viewportWidth,
                camera.viewportHeight);
        }
    }

    private void renderFog(SpriteBatch batch, float x, float y, float width, float height) {
        batch.setColor(1, 1, 1, 0.3f * intensity);
        TextureRegion fogTexture = TextureManager.effects.findRegion("fog");
        if (fogTexture == null) return;

        float fogTextureWidth = fogTexture.getRegionWidth();
        float fogTextureHeight = fogTexture.getRegionHeight();
        float startX = x - (fogOffsetX % fogTextureWidth) - fogTextureWidth;
        float startY = y - (fogOffsetY % fogTextureHeight) - fogTextureHeight;

        for (float posX = startX; posX < x + width; posX += fogTextureWidth) {
            for (float posY = startY; posY < y + height; posY += fogTextureHeight) {
                batch.draw(fogTexture, posX, posY, fogTextureWidth, fogTextureHeight);
            }
        }
        batch.setColor(1, 1, 1, 1);
    }

    private void updateAccumulation(float delta) {
        float accumulationRate;
        if (currentWeather == WeatherType.SNOW || currentWeather == WeatherType.BLIZZARD) {
            accumulationRate = 0.05f * intensity; // Slower accumulation
        } else if (currentWeather == WeatherType.RAIN) {
            accumulationRate = 0.03f * intensity;
        } else if (currentWeather == WeatherType.HEAVY_RAIN || currentWeather == WeatherType.THUNDERSTORM) {
            accumulationRate = 0.08f * intensity;
        } else {
            accumulationRate = -0.02f; // Slower dissipation
        }
        accumulation = MathUtils.clamp(accumulation + accumulationRate * delta, 0, 1);
    }

    public float getIntensity() {
        return intensity;
    }

    public float getAccumulation() {
        return accumulation;
    }

    public void setAccumulation(float accumulation) {
        this.accumulation = accumulation;
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

    /**
     * Enhanced weather particle with pooling support and sway for snow
     */
    public static class WeatherParticle implements Pool.Poolable {
        private TextureRegion texture;
        private float x, y;
        private float velocityX, velocityY;
        private float rotation;
        private float rotationSpeed;
        private float scale;
        private boolean rotating;
        private float swayOffset;
        private float lifetime;

        public void init(float x, float y, float velocityX, float velocityY,
                         TextureRegion texture, boolean rotating, float scale) {
            this.x = x;
            this.y = y;
            this.velocityX = velocityX;
            this.velocityY = velocityY;
            this.texture = texture;
            this.rotating = rotating;
            this.scale = scale;
            this.rotation = 0f;
            this.rotationSpeed = rotating ? (float) (15 + Math.random() * 60) : 0f;
            this.swayOffset = 0f;
            this.lifetime = 0f;
        }

        public void update(float delta) {
            // Basic movement
            x += velocityX * delta;
            y += velocityY * delta;
            lifetime += delta;

            // Add sway for snow particles
            if (texture != null && velocityY < -100 && velocityY > -200) { // Snow particles
                float swayAmount = MathUtils.sin(lifetime * SNOW_SWAY_FREQUENCY + swayOffset) * SNOW_SWAY_AMPLITUDE;
                x += swayAmount * delta;
            }

            if (rotating) {
                rotation += rotationSpeed * delta;
            }
        }

        public void render(SpriteBatch batch) {
            if (texture == null) return;

            if (rotating) {
                batch.draw(texture,
                    x, y,
                    texture.getRegionWidth() / 2f,
                    texture.getRegionHeight() / 2f,
                    texture.getRegionWidth(),
                    texture.getRegionHeight(),
                    scale, scale,
                    rotation);
            } else {
                batch.draw(texture,
                    x, y,
                    texture.getRegionWidth() * scale,
                    texture.getRegionHeight() * scale);
            }
        }

        public boolean isOutOfBounds(float left, float right, float bottom, float top) {
            return x < left || x > right || y < bottom || y > top;
        }

        @Override
        public void reset() {
            x = 0;
            y = 0;
            velocityX = 0;
            velocityY = 0;
            rotation = 0;
            rotationSpeed = 0;
            scale = 1;
            rotating = false;
            texture = null;
            swayOffset = 0;
            lifetime = 0;
        }
    }

    /**
     * Landing effect for rain splashes and snow poofs
     */
    private static class LandingEffect implements Pool.Poolable {
        private float x, y;
        private float lifetime;
        private float alpha;
        private TextureRegion texture;
        private WeatherType type;
        private float scale;

        public void init(float x, float y, WeatherType weatherType) {
            this.x = x;
            this.y = y;
            this.lifetime = 0f;
            this.alpha = 1f;
            this.type = weatherType;

            // Set texture and scale based on weather type
            if (weatherType == WeatherType.RAIN || weatherType == WeatherType.HEAVY_RAIN) {
                this.texture = TextureManager.owFx.findRegion("rain_splash");
                this.scale = 0.5f + MathUtils.random() * 0.3f;
            } else if (weatherType == WeatherType.SNOW || weatherType == WeatherType.BLIZZARD) {
                this.texture = TextureManager.owFx.findRegion("snow_poof");
                this.scale = 0.3f + MathUtils.random() * 0.2f;
            }
        }

        public void update(float delta) {
            lifetime += delta;
            // Fade out over the duration
            alpha = 1f - (lifetime / LANDING_EFFECT_DURATION);

            // Expand slightly for splash effect
            if (type == WeatherType.RAIN || type == WeatherType.HEAVY_RAIN) {
                scale += delta * 0.5f;
            }
        }

        public void render(SpriteBatch batch) {
            if (texture == null || alpha <= 0) return;

            Color prevColor = batch.getColor();
            batch.setColor(1, 1, 1, alpha * 0.6f);

            float width = texture.getRegionWidth() * scale;
            float height = texture.getRegionHeight() * scale;
            batch.draw(texture, x - width/2, y - height/2, width, height);

            batch.setColor(prevColor);
        }

        public boolean isFinished() {
            return lifetime >= LANDING_EFFECT_DURATION;
        }

        @Override
        public void reset() {
            x = 0;
            y = 0;
            lifetime = 0;
            alpha = 1;
            texture = null;
            type = null;
            scale = 1;
        }
    }
}
