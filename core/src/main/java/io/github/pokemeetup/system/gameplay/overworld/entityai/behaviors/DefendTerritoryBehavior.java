package io.github.pokemeetup.system.gameplay.overworld.entityai.behaviors;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonAI;
import io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonPersonalityTrait;
import io.github.pokemeetup.utils.GameLogger;

// Defend Territory Behavior - More aggressive territory defense
public class DefendTerritoryBehavior implements PokemonBehavior {
    private static final float CHASE_DISTANCE = 20.0f * World.TILE_SIZE;

    private final WildPokemon pokemon;
    private final PokemonAI ai;
    private boolean isChasing = false;
    private int chaseStepsRemaining = 0;

    public DefendTerritoryBehavior(WildPokemon pokemon, PokemonAI ai) {
        this.pokemon = pokemon;
        this.ai = ai;
    }

    @Override
    public void execute(float delta) {
        if (!pokemon.isMoving()) {
            Player player = GameContext.get().getPlayer();
            if (player != null) {
                if (shouldChasePlayer(player)) {
                    initiateChase(player);
                } else if (isChasing) {
                    endChase();
                }
            }
        }
    }

    private boolean shouldChasePlayer(Player player) {
        Vector2 territory = ai.getTerritoryCenter();
        float playerDistanceFromTerritory = Vector2.dst(player.getX(), player.getY(), territory.x, territory.y);
        float pokemonDistanceFromPlayer = Vector2.dst(pokemon.getX(), pokemon.getY(), player.getX(), player.getY());

        return playerDistanceFromTerritory <= ai.getTerritoryRadius() &&
            pokemonDistanceFromPlayer <= CHASE_DISTANCE;
    }

    private void initiateChase(Player player) {
        if (!isChasing) {
            GameLogger.info(pokemon.getName() + " aggressively chases intruder!");
            isChasing = true;
            chaseStepsRemaining = MathUtils.random(3, 6);
        }

        if (chaseStepsRemaining > 0) {
            moveTowardsPlayer(player);
            chaseStepsRemaining--;
        } else {
            endChase();
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
            ai.setCurrentState(PokemonAI.AIState.APPROACHING);
        }
    }

    private void endChase() {
        isChasing = false;
        chaseStepsRemaining = 0;
        ai.setCooldown(getName(), 8.0f);
        GameLogger.info(pokemon.getName() + " stops chasing");
    }

    @Override
    public boolean canExecute() {
        return ai.hasPersonalityTrait(PokemonPersonalityTrait.TERRITORIAL) &&
            ai.hasPersonalityTrait(PokemonPersonalityTrait.AGGRESSIVE) &&
            !ai.isOnCooldown(getName());
    }

    @Override
    public int getPriority() {
        return 9; // Very high priority
    }

    @Override
    public String getName() {
        return "defend_territory";
    }
}
