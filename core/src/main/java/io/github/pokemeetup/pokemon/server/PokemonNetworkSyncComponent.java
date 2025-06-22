package io.github.pokemeetup.pokemon.server;

import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.pokemon.WildPokemon;

/**
 * Handles network synchronization for WildPokemon entities,
 * providing smooth interpolation between server updates.
 */
public class PokemonNetworkSyncComponent {
    private final WildPokemon pokemon;

    // Interpolation settings
    private static final float INTERPOLATION_SPEED = 5.0f; // Lower for smoother but slower adjustments
    private static final float POSITION_THRESHOLD = 0.1f; // Distance threshold to consider positions equal

    // Network state
    private Vector2 serverPosition = new Vector2();
    private String serverDirection = "down";
    private boolean serverMoving = false;
    private long lastUpdateTime = 0;

    // Interpolation state
    private Vector2 interpolationStart = new Vector2();
    private Vector2 interpolationTarget = new Vector2();
    private float interpolationProgress = 1.0f;

    // Smoothing state
    private boolean isInterpolating = false;

    public PokemonNetworkSyncComponent(WildPokemon pokemon) {
        this.pokemon = pokemon;
        this.serverPosition.set(pokemon.getX(), pokemon.getY());
        this.interpolationStart.set(pokemon.getX(), pokemon.getY());
        this.interpolationTarget.set(pokemon.getX(), pokemon.getY());
    }

    /**
     * Process a network update received from the server
     */
    public void processNetworkUpdate(float x, float y, String direction, boolean isMoving, long timestamp) {
        // Ignore outdated updates
        if (timestamp <= lastUpdateTime) {
            return;
        }

        lastUpdateTime = timestamp;
        serverPosition.set(x, y);
        serverDirection = direction;
        serverMoving = isMoving;

        // If we're more than POSITION_THRESHOLD away from the target, start interpolating
        if (Vector2.dst(pokemon.getX(), pokemon.getY(), x, y) > POSITION_THRESHOLD) {
            startInterpolation(x, y);
        }

        // Always update the direction and movement state immediately
        pokemon.setDirection(direction);
        pokemon.setMoving(isMoving);
    }

    /**
     * Start interpolating to a new position
     */
    private void startInterpolation(float x, float y) {
        interpolationStart.set(pokemon.getX(), pokemon.getY());
        interpolationTarget.set(x, y);
        interpolationProgress = 0.0f;
        isInterpolating = true;
    }

    /**
     * Update the interpolation (called every frame)
     */
    public void update(float deltaTime) {
        if (!isInterpolating) {
            return;
        }

        // Advance interpolation progress
        interpolationProgress += deltaTime * INTERPOLATION_SPEED;

        // Apply smooth step function for more natural movement
        float t = calculateSmoothStep(interpolationProgress);

        // Calculate interpolated position
        float x = interpolationStart.x + (interpolationTarget.x - interpolationStart.x) * t;
        float y = interpolationStart.y + (interpolationTarget.y - interpolationStart.y) * t;

        // Apply the interpolated position to the Pokemon
        pokemon.setX(x);
        pokemon.setY(y);

        // If we're done interpolating, snap to final position
        if (interpolationProgress >= 1.0f) {
            pokemon.setX(interpolationTarget.x);
            pokemon.setY(interpolationTarget.y);
            isInterpolating = false;
        }

        // Update the bounding box with the new position
        pokemon.updateBoundingBox();
    }

    /**
     * Calculate a smooth step function for more natural movement
     */
    private float calculateSmoothStep(float x) {
        x = Math.min(1.0f, Math.max(0.0f, x));
        return x * x * (3 - 2 * x);
    }

    /**
     * Returns true if this Pokemon is currently being interpolated
     */
    public boolean isInterpolating() {
        return isInterpolating;
    }

    /**
     * Gets the server's canonical position of this Pokemon
     */
    public Vector2 getServerPosition() {
        return serverPosition;
    }
}
