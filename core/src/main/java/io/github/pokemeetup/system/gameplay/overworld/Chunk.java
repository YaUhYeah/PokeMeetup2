package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.managers.BiomeManager;
import io.github.pokemeetup.system.data.BlockSaveData;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.utils.textures.TileType;

import java.util.*;

public class Chunk {
    public static final int CHUNK_SIZE = 16;

    private final long worldSeed;
    private final int chunkX;
    private final int chunkY;
    public boolean isDirty = false;
    private Biome biome;
    private Map<Vector2, PlaceableBlock> blocks = new HashMap<>();
    private int[][] tileData;
    private int[][] elevationBands;

    private long generationSeed;

    public long getGenerationSeed() {
        return generationSeed;
    }

    public void setGenerationSeed(long seed) {
        this.generationSeed = seed;
    }

    public Chunk() {
        this.chunkX = 0;
        this.chunkY = 0;
        this.worldSeed = 5;
    }

    public Chunk(int chunkX, int chunkY, Biome biome, long worldSeed, BiomeManager biomeManager) {
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.biome = biome;
        this.worldSeed = worldSeed;
        this.generationSeed = worldSeed;
        this.tileData = new int[CHUNK_SIZE][CHUNK_SIZE];
    }

    public void addBlock(PlaceableBlock block) {
        if (block != null) {
            blocks.put(block.getPosition(), block);
            isDirty = true;
        }
    }

    public void removeBlock(Vector2 position) {
        blocks.remove(position);
        isDirty = true;
    }

    private List<WorldObject> worldObjects = new ArrayList<>();

    public void setBiome(Biome biome) {
        this.biome = biome;
    }

    public List<WorldObject> getWorldObjects() {
        return worldObjects;
    }

    public void setWorldObjects(List<WorldObject> worldObjects) {
        this.worldObjects = worldObjects;
    }


    public void setBlocks(Map<Vector2, PlaceableBlock> blocks) {
        this.blocks = blocks;
    }

    public void setElevationBands(int[][] elevationBands) {
        this.elevationBands = elevationBands;
    }

    public PlaceableBlock getBlock(Vector2 position) {
        return blocks.get(position);
    }

    public Map<Vector2, PlaceableBlock> getBlocks() {
        return new HashMap<>(blocks);
    }

    public boolean isDirty() {
        return isDirty;
    }

    public void setDirty(boolean dirty) {
        this.isDirty = dirty;
    }

    public Biome getBiome() {
        return biome;
    }

    public int getTileType(int localX, int localY) {
        if (localX < 0 || localX >= CHUNK_SIZE || localY < 0 || localY >= CHUNK_SIZE) {
            return -1;
        }
        return tileData[localX][localY];
    }

    public int[][] getTileData() {
        return tileData;
    }

    public void setTileData(int[][] tileData) {
        this.tileData = tileData;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkY() {
        return chunkY;
    }

    public List<BlockSaveData.BlockData> getBlockDataForSave() {
        List<BlockSaveData.BlockData> blockDataList = new ArrayList<>();
        for (PlaceableBlock b : blocks.values()) {
            BlockSaveData.BlockData data = new BlockSaveData.BlockData();
            data.type = b.getId();
            data.x = (int) b.getPosition().x;
            data.y = (int) b.getPosition().y;
            data.isFlipped = b.isFlipped();
            data.isChestOpen = b.isChestOpen();
            if (b.getType() == PlaceableBlock.BlockType.CHEST && b.getChestData() != null) {
                data.chestData = b.getChestData();
            }
            blockDataList.add(data);
        }
        return blockDataList;
    }
    public int[][] getElevationBands() {
        return this.elevationBands;
    }





    public boolean isPassable(int localX, int localY) {
        localX = (localX + CHUNK_SIZE) % CHUNK_SIZE;
        localY = (localY + CHUNK_SIZE) % CHUNK_SIZE;
        int tType = tileData[localX][localY];
        return TileType.isPassableTile(tType);
    }

}
