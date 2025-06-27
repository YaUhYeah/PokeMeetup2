package io.github.pokemeetup.system.data;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.InventorySlotData;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.ItemContainer;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChestData implements Serializable, Json.Serializable,ItemContainer {
    public static final int CHEST_SIZE = 27;
    private static final long serialVersionUID = 1L;
    public UUID chestId;
    public List<ItemData> items;
    public Vector2 position;
    public boolean isDirty;
    public transient InventorySlotData[] slotDataArray;

    public List<ItemData> getItems() {
        return items;
    }

    public void setItems(List<ItemData> items) {
        this.items = items;
    }

    public ChestData() {
        this.chestId = UUID.randomUUID();
        this.items = new ArrayList<>(CHEST_SIZE);
        this.position = new Vector2(0, 0);
        initializeSlots();
    }

    public ChestData(int x, int y) {
        this.chestId = UUID.randomUUID();
        this.items = new ArrayList<>(CHEST_SIZE);
        this.position = new Vector2(x, y);
        this.isDirty = false;
        initializeSlots();
    }

    private void initializeSlots() {
        if (items == null) {
            items = new ArrayList<>(CHEST_SIZE);
        }
        items.clear();
        for (int i = 0; i < CHEST_SIZE; i++) {
            items.add(null);
        }
        initializeSlotDataArray();
    }

    public InventorySlotData getSlotData(int index) {
        if (slotDataArray == null) {
            initializeSlotDataArray();
        }

        if (index >= 0 && index < CHEST_SIZE) {
            return slotDataArray[index];
        }
        return null;
    }

    @Override
    public ItemData getItemAt(int index) {
        if (items == null) {
            initializeSlots();
        }

        if (index >= 0 && index < items.size()) {
            return items.get(index);
        }
        return null;
    }

    @Override
    public void setItemAt(int index, ItemData item) {
        if (items == null) {
            initializeSlots();
        }

        if (index >= 0 && index < items.size()) {
            if (item != null) {
                items.set(index, item.copy());
            } else {
                items.set(index, null);
            }
            isDirty = true;
        }
    }

    @Override
    public int getSize() {
        return CHEST_SIZE;
    }



    @Override
    public void write(Json json) {
        json.writeValue("chestId", chestId.toString());
        json.writeValue("position", position);
        json.writeValue("isDirty", isDirty);
        json.writeValue("items", items, ArrayList.class, ItemData.class);
    }

    @Override
    public void read(Json json, JsonValue jsonData) {
        chestId = UUID.fromString(jsonData.getString("chestId"));
        position = json.readValue(Vector2.class, jsonData.get("position"));
        isDirty = jsonData.getBoolean("isDirty", false);
        items = json.readValue(ArrayList.class, ItemData.class, jsonData.get("items"));
        initializeSlotDataArray();
    }

    public void initializeSlotDataArray() {
        if (slotDataArray == null) {
            slotDataArray = new InventorySlotData[CHEST_SIZE];
            for (int i = 0; i < CHEST_SIZE; i++) {
                slotDataArray[i] = new InventorySlotData(i, InventorySlotData.SlotType.CHEST, this);
            }
        }
    }


    public ChestData copy() {
        ChestData copy = new ChestData((int)position.x, (int)position.y);
        copy.chestId = UUID.fromString(this.chestId.toString()); // Deep copy UUID
        copy.isDirty = this.isDirty;
        if (this.items != null) {
            for (int i = 0; i < this.items.size(); i++) {
                ItemData item = this.items.get(i);
                if (item != null) {
                    copy.items.set(i, item.copy());
                }
            }
        }

        return copy;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ChestData{id=").append(chestId)
            .append(", position=").append(position)
            .append(", items=[");

        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                ItemData item = items.get(i);
                if (item != null) {
                    sb.append("\n  ").append(i).append(": ").append(item);
                }
            }
        }

        sb.append("\n]}");
        return sb.toString();
    }

}
