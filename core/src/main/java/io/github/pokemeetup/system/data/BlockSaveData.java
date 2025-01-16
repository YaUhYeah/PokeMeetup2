package io.github.pokemeetup.system.data;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.pokemeetup.utils.GameLogger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockSaveData {
    private Map<String, List<BlockData>> placedBlocks;

    public BlockSaveData() {
        this.placedBlocks = new HashMap<>();
    }

    public void addBlock(String chunkKey, BlockData block) {
        if (block == null) {
            GameLogger.error("Attempted to add invalid block data");
            return;
        }

        placedBlocks.computeIfAbsent(chunkKey, k -> new ArrayList<>()).add(block);
        GameLogger.info("Added block " + block.type + " to chunk " + chunkKey);
    }

    public Map<String, List<BlockData>> getPlacedBlocks() {
        if (placedBlocks == null) {
            placedBlocks = new HashMap<>();
        }
        return placedBlocks;
    }

    public void setPlacedBlocks(Map<String, List<BlockData>> placedBlocks) {
        this.placedBlocks = placedBlocks;
    }

    public BlockSaveData copy() {
        BlockSaveData copy = new BlockSaveData();

        if (this.placedBlocks != null) {
            Map<String, List<BlockData>> placedBlocksCopy = new HashMap<>();
            for (Map.Entry<String, List<BlockData>> entry : this.placedBlocks.entrySet()) {
                String chunkKey = entry.getKey();
                List<BlockData> originalList = entry.getValue();
                List<BlockData> copiedList = new ArrayList<>();

                if (originalList != null) {
                    for (BlockData blockData : originalList) {
                        if (blockData != null) {
                            copiedList.add(blockData.copy());
                        } else {
                            copiedList.add(null);
                        }
                    }
                }

                placedBlocksCopy.put(chunkKey, copiedList);
            }
            copy.setPlacedBlocks(placedBlocksCopy);
        }

        return copy;
    }

    public static class BlockData implements Serializable, Json.Serializable {
        public String type;
        public int x;
        public int y;
        public boolean isFlipped;     // Ensure this field is present
        public boolean isChestOpen;
        public ChestData chestData;
        public HashMap<String, Object> extraData;

        public BlockData() {
            this.extraData = new HashMap<>();
        }

        public BlockData(String type, int x, int y) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.extraData = new HashMap<>();
            this.isFlipped = false;
            this.isChestOpen = false;
        }

        @Override
        public void write(Json json) {
            json.writeValue("type", type);
            json.writeValue("x", x);
            json.writeValue("y", y);
            json.writeValue("isFlipped", isFlipped);   // Make sure we're writing this
            json.writeValue("isChestOpen", isChestOpen);
            json.writeValue("extraData", extraData, HashMap.class);

            if (chestData != null) {
                json.writeValue("chestData", chestData);
            }
        }

        @Override
        public void read(Json json, JsonValue jsonData) {
            type = jsonData.getString("type");
            x = jsonData.getInt("x");
            y = jsonData.getInt("y");
            isFlipped = jsonData.getBoolean("isFlipped", false); // Reading flipped state
            isChestOpen = jsonData.getBoolean("isChestOpen", false);
            extraData = json.readValue(HashMap.class, jsonData.get("extraData"));
            chestData = json.readValue(ChestData.class, jsonData.get("chestData"));
        }

        public BlockData copy() {
            BlockData copy = new BlockData();
            copy.type = this.type;
            copy.x = this.x;
            copy.y = this.y;
            copy.isFlipped = this.isFlipped;
            copy.isChestOpen = this.isChestOpen;
            copy.extraData = new HashMap<>(this.extraData);
            if (this.chestData != null) {
                copy.chestData = this.chestData.copy();
            }
            return copy;
        }
    }
}
