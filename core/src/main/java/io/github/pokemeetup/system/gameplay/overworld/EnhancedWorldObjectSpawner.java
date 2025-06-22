package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TileType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Refined world object spawning system with proper spacing
 * and balanced vegetation distribution.
 */
public class EnhancedWorldObjectSpawner {

    // Increased minimum spacing between trees
    private static final float MIN_TREE_SPACING = 3.5f;

    // Higher spacing for specific large trees
    private static final float APRICORN_TREE_SPACING = 4.0f;

    // Buffer around chunk edges to prevent cross-chunk overlap
    private static final int CHUNK_EDGE_BUFFER = 1;

    /**
     * Spawns world objects in the chunk with improved spacing and distribution.
     */
    public static List<WorldObject> spawnWorldObjects(Chunk chunk, int[][] tiles, long worldSeed) {
        List<WorldObject> spawned = new ArrayList<>();
        Random rng = new Random(worldSeed + (chunk.getChunkX() * 31L) ^ (chunk.getChunkY() * 1337L));
        Biome biome = chunk.getBiome();
        List<WorldObject.ObjectType> spawnable = biome.getSpawnableObjects();

        if (spawnable == null || spawnable.isEmpty()) {
            GameLogger.error("Biome " + biome.getName() + " returned no spawnable objects.");
            return spawned;
        }

        // First pass: spawn trees with strict spacing rules
        spawnTreeObjects(spawnable, biome, chunk, tiles, rng, spawned);

        // Second pass: spawn other objects including appropriately balanced tall grass
        spawnNonTreeObjects(spawnable, biome, chunk, tiles, rng, spawned);

        // Log summary of what was spawned
        int treeCount = 0, grassCount = 0, otherCount = 0;
        for (WorldObject obj : spawned) {
            if (isTreeType(obj.getType())) treeCount++;
            else if (isTallGrassType(obj.getType())) grassCount++;
            else otherCount++;
        }

        GameLogger.info(String.format("Spawned objects in chunk (%d,%d): %d trees, %d grass, %d other objects",
            chunk.getChunkX(), chunk.getChunkY(), treeCount, grassCount, otherCount));

        return spawned;
    }

    /**
     * Spawns tree objects with strict spacing requirements
     */
    private static void spawnTreeObjects(List<WorldObject.ObjectType> spawnable,
                                         Biome biome,
                                         Chunk chunk,
                                         int[][] tiles,
                                         Random rng,
                                         List<WorldObject> spawned) {

        // Filter for tree object types
        List<WorldObject.ObjectType> treeTypes = new ArrayList<>();
        for (WorldObject.ObjectType type : spawnable) {
            if (isTreeType(type)) {
                treeTypes.add(type);
            }
        }

        if (treeTypes.isEmpty()) return;

        // Calculate tree density (reduced from original)
        float totalTreeChance = 0;
        for (WorldObject.ObjectType type : treeTypes) {
            totalTreeChance += biome.getSpawnChanceForObject(type);
        }

        // More conservative density multiplier
        float densityMultiplier = 0.6f;
        int treeAttempts = Math.round(Chunk.CHUNK_SIZE * Chunk.CHUNK_SIZE * totalTreeChance * densityMultiplier);

        // Track tree placements in a virtual grid for quick collision detection
        boolean[][] treeGrid = new boolean[Chunk.CHUNK_SIZE + 6][Chunk.CHUNK_SIZE + 6];

        // Try to place each tree
        for (int i = 0; i < treeAttempts; i++) {
            // Pick a random tree type based on weighted probabilities
            WorldObject.ObjectType selectedType = selectRandomTreeType(treeTypes, biome, rng);
            if (selectedType == null) continue;

            // Try multiple positions to find a valid placement
            for (int attempt = 0; attempt < 15; attempt++) {
                // Keep trees away from chunk edges to prevent cross-chunk overlap
                int lx = CHUNK_EDGE_BUFFER + rng.nextInt(Chunk.CHUNK_SIZE - (2 * CHUNK_EDGE_BUFFER));
                int ly = CHUNK_EDGE_BUFFER + rng.nextInt(Chunk.CHUNK_SIZE - (2 * CHUNK_EDGE_BUFFER));

                // Skip if position already has a tree (quick check)
                if (treeGrid[lx + 3][ly + 3]) continue;

                // Perform thorough spacing check
                if (canPlaceTreeAt(lx, ly, selectedType, chunk, tiles, spawned, biome, treeGrid)) {
                    int worldTileX = chunk.getChunkX() * Chunk.CHUNK_SIZE + lx;
                    int worldTileY = chunk.getChunkY() * Chunk.CHUNK_SIZE + ly;

                    WorldObject tree = new WorldObject(worldTileX, worldTileY, null, selectedType);
                    tree.ensureTexture();
                    spawned.add(tree);

                    // Mark tree location and surrounding area in grid
                    markTreeInGrid(lx, ly, selectedType, treeGrid);

                    // Only add a limited number of trees per chunk
                    if (countTreesOfType(spawned, selectedType) >= getMaxTreesOfType(selectedType, biome)) {
                        break;
                    }

                    break;
                }
            }
        }
    }

