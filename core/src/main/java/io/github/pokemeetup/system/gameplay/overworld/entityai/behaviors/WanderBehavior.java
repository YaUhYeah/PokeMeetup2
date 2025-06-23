package io.github.pokemeetup.system.gameplay.overworld.entityai.behaviors;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonAI;
import io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonPersonalityTrait;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class WanderBehavior implements PokemonBehavior {
    // Make Pokemon move more frequently by reducing cooldown.
    private static final float WANDER_COOLDOWN = 0.4f; // Was 1.0f

    private final WildPokemon pokemon;
    private final PokemonAI ai;

    public WanderBehavior(WildPokemon pokemon, PokemonAI ai) {
        this.pokemon = pokemon;
        this.ai = ai;
    }

    @Override
    public void execute(float delta) {
        if (!pokemon.isMoving()) {
            World world = GameContext.get().getWorld();
            if (world != null) {
                moveRandomDirection(world);
                ai.setCooldown(getName(), WANDER_COOLDOWN);
            }
        }
    }

    private void moveRandomDirection(World world) {
        // 15% chance to just stay idle for this tick, makes movement less predictable.
        if (MathUtils.random() < 0.15f) {
            ai.setCooldown(getName(), WANDER_COOLDOWN * 1.5f); // Slightly longer cooldown if idling
            return;
        }

        int currentTileX = pokemon.getTileX();
        int currentTileY = pokemon.getTileY();
        String lastDirection = pokemon.getDirection();

        // Create a list of potential directions
        List<String> potentialDirections = new ArrayList<>(Arrays.asList("up", "down", "left", "right"));

        // Shuffle to randomize the order of non-priority directions
        Collections.shuffle(potentialDirections);

        // Bias towards continuing in the same direction by moving it to the front
        potentialDirections.remove(lastDirection);
        potentialDirections.add(0, lastDirection);

        // Try to move in the prioritized order
        for (String direction : potentialDirections) {
            int targetTileX = currentTileX;
            int targetTileY = currentTileY;

            switch (direction) {
                case "up":    targetTileY++; break;
                case "down":  targetTileY--; break;
                case "left":  targetTileX--; break;
                case "right": targetTileX++; break;
            }

            if (world.isPassable(targetTileX, targetTileY) && isWithinWanderRange(targetTileX, targetTileY)) {
                pokemon.moveToTile(targetTileX, targetTileY, direction);
                ai.setCurrentState(PokemonAI.AIState.WANDERING);
                return;
            }
        }
    }

    private boolean isWithinWanderRange(int tileX, int tileY) {
        if (!ai.hasPersonalityTrait(PokemonPersonalityTrait.TERRITORIAL)) {
            return true; // No territory restrictions
        }

        Vector2 territory = ai.getTerritoryCenter();
        float distance = Vector2.dst(tileX * World.TILE_SIZE, tileY * World.TILE_SIZE,
            territory.x, territory.y);
        return distance <= ai.getTerritoryRadius();
    }

    @Override
    public boolean canExecute() {
        // Pokemon can decide to wander more quickly after stopping.
        return !pokemon.isMoving() &&
            !ai.isOnCooldown(getName()) &&
            ai.getStateTimer() > 0.4f; // Was 1.0f
    }

    @Override
    public int getPriority() {
        return 3;
    }

    @Override
    public String getName() {
        return "wander";
    }
}
