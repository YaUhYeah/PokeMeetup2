package io.github.pokemeetup.system.gameplay.overworld.entityai;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;

import java.util.Collection;

public class PokemonAI {

    private static final float DECISION_INTERVAL = 1.0f;
    // Shorter idle durations so decisions happen more frequently.
    private static final float IDLE_MIN_DURATION = 0.25f;
    private static final float IDLE_MAX_DURATION = 0.75f;
    private static final float BASE_MOVEMENT_CHANCE = 0.85f;
    private static final float BASE_FLEE_RANGE = 150f;
    private static final float MIN_DISTANCE_TO_OTHERS = World.TILE_SIZE * 2;

    // Personality modifier enums:
    public enum PokemonPersonality {
        TIMID,    // Flees earlier, less likely to move (more skittish)
        CURIOUS,  // May occasionally approach the player
        LAZY      // Moves infrequently
    }

    private final WildPokemon pokemon;
    private boolean isPaused = false;
    private float decisionTimer = 0;
    private float stateTimer = 0;
    private float idleDuration = 0;
    // NEW: Field to hold our roaming/flee target position
    private Vector2 targetPosition = new Vector2();
    // Replace the old MOVING state with a new ROAMING state.
    private AIState currentState = AIState.IDLE;
    private final PokemonPersonality personality;

    public PokemonAI(WildPokemon pokemon) {
        this.pokemon = pokemon;
        // Randomly assign a personality.
        PokemonPersonality[] options = PokemonPersonality.values();
        this.personality = options[MathUtils.random(options.length - 1)];
        GameLogger.info("Assigned personality " + personality + " to " + pokemon.getName());
    }

    public void setPaused(boolean paused) {
        this.isPaused = paused;
    }

    public void update(float delta, World world) {
        if (world == null || pokemon == null) {
            GameLogger.error("AI Update skipped - null world or pokemon");
            return;
        }
        if (isPaused) return;

        // Check for species-specific actions (e.g. Abra teleport, Diglett underground)
        handleSpecialTraits(world);

        stateTimer += delta;
        decisionTimer += delta;

        Player player = GameContext.get().getPlayer();
        if (player != null) {
            float fleeRange = BASE_FLEE_RANGE;
            if (personality == PokemonPersonality.TIMID) {
                fleeRange *= 0.8f;
            }
            // Convert player tile coords to pixels.
            float playerX = player.getX() * World.TILE_SIZE;
            float playerY = player.getY() * World.TILE_SIZE;
            float distToPlayer = Vector2.dst(pokemon.getX(), pokemon.getY(), playerX, playerY);

            // If too close, switch to fleeing state.
            if (distToPlayer < 2 * World.TILE_SIZE) {
                if (currentState != AIState.FLEEING) {
                    GameLogger.info(pokemon.getName() + " (" + personality + ") is frightened and flees!");
                    currentState = AIState.FLEEING;
                    stateTimer = 0;
                }
            }
            // If we are fleeing and now safe, transition to roaming.
            else if (currentState == AIState.FLEEING && distToPlayer > 3 * World.TILE_SIZE) {
                currentState = AIState.ROAMING;
                stateTimer = 0;
                chooseRandomTarget(world, player);
            }
        }

        // Every decision interval, perform a state–based update.
        if (decisionTimer >= DECISION_INTERVAL) {
            decisionTimer = 0;
            switch (currentState) {
                case IDLE:
                    if (stateTimer >= idleDuration) {
                        // Transition from idle to roaming.
                        currentState = AIState.ROAMING;
                        stateTimer = 0;
                        chooseRandomTarget(world, player);
                    }
                    break;
                case ROAMING:
                    if (reachedTarget()) {
                        GameLogger.info(pokemon.getName() + " reached its target and is now idling.");
                        enterIdleState();
                    } else {
                        moveTowardsTarget(delta, world);
                    }
                    break;
                case FLEEING:
                    if (player != null) {
                        chooseFleeTarget(player, world);
                        moveTowardsTarget(delta, world);
                    }
                    break;
            }
        }
    }

