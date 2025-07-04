package io.github.pokemeetup.system.gameplay.inventory.crafting;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import io.github.pokemeetup.screens.InventoryScreenInterface;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.utils.textures.TextureManager;

import java.util.List;
import java.util.Map;

import static io.github.pokemeetup.screens.CraftingTableScreen.SLOT_SIZE;

public class RecipeGlossaryUI {
    private final Stage stage;
    private final Skin skin;
    private ScrollPane recipeScroll;
    private Table recipeList;
    private final InventoryScreenInterface screenInterface;
    private final CraftingSystem craftingSystem;

    public RecipeGlossaryUI(Stage stage, Skin skin, InventoryScreenInterface screenInterface,
                            CraftingSystem craftingSystem) {
        this.stage = stage;
        this.skin = skin;
        this.screenInterface = screenInterface;
        this.craftingSystem = craftingSystem;

        recipeList = new Table();
        recipeList.top().left();

        recipeScroll = new ScrollPane(recipeList, skin);
        recipeScroll.setFadeScrollBars(false);

        populateRecipes();
        setupUI();
    }


    private void setupUI() {
        recipeList = new Table();
        recipeList.top().left();
        ScrollPane.ScrollPaneStyle scrollStyle = new ScrollPane.ScrollPaneStyle(skin.get(ScrollPane.ScrollPaneStyle.class));
        scrollStyle.background = new TextureRegionDrawable(TextureManager.ui.findRegion("hotbar_bg"))
            .tint(new Color(0.2f, 0.2f, 0.2f, 0.7f));

        recipeScroll = new ScrollPane(recipeList, scrollStyle);
        recipeScroll.setFadeScrollBars(false);
        recipeScroll.setScrollbarsVisible(true);

        populateRecipes();
    }

    private void populateRecipes() {
        recipeList.clear();
        List<RecipeManager.CraftingRecipe> recipes = RecipeManager.getInstance().getAllRecipes();
        int availableDimension = (int) Math.sqrt(craftingSystem.getCraftingGrid().getSize());

        for (RecipeManager.CraftingRecipe recipe : recipes) {
            if (recipe.isShaped()) {
                int patternRows = recipe.getPattern().length;
                int patternCols = recipe.getPattern()[0].length;
                if (patternRows > availableDimension || patternCols > availableDimension) {
                    continue;
                }
            } else {
                int totalIngredients = recipe.getIngredients().values().stream().mapToInt(Integer::intValue).sum();
                if (totalIngredients > availableDimension * availableDimension) {
                    continue;
                }
            }
            createRecipeEntry(recipe);
        }
    }

    public ScrollPane getRecipeScroll() {
        return recipeScroll;
    }

    private void createRecipeEntry(RecipeManager.CraftingRecipe recipe) {
        Table entry = new Table();
        Table resultDisplay = new Table();
        resultDisplay.add(createItemDisplay(recipe.getResult()))
            .width(SLOT_SIZE * 3)
            .left();
        Table ingredientsList = new Table();
        ScrollPane ingredientsScroll = new ScrollPane(ingredientsList);
        recipe.getIngredients().forEach((itemId, count) -> {
            Label ingredient = new Label(itemId + " x" + count, skin);
            ingredient.setFontScale(SLOT_SIZE * 0.025f);
            ingredientsList.add(ingredient).padRight(10).left().row();
        });
        entry.add(resultDisplay).width(SLOT_SIZE * 3).pad(5);
        entry.add(ingredientsScroll).width(SLOT_SIZE * 4).pad(5);
        entry.add(createCraftButton(recipe)).pad(5);

        recipeList.add(entry).expandX().fillX().pad(5).row();
    }

    private Table createItemDisplay(ItemData item) {
        Table display = new Table();
        TextureRegion texture = TextureManager.items.findRegion(item.getItemId().toLowerCase() + "_item");
        if (texture != null) {
            display.add(new Image(texture)).size(SLOT_SIZE * 0.8f);
            display.add(new Label(item.getItemId(), skin)).padLeft(5);
        }
        return display;
    }

