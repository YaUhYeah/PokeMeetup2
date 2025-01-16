package io.github.pokemeetup.system;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import io.github.pokemeetup.utils.GameLogger;

public class GlobalInputProcessor extends InputAdapter {
    private final InputManager inputManager;

    public GlobalInputProcessor(InputManager inputManager) {
        this.inputManager = inputManager;
    }

    @Override
    public boolean keyDown(int keycode) {
        InputManager.UIState currentState = inputManager.getCurrentState();
        GameLogger.info("GlobalInputProcessor keyDown: keycode=" + keycode + ", currentState=" + currentState);

        // Handle ESCAPE key to toggle game menu
        if (keycode == Input.Keys.ESCAPE) {
            if (currentState != InputManager.UIState.MENU) {
                inputManager.setUIState(InputManager.UIState.MENU);
            } else {
                inputManager.setUIState(InputManager.UIState.NORMAL);
            }
            return true; // Event handled
        }

        // Handle 'E' key to toggle inventory
        if (keycode == Input.Keys.E) {
            if (currentState == InputManager.UIState.INVENTORY ||
                currentState == InputManager.UIState.CRAFTING ||
                currentState == InputManager.UIState.CHEST_SCREEN) {
                inputManager.setUIState(InputManager.UIState.NORMAL);
            } else if (currentState == InputManager.UIState.NORMAL) {
                inputManager.setUIState(InputManager.UIState.INVENTORY);
            }
            return true; // Event handled
        }

        // Do not consume movement keys
        if (keycode == Input.Keys.W || keycode == Input.Keys.A ||
            keycode == Input.Keys.S || keycode == Input.Keys.D ||
            keycode == Input.Keys.UP || keycode == Input.Keys.DOWN ||
            keycode == Input.Keys.LEFT || keycode == Input.Keys.RIGHT) {
            return false; // Let InputHandler process these
        }

        return false; // Do not consume other keys
    }

    @Override
    public boolean keyUp(int keycode) {
        // Do not handle keyUp events for ESCAPE and E keys
        return false;
    }
}
