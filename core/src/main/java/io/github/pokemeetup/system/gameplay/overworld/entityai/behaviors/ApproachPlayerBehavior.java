package io.github.pokemeetup.system.gameplay.overworld.entityai.behaviors;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonAI;
import io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonPersonalityTrait;

// Approach Player Behavior
public class ApproachPlayerBehavior implements PokemonBehavior {
    private static final float APPROACH_RANGE = 10.0f * World.TILE_SIZE;
    private static final float OPTIMAL_DISTANCE = 2.0f * World.TILE_SIZE;

    private final WildPokemon pokemon;
    private final PokemonAI ai;

    public ApproachPlayerBehavior(WildPokemon pokemon, PokemonAI ai) {
        this.pokemon = pokemon;
        this.ai = ai;
    }

    @Override
    public void execute(float delta) {
        if (!pokemon.isMoving()) {
            Player player = GameContext.get().getPlayer();
            if (player != null) {
                moveTowardsPlayer(player);
            }
        }
    }

    private void moveTowardsPlayer(Player player) {
        World world = GameContext.get().getWorld();
        if (world == null) return;

        int pokemonTileX = (int) (pokemon.getX() / World.TILE_SIZE);
        int pokemonTileY = (int) (pokemon.getY() / World.TILE_SIZE);
        int playerTileX = (int) (player.getX() / World.TILE_SIZE);
        int playerTileY = (int) (player.getY() / World.TILE_SIZE);

        int dx = Integer.compare(playerTileX, pokemonTileX);
        int dy = Integer.compare(playerTileY, pokemonTileY);

        // Choose direction based on larger distance
        String direction;
        int targetTileX = pokemonTileX;
        int targetTileY = pokemonTileY;

        if (Math.abs(playerTileX - pokemonTileX) >= Math.abs(playerTileY - pokemonTileY)) {
            direction = dx > 0 ? "right" : "left";
            targetTileX += dx;
        } else {
            direction = dy > 0 ? "up" : "down";
            targetTileY += dy;
        }

        if (world.isPassable(targetTileX, targetTileY)) {
            pokemon.moveToTile(targetTileX, targetTileY, direction);
            ai.setCurrentState(PokemonAI.AIState.APPROACHING);
            ai.setCooldown(getName(), 1.0f);
        }
    }

    @Override
    public boolean canExecute() {
        if (!ai.hasPersonalityTrait(PokemonPersonalityTrait.AGGRESSIVE) &&
            !ai.hasPersonalityTrait(PokemonPersonalityTrait.CURIOUS)) {
            return false;
        }

        Player player = GameContext.get().getPlayer();
        if (player == null) return false;

        float distance = Vector2.dst(pokemon.getX(), pokemon.getY(),
            player.getX(), player.getY());

        return distance <= APPROACH_RANGE &&
            distance > OPTIMAL_DISTANCE &&
            !ai.isOnCooldown(getName()) &&
            MathUtils.random() < (ai.getApproachFactor() * 0.1f);
    }

    @Override
    public int getPriority() {
        return 6;
    }

    @Override
    public String getName() {
        return "approach_player";
    }
}
