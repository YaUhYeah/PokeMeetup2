    package io.github.pokemeetup.screens;

    import com.badlogic.gdx.*;
    import com.badlogic.gdx.files.FileHandle;
    import com.badlogic.gdx.graphics.Color;
    import com.badlogic.gdx.graphics.GL20;
    import com.badlogic.gdx.graphics.Pixmap;
    import com.badlogic.gdx.graphics.Texture;
    import com.badlogic.gdx.graphics.g2d.TextureAtlas;
    import com.badlogic.gdx.graphics.g2d.TextureRegion;
    import com.badlogic.gdx.scenes.scene2d.*;
    import com.badlogic.gdx.scenes.scene2d.actions.Actions;
    import com.badlogic.gdx.scenes.scene2d.ui.*;
    import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
    import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
    import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
    import com.badlogic.gdx.utils.Align;
    import com.badlogic.gdx.utils.Array;
    import com.badlogic.gdx.utils.Json;
    import com.badlogic.gdx.utils.viewport.FitViewport;
    import com.esotericsoftware.kryonet.Connection;
    import com.esotericsoftware.kryonet.Listener;
    import io.github.pokemeetup.CreatureCaptureGame;
    import io.github.pokemeetup.context.GameContext;
    import io.github.pokemeetup.multiplayer.client.GameClient;
    import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
    import io.github.pokemeetup.multiplayer.server.config.ServerConfigManager;
    import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
    import io.github.pokemeetup.screens.otherui.ServerManagementDialog;
    import io.github.pokemeetup.utils.GameLogger;
    import io.github.pokemeetup.utils.textures.TextureManager;

    import java.io.IOException;
    import java.util.Base64;
    import java.util.HashSet;
    import java.util.Map;
    import java.util.concurrent.ConcurrentHashMap;

    import static io.github.pokemeetup.multiplayer.server.config.ServerConfigManager.CONFIG_DIR;
    import static io.github.pokemeetup.multiplayer.server.config.ServerConfigManager.CONFIG_FILE;


    public class LoginScreen implements Screen {

        public static final String SERVERS_PREFS = "ServerPrefs";
        public static final float MIN_WIDTH = 300f;
        public static final float MAX_WIDTH = 500f;
        private static final String DEFAULT_SERVER_ICON = "ui/default-server-icon.png";
        private static final int MIN_HEIGHT = 600;
        private static final float CONNECTION_TIMEOUT = 10f;
        private static final int MAX_CONNECTION_ATTEMPTS = 3;
        private static final float VIRTUAL_WIDTH = 800;
        private static final float VIRTUAL_HEIGHT = 600;
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
        TextButton loginButton;
        TextButton registerButton;
        TextButton backButton;
        ProgressBar connectionProgress;
        Label statusLabel;
        private float connectionTimer;
        private boolean isConnecting = false;
        private int connectionAttempts = 0;
        private ScrollPane serverListScrollPane;
        private TextButton editServerButton;
        private TextButton deleteServerButton;

        public LoginScreen(CreatureCaptureGame game) {
            this.game = game;
            this.stage = new Stage(new FitViewport(VIRTUAL_WIDTH, VIRTUAL_HEIGHT));

            TextureAtlas atlas;
            try {
                atlas = new TextureAtlas(Gdx.files.internal("Skins/uiskin.atlas"));
            } catch (Exception e) {
                Gdx.app.error("LoginScreen", "Failed to load uiskin.atlas", e);
                throw new RuntimeException("Could not load uiskin.atlas", e);
            }

            this.skin = new Skin(atlas);
            try {
                this.skin.load(Gdx.files.internal("Skins/uiskin.json"));
            } catch (Exception e) {
                Gdx.app.error("LoginScreen", "Failed to load uiskin.json", e);
                throw new RuntimeException("Could not load uiskin.json", e);
            }
            this.prefs = Gdx.app.getPreferences("LoginPrefs");
            Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pixmap.setColor(Color.WHITE);
            pixmap.fill();
            skin.add("white", new Texture(pixmap));
            pixmap.dispose();
            this.servers = ServerConfigManager.getInstance().getServers();

            createUIComponents();
            setupListeners();
            initializeUI();
            setupInputHandling();
            loadSavedCredentials();
            stage.addListener(new InputListener() {
                @Override
                public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
                    float scrollAmount = amountY * 30f; // Adjust sensitivity
                    serverListScrollPane.setScrollY(serverListScrollPane.getScrollY() + scrollAmount);
                    return true;
                }
            });

            Gdx.input.setInputProcessor(stage);
        }

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
        }

        @Override
        public void resume() {
        }

        @Override
        public void hide() {
        }

        @Override
        public void dispose() {
            stage.dispose();
        }

        private void createUIComponents() {
            mainTable = new Table();
            mainTable.setFillParent(true);


            TextButton.TextButtonStyle buttonStyle = skin.get("default", TextButton.TextButtonStyle.class);
            TextField.TextFieldStyle textFieldStyle = skin.get("default", TextField.TextFieldStyle.class);
            ProgressBar.ProgressBarStyle progressStyle = skin.get("default-horizontal", ProgressBar.ProgressBarStyle.class);loginButton = new TextButton("Login", buttonStyle);
            registerButton = new TextButton("Register", buttonStyle);
            backButton = new TextButton("Back", buttonStyle);

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

            Label titleLabel = new Label("PokéMeetup", skin);
            titleLabel.setFontScale(2f);
            darkPanel.add(titleLabel).padBottom(30).row();

            Table serverHeader = new Table();
            serverHeader.defaults().pad(5);
            Label serverLabel = new Label("Available Servers", skin);
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
            Table topLeftTable = new Table();
            topLeftTable.setFillParent(true);
            topLeftTable.top().left();
            topLeftTable.add(backButton);
            stage.addActor(topLeftTable);
            updateServerList();
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
            ScrollPane.ScrollPaneStyle scrollStyle = new ScrollPane.ScrollPaneStyle();
            scrollStyle.background = new TextureRegionDrawable(TextureManager.ui.findRegion("textfield"));
            ScrollPane scrollPane = new ScrollPane(serverListTable, scrollStyle);
            scrollPane.setFadeScrollBars(false);
            scrollPane.setScrollingDisabled(true, false);
            scrollPane.setForceScroll(false, true);
            scrollPane.setOverscroll(false, false);
            return scrollPane;
        }

        void showServerDialog(ServerConnectionConfig editServer) {
            ServerManagementDialog dialog = new ServerManagementDialog(
                skin,
                editServer,
                config -> {
                    if (editServer != null) {
                        ServerConfigManager.getInstance().removeServer(editServer);
                    }
                    ServerConfigManager.getInstance().addServer(config);
                    this.servers = ServerConfigManager.getInstance().getServers();
                    updateServerList();
                }
            );
            dialog.show(stage);
        }

        /**
         * Processes the received server info, decodes the icon, and triggers a UI update.
         * This method is called from the network thread, so it uses Gdx.app.postRunnable
         * to safely perform graphics operations on the main rendering thread.
         *
         * @param info      The ServerInfo object received from the server.
         * @param forServer The server configuration this info belongs to.
         */
        private void handleServerInfoResponse(NetworkProtocol.ServerInfo info, ServerConnectionConfig forServer) {
            if (info == null) return;

            String serverKey = forServer.getServerIP() + ":" + forServer.getTcpPort();

            if (info.iconBase64 != null && !info.iconBase64.isEmpty()) {
                final byte[] iconBytes;
                try {
                    iconBytes = Base64.getDecoder().decode(info.iconBase64);
                } catch (IllegalArgumentException e) {
                    GameLogger.error("Invalid Base64 string for server icon: " + e.getMessage());
                    return;
                }
                Gdx.app.postRunnable(() -> {
                    try {
                        Pixmap pixmap = new Pixmap(iconBytes, 0, iconBytes.length);
                        Texture iconTexture = new Texture(pixmap);
                        pixmap.dispose(); // Dispose the pixmap after the texture is created
                        if (serverIcons.containsKey(serverKey)) {
                            serverIcons.get(serverKey).dispose();
                        }
                        serverIcons.put(serverKey, iconTexture);

                        GameLogger.info("Successfully created texture for server: " + forServer.getServerName());
                        updateServerList();

                    } catch (Exception e) {
                        GameLogger.error("Failed to create texture from server icon: " + e.getMessage());
                    }
                });
            } else {
                Gdx.app.postRunnable(this::updateServerList);
            }
        }

        private final Map<String, Texture> serverIcons = new ConcurrentHashMap<>();
        private void refreshSelectedServerInfo() {
            if (selectedServer == null) {
                return;
            }
            GameClient client = new GameClient(selectedServer);
            client.getClient().addListener(new Listener() {
                @Override
                public void received(Connection connection, Object object) {
                    if (object instanceof NetworkProtocol.ServerInfoResponse) {
                        handleServerInfoResponse(((NetworkProtocol.ServerInfoResponse) object).serverInfo, selectedServer);
                        client.getClient().removeListener(this);
                        client.dispose(); // Clean up the temporary client
                    }
                }
            });
            try {
                client.getClient().start();
                client.getClient().connect(5000, selectedServer.getServerIP(), selectedServer.getTcpPort(), selectedServer.getUdpPort());

                NetworkProtocol.ServerInfoRequest request = new NetworkProtocol.ServerInfoRequest();
                client.getClient().sendTCP(request);
            } catch (IOException e) {
                showError("Could not connect to server: " + e.getMessage());
                client.dispose();
            }
        }

        void deleteServer(ServerConnectionConfig server) {
            Dialog confirm = new Dialog("Confirm Delete", skin) {
                @Override
                protected void result(Object obj) {
                    if ((Boolean) obj) {
                        ServerConfigManager.getInstance().removeServer(server);
                        servers.removeValue(server, true); // Update local copy
                        if (selectedServer == server) {
                            selectedServer = null;
                        }
                        updateServerList();
                    }
                }
            };
            confirm.text("Are you sure you want to delete '" + server.getServerName() + "'?");
            confirm.button("Yes", true);
            confirm.button("No", false);
            confirm.show(stage);
        }
        private void updateServerList() {
            serverListTable.clear();
            serverListTable.top();

            if (servers.isEmpty()) {
                Label emptyLabel = new Label("No servers found.\nClick 'Add Server' to begin.", skin);
                emptyLabel.setColor(Color.GRAY);
                emptyLabel.setAlignment(Align.center);
                serverListTable.add(emptyLabel).pad(20).expand().fill();
            } else {
                for (ServerConnectionConfig server : servers) {
                    Table serverEntry = createServerEntry(server);
                    serverListTable.add(serverEntry).expandX().fillX().padBottom(2).row();
                }
            }

            selectedServer = servers.isEmpty() ? null : servers.first();

            editServerButton.setDisabled(selectedServer == null);
            deleteServerButton.setDisabled(selectedServer == null);
            loginButton.setDisabled(selectedServer == null);

            if (selectedServer != null && !serverListTable.getChildren().isEmpty()) {
                Actor firstEntry = serverListTable.getChildren().first();
                if (firstEntry instanceof Table) {
                    updateServerSelection((Table) firstEntry);
                }
            }
        }


        private Table createServerEntry(final ServerConnectionConfig server) {
            Table entry = new Table();
            entry.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("textfield")));
            entry.pad(10);


            Table iconContainer = new Table();
            Image iconImage = new Image(); // Create the Image widget
            iconImage.setSize(32, 32);
            String serverKey = server.getServerIP() + ":" + server.getTcpPort();
            if (serverIcons.containsKey(serverKey)) {
                iconImage.setDrawable(new TextureRegionDrawable(new TextureRegion(serverIcons.get(serverKey))));
            } else {
                addDefaultIcon(iconContainer); // Your existing method to show a placeholder
            }

            iconContainer.add(iconImage).size(32);

            Table infoPanel = new Table();
            infoPanel.defaults().expandX().fillX().space(5);
            infoPanel.left();

            Label nameLabel = new Label(server.getServerName(), skin); // FIX: Use default style
            nameLabel.setEllipsis(true);
            infoPanel.add(nameLabel).left().expandX().fillX().height(24).row();

            Label motdLabel = new Label(server.getMotd() != null ? server.getMotd() : "Welcome!", skin);
            motdLabel.setEllipsis(true);
            motdLabel.setColor(0.8f, 0.8f, 0.8f, 1f);
            infoPanel.add(motdLabel).left().expandX().fillX().height(24).row();

            Label addressLabel = new Label(server.getServerIP() + ":" + server.getTcpPort(), skin);
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
                    selectAndRefreshServer(server, entry);
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

        /**
         * Handles selecting a server, updating the UI, and triggering a refresh of its data.
         * @param server The server that was selected.
         * @param selectedEntry The UI table for the selected server.
         */
        private void selectAndRefreshServer(ServerConnectionConfig server, Table selectedEntry) {
            if (server == null) return;
            selectedServer = server;
            updateServerSelection(selectedEntry);
            refreshSelectedServerInfo();
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
        void attemptLogin() {
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
            final GameClient client = new GameClient(selectedServer);
            GameContext.get().setGameClient(client);
            isConnecting = true;  // mark that we are mid-connection
            connectionTimer = 0;  // reset the local timer
            client.connectIfNeeded(
                () -> {
                    Gdx.app.postRunnable(() -> {
                        statusLabel.setText("Connected. Logging in...");
                        client.sendLoginRequest(
                            username,
                            password,
                            (NetworkProtocol.LoginResponse lr) -> {
                                isConnecting = false;
                                connectionProgress.setVisible(false);

                                if (lr.success) {
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
                                isConnecting = false;
                                connectionProgress.setVisible(false);
                                setUIEnabled(true);
                                showError("Login request error: " + error);
                            }
                        );
                    });
                },
                (String errorMsg) -> {
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
            } catch (Exception e) {
                GameLogger.error("Failed to transition to game: " + e.getMessage());
                showError("Failed to start game: " + e.getMessage());
            }
        }

        void attemptRegistration() {
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
            final GameClient client = new GameClient(selectedServer);
            GameContext.get().setGameClient(client);
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

        private void loadServers() {
            try {
                FileHandle file = Gdx.files.local(CONFIG_DIR + "/" + CONFIG_FILE);
                if (file.exists() && file.length() > 0) {
                    Json json = new Json();
                    String fileContent = file.readString();
                    GameLogger.info("Loading servers from: " + file.path());

                    @SuppressWarnings("unchecked")
                    Array<ServerConnectionConfig> loadedServers = json.fromJson(Array.class,
                        ServerConnectionConfig.class, fileContent);

                    if (loadedServers != null && loadedServers.size > 0) {
                        HashSet<ServerConnectionConfig> uniqueSet = new HashSet<>();
                        for (ServerConnectionConfig server : loadedServers) {
                            if (!uniqueSet.add(server)) {
                                GameLogger.info("Removed duplicate server on load: " + server.getServerName());
                            }
                        }
                        boolean wasCleaned = uniqueSet.size() < loadedServers.size;
                        servers.clear();
                        for(ServerConnectionConfig uniqueServer : uniqueSet) {
                            servers.add(uniqueServer);
                        }

                        GameLogger.info("Loaded " + servers.size + " unique servers.");
                        if(wasCleaned) {
                            saveServers();
                        }
                    }
                }
            } catch (Exception e) {
                GameLogger.info("Error loading servers: " + e.getMessage());
                e.printStackTrace();
            }
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

        private static class ServerEntry {
            public String name;
            public String ip;
            public int tcpPort;
            public int udpPort;
            public String motd;
            public int maxPlayers;
            public String iconPath;

            public ServerEntry() {}

            public ServerEntry(String name, String ip, int tcpPort, int udpPort,
                               String motd, int maxPlayers, String iconPath) {
                this.name = name;
                this.ip = ip;
                this.tcpPort = tcpPort;
                this.udpPort = udpPort;
                this.motd = motd;
                this.maxPlayers = maxPlayers;
                this.iconPath = iconPath;
            }
        }
    }
