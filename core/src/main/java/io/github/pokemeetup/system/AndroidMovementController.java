package io.github.pokemeetup.system;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.MathUtils;

public class AndroidMovementController {
    private static final float DEADZONE = 0.2f;
    private static final float MAX_JOYSTICK_RADIUS = 100f;
    private static final float DIRECTION_THRESHOLD = 0.5f;

    private final Player player;
    private final Vector2 joystickCenter;
    private final Vector2 joystickCurrent;
    private final Vector2 movementVector;
    private boolean isActive;
    private String currentDirection;
    private float magnitude;

    public AndroidMovementController(Player player, InputHandler inputHandler) {
        this.player = player;
        this.joystickCenter = new Vector2();
        this.inputHandler = inputHandler;
        this.joystickCurrent = new Vector2();
        this.movementVector = new Vector2();
        this.isActive = false;
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
        updateJoystick();
    }



    private final InputHandler inputHandler;
    private void updateJoystick() {
        movementVector.set(joystickCurrent).sub(joystickCenter);

        // Calculate magnitude (0 to 1)
        magnitude = movementVector.len() / MAX_JOYSTICK_RADIUS;
        magnitude = MathUtils.clamp(magnitude, 0, 1);

        // Apply deadzone
        if (magnitude < DEADZONE) {
            magnitude = 0;
            movementVector.setZero();
            currentDirection = null;
            // Reset movement flags
            resetMovementFlags();
            return;
        }

        // Normalize vector
        movementVector.nor();

        updateDirection();

        // Set running based on magnitude
        if (inputHandler != null) {
            inputHandler.setRunning(magnitude > 0.8f);
        }
    } private void resetMovementFlags() {
        inputHandler.moveUp(false);
        inputHandler.moveDown(false);
        inputHandler.moveLeft(false);
        inputHandler.moveRight(false);
    }
    private void updateDirection() {
        float x = movementVector.x;
        float y = movementVector.y;

        // Reset movement flags
        resetMovementFlags();

        // Determine direction and set flags
        if (Math.abs(x) > DIRECTION_THRESHOLD) {
            if (x > 0) {
                inputHandler.moveRight(true);
            } else {
                inputHandler.moveLeft(true);
            }
        }
        if (Math.abs(y) > DIRECTION_THRESHOLD) {
            if (y > 0) {
                inputHandler.moveUp(true);
            } else {
                inputHandler.moveDown(true);
            }
        }
    }
    public void handleTouchUp() {
        isActive = false;
        movementVector.setZero();
        magnitude = 0;
        currentDirection = null;
        resetMovementFlags();
        if (player != null) {
            player.setMoving(false);
        }
    }

    public void update() {
        if (!isActive || magnitude < DEADZONE || currentDirection == null) {
            return;
        }
        if (!player.isMoving() && currentDirection != null) {
            player.move(currentDirection);
        } else if (player.isMoving()) {
            player.setDirection(currentDirection);
        }
    }

    public Vector2 getJoystickCenter() {
        return joystickCenter;
    }

    public Vector2 getJoystickCurrent() {
        return joystickCurrent;
    }

    public float getMaxRadius() {
        return MAX_JOYSTICK_RADIUS;
    }

    public boolean isActive() {
        return isActive;
    }

    public float getMagnitude() {
        return magnitude;
    }
}
