package io.github.pokemeetup.screens;

import com.badlogic.gdx.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.viewport.FitViewport;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import io.github.pokemeetup.screens.otherui.ServerManagementDialog;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;


public class LoginScreen implements Screen {

    // == Constants ==

    public static final String SERVERS_PREFS = "ServerPrefs";
    public static final float MIN_WIDTH = 300f;
    public static final float MAX_WIDTH = 500f;
    private static final String DEFAULT_SERVER_ICON = "ui/default-server-icon.png";
    private static final int MIN_HEIGHT = 600;
    private static final float CONNECTION_TIMEOUT = 30f;
    private static final int MAX_CONNECTION_ATTEMPTS = 3;

    // Virtual resolution (for the FitViewport)
    private static final float VIRTUAL_WIDTH = 800;
    private static final float VIRTUAL_HEIGHT = 600;

    // == Fields ==
    public final Stage stage;
    public final Skin skin;
    public final CreatureCaptureGame game;
    private final Preferences prefs;
    public Array<ServerConnectionConfig> servers;
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

    // == Constructor ==
    public LoginScreen(CreatureCaptureGame game) {
        this.game = game;
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

    // == Screen Implementation ==

    @Override
    public void show() {
        GameContext.get().setMultiplayer(true);
    }

    @Override
    public void render(float delta) {
        if (isConnecting) {
            connectionTimer += delta;
            connectionProgress.setValue(connectionTimer / CONNECTION_TIMEOUT);

            if (connectionTimer >= CONNECTION_TIMEOUT) {
                handleConnectionTimeout();
                return;
            }
        }

        Gdx.gl.glClearColor(0.1f, 0.1f, 0.1f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stage.act(delta);
        stage.draw();

        // <-- CHANGED: We do not call any "client.connect()" here
        if (GameContext.get().getGameClient() != null) {
            GameContext.get().getGameClient().tick();
        }
    }

    @Override
    public void resize(int width, int height) {
        width = (int) Math.max(width, MIN_WIDTH);
        height = Math.max(height, MIN_HEIGHT);

        stage.getViewport().update(width, height, true);
        mainTable.invalidateHierarchy();
        serverListScrollPane.invalidateHierarchy();
    }

    @Override
    public void pause() {
        // Not needed
    }

    @Override
    public void resume() {
        // Not needed
    }

    @Override
    public void hide() {
        // Not needed
    }

    @Override
    public void dispose() {
        stage.dispose();
    }

    // == UI Creation & Setup ==

    private void createUIComponents() {
        mainTable = new Table();
        mainTable.setFillParent(true);

        TextButton.TextButtonStyle buttonStyle = new TextButton.TextButtonStyle();
        buttonStyle.up = skin.getDrawable("button");
        buttonStyle.down = skin.getDrawable("button-pressed");
        buttonStyle.over = skin.getDrawable("button-over");
        buttonStyle.font = skin.getFont("default-font");

        loginButton = new TextButton("Login", buttonStyle);
        registerButton = new TextButton("Register", buttonStyle);
        backButton = new TextButton("Back", buttonStyle);

        TextField.TextFieldStyle textFieldStyle = new TextField.TextFieldStyle(
            skin.get(TextField.TextFieldStyle.class)
        );
        textFieldStyle.font = skin.getFont("default-font");
        textFieldStyle.fontColor = Color.WHITE;
        textFieldStyle.background = new TextureRegionDrawable(TextureManager.ui.findRegion("textfield"));
        textFieldStyle.cursor = skin.getDrawable("cursor");
        textFieldStyle.selection = skin.getDrawable("selection");
        textFieldStyle.messageFontColor = new Color(0.7f, 0.7f, 0.7f, 1f);

        usernameField = new TextField("", textFieldStyle);
        usernameField.setMessageText("Enter username");

        passwordField = new TextField("", textFieldStyle);
        passwordField.setMessageText("Enter password");
        passwordField.setPasswordMode(true);
        passwordField.setPasswordCharacter('*');

        rememberMeBox = new CheckBox(" Remember Me", skin);

        feedbackLabel = new Label("", skin);
        feedbackLabel.setWrap(true);

        statusLabel = new Label("", skin);
        statusLabel.setWrap(true);

        ProgressBar.ProgressBarStyle progressStyle = new ProgressBar.ProgressBarStyle();
        progressStyle.background = skin.getDrawable("progress-bar-bg");
        progressStyle.knob = skin.getDrawable("progress-bar-knob");
        progressStyle.knobBefore = skin.getDrawable("progress-bar-bg");
        connectionProgress = new ProgressBar(0, 1, 0.01f, false, progressStyle);
        connectionProgress.setVisible(false);

        serverListScrollPane = createServerList();

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

    private void initializeUI() {
        float screenWidth = stage.getWidth();
        float screenHeight = stage.getHeight();

        float contentWidth = Math.min(MAX_WIDTH, screenWidth * 0.9f);
        float contentHeight = Math.min(700, screenHeight * 0.9f);

        Table darkPanel = new Table();
        darkPanel.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("window")));
        darkPanel.pad(20);

        Label titleLabel = new Label("PokéMeetup", skin, "title");
        titleLabel.setFontScale(2f);
        darkPanel.add(titleLabel).padBottom(30).row();

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
        });

