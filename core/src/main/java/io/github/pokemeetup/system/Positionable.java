package io.github.pokemeetup.system;

/**
 * A minimal interface for any object that has a position and a moving state.
 */
public interface Positionable {
    float getX();
    float getY();
    boolean isMoving();
}