    /**
     * Chooses a random target position (for roaming) a set distance from the player.
     */
    private void chooseRandomTarget(World world, Player player) {
        float baseX = (player != null) ? player.getX() * World.TILE_SIZE : pokemon.getX();
        float baseY = (player != null) ? player.getY() * World.TILE_SIZE : pokemon.getY();
        // Choose a random angle and a distance between 10 and 20 tiles.
        float angle = MathUtils.random(0, MathUtils.PI2);
        float distance = MathUtils.random(10 * World.TILE_SIZE, 20 * World.TILE_SIZE);
        float targetX = baseX + MathUtils.cos(angle) * distance;
        float targetY = baseY + MathUtils.sin(angle) * distance;
        // Check if the target tile is passable.
        if (world.isPassable((int)(targetX/World.TILE_SIZE), (int)(targetY/World.TILE_SIZE))) {
            targetPosition.set(targetX, targetY);
            GameLogger.info(pokemon.getName() + " is roaming to (" + targetX + "," + targetY + ")");
        } else {
            targetPosition.set(pokemon.getX(), pokemon.getY());
            GameLogger.info(pokemon.getName() + " could not find a valid roaming target; idling instead.");
        }
    }

    /**
     * Chooses a flee target directly opposite the player's position.
     */
    private void chooseFleeTarget(Player player, World world) {
        float dx = pokemon.getX() - player.getX() * World.TILE_SIZE;
        float dy = pokemon.getY() - player.getY() * World.TILE_SIZE;
        Vector2 dir = new Vector2(dx, dy).nor();
        float targetX = pokemon.getX() + dir.x * World.TILE_SIZE;
        float targetY = pokemon.getY() + dir.y * World.TILE_SIZE;
        if (world.isPassable((int)(targetX/World.TILE_SIZE), (int)(targetY/World.TILE_SIZE))) {
            targetPosition.set(targetX, targetY);
        } else {
            targetPosition.set(pokemon.getX(), pokemon.getY());
        }
    }

    /**
     * Moves the Pokémon toward the target position using simple linear interpolation.
     */
    private void moveTowardsTarget(float delta, World world) {
        float currentX = pokemon.getX();
        float currentY = pokemon.getY();
        float targetX = targetPosition.x;
        float targetY = targetPosition.y;
        float distance = Vector2.dst(currentX, currentY, targetX, targetY);
        if (distance < 0.1f) return; // Already at target

        // Speed can be adjusted as needed.
        float speed = World.TILE_SIZE * 2;
        float lerpFactor = delta * speed / distance;
        float newX = MathUtils.lerp(currentX, targetX, lerpFactor);
        float newY = MathUtils.lerp(currentY, targetY, lerpFactor);
        if (world.isPassable((int)(newX/World.TILE_SIZE), (int)(newY/World.TILE_SIZE))) {
            pokemon.setX(newX);
            pokemon.setY(newY);
        } else {
            GameLogger.info(pokemon.getName() + " encountered an obstacle; idling.");
            enterIdleState();
        }
    }

    /**
     * Checks whether the Pokémon is close enough to its target.
     */
    private boolean reachedTarget() {
        return Vector2.dst(pokemon.getX(), pokemon.getY(), targetPosition.x, targetPosition.y) < 2f;
    }

    // ------------------- Existing Methods Below (with minor tweaks) -------------------

    private void handleSpecialTraits(World world) {
        if (pokemon.isMoving()) return; // Do not interrupt if already moving

        String name = pokemon.getName().toLowerCase();
        if (name.equalsIgnoreCase("abra")) {
            if (MathUtils.random() < 0.01f) {
                int currentTileX = (int) (pokemon.getX() / World.TILE_SIZE);
                int currentTileY = (int) (pokemon.getY() / World.TILE_SIZE);
                int step = MathUtils.random(3, 6);
                int[] dx = {step, -step, 0, 0};
                int[] dy = {0, 0, step, -step};
                int index = MathUtils.random(0, 3);
                int targetTileX = currentTileX + dx[index];
                int targetTileY = currentTileY + dy[index];
                if (world.isPassable(targetTileX, targetTileY)) {
                    GameLogger.info("Abra teleports from (" + currentTileX + "," + currentTileY +
                        ") to (" + targetTileX + "," + targetTileY + ")");
                    pokemon.setX(targetTileX * World.TILE_SIZE);
                    pokemon.setY(targetTileY * World.TILE_SIZE);
                    enterIdleState();
                }
            }
        } else if (name.equalsIgnoreCase("diglett")) {
            if (MathUtils.random() < 0.01f) {
                int currentTileX = (int) (pokemon.getX() / World.TILE_SIZE);
                int currentTileY = (int) (pokemon.getY() / World.TILE_SIZE);
                int step = MathUtils.random(1, 3);
                int[] dx = {step, -step, 0, 0};
                int[] dy = {0, 0, step, -step};
                int index = MathUtils.random(0, 3);
                int targetTileX = currentTileX + dx[index];
                int targetTileY = currentTileY + dy[index];
                if (world.isPassable(targetTileX, targetTileY)) {
                    GameLogger.info("Diglett goes underground from (" + currentTileX + "," + currentTileY +
                        ") and reappears at (" + targetTileX + "," + targetTileY + ")");
                    pokemon.setX(targetTileX * World.TILE_SIZE);
                    pokemon.setY(targetTileY * World.TILE_SIZE);
                    enterIdleState();
                }
            }
        }
    }