        serverHeader.add(addServerButton).right();
        darkPanel.add(serverHeader).fillX().padBottom(10).row();

        darkPanel.add(serverListScrollPane)
            .width(contentWidth - 40)
            .height(Math.min(220, screenHeight * 0.25f))
            .padBottom(20)
            .row();

        Table loginForm = createLoginForm(contentWidth);
        darkPanel.add(loginForm).width(contentWidth - 40).row();

        Table serverActionTable = new Table();
        serverActionTable.defaults().width((contentWidth - 40) / 2f).height(40).padTop(10);
        serverActionTable.add(editServerButton).padRight(10);
        serverActionTable.add(deleteServerButton);
        darkPanel.add(serverActionTable).padBottom(20).row();

        Table statusSection = new Table();
        statusSection.add(statusLabel).width(contentWidth - 40).padBottom(5).row();
        statusSection.add(connectionProgress).width(contentWidth - 40).height(4).padBottom(5).row();
        statusSection.add(feedbackLabel).width(contentWidth - 40);
        darkPanel.add(statusSection).row();

        mainTable.clear();
        mainTable.setFillParent(true);
        mainTable.center().pad(10);
        mainTable.add(darkPanel);
        darkPanel.pack();
        stage.addActor(mainTable);

        // Place the back button at top-left
        Table topLeftTable = new Table();
        topLeftTable.setFillParent(true);
        topLeftTable.top().left();
        topLeftTable.add(backButton);
        stage.addActor(topLeftTable);
    }

    private Table createLoginForm(float width) {
        Table form = new Table();
        form.defaults().width(width - 40).padBottom(10);

        Table usernameRow = new Table();
        Label usernameLabel = new Label("Username:", skin);
        usernameRow.add(usernameLabel).width(80).right().padRight(10);
        usernameRow.add(usernameField).expandX().fillX().height(36);
        form.add(usernameRow).row();

        Table passwordRow = new Table();
        Label passwordLabel = new Label("Password:", skin);
        passwordRow.add(passwordLabel).width(80).right().padRight(10);
        passwordRow.add(passwordField).expandX().fillX().height(36);
        form.add(passwordRow).row();

        form.add(rememberMeBox).left().padTop(5).row();

        Table buttons = new Table();
        buttons.defaults().width((width - 60) / 2f).height(40);
        buttons.add(loginButton).padRight(10);
        buttons.add(registerButton);
        form.add(buttons).padTop(20);

        return form;
    }

    private ScrollPane createServerList() {
        serverListTable = new Table();
        serverListTable.top();

        for (ServerConnectionConfig server : servers) {
            Table serverEntry = createServerEntry(server);
            serverListTable.add(serverEntry).expandX().fillX().padBottom(2).row();
        }

        ScrollPane.ScrollPaneStyle scrollStyle = new ScrollPane.ScrollPaneStyle();
        scrollStyle.background = new TextureRegionDrawable(TextureManager.ui.findRegion("textfield"));
        scrollStyle.vScroll = skin.getDrawable("scrollbar-v");
        scrollStyle.vScrollKnob = skin.getDrawable("scrollbar-knob-v");

        ScrollPane scrollPane = new ScrollPane(serverListTable, scrollStyle);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(true, false);
        scrollPane.setForceScroll(false, true);
        scrollPane.setOverscroll(false, false);

        return scrollPane;
    }

    // == Server Management ==

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

    private void updateServerList() {
        serverListTable.clear();
        serverListTable.top();

        Label headerLabel = new Label("Available Servers", skin, "title-small");
        serverListTable.add(headerLabel).pad(10).row();

        boolean hasServers = false;
        for (ServerConnectionConfig server : servers) {
            Table serverEntry = createServerEntry(server);
            serverListTable.add(serverEntry).expandX().fillX().padBottom(2).row();
            hasServers = true;
        }

        if (!hasServers) {
            Label emptyLabel = new Label("No servers available.", skin);
            emptyLabel.setColor(Color.GRAY);
            serverListTable.add(emptyLabel).pad(20);
        }

        if (selectedServer == null && servers.size > 0) {
            selectedServer = servers.first();
            Cell<?> firstCell = serverListTable.getCells().first();
            if (firstCell != null && firstCell.getActor() instanceof Table) {
                updateServerSelection((Table) firstCell.getActor());
            }
        }

        serverListTable.invalidate();
        serverListScrollPane.invalidate();
        saveServers();

        GameLogger.info("Server list updated with " + servers.size + " servers");
    }

    private Table createServerEntry(final ServerConnectionConfig server) {
        Table entry = new Table();
        entry.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("textfield")));
        entry.pad(10);

        Table iconContainer = new Table();
        try {
            if (server.getIconPath() != null) {
                FileHandle iconFile = Gdx.files.internal(server.getIconPath());
                if (iconFile.exists()) {
                    Image icon = new Image(new Texture(iconFile));
                    icon.setSize(32, 32);
                    iconContainer.add(icon).size(32);
                } else {
                    addDefaultIcon(iconContainer);
                }
            } else {
                addDefaultIcon(iconContainer);
            }
        } catch (Exception e) {
            GameLogger.error("Failed to load server icon: " + e.getMessage());
            addDefaultIcon(iconContainer);
        }

        Table infoPanel = new Table();
        infoPanel.defaults().expandX().fillX().space(5);
        infoPanel.left();

        Label nameLabel = new Label(server.getServerName(), skin, "title-small");
        nameLabel.setEllipsis(true);
        infoPanel.add(nameLabel).left().expandX().fillX().height(24).row();

        Label motdLabel = new Label(server.getMotd() != null ? server.getMotd() : "Welcome!", skin, "default");
        motdLabel.setEllipsis(true);
        motdLabel.setColor(0.8f, 0.8f, 0.8f, 1f);
        infoPanel.add(motdLabel).left().expandX().fillX().height(24).row();

        Label addressLabel = new Label(server.getServerIP() + ":" + server.getTcpPort(), skin, "small");
        addressLabel.setEllipsis(true);
        addressLabel.setColor(0.7f, 0.7f, 0.7f, 1f);
        infoPanel.add(addressLabel).left().expandX().fillX().height(24).row();

        entry.add(iconContainer).padRight(10).width(40);
        entry.add(infoPanel).expandX().fillX();

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
        if (TextureManager.ui.findRegion("default-server-icon") != null) {
            Image defaultIcon = new Image(TextureManager.ui.findRegion("default-server-icon"));
            defaultIcon.setSize(32, 32);
            container.add(defaultIcon).size(32);
        } else {
            FileHandle iconFile = Gdx.files.internal(DEFAULT_SERVER_ICON);
            if (iconFile.exists()) {
                Image defaultIcon = new Image(new Texture(iconFile));
                defaultIcon.setSize(32, 32);
                container.add(defaultIcon).size(32);
            }
        }
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

    // == Listeners & Event Handling ==

    private void setupListeners() {
        if (loginButton != null) {
            loginButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    attemptLogin();
                }
            });
        }

        if (registerButton != null) {
            registerButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    attemptRegistration();
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

    private void setupInputHandling() {
        boolean isMobile = Gdx.app.getType() == Application.ApplicationType.Android
            || Gdx.app.getType() == Application.ApplicationType.iOS;
        float touchPadding = isMobile ? 12 : 6;

        loginButton.padTop(touchPadding).padBottom(touchPadding);
        registerButton.padTop(touchPadding).padBottom(touchPadding);

        // If needed, you can add ripple effect here for mobile, etc.
        // ...
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

    // == Login Flow ==
    private void attemptLogin() {
        final String username = usernameField.getText().trim();
        final String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Username and password are required");
            return;
        }
        if (selectedServer == null) {
            showError("Please select a server");
            return;
        }

        setUIEnabled(false);
        statusLabel.setText("Connecting to server...");
        connectionProgress.setVisible(true);
        connectionProgress.setValue(0f);

        // <-- CHANGED: We always create exactly ONE GameClient here, no extra calls
        final GameClient client = new GameClient(selectedServer);
        GameContext.get().setGameClient(client);

        // Now use the single connectIfNeeded
        isConnecting = true;  // mark that we are mid-connection
        connectionTimer = 0;  // reset the local timer
        client.connectIfNeeded(
            // onSuccess
            () -> {
                Gdx.app.postRunnable(() -> {
                    statusLabel.setText("Connected. Logging in...");
                    // Now do the login request
                    client.sendLoginRequest(
                        username,
                        password,
                        (NetworkProtocol.LoginResponse lr) -> {
                            // This is your “onResponse”
                            isConnecting = false;
                            connectionProgress.setVisible(false);

                            if (lr.success) {
                                // Optionally store credentials if remember me is checked:
                                if (rememberMeBox.isChecked()) {
                                    saveCredentials(username, password);
                                }
                                setUIEnabled(true);
                                proceedToGame(lr);
                            } else {
                                setUIEnabled(true);
                                showError("Login failed: " + lr.message);
                            }
                        },
                        (String error) -> {
                            // onError from sendLoginRequest
                            isConnecting = false;
                            connectionProgress.setVisible(false);
                            setUIEnabled(true);
                            showError("Login request error: " + error);
                        }
                    );
                });
            },

            // onError
            (String errorMsg) -> {
                // Connection error
                Gdx.app.postRunnable(() -> {
                    isConnecting = false;
                    connectionProgress.setVisible(false);
                    setUIEnabled(true);
                    showError("Connection failed: " + errorMsg);
                });
            }
        , REGISTRATION_CONNECT_TIMEOUT_MS);
    }

    private void proceedToGame(NetworkProtocol.LoginResponse response) {
        try {
            LoadingScreen loadingScreen = new LoadingScreen(game, null);
            game.setScreen(loadingScreen);

            GameScreen gameScreen = new GameScreen(
                game,
                response.username,
                GameContext.get().getGameClient()
            );
            GameContext.get().setGameScreen(gameScreen);
            loadingScreen.setNextScreen(gameScreen);

            // Do not call dispose() on the login screen here.
        } catch (Exception e) {
            GameLogger.error("Failed to transition to game: " + e.getMessage());
            showError("Failed to start game: " + e.getMessage());
        }
    }

    // == Registration Flow ==

    private void attemptRegistration() {
        if (isConnecting) return;
        final String username = usernameField.getText().trim();
        final String password = passwordField.getText().trim();

        if (!validateRegistrationInput(username, password)) {
            return;
        }
        if (selectedServer == null) {
            showError("Please select a server");
            return;
        }
        isConnecting = true;
        connectionTimer = 0;
        setUIEnabled(false);
        statusLabel.setText("Creating account...");
        statusLabel.setColor(Color.WHITE);
        feedbackLabel.setText("");
        connectionProgress.setVisible(true);
        connectionProgress.setValue(0);

        // <-- CHANGED: Only create a single new GameClient
        final GameClient client = new GameClient(selectedServer);
        GameContext.get().setGameClient(client);

        // Connect asynchronously and then send registration request with callbacks
        client.connectIfNeeded(
            () -> {
                Gdx.app.postRunnable(() -> {
                    client.sendRegisterRequest(
                        username,
                        password,
                        (NetworkProtocol.RegisterResponse response) -> {
                            Gdx.app.postRunnable(() -> {
                                isConnecting = false;
                                connectionProgress.setVisible(false);

                                if (response.success) {
                                    showSuccessDialog(response.username);
                                } else {
                                    setUIEnabled(true);
                                    showError(response.message != null ? response.message : "Registration failed");
                                }
                            });
                        },
                        (String errorMsg) -> {
                            Gdx.app.postRunnable(() -> {
                                isConnecting = false;
                                connectionProgress.setVisible(false);
                                setUIEnabled(true);
                                showError("Registration failed: " + errorMsg);
                            });
                        }
                    );
                });
            },
            (String errorMsg) -> {
                Gdx.app.postRunnable(() -> {
                    isConnecting = false;
                    connectionProgress.setVisible(false);
                    setUIEnabled(true);
                    showError("Connection error: " + errorMsg);
                });
            }
        , REGISTRATION_CONNECT_TIMEOUT_MS);
    }
    private static final long REGISTRATION_CONNECT_TIMEOUT_MS = 10000; // 10 seconds

    private void showSuccessDialog(String createdUsername) {
        Dialog dialog = new Dialog("Registration Successful", skin) {
            @Override
            protected void result(Object obj) {
                if ((Boolean) obj) {
                    usernameField.setText(createdUsername);
                    passwordField.setText("");
                    statusLabel.setText("Ready to login");
                    statusLabel.setColor(Color.WHITE);
                    feedbackLabel.setText("");
                    setUIEnabled(true);
                }
            }
        };

        dialog.text("Your account has been created successfully!\nYou can now log in with your credentials.");
        dialog.button("OK", true);
        dialog.setMovable(false);
        dialog.setModal(true);
        dialog.show(stage);
    }

    // == Utility & Validation ==


    private boolean validateRegistrationInput(String username, String password) {
        if (username.isEmpty() || password.isEmpty()) {
            showErrorMessage("Invalid Input", "Username and password cannot be empty.");
            return false;
        }

        if (username.length() < 3 || username.length() > 20) {
            showErrorMessage("Invalid Username", "Username must be between 3 and 20 characters.");
            return false;
        }

        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            showErrorMessage("Invalid Username", "Username can only contain letters, numbers, and underscores.");
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

    private void showError(String message) {
        feedbackLabel.setColor(Color.RED);
        feedbackLabel.setText(message);
        stage.addAction(Actions.sequence(
            Actions.moveBy(5f, 0f, 0.05f),
            Actions.moveBy(-10f, 0f, 0.05f),
            Actions.moveBy(5f, 0f, 0.05f)
        ));
        GameLogger.error(message);
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

    private void setUIEnabled(boolean enabled) {
        float alpha = enabled ? 1f : 0.6f;

        usernameField.setDisabled(!enabled);
        passwordField.setDisabled(!enabled);
        rememberMeBox.setDisabled(!enabled);

        loginButton.setDisabled(!enabled);
        registerButton.setDisabled(!enabled);
        backButton.setDisabled(!enabled);

        usernameField.setColor(1, 1, 1, alpha);
        passwordField.setColor(1, 1, 1, alpha);
        loginButton.setColor(1, 1, 1, alpha);
        registerButton.setColor(1, 1, 1, alpha);
        backButton.setColor(1, 1, 1, alpha);
        rememberMeBox.setColor(1, 1, 1, alpha);

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

    private void saveCredentials(String username, String password) {
        prefs.putBoolean("rememberMe", true);
        prefs.putString("username", username);
        prefs.putString("password", password);
        prefs.flush();
        GameLogger.info("Credentials saved to preferences");
    }

    private void loadSavedCredentials() {
        boolean rememberMe = prefs.getBoolean("rememberMe", false);
        if (rememberMe) {
            String savedUsername = prefs.getString("username", "");
            String savedPassword = prefs.getString("password", "");
            usernameField.setText(savedUsername);
            passwordField.setText(savedPassword);
            rememberMeBox.setChecked(true);
        }
    }

    public void loadServers() {
        if (servers == null) {
            servers = new Array<>();
        }
        servers.clear();

        // Ensure we have a default server
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
                        }
                    }
                } catch (Exception e) {
                    GameLogger.error("Error loading saved server: " + e.getMessage());
                }
            }
        }
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

    private boolean isDefaultServer(ServerEntry entry) {
        return entry.isDefault && "localhost".equals(entry.ip) && entry.tcpPort == 54555;
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

    // == Inner Class ==

    private static class ServerEntry {
        public String name;
        public String ip;
        public int tcpPort;
        public int udpPort;
        public String motd;
        public boolean isDefault;
        public int maxPlayers;
        public String iconPath;

        public ServerEntry() {}

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
