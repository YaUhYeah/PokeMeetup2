package io.github.pokemeetup.system;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import io.github.pokemeetup.chat.ChatSystem;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.utils.GameLogger;

public class GlobalInputProcessor extends InputAdapter {
    private final InputManager inputManager;

    public GlobalInputProcessor(InputManager inputManager) {
        this.inputManager = inputManager;
    }

    @Override
    public boolean keyDown(int keycode) {
        ChatSystem chat = GameContext.get().getChatSystem();
        InputManager.UIState currentState = inputManager.getCurrentState();
        if (chat != null && chat.isActive() && keycode == Input.Keys.ESCAPE) {
            chat.deactivateChat();
            return true;
        }
        if ((currentState == InputManager.UIState.NORMAL || currentState == InputManager.UIState.BUILD_MODE)) {
            if (chat != null && !chat.isActive()) {
                if (keycode == Input.Keys.T) {
                    // Activate chat directly from the stable global processor
                    chat.activateChat("");
                    return true; // Event handled
                }
                if (keycode == Input.Keys.SLASH) {
                    chat.activateChat("/");
                    return true; // Event handled
                }
            }
        }
        if (keycode == Input.Keys.ESCAPE) {
            if (currentState != InputManager.UIState.MENU) {
                inputManager.setUIState(InputManager.UIState.MENU);
            } else {
                // MODIFICATION: Instead of forcing NORMAL, return to the previous state.
                inputManager.returnToPreviousState();
            }
            return true; // Event handled
        }
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
        return false;
    }
}
