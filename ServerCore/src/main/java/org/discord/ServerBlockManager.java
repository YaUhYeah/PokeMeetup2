package org.discord;

import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.blocks.PlaceableBlock;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerBlockManager {
    private final Map<Vector2, PlaceableBlock> placedBlocks = new ConcurrentHashMap<>();

    public boolean placeBlock(PlaceableBlock.BlockType type, int tileX, int tileY) {
        Vector2 pos = new Vector2(tileX, tileY);
        if (placedBlocks.containsKey(pos)) {
            return false;
        }
        PlaceableBlock block = new PlaceableBlock(type, pos);
        placedBlocks.put(pos, block);
        return true;
    }

    public void removeBlock(int tileX, int tileY) {
        Vector2 pos = new Vector2(tileX, tileY);
        if (placedBlocks.containsKey(pos)) {
            return;
        }
        placedBlocks.remove(pos);
    }

    public Map<Vector2, PlaceableBlock> getPlacedBlocks() {
        return placedBlocks;
    }
}
