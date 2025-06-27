package io.github.pokemeetup.system.gameplay.inventory;

import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.context.GameContext;

public class ChestInteractionHandler {
    private boolean isChestOpen = false;
    private Vector2 currentChestPosition = null;


    public boolean canInteractWithChest() {
        if (GameContext.get().getPlayer() == null || GameContext.get().getPlayer().getWorld() == null) {
            return false;
        }    if (isChestOpen) {
            return false;
        }

        int targetX = GameContext.get().getPlayer().getTileX();
        int targetY = GameContext.get().getPlayer().getTileY();
        switch (GameContext.get().getPlayer().getDirection()) {
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

        PlaceableBlock block = GameContext.get().getWorld().getBlockManager().getBlockAt(targetX, targetY);
        if (block != null && block.getType() == PlaceableBlock.BlockType.CHEST) {
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
