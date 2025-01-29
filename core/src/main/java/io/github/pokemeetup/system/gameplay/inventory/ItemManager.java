package io.github.pokemeetup.system.gameplay.inventory;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.utils.GameLogger;

import java.util.*;

public class ItemManager {
    private static final Map<String, Item> items = new HashMap<>();
    private static final String DEFAULT_TEXTURE = "missing_texture";
    private static boolean initialized = false;
    private static boolean isServerMode = false;

    public static void setServerMode(boolean serverMode) {
        isServerMode = serverMode;
        initialized = false; // Reset initialization to allow server-specific init
    }

    public static void initialize(TextureAtlas atlas) {
        if (initialized) {
            return;
        }
        if (isServerMode && atlas == null) {
            initializeServerItems();
            return;
        }

        if (atlas == null) {
            GameLogger.error("Cannot initialize ItemManager - atlas is null");
            return;
        }

        GameLogger.info("Initializing ItemManager with atlas...");
        logAvailableRegions(atlas);

        // Register standard items...
        Map<String, String> standardItems = new HashMap<>();
        standardItems.put(ItemIDs.POTION, "potion_item");
        standardItems.put(ItemIDs.ELIXIR, "elixir_item");
        standardItems.put(ItemIDs.POKEBALL, "pokeball_item");
        standardItems.put(ItemIDs.WOODEN_AXE, "wooden_axe_item");
        standardItems.put(ItemIDs.STICK, "stick_item");

        // Register block items
        for (PlaceableBlock.BlockType blockType : PlaceableBlock.BlockType.values()) {
            String itemId = blockType.getId().toLowerCase();
            String textureKey = itemId + "_item";
            standardItems.put(itemId, textureKey);
            GameLogger.info("Registered block item: " + itemId);
        }

        for (Map.Entry<String, String> entry : standardItems.entrySet()) {
            String itemId = entry.getKey().toLowerCase();
            String textureKey = entry.getValue();

            TextureRegion texture = getTextureWithFallbacks(atlas, textureKey, itemId);
            if (texture != null) {
                Item item = new Item(itemId, textureKey, texture);


                if (itemId.equals(ItemIDs.WOODEN_AXE)) {
                    item.setStackable(false);
                    item.setMaxDurability(100);
                    item.setDurability(100);
                } else {
                    item.setStackable(true);
                    item.setMaxDurability(-1);
                }

                items.put(itemId, item); // Use normalized itemId without "_item"
                GameLogger.info(String.format("Initialized item: %s with texture %s", itemId, textureKey));
            }
        }

        initialized = true;
        validateItems();
        logInitializationSummary();
    }

    private static void initializeServerItems() {
        GameLogger.info("Initializing ItemManager in server mode...");

        // Register standard items
        Map<String, String> standardItems = new HashMap<>();
        standardItems.put(ItemIDs.POTION, "potion");
        standardItems.put(ItemIDs.ELIXIR, "elixir");
        standardItems.put(ItemIDs.POKEBALL, "pokeball");
        standardItems.put(ItemIDs.WOODEN_AXE, "wooden_axe");
        standardItems.put(ItemIDs.STICK, "stick");

        // Register block items
        for (PlaceableBlock.BlockType blockType : PlaceableBlock.BlockType.values()) {
            String itemId = blockType.getId().toLowerCase();
            standardItems.put(itemId, itemId);
        }

        for (Map.Entry<String, String> entry : standardItems.entrySet()) {
            String itemId = entry.getKey().toLowerCase();
            // Create items without textures in server mode
            Item item = new Item(itemId, entry.getValue(), null);

            if (itemId.equals(ItemIDs.WOODEN_AXE)) {
                item.setStackable(false);
                item.setMaxDurability(100);
                item.setDurability(100);
            } else {
                item.setStackable(true);
                item.setMaxDurability(-1);
            }

            items.put(itemId, item);
            GameLogger.info("Initialized server item: " + itemId);
        }

        initialized = true;
        GameLogger.info("Server mode ItemManager initialization complete: " + items.size() + " items");
    }

    private static TextureRegion getTextureWithFallbacks(TextureAtlas atlas, String primaryKey, String itemId) {
        TextureRegion texture;
        String[] attempts = new String[]{
            primaryKey,
            itemId + "_item",
            itemId.toLowerCase() + "_item",
            itemId,
            itemId.toLowerCase(),
            DEFAULT_TEXTURE
        };

        for (String key : attempts) {
            texture = atlas.findRegion(key);
            if (texture != null) {
                GameLogger.info(String.format("Found texture for %s using key: %s", itemId, key));
                return texture;
            }
        }

        GameLogger.error(String.format("Failed to find any texture for item: %s", itemId));
        return null;
    }

