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
    private static final float TERRITORY_AGGRESSION_RANGE = 3.0f * World.TILE_SIZE;

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
            Player player = GameContext.get().getPlayer();
            if (player != null && isPlayerInTerritory(player)) {
                initiateDefense(player);
            } else {
                isDefending = false;
            }
        }
    }

    private boolean isPlayerInTerritory(Player player) {
        Vector2 territory = ai.getTerritoryCenter();
        float distance = Vector2.dst(player.getX(), player.getY(), territory.x, territory.y);
        return distance <= ai.getTerritoryRadius();
    }

    private void initiateDefense(Player player) {
        if (!isDefending) {
            GameLogger.info(pokemon.getName() + " is defending its territory!");
            isDefending = true;
        }
        Vector2 territory = ai.getTerritoryCenter();
        int pokemonTileX = (int)(pokemon.getX() / World.TILE_SIZE);
        int pokemonTileY = (int)(pokemon.getY() / World.TILE_SIZE);
        int playerTileX = (int)(player.getX() / World.TILE_SIZE);
        int playerTileY = (int)(player.getY() / World.TILE_SIZE);
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

        World world = GameContext.get().getWorld();
        if (world != null && world.isPassable(targetTileX, targetTileY)) {
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

