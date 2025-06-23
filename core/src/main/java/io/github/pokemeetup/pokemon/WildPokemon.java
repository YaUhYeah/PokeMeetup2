package io.github.pokemeetup.pokemon;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.pokemon.attacks.Move;
import io.github.pokemeetup.pokemon.data.PokemonDatabase;
import io.github.pokemeetup.pokemon.server.PokemonNetworkSyncComponent;
import io.github.pokemeetup.system.Positionable;
import io.github.pokemeetup.system.gameplay.PokemonAnimations;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonAI;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.PokemonSpawnManager;

import java.util.ArrayList;
import java.util.List;

import static io.github.pokemeetup.system.gameplay.PokemonAnimations.IDLE_BOUNCE_DURATION;

public class WildPokemon extends Pokemon implements Positionable {
    private static final float SCALE = 2.0f;
    private static final float TILE_SIZE = 32f;
    private static final float MOVEMENT_DURATION = 0.75f;
    // FIX: Adjusted collision scale to be more accurate, similar to the player's.
    private static final float COLLISION_SCALE = 0.6f;
    private static final float COLLISION_HEIGHT_SCALE = 0.4f;
    private static final float FRAME_WIDTH = World.TILE_SIZE;
    private static final float FRAME_HEIGHT = World.TILE_SIZE;
    private static final float IDLE_BOUNCE_HEIGHT = 2f;

    // Enhanced AI and behavior
    private PokemonAI enhancedAI;
    private Object legacyAI; // Keep for backward compatibility

    // Core properties
    private final PokemonAnimations animations;
    private Rectangle boundingBox;
    private PokemonNetworkSyncComponent networkSync;
    private boolean isNetworkControlled = false;

    // Position and movement
    private float width;
    private float height;
    private float pixelX;
    private float pixelY;
    private float x;
    private float y;
    private boolean isMoving;
    private Vector2 startPosition;
    private Vector2 targetPosition;
    private float movementProgress;
    private float currentMoveTime = 0f;
    private boolean isInterpolating = false;
    private float lastUpdateX;
    private float lastUpdateY;

    // Game state
    private World world;
    private long spawnTime;
    private String direction;
    private boolean isExpired = false;
    private boolean isAddedToParty = false;
    private boolean isDespawning = false;
    private PokemonDespawnAnimation despawnAnimation;
    private float idleAnimationTime = 0;
    private boolean isIdling = false;

    // Constructors
    public WildPokemon(String name, int level) {
        super(name, level);
        this.pixelX = 0;
        this.pixelY = 0;
        this.networkSync = new PokemonNetworkSyncComponent(this);
        this.isNetworkControlled = false;
        this.x = 0;
        this.y = 0;
        this.startPosition = new Vector2(0, 0);
        this.targetPosition = new Vector2(0, 0);
        this.direction = "down";
        this.currentMoveTime = 0;
        this.width = 0;
        this.height = 0;
        this.boundingBox = new Rectangle(0, 0, 0, 0);
        this.animations = null;

        initializePokemonData(name, level);
    }

    public WildPokemon(String name, int level, int pixelX, int pixelY, boolean noTexture) {
        super(noTexture);

        // Snap position to tile grid
        int tileX = MathUtils.floor((float) pixelX / World.TILE_SIZE);
        int tileY = MathUtils.floor((float) pixelY / World.TILE_SIZE);
        // FIX: Align position to the bottom-center of the tile, just like the player.
        this.pixelX = tileX * World.TILE_SIZE + (World.TILE_SIZE / 2f);
        this.pixelY = tileY * World.TILE_SIZE;
        this.x = this.pixelX;
        this.y = this.pixelY;

        this.level = level;
        this.name = name;
        this.isNetworkControlled = true;
        this.animations = null;

        setSpawnTime(System.currentTimeMillis() / 1000L);
        initializePokemonData(name, level);
        initializeBoundingBox();

        this.direction = "down";
        this.isMoving = false;
    }

