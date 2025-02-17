package io.github.pokemeetup.screens.otherui;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.client.GameClientSingleton;
import io.github.pokemeetup.screens.GameScreen;
import io.github.pokemeetup.system.InputManager;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldManager;
import io.github.pokemeetup.system.keybinds.ControllerBindsDialog;
import io.github.pokemeetup.system.keybinds.KeyBindsDialog;
import io.github.pokemeetup.utils.GameLogger;

public class GameMenu extends Actor {
    private static final float BUTTON_WIDTH = 200f;
    private static final float BUTTON_HEIGHT = 50f;
    private static final float MENU_PADDING = 20f;

    private final CreatureCaptureGame game;
    private final InputManager inputManager;
    private Stage stage;
    private Skin skin;
    private Window menuWindow;
    private Table menuTable;
    private boolean isVisible;
    private Window optionsWindow;
    private Slider musicSlider;
    private Slider soundSlider;
    private CheckBox musicEnabled;
    private CheckBox soundEnabled;
    private volatile boolean disposalRequested = false;
    private volatile boolean isDisposing = false;

    public GameMenu(CreatureCaptureGame game, Skin skin, InputManager inputManager) {
        this.game = game;
        this.skin = skin;
        this.inputManager = inputManager;
        this.stage = new Stage(new ScreenViewport());
        createMenu();
        menuWindow.setVisible(false);
        hide();
    }

    private void handleExit() {
        // In multiplayer mode, perform a multiplayer exit routine.
        GameClient client = GameContext.get().getGameClient();
        if (client != null && GameContext.get().isMultiplayer()) {
            performMultiplayerExit();
        } else {
            // Otherwise use the regular single-player save-and-dispose routine.
            performSaveAndExit();
        }
    }
    public void resize(int width, int height) {
        // Update the stage's viewport.
        stage.getViewport().update(width, height, true);

        // Center the main menu window.
        if (menuWindow != null) {
            menuWindow.pack(); // Recalculate size if needed
            menuWindow.setPosition(
                (width - menuWindow.getWidth()) / 2,
                (height - menuWindow.getHeight()) / 2
            );
        }

        // Center the options window as well.
        if (optionsWindow != null) {
            optionsWindow.pack();
            optionsWindow.setPosition(
                (width - optionsWindow.getWidth()) / 2,
                (height - optionsWindow.getHeight()) / 2
            );
        }
    }
    private TextButton controllerBindsButton;


    /**
     * Transitions to the title screen (or ModeSelectionScreen) after disposing of UI resources.
     *
     * @param loadingDialog  the dialog that was displayed during shutdown
     * @param isSinglePlayer if true, the game will reinitialize the world locally;
     *                       if false (multiplayer) it will simply switch to the title screen.
     */
    private void safeDisposeAndTransition(Dialog loadingDialog, boolean isSinglePlayer) {
        try {
            isDisposing = true;
            if (menuWindow != null) menuWindow.setVisible(false);
            if (stage != null) {
                stage.clear();
                stage.dispose();
            }
            if (loadingDialog != null) {
                loadingDialog.hide();
            }
            GameContext.get().setInventoryScreen(null);
            GameContext.get().setCraftingScreen(null);
            GameContext.get().setBuildModeUI(null);
            if (isSinglePlayer) {
                game.saveAndDispose();
                game.reinitializeGame();
            }
            game.setScreen(new io.github.pokemeetup.screens.ModeSelectionScreen(game));
            isDisposing = false;
        } catch (Exception e) {
            GameLogger.error("Cleanup error: " + e.getMessage());
            isDisposing = false;
            if (loadingDialog != null) loadingDialog.hide();
            showErrorDialog("Error during cleanup: " + e.getMessage());
        }
    }

