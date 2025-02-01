package io.github.pokemeetup.system.gameplay.inventory.crafting;

import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.InventorySlotData;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.InventorySlotDataObserver;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.ItemContainer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CraftingGrid implements ItemContainer {
    private final int size;
    private final ItemData[] gridItems;
    private final List<CraftingGridObserver> observers = new ArrayList<>();
    private final List<InventorySlotDataObserver>[] slotObservers;
    private final InventorySlotData[] slotData;

    @SuppressWarnings("unchecked")
    public CraftingGrid(int size) {
        this.size = size;
        this.gridItems = new ItemData[size];
        this.slotObservers = new List[size];
        this.slotData = new InventorySlotData[size];

        for (int i = 0; i < size; i++) {
            slotObservers[i] = new ArrayList<>();
            slotData[i] = new InventorySlotData(i, InventorySlotData.SlotType.CRAFTING, this);
        }
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
        Collections.addAll(items, gridItems);
        return items;
    }

    @Override
    public void setItemAt(int index, ItemData item) {
        if (index >= 0 && index < size) {
            gridItems[index] = item;
            notifySlotObservers(index);
            notifyObservers();
        }
    }

    @Override
    public int getSize() {
        return size;
    }

    @Override
    public InventorySlotData getSlotData(int index) {
        if (index >= 0 && index < size) {
            return slotData[index];
        }
        return null;
    }

    public void clear() {
        for (int i = 0; i < size; i++) {
            gridItems[i] = null;
            notifySlotObservers(i);
        }
        notifyObservers();
    }

    public void addObserver(CraftingGridObserver observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void addSlotObserver(int index, InventorySlotDataObserver observer) {
        if (index >= 0 && index < size) {
            slotObservers[index].add(observer);
        }
    }

    public void removeSlotObserver(int index, InventorySlotDataObserver observer) {
        if (index >= 0 && index < size) {
            slotObservers[index].remove(observer);
        }
    }

    public void removeObserver(CraftingGridObserver observer) {
        observers.remove(observer);
    }

    private void notifySlotObservers(int index) {
        if (index >= 0 && index < size) {
            for (InventorySlotDataObserver observer : slotObservers[index]) {
                observer.onSlotDataChanged();
            }
        }
    }

    private void notifyObservers() {
        for (CraftingGridObserver observer : observers) {
            observer.onCraftingGridChanged();
        }
    }

    public interface CraftingGridObserver {
        void onCraftingGridChanged();
    }
}
