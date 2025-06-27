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
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                Vector2 chunkPos = new Vector2(x, y);
                List<WorldObject> objects = worldData.getChunkObjects().get(chunkPos);
                if (objects != null) {
                    logger.info(String.format("Chunk (%d, %d) contains %d objects", x, y, objects.size()));
                    for (WorldObject obj : objects) {
                        if (obj != null) {
                            logger.fine(String.format("- %s at (%d,%d)",
                                obj.getType(), obj.getTileX(), obj.getTileY()));
                        }
                    }
                }
            }
        }

        logger.info("Initial spawn chunks generated");
        serverWorldManager.saveWorld(worldData);
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