    private void performMultiplayerExit() {
        Dialog confirmDialog = new Dialog("Confirm Exit", skin) {
            @Override
            protected void result(Object object) {
                if ((Boolean) object) {
                    final Dialog loadingDialog = new Dialog("", skin);
                    loadingDialog.text("Saving and exiting...");
                    loadingDialog.show(stage);
                    new Thread(() -> {
                        try {
                            // In multiplayer, simply send the player state to the server.
                            GameClient client = GameContext.get().getGameClient();
                            // Dispose the client
                            if (client != null) {
                                client.dispose();
                            }
                            // Reset the client singleton and clear the reference
                            GameClientSingleton.resetInstance();
                            GameContext.get().setGameClient(null);
                            // (Optionally, also clear UI screens that depend on multiplayer.)
                            GameContext.get().setInventoryScreen(null);
                            GameContext.get().setCraftingScreen(null);
                            GameContext.get().setBuildModeUI(null);
                            // Transition to the title screen (or ModeSelectionScreen)
                            Gdx.app.postRunnable(() -> safeDisposeAndTransition(loadingDialog, false));
                        } catch (Exception e) {
                            GameLogger.error("Exit failed: " + e.getMessage());
                            Gdx.app.postRunnable(() -> {
                                loadingDialog.hide();
                                showErrorDialog("Failed to exit: " + e.getMessage());
                            });
                        }
                    }).start();
                }
            }
        };
        confirmDialog.text("Are you sure you want to exit to title?\nYour progress will be saved on the server.");
        confirmDialog.button("Yes", true);
        confirmDialog.button("No", false);
        confirmDialog.show(stage);
    }

