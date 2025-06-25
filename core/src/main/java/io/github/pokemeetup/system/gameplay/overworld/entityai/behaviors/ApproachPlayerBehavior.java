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

    private static final float ATTACK_RANGE = 1.25f * World.TILE_SIZE;

    @Override
    public void execute(float delta) {
        if (!pokemon.isMoving()) {
            if (ai.isOnCooldown(getName())) return;
            Player player = GameContext.get().getPlayer();
            if (player != null) {
                float distance = Vector2.dst(pokemon.getX(), pokemon.getY(), player.getX(), player.getY());

                // NEW: Check for forceful battle initiation
                if (ai.hasPersonalityTrait(PokemonPersonalityTrait.AGGRESSIVE) && distance <= ATTACK_RANGE) {
                    // Check if GameScreen is available and not already in battle
                        if (GameContext.get().getGameScreen() != null) {

                            GameLogger.info(pokemon.getName() + " is initiating battle forcefully!");
                            // Post the action to the main game thread to avoid concurrency issues with UI
                            Gdx.app.postRunnable(() -> {
                                ((BattleInitiationHandler) GameContext.get().getGameScreen()).forceBattleInitiation(pokemon);
                            });
                            ai.setCooldown(getName(), 15f); // Cooldown after attempting to battle
                        }
                        return;

                }
                moveTowardsPlayer(player);
            }
        }
    }

    private void moveTowardsPlayer(Player player) {
        World world = GameContext.get().getWorld();
        if (world == null) return;

        int pokemonTileX = pokemon.getTileX();
        int pokemonTileY = pokemon.getTileY();
        int playerTileX = player.getTileX();
        int playerTileY = player.getTileY();

        int dx = Integer.compare(playerTileX, pokemonTileX);
        int dy = Integer.compare(playerTileY, pokemonTileY);

        if (dx == 0 && dy == 0) return; // Already at target

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
                ai.setCooldown(getName(), 0.5f); // Was 1.0f
                return; // Move made, exit
            }
        }
    }

    public boolean canExecute() {
        if (!ai.hasPersonalityTrait(PokemonPersonalityTrait.AGGRESSIVE) &&
            !ai.hasPersonalityTrait(PokemonPersonalityTrait.CURIOUS)) {
            return false;
        }

        Player player = GameContext.get().getPlayer();
        if (player == null) return false;

        float distance = Vector2.dst(pokemon.getX(), pokemon.getY(),
            player.getX(), player.getY());

        // Aggressive pokemon will continue to approach until very close
        if (ai.hasPersonalityTrait(PokemonPersonalityTrait.AGGRESSIVE)) {
            return distance <= APPROACH_RANGE && !ai.isOnCooldown(getName());
        }

        // Curious pokemon will stop at an optimal distance
        return distance <= APPROACH_RANGE && distance > OPTIMAL_DISTANCE && !ai.isOnCooldown(getName()) && MathUtils.random() < (ai.getApproachFactor() * 0.25f);
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
