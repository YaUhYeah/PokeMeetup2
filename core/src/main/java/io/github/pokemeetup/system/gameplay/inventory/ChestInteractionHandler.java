package io.github.pokemeetup.system.gameplay.inventory;

import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;

public class ChestInteractionHandler {
    private static final float INTERACTION_RANGE = World.TILE_SIZE * 1.5f;
    private boolean isChestOpen = false;
    private Vector2 currentChestPosition = null;

    public void resetChestBlockOpenState(World world) {
        if (currentChestPosition != null && world != null) {
            PlaceableBlock chestBlock = world.getBlockManager()
                .getBlockAt((int) currentChestPosition.x, (int) currentChestPosition.y);
            if (chestBlock != null && chestBlock.getType() == PlaceableBlock.BlockType.CHEST) {
                chestBlock.setChestOpen(false);
            }
        }
    }

    public boolean canInteractWithChest(Player player) {
        if (player == null || player.getWorld() == null) {
            return false;
        }    if (isChestOpen) {
            return false;
        }

        int targetX = player.getTileX();
        int targetY = player.getTileY();

        // Get tile in front of player based on direction
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

        PlaceableBlock block = player.getWorld().getBlockManager().getBlockAt(targetX, targetY);
        if (block != null && block.getType() == PlaceableBlock.BlockType.CHEST) {
            // Cache the chest position when found
            currentChestPosition = new Vector2(targetX, targetY);
            return true;
        }

        return false;
    }

    public Vector2 getCurrentChestPosition() {
        return currentChestPosition;
    }

    public boolean isChestOpen() {
        return isChestOpen;
    }

    public void setChestOpen(boolean isOpen) {
        this.isChestOpen = isOpen;
    }

    public void reset() {
        isChestOpen = false;
        currentChestPosition = null;
    }
}
