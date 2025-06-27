package io.github.pokemeetup.pokemon.server;

import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.pokemon.WildPokemon;

/**
 * Handles network synchronization for WildPokemon entities,
 * providing smooth interpolation between server updates.
 */
public class PokemonNetworkSyncComponent {
    private final WildPokemon pokemon;
    private static final float INTERPOLATION_SPEED = 5.0f; // Lower for smoother but slower adjustments
    private static final float POSITION_THRESHOLD = 0.1f; // Distance threshold to consider positions equal
    private Vector2 serverPosition = new Vector2();
    private String serverDirection = "down";
    private boolean serverMoving = false;
    private long lastUpdateTime = 0;
    private Vector2 interpolationStart = new Vector2();
    private Vector2 interpolationTarget = new Vector2();
    private float interpolationProgress = 1.0f;
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
        if (timestamp <= lastUpdateTime) {
            return;
        }

        lastUpdateTime = timestamp;
        serverPosition.set(x, y);
        serverDirection = direction;
        serverMoving = isMoving;
        if (Vector2.dst(pokemon.getX(), pokemon.getY(), x, y) > POSITION_THRESHOLD) {
            startInterpolation(x, y);
        }
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
        interpolationProgress += deltaTime * INTERPOLATION_SPEED;
        float t = calculateSmoothStep(interpolationProgress);
        float x = interpolationStart.x + (interpolationTarget.x - interpolationStart.x) * t;
        float y = interpolationStart.y + (interpolationTarget.y - interpolationStart.y) * t;
        pokemon.setX(x);
        pokemon.setY(y);
        if (interpolationProgress >= 1.0f) {
            pokemon.setX(interpolationTarget.x);
            pokemon.setY(interpolationTarget.y);
            isInterpolating = false;
        }
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
