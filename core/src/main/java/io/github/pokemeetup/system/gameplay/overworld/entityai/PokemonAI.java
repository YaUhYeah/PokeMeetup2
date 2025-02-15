package io.github.pokemeetup.system.gameplay.overworld.entityai;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;

public class PokemonAI {

    // Normal roaming: delay between moves (in seconds)
    private static final float IDLE_MIN_DURATION = 1.0f;
    private static final float IDLE_MAX_DURATION = 2.0f;
    // Flee (sprint) mode: shorter delay between moves
    private static final float FLEE_IDLE_DURATION = 0.05f;
    // Flee sprint: number of consecutive tile moves
    private static final int FLEE_MIN_STEPS = 2;
    private static final int FLEE_MAX_STEPS = 8;
    // Base flee range in pixels.

    // Personality modifier enums.
    public enum PokemonPersonality {
        TIMID,    // Flees earlier
        CURIOUS,  // May occasionally approach
        LAZY      // Moves infrequently
    }

    private final WildPokemon pokemon;
    private boolean isPaused = false;
    private float stateTimer = 0;
    private float idleDuration = 0;
    private AIState currentState = AIState.IDLE;
    private final PokemonPersonality personality;

    // Flee sprint variables.
    private int fleeStepsRemaining = 0;
    private String fleeDirection = null;

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

    /**
     * Updates the AI. If the Pokémon is not moving and the idle delay has passed,
     * then issue a new move command.
     * - In normal (roaming) mode, it moves one tile in a random cardinal direction.
     * - In flee mode, it “sprints” several tiles away with a short delay between each.
     */
    public void update(float delta, World world) {
        if (world == null || pokemon == null) {
            GameLogger.error("AI Update skipped - null world or pokemon");
            return;
        }
        if (isPaused) return;

        // Handle species-specific behavior.
        handleSpecialTraits(world);

        stateTimer += delta;
        Player player = GameContext.get().getPlayer();

        // Check if the player is too close.
        if (player != null) {
            float distToPlayer = Vector2.dst(pokemon.getX(), pokemon.getY(), player.getX(), player.getY());
            if (distToPlayer < 2 * World.TILE_SIZE) {
                if (currentState != AIState.FLEEING) {
                    GameLogger.info(pokemon.getName() + " (" + personality + ") is frightened and flees!");
                    currentState = AIState.FLEEING;
                    // Reset flee sprint variables.
                    fleeDirection = null;
                    fleeStepsRemaining = 0;
                }
            } else if (currentState == AIState.FLEEING && distToPlayer > 3 * World.TILE_SIZE) {
                // Once far enough, exit flee mode.
                currentState = AIState.ROAMING;
                fleeDirection = null;
                fleeStepsRemaining = 0;
            }
        }

        // If already moving, wait.
        if (pokemon.isMoving()) {
            return;
        }

        // Set idle duration based on mode.
        if (currentState == AIState.FLEEING) {
            idleDuration = FLEE_IDLE_DURATION;
        }
        if (stateTimer < idleDuration) {
            return;
        }

        // Issue a move command.
        if (currentState == AIState.FLEEING) {
            // On the first flee move, compute the flee direction.
            if (fleeDirection == null) {
                fleeDirection = computeFleeDirection(world);
                if (fleeDirection != null) {
                    fleeStepsRemaining = MathUtils.random(FLEE_MIN_STEPS, FLEE_MAX_STEPS);
                    GameLogger.info(pokemon.getName() + " starts sprinting in direction: " + fleeDirection
                        + " for " + fleeStepsRemaining + " steps.");
                } else {
                    // Fallback if no valid flee direction found.
                    chooseNewAdjacentTarget(world);
                    stateTimer = 0;
                    return;
                }
            }
            // Issue a move in the flee direction.
            move(fleeDirection);
            fleeStepsRemaining--;
            if (fleeStepsRemaining <= 0) {
                GameLogger.info(pokemon.getName() + " finished sprinting away.");
                currentState = AIState.IDLE;
                fleeDirection = null;
                fleeStepsRemaining = 0;
            }
        } else {
            // Normal roaming: move one tile in a random cardinal direction.
            chooseRandomTargetTile(world);
        }

        stateTimer = 0;
        if (currentState != AIState.FLEEING) {
            idleDuration = MathUtils.random(IDLE_MIN_DURATION, IDLE_MAX_DURATION);
        }
    }

    /**
     * Issues a move command in the given cardinal direction (one tile).
     */
    private void move(String newDirection) {
        pokemon.moveToTile(getNextTileX(newDirection), getNextTileY(newDirection), newDirection);
    }

    private int getNextTileX(String direction) {
        int currentTileX = (int)(pokemon.getX() / World.TILE_SIZE);
        if (direction.equals("left")) {
            return currentTileX - 1;
        } else if (direction.equals("right")) {
            return currentTileX + 1;
        }
        return currentTileX;
    }

    private int getNextTileY(String direction) {
        int currentTileY = (int)(pokemon.getY() / World.TILE_SIZE);
        if (direction.equals("up")) {
            return currentTileY + 1;
        } else if (direction.equals("down")) {
            return currentTileY - 1;
        }
        return currentTileY;
    }

