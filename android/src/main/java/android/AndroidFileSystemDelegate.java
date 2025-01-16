package android;

import android.content.Context;
import android.util.Log;
import io.github.pokemeetup.FileSystemDelegate;
import io.github.pokemeetup.utils.GameLogger;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class AndroidFileSystemDelegate implements FileSystemDelegate {
    private static final String TAG = "AndroidFileSystem";
    private final Context context;
    private final String basePath;

    public AndroidFileSystemDelegate(Context context) {
        this.context = context;
        this.basePath = context.getFilesDir().getAbsolutePath();
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
    public OutputStream openOutputStream(String path) throws IOException {
        File file = getFile(path);
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try {
            return new FileOutputStream(file);
        } catch (IOException e) {
            GameLogger.info(TAG + "Error opening output stream: " + path + e);
            throw e;
        }
    }

    @Override
    public void createDirectory(String path) {
        File dir = getFile(path);
        if (!dir.exists() && !dir.mkdirs()) {
            GameLogger.info("Failed to create directory: " + path);
            throw new RuntimeException("Failed to create directory: " + path);
        }
        GameLogger.info("Created directory: " + path);
    }

    @Override
    public void writeString(String path, String content) throws IOException {
        File file = getFile(path);
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        try (FileOutputStream fos = new FileOutputStream(file);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            writer.write(content);
            writer.flush();
            GameLogger.info("Written to file: " + path);
        }
    }

    @Override
    public String[] list(String path) {
        File file = getFile(path);
        String[] files = file.list();
        if (files == null) {
            Log.w(TAG, "Directory listing returned null for: " + path);
            return new String[0];
        }
        Log.d(TAG, "Listed " + files.length + " files in: " + path);
        return files;
    }

    @Override
    public void copyFile(String sourcePath, String destinationPath) throws IOException {
        try (InputStream in = openInputStream(sourcePath);
             OutputStream out = openOutputStream(destinationPath)) {

            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
            out.flush();
            Log.d(TAG, "Copied file from " + sourcePath + " to " + destinationPath);
        } catch (IOException e) {
            Log.e(TAG, "Error copying file from " + sourcePath + " to " + destinationPath, e);
            throw e;
        }
    }

    @Override
    public InputStream openInputStream(String path) throws IOException {
        try {
            return new FileInputStream(getFile(path));
        } catch (IOException e) {
            Log.e(TAG, "Error opening input stream: " + path, e);
            throw e;
        }
    }

    private String readFromInternal(String path) throws IOException {
        File file = new File(basePath, path);
        if (!file.exists()) {
            throw new FileNotFoundException("File not found: " + path);
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            return readStreamToString(fis);
        }
    }

    @Override
    public String readString(String path) throws IOException {
        // First try reading from assets
        try {
            return readFromAssets(path);
        } catch (IOException e) {
            // If not in assets, try internal storage
            return readFromInternal(path);
        }
    }

    private String readFromAssets(String path) throws IOException {
        String[] variants = {
            path,
            path.toLowerCase(),
            path.replace("data/", "data/"),
            path.replace("/data/", "/data/"),
            path.substring(path.lastIndexOf("/") + 1)
        };

        IOException lastException = null;
        for (String variant : variants) {
            try (InputStream is = context.getAssets().open(variant)) {
                String content = readStreamToString(is);
                Log.d(TAG, "Successfully read asset: " + variant +
                    " (length: " + content.length() + ")");
                return content;
            } catch (IOException e) {
                lastException = e;
            }
        }

        throw lastException != null ? lastException :
            new IOException("Could not read from assets: " + path);
    }

    private String readBiomesJson() throws IOException {
        String[] biomePaths = {
                "data/biomes.json",
                "data/biomes.json",
            "biomes.json",
            "assets/Data/biomes.json"
        };

        for (String path : biomePaths) {
            try (InputStream is = context.getAssets().open(path)) {
                String content = readStreamToString(is);
                if (content.contains("\"type\":\"PLAINS\"")) {
                    Log.d(TAG, "Found valid biomes.json at " + path +
                        " (length: " + content.length() + ")");
                    return content;
                }
            } catch (IOException ignored) {
                Log.d(TAG, "Couldn't find biomes.json at " + path);
            }
        }

        throw new IOException("Could not find valid biomes.json");
    }


    private String readStreamToString(InputStream is) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int length;
        while ((length = is.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString(StandardCharsets.UTF_8);
    }

    private String normalizePath(String path) {
        // Remove any leading slashes or "assets/" prefix
        path = path.replaceFirst("^/+", "")
            .replaceFirst("^assets/", "");

        // Handle special directories like "Data"
        if (path.startsWith("data/")) {
            path = "data/" + path.substring(5);
        }

        return path;
    }

    private File getFile(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }
        return new File(basePath, path);
    }

    private String readInternalFile(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {

            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append('\n');
            }
            return content.toString();
        } catch (IOException e) {
            GameLogger.error("Error reading internal file: " + file.getPath() + " - " + e.getMessage());
            throw e;
        }
    }

    @Override
    public void deleteFile(String path) {
        File file = getFile(path);
        if (file.exists() && !file.delete()) {
            Log.e(TAG, "Failed to delete file: " + path);
            throw new RuntimeException("Failed to delete file: " + path);
        }
        Log.d(TAG, "Deleted file: " + path);
    }


    @Override
    public void deleteDirectory(String path) {
        File dir = getFile(path);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file.getName());
                    } else {
                        if (!file.delete()) {
                            Log.e(TAG, "Failed to delete file in directory: " + file.getPath());
                        }
                    }
                }
            }
            if (!dir.delete()) {
                Log.e(TAG, "Failed to delete directory: " + path);
                throw new RuntimeException("Failed to delete directory: " + path);
            }
            Log.d(TAG, "Deleted directory: " + path);
        }
    }

    @Override
    public boolean isDirectory(String path) {
        return getFile(path).isDirectory();
    }


    private String readAssetFile(String assetPath) throws IOException {
        String[] possiblePaths = {
            assetPath,
            assetPath.toLowerCase(),
            "assets/" + assetPath,
            assetPath.replace("data/", "data/"),
            assetPath.replace("data/", "data/")
        };

        for (String tryPath : possiblePaths) {
            try (InputStream is = context.getAssets().open(tryPath)) {
                GameLogger.info("Successfully found asset at: " + tryPath);
                return readStreamToString(is);
            } catch (IOException ignored) {
                // Try next path
            }
        }

        throw new IOException("Could not find asset: " + assetPath);
    }

    @Override
    public boolean exists(String path) {
        // Check internal storage first
        if (getFile(path).exists()) {
            return true;
        }

        // Then check assets
        try {
            String normalizedPath = normalizePath(path);
            context.getAssets().open(normalizedPath).close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
