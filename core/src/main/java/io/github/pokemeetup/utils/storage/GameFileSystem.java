package io.github.pokemeetup.utils.storage;

import io.github.pokemeetup.FileSystemDelegate;
import io.github.pokemeetup.utils.GameLogger;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class GameFileSystem {
    private static GameFileSystem instance;
    private FileSystemDelegate delegate;
    private final Map<String, String> cachedPaths = new HashMap<>();

    public FileSystemDelegate getDelegate() {
        checkDelegate();
        return delegate;
    }
    private GameFileSystem() {}
    public void moveFile(String sourcePath, String destinationPath) throws IOException {
        checkDelegate();
        try {
            delegate.moveFile(sourcePath, destinationPath);
            GameLogger.info("Moved file from " + sourcePath + " to " + destinationPath);
        } catch (IOException e) {
            GameLogger.error("Failed to move file from " + sourcePath + " to " + destinationPath + ": " + e.getMessage());
            throw e;
        }
    }
    public static GameFileSystem getInstance() {
        if (instance == null) {
            instance = new GameFileSystem();
        }
        return instance;
    }

    public void setDelegate(FileSystemDelegate delegate) {
        this.delegate = delegate;
        GameLogger.info("File system delegate set: " + delegate.getClass().getSimpleName());
    }

    public void writeString(String path, String content) throws IOException {
        checkDelegate();
        try {
            delegate.writeString(path, content);
            GameLogger.info("Successfully wrote to file: " + path);
        } catch (IOException e) {
            GameLogger.error("Failed to write to file: " + path);
            throw e;
        }
    }

    public String readString(String path) throws IOException {
        checkDelegate();
        try {
            String content = delegate.readString(path);
            GameLogger.info("Successfully read from file: " + path);
            return content;
        } catch (IOException e) {
            GameLogger.error("Failed to read from file: " + path);
            throw e;
        }
    }

    public boolean exists(String path) {
        checkDelegate();
        return delegate.exists(path);
    }

    public void createDirectory(String path) {
        checkDelegate();
        try {
            delegate.createDirectory(path);
            GameLogger.info("Created directory: " + path);
        } catch (Exception e) {
            GameLogger.error("Failed to create directory: " + path);
            throw e;
        }
    }

    public void deleteFile(String path) {
        checkDelegate();
        try {
            delegate.deleteFile(path);
            GameLogger.info("Deleted file: " + path);
        } catch (Exception e) {
            GameLogger.error("Failed to delete file: " + path);
            throw e;
        }
    }

    public void deleteDirectory(String path) {
        checkDelegate();
        try {
            delegate.deleteDirectory(path);
            GameLogger.info("Deleted directory: " + path);
        } catch (Exception e) {
            GameLogger.error("Failed to delete directory: " + path);
            throw e;
        }
    }

    public boolean isDirectory(String path) {
        checkDelegate();
        return delegate.isDirectory(path);
    }

    public String[] list(String path) {
        checkDelegate();
        return delegate.list(path);
    }

    public void copyFile(String sourcePath, String destinationPath) throws IOException {
        checkDelegate();
        try {
            delegate.copyFile(sourcePath, destinationPath);
            GameLogger.info("Copied file from " + sourcePath + " to " + destinationPath);
        } catch (IOException e) {
            GameLogger.error("Failed to copy file from " + sourcePath + " to " + destinationPath);
            throw e;
        }
    }

    public InputStream openInputStream(String path) throws IOException {
        checkDelegate();
        return delegate.openInputStream(path);
    }

    public OutputStream openOutputStream(String path) throws IOException {
        checkDelegate();
        return delegate.openOutputStream(path);
    }

    private void checkDelegate() {
        if (delegate == null) {
            throw new IllegalStateException("FileSystemDelegate not set. Call setDelegate() first.");
        }
    }
}
