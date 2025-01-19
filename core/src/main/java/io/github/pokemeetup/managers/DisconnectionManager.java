package io.github.pokemeetup.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.screens.DisconnectionScreen;
import io.github.pokemeetup.screens.LoginScreen;
import io.github.pokemeetup.utils.GameLogger;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class DisconnectionManager {
    private static final int MAX_RECONNECT_ATTEMPTS = 3;
    private static final long RECONNECT_DELAY = 5000; // 5 seconds
    private static final long SAVE_INTERVAL = 30000; // 30 seconds

    private final AtomicBoolean isHandlingDisconnect = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private final CreatureCaptureGame game;
    private Screen previousScreen;
    private DisconnectionScreen disconnectionScreen;
    private String disconnectReason;
    private boolean showingDisconnectScreen = false;
    private ScheduledFuture<?> autoSaveTask;
    private ScheduledExecutorService scheduler;

    public DisconnectionManager(CreatureCaptureGame game) {
        this.game = game;
    }

    public void handleDisconnect(String reason) {
        if (isHandlingDisconnect.getAndSet(true)) {
            return; // Already handling disconnect
        }

        GameLogger.info("Handling disconnect: " + reason);
        disconnectReason = reason;
        reconnectAttempts.set(0);
        previousScreen = game.getScreen();

        // Save player state if possible
        savePlayerState();

        // Schedule periodic saves while disconnected
        scheduleAutoSave();

        // Show disconnect screen on main thread
        Gdx.app.postRunnable(() -> {
            if (!showingDisconnectScreen) {
                showDisconnectScreen();
            }
        });
    }

    private void scheduleAutoSave() {
        if (scheduler != null && !scheduler.isShutdown()) {
            autoSaveTask = scheduler.scheduleWithFixedDelay(
                this::savePlayerState,
                SAVE_INTERVAL,
                SAVE_INTERVAL,
                TimeUnit.MILLISECONDS
            );
        }
    }

    private void savePlayerState() {
        try {
            if (GameContext.get().getPlayer() != null && GameContext.get().getWorld() != null) {
                GameContext.get().getWorld().save();
                GameLogger.info("Saved player state during disconnect");
            }
        } catch (Exception e) {
            GameLogger.error("Failed to save player state: " + e.getMessage());
        }
    }

    public void attemptReconnect() {
        if (reconnectAttempts.get() >= MAX_RECONNECT_ATTEMPTS) {
            exitToLogin("Maximum reconnection attempts reached");
            return;
        }

        reconnectAttempts.incrementAndGet();
        GameLogger.info("Attempting reconnect: " + reconnectAttempts.get() + "/" + MAX_RECONNECT_ATTEMPTS);

        GameClient client = GameContext.get().getGameClient();
        if (client != null) {
            client.dispose();
            GameContext.get().setGameClient(null);
            try {
                Thread.sleep(RECONNECT_DELAY);
                client.connect();
            } catch (Exception e) {
                GameLogger.error("Reconnection attempt failed: " + e.getMessage());
                handleReconnectFailure();
            }
        } else {
            exitToLogin("Game client is null");
        }
    }

    private void handleReconnectFailure() {
        if (reconnectAttempts.get() < MAX_RECONNECT_ATTEMPTS) {
            // Schedule another attempt
            Gdx.app.postRunnable(this::attemptReconnect);
        } else {
            exitToLogin("Unable to reconnect to server");
        }
    }

    private void showDisconnectScreen() {
        showingDisconnectScreen = true;
        disconnectionScreen = new DisconnectionScreen(game, disconnectReason, this);
        Gdx.app.postRunnable(() -> {
            game.setScreen(disconnectionScreen);
        });
    }

    public void onReconnectionSuccess() {
        Gdx.app.postRunnable(() -> {
            isHandlingDisconnect.set(false);
            showingDisconnectScreen = false;
            cancelAutoSave();

            if (previousScreen != null) {
                game.setScreen(previousScreen);
                GameLogger.info("Returned to previous screen after reconnection");
            }
        });
    }

    private void cancelAutoSave() {
        if (autoSaveTask != null && !autoSaveTask.isDone()) {
            autoSaveTask.cancel(false);
        }
    }

    private void exitToLogin(String reason) {
        Gdx.app.postRunnable(() -> {
            cleanup();
            game.setScreen(new LoginScreen(game));
            GameLogger.info("Exited to login: " + reason);
        });
    }

    public void cleanup() {
        isHandlingDisconnect.set(false);
        showingDisconnectScreen = false;
        cancelAutoSave();

        if (GameContext.get().getGameClient() != null) {
            GameContext.get().getGameClient().dispose();
            GameContext.get().setGameClient(null);
        }

        if (GameContext.get().getWorld() != null) {
            GameContext.get().getWorld().save();
            GameContext.get().getWorld().dispose();
            GameContext.get().setWorld(null);
        }

        if (previousScreen != null) {
            previousScreen.dispose();
            previousScreen = null;
        }

        if (disconnectionScreen != null) {
            disconnectionScreen.dispose();
            disconnectionScreen = null;
        }

        System.gc();
    }

    public boolean isHandlingDisconnect() {
        return isHandlingDisconnect.get();
    }

    public void setScheduler(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }
}
