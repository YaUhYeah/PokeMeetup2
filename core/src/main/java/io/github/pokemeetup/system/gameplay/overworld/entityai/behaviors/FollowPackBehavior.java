package io.github.pokemeetup.system.gameplay.overworld.entityai.behaviors;

import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonAI;
import io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonPersonalityTrait;

import java.util.*;

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
        if (pokemon.isMoving()) {
            return;
        }
        WildPokemon leader = findPackLeader();
        if (leader != null) {
            followLeader(leader);
        }
    }

    private WildPokemon findPackLeader() {
        UUID leaderId = ai.getPackLeaderId();
        if (leaderId == null) return null;
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
        float distance = Vector2.dst(pokemon.getX(), pokemon.getY(), leader.getX(), leader.getY());
        if (distance <= FOLLOW_DISTANCE) {
            return; // Already close enough.
        }

        World world = GameContext.get().getWorld();
        if (world == null) return;

        int pokemonTileX = pokemon.getTileX();
        int pokemonTileY = pokemon.getTileY();
        int leaderTileX = leader.getTileX();
        int leaderTileY = leader.getTileY();

        int dx = Integer.compare(leaderTileX, pokemonTileX);
        int dy = Integer.compare(leaderTileY, pokemonTileY);

        List<String> moveOptions = new ArrayList<>();
        if (dx != 0) moveOptions.add("horizontal");
        if (dy != 0) moveOptions.add("vertical");
        Collections.shuffle(moveOptions);

        boolean moveMade = false;
        for (String move : moveOptions) {
            int targetTileX = pokemonTileX;
            int targetTileY = pokemonTileY;
            String direction;

            if (move.equals("horizontal")) {
                targetTileX += dx;
                direction = dx > 0 ? "right" : "left";
            } else { // vertical
                targetTileY += dy;
                direction = dy > 0 ? "up" : "down";
            }

            if (world.isPassable(targetTileX, targetTileY)) {
                pokemon.moveToTile(targetTileX, targetTileY, direction);
                ai.setCurrentState(PokemonAI.AIState.FOLLOWING);
                moveMade = true;
                break;
            }
        }

        // [FIX] Only set a cooldown if blocked.
        if (!moveMade) {
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
