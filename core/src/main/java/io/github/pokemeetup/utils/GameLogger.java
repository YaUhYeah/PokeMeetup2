package io.github.pokemeetup.utils;

import com.badlogic.gdx.Gdx;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class GameLogger {
    // By default, only error logging is enabled.
    // You can enable info logging by passing -Dgame.log.info=true on the command line.
    public static boolean isInfoEnabled;
    public static boolean isErrorEnabled;

    private static PrintWriter fileWriter;

    static {
        // Read logging toggles from system properties (or set them manually)
        isInfoEnabled =true;// Boolean.getBoolean("game.log.info");  // default is false
        // Always enable errors unless explicitly disabled:
        isErrorEnabled = !Boolean.getBoolean("game.log.error.disabled");

        // Optionally, setup file logging. Log file will be appended.
        try {
            fileWriter = new PrintWriter(new FileWriter("game.log", true));
        } catch (IOException e) {
            // If file logging fails, fall back to console-only logging.
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

    // Optionally, allow toggling logging at runtime
    public static void setLogging(boolean infoEnabled, boolean errorEnabled) {
        isInfoEnabled = infoEnabled;
        isErrorEnabled = errorEnabled;
    }

    // Call this on shutdown to close the log file
    public static void close() {
        if (fileWriter != null) {
            fileWriter.close();
        }
    }
}
