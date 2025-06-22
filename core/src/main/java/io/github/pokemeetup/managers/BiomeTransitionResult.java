package io.github.pokemeetup.managers;

import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import java.util.Objects;

/**
 * Represents a biome transition result that can contain either a single biome
 * or a transition between two biomes with a blend factor.
 *
 * This class is immutable and thread-safe, following best practices for
 * data transfer objects in concurrent environments.
 *
 * @author PokeMeetup Team
 * @version 2.0
 */
public final class BiomeTransitionResult {

    private final Biome primaryBiome;
    private final Biome secondaryBiome;
    private final float transitionFactor;

    /**
     * Creates a biome transition result.
     *
     * @param primaryBiome The primary (dominant) biome, must not be null
     * @param secondaryBiome The secondary biome for transitions, can be null for single biome
     * @param transitionFactor The blend factor (0.0 = full secondary, 1.0 = full primary)
     * @throws IllegalArgumentException if primaryBiome is null or transitionFactor is out of range
     */
    public BiomeTransitionResult(Biome primaryBiome, Biome secondaryBiome, float transitionFactor) {
        if (primaryBiome == null) {
            throw new IllegalArgumentException("Primary biome cannot be null");
        }
        if (transitionFactor < 0.0f || transitionFactor > 1.0f) {
            throw new IllegalArgumentException("Transition factor must be between 0.0 and 1.0, got: " + transitionFactor);
        }

        this.primaryBiome = primaryBiome;
        this.secondaryBiome = secondaryBiome;
        this.transitionFactor = transitionFactor;
    }

    /**
     * Creates a single-biome result with no transition.
     *
     * @param biome The single biome
     * @return A BiomeTransitionResult with only the primary biome
     */
    public static BiomeTransitionResult single(Biome biome) {
        return new BiomeTransitionResult(biome, null, 1.0f);
    }

    /**
     * Creates a transition between two biomes.
     *
     * @param primary The primary biome
     * @param secondary The secondary biome
     * @param factor The transition factor (0.0 = full secondary, 1.0 = full primary)
     * @return A BiomeTransitionResult representing the transition
     */
    public static BiomeTransitionResult transition(Biome primary, Biome secondary, float factor) {
        return new BiomeTransitionResult(primary, secondary, factor);
    }

    /**
     * Gets the primary biome.
     *
     * @return The primary biome, never null
     */
    public Biome getPrimaryBiome() {
        return primaryBiome;
    }

    /**
     * Gets the secondary biome if this represents a transition.
     *
     * @return The secondary biome, or null if this is a single biome
     */
    public Biome getSecondaryBiome() {
        return secondaryBiome;
    }

    /**
     * Gets the transition factor between biomes.
     *
     * @return The transition factor (0.0 = full secondary, 1.0 = full primary)
     */
    public float getTransitionFactor() {
        return transitionFactor;
    }

    /**
     * Checks if this result represents a transition between two biomes.
     *
     * @return true if this is a transition, false if single biome
     */
    public boolean isTransition() {
        return secondaryBiome != null;
    }

    /**
     * Gets the effective biome based on the transition factor.
     * Returns primary if factor >= 0.5, otherwise secondary.
     *
     * @return The dominant biome based on transition factor
     */
    public Biome getEffectiveBiome() {
        if (!isTransition() || transitionFactor >= 0.5f) {
            return primaryBiome;
        }
        return secondaryBiome;
    }

    /**
     * Calculates the weight of the primary biome in the transition.
     *
     * @return The weight of the primary biome (same as transition factor)
     */
    public float getPrimaryWeight() {
        return transitionFactor;
    }

    /**
     * Calculates the weight of the secondary biome in the transition.
     *
     * @return The weight of the secondary biome (1 - transition factor)
     */
    public float getSecondaryWeight() {
        return 1.0f - transitionFactor;
    }

    /**
     * Inverts the transition, swapping primary and secondary biomes.
     *
     * @return A new BiomeTransitionResult with swapped biomes
     */
    public BiomeTransitionResult invert() {
        if (!isTransition()) {
            return this;
        }
        return new BiomeTransitionResult(secondaryBiome, primaryBiome, 1.0f - transitionFactor);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BiomeTransitionResult that = (BiomeTransitionResult) o;

        return Float.compare(that.transitionFactor, transitionFactor) == 0 &&
            Objects.equals(primaryBiome, that.primaryBiome) &&
            Objects.equals(secondaryBiome, that.secondaryBiome);
    }

    @Override
    public int hashCode() {
        return Objects.hash(primaryBiome, secondaryBiome, transitionFactor);
    }

    @Override
    public String toString() {
        if (isTransition()) {
            return String.format("BiomeTransition[%s -> %s @ %.2f]",
                primaryBiome.getName(),
                secondaryBiome.getName(),
                transitionFactor);
        } else {
            return String.format("BiomeTransition[%s]", primaryBiome.getName());
        }
    }
}
