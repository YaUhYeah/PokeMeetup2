package io.github.pokemeetup.pokemon;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.pokemon.attacks.Move;
import io.github.pokemeetup.pokemon.data.PokemonDatabase;
import io.github.pokemeetup.screens.GameScreen;
import io.github.pokemeetup.system.gameplay.PokemonAnimations;
import io.github.pokemeetup.system.gameplay.overworld.PokemonSpawnManager;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonAI;
import io.github.pokemeetup.utils.GameLogger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import static io.github.pokemeetup.system.gameplay.PokemonAnimations.IDLE_BOUNCE_DURATION;


public class WildPokemon extends Pokemon {
    private static final float SCALE = 2.0f;
    private static final float TILE_SIZE = 32f;
    private static final float MOVEMENT_DURATION = 0.75f;  // Slower, smoother movement
    private static final float RENDER_SCALE = 1.5f;
    private static final float COLLISION_SCALE = 0.8f;

    private static final float FRAME_WIDTH = World.TILE_SIZE;
    private static final float FRAME_HEIGHT = World.TILE_SIZE;
    private static final float IDLE_BOUNCE_HEIGHT = 2f;
    private final PokemonAnimations animations;
    private final float width;
    private final float height;
    private final Rectangle boundingBox;
    private float pixelX;
    private float pixelY;
    private World world;
    private int gridX;
    private boolean isMoving;
    private Vector2 startPosition;
    private Vector2 targetPosition;
    private float movementProgress;
    private PokemonAI ai;
    private int gridY;
    private float x;
    private float y;
    private long spawnTime;
    private String direction;
    private boolean isExpired = false;
    private boolean isAddedToParty = false;
    private boolean isDespawning = false;
    private float elapsedMovementTime = 0f;
    private PokemonDespawnAnimation despawnAnimation;
    private float idleTimer = 0f;
    private float idleAnimationTime = 0;
    private boolean isIdling = false;
    private float currentMoveTime = 0f;
    private boolean isInterpolating = false;
    private float lastUpdateX;
    private float lastUpdateY;
    public WildPokemon(String name, int level) {
        // Initialize with default/dummy values
        super(name, level);
        this.pixelX = 0;
        this.pixelY = 0;
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
    }

    public WildPokemon(String name, int level, int pixelX, int pixelY, TextureRegion overworldSprite) {
        super(name, level);
        this.pixelX = pixelX;
        this.pixelY = pixelY;
        this.x = pixelX;
        this.y = pixelY;
        this.startPosition = new Vector2(pixelX, pixelY);
        this.targetPosition = new Vector2(pixelX, pixelY);
        this.direction = "down";
        this.animations = new PokemonAnimations(overworldSprite);
        this.width = World.TILE_SIZE * SCALE;
        this.height = World.TILE_SIZE * SCALE;
        float collisionWidth = TILE_SIZE * COLLISION_SCALE;
        float collisionHeight = TILE_SIZE * COLLISION_SCALE;
        this.boundingBox = new Rectangle(
            this.pixelX + (TILE_SIZE - collisionWidth) / 2f,
            this.pixelY + (height - collisionHeight) / 2f,
            collisionWidth,
            collisionHeight
        );
        setSpawnTime((long) (System.currentTimeMillis() / 1000f));
        PokemonDatabase.PokemonTemplate template = PokemonDatabase.getTemplate(name);
        if (template != null) {
            // Set base stats from template
            this.setPrimaryType(template.primaryType);
            this.setSecondaryType(template.secondaryType);

            // Calculate and set stats based on level
            int baseHp = template.baseStats.baseHp;
            int baseAtk = template.baseStats.baseAttack;
            int baseDef = template.baseStats.baseDefense;
            int baseSpAtk = template.baseStats.baseSpAtk;
            int baseSpDef = template.baseStats.baseSpDef;
            int baseSpd = template.baseStats.baseSpeed;

            Stats stats = this.getStats();
            stats.setHp(calculateStat(baseHp, stats.ivs[0], stats.evs[0], level, true));
            stats.setAttack(calculateStat(baseAtk, stats.ivs[1], stats.evs[1], level, false));
            stats.setDefense(calculateStat(baseDef, stats.ivs[2], stats.evs[2], level, false));
            stats.setSpecialAttack(calculateStat(baseSpAtk, stats.ivs[3], stats.evs[3], level, false));
            stats.setSpecialDefense(calculateStat(baseSpDef, stats.ivs[4], stats.evs[4], level, false));
            stats.setSpeed(calculateStat(baseSpd, stats.ivs[5], stats.evs[5], level, false));

            // Set current HP to max HP
            this.setCurrentHp(stats.getHp());

            // Initialize moves based on level
            initializeMovesForLevel(template.moves, level);
        }

        this.ai = new PokemonAI(this);
    }
    private void initializeMovesForLevel(List<PokemonDatabase.MoveEntry> availableMoves, int level) {
        List<Move> learnedMoves = new ArrayList<>();

        // Sort moves by level requirement
        availableMoves.sort((a, b) -> Integer.compare(b.level, a.level));

        // Get up to 4 moves that the Pokemon can know at its current level
        for (PokemonDatabase.MoveEntry entry : availableMoves) {
            if (entry.level <= level && learnedMoves.size() < 4) {
                Move move = PokemonDatabase.getMoveByName(entry.name);
                if (move != null) {
                    learnedMoves.add(PokemonDatabase.cloneMove(move));
                }
            }
        }

        this.setMoves(learnedMoves);
    }


