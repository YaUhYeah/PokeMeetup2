package io.github.pokemeetup.system;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.screens.ChestScreen;
import io.github.pokemeetup.screens.CraftingTableScreen;
import io.github.pokemeetup.screens.GameScreen;
import io.github.pokemeetup.screens.InventoryScreen;
import io.github.pokemeetup.screens.otherui.GameMenu;

public class InputManager {
    private final GlobalInputProcessor globalInputProcessor;
    private final GameScreen gameScreen;
    private final InputMultiplexer inputMultiplexer;
    private UIState currentState;
    private UIState previousState;

    public InputManager(GameScreen gameScreen) {
        this.gameScreen = gameScreen;
        this.inputMultiplexer = new InputMultiplexer();
        this.currentState = UIState.NORMAL;
        this.previousState = UIState.NORMAL;
        this.globalInputProcessor = new GlobalInputProcessor(this);
        updateInputProcessors();
    }

    public void returnToPreviousState() {
        if (previousState != null && previousState != UIState.MENU) {
            setUIState(previousState);
        } else {
            setUIState(UIState.NORMAL); // Fallback to normal state
        }
    }

    public void setUIState(UIState newState) {
        if (currentState != newState) {
            // Store the current state if we are opening the menu
            if (newState == UIState.MENU) {
                this.previousState = this.currentState;
            }
            currentState = newState;
            handleUIStateChange();
            updateInputProcessors();
        }
    }

    public UIState getCurrentState() {
        return currentState;
    }

    private void handleUIStateChange() {
        hideAllUI();

        switch (currentState) {
            case NORMAL:
                if (GameContext.get().getHotbarSystem() != null && GameContext.get().getHotbarSystem().getHotbarTable().getParent() != null) {
                    GameContext.get().getHotbarSystem().getHotbarTable().getParent().setVisible(true);
                }
                break;
            case BUILD_MODE:
                if (GameContext.get().getBuildModeUI() != null) {
                    GameContext.get().getBuildModeUI().setVisible(true);
                    GameContext.get().getBuildModeUI().refreshBuildInventory();
                }
                break;
            case INVENTORY:
                showInventoryScreen();
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
            case STARTER_SELECTION:
                break;
        }
        if (gameScreen.getInputHandler() != null) {
            gameScreen.getInputHandler().resetMovementFlags();
        }
        if (currentState != UIState.NORMAL && currentState != UIState.BUILD_MODE) {
            Player player = GameContext.get().getPlayer();
            if (player != null) {
                player.stopMovement();
            }
        }
    }
    public void hideAllUI() {
        if (GameContext.get().getHotbarSystem() != null && GameContext.get().getHotbarSystem().getHotbarTable().getParent() != null) {
            GameContext.get().getHotbarSystem().getHotbarTable().getParent().setVisible(false);
        }
        if (GameContext.get().getBuildModeUI() != null) {
            GameContext.get().getBuildModeUI().setVisible(false);
        }
        if (gameScreen.getInventoryScreen() != null) {
            gameScreen.getInventoryScreen().hide();
        }
        if (gameScreen.getGameMenu() != null) {
            // was: gameScreen.getGameMenu().hide();
            gameScreen.getGameMenu().hideSilently();  // <-- no state bounce
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
        inputMultiplexer.clear();

        // The order of adding processors to the multiplexer matters.
        // The first one added gets the first chance to handle an event.
        // We add the most specific/modal UI first.

        switch (currentState) {
            case INVENTORY:
                if (gameScreen.getInventoryScreen() != null && gameScreen.getInventoryScreen().getStage() != null) {
                    inputMultiplexer.addProcessor(gameScreen.getInventoryScreen().getStage());
                }
                break;
            case CRAFTING:
                if (gameScreen.getCraftingScreen() != null && gameScreen.getCraftingScreen().getStage() != null) {
                    inputMultiplexer.addProcessor(gameScreen.getCraftingScreen().getStage());
                }
                break;
            case CHEST_SCREEN:
                if (gameScreen.getChestScreen() != null && gameScreen.getChestScreen().getStage() != null) {
                    inputMultiplexer.addProcessor(gameScreen.getChestScreen().getStage());
                }
                break;
            case BATTLE:
                if (gameScreen.getBattleStage() != null) {
                    inputMultiplexer.addProcessor(gameScreen.getBattleStage());
                }
                break;
            case NORMAL:
            case BUILD_MODE:
                // UI stage should have priority over game input to catch HUD clicks
                if (gameScreen.getUiStage() != null) {
                    inputMultiplexer.addProcessor(gameScreen.getUiStage());
                }
                if (gameScreen.getInputHandler() != null) {
                    inputMultiplexer.addProcessor(gameScreen.getInputHandler());
                }
                break;
            case CHAT:
            case MENU:
            case STARTER_SELECTION:
                // These UI states are all handled by actors on the main uiStage
                if (gameScreen.getUiStage() != null) {
                    inputMultiplexer.addProcessor(gameScreen.getUiStage());
                }
                break;
            default:
                // Fallback for any other state
                if (gameScreen.getUiStage() != null) {
                    inputMultiplexer.addProcessor(gameScreen.getUiStage());
                }
                break;
        }

        // Global processor should always be present to catch global keys.
        // It is added last, so it's checked after more specific processors.
        inputMultiplexer.addProcessor(globalInputProcessor);
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
        BATTLE,
        CHAT // <-- ADD THIS
    }
}
