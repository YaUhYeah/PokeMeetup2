package io.github.pokemeetup.screens;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import io.github.pokemeetup.screens.otherui.ServerManagementDialog;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;

public class AndroidLoginScreen extends LoginScreen {

    // Layout constants
    private static final float MIN_BUTTON_HEIGHT = 48f;
    private static final float OPTIMAL_BUTTON_HEIGHT = 56f;
    private static final float PADDING_SMALL = 12f;
    private static final float PADDING_MEDIUM = 16f;
    private static final float PADDING_LARGE = 24f;
    static final float INPUT_FIELD_HEIGHT = 56f;
    private static final float SERVER_ICON_SIZE = 40f;
    private static final float SERVER_ENTRY_MIN_HEIGHT = 64f;

    // Dynamic layout properties
    private float currentFontScale;
    private float screenDensity;
    private boolean isSmallScreen;
    private boolean isTablet;
    private boolean isLandscape;

    private Table mobileLayout;
    private ScrollPane serverScrollPane;
    private Container<Table> dynamicContainer;
    private Label titleLabel;

    public AndroidLoginScreen(CreatureCaptureGame game) {
        super(game);
        analyzeScreenProperties();
        setupAdaptiveUI();
    }

    private void analyzeScreenProperties() {
        float width = Gdx.graphics.getWidth();
        float height = Gdx.graphics.getHeight();
        screenDensity = Gdx.graphics.getDensity();

        // Calculate screen size in DP (density-independent pixels)
        float widthDp = width / screenDensity;
        float heightDp = height / screenDensity;

        // Determine device type and orientation
        isLandscape = width > height;
        isSmallScreen = Math.min(widthDp, heightDp) < 360;
        isTablet = Math.min(widthDp, heightDp) >= 600;

        // Calculate appropriate font scale
        float baseScale = Math.min(widthDp / 360f, heightDp / 640f);
        currentFontScale = MathUtils.clamp(baseScale * 1.2f, 0.8f, 1.6f);

        GameLogger.info(String.format("Screen: %.0fx%.0f (%.0fx%.0f dp), Tablet: %b, Small: %b, Landscape: %b",
            width, height, widthDp, heightDp, isTablet, isSmallScreen, isLandscape));
    }

    private void setupAdaptiveUI() {
        stage.clear();

        // Background setup
        setupBackground();

        // Main layout container
        mobileLayout = new Table();
        mobileLayout.setFillParent(true);

        // Choose layout based on screen properties
        if (isLandscape && !isSmallScreen) {
            setupLandscapeLayout();
        } else {
            setupPortraitLayout();
        }

        stage.addActor(mobileLayout);

        // Back button overlay
        createBackButton();
    }

    private void setupBackground() {
        // Gradient background
        Image darkBg = new Image(skin.newDrawable("white", new Color(0.05f, 0.05f, 0.08f, 1f)));
        darkBg.setFillParent(true);
        stage.addActor(darkBg);

        // Optional ethereal overlay
        if (TextureManager.ui != null && TextureManager.ui.findRegion("ethereal") != null) {
            Image ethereal = new Image(TextureManager.ui.findRegion("ethereal"));
            ethereal.setFillParent(true);
            ethereal.setScaling(com.badlogic.gdx.utils.Scaling.fill);
            ethereal.setColor(1, 1, 1, 0.08f);
            stage.addActor(ethereal);
        }
    }

    private void setupPortraitLayout() {
        float padding = isSmallScreen ? PADDING_SMALL : PADDING_MEDIUM;
        mobileLayout.pad(padding);

        // Dynamic container that adjusts based on content
        dynamicContainer = new Container<>();
        Table content = new Table();

        // Title section
        titleLabel = new Label("PokéMeetup", skin);
        float titleScale = currentFontScale * (isSmallScreen ? 1.8f : 2.2f);
        titleLabel.setFontScale(titleScale);
        titleLabel.setAlignment(Align.center);

        content.add(titleLabel).padTop(padding * 2).padBottom(padding * 2).row();

        // Main content area with scroll if needed
        Table mainContent = new Table();

        if (isSmallScreen) {
            // Compact layout for small screens
            setupCompactPortraitContent(mainContent);
        } else {
            // Standard portrait layout
            setupStandardPortraitContent(mainContent);
        }

        ScrollPane contentScroll = new ScrollPane(mainContent, skin);
        contentScroll.setScrollingDisabled(true, false);
        contentScroll.setFadeScrollBars(false);
        contentScroll.setOverscroll(false, true);

        content.add(contentScroll).expand().fill().row();

        // Status section at bottom
        Table statusSection = createStatusSection();
        content.add(statusSection).fillX().padTop(padding);

        dynamicContainer.setActor(content);
        dynamicContainer.fill();
        mobileLayout.add(dynamicContainer).expand().fill();
    }

