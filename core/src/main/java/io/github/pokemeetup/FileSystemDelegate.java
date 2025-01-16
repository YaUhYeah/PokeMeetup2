package io.github.pokemeetup;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface FileSystemDelegate {
    boolean exists(String path);
    void createDirectory(String path);
    void writeString(String path, String content) throws IOException;
    String readString(String path) throws IOException;
    void deleteFile(String path);
    void deleteDirectory(String path);
    boolean isDirectory(String path); void moveFile(String sourcePath, String destinationPath) throws IOException;
    String[] list(String path);
    void copyFile(String sourcePath, String destinationPath) throws IOException;
    InputStream openInputStream(String path) throws IOException;
    OutputStream openOutputStream(String path) throws IOException;
}
