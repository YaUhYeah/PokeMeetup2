package io.github.pokemeetup.multiplayer.client;

import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.multiplayer.server.config.ServerConfigManager;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.utils.GameLogger;


public class GameClientSingleton {
    private static final Object lock = new Object();
    private static GameClient instance;

    public static void resetInstance() {
        if (instance != null) {
            instance.dispose();
            instance = null;
        }
    }

    public static GameClient getInstance(ServerConnectionConfig config) {
        if (config == null) {
            config = ServerConfigManager.getDefaultServerConfig();
        }

        synchronized (lock) {
            try {
                validateConfig(config);

                if (instance != null) {
                    instance.dispose();
                    instance = null;
                }

                GameContext.get().setMultiplayer(true);
                instance = new GameClient(config);

                return instance;

            } catch (Exception e) {
                GameLogger.error("Failed to initialize GameClient: " + e.getMessage());
                if (instance != null) {
                    instance.dispose();
                    instance = null;
                }
                throw new RuntimeException("Failed to initialize GameClient: " + e.getMessage(), e);
            }
        }
    }

    private static void validateConfig(ServerConnectionConfig config) {
        if (config.getServerIP() == null || config.getServerIP().isEmpty()) {
            throw new IllegalArgumentException("Server IP cannot be null or empty");
        }
        if (config.getTcpPort() <= 0) {
            throw new IllegalArgumentException("Invalid TCP port: " + config.getTcpPort());
        }
        if (config.getUdpPort() <= 0) {
            throw new IllegalArgumentException("Invalid UDP port: " + config.getUdpPort());
        }
    }

    public static synchronized GameClient getSinglePlayerInstance(Player player) {
        synchronized (lock) {
            try {
                if (instance != null) {
                    instance.dispose();
                    instance = null;
                }
                ServerConnectionConfig singlePlayerConfig = ServerConnectionConfig.getDefault();
                GameContext.get().setMultiplayer(false);
                instance = new GameClient(singlePlayerConfig);
                instance.setActivePlayer(player);
                return instance;
            } catch (Exception e) {
                GameLogger.error("Error disposing GameClient: " + e.getMessage());
                throw new RuntimeException("Failed to initialize single player GameClient", e);

            }
        }
    }

    public static synchronized GameClient getSinglePlayerInstance() {
        synchronized (lock) {
            try {
                if (instance != null) {
                    instance.dispose();
                    instance = null;
                }

                ServerConnectionConfig singlePlayerConfig = ServerConnectionConfig.getDefault();
                GameContext.get().setMultiplayer(false);
                instance = new GameClient(singlePlayerConfig);
                return instance;

            } catch (Exception e) {
                GameLogger.error("Failed to create single player GameClient: " + e.getMessage());
                throw new RuntimeException("Failed to initialize single player GameClient", e);
            }
        }
    }

    public static void clearInstance() {
        synchronized (lock) {
            if (instance != null) {
                try {
                    instance.dispose();
                } catch (Exception e) {
                    GameLogger.error("Error disconnecting GameClient: " + e.getMessage());
                }
                instance = null;
            }
        }
    }


}