    /**
     * For normal roaming, randomly pick one of the four cardinal directions (one tile).
     */
    private void chooseRandomTargetTile(World world) {
        int currentTileX = (int)(pokemon.getX() / World.TILE_SIZE);
        int currentTileY = (int)(pokemon.getY() / World.TILE_SIZE);
        String[] directions = {"up", "down", "left", "right"};
        int[] order = {0, 1, 2, 3};
        // Shuffle order.
        for (int i = 0; i < order.length; i++) {
            int j = MathUtils.random(i, order.length - 1);
            int temp = order[i];
            order[i] = order[j];
            order[j] = temp;
        }
        for (int idx : order) {
            String dir = directions[idx];
            int targetTileX = currentTileX;
            int targetTileY = currentTileY;
            if (dir.equals("up")) targetTileY++;
            else if (dir.equals("down")) targetTileY--;
            else if (dir.equals("left")) targetTileX--;
            else if (dir.equals("right")) targetTileX++;
            if (world.isPassable(targetTileX, targetTileY)) {
                GameLogger.info(pokemon.getName() + " is roaming from tile (" + currentTileX + "," + currentTileY +
                    ") to tile (" + targetTileX + "," + targetTileY + ") facing " + dir);
                pokemon.moveToTile(targetTileX, targetTileY, dir);
                return;
            }
        }
        GameLogger.info(pokemon.getName() + " could not find a valid roaming target; idling instead.");
    }

    /**
     * Computes a flee direction based on the player's position, using only cardinal directions.
     */
    private String computeFleeDirection(World world) {
        Player player = GameContext.get().getPlayer();
        if (player == null) return null;
        int pokemonTileX = (int)(pokemon.getX() / World.TILE_SIZE);
        int pokemonTileY = (int)(pokemon.getY() / World.TILE_SIZE);
        int playerTileX = (int)(player.getX() / World.TILE_SIZE);
        int playerTileY = (int)(player.getY() / World.TILE_SIZE);
        int dx = pokemonTileX - playerTileX;
        int dy = pokemonTileY - playerTileY;
        // Use dominant axis to avoid diagonal movement.
        if (Math.abs(dx) >= Math.abs(dy)) {
            return (dx >= 0) ? "right" : "left";
        } else {
            return (dy >= 0) ? "up" : "down";
        }
    }

    /**
     * If the direct flee tile is blocked, try one of the four adjacent tiles.
     */
    private void chooseNewAdjacentTarget(World world) {
        int currentTileX = (int)(pokemon.getX() / World.TILE_SIZE);
        int currentTileY = (int)(pokemon.getY() / World.TILE_SIZE);
        int[] dx = {0, 0, -1, 1};
        int[] dy = {1, -1, 0, 0};
        String[] dirs = {"up", "down", "left", "right"};
        for (int i = 0; i < 4; i++) {
            int targetTileX = currentTileX + dx[i];
            int targetTileY = currentTileY + dy[i];
            if (world.isPassable(targetTileX, targetTileY)) {
                GameLogger.info(pokemon.getName() + " chooses an adjacent tile (" + targetTileX + "," + targetTileY + ")");
                pokemon.moveToTile(targetTileX, targetTileY, dirs[i]);
                return;
            }
        }
        GameLogger.info("No valid adjacent moves found for " + pokemon.getName() + "; remaining idle.");
    }

    /**
     * Determines a cardinal direction based on the difference between two tile positions.
     */
    private String determineDirection(int fromX, int fromY, int toX, int toY) {
        int dx = toX - fromX;
        int dy = toY - fromY;
        if (Math.abs(dx) >= Math.abs(dy)) {
            return (dx >= 0) ? "right" : "left";
        } else {
            return (dy >= 0) ? "up" : "down";
        }
    }

    /**
     * Handles species-specific behavior (e.g., Abra teleport or Diglett underground)
     * without interrupting an active move.
     */
    private void handleSpecialTraits(World world) {
        if (pokemon.isMoving()) return;
        String name = pokemon.getName().toLowerCase();
        if (name.equals("abra")) {
            if (MathUtils.random() < 0.01f) {
                int currentTileX = (int)(pokemon.getX() / World.TILE_SIZE);
                int currentTileY = (int)(pokemon.getY() / World.TILE_SIZE);
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
        } else if (name.equals("diglett")) {
            if (MathUtils.random() < 0.01f) {
                int currentTileX = (int)(pokemon.getX() / World.TILE_SIZE);
                int currentTileY = (int)(pokemon.getY() / World.TILE_SIZE);
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

    public void enterIdleState() {
        currentState = AIState.IDLE;
        stateTimer = 0;
        idleDuration = MathUtils.random(IDLE_MIN_DURATION, IDLE_MAX_DURATION);
        // Reset flee mode variables.
        fleeDirection = null;
        fleeStepsRemaining = 0;
        pokemon.setMoving(false);
    }

    private enum AIState {
        IDLE,
        ROAMING,
        FLEEING
    }
}
