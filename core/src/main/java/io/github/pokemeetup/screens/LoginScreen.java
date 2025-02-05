package io.github.pokemeetup.screens;

import com.badlogic.gdx.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.utils.viewport.FitViewport;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.client.GameClientSingleton;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.multiplayer.server.config.ServerConfigManager;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import io.github.pokemeetup.screens.otherui.ServerManagementDialog;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class LoginScreen implements Screen {
    // --- Constants for minimum/maximum dimensions and virtual viewport size ---
    public static final String SERVERS_PREFS = "ServerPrefs";
    public static final float MIN_WIDTH = 300f;
    public static final float MAX_WIDTH = 500f;
    private static final String DEFAULT_SERVER_ICON = "ui/default-server-icon.png";
    private static final int MIN_HEIGHT = 600;
    private static final float CONNECTION_TIMEOUT = 30f;
    private static final int MAX_CONNECTION_ATTEMPTS = 3;

    // Virtual resolution (for the FitViewport) – adjust as needed.
    private static final float VIRTUAL_WIDTH = 800;
    private static final float VIRTUAL_HEIGHT = 600;

    public final Stage stage;
    public final Skin skin;
    public final CreatureCaptureGame game;
    private final Preferences prefs;
    public Array<ServerConnectionConfig> servers; // Adding the servers variable
    public TextField usernameField;
    public TextField passwordField;
    public CheckBox rememberMeBox;
    public Label feedbackLabel;
    public ServerConnectionConfig selectedServer;
    private Table serverListTable;
    private Table mainTable;
    private TextButton loginButton;
    private TextButton registerButton;
    private TextButton backButton;
    private ProgressBar connectionProgress;
    private Label statusLabel;
    private float connectionTimer;
    private boolean isConnecting = false;
    private int connectionAttempts = 0;
    private ScrollPane serverListScrollPane;
    private TextButton editServerButton;
    private TextButton deleteServerButton;

    public LoginScreen(CreatureCaptureGame game) {
        this.game = game;
        // Use a FitViewport to ensure all content is visible regardless of device aspect ratio.
        this.stage = new Stage(new FitViewport(VIRTUAL_WIDTH, VIRTUAL_HEIGHT));
        this.skin = new Skin(Gdx.files.internal("atlas/ui-gfx-atlas.json"));
        this.prefs = Gdx.app.getPreferences("LoginPrefs");
        loadServers();

        createUIComponents();
        setupListeners();
        initializeUI();
        setupInputHandling();
        loadSavedCredentials();

        Gdx.input.setInputProcessor(stage);
    }

    private static ServerConnectionConfig getServerConnectionConfig(ServerEntry entry) {
        ServerConnectionConfig config = new ServerConnectionConfig(
            entry.ip,
            entry.tcpPort,
            entry.udpPort,
            entry.name,
            entry.isDefault,
            entry.maxPlayers
        );
        config.setMotd(entry.motd);
        config.setIconPath(entry.iconPath != null ? entry.iconPath : DEFAULT_SERVER_ICON);
        return config;
    }

    private void setupInputHandling() {
        boolean isMobile = Gdx.app.getType() == Application.ApplicationType.Android
            || Gdx.app.getType() == Application.ApplicationType.iOS;

        float touchPadding = isMobile ? 12 : 6; // Larger touch targets on mobile

        // Update button sizes
        loginButton.padTop(touchPadding).padBottom(touchPadding);
        registerButton.padTop(touchPadding).padBottom(touchPadding);

        // Add ripple effect on mobile
        if (isMobile) {
            addTouchRipple(loginButton);
            addTouchRipple(registerButton);
        }

        // Handle back button on Android
        if (Gdx.app.getType() == Application.ApplicationType.Android) {
            stage.addListener(new InputListener() {
                @Override
                public boolean keyDown(InputEvent event, int keycode) {
                    if (keycode == Input.Keys.BACK) {
                        handleBackButton();
                        return true;
                    }
                    return false;
                }
            });
        }
    }

    private void addTouchRipple(final TextButton targetButton) {
        targetButton.addListener(new InputListener() {
            private Actor ripple;
            private float startX, startY;

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int buttonId) {
                // Create ripple effect
                ripple = new Image(TextureManager.ui.findRegion("circle"));
                ripple.setSize(20, 20);
                ripple.setPosition(x - 10, y - 10); // Center ripple on touch
                ripple.setColor(1, 1, 1, 0.3f);
                startX = x;
                startY = y;

                targetButton.addActor(ripple);

                // Animate ripple
                float duration = 0.4f;
                ripple.addAction(Actions.parallel(
                    Actions.scaleTo(10, 10, duration),
                    Actions.fadeOut(duration),
                    Actions.run(() -> ripple.remove())
                ));

                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                // If drag exceeds threshold, cancel ripple
                if (Math.abs(x - startX) > 20 || Math.abs(y - startY) > 20) {
                    if (ripple != null) {
                        ripple.remove();
                        ripple = null;
                    }
                }
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int buttonId) {
                // Fade out ripple faster on touch up
                if (ripple != null) {
                    ripple.addAction(Actions.fadeOut(0.1f));
                }
            }
        });
    }

    private void handleBackButton() {
        if (isConnecting) {
            // Show confirmation dialog when attempting to cancel login
            Dialog dialog = new Dialog("Cancel Login?", skin) {
                @Override
                protected void result(Object obj) {
                    if ((Boolean) obj) {
                        // User confirmed cancel
                        if (GameContext.get().getGameClient() != null) {
                            GameContext.get().getGameClient().dispose();
                            GameContext.get().setGameClient(null);
                        }
                        isConnecting = false;
                        connectionProgress.setVisible(false);
                        setUIEnabled(true);
                        statusLabel.setText("");
                        feedbackLabel.setText("Login cancelled");
                    }
                }
            };

            dialog.text("Are you sure you want to cancel the login?");
            dialog.button("Yes", true);
            dialog.button("No", false);
            dialog.show(stage);

        } else {
            // If not connecting, just go back to mode selection
            game.setScreen(new ModeSelectionScreen(game));
        }
    }

    private void updateServerList() {
        // Clear existing list
        serverListTable.clear();
        serverListTable.top();

        // Add header if needed
        Label headerLabel = new Label("Available Servers", skin, "title-small");
        serverListTable.add(headerLabel).pad(10).row();

        // Add server entries
        boolean hasServers = false;
        for (ServerConnectionConfig server : servers) {
            Table serverEntry = createServerEntry(server);
            serverListTable.add(serverEntry).expandX().fillX().padBottom(2).row();
            hasServers = true;
        }

        // Add empty state message if no servers
        if (!hasServers) {
            Label emptyLabel = new Label("No servers available.", skin);
            emptyLabel.setColor(Color.GRAY);
            serverListTable.add(emptyLabel).pad(20);
        }

        // Select first server by default if none selected
        if (selectedServer == null && servers.size > 0) {
            selectedServer = servers.first();
            Cell<?> firstCell = serverListTable.getCells().first();
            if (firstCell != null && firstCell.getActor() instanceof Table) {
                updateServerSelection((Table) firstCell.getActor());
            }
        }

        // Force layout update
        serverListTable.invalidate();
        serverListScrollPane.invalidate();

        // Save server list
        saveServers();

        // Log update
        GameLogger.info("Server list updated with " + servers.size + " servers");
    }

    private Table createServerEntry(final ServerConnectionConfig server) {
        Table entry = new Table();
        entry.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("info-box-bg")));
        entry.pad(10);

        // Left icon (unchanged)
        Table iconContainer = new Table();
        try {
            if (server.getIconPath() != null) {
                if (TextureManager.ui.findRegion("default-server-icon") == null
                    || !server.getIconPath().contains("default-server-icon")) {
                    FileHandle iconFile = Gdx.files.internal(server.getIconPath());
                    if (iconFile.exists()) {
                        Image icon = new Image(new Texture(iconFile));
                        icon.setSize(32, 32);
                        iconContainer.add(icon).size(32);
                    } else {
                        addDefaultIcon(iconContainer);
                    }
                } else {
                    Image icon = new Image(TextureManager.ui.findRegion("default-server-icon"));
                    icon.setSize(32, 32);
                    iconContainer.add(icon).size(32);
                }
            } else {
                addDefaultIcon(iconContainer);
            }
        } catch (Exception e) {
            GameLogger.error("Failed to load server icon: " + e.getMessage());
            addDefaultIcon(iconContainer);
        }

        // Middle info panel with single-line labels using ellipsis
        Table infoPanel = new Table();
        infoPanel.defaults().expandX().fillX().space(5);
        infoPanel.left();

        Label nameLabel = new Label(server.getServerName(), skin, "title-small");
        nameLabel.setWrap(false);
        nameLabel.setEllipsis(true);
        nameLabel.setFontScale(1.1f);
        // Force a fixed height (adjust 24 as needed)
        infoPanel.add(nameLabel).left().expandX().fillX().height(24).row();

        Label motdLabel = new Label(server.getMotd() != null ? server.getMotd() : "Welcome!", skin, "default");
        motdLabel.setWrap(false);
        motdLabel.setEllipsis(true);
        motdLabel.setColor(0.8f, 0.8f, 0.8f, 1f);
        infoPanel.add(motdLabel).left().expandX().fillX().height(24).row();

        Label addressLabel = new Label(server.getServerIP() + ":" + server.getTcpPort(), skin, "small");
        addressLabel.setWrap(false);
        addressLabel.setEllipsis(true);
        addressLabel.setColor(0.7f, 0.7f, 0.7f, 1f);
        infoPanel.add(addressLabel).left().expandX().fillX().height(24).row();


        entry.add(iconContainer).padRight(10).width(40);
        entry.add(infoPanel).expandX().fillX();

        // Selection listeners (unchanged)
        entry.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                selectedServer = server;
                updateServerSelection(entry);
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                if (selectedServer != server) {
                    entry.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("textfield-active")));
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                if (selectedServer != server) {
                    entry.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("textfield")));
                }
            }
        });

        return entry;
    }

    private void addDefaultIcon(Table container) {
        // Fallback: either from atlas or from file
        if (TextureManager.ui.findRegion("default-server-icon") != null) {
            Image defaultIcon = new Image(TextureManager.ui.findRegion("default-server-icon"));
            defaultIcon.setSize(32, 32);
            container.add(defaultIcon).size(32);
        } else {
            // fallback file load
            FileHandle iconFile = Gdx.files.internal(DEFAULT_SERVER_ICON);
            if (iconFile.exists()) {
                Image defaultIcon = new Image(new Texture(iconFile));
                defaultIcon.setSize(32, 32);
                container.add(defaultIcon).size(32);
            }
        }
    }

    private void updateUI() {
        // (Optional) If you need to update UI elements dynamically.
    }

    private void initializeUI() {
        // Instead of using Gdx.graphics.getWidth/Height, we use the stage's viewport dimensions.
        float screenWidth = stage.getWidth();
        float screenHeight = stage.getHeight();

        // Determine content width/height (capped to MAX_WIDTH and a reasonable height)
        float contentWidth = Math.min(MAX_WIDTH, screenWidth * 0.9f);
        float contentHeight = Math.min(700, screenHeight * 0.9f);

        // Create a dark panel that will hold our login UI elements.
        Table darkPanel = new Table();
        darkPanel.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("window")));
        darkPanel.pad(20);

        // Title section
        Label titleLabel = new Label("PokéMeetup", skin, "title");
        titleLabel.setFontScale(2f);
        darkPanel.add(titleLabel).padBottom(30).row();

        // --- Server selection section ---
        Table serverHeader = new Table();
        serverHeader.defaults().pad(5);
        Label serverLabel = new Label("Available Servers", skin, "title-small");
        serverHeader.add(serverLabel).expandX().left();
        TextButton addServerButton = new TextButton("Add Server", skin);
        addServerButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showServerDialog(null);
            }
        });// Add a back button at the top left of the screen
        Table topLeftTable = new Table();
        topLeftTable.setFillParent(true);
        topLeftTable.top().left();
        topLeftTable.add(backButton);  // adjust padding as needed
        stage.addActor(topLeftTable);

        serverHeader.add(addServerButton).right();
        darkPanel.add(serverHeader).fillX().padBottom(10).row();

        darkPanel.add(serverListScrollPane)
            .width(contentWidth - 40)
            // Cap the height (or use a fraction of the available stage height)
            .height(Math.min(220, screenHeight * 0.25f))
            .padBottom(20)
            .row();
