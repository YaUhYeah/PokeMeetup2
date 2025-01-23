package io.github.pokemeetup.system.battle;

import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.PokemonParty;
import io.github.pokemeetup.pokemon.WildPokemon;

public class BattleSystemHandler {
    private boolean isInBattle = false;   private boolean attemptingBattle = false;

    public void setAttemptingBattle(boolean attempting) {
        this.attemptingBattle = attempting;
    }

    public boolean isAttemptingBattle() {
        return this.attemptingBattle;
    }
    private WildPokemon lockedPokemon = null;

    public boolean canStartBattle(PokemonParty playerParty) {
        if (playerParty == null) return false;

        if (isInBattle) {
            for (Pokemon pokemon : playerParty.getParty()) {
                if (pokemon != null && pokemon.getCurrentHp() > 0) {
                    return true;
                }
            }
            return false;
        }
        return true; // Allow other actions when not in battle
    }



    // Lock/unlock Pokemon during battle
    public void lockPokemonForBattle(WildPokemon pokemon) {
        if (pokemon != null) {
            pokemon.setMoving(false);
            if (pokemon.getAi() != null) {
                pokemon.getAi().setPaused(true);
            }
            pokemon.setX(pokemon.getX()); // Force position update
            pokemon.setY(pokemon.getY());
            lockedPokemon = pokemon;
        }
    }

    public void unlockPokemon() {
        if (lockedPokemon != null) {
            if (lockedPokemon.getAi() != null) {
                lockedPokemon.getAi().setPaused(false);
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
