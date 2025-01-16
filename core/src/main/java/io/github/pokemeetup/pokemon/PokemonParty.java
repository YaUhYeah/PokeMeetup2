package io.github.pokemeetup.pokemon;

import io.github.pokemeetup.pokemon.attacks.Move;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PokemonParty {
    public static final int MAX_PARTY_SIZE = 6;
    private final List<Pokemon> party;
    public final Object partyLock = new Object();

    public PokemonParty() {
        this.party = new ArrayList<>(MAX_PARTY_SIZE);
    }

    public void addPokemon(Pokemon pokemon) {
        synchronized (partyLock) {
            if (party.size() >= MAX_PARTY_SIZE) {
                return;
            }
            party.add(pokemon);
        }
    }public Pokemon getFirstPokemon() {
        synchronized (partyLock) {
            return party.isEmpty() ? null : party.get(0);
        }
    }

    // Add healing method to Pokemon class (if not already present)


    public void clearParty() {
        party.clear();
    }

    public Pokemon removePokemon(int index) {
        synchronized (partyLock) {
            if (index >= 0 && index < party.size()) {
                return party.remove(index);
            }
            return null;
        }
    }

    public void swapPositions(int index1, int index2) {
        synchronized (partyLock) {
            if (index1 >= 0 && index1 < party.size() &&
                index2 >= 0 && index2 < party.size()) {
                Collections.swap(party, index1, index2);
            }
        }
    }

    public Pokemon getPokemon(int index) {
        synchronized (partyLock) {
            if (index >= 0 && index < party.size()) {
                return party.get(index);
            }
            return null;
        }
    }

    public List<Pokemon> getParty() {
        synchronized (partyLock) {
            return new ArrayList<>(party);
        }
    }

    public int getSize() {
        synchronized (partyLock) {
            return party.size();
        }
    }

    public boolean isFull() {
        synchronized (partyLock) {
            return party.size() >= MAX_PARTY_SIZE;
        }
    }

    public void healAllPokemon() {
        synchronized (partyLock) {
            for (Pokemon pokemon : party) {
                // Restore HP and PP
                pokemon.setCurrentHp(pokemon.getStats().getHp());
                for (Move move : pokemon.getMoves()) {
//                    move.restore();
                }
            }
        }
    }
}