    private TextButton createCraftButton(RecipeManager.CraftingRecipe recipe) {
        TextButton craftButton = new TextButton("Craft", skin);
        craftButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                attemptAutoCraft(recipe);
            }
        });
        return craftButton;
    }

    /**
     * [FIXED] This method now correctly uses the new crafting system.
     */
    private void attemptAutoCraft(RecipeManager.CraftingRecipe recipe) {
        Inventory inventory = screenInterface.getInventory();
        Map<String, Integer> required = recipe.getIngredients();
        String[][] pattern = recipe.getPattern();
        boolean hasAll = required.entrySet().stream()
            .allMatch(entry -> hasEnoughItems(inventory, entry.getKey(), entry.getValue()));
        boolean hasSpace = inventory.hasSpaceFor(recipe.getResult());

        if (hasAll && hasSpace) {
            placeItemsInGrid(pattern, recipe.isShaped());
            ItemData craftedItem = craftingSystem.craftAndConsume();
            if (craftedItem != null) {
                inventory.addItem(craftedItem);
            }
        } else if (!hasAll) {
            showMissingIngredientsDialog(required);
        } else {
            showInventoryFullDialog();
        }
    }

    private boolean hasEnoughItems(Inventory inventory, String itemId, int required) {
        int count = 0;
        List<ItemData> items = inventory.getAllItems();
        for (ItemData item : items) {
            if (item != null && item.getItemId().equals(itemId)) {
                count += item.getCount();
            }
        }
        return count >= required;
    }

    private void placeItemsInGrid(String[][] pattern, boolean shaped) {
        for (int i = 0; i < craftingSystem.getCraftingGrid().getSize(); i++) {
            craftingSystem.setItemInGrid(i, null);
        }

        if (shaped) {
            int totalSlots = craftingSystem.getCraftingGrid().getSize();
            int gridDimension = (int) Math.sqrt(totalSlots);
            int patternRows = pattern.length;
            int patternCols = pattern[0].length;
            int offsetRow = (gridDimension - patternRows) / 2;
            int offsetCol = (gridDimension - patternCols) / 2;

            for (int row = 0; row < patternRows; row++) {
                for (int col = 0; col < patternCols; col++) {
                    if (pattern[row][col] != null) {
                        int gridIndex = (offsetRow + row) * gridDimension + (offsetCol + col);
                        String symbol = pattern[row][col];
                        String itemId = null;
                        if ("P".equals(symbol)) {
                            itemId = ItemManager.ItemIDs.WOODEN_PLANKS;
                        } else if ("S".equals(symbol)) {
                            itemId = ItemManager.ItemIDs.STICK;
                        }
                        if (itemId != null) {
                            ItemData item = findAndRemoveItem(screenInterface.getInventory(), itemId, 1);
                            if (item != null) {
                                craftingSystem.setItemInGrid(gridIndex, item);
                            }
                        }
                    }
                }
            }
        } else {
            int gridSize = craftingSystem.getCraftingGrid().getSize();
            int index = 0;
            for (String[] strings : pattern) {
                for (String string : strings) {
                    if (string != null) {
                        String itemId = null;
                        if ("P".equals(string)) {
                            itemId = ItemManager.ItemIDs.WOODEN_PLANKS;
                        } else if ("S".equals(string)) {
                            itemId = ItemManager.ItemIDs.STICK;
                        }
                        if (itemId != null) {
                            while (index < gridSize && craftingSystem.getItemInGrid(index) != null) {
                                index++;
                            }
                            if (index < gridSize) {
                                ItemData item = findAndRemoveItem(screenInterface.getInventory(), itemId, 1);
                                if (item != null) {
                                    craftingSystem.setItemInGrid(index, item);
                                }
                                index++;
                            }
                        }
                    }
                }
            }
        }
    }


    private ItemData findAndRemoveItem(Inventory inventory, String itemId, int count) {
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemData item = inventory.getItemAt(i);
            if (item != null && item.getItemId().equals(itemId) && item.getCount() >= count) {
                ItemData result = item.copy();
                result.setCount(count);
                int newCount = item.getCount() - count;
                if (newCount <= 0) {
                    inventory.removeItemAt(i);
                } else {
                    item.setCount(newCount);
                }

                return result;
            }
        }
        return null;
    }

    private void showMissingIngredientsDialog(Map<String, Integer> required) {
        Dialog dialog = new Dialog("Missing Ingredients", skin);
        Table content = new Table(skin);

        required.forEach((itemId, count) -> {
            if (!hasEnoughItems(screenInterface.getInventory(), itemId, count)) {
                content.add(new Label("Need: " + itemId + " x" + count, skin)).row();
            }
        });

        dialog.getContentTable().add(content);
        dialog.button("OK");
        dialog.show(stage);
    }

    /**
     * [NEW] Shows a dialog informing the user their inventory is full.
     */
    private void showInventoryFullDialog() {
        Dialog dialog = new Dialog("Inventory Full", skin);
        dialog.text("Not enough space in your inventory to craft this item.");
        dialog.button("OK");
        dialog.show(stage);
    }

    public void show() {
        Table container = new Table();
        container.setFillParent(true);
        container.add(recipeScroll).expand().fill().pad(10);
        stage.addActor(container);
    }

    public void hide() {
        recipeScroll.remove();
    }
}
