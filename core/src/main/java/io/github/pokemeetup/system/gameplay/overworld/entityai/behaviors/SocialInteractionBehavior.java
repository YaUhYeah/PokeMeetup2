package io.github.pokemeetup.system.gameplay.overworld.entityai.behaviors;

import com.badlogic.gdx.math.MathUtils;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonAI;
import io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonPersonalityTrait;
import io.github.pokemeetup.utils.GameLogger;

import java.util.Collection;
public class SocialInteractionBehavior implements PokemonBehavior {
    private static final float INTERACTION_RANGE = 2.0f * World.TILE_SIZE;
    private static final float SOCIAL_COOLDOWN = 4.0f; // Was 10.0f

    private final WildPokemon pokemon;
    private final PokemonAI ai;

    public SocialInteractionBehavior(WildPokemon pokemon, PokemonAI ai) {
        this.pokemon = pokemon;
        this.ai = ai;
    }

    @Override
    public void execute(float delta) {
        if (!pokemon.isMoving()) {
            WildPokemon nearbyPokemon = findNearbyPokemon();
            if (nearbyPokemon != null) {
                interactWith(nearbyPokemon);
            }
        }
    }

    private WildPokemon findNearbyPokemon() {
        Collection<WildPokemon> nearby = GameContext.get().getWorld()
            .getPokemonSpawnManager().getPokemonInRange(
                pokemon.getX(), pokemon.getY(), INTERACTION_RANGE);

        for (WildPokemon other : nearby) {
            if (!other.getUuid().equals(pokemon.getUuid()) && !other.isMoving()) {
                return other;
            }
        }

        return null;
    }

    private void interactWith(WildPokemon other) {
        if (ai.hasPersonalityTrait(PokemonPersonalityTrait.AGGRESSIVE)) {
            GameLogger.info(pokemon.getName() + " challenges " + other.getName());
        } else if (ai.hasPersonalityTrait(PokemonPersonalityTrait.CURIOUS)) {
            GameLogger.info(pokemon.getName() + " curiously approaches " + other.getName());
        } else {
            GameLogger.info(pokemon.getName() + " peacefully interacts with " + other.getName());
        }

        ai.setCooldown(getName(), SOCIAL_COOLDOWN);
    }

    @Override
    public boolean canExecute() {
        return !ai.hasPersonalityTrait(PokemonPersonalityTrait.SOLITARY) &&
            !ai.isOnCooldown(getName()) &&
            MathUtils.random() < 0.2f; // Was 0.05f (5% chance)
    }

    @Override
    public int getPriority() {
        return 2; // Low priority
    }

    @Override
    public String getName() {
        return "social_interaction";
    }
}
