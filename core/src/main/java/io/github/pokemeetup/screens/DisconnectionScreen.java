package io.github.pokemeetup.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.managers.DisconnectionManager;
import io.github.pokemeetup.utils.GameLogger;

public class DisconnectionScreen implements Screen {
    private static final float RETRY_INTERVAL = 5f; // 5 seconds between retries
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final float BUTTON_WIDTH = 200f;
    private static final float BUTTON_HEIGHT = 50f;

    private final CreatureCaptureGame game;
    private final Stage stage;
    private final Skin skin;
    private final Table mainTable;
    private final Label statusLabel;
    private final Label countdownLabel;
    private final TextButton retryButton;
    private final TextButton exitButton;
    private final DisconnectionManager disconnectionManager;

    private int retryAttempts = 0;
    private float countdownTime = RETRY_INTERVAL;
    private boolean isRetrying = false;
    private boolean disposed = false;
    private String disconnectReason;

    private final Timer.Task countdownTask = new Timer.Task() {
        @Override
        public void run() {
            if (countdownTime > 0) {
                countdownTime -= 1;
                updateCountdownLabel();
            } else {
                stopCountdown();
                attemptReconnection();
            }
        }
    };

    public DisconnectionScreen(CreatureCaptureGame game, String reason, DisconnectionManager manager) {
        this.game = game;
        this.disconnectReason = reason;
        this.disconnectionManager = manager;
        this.stage = new Stage(new ScreenViewport());
        this.skin = new Skin(Gdx.files.internal("Skins/uiskin.json"));

        // Create main table for layout
        mainTable = new Table();
        mainTable.setFillParent(true);

        // Create UI components with custom styling
        Label titleLabel = new Label("Connection Lost", createLabelStyle());
        titleLabel.setFontScale(2f);

        statusLabel = new Label(reason, createLabelStyle());
        countdownLabel = new Label("", createLabelStyle());

        retryButton = createButton("Retry Connection", new Color(0.2f, 0.6f, 1f, 1f));
        exitButton = createButton("Exit to Menu", new Color(1f, 0.3f, 0.3f, 1f));

        // Layout components
        mainTable.add(titleLabel).pad(20).row();
        mainTable.add(statusLabel).pad(10).row();
        mainTable.add(countdownLabel).pad(10).row();

        // Button table for horizontal layout
        Table buttonTable = new Table();
        buttonTable.add(retryButton).pad(10).width(BUTTON_WIDTH).height(BUTTON_HEIGHT);
        buttonTable.add(exitButton).pad(10).width(BUTTON_WIDTH).height(BUTTON_HEIGHT);

        mainTable.add(buttonTable).pad(20).row();

        // Add overlay shadow effect
        Table overlayTable = new Table();
        overlayTable.setFillParent(true);
        overlayTable.setBackground(skin.newDrawable("white", new Color(0f, 0f, 0f, 0.8f)));

        // Add tables to stage
        stage.addActor(overlayTable);
        stage.addActor(mainTable);

        // Set up input processing
        Gdx.input.setInputProcessor(stage);

        // Add button listeners
        setupButtonListeners();

        GameLogger.info("DisconnectionScreen initialized with reason: " + reason);
    }

    private Label.LabelStyle createLabelStyle() {
        return new Label.LabelStyle(skin.getFont("default-font"), Color.WHITE);
    }

    private TextButton createButton(String text, Color color) {
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = skin.getFont("default-font");
        style.fontColor = Color.WHITE;
        style.up = skin.newDrawable("default-round", color);
        style.down = skin.newDrawable("default-round-down", color.cpy().mul(0.8f));
        style.over = skin.newDrawable("default-round", color.cpy().mul(1.2f));

        TextButton button = new TextButton(text, style);
        button.pad(10);
        return button;
    }

