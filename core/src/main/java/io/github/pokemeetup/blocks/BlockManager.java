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
    private boolean initialized = false;


    public BlockManager() {
        GameLogger.info("Initialized BlockManager");
    }

    public boolean placeBlockFromPlayer(PlaceableBlock.BlockType type) {
        if (type == null || GameContext.get().getPlayer() == null || GameContext.get().getWorld() == null) {
            GameLogger.error("Invalid parameters for player block placement");
            return false;
        }

        Vector2 targetPos = calculateTargetPosition(GameContext.get().getPlayer());
        int targetX = (int) targetPos.x;
        int targetY = (int) targetPos.y;

        GameLogger.info("Checking placement - Player(" + GameContext.get().getPlayer().getTileX() + "," + GameContext.get().getPlayer().getTileY() +
            ") Target(" + targetX + "," + targetY + ") Dir:" + GameContext.get().getPlayer().getDirection());

        if (!isValidPlacement(targetX, targetY, GameContext.get().getWorld())) {
            return false;
        }

        if (GameContext.get().getGameClient() != null && GameContext.get().isMultiplayer()) {
            NetworkProtocol.BlockPlacement placement = new NetworkProtocol.BlockPlacement();
            placement.username = GameContext.get().getPlayer().getUsername();
            placement.blockTypeId = type.id;
            placement.tileX = targetX;
            placement.tileY = targetY;
            placement.action = NetworkProtocol.BlockAction.PLACE;
            GameContext.get().getGameClient().sendBlockPlacement(placement);
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
        Chunk chunk = GameContext.get().getWorld().getChunkAtPosition(worldX, worldY);
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
        int chunkX = Math.floorDiv(tileX, Chunk.CHUNK_SIZE);
        int chunkY = Math.floorDiv(tileY, Chunk.CHUNK_SIZE);
        Vector2 chunkPos = new Vector2(chunkX, chunkY);
        Chunk chunk = GameContext.get().getWorld().getChunkAtPosition(tileX, tileY);
        if (chunk == null) {
            chunk = GameContext.get().getWorld().loadOrGenerateChunk(chunkPos);
            GameContext.get().getWorld().getChunks().put(chunkPos, chunk);
        }
        Vector2 blockPos = new Vector2(tileX, tileY);
        if (chunk.getBlock(blockPos) != null) {
            GameLogger.info("Block already exists at position: " + blockPos);
            return false; // Can't place block on top of another block
        }
        PlaceableBlock block = new PlaceableBlock(type, blockPos, null, false);
        block.setTexture(BlockTextureManager.getBlockFrame(block, 0));
        chunk.addBlock(block);
        chunk.setDirty(true); // Mark chunk as dirty for saving
        GameLogger.info("Placed block of type " + type + " at " + blockPos);

        return true;
    }


    public void removeBlock(int tileX, int tileY) {
        int chunkX = Math.floorDiv(tileX, Chunk.CHUNK_SIZE);
        int chunkY = Math.floorDiv(tileY, Chunk.CHUNK_SIZE);
        Vector2 chunkPos = new Vector2(chunkX, chunkY);
        Chunk chunk = GameContext.get().getWorld().getChunkAtPosition(tileX, tileY);
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
        if (GameContext.get() == null) {
            return;
        }
        for (Chunk chunk : GameContext.get().getWorld().getChunks().values()) {
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
                    Vector2 tilePos = block.getPosition();
                    Float lightLevel = GameContext.get().getWorld().getLightLevelAtTile(tilePos);
                    if (lightLevel != null && lightLevel > 0) {
                        Color lightColor = new Color(1f, 0.8f, 0.6f, 1f);
                        Color baseColor = GameContext.get().getWorld().getCurrentWorldColor().cpy();
                        baseColor.lerp(lightColor, lightLevel * 0.7f);
                        batch.setColor(baseColor);
                    } else {
                        batch.setColor(GameContext.get().getWorld().getCurrentWorldColor());
                    }
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
                    batch.setColor(originalColor);
                }
            }
        }
    }


}
