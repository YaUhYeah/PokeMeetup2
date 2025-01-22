package org.discord;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.multiplayer.server.ServerStorageSystem;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import io.github.pokemeetup.system.gameplay.overworld.World;
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
import java.util.logging.Logger;

public class ServerLauncher {
    private static final String DEFAULT_ICON = "server-icon.png";
    private static final String DEFAULT_MOTD = "Basic and default server description fr!";
    private static final Logger logger = Logger.getLogger(ServerLauncher.class.getName());
    private static final Path SERVER_ROOT = Paths.get(".");
    public static ServerStorageSystem storage;

    public static void main(String[] args) {
        Server h2Server = null;
        try {
            // Initialize server deployment
            logger.info("Initializing server deployment...");
            DeploymentHelper.createServerDeployment(SERVER_ROOT);
            logger.info("Server deployment initialized");

            // Initialize file system
            GameFileSystem.getInstance().setDelegate(new ServerFileDelegate());
            logger.info("Server file system initialized");

            // Start H2 Database Server
            h2Server = startH2Server();

            // Load server configuration
            ServerConnectionConfig config = loadServerConfig();
            logger.info("Server configuration loaded");

            // Initialize storage and world management
            storage = new ServerStorageSystem();
            logger.info("World management system initialized");
            ServerWorldManager serverWorldManager = ServerWorldManager.getInstance(storage);
            serverWorldManager.loadWorld("multiplayer_world");
            ServerGameContext.init(serverWorldManager,storage);
            GameServer server = new GameServer(config);
            server.start();
            logger.info("Game server started successfully");

            // Add shutdown hook
            addShutdownHook(server, h2Server);

        } catch (Exception e) {
            logger.severe("Failed to start server: " + e.getMessage());
            if (h2Server != null) {
                h2Server.stop();
            }
            System.exit(1);
        }
    }

    private static Server startH2Server() throws Exception {
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

        try {
            if (!configFile.toFile().exists()) {
                logger.info("Configuration not found, loading defaults");
                return new ServerConnectionConfig(
                    "0.0.0.0",
                    54555,
                    54556,
                    "Pokemon Meetup Server",
                    true,
                    100
                );
            }

            Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .serializeNulls()
                .create();

            String jsonContent = Files.readString(configFile);
            ServerConnectionConfig config = gson.fromJson(jsonContent, ServerConnectionConfig.class);
            config.setServerIP("0.0.0.0");

            return config;
        } catch (Exception e) {
            Path iconPath = SERVER_ROOT.resolve(DEFAULT_ICON);
            if (!Files.exists(iconPath)) {
                // Copy default icon from resources
                try (InputStream is = ServerLauncher.class.getResourceAsStream("/assets/default-server-icon.png")) {
                    if (is != null) {
                        Files.copy(is, iconPath);
                    }
                }
            }
            logger.warning("Error loading config: " + e.getMessage() + ". Using defaults.");
            ServerConnectionConfig config = new ServerConnectionConfig(
                "0.0.0.0",
                54555,
                54556,
                "Pokemon Meetup Server",
                true,
                100
            );
            config.setMotd(DEFAULT_MOTD);
            config.setIconPath(DEFAULT_ICON);
            return config;
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
