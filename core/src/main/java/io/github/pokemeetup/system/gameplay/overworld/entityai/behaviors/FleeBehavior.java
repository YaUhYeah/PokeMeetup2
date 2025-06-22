package io.github.pokemeetup.system.gameplay.overworld.entityai.behaviors;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonAI;

// Flee Behavior
public class FleeBehavior implements PokemonBehavior {
    private static final float FLEE_SPEED_MULTIPLIER = 0.5f; // Faster movement
    private static final int MAX_FLEE_STEPS = 32;

    private final WildPokemon pokemon;
    private final PokemonAI ai;
    private int fleeStepsRemaining = 0;
    private String fleeDirection;

    public FleeBehavior(WildPokemon pokemon, PokemonAI ai) {
        this.pokemon = pokemon;
        this.ai = ai;
    }

    @Override
    public void execute(float delta) {
        if (!pokemon.isMoving()) {
            if (fleeStepsRemaining <= 0) {
                initiateFlee();
            } else {
                continueFleeMovement();
            }
        }
    }

    private void initiateFlee() {
        Player player = GameContext.get().getPlayer();
        if (player == null) return;

        fleeDirection = calculateFleeDirection(player);
        fleeStepsRemaining = MathUtils.random(2, MAX_FLEE_STEPS);
        continueFleeMovement();
    }

    private void continueFleeMovement() {
        World world = GameContext.get().getWorld();
        if (world == null) return;

        int currentTileX = (int) (pokemon.getX() / World.TILE_SIZE);
        int currentTileY = (int) (pokemon.getY() / World.TILE_SIZE);

        int targetTileX = currentTileX;
        int targetTileY = currentTileY;

        switch (fleeDirection) {
            case "up":
                targetTileY++;
                break;
            case "down":
                targetTileY--;
                break;
            case "left":
                targetTileX--;
                break;
            case "right":
                targetTileX++;
                break;
        }

        if (world.isPassable(targetTileX, targetTileY)) {
            pokemon.moveToTile(targetTileX, targetTileY, fleeDirection);
            fleeStepsRemaining--;
            ai.setCurrentState(PokemonAI.AIState.FLEEING);
        } else {
            // Try alternative directions
            tryAlternativeFleeDirection(world);
        }

        if (fleeStepsRemaining <= 0) {
            ai.setCooldown(getName(), 5.0f);
        }
    }

    private void tryAlternativeFleeDirection(World world) {
        String[] alternatives = {"up", "down", "left", "right"};
        int currentTileX = (int) (pokemon.getX() / World.TILE_SIZE);
        int currentTileY = (int) (pokemon.getY() / World.TILE_SIZE);

        for (String direction : alternatives) {
            if (direction.equals(fleeDirection)) continue;

            int targetTileX = currentTileX;
            int targetTileY = currentTileY;

            switch (direction) {
                case "up":
                    targetTileY++;
                    break;
                case "down":
                    targetTileY--;
                    break;
                case "left":
                    targetTileX--;
                    break;
                case "right":
                    targetTileX++;
                    break;
            }

            if (world.isPassable(targetTileX, targetTileY)) {
                pokemon.moveToTile(targetTileX, targetTileY, direction);
                fleeDirection = direction;
                fleeStepsRemaining--;
                return;
            }
        }

        // No valid escape route
        fleeStepsRemaining = 0;
    }

    private String calculateFleeDirection(Player player) {
        int pokemonTileX = (int) (pokemon.getX() / World.TILE_SIZE);
        int pokemonTileY = (int) (pokemon.getY() / World.TILE_SIZE);
        int playerTileX = (int) (player.getX() / World.TILE_SIZE);
        int playerTileY = (int) (player.getY() / World.TILE_SIZE);

        int dx = pokemonTileX - playerTileX;
        int dy = pokemonTileY - playerTileY;

        if (Math.abs(dx) >= Math.abs(dy)) {
            return (dx >= 0) ? "right" : "left";
        } else {
            return (dy >= 0) ? "up" : "down";
        }
    }

    @Override
    public boolean canExecute() {
        Player player = GameContext.get().getPlayer();
        if (player == null) return false;

        float distance = Vector2.dst(pokemon.getX(), pokemon.getY(),
            player.getX(), player.getY());
        return distance < ai.getFleeThreshold();
    }

    @Override
    public int getPriority() {
        return 8; // High priority
    }

    @Override
    public String getName() {
        return "flee";
    }
}
