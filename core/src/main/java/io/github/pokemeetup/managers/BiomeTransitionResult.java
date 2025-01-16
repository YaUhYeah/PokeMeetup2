package io.github.pokemeetup.managers;

import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;

public class BiomeTransitionResult {
    public final Biome primaryBiome;
    public final Biome secondaryBiome;
    public final float transitionFactor;

    public BiomeTransitionResult(Biome primaryBiome, Biome secondaryBiome, float transitionFactor) {
        this.primaryBiome = primaryBiome;
        this.secondaryBiome = secondaryBiome;
        this.transitionFactor = transitionFactor;
    }

    public Biome getPrimaryBiome() {
        return primaryBiome;
    }

    public Biome getSecondaryBiome() {
        return secondaryBiome;
    }

    public float getTransitionFactor() {
        return transitionFactor;
    }
}
