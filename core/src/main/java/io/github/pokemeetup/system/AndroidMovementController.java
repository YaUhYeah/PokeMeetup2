// File: src/main/java/io/github/pokemeetup/system/AndroidMovementController.java
package io.github.pokemeetup.system;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.MathUtils;

public class AndroidMovementController {
    private static final float DEADZONE = 0.2f;
    private static final float MAX_JOYSTICK_RADIUS = 100f;

    private final InputHandler inputHandler;
    private final Vector2 joystickCenter = new Vector2();
    private final Vector2 joystickCurrent = new Vector2();
    private final Vector2 movementVector = new Vector2();
    private boolean isActive = false;

    public AndroidMovementController(Player player, InputHandler inputHandler) {
        this.inputHandler = inputHandler;
    }

    public void handleTouchDown(float x, float y) {
        joystickCenter.set(x, y);
        joystickCurrent.set(x, y);
        isActive = true;
        updateJoystick();
    }

    public void handleTouchDragged(float x, float y) {
        if (!isActive) return;

        joystickCurrent.set(x, y);

        // [FIX] Clamp the joystick knob to stay within the max radius
        Vector2 diff = new Vector2(joystickCurrent).sub(joystickCenter);
        if (diff.len() > MAX_JOYSTICK_RADIUS) {
            diff.setLength(MAX_JOYSTICK_RADIUS);
            joystickCurrent.set(joystickCenter).add(diff);
        }

        updateJoystick();
    }

    public void handleTouchUp() {
        isActive = false;
        inputHandler.resetMovementFlags(); // [FIX] This clears all directional flags
    }

    // [MODIFIED] This method no longer controls the player directly.
    // It now sets boolean flags in the InputHandler, just like the keyboard.
    private void updateJoystick() {
        if (!isActive) return;

        movementVector.set(joystickCurrent).sub(joystickCenter);
        float magnitude = movementVector.len() / MAX_JOYSTICK_RADIUS;

        if (magnitude < DEADZONE) {
            inputHandler.resetMovementFlags();
            return;
        }

        movementVector.nor(); // Normalize to get a direction vector

        float angle = movementVector.angleDeg();

        // Reset all directions before setting the new one
        inputHandler.moveUp(false);
        inputHandler.moveDown(false);
        inputHandler.moveLeft(false);
        inputHandler.moveRight(false);

        // Determine the primary direction and set the corresponding flag
        if (angle > 45 && angle <= 135) {
            inputHandler.moveUp(true);
        } else if (angle > 135 && angle <= 225) {
            inputHandler.moveLeft(true);
        } else if (angle > 225 && angle <= 315) {
            inputHandler.moveDown(true);
        } else {
            inputHandler.moveRight(true);
        }

        // [FIX] Set running state based on how far the joystick is pushed
        inputHandler.setRunning(magnitude > 0.8f);
    }

    // These getters are for the GameScreen to render the joystick UI
    public boolean isActive() { return isActive; }
    public Vector2 getJoystickCenter() { return joystickCenter; }
    public Vector2 getJoystickCurrent() { return joystickCurrent; }
    public float getMaxRadius() { return MAX_JOYSTICK_RADIUS; }
    public float getMagnitude() {
        return MathUtils.clamp(movementVector.len() / MAX_JOYSTICK_RADIUS, 0, 1);
    }

    // [REMOVED] The `update()` method is no longer needed here, as the `InputHandler`'s update loop now drives player movement.
}
