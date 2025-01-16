package io.github.pokemeetup.system.gameplay.inventory.secureinventories;

import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.utils.GameLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class InventorySlotData implements ItemDataObserver {
    private final int slotIndex;
    private final List<InventorySlotDataObserver> observers = new ArrayList<>();
    private final transient ItemContainer itemContainer;
    private SlotType slotType;
    private int position;
    private String slotId;

    public InventorySlotData(int slotIndex, SlotType slotType, ItemContainer itemContainer) {
        this.slotIndex = slotIndex;
        this.slotType = slotType;
        this.itemContainer = itemContainer;
        this.position = 0;
        this.slotId = "";
    }

    public SlotType getSlotType() {
        return slotType;
    }

    public void setSlotType(SlotType slotType) {
        this.slotType = slotType;
    }

    public InventorySlotData copy() {
        InventorySlotData copy = new InventorySlotData(this.slotIndex, this.slotType, this.itemContainer);
        copy.position = this.position;
        copy.slotId = this.slotId;
        return copy;
    }

    public void setItem(String itemId, int count, UUID uuid) {
        if (itemId == null || count <= 0) {
            this.itemContainer.setItemAt(slotIndex, null);
        } else {
            ItemData newItemData = new ItemData(itemId, count, uuid != null ? uuid : UUID.randomUUID());
            this.itemContainer.setItemAt(slotIndex, newItemData);

            if (slotType == SlotType.CRAFTING || slotType == SlotType.CRAFTING_RESULT) {
                notifyObservers();
            }
        }
        notifyObservers();
    }


    public void clear() {
        GameLogger.info("Clearing slot " + slotIndex);
        ItemData currentItem = getItemData();
        if (currentItem != null) {
            currentItem.removeObserver(this);
        }
        this.itemContainer.setItemAt(slotIndex, null);
        notifyObservers();
    }

    @Override
    public void onItemDataChanged(ItemData itemData) {
        GameLogger.error("Item data changed in slot " + slotIndex + ": " +
            (itemData != null ? itemData.getItemId() + " x" + itemData.getCount() : "null"));
        notifyObservers();
    }

    public void addObserver(InventorySlotDataObserver observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void notifyObservers() {
        for (InventorySlotDataObserver observer : observers) {
            observer.onSlotDataChanged();
        }
    }

    public boolean isEmpty() {
        ItemData itemData = getItemData();
        return itemData == null || itemData.getCount() <= 0;
    }

    public String getItemId() {
        ItemData itemData = getItemData();
        return itemData != null ? itemData.getItemId() : null;
    }

    public int getCount() {
        ItemData itemData = getItemData();
        return itemData != null ? itemData.getCount() : 0;
    }

    public void setCount(int count) {
        ItemData itemData = getItemData();
        if (itemData != null) {
            itemData.setCount(count);
            if (count <= 0) {
                this.itemContainer.setItemAt(slotIndex, null);
            }
        }
        notifyObservers();
    }

    public ItemData getItemData() {
        // Removed the logging inside this getter to avoid confusion
        return itemContainer.getItemAt(slotIndex);
    }

    public void setItemData(ItemData itemData) {
        try {
            // Only log important changes, not every trivial action
            if (itemData != null) {
                ItemData copyData = itemData.copy();
                copyData.addObserver(this);
                this.itemContainer.setItemAt(slotIndex, copyData);
            } else {
                this.itemContainer.setItemAt(slotIndex, null);
            }

            notifyObservers();
        } catch (Exception e) {
            GameLogger.error("Error in setItemData: " + e.getMessage() + " for slot " + slotIndex);
        }
    }


    public int getSlotIndex() {
        return slotIndex;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public UUID getUuid() {
        ItemData itemData = getItemData();
        return itemData != null ? itemData.getUuid() : null;
    }

    public Item getItem() {
        ItemData itemData = getItemData();
        if (itemData == null) return null;

        Item baseItem = ItemManager.getItem(itemData.getItemId());
        if (baseItem == null) {
            GameLogger.error("Could not find base item for: " + itemData.getItemId());
            return null;
        }

        Item item = baseItem.copy();
        item.setCount(itemData.getCount());
        item.setUuid(itemData.getUuid());
        return item;
    }


    public ItemContainer getItemContainer() {
        return itemContainer;
    }

    public enum SlotType {
        INVENTORY,
        HOTBAR,
        CRAFTING,
        CRAFTING_RESULT,
        EXPANDED_CRAFTING,
        CHEST
    }
}
