package org.discord;

import com.badlogic.gdx.math.Vector2;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.pokemeetup.multiplayer.server.ServerStorageSystem;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import io.github.pokemeetup.multiplayer.server.events.EventManager;
import io.github.pokemeetup.system.data.WorldData;
import io.github.pokemeetup.system.gameplay.inventory.ItemEntityManager;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.utils.storage.GameFileSystem;
import org.discord.context.ServerGameContext;
import org.discord.files.ServerFileDelegate;
import org.discord.utils.ServerWorldManager;
import org.h2.tools.Server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Logger;

import static io.github.pokemeetup.CreatureCaptureGame.MULTIPLAYER_WORLD_NAME;

public class ServerLauncher {
    private static final String DEFAULT_ICON = "server-icon.png";
    private static final String DEFAULT_MOTD = "Basic and default server description fr!";
    private static final Logger logger = Logger.getLogger(ServerLauncher.class.getName());
    private static final Path SERVER_ROOT = Paths.get(".");
    public static ServerStorageSystem storage;

    public static void main(String[] args) {
        Server h2Server = null;
        try {
            logger.info("Initializing server deployment...");
            DeploymentHelper.createServerDeployment(SERVER_ROOT);
            logger.info("Server deployment initialized");
            GameFileSystem.getInstance().setDelegate(new ServerFileDelegate());
            logger.info("Server file system initialized");
            h2Server = startH2Server();
            ServerConnectionConfig config = loadServerConfig();
            logger.info("Server configuration loaded");
            storage = new ServerStorageSystem();
            logger.info("Storage system initialized");
            ServerWorldManager serverWorldManager = ServerWorldManager.getInstance(storage);
            logger.info("World manager initialized");
            ServerWorldObjectManager worldObjectManager = new ServerWorldObjectManager();
            worldObjectManager.initializeWorld(MULTIPLAYER_WORLD_NAME);
            ServerGameContext.init(serverWorldManager, storage, worldObjectManager, new ItemEntityManager(), new ServerBlockManager(), null, new EventManager());
            logger.info("Server game context initialized");
            WorldData worldData = serverWorldManager.loadWorld("multiplayer_world");
            if (worldData == null) {
                logger.info("No existing world; creating new multiplayer world...");
                long seed = System.currentTimeMillis();
                worldData = serverWorldManager.createWorld("multiplayer_world", seed, 0.15f, 0.05f);
            }
            logger.info("World loaded – warming up spawn area chunks");
            generateInitialChunks(serverWorldManager, worldData);
            GameServer server = new GameServer(config);
            server.start();
            ServerGameContext.get().setGameServer(server);
            logger.info("Game server started successfully");
            addShutdownHook(server, h2Server);

        } catch (Exception e) {
            logger.severe("Failed to start server: " + e.getMessage());
            e.printStackTrace();
            if (h2Server != null) {
                h2Server.stop();
            }
            System.exit(1);
        }
    }



