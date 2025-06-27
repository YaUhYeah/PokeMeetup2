package io.github.pokemeetup.pokemon;

import io.github.pokemeetup.pokemon.attacks.Move;
import io.github.pokemeetup.utils.GameLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PokemonParty {
    public static final int MAX_PARTY_SIZE = 6;
    private final List<Pokemon> party;
    public final Object partyLock = new Object();
    private int activePokemonIndex = 0; // Track which Pokemon is currently active in battle

    public PokemonParty() {
        this.party = new ArrayList<>(MAX_PARTY_SIZE);
    }

    /**
     * Add a Pokemon to the party if there's room
     * @param pokemon The Pokemon to add
     * @return true if added successfully, false if party is full
     */
    public boolean addPokemon(Pokemon pokemon) {
        synchronized (partyLock) {
            if (party.size() >= MAX_PARTY_SIZE) {
                GameLogger.info("Party is full, cannot add " + pokemon.getName());
                return false;
            }
            party.add(pokemon);
            GameLogger.info(pokemon.getName() + " was added to the party!");
            return true;
        }
    }

    /**
     * Get the first healthy Pokemon in the party
     * @return The first Pokemon with HP > 0, or null if none
     */
    public Pokemon getFirstHealthyPokemon() {
        synchronized (partyLock) {
            for (Pokemon pokemon : party) {
                if (pokemon.getCurrentHp() > 0) {
                    return pokemon;
                }
            }
            return null; // No healthy Pokemon found
        }
    }

    /**
     * Get the first Pokemon in the party
     * @return The first Pokemon, or null if party is empty
     */
    public Pokemon getFirstPokemon() {
        synchronized (partyLock) {
            return party.isEmpty() ? null : party.get(0);
        }
    }

    /**
     * Get the currently active Pokemon for battle
     * @return The active Pokemon, or null if none
     */
    public Pokemon getActivePokemon() {
        synchronized (partyLock) {
            if (activePokemonIndex >= 0 && activePokemonIndex < party.size()) {
                return party.get(activePokemonIndex);
            }
            Pokemon healthy = getFirstHealthyPokemon();
            if (healthy != null) {
                activePokemonIndex = party.indexOf(healthy);
                return healthy;
            }
            return null;
        }
    }

    /**
     * Set a Pokemon as the active one for battle
     * @param index The index of the Pokemon to set as active
     * @return true if successful, false if index is invalid
     */
    public boolean setActivePokemon(int index) {
        synchronized (partyLock) {
            if (index >= 0 && index < party.size() && party.get(index).getCurrentHp() > 0) {
                activePokemonIndex = index;
                GameLogger.info(party.get(index).getName() + " is now the active Pokemon!");
                return true;
            }
            return false;
        }
    }

    /**
     * Clear all Pokemon from the party
     */
    public void clearParty() {
        synchronized (partyLock) {
            party.clear();
            activePokemonIndex = 0;
        }
    }

    /**
     * Remove a Pokemon from the party
     * @param index The index of the Pokemon to remove
     * @return The removed Pokemon, or null if index is invalid
     */
    public Pokemon removePokemon(int index) {
        synchronized (partyLock) {
            if (index >= 0 && index < party.size()) {
                Pokemon removed = party.remove(index);
                if (index == activePokemonIndex) {
                    activePokemonIndex = 0; // Reset to first Pokemon
                } else if (index < activePokemonIndex) {
                    activePokemonIndex--; // Adjust index if removed Pokemon was before active one
                }

                return removed;
            }
            return null;
        }
    }

    /**
     * Swap the positions of two Pokemon in the party
     * @param index1 The index of the first Pokemon
     * @param index2 The index of the second Pokemon
     */
    public void swapPositions(int index1, int index2) {
        synchronized (partyLock) {
            if (index1 >= 0 && index1 < party.size() &&
                index2 >= 0 && index2 < party.size()) {
                if (activePokemonIndex == index1) {
                    activePokemonIndex = index2;
                } else if (activePokemonIndex == index2) {
                    activePokemonIndex = index1;
                }

                Collections.swap(party, index1, index2);
                GameLogger.info("Swapped " + party.get(index2).getName() +
                    " with " + party.get(index1).getName());
            }
        }
    }

    /**
     * Get a Pokemon by index
     * @param index The index of the Pokemon to get
     * @return The Pokemon, or null if index is invalid
     */
    public Pokemon getPokemon(int index) {
        synchronized (partyLock) {
            if (index >= 0 && index < party.size()) {
                return party.get(index);
            }
            return null;
        }
    }

    /**
     * Get a shallow copy of all Pokemon in the party
     * @return A list containing all Pokemon in the party
     */
    public List<Pokemon> getParty() {
        synchronized (partyLock) {
            return new ArrayList<>(party);
        }
    }

    /**
     * Get the number of Pokemon in the party
     * @return The party size
     */
    public int getSize() {
        synchronized (partyLock) {
            return party.size();
        }
    }

    /**
     * Check if the party is full
     * @return true if the party has reached MAX_PARTY_SIZE
     */
    public boolean isFull() {
        synchronized (partyLock) {
            return party.size() >= MAX_PARTY_SIZE;
        }
    }

    /**
     * Get the number of healthy Pokemon in the party
     * @return The number of Pokemon with HP > 0
     */
    public int getHealthyPokemonCount() {
        synchronized (partyLock) {
            int count = 0;
            for (Pokemon pokemon : party) {
                if (pokemon.getCurrentHp() > 0) {
                    count++;
                }
            }
            return count;
        }
    }

    /**
     * Heal all Pokemon in the party to full health and cure status conditions
     */
    public void healAllPokemon() {
        synchronized (partyLock) {
            for (Pokemon pokemon : party) {
                pokemon.heal(); // Using the Pokemon class heal method
            }
            GameLogger.info("All Pokemon have been healed!");
        }
    }

    /**
     * Check if at least one Pokemon in the party is alive
     * @return true if any Pokemon has HP > 0
     */
    public boolean hasAlivePokemon() {
        synchronized (partyLock) {
            for (Pokemon pokemon : party) {
                if (pokemon.getCurrentHp() > 0) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Award experience to the active Pokemon
     * @param amount The amount of experience to award
     * @return true if the active Pokemon leveled up, false otherwise
     */
    public boolean awardExperienceToActive(int amount) {
        synchronized (partyLock) {
            Pokemon active = getActivePokemon();
            if (active != null) {
                int oldLevel = active.getLevel();
                active.addExperience(amount);
                GameLogger.info(active.getName() + " gained " + amount + " experience points!");
                return active.getLevel() > oldLevel; // Return true if leveled up
            }
            return false;
        }
    }
}