    /**
     * Marks tree position and buffer zone in the grid
     */
    private static void markTreeInGrid(int lx, int ly, WorldObject.ObjectType type, boolean[][] grid) {
        int treeWidth = getTreeWidth(type);
        int treeHeight = getTreeHeight(type);

        int bufferSize = (type == WorldObject.ObjectType.APRICORN_TREE) ? 5 : 4;

        for (int dx = -bufferSize; dx < treeWidth + bufferSize; dx++) {
            for (int dy = -bufferSize; dy < treeHeight + bufferSize; dy++) {
                int gx = lx + dx + 3;
                int gy = ly + dy + 3;

                if (gx >= 0 && gx < grid.length && gy >= 0 && gy < grid[0].length) {
                    grid[gx][gy] = true;
                }
            }
        }
    }

    /**
     * Returns the maximum number of trees of a specific type to spawn per chunk
     */
    private static int getMaxTreesOfType(WorldObject.ObjectType type, Biome biome) {
        // Limit number of trees based on type and biome
        if (type == WorldObject.ObjectType.APRICORN_TREE) {
            return 2; // Limit to 2 apricorn trees per chunk
        } else if (biome.getType() == BiomeType.FOREST || biome.getType() == BiomeType.RAIN_FOREST) {
            return 6; // More trees in forest biomes
        } else {
            return 4; // Default for other biomes
        }
    }

    /**
     * Count trees of a specific type in the spawned list
     */
    private static int countTreesOfType(List<WorldObject> spawned, WorldObject.ObjectType type) {
        int count = 0;
        for (WorldObject obj : spawned) {
            if (obj.getType() == type) {
                count++;
            }
        }
        return count;
    }

    /**
     * Spawns non-tree objects with balanced distribution
     */
    private static void spawnNonTreeObjects(List<WorldObject.ObjectType> spawnable,
                                            Biome biome,
                                            Chunk chunk,
                                            int[][] tiles,
                                            Random rng,
                                            List<WorldObject> spawned) {

        // Track tall grass count to limit total amount
        int tallGrassCount = 0;
        int maxTallGrassPerChunk = calculateMaxGrassPerChunk(biome);

        for (WorldObject.ObjectType type : spawnable) {
            if (isTreeType(type)) continue; // Skip trees, already handled

            // Adjusted multipliers - reduced for tall grass but still more than original
            float multiplier;
            if (isTallGrassType(type)) {
                multiplier = 0.4f; // Reduced from 0.85f to create a more balanced amount
                if (tallGrassCount >= maxTallGrassPerChunk) continue;
            } else {
                multiplier = 0.35f; // Other objects stay the same
            }

            double spawnChance = biome.getSpawnChanceForObject(type) * multiplier;
            int attempts = (int) (Chunk.CHUNK_SIZE * Chunk.CHUNK_SIZE * spawnChance);

            for (int i = 0; i < attempts; i++) {
                int lx = rng.nextInt(Chunk.CHUNK_SIZE);
                int ly = rng.nextInt(Chunk.CHUNK_SIZE);

                // Pass rng to canPlaceObjectAt
                if (canPlaceObjectAt(lx, ly, type, chunk, tiles, spawned, biome, rng)) {
                    int worldTileX = chunk.getChunkX() * Chunk.CHUNK_SIZE + lx;
                    int worldTileY = chunk.getChunkY() * Chunk.CHUNK_SIZE + ly;

                    WorldObject object = new WorldObject(worldTileX, worldTileY, null, type);
                    object.ensureTexture();
                    spawned.add(object);

                    if (isTallGrassType(type)) {
                        tallGrassCount++;
                        if (tallGrassCount >= maxTallGrassPerChunk) break;
                    }
                }
            }
        }
    }

