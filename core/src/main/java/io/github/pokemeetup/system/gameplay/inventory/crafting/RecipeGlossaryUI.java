package io.github.pokemeetup.system.gameplay.inventory.crafting;

import com.badlogic.gdx.Gdx;
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
        this.slotSize = Math.min(Gdx.graphics.getWidth() * 0.04f, Gdx.graphics.getHeight() * 0.07f);
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

        // Style the scroll pane
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

        for (RecipeManager.CraftingRecipe recipe : recipes) {
            createRecipeEntry(recipe);
        }
    }

    public ScrollPane getRecipeScroll() {
        return recipeScroll;
    }

    private float slotSize;

    private void createRecipeEntry(RecipeManager.CraftingRecipe recipe) {
        Table entry = new Table();

        // Result display with fixed width
        Table resultDisplay = new Table();
        resultDisplay.add(createItemDisplay(recipe.getResult()))
            .width(SLOT_SIZE * 3)
            .left();

        // Ingredients with scrolling if needed
        Table ingredientsList = new Table();
        ScrollPane ingredientsScroll = new ScrollPane(ingredientsList);
        recipe.getIngredients().forEach((itemId, count) -> {
            Label ingredient = new Label(itemId + " x" + count, skin);
            ingredient.setFontScale(SLOT_SIZE * 0.025f);
            ingredientsList.add(ingredient).padRight(10).left().row();
        });

        // Fixed widths to prevent horizontal scroll
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

    private void attemptAutoCraft(RecipeManager.CraftingRecipe recipe) {
        Inventory inventory = screenInterface.getInventory();
        Map<String, Integer> required = recipe.getIngredients();
        String[][] pattern = recipe.getPattern();

        // Check if we have all ingredients
        boolean hasAll = required.entrySet().stream()
            .allMatch(entry -> hasEnoughItems(inventory, entry.getKey(), entry.getValue()));

        if (hasAll) {
            // Auto-place items in crafting grid
            placeItemsInGrid(pattern, recipe.isShaped());
            // Trigger craft
            craftingSystem.craftOneItem();
        } else {
            // Show missing ingredients message
            showMissingIngredientsDialog(required);
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
        // Clear existing grid
        for (int i = 0; i < craftingSystem.getCraftingGrid().getSize(); i++) {
            craftingSystem.setItemInGrid(i, null);
        }

        if (shaped) {
            // Place items according to pattern
            for (int row = 0; row < pattern.length; row++) {
                for (int col = 0; col < pattern[row].length; col++) {
                    if (pattern[row][col] != null) {
                        int gridIndex = row * pattern[row].length + col;
                        String itemId = pattern[row][col].equals("P") ? ItemManager.ItemIDs.WOODEN_PLANKS :
                            pattern[row][col].equals("S") ? ItemManager.ItemIDs.STICK : null;

                        if (itemId != null) {
                            ItemData item = findAndRemoveItem(screenInterface.getInventory(), itemId, 1);
                            if (item != null) {
                                craftingSystem.setItemInGrid(gridIndex, item);
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

                // Update original item count
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
