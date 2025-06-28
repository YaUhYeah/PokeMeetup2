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
    private static final float MIN_PAUSE_TIME = 0.5f;
    private static final float MAX_PAUSE_TIME = 1.5f;

    private final WildPokemon pokemon;
    private final PokemonAI ai;

    private int stepsRemaining = 0;
    private String currentWanderDirection = null;

    public WanderBehavior(WildPokemon pokemon, PokemonAI ai) {
        this.pokemon = pokemon;
        this.ai = ai;
    }

    @Override
    public void execute(float delta) {
        if (pokemon.isMoving()) {
            return; // Don't decide a new move while already moving
        }

        if (stepsRemaining > 0) {
            moveAlongPath();
        } else if (!ai.isOnCooldown(getName())) {
            decideNewWanderPath();
        }
    }

    private void decideNewWanderPath() {
        List<String> potentialDirections = new ArrayList<>(Arrays.asList("up", "down", "left", "right"));
        Collections.shuffle(potentialDirections);

        // Try to find a valid direction to start a new path
        for (String direction : potentialDirections) {
            int nextTileX = pokemon.getTileX();
            int nextTileY = pokemon.getTileY();

            switch (direction) {
                case "up":    nextTileY++; break;
                case "down":  nextTileY--; break;
                case "left":  nextTileX--; break;
                case "right": nextTileX++; break;
            }

            if (GameContext.get().getWorld().isPassable(nextTileX, nextTileY) && isWithinWanderRange(nextTileX, nextTileY)) {
                currentWanderDirection = direction;
                stepsRemaining = MathUtils.random(1, 4); // New path of 1-4 steps
                moveAlongPath(); // Take the first step
                return;
            }
        }
    }

    private void moveAlongPath() {
        if (currentWanderDirection == null || stepsRemaining <= 0) {
            return;
        }

        int targetTileX = pokemon.getTileX();
        int targetTileY = pokemon.getTileY();

        switch (currentWanderDirection) {
            case "up":    targetTileY++; break;
            case "down":  targetTileY--; break;
            case "left":  targetTileX--; break;
            case "right": targetTileX++; break;
        }

        if (GameContext.get().getWorld().isPassable(targetTileX, targetTileY) && isWithinWanderRange(targetTileX, targetTileY)) {
            pokemon.moveToTile(targetTileX, targetTileY, currentWanderDirection);
            stepsRemaining--;

            if (stepsRemaining <= 0) {
                // Path is complete, set a cooldown for a natural pause
                ai.setCooldown(getName(), MathUtils.random(MIN_PAUSE_TIME, MAX_PAUSE_TIME));
                currentWanderDirection = null;
            }
        } else {
            // Path is blocked, stop wandering and pause
            stepsRemaining = 0;
            currentWanderDirection = null;
            ai.setCooldown(getName(), MathUtils.random(MIN_PAUSE_TIME, MAX_PAUSE_TIME));
        }
    }

    private boolean isWithinWanderRange(int tileX, int tileY) {
        if (!ai.hasPersonalityTrait(PokemonPersonalityTrait.TERRITORIAL)) {
            return true; // No territory restrictions
        }
        Vector2 territory = ai.getTerritoryCenter();
        float distance = Vector2.dst(tileX * World.TILE_SIZE, tileY * World.TILE_SIZE, territory.x, territory.y);
        return distance <= ai.getTerritoryRadius();
    }

    @Override
    public boolean canExecute() {
        return !pokemon.isMoving();
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
