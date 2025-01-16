package io.github.pokemeetup.system.gameplay.inventory.crafting;

import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.InventorySlotData;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.ItemContainer;

import java.util.ArrayList;
import java.util.List;

public class CraftingGrid implements ItemContainer {
    private final int size;
    private final ItemData[] gridItems;

    // Observer list
    private final List<CraftingGridObserver> observers = new ArrayList<>();

    public CraftingGrid(int size) {
        this.size = size;
        this.gridItems = new ItemData[size];
    }

    @Override
    public ItemData getItemAt(int index) {
        if (index >= 0 && index < size) {
            return gridItems[index];
        }
        return null;
    }

    public List<ItemData> getAllItems() {
        List<ItemData> items = new ArrayList<>();
        for (ItemData item : gridItems) {
            items.add(item); // Include nulls to maintain grid structure
        }
        return items;
    }

    @Override
    public void setItemAt(int index, ItemData item) {
        if (index >= 0 && index < size) {
            gridItems[index] = item;
            notifyObservers();
        }
    }

    @Override
    public int getSize() {
        return size;
    }

    @Override
    public InventorySlotData getSlotData(int index) {
        return null;
    }

    public void clear() {
        for (int i = 0; i < size; i++) {
            gridItems[i] = null;
        }
        notifyObservers();
    }

    // Observer pattern implementation
    public void addObserver(CraftingGridObserver observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void removeObserver(CraftingGridObserver observer) {
        observers.remove(observer);
    }

    private void notifyObservers() {
        for (CraftingGridObserver observer : observers) {
            observer.onCraftingGridChanged();
        }
    }

    // Observer interface
    public interface CraftingGridObserver {
        void onCraftingGridChanged();
    }
}
