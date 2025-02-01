package io.github.pokemeetup.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.*;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.pokemeetup.system.InputManager;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.*;
import io.github.pokemeetup.system.gameplay.inventory.crafting.CraftingGrid;
import io.github.pokemeetup.system.gameplay.inventory.crafting.CraftingSystem;
import io.github.pokemeetup.system.gameplay.inventory.crafting.RecipeGlossaryUI;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.InventoryObserver;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.InventorySlotData;
import io.github.pokemeetup.screens.otherui.InventorySlotUI;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.ItemContainer;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.storage.InventoryConverter;
import io.github.pokemeetup.utils.textures.TextureManager;

import java.util.*;
import java.util.List;

public class InventoryScreen implements Screen, InventoryObserver, CraftingSystem.CraftingSystemObserver, InventoryScreenInterface {
    private static int SLOT_SIZE = 40;

    private final Skin skin;
    private final Stage stage;
    private final SpriteBatch batch;
    private final ShapeRenderer shapeRenderer;
    private final Player player;
    private final Inventory inventory;
    private final CraftingSystem craftingSystem;
    private final List<InventorySlotUI> craftingSlotUIs;
    private final CraftingGrid craftingGrid;
    private List<InventorySlotData> inventorySlots;
    private Group heldItemGroup;
    private Image heldItemImage;
    private InputManager inputManager;
    private Label heldItemCountLabel;
    private Item heldItem = null;
    private InventorySlotUI craftingResultSlotUI;
    private boolean visible = false;

    public InventoryScreen(Player player, Skin skin, Inventory inventory, InputManager inputManager) {
        this.player = player;
        this.skin = skin;
        this.craftingGrid = new CraftingGrid(4); // 2x2 grid
        this.inventory = inventory;
        this.inputManager = inputManager;
        this.stage = new Stage(new ScreenViewport());
        this.batch = new SpriteBatch();
        this.shapeRenderer = new ShapeRenderer();

        this.craftingSystem = new CraftingSystem(inventory, 2, craftingGrid);
        this.craftingSystem.addObserver(this);

        setupHeldItemDisplay();
        initializeInventorySlots();
        this.craftingSlotUIs = new ArrayList<>();

        setupUI();
        inventory.addObserver(this);

        GameLogger.info("InventoryScreen initialized");
        // Removed stage listener that handled global touches.
        // Now rely solely on InventorySlotUI logic for item placement.
    }

    @Override
    public Item getHeldItemObject() {
        return heldItem;
    }

    @Override
    public void show() {
        if (!visible) {
            GameLogger.info("InventoryScreen show() called");
            visible = true;

            // If desired, we can ensure no items are incorrectly held by the player on show,
            // but let's just leave as-is to match the stable approach from CraftingTableScreen.

            // Reload inventory once on show
            reloadInventory();

            if (stage != null) {
                stage.setKeyboardFocus(null);
                stage.unfocusAll();
            }
        }
    }

