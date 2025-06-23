
package io.github.pokemeetup.system.battle;

import io.github.pokemeetup.pokemon.WildPokemon;

public interface BattleInitiationHandler {
    void handleBattleInitiation();
    void forceBattleInitiation(WildPokemon aggressor);
}
