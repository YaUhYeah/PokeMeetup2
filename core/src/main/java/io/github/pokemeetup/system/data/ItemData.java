package io.github.pokemeetup.system.data;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.ItemDataObserver;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ItemData {
    private final transient List<ItemDataObserver> observers = new ArrayList<>();
    public String itemId;   // Changed to public
    public int count;       // Changed to public
    public UUID uuid;       // Changed to public
    public int durability = -1;     // Changed to public
    public int maxDurability = -1;  // Changed to public

    public ItemData() {
        this.uuid = UUID.randomUUID();
    }

    public ItemData(String itemId, int count, UUID uuid) {
        this.itemId = itemId;
        this.count = count;
        this.uuid = uuid != null ? uuid : UUID.randomUUID();

        // Initialize durability from item template
        Item itemTemplate = ItemManager.getItemTemplate(itemId);
        if (itemTemplate != null) {
            this.durability = itemTemplate.getMaxDurability();
            this.maxDurability = itemTemplate.getMaxDurability();
        } else {
            this.durability = -1;
            this.maxDurability = -1;
        }
    }

    public ItemData(String itemId, int count) {
        this.itemId = itemId;
        this.count = count;
        this.uuid = UUID.randomUUID();
    }


    public ItemData(String itemId) {
        this.itemId = itemId;
        this.count = 1;
        this.uuid = UUID.randomUUID();
    }

    public ItemData(ItemData other) {
        this.itemId = other.itemId;
        this.count = other.count;
        this.uuid = other.uuid != null ? other.uuid : UUID.randomUUID();
        this.durability = other.durability;
        this.maxDurability = other.maxDurability;
    }

    public boolean isBroken() {
        return maxDurability > 0 && durability <= 0;
    }


    public void updateDurability(int amount) {
        if (maxDurability > 0) {
            int oldDurability = durability;
            durability = Math.max(0, Math.min(maxDurability, durability + amount));

            if (oldDurability != durability) {
                notifyObservers();
            }
        }
    }public void addObserver(ItemDataObserver observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void removeObserver(ItemDataObserver observer) {
        observers.remove(observer);
    }


    private void notifyObservers() {
        for (ItemDataObserver observer : observers) {
            observer.onItemDataChanged(this);
        }
    }


    public int getDurability() {
        return durability;
    }

    public void setDurability(int durability) {
        this.durability = durability;
        if (this.maxDurability > 0 && this.durability > this.maxDurability) {
            this.durability = this.maxDurability;
        }
        notifyObservers();
    }

    public boolean hasDurability() {
        return maxDurability > 0;
    }

    private String normalizeItemId(String itemId) {
        String normalized = itemId.toLowerCase();
        if (!normalized.endsWith("_item")) {
            normalized += "_item";
        }
        return normalized;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId.toLowerCase(); // Normalize to lowercase

        // Retrieve item template using the consistent itemId
        Item itemTemplate = ItemManager.getItemTemplate(this.itemId);
        if (itemTemplate != null) {
            if (this.maxDurability == -1) {
                this.maxDurability = itemTemplate.getMaxDurability();
            }

            if (this.durability == -1) {
                this.durability = this.maxDurability;
            }

            if (this.maxDurability > 0 && this.durability > this.maxDurability) {
                this.durability = this.maxDurability;
            }
        }
    }



    public int getMaxDurability() {
        return maxDurability;
    }

    public void setMaxDurability(int maxDurability) {
        this.maxDurability = maxDurability;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        if (count < 0) {
            GameLogger.error("Attempted to set negative count: " + count);
            this.count = 0;
            return;
        }

        Item template = ItemManager.getItemTemplate(itemId);
        if (template != null && !template.isStackable()) {
            this.count = Math.min(1, count);
            GameLogger.info("Set count=1 for unstackable item: " + itemId);
        } else {
            this.count = Math.min(count, Item.MAX_STACK_SIZE);
            GameLogger.info("Set count=" + this.count + " for " + itemId);
        }
        notifyObservers(); // Notify when count changes
    }

    public UUID getUuid() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        return uuid;
    }


    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }


    public boolean isEmpty() {
        return this.count <= 0;
    }

    public ItemData copyWithUUID() {
        return new ItemData(this.itemId, this.count, UUID.randomUUID());
    }


    @Override
    public String toString() {
        return "ItemData{" +
            "itemId='" + itemId + '\'' +
            ", count=" + count +
            ", uuid=" + uuid +
            '}';
    }
    public float getDurabilityPercentage() {
        if (maxDurability > 0) {
            return (float) durability / (float) maxDurability;
        } else {
            return 0f;
        }
    }

    public ItemData copy() {
        ItemData copy = new ItemData(this.itemId, this.count, this.uuid);
        copy.setDurability(this.durability);
        copy.setMaxDurability(this.maxDurability);
        return copy;
    }


    public boolean isValid() {
        if (itemId == null || itemId.trim().isEmpty()) {
            return false;
        }
        if (count <= 0) {
            return false;
        }
        if (uuid == null) {
            uuid = UUID.randomUUID(); // Auto-generate UUID if missing
        }
        return ItemManager.getItem(itemId) != null; // Verify item exists in manager
    }

}
