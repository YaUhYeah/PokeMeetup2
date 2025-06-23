package io.github.pokemeetup.system.gameplay.overworld.entityai.behaviors;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonAI;
import io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonPersonalityTrait;
import io.github.pokemeetup.utils.GameLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Pack Leader Behavior - Coordinates pack movement and behavior
public class PackLeaderBehavior implements PokemonBehavior {
    private static final float PACK_COORDINATION_RANGE = 16.0f * World.TILE_SIZE;
    private static final float LEADERSHIP_COOLDOWN = 0.5f;

    private final WildPokemon pokemon;
    private final PokemonAI ai;
    private Vector2 packDestination;

    public PackLeaderBehavior(WildPokemon pokemon, PokemonAI ai) {
        this.pokemon = pokemon;
        this.ai = ai;
    }

    @Override
    public void execute(float delta) {
        if (!pokemon.isMoving()) {
            coordinatePackMovement();
            ai.setCooldown(getName(), LEADERSHIP_COOLDOWN);
        }
    }

    private void coordinatePackMovement() {
        // Determine where the pack should move
        if (packDestination == null || hasReachedDestination()) {
            packDestination = selectNewPackDestination();
        }

        if (packDestination != null) {
            moveTowardsDestination();
            signalPackMembers();
        }
    }

    private Vector2 selectNewPackDestination() {
        World world = GameContext.get().getWorld();
        if (world == null) return null;

        // Choose a destination within territory if territorial, otherwise random
        Vector2 center = ai.hasPersonalityTrait(PokemonPersonalityTrait.TERRITORIAL) ?
            ai.getTerritoryCenter() : new Vector2(pokemon.getX(), pokemon.getY());

        float maxRange = ai.hasPersonalityTrait(PokemonPersonalityTrait.TERRITORIAL) ?
            ai.getTerritoryRadius() : PACK_COORDINATION_RANGE;

        for (int attempts = 0; attempts < 10; attempts++) {
            float angle = MathUtils.random(MathUtils.PI2);
            float distance = MathUtils.random(World.TILE_SIZE * 2, maxRange);
            float x = center.x + MathUtils.cos(angle) * distance;
            float y = center.y + MathUtils.sin(angle) * distance;

            int tileX = Math.round(x / World.TILE_SIZE);
            int tileY = Math.round(y / World.TILE_SIZE);

            if (world.isPassable(tileX, tileY)) {
                return new Vector2(tileX * World.TILE_SIZE, tileY * World.TILE_SIZE);
            }
        }

        return null;
    }

    private boolean hasReachedDestination() {
        if (packDestination == null) return true;

        float distance = Vector2.dst(pokemon.getX(), pokemon.getY(),
            packDestination.x, packDestination.y);
        return distance <= World.TILE_SIZE;
    }
    private void moveTowardsDestination() {
        World world = GameContext.get().getWorld();
        if (world == null || packDestination == null) return;

        int pokemonTileX = pokemon.getTileX();
        int pokemonTileY = pokemon.getTileY();
        int destTileX = (int) (packDestination.x / World.TILE_SIZE);
        int destTileY = (int) (packDestination.y / World.TILE_SIZE);

        int dx = Integer.compare(destTileX, pokemonTileX);
        int dy = Integer.compare(destTileY, pokemonTileY);

        if (dx == 0 && dy == 0) return;

        // FIX: Ensure cardinal movement by randomly choosing a valid axis to move on.
        List<String> moveOptions = new ArrayList<>();
        if (dx != 0) moveOptions.add("horizontal");
        if (dy != 0) moveOptions.add("vertical");
        Collections.shuffle(moveOptions);

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
                ai.setCurrentState(PokemonAI.AIState.WANDERING);
                return; // Move made, exit
            }
        }
    }

    private void signalPackMembers() {
        // Pack members will detect leader movement and follow
        // This is handled in the FollowPackBehavior
        GameLogger.info("Pack leader " + pokemon.getName() + " signals movement");
    }

    @Override
    public boolean canExecute() {
        return ai.hasPersonalityTrait(PokemonPersonalityTrait.PACK_LEADER) &&
            !ai.getPackMembers().isEmpty() &&
            !ai.isOnCooldown(getName());
    }

    @Override
    public int getPriority() {
        return 5;
    }

    @Override
    public String getName() {
        return "pack_leader";
    }
}