    private void setupLandscapeLayout() {
        float padding = isTablet ? PADDING_LARGE : PADDING_MEDIUM;
        mobileLayout.pad(padding);

        // Title at top
        titleLabel = new Label("PokéMeetup", skin);
        titleLabel.setFontScale(currentFontScale * 2.5f);
        titleLabel.setAlignment(Align.center);
        mobileLayout.add(titleLabel).colspan(2).padBottom(padding * 2).row();

        // Two-column layout
        Table leftColumn = new Table();
        Table rightColumn = new Table();

        // Left: Server selection
        setupServerSection(leftColumn, true);

        // Right: Login form
        setupLoginSection(rightColumn, true);

        float columnRatio = isTablet ? 0.4f : 0.45f;
        mobileLayout.add(leftColumn).width(Value.percentWidth(columnRatio, mobileLayout))
            .expandY().fillY().padRight(padding);
        mobileLayout.add(rightColumn).width(Value.percentWidth(1f - columnRatio, mobileLayout))
            .expandY().fillY();
        mobileLayout.row();

        // Status bar at bottom
        Table statusSection = createStatusSection();
        mobileLayout.add(statusSection).colspan(2).fillX().padTop(padding);
    }

    private void setupCompactPortraitContent(Table content) {
        float padding = PADDING_SMALL;

        // Tabbed interface for compact screens
        Table tabs = new Table();
        TextButton serverTab = new TextButton("Servers", skin);
        TextButton loginTab = new TextButton("Login", skin);

        serverTab.getLabel().setFontScale(currentFontScale);
        loginTab.getLabel().setFontScale(currentFontScale);

        ButtonGroup<TextButton> tabGroup = new ButtonGroup<>(serverTab, loginTab);
        tabGroup.setMaxCheckCount(1);
        tabGroup.setMinCheckCount(1);

        tabs.add(serverTab).expandX().fillX().height(48f);
        tabs.add(loginTab).expandX().fillX().height(48f);

        content.add(tabs).fillX().padBottom(padding).row();

        // Content that switches based on tab
        Stack contentStack = new Stack();

        Table serverContent = new Table();
        setupServerSection(serverContent, false);

        Table loginContent = new Table();
        setupLoginSection(loginContent, false);

        contentStack.add(serverContent);
        contentStack.add(loginContent);

        // Tab switching logic
        serverTab.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                serverContent.setVisible(true);
                loginContent.setVisible(false);
            }
        });

        loginTab.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                serverContent.setVisible(false);
                loginContent.setVisible(true);
            }
        });

        serverTab.setChecked(true);
        loginContent.setVisible(false);

        content.add(contentStack).expand().fill();
    }

    private void setupStandardPortraitContent(Table content) {
        float padding = PADDING_MEDIUM;

        // Server section
        Table serverSection = new Table();
        setupServerSection(serverSection, false);
        content.add(serverSection).fillX().padBottom(padding * 2).row();

        // Divider
        Image divider = new Image(skin.newDrawable("white", new Color(0.2f, 0.2f, 0.2f, 0.5f)));
        content.add(divider).fillX().height(1f).padBottom(padding * 2).row();

        // Login section
        Table loginSection = new Table();
        setupLoginSection(loginSection, false);
        content.add(loginSection).fillX();
    }

    private void setupServerSection(Table container, boolean expanded) {
        float padding = isSmallScreen ? PADDING_SMALL : PADDING_MEDIUM;

        // Header
        Table header = new Table();
        Label serverLabel = new Label("Select Server", skin);
        serverLabel.setFontScale(currentFontScale * 1.2f);

        TextButton addBtn = new TextButton("+", skin);
        addBtn.getLabel().setFontScale(currentFontScale * 1.5f);
        addBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showServerDialog(null);
            }
        });

        header.add(serverLabel).expandX().left();
        header.add(addBtn).size(40f * currentFontScale);

        container.add(header).fillX().padBottom(padding).row();

        // Server list
        Table serverListContent = new Table();
        serverListContent.top();

        if (servers == null || servers.size == 0) {
            Label emptyLabel = new Label("No servers available\nTap + to add", skin);
            emptyLabel.setFontScale(currentFontScale);
            emptyLabel.setAlignment(Align.center);
            emptyLabel.setColor(0.5f, 0.5f, 0.5f, 1f);
            serverListContent.add(emptyLabel).expand().center().pad(padding * 3);
        } else {
            for (ServerConnectionConfig server : servers) {
                Table entry = createAdaptiveServerEntry(server);
                serverListContent.add(entry).fillX().expandX().padBottom(padding / 2).row();
            }
        }

        // Scroll pane with appropriate height
        serverScrollPane = new ScrollPane(serverListContent, skin);
        serverScrollPane.setFadeScrollBars(true);
        serverScrollPane.setScrollingDisabled(true, false);

        float scrollHeight = expanded ? 0 : (isSmallScreen ? 120f : 180f);
        if (scrollHeight > 0) {
            container.add(serverScrollPane).fillX().height(scrollHeight);
        } else {
            container.add(serverScrollPane).expand().fill();
        }
    }

    private Table createAdaptiveServerEntry(ServerConnectionConfig server) {
        Table entry = new Table();
        entry.setTouchable(Touchable.enabled);

        float padding = isSmallScreen ? PADDING_SMALL : PADDING_MEDIUM;

        // Dynamic background
        Color normalColor = new Color(0.12f, 0.12f, 0.15f, 0.8f);
        Color selectedColor = new Color(0.15f, 0.3f, 0.6f, 0.9f);

        Drawable normalBg = skin.newDrawable("default-round", normalColor);
        Drawable selectedBg = skin.newDrawable("default-round", selectedColor);

        entry.setBackground(selectedServer == server ? selectedBg : normalBg);
        entry.pad(padding);

        // Icon
        Image icon = new Image(skin.newDrawable("white", new Color(0.3f, 0.4f, 0.6f, 1f)));
        float iconSize = SERVER_ICON_SIZE * currentFontScale;

        // Server info
        Table infoTable = new Table();
        Label nameLabel = new Label(server.getServerName(), skin);
        nameLabel.setFontScale(currentFontScale);
        nameLabel.setEllipsis(true);

        Label addressLabel = new Label(server.getServerIP() + ":" + server.getTcpPort(), skin);
        addressLabel.setFontScale(currentFontScale * 0.8f);
        addressLabel.setColor(0.6f, 0.6f, 0.6f, 1f);

        infoTable.add(nameLabel).left().expandX().fillX().row();
        infoTable.add(addressLabel).left().expandX().fillX();

        entry.add(icon).size(iconSize).padRight(padding);
        entry.add(infoTable).expand().fill();

        // Actions for selected
        if (selectedServer == server && !isSmallScreen) {
            Table actions = new Table();
            TextButton editBtn = new TextButton("✎", skin);
            TextButton deleteBtn = new TextButton("✕", skin);

            float btnScale = currentFontScale * 1.2f;
            editBtn.getLabel().setFontScale(btnScale);
            deleteBtn.getLabel().setFontScale(btnScale);

            editBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    showServerDialog(server);
                }
            });

            deleteBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    deleteServer(server);
                }
            });

            float btnSize = 36f * currentFontScale;
            actions.add(editBtn).size(btnSize).padRight(4f);
            actions.add(deleteBtn).size(btnSize);
            entry.add(actions).padLeft(padding);
        }

        // Selection
        entry.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                selectedServer = server;
                refreshServerList();
            }
        });

        return entry;
    }

    private void setupLoginSection(Table container, boolean expanded) {
        float padding = isSmallScreen ? PADDING_SMALL : PADDING_MEDIUM;

        // Login header
        Label loginLabel = new Label("Login", skin);
        loginLabel.setFontScale(currentFontScale * 1.4f);
        loginLabel.setAlignment(Align.center);
        container.add(loginLabel).padBottom(padding * 1.5f).row();

        // Username
        Label usernameLabel = new Label("Username", skin);
        usernameLabel.setFontScale(currentFontScale);
        container.add(usernameLabel).left().padBottom(padding / 2).row();

        usernameField = new TextField("", skin);
        usernameField.setMessageText("Enter username");
        container.add(usernameField).fillX().height(INPUT_FIELD_HEIGHT * currentFontScale)
            .padBottom(padding * 1.5f).row();

        // Password
        Label passwordLabel = new Label("Password", skin);
        passwordLabel.setFontScale(currentFontScale);
        container.add(passwordLabel).left().padBottom(padding / 2).row();

        passwordField = new TextField("", skin);
        passwordField.setMessageText("Enter password");
        passwordField.setPasswordMode(true);
        passwordField.setPasswordCharacter('•');
        container.add(passwordField).fillX().height(INPUT_FIELD_HEIGHT * currentFontScale)
            .padBottom(padding).row();

        // Remember me
        rememberMeBox = new CheckBox(" Remember Me", skin);
        rememberMeBox.getLabel().setFontScale(currentFontScale);
        container.add(rememberMeBox).left().padBottom(padding * 2).row();

        // Buttons
        Table buttonTable = new Table();

        loginButton = new TextButton("Login", skin);
        registerButton = new TextButton("Register", skin);

        float btnScale = currentFontScale * 1.1f;
        loginButton.getLabel().setFontScale(btnScale);
        registerButton.getLabel().setFontScale(btnScale);

        float btnHeight = (isSmallScreen ? MIN_BUTTON_HEIGHT : OPTIMAL_BUTTON_HEIGHT) * currentFontScale;
        loginButton.pad(padding, padding * 2, padding, padding * 2);
        registerButton.pad(padding, padding * 2, padding, padding * 2);

        loginButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                attemptLogin();
            }
        });

        registerButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                attemptRegistration();
            }
        });

        buttonTable.add(loginButton).fillX().expandX().height(btnHeight).padRight(padding);
        buttonTable.add(registerButton).fillX().expandX().height(btnHeight);

        container.add(buttonTable).fillX();
    }

    private Table createStatusSection() {
        Table statusTable = new Table();
        float padding = isSmallScreen ? PADDING_SMALL : PADDING_MEDIUM;

        statusLabel = new Label("", skin);
        statusLabel.setFontScale(currentFontScale * 0.9f);
        statusLabel.setWrap(true);
        statusLabel.setAlignment(Align.center);

        connectionProgress = new ProgressBar(0, 1, 0.01f, false, skin);
        connectionProgress.setVisible(false);

        feedbackLabel = new Label("", skin);
        feedbackLabel.setFontScale(currentFontScale * 0.9f);
        feedbackLabel.setWrap(true);
        feedbackLabel.setAlignment(Align.center);

        statusTable.add(statusLabel).fillX().row();
        statusTable.add(connectionProgress).fillX().height(4f).padTop(4f).padBottom(4f).row();
        statusTable.add(feedbackLabel).fillX();

        return statusTable;
    }

    private void createBackButton() {
        Table backContainer = new Table();
        backContainer.setFillParent(true);
        backContainer.top().left().pad(PADDING_MEDIUM);

        backButton = new TextButton("◄", skin);
        backButton.getLabel().setFontScale(currentFontScale * 1.8f);
        backButton.pad(12f);
        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.setScreen(new ModeSelectionScreen(game));
            }
        });

        float btnSize = 48f * currentFontScale;
        backContainer.add(backButton).size(btnSize);
        stage.addActor(backContainer);
    }

    private void refreshServerList() {
        if (serverScrollPane != null && serverScrollPane.getWidget() instanceof Table) {
            Table container = (Table) serverScrollPane.getWidget();
            container.clear();

            float padding = isSmallScreen ? PADDING_SMALL : PADDING_MEDIUM;

            if (servers != null && servers.size > 0) {
                for (ServerConnectionConfig server : servers) {
                    Table entry = createAdaptiveServerEntry(server);
                    container.add(entry).fillX().expandX().padBottom(padding / 2).row();
                }
            } else {
                Label emptyLabel = new Label("No servers available\nTap + to add", skin);
                emptyLabel.setFontScale(currentFontScale);
                emptyLabel.setAlignment(Align.center);
                emptyLabel.setColor(0.5f, 0.5f, 0.5f, 1f);
                container.add(emptyLabel).expand().center().pad(padding * 3);
            }
        }
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);

        // Re-analyze screen properties
        analyzeScreenProperties();

        // Rebuild UI if orientation changed
        boolean newLandscape = width > height;
        if (newLandscape != isLandscape) {
            setupAdaptiveUI();
        } else {
            // Just update font scales
            updateAllFontScales(stage.getRoot());
        }
    }

    private void updateAllFontScales(Actor actor) {
        if (actor instanceof Label) {
            Label label = (Label) actor;
            if (label == titleLabel) {
                float titleScale = currentFontScale * (isSmallScreen ? 1.8f : 2.2f);
                label.setFontScale(titleScale);
            } else {
                label.setFontScale(currentFontScale);
            }
        } else if (actor instanceof TextButton) {
            TextButton button = (TextButton) actor;
            button.getLabel().setFontScale(currentFontScale * 1.1f);
        } else if (actor instanceof CheckBox) {
            ((CheckBox) actor).getLabel().setFontScale(currentFontScale);
        } else if (actor instanceof Group) {
            for (Actor child : ((Group) actor).getChildren()) {
                updateAllFontScales(child);
            }
        }
    }

    @Override
    public void show() {
        super.show();

        // Smooth fade in
        stage.getRoot().setColor(1, 1, 1, 0);
        stage.getRoot().addAction(Actions.fadeIn(0.3f));

        // Focus username field
        stage.setKeyboardFocus(usernameField);
    }
}

