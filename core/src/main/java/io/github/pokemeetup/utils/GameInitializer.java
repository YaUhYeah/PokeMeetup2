package io.github.pokemeetup.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

public class GameInitializer {
    private static String basePath = "";
    private static boolean isAndroid = false;
    private static boolean isInitialized = false;

    public static void init(String internalPath, boolean isAndroidPlatform) {
        basePath = internalPath;
        isAndroid = isAndroidPlatform;
        // Don't create directories here - wait for Gdx to be initialized
    }

    public static void initializeDirectories() {
        if (!isInitialized && Gdx.files != null) {
            createRequiredDirectories();
            isInitialized = true;
        }
    }

    private static void createRequiredDirectories() {
        if (Gdx.files == null) return;

        String[] dirs = {"worlds", "save", "configs", "atlas", "audio"};
        for (String dir : dirs) {
            String fullPath = getFullPath(dir);
            if (isAndroid) {
                java.io.File directory = new java.io.File(fullPath);
                if (!directory.exists()) {
                    directory.mkdirs();
                }
            } else {
                FileHandle dirHandle = Gdx.files.local(dir);
                if (!dirHandle.exists()) {
                    dirHandle.mkdirs();
                }
            }
        }
    }

    public static FileHandle getFileHandle(String path) {
        if (Gdx.files == null) {
            throw new IllegalStateException("Gdx.files not initialized yet");
        }

        if (isAndroid) {
            return new FileHandle(new java.io.File(basePath + path));
        }
        return Gdx.files.local(path);
    }

    public static String getFullPath(String path) {
        return isAndroid ? basePath + path : path;
    }

    public static boolean isAndroid() {
        return isAndroid;
    }
}
