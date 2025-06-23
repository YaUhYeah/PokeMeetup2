package io.github.pokemeetup.system.gameplay.overworld.entityai.behaviors;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.battle.BattleInitiationHandler;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonAI;
import io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonPersonalityTrait;
import io.github.pokemeetup.utils.GameLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
            float distance = Vector2.dst(pokemon.getX(), pokemon.getY(), player.getX(), player.getY());
            if (distance <= 1.5f * World.TILE_SIZE) {
                if (GameContext.get().getGameScreen() != null && !GameContext.get().getBattleSystem().isInBattle()) {
                    GameLogger.info(pokemon.getName() + " is initiating battle while defending territory!");
                    Gdx.app.postRunnable(() -> {
                        ((BattleInitiationHandler) GameContext.get().getGameScreen()).forceBattleInitiation(pokemon);
                    });
                    ai.setCooldown(getName(), 15f);
                    endChase(); // End the chase behavior
                    return;
                }
            }
            chaseStepsRemaining--;
        } else {
            endChase();
        }}
    private void moveTowardsPlayer(Player player) {
        World world = GameContext.get().getWorld();
        if (world == null) return;

        int pokemonTileX = pokemon.getTileX();
        int pokemonTileY = pokemon.getTileY();
        int playerTileX = player.getTileX();
        int playerTileY = player.getTileY();

        int dx = Integer.compare(playerTileX, pokemonTileX);
        int dy = Integer.compare(playerTileY, pokemonTileY);

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
                ai.setCurrentState(PokemonAI.AIState.APPROACHING);
                return; // Move made, exit
            }
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
