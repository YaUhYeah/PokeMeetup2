package io.github.pokemeetup.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.client.GameClientSingleton;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.multiplayer.server.config.ServerConfigManager;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import io.github.pokemeetup.screens.otherui.ServerListEntry;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public class LoginScreen implements Screen {
    public static final String SERVERS_PREFS = "ServerPrefs";
    public static final float MIN_WIDTH = 300f;
    public static final float MAX_WIDTH = 500f;
    private static final String DEFAULT_SERVER_ICON = "ui/default-server-icon.png";
    private static final int MIN_HEIGHT = 600;
    private static final float LOGIN_TIMEOUT = 15f; // 15 seconds for total login process
    private static final float CONNECTION_TIMEOUT = 30f;
    private static final int MAX_CONNECTION_ATTEMPTS = 3;
    public final Stage stage;
    public final Skin skin;
    public final CreatureCaptureGame game;
    private final Preferences prefs;
    private final AtomicBoolean isProcessingLoginResponse = new AtomicBoolean(false);
    public Array<ServerConnectionConfig> servers; // Adding the servers variable
    public TextField usernameField;
    public TextField passwordField;
    public CheckBox rememberMeBox;
    public Label feedbackLabel;
    public SelectBox<ServerConnectionConfig> serverSelect;
    public ServerConnectionConfig selectedServer;
    private float fadeAlpha = 0f;
    private boolean isTransitioning = false;
    private Screen nextScreen = null;
    private Action fadeAction = null;
    private volatile GameClient gameClient;
    private boolean isDisposed = false;
    private ScrollPane serverListPane;
    private Table serverListTable;
    private Array<ServerListEntry> serverEntries;
    private Table mainTable;
    private TextButton loginButton;
    private TextButton registerButton;
    private TextButton backButton;
    private ProgressBar connectionProgress;
    private Label statusLabel;
    // State management
    private float connectionTimer;
    private boolean isConnecting = false;
    private float connectionTimeout = 10f;
    private int connectionAttempts = 0;
    private Label selectedServerInfoLabel;
    private ScrollPane serverListScrollPane;

    public LoginScreen(CreatureCaptureGame game) {
        this.game = game;
        this.stage = new Stage(new ScreenViewport());
        this.skin = new Skin(Gdx.files.internal("atlas/ui-gfx-atlas.json"));
        this.prefs = Gdx.app.getPreferences("LoginPrefs");
        loadServers();

        // Create all UI components
        createUIComponents();

        // Setup listeners after components are created
        setupListeners();

        // Initialize UI layout
        initializeUI();

        // Load saved credentials
        loadSavedCredentials();

        Gdx.input.setInputProcessor(stage);
    }

    private void handleLoginButtonPressed(ServerConnectionConfig serverConfig) {
        String username = usernameField.getText();
        String password = passwordField.getText();

        // Show loading indicator
        statusLabel.setText("Loading world...");
        statusLabel.setVisible(true);

        // Disable UI during login
        setUIEnabled(false);

        // Instantiate GameClient
        gameClient = new GameClient(serverConfig, false, serverConfig.getServerIP(), serverConfig.getTcpPort(), serverConfig.getUdpPort(), null);

        // Set the pending credentials
        gameClient.setPendingCredentials(username, password);

        // Set login response listener before connecting
        gameClient.setLoginResponseListener(response -> {
            if (response.success) {
                Gdx.app.postRunnable(() -> {
                    statusLabel.setText("Initializing world...");

                    // Create LoadingScreen first
                    LoadingScreen loadingScreen = new LoadingScreen(game, null);

                    // Switch to loading screen while world initializes
                    game.setScreen(loadingScreen);

                    try {
                        // Create GameScreen with proper initialization tracking
                        GameScreen gameScreen = new GameScreen(game, username, gameClient, gameClient.getCurrentWorld());

                        // Update loading screen target
                        loadingScreen.setNextScreen(gameScreen);

                        // Dispose login screen
                        dispose();

                        GameLogger.info("Successfully created game screen, waiting for initialization");

                    } catch (Exception e) {
                        GameLogger.error("Failed to create game screen: " + e.getMessage());
                        showError("Failed to initialize game: " + e.getMessage());
                        setUIEnabled(true);
                    }
                });
            } else {
                Gdx.app.postRunnable(() -> {
                    showError(response.message);
                    setUIEnabled(true);
                });
            }
        });

        // Connect to server
        gameClient.connect();
        GameLogger.info("Started connection attempt for: " + username);
    }

    private void createUIComponents() {
        // Create main container
        mainTable = new Table();
        mainTable.setFillParent(true);

        // Create buttons first
        TextButton.TextButtonStyle buttonStyle = new TextButton.TextButtonStyle();
        buttonStyle.up = skin.getDrawable("button");
        buttonStyle.down = skin.getDrawable("button-pressed");
        buttonStyle.over = skin.getDrawable("button-over");
        buttonStyle.font = skin.getFont("default-font");

        loginButton = new TextButton("Login", buttonStyle);
        registerButton = new TextButton("Register", buttonStyle);
        backButton = new TextButton("Back", buttonStyle);

        // Create custom text field style
        TextField.TextFieldStyle textFieldStyle = new TextField.TextFieldStyle(skin.get(TextField.TextFieldStyle.class));
        textFieldStyle.font = skin.getFont("default-font");
        textFieldStyle.fontColor = Color.WHITE;
        textFieldStyle.background = new TextureRegionDrawable(TextureManager.ui.findRegion("textfield"));
        textFieldStyle.cursor = skin.getDrawable("cursor");
        textFieldStyle.selection = skin.getDrawable("selection");
        textFieldStyle.messageFontColor = new Color(0.7f, 0.7f, 0.7f, 1f);

        // Create input fields
        usernameField = new TextField("", textFieldStyle);
        usernameField.setMessageText("Enter username");

        passwordField = new TextField("", textFieldStyle);
        passwordField.setMessageText("Enter password");
        passwordField.setPasswordMode(true);
        passwordField.setPasswordCharacter('*');

        // Create checkbox
        rememberMeBox = new CheckBox(" Remember Me", skin);

        // Create labels
        feedbackLabel = new Label("", skin);
        feedbackLabel.setWrap(true);

        statusLabel = new Label("", skin);
        statusLabel.setWrap(true);

        // Create progress bar
        ProgressBar.ProgressBarStyle progressStyle = new ProgressBar.ProgressBarStyle();
        progressStyle.background = skin.getDrawable("progress-bar-bg");
        progressStyle.knob = skin.getDrawable("progress-bar-knob");
        progressStyle.knobBefore = skin.getDrawable("progress-bar-bg");

        connectionProgress = new ProgressBar(0, 1, 0.01f, false, progressStyle);
        connectionProgress.setVisible(false);

        // Create server list
        serverListScrollPane = createServerList();
    }

    private ScrollPane createServerList() {
        serverListTable = new Table();
        serverListTable.top();

        // Add servers
        for (ServerConnectionConfig server : servers) {
            Table serverEntry = createServerEntry(server);
            serverListTable.add(serverEntry).expandX().fillX().padBottom(2).row();
        }

        // Create scroll pane with styling
        ScrollPane.ScrollPaneStyle scrollStyle = new ScrollPane.ScrollPaneStyle();
        scrollStyle.background = new TextureRegionDrawable(TextureManager.ui.findRegion("textfield"));
        scrollStyle.vScroll = skin.getDrawable("scrollbar-v");
        scrollStyle.vScrollKnob = skin.getDrawable("scrollbar-knob-v");

        ScrollPane scrollPane = new ScrollPane(serverListTable, scrollStyle);
        scrollPane.setScrollingDisabled(true, false);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setOverscroll(false, false);

        return scrollPane;
    }

    private Table createServerEntry(final ServerConnectionConfig server) {
        Table entry = new Table();
        entry.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("info-box-bg")));
        entry.pad(10);

        // Server icon with error handling
        Table iconContainer = new Table();
        try {
            if (server.getIconPath() != null && !server.getIconPath().isEmpty()) {
                FileHandle iconFile = Gdx.files.internal(server.getIconPath());
                if (iconFile.exists()) {
                    Image icon = new Image(new TextureRegionDrawable(new TextureRegion(new Texture(iconFile))));
                    iconContainer.add(icon).size(32, 32);
                } else {
                    // Use default icon
                    Image defaultIcon = new Image(TextureManager.ui.findRegion("default-server-icon"));
                    iconContainer.add(defaultIcon).size(32, 32);
                }
            } else {
                // Use default icon
                Image defaultIcon = new Image(TextureManager.ui.findRegion("default-server-icon"));
                iconContainer.add(defaultIcon).size(32, 32);
            }
        } catch (Exception e) {
            GameLogger.error("Failed to load server icon: " + e.getMessage());
            // Use default icon
            Image defaultIcon = new Image(TextureManager.ui.findRegion("default-server-icon"));
            iconContainer.add(defaultIcon).size(32, 32);
        }

        // Server info
        Table infoTable = new Table();
        infoTable.left();

        Label nameLabel = new Label(server.getServerName(), skin);
        nameLabel.setFontScale(1.1f);

        Label motdLabel = new Label(server.getMotd() != null ? server.getMotd() : "Welcome!", skin);
        motdLabel.setColor(0.8f, 0.8f, 0.8f, 1f);

        Label addressLabel = new Label(server.getServerIP() + ":" + server.getTcpPort(), skin);
        addressLabel.setColor(0.7f, 0.7f, 0.7f, 1f);

        infoTable.add(nameLabel).left().row();
        infoTable.add(motdLabel).left().padTop(2).row();
        infoTable.add(addressLabel).left().padTop(2);

        entry.add(iconContainer).padRight(10);
        entry.add(infoTable).expandX().fillX().left();

        // Selection handling
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

    private void updateServerSelection(Table selectedEntry) {
        // Update visual selection for all entries
        for (Cell<?> cell : serverListTable.getCells()) {
            Actor actor = cell.getActor();
            if (actor instanceof Table) {
                Table entry = (Table) actor;
                TextureRegionDrawable background;
                if (entry == selectedEntry) {
                    background = new TextureRegionDrawable(TextureManager.ui.findRegion("textfield-active"));
                    background.setMinWidth(entry.getWidth());
                    background.setMinHeight(entry.getHeight());
                    entry.setBackground(background);
                } else {
                    background = new TextureRegionDrawable(TextureManager.ui.findRegion("textfield"));
                    background.setMinWidth(entry.getWidth());
                    background.setMinHeight(entry.getHeight());
                    entry.setBackground(background);
                }
            }
        }
    }

    private void initializeUI() {
        float screenWidth = Gdx.graphics.getWidth();
        float contentWidth = Math.min(500, screenWidth * 0.9f);

        // Create dark panel container
        Table darkPanel = new Table();
        darkPanel.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("window")));
        darkPanel.pad(20);

        // Title
        Label titleLabel = new Label("PokeMeetup", skin);
        titleLabel.setFontScale(2f);
        darkPanel.add(titleLabel).padBottom(30).row();

        // Server selection section
        Label serverLabel = new Label("Select Server", skin);
        serverLabel.setFontScale(1.2f);
        darkPanel.add(serverLabel).left().padBottom(10).row();

        // Server list container
        darkPanel.add(serverListScrollPane)
            .width(contentWidth - 40)
            .height(180)
            .padBottom(20)
            .row();

        // Login fields
        Table fieldsTable = new Table();
        fieldsTable.defaults().width(contentWidth - 40).padBottom(10);

        // Username field with label
        Table usernameRow = new Table();
        Label usernameLabel = new Label("Username:", skin);
        usernameRow.add(usernameLabel).width(80).right().padRight(10);
        usernameRow.add(usernameField).expandX().fillX().height(36);
        fieldsTable.add(usernameRow).row();

        // Password field with label
        Table passwordRow = new Table();
        Label passwordLabel = new Label("Password:", skin);
        passwordRow.add(passwordLabel).width(80).right().padRight(10);
        passwordRow.add(passwordField).expandX().fillX().height(36);
        fieldsTable.add(passwordRow).row();

        // Remember me checkbox
        rememberMeBox = new CheckBox(" Remember Me", skin);
        fieldsTable.add(rememberMeBox).left().padTop(5).row();

        darkPanel.add(fieldsTable).row();

        // Buttons
        Table buttonTable = new Table();
        float buttonWidth = (contentWidth - 60) / 2;

        // Login and Register buttons
        Table actionButtons = new Table();
        actionButtons.add(loginButton).width(buttonWidth).padRight(10);
        actionButtons.add(registerButton).width(buttonWidth);
        buttonTable.add(actionButtons).padBottom(10).row();

        // Back button
        buttonTable.add(backButton).width(buttonWidth);

        darkPanel.add(buttonTable).padTop(20).padBottom(10).row();

        // Status elements
        Table statusTable = new Table();
        statusTable.add(statusLabel).width(contentWidth - 40).padBottom(5).row();
        statusTable.add(connectionProgress).width(contentWidth - 40).height(4).padBottom(5).row();
        statusTable.add(feedbackLabel).width(contentWidth - 40);

        darkPanel.add(statusTable).row();

        // Add to main table
        mainTable.add(darkPanel);
        // Select first server by default if available

        stage.addActor(mainTable);
        if (servers.isEmpty()) {
            return;
        }
        if (servers != null && servers.size > 0) {
            selectedServer = servers.first();
            updateServerSelection((Table) serverListTable.getCells().first().getActor());
        }
    }private void attemptLogin() {
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
            if (gameClient != null) {
                gameClient.dispose();
                GameClientSingleton.resetInstance();
            }

            // Create new client
            gameClient = new GameClient(
                selectedServer,
                false,
                selectedServer.getServerIP(),
                selectedServer.getTcpPort(),
                selectedServer.getUdpPort(),
                null
            );

            // Set up response handlers
            gameClient.setLoginResponseListener(response -> {
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
            gameClient.setInitializationListener(success -> {
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
            gameClient.setPendingCredentials(username, password);
            gameClient.connect();

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

            // Create game screen
            GameScreen gameScreen = new GameScreen(
                game,
                response.username,
                gameClient,
                gameClient.getCurrentWorld()
            );

            // Update loading screen target
            loadingScreen.setNextScreen(gameScreen);

            // Save credentials if needed
            if (rememberMeBox.isChecked()) {
                saveCredentials(usernameField.getText(), passwordField.getText(), true);
            }

            // Clean up
            dispose();

        } catch (Exception e) {
            transitionStarted.set(false);
            handleGameCreationError(e);
        }
    }

    private void handleLoginResponse(NetworkProtocol.LoginResponse response) {
        if (isDisposed) {
            GameLogger.error("Login screen disposed, ignoring response");
            return;
        }

        Gdx.app.postRunnable(() -> {
            try {
                if (response.success) {
                    GameLogger.info("Processing successful login for: " + response.username);
                    handleSuccessfulLoginAttempt(response);
                } else if (response.message != null && response.message.contains("already logged in")) {
                    // Special handling for already logged in case
                    handleAlreadyLoggedIn();
                } else {
                    handleLoginFailure(response.message);
                }
            } catch (Exception e) {
                GameLogger.error("Error handling login response: " + e.getMessage());
                handleLoginFailure("Internal error occurred");
            }
        });
    }

    private void handleAlreadyLoggedIn() {
        isConnecting = false;
        connectionProgress.setVisible(false);

        // Show warning dialog
        Dialog dialog = new Dialog("Already Logged In", skin) {
            @Override
            protected void result(Object obj) {
                if ((Boolean) obj) {
                    // User chose to force login - attempt relogin with force flag
                    forceRelogin();
                } else {
                    // User cancelled - reset UI
                    setUIEnabled(true);
                    statusLabel.setText("");
                    feedbackLabel.setText("Login cancelled");
                }
            }
        };

        dialog.text("This account is already logged in.\nWould you like to disconnect the other session?");
        dialog.button("Yes, log out other session", true);
        dialog.button("Cancel", false);
        dialog.show(stage);
    }

    private void forceRelogin() {
        if (gameClient != null) {
            gameClient.dispose();
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
            gameClient = new GameClient(selectedServer, false,
                selectedServer.getServerIP(),
                selectedServer.getTcpPort(),
                selectedServer.getUdpPort(),
                null);

            // Set login response listener
            gameClient.setLoginResponseListener(this::handleForceLoginResponse);

            // Set force login flag and credentials
            gameClient.setPendingCredentials(username, password);
            gameClient.connect();

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
            if (gameClient == null || !gameClient.isConnected()) {
                throw new IllegalStateException("Invalid game client state");
            }

            // Create loading screen
            LoadingScreen loadingScreen = new LoadingScreen(game, null);
            game.setScreen(loadingScreen);

            // Initialize game screen
            GameScreen gameScreen = new GameScreen(
                game,
                response.username,
                gameClient,
                gameClient.getCurrentWorld()
            );

            // Update loading screen target
            loadingScreen.setNextScreen(gameScreen);

            // Save credentials if needed
            if (rememberMeBox.isChecked()) {
                saveCredentials(usernameField.getText(), passwordField.getText(), true);
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
            if (gameClient != null) {
                gameClient.dispose();
                gameClient = null;
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
        if (gameClient != null) {
            gameClient.tick(delta);
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
            if (gameClient != null) {
                gameClient.dispose();
            }
            GameClientSingleton.resetInstance();

            // Create new client instance
            gameClient = GameClientSingleton.getInstance(server);
            if (gameClient == null) {
                GameLogger.error("Failed to initialize GameClient.");
                handleConnectionError(new Exception("Failed to initialize GameClient."));
                return;
            }

            // Set up login response listener BEFORE connecting
            gameClient.setLoginResponseListener(this::handleLoginResponse);

            // Set up initialization listener
            gameClient.setInitializationListener(success -> {
                if (success) {
                    GameLogger.info("Game client initialization successful");

                    // CRITICAL: Create and switch to game screen immediately
                    Gdx.app.postRunnable(() -> {
                        try {
                            GameScreen gameScreen = new GameScreen(
                                game,
                                username,
                                gameClient,
                                game.getCurrentWorld()
                            );
                            game.setScreen(gameScreen);
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
            gameClient.setPendingCredentials(username, password);
            gameClient.connect();

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

        // Add enter key listener
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
        isDisposed = true;
        stage.dispose();
    }

    @Override
    public void resize(int width, int height) {
        // Enforce minimum dimensions
        width = (int) Math.max(width, MIN_WIDTH);
        height = Math.max(height, MIN_HEIGHT);

        GameLogger.info("Resizing login screen to: " + width + "x" + height);

        stage.getViewport().update(width, height, true);

        // Recalculate UI dimensions
        float contentWidth = Math.min(MAX_WIDTH, width * 0.9f);

        // Update main container size
        if (mainTable != null) {
            mainTable.setWidth(contentWidth);
            mainTable.invalidateHierarchy();
        }

        // Update server list if it exists
        if (serverListScrollPane != null) {
            serverListScrollPane.setWidth(contentWidth - 40);
            serverListScrollPane.invalidateHierarchy();
        }

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
            if (gameClient != null) {
                gameClient.dispose();
                gameClient = null;
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
            if (gameClient != null) {
                gameClient.dispose();
                gameClient = null;
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
                    saveCredentials(usernameField.getText(), passwordField.getText(), true);
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
            World currentWorld = gameClient.getCurrentWorld();

            if (currentWorld == null) {
                throw new IllegalStateException("World not initialized");
            }

            GameScreen gameScreen = new GameScreen(
                game,
                username,
                gameClient,
                currentWorld
            );

            // Verify initialization
            if (!gameScreen.isInitialized()) {
                throw new IllegalStateException("Game screen failed to initialize");
            }

            return gameScreen;

        } catch (Exception e) {
            GameLogger.error("Failed to create game screen: " + e.getMessage());
            return null;
        }
    }
    private void handleGameScreenCreationError() {
        Gdx.app.postRunnable(() -> {
            // Return to login screen state
            game.setScreen(new LoginScreen(game));

            // Show error dialog
            Dialog dialog = new Dialog("Error", skin);
            dialog.text("Failed to create game screen. Please try again.");
            dialog.button("OK", true);
            dialog.show(stage);
        });
    } void handleGameTransitionError(Exception e) {
        Gdx.app.postRunnable(() -> {
            // Clean up any partial state
            if (gameClient != null) {
                gameClient.dispose();
                gameClient = null;
            }

            // Show error dialog
            Dialog dialog = new Dialog("Error", skin);
            dialog.text("Failed to start game: " + e.getMessage() + "\nPlease try again.");
            dialog.button("OK", true);
            dialog.show(stage);

            // Reset UI state
            setUIEnabled(true);
            statusLabel.setText("");
            connectionProgress.setVisible(false);
            isConnecting = false;
        });
    }

    private void validateGameClient() {
        if (gameClient == null) {
            throw new IllegalStateException("GameClient is null");
        }

        if (!gameClient.isInitialized()) {
            throw new IllegalStateException("GameClient not fully initialized");
        }

        if (gameClient.getCurrentWorld() == null) {
            throw new IllegalStateException("World not initialized");
        }
    }

    // Helper method to show errors in a consistent way
    private void showError(String message) {
        Gdx.app.postRunnable(() -> {
            feedbackLabel.setColor(Color.RED);
            feedbackLabel.setText(message);

            // Add screen shake effect for feedback
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

    public Array<ServerConnectionConfig> loadServers() {
        try {
            if (servers == null) {
                servers = new Array<>();
            }
            servers.clear();

            // Always add default server first
            ServerConnectionConfig defaultServer = ServerConnectionConfig.getInstance();
            defaultServer.setIconPath(DEFAULT_SERVER_ICON);
            servers.add(defaultServer);

            // Load saved servers from preferences
            Preferences serverPrefs = Gdx.app.getPreferences(SERVERS_PREFS);
            String savedServers = serverPrefs.getString("servers", "");

            if (!savedServers.isEmpty()) {
                Json json = new Json();
                for (String serverString : savedServers.split("\\|")) {
                    try {
                        if (!serverString.trim().isEmpty()) {
                            ServerEntry entry = json.fromJson(ServerEntry.class, serverString);
                            if (entry != null && !isDefaultServer(entry)) {
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
        return servers;
    }

    private boolean isDefaultServer(ServerEntry entry) {
        return entry.isDefault && "localhost".equals(entry.ip) && entry.tcpPort == 54555;
    }

    private void saveCredentials(String username, String password, boolean remember) {
        GameLogger.info("Saving credentials for: " + username + ", remember: " + remember);
        prefs.putBoolean("rememberMe", remember);
        if (remember) {
            prefs.putString("username", username);
            prefs.putString("password", password);
            GameLogger.info("Credentials saved to preferences");
        } else {
            prefs.remove("username");
            prefs.remove("password");
            GameLogger.info("Credentials removed from preferences");
        }
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

        // Create connection with timeout
        CompletableFuture.runAsync(() -> {
            try {
                // Cleanup existing client
                if (gameClient != null) {
                    gameClient.dispose();
                    GameClientSingleton.resetInstance();
                }

                // Create new client
                gameClient = new GameClient(
                    selectedServer,
                    false,
                    selectedServer.getServerIP(),
                    selectedServer.getTcpPort(),
                    selectedServer.getUdpPort(),
                    null
                );

                // Set registration response handler
                gameClient.setRegistrationResponseListener(response -> {
                    Gdx.app.postRunnable(() -> {
                        handleRegistrationResponse(response);
                    });
                });

                // Connect and send registration
                gameClient.connect();

                // Small delay to ensure connection is ready
                Thread.sleep(100);

                gameClient.sendRegisterRequest(username, password);

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
        if (gameClient != null) {
            gameClient.dispose();
            gameClient = null;
        }
    }
    private void handleLoginFailure(String message) {
        Gdx.app.postRunnable(() -> {
            isConnecting = false;
            connectionProgress.setVisible(false);
            setUIEnabled(true);
            showError(message);

            if (gameClient != null) {
                gameClient.dispose();
                gameClient = null;
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

    private void setupButtonListeners(TextButton loginButton, TextButton registerButton, TextButton backButton) {
        loginButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                attemptLogin();
            }
        });

        registerButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                attemptRegistration();
            }
        });

        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                fadeToScreen(new ModeSelectionScreen(game));
            }
        });
    }

    private void handleRegistrationError(Exception e) {
        Gdx.app.postRunnable(() -> {
            showError("Registration failed: " + e.getMessage());
            setUIEnabled(true);
            isConnecting = false;
            connectionProgress.setVisible(false);

            if (gameClient != null) {
                gameClient.dispose();
                gameClient = null;
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
    }

    @Override
    public void show() {
    }

    @Override
    public void pause() {
        // Implement if needed
    }

    @Override
    public void resume() {
        // Implement if needed
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
