package org.discord;

import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.system.data.ChestData;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import org.discord.context.ServerGameContext;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ServerBlockManager {
    // Map storing block placements keyed by world tile position.
    private final Map<Vector2, PlaceableBlock> placedBlocks = new ConcurrentHashMap<>();

    /**
     * Places a block at the given tile coordinates.
     * In addition to storing the block in our internal map, we also determine the chunk
     * that covers the tile, add the block to that chunk, and mark the chunk as dirty.
     *
     * @param type      The type of block to place.
     * @param tileX     The world X tile coordinate.
     * @param tileY     The world Y tile coordinate.
     * @param isFlipped Whether the block should be rendered flipped.
     * @return true if placement succeeded; false otherwise.
     */
    public boolean placeBlock(PlaceableBlock.BlockType type, int tileX, int tileY, boolean isFlipped) {
        Vector2 pos = new Vector2(tileX, tileY);
        // Prevent placement if a block already exists at this tile.
        if (placedBlocks.containsKey(pos)) {
            return false;
        }
        // Create the block.
        PlaceableBlock block = new PlaceableBlock(type, pos);
        if (isFlipped) {
            block.toggleFlip();
        }

        // If the block is a chest, initialize its chest data.
        if (type == PlaceableBlock.BlockType.CHEST) {
            // Create a new ChestData for this chest block.
            ChestData chestData = new ChestData(tileX, tileY);
            block.setChestData(chestData);
        }

        placedBlocks.put(pos, block);

        // Now update the chunk data.
        int chunkX = Math.floorDiv(tileX, World.CHUNK_SIZE);
        int chunkY = Math.floorDiv(tileY, World.CHUNK_SIZE);

        // Here we assume the world name is "multiplayer_world" (adjust as needed).
        Chunk chunk = ServerGameContext.get().getWorldManager().loadChunk("multiplayer_world", chunkX, chunkY);
        if (chunk != null) {
            chunk.addBlock(block);
            chunk.setDirty(true);
        }
        return true;
    }


    /**
     * Returns the block at the given world tile position.
     *
     * @param pos the tile position as a Vector2.
     * @return the PlaceableBlock at that position, or null if none exists.
     */
    public PlaceableBlock getBlockAt(Vector2 pos) {
        return placedBlocks.get(pos);
    }

    /**
     * Returns the block at the given world tile coordinates.
     *
     * @param tileX the world X tile coordinate.
     * @param tileY the world Y tile coordinate.
     * @return the PlaceableBlock at that position, or null if none exists.
     */
    public PlaceableBlock getBlockAt(int tileX, int tileY) {
        return getBlockAt(new Vector2(tileX, tileY));
    }

    public PlaceableBlock getChestBlock(UUID chestId) {
        for (PlaceableBlock block : placedBlocks.values()) {
            if (block.getType() == PlaceableBlock.BlockType.CHEST && block.getChestData() != null) {
                if (block.getChestData().chestId.equals(chestId)) {
                    return block;
                }
            }
        }
        return null;
    }

    /**
     * Removes a block at the specified tile coordinates.
     * Also removes the block from the corresponding chunk and marks the chunk dirty.
     *
     * @param tileX The world X tile coordinate.
     * @param tileY The world Y tile coordinate.
     */
    public void removeBlock(int tileX, int tileY) {
        Vector2 pos = new Vector2(tileX, tileY);
        // If there is no block at the specified position, do nothing.
        if (!placedBlocks.containsKey(pos)) {
            return;
        }
        placedBlocks.remove(pos);

        // Update the corresponding chunk.
        int chunkX = Math.floorDiv(tileX, World.CHUNK_SIZE);
        int chunkY = Math.floorDiv(tileY, World.CHUNK_SIZE);
        Chunk chunk = ServerGameContext.get().getWorldManager().loadChunk("multiplayer_world", chunkX, chunkY);
        if (chunk != null) {
            chunk.removeBlock(pos);
            chunk.setDirty(true);
        }
    }

    public Map<Vector2, PlaceableBlock> getPlacedBlocks() {
        return placedBlocks;
    }
}
