package io.github.pokemeetup.system.gameplay.inventory;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;

import java.util.UUID;

public class Item {
    public static final int MAX_STACK_SIZE = 64;
    private boolean stackable = true;  // Default to stackable
    private int maxDurability = -1;    // -1 means no durability
    private int durability = -1;       // Current durability

    private UUID uuid; // Unique identifier for each Item instance
    private String name;
    private String iconName; // Name of the texture region
    private transient TextureRegion icon; // Marked transient to avoid serialization
    private int count = 1;
    private boolean isCraftingResult = false;

    public Item(String name) {
        this.name = name;
        this.uuid = UUID.randomUUID();
        Item template = ItemManager.getItemTemplate(name);
        if (template != null) {
            this.iconName = template.getIconName();
            this.icon = template.getIcon();
            this.stackable = template.isStackable();
            this.maxDurability = template.getMaxDurability();
            this.durability = template.getMaxDurability();
        } else {
            GameLogger.error("Failed to find template for item: " + name);
            this.iconName = "missing";
            this.icon = TextureManager.items.findRegion("stick_item");
        }
        this.count = 1;
    }

    public Item(String name, String iconName, TextureRegion icon) {
        this.name = name;
        this.iconName = iconName;
        this.icon = icon;
        this.uuid = UUID.randomUUID();
        if (name.toLowerCase().contains("axe")) {
            this.stackable = false;
            this.maxDurability = 100;  // Example durability for tools
            this.durability = 100;
        }
    }

    public Item(String name, String iconName, TextureRegion icon, int count) {
        this.name = name;
        this.iconName = iconName;
        this.icon = icon;
        this.count = count;
        this.uuid = UUID.randomUUID();
        if (name.toLowerCase().contains("axe")) {
            this.stackable = false;
            this.maxDurability = 100;  // Example durability for tools
            this.durability = 100;
        }
    }

    public Item() {
        this.uuid = UUID.randomUUID();
    }

    public Item(Item other) {
        this.name = other.name;
        this.count = other.count;
        this.icon = other.icon;
        this.uuid = other.uuid;
        this.stackable = other.stackable;
        this.maxDurability = other.maxDurability;
        this.durability = other.durability;
    }
    public int getDurability() {
        return durability;
    }

    public void setDurability(int durability) {
        this.durability = durability;
    }

    public boolean isStackable() {
        return stackable;
    }

    public void setStackable(boolean stackable) {
        this.stackable = stackable;
    }

    public int getMaxDurability() {
        return maxDurability;
    }

    public void setMaxDurability(int maxDurability) {
        this.maxDurability = maxDurability;
    }

    public boolean isCraftingResult() {
        return isCraftingResult;
    }

    public void setCraftingResult(boolean craftingResult) {
        isCraftingResult = craftingResult;
    }

    public boolean isBlock() {
        for (PlaceableBlock.BlockType type : PlaceableBlock.BlockType.values()) {
            if (getName().equalsIgnoreCase(type.getId())) {
                return true;
            }
        }
        return false;
    }



    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public int getCount() {
        GameLogger.info("Item '" + name + "' getCount() returning: " + this.count);
        return this.count;
    }
    public void setCount(int count) {
        if (count < 0) {
            GameLogger.error("Attempted to set negative count for item '" + name + "': " + count);
            this.count = 0;
        } else {
            this.count = Math.min(count, MAX_STACK_SIZE);
        }
        GameLogger.info("Item '" + name + "' count set to: " + this.count);
    }


    public boolean isEmpty() {
        return this.count <= 0;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIconName() {
        return iconName;
    }

    public void setIconName(String iconName) {
        this.iconName = iconName;
    }

    public TextureRegion getIcon() {
        if (icon == null) {
            icon = TextureManager.items.findRegion(name.toLowerCase() + "_item");
            if (icon != null) {
                GameLogger.info("Loaded icon for " + name + " from TextureManager");
            } else {
                GameLogger.error("Could not find icon for " + name);
            }
        }
        return icon;
    }

    public void setIcon(TextureRegion icon) {
        if (icon == null) {
            GameLogger.error("Attempted to set null icon for item: " + name);
            return;
        }
        this.icon = icon;
        GameLogger.info("Set icon for item: " + name);
    }

    public Item copy() {
        return new Item(this);
    }

    @Override
    public String toString() {
        return "Item{" +
            "name='" + name + '\'' +
            ", count=" + count +
            ", uuid=" + uuid +
            '}';
    }

}
