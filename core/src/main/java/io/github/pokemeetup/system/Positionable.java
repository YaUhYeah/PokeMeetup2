package io.github.pokemeetup.system;

import com.badlogic.gdx.math.Vector2;

/**
 * A minimal interface for any object that has a position and a moving state.
 */
public interface Positionable {
    float getX();
    float getY();

    void update(float deltaTime)
    ;

    Vector2 getPosition()
    ;

    boolean isMoving();

    void setCharacterType(String characterType);
}
