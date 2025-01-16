package org.discord;

import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ServerWorldObjectManager {
    private final Map<Vector2, List<WorldObject>> objectsByChunk = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, NetworkProtocol.WorldObjectData> worldObjects = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> objectLocks = new ConcurrentHashMap<>(); // objectId -> playerId

    public void addObject(WorldObject object) {
        Vector2 chunkPos = getChunkPosition(object.getTileX(), object.getTileY());
        List<WorldObject> objects = objectsByChunk.computeIfAbsent(chunkPos, k -> new CopyOnWriteArrayList<>());
        objects.add(object);
    }

    /**
     * Attempts to lock a WorldObject for a specific player.
     *
     * @param objectId The ID of the WorldObject.
     * @param playerId The ID of the player attempting to lock.
     * @return True if the lock was successful, false otherwise.
     */
    public boolean lockObject(String objectId, String playerId) {
        return objectLocks.putIfAbsent(objectId, playerId) == null;
    }

    /**
     * Unlocks a WorldObject.
     *
     * @param objectId The ID of the WorldObject.
     * @param playerId The ID of the player attempting to unlock.
     * @return True if the unlock was successful, false otherwise.
     */
    public boolean unlockObject(String objectId, String playerId) {
        return objectLocks.remove(objectId, playerId);
    }

    /**
     * Checks if a WorldObject is currently locked.
     *
     * @param objectId The ID of the WorldObject.
     * @return True if locked, false otherwise.
     */
    public boolean isLocked(String objectId) {
        return objectLocks.containsKey(objectId);
    }

    /**
     * Retrieves the ID of the player who locked the WorldObject.
     *
     * @param objectId The ID of the WorldObject.
     * @return The playerId if locked, null otherwise.
     */
    public String getLocker(String objectId) {
        return objectLocks.get(objectId);
    }

    /**
     * Retrieves a WorldObjectData instance by its ID.
     *
     * @param objectId The ID of the WorldObject.
     * @return The WorldObjectData if exists, null otherwise.
     */
    public NetworkProtocol.WorldObjectData getObjectData(String objectId) {
        return worldObjects.get(objectId);
    }

    public boolean removeObject(String objectId) {
        for (List<WorldObject> objects : objectsByChunk.values()) {
            objects.removeIf(obj -> obj.getId().equals(objectId));
        }
        return false;
    }

    public List<WorldObject> getObjectsForChunk(Vector2 chunkPos) {
        return objectsByChunk.getOrDefault(chunkPos, Collections.emptyList());
    }

    private Vector2 getChunkPosition(int tileX, int tileY) {
        int chunkX = Math.floorDiv(tileX, Chunk.CHUNK_SIZE);
        int chunkY = Math.floorDiv(tileY, Chunk.CHUNK_SIZE);
        return new Vector2(chunkX, chunkY);
    }
}
