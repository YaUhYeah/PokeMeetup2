package org.discord.context;

import org.discord.utils.ServerWorldManager;

public final class ServerGameContext {
    private static ServerGameContext instance;
    private final ServerWorldManager worldManager;

    private ServerGameContext(ServerWorldManager worldManager) {
        this.worldManager = worldManager;
    }

    public static void init(ServerWorldManager worldManager) {
        if (instance != null) {
            throw new IllegalStateException("ServerGameContext already initialized!");
        }
        instance = new ServerGameContext(worldManager);
    }

    public static ServerGameContext get() {
        if (instance == null) {
            throw new IllegalStateException("ServerGameContext not initialized yet!");
        }
        return instance;
    }

    public ServerWorldManager getWorldManager() {
        return worldManager;
    }

    public void dispose() {
        if (worldManager != null) {
            worldManager.shutdown();
        }
        instance = null;
    }
}
