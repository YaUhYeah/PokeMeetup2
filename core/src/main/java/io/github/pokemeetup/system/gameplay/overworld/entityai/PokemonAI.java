package io.github.pokemeetup.system.gameplay.overworld.entityai;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;

import java.util.Collection;

public class PokemonAI {
    private boolean isPaused = false;

    public void setPaused(boolean paused) {
        this.isPaused = paused;
    }
    private static final float DECISION_INTERVAL = 2.0f;
    private static final float IDLE_MIN_DURATION = 1.5f;
    private static final float IDLE_MAX_DURATION = 4.0f;
    private static final float MOVEMENT_CHANCE = 0.6f;
    private static final float FLEE_RANGE = 150f;
    private static final float MIN_DISTANCE_TO_OTHERS = World.TILE_SIZE * 2;

    private final WildPokemon pokemon;
    private float decisionTimer = 0;
    private float stateTimer = 0;
    private float idleDuration = 0;
    private AIState currentState = AIState.IDLE;

    public PokemonAI(WildPokemon pokemon) {
        this.pokemon = pokemon;
    }

    public void update(float delta, World world) {
        if (world == null || pokemon == null) {
            GameLogger.error("AI Update skipped - null world or pokemon");
            return;
        }
        if (isPaused) return;

        stateTimer += delta;
        decisionTimer += delta;

        // Log current state
        if (decisionTimer >= DECISION_INTERVAL) {
            decisionTimer = 0;
            GameLogger.error(String.format(
                "Pokemon %s at (%.1f,%.1f) - State: %s, Timer: %.1f, Duration: %.1f",
                pokemon.getName(), pokemon.getX(), pokemon.getY(),
                currentState, stateTimer, idleDuration
            ));

            if (currentState == AIState.IDLE && stateTimer >= idleDuration) {
                if (MathUtils.random() < MOVEMENT_CHANCE) {
                    GameLogger.error("Attempting to choose new target");
                    chooseNewTarget(world);
                } else {
                    GameLogger.error("Staying idle");
                    enterIdleState();
                }
            }
        }

        // Check player proximity
        Player player = world.getPlayer();
        if (player != null) {
            float dist = Vector2.dst(
                pokemon.getX(), pokemon.getY(),
                player.getX() * World.TILE_SIZE, player.getY() * World.TILE_SIZE
            );
            if (dist < FLEE_RANGE) {
                GameLogger.error(String.format(
                    "Player detected at distance %.1f - initiating flee", dist
                ));
                enterFleeingState(world);
            }
        }
    }

    private void chooseNewTarget(World world) {
        if (pokemon.isMoving()) {
            GameLogger.error("Cannot choose new target while moving");
            return;
        }

        int currentTileX = (int)(pokemon.getX() / World.TILE_SIZE);
        int currentTileY = (int)(pokemon.getY() / World.TILE_SIZE);

        GameLogger.info(String.format(
            "Current position: Tile(%d,%d) Pixel(%.1f,%.1f)",
            currentTileX, currentTileY, pokemon.getX(), pokemon.getY()
        ));

        // Try all directions systematically
        int[] dx = {0, 0, -1, 1};
        int[] dy = {1, -1, 0, 0};
        String[] dirs = {"up", "down", "left", "right"};

        for (int i = 0; i < 4; i++) {
            int targetTileX = currentTileX + dx[i];
            int targetTileY = currentTileY + dy[i];

            GameLogger.info(String.format(
                "Checking move to (%d,%d) direction: %s",
                targetTileX, targetTileY, dirs[i]
            ));

            if (isValidMove(targetTileX, targetTileY, world)) {
                pokemon.moveToTile(targetTileX, targetTileY, dirs[i]);
                currentState = AIState.MOVING;
                GameLogger.info(String.format(
                    "Move validated - moving %s to (%d,%d)",
                    dirs[i], targetTileX, targetTileY
                ));
                return;
            }
        }

        GameLogger.error("No valid moves found - entering idle state");
        enterIdleState();
    }



    private boolean isValidMove(int tileX, int tileY, World world) {
        // Check passability
        if (!world.isPassable(tileX, tileY)) {
            return false;
        }

        // Convert to pixel coordinates for collision checks
        float pixelX = tileX * World.TILE_SIZE;
        float pixelY = tileY * World.TILE_SIZE;

        // Check distance from other Pokemon
        Collection<WildPokemon> nearby = world.getPokemonSpawnManager()
            .getPokemonInRange(pixelX, pixelY, MIN_DISTANCE_TO_OTHERS);

        // Allow if no nearby Pokemon or only self
        return nearby.isEmpty() || (nearby.size() == 1 && nearby.iterator().next() == pokemon);
    }

    private boolean checkForNearbyPlayer(World world) {
        Player player = world.getPlayer();
        if (player == null) return false;

        float distanceToPlayer = Vector2.dst(
            pokemon.getX(), pokemon.getY(),
            player.getX() * World.TILE_SIZE,
            player.getY() * World.TILE_SIZE
        );

        return distanceToPlayer < FLEE_RANGE;
    }

    private void enterFleeingState(World world) {
        Player player = world.getPlayer();
        if (player == null) return;

        // Calculate direction away from player in tile coordinates
        int pokemonTileX = (int)(pokemon.getX() / World.TILE_SIZE);
        int pokemonTileY = (int)(pokemon.getY() / World.TILE_SIZE);
        int playerTileX = (int)(player.getX());
        int playerTileY = (int)(player.getY());

        // Determine escape direction
        int dx = pokemonTileX - playerTileX;
        int dy = pokemonTileY - playerTileY;

        // Try to move away from player
        int targetTileX = pokemonTileX + (dx != 0 ? dx / Math.abs(dx) : 0);
        int targetTileY = pokemonTileY + (dy != 0 ? dy / Math.abs(dy) : 0);

        if (world.isPassable(targetTileX, targetTileY)) {
            String direction = "";
            if (Math.abs(dx) > Math.abs(dy)) {
                direction = dx > 0 ? "right" : "left";
            } else {
                direction = dy > 0 ? "up" : "down";
            }

            pokemon.moveToTile(targetTileX, targetTileY, direction);
            currentState = AIState.FLEEING;
        } else {
            // If can't move directly away, try to move perpendicular
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
        MOVING,
        FLEEING
    }
}
