package io.github.pokemeetup.system.gameplay.overworld.entityai.behaviors;

import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonAI;
import io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonPersonalityTrait;
import io.github.pokemeetup.utils.GameLogger;
public class TerritorialBehavior implements PokemonBehavior {
    private final WildPokemon pokemon;
    private final PokemonAI ai;
    private boolean isDefending = false;

    public TerritorialBehavior(WildPokemon pokemon, PokemonAI ai) {
        this.pokemon = pokemon;
        this.ai = ai;
    }

    @Override
    public void execute(float delta) {
        if (!pokemon.isMoving()) {
            Vector2 playerPos = ai.getSafePlayerPosition();
            if (playerPos != null && isPlayerInTerritory(playerPos)) {
                initiateDefense(playerPos);
            } else {
                isDefending = false;
            }
        }
    }

    private boolean isPlayerInTerritory(Vector2 playerPos) {
        Vector2 territory = ai.getTerritoryCenter();
        float distance = Vector2.dst(playerPos.x, playerPos.y, territory.x, territory.y);
        return distance <= ai.getTerritoryRadius();
    }

    private void initiateDefense(Vector2 playerPos) {
        if (!isDefending) {
            GameLogger.info(pokemon.getName() + " is defending its territory!");
            isDefending = true;
        }
        Vector2 territory = ai.getTerritoryCenter();
        int pokemonTileX = (int)(pokemon.getX() / World.TILE_SIZE);
        int pokemonTileY = (int)(pokemon.getY() / World.TILE_SIZE);
        int playerTileX = (int)(playerPos.x / World.TILE_SIZE);
        int playerTileY = (int)(playerPos.y / World.TILE_SIZE);
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

        World world = ai.getSafeWorld();
        if (ai.checkPassable(world, targetTileX, targetTileY)) {
            pokemon.moveToTile(targetTileX, targetTileY, direction);
            ai.setCurrentState(PokemonAI.AIState.APPROACHING);
        }
    }

    @Override
    public boolean canExecute() {
        return ai.hasPersonalityTrait(PokemonPersonalityTrait.TERRITORIAL) &&
            !ai.isOnCooldown(getName());
    }

    @Override
    public int getPriority() {
        return 7; // High priority when defending territory
    }

    @Override
    public String getName() {
        return "territorial_defense";
    }
}

