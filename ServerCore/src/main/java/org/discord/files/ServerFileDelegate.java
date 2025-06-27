package org.discord.files;

import io.github.pokemeetup.FileSystemDelegate;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

public class ServerFileDelegate implements FileSystemDelegate {
    private static final Logger logger = Logger.getLogger(ServerFileDelegate.class.getName());
    private final String basePath;
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
    public ServerFileDelegate() {
        this.basePath = System.getProperty("user.dir");
        logger.info("Initialized server file system with base path: " + basePath);
    }

    private Path getPath(String path) {
        return Paths.get(basePath, path);
    }

    @Override
    public boolean exists(String path) {
        return Files.exists(getPath(path));
    }

    @Override
    public void createDirectory(String path) {
        try {
            Files.createDirectories(getPath(path));
        } catch (IOException e) {
            logger.severe("Failed to create directory: " + path);
            throw new RuntimeException("Failed to create directory: " + path, e);
        }
    }

    @Override
    public void writeString(String path, String content) throws IOException {
        Path filePath = getPath(path);
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content, StandardCharsets.UTF_8);
    }

    @Override
    public String readString(String path) throws IOException {
        return Files.readString(getPath(path), StandardCharsets.UTF_8);
    }

    @Override
    public void deleteFile(String path) {
        try {
            Files.deleteIfExists(getPath(path));
        } catch (IOException e) {
            logger.severe("Failed to delete file: " + path);
            throw new RuntimeException("Failed to delete file: " + path, e);
        }
    }

    @Override
    public void deleteDirectory(String path) {
        try {
            Files.walk(getPath(path))
                .sorted((a, b) -> -a.compareTo(b))
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        logger.severe("Failed to delete: " + p);
                    }
                });
        } catch (IOException e) {
            logger.severe("Failed to delete directory: " + path);
            throw new RuntimeException("Failed to delete directory: " + path, e);
        }
    }

    @Override
    public boolean isDirectory(String path) {
        return Files.isDirectory(getPath(path));
    }

    @Override
    public String[] list(String path) {
        try {
            return Files.list(getPath(path))
                .map(p -> p.getFileName().toString())
                .toArray(String[]::new);
        } catch (IOException e) {
            logger.severe("Failed to list directory: " + path);
            return new String[0];
        }
    }

    @Override
    public void copyFile(String sourcePath, String destinationPath) throws IOException {
        Files.copy(getPath(sourcePath), getPath(destinationPath),
            StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public InputStream openInputStream(String path) throws IOException {
        return Files.newInputStream(getPath(path));
    }

    @Override
    public OutputStream openOutputStream(String path) throws IOException {
        Path filePath = getPath(path);
        Files.createDirectories(filePath.getParent());
        return Files.newOutputStream(filePath);
    }
}