    @Override
    public void render(float delta) {
        if (!visible) return;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        stage.act(Math.min(delta, 1 / 30f));
        updateHeldItemPosition();
        stage.draw();

        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void updateHeldItemPosition() {
        if (heldItemGroup != null && heldItemGroup.isVisible()) {
            float x = Gdx.input.getX() - 16;
            float y = Gdx.graphics.getHeight() - Gdx.input.getY() - 16;
            heldItemGroup.setPosition(x, y);
        }
    }

    public Stage getStage() {
        return stage;
    }

    @Override
    public CraftingSystem getCraftingSystem() {
        return craftingSystem;
    }

    @Override
    public ItemContainer getChestData() {
        return null;
    }

    private void initializeInventorySlots() {
        inventorySlots = new ArrayList<>();
        List<ItemData> currentItems = inventory.getAllItems();
        GameLogger.info("InventoryScreen: Inventory has " + currentItems.size() + " slots.");
        int nonNullItemCount = 0;

        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            InventorySlotData slotData = new InventorySlotData(i, InventorySlotData.SlotType.INVENTORY, inventory);
            ItemData itemData = i < currentItems.size() ? currentItems.get(i) : null;

            if (itemData != null) {
                if (itemData.getMaxDurability() <= 0) {
                    Item itemTemplate = ItemManager.getItem(itemData.getItemId());
                    if (itemTemplate != null) {
                        itemData.setMaxDurability(itemTemplate.getMaxDurability());
                    }
                }
                if (itemData.getDurability() <= 0) {
                    itemData.setDurability(itemData.getMaxDurability());
                }

                slotData.setItemData(itemData);
                GameLogger.info("InventoryScreen: Loaded item into slot " + i + ": " +
                    itemData.getItemId() + " x" + itemData.getCount() +
                    ", durability: " + itemData.getDurability() + "/" + itemData.getMaxDurability());
                nonNullItemCount++;
            }

            inventorySlots.add(slotData);
        }

        GameLogger.info("InventoryScreen: Total non-null items loaded: " + nonNullItemCount);
    }
    private void setupUI() {
        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();

        // Calculate relative sizes
        float baseSize = Math.min(screenWidth * 0.04f, screenHeight * 0.07f); // Base size for slots
        SLOT_SIZE = (int) Math.max(baseSize, 40); // Minimum size of 40
        float containerPadding = SLOT_SIZE * 0.25f;

        Table mainTable = new Table();
        mainTable.setFillParent(true);
        mainTable.center();

        // Semi-transparent background
        Pixmap bgPixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        bgPixmap.setColor(0, 0, 0, 0.2f);
        bgPixmap.fill();
        Texture bgTexture = new Texture(bgPixmap);
        TextureRegionDrawable background = new TextureRegionDrawable(new TextureRegion(bgTexture));
        mainTable.setBackground(background);
        bgPixmap.dispose();

        // Create a container for everything to ensure proper centering
        Table contentContainer = new Table();

        // Top section container (Crafting + Recipe)
        Table topContainer = new Table();

        // Crafting section
        Table craftingContainer = new Table();
        craftingContainer.setBackground(createBackground());
        craftingContainer.pad(containerPadding);

        // Crafting grid
        Table craftingGrid1 = new Table();
        craftingGrid1.defaults().space(SLOT_SIZE * 0.1f);

        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) {
                final int index = y * 2 + x;
                InventorySlotData craftSlotData = new InventorySlotData(index, InventorySlotData.SlotType.CRAFTING, craftingGrid);

                InventorySlotUI craftSlot = new InventorySlotUI(craftSlotData, skin, this);
                craftingSlotUIs.add(craftSlot);
                craftingSystem.addSlotObserver(index, craftSlot);

                craftingGrid1.add(craftSlot).size(SLOT_SIZE);
                if (x == 1) craftingGrid1.row();
            }
        }

        // Arrow and result
        Image arrowImage = new Image(TextureManager.ui.findRegion("arrow"));
        InventorySlotData resultSlotData = new InventorySlotData(-1, InventorySlotData.SlotType.CRAFTING_RESULT, craftingGrid);
        craftingResultSlotUI = new InventorySlotUI(resultSlotData, skin, this);

        craftingContainer.add(craftingGrid1).padRight(SLOT_SIZE * 0.5f);
        craftingContainer.add(arrowImage).size(SLOT_SIZE * 0.8f).padRight(SLOT_SIZE * 0.5f);
        craftingContainer.add(craftingResultSlotUI).size(SLOT_SIZE);

        // Recipe glossary section
        RecipeGlossaryUI recipeGlossary = new RecipeGlossaryUI(stage, skin, this, craftingSystem);
        ScrollPane recipeScroll = recipeGlossary.getRecipeScroll();

        Table recipeContainer = new Table();
        recipeContainer.setBackground(createBackground());

        // Recipe list header
        Label recipesLabel = new Label("Recipes", skin);
        recipesLabel.setFontScale(SLOT_SIZE * 0.04f);

        recipeContainer.add(recipesLabel).padBottom(containerPadding).row();
        recipeContainer.add(recipeScroll)
            .width(SLOT_SIZE * 6f)
            .height(SLOT_SIZE * 2.5f)
            .pad(containerPadding);

        // Add both to top container with proper spacing
        topContainer.add(craftingContainer).padRight(SLOT_SIZE * 0.5f);
        topContainer.add(recipeContainer);

        contentContainer.add(topContainer).padBottom(SLOT_SIZE * 0.75f).row();

        // Inventory grid
        Table gridTable = new Table();
        gridTable.setName("gridTable");
        gridTable.setBackground(createBackground());
        gridTable.pad(containerPadding);

        // Create inventory slots
        int cols = 9;
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            InventorySlotUI slotUI = createSlotUI(i);
            gridTable.add(slotUI).size(SLOT_SIZE).pad(SLOT_SIZE * 0.05f);
            if ((i + 1) % cols == 0) {
                gridTable.row();
            }
        }

        contentContainer.add(gridTable).row();

        // Close button
        TextButton closeButton = new TextButton("Close", skin);
        closeButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                hide();
            }
        });

        // Scale button size relative to slot size
        float buttonWidth = SLOT_SIZE * 2.5f;
        float buttonHeight = SLOT_SIZE * 1.0f;
        contentContainer.add(closeButton).size(buttonWidth, buttonHeight).pad(SLOT_SIZE * 0.5f);

        // Add the content container to the main table
        mainTable.add(contentContainer);

        stage.addActor(mainTable);
        stage.addActor(heldItemGroup);
    }

    // Update createBackground method for consistent styling
    private Drawable createBackground() {
        return new TextureRegionDrawable(TextureManager.ui.findRegion("hotbar_bg"))
            .tint(new Color(0.2f, 0.2f, 0.2f, 0.85f));
    }

    @Override
    public void resize(int width, int height) {
        if (stage != null) {
            stage.getViewport().update(width, height, true);

            // Recalculate UI sizes and update if needed
            float baseSize = Math.min(width * 0.04f, height * 0.07f);
            SLOT_SIZE = (int) Math.max(baseSize, 40);

        }
    }

    private void rebuildUI() {
        stage.clear();
        setupUI();
    }
    public void reloadInventory() {
        GameLogger.info("Reloading inventory (only on show or controlled calls)...");
        if (inventory != null) {
            List<ItemData> currentItems = inventory.getAllItems();

            for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
                InventorySlotData slotData = inventorySlots.get(i);
                ItemData item = i < currentItems.size() ? currentItems.get(i) : null;

                if (item != null) {
                    slotData.setItemData(item);
                } else {
                    slotData.clear();
                }
                slotData.notifyObservers();
            }
        }
    }

    @Override
    public void onCraftingResultChanged(ItemData newResult) {
        if (craftingResultSlotUI != null) {
            craftingResultSlotUI.forceUpdate();
        }
    }

    private void setupHeldItemDisplay() {
        heldItemImage = new Image();
        heldItemImage.setSize(32, 32);
        heldItemImage.setVisible(false);

        heldItemCountLabel = new Label("", skin);
        heldItemCountLabel.setVisible(false);

        heldItemGroup = new Group();
        heldItemGroup.addActor(heldItemImage);
        heldItemGroup.addActor(heldItemCountLabel);

        heldItemGroup.setTouchable(Touchable.disabled);
    }

    private InventorySlotUI createSlotUI(int index) {
        InventorySlotData slotData = inventorySlots.get(index);
        return new InventorySlotUI(slotData, skin, this);
    }

    @Override
    public void updateHeldItemDisplay() {
        if (heldItemGroup == null) return;

        heldItemImage.setVisible(false);
        heldItemCountLabel.setVisible(false);
        heldItemGroup.setVisible(false);

        if (heldItem != null) {
            TextureRegion texture = TextureManager.items.findRegion(heldItem.getName().toLowerCase() + "_item");
            if (texture == null) {
                texture = TextureManager.items.findRegion(heldItem.getName().toLowerCase());
            }

            if (texture != null) {
                heldItemImage.setDrawable(new TextureRegionDrawable(texture));
                heldItemImage.setVisible(true);
                if (heldItem.getCount() > 1) {
                    heldItemCountLabel.setText(String.valueOf(heldItem.getCount()));
                    heldItemCountLabel.setVisible(true);
                } else {
                    heldItemCountLabel.setVisible(false);
                }
                heldItemGroup.setVisible(true);
                heldItemGroup.toFront();
            }
        }
    }


    @Override
    public void pause() {
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
        if (visible) {
            GameLogger.info("InventoryScreen hide() called");
            visible = false;

            // If there's still a held item, return it to the inventory now
            if (heldItem != null) {
                ItemData heldItemData = InventoryConverter.itemToItemData(heldItem);
                if (heldItemData != null) {
                    getInventory().addItem(heldItemData);
                }
                setHeldItem(null);
            }

            // Return crafting items
            craftingSystem.returnItemsToInventory();

            if (stage != null) {
                stage.setKeyboardFocus(null);
                stage.unfocusAll();
            }
            inputManager.setUIState(InputManager.UIState.NORMAL);
        }
    }

    @Override
    public void dispose() {
        stage.dispose();
        batch.dispose();
        shapeRenderer.dispose();
    }

    @Override
    public ItemData getHeldItem() {
        return InventoryConverter.itemToItemData(heldItem);
    }

    @Override
    public synchronized void setHeldItem(Item item) {
        if (item == null || item.getCount() <= 0) {
            this.heldItem = null;
        } else {
            Item newHeldItem = new Item(item.getName());
            newHeldItem.setCount(item.getCount());
            newHeldItem.setUuid(item.getUuid() != null ? item.getUuid() : UUID.randomUUID());
            newHeldItem.setDurability(item.getDurability());
            newHeldItem.setMaxDurability(item.getMaxDurability());
            this.heldItem = newHeldItem;
        }
        updateHeldItemDisplay();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public void onInventoryChanged() {
        // Just log the change. The slots will update themselves when data changes.
        GameLogger.info("Inventory changed - not reloading display immediately");
        // No forced reloadInventory() call here to avoid conflicts.
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    static class ParticleEffectActor extends Actor {
        private final ParticleEffect effect;

        public ParticleEffectActor(ParticleEffect effect) {
            this.effect = effect;
            effect.start();
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            effect.setPosition(getX(), getY());
            effect.draw(batch, Gdx.graphics.getDeltaTime());
        }

        @Override
        public void act(float delta) {
            super.act(delta);
            effect.update(delta);
        }
    }
}
