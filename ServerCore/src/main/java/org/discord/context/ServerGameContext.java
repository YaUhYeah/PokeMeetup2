
package org.discord.context;

import io.github.pokemeetup.multiplayer.server.ServerStorageSystem;
import org.discord.ServerWorldObjectManager;
import org.discord.utils.ServerWorldManager;

public final class ServerGameContext {
    private static ServerGameContext instance;
    private final ServerWorldManager worldManager;
    private final ServerStorageSystem storageSystem;
    private final ServerWorldObjectManager worldObjectManager;

    private ServerGameContext(ServerWorldManager worldManager,
                              ServerStorageSystem storageSystem,
                              ServerWorldObjectManager worldObjectManager) {
        this.worldManager = worldManager;
        this.storageSystem = storageSystem;
        this.worldObjectManager = worldObjectManager;
    }

    public static void init(ServerWorldManager worldManager,
                            ServerStorageSystem storageSystem,
                            ServerWorldObjectManager worldObjectManager) {
        if (instance != null) {
            throw new IllegalStateException("ServerGameContext already initialized!");
        }
        instance = new ServerGameContext(worldManager, storageSystem, worldObjectManager);
    }

    public static ServerGameContext get() {
        if (instance == null) {
            throw new IllegalStateException("ServerGameContext not initialized yet!");
        }
        return instance;
    }

    public ServerStorageSystem getStorageSystem() {
        return storageSystem;
    }

    public ServerWorldManager getWorldManager() {
        return worldManager;
    }

    public ServerWorldObjectManager getWorldObjectManager() {
        return worldObjectManager;
    }

    public void dispose() {
        if (worldManager != null) {
            worldManager.shutdown();
        }
        if (worldObjectManager != null) {
            worldObjectManager.cleanup();
        }
        instance = null;
    }
}