    public WildPokemon(String name, int level, int pixelX, int pixelY, TextureRegion overworldSprite) {
        super(name, level);
        // FIX: Align position to the bottom-center of the tile.
        this.pixelX = pixelX + (World.TILE_SIZE / 2f);
        this.pixelY = pixelY;
        this.x = this.pixelX;
        this.y = this.pixelY;
        this.networkSync = new PokemonNetworkSyncComponent(this);
        this.isNetworkControlled = false;
        this.startPosition = new Vector2(this.x, this.y);
        this.targetPosition = new Vector2(this.x, this.y);
        this.direction = "down";
        this.animations = new PokemonAnimations(overworldSprite);
        this.width = World.TILE_SIZE * SCALE;
        this.height = World.TILE_SIZE * SCALE;

        setSpawnTime((long) (System.currentTimeMillis() / 1000f));
        initializePokemonData(name, level);

        // Enhanced AI will be set by the spawn manager
    }

    private void initializePokemonData(String name, int level) {
        PokemonDatabase.PokemonTemplate template = PokemonDatabase.getTemplate(name);
        if (template != null) {
            setPrimaryType(template.primaryType);
            setSecondaryType(template.secondaryType);

            if (animations != null) {
                this.width *= template.width;
                this.height *= template.height;
            }

            // Calculate stats
            int baseHp = template.baseStats.baseHp;
            int baseAtk = template.baseStats.baseAttack;
            int baseDef = template.baseStats.baseDefense;
            int baseSpAtk = template.baseStats.baseSpAtk;
            int baseSpDef = template.baseStats.baseSpDef;
            int baseSpd = template.baseStats.baseSpeed;

            Stats stats = getStats();
            boolean isHpStat = true;
            stats.setHp(calculateStat(baseHp, stats.ivs[0], stats.evs[0], level, isHpStat));
            stats.setAttack(calculateStat(baseAtk, stats.ivs[1], stats.evs[1], level, false));
            stats.setDefense(calculateStat(baseDef, stats.ivs[2], stats.evs[2], level, false));
            stats.setSpecialAttack(calculateStat(baseSpAtk, stats.ivs[3], stats.evs[3], level, false));
            stats.setSpecialDefense(calculateStat(baseSpDef, stats.ivs[4], stats.evs[4], level, false));
            stats.setSpeed(calculateStat(baseSpd, stats.ivs[5], stats.evs[5], level, false));

            setCurrentHp(stats.getHp());

            if (template.moves != null && !template.moves.isEmpty()) {
                List<Move> moves = PokemonDatabase.getMovesForLevel(template.moves, level);
                setMoves(moves);
            }
        }
    }

    private void initializeBoundingBox() {
        // FIX: Collision box is now sized relative to a tile and centered on the Pokemon's position.
        float collisionWidth = World.TILE_SIZE * COLLISION_SCALE;
        float collisionHeight = World.TILE_SIZE * COLLISION_HEIGHT_SCALE;
        float bboxX = this.x - collisionWidth / 2f;
        float bboxY = this.y;
        this.boundingBox = new Rectangle(bboxX, bboxY, collisionWidth, collisionHeight);
    }

    private int calculateStat(int base, int iv, int ev, int level, boolean isHp) {
        if (isHp) {
            return ((2 * base + iv + ev / 4) * level / 100) + level + 10;
        } else {
            return ((2 * base + iv + ev / 4) * level / 100) + 5;
        }
    }

    // AI Management
    public void setAi(Object ai) {
        if (ai instanceof PokemonAI) {
            this.enhancedAI = (PokemonAI) ai;
            this.legacyAI = null;
        } else {
            this.legacyAI = ai;
            this.enhancedAI = null;
        }
    }  public Rectangle getBoundingBox() {
        return new Rectangle(
            (float)getTileX() * World.TILE_SIZE,
            (float)getTileY() * World.TILE_SIZE,
            World.TILE_SIZE,
            World.TILE_SIZE
        );
    }


    public Object getAi() {
        return enhancedAI != null ? enhancedAI : legacyAI;
    }

    public PokemonAI getEnhancedAI() {
        return enhancedAI;
    }

    // Network and synchronization
    public void setNetworkControlled(boolean networkControlled) {
        this.isNetworkControlled = networkControlled;
    }

