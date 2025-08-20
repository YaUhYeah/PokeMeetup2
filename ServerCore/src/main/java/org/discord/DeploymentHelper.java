// src/main/java/org/discord/DeploymentHelper.java

package org.discord;

import com.badlogic.gdx.utils.Json;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class DeploymentHelper {
    public static void createServerDeployment(Path deploymentDir) throws IOException {
        System.out.println("Creating server deployment in: " + deploymentDir.toAbsolutePath());
        createDirectory(deploymentDir);
        createDirectory(Paths.get(deploymentDir.toString(), "config"));
        createDirectory(Paths.get(deploymentDir.toString(), "plugins"));

        // Consolidated data directory
        Path dataDir = Paths.get(deploymentDir.toString(), "data");
        createDirectory(dataDir);
        createDirectory(dataDir.resolve("worlds"));

        Path serverJar;
        if (isRunningFromJar()) {
            serverJar = getCurrentJarPath();
            System.out.println("Running from JAR: " + serverJar);
        } else {
            serverJar = Paths.get("build/libs/server.jar"); // Adjusted path for dev environment
            System.out.println("Running in development mode, looking for: " + serverJar);
        }

        if (Files.exists(serverJar)) {
            Files.copy(serverJar, Paths.get(deploymentDir.toString(), "server.jar"), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Copied server JAR successfully");
        } else {
            System.out.println("Warning: Server JAR not found at: " + serverJar.toAbsolutePath());
        }

        createDefaultConfig(deploymentDir);
        copyBiomeDataFile(deploymentDir); // Fix for biome loading issue
        createStartScripts(deploymentDir);

        Path startSh = Paths.get(deploymentDir.toString(), "start.sh");
        if (Files.exists(startSh)) {
            startSh.toFile().setExecutable(true);
        }
        createReadme(deploymentDir);

        System.out.println("Server deployment completed successfully");
    }

    private static void copyBiomeDataFile(Path deploymentDir) throws IOException {
        Path dataDir = deploymentDir.resolve("Data");
        if (!Files.exists(dataDir)) {
            Files.createDirectories(dataDir);
        }
        Path targetFile = dataDir.resolve("biomes.json");

        // This is the path we expect inside the JAR, based on your screenshot showing "assets" as the resource root.
        String resourcePath = "/Data/biomes.json";
        System.out.println("--- Biome Loading Diagnostic ---");
        System.out.println("Attempting to copy resource from JAR path: " + resourcePath);

        InputStream is = null;

        // Attempt 1: Standard ClassLoader
        System.out.println("Attempt 1: Using DeploymentHelper.class.getResourceAsStream...");
        is = DeploymentHelper.class.getResourceAsStream(resourcePath);

        if (is == null) {
            System.out.println("Attempt 1 FAILED. Trying Thread's ContextClassLoader...");
            // Attempt 2: Thread Context ClassLoader (often more reliable in complex environments)
            // Note: Context ClassLoader does not want the leading slash.
            String contextPath = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
            is = Thread.currentThread().getContextClassLoader().getResourceAsStream(contextPath);
            if (is != null) {
                System.out.println("Attempt 2 SUCCEEDED.");
            }
        } else {
            System.out.println("Attempt 1 SUCCEEDED.");
        }

        if (is == null) {
            String errorMessage = String.format(
                "FATAL: Biome file could not be found inside the JAR using multiple methods.%n" +
                    "Path tried: '%s'.%n" +
                    "This confirms the file is NOT being packaged correctly into server.jar.%n" +
                    "Please run './gradlew checkCoreResources' and verify its output.",
                resourcePath
            );
            throw new IOException(errorMessage);
        }

        try (InputStream finalIs = is) {
            Files.copy(finalIs, targetFile, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Successfully copied biomes.json to: " + targetFile.toAbsolutePath());
        } catch (IOException e) {
            throw new IOException("Failed to copy biomes.json from JAR to filesystem: " + e.getMessage(), e);
        } finally {
            System.out.println("--- End Biome Loading Diagnostic ---");
        }
    }
    private static boolean isRunningFromJar() {
        String className = DeploymentHelper.class.getName().replace('.', '/');
        String classJar = DeploymentHelper.class.getResource("/" + className + ".class").toString();
        return classJar.startsWith("jar:");
    }

    private static Path getCurrentJarPath() {
        try {
            return Paths.get(DeploymentHelper.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception e) {
            return Paths.get("server.jar");
        }
    }

    private static void createDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
            System.out.println("Created directory: " + dir);
        }
    }
    private static void createStartScripts(Path deploymentDir) throws IOException {
        try {
            createWindowsScript(deploymentDir);
            System.out.println("Created start.bat successfully");
        } catch (IOException e) {
            throw new IOException("Failed to create start.bat: " + e.getMessage());
        }
        try {
            createUnixScript(deploymentDir);
            System.out.println("Created start.sh successfully");
        } catch (IOException e) {
            throw new IOException("Failed to create start.sh: " + e.getMessage());
        }
    }
    private static void createWindowsScript(Path deploymentDir) throws IOException {
        String batScript =
            "@echo off\n" +
                "setlocal enabledelayedexpansion\n\n" +
                ":: Set Java path if needed\n" +
                "set JAVA_HOME=\n" +
                "if defined JAVA_HOME (\n" +
                "    set JAVA=\"%JAVA_HOME%/bin/java\"\n" +
                ") else (\n" +
                "    set JAVA=java\n" +
                ")\n\n" +
                ":: Set memory options\n" +
                "set MIN_MEMORY=1G\n" +
                "set MAX_MEMORY=4G\n\n" +
                ":: Start server\n" +
                "echo Starting Pokemon Meetup Server...\n" +
                "%JAVA% -Xms%MIN_MEMORY% -Xmx%MAX_MEMORY% -jar server.jar\n" +
                "pause\n";

        Path batPath = Paths.get(deploymentDir.toString(), "start.bat");
        Files.write(batPath, batScript.getBytes(StandardCharsets.UTF_8));
    }
    private static void createUnixScript(Path deploymentDir) throws IOException {
        String shScript =
            "#!/bin/bash\n\n" +
                "# Set Java path if needed\n" +
                "if [ -n \"$JAVA_HOME\" ]; then\n" +
                "    JAVA=\"$JAVA_HOME/bin/java\"\n" +
                "else\n" +
                "    JAVA=\"java\"\n" +
                "fi\n\n" +
                "# Set memory options\n" +
                "MIN_MEMORY=\"1G\"\n" +
                "MAX_MEMORY=\"4G\"\n\n" +
                "# Start server\n" +
                "echo \"Starting Pokemon Meetup Server...\"\n" +
                "$JAVA -Xms$MIN_MEMORY -Xmx$MAX_MEMORY -jar server.jar\n";

        Path shPath = Paths.get(deploymentDir.toString(), "start.sh");
        Files.write(shPath, shScript.getBytes(StandardCharsets.UTF_8));
        try {
            shPath.toFile().setExecutable(true);
        } catch (SecurityException e) {
            System.out.println("Warning: Could not make start.sh executable: " + e.getMessage());
        }
    }

    private static void createDefaultConfig(Path deploymentDir) throws IOException {
        ServerConnectionConfig config = new ServerConnectionConfig(
            "0.0.0.0",
            54555,
            54556,
            "Pokemon Meetup Server",
            100
        );

        Json json = new Json();
        Path configFile = Paths.get(deploymentDir.toString(), "config/server.json");
        Files.write(configFile, Arrays.asList(json.prettyPrint(config).split("\n")), StandardCharsets.UTF_8);
    }

    private static void createReadme(Path deploymentDir) throws IOException {
        String readme =
            "Pokemon Meetup Server\n" +
                "====================\n\n" +
                "Quick Start:\n" +
                "1. Edit config/server.json to configure your server\n" +
                "2. On Windows: Run start.bat\n" +
                "   On Linux/Mac: Run ./start.sh\n" +
                "3. Server will create necessary directories on first run\n\n" +
                "Plugins:\n" +
                "- Place plugin .jar files in the plugins directory\n" +
                "- Server will load plugins automatically on startup\n\n" +
                "Configuration:\n" +
                "- Server settings: config/server.json\n" +
                "- Plugin configs: config/<plugin-id>.json\n\n" +
                "Logs:\n" +
                "- Server logs are stored in the logs directory\n\n" +
                "Support:\n" +
                "- Issues: https://github.com/yourusername/pokemon-meetup/issues\n" +
                "- Wiki: https://github.com/yourusername/pokemon-meetup/wiki\n";

        Path readmeFile = Paths.get(deploymentDir.toString(), "README.md");
        Files.write(readmeFile, Arrays.asList(readme.split("\n")), StandardCharsets.UTF_8);
    }
}