    private static void generateInitialChunks(ServerWorldManager serverWorldManager, WorldData worldData) {
        logger.info("Generating initial spawn chunks...");
        int radius = 2;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                try {
                    Chunk chunk = serverWorldManager.loadChunk("multiplayer_world", x, y);
                    if (chunk != null) {
                        serverWorldManager.saveChunk("multiplayer_world", chunk);
                        logger.info(String.format("Generated chunk at (%d, %d)", x, y));
                    }
                } catch (Exception e) {
                    logger.warning(String.format("Failed to generate chunk at (%d, %d): %s", x, y, e.getMessage()));
                }
            }
        }


        logger.info("Initial spawn chunks generated");
        serverWorldManager.saveWorld(worldData);
    }

    private static Server startH2Server() throws Exception {
        // This path is now consistent with the unified data directory.
        Server h2Server = Server.createTcpServer(
            "-tcpPort", "9101",
            "-tcpAllowOthers",
            "-ifNotExists",
            "-baseDir", "./data"
        ).start();

        if (h2Server.isRunning(true)) {
            logger.info("H2 Database Server started on port 9101");
        }
        return h2Server;
    }
    private static ServerConnectionConfig loadServerConfig() throws IOException {
        Path configDir = SERVER_ROOT.resolve("config");
        Path configFile = configDir.resolve("server.json");

        // Load defaults or from file
        ServerConnectionConfig config;
        try {
            if (!configFile.toFile().exists()) {
                logger.info("Configuration file not found, creating default config");
                Files.createDirectories(configDir);
                config = createDefaultConfig();
                saveDefaultConfig(configFile, config);
            } else {
                Gson gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .serializeNulls()
                    .create();

                String jsonContent = Files.readString(configFile);
                config = gson.fromJson(jsonContent, ServerConnectionConfig.class);
                logger.info("Configuration loaded from file");
            }
        } catch (Exception e) {
            logger.warning("Error loading config: " + e.getMessage() + ". Using defaults.");
            config = createDefaultConfig();
        }

        // Override with environment variables (for Pterodactyl compatibility)
        applyEnvironmentVariables(config);

        // Ensure server icon exists
        ensureServerIcon();

        config.setServerIP("0.0.0.0"); // Always bind to all interfaces
        logger.info("Server configuration: " + config.getServerName() +
                   " | TCP: " + config.getTcpPort() +
                   " | UDP: " + config.getUdpPort() +
                   " | Max Players: " + config.getMaxPlayers());

        return config;
    }

    private static ServerConnectionConfig createDefaultConfig() {
        ServerConnectionConfig config = new ServerConnectionConfig(
            "0.0.0.0",
            54555,
            54556,
            "Pokemon Meetup Server",
            100
        );
        config.setMotd(DEFAULT_MOTD);
        config.setIconPath(DEFAULT_ICON);
        config.setVersion("1.0.0");
        return config;
    }

    private static void saveDefaultConfig(Path configFile, ServerConnectionConfig config) {
        try {
            Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .serializeNulls()
                .create();

            String jsonContent = gson.toJson(config);
            Files.writeString(configFile, jsonContent);
            logger.info("Default configuration saved to: " + configFile);
        } catch (Exception e) {
            logger.warning("Failed to save default config: " + e.getMessage());
        }
    }
    private static void applyEnvironmentVariables(ServerConnectionConfig config) {
        // Server Name
        String serverName = System.getenv("GAME_NAME");
        if (serverName != null && !serverName.isEmpty()) {
            config.setServerName(serverName);
            logger.info("Server name set from environment: " + serverName);
        }

        // MOTD (Message of the Day)
        String motd = System.getenv("GAME_MOTD");
        if (motd != null && !motd.isEmpty()) {
            config.setMotd(motd);
            logger.info("MOTD set from environment");
        }

        // TCP Port (Pterodactyl provides SERVER_PORT automatically)
        String tcpPort = System.getenv("SERVER_PORT");
        if (tcpPort != null && !tcpPort.isEmpty()) {
            try {
                int port = Integer.parseInt(tcpPort);
                config.setTcpPort(port);
                config.setUdpPort(port + 1);
                logger.info("Ports set from environment - TCP: " + port + ", UDP: " + (port + 1));
            } catch (NumberFormatException e) {
                logger.warning("Invalid SERVER_PORT value: " + tcpPort);
            }
        }

        // Max Players
        String maxPlayers = System.getenv("MAX_PLAYERS");
        if (maxPlayers != null && !maxPlayers.isEmpty()) {
            try {
                config.setMaxPlayers(Integer.parseInt(maxPlayers));
                logger.info("Max players set from environment: " + maxPlayers);
            } catch (NumberFormatException e) {
                logger.warning("Invalid max players value: " + maxPlayers);
            }
        }

        // Version
        String version = System.getenv("GAME_VERSION");
        if (version != null && !version.isEmpty()) {
            config.setVersion(version);
            logger.info("Version set from environment: " + version);
        }
    }

    private static void ensureServerIcon() {
        Path iconPath = SERVER_ROOT.resolve(DEFAULT_ICON);
        if (!Files.exists(iconPath)) {
            try (InputStream is = ServerLauncher.class.getResourceAsStream("/assets/default-server-icon.png")) {
                if (is != null) {
                    Files.copy(is, iconPath);
                    logger.info("Default server icon created");
                }
            } catch (Exception e) {
                logger.warning("Could not create default server icon: " + e.getMessage());
            }
        }
    }
    private static void addShutdownHook(GameServer server, Server h2Server) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down server...");
            try {
                server.shutdown();
                logger.info("Game server stopped");

                storage.shutdown();

                if (h2Server != null) {
                    h2Server.stop();
                    logger.info("Database server stopped");
                }

            } catch (Exception e) {
                logger.severe("Error during shutdown: " + e.getMessage());
            }
        }));
    }
}
