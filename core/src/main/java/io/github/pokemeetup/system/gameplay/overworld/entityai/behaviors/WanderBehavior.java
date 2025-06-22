package io.github.pokemeetup.system.gameplay.overworld.entityai.behaviors;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonAI;
import io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonPersonalityTrait;

// Wander Behavior
public class WanderBehavior implements PokemonBehavior {
    private static final float WANDER_COOLDOWN = 2.0f;

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
        int currentTileX = (int) (pokemon.getX() / World.TILE_SIZE);
        int currentTileY = (int) (pokemon.getY() / World.TILE_SIZE);

        String[] directions = {"up", "down", "left", "right"};

        // Shuffle directions for randomness
        for (int i = 0; i < directions.length; i++) {
            int j = MathUtils.random(i, directions.length - 1);
            String temp = directions[i];
            directions[i] = directions[j];
            directions[j] = temp;
        }

        for (String direction : directions) {
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

            if (world.isPassable(targetTileX, targetTileY) &&
                isWithinWanderRange(targetTileX, targetTileY)) {
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
        return !pokemon.isMoving() &&
            !ai.isOnCooldown(getName()) &&
            ai.getStateTimer() > 1.0f;
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