    public void applyNetworkUpdate(float x, float y, String direction, boolean isMoving, long timestamp) {
        if (networkSync != null) {
            networkSync.processNetworkUpdate(x, y, direction, isMoving, timestamp);
        }
    }

    public PokemonNetworkSyncComponent getNetworkSync() {
        return networkSync;
    }

    // Update method with enhanced AI support
    public void update(float delta, World world) {
        if (world == null) return;

        if (isDespawning) {
            if (despawnAnimation != null && despawnAnimation.update(delta)) {
                isExpired = true;
            }
            return;
        }

        // Handle network synchronization first
        if (isNetworkControlled && networkSync != null) {
            networkSync.update(delta);

            if (networkSync.isInterpolating()) {
                if (animations != null) {
                    animations.update(delta);
                }
                return;
            }
        }

        // Use enhanced AI if available, otherwise fall back to legacy
        if (!isNetworkControlled) {
            if (enhancedAI != null) {
                enhancedAI.update(delta, world);
            } else if (legacyAI != null) {
                // Legacy AI update would go here
                // For now, we'll use enhanced AI as the default
            }
        }

        // Update movement and animations
        if (isMoving) {
            updateMovement(delta);
            idleAnimationTime = 0;
        } else {
            updateIdleAnimation(delta);
        }

        if (animations != null) {
            animations.update(delta);
            if (isMoving != animations.isMoving()) {
                if (isMoving) {
                    animations.startMoving(direction);
                } else {
                    animations.stopMoving();
                }
            }
        }

        updateWaterSoundTimer(delta);
        updateBoundingBox();
    }

    private void updateIdleAnimation(float delta) {
        idleAnimationTime = (idleAnimationTime + delta) % IDLE_BOUNCE_DURATION;
    }

    private void updateMovement(float delta) {
        if (!isMoving || !isInterpolating) return;

        currentMoveTime += delta;
        movementProgress = Math.min(currentMoveTime / MOVEMENT_DURATION, 1.0f);

        // Smooth interpolation
        float smoothProgress = calculateSmoothProgress(movementProgress);

        float newX = MathUtils.lerp(startPosition.x, targetPosition.x, smoothProgress);
        float newY = MathUtils.lerp(startPosition.y, targetPosition.y, smoothProgress);

        if (newX != lastUpdateX || newY != lastUpdateY) {
            setX(newX);
            setY(newY);
            lastUpdateX = newX;
            lastUpdateY = newY;
            updateBoundingBox();
        }

        if (movementProgress >= 1.0f) {
            completeMovement();
        }
    }

    private float calculateSmoothProgress(float progress) {
        return progress * progress * (3 - 2 * progress);
    }

    private void completeMovement() {
        isInterpolating = false;
        isMoving = false;
        currentMoveTime = 0f;

        setX(targetPosition.x);
        setY(targetPosition.y);

        if (animations != null) {
            animations.stopMoving();
        }

        updateBoundingBox();
    }

    public void moveToTile(int targetTileX, int targetTileY, String newDirection) {
        if (!isMoving) {
            startPosition.set(x, y);
            lastUpdateX = x;
            lastUpdateY = y;

            // FIX: Calculate target pixel coordinates to be bottom-center of the tile.
            float targetPixelX = targetTileX * World.TILE_SIZE + (World.TILE_SIZE / 2f);
            float targetPixelY = targetTileY * World.TILE_SIZE;
            targetPosition.set(targetPixelX, targetPixelY);

            this.direction = newDirection;
            this.isMoving = true;
            this.isInterpolating = true;
            this.currentMoveTime = 0f;
            this.movementProgress = 0f;

            if (animations != null) {
                animations.startMoving(direction);
            }
        }
    }


    public void updateBoundingBox() {
        if (boundingBox != null) {
            // FIX: Update bounding box to be centered horizontally on the Pokemon's position.
            float collisionWidth = World.TILE_SIZE * COLLISION_SCALE;
            float collisionHeight = World.TILE_SIZE * COLLISION_HEIGHT_SCALE;
            float newX = x - collisionWidth / 2f;
            float newY = y;
            boundingBox.setPosition(newX, newY);
            boundingBox.setSize(collisionWidth, collisionHeight);
        }
    }
    private boolean wasOnWater = false;
    private float waterSoundTimer = 0f;
    @Override
    public boolean wasOnWater() {
        return wasOnWater;
    }

