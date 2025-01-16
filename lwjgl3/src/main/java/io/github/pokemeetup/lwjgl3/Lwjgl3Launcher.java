package io.github.pokemeetup.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowListener;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.utils.GameLogger;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Lwjgl3Launcher {

    public static void main(String[] args) {
        // Instantiate the game
        CreatureCaptureGame game = new CreatureCaptureGame(false);

        Lwjgl3ApplicationConfiguration configuration = getDefaultConfiguration();

        configuration.setWindowListener(new Lwjgl3WindowListener() {
            @Override
            public void created(Lwjgl3Window window) {
                GameLogger.info("Game window created.");
            }

            @Override
            public void iconified(boolean isIconified) {
                // Handle window iconify (minimize) events if necessary
            }

            @Override
            public void maximized(boolean isMaximized) {
                // Handle window maximize events if necessary
            }

            @Override
            public void focusLost() {
                // Handle window losing focus if necessary
            }

            @Override
            public void focusGained() {
                // Handle window gaining focus if necessary
            }
            // In Lwjgl3Launcher class
            @Override
            public boolean closeRequested() {
                GameLogger.info("Window close requested, saving final state...");
                game.shutdown();
                return true;
            }
            @Override
            public void filesDropped(String[] files) {
                // Handle file drop events if necessary
            }

            @Override
            public void refreshRequested() {
                // Handle refresh requests if necessary
            }
        });

        // Instantiate the application, which starts the game loop
        new Lwjgl3Application(game, configuration);
    }

    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("PokeMeetup");
        configuration.useVsync(true);

        // Set the foreground FPS to the monitor's refresh rate + 1 for smoother rendering
        int refreshRate = Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate;
        if (refreshRate > 0) {
            configuration.setForegroundFPS(refreshRate + 1);
        } else {
            // Fallback if refresh rate is not available
            configuration.setForegroundFPS(60);
        }

        configuration.setWindowedMode(800, 600);
        configuration.setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png");
        return configuration;
    }
}
