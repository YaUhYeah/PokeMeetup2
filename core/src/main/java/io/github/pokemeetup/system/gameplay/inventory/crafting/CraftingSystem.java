package io.github.pokemeetup.system.gameplay.inventory.crafting;

import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.InventorySlotDataObserver;
import io.github.pokemeetup.utils.GameLogger;

import java.util.*;

public class CraftingSystem implements CraftingGrid.CraftingGridObserver {
    private final CraftingGrid craftingGrid;
    private final Object craftingLock = new Object();
    private final List<InventorySlotDataObserver>[] slotObservers;
    private final List<CraftingSystemObserver> observers = new ArrayList<>();
    private final Inventory inventory;
    private final int gridSize;
    private ItemData resultSlot;

    public CraftingSystem(Inventory inventory, int gridSize, CraftingGrid craftingGrid) {
        this.inventory = inventory;
        this.gridSize = gridSize;
        this.craftingGrid = craftingGrid;
        this.craftingGrid.addObserver(this); // Register as observer
        this.slotObservers = new List[gridSize * gridSize];
        for (int i = 0; i < gridSize * gridSize; i++) {
            slotObservers[i] = new ArrayList<>();
        }
    }

    public void returnItemsToInventory() {
        synchronized (craftingLock) {
            for (int i = 0; i < craftingGrid.getSize(); i++) {
                ItemData item = craftingGrid.getItemAt(i);
                if (item != null) {
                    boolean added = inventory.addItem(item.copy());
                    if (added) {
                        craftingGrid.setItemAt(i, null);
                        notifySlotObservers(i);
                    } else {
                        GameLogger.error("Inventory full, cannot return item: " + item.getItemId());
                    }
                }
            }
            updateCraftingResult();
        }
    }

    public void addObserver(CraftingSystemObserver observer) {
        observers.add(observer);
    }

    public void addSlotObserver(int index, InventorySlotDataObserver observer) {
        if (index >= 0 && index < craftingGrid.getSize()) {
            slotObservers[index].add(observer);
        }
    }

    public void updateCraftingResult() {
        synchronized (craftingLock) {
            resultSlot = checkRecipes();
            notifyCraftingResultChanged();
        }
    }


    private void notifySlotObservers(int index) {
        if (index >= 0 && index < slotObservers.length) {
            List<InventorySlotDataObserver> observers = slotObservers[index];
            if (observers != null) {
                for (InventorySlotDataObserver observer : observers) {
                    if (observer != null) {
                        observer.onSlotDataChanged();
                    }
                }
            }
        }
    }

    public boolean craftOneItem() {
        synchronized (craftingLock) {
            if (resultSlot == null) return false;

            // Consume ingredients
            Map<String, Integer> requiredItems = getRequiredItemsForCurrentRecipe();
            if (requiredItems == null) return false; // No recipe found

            // Check if the grid has sufficient items for one craft
            for (Map.Entry<String, Integer> entry : requiredItems.entrySet()) {
                String itemId = entry.getKey();
                int requiredCount = entry.getValue();
                int availableCount = getTotalItemCountInGrid(itemId);

                if (availableCount < requiredCount) {
                    GameLogger.error("Not enough " + itemId + " to craft one " + resultSlot.getItemId());
                    return false;
                }
            }

            // Consume the required items for one craft
            for (Map.Entry<String, Integer> entry : requiredItems.entrySet()) {
                String itemId = entry.getKey();
                int requiredCount = entry.getValue();
                consumeItemsFromGrid(itemId, requiredCount);
            }

            // Update crafting result based on the new grid state
            updateCraftingResult();

            AudioManager.getInstance().playSound(AudioManager.SoundEffect.CRAFT);

            return true;
        }
    }


    private int getTotalItemCountInGrid(String itemId) {
        int totalCount = 0;
        for (ItemData item : craftingGrid.getAllItems()) {
            if (item != null && item.getItemId().equalsIgnoreCase(itemId)) {
                totalCount += item.getCount();
            }
        }
        return totalCount;
    }

