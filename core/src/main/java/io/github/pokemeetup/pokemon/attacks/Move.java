package io.github.pokemeetup.pokemon.attacks;

import io.github.pokemeetup.pokemon.Pokemon;

import java.util.HashMap;
import java.util.Map;

public class Move {
    private String name;
    private Pokemon.PokemonType type;
    private int power;
    private int accuracy;
    private int pp;
    private int maxPp;

    public void setName(String name) {
        this.name = name;
    }

    public void setType(Pokemon.PokemonType type) {
        this.type = type;
    }

    public void setPower(int power) {
        this.power = power;
    }

    public void setAccuracy(int accuracy) {
        this.accuracy = accuracy;
    }

    public void setPp(int pp) {
        this.pp = pp;
    }

    public void setMaxPp(int maxPp) {
        this.maxPp = maxPp;
    }

    public void setSpecial(boolean special) {
        isSpecial = special;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setEffect(MoveEffect effect) {
        this.effect = effect;
    }

    public void setCanFlinch(boolean canFlinch) {
        this.canFlinch = canFlinch;
    }

    private boolean isSpecial;
    private String description;

    private MoveEffect effect;
    private boolean canFlinch;

    // Constructors, Getters, and Setters

    public Move() {
        // Default constructor
    }

    public String getName() {
        return name;
    }

    public Pokemon.PokemonType getType() {
        return type;
    }

    public int getPower() {
        return power;
    }

    public int getAccuracy() {
        return accuracy;
    }

    public int getPp() {
        return pp;
    }

    public int getMaxPp() {
        return maxPp;
    }

    public boolean isSpecial() {
        return isSpecial;
    }

    public String getDescription() {
        return description;
    }

    public MoveEffect getEffect() {
        return effect;
    }

    public boolean canFlinch() {
        return canFlinch;
    }

    public static class MoveEffect {
        private Pokemon.Status statusEffect;
        private Map<String, Integer> statModifiers;
        private String effectType;
        private float chance;
        private String animation;
        private String sound;
        private int duration;       // Added duration field

        public MoveEffect() {
            this.statModifiers = new HashMap<>();
        }
        public int getDuration() {
            return duration;
        }

        public void setDuration(int duration) {
            this.duration = duration;
        }


        // Getters and Setters

        public Pokemon.Status getStatusEffect() {
            return statusEffect;
        }

        public void setStatusEffect(Pokemon.Status statusEffect) {
            this.statusEffect = statusEffect;
        }

        public Map<String, Integer> getStatModifiers() {
            return statModifiers;
        }

        public void setStatModifiers(Map<String, Integer> statModifiers) {
            this.statModifiers = statModifiers;
        }

        public String getEffectType() {
            return effectType;
        }

        public void setEffectType(String effectType) {
            this.effectType = effectType;
        }

        public float getChance() {
            return chance;
        }

        public void setChance(float chance) {
            this.chance = chance;
        }

        public String getAnimation() {
            return animation;
        }

        public void setAnimation(String animation) {
            this.animation = animation;
        }

        public String getSound() {
            return sound;
        }

        public void setSound(String sound) {
            this.sound = sound;
        }
    }

    // Builder pattern for move creation
    public static class Builder {
        private final Move move;

        public Builder(String name, Pokemon.PokemonType type) {
            move = new Move();
            move.name = name;
            move.type = type;
        }

        public Builder power(int power) {
            move.power = power;
            return this;
        }

        public Builder accuracy(int accuracy) {
            move.accuracy = accuracy;
            return this;
        }

        public Builder pp(int pp) {
            move.pp = pp;
            move.maxPp = pp;
            return this;
        }

        public Builder special(boolean isSpecial) {
            move.isSpecial = isSpecial;
            return this;
        }

        public Builder description(String description) {
            move.description = description;
            return this;
        }

        public Builder effect(MoveEffect effect) {
            move.effect = effect;
            return this;
        }

        public Builder canFlinch(boolean canFlinch) {
            move.canFlinch = canFlinch;
            return this;
        }

        public Move build() {
            return move;
        }
    }
}
