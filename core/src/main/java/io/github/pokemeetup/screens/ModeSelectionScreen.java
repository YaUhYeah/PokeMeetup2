package io.github.pokemeetup.screens;

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

    public ModeSelectionScreen(CreatureCaptureGame game) {
        this.game = game;
        this.stage = new Stage(new ScreenViewport());
        game.reinitializeGame();
        Gdx.input.setInputProcessor(stage);

        this.skin = new Skin();
        this.timer = new Timer();

        try {
            this.font = initializeSkin();
            Gdx.app.log("SkinSetup", "Successfully initialized the skin.");
        } catch (Exception e) {
            showError("Failed to initialize UI: " + e.getMessage());
            Gdx.app.error("SkinSetup", "Failed to initialize UI", e);
            return;
        }
        AudioManager.getInstance().setMusicEnabled(true);
        createUI();
    }

    private BitmapFont initializeSkin() {
        // Add BitmapFont with larger font size
        BitmapFont font = new BitmapFont(Gdx.files.internal("Skins/default.fnt")); // Use a larger font file
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
        // Create root table
        Table rootTable = new Table();
        rootTable.setFillParent(true);

        Table mainTable = new Table();
        mainTable.setFillParent(true);
        TextureRegion backgroundRegion = new TextureRegionDrawable(TextureManager.ui.findRegion("ethereal")).getRegion();
        Image backgroundImage = new Image(backgroundRegion);
        backgroundImage.setFillParent(true);
        stage.addActor(backgroundImage);

        stage.addActor(backgroundImage);
        // Add root table on top
        stage.addActor(rootTable);

        // Title Label
        Label titleLabel = new Label("MineMon", skin);
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
                game.setScreen(new WorldSelectionScreen(game));
                dispose();
            }
        });

        multiplayerButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.setScreen(new LoginScreen(game));
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

    // Other required Screen methods...
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
