package io.github.pokemeetup.system;

import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerAdapter;
import com.badlogic.gdx.controllers.Controllers;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.system.keybinds.ControllerBinds;
import io.github.pokemeetup.utils.GameLogger;

import static io.github.pokemeetup.system.keybinds.ControllerBinds.*;

public class ControllerInputProcessor extends ControllerAdapter {

    private final InputManager inputManager;
    private final InputHandler inputHandler;
    private final float axisThreshold = 0.5f;  // deadzone for analog sticks
    private final float povDeadzone = 0.2f;      // deadzone for D-pad axis polling

    public ControllerInputProcessor(InputManager inputManager, InputHandler inputHandler) {
        this.inputManager = inputManager;
        this.inputHandler = inputHandler;

        // Register ourselves as a controller listener
        Controllers.addListener(this);

        // Log all connected controllers
        for (Controller c : Controllers.getControllers()) {
            GameLogger.info("Controller detected: " + c.getName());
        }
    }

    @Override
    public boolean buttonDown(Controller controller, int buttonCode) {
        GameLogger.info("ButtonDown on " + controller.getName() + " code: " + buttonCode);

        // --- Handle movement buttons (for XInput controllers the D-pad is sent as buttons) ---
        if (buttonCode == ControllerBinds.getBinding(MOVE_UP)) {
            inputHandler.moveUp(true);
            return true;
        }
        if (buttonCode == ControllerBinds.getBinding(MOVE_DOWN)) {
            inputHandler.moveDown(true);
            return true;
        }
        if (buttonCode == ControllerBinds.getBinding(MOVE_LEFT)) {
            inputHandler.moveLeft(true);
            return true;
        }
        if (buttonCode == ControllerBinds.getBinding(MOVE_RIGHT)) {
            inputHandler.moveRight(true);
            return true;
        }

        // --- Handle other actions ---
        if (buttonCode == ControllerBinds.getBinding(ACTION)) {
            inputHandler.startChopOrPunch();
        } else if (buttonCode == ControllerBinds.getBinding(INTERACT)) {
            inputHandler.handleInteraction();
        } else if (buttonCode == ControllerBinds.getBinding(INVENTORY)) {
            if (inputManager.getCurrentState() == InputManager.UIState.INVENTORY ||
                inputManager.getCurrentState() == InputManager.UIState.CRAFTING ||
                inputManager.getCurrentState() == InputManager.UIState.CHEST_SCREEN) {
                inputManager.setUIState(InputManager.UIState.NORMAL);
            } else if (inputManager.getCurrentState() == InputManager.UIState.NORMAL) {
                inputManager.setUIState(InputManager.UIState.INVENTORY);
            }
        } else if (buttonCode == ControllerBinds.getBinding(BUILD_MODE)) {
            inputHandler.toggleBuildMode();
        } else if (buttonCode == ControllerBinds.getBinding(SPRINT)) {
            inputHandler.setRunning(true);
        } else if (buttonCode == ControllerBinds.getBinding(BREAK)) {
            var block = inputHandler.findBreakableBlock();
            if (block != null) {
                inputHandler.startBreaking(block);
            }
        } else if (buttonCode == ControllerBinds.getBinding(PLACE)) {
            inputHandler.handleBlockPlacement();
        }
        // Example: Some controllers use button 7 as “Start”
        else if (buttonCode == 7) {
            if (inputManager.getCurrentState() != InputManager.UIState.MENU) {
                inputManager.setUIState(InputManager.UIState.MENU);
            } else {
                inputManager.setUIState(InputManager.UIState.NORMAL);
            }
        }
        return true;
    }

    @Override
    public boolean buttonUp(Controller controller, int buttonCode) {
        GameLogger.info("ButtonUp on " + controller.getName() + " code: " + buttonCode);
        // --- Handle movement buttons for XInput controllers ---
        if (buttonCode == ControllerBinds.getBinding(MOVE_UP)) {
            inputHandler.moveUp(false);
            return true;
        }
        if (buttonCode == ControllerBinds.getBinding(MOVE_DOWN)) {
            inputHandler.moveDown(false);
            return true;
        }
        if (buttonCode == ControllerBinds.getBinding(MOVE_LEFT)) {
            inputHandler.moveLeft(false);
            return true;
        }
        if (buttonCode == ControllerBinds.getBinding(MOVE_RIGHT)) {
            inputHandler.moveRight(false);
            return true;
        }

        if (buttonCode == ControllerBinds.getBinding(SPRINT)) {
            inputHandler.setRunning(false);
        }
        return true;
    }

