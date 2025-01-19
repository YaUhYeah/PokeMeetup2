package io.github.pokemeetup.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;

public class LoadingScreen implements Screen {
    private static final float PROGRESS_BAR_WIDTH = 300;
    private static final float PROGRESS_BAR_HEIGHT = 20;
    private static final float UPDATE_INTERVAL = 0.1f; // Update every 100ms

    private final CreatureCaptureGame game;
    private Screen nextScreen;
    private final Stage stage;
    private final ProgressBar progressBar;
    private final Label statusLabel;
    private final Label progressLabel;
    private boolean disposed = false;
    private float progress = 0;
    private float elapsedTime = 0;
    private float updateTimer = 0;
    private String currentStatus = "Initializing...";

    public LoadingScreen(CreatureCaptureGame game, Screen nextScreen) {
        this.game = game;
        this.nextScreen = nextScreen;
        this.stage = new Stage(new ScreenViewport());
        Skin skin = new Skin(Gdx.files.internal("atlas/ui-gfx-atlas.json"));
        Table mainTable = new Table();
        mainTable.setFillParent(true);
        ProgressBar.ProgressBarStyle progressStyle = new ProgressBar.ProgressBarStyle();
        progressStyle.background = skin.getDrawable("progress-bar-bg");
        progressStyle.knob = skin.getDrawable("progress-bar-knob");
        progressStyle.knobBefore = skin.getDrawable("progress-bar-bg");

        progressBar = new ProgressBar(0, 1, 0.01f, false, progressStyle);
        progressBar.setSize(PROGRESS_BAR_WIDTH, PROGRESS_BAR_HEIGHT);

        // Labels
        Label.LabelStyle labelStyle = new Label.LabelStyle(skin.getFont("default-font"), Color.WHITE);
        statusLabel = new Label("", labelStyle);
        progressLabel = new Label("", labelStyle);

        // Layout
        mainTable.add(statusLabel).pad(10).row();
        mainTable.add(progressBar).width(PROGRESS_BAR_WIDTH).height(PROGRESS_BAR_HEIGHT).pad(10).row();
        mainTable.add(progressLabel).pad(10);

        stage.addActor(mainTable);

        GameLogger.info("Loading screen initialized");
    }

    public void setNextScreen(Screen screen) {
        this.nextScreen = screen;
        GameLogger.info("Next screen set: " + screen.getClass().getSimpleName());
    }

    @Override
    public void render(float delta) {
        elapsedTime += delta;
        updateTimer += delta;

        // Update status at intervals
        if (updateTimer >= UPDATE_INTERVAL) {
            updateTimer = 0;
            updateLoadingStatus();
        }

        // Clear screen
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.1f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Check if next screen is ready
        if (nextScreen instanceof GameScreen) {
            GameScreen gameScreen = (GameScreen) nextScreen;

            if (gameScreen.isInitialized()) {
                GameLogger.info("Game screen initialized, transitioning...");
                progress = 1;
                updateUI();
                // Add small delay before transition
                if (elapsedTime > 0.5f) {
                    game.setScreen(nextScreen);
                    dispose();
                    return;
                }
            } else {
                updateProgress();
            }
        }

        stage.act(delta);
        stage.draw();
    }

    private void updateLoadingStatus() {
        if (nextScreen instanceof GameScreen) {
            GameScreen gameScreen = (GameScreen) nextScreen;
            String newStatus = getStatusMessage(gameScreen);

            if (!newStatus.equals(currentStatus)) {
                currentStatus = newStatus;
                statusLabel.setText(currentStatus);
                GameLogger.info("Loading status: " + currentStatus);
            }
        }
        updateUI();
    }

    private String getStatusMessage(GameScreen gameScreen) {
        if (progress < 0.3f) return "Initializing world...";
        if (progress < 0.6f) return "Loading chunks...";
        if (progress < 0.9f) return "Preparing game...";
        return "Starting game...";
    }

    private void updateProgress() {
        // Update progress based on actual loading state
        if (GameContext.get().getWorld() != null && GameContext.get().getWorld().getChunks() != null) {
            int totalRequired = (World.INITIAL_LOAD_RADIUS * 2 + 1) *
                (World.INITIAL_LOAD_RADIUS * 2 + 1);
            int loaded = GameContext.get().getWorld().getChunks().size();
            progress = Math.min(0.9f, (float) loaded / totalRequired);
        }
    }

    private void updateUI() {
        progressBar.setValue(progress);
        progressLabel.setText(String.format("%.0f%%", progress * 100));
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        if (!disposed) {
            GameLogger.info("Disposing loading screen");
            stage.dispose();
            disposed = true;
        }
    }

    @Override
    public void show() {}

    @Override
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public void hide() {}
}
