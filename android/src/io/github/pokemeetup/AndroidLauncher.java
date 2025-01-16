package io.github.pokemeetup;

import android.AndroidFileSystemDelegate;
import android.os.Bundle;
import android.util.Log;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.storage.GameFileSystem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;

public class AndroidLauncher extends AndroidApplication {
    private String readStreamToString(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);

            // Log the start of initialization
            Log.d("AndroidLauncher", "Starting game initialization");

            // Create delegate FIRST
            AndroidFileSystemDelegate fileDelegate = new AndroidFileSystemDelegate(this);

            // Initialize GameFileSystem BEFORE accessing getInstance
            GameFileSystem fileSystem = GameFileSystem.getInstance();
            fileSystem.setDelegate(fileDelegate);

            // Debug log to verify delegate
            Log.d("AndroidLauncher", "File system delegate initialized: " +
                (fileSystem.getDelegate() != null ? fileSystem.getDelegate().getClass().getSimpleName() : "null"));

            // Configure Android
            AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
            configureAndroidSettings(config);

            // Pre-verify critical files exist in assets
            verifyAssetFiles();

            // Initialize the game
            CreatureCaptureGame game = new CreatureCaptureGame(true);
            initialize(game, config);

            Log.d("AndroidLauncher", "Game initialization completed successfully");

        } catch (Exception e) {
            Log.e("AndroidLauncher", "Failed to initialize game", e);
            String message = "Initialization failed: " + e.getMessage();
            GameLogger.error(message);
            throw new RuntimeException(message, e);
        }
    }

    private void verifyAssetFiles() {
        try {
            // List all assets first for debugging
            String[] assets = getAssets().list("");
            Log.d("AndroidLauncher", "Root assets: " + Arrays.toString(assets));

            // Try both Data and data directories
            String[] dataFiles = null;
            try {
                dataFiles = getAssets().list("data");
                Log.d("AndroidLauncher", "Files in Data/: " + Arrays.toString(dataFiles));
            } catch (IOException e) {
                try {
                    dataFiles = getAssets().list("data");
                    Log.d("AndroidLauncher", "Files in data/: " + Arrays.toString(dataFiles));
                } catch (IOException e2) {
                    Log.e("AndroidLauncher", "Could not find Data or data directory");
                }
            }

            // Specifically verify biomes.json
            boolean biomesFound = false;
            String[] biomePaths = {
                    "data/biomes.json",
                    "data/biomes.json",
                "biomes.json"
            };

            for (String path : biomePaths) {
                try (InputStream is = getAssets().open(path)) {
                    String content = readStreamToString(is);
                    Log.d("AndroidLauncher", "Found biomes.json at " + path +
                        " with content length: " + content.length());
                    biomesFound = true;
                    break;
                } catch (IOException ignored) {}
            }

            if (!biomesFound) {
                throw new RuntimeException("Could not find biomes.json in any location");
            }
        } catch (Exception e) {
            Log.e("AndroidLauncher", "Failed to verify assets: " + e.getMessage());
            throw new RuntimeException("Asset verification failed", e);
        }
    }

    private void verifyAssetFile(String path) throws IOException {
        String[] variants = {
            path,
            path.toLowerCase(),
            "assets/" + path,
            path.replace("data/", "data/")
        };

        boolean found = false;
        for (String variant : variants) {
            try (InputStream is = getAssets().open(variant)) {
                // Read a small amount to verify file is readable
                byte[] buffer = new byte[1024];
                int bytesRead = is.read(buffer);
                if (bytesRead > 0) {
                    Log.d("AndroidLauncher", "Successfully verified file: " + variant);
                    found = true;
                    break;
                }
            } catch (IOException ignored) {
                // Try next variant
            }
        }

        if (!found) {
            throw new IOException("Could not find or read file: " + path);
        }
    }

    private void configureAndroidSettings(AndroidApplicationConfiguration config) {
        config.useGL30 = false;
        config.useImmersiveMode = true;
        config.useAccelerometer = false;
        config.useCompass = false;
        config.r = 8;
        config.g = 8;
        config.b = 8;
        config.a = 8;
        config.depth = 16;
        config.maxSimultaneousSounds = 8;
        config.useWakelock = true;
    }
}
