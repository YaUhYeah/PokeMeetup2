package io.github.pokemeetup.blocks;

import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.utils.GameLogger;

public class BuildingTemplate {
    private final int width;
    private final int height;
    private final BlockData[][] layout;

    public BuildingTemplate(int width, int height) {
        this.width = width;
        this.height = height;
        this.layout = new BlockData[width][height];
    }

    public static BuildingTemplate createWoodenHouse() {
        BuildingTemplate template = new BuildingTemplate(5, 4);

        template.setBlock(2, 0, new BlockData(PlaceableBlock.BlockType.WOODEN_DOOR, false));
        template.setBlock(0, 0, new BlockData(PlaceableBlock.BlockType.HOUSE_PART, false));
        template.setBlock(1, 0, new BlockData(PlaceableBlock.BlockType.HOUSE_PLANKS, false));
        template.setBlock(3, 0, new BlockData(PlaceableBlock.BlockType.HOUSE_PLANKS, true));
        template.setBlock(4, 0, new BlockData(PlaceableBlock.BlockType.HOUSE_PART, true));

        template.setBlock(0, 1, new BlockData(PlaceableBlock.BlockType.HOUSE_MIDDLE_PART, false));
        template.setBlock(1, 1, new BlockData(PlaceableBlock.BlockType.HOUSE_MIDDLE_PART_1, false));
        template.setBlock(2, 1, new BlockData(PlaceableBlock.BlockType.HOUSE_MIDDLE_PART_0, false));
        template.setBlock(3, 1, new BlockData(PlaceableBlock.BlockType.HOUSE_MIDDLE_PART_1, true));
        template.setBlock(4, 1, new BlockData(PlaceableBlock.BlockType.HOUSE_MIDDLE_PART, true));

        template.setBlock(0, 2, new BlockData(PlaceableBlock.BlockType.ROOFINNER, false));
        template.setBlock(1, 2, new BlockData(PlaceableBlock.BlockType.ROOF_MIDDLE_OUTSIDE, false));
        template.setBlock(2, 2, new BlockData(PlaceableBlock.BlockType.ROOF_MIDDLE_OUTER, false));
        template.setBlock(3, 2, new BlockData(PlaceableBlock.BlockType.ROOF_MIDDLE_OUTSIDE, true));
        template.setBlock(4, 2, new BlockData(PlaceableBlock.BlockType.ROOFINNER, true));

        template.setBlock(0, 3, new BlockData(PlaceableBlock.BlockType.ROOF_CORNER, false));
        template.setBlock(1, 3, new BlockData(PlaceableBlock.BlockType.ROOF_CORNER_1, false));
        template.setBlock(2, 3, new BlockData(PlaceableBlock.BlockType.ROOF_MIDDLE, false));
        template.setBlock(3, 3, new BlockData(PlaceableBlock.BlockType.ROOF_CORNER_1, true));
        template.setBlock(4, 3, new BlockData(PlaceableBlock.BlockType.ROOF_CORNER, true));

        return template;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public BlockData[][] getLayout() {
        return layout;
    }

    public BlockData getBlockAt(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return null;
        }
        return layout[x][y];
    }

    public void setBlock(int x, int y, BlockData blockData) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            layout[x][y] = blockData;
        }
    }

    public boolean placeBuilding(World world, int startX, int startY) {
        if (!canPlaceAt(world, startX, startY)) {
            GameLogger.info("Cannot place building - area not clear");
            return false;
        }

        try {
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    BlockData blockData = layout[x][y];
                    if (blockData != null) {
                        Vector2 pos = new Vector2(startX + x, startY + y);
                        boolean placed = world.getBlockManager().placeBlock(blockData.type, startX + x, startY + y);
                        if (!placed) {
                            GameLogger.error("Failed to place block at " + pos + " of type " + blockData.type);
                            return false;
                        }
                        PlaceableBlock placedBlock = world.getBlockManager().getBlockAt(startX + x, startY + y);
                        if (placedBlock != null && blockData.isFlipped) {
                            placedBlock.toggleFlip();
                            int chunkX = Math.floorDiv((int) placedBlock.getPosition().x, World.CHUNK_SIZE);
                            int chunkY = Math.floorDiv((int) placedBlock.getPosition().y, World.CHUNK_SIZE);
                            Chunk chunk = world.getChunks().get(new Vector2(chunkX, chunkY));
                            if (chunk != null) {
                                chunk.setDirty(true);
                            }
                        }
                        GameLogger.info("Placed block: " + blockData.type + " at " + pos +
                            " flipped: " + blockData.isFlipped);
                    }
                }
            }
            AudioManager.getInstance().playSound(AudioManager.SoundEffect.HOUSE_BUILD);
            return true;
        } catch (Exception e) {
            GameLogger.error("Error placing building: " + e.getMessage());
            return false;
        }
    }

    private boolean canPlaceAt(World world, int startX, int startY) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (!world.isPassable(startX + x, startY + y) ||
                    world.getBlockManager().getBlockAt(startX + x, startY + y) != null) {
                    return false;
                }
            }
        }
        return true;
    }

    public NetworkProtocol.BuildingPlacement toNetworkMessage(String username, int startX, int startY) {
        NetworkProtocol.BuildingPlacement bp = new NetworkProtocol.BuildingPlacement();
        bp.username = username;
        bp.startX = startX;
        bp.startY = startY;
        bp.width = this.width;
        bp.height = this.height;
        bp.blockTypeIds = new String[width][height];
        bp.flippedFlags = new boolean[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                BlockData data = this.getBlockAt(x, y);
                if (data != null) {
                    bp.blockTypeIds[x][y] = data.type.id;
                    bp.flippedFlags[x][y] = data.isFlipped;
                } else {
                    bp.blockTypeIds[x][y] = "";
                    bp.flippedFlags[x][y] = false;
                }
            }
        }
        bp.timestamp = System.currentTimeMillis();
        return bp;
    }

    public static class BlockData {
        public PlaceableBlock.BlockType type;
        public boolean isFlipped;

        public BlockData(PlaceableBlock.BlockType type, boolean isFlipped) {
            this.type = type;
            this.isFlipped = isFlipped;
        }
    }
}
