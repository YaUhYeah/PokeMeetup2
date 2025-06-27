package io.github.pokemeetup.utils;

import com.badlogic.gdx.Gdx;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class GameLogger {
    public static boolean isInfoEnabled;
    public static boolean isErrorEnabled;

    private static PrintWriter fileWriter;

    static {
        isInfoEnabled =true;// Boolean.getBoolean("game.log.info");  // default is false
        isErrorEnabled = !Boolean.getBoolean("game.log.error.disabled");
        try {
            fileWriter = new PrintWriter(new FileWriter("game.log", true));
        } catch (IOException e) {
            System.err.println("Unable to open log file: " + e.getMessage());
        }
    }

    public static void info(String message) {
        if (isInfoEnabled) {
            if (Gdx.app != null) {
                Gdx.app.log("Game", message);
            } else {
                System.out.println("INFO: " + message);
            }
            logToFile("INFO: " + message);
        }
    }

    public static void error(String message) {
        if (isErrorEnabled) {
            if (Gdx.app != null) {
                Gdx.app.error("Game", message);
            } else {
                System.err.println("ERROR: " + message);
            }
            logToFile("ERROR: " + message);
        }
    }

    private static void logToFile(String logMessage) {
        if (fileWriter != null) {
            fileWriter.println(logMessage);
            fileWriter.flush();
        }
    }
    public static void setLogging(boolean infoEnabled, boolean errorEnabled) {
        isInfoEnabled = infoEnabled;
        isErrorEnabled = errorEnabled;
    }
    public static void close() {
        if (fileWriter != null) {
            fileWriter.close();
        }
    }
}