    private void createMenu() {
        // Create the menu window and table
        menuWindow = new Window("Menu", skin);
        menuWindow.setMovable(false);
        menuWindow.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                GameLogger.info("GameMenu keyDown: keycode=" + keycode);
                return false; // Allow event propagation
            }
            @Override
            public boolean keyUp(InputEvent event, int keycode) {
                GameLogger.info("GameMenu keyUp: keycode=" + keycode);
                return false;
            }
        });

        menuTable = new Table();
        menuTable.defaults().pad(10).width(BUTTON_WIDTH).height(BUTTON_HEIGHT);

        // Create main menu buttons
        TextButton saveButton = new TextButton("Save Game", skin);
        TextButton bagButton = new TextButton("Bag", skin);
        TextButton pokemonButton = new TextButton("Pokemon", skin);
        TextButton optionsButton = new TextButton("Options", skin);
        TextButton exitButton = new TextButton("Quit and Save to Title", skin);

        saveButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                // Only save in single-player mode
                if (!GameContext.get().isMultiplayer()) {
                    saveGame();
                }
            }
        });
        bagButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showNotImplementedMessage();
            }
        });
        pokemonButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showPartyScreen(false);
            }
        });
        exitButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                handleExit();
            }
        });
        optionsButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showOptions();
            }
        });

        // Add buttons to the menu table
        menuTable.add(saveButton).row();
        menuTable.add(bagButton).row();
        menuTable.add(pokemonButton).row();
        menuTable.add(optionsButton).row();
        menuTable.add(exitButton).row();

        // Add the menu table to the menu window
        menuWindow.add(menuTable).pad(MENU_PADDING);
        menuWindow.pack();
        menuWindow.setPosition((Gdx.graphics.getWidth() - menuWindow.getWidth()) / 2,
            (Gdx.graphics.getHeight() - menuWindow.getHeight()) / 2);

        // Build the options menu (which also includes controller keybinds)
        createOptionsMenu();

        stage.addActor(menuWindow);
    }


    private void showPartyScreen(boolean battleMode) {
        // Hide menu window temporarily
        menuWindow.setVisible(false);
        if (GameContext.get().getGameScreen().getBattleSkin() != null) {
            // Create party screen
            PokemonPartyWindow partyScreen = new PokemonPartyWindow(
                GameContext.get().getGameScreen().getBattleSkin() ,
                GameContext.get().getPlayer().getPokemonParty(),
                battleMode,
                (selectedPokemon) -> {
                    // Handle selection
                    menuWindow.setVisible(true);
                },
                () -> {
                    // Handle cancel
                    menuWindow.setVisible(true);
                }
            );

            // Add to stage above other elements
            stage.addActor(partyScreen);
            partyScreen.show(stage);
        }
    }

    private void performSaveAndExit() {
        if (disposalRequested) return;
        disposalRequested = true;
        final Dialog loadingDialog = new Dialog("", skin);
        loadingDialog.text("Saving game...");
        loadingDialog.show(stage);
        new Thread(() -> {
            try {
                game.saveAndDispose();
                Gdx.app.postRunnable(() -> safeDisposeAndTransition(loadingDialog, true));
            } catch (Exception e) {
                GameLogger.error("Save failed: " + e.getMessage());
                Gdx.app.postRunnable(() -> {
                    loadingDialog.hide();
                    showErrorDialog("Failed to save: " + e.getMessage());
                });
            }
        }).start();
    }

    private void showErrorDialog(String message) {
        try {
            Dialog errorDialog = new Dialog("Error", skin);
            errorDialog.text(message);
            errorDialog.button("OK");
            errorDialog.show(stage);
        } catch (Exception e) {
            GameLogger.error("Failed to show error dialog: " + e.getMessage());
        }
    }

    public Stage getStage() {
        if (isDisposing || stage == null) {
            return null;
        }
        return stage;
    }

    public void show() {
        if (isVisible) return;
        isVisible = true;
        menuWindow.setVisible(true);
        stage.setKeyboardFocus(menuWindow);
        inputManager.setUIState(InputManager.UIState.MENU);
    }

    public void hide() {
        if (!isVisible) return;
        isVisible = false;
        menuWindow.setVisible(false);
        stage.unfocus(menuWindow); // Remove focus
        inputManager.setUIState(InputManager.UIState.NORMAL); // Update UI state
    }

    public boolean isVisible() {
        return isVisible;
    }

    private void saveGame() {
        GameLogger.info("Attempting to save game");
        try {
            if (GameContext.get().getGameClient() != null && GameContext.get().getPlayer().getWorld() != null) {
                PlayerData playerData = GameContext.get().getPlayer().getPlayerData();
                // In single-player mode, update and save the world state.
                GameContext.get().getWorld().getWorldData().savePlayerData(
                    GameContext.get().getPlayer().getUsername(), playerData, false);
                WorldManager.getInstance().saveWorld(GameContext.get().getWorld().getWorldData());
                GameLogger.info("Game saved successfully");
                showSaveSuccessDialog();
            } else {
                throw new Exception("Game state is invalid");
            }
        } catch (Exception e) {
            showSaveErrorDialog(e.getMessage());
        }
    }

    private void showSaveSuccessDialog() {
        Dialog dialog = new Dialog("Success", skin) {
            public void result(Object obj) {
                hide();
            }
        };
        dialog.text("Game saved successfully!");
        dialog.button("OK");
        dialog.show(stage);
    }

    private void showSaveErrorDialog(String errorMessage) {
        GameLogger.info("Save error: " + errorMessage);
        Dialog dialog = new Dialog("Error", skin) {
            public void result(Object obj) {
                hide();
            }
        };
        dialog.text("Failed to save game: " + errorMessage);
        dialog.button("OK");
        dialog.show(stage);
    }

    private void showNotImplementedMessage() {
        Dialog dialog = new Dialog("Notice", skin) {
            public void result(Object obj) {
                hide();
            }
        };
        dialog.text("This feature is not yet implemented.");
        dialog.button("OK");
        dialog.show(stage);
    }


    private void createOptionsMenu() {
        optionsWindow = new Window("Options", skin);
        optionsWindow.setMovable(false);

        Table optionsTable = new Table();
        optionsTable.pad(MENU_PADDING);

        // Audio settings
        Label musicLabel = new Label("Music Volume", skin);
        musicSlider = new Slider(0f, 1f, 0.1f, false, skin);
        musicSlider.setValue(AudioManager.getInstance().getMusicVolume());

        musicEnabled = new CheckBox(" Music Enabled", skin);
        musicEnabled.setChecked(AudioManager.getInstance().isMusicEnabled());

        Label soundLabel = new Label("Sound Volume", skin);
        soundSlider = new Slider(0f, 1f, 0.1f, false, skin);
        soundSlider.setValue(AudioManager.getInstance().getSoundVolume());

        soundEnabled = new CheckBox(" Sound Enabled", skin);
        soundEnabled.setChecked(AudioManager.getInstance().isSoundEnabled());

        // Buttons to open keybind dialogs
        TextButton keyBindsButton = new TextButton("Keyboard Key Bindings", skin);
        keyBindsButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                KeyBindsDialog dialog = new KeyBindsDialog(skin);
                dialog.show(stage);
            }
        });

        controllerBindsButton = new TextButton("Controller Key Bindings", skin);
        controllerBindsButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                ControllerBindsDialog dialog = new ControllerBindsDialog(skin);
                dialog.show(stage);
            }
        });

        // Add audio settings to options table
        optionsTable.add(keyBindsButton).width(BUTTON_WIDTH).height(BUTTON_HEIGHT).padTop(20).row();
        optionsTable.add(controllerBindsButton).width(BUTTON_WIDTH).height(BUTTON_HEIGHT).padTop(10).row();
        optionsTable.add(musicLabel).left().padBottom(10).row();
        optionsTable.add(musicSlider).width(200).padBottom(5).row();
        optionsTable.add(musicEnabled).left().padBottom(20).row();
        optionsTable.add(soundLabel).left().padBottom(10).row();
        optionsTable.add(soundSlider).width(200).padBottom(5).row();
        optionsTable.add(soundEnabled).left().padBottom(20).row();

        // Save and Cancel buttons for options
        TextButton saveButton = new TextButton("Save", skin);
        saveButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                saveAudioSettings();
                hideOptions();
            }
        });
        TextButton cancelButton = new TextButton("Cancel", skin);
        cancelButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                hideOptions();
            }
        });
        optionsTable.add(saveButton).width(BUTTON_WIDTH).height(BUTTON_HEIGHT).padTop(10).row();
        optionsTable.add(cancelButton).width(BUTTON_WIDTH).height(BUTTON_HEIGHT).padTop(10).row();

        optionsWindow.add(optionsTable);
        optionsWindow.pack();
        optionsWindow.setPosition((Gdx.graphics.getWidth() - optionsWindow.getWidth()) / 2,
            (Gdx.graphics.getHeight() - optionsWindow.getHeight()) / 2);
        optionsWindow.setVisible(false);
        stage.addActor(optionsWindow);
    }

    private void setupAudioListeners() {
        musicSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AudioManager.getInstance().setMusicVolume(musicSlider.getValue());
            }
        });

        musicEnabled.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AudioManager.getInstance().setMusicEnabled(musicEnabled.isChecked());
            }
        });

        soundSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AudioManager.getInstance().setSoundVolume(soundSlider.getValue());
                if (soundEnabled.isChecked()) {
                    AudioManager.getInstance().playSound(AudioManager.SoundEffect.MENU_SELECT);
                }
            }
        });

        soundEnabled.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AudioManager.getInstance().setSoundEnabled(soundEnabled.isChecked());
            }
        });
    }

    private void setupSaveButton(Table optionsTable) {
        TextButton saveButton = new TextButton("Save", skin);
        saveButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                saveAudioSettings();
                hideOptions();
            }
        });
        optionsTable.add(saveButton).width(BUTTON_WIDTH).height(BUTTON_HEIGHT).padTop(10).row();
    }

    private void setupCancelButton(Table optionsTable) {
        TextButton cancelButton = new TextButton("Cancel", skin);
        cancelButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                hideOptions();
            }
        });
        optionsTable.add(cancelButton).width(BUTTON_WIDTH).height(BUTTON_HEIGHT).padTop(10).row();
    }

    private void saveAudioSettings() {
        Preferences prefs = Gdx.app.getPreferences("audio_settings");
        prefs.putFloat("music_volume", musicSlider.getValue());
        prefs.putFloat("sound_volume", soundSlider.getValue());
        prefs.putBoolean("music_enabled", musicEnabled.isChecked());
        prefs.putBoolean("sound_enabled", soundEnabled.isChecked());
        prefs.flush();

        Dialog dialog = new Dialog("Settings Saved", skin) {
            public void result(Object obj) {
                hide();
            }
        };
        dialog.text("Settings have been saved.");
        dialog.button("OK");
        dialog.show(stage);
    }

    private void showOptions() {
        menuWindow.setVisible(false);
        optionsWindow.setVisible(true);
        musicSlider.setValue(AudioManager.getInstance().getMusicVolume());
        soundSlider.setValue(AudioManager.getInstance().getSoundVolume());
        musicEnabled.setChecked(AudioManager.getInstance().isMusicEnabled());
        soundEnabled.setChecked(AudioManager.getInstance().isSoundEnabled());
    }

    private void hideOptions() {
        optionsWindow.setVisible(false);
        menuWindow.setVisible(true);
    }

    public void render() {
        if (!isDisposing && isVisible && stage != null) {
            try {
                stage.act();
                stage.draw();
            } catch (Exception e) {
                GameLogger.error("Error rendering menu: " + e.getMessage());
            }
        }
    }

    public void dispose() {
        if (isDisposing) {
            return;
        }

        isDisposing = true;
        if (stage != null) {
            Gdx.app.postRunnable(() -> {
                try {
                    if (stage != null) {
                        stage.clear();
                        stage.dispose();
                        stage = null;
                    }
                    skin = null;
                    menuWindow = null;
                    optionsWindow = null;
                    menuTable = null;
                    GameLogger.info("GameMenu disposed successfully");
                } catch (Exception e) {
                    GameLogger.error("Error disposing GameMenu: " + e.getMessage());
                }
            });
        }
    }
}
