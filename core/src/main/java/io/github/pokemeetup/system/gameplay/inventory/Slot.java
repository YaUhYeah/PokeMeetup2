package io.github.pokemeetup.system.gameplay.inventory;

import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.utils.GameLogger;

import java.util.UUID;

public class Slot {
    private final Object slotLock = new Object();
    private ItemData itemData;

    public Slot() {

    }


    public ItemData getItemData() {
        synchronized (slotLock) {
            return itemData;
        }
    }


    public void setItemData(ItemData newItemData) {
        synchronized (slotLock) {
            this.itemData = newItemData;
            validateItemData();
        }
    }

    private void validateItemData() {
        if (itemData != null) {
            Item template = ItemManager.getItemTemplate(itemData.getItemId());
            if (template != null) {
                if (!template.isStackable() && itemData.getCount() > 1) {
                    GameLogger.info("Fixing unstackable item count for: " + itemData.getItemId());
                    itemData.setCount(1);
                }
                if (itemData.getCount() <= 0) {
                    GameLogger.info("Removing item with invalid count: " + itemData.getItemId());
                    itemData = null;
                }
            }
        }
    }

    public boolean isEmpty() {
        synchronized (slotLock) {
            return itemData == null || itemData.getCount() <= 0;
        }
    }

    public void clear() {
        synchronized (slotLock) {
            this.itemData = null;
        }
    }
}
