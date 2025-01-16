package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.utils.ChunkPos;

import java.util.*;

import static io.github.pokemeetup.system.gameplay.overworld.World.TILE_SIZE;

public class ChunkManager {
    public static final int CHUNK_SIZE = 16;
    public static final float CHUNK_PIXEL_SIZE = CHUNK_SIZE * TILE_SIZE;
    private static final int LOAD_RADIUS = 3;
    public static final float VISIBILITY_BUFFER = 2f * CHUNK_PIXEL_SIZE;

    public static boolean isChunkVisible(ChunkPos chunkPos, Rectangle viewBounds) {
        // Convert chunk position to world coordinates
        float chunkWorldX = chunkPos.x * CHUNK_PIXEL_SIZE;
        float chunkWorldY = chunkPos.y * CHUNK_PIXEL_SIZE;

        // Create chunk bounds with buffer for smoother loading
        Rectangle chunkBounds = new Rectangle(
            chunkWorldX - VISIBILITY_BUFFER,
            chunkWorldY - VISIBILITY_BUFFER,
            CHUNK_PIXEL_SIZE + (VISIBILITY_BUFFER * 2),
            CHUNK_PIXEL_SIZE + (VISIBILITY_BUFFER * 2)
        );

        return viewBounds.overlaps(chunkBounds);
    }




    public static Rectangle calculateViewBounds(float playerX, float playerY, float viewportWidth, float viewportHeight) {
        // Add buffer zones for smoother loading
        float bufferZone = World.TILE_SIZE * 4;
        return new Rectangle(
            playerX - (viewportWidth / 2) - bufferZone,
            playerY - (viewportHeight / 2) - bufferZone,
            viewportWidth + (bufferZone * 2),
            viewportHeight + (bufferZone * 2)
        );
    }

    public static Set<Vector2> getChunksToLoad(Vector2 playerPosition, Rectangle viewBounds) {
        Set<Vector2> chunksNeeded = new HashSet<>();

        // Calculate chunk coordinates from view bounds
        int minChunkX = Math.floorDiv((int)viewBounds.x, World.CHUNK_SIZE * World.TILE_SIZE);
        int maxChunkX = Math.floorDiv((int)(viewBounds.x + viewBounds.width), World.CHUNK_SIZE * World.TILE_SIZE);
        int minChunkY = Math.floorDiv((int)viewBounds.y, World.CHUNK_SIZE * World.TILE_SIZE);
        int maxChunkY = Math.floorDiv((int)(viewBounds.y + viewBounds.height), World.CHUNK_SIZE * World.TILE_SIZE);

        // Add an extra radius for smoother transitions
        int extraRadius = 2;
        for (int x = minChunkX - extraRadius; x <= maxChunkX + extraRadius; x++) {
            for (int y = minChunkY - extraRadius; y <= maxChunkY + extraRadius; y++) {
                chunksNeeded.add(new Vector2(x, y));
            }
        }

        return chunksNeeded;
    }

}
