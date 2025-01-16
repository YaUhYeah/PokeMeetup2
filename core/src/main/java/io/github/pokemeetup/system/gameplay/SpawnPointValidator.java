package io.github.pokemeetup.system.gameplay;

import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.utils.textures.TileType;

import java.util.List;

public class SpawnPointValidator {
    private static final int SPAWN_CHECK_RADIUS = 5;

    public static Vector2 findValidSpawnPoint(World world, int startX, int startY) {
        int radius = 0;
        int maxRadius = 100;
        while (radius < maxRadius) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    int checkX = startX + dx;
                    int checkY = startY + dy;

                    if (isValidSpawnLocation(world, checkX, checkY)) {
                        if (hasEnoughClearSpace(world, checkX, checkY)) {
                            return new Vector2(checkX, checkY);
                        }
                    }
                }
            }
            radius++;
        }
        return new Vector2(World.DEFAULT_X_POSITION, World.DEFAULT_Y_POSITION);
    }

    private static boolean isValidSpawnLocation(World world, int x, int y) {
        if (!world.isPassable(x, y)) {
            return false;
        }
        Chunk chunk = world.getChunkAtPosition(x, y);
        if (chunk != null) {
            int localX = Math.floorMod(x, Chunk.CHUNK_SIZE);
            int localY = Math.floorMod(y, Chunk.CHUNK_SIZE);
            int tileType = chunk.getTileType(localX, localY);

            if (TileType.isMountainTile(tileType) && !TileType.isPassableMountainTile(tileType)) {
                return false;
            }
        }
        List<WorldObject> nearbyObjects = world.getObjectManager().getObjectsNearPosition(
            x * World.TILE_SIZE,
            y * World.TILE_SIZE
        );

        for (WorldObject obj : nearbyObjects) {
            if (obj.getBoundingBox().contains(x * World.TILE_SIZE, y * World.TILE_SIZE)) {
                return false;
            }
        }

        return true;
    }

    private static boolean hasEnoughClearSpace(World world, int centerX, int centerY) {
        for (int dx = -SPAWN_CHECK_RADIUS; dx <= SPAWN_CHECK_RADIUS; dx++) {
            for (int dy = -SPAWN_CHECK_RADIUS; dy <= SPAWN_CHECK_RADIUS; dy++) {
                if (!isValidSpawnLocation(world, centerX + dx, centerY + dy)) {
                    return false;
                }
            }
        }
        return true;
    }
}
