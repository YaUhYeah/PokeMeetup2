
package io.github.pokemeetup.blocks;

import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.utils.GameLogger;
import java.util.*;

public class SmartBlockConfig {
    private final Map<ConnectionPattern, PlaceableBlock.BlockType> variants = new HashMap<>();
    private final String groupId;

    public SmartBlockConfig(String groupId) {
        this.groupId = groupId;
    }

    public void addVariant(ConnectionPattern pattern, PlaceableBlock.BlockType blockType) {
        variants.put(pattern, blockType);
        GameLogger.info("Added variant for " + groupId + ": " + blockType.getId());
    }

    public PlaceableBlock.BlockType getVariantForPattern(ConnectionPattern pattern) {
        return variants.getOrDefault(pattern, getDefaultVariant());
    }

    public boolean containsBlockType(PlaceableBlock.BlockType blockType) {
        return variants.containsValue(blockType);
    }

    private PlaceableBlock.BlockType getDefaultVariant() {
        return variants.values().iterator().next();
    }

}
