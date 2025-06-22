package io.github.pokemeetup.system.gameplay.overworld.entityai.behaviors;

import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonAI;
import io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonPersonalityTrait;

import java.util.Collection;
import java.util.UUID;

public class FollowPackBehavior implements PokemonBehavior {
    private static final float FOLLOW_DISTANCE = 3.0f * World.TILE_SIZE;
    private static final float MAX_FOLLOW_DISTANCE = 6.0f * World.TILE_SIZE;

    private final WildPokemon pokemon;
    private final PokemonAI ai;

    public FollowPackBehavior(WildPokemon pokemon, PokemonAI ai) {
        this.pokemon = pokemon;
        this.ai = ai;
    }

    @Override
    public void execute(float delta) {
        if (!pokemon.isMoving()) {
            WildPokemon leader = findPackLeader();
            if (leader != null) {
                followLeader(leader);
            }
        }
    }

    private WildPokemon findPackLeader() {
        UUID leaderId = ai.getPackLeaderId();
        if (leaderId == null) return null;

        // In a real implementation, you'd get this from the spawn manager
        Collection<WildPokemon> nearbyPokemon = GameContext.get().getWorld()
            .getPokemonSpawnManager().getPokemonInRange(
                pokemon.getX(), pokemon.getY(), MAX_FOLLOW_DISTANCE);

        for (WildPokemon nearby : nearbyPokemon) {
            if (nearby.getUuid().equals(leaderId)) {
                return nearby;
            }
        }

        return null;
    }

    private void followLeader(WildPokemon leader) {
        float distance = Vector2.dst(pokemon.getX(), pokemon.getY(),
            leader.getX(), leader.getY());

        // Only follow if too far away
        if (distance <= FOLLOW_DISTANCE) {
            return;
        }

        World world = GameContext.get().getWorld();
        if (world == null) return;

        int pokemonTileX = (int) (pokemon.getX() / World.TILE_SIZE);
        int pokemonTileY = (int) (pokemon.getY() / World.TILE_SIZE);
        int leaderTileX = (int) (leader.getX() / World.TILE_SIZE);
        int leaderTileY = (int) (leader.getY() / World.TILE_SIZE);

        int dx = Integer.compare(leaderTileX, pokemonTileX);
        int dy = Integer.compare(leaderTileY, pokemonTileY);

        String direction;
        int targetTileX = pokemonTileX;
        int targetTileY = pokemonTileY;

        if (Math.abs(dx) >= Math.abs(dy)) {
            direction = dx > 0 ? "right" : "left";
            targetTileX += dx;
        } else {
            direction = dy > 0 ? "up" : "down";
            targetTileY += dy;
        }

        if (world.isPassable(targetTileX, targetTileY)) {
            pokemon.moveToTile(targetTileX, targetTileY, direction);
            ai.setCurrentState(PokemonAI.AIState.FOLLOWING);
            ai.setCooldown(getName(), 1.0f);
        }
    }

    @Override
    public boolean canExecute() {
        return ai.hasPersonalityTrait(PokemonPersonalityTrait.FOLLOWER) &&
            ai.getPackLeaderId() != null &&
            !ai.isOnCooldown(getName());
    }

    @Override
    public int getPriority() {
        return 6; // Higher than wandering, lower than fleeing
    }

    @Override
    public String getName() {
        return "follow_pack";
    }
}
