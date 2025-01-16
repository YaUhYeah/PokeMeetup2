package org.discord;

import com.badlogic.gdx.utils.Json;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class DeploymentHelper {
    public static void createServerDeployment(Path deploymentDir) throws IOException {
        System.out.println("Creating server deployment in: " + deploymentDir.toAbsolutePath());

        // Create directory structure
        createDirectory(deploymentDir);
        createDirectory(Paths.get(deploymentDir.toString(), "config"));
        createDirectory(Paths.get(deploymentDir.toString(), "plugins"));
        createDirectory(Paths.get(deploymentDir.toString(), "worlds"));
        createDirectory(Paths.get(deploymentDir.toString(), "logs"));
        createDirectory(Paths.get(deploymentDir.toString(), "server/data"));
       // Check if we're running from JAR or development environment
        Path serverJar;
        if (isRunningFromJar()) {
            // When running from JAR, use the current JAR
            serverJar = getCurrentJarPath();
            System.out.println("Running from JAR: " + serverJar);
        } else {
            // In development, look for the JAR in build directory
            serverJar = Paths.get("server.jar");
            System.out.println("Running in development mode, looking for: " + serverJar);
        }

        if (Files.exists(serverJar)) {
            Files.copy(serverJar, Paths.get(deploymentDir.toString(), "server.jar"));
            System.out.println("Copied server JAR successfully");
        } else {
            System.out.println("Warning: Server JAR not found at: " + serverJar);
            // Continue anyway as we might be in development mode
        }

        // Create configurations
        createDefaultConfig(deploymentDir);
        createBiomesConfig(deploymentDir);

        // Create start scripts
        createStartScripts(deploymentDir);

        // Make shell script executable on Unix
        Path startSh = Paths.get(deploymentDir.toString(), "start.sh");
        if (Files.exists(startSh)) {
            startSh.toFile().setExecutable(true);
        }

        // Create README
        createReadme(deploymentDir);

        System.out.println("Server deployment completed successfully");
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

    private static void createBiomesConfig(Path deploymentDir) throws IOException {
        // Create biomes configuration
        List<Map<String, Object>> biomes = new ArrayList<>();

        // Define each biome
        for (BiomeType type : BiomeType.values()) {
            Map<String, Object> biome = new HashMap<>();
            biome.put("name", type.name().toLowerCase());
            biome.put("type", type.name());
            biome.put("allowedTileTypes", Arrays.asList(1, 2, 3));

            // Create tile distribution
            Map<String, Double> distribution = new HashMap<>();
            switch (type) {
                case DESERT:
                    distribution.put("1", 85.0);
                    distribution.put("2", 10.0);
                    distribution.put("3", 5.0);
                    break;
                case FOREST:
                    distribution.put("1", 60.0);
                    distribution.put("2", 30.0);
                    distribution.put("3", 10.0);
                    break;
                case SNOW:
                    distribution.put("1", 75.0);
                    distribution.put("2", 20.0);
                    distribution.put("3", 5.0);
                    break;
                case HAUNTED:
                    distribution.put("1", 65.0);
                    distribution.put("2", 25.0);
                    distribution.put("3", 10.0);
                    break;
                default:
                    distribution.put("1", 70.0);
                    distribution.put("2", 20.0);
                    distribution.put("3", 10.0);
                    break;
            }
            biome.put("tileDistribution", distribution);

            // Add spawn configuration
            List<String> spawnableObjects = new ArrayList<>();
            Map<String, Double> spawnChances = new HashMap<>();

            switch (type) {
                case FOREST:
                    spawnableObjects.add("TREE");
                    spawnChances.put("TREE", 0.7);
                    break;
                case DESERT:
                    spawnableObjects.add("CACTUS");
                    spawnChances.put("CACTUS", 0.4);
                    break;
                case SNOW:
                    spawnableObjects.add("SNOW_TREE");
                    spawnChances.put("SNOW_TREE", 0.5);
                    break;
                case HAUNTED:
                    spawnableObjects.add("HAUNTED_TREE");
                    spawnChances.put("HAUNTED_TREE", 0.6);
                    break;
            }

            biome.put("spawnableObjects", spawnableObjects);
            biome.put("spawnChances", spawnChances);

            biomes.add(biome);
        }
        // Write biomes configuration to both server data and config directories
        Json json = new Json();
        String biomesJson = json.prettyPrint(biomes);

        // Save to server/data directory
        Path serverDataPath = Paths.get(deploymentDir.toString(), "config/biomes.json");
        Files.write(serverDataPath, biomesJson.getBytes(StandardCharsets.UTF_8));

        // Also save to config directory for reference
        Path configPath = Paths.get(deploymentDir.toString(), "config/biomes.json");
        Files.write(configPath, biomesJson.getBytes(StandardCharsets.UTF_8));

    }



    // Helper method to create both start scripts
    private static void createStartScripts(Path deploymentDir) throws IOException {
        // Create Windows batch file
        try {
            createWindowsScript(deploymentDir);
            System.out.println("Created start.bat successfully");
        } catch (IOException e) {
            throw new IOException("Failed to create start.bat: " + e.getMessage());
        }

        // Create Unix shell script
        try {
            createUnixScript(deploymentDir);
            System.out.println("Created start.sh successfully");
        } catch (IOException e) {
            throw new IOException("Failed to create start.sh: " + e.getMessage());
        }
    }

    // Create Windows batch script
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

    // Create Unix shell script
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

        // Make shell script executable
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
            true,
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
