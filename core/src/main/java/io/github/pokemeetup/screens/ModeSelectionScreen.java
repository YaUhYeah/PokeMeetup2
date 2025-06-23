package io.github.pokemeetup.screens;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.*;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.utils.textures.TextureManager;

public class ModeSelectionScreen implements Screen {
    private final CreatureCaptureGame game;
    private final Stage stage;
    private final Skin skin;
    private final Timer timer;
    private BitmapFont font;
    private final boolean isAndroid;

    public ModeSelectionScreen(CreatureCaptureGame game) {
        this.game = game;
        this.stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);

        this.skin = new Skin();
        this.timer = new Timer();

        // Detect if running on Android
        this.isAndroid = Gdx.app.getType() == Application.ApplicationType.Android ||
            Gdx.app.getType() == Application.ApplicationType.iOS;

        try {
            this.font = initializeSkin();
            Gdx.app.log("SkinSetup", "Successfully initialized the skin.");
        } catch (Exception e) {
            showError("Failed to initialize UI: " + e.getMessage());
            Gdx.app.error("SkinSetup", "Failed to initialize UI", e);
            return;
        }
        AudioManager.getInstance().setMusicEnabled(true);

        if (isAndroid) {
            createMobileUI();
        } else {
            createUI();
        }
    }

    private BitmapFont initializeSkin() {
        // Add BitmapFont with larger font size
        BitmapFont font = new BitmapFont(Gdx.files.internal("Skins/default.fnt"));
        skin.add("default", font);

        // Define Colors
        skin.add("white", Color.WHITE);
        skin.add("black", Color.BLACK);
        skin.add("gray", Color.GRAY);

        // Create drawables
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        skin.add("white", new Texture(pixmap));

        // Clean up the pixmap
        pixmap.dispose();

        // Create styles
        TextButton.TextButtonStyle textButtonStyle = new TextButton.TextButtonStyle();
        textButtonStyle.up = skin.newDrawable("white", Color.DARK_GRAY);
        textButtonStyle.down = skin.newDrawable("white", Color.LIGHT_GRAY);
        textButtonStyle.over = skin.newDrawable("white", Color.GRAY);
        textButtonStyle.font = skin.getFont("default");
        textButtonStyle.fontColor = Color.WHITE;
        skin.add("default", textButtonStyle);

        Label.LabelStyle labelStyle = new Label.LabelStyle();
        labelStyle.font = skin.getFont("default");
        labelStyle.fontColor = Color.WHITE;
        skin.add("default", labelStyle);

        // Create and add WindowStyle
        Window.WindowStyle windowStyle = new Window.WindowStyle();
        windowStyle.titleFont = skin.getFont("default");
        windowStyle.background = skin.newDrawable("white", new Color(0.2f, 0.2f, 0.2f, 0.8f));
        windowStyle.titleFontColor = Color.WHITE;
        skin.add("default", windowStyle);

        return font;
    }

    private void createUI() {
        // Original desktop UI
        Table rootTable = new Table();
        rootTable.setFillParent(true);

        TextureRegion backgroundRegion = new TextureRegionDrawable(TextureManager.ui.findRegion("ethereal")).getRegion();
        Image backgroundImage = new Image(backgroundRegion);
        backgroundImage.setFillParent(true);
        stage.addActor(backgroundImage);
        stage.addActor(rootTable);

        // Title Label
        Label titleLabel = new Label("Capsule Story", skin);
        titleLabel.setFontScale(1.5f);

        // Version Label
        Label versionLabel = new Label("Version 1.0", skin);
        versionLabel.setFontScale(0.8f);

        // Buttons with styles
        TextButton.TextButtonStyle buttonStyle = skin.get("default", TextButton.TextButtonStyle.class);
        buttonStyle.font.getData().setScale(1.2f);

        TextButton singlePlayerButton = new TextButton("Single Player", buttonStyle);
        TextButton multiplayerButton = new TextButton("Multiplayer", buttonStyle);
        TextButton exitButton = new TextButton("Exit Game", buttonStyle);

        // Build the UI layout
        rootTable.pad(20);
        rootTable.defaults().pad(10).width(Value.percentWidth(0.6f, rootTable)).height(50);

        rootTable.add(titleLabel).expandX().center().row();
        rootTable.add(versionLabel).expandX().center().padBottom(30).row();
        rootTable.add(singlePlayerButton).row();
        rootTable.add(multiplayerButton).row();
        rootTable.add(exitButton).row();

        // Add button listeners
        singlePlayerButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (isAndroid) {
                    game.setScreen(new AndroidWorldSelectionScreen(game));
                } else {
                    game.setScreen(new WorldSelectionScreen(game));
                }
                dispose();
            }
        });

        multiplayerButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (isAndroid) {
                    game.setScreen(new AndroidLoginScreen(game));
                } else {
                    game.setScreen(new LoginScreen(game));
                }
                dispose();
            }
        });

        exitButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                Gdx.app.exit();
            }
        });
    }

    private void createMobileUI() {
        // Mobile-optimized UI
        Table rootTable = new Table();
        rootTable.setFillParent(true);
        rootTable.pad(32f); // Larger padding for mobile

        // Background
        TextureRegion backgroundRegion = TextureManager.ui.findRegion("ethereal");
        if (backgroundRegion != null) {
            Image backgroundImage = new Image(backgroundRegion);
            backgroundImage.setFillParent(true);
            backgroundImage.setScaling(com.badlogic.gdx.utils.Scaling.fill);
            stage.addActor(backgroundImage);
        }

        stage.addActor(rootTable);

        // Title with larger font
        Label titleLabel = new Label("Capsule Story", skin);
        titleLabel.setFontScale(2.5f);
        titleLabel.setAlignment(Align.center);

        // Version Label
        Label versionLabel = new Label("Version 1.0", skin);
        versionLabel.setFontScale(1.2f);
        versionLabel.setColor(0.8f, 0.8f, 0.8f, 1f);

        // Create mobile-optimized buttons
        TextButton.TextButtonStyle mobileButtonStyle = new TextButton.TextButtonStyle(skin.get("default", TextButton.TextButtonStyle.class));
        mobileButtonStyle.font.getData().setScale(1.8f);

        // Add rounded corners and better padding
        mobileButtonStyle.up = createRoundedDrawable(new Color(0.3f, 0.3f, 0.3f, 0.9f));
        mobileButtonStyle.down = createRoundedDrawable(new Color(0.5f, 0.5f, 0.5f, 0.9f));
        mobileButtonStyle.over = createRoundedDrawable(new Color(0.4f, 0.4f, 0.4f, 0.9f));

        TextButton singlePlayerButton = new TextButton("Single Player", mobileButtonStyle);
        TextButton multiplayerButton = new TextButton("Multiplayer", mobileButtonStyle);
        TextButton exitButton = new TextButton("Exit Game", mobileButtonStyle);

        // Add padding to buttons for better touch targets
        singlePlayerButton.pad(24f, 48f, 24f, 48f);
        multiplayerButton.pad(24f, 48f, 24f, 48f);
        exitButton.pad(24f, 48f, 24f, 48f);

        // Build the mobile layout
        rootTable.defaults().pad(16f).width(Value.percentWidth(0.8f, rootTable)).height(80f);

        rootTable.add(titleLabel).expandX().center().row();
        rootTable.add(versionLabel).expandX().center().padBottom(48f).row();
        rootTable.add(singlePlayerButton).row();
        rootTable.add(multiplayerButton).row();
        rootTable.add(exitButton).padTop(32f).row();

        // Add button listeners (navigate to Android-specific screens)
        singlePlayerButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.setScreen(new AndroidWorldSelectionScreen(game));
                dispose();
            }
        });

        multiplayerButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.setScreen(new AndroidLoginScreen(game));
                dispose();
            }
        });

        exitButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                Gdx.app.exit();
            }
        });
    }

    private TextureRegionDrawable createRoundedDrawable(Color color) {
        // For now, use a simple colored drawable
        // In production, you'd create an actual rounded rectangle texture
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();
        TextureRegionDrawable drawable = new TextureRegionDrawable(new Texture(pixmap));
        pixmap.dispose();
        return drawable;
    }

    private void showError(String message) {
        Dialog dialog = new Dialog("Error", skin);
        dialog.text(message);
        dialog.button("OK");
        dialog.show(stage);
    }

    @Override
    public void render(float delta) {
        // Handle back button on Android
        if (Gdx.input.isKeyJustPressed(Input.Keys.BACK)) {
            Gdx.app.exit();
            return;
        }

        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        stage.dispose();
        skin.dispose();
        font.dispose();
        if (timer != null) {
            timer.clear();
        }
    }

    @Override
    public void show() {
        AudioManager.getInstance().playMenuMusic();
    }

    @Override
    public void hide() {
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }
}
