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

public class ApproachPlayerBehavior implements PokemonBehavior {
    private static final float APPROACH_RANGE = 10.0f * World.TILE_SIZE;
    private static final float OPTIMAL_DISTANCE = 2.0f * World.TILE_SIZE;
    private static final float ATTACK_RANGE = 1.25f * World.TILE_SIZE;

    private final WildPokemon pokemon;
    private final PokemonAI ai;

    public ApproachPlayerBehavior(WildPokemon pokemon, PokemonAI ai) {
        this.pokemon = pokemon;
        this.ai = ai;
    }

    @Override
    public void execute(float delta) {
        // [FIX] Execute logic only when not already moving to a tile.
        if (pokemon.isMoving()) {
            return;
        }

        Player player = GameContext.get().getPlayer();
        if (player == null) return;

        float distance = Vector2.dst(pokemon.getX(), pokemon.getY(), player.getX(), player.getY());

        // Stop approaching if close enough (for curious pokemon) or if battle is initiated.
        if (!ai.hasPersonalityTrait(PokemonPersonalityTrait.AGGRESSIVE) && distance <= OPTIMAL_DISTANCE) {
            ai.setCurrentState(PokemonAI.AIState.IDLE);
            ai.setCooldown(getName(), 2.0f); // Pause before deciding to approach again.
            return;
        }

        if (ai.hasPersonalityTrait(PokemonPersonalityTrait.AGGRESSIVE) && distance <= ATTACK_RANGE) {
            if (GameContext.get().getGameScreen() != null && !GameContext.get().getBattleSystem().isInBattle()) {
                GameLogger.info(pokemon.getName() + " is initiating battle forcefully!");
                Gdx.app.postRunnable(() -> {
                    ((BattleInitiationHandler) GameContext.get().getGameScreen()).forceBattleInitiation(pokemon);
                });
                ai.setCooldown(getName(), 15f); // Long cooldown after initiating battle.
            }
            return;
        }

        // If not on cooldown from a failed move, attempt to move.
        if (!ai.isOnCooldown(getName())) {
            moveTowardsPlayer(player);
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

        if (dx == 0 && dy == 0) return;

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
                ai.setCurrentState(PokemonAI.AIState.APPROACHING);
                moveMade = true;
                break;
            }
        }

        // [FIX] Only set a cooldown if the Pokemon gets stuck and cannot move.
        if (!moveMade) {
            ai.setCooldown(getName(), 0.5f);
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

        if (ai.hasPersonalityTrait(PokemonPersonalityTrait.AGGRESSIVE)) {
            return distance <= APPROACH_RANGE; // Always try to approach if aggressive and in range
        }

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
