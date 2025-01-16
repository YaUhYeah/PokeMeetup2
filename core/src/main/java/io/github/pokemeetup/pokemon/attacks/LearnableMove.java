package io.github.pokemeetup.pokemon.attacks;

import io.github.pokemeetup.pokemon.Pokemon;

public class LearnableMove {
    private String moveName;
    private int levelLearned;
    private boolean isStartingMove;
    private Pokemon.PokemonType moveType;
    private int power;
    private int accuracy;
    private int pp;
    private String description;

    public LearnableMove() {
        // Default constructor
    }

    // Getters and setters
    public String getMoveName() {
        return moveName;
    }

    public void setMoveName(String moveName) {
        this.moveName = moveName;
    }

    public int getLevelLearned() {
        return levelLearned;
    }

    public void setLevelLearned(int levelLearned) {
        this.levelLearned = levelLearned;
    }

    public boolean isStartingMove() {
        return isStartingMove;
    }

    public void setStartingMove(boolean startingMove) {
        isStartingMove = startingMove;
    }

    public Pokemon.PokemonType getMoveType() {
        return moveType;
    }

    public void setMoveType(Pokemon.PokemonType moveType) {
        this.moveType = moveType;
    }

    public int getPower() {
        return power;
    }

    public void setPower(int power) {
        this.power = power;
    }

    public int getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(int accuracy) {
        this.accuracy = accuracy;
    }

    public int getPp() {
        return pp;
    }

    public void setPp(int pp) {
        this.pp = pp;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
