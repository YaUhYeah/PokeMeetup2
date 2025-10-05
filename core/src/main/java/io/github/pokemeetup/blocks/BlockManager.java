package io.github.pokemeetup.blocks;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.data.ChestData;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.BlockTextureManager;

import java.util.Map;

public class BlockManager {
    private boolean initialized = false;


    public BlockManager() {
        GameLogger.info("Initialized BlockManager");
    }

    public PlaceableBlock getBlockAt(int tileX, int tileY) {
        Chunk chunk = GameContext.get().getWorld().getChunkAtTile(tileX, tileY);
        if (chunk == null) return null;
        Vector2 blockPos = new Vector2(tileX, tileY);
        return chunk.getBlock(blockPos);
    }


    public boolean placeBlock(PlaceableBlock.BlockType type, int tileX, int tileY) {
        int chunkX = Math.floorDiv(tileX, Chunk.CHUNK_SIZE);
        int chunkY = Math.floorDiv(tileY, Chunk.CHUNK_SIZE);
        Vector2 chunkPos = new Vector2(chunkX, chunkY);
        World world = GameContext.get().getWorld();
        Chunk chunk = world.getChunkAtTile(tileX, tileY);
        GameLogger.info("placeBlock called for tile (" + tileX + "," + tileY + ") in chunk (" + chunkX + "," + chunkY + "), chunk " + (chunk == null ? "NULL" : "exists"));
        if (chunk == null) {
            chunk = world.loadOrGenerateChunk(chunkPos);
            world.getChunks().put(chunkPos, chunk);
            GameLogger.info("Loaded/generated chunk for position (" + chunkX + "," + chunkY + ")");
        }
        Vector2 blockPos = new Vector2(tileX, tileY);
        if (chunk.getBlock(blockPos) != null) {
            GameLogger.info("Block already exists at position: " + blockPos);
            return false; // Can't place block on top of another block
        }
        PlaceableBlock block = new PlaceableBlock(type, blockPos, null, false);
        chunk.addBlock(block);
        chunk.setDirty(true); // Mark chunk as dirty for saving
        world.queueChunkSave(chunkPos);
        GameLogger.info("Placed block of type " + type + " at " + blockPos + ", queueing chunk save");
        world.invalidateRenderCaches();
        GameContext.get().getWorld().markYSortDirty();
        return true;
    }


    public void removeBlock(int tileX, int tileY) {
        int chunkX = Math.floorDiv(tileX, Chunk.CHUNK_SIZE);
        int chunkY = Math.floorDiv(tileY, Chunk.CHUNK_SIZE);
        Vector2 chunkPos = new Vector2(chunkX, chunkY);
        World world = GameContext.get().getWorld();
        Chunk chunk = world.getChunkAtTile(tileX, tileY);
        if (chunk == null) {
            GameLogger.info("Chunk not loaded at position: " + chunkPos);
            return;
        }

        Vector2 blockPos = new Vector2(tileX, tileY);
        PlaceableBlock block = chunk.getBlock(blockPos);
        if (block == null) {
            GameLogger.info("No block exists at position: " + blockPos);
            return; // Can't remove a block that doesn't exist
        }
        chunk.removeBlock(blockPos);
        chunk.setDirty(true); // Mark chunk as dirty for saving
        world.queueChunkSave(chunkPos);        world.invalidateRenderCaches();
        GameLogger.info("Removed block at " + blockPos);

    }

    public boolean isInitialized() {
        return initialized;
    }


    public boolean hasCollisionAt(int tileX, int tileY) {
        PlaceableBlock block = getBlockAt(tileX, tileY);
        return block != null && block.getType().hasCollision;
    }

