package io.github.pokemeetup.system;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.utils.GameLogger;

public class GlobalInputProcessor extends InputAdapter {
    private final InputManager inputManager;

    public GlobalInputProcessor(InputManager inputManager) {
        this.inputManager = inputManager;
    }

    @Override
    public boolean keyDown(int keycode) {
        InputManager.UIState currentState = inputManager.getCurrentState();

        // --- FIX: Add chat activation logic here ---
        if (currentState == InputManager.UIState.NORMAL || currentState == InputManager.UIState.BUILD_MODE) {
            if (keycode == Input.Keys.T || keycode == Input.Keys.SLASH) {
                // Directly set the UI state to CHAT.
                inputManager.setUIState(InputManager.UIState.CHAT);

                // If slash was pressed, pre-fill the input field.
                if (keycode == Input.Keys.SLASH && GameContext.get().getChatSystem() != null) {
                    GameContext.get().getChatSystem().prefillField("/");
                }
                return true; // Event handled.
            }
        }
        // --- END FIX ---
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
