package io.github.pokemeetup.system.battle;

import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.WildPokemon;

public class BattleResult {
    private final boolean victory;
    private final int experienceGained;
    private final Pokemon playerPokemon;
    private final WildPokemon wildPokemon;

    public BattleResult(boolean victory, Pokemon playerPokemon, WildPokemon wildPokemon) {
        this.victory = victory;
        this.playerPokemon = playerPokemon;
        this.wildPokemon = wildPokemon;
        this.experienceGained = calculateExperience();
    }

    private int calculateExperience() {
        if (!victory) return 0;
        // Basic experience formula based on defeated Pokemon's level
        return (wildPokemon.getLevel() * 3) + 20;
    }

    public boolean isVictory() { return victory; }
    public int getExperienceGained() { return experienceGained; }
    public Pokemon getPlayerPokemon() { return playerPokemon; }
    public WildPokemon getWildPokemon() { return wildPokemon; }
}

