package io.github.pokemeetup.utils;

import com.badlogic.gdx.Gdx;

public class GameLogger {
    public static boolean isInfoEnabled = true;
    public static boolean isErrorEnabled = true;

    public static void info(String message) {
        if (isInfoEnabled) {
            if (Gdx.app != null) {
                Gdx.app.log("Game", message);
            } else {
//            System.out.println(message);
            }
        }
    }

    public static void error(String message) {
        if (isErrorEnabled) {
            if (Gdx.app != null) {
                Gdx.app.error("Game", message);
            } else {
                System.out.println(message);
            }
        }
    }
}
