package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.system.data.BlockSaveData;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.utils.textures.TileType;

import java.util.*;

public class Chunk {
    public static final int CHUNK_SIZE = 16;

    private final int chunkX;
    private final int chunkY;
    public boolean isDirty = false;
    private TextureRegion[][] autotileRegions;
    private TextureRegion[][] seatileRegions;
    private Biome biome;
    private Map<Vector2, PlaceableBlock> blocks = new HashMap<>();
    private int[][] tileData;
    private List<WorldObject> worldObjects = new ArrayList<>();

    public Chunk() {
        this.chunkX = 0;
        this.chunkY = 0;
        this.finalBiomeTypes = new BiomeType[CHUNK_SIZE][CHUNK_SIZE];
    }

    public Chunk(int chunkX, int chunkY, Biome biome, long worldSeed) {
        this.chunkX = chunkX;
        this.finalBiomeTypes = new BiomeType[CHUNK_SIZE][CHUNK_SIZE];
        this.chunkY = chunkY;
        this.biome = biome;
        this.tileData = new int[CHUNK_SIZE][CHUNK_SIZE];
    }

    public TextureRegion[][] getAutotileRegions() {
        return autotileRegions;
    }


    public void setAutotileRegions(TextureRegion[][] regions) {
        this.autotileRegions = regions;
    }


    public void addBlock(PlaceableBlock block) {
        if (block != null) {
            blocks.put(block.getPosition(), block);
            isDirty = true;
        }
    }
    private BiomeType[][] finalBiomeTypes;

    public void removeBlock(Vector2 position) {
        blocks.remove(position);
        isDirty = true;
    }

    public TextureRegion[][] getSeatileRegions() {
        return seatileRegions;
    }

    public void setSeatileRegions(TextureRegion[][] seatileRegions) {
        this.seatileRegions = seatileRegions;
    }

    public List<WorldObject> getWorldObjects() {
        return worldObjects;
    }

    public void setWorldObjects(List<WorldObject> worldObjects) {
        this.worldObjects = worldObjects;
    }

    public PlaceableBlock getBlock(Vector2 position) {
        return blocks.get(position);
    }

    public Map<Vector2, PlaceableBlock> getBlocks() {
        return new HashMap<>(blocks);
    }

    public void setBlocks(Map<Vector2, PlaceableBlock> blocks) {
        this.blocks = blocks;
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

    public void setBiome(Biome biome) {
        this.biome = biome;
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



    public boolean isPassable(int localX, int localY) {
        localX = (localX + CHUNK_SIZE) % CHUNK_SIZE;
        localY = (localY + CHUNK_SIZE) % CHUNK_SIZE;
        int tType = tileData[localX][localY];
        return TileType.isPassableTile(tType);
    }

    public BiomeType[][] getFinalBiomeTypes() {
        return finalBiomeTypes;
    }

    public void setFinalBiomeTypes(BiomeType[][] finalBiomeTypes) {
        this.finalBiomeTypes = finalBiomeTypes;
    }
}
