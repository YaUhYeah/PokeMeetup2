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

    // Android-specific constants
    private static final float MIN_BUTTON_HEIGHT = 64f; // Minimum 64dp for touch targets
    private static final float MOBILE_PADDING = 32f;
    private static final float MOBILE_FONT_SCALE = 1.5f;
    static final float INPUT_FIELD_HEIGHT = 80f;
    private static final float MOBILE_SPACING = 24f;

    private Table mobileLayout;
    private ScrollPane serverScrollPane;
    private boolean isTablet;

    public AndroidLoginScreen(CreatureCaptureGame game) {
        super(game);
        detectDeviceType();
        setupMobileUI();
    }

    private void detectDeviceType() {
        // Simple tablet detection based on screen size
        float screenInches = calculateScreenSizeInches();
        isTablet = screenInches >= 7.0f;
        GameLogger.info("Device detected as: " + (isTablet ? "Tablet" : "Phone") + " (" + screenInches + " inches)");
    }

    private float calculateScreenSizeInches() {
        float widthPixels = Gdx.graphics.getWidth();
        float heightPixels = Gdx.graphics.getHeight();
        float widthDpi = Gdx.graphics.getPpiX();
        float heightDpi = Gdx.graphics.getPpiY();
        float widthInches = widthPixels / widthDpi;
        float heightInches = heightPixels / heightDpi;
        return (float) Math.sqrt(widthInches * widthInches + heightInches * heightInches);
    }

    private void setupMobileUI() {
        stage.clear();

        mobileLayout = new Table();
        mobileLayout.setFillParent(true);
        mobileLayout.pad(MOBILE_PADDING);

        // Background
        if (TextureManager.ui != null && TextureManager.ui.findRegion("ethereal") != null) {
            Image background = new Image(TextureManager.ui.findRegion("ethereal"));
            background.setFillParent(true);
            background.setScaling(com.badlogic.gdx.utils.Scaling.fill);
            stage.addActor(background);
        }

        // Main content container with dark background
        Table contentContainer = new Table();
        contentContainer.setBackground(new TextureRegionDrawable(
            createRoundedBackground(new Color(0.1f, 0.1f, 0.1f, 0.9f))
        ));
        contentContainer.pad(MOBILE_PADDING);

        // Title
        Label titleLabel = new Label("PokéMeetup", skin, "title");
        titleLabel.setFontScale(isTablet ? 2.5f : 2.0f);
        titleLabel.setAlignment(Align.center);
        contentContainer.add(titleLabel).padBottom(MOBILE_SPACING * 2).row();

        // Server selection section
        createMobileServerSection(contentContainer);

        // Login form section
        createMobileLoginForm(contentContainer);

        // Status section
        createMobileStatusSection(contentContainer);

        // Add content to main layout
        if (isTablet) {
            mobileLayout.add(contentContainer).width(Value.percentWidth(0.7f, mobileLayout));
        } else {
            mobileLayout.add(contentContainer).expand().fill();
        }

        stage.addActor(mobileLayout);

        // Back button (top-left corner)
        createMobileBackButton();
    }

    private void createMobileServerSection(Table container) {
        // Server header
        Table serverHeader = new Table();
        Label serverLabel = new Label("Select Server", skin);
        serverLabel.setFontScale(MOBILE_FONT_SCALE);

        TextButton addServerBtn = createMobileButton("Add", skin);
        addServerBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showServerDialog(null);
            }
        });

        serverHeader.add(serverLabel).expandX().left().padRight(MOBILE_SPACING);
        serverHeader.add(addServerBtn).right();

        container.add(serverHeader).fillX().padBottom(MOBILE_SPACING).row();

        // Server list
        Table serverListContent = new Table();
        serverListContent.top();

        for (ServerConnectionConfig server : servers) {
            serverListContent.add(createMobileServerEntry(server))
                .fillX().expandX().padBottom(MOBILE_SPACING).row();
        }

        // Scrollable server list
        ScrollPane.ScrollPaneStyle scrollStyle = new ScrollPane.ScrollPaneStyle();
        scrollStyle.background = new TextureRegionDrawable(
            createRoundedBackground(new Color(0.2f, 0.2f, 0.2f, 0.8f))
        );

        serverScrollPane = new ScrollPane(serverListContent, scrollStyle);
        serverScrollPane.setFadeScrollBars(true);
        serverScrollPane.setScrollingDisabled(true, false);
        serverScrollPane.setOverscroll(false, true);

        float scrollHeight = isTablet ? 300f : 200f;
        container.add(serverScrollPane).fillX().height(scrollHeight).padBottom(MOBILE_SPACING * 2).row();
    }

    private Table createMobileServerEntry(ServerConnectionConfig server) {
        Table entry = new Table();
        entry.setTouchable(Touchable.enabled);
        entry.pad(16f);

        // Set background
        TextureRegionDrawable normalBg = new TextureRegionDrawable(
            createRoundedBackground(new Color(0.3f, 0.3f, 0.3f, 0.8f))
        );
        TextureRegionDrawable selectedBg = new TextureRegionDrawable(
            createRoundedBackground(new Color(0.3f, 0.6f, 1f, 0.8f))
        );

        entry.setBackground(selectedServer == server ? selectedBg : normalBg);

        // Server info
        Table infoTable = new Table();
        Label nameLabel = new Label(server.getServerName(), skin);
        nameLabel.setFontScale(MOBILE_FONT_SCALE);

        Label addressLabel = new Label(server.getServerIP() + ":" + server.getTcpPort(), skin);
        addressLabel.setFontScale(MOBILE_FONT_SCALE * 0.8f);
        addressLabel.setColor(0.7f, 0.7f, 0.7f, 1f);

        infoTable.add(nameLabel).left().row();
        infoTable.add(addressLabel).left();

        entry.add(infoTable).expand().fill().padRight(16f);

        // Action buttons for selected server
        if (selectedServer == server) {
            Table actionButtons = new Table();
            TextButton editBtn = createSmallMobileButton("Edit", skin);
            TextButton deleteBtn = createSmallMobileButton("Delete", skin);

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

            actionButtons.add(editBtn).padRight(8f);
            actionButtons.add(deleteBtn);
            entry.add(actionButtons);
        }

        // Touch listener
        entry.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                selectedServer = server;
                refreshMobileServerList();
            }
        });

        return entry;
    }

    private void createMobileLoginForm(Table container) {
        // Username field
        Table usernameRow = new Table();
        Label usernameLabel = new Label("Username", skin);
        usernameLabel.setFontScale(MOBILE_FONT_SCALE);
        usernameRow.add(usernameLabel).left().row();

        TextField.TextFieldStyle textFieldStyle = new TextField.TextFieldStyle(skin.get(TextField.TextFieldStyle.class));
        textFieldStyle.font.getData().setScale(MOBILE_FONT_SCALE);

        usernameField = new TextField("", textFieldStyle);
        usernameField.setMessageText("Enter username");
        usernameRow.add(usernameField).fillX().height(INPUT_FIELD_HEIGHT).padTop(8f);

        container.add(usernameRow).fillX().padBottom(MOBILE_SPACING).row();

        // Password field
        Table passwordRow = new Table();
        Label passwordLabel = new Label("Password", skin);
        passwordLabel.setFontScale(MOBILE_FONT_SCALE);
        passwordRow.add(passwordLabel).left().row();

        passwordField = new TextField("", textFieldStyle);
        passwordField.setMessageText("Enter password");
        passwordField.setPasswordMode(true);
        passwordField.setPasswordCharacter('•');
        passwordRow.add(passwordField).fillX().height(INPUT_FIELD_HEIGHT).padTop(8f);

        container.add(passwordRow).fillX().padBottom(MOBILE_SPACING).row();

        // Remember me checkbox
        CheckBox.CheckBoxStyle checkBoxStyle = new CheckBox.CheckBoxStyle(skin.get(CheckBox.CheckBoxStyle.class));
        checkBoxStyle.font.getData().setScale(MOBILE_FONT_SCALE);

        rememberMeBox = new CheckBox(" Remember Me", checkBoxStyle);
        container.add(rememberMeBox).left().padBottom(MOBILE_SPACING * 2).row();

        // Action buttons
        Table buttonRow = new Table();
        loginButton = createMobileButton("Login", skin);
        registerButton = createMobileButton("Register", skin);

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

        float buttonSpacing = isTablet ? 20f : 12f;
        buttonRow.add(loginButton).uniformX().fill().padRight(buttonSpacing);
        buttonRow.add(registerButton).uniformX().fill();

        container.add(buttonRow).fillX().row();
    }

    private void createMobileStatusSection(Table container) {
        // Status label
        statusLabel = new Label("", skin);
        statusLabel.setFontScale(MOBILE_FONT_SCALE * 0.9f);
        statusLabel.setWrap(true);
        statusLabel.setAlignment(Align.center);
        container.add(statusLabel).fillX().padTop(MOBILE_SPACING).row();

        // Progress bar
        ProgressBar.ProgressBarStyle progressStyle = new ProgressBar.ProgressBarStyle();
        progressStyle.background = skin.newDrawable("white", Color.DARK_GRAY);
        progressStyle.knob = skin.newDrawable("white", Color.GREEN);
        progressStyle.knobBefore = skin.newDrawable("white", Color.GREEN);

        connectionProgress = new ProgressBar(0, 1, 0.01f, false, progressStyle);
        connectionProgress.setVisible(false);
        container.add(connectionProgress).fillX().height(8f).padTop(MOBILE_SPACING).row();

        // Feedback label
        feedbackLabel = new Label("", skin);
        feedbackLabel.setFontScale(MOBILE_FONT_SCALE * 0.9f);
        feedbackLabel.setWrap(true);
        feedbackLabel.setAlignment(Align.center);
        container.add(feedbackLabel).fillX().padTop(MOBILE_SPACING);
    }

    private void createMobileBackButton() {
        Table backButtonContainer = new Table();
        backButtonContainer.setFillParent(true);
        backButtonContainer.top().left().pad(MOBILE_PADDING);

        backButton = createMobileButton("← Back", skin);
        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.setScreen(new ModeSelectionScreen(game));
            }
        });

        backButtonContainer.add(backButton);
        stage.addActor(backButtonContainer);
    }

    private TextButton createMobileButton(String text, Skin skin) {
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle(skin.get(TextButton.TextButtonStyle.class));
        style.font.getData().setScale(MOBILE_FONT_SCALE);

        TextButton button = new TextButton(text, style);
        button.pad(16f, 32f, 16f, 32f);
        button.getLabel().setFontScale(MOBILE_FONT_SCALE);

        return button;
    }

    private TextButton createSmallMobileButton(String text, Skin skin) {
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle(skin.get(TextButton.TextButtonStyle.class));
        style.font.getData().setScale(MOBILE_FONT_SCALE * 0.8f);

        TextButton button = new TextButton(text, style);
        button.pad(12f, 20f, 12f, 20f);

        return button;
    }

    private TextureRegion createRoundedBackground(Color color) {
        // Create a simple colored background (in a real implementation, you'd create a rounded rectangle)
        return TextureManager.ui.findRegion("default-pane");
    }

    private void refreshMobileServerList() {
        Table container = (Table) serverScrollPane.getWidget();
        container.clear();

        for (ServerConnectionConfig server : servers) {
            container.add(createMobileServerEntry(server))
                .fillX().expandX().padBottom(MOBILE_SPACING).row();
        }
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);

        // Adjust font scale based on screen size
        float scaleFactor = Math.min(width / 800f, height / 600f);
        float adjustedFontScale = MathUtils.clamp(MOBILE_FONT_SCALE * scaleFactor, 1.0f, 2.0f);

        // Update all labels and buttons with new font scale
        updateFontScales(stage.getRoot(), adjustedFontScale);
    }

    private void updateFontScales(Actor actor, float scale) {
        if (actor instanceof Label) {
            ((Label) actor).setFontScale(scale);
        } else if (actor instanceof TextButton) {
            ((TextButton) actor).getLabel().setFontScale(scale);
        } else if (actor instanceof TextField) {
            ((TextField) actor).getStyle().font.getData().setScale(scale);
        } else if (actor instanceof Group) {
            for (Actor child : ((Group) actor).getChildren()) {
                updateFontScales(child, scale);
            }
        }
    }

    @Override
    public void show() {
        super.show();

        // Add slide-in animation for mobile
        mobileLayout.setPosition(-stage.getWidth(), 0);
        mobileLayout.addAction(Actions.moveTo(0, 0, 0.3f));

        // Focus on username field
        stage.setKeyboardFocus(usernameField);
    }
}
