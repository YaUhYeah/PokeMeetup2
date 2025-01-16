package io.github.pokemeetup.blocks;

import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;

import java.util.*;

public class SmartBuildingManager {
    private final World world;
    private final Map<String, SmartBlockConfig> smartBlocks = new HashMap<>();
    private final Map<String, BuildingTemplate> buildingTemplates = new HashMap<>();

    public SmartBuildingManager(World world) {
        this.world = world;
        initializeSmartBlocks();
        initializeBuildingTemplates();
    }

    private void initializeBuildingTemplates() {
        buildingTemplates.put("wooden_house", BuildingTemplate.createWoodenHouse());
    }

    public boolean placeBuilding(String templateId, int startX, int startY) {
        BuildingTemplate template = buildingTemplates.get(templateId);
        if (template == null) {
            GameLogger.error("No template found for: " + templateId);
            return false;
        }

        return template.placeBuilding(world, startX, startY);
    }

    private void initializeSmartBlocks() {
        // Initialize roof configuration
        SmartBlockConfig roofConfig = new SmartBlockConfig("roof");
        roofConfig.addVariant(new ConnectionPattern(false, true, true, true),
            PlaceableBlock.BlockType.ROOF_CORNER);
        roofConfig.addVariant(new ConnectionPattern(true, true, true, true),
            PlaceableBlock.BlockType.ROOF_MIDDLE);
        roofConfig.addVariant(new ConnectionPattern(true, false, true, true),
            PlaceableBlock.BlockType.ROOF_INSIDE);
        smartBlocks.put("roof", roofConfig);

        // Initialize wall configuration
        SmartBlockConfig wallConfig = new SmartBlockConfig("wall");
        wallConfig.addVariant(new ConnectionPattern(true, true, false, false),
            PlaceableBlock.BlockType.WOODEN_PLANKS);
        wallConfig.addVariant(new ConnectionPattern(false, true, true, true),
            PlaceableBlock.BlockType.HOUSE_MIDDLE_PART);
        smartBlocks.put("wall", wallConfig);

        GameLogger.info("Initialized smart block configurations");
    }

    public PlaceableBlock.BlockType getSmartBlockType(String groupId, int x, int y) {
        SmartBlockConfig config = smartBlocks.get(groupId);
        if (config == null) return null;

        ConnectionPattern pattern = calculateConnectionPattern(groupId, x, y);
        return config.getVariantForPattern(pattern);
    }

    private ConnectionPattern calculateConnectionPattern(String groupId, int x, int y) {
        boolean north = hasConnection(groupId, x, y + 1);
        boolean south = hasConnection(groupId, x, y - 1);
        boolean east = hasConnection(groupId, x + 1, y);
        boolean west = hasConnection(groupId, x - 1, y);

        return new ConnectionPattern(north, south, east, west);
    }

    private boolean hasConnection(String groupId, int x, int y) {
        PlaceableBlock block = world.getBlockManager().getBlockAt(x, y);
        if (block == null) return false;

        SmartBlockConfig config = smartBlocks.get(groupId);
        return config != null && config.containsBlockType(block.getType());
    }

    public void updateSurroundingBlocks(int x, int y, String groupId) {
        updateBlockIfNeeded(x + 1, y, groupId);
        updateBlockIfNeeded(x - 1, y, groupId);
        updateBlockIfNeeded(x, y + 1, groupId);
        updateBlockIfNeeded(x, y - 1, groupId);
    }

    private void updateBlockIfNeeded(int x, int y, String groupId) {
        PlaceableBlock existingBlock = world.getBlockManager().getBlockAt(x, y);
        if (existingBlock == null) return;

        SmartBlockConfig config = smartBlocks.get(groupId);
        if (config != null && config.containsBlockType(existingBlock.getType())) {
            PlaceableBlock.BlockType newType = getSmartBlockType(groupId, x, y);
            if (newType != null && newType != existingBlock.getType()) {
                world.getBlockManager().placeBlock(newType, x, y);
            }
        }
    }

    public String getGroupIdForBlockType(PlaceableBlock.BlockType blockType) {
        for (Map.Entry<String, SmartBlockConfig> entry : smartBlocks.entrySet()) {
            if (entry.getValue().containsBlockType(blockType)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
