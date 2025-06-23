package io.github.pokemeetup.system;

import com.badlogic.gdx.math.Vector2;

/**
 * A minimal interface for any object that has a position and a moving state.
 */
public interface Positionable {
    float getX();
    float getY();
    void update(float deltaTime);
    Vector2 getPosition();
    boolean isMoving();
    void setCharacterType(String characterType);

    // Methods for water interaction state
    boolean wasOnWater();
    void setWasOnWater(boolean onWater);
    float getWaterSoundTimer();
    void setWaterSoundTimer(float timer);
    void updateWaterSoundTimer(float delta);
}
