package io.github.pokemeetup.system;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.InputMultiplexer;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.screens.ChestScreen;
import io.github.pokemeetup.screens.CraftingTableScreen;
import io.github.pokemeetup.screens.GameScreen;
import io.github.pokemeetup.screens.InventoryScreen;
import io.github.pokemeetup.screens.otherui.BuildModeUI;
import io.github.pokemeetup.screens.otherui.GameMenu;
import io.github.pokemeetup.utils.GameLogger;

public class InputManager {
    private final GlobalInputProcessor globalInputProcessor;
    private final GameScreen gameScreen;
    private final InputMultiplexer inputMultiplexer;
    private UIState currentState;
    public InputManager(GameScreen gameScreen) {
        this.gameScreen = gameScreen;
        this.inputMultiplexer = new InputMultiplexer();
        this.currentState = UIState.NORMAL;
        this.globalInputProcessor = new GlobalInputProcessor(this);
        // Initialize input processors based on the initial state
        updateInputProcessors();
    }

    public void setUIState(UIState newState) {
        if (currentState != newState) {
            currentState = newState;
            handleUIStateChange();
            updateInputProcessors();
        }
    }

    public UIState getCurrentState() {
        return currentState;
    }

    private void handleUIStateChange() {
        switch (currentState) {
            case NORMAL:
                hideAllUI();
                break;
            case INVENTORY:
                showInventoryScreen();
                break;
            case BUILD_MODE:
                showBuildModeUI();
                break;
            case CRAFTING:
                showCraftingScreen();
                break;
            case MENU:
                showGameMenu();
                break;
            case CHEST_SCREEN:
                showChestScreen();
                break;
            case BATTLE:
                // Battle UI is managed separately
                break;
            case STARTER_SELECTION:
                // Starter selection is shown during initialization
                break;
        }
        if (gameScreen.getInputHandler() != null) {
            gameScreen.getInputHandler().resetMovementFlags();
        }
    }

    public void hideAllUI() {
        if (gameScreen.getInventoryScreen() != null) {
            gameScreen.getInventoryScreen().hide();
        }
        if (gameScreen.getGameMenu() != null) {
            gameScreen.getGameMenu().hide();
        }
        if (gameScreen.getBuildModeUI() != null) {
            gameScreen.getBuildModeUI().hide();
        }
        if (gameScreen.getCraftingScreen() != null) {
            gameScreen.getCraftingScreen().hide();
        }
        if (gameScreen.getChestScreen() != null) {
            gameScreen.getChestScreen().hide();
        }
    }

    private void showInventoryScreen() {
        if (gameScreen.getInventoryScreen() == null) {
            GameContext.get().setInventoryScreen(new InventoryScreen(GameContext.get().getPlayer(), gameScreen.getSkin(), GameContext.get().getPlayer().getInventory(), gameScreen.getInputManager()));
        }
        gameScreen.getInventoryScreen().show();
    }

    private void showBuildModeUI() {
        if (GameContext.get().getBuildModeUI() == null) {
            GameContext.get().setBuildModeUI(new BuildModeUI(gameScreen.getSkin()));
        }
        GameContext.get().getBuildModeUI().show();
    }

    private void showCraftingScreen() {
        if (gameScreen.getCraftingScreen() == null) {
            gameScreen.setCraftingScreen(new CraftingTableScreen(GameContext.get().getPlayer(), gameScreen.getSkin(), GameContext.get().getWorld(), GameContext.get().getGameClient(), gameScreen, this));
        }
        gameScreen.getCraftingScreen().show();
    }

    private void showGameMenu() {
        if (GameContext.get().getGameMenu() == null) {
                GameContext.get().setGameMenu(new GameMenu(
                gameScreen.getGame(),
                gameScreen.getSkin(),
                this
            ));
        }
        GameContext.get().getGameMenu().show();
    }

    private void showChestScreen() {
        if (gameScreen.getChestScreen() == null) {
            gameScreen.setChestScreen(new ChestScreen(gameScreen.getSkin(), null, null, gameScreen));
        }
        gameScreen.getChestScreen().show();
    }

    public void updateInputProcessors() {
        // Clear any previously added processors.
        inputMultiplexer.clear();

        // 1) ChatSystem first, if it exists and has a Stage
        if (GameContext.get().getChatSystem() != null &&
            GameContext.get().getChatSystem().getStage() != null) {
            inputMultiplexer.addProcessor(
                GameContext.get().getChatSystem().getStage()
            );
        }

        // 2) The main UI Stage (HUD, overlays, etc.)
        if (GameContext.get().getUiStage() != null) {
            inputMultiplexer.addProcessor(GameContext.get().getUiStage());
        }

        // 3) Add the Stage relevant to our current UI state
        switch (currentState) {
            case STARTER_SELECTION:
                // If you have a separate Stage for starter UI, add it here.
                // Otherwise, your starter selection is already a table in the UiStage, so do nothing.
                break;

            case INVENTORY:
                if (GameContext.get().getInventoryScreen() != null) {
                    inputMultiplexer.addProcessor(
                        GameContext.get().getInventoryScreen().getStage()
                    );
                }
                break;

            case CRAFTING:
                if (GameContext.get().getCraftingScreen() != null &&
                    GameContext.get().getCraftingScreen().getStage() != null) {
                    inputMultiplexer.addProcessor(
                        GameContext.get().getCraftingScreen().getStage()
                    );
                }
                break;

            case MENU:
                // If your GameMenu has its own Stage:
                if (GameContext.get().getGameMenu() != null &&
                    GameContext.get().getGameMenu().getStage() != null) {
                    inputMultiplexer.addProcessor(
                        GameContext.get().getGameMenu().getStage()
                    );
                }
                break;

            case CHEST_SCREEN:
                if (gameScreen.getChestScreen() != null &&
                    gameScreen.getChestScreen().getStage() != null) {
                    inputMultiplexer.addProcessor(
                        gameScreen.getChestScreen().getStage()
                    );
                }
                break;

            case BATTLE:
                if (gameScreen.getBattleStage() != null) {
                    inputMultiplexer.addProcessor(gameScreen.getBattleStage());
                }
                break;

            case NORMAL:
            case BUILD_MODE:
                // Nothing special to add here.
                break;
        }

        // 4) The main in‑game InputHandler (movement, chop/punch, etc.)
        if (gameScreen.getInputHandler() != null) {
            inputMultiplexer.addProcessor(gameScreen.getInputHandler());
        }

        // 5) GlobalInputProcessor last (for ESC key, or “always-listen” input)
        inputMultiplexer.addProcessor(globalInputProcessor);

        // Finally, set this multiplexer as the active input processor
        Gdx.input.setInputProcessor(inputMultiplexer);
    }



    public enum UIState {
        NORMAL,
        INVENTORY,
        BUILD_MODE,
        CRAFTING,
        MENU,
        STARTER_SELECTION,
        CHEST_SCREEN,
        BATTLE
    }
}
