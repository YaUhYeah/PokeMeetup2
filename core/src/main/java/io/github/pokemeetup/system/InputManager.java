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
            gameScreen.getGameMenu().hide();
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

        if (currentState == UIState.CHAT && GameContext.get().getChatSystem() != null) {
            inputMultiplexer.addProcessor(GameContext.get().getChatSystem().getStage());
            Gdx.input.setInputProcessor(inputMultiplexer);
            return; // When chat is active, it gets exclusive input priority.
        }
        if (GameContext.get().getUiStage() != null) {
            inputMultiplexer.addProcessor(GameContext.get().getUiStage());
        }
        switch (currentState) {
            case STARTER_SELECTION:
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
                break;
        }
        if (gameScreen.getInputHandler() != null) {
            inputMultiplexer.addProcessor(gameScreen.getInputHandler());
        }
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