    public void setItemInGrid(int index, ItemData item) {
        synchronized (craftingLock) {
            if (index < 0 || index >= craftingGrid.getSize()) {
                GameLogger.error("Invalid grid index: " + index);
                return;
            }

            GameLogger.info("Setting item in grid index " + index + ": " +
                (item != null ? item.getItemId() + " x" + item.getCount() : "null"));

            // Only copy if we're actually placing the item, not when removing
            if (item != null) {
                // Get the current item in the slot
                ItemData currentItem = craftingGrid.getItemAt(index);

                // If the slot already has the same type of item, update its count instead of creating new
                if (currentItem != null && currentItem.getItemId().equals(item.getItemId())) {
                    currentItem.setCount(item.getCount());
                    GameLogger.info("Updated existing item count to: " + currentItem.getCount());
                } else {
                    // If it's a different item or empty slot, place a copy
                    ItemData itemCopy = item.copy();
                    craftingGrid.setItemAt(index, itemCopy);
                    GameLogger.info("Placed new item in grid: " + itemCopy.getItemId() + " x" + itemCopy.getCount());
                }
            } else {
                // If we're removing the item (item is null), just set it directly
                craftingGrid.setItemAt(index, null);
                GameLogger.info("Removed item from grid index: " + index);
            }

            notifySlotObservers(index);
            updateCraftingResult();
        }
    }