    public World getWorld() {
        return world;
    }

    public void setWorld(World world) {
        this.world = world;
    }

    public void updateFromNetworkUpdate(NetworkProtocol.PokemonUpdate update) {
        if (update == null) {
            GameLogger.error("Received null PokemonUpdate in updateFromNetworkUpdate");
            return;
        }

        // Update position
        this.x = update.x * World.TILE_SIZE + (World.TILE_SIZE - this.width) / 2f;
        this.y = update.y * World.TILE_SIZE + (World.TILE_SIZE - this.height) / 2f;
        updateBoundingBox();

        // Update level if it has changed
        if (this.getLevel() != update.level) {
            this.setLevel(update.level);
            GameLogger.info(getName() + " leveled up to " + this.getLevel());
        }

        // Update other attributes as needed
        // Assuming NetworkProtocol.PokemonUpdate has fields like currentHp, statusEffects, etc.

        if (update.currentHp != -1) { // Assuming -1 signifies no update
            setCurrentHp(update.currentHp);
            GameLogger.info(getName() + " HP updated to " + update.currentHp);
        }

    }


    private int calculateStat(int base, int iv, int ev, int level, boolean isHp) {
        if (isHp) {
            return ((2 * base + iv + ev / 4) * level / 100) + level + 10;
        } else {
            return ((2 * base + iv + ev / 4) * level / 100) + 5;
        }
    }
    public boolean isAddedToParty() {
        return isAddedToParty;
    }

    public void setAddedToParty(boolean addedToParty) {
        isAddedToParty = addedToParty;
    }

    public Rectangle getBoundingBox() {
        return boundingBox;
    }

    @Override
    public PokemonAnimations getAnimations() {
        return animations;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }


    public void setSpawnTime(long spawnTime) {
        this.spawnTime = spawnTime;
    }

    @Override
    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    @Override
    public boolean isMoving() {
        return isMoving;
    }

    public void setMoving(boolean moving) {
        this.isMoving = moving;
    }

    public void updateBoundingBox() {
        if (boundingBox != null) {
            float collisionWidth = World.TILE_SIZE * COLLISION_SCALE;
            float collisionHeight = World.TILE_SIZE * COLLISION_SCALE;

            // Center the collision box regardless of render size
            boundingBox.setPosition(
                x + (World.TILE_SIZE - collisionWidth) / 2f,
                y + (World.TILE_SIZE - collisionHeight) / 2f
            );
            boundingBox.setSize(collisionWidth, collisionHeight);
        }
    }

    public TextureRegion getCurrentFrame() {
        if (animations != null) {
            return animations.getCurrentFrame(direction, isMoving, Gdx.graphics.getDeltaTime());
        }
        return null;
    }

    private void updateIdleAnimation(float delta) {
        idleTimer += delta;

        // Start new idle animation
        if (!isIdling && idleTimer >= MathUtils.random(2f, 4f)) {
            isIdling = true;
            idleTimer = 0;
            idleAnimationTime = 0;
        }

        // Update current idle animation
        if (isIdling) {
            idleAnimationTime += delta;
            if (idleAnimationTime >= IDLE_BOUNCE_DURATION) {
                isIdling = false;
                idleAnimationTime = 0;
            }
        }
    }

    public void update(float delta, World world) {
        if (world == null) return;if (isDespawning) {
            if (despawnAnimation != null) {
                // Update the despawn animation.
                // The update method returns true when the animation is complete.
                if (despawnAnimation.update(delta)) {
                    // Once the despawn animation has finished,
                    // mark the Pokémon as expired so that it can be removed from the world.
                    isExpired = true;
                }
            }
            // Skip all other update logic when despawning.
            return;
        }

        // Update AI first
        if (ai != null) {
            ai.update(delta, world);
        }

        // Update movement and animations
        if (isMoving) {
            updateMovement(delta);
            isIdling = false;
            idleAnimationTime = 0;
        } else {
            updateIdleAnimation(delta);
        }

        // Update animations
        if (animations != null) {
            animations.update(delta);

            // Sync animation state with movement
            if (isMoving != animations.isMoving()) {
                if (isMoving) {
                    animations.startMoving(direction);
                } else {
                    animations.stopMoving();
                }
            }
        }
    }

