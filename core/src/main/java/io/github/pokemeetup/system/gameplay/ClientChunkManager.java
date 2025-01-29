package io.github.pokemeetup.system.gameplay;

import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.system.gameplay.ClientChunkData;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class ClientChunkManager {
    private final Map<Vector2, ClientChunkData> chunkMap = new ConcurrentHashMap<>();

    public ClientChunkManager() {
    }

    public void processChunkData(NetworkProtocol.ChunkData serverChunkData) {
        Vector2 chunkPos = new Vector2(serverChunkData.chunkX, serverChunkData.chunkY);

        ClientChunkData clientData = new ClientChunkData();
        clientData.chunkPos  = chunkPos;
        clientData.biomeType = serverChunkData.biomeType;
        clientData.tileData  = serverChunkData.tileData;
        clientData.blockData = serverChunkData.blockData;
        if (serverChunkData.worldObjects != null) {
            for (Map<String, Object> objMap : serverChunkData.worldObjects) {
                WorldObject wo = new WorldObject();
                wo.updateFromData(objMap);
                clientData.worldObjects.add(wo);
            }
        }

        // Store in the manager
        chunkMap.put(chunkPos, clientData);
    }

    /** Returns null if the chunk isn’t loaded/stored yet. */
    public ClientChunkData getChunkData(Vector2 chunkPos) {
        return chunkMap.get(chunkPos);
    }

    public boolean hasChunk(Vector2 chunkPos) {
        return chunkMap.containsKey(chunkPos);
    }

    /**
     * Example method for removing chunk data if you want to unload or
     * free memory after some time.
     */
    public void unloadChunk(Vector2 chunkPos) {
        chunkMap.remove(chunkPos);
    }

    public Map<Vector2, ClientChunkData> getAllChunks() {
        return chunkMap;
    }
}