    /**
     * Calculate maximum grass objects per chunk based on biome
     */
    private static int calculateMaxGrassPerChunk(Biome biome) {
        switch (biome.getType()) {
            case PLAINS:
                return 45; // Moderately grassy
            case FOREST:
            case RAIN_FOREST:
                return 35; // Still grassy but not overwhelming
            case DESERT:
                return 15; // Sparse grass in desert
            case SNOW:
                return 20; // Limited snow grass
            default:
                return 30; // Default for other biomes
        }
    }

    /**
     * Checks if a tree can be placed at the given location with strict spacing rules
     */
    private static boolean canPlaceTreeAt(int localX, int localY,
                                          WorldObject.ObjectType type,
                                          Chunk chunk,
                                          int[][] tiles,
                                          List<WorldObject> existingObjects,
                                          Biome biome,
                                          boolean[][] treeGrid) {

        // Basic terrain check
        int tileType = chunk.getTileType(localX, localY);
        if (!biome.getAllowedTileTypes().contains(tileType)) return false;
        if (!chunk.isPassable(localX, localY)) return false;

        // Skip water and beach tiles
        if (tileType == TileType.WATER || tileType == TileType.BEACH_SAND) return false;

        // Convert to world coordinates
        int worldTileX = chunk.getChunkX() * Chunk.CHUNK_SIZE + localX;
        int worldTileY = chunk.getChunkY() * Chunk.CHUNK_SIZE + localY;

        // Get tree dimensions
        int treeWidth = getTreeWidth(type);
        int treeHeight = getTreeHeight(type);

        // Strict spacing check for the entire tree footprint
        float minSpacing = (type == WorldObject.ObjectType.APRICORN_TREE) ?
            APRICORN_TREE_SPACING : MIN_TREE_SPACING;

        // Check for existing objects with strict spacing
        for (WorldObject existing : existingObjects) {
            if (!isTreeType(existing.getType())) continue;

            float distX = Math.abs(existing.getTileX() - worldTileX);
            float distY = Math.abs(existing.getTileY() - worldTileY);

            // Calculate required spacing based on tree types
            float requiredSpacing = minSpacing +
                (getTreeWidth(existing.getType()) + treeWidth) / 2.0f;

            // If too close to another tree, reject placement
            if (distX < requiredSpacing && distY < requiredSpacing) {
                return false;
            }
        }

        // Check if the entire tree footprint is on valid terrain
        for (int dx = -1; dx <= treeWidth; dx++) {
            for (int dy = -1; dy <= treeHeight; dy++) {
                int checkX = localX + dx;
                int checkY = localY + dy;

                // Skip if outside chunk bounds
                if (checkX < 0 || checkX >= Chunk.CHUNK_SIZE ||
                    checkY < 0 || checkY >= Chunk.CHUNK_SIZE) {
                    continue;
                }

                int checkTileType = chunk.getTileType(checkX, checkY);

                // Check for invalid placement tiles
                if (checkTileType == TileType.WATER ||
                    checkTileType == TileType.BEACH_SAND ||
                    !chunk.isPassable(checkX, checkY)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Checks if a non-tree object can be placed at the given location.
     *
     * @param rng The random number generator to use for pattern creation
     */
    private static boolean canPlaceObjectAt(int localX, int localY,
                                            WorldObject.ObjectType type,
                                            Chunk chunk,
                                            int[][] tiles,
                                            List<WorldObject> existingObjects,
                                            Biome biome,
                                            Random rng) {

        // Basic terrain check
        int tileType = chunk.getTileType(localX, localY);
        if (!biome.getAllowedTileTypes().contains(tileType)) return false;
        if (!chunk.isPassable(localX, localY)) return false;

        // Special case for tall grass
        if (isTallGrassType(type)) {
            // Skip water but allow more flexible placement
            if (tileType == TileType.WATER) return false;

            // Don't place tall grass too close to trees
            int worldTileX = chunk.getChunkX() * Chunk.CHUNK_SIZE + localX;
            int worldTileY = chunk.getChunkY() * Chunk.CHUNK_SIZE + localY;

            for (WorldObject existing : existingObjects) {
                if (isTreeType(existing.getType())) {
                    float distX = Math.abs(existing.getTileX() - worldTileX);
                    float distY = Math.abs(existing.getTileY() - worldTileY);

                    // Keep grass away from tree trunks
                    if (distX < 1.0f && distY < 1.0f) {
                        return false;
                    }
                }

                // Don't stack grass on itself - each position has at most one grass
                if (isTallGrassType(existing.getType()) &&
                    existing.getTileX() == worldTileX &&
                    existing.getTileY() == worldTileY) {
                    return false;
                }
            }

            // Apply a simple random pattern to make grass less uniform
            return rng.nextFloat() < 0.65f;
        }

        // For other objects, use standard placement logic
        if (tileType == TileType.WATER || tileType == TileType.BEACH_SAND) return false;

        int worldTileX = chunk.getChunkX() * Chunk.CHUNK_SIZE + localX;
        int worldTileY = chunk.getChunkY() * Chunk.CHUNK_SIZE + localY;

        // Check for collision with other objects
        for (WorldObject existing : existingObjects) {
            float distX = Math.abs(existing.getTileX() - worldTileX);
            float distY = Math.abs(existing.getTileY() - worldTileY);

            // Simple spacing check for non-tree objects
            if (distX < 1.0f && distY < 1.0f) {
                return false;
            }
        }

        return true;
    }

    /**
     * Selects a random tree type based on weighted probabilities.
     */
    private static WorldObject.ObjectType selectRandomTreeType(List<WorldObject.ObjectType> trees,
                                                               Biome biome,
                                                               Random rng) {
        if (trees.isEmpty()) return null;

        float totalWeight = 0;
        for (WorldObject.ObjectType type : trees) {
            totalWeight += biome.getSpawnChanceForObject(type);
        }

        if (totalWeight <= 0) return trees.get(0);

        float roll = rng.nextFloat() * totalWeight;
        float current = 0;

        for (WorldObject.ObjectType type : trees) {
            current += biome.getSpawnChanceForObject(type);
            if (roll < current) {
                return type;
            }
        }

        return trees.get(0);
    }

    /**
     * Determines if the given object type is a tree.
     */
    private static boolean isTreeType(WorldObject.ObjectType type) {
        return type == WorldObject.ObjectType.TREE_0 ||
            type == WorldObject.ObjectType.TREE_1 ||
            type == WorldObject.ObjectType.SNOW_TREE ||
            type == WorldObject.ObjectType.HAUNTED_TREE ||
            type == WorldObject.ObjectType.RUINS_TREE ||
            type == WorldObject.ObjectType.APRICORN_TREE ||
            type == WorldObject.ObjectType.RAIN_TREE ||
            type == WorldObject.ObjectType.CHERRY_TREE ||
            type == WorldObject.ObjectType.BEACH_TREE;
    }

    /**
     * Determines if the given object type is tall grass.
     */
    private static boolean isTallGrassType(WorldObject.ObjectType type) {
        return type == WorldObject.ObjectType.TALL_GRASS ||
            type == WorldObject.ObjectType.TALL_GRASS_2 ||
            type == WorldObject.ObjectType.TALL_GRASS_3 ||
            type == WorldObject.ObjectType.FOREST_TALL_GRASS ||
            type == WorldObject.ObjectType.HAUNTED_TALL_GRASS ||
            type == WorldObject.ObjectType.RAIN_FOREST_TALL_GRASS ||
            type == WorldObject.ObjectType.DESERT_TALL_GRASS ||
            type == WorldObject.ObjectType.SNOW_TALL_GRASS ||
            type == WorldObject.ObjectType.RUINS_TALL_GRASS;
    }

    /**
     * Gets the width of a tree in tiles.
     */
    private static int getTreeWidth(WorldObject.ObjectType type) {
        if (type == WorldObject.ObjectType.APRICORN_TREE) {
            return 3; // 3x3 tree
        } else if (isTreeType(type)) {
            return 2; // 2x3 tree
        } else {
            return 1; // Default size
        }
    }

    /**
     * Gets the height of a tree in tiles.
     */
    private static int getTreeHeight(WorldObject.ObjectType type) {
        if (isTreeType(type)) {
            return 3; // Trees are 3 tiles tall
        } else {
            return 1; // Default size
        }
    }
}