    private float calculateSmoothProgress(float progress) {
        return progress * progress * (3 - 2 * progress);
    }

    private void completeMovement() {
        isInterpolating = false;
        isMoving = false;
        currentMoveTime = 0f;

        // Ensure final position is exactly on target
        setX(targetPosition.x);
        setY(targetPosition.y);

        // Stop walking animation
        if (animations != null) {
            animations.stopMoving();
        }

        updateBoundingBox();

    }
    private void updateMovement(float delta) {
        if (!isMoving || !isInterpolating) return;

        currentMoveTime += delta;
        movementProgress = Math.min(currentMoveTime / MOVEMENT_DURATION, 1.0f);

        // Use smooth step interpolation
        float smoothProgress = calculateSmoothProgress(movementProgress);

        // Calculate new position
        float newX = MathUtils.lerp(startPosition.x, targetPosition.x, smoothProgress);
        float newY = MathUtils.lerp(startPosition.y, targetPosition.y, smoothProgress);

        // Only update if position actually changed
        if (newX != lastUpdateX || newY != lastUpdateY) {
            setX(newX);
            setY(newY);
            lastUpdateX = newX;
            lastUpdateY = newY;
            updateBoundingBox();
        }

        // Check if movement is complete
        if (movementProgress >= 1.0f) {
            completeMovement();
        }
    }

    public void moveToTile(int targetTileX, int targetTileY, String newDirection) {
        if (!isMoving) {
            // Store current position as start
            startPosition.set(x, y);
            lastUpdateX = x;
            lastUpdateY = y;

            // Calculate target position in pixels
            float targetPixelX = targetTileX * World.TILE_SIZE;
            float targetPixelY = targetTileY * World.TILE_SIZE;
            targetPosition.set(targetPixelX, targetPixelY);

            // Set movement state
            this.direction = newDirection;
            this.isMoving = true;
            this.isInterpolating = true;
            this.currentMoveTime = 0f;

            // Calculate actual distance for movement duration
            float distance = Vector2.dst(startPosition.x, startPosition.y,
                targetPosition.x, targetPosition.y);

            // Adjust movement duration based on distance
            this.movementProgress = 0f;

            // Start walking animation
            animations.startMoving(direction);

        }
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.pixelX = x;
        this.x = x;
        updateBoundingBox();
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.pixelY = y;
        this.y = y;
        updateBoundingBox();
    }

    public PokemonAI getAi() {
        return ai;
    }

    public void render(SpriteBatch batch) {
        if (isDespawning) {
            if (despawnAnimation != null) {
                despawnAnimation.render(batch, getCurrentFrame(), FRAME_WIDTH, FRAME_HEIGHT);
            }
            return;
        }

        TextureRegion frame = getCurrentFrame();
        if (frame != null) {
            float renderX = x;
            float renderY = y;

            if (!isMoving && isIdling) {
                float bounceOffset = IDLE_BOUNCE_HEIGHT *
                    MathUtils.sin(idleAnimationTime * MathUtils.PI2 / IDLE_BOUNCE_DURATION);
                renderY += bounceOffset;
            }

            // Scale and center the sprite
            float width = FRAME_WIDTH * RENDER_SCALE;
            float height = FRAME_HEIGHT * RENDER_SCALE;
            float offsetX = (width - FRAME_WIDTH) / 2f;
            float offsetY = (height - FRAME_HEIGHT) / 2f;

            // Save original batch color
            Color originalColor = batch.getColor().cpy();

            if (world != null) {
                // Get base color from the world
                Color baseColor = world.getCurrentWorldColor();

                // Convert position to tile coordinates
                int tileX = (int) (x / World.TILE_SIZE);
                int tileY = (int) (y / World.TILE_SIZE);
                Vector2 tilePos = new Vector2(tileX, tileY);

                // Get light level at this position
                Float lightLevel = world.getLightLevelAtTile(tilePos);
                if (lightLevel != null && lightLevel > 0) {
                    Color lightColor = new Color(1f, 0.9f, 0.7f, 1f);
                    baseColor = baseColor.cpy().lerp(lightColor, lightLevel);
                }

                // Set the adjusted color
                batch.setColor(baseColor);
            }

            // Draw with smooth interpolation
            batch.draw(frame,
                renderX - offsetX,
                renderY - offsetY,
                width,
                height);

            // Restore original batch color
            batch.setColor(originalColor);
        }
    }


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
            despawnAnimation = new PokemonDespawnAnimation(getX(), getY());
        }
    }
}