    private boolean isCraftingTableRecipe3x3() {
        // We'll assume the crafting table is crafted by placing 4 sticks in a 2x2 pattern anywhere in the grid
        for (int row = 0; row <= gridSize - 2; row++) {
            for (int col = 0; col <= gridSize - 2; col++) {
                if (check2x2PatternAt(row, col)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean itemMatches(ItemData itemData, String itemId) {
        return itemData != null && itemData.getItemId().equalsIgnoreCase(itemId);
    }


    private ItemData checkRecipes() {
        ItemData result;

        if (gridSize >= 2) {
            result = check2x2Recipes();
            if (result != null) return result;
        }

        if (gridSize >= 3) {
            result = check3x3Recipes();
            return result;
        }

        return null;
    }


    private boolean isWoodenAxeRecipe() {
        // Ensure grid size is at least 3x3
        if (gridSize < 3) return false;

        for (int row = 0; row <= gridSize - 3; row++) {
            for (int col = 0; col <= gridSize - 2; col++) {
                if (checkWoodenAxePatternAt(row, col, false) || checkWoodenAxePatternAt(row, col, true)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkWoodenAxePatternAt(int row, int col, boolean mirrored) {
        // Define the pattern for the axe
        String[][] pattern = mirrored ? new String[][]{
            {ItemManager.ItemIDs.WOODEN_PLANKS, ItemManager.ItemIDs.WOODEN_PLANKS, null},
            {ItemManager.ItemIDs.STICK, ItemManager.ItemIDs.WOODEN_PLANKS, null},
            {ItemManager.ItemIDs.STICK, null, null}
        } : new String[][]{
            {null, ItemManager.ItemIDs.WOODEN_PLANKS, ItemManager.ItemIDs.WOODEN_PLANKS},
            {null, ItemManager.ItemIDs.STICK, ItemManager.ItemIDs.WOODEN_PLANKS},
            {null, ItemManager.ItemIDs.STICK, null}
        };

        Set<Integer> usedIndices = new HashSet<>();

        for (int r = 0; r < pattern.length; r++) {
            for (int c = 0; c < pattern[r].length; c++) {
                int gridRow = row + r;
                int gridCol = col + c;
                if (gridRow >= gridSize || gridCol >= gridSize) {
                    return false;
                }

                int index = gridRow * gridSize + gridCol;
                usedIndices.add(index);

                String expectedItem = pattern[r][c];
                ItemData actualItem = craftingGrid.getItemAt(index);

                if (expectedItem == null) {
                    if (actualItem != null) return false;
                } else {
                    if (!itemMatches(actualItem, expectedItem)) return false;
                }
            }
        }

        // Ensure all other slots are empty
        return otherSlotsEmpty(usedIndices);
    }

    private boolean check2x2PatternAt(int row, int col) {
        int index1 = row * gridSize + col;
        int index2 = row * gridSize + (col + 1);
        int index3 = (row + 1) * gridSize + col;
        int index4 = (row + 1) * gridSize + (col + 1);

        // Ensure indices are within bounds
        if (index1 >= craftingGrid.getSize() || index2 >= craftingGrid.getSize() ||
            index3 >= craftingGrid.getSize() || index4 >= craftingGrid.getSize()) {
            return false;
        }

        return itemMatches(craftingGrid.getItemAt(index1), "Stick") &&
            itemMatches(craftingGrid.getItemAt(index2), "Stick") &&
            itemMatches(craftingGrid.getItemAt(index3), "Stick") &&
            itemMatches(craftingGrid.getItemAt(index4), "Stick") &&
            otherSlotsEmpty(Set.of(index1, index2, index3, index4));
    }

    private ItemData check3x3Recipes() {
        if (isWoodenAxeRecipe()) {
            return new ItemData(ItemManager.ItemIDs.WOODEN_AXE, 1, UUID.randomUUID());
        }
        if (isCraftingTableRecipe3x3()) {
            return new ItemData(ItemManager.ItemIDs.CRAFTING_TABLE, 1, UUID.randomUUID());
        }
        // Add more recipes here as needed
        return null;
    }


    private Map<String, Integer> getRequiredItemsForCurrentRecipe() {
        if (resultSlot == null) return null;

        Map<String, Integer> requiredItems = new HashMap<>();

        if (resultSlot.getItemId().equalsIgnoreCase(ItemManager.ItemIDs.WOODEN_AXE)) {
            requiredItems.put(ItemManager.ItemIDs.WOODEN_PLANKS, 3);
            requiredItems.put(ItemManager.ItemIDs.STICK, 2);
        } else if (resultSlot.getItemId().equalsIgnoreCase(ItemManager.ItemIDs.CRAFTING_TABLE)) {
            requiredItems.put(ItemManager.ItemIDs.WOODEN_PLANKS, 4);
        } else if (resultSlot.getItemId().equalsIgnoreCase(ItemManager.ItemIDs.STICK)) {
            requiredItems.put(ItemManager.ItemIDs.WOODEN_PLANKS, 2);
        }

        return requiredItems;
    }

    private boolean otherSlotsEmpty(Set<Integer> usedIndices) {
        for (int i = 0; i < craftingGrid.getSize(); i++) {
            if (!usedIndices.contains(i) && craftingGrid.getItemAt(i) != null) {
                return false;
            }
        }
        return true;
    }

    public ItemData getItemInGrid(int index) {
        synchronized (craftingLock) {
            if (index < 0 || index >= craftingGrid.getSize()) {
                return null;
            }
            return craftingGrid.getItemAt(index);
        }
    }

    private ItemData check2x2Recipes() {
        int patternHeight = 2;
        int patternWidth = 1; // For the stick recipe

        for (int row = 0; row <= gridSize - patternHeight; row++) {
            for (int col = 0; col <= gridSize - patternWidth; col++) {
                if (isStickRecipeAt(row, col)) {
                    GameLogger.info("Stick recipe matched at position (" + row + ", " + col + ").");
                    return new ItemData(ItemManager.ItemIDs.STICK, 4, UUID.randomUUID()); // 4 sticks
                }

                if (isCraftingTableRecipe2x2At(row, col)) {
                    GameLogger.info("Crafting table recipe matched at position (" + row + ", " + col + ").");
                    return new ItemData(ItemManager.ItemIDs.CRAFTING_TABLE, 1, UUID.randomUUID()); // 1 table
                }
            }
        }
        return null;
    }

    private boolean isStickRecipeAt(int row, int col) {
        int patternHeight = 2;
        int patternWidth = 1;

        // Check if the pattern fits in the grid
        if (row + patternHeight > gridSize || col + patternWidth > gridSize) {
            return false;
        }

        Set<Integer> usedIndices = new HashSet<>();

        for (int r = 0; r < patternHeight; r++) {
            int gridRow = row + r;

            int index = gridRow * gridSize + col;
            usedIndices.add(index);

            ItemData actualItem = craftingGrid.getItemAt(index);
            if (!itemMatches(actualItem, ItemManager.ItemIDs.WOODEN_PLANKS)) {
                return false;
            }
        }

        // Ensure all other slots are empty
        return otherSlotsEmpty(usedIndices);
    }

    private boolean isCraftingTableRecipe2x2At(int row, int col) {
        int patternHeight = 2;
        int patternWidth = 2;

        // Check if the pattern fits in the grid
        if (row + patternHeight > gridSize || col + patternWidth > gridSize) {
            return false;
        }

        Set<Integer> usedIndices = new HashSet<>();

        for (int r = 0; r < patternHeight; r++) {
            for (int c = 0; c < patternWidth; c++) {
                int gridRow = row + r;
                int gridCol = col + c;

                int index = gridRow * gridSize + gridCol;
                usedIndices.add(index);

                ItemData actualItem = craftingGrid.getItemAt(index);
                if (!itemMatches(actualItem, ItemManager.ItemIDs.WOODEN_PLANKS)) {
                    return false;
                }
            }
        }

        // Ensure all other slots are empty
        return otherSlotsEmpty(usedIndices);
    }


    public ItemData getCraftingResult() {
        synchronized (craftingLock) {
            if (resultSlot == null) return null;

            int maxCraftable = calculateMaxCraftableAmount();
            if (maxCraftable <= 0) return null;

            ItemData result = resultSlot.copy();
            result.setCount(maxCraftable);
            return result;
        }
    }

    private int calculateMaxCraftableAmount() {
        Map<String, Integer> requiredItems = getRequiredItemsForCurrentRecipe();
        if (requiredItems == null) return 0;

        Map<String, Integer> itemsInGrid = new HashMap<>();
        for (ItemData item : craftingGrid.getAllItems()) {
            if (item != null) {
                itemsInGrid.put(item.getItemId(),
                    itemsInGrid.getOrDefault(item.getItemId(), 0) + item.getCount());
            }
        }

        int maxCraftable = Integer.MAX_VALUE;
        for (Map.Entry<String, Integer> entry : requiredItems.entrySet()) {
            String itemId = entry.getKey();
            int requiredCount = entry.getValue();
            int availableCount = itemsInGrid.getOrDefault(itemId, 0);

            int craftableWithItem = availableCount / requiredCount;
            if (craftableWithItem < maxCraftable) {
                maxCraftable = craftableWithItem;
            }
        }
        return maxCraftable;
    }


    private void consumeItemsFromGrid(String itemId, int requiredCount) {
        int countToConsume = requiredCount;

        for (int i = 0; i < craftingGrid.getSize(); i++) {
            ItemData item = craftingGrid.getItemAt(i);
            if (item != null && item.getItemId().equalsIgnoreCase(itemId)) {
                int itemCount = item.getCount();
                if (itemCount <= countToConsume) {
                    // Consume the whole stack
                    countToConsume -= itemCount;
                    craftingGrid.setItemAt(i, null); // Clear the slot
                } else {
                    // Consume part of the stack
                    item.setCount(itemCount - countToConsume);
                    countToConsume = 0;
                }
                notifySlotObservers(i);

                if (countToConsume <= 0) {
                    break;
                }
            }
        }

        if (countToConsume > 0) {
            GameLogger.error("consumeItemsFromGrid: Insufficient items in grid for item: " + itemId);
        }
    }


    private void notifyCraftingResultChanged() {
        for (CraftingSystemObserver observer : observers) {
            observer.onCraftingResultChanged(resultSlot);
        }
    }

    @Override
    public void onCraftingGridChanged() {
        updateCraftingResult();
    }

    public interface CraftingSystemObserver {
        void onCraftingResultChanged(ItemData newResult);
    }
}