// --- Login form section ---
        Table loginForm = createLoginForm(contentWidth);
        darkPanel.add(loginForm).width(contentWidth - 40).row();

// --- Server action buttons (Edit/Delete) section ---
        Table serverActionTable = new Table();
        serverActionTable.defaults().width((contentWidth - 40) / 2f).height(40).padTop(10);
        serverActionTable.add(editServerButton).padRight(10);
        serverActionTable.add(deleteServerButton);
        darkPanel.add(serverActionTable).padBottom(20).row();

// --- Status and progress section ---
        Table statusSection = new Table();
        statusSection.add(statusLabel).width(contentWidth - 40).padBottom(5).row();
        statusSection.add(connectionProgress).width(contentWidth - 40).height(4).padBottom(5).row();
        statusSection.add(feedbackLabel).width(contentWidth - 40);
        darkPanel.add(statusSection).row();


        // Instead of manually setting the position of darkPanel, add it to a root table that fills the stage.
        mainTable.clear();
        mainTable.setFillParent(true);
        mainTable.center().pad(10);
        mainTable.add(darkPanel);
        darkPanel.pack();
        stage.addActor(mainTable);
    }

    private Table createLoginForm(float width) {
        Table form = new Table();
        // Use relative width for each row
        form.defaults().width(width - 40).padBottom(10);

        // Username row
        Table usernameRow = new Table();
        Label usernameLabel = new Label("Username:", skin);
        usernameRow.add(usernameLabel).width(80).right().padRight(10);
        usernameRow.add(usernameField).expandX().fillX().height(36);
        form.add(usernameRow).row();

        // Password row
        Table passwordRow = new Table();
        Label passwordLabel = new Label("Password:", skin);
        passwordRow.add(passwordLabel).width(80).right().padRight(10);
        passwordRow.add(passwordField).expandX().fillX().height(36);
        form.add(passwordRow).row();

        // Remember me
        form.add(rememberMeBox).left().padTop(5).row();

        // Buttons row
        Table buttons = new Table();
        buttons.defaults().width((width - 60) / 2f).height(40);
        buttons.add(loginButton).padRight(10);
        buttons.add(registerButton);
        form.add(buttons).padTop(20);

        return form;
    }


    private ScrollPane createServerList() {
        serverListTable = new Table();
        // Let each row expand horizontally
        serverListTable.defaults().expandX().fillX().pad(5);
        serverListTable.top();

        // We'll add servers once we call updateServerList() again
        for (ServerConnectionConfig server : servers) {
            Table serverEntry = createServerEntry(server);
            serverListTable.add(serverEntry).expandX().fillX().padBottom(2).row();
        }

        // Scroll pane style
        ScrollPane.ScrollPaneStyle scrollStyle = new ScrollPane.ScrollPaneStyle();
        scrollStyle.background = new TextureRegionDrawable(TextureManager.ui.findRegion("textfield"));
        scrollStyle.vScroll = skin.getDrawable("scrollbar-v");
        scrollStyle.vScrollKnob = skin.getDrawable("scrollbar-knob-v");

        ScrollPane scrollPane = new ScrollPane(serverListTable, scrollStyle);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(true, false); // only vertical
        scrollPane.setForceScroll(false, true);
        scrollPane.setOverscroll(false, false);

        return scrollPane;
    }

    private void saveServers() {
        try {
            Json json = new Json();
            StringBuilder sb = new StringBuilder();

            for (ServerConnectionConfig server : servers) {
                ServerEntry entry = new ServerEntry(
                    server.getServerName(),
                    server.getServerIP(),
                    server.getTcpPort(),
                    server.getUdpPort(),
                    server.getMotd(),
                    server.isDefault(),
                    server.getMaxPlayers(),
                    server.getIconPath()
                );
                if (sb.length() > 0) sb.append("|");
                sb.append(json.toJson(entry));
            }

            Preferences prefs = Gdx.app.getPreferences(SERVERS_PREFS);
            prefs.putString("servers", sb.toString());
            prefs.flush();
        } catch (Exception e) {
            GameLogger.error("Failed to save servers: " + e.getMessage());
        }
    }

    private void attemptLogin() {
        if (isConnecting) {
            return;
        }

        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (!validateInput(username, password)) {
            return;
        }

        if (selectedServer == null) {
            showError("Please select a server");
            return;
        }

        // Show loading feedback
        isConnecting = true;
        setUIEnabled(false);
        statusLabel.setText("Connecting to server...");
        statusLabel.setColor(Color.WHITE);
        connectionProgress.setVisible(true);
        connectionProgress.setValue(0);
        feedbackLabel.setText("");

        // Track login state
        final AtomicBoolean loginCompleted = new AtomicBoolean(false);
        final AtomicBoolean transitionStarted = new AtomicBoolean(false);

        try {
            // Cleanup existing client
            if (GameContext.get().getGameClient() != null) {
                GameContext.get().getGameClient().dispose();
                GameClientSingleton.resetInstance();
            }

            GameContext.get().setMultiplayer(true);
            // Create new client
            GameContext.get().setGameClient(new GameClient(selectedServer));

            // Set up response handlers
            GameContext.get().getGameClient().setLoginResponseListener(response -> {
                if (loginCompleted.get()) {
                    return; // Prevent duplicate processing
                }
                loginCompleted.set(true);

                Gdx.app.postRunnable(() -> {
                    if (response.success) {
                        handleSuccessfulLogin(response, transitionStarted);
                    } else {
                        handleLoginFailure(response.message);
                    }
                });
            });

            // Set initialization listener
            GameContext.get().getGameClient().setInitializationListener(success -> {
                if (transitionStarted.get()) {
                    return; // Prevent duplicate transitions
                }

                Gdx.app.postRunnable(() -> {
                    if (success) {
                        proceedToGame(transitionStarted);
                    } else {
                        handleInitializationFailure();
                    }
                });
            });

            // Start progress animation
            Timer.schedule(new Timer.Task() {
                float progress = 0;

                @Override
                public void run() {
                    if (!isConnecting) {
                        cancel();
                        return;
                    }
                    progress += 0.05f;
                    connectionProgress.setValue(Math.min(0.9f, progress));
                }
            }, 0, 0.05f);

            // Set credentials and connect
            GameContext.get().getGameClient().setPendingCredentials(username, password);
            GameContext.get().getGameClient().connect();

        } catch (Exception e) {
            handleLoginError(e);
        }
    }

    private void showRetryDialog() {
        Dialog dialog = new Dialog("Connection Failed", skin) {
            @Override
            protected void result(Object obj) {
                if ((Boolean) obj) {
                    attemptLogin();
                } else {
                    setUIEnabled(true);
                }
            }
        };

        dialog.text("Would you like to try connecting again?");
        dialog.button("Retry", true);
        dialog.button("Cancel", false);
        dialog.show(stage);
    }

    private void handleSuccessfulLogin(NetworkProtocol.LoginResponse response, AtomicBoolean transitionStarted) {
        if (transitionStarted.get()) {
            return;
        }
        transitionStarted.set(true);

        try {
            // Update progress
            connectionProgress.setValue(0.95f);
            statusLabel.setText("Loading world...");

            // Create loading screen
            LoadingScreen loadingScreen = new LoadingScreen(game, null);
            game.setScreen(loadingScreen);

            GameContext.get().setGameScreen(new GameScreen(
                game,
                response.username,
                GameContext.get().getGameClient()
            ));

            // Update loading screen target
            loadingScreen.setNextScreen(GameContext.get().getGameScreen());

            // Save credentials if needed
            if (rememberMeBox.isChecked()) {
                saveCredentials(usernameField.getText(), passwordField.getText());
            }

            // Clean up
            dispose();

        } catch (Exception e) {
            transitionStarted.set(false);
            handleGameCreationError(e);
        }
    }

    private void showMultiplayerCharacterSelectionDialog(final NetworkProtocol.LoginResponse response,
                                                         final Runnable onComplete) {
        final Dialog dialog = new Dialog("Choose Your Character", skin) {
            @Override
            protected void result(Object object) {
                // The object is the result provided when the button was added.
                String chosenType = (String) object;
                // Update the player data from the login response.
                response.playerData.setCharacterType(chosenType);
                // Optionally send the update to the server.
                GameContext.get().getGameClient().savePlayerState(response.playerData);
                onComplete.run();
            }
        };

// Add buttons with their result values.
        dialog.button("Boy", "boy");
        dialog.button("Girl", "girl");

// Optionally, add a label or any other content.
        dialog.getContentTable().add(new Label("Select your character:", skin)).colspan(2).pad(10);

// Finally, show the dialog.
        dialog.show(stage);


    }

    private void handleLoginResponse(NetworkProtocol.LoginResponse response) {
        isConnecting = false;
        if (response.success) {
            // Mark as authenticated and store the username.
            // In multiplayer, check whether the returned PlayerData indicates a new player.
            if (GameContext.get().isMultiplayer() &&
                (response.playerData.getCharacterType() == null || response.playerData.getCharacterType().isEmpty())) {
                // Prompt character selection before proceeding.
                showMultiplayerCharacterSelectionDialog(response, () -> {
                    proceedToGameAfterCharacterSelection(response);
                });
            } else {
                // Otherwise, simply continue.
                proceedToGameAfterCharacterSelection(response);
            }
        } else {
            showError(response.message != null ? response.message : "Login failed");
            setUIEnabled(true);
        }
    }

    private void proceedToGameAfterCharacterSelection(NetworkProtocol.LoginResponse response) {
        try {
            // Create a loading screen (your existing LoadingScreen implementation)
            LoadingScreen loadingScreen = new LoadingScreen(game, null);
            game.setScreen(loadingScreen);

            // Now create the GameScreen. In your GameScreen constructor, use the information
            // from response.playerData (which now includes the chosen character type) to initialize the Player.
            GameScreen gameScreen = new GameScreen(
                game,
                response.username,
                GameContext.get().getGameClient()
            );
            // Tell the loading screen which screen to switch to
            loadingScreen.setNextScreen(gameScreen);

            // Optionally, save credentials here.
            if (rememberMeBox.isChecked()) {
                saveCredentials(usernameField.getText(), passwordField.getText());
            }
            // Clean up the login screen.
            dispose();
        } catch (Exception e) {
            GameLogger.error("Failed to transition to game: " + e.getMessage());
            showError("Failed to start game: " + e.getMessage());
            setUIEnabled(true);
        }
    }


    private void forceRelogin() {
        if (GameContext.get().getGameClient() != null) {
            GameContext.get().getGameClient().dispose();
        }

        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        // Show reconnecting status
        statusLabel.setText("Reconnecting...");
        setUIEnabled(false);
        connectionProgress.setVisible(true);
        connectionProgress.setValue(0);

        // Create new client with force login flag
        try {
            GameContext.get().setGameClient(new GameClient(selectedServer));
            GameContext.get().setMultiplayer(true);

            // Set login response listener
            GameContext.get().getGameClient().setLoginResponseListener(this::handleForceLoginResponse);

            // Set force login flag and credentials
            GameContext.get().getGameClient().setPendingCredentials(username, password);
            GameContext.get().getGameClient().connect();

        } catch (Exception e) {
            GameLogger.error("Force relogin failed: " + e.getMessage());
            handleLoginFailure("Failed to reconnect: " + e.getMessage());
        }
    }

    private void handleForceLoginResponse(NetworkProtocol.LoginResponse response) {
        Gdx.app.postRunnable(() -> {
            if (response.success) {
                // Successfully forced login
                handleSuccessfulLoginAttempt(response);
            } else {
                // Failed to force login
                handleLoginFailure(response.message);
            }
        });
    }

    private void handleSuccessfulLoginAttempt(NetworkProtocol.LoginResponse response) {
        // Disable UI during transition
        setUIEnabled(false);
        statusLabel.setText("Initializing game...");
        connectionProgress.setValue(0.9f);

        try {
            // Validate game client state
            if (GameContext.get().getGameClient() == null || !GameContext.get().getGameClient().isConnected()) {
                throw new IllegalStateException("Invalid game client state");
            }

            // Create loading screen
            LoadingScreen loadingScreen = new LoadingScreen(game, null);
            game.setScreen(loadingScreen);

            // Initialize game screen
            GameScreen gameScreen = new GameScreen(
                game,
                response.username,
                GameContext.get().getGameClient()
            );

            // Update loading screen target
            loadingScreen.setNextScreen(gameScreen);

            // Save credentials if needed
            if (rememberMeBox.isChecked()) {
                saveCredentials(usernameField.getText(), passwordField.getText());
            }

            // Clean up login screen
            dispose();

        } catch (Exception e) {
            GameLogger.error("Error during game initialization: " + e.getMessage());
            handleGameCreationError(e);
        }
    }

    private void handleGameCreationError(Exception e) {
        Gdx.app.postRunnable(() -> {
            // Clean up any partial state
            if (GameContext.get().getGameClient() != null) {
                GameContext.get().getGameClient().dispose();
                GameContext.get().setGameClient(null);
            }

            // Reset UI state
            isConnecting = false;
            connectionProgress.setVisible(false);
            setUIEnabled(true);

            // Show error dialog
            Dialog dialog = new Dialog("Login Error", skin);
            dialog.text("Failed to start game: " + e.getMessage() + "\nPlease try again.");
            dialog.button("OK", true);
            dialog.show(stage);
        });
    }

    @Override
    public void render(float delta) {
        // Update connection timeout if connecting
        if (isConnecting) {
            connectionTimer += delta;
            connectionProgress.setValue(connectionTimer / CONNECTION_TIMEOUT);

            if (connectionTimer >= CONNECTION_TIMEOUT) {
                handleConnectionTimeout();
                return;
            }
        }

        // Regular rendering
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.1f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stage.act(delta);
        stage.draw();

        // Update game client if exists
        if (GameContext.get().getGameClient() != null) {
            GameContext.get().getGameClient().tick(delta);
        }
    }

    private void handleConnectionTimeout() {
        isConnecting = false;
        connectionTimer = 0;
        connectionProgress.setVisible(false);

        if (connectionAttempts < MAX_CONNECTION_ATTEMPTS) {
            showRetryDialog();
        } else {
            showError("Connection failed after multiple attempts. Please try again later.");
            setUIEnabled(true);
        }
    }

    private void handleConnectionError(Exception e) {
        Gdx.app.postRunnable(() -> {
            showError("Connection failed: " + e.getMessage());
            setUIEnabled(true);
            isConnecting = false;
            connectionProgress.setVisible(false);
        });
    }

    private void startConnection(String username, String password, ServerConnectionConfig server) {
        isConnecting = true;
        connectionTimer = 0;
        setUIEnabled(false);

        // Update UI feedback
        statusLabel.setText("Connecting to server...");
        statusLabel.setColor(Color.WHITE);
        connectionProgress.setVisible(true);
        connectionProgress.setValue(0);
        feedbackLabel.setText("");

        try {
            GameLogger.info("Starting connection to: " + server.getServerIP() + ":" +
                server.getTcpPort() + "/" + server.getUdpPort());

            // Reset existing client
            if (GameContext.get().getGameClient() != null) {
                GameContext.get().getGameClient().dispose();
            }
            GameClientSingleton.resetInstance();

            // Create new client instance
            GameContext.get().setGameClient(GameClientSingleton.getInstance(server));
            if (GameContext.get().getGameClient() == null) {
                GameLogger.error("Failed to initialize GameClient.");
                handleConnectionError(new Exception("Failed to initialize GameClient."));
                return;
            }

            // Set up login response listener BEFORE connecting
            GameContext.get().getGameClient().setLoginResponseListener(this::handleLoginResponse);

            // Set up initialization listener
            GameContext.get().getGameClient().setInitializationListener(success -> {
                if (success) {
                    GameLogger.info("Game client initialization successful");

                    // CRITICAL: Create and switch to game screen immediately
                    Gdx.app.postRunnable(() -> {
                        try {
                            GameContext.get().setGameScreen(new GameScreen(
                                game,
                                username,
                                GameContext.get().getGameClient()
                            ));
                            game.setScreen(GameContext.get().getGameScreen());
                            dispose(); // Clean up login screen
                        } catch (Exception e) {
                            GameLogger.error("Failed to create game screen: " + e.getMessage());
                            showError("Failed to start game: " + e.getMessage());
                            setUIEnabled(true);
                            isConnecting = false;
                        }
                    });
                } else {
                    GameLogger.error("Game client initialization failed");
                    showError("Failed to initialize game");
                    setUIEnabled(true);
                    isConnecting = false;
                }
            });

            // Set credentials and connect
            GameContext.get().getGameClient().setPendingCredentials(username, password);
            GameContext.get().getGameClient().connect();

            GameLogger.info("Connection attempt started for user: " + username);

        } catch (Exception e) {
            GameLogger.error("Connection error: " + e.getMessage());
            handleConnectionError(e);
        }
    }

    private void setupListeners() {
        if (loginButton != null) {
            loginButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (!isConnecting) {
                        attemptLogin();
                    } else {
                        GameLogger.info("Login already in progress");
                    }
                }
            });
        }

        if (registerButton != null) {
            registerButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (!isConnecting) {
                        attemptRegistration();
                    }
                }
            });
        }

        if (backButton != null) {
            backButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (!isConnecting) {
                        game.setScreen(new ModeSelectionScreen(game));
                    }
                }
            });
        }

        // Add enter key listener for login
        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ENTER && !isConnecting) {
                    attemptLogin();
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public void dispose() {
        stage.dispose();
    }

    @Override
    public void resize(int width, int height) {
        // Enforce a minimum dimension
        width = (int) Math.max(width, MIN_WIDTH);
        height = Math.max(height, MIN_HEIGHT);

        stage.getViewport().update(width, height, true);

        // Let the UI recalc its layout
        mainTable.invalidateHierarchy();
        serverListScrollPane.invalidateHierarchy();
    }

    private void updateStatusLabel(String status, Color color) {
        statusLabel.setText(status);
        statusLabel.setColor(color);
    }

    private void handleLoginError(Exception e) {
        Gdx.app.postRunnable(() -> {
            isConnecting = false;
            connectionProgress.setVisible(false);

            // Clean up any partial client state
            if (GameContext.get().getGameClient() != null) {
                GameContext.get().getGameClient().dispose();
                GameContext.get().setGameClient(null);
            }

            // Show error to user
            String errorMessage = "Login failed: " +
                (e.getMessage() != null ? e.getMessage() : "Unknown error occurred");
            showError(errorMessage);

            // Re-enable UI
            setUIEnabled(true);

            GameLogger.error("Login error: " + e.getMessage());
            e.printStackTrace();
        });
    }

    private void handleInitializationFailure() {
        Gdx.app.postRunnable(() -> {
            isConnecting = false;
            connectionProgress.setVisible(false);

            // Clean up resources
            if (GameContext.get().getGameClient() != null) {
                GameContext.get().getGameClient().dispose();
                GameContext.get().setGameClient(null);
            }

            // Show error dialog with retry option
            Dialog dialog = new Dialog("Initialization Failed", skin) {
                @Override
                protected void result(Object obj) {
                    if ((Boolean) obj) {
                        // Retry connection
                        attemptLogin();
                    } else {
                        // Reset UI state
                        setUIEnabled(true);
                        statusLabel.setText("");
                    }
                }
            };

            dialog.text("Failed to initialize game. Would you like to try again?");
            dialog.button("Retry", true);
            dialog.button("Cancel", false);
            dialog.show(stage);

            GameLogger.error("Game initialization failed");
        });
    }

    private void proceedToGame(AtomicBoolean transitionStarted) {
        if (transitionStarted.get()) {
            return;
        }
        transitionStarted.set(true);

        try {
            validateGameClient();
            GameScreen gameScreen = createGameScreen();

            if (gameScreen != null) {
                // Create and show loading screen
                LoadingScreen loadingScreen = new LoadingScreen(game, gameScreen);
                game.setScreen(loadingScreen);

                // Save credentials if needed
                if (rememberMeBox.isChecked()) {
                    saveCredentials(usernameField.getText(), passwordField.getText());
                }

                // Clean up
                dispose();
            }
        } catch (Exception e) {
            transitionStarted.set(false);
            handleGameTransitionError(e);
        }
    }

    private GameScreen createGameScreen() {
        try {
            String username = usernameField.getText().trim();
            World currentWorld = GameContext.get().getGameClient().getCurrentWorld();

            if (currentWorld == null) {
                throw new IllegalStateException("World not initialized");
            }

            GameContext.get().setGameScreen(new GameScreen(
                game,
                username,
                GameContext.get().getGameClient()
            ));

            // Verify initialization
            if (!GameContext.get().getGameScreen().isInitialized()) {
                throw new IllegalStateException("Game screen failed to initialize");
            }

            return GameContext.get().getGameScreen();

        } catch (Exception e) {
            GameLogger.error("Failed to create game screen: " + e.getMessage());
            return null;
        }
    }

    void handleGameTransitionError(Exception e) {
        Gdx.app.postRunnable(() -> {
            if (GameContext.get().getGameClient() != null) {
                GameContext.get().getGameClient().dispose();
                GameContext.get().setGameClient(null);
            }
            Dialog dialog = new Dialog("Error", skin);
            dialog.text("Failed to start game: " + e.getMessage() + "\nPlease try again.");
            dialog.button("OK", true);
            dialog.show(stage);
            setUIEnabled(true);
            statusLabel.setText("");
            connectionProgress.setVisible(false);
            isConnecting = false;
        });
    }

    private void validateGameClient() {
        if (GameContext.get().getGameClient() == null) {
            throw new IllegalStateException("GameClient is null");
        }

        if (!GameContext.get().getGameClient().isInitialized()) {
            throw new IllegalStateException("GameClient not fully initialized");
        }

        if (GameContext.get().getGameClient().getCurrentWorld() == null) {
            throw new IllegalStateException("World not initialized");
        }
    }

    private void showError(String message) {
        Gdx.app.postRunnable(() -> {
            feedbackLabel.setColor(Color.RED);
            feedbackLabel.setText(message);
            // small shake effect
            stage.addAction(Actions.sequence(
                Actions.moveBy(5f, 0f, 0.05f),
                Actions.moveBy(-10f, 0f, 0.05f),
                Actions.moveBy(5f, 0f, 0.05f)
            ));
            GameLogger.error(message);
        });
    }

    private void setUIEnabled(boolean enabled) {
        float alpha = enabled ? 1f : 0.6f;

        // Disable/enable input fields
        usernameField.setDisabled(!enabled);
        passwordField.setDisabled(!enabled);
        rememberMeBox.setDisabled(!enabled);

        // Disable/enable buttons
        loginButton.setDisabled(!enabled);
        registerButton.setDisabled(!enabled);
        backButton.setDisabled(!enabled);

        // Update visual feedback
        usernameField.setColor(1, 1, 1, alpha);
        passwordField.setColor(1, 1, 1, alpha);
        loginButton.setColor(1, 1, 1, alpha);
        registerButton.setColor(1, 1, 1, alpha);
        backButton.setColor(1, 1, 1, alpha);
        rememberMeBox.setColor(1, 1, 1, alpha);

        // Disable/enable server list entries
        if (serverListTable != null) {
            for (Cell<?> cell : serverListTable.getCells()) {
                Actor actor = cell.getActor();
                if (actor instanceof Table) {
                    actor.setTouchable(enabled ? Touchable.enabled : Touchable.disabled);
                    actor.setColor(1, 1, 1, alpha);
                }
            }
        }
    }

    public void loadServers() {
        try {
            if (servers == null) {
                servers = new Array<>();
            }
            servers.clear();

            ServerConnectionConfig defaultServer = ServerConnectionConfig.getInstance();
            defaultServer.setIconPath(DEFAULT_SERVER_ICON);
            servers.add(defaultServer);

            Preferences serverPrefs = Gdx.app.getPreferences(SERVERS_PREFS);
            String savedServers = serverPrefs.getString("servers", "");

            if (!savedServers.isEmpty()) {
                Json json = new Json();
                for (String serverString : savedServers.split("\\|")) {
                    try {
                        if (!serverString.trim().isEmpty()) {
                            ServerEntry entry = json.fromJson(ServerEntry.class, serverString);
                            if (entry != null && !isDefaultServer(entry)) {
                                ServerConnectionConfig config = getServerConnectionConfig(entry);
                                servers.add(config);
                                GameLogger.info("Loaded server: " + config.getServerName());
                            }
                        }
                    } catch (Exception e) {
                        GameLogger.error("Error loading saved server: " + e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            GameLogger.error("Error loading servers: " + e.getMessage());
            // Ensure we at least have the default server
            if (servers == null || servers.isEmpty()) {
                servers = new Array<>();
                ServerConnectionConfig defaultServer = ServerConnectionConfig.getInstance();
                defaultServer.setIconPath(DEFAULT_SERVER_ICON);
                servers.add(defaultServer);
            }
        }
    }

    private boolean isDefaultServer(ServerEntry entry) {
        return entry.isDefault && "localhost".equals(entry.ip) && entry.tcpPort == 54555;
    }

    private void saveCredentials(String username, String password) {
        GameLogger.info("Saving credentials for: " + username + ", remember: " + true);
        prefs.putBoolean("rememberMe", true);
        prefs.putString("username", username);
        prefs.putString("password", password);
        GameLogger.info("Credentials saved to preferences");
        prefs.flush();
    }

    private void loadSavedCredentials() {
        GameLogger.info("Loading saved credentials");
        boolean rememberMe = prefs.getBoolean("rememberMe", false);

        if (rememberMe) {
            String savedUsername = prefs.getString("username", "");
            String savedPassword = prefs.getString("password", "");

            GameLogger.info("Found saved credentials for: " + savedUsername +
                " (Has password: " + !savedPassword.isEmpty() + ")");

            usernameField.setText(savedUsername);
            passwordField.setText(savedPassword);
            rememberMeBox.setChecked(true);
        } else {
            GameLogger.info("No saved credentials found");
        }
    }

    private void attemptRegistration() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (!validateRegistrationInput(username, password)) {
            return;
        }

        if (selectedServer == null) {
            showError("Please select a server");
            return;
        }

        // Show immediate UI feedback
        isConnecting = true;
        setUIEnabled(false);
        statusLabel.setText("Creating account...");
        statusLabel.setColor(Color.WHITE);
        connectionProgress.setVisible(true);
        connectionProgress.setValue(0);
        feedbackLabel.setText("");

        CompletableFuture.runAsync(() -> {
            try {
                if (GameContext.get().getGameClient() != null) {
                    GameContext.get().getGameClient().dispose();
                    GameClientSingleton.resetInstance();
                }

                GameContext.get().setGameClient(new GameClient(selectedServer));
                GameContext.get().setMultiplayer(true);

                GameContext.get().getGameClient().setRegistrationResponseListener(response -> {
                    Gdx.app.postRunnable(() -> {
                        handleRegistrationResponse(response);
                    });
                });

                GameContext.get().getGameClient().connect();

                Thread.sleep(100);

                GameContext.get().getGameClient().sendRegisterRequest(username, password);

            } catch (Exception e) {
                Gdx.app.postRunnable(() -> handleRegistrationError(e));
            }
        });

        // Start progress animation
        Timer.schedule(new Timer.Task() {
            float progress = 0;

            @Override
            public void run() {
                if (!isConnecting) {
                    cancel();
                    return;
                }
                progress += 0.05f;
                connectionProgress.setValue(Math.min(0.9f, progress));
            }
        }, 0, 0.05f);
    }

    private void handleRegistrationResponse(NetworkProtocol.RegisterResponse response) {
        isConnecting = false;
        connectionProgress.setValue(1);

        if (response.success) {
            // Show success animation
            stage.addAction(Actions.sequence(
                Actions.run(() -> {
                    statusLabel.setText("Account created successfully!");
                    statusLabel.setColor(Color.GREEN);
                    connectionProgress.setColor(Color.GREEN);
                }),
                Actions.delay(1f),
                Actions.run(() -> {
                    // Pre-fill username and clear password
                    usernameField.setText(response.username);
                    passwordField.setText("");

                    // Reset UI state
                    statusLabel.setText("Ready to login");
                    statusLabel.setColor(Color.WHITE);
                    connectionProgress.setVisible(false);
                    setUIEnabled(true);

                    // Show success dialog
                    showSuccessDialog();
                })
            ));
        } else {
            stage.addAction(Actions.sequence(
                Actions.run(() -> {
                    showError(response.message != null ? response.message : "Registration failed");
                    connectionProgress.setVisible(false);
                    setUIEnabled(true);
                }),
                Actions.delay(0.5f),
                Actions.run(() -> {
                    // Re-enable after short delay
                    setUIEnabled(true);
                })
            ));
        }

        // Cleanup
        if (GameContext.get().getGameClient() != null) {
            GameContext.get().getGameClient().dispose();
            GameContext.get().setGameClient(null);
        }
    }

    private void handleLoginFailure(String message) {
        Gdx.app.postRunnable(() -> {
            isConnecting = false;
            connectionProgress.setVisible(false);
            setUIEnabled(true);
            showError(message);

            if (GameContext.get().getGameClient() != null) {
                GameContext.get().getGameClient().dispose();
                GameContext.get().setGameClient(null);
            }
        });
    }

    private void handleTransitionError(Exception e) {
        Gdx.app.postRunnable(() -> {
            isConnecting = false;
            connectionProgress.setVisible(false);
            setUIEnabled(true);

            Dialog errorDialog = new Dialog("Error", skin);
            errorDialog.text("Failed to start game: " + e.getMessage() + "\nWould you like to try again?");
            errorDialog.button("Retry", true);
            errorDialog.button("Cancel", false);
            errorDialog.setMovable(false);

            errorDialog.setPosition(
                (stage.getWidth() - errorDialog.getWidth()) / 2,
                (stage.getHeight() - errorDialog.getHeight()) / 2
            );

            errorDialog.show(stage);
        });
    }

    private void showErrorMessage(String title, String message) {
        Dialog dialog = new Dialog(title, skin) {
            @Override
            protected void result(Object obj) {
                feedbackLabel.setColor(Color.RED);
                feedbackLabel.setText(message);
            }
        };
        dialog.text(message);
        dialog.button("OK", true);
        dialog.show(stage);
    }

    private void handleRegistrationError(Exception e) {
        Gdx.app.postRunnable(() -> {
            showError("Registration failed: " + e.getMessage());
            setUIEnabled(true);
            isConnecting = false;
            connectionProgress.setVisible(false);

            if (GameContext.get().getGameClient() != null) {
                GameContext.get().getGameClient().dispose();
                GameContext.get().setGameClient(null);
            }
        });
    }

    private void showSuccessDialog() {
        Dialog dialog = new Dialog("Registration Successful", skin) {
            @Override
            protected void result(Object obj) {
                if ((Boolean) obj) {
                    // Clear password and update UI
                    passwordField.setText("");
                    statusLabel.setText("Ready to login");
                    statusLabel.setColor(Color.WHITE);
                    feedbackLabel.setText("");
                }
            }
        };

        dialog.text("Your account has been created successfully!\nYou can now log in with your credentials.");
        dialog.button("OK", true);
        dialog.setMovable(false);
        dialog.setModal(true);

        // Center the dialog
        dialog.setPosition(
            (stage.getWidth() - dialog.getWidth()) / 2,
            (stage.getHeight() - dialog.getHeight()) / 2
        );

        dialog.show(stage);
    }

    private boolean validateRegistrationInput(String username, String password) {
        if (username.isEmpty() || password.isEmpty()) {
            showErrorMessage("Invalid Input", "Username and password cannot be empty.");
            return false;
        }

        if (username.length() < 3 || username.length() > 20) {
            showErrorMessage("Invalid Username",
                "Username must be between 3 and 20 characters.");
            return false;
        }

        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            showErrorMessage("Invalid Username",
                "Username can only contain letters, numbers, and underscores.");
            return false;
        }

        String passwordError = validatePassword(password);
        if (passwordError != null) {
            showErrorMessage("Invalid Password", passwordError);
            return false;
        }

        return true;
    }

    private String validatePassword(String password) {
        if (password.length() < 8) {
            return "Password must be at least 8 characters long.";
        }
        if (!password.matches(".*[A-Z].*")) {
            return "Password must contain at least one uppercase letter.";
        }
        if (!password.matches(".*[a-z].*")) {
            return "Password must contain at least one lowercase letter.";
        }
        if (!password.matches(".*\\d.*")) {
            return "Password must contain at least one number.";
        }
        if (!password.matches(".*[!@#$%^&*()\\[\\]{}_+=\\-.,].*")) {
            return "Password must contain at least one special character.";
        }
        return null;
    }

    private boolean validateInput(String username, String password) {
        if (username.isEmpty() || password.isEmpty()) {
            feedbackLabel.setText("Username and password are required");
            return false;
        }
        return true;
    }

    private void fadeToScreen(Screen next) {
        game.setScreen(next);
        dispose();
    }

    @Override
    public void hide() {
        // Implement if needed
    }

    @Override
    public void show() {
        // Implement if needed
    }

    @Override
    public void pause() {
        // Implement if needed
    }

    @Override
    public void resume() {
        // Implement if needed
    }

    private void deleteServer(ServerConnectionConfig server) {
        if (server.isDefault()) {
            showError("Cannot delete default server");
            return;
        }
        Dialog confirm = new Dialog("Confirm Delete", skin) {
            @Override
            protected void result(Object obj) {
                if ((Boolean) obj) {
                    servers.removeValue(server, true);
                    saveServers();
                    updateServerList();
                }
            }
        };
        confirm.text("Are you sure you want to delete this server?");
        confirm.button("Yes", true);
        confirm.button("No", false);
        confirm.show(stage);
    }


    private void showServerDialog(ServerConnectionConfig editServer) {
        ServerManagementDialog dialog = new ServerManagementDialog(
            skin,
            editServer,
            config -> {
                if (editServer != null) {
                    servers.removeValue(editServer, true);
                }
                servers.add(config);
                saveServers();
                updateServerList();
            }
        );
        dialog.show(stage);
    }

    private void createUIComponents() {
        mainTable = new Table();
        mainTable.setFillParent(true);

        // Buttons
        TextButton.TextButtonStyle buttonStyle = new TextButton.TextButtonStyle();
        buttonStyle.up = skin.getDrawable("button");
        buttonStyle.down = skin.getDrawable("button-pressed");
        buttonStyle.over = skin.getDrawable("button-over");
        buttonStyle.font = skin.getFont("default-font");

        loginButton = new TextButton("Login", buttonStyle);
        registerButton = new TextButton("Register", buttonStyle);
        backButton = new TextButton("Back", buttonStyle);

        // TextField style
        TextField.TextFieldStyle textFieldStyle = new TextField.TextFieldStyle(
            skin.get(TextField.TextFieldStyle.class)
        );
        textFieldStyle.font = skin.getFont("default-font");
        textFieldStyle.fontColor = Color.WHITE;
        textFieldStyle.background = new TextureRegionDrawable(TextureManager.ui.findRegion("textfield"));
        textFieldStyle.cursor = skin.getDrawable("cursor");
        textFieldStyle.selection = skin.getDrawable("selection");
        textFieldStyle.messageFontColor = new Color(0.7f, 0.7f, 0.7f, 1f);

        // Input fields
        usernameField = new TextField("", textFieldStyle);
        usernameField.setMessageText("Enter username");
        passwordField = new TextField("", textFieldStyle);
        passwordField.setMessageText("Enter password");
        passwordField.setPasswordMode(true);
        passwordField.setPasswordCharacter('*');

        // Remember-me
        rememberMeBox = new CheckBox(" Remember Me", skin);

        // Labels
        feedbackLabel = new Label("", skin);
        feedbackLabel.setWrap(true);

        statusLabel = new Label("", skin);
        statusLabel.setWrap(true);

        // Progress bar
        ProgressBar.ProgressBarStyle progressStyle = new ProgressBar.ProgressBarStyle();
        progressStyle.background = skin.getDrawable("progress-bar-bg");
        progressStyle.knob = skin.getDrawable("progress-bar-knob");
        progressStyle.knobBefore = skin.getDrawable("progress-bar-bg");
        connectionProgress = new ProgressBar(0, 1, 0.01f, false, progressStyle);
        connectionProgress.setVisible(false);

        // Server list + scrollpane
        serverListScrollPane = createServerList();// Create new buttons for editing/deleting the selected server.
        editServerButton = new TextButton("Edit Server", buttonStyle);
        deleteServerButton = new TextButton("Delete Server", buttonStyle);

        editServerButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (selectedServer != null) {
                    showServerDialog(selectedServer);
                } else {
                    showError("No server selected.");
                }
            }
        });

        deleteServerButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (selectedServer != null) {
                    deleteServer(selectedServer);
                } else {
                    showError("No server selected.");
                }
            }
        });

    }

    private void updateServerSelection(Table selectedEntry) {
        for (Cell<?> cell : serverListTable.getCells()) {
            Actor actor = cell.getActor();
            if (actor instanceof Table) {
                Table entry = (Table) actor;
                if (entry == selectedEntry) {
                    entry.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("textfield-active")));
                } else {
                    entry.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("textfield")));
                }
            }
        }
    }


    private ServerConnectionConfig getSelectedServerConfig() {
        return selectedServer != null ? selectedServer : ServerConfigManager.getDefaultServerConfig();
    }

    // Updated ServerEntry class with icon support
    private static class ServerEntry {
        public String name;
        public String ip;
        public int tcpPort;
        public int udpPort;
        public String motd;
        public boolean isDefault;
        public int maxPlayers;
        public String iconPath;

        public ServerEntry() {
        }

        public ServerEntry(String name, String ip, int tcpPort, int udpPort,
                           String motd, boolean isDefault, int maxPlayers, String iconPath) {
            this.name = name;
            this.ip = ip;
            this.tcpPort = tcpPort;
            this.udpPort = udpPort;
            this.motd = motd;
            this.isDefault = isDefault;
            this.maxPlayers = maxPlayers;
            this.iconPath = iconPath;
        }
    }
}
