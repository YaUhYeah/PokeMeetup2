package io.github.pokemeetup.system.gameplay;

import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.system.data.BlockSaveData;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;

import java.util.ArrayList;
import java.util.List;

public class ClientChunkData {
    public Vector2 chunkPos;
    public BiomeType biomeType;
    public int[][] tileData;
    public List<BlockSaveData.BlockData> blockData;
    public List<WorldObject> worldObjects;

    public ClientChunkData() {
        this.tileData = new int[16][16];
        this.blockData = new ArrayList<>();
        this.worldObjects = new ArrayList<>();
    }
}
