package io.github.pokemeetup.system.battle;

import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonAI;

public class BattleSystemHandler {
    private boolean isInBattle = false;

    private WildPokemon lockedPokemon = null;


    public void lockPokemonForBattle(WildPokemon pokemon) {
        if (pokemon != null) {
            pokemon.setMoving(false);
            if (pokemon.getAi() instanceof PokemonAI) {
                ((PokemonAI) pokemon.getAi()).setPaused(true);
            }
            pokemon.setX(pokemon.getX());
            pokemon.setY(pokemon.getY());
            lockedPokemon = pokemon;
        }
    }

    public void unlockPokemon() {
        if (lockedPokemon != null) {
            if (lockedPokemon.getAi() instanceof PokemonAI) {
                ((PokemonAI) lockedPokemon.getAi()).setPaused(false);
            }
            lockedPokemon = null;
        }
    }

    public void startBattle() {
        isInBattle = true;
    }

    public void endBattle() {
        isInBattle = false;
        unlockPokemon();
    }

    public boolean isInBattle() {
        return isInBattle;
    }
}
