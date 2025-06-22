// Base Behavior Classes
package io.github.pokemeetup.system.gameplay.overworld.entityai.behaviors;

import com.badlogic.gdx.math.MathUtils;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonAI;

// Idle Behavior
public class IdleBehavior implements PokemonBehavior {
    private static final float MIN_IDLE_TIME = 1.0f;
    private static final float MAX_IDLE_TIME = 3.0f;

    private final WildPokemon pokemon;
    private final PokemonAI ai;
    private float idleDuration;

    public IdleBehavior(WildPokemon pokemon, PokemonAI ai) {
        this.pokemon = pokemon;
        this.ai = ai;
        this.idleDuration = MathUtils.random(MIN_IDLE_TIME, MAX_IDLE_TIME);
    }

    @Override
    public void execute(float delta) {
        if (ai.getStateTimer() >= idleDuration) {
            idleDuration = MathUtils.random(MIN_IDLE_TIME, MAX_IDLE_TIME);
            ai.setCurrentState(PokemonAI.AIState.IDLE);
        }
    }

    @Override
    public boolean canExecute() {
        return !pokemon.isMoving();
    }

    @Override
    public int getPriority() {
        return 1; // Lowest priority - default behavior
    }

    @Override
    public String getName() {
        return "idle";
    }
}

// Additional behaviors would go here (TerritorialBehavior, DefendTerritoryBehavior, PackLeaderBehavior, FollowPackBehavior)
// These follow the same pattern but with specific logic for their behaviors
