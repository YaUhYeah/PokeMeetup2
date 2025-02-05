package io.github.pokemeetup.screens;

import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.data.ChestData;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.gameplay.inventory.crafting.CraftingSystem;

public interface InventoryScreenInterface {
    Inventory getInventory();
    Player getPlayer();
    void updateHeldItemDisplay();
    Item getHeldItemObject();  // Add this method
    ItemData getHeldItem();
    void setHeldItem(Item item);
    CraftingSystem getCraftingSystem();
    ChestData getChestData();
}
