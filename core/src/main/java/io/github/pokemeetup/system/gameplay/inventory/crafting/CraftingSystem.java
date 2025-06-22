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
        // Compute frequency for each non-null symbol in the pattern
        Map<String, Integer> symbolFrequency = new HashMap<>();
        for (int r = 0; r < pattern.length; r++) {
            for (int c = 0; c < pattern[r].length; c++) {
                String symbol = pattern[r][c];
                if (symbol != null) {
                    symbolFrequency.merge(symbol, 1, Integer::sum);
                }
            }
        }

        // Build the expected mapping: for each symbol, we expect a specific item.
        // We assume that for each symbol, its frequency matches one ingredient’s required count.
        Map<String, String> expectedMapping = new HashMap<>();
        // Make a copy of the ingredients map (itemId -> count)
        Map<String, Integer> ingredients = new HashMap<>(recipe.getIngredients());

        for (Map.Entry<String, Integer> symbolEntry : symbolFrequency.entrySet()) {
            boolean found = false;
            for (Iterator<Map.Entry<String, Integer>> it = ingredients.entrySet().iterator(); it.hasNext();) {
                Map.Entry<String, Integer> ingEntry = it.next();
                if (ingEntry.getValue().equals(symbolEntry.getValue())) {
                    expectedMapping.put(symbolEntry.getKey(), ingEntry.getKey());
                    it.remove();
                    found = true;
                    break;
                }
            }
            if (!found) {
                // No matching ingredient with the exact count – pattern does not match recipe.
                return false;
            }
        }

        // Try the recipe pattern at every possible starting position in the grid.
        int patternHeight = pattern.length;
        int patternWidth = pattern[0].length;
        for (int startRow = 0; startRow <= gridSize - patternHeight; startRow++) {
            for (int startCol = 0; startCol <= gridSize - patternWidth; startCol++) {
                if (matchesPatternAtPosition(pattern, startRow, startCol, expectedMapping)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesPatternAtPosition(String[][] pattern, int startRow, int startCol, Map<String, String> expectedMapping) {
        Set<Integer> usedSlots = new HashSet<>();
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

                // Instead of inferring, check against our expected mapping.
                String expectedItemId = expectedMapping.get(symbol);
                if (expectedItemId == null || !expectedItemId.equals(slotItem.getItemId())) {
                    return false;
                }

                usedSlots.add(gridIndex);
            }
        }
        // Ensure all grid slots not used by the pattern are empty.
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

    /**
     * [FIXED] Consumes one set of ingredients from the grid and returns the crafted item.
     * It no longer adds the item to the inventory directly.
     *
     * @return The crafted ItemData, or null if crafting failed.
     */
    public ItemData craftAndConsume() {
        synchronized (craftingLock) {
            if (resultSlot == null) {
                return null;
            }

            ItemData craftedItem = resultSlot.copy();

            RecipeManager.CraftingRecipe recipe = findMatchingRecipe();
            if (recipe == null) {
                GameLogger.error("Recipe mismatch for result: " + resultSlot.getItemId());
                return null;
            }

            // Consume ingredients from the grid
            for (Map.Entry<String, Integer> entry : recipe.getIngredients().entrySet()) {
                consumeItemsFromGrid(entry.getKey(), entry.getValue());
            }

            AudioManager.getInstance().playSound(AudioManager.SoundEffect.CRAFT);
            updateCraftingResult(); // This will re-evaluate the grid, which is now missing ingredients

            return craftedItem;
        }
    }

    /**
     * [NEW] Calculates the maximum number of times the current recipe can be crafted
     * based on the ingredients available in the crafting grid.
     *
     * @return The number of times the current recipe can be crafted.
     */
    public int calculateMaxCrafts() {
        synchronized (craftingLock) {
            if (resultSlot == null) return 0;
            RecipeManager.CraftingRecipe recipe = findMatchingRecipe();
            if (recipe == null) return 0;

            int maxCrafts = Integer.MAX_VALUE;

            for (Map.Entry<String, Integer> req : recipe.getIngredients().entrySet()) {
                String itemId = req.getKey();
                int requiredCount = req.getValue();
                if (requiredCount == 0) continue;

                int availableCount = 0;
                for (int i = 0; i < craftingGrid.getSize(); i++) {
                    ItemData item = craftingGrid.getItemAt(i);
                    if (item != null && item.getItemId().equals(itemId)) {
                        availableCount += item.getCount();
                    }
                }

                maxCrafts = Math.min(maxCrafts, availableCount / requiredCount);
            }

            return (maxCrafts == Integer.MAX_VALUE) ? 0 : maxCrafts;
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
