package io.github.pokemeetup.system.gameplay.inventory.crafting;

import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;

import java.util.*;
import java.util.List;

public class RecipeManager {
    private static RecipeManager instance;
    private final Map<String, CraftingRecipe> recipes = new HashMap<>();
    private final Map<String, List<CraftingRecipe>> recipesByOutput = new HashMap<>();

    private RecipeManager() {
        initializeRecipes();
    }

    public static synchronized RecipeManager getInstance() {
        if (instance == null) {
            instance = new RecipeManager();
        }
        return instance;
    }

    private void initializeRecipes() {
        // Add Wooden Axe recipe
        Map<String, Integer> axeIngredients = new HashMap<>();
        axeIngredients.put(ItemManager.ItemIDs.WOODEN_PLANKS, 3);
        axeIngredients.put(ItemManager.ItemIDs.STICK, 2);
        addRecipe(new CraftingRecipe(
            "wooden_axe_recipe",
            axeIngredients,
            new ItemData(ItemManager.ItemIDs.WOODEN_AXE, 1, UUID.randomUUID()),
            new String[][]{
                {"P", "P", null},
                {"S", "P", null},
                {"S", null, null}
            },
            true // Shaped recipe
        ));

        // Add Crafting Table recipe
        Map<String, Integer> tableIngredients = new HashMap<>();
        tableIngredients.put(ItemManager.ItemIDs.WOODEN_PLANKS, 4);
        addRecipe(new CraftingRecipe(
            "crafting_table_recipe",
            tableIngredients,
            new ItemData(ItemManager.ItemIDs.CRAFTING_TABLE, 1, UUID.randomUUID()),
            new String[][]{
                {"P", "P"},
                {"P", "P"}
            },
            true
        ));

        // Add Stick recipe
        Map<String, Integer> stickIngredients = new HashMap<>();
        stickIngredients.put(ItemManager.ItemIDs.WOODEN_PLANKS, 2);
        addRecipe(new CraftingRecipe(
            "stick_recipe",
            stickIngredients,
            new ItemData(ItemManager.ItemIDs.STICK, 4, UUID.randomUUID()),
            new String[][]{
                {"P"},
                {"P"}
            },
            true
        ));
    }

    public void addRecipe(CraftingRecipe recipe) {
        recipes.put(recipe.getId(), recipe);
        recipesByOutput.computeIfAbsent(recipe.getResult().getItemId(), k -> new ArrayList<>())
            .add(recipe);
    }

    public List<CraftingRecipe> getAllRecipes() {
        return new ArrayList<>(recipes.values());
    }

    public List<CraftingRecipe> getRecipesByOutput(String itemId) {
        return recipesByOutput.getOrDefault(itemId, new ArrayList<>());
    }

    public CraftingRecipe getRecipeById(String id) {
        return recipes.get(id);
    }

    public static class CraftingRecipe {
        private final String id;
        private final Map<String, Integer> ingredients;
        private final ItemData result;
        private final String[][] pattern;
        private final boolean shaped;

        public CraftingRecipe(String id, Map<String, Integer> ingredients, ItemData result,
                              String[][] pattern, boolean shaped) {
            this.id = id;
            this.ingredients = ingredients;
            this.result = result;
            this.pattern = pattern;
            this.shaped = shaped;
        }

        public String getId() { return id; }
        public Map<String, Integer> getIngredients() { return ingredients; }
        public ItemData getResult() { return result; }
        public String[][] getPattern() { return pattern; }
        public boolean isShaped() { return shaped; }
    }
}
