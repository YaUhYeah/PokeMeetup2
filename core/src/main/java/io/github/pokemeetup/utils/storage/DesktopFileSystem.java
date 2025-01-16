package io.github.pokemeetup.utils.storage;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import io.github.pokemeetup.FileSystemDelegate;
import io.github.pokemeetup.utils.GameLogger;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class DesktopFileSystem implements FileSystemDelegate {

    @Override
    public boolean exists(String path) {
        return Gdx.files.local(path).exists();
    }

    @Override
    public void createDirectory(String path) {
        FileHandle dir = Gdx.files.local(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
    @Override
    public void moveFile(String sourcePath, String destinationPath) throws IOException {
        File sourceFile = new File(sourcePath);
        File destFile = new File(destinationPath);

        if (!sourceFile.exists()) {
            throw new FileNotFoundException("Source file does not exist: " + sourcePath);
        }

        boolean success = sourceFile.renameTo(destFile);
        if (!success) {
            throw new IOException("Failed to move file from " + sourcePath + " to " + destinationPath);
        }
    }

    @Override
    public void writeString(String path, String content) throws IOException {
        try {
            Gdx.files.local(path).writeString(content, false);
        } catch (Exception e) {
            throw new IOException("Failed to write to file: " + path, e);
        }
    }

    @Override
    public String readString(String path) throws IOException {
        try {
            return Gdx.files.local(path).readString();
        } catch (Exception e) {
            throw new IOException("Failed to read from file: " + path, e);
        }
    }

    @Override
    public void deleteFile(String path) {
        FileHandle file = Gdx.files.local(path);
        if (file.exists()) {
            file.delete();
        }
    }

    @Override
    public void deleteDirectory(String path) {
        FileHandle dir = Gdx.files.local(path);
        if (dir.exists() && dir.isDirectory()) {
            dir.deleteDirectory();
        }
    }

    @Override
    public boolean isDirectory(String path) {
        return Gdx.files.local(path).isDirectory();
    }

    @Override
    public String[] list(String path) {
        FileHandle dir = Gdx.files.local(path);
        if (dir.exists() && dir.isDirectory()) {
            FileHandle[] files = dir.list();
            String[] names = new String[files.length];
            for (int i = 0; i < files.length; i++) {
                names[i] = files[i].name();
            }
            return names;
        }
        return new String[0];
    }

    @Override
    public void copyFile(String sourcePath, String destinationPath) throws IOException {
        try {
            FileHandle source = Gdx.files.local(sourcePath);
            FileHandle destination = Gdx.files.local(destinationPath);
            source.copyTo(destination);
        } catch (Exception e) {
            throw new IOException("Failed to copy file from " + sourcePath + " to " + destinationPath, e);
        }
    }

    @Override
    public InputStream openInputStream(String path) throws IOException {
        try {
            return Gdx.files.local(path).read();
        } catch (Exception e) {
            throw new IOException("Failed to open input stream: " + path, e);
        }
    }

    @Override
    public OutputStream openOutputStream(String path) throws IOException {
        try {
            return Gdx.files.local(path).write(false);
        } catch (Exception e) {
            throw new IOException("Failed to open output stream: " + path, e);
        }
    }
}
