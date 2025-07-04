package io.github.pokemeetup;

import android.AndroidFileSystemDelegate;
import android.os.Bundle;
import android.util.Log;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
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
            Log.d("AndroidLauncher", "Starting game initialization");
            AndroidFileSystemDelegate fileDelegate = new AndroidFileSystemDelegate(this);
            GameFileSystem fileSystem = GameFileSystem.getInstance();
            fileSystem.setDelegate(fileDelegate);
            Log.d("AndroidLauncher", "File system delegate initialized: " +
                (fileSystem.getDelegate() != null ? fileSystem.getDelegate().getClass().getSimpleName() : "null"));
            AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
            configureAndroidSettings(config);
            verifyAssetFiles();
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
            String[] assets = getAssets().list("");
            Log.d("AndroidLauncher", "Root assets: " + Arrays.toString(assets));
            String[] dataFiles = null;
            try {
                dataFiles = getAssets().list("Data");
                Log.d("AndroidLauncher", "Files in Data/: " + Arrays.toString(dataFiles));
            } catch (IOException e) {
                try {
                    dataFiles = getAssets().list("Data");
                    Log.d("AndroidLauncher", "Files in data/: " + Arrays.toString(dataFiles));
                } catch (IOException e2) {
                    Log.e("AndroidLauncher", "Could not find Data or data directory");
                }
            }
            boolean biomesFound = false;
            String[] biomePaths = {
                    "Data/biomes.json",
                    "Data/biomes.json",
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
