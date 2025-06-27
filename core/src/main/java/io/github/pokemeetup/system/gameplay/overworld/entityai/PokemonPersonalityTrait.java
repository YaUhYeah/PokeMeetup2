
package io.github.pokemeetup.system.gameplay.overworld.entityai;

public enum PokemonPersonalityTrait {
    AGGRESSIVE(1.5f, 0.8f, 3.0f), // approach factor, flee threshold, detection range multiplier
    TERRITORIAL(1.2f, 0.6f, 2.5f),
    PASSIVE(0.3f, 1.5f, 1.0f),
    CURIOUS(1.1f, 1.0f, 2.0f),
    TIMID(0.1f, 2.0f, 1.5f),
    LAZY(0.5f, 1.0f, 0.8f),
    PACK_LEADER(1.0f, 0.7f, 2.0f),
    FOLLOWER(0.8f, 1.2f, 1.2f),
    SOLITARY(0.6f, 1.0f, 1.0f),
    NOCTURNAL(1.0f, 1.0f, 1.0f),
    DIURNAL(1.0f, 1.0f, 1.0f),
    PROTECTIVE(1.3f, 0.5f, 2.5f);

    public final float approachFactor;
    public final float fleeThreshold;
    public final float detectionRangeMultiplier;

    PokemonPersonalityTrait(float approachFactor, float fleeThreshold, float detectionRangeMultiplier) {
        this.approachFactor = approachFactor;
        this.fleeThreshold = fleeThreshold;
        this.detectionRangeMultiplier = detectionRangeMultiplier;
    }
}
