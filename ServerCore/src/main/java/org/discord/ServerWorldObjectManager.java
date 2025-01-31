package org.discord;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.utils.GameLogger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ServerWorldObjectManager {
    private static final float MIN_TREE_SPACING = 4.0f;
    private static final float OBJECT_DENSITY = 0.015f;
    private final Map<String, Map<Vector2, List<WorldObject>>> worldObjectsByWorld = new ConcurrentHashMap<>();
    private final Map<Vector2, Long> lastChunkAccess = new ConcurrentHashMap<>();

    public void initializeWorld(String worldName) {
        worldObjectsByWorld.putIfAbsent(worldName, new ConcurrentHashMap<>());
    }

    public List<WorldObject> getObjectsForChunk(String worldName, Vector2 chunkPos) {
        Map<Vector2, List<WorldObject>> worldObjects = worldObjectsByWorld.get(worldName);
        if (worldObjects == null) return new ArrayList<>();

        List<WorldObject> objects = worldObjects.get(chunkPos);
        lastChunkAccess.put(chunkPos, System.currentTimeMillis());
        return objects != null ? objects : new ArrayList<>();
    }

    public void removeObject(String worldName, Vector2 chunkPos, String objectId) {
        Map<Vector2, List<WorldObject>> worldObjects = worldObjectsByWorld.get(worldName);
        if (worldObjects == null) return;

        List<WorldObject> objects = worldObjects.get(chunkPos);
        if (objects != null) {
            objects.removeIf(obj -> obj.getId().equals(objectId));
            if (objects.isEmpty()) {
                worldObjects.remove(chunkPos);
            }
            GameLogger.info("Removed object " + objectId + " from chunk " + chunkPos);
        }
    }
    public List<WorldObject> generateObjectsForChunk(String worldName, Vector2 chunkPos, Chunk chunk) {
        List<WorldObject> objects = new ArrayList<>();
        if (chunk == null) return objects;

        Random rand = new Random(chunk.getGenerationSeed());

        try {
            for (WorldObject.ObjectType objType : chunk.getBiome().getSpawnableObjects()) {
                double baseChance = chunk.getBiome().getSpawnChanceForObject(objType);
                // Reduce spawn chance based on object type
                double adjustedChance = adjustSpawnChance(baseChance, objType);

                // Generate objects with adjusted density
                for (int lx = 0; lx < Chunk.CHUNK_SIZE; lx++) {
                    for (int ly = 0; ly < Chunk.CHUNK_SIZE; ly++) {
                        if (rand.nextDouble() < adjustedChance * OBJECT_DENSITY) {
                            int tileType = chunk.getTileType(lx, ly);
                            if (!chunk.getBiome().getAllowedTileTypes().contains(tileType)) {
                                continue;
                            }

                            int wTileX = chunk.getChunkX() * Chunk.CHUNK_SIZE + lx;
                            int wTileY = chunk.getChunkY() * Chunk.CHUNK_SIZE + ly;

                            if (canPlaceObject(wTileX, wTileY, objType, objects)) {
                                WorldObject obj = createObject(objType, wTileX, wTileY);
                                if (obj != null) {
                                    objects.add(obj);
                                    GameLogger.info("Generated " + objType + " at (" + wTileX + "," + wTileY + ")");
                                }
                            }
                        }
                    }
                }
            }

            // Update world storage
            worldObjectsByWorld.computeIfAbsent(worldName, k -> new ConcurrentHashMap<>())
                .put(chunkPos, new ArrayList<>(objects));

            return objects;

        } catch (Exception e) {
            GameLogger.error("Error generating objects for chunk: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private double adjustSpawnChance(double baseChance, WorldObject.ObjectType type) {
        if (isTreeType(type)) {
            return baseChance * 0.4f; // Reduce tree density significantly
        } else if (type == WorldObject.ObjectType.POKEBALL) {
            return baseChance * 0.7f; // Moderate reduction for Pokeballs
        }
        return baseChance;
    }

    private boolean canPlaceObject(int worldTileX, int worldTileY, WorldObject.ObjectType type, List<WorldObject> existingObjects) {
        float requiredSpacing = getRequiredSpacing(type);

        // Check spacing against existing objects
        for (WorldObject existing : existingObjects) {
            float dx = Math.abs(existing.getTileX() - worldTileX);
            float dy = Math.abs(existing.getTileY() - worldTileY);
            float minDistance = getMinDistanceBetweenTypes(type, existing.getType());

            if (dx <= minDistance && dy <= minDistance) {
                return false;
            }
        }

        return true;
    }

    private float getRequiredSpacing(WorldObject.ObjectType type) {
        if (isTreeType(type)) {
            return MIN_TREE_SPACING;
        } else if (type == WorldObject.ObjectType.POKEBALL) {
            return 3.0f;
        }
        return 2.0f;
    }

    private float getMinDistanceBetweenTypes(WorldObject.ObjectType type1, WorldObject.ObjectType type2) {
        // Larger spacing between trees
        if (isTreeType(type1) && isTreeType(type2)) {
            return MIN_TREE_SPACING;
        }
        // Moderate spacing between trees and other objects
        if (isTreeType(type1) || isTreeType(type2)) {
            return 3.0f;
        }
        // Default spacing for other objects
        return 2.0f;
    }

    public WorldObject createObject(WorldObject.ObjectType type, int tileX, int tileY) {
        try {
            // Initialize basic object data
            WorldObject obj = new WorldObject(tileX, tileY, null, type);
            obj.setId(UUID.randomUUID().toString());
            obj.ensureTexture();
            return obj;
        } catch (Exception e) {
            GameLogger.error("Error creating object: " + e.getMessage());
            return null;
        }
    }

    private boolean canPlaceObject(Chunk chunk, int localX, int localY, List<WorldObject> existingObjects) {
        if (chunk == null) return false;

        int worldTileX = chunk.getChunkX() * Chunk.CHUNK_SIZE + localX;
        int worldTileY = chunk.getChunkY() * Chunk.CHUNK_SIZE + localY;

        // Check tile type compatibility
        int tileType = chunk.getTileType(localX, localY);
        if (!chunk.getBiome().getAllowedTileTypes().contains(tileType)) {
            return false;
        }

        // Check elevation restrictions
        int band = chunk.getElevationBands()[localX][localY];

        // Check spacing against existing objects
        Rectangle newObjBounds = new Rectangle(
            worldTileX * World.TILE_SIZE,
            worldTileY * World.TILE_SIZE,
            World.TILE_SIZE,
            World.TILE_SIZE
        );

        for (WorldObject existing : existingObjects) {
            if (existing == null) continue;

            Rectangle existingBounds = existing.getCollisionBox();
            if (existingBounds != null) {
                // Add padding around objects
                float padding = World.TILE_SIZE * 0.5f;
                existingBounds.x -= padding;
                existingBounds.y -= padding;
                existingBounds.width += padding * 2;
                existingBounds.height += padding * 2;

                if (existingBounds.overlaps(newObjBounds)) {
                    return false;
                }
            }
        }

        // Add specific checks for trees
        if (isTreeType(WorldObject.ObjectType.TREE_0)) {
            // Trees can't spawn on higher elevation bands
            if (band >= 1) {
                return false;
            }

            // Check tree spacing
            for (WorldObject obj : existingObjects) {
                if (obj != null && isTreeType(obj.getType())) {
                    float dx = Math.abs(obj.getTileX() - worldTileX);
                    float dy = Math.abs(obj.getTileY() - worldTileY);
                    if (dx <= 2 && dy <= 2) {  // Minimum 2 tile spacing between trees
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private boolean isTreeType(WorldObject.ObjectType type) {
        return type == WorldObject.ObjectType.TREE_0 ||
            type == WorldObject.ObjectType.TREE_1 ||
            type == WorldObject.ObjectType.SNOW_TREE ||
            type == WorldObject.ObjectType.HAUNTED_TREE ||
            type == WorldObject.ObjectType.RAIN_TREE ||
            type == WorldObject.ObjectType.RUINS_TREE ||
            type == WorldObject.ObjectType.APRICORN_TREE;
    }


    public void setObjectsForChunk(String worldName, Vector2 chunkPos, List<WorldObject> objects) {
        try {
            if (objects == null) {
                worldObjectsByWorld.computeIfAbsent(worldName, k -> new ConcurrentHashMap<>())
                    .remove(chunkPos);
                return;
            }

            // Create a safe copy of objects
            List<WorldObject> safeObjects = new CopyOnWriteArrayList<>();
            for (WorldObject obj : objects) {
                if (obj != null) {
                    if (obj.getId() == null) {
                        obj.setId(UUID.randomUUID().toString());
                    }
                    obj.ensureTexture();
                    safeObjects.add(obj);
                }
            }

            // Update storage
            worldObjectsByWorld.computeIfAbsent(worldName, k -> new ConcurrentHashMap<>())
                .put(chunkPos, safeObjects);

            GameLogger.info("Updated " + safeObjects.size() + " objects in chunk " + chunkPos +
                " for world " + worldName);

        } catch (Exception e) {
            GameLogger.error("Error setting chunk objects: " + e.getMessage());
        }
    }
    public void clearWorld(String worldName) {
        worldObjectsByWorld.remove(worldName);
        GameLogger.info("Cleared all objects for world: " + worldName);
    }

    public List<WorldObject> getObjectsNearPosition(String worldName, float worldX, float worldY, float radius) {
        int chunkX = (int) Math.floor(worldX / (World.CHUNK_SIZE * World.TILE_SIZE));
        int chunkY = (int) Math.floor(worldY / (World.CHUNK_SIZE * World.TILE_SIZE));

        List<WorldObject> nearbyObjects = new ArrayList<>();
        Map<Vector2, List<WorldObject>> worldObjects = worldObjectsByWorld.get(worldName);
        if (worldObjects == null) return nearbyObjects;

        // Check current and adjacent chunks
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                Vector2 chunkPos = new Vector2(chunkX + dx, chunkY + dy);
                List<WorldObject> chunkObjects = worldObjects.get(chunkPos);
                if (chunkObjects != null) {
                    for (WorldObject obj : chunkObjects) {
                        float dx2 = obj.getPixelX() - worldX;
                        float dy2 = obj.getPixelY() - worldY;
                        if (Math.sqrt(dx2 * dx2 + dy2 * dy2) <= radius) {
                            nearbyObjects.add(obj);
                        }
                    }
                }
            }
        }

        return nearbyObjects;
    }

    public void cleanup() {
        worldObjectsByWorld.clear();
        lastChunkAccess.clear();
    }
}
