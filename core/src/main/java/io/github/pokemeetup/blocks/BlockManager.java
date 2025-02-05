package io.github.pokemeetup.blocks;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.data.ChestData;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.BlockTextureManager;

public class BlockManager {
    private final World world;
    private boolean initialized = false;


    public BlockManager(World world) {
        this.world = world;
        GameLogger.info("Initialized BlockManager");
    }

    public boolean placeBlockFromPlayer(PlaceableBlock.BlockType type, Player player, World world) {
        if (type == null || player == null || world == null) {
            GameLogger.error("Invalid parameters for player block placement");
            return false;
        }

        Vector2 targetPos = calculateTargetPosition(player);
        int targetX = (int) targetPos.x;
        int targetY = (int) targetPos.y;

        GameLogger.info("Checking placement - Player(" + player.getTileX() + "," + player.getTileY() +
            ") Target(" + targetX + "," + targetY + ") Dir:" + player.getDirection());

        if (!isValidPlacement(targetX, targetY, world)) {
            return false;
        }

        if (world.getGameClient() != null && GameContext.get().isMultiplayer()) {
            NetworkProtocol.BlockPlacement placement = new NetworkProtocol.BlockPlacement();
            placement.username = player.getUsername();
            placement.blockTypeId = type.id;
            placement.tileX = targetX;
            placement.tileY = targetY;
            placement.action = NetworkProtocol.BlockAction.PLACE;
            world.getGameClient().sendBlockPlacement(placement);
        }
        return placeBlock(type, targetX, targetY);
    }

    private Vector2 calculateTargetPosition(Player player) {
        int targetX = player.getTileX();
        int targetY = player.getTileY();

        switch (player.getDirection()) {
            case "up":
                targetY++;
                break;
            case "down":
                targetY--;
                break;
            case "left":
                targetX--;
                break;
            case "right":
                targetX++;
                break;
        }

        return new Vector2(targetX, targetY);
    }


    public PlaceableBlock getBlockAt(int worldX, int worldY) {
        Chunk chunk = world.getChunkAtPosition(worldX, worldY);
        if (chunk == null) return null;
        Vector2 blockPos = new Vector2(worldX, worldY);
        return chunk.getBlock(blockPos);
    }

    private boolean isValidPlacement(int tileX, int tileY, World world) {
        if (!world.isPassable(tileX, tileY)) {
            GameLogger.info("Cannot place at non-passable location");
            return false;
        }

        return true;
    }


    public boolean placeBlock(PlaceableBlock.BlockType type, int tileX, int tileY) {
        // Calculate the chunk position
        int chunkX = Math.floorDiv(tileX, Chunk.CHUNK_SIZE);
        int chunkY = Math.floorDiv(tileY, Chunk.CHUNK_SIZE);
        Vector2 chunkPos = new Vector2(chunkX, chunkY);

        // Get or load the chunk
        Chunk chunk = world.getChunkAtPosition(tileX, tileY);
        if (chunk == null) {
            chunk = world.loadOrGenerateChunk(chunkPos);
            world.getChunks().put(chunkPos, chunk);
        }

        // Check if there's already a block at this position
        Vector2 blockPos = new Vector2(tileX, tileY);
        if (chunk.getBlock(blockPos) != null) {
            GameLogger.info("Block already exists at position: " + blockPos);
            return false; // Can't place block on top of another block
        }

        // Create the block
        PlaceableBlock block = new PlaceableBlock(type, blockPos, null, false);

        // Set the texture
        block.setTexture(BlockTextureManager.getBlockFrame(block, 0));

        // Add the block to the chunk
        chunk.addBlock(block);
        chunk.setDirty(true); // Mark chunk as dirty for saving
        GameLogger.info("Placed block of type " + type + " at " + blockPos);

        return true;
    }


    public void removeBlock(int tileX, int tileY) {
        // Calculate the chunk position
        int chunkX = Math.floorDiv(tileX, Chunk.CHUNK_SIZE);
        int chunkY = Math.floorDiv(tileY, Chunk.CHUNK_SIZE);
        Vector2 chunkPos = new Vector2(chunkX, chunkY);

        // Get the chunk
        Chunk chunk = world.getChunkAtPosition(tileX, tileY);
        if (chunk == null) {
            GameLogger.info("Chunk not loaded at position: " + chunkPos);
            return;
        }

        // Check if there's a block at this position
        Vector2 blockPos = new Vector2(tileX, tileY);
        PlaceableBlock block = chunk.getBlock(blockPos);
        if (block == null) {
            GameLogger.info("No block exists at position: " + blockPos);
            return; // Can't remove a block that doesn't exist
        }

        // Remove the block from the chunk
        chunk.removeBlock(blockPos);
        chunk.setDirty(true); // Mark chunk as dirty for saving
        GameLogger.info("Removed block at " + blockPos);

    }

    public boolean isInitialized() {
        return initialized;
    }


    public boolean hasCollisionAt(int tileX, int tileY) {
        PlaceableBlock block = getBlockAt(tileX, tileY);
        return block != null && block.getType().hasCollision;
    }

    public void render(SpriteBatch batch, double worldTimeInMinutes) {
        for (Chunk chunk : world.getChunks().values()) {
            for (PlaceableBlock block : chunk.getBlocks().values()) {
                TextureRegion currentFrame = BlockTextureManager.getBlockFrame(
                    block, (float) worldTimeInMinutes
                );

                if (currentFrame != null) {
                    float tileX = block.getPosition().x * World.TILE_SIZE;
                    float tileY = block.getPosition().y * World.TILE_SIZE;
                    float blockWidth = currentFrame.getRegionWidth();
                    float blockHeight = currentFrame.getRegionHeight();
                    float offsetX = (World.TILE_SIZE - blockWidth) / 2;
                    float offsetY = 0;

                    Color originalColor = batch.getColor().cpy();

                    // Get light level for this block's position
                    Vector2 tilePos = block.getPosition();
                    Float lightLevel = world.getLightLevelAtTile(tilePos);

                    // Apply light level if it exists
                    if (lightLevel != null && lightLevel > 0) {
                        Color lightColor = new Color(1f, 0.8f, 0.6f, 1f);
                        Color baseColor = world.getCurrentWorldColor().cpy();
                        baseColor.lerp(lightColor, lightLevel * 0.7f);
                        batch.setColor(baseColor);
                    } else {
                        batch.setColor(world.getCurrentWorldColor());
                    }

                    // Handle flipped rendering
                    if (block.isFlipped()) {
                        batch.draw(currentFrame,
                            tileX + offsetX + blockWidth, // X position (offset + width for flip)
                            tileY + offsetY,              // Y position
                            -blockWidth,                  // Negative width for horizontal flip
                            blockHeight                   // Normal height
                        );
                    } else {
                        batch.draw(currentFrame,
                            tileX + offsetX,
                            tileY + offsetY,
                            blockWidth,
                            blockHeight
                        );
                    }

                    // Restore original color
                    batch.setColor(originalColor);
                }
            }
        }
    }

    public String getChunkKey(int tileX, int tileY) {
        int chunkX = tileX / World.CHUNK_SIZE;
        int chunkY = tileY / World.CHUNK_SIZE;
        return chunkX + "," + chunkY;
    }


}