    @Override
    public void setWasOnWater(boolean onWater) {
        this.wasOnWater = onWater;
    }

    @Override
    public float getWaterSoundTimer() {
        return waterSoundTimer;
    }

    @Override
    public void setWaterSoundTimer(float timer) {
        this.waterSoundTimer = timer;
    }

    @Override
    public void updateWaterSoundTimer(float delta) {
        if (this.waterSoundTimer > 0) {
            this.waterSoundTimer -= delta;
        }
    }

    public int getTileX() {
        return (int) (x / TILE_SIZE);
    }

    public int getTileY() {
        return (int) (y / TILE_SIZE);
    }

    @Override
    public void render(SpriteBatch batch) {
        if (isDespawning) {
            if (despawnAnimation != null) {
                despawnAnimation.render(batch, getCurrentFrame());
            }
            return;
        }

        TextureRegion frame = getCurrentFrame();
        if (frame != null) {
            float renderWidth = this.width;
            float renderHeight = this.height;
            // FIX: The render logic now correctly centers the sprite based on the new position anchor.
            float offsetX = renderWidth / 2f;
            float renderY = y;

            // Apply idle bounce animation
            if (!isMoving) {
                float bounceOffset = IDLE_BOUNCE_HEIGHT *
                    MathUtils.sin(idleAnimationTime * MathUtils.PI2 / IDLE_BOUNCE_DURATION);
                renderY += bounceOffset;
            }

            // Apply world lighting
            Color originalColor = batch.getColor().cpy();
            if (world != null) {
                Color baseColor = world.getCurrentWorldColor();
                int tileX = (int) (x / World.TILE_SIZE);
                int tileY = (int) (y / World.TILE_SIZE);
                Vector2 tilePos = new Vector2(tileX, tileY);
                Float lightLevel = world.getLightLevelAtTile(tilePos);
                if (lightLevel != null && lightLevel > 0) {
                    Color lightColor = new Color(1f, 0.9f, 0.7f, 1f);
                    baseColor = baseColor.cpy().lerp(lightColor, lightLevel);
                }
                batch.setColor(baseColor);
            }

            batch.draw(frame, x - offsetX, renderY, renderWidth, renderHeight);
            batch.setColor(originalColor);
        }
    }
    public TextureRegion getCurrentFrame() {
        if (animations != null) {
            return animations.getCurrentFrame(direction, isMoving);
        }
        return null;
    }

    // Expiration and despawning
    public boolean isExpired() {
        if (isExpired) return true;
        float currentTime = System.currentTimeMillis() / 1000f;
        return currentTime - spawnTime > PokemonSpawnManager.POKEMON_DESPAWN_TIME;
    }

    public boolean isDespawning() {
        return isDespawning;
    }

    public void startDespawnAnimation() {
        if (!isDespawning) {
            isDespawning = true;
            despawnAnimation = new PokemonDespawnAnimation(getX(), getY(), FRAME_WIDTH, FRAME_HEIGHT);
        }
    }

    // Getters and setters
    public World getWorld() { return world; }
    public void setWorld(World world) { this.world = world; }

    public float getX() { return x; }


    public float getY() { return y; }
    public void setX(float x) {
        this.pixelX = x;
        this.x = x;
    }

    public void setY(float y) {
        this.pixelY = y;
        this.y = y;
    }

    @Override
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    @Override
    public boolean isMoving() { return isMoving; }

    @Override
    public void setCharacterType(String characterType) {

    }

    public void setMoving(boolean moving) { this.isMoving = moving; }

    public boolean isAddedToParty() { return isAddedToParty; }
    public void setAddedToParty(boolean addedToParty) { isAddedToParty = addedToParty; }


    @Override
    public PokemonAnimations getAnimations() { return animations; }

    public float getWidth() { return width; }
    public float getHeight() { return height; }

    public void setSpawnTime(long spawnTime) { this.spawnTime = spawnTime; }
}
