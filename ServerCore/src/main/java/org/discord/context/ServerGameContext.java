package org.discord.context;

import io.github.pokemeetup.multiplayer.server.ServerStorageSystem;
import io.github.pokemeetup.system.gameplay.overworld.World;
import org.discord.utils.ServerWorldManager;

public final class ServerGameContext {
    private static ServerGameContext instance;
    private final ServerWorldManager worldManager;
    private final ServerStorageSystem storageSystem;


    private ServerGameContext(ServerWorldManager worldManager, ServerStorageSystem storageSystem) {
        this.worldManager = worldManager;
        this.storageSystem = storageSystem;
    }

    public static void init(ServerWorldManager worldManager, ServerStorageSystem storageSystem) {
        if (instance != null) {
            throw new IllegalStateException("ServerGameContext already initialized!");
        }
        instance = new ServerGameContext(worldManager, storageSystem);
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

    public void dispose() {
        if (worldManager != null) {
            worldManager.shutdown();
        }
        instance = null;
    }
}
