package io.github.pokemeetup.system;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.InputMultiplexer;
import io.github.pokemeetup.screens.ChestScreen;
import io.github.pokemeetup.screens.CraftingTableScreen;
import io.github.pokemeetup.screens.GameScreen;
import io.github.pokemeetup.screens.InventoryScreen;
import io.github.pokemeetup.screens.otherui.BuildModeUI;
import io.github.pokemeetup.screens.otherui.GameMenu;
import io.github.pokemeetup.utils.GameLogger;

public class InputManager {
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
            GameLogger.info("Switching UI state to: " + currentState);
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
            gameScreen.setInventoryScreen(new InventoryScreen(
                gameScreen.getPlayer(),
                gameScreen.getSkin(),
                gameScreen.getPlayer().getInventory(),this
            ));
        }
        gameScreen.getInventoryScreen().show();
    }

    private void showBuildModeUI() {
        if (gameScreen.getBuildModeUI() == null) {
            gameScreen.setBuildModeUI(new BuildModeUI(gameScreen.getSkin(), gameScreen.getPlayer()));
        }
        gameScreen.getBuildModeUI().show();
    }

    private void showCraftingScreen() {
        if (gameScreen.getCraftingScreen() == null) {
            gameScreen.setCraftingScreen(new CraftingTableScreen(gameScreen.getPlayer(), gameScreen.getSkin(), gameScreen.getWorld(), gameScreen.getGameClient(), gameScreen, this));
        }
        gameScreen.getCraftingScreen().show();
    }

    private void showGameMenu() {
        if (gameScreen.getGameMenu() == null) {
            gameScreen.setGameMenu(new GameMenu(
                gameScreen.getGame(),
                gameScreen.getSkin(),
                gameScreen.getPlayer(),
                gameScreen.getGameClient(),
                this // Pass InputManager reference
            ));
        }
        gameScreen.getGameMenu().show();
    }

    private void showChestScreen() {
        if (gameScreen.getChestScreen() == null) {
            gameScreen.setChestScreen(new ChestScreen(gameScreen.getPlayer(), gameScreen.getSkin(), null, null, gameScreen));
        }
        gameScreen.getChestScreen().show();
    }

    public void updateInputProcessors() {
        inputMultiplexer.clear();

        // Add UI-stage first for any state that uses it
        if (gameScreen.getUiStage() != null) {
            inputMultiplexer.addProcessor(gameScreen.getUiStage());
            GameLogger.info("Added UI stage");
        }

        switch (currentState) {
            case STARTER_SELECTION:
                // For starter selection, we only add the uiStage here.
                // Do NOT add InputHandler or GlobalInputProcessor here.
                GameLogger.info("UI State: STARTER_SELECTION - UI stage added first");
                break;
            case INVENTORY:
                if (gameScreen.getInventoryScreen() != null) {
                    inputMultiplexer.addProcessor(gameScreen.getInventoryScreen().getStage());
                    GameLogger.info("Added InventoryScreen stage");
                }
                break;
            case CRAFTING:
                if (gameScreen.getCraftingScreen() != null && gameScreen.getCraftingScreen().getStage() != null) {
                    inputMultiplexer.addProcessor(gameScreen.getCraftingScreen().getStage());
                    GameLogger.info("Added CraftingScreen stage");
                }
                break;
            case MENU:
                if (gameScreen.getGameMenu() != null && gameScreen.getGameMenu().getStage() != null) {
                    inputMultiplexer.addProcessor(gameScreen.getGameMenu().getStage());
                    GameLogger.info("Added GameMenu stage");
                }
                break;
            case CHEST_SCREEN:
                if (gameScreen.getChestScreen() != null && gameScreen.getChestScreen().getStage() != null) {
                    inputMultiplexer.addProcessor(gameScreen.getChestScreen().getStage());
                    GameLogger.info("Added ChestScreen stage");
                }
                break;
            case BATTLE:
                if (gameScreen.getBattleStage() != null) {
                    inputMultiplexer.addProcessor(gameScreen.getBattleStage());
                    GameLogger.info("Added BattleStage");
                }
                break;
            case NORMAL:
            case BUILD_MODE:
                // No extra UI processors, just uiStage if needed
                break;
        }

        if (gameScreen.getInputHandler() != null) {
            inputMultiplexer.addProcessor(gameScreen.getInputHandler());
            GameLogger.info("Added InputHandler");
        }

        inputMultiplexer.addProcessor(globalInputProcessor);
        GameLogger.info("Added GlobalInputProcessor");

        Gdx.input.setInputProcessor(inputMultiplexer);
        debugInputProcessors();
    }


    private void debugInputProcessors() {
        GameLogger.info("Current InputProcessors:");
        for (int i = 0; i < inputMultiplexer.getProcessors().size; i++) {
            InputProcessor processor = inputMultiplexer.getProcessors().get(i);
            GameLogger.info(" - Processor " + i + ": " + processor.getClass().getSimpleName());
        }
    }
}