    public void render(SpriteBatch batch, double worldTimeInMinutes, Rectangle viewBounds) {
        World world = GameContext.get().getWorld();
        if (world == null) return;

        // Use the reusable Rectangle object
        float buffer = World.TILE_SIZE;
        tempCullBounds.set(
            viewBounds.x - buffer,
            viewBounds.y - buffer,
            viewBounds.width + buffer * 2,
            viewBounds.height + buffer * 2
        );

        int minChunkX = (int) Math.floor(tempCullBounds.x / (World.CHUNK_SIZE * World.TILE_SIZE));
        int maxChunkX = (int) Math.floor((tempCullBounds.x + tempCullBounds.width) / (World.CHUNK_SIZE * World.TILE_SIZE));
        int minChunkY = (int) Math.floor(tempCullBounds.y / (World.CHUNK_SIZE * World.TILE_SIZE));
        int maxChunkY = (int) Math.floor((tempCullBounds.y + tempCullBounds.height) / (World.CHUNK_SIZE * World.TILE_SIZE));

        Color worldColor = world.getCurrentWorldColor();

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cy = minChunkY; cy <= maxChunkY; cy++) {
                // Use the reusable Vector2 object
                tempChunkPos.set(cx, cy);
                Chunk chunk = world.getChunks().get(tempChunkPos);
                if (chunk == null || chunk.getBlocks().isEmpty()) continue;

                // Debug: Log if this is chunk (-1, 1) which should have blocks
                if (cx == -1 && cy == 1 && !chunk.getBlocks().isEmpty()) {
                    GameLogger.info("Rendering chunk (-1,1) with " + chunk.getBlocks().size() + " blocks");
                }

                renderChunkBlocks(batch, chunk, tempCullBounds, worldTimeInMinutes, worldColor, world);
            }
        }
    }

    private final Rectangle tempCullBounds = new Rectangle();
    private final Vector2 tempChunkPos = new Vector2();

    private void renderChunkBlocks(SpriteBatch batch, Chunk chunk, Rectangle cullBounds,
                                   double worldTimeInMinutes, Color worldColor, World world) {

        // Batch state optimization
        Color originalColor = batch.getColor();

        for (PlaceableBlock block : chunk.getBlocks().values()) {
            // Update block animations (especially chest open/close)
            block.update(com.badlogic.gdx.Gdx.graphics.getDeltaTime());

            float tileX = block.getPosition().x * World.TILE_SIZE;
            float tileY = block.getPosition().y * World.TILE_SIZE;

            // Fine-grained culling
            if (!isBlockVisible(tileX, tileY, cullBounds)) continue;

            TextureRegion currentFrame = BlockTextureManager.getBlockFrame(block, (float) worldTimeInMinutes);
            if (currentFrame == null) continue;

            float blockWidth = currentFrame.getRegionWidth();
            float blockHeight = currentFrame.getRegionHeight();
            float offsetX = (World.TILE_SIZE - blockWidth) / 2;

            // Calculate color once
            Color blockColor = worldColor;
            Float lightLevel = world.getLightLevelAtTile(block.getPosition());
            if (lightLevel != null && lightLevel > 0) {
                // Reuse color object instead of creating new ones
                blockColor = tempColor.set(worldColor);
                blockColor.lerp(LIGHT_COLOR, lightLevel * 0.7f);
            }

            batch.setColor(blockColor);

            // Draw block
            if (block.isFlipped()) {
                batch.draw(currentFrame,
                    tileX + offsetX + blockWidth, tileY,
                    -blockWidth, blockHeight);
            } else {
                batch.draw(currentFrame,
                    tileX + offsetX, tileY,
                    blockWidth, blockHeight);
            }
        }

        batch.setColor(originalColor);
    }

    private boolean isBlockVisible(float tileX, float tileY, Rectangle bounds) {
        return !(tileX > bounds.x + bounds.width ||
            tileX + World.TILE_SIZE < bounds.x ||
            tileY > bounds.y + bounds.height ||
            tileY + World.TILE_SIZE < bounds.y);
    }

    // Add these as class fields
    private static final Color LIGHT_COLOR = new Color(1f, 0.8f, 0.6f, 1f);
    private final Color tempColor = new Color();

}
