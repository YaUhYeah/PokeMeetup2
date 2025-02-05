package io.github.pokemeetup.utils.storage;

import io.github.pokemeetup.FileSystemDelegate;
import io.github.pokemeetup.utils.GameLogger;

import java.io.*;

public class GameFileSystem {
    private static GameFileSystem instance;
    private FileSystemDelegate delegate;

    public FileSystemDelegate getDelegate() {
        checkDelegate();
        return delegate;
    }
    private GameFileSystem() {}
    public void moveFile(String sourcePath, String destinationPath) throws IOException {
        checkDelegate();
        try {
            delegate.moveFile(sourcePath, destinationPath);
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
        } catch (IOException e) {
            GameLogger.error("Failed to write to file: " + path);
            throw e;
        }
    }

    public String readString(String path) throws IOException {
        checkDelegate();
        try {
            return delegate.readString(path);
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
        } catch (Exception e) {
            GameLogger.error("Failed to create directory: " + path);
            throw e;
        }
    }

    public void deleteFile(String path) {
        checkDelegate();
        try {
            delegate.deleteFile(path);
        } catch (Exception e) {
            GameLogger.error("Failed to delete file: " + path);
            throw e;
        }
    }

    public void deleteDirectory(String path) {
        checkDelegate();
        try {
            delegate.deleteDirectory(path);
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


    private void checkDelegate() {
        if (delegate == null) {
            throw new IllegalStateException("FileSystemDelegate not set. Call setDelegate() first.");
        }
    }
}