    public static Item getItemTemplate(String itemId) {
        if (!initialized) {
            GameLogger.error("Attempting to get item before ItemManager initialization");
            return null;
        }
        return items.get(itemId);
    }

    public static Item getItem(String itemId) {
        if (!initialized) {
            if (isServerMode) {
                initialize(null); // Auto-initialize for server
            } else {
                GameLogger.error("Attempting to get item before ItemManager initialization");
                return null;
            }
        }

        if (itemId == null) {
            GameLogger.error("Null itemId provided to getItem");
            return null;
        }

        String normalizedId = itemId.toLowerCase().replace("_item", "");
        Item baseItem = items.get(normalizedId);

        if (baseItem == null) {
            GameLogger.error("No item found with ID: " + normalizedId);
            return null;
        }

        if (!isServerMode && baseItem.getIcon() == null) {
            GameLogger.error("Item found but missing texture: " + itemId);
            return null;
        }

        return baseItem.copy();
    }


    public static void validateItems() {
        GameLogger.info("Validating initialized items...");
        if (isServerMode) {
            // Simple validation for server mode
            GameLogger.info("Validating server items...");
            for (Map.Entry<String, Item> entry : items.entrySet()) {
                GameLogger.info("Validated server item: " + entry.getKey());
            }
            return;
        }
        for (Map.Entry<String, Item> entry : items.entrySet()) {
            Item item = entry.getValue();
            if (item.getIcon() == null) {
                GameLogger.error(String.format("Item %s is missing texture", entry.getKey()));
                continue;
            }

            if (!item.getIcon().getTexture().isManaged()) {
                GameLogger.error(String.format("Item %s has invalid texture state", entry.getKey()));
            }

            GameLogger.info(String.format("Validated item: %s (texture: %dx%d)",
                entry.getKey(),
                item.getIcon().getRegionWidth(),
                item.getIcon().getRegionHeight()));
        }
    }

    public static Collection<String> getAllItemNames() {
        if (!initialized) {
            GameLogger.error("Attempting to get item names before initialization");
            return Collections.emptyList();
        }
        return new ArrayList<>(items.keySet());
    }

    public static List<String> getAllFindableItemNames() {
        List<String> itemsFindable = new ArrayList<>();
        if (!initialized) {
            GameLogger.error("Attempting to get item names before initialization");
            return Collections.emptyList();
        }
        itemsFindable.add("pokeball");
        itemsFindable.add("stick");
        itemsFindable.add("potion");
        itemsFindable.add("elixir");

        return itemsFindable;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    // Helper methods
    private static void logAvailableRegions(TextureAtlas atlas) {
        GameLogger.info("Available regions in atlas:");
        for (TextureAtlas.AtlasRegion region : atlas.getRegions()) {
            GameLogger.info(String.format("- %s (%dx%d)",
                region.name, region.getRegionWidth(), region.getRegionHeight()));
        }
    }

    private static void logInitializationSummary() {
        GameLogger.info(String.format("ItemManager initialization complete: %d items loaded",
            items.size()));
        GameLogger.info("Loaded items: " + String.join(", ", items.keySet()));
    }

    public static final class ItemIDs {
        public static final String POTION = "potion";
        public static final String HOUSE_PLANKS = "house_planks";
        public static final String ELIXIR = "elixir";
        public static final String POKEBALL = "pokeball";
        public static final String STICK = "stick";
        public static final String CRAFTING_TABLE = "craftingtable";
        public static final String FURNACE = "furnace";
        public static final String WOODEN_PLANKS = "wooden_planks";
        public static final String WOODEN_DOOR = "wooden_door";
        public static final String ROOF_CORNER = "roof_corner";
        public static final String HOUSE_PART = "house_part";
        public static final String HOUSE_MIDDLE_PART = "house_middlesection_part";
        public static final String HOUSE_MIDDLE_PART_0 = "house_midsection_part";
        public static final String HOUSE_MIDDLE_PART_1 = "house_middlesection";
        public static final String ROOF_CORNER_1 = "roof_middle_part";
        public static final String ROOF_MIDDLE = "roof_middle";
        public static final String ROOFINNER = "roofinner";
        public static final String ROOF_INSIDE = "roof_middle_outside";
        public static final String CHEST = "chest";
        public static final String WOODEN_AXE = "wooden_axe";
    }
}
