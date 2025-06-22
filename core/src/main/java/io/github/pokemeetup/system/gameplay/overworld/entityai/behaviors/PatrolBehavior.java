package io.github.pokemeetup.system.gameplay.overworld.entityai.behaviors;

import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonAI;
import io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonPersonalityTrait;

// Patrol Behavior
public class PatrolBehavior implements PokemonBehavior {
    private final WildPokemon pokemon;
    private final PokemonAI ai;

    public PatrolBehavior(WildPokemon pokemon, PokemonAI ai) {
        this.pokemon = pokemon;
        this.ai = ai;
    }

    @Override
    public void execute(float delta) {
        if (!pokemon.isMoving() && !ai.getPatrolRoute().isEmpty()) {
            moveToNextPatrolPoint();
        }
    }

    private void moveToNextPatrolPoint() {
        World world = GameContext.get().getWorld();
        if (world == null) return;

        Vector2 currentTarget = ai.getCurrentPatrolTarget();
        if (currentTarget == null) {
            ai.setCurrentPatrolIndex(0);
            currentTarget = ai.getCurrentPatrolTarget();
        }

        if (currentTarget != null) {
            int targetTileX = (int) (currentTarget.x / World.TILE_SIZE);
            int targetTileY = (int) (currentTarget.y / World.TILE_SIZE);
            int pokemonTileX = (int) (pokemon.getX() / World.TILE_SIZE);
            int pokemonTileY = (int) (pokemon.getY() / World.TILE_SIZE);

            // Check if we've reached the patrol point
            if (pokemonTileX == targetTileX && pokemonTileY == targetTileY) {
                // Move to next patrol point
                int nextIndex = (ai.getCurrentPatrolIndex() + 1) % ai.getPatrolRoute().size();
                ai.setCurrentPatrolIndex(nextIndex);
                ai.setCooldown(getName(), 2.0f); // Pause at patrol points
                return;
            }

            // Move towards current patrol point
            int dx = Integer.compare(targetTileX, pokemonTileX);
            int dy = Integer.compare(targetTileY, pokemonTileY);

            String direction;
            int nextTileX = pokemonTileX;
            int nextTileY = pokemonTileY;

            if (Math.abs(dx) >= Math.abs(dy)) {
                direction = dx > 0 ? "right" : "left";
                nextTileX += dx;
            } else {
                direction = dy > 0 ? "up" : "down";
                nextTileY += dy;
            }

            if (world.isPassable(nextTileX, nextTileY)) {
                pokemon.moveToTile(nextTileX, nextTileY, direction);
                ai.setCurrentState(PokemonAI.AIState.PATROLLING);
            }
        }
    }

    @Override
    public boolean canExecute() {
        return ai.hasPersonalityTrait(PokemonPersonalityTrait.TERRITORIAL) &&
            !ai.getPatrolRoute().isEmpty() &&
            !ai.isOnCooldown(getName()) &&
            ai.getStateTimer() > 3.0f;
    }

    @Override
    public int getPriority() {
        return 4;
    }

    @Override
    public String getName() {
        return "patrol";
    }
}
