package io.github.pokemeetup.system.gameplay.overworld.entityai.behaviors;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonAI;
import io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonPersonalityTrait;

// Investigate Behavior
public class InvestigateBehavior implements PokemonBehavior {
    private static final float INVESTIGATION_RANGE = 20.0f * World.TILE_SIZE;

    private final WildPokemon pokemon;
    private final PokemonAI ai;
    private Vector2 investigationTarget;

    public InvestigateBehavior(WildPokemon pokemon, PokemonAI ai) {
        this.pokemon = pokemon;
        this.ai = ai;
    }

    @Override
    public void execute(float delta) {
        if (!pokemon.isMoving()) {
            if (investigationTarget == null) {
                findSomethingToInvestigate();
            } else {
                moveTowardsInvestigationTarget();
            }
        }
    }

    private void findSomethingToInvestigate() {
        Player player = GameContext.get().getPlayer();
        if (player != null) {
            float distance = Vector2.dst(pokemon.getX(), pokemon.getY(),
                player.getX(), player.getY());
            if (distance <= INVESTIGATION_RANGE) {
                investigationTarget = new Vector2(player.getX(), player.getY());
            }
        }
    }

    private void moveTowardsInvestigationTarget() {
        World world = GameContext.get().getWorld();
        if (world == null) return;

        int pokemonTileX = (int) (pokemon.getX() / World.TILE_SIZE);
        int pokemonTileY = (int) (pokemon.getY() / World.TILE_SIZE);
        int targetTileX = (int) (investigationTarget.x / World.TILE_SIZE);
        int targetTileY = (int) (investigationTarget.y / World.TILE_SIZE);

        // If we've reached the investigation point
        if (pokemonTileX == targetTileX && pokemonTileY == targetTileY) {
            investigationTarget = null;
            ai.setCooldown(getName(), 10.0f);
            return;
        }

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
            ai.setCurrentState(PokemonAI.AIState.INVESTIGATING);
        } else {
            investigationTarget = null; // Can't reach target
        }
    }

    @Override
    public boolean canExecute() {
        return ai.hasPersonalityTrait(PokemonPersonalityTrait.CURIOUS) &&
            !ai.isOnCooldown(getName()) &&
            MathUtils.random() < 0.05f; // 5% chance per update
    }

    @Override
    public int getPriority() {
        return 5;
    }

    @Override
    public String getName() {
        return "investigate";
    }
}
