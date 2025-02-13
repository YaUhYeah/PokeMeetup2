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
    private static final float MOVEMENT_DURATION = 0.75f;
    private static final float COLLISION_SCALE = 0.8f;

    private static final float FRAME_WIDTH = World.TILE_SIZE;
    private static final float FRAME_HEIGHT = World.TILE_SIZE;
    private static final float IDLE_BOUNCE_HEIGHT = 2f;
    private final PokemonAnimations animations;
    private float width;
    private float height;
    private final Rectangle boundingBox;
    private float pixelX;
    private float pixelY;
    private World world;
    private boolean isMoving;
    private Vector2 startPosition;
    private Vector2 targetPosition;
    private float movementProgress;
    private PokemonAI ai;
    private float x;
    private float y;
    private long spawnTime;
    private String direction;
    private boolean isExpired = false;
    private boolean isAddedToParty = false;
    private boolean isDespawning = false;
    private PokemonDespawnAnimation despawnAnimation;
    private float idleAnimationTime = 0;
    private boolean isIdling = false;
    private float currentMoveTime = 0f;
    private boolean isInterpolating = false;
    private float lastUpdateX;
    private float lastUpdateY;
    public WildPokemon(String name, int level) {
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
        this.animations = null;  PokemonDatabase.PokemonTemplate template = PokemonDatabase.getTemplate(name);
        if (template != null && template.moves != null && !template.moves.isEmpty()) {
            // Use the helper that returns the moves for the given level
            List<Move> moves = PokemonDatabase.getMovesForLevel(template.moves, level);
            setMoves(moves);
        }
    }
    public WildPokemon(String name, int level, int pixelX, int pixelY, boolean noTexture) {
        super(noTexture);
        // --- SNAP THE POSITION TO THE TILE GRID ---
        // Convert the incoming pixel coordinates to tile coordinates.
        int tileX = MathUtils.floor((float) pixelX / World.TILE_SIZE);
        int tileY = MathUtils.floor((float) pixelY / World.TILE_SIZE);
        // Now snap: assign x and y to the top–left corner of that tile.
        this.pixelX = tileX * World.TILE_SIZE;
        this.level = level;
        this.pixelY = tileY * World.TILE_SIZE;
        this.x = this.pixelX;
        this.y = this.pixelY;
        // ----------------------------------------

        this.name = name; // Ensure the Pokémon's name is set.
        // We skip sprite/animation initialization since this is a server–logic (noTexture) instance.
        this.animations = null;

        // Set the spawn time in seconds.
        setSpawnTime(System.currentTimeMillis() / 1000L);

        // If a template exists, initialize stats and moves.
        PokemonDatabase.PokemonTemplate template = PokemonDatabase.getTemplate(name);
        if (template != null) {
            setPrimaryType(template.primaryType);
            setSecondaryType(template.secondaryType);

            // Calculate base stats from the template.
            int baseHp    = template.baseStats.baseHp;
            int baseAtk   = template.baseStats.baseAttack;
            int baseDef   = template.baseStats.baseDefense;
            int baseSpAtk = template.baseStats.baseSpAtk;
            int baseSpDef = template.baseStats.baseSpDef;
            int baseSpd   = template.baseStats.baseSpeed;

            Stats stats = getStats();
            stats.setHp(calculateStat(baseHp, stats.ivs[0], stats.evs[0]));
            stats.setAttack(calculateStat(baseAtk, stats.ivs[1], stats.evs[1]));
            stats.setDefense(calculateStat(baseDef, stats.ivs[2], stats.evs[2]));
            stats.setSpecialAttack(calculateStat(baseSpAtk, stats.ivs[3], stats.evs[3]));
            stats.setSpecialDefense(calculateStat(baseSpDef, stats.ivs[4], stats.evs[4]));
            stats.setSpeed(calculateStat(baseSpd, stats.ivs[5], stats.evs[5]));

            // Set current HP to the maximum.
            setCurrentHp(stats.getHp());
            if (template.moves != null && !template.moves.isEmpty()) {
                // Use the helper that returns the moves for the given level
                List<Move> moves = PokemonDatabase.getMovesForLevel(template.moves, level);
                setMoves(moves);
            }
        }

        // --- Initialize the collision bounding box ---
        // Use the World.TILE_SIZE and collision scale to compute width and height.
        float collisionWidth = World.TILE_SIZE * COLLISION_SCALE;   // For example, 32 * 0.8 = 25.6
        float collisionHeight = World.TILE_SIZE * COLLISION_SCALE;
        // Center the collision box within the tile.
        float bboxX = this.pixelX + (World.TILE_SIZE - collisionWidth) / 2f;
        float bboxY = this.pixelY + (World.TILE_SIZE - collisionHeight) / 2f;
        this.boundingBox = new Rectangle(bboxX, bboxY, collisionWidth, collisionHeight);
        // ----------------------------------------------

        this.direction = "down";
        this.isMoving = false;
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
            this.width *= template.width;
            this.height *= template.height;
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
            if (template != null && template.moves != null && !template.moves.isEmpty()) {
                // Use the helper that returns the moves for the given level
                List<Move> moves = PokemonDatabase.getMovesForLevel(template.moves, level);
                setMoves(moves);
            }
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
            // Align to the tile grid
            float newX = x + (World.TILE_SIZE - collisionWidth) / 2f;
            float newY = y + (World.TILE_SIZE - collisionHeight) / 2f;
            boundingBox.setPosition(newX, newY);
            boundingBox.setSize(collisionWidth, collisionHeight);
        }
    }


    public TextureRegion getCurrentFrame() {
        if (animations != null) {
            return animations.getCurrentFrame(direction, isMoving);
        }
        return null;
    }




    public void update(float delta, World world) {
        if (world == null) return;
        if (isDespawning) {
            if (despawnAnimation != null && despawnAnimation.update(delta)) {
                isExpired = true;
            }
            return;
        }

        if (ai != null) {
            ai.update(delta, world);
        }

        if (isMoving) {
            updateMovement(delta);
            idleAnimationTime = 0; // reset idle bounce when moving
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
        updateBoundingBox();
    }


    private void updateIdleAnimation(float delta) {
        idleAnimationTime = (idleAnimationTime + delta) % IDLE_BOUNCE_DURATION;
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
            float offsetX = (renderWidth - World.TILE_SIZE) / 2f;
            float offsetY = (renderHeight - World.TILE_SIZE) / 2f;
            float renderY = y;

            // When idle, apply a smooth sine–wave bounce.
            if (!isMoving) {
                float bounceOffset = IDLE_BOUNCE_HEIGHT *
                    MathUtils.sin(idleAnimationTime * MathUtils.PI2 / IDLE_BOUNCE_DURATION);
                renderY += bounceOffset;
            }

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
            batch.draw(frame, x - offsetX, renderY - offsetY, renderWidth, renderHeight);
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
            // Use the Pokémon’s current X/Y and frame dimensions (using your FRAME_WIDTH/HEIGHT constants)
            despawnAnimation = new PokemonDespawnAnimation(getX(), getY(), WildPokemon.FRAME_WIDTH, WildPokemon.FRAME_HEIGHT);
        }
    }
}
