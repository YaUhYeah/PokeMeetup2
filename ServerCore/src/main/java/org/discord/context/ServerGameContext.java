package org.discord.context;

import io.github.pokemeetup.multiplayer.server.ServerStorageSystem;
import io.github.pokemeetup.multiplayer.server.events.EventManager;
import io.github.pokemeetup.system.gameplay.inventory.ItemEntityManager;
import org.discord.GameServer;
import org.discord.ServerBlockManager;
import org.discord.ServerWorldObjectManager;
import org.discord.utils.ServerWorldManager;

public final class ServerGameContext {
    private static ServerGameContext instance;
    private final ServerWorldManager worldManager;
    private final ServerStorageSystem storageSystem;
    private final ServerBlockManager serverBlockManager;
    private final ServerWorldObjectManager worldObjectManager;
    private final ItemEntityManager itemEntityManager;
    private final EventManager eventManager;
    private GameServer gameServer;

    private ServerGameContext(ServerWorldManager worldManager,
                              ServerStorageSystem storageSystem,
                              ServerWorldObjectManager worldObjectManager,
                              ItemEntityManager itemEntityManager, ServerBlockManager serverBlockManager, GameServer gameServer
    , EventManager eventManager) {
        this.worldManager = worldManager;
        this.storageSystem = storageSystem;
        this.worldObjectManager = worldObjectManager;
        this.serverBlockManager = serverBlockManager;
        this.itemEntityManager = itemEntityManager;
        this.gameServer = gameServer;
        this.eventManager = eventManager;
    }

    public static void init(ServerWorldManager worldManager,
                            ServerStorageSystem storageSystem,
                            ServerWorldObjectManager worldObjectManager,
                            ItemEntityManager itemEntityManager, ServerBlockManager serverBlockManager, GameServer gameServer, EventManager eventManager) {
        if (instance != null) {
            throw new IllegalStateException("ServerGameContext already initialized!");
        }
        instance = new ServerGameContext(worldManager, storageSystem, worldObjectManager, itemEntityManager, serverBlockManager, gameServer,eventManager);
    }

    public static ServerGameContext get() {
        if (instance == null) {
            throw new IllegalStateException("ServerGameContext not initialized yet!");
        }
        return instance;
    }

    public GameServer getGameServer() {
        return gameServer;
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public void setGameServer(GameServer gameServer) {
        this.gameServer = gameServer;
    }

    public ItemEntityManager getItemEntityManager() {
        return itemEntityManager;
    }

    public ServerBlockManager getServerBlockManager() {
        return serverBlockManager;
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
