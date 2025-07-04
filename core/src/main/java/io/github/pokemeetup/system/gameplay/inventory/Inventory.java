package io.github.pokemeetup.system.gameplay.inventory;

import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.InventoryObserver;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.InventorySlotData;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.ItemContainer;
import io.github.pokemeetup.utils.GameLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Inventory implements ItemContainer {
    public static final int INVENTORY_SIZE = 27;
    private final List<Slot> slots;
    private final Map<UUID, ItemData> itemTracker;
    private final Object inventoryLock = new Object();
    private final List<InventoryObserver> observers = new ArrayList<>();
    private InventorySlotData[] slotDataArray;

    public Inventory() {
        this.slots = new ArrayList<>(INVENTORY_SIZE);
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            slots.add(new Slot());
        }
        this.itemTracker = new ConcurrentHashMap<>();
        validateSlots();
        slotDataArray = new InventorySlotData[INVENTORY_SIZE];
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            slotDataArray[i] = new InventorySlotData(i, InventorySlotData.SlotType.INVENTORY, this);
        }
    }

    /**
     * [NEW] Checks if the inventory has enough of a specific item.
     * @param itemId The ID of the item to check.
     * @param requiredCount The amount required.
     * @return true if the inventory contains at least the required amount.
     */
    public boolean hasEnoughItems(String itemId, int requiredCount) {
        synchronized (inventoryLock) {
            int total = 0;
            for (Slot slot : slots) {
                ItemData item = slot.getItemData();
                if (item != null && item.getItemId().equals(itemId)) {
                    total += item.getCount();
                }
            }
            return total >= requiredCount;
        }
    }

    /**
     * [NEW] Removes a specified amount of an item from the inventory by its ID.
     * @param itemId The ID of the item to remove.
     * @param count The amount to remove.
     * @return true if the items were successfully removed.
     */
    public boolean removeItem(String itemId, int count) {
        synchronized (inventoryLock) {
            if (!hasEnoughItems(itemId, count)) {
                return false;
            }

            int remainingToRemove = count;
            for (int i = slots.size() - 1; i >= 0; i--) {
                if (remainingToRemove <= 0) break;

                Slot slot = slots.get(i);
                ItemData item = slot.getItemData();

                if (item != null && item.getItemId().equals(itemId)) {
                    int countInSlot = item.getCount();
                    if (countInSlot <= remainingToRemove) {
                        remainingToRemove -= countInSlot;
                        setItemAt(i, null);
                    } else {
                        item.setCount(countInSlot - remainingToRemove);
                        remainingToRemove = 0;
                        notifyObservers();
                    }
                }
            }
            return true;
        }
    }

    /**
     * [NEW] Checks if the inventory has space for a given item, considering stacking.
     * Does not modify the inventory.
     *
     * @param itemToAdd The item to check for.
     * @return True if the item can be fully added, false otherwise.
     */
    public boolean hasSpaceFor(ItemData itemToAdd) {
        if (itemToAdd == null) return true;
        synchronized (inventoryLock) {
            int remaining = itemToAdd.getCount();
            Item itemTemplate = ItemManager.getItemTemplate(itemToAdd.getItemId());
            boolean isStackable = (itemTemplate != null && itemTemplate.isStackable());
            if (isStackable) {
                for (Slot slot : slots) {
                    ItemData existing = slot.getItemData();
                    if (existing != null && existing.getItemId().equals(itemToAdd.getItemId()) && existing.getCount() < Item.MAX_STACK_SIZE) {
                        int space = Item.MAX_STACK_SIZE - existing.getCount();
                        int canAdd = Math.min(remaining, space);
                        remaining -= canAdd;
                        if (remaining <= 0) return true;
                    }
                }
            }
            for (Slot slot : slots) {
                if (slot.isEmpty()) {
                    remaining -= isStackable ? Item.MAX_STACK_SIZE : 1;
                    if (remaining <= 0) return true;
                }
            }

            return false;
        }
    }

    public ItemData getSelectedItem(int hotbarSlot) {
        synchronized (inventoryLock) {
            if (hotbarSlot >= 0 && hotbarSlot < 9) {
                Slot slot = slots.get(hotbarSlot);
                return slot != null ? slot.getItemData() : null;
            }
            return null;
        }
    }
    public boolean removeItem(ItemData itemData) {
        synchronized (inventoryLock) {
            for (Slot slot : slots) {
                ItemData slotItem = slot.getItemData();
                if (slotItem != null && slotItem.getUuid().equals(itemData.getUuid())) {
                    slot.setItemData(null);
                    itemTracker.remove(itemData.getUuid());
                    notifyObservers();
                    return true;
                }
            }
            return false;
        }
    }

    public ItemData getItemAt(int index) {
        synchronized (inventoryLock) {
            if (index < 0 || index >= INVENTORY_SIZE) {
                GameLogger.error("Invalid inventory slot index: " + index);
                return null;
            }

            Slot slot = slots.get(index);
            if (slot == null) {
                return null;
            }

            return slot.getItemData();
        }
    }

    public void update() {
        synchronized (inventoryLock) {
            for (Slot slot : slots) {
                ItemData item = slot.getItemData();
                if (item != null && (item.getCount() <= 0 || item.getUuid() == null)) {
                    itemTracker.remove(item.getUuid());
                    slot.setItemData(null);
                }
            }
            itemTracker.clear();
            for (Slot slot : slots) {
                ItemData item = slot.getItemData();
                if (item != null && item.getUuid() != null) {
                    itemTracker.put(item.getUuid(), item);
                }
            }
            for (Slot slot : slots) {
                ItemData item = slot.getItemData();
                if (item != null && item.getDurability() == 0) {
                    GameLogger.info("Item broken due to zero durability: " + item.getItemId());
                    itemTracker.remove(item.getUuid());
                    slot.setItemData(null);
                    notifyObservers();
                }
            }
        }
    }

    public void setItemAt(int index, ItemData itemData) {
        synchronized (inventoryLock) {
            if (index < 0 || index >= slots.size()) {
                GameLogger.error("Invalid slot index: " + index);
                return;
            }

            Slot slot = slots.get(index);
            if (slot == null) {
                slot = new Slot();
                slots.set(index, slot);
            }

            ItemData oldItem = slot.getItemData();
            if (oldItem != null && oldItem.getUuid() != null) {
                itemTracker.remove(oldItem.getUuid());
            }

            if (itemData != null) {
                if (itemData.getUuid() == null) {
                    itemData.setUuid(UUID.randomUUID());
                }
                Item itemTemplate = ItemManager.getItemTemplate(itemData.getItemId());
                if (itemTemplate != null && !itemTemplate.isStackable()) {
                    if (itemData.getCount() > 1) {
                        GameLogger.error("Attempted to set unstackable item with count > 1: " + itemData.getItemId());
                        itemData.setCount(1);
                    }
                }

                slot.setItemData(itemData);
                itemTracker.put(itemData.getUuid(), itemData);
            } else {
                slot.setItemData(null);
            }
            notifyObservers();
        }
    }

    @Override
    public int getSize() {
        return INVENTORY_SIZE;
    }

    public List<ItemData> getAllItems() {
        synchronized (inventoryLock) {
            List<ItemData> items = new ArrayList<>(INVENTORY_SIZE);
            for (Slot slot : slots) {
                if (slot == null) {
                    items.add(null);
                    continue;
                }
                items.add(slot.getItemData());
            }
            return items;
        }
    }


    public void setAllItems(List<ItemData> items) {
        if (items == null) {
            return;
        }
        synchronized (inventoryLock) {
            GameLogger.info("Setting all items - Received " +
                items.stream().filter(Objects::nonNull).count() + " items");
            for (int i = 0; i < INVENTORY_SIZE; i++) {
                if (i < items.size() && items.get(i) != null) {
                    ItemData item = items.get(i).copy(); // Make defensive copy
                    setItemAt(i, item);
                    GameLogger.info("Set item at " + i + ": " + item.getItemId() + " x" + item.getCount());
                } else {
                    setItemAt(i, null);
                }
            }

            notifyObservers();
            validateAndRepair();
        }
    }

    public boolean addItem(ItemData itemData) {
        if (itemData == null) return false;

        synchronized (inventoryLock) {
            try {
                Item itemTemplate = ItemManager.getItemTemplate(itemData.getItemId());
                if (itemTemplate == null) {
                    return false;
                }
                if (itemTemplate.isStackable()) {
                    int remainingCount = itemData.getCount();
                    GameLogger.info("Processing stackable item, count=" + remainingCount);
                    for (int i = 0; i < slots.size() && remainingCount > 0; i++) {
                        Slot slot = slots.get(i);
                        if (slot == null) {
                            slots.set(i, new Slot());
                            continue;
                        }

                        ItemData existingItem = slot.getItemData();
                        if (existingItem != null &&
                            existingItem.getItemId().equals(itemData.getItemId()) &&
                            existingItem.getCount() < Item.MAX_STACK_SIZE) {

                            int spaceInStack = Item.MAX_STACK_SIZE - existingItem.getCount();
                            int amountToAdd = Math.min(spaceInStack, remainingCount);
                            ItemData updatedItem = existingItem.copy();
                            updatedItem.setCount(existingItem.getCount() + amountToAdd);
                            slot.setItemData(updatedItem);

                            remainingCount -= amountToAdd;
                            GameLogger.info("Stacked " + amountToAdd + " in slot " + i +
                                ", remaining: " + remainingCount);
                        }
                    }
                    while (remainingCount > 0) {
                        Slot emptySlot = findEmptySlot();
                        if (emptySlot == null) {
                            GameLogger.info("No empty slots for remaining " + remainingCount + " items");
                            return remainingCount < itemData.getCount(); // Return true if we stacked anything
                        }

                        int stackSize = Math.min(remainingCount, Item.MAX_STACK_SIZE);
                        ItemData newStack = new ItemData(itemData.getItemId(), stackSize, UUID.randomUUID());
                        emptySlot.setItemData(newStack);

                        remainingCount -= stackSize;
                        GameLogger.info("Created new stack of " + stackSize + " in empty slot");
                    }

                    validateAndRepair();
                    notifyObservers();
                    return true;
                } else {
                    for (int i = 0; i < itemData.getCount(); i++) {
                        Slot emptySlot = findEmptySlot();
                        if (emptySlot == null) {
                            GameLogger.info("No empty slots for non-stackable item");
                            return i > 0; // Return true if we placed any items
                        }

                        ItemData singleItem = new ItemData(itemData.getItemId(), 1, UUID.randomUUID());
                        emptySlot.setItemData(singleItem);
                        GameLogger.info("Placed non-stackable item in empty slot");
                    }

                    validateAndRepair();
                    notifyObservers();
                    return true;
                }

            } catch (Exception e) {
                GameLogger.error("Error in addItem: " + e.getMessage());
                validateAndRepair();
                return false;
            }
        }
    }

    public InventorySlotData getSlotData(int index) {
        if (index >= 0 && index < slotDataArray.length) {
            return slotDataArray[index];
        }
        return null;
    }

    private Slot findEmptySlot() {
        for (Slot slot : slots) {
            if (slot == null) continue;
            if (slot.isEmpty()) return slot;
        }
        return null;
    }

    public void notifyObservers() {
        for (InventoryObserver observer : observers) {
            observer.onInventoryChanged();
        }
    }

    public void addObserver(InventoryObserver observer) {
        observers.add(observer);
    }


    public void validateAndRepair() {
        synchronized (inventoryLock) {
            try {
                if (slots.size() != INVENTORY_SIZE) {
                    GameLogger.error("Invalid slots size: " + slots.size());
                    while (slots.size() < INVENTORY_SIZE) {
                        slots.add(new Slot());
                    }
                }
                itemTracker.clear();
                int itemCount = 0;

                for (int i = 0; i < slots.size(); i++) {
                    Slot slot = slots.get(i);
                    if (slot == null) {
                        slots.set(i, new Slot());
                        continue;
                    }

                    ItemData item = slot.getItemData();
                    if (item != null) {
                        itemCount++;
                        if (item.getUuid() == null) {
                            item.setUuid(UUID.randomUUID());
                        }
                        itemTracker.put(item.getUuid(), item);
                        Item itemTemplate = ItemManager.getItemTemplate(item.getItemId());
                        if (itemTemplate != null) {
                            if (!itemTemplate.isStackable() && item.getCount() > 1) {
                                GameLogger.error("Unstackable item with count > 1 in slot " + i + ": " + item.getCount());
                                item.setCount(1);
                            } else if (item.getCount() <= 0 || item.getCount() > Item.MAX_STACK_SIZE) {
                                GameLogger.error("Invalid stack size in slot " + i + ": " + item.getCount());
                                if (item.getCount() <= 0) {
                                    slot.setItemData(null);
                                    itemCount--;
                                } else {
                                    item.setCount(Item.MAX_STACK_SIZE);
                                }
                            }
                        }
                    }
                }

            } catch (Exception e) {
                GameLogger.error("Error during inventory validation: " + e.getMessage());
            }
        }
    }

    public void clear() {
        synchronized (inventoryLock) {
            for (Slot slot : slots) {
                if (slot != null) {
                    slot.setItemData(null);
                }
            }
            itemTracker.clear();
        }
    }


    public Object getInventoryLock() {
        return inventoryLock;
    }

    public void load() {
        synchronized (inventoryLock) {
            validateSlots(); // Ensure slots are valid before loading
            validateAndRepair(); // Validate after loading
        }
    }

    private void validateSlots() {
        synchronized (inventoryLock) {
            boolean needsRepair = false;
            for (int i = 0; i < INVENTORY_SIZE; i++) {
                if (slots.get(i) == null) {
                    slots.set(i, new Slot());
                    needsRepair = true;
                    GameLogger.error("Repaired null slot at index " + i);
                }
            }

            if (needsRepair) {
                validateAndRepair(); // Run full validation after slot repair
            }
        }
    }


    public void removeItemAt(int index) {
        synchronized (inventoryLock) {
            if (index < 0 || index >= slots.size()) {
                return;
            }

            Slot slot = slots.get(index);
            ItemData removedItem = slot.getItemData();
            if (removedItem != null) {
                itemTracker.remove(removedItem.getUuid());
                slot.setItemData(null);
                notifyObservers();
            }
        }
    }

    public boolean isEmpty() {
        synchronized (inventoryLock) {
            return slots.stream().allMatch(Slot::isEmpty);
        }
    }


}
