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
            Vector2 playerPos = ai.getSafePlayerPosition();
            if (playerPos != null) {
                if (shouldChasePlayer(playerPos)) {
                    initiateChase(playerPos);
                } else if (isChasing) {
                    endChase();
                }
            }
        }
    }

    private boolean shouldChasePlayer(Vector2 playerPos) {
        Vector2 territory = ai.getTerritoryCenter();
        float playerDistanceFromTerritory = Vector2.dst(playerPos.x, playerPos.y, territory.x, territory.y);
        float pokemonDistanceFromPlayer = Vector2.dst(pokemon.getX(), pokemon.getY(), playerPos.x, playerPos.y);

        return playerDistanceFromTerritory <= ai.getTerritoryRadius() &&
            pokemonDistanceFromPlayer <= CHASE_DISTANCE;
    }

    private void initiateChase(Vector2 playerPos) {
        if (!isChasing) {
            GameLogger.info(pokemon.getName() + " aggressively chases intruder!");
            isChasing = true;
            chaseStepsRemaining = MathUtils.random(3, 6);
        }

        if (chaseStepsRemaining > 0) {
            moveTowardsPlayer(playerPos);
            float distance = Vector2.dst(pokemon.getX(), pokemon.getY(), playerPos.x, playerPos.y);
            if (distance <= 1.5f * World.TILE_SIZE) {
                try {
                    if (GameContext.get().getGameScreen()!=null) {
                        GameLogger.info(pokemon.getName() + " is initiating battle while defending territory!");
                        Gdx.app.postRunnable(() -> {
                            GameContext.get().getGameScreen().forceBattleInitiation(pokemon);
                        });
                        ai.setCooldown(getName(), 15f);
                        endChase(); // End the chase behavior
                        return;
                    }
                } catch (IllegalStateException e) {
                    // Server-side: cannot initiate battles
                }
            }
            chaseStepsRemaining--;
        } else {
            endChase();
        }}
    private void moveTowardsPlayer(Vector2 playerPos) {
        World world = null;
        try {
            world = GameContext.get().getWorld();
        } catch (IllegalStateException e) {
            // Server-side
        }
        if (world == null) return;

        int pokemonTileX = pokemon.getTileX();
        int pokemonTileY = pokemon.getTileY();
        int playerTileX = (int)(playerPos.x / World.TILE_SIZE);
        int playerTileY = (int)(playerPos.y / World.TILE_SIZE);

        int dx = Integer.compare(playerTileX, pokemonTileX);
        int dy = Integer.compare(playerTileY, pokemonTileY);

        if (dx == 0 && dy == 0) return;
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

            if (ai.checkPassable(world, targetTileX, targetTileY)) {
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