    private void approachPlayer(World world, Player player) {
        int pokemonTileX = (int) (pokemon.getX() / World.TILE_SIZE);
        int pokemonTileY = (int) (pokemon.getY() / World.TILE_SIZE);
        int playerTileX = (int) (player.getX());
        int playerTileY = (int) (player.getY());

        int dx = playerTileX - pokemonTileX;
        int dy = playerTileY - pokemonTileY;
        int stepX = dx != 0 ? dx / Math.abs(dx) : 0;
        int stepY = dy != 0 ? dy / Math.abs(dy) : 0;
        int targetTileX = pokemonTileX + stepX;
        int targetTileY = pokemonTileY + stepY;

        if (world.isPassable(targetTileX, targetTileY)) {
            String direction = (Math.abs(dx) > Math.abs(dy)) ? (dx > 0 ? "right" : "left")
                : (dy > 0 ? "up" : "down");
            pokemon.moveToTile(targetTileX, targetTileY, direction);
            currentState = AIState.ROAMING;
        } else {
            chooseNewTarget(world);
        }
    }

    private void chooseNewTarget(World world) {
        if (pokemon.isMoving()) {
            GameLogger.info("Cannot choose new target while moving");
            return;
        }
        int currentTileX = (int) (pokemon.getX() / World.TILE_SIZE);
        int currentTileY = (int) (pokemon.getY() / World.TILE_SIZE);
        int step = MathUtils.random(1, 3);
        int[] dx = {0, 0, -step, step};
        int[] dy = {step, -step, 0, 0};
        String[] dirs = {"up", "down", "left", "right"};

        for (int i = 0; i < 4; i++) {
            int targetTileX = currentTileX + dx[i];
            int targetTileY = currentTileY + dy[i];
            if (isValidMove(targetTileX, targetTileY, world)) {
                pokemon.moveToTile(targetTileX, targetTileY, dirs[i]);
                currentState = AIState.ROAMING;
                return;
            }
        }
        GameLogger.info("No valid moves found - entering idle state");
        enterIdleState();
    }

    private boolean isValidMove(int tileX, int tileY, World world) {
        if (!world.isPassable(tileX, tileY)) {
            return false;
        }
        float pixelX = tileX * World.TILE_SIZE;
        float pixelY = tileY * World.TILE_SIZE;
        Collection<WildPokemon> nearby = world.getPokemonSpawnManager()
            .getPokemonInRange(pixelX, pixelY, MIN_DISTANCE_TO_OTHERS);
        return nearby.isEmpty() || (nearby.size() == 1 && nearby.iterator().next() == pokemon);
    }

    private void enterFleeingState(World world) {
        Player player = GameContext.get().getPlayer();
        if (player == null) return;

        int pokemonTileX = (int) (pokemon.getX() / World.TILE_SIZE);
        int pokemonTileY = (int) (pokemon.getY() / World.TILE_SIZE);
        int playerTileX = (int) (player.getX());
        int playerTileY = (int) (player.getY());

        int dx = pokemonTileX - playerTileX;
        int dy = pokemonTileY - playerTileY;
        int targetTileX = pokemonTileX + (dx != 0 ? dx / Math.abs(dx) : 0);
        int targetTileY = pokemonTileY + (dy != 0 ? dy / Math.abs(dy) : 0);

        if (world.isPassable(targetTileX, targetTileY)) {
            String direction = (Math.abs(dx) > Math.abs(dy)) ? (dx > 0 ? "right" : "left")
                : (dy > 0 ? "up" : "down");
            pokemon.moveToTile(targetTileX, targetTileY, direction);
            currentState = AIState.FLEEING;
        } else {
            chooseNewTarget(world);
        }
    }

    public void enterIdleState() {
        currentState = AIState.IDLE;
        stateTimer = 0;
        idleDuration = MathUtils.random(IDLE_MIN_DURATION, IDLE_MAX_DURATION);
        pokemon.setMoving(false);
    }

    private enum AIState {
        IDLE,
        ROAMING,
        FLEEING
    }
}