    private void setupButtonListeners() {
        retryButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!isRetrying) {
                    startRetryCountdown();
                }
            }
        });

        exitButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                exitToMenu();
            }
        });
    }

    @Override
    public void render(float delta) {
        // Clear screen with dark background
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.15f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Update countdown if active
        if (isRetrying) {
            countdownTime -= delta;
            if (countdownTime <= 0) {
                stopCountdown();
                attemptReconnection();
            }
            updateCountdownLabel();
        }

        // Update and draw stage
        stage.act(delta);
        stage.draw();
    }

    private void startRetryCountdown() {
        if (retryAttempts >= MAX_RETRY_ATTEMPTS) {
            statusLabel.setText("Maximum retry attempts reached");
            retryButton.setDisabled(true);
            return;
        }

        isRetrying = true;
        countdownTime = RETRY_INTERVAL;
        retryButton.setDisabled(true);
        updateCountdownLabel();
        GameLogger.info("Starting retry countdown");
    }

    private void stopCountdown() {
        isRetrying = false;
        countdownTime = RETRY_INTERVAL;
        retryButton.setDisabled(false);
        countdownLabel.setText("");

        if (countdownTask.isScheduled()) {
            countdownTask.cancel();
        }
    }

    private void updateCountdownLabel() {
        countdownLabel.setText(String.format("Retrying in %.0f seconds...", Math.max(0, countdownTime)));
        countdownLabel.setAlignment(Align.center);
    }

    private void attemptReconnection() {
        if (disconnectionManager != null) {
            disconnectionManager.attemptReconnect();
        }
    }

    private void exitToMenu() {
        if (disconnectionManager != null) {
            disconnectionManager.cleanup();
        }
        game.setScreen(new LoginScreen(game));
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        mainTable.invalidate(); // Reflow the UI
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }
    @Override
    public void dispose() {
        if (!disposed) {
            stage.dispose();
            disposed = true;
            GameLogger.info("DisconnectionScreen disposed");
        }
    }

    @Override
    public void hide() {
        // Remove input processor when screen is hidden
        if (Gdx.input.getInputProcessor() == stage) {
            Gdx.input.setInputProcessor(null);
        }
    }

    @Override
    public void pause() {
        // Pause any active countdowns or animations
        if (isRetrying) {
            stopCountdown();
        }
    }

    @Override
    public void resume() {
        // Ensure input processor is set when screen resumes
        Gdx.input.setInputProcessor(stage);
    }

    public void onReconnectionSuccess() {
        Gdx.app.postRunnable(() -> {
            stopCountdown();
            statusLabel.setText("Connection restored!");
            // Add success animation or feedback here if desired
            Timer.schedule(new Timer.Task() {
                @Override
                public void run() {
                    if (disconnectionManager != null) {
                        disconnectionManager.onReconnectionSuccess();
                    }
                }
            }, 1); // Wait 1 second before returning to game
        });
    }

    public void onReconnectionFailure(String reason) {
        Gdx.app.postRunnable(() -> {
            stopCountdown();
            statusLabel.setText("Reconnection failed: " + reason);
            if (retryAttempts >= MAX_RETRY_ATTEMPTS) {
                retryButton.setDisabled(true);
                Timer.schedule(new Timer.Task() {
                    @Override
                    public void run() {
                        exitToMenu();
                    }
                }, 2); // Wait 2 seconds before exiting to menu
            }
        });
    }

    public void updateStatus(String status) {
        Gdx.app.postRunnable(() -> {
            statusLabel.setText(status);
        });
    }

    private void fadeOut(Runnable onComplete) {
        mainTable.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(
            com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeOut(0.5f),
            com.badlogic.gdx.scenes.scene2d.actions.Actions.run(onComplete)
        ));
    }

    private void fadeIn() {
        mainTable.getColor().a = 0;
        mainTable.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeIn(0.5f));
    }

    public boolean isRetrying() {
        return isRetrying;
    }

    public int getRetryAttempts() {
        return retryAttempts;
    }
}
