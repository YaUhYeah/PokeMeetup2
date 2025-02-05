package io.github.pokemeetup.system.gameplay.inventory.crafting;

import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.InventorySlotDataObserver;
import io.github.pokemeetup.utils.GameLogger;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class CraftingSystem {
    private final CraftingGrid craftingGrid;
    private final Object craftingLock = new Object();
    private final List<CraftingGridObserver> gridObservers = new CopyOnWriteArrayList<>();
    private final List<CraftingSystemObserver> observers = new CopyOnWriteArrayList<>();
    private final Inventory inventory;
    private final int gridSize;
    private final RecipeManager recipeManager;
    private ItemData resultSlot;

    public CraftingSystem(Inventory inventory, int gridSize, CraftingGrid craftingGrid) {
        this.inventory = inventory;
        this.gridSize = gridSize;
        this.craftingGrid = craftingGrid;
        this.recipeManager = RecipeManager.getInstance();
        this.craftingGrid.addObserver(this::onCraftingGridChanged);
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
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void addSlotObserver(int index, InventorySlotDataObserver observer) {
        if (index >= 0 && index < craftingGrid.getSize()) {
            craftingGrid.addSlotObserver(index, observer);
        }
    }

    public void updateCraftingResult() {
        synchronized (craftingLock) {
            resultSlot = checkRecipes();
            notifyCraftingResultChanged();
        }
    }

    private ItemData checkRecipes() {
        List<RecipeManager.CraftingRecipe> allRecipes = recipeManager.getAllRecipes();
        for (RecipeManager.CraftingRecipe recipe : allRecipes) {
            if (matchesRecipe(recipe)) {
                return recipe.getResult().copy();
            }
        }
        return null;
    }

    private boolean matchesRecipe(RecipeManager.CraftingRecipe recipe) {
        if (recipe.isShaped()) {
            return matchesShapedRecipe(recipe);
        } else {
            return matchesShapelessRecipe(recipe);
        }
    }

    private boolean matchesShapedRecipe(RecipeManager.CraftingRecipe recipe) {
        String[][] pattern = recipe.getPattern();
        int patternHeight = pattern.length;
        int patternWidth = pattern[0].length;

        // Try recipe at each possible position in grid
        for (int startRow = 0; startRow <= gridSize - patternHeight; startRow++) {
            for (int startCol = 0; startCol <= gridSize - patternWidth; startCol++) {
                if (matchesPatternAtPosition(pattern, startRow, startCol)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesPatternAtPosition(String[][] pattern, int startRow, int startCol) {
        Set<Integer> usedSlots = new HashSet<>();
        Map<String, String> symbolToItem = new HashMap<>();

        for (int row = 0; row < pattern.length; row++) {
            for (int col = 0; col < pattern[row].length; col++) {
                int gridIndex = (startRow + row) * gridSize + (startCol + col);
                String symbol = pattern[row][col];
                ItemData slotItem = craftingGrid.getItemAt(gridIndex);

                if (symbol == null) {
                    if (slotItem != null) return false;
                    continue;
                }

                if (slotItem == null) return false;

                String expectedItemId = symbolToItem.get(symbol);
                if (expectedItemId == null) {
                    symbolToItem.put(symbol, slotItem.getItemId());
                } else if (!expectedItemId.equals(slotItem.getItemId())) {
                    return false;
                }

                usedSlots.add(gridIndex);
            }
        }

        // Check that all other slots are empty
        for (int i = 0; i < craftingGrid.getSize(); i++) {
            if (!usedSlots.contains(i) && craftingGrid.getItemAt(i) != null) {
                return false;
            }
        }

        return true;
    }

    private boolean matchesShapelessRecipe(RecipeManager.CraftingRecipe recipe) {
        Map<String, Integer> required = new HashMap<>(recipe.getIngredients());
        Map<String, Integer> found = new HashMap<>();

        // Count all items in grid
        for (int i = 0; i < craftingGrid.getSize(); i++) {
            ItemData item = craftingGrid.getItemAt(i);
            if (item != null) {
                found.merge(item.getItemId(), item.getCount(), Integer::sum);
            }
        }

        // Check if we have all required items
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            int foundCount = found.getOrDefault(entry.getKey(), 0);
            if (foundCount < entry.getValue()) {
                return false;
            }
        }

        // Check if we have no extra items
        return found.keySet().equals(required.keySet());
    }

    private RecipeManager.CraftingRecipe findMatchingRecipe() {
        if (resultSlot == null) return null;
        List<RecipeManager.CraftingRecipe> recipes = recipeManager.getRecipesByOutput(resultSlot.getItemId());
        for (RecipeManager.CraftingRecipe recipe : recipes) {
            if (matchesRecipe(recipe)) {
                return recipe;
            }
        }
        return null;
    }

    private int getTotalItemCountInGrid(String itemId) {
        int totalCount = 0;
        for (int i = 0; i < craftingGrid.getSize(); i++) {
            ItemData item = craftingGrid.getItemAt(i);
            if (item != null && item.getItemId().equals(itemId)) {
                totalCount += item.getCount();
            }
        }
        return totalCount;
    }

    public boolean craftOneItem() {
        synchronized (craftingLock) {
            if (resultSlot == null) return false;

            // First verify we can add the result to inventory
            ItemData craftResult = resultSlot.copy();
            if (!inventory.addItem(craftResult)) {
                GameLogger.error("Cannot craft - inventory full");
                return false;
            }

            // Get recipe requirements
            RecipeManager.CraftingRecipe recipe = findMatchingRecipe();
            if (recipe == null) return false;

            Map<String, Integer> requiredItems = recipe.getIngredients();
            if (requiredItems == null) return false;

            // Check if grid has sufficient items
            for (Map.Entry<String, Integer> entry : requiredItems.entrySet()) {
                String itemId = entry.getKey();
                int requiredCount = entry.getValue();
                int availableCount = getTotalItemCountInGrid(itemId);

                if (availableCount < requiredCount) {
                    GameLogger.error("Not enough " + itemId + " to craft");
                    return false;
                }
            }

            // Consume ingredients
            for (Map.Entry<String, Integer> entry : requiredItems.entrySet()) {
                consumeItemsFromGrid(entry.getKey(), entry.getValue());
            }

            // Play craft sound
            AudioManager.getInstance().playSound(AudioManager.SoundEffect.CRAFT);

            inventory.notifyObservers();
            // Update crafting result and UI
            updateCraftingResult();

            GameLogger.info("Successfully crafted: " + craftResult.getItemId() + " x" + craftResult.getCount());
            return true;
        }
    }

    private void consumeItemsFromGrid(String itemId, int requiredCount) {
        int remaining = requiredCount;

        for (int i = 0; i < craftingGrid.getSize() && remaining > 0; i++) {
            ItemData item = craftingGrid.getItemAt(i);
            if (item != null && item.getItemId().equals(itemId)) {
                int toConsume = Math.min(remaining, item.getCount());
                remaining -= toConsume;

                if (toConsume >= item.getCount()) {
                    craftingGrid.setItemAt(i, null);
                } else {
                    item.setCount(item.getCount() - toConsume);
                }
                notifySlotObservers(i);
            }
        }
    }

    public void setItemInGrid(int index, ItemData item) {
        synchronized (craftingLock) {
            if (index < 0 || index >= craftingGrid.getSize()) {
                GameLogger.error("Invalid grid index: " + index);
                return;
            }

            if (item != null) {
                ItemData itemCopy = item.copy();
                craftingGrid.setItemAt(index, itemCopy);
            } else {
                craftingGrid.setItemAt(index, null);
            }

            notifySlotObservers(index);
            updateCraftingResult();
        }
    }

    public ItemData getItemInGrid(int index) {
        synchronized (craftingLock) {
            if (index < 0 || index >= craftingGrid.getSize()) {
                return null;
            }
            return craftingGrid.getItemAt(index);
        }
    }

    public ItemData getCraftingResult() {
        synchronized (craftingLock) {
            if (resultSlot == null) return null;
            return resultSlot.copy();
        }
    }

    private void notifySlotObservers(int index) {
        gridObservers.forEach(observer -> observer.onSlotChanged(index));
    }

    private void notifyCraftingResultChanged() {
        observers.forEach(observer -> observer.onCraftingResultChanged(resultSlot));
    }

    public void onCraftingGridChanged() {
        updateCraftingResult();
    }

    public CraftingGrid getCraftingGrid() {
        return craftingGrid;
    }

    public interface CraftingGridObserver {
        void onSlotChanged(int index);
    }

    public interface CraftingSystemObserver {
        void onCraftingResultChanged(ItemData newResult);
    }
}
