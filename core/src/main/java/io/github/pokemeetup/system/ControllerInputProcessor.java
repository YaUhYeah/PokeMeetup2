package io.github.pokemeetup.system;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerAdapter;
import com.badlogic.gdx.controllers.Controllers;
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.system.keybinds.ControllerBinds;
import io.github.pokemeetup.utils.GameLogger;
import static io.github.pokemeetup.system.keybinds.ControllerBinds.*; // for convenience

/**
 * Listens for controller events and translates them into game actions.
 * Uses rebindable controller keys defined in ControllerBinds.
 */
public class ControllerInputProcessor extends ControllerAdapter {

    private final InputManager inputManager;
    private final InputHandler inputHandler;
    private final float axisThreshold = 0.5f; // deadzone threshold

    public ControllerInputProcessor(InputManager inputManager, InputHandler inputHandler) {
        this.inputManager = inputManager;
        this.inputHandler = inputHandler;
        Controllers.addListener(this);
    }

    @Override
    public boolean buttonDown(Controller controller, int buttonCode) {
        // Map buttons using ControllerBinds:
        if (buttonCode == ControllerBinds.getBinding(ACTION)) {
            inputHandler.startChopOrPunch();
        } else if (buttonCode == ControllerBinds.getBinding(INTERACT)) {
            inputHandler.handleInteraction();
        } else if (buttonCode == ControllerBinds.getBinding(INVENTORY)) {
            // Toggle inventory similarly to keyboard logic:
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
            PlaceableBlock block = inputHandler.findBreakableBlock();
            if (block != null) {
                inputHandler.startBreaking(block);
            }
        } else if (buttonCode == ControllerBinds.getBinding(PLACE)) {
            inputHandler.handleBlockPlacement();
        } else if (buttonCode == 7) { // Optionally, a hard-coded Start button
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
        if (buttonCode == ControllerBinds.getBinding(SPRINT)) {
            inputHandler.setRunning(false);
        }
        return true;
    }

    @Override
    public boolean axisMoved(Controller controller, int axisIndex, float value) {
        // Map left analog stick axes (axis 0 horizontal, axis 1 vertical)
        if (axisIndex == 0) {
            if (value > axisThreshold) {
                inputHandler.moveRight(true);
                inputHandler.moveLeft(false);
            } else if (value < -axisThreshold) {
                inputHandler.moveLeft(true);
                inputHandler.moveRight(false);
            } else {
                inputHandler.moveRight(false);
                inputHandler.moveLeft(false);
            }
        } else if (axisIndex == 1) {
            if (value < -axisThreshold) { // negative typically means UP
                inputHandler.moveUp(true);
                inputHandler.moveDown(false);
            } else if (value > axisThreshold) { // positive means DOWN
                inputHandler.moveDown(true);
                inputHandler.moveUp(false);
            } else {
                inputHandler.moveUp(false);
                inputHandler.moveDown(false);
            }
        }
        return true;
    }

    /**
     * Although povMoved() may not be auto-called by your controller backend,
     * you can call this method manually from an update loop.
     */
    public boolean povMoved(Controller controller, int povCode, PovDirection value) {
        // Use the custom PovDirection enum.
        if (value == PovDirection.north) {
            inputHandler.moveUp(true);
        } else if (value == PovDirection.south) {
            inputHandler.moveDown(true);
        } else if (value == PovDirection.east) {
            inputHandler.moveRight(true);
        } else if (value == PovDirection.west) {
            inputHandler.moveLeft(true);
        } else if (value == PovDirection.center) {
            inputHandler.moveUp(false);
            inputHandler.moveDown(false);
            inputHandler.moveLeft(false);
            inputHandler.moveRight(false);
        }
        return true;
    }

    /**
     * Call this method periodically to poll for POV changes if your controller
     * backend doesn't fire POV events.
     */
    public void update() {
        for (Controller controller : Controllers.getControllers()) {
            // Assuming your controller backend supports getPov; if not, you might use getAxis(…) for dpad axes.
            PovDirection pov = getControllerPov(controller);
            // Call our povMoved() manually.
            povMoved(controller, 0, pov);
        }
    }

    /**
     * Helper method to retrieve a POV value from the controller.
     * You may need to adjust this depending on your controller backend.
     */
    private PovDirection getControllerPov(Controller controller) {
        // This is a stub. Some backends support controller.getPov(int), others map it to axes.
        // For now, return PovDirection.center.
        return PovDirection.center;
    }

    @Override
    public void connected(Controller controller) {
        GameLogger.info("Controller connected: " + controller.getName());
    }

    @Override
    public void disconnected(Controller controller) {
        GameLogger.info("Controller disconnected: " + controller.getName());
    }
}
