package io.github.pokemeetup.screens;

import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.data.ChestData;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.gameplay.inventory.crafting.CraftingSystem;

public interface InventoryScreenInterface {
    Inventory getInventory();
    Player getPlayer();
    void refreshScreens(); // <-- ADD THIS METHOD

    void updateHeldItemDisplay();
    Item getHeldItemObject();  // Add this method
    ItemData getHeldItem();
    void setHeldItem(Item item);
    CraftingSystem getCraftingSystem();
    ChestData getChestData();
    Vector2 getChestPosition(); // For real-time chest synchronization

    // Pending operation tracking to prevent spam-click duplication
    default boolean hasPendingOperation() {
        return false;
    }

    default void setPendingOperation(boolean pending) {
        // Default implementation does nothing (for non-chest screens)
    }
}
