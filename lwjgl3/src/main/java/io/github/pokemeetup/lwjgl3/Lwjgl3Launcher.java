package io.github.pokemeetup.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowListener;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.utils.GameLogger;

public class Lwjgl3Launcher {

    public static void main(String[] args) {
        GameLogger.info("Working directory: " + System.getProperty("user.dir"));
        CreatureCaptureGame game = new CreatureCaptureGame(false);

        Lwjgl3ApplicationConfiguration configuration = getDefaultConfiguration();

        configuration.setWindowListener(new Lwjgl3WindowListener() {
            @Override
            public void created(Lwjgl3Window window) {
                GameLogger.info("Game window created.");
            }

            @Override
            public void iconified(boolean isIconified) { }

            @Override
            public void maximized(boolean isMaximized) { }

            @Override
            public void focusLost() { }

            @Override
            public void focusGained() { }

            @Override
            public boolean closeRequested() {
                GameLogger.info("Window close requested, saving final state...");
                game.shutdown();
                return true;
            }

            @Override
            public void filesDropped(String[] files) { }

            @Override
            public void refreshRequested() { }
        });

        new Lwjgl3Application(game, configuration);
    }

    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("Capsule Story");
        configuration.useVsync(true);

        int refreshRate = Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate;
        configuration.setForegroundFPS(refreshRate > 0 ? refreshRate + 1 : 60);

        configuration.setWindowedMode(800, 600);
        configuration.setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png");
        return configuration;
    }
}