    @Override
    public boolean axisMoved(Controller controller, int axisIndex, float value) {
        GameLogger.info("AxisMoved on " + controller.getName() + " axis: " + axisIndex + " value: " + value);
        // For analog stick movement, assume axes 0 and 1 control the left stick.
        if (axisIndex == 0) {
            if (value > axisThreshold) {
                inputHandler.moveRight(true);
                inputHandler.moveLeft(false);
                GameContext.get().getPlayer().setInputHeld(true);
            } else if (value < -axisThreshold) {
                inputHandler.moveLeft(true);
                inputHandler.moveRight(false);
                GameContext.get().getPlayer().setInputHeld(true);
            } else {
                inputHandler.moveRight(false);
                inputHandler.moveLeft(false);
                GameContext.get().getPlayer().setInputHeld(false);
            }
        } else if (axisIndex == 1) {
            if (value < -axisThreshold) { // negative Y usually means up
                inputHandler.moveUp(true);
                inputHandler.moveDown(false);
                GameContext.get().getPlayer().setInputHeld(true);
            } else if (value > axisThreshold) { // positive Y means down
                inputHandler.moveDown(true);
                inputHandler.moveUp(false);
                GameContext.get().getPlayer().setInputHeld(true);
            } else {
                inputHandler.moveUp(false);
                inputHandler.moveDown(false);
                GameContext.get().getPlayer().setInputHeld(false);
            }
        }
        // Leave D-pad polling to update() for non-XInput controllers.
        return true;
    }

    @Override
    public void connected(Controller controller) {
        GameLogger.info("Controller connected: " + controller.getName());
    }

    @Override
    public void disconnected(Controller controller) {
        GameLogger.info("Controller disconnected: " + controller.getName());
    }

    /**
     * Poll controllers for D-pad input by reading axes 6 & 7.
     * For XInput controllers, the D-pad is sent as buttons so skip polling.
     * Call this from your GameScreen render() or update() method.
     */
    public void update() {
        for (Controller c : Controllers.getControllers()) {
            // If controller name indicates XInput, skip axis polling for D-pad
            if (c.getName().contains("XInput")) {
                continue;
            }
            float dpadX = c.getAxis(6);
            float dpadY = c.getAxis(7);
            PovDirection pov = getPovFromAxes(dpadX, dpadY);
            povMoved(c, 0, pov);
        }
    }

    public boolean povMoved(Controller controller, int povCode, PovDirection value) {
        GameLogger.info("POV moved on " + controller.getName() + " value: " + value);
        // This callback handles D-pad input for non-XInput controllers.
        if (value == PovDirection.north) {
            inputHandler.moveUp(true);
            inputHandler.moveDown(false);
            GameContext.get().getPlayer().setInputHeld(true);
        } else if (value == PovDirection.south) {
            inputHandler.moveDown(true);
            inputHandler.moveUp(false);
            GameContext.get().getPlayer().setInputHeld(true);
        } else if (value == PovDirection.east) {
            inputHandler.moveRight(true);
            inputHandler.moveLeft(false);
            GameContext.get().getPlayer().setInputHeld(true);
        } else if (value == PovDirection.west) {
            inputHandler.moveLeft(true);
            inputHandler.moveRight(false);
            GameContext.get().getPlayer().setInputHeld(true);
        } else { // center or undefined
            inputHandler.moveUp(false);
            inputHandler.moveDown(false);
            inputHandler.moveLeft(false);
            inputHandler.moveRight(false);
            GameContext.get().getPlayer().setInputHeld(false);
        }
        return true;
    }

    /**
     * Converts raw D-pad axis values (x and y) into a PovDirection.
     * Adjust the deadzone as needed.
     *
     * @param x The x-axis value from the D-pad.
     * @param y The y-axis value from the D-pad.
     * @return The computed PovDirection.
     */
    private PovDirection getPovFromAxes(float x, float y) {
        if (Math.abs(x) < povDeadzone && Math.abs(y) < povDeadzone) {
            return PovDirection.center;
        }
        if (y > povDeadzone && Math.abs(x) < povDeadzone) {
            return PovDirection.north;
        }
        if (y < -povDeadzone && Math.abs(x) < povDeadzone) {
            return PovDirection.south;
        }
        if (x > povDeadzone && Math.abs(y) < povDeadzone) {
            return PovDirection.east;
        }
        if (x < -povDeadzone && Math.abs(y) < povDeadzone) {
            return PovDirection.west;
        }
        if (x > povDeadzone && y > povDeadzone) {
            return PovDirection.northEast;
        }
        if (x < -povDeadzone && y > povDeadzone) {
            return PovDirection.northWest;
        }
        if (x > povDeadzone && y < -povDeadzone) {
            return PovDirection.southEast;
        }
        if (x < -povDeadzone && y < -povDeadzone) {
            return PovDirection.southWest;
        }
        return PovDirection.center;
    }
}
