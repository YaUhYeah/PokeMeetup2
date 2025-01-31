package org.discord.utils;

import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;

public class BiomeData {
    private BiomeType primaryBiomeType;
    private BiomeType secondaryBiomeType;
    private float transitionFactor;
    private long generationSeed;
    private long lastModified;

    public BiomeType getPrimaryBiomeType() { return primaryBiomeType; }
    public BiomeType getSecondaryBiomeType() { return secondaryBiomeType; }
    public float getTransitionFactor() { return transitionFactor; }
    public long getGenerationSeed() { return generationSeed; }
    public long getLastModified() { return lastModified; }

    public void setPrimaryBiomeType(BiomeType type) {
        this.primaryBiomeType = type;
        this.lastModified = System.currentTimeMillis();
    }

    public void setSecondaryBiomeType(BiomeType type) {
        this.secondaryBiomeType = type;
        this.lastModified = System.currentTimeMillis();
    }

    public void setTransitionFactor(float factor) {
        this.transitionFactor = factor;
        this.lastModified = System.currentTimeMillis();
    }

    public void setGenerationSeed(long seed) {
        this.generationSeed = seed;
        this.lastModified = System.currentTimeMillis();
    }
}
