package io.github.pokemeetup.utils.storage;

import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.utils.GameLogger;

import java.util.*;

public class InventoryConverter {



    public static ItemData itemToItemData(Item item) {
        if (item == null) {
            return null;
        }

        ItemData itemData = new ItemData();
        itemData.setItemId(item.getName());
        itemData.setCount(item.getCount());
        itemData.setUuid(item.getUuid() != null ? item.getUuid() : UUID.randomUUID());
        itemData.setDurability(item.getDurability());
        itemData.setMaxDurability(item.getMaxDurability());

        GameLogger.info("Converting Item to ItemData: " + item.getName() + " x" + item.getCount());
        return itemData;
    }

    public static Item itemDataToItem(ItemData itemData) {
        if (itemData == null) {
            return null;
        }
        Item item = new Item(itemData.getItemId());
        item.setCount(itemData.getCount());
        item.setUuid(itemData.getUuid() != null ? itemData.getUuid() : UUID.randomUUID());
        item.setDurability(itemData.getDurability());
        item.setMaxDurability(itemData.getMaxDurability());

        GameLogger.info("Converting ItemData to Item: " + itemData.getItemId() + " x" + itemData.getCount());
        return item;
    }

        public static void extractInventoryDataFromPlayer(Player player, PlayerData playerData) {
            if (player == null || playerData == null) {
                GameLogger.error("Cannot extract inventory from null Player or PlayerData");
                return;
            }

            try {
                Inventory inventory = player.getInventory();
                if (inventory == null) {
                    GameLogger.error("Player inventory is null");
                    playerData.setInventoryItems(new ArrayList<>(Collections.nCopies(Inventory.INVENTORY_SIZE, null)));
                    return;
                }

                List<ItemData> items = new ArrayList<>(Inventory.INVENTORY_SIZE);
                for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
                    ItemData item = inventory.getItemAt(i);
                    if (item != null && item.isValid()) {
                        items.add(item.copy()); // Create deep copy
                    } else {
                        items.add(null);
                    }
                }
                playerData.setInventoryItems(items);
                GameLogger.info("Extracted " + items.stream().filter(Objects::nonNull).count() +
                    " items from inventory");
            } catch (Exception e) {
                GameLogger.error("Error extracting inventory data: " + e.getMessage());
                e.printStackTrace();
            }
        }


    @Deprecated
    public static List<String> toPlayerDataFormat(List<ItemData> items) {
        List<String> itemStrings = new ArrayList<>();
        for (ItemData item : items) {
            if (item != null) {
                itemStrings.add(item.getItemId() + ":" + item.getCount());
            } else {
                itemStrings.add(null);
            }
        }
        return itemStrings;
    }
    public static boolean addItemToInventory(Inventory inventory, ItemData newItem) {
        if (inventory == null || newItem == null) {
            GameLogger.error("Inventory or newItem is null. Cannot add item.");
            return false;
        }

        synchronized (inventory) {
            boolean added = inventory.addItem(newItem.copyWithUUID());
            if (added) {
                GameLogger.info("Item added to inventory successfully: " + newItem.getItemId());
            } else {
                GameLogger.error("Failed to add item to inventory: " + newItem.getItemId());
            }
            return added;
        }
    }
}
