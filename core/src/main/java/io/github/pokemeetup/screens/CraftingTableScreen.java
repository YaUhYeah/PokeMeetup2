package io.github.pokemeetup.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.screens.otherui.InventorySlotUI;
import io.github.pokemeetup.system.InputManager;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.data.ChestData;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.gameplay.inventory.crafting.CraftingGrid;
import io.github.pokemeetup.system.gameplay.inventory.crafting.CraftingSystem;
import io.github.pokemeetup.system.gameplay.inventory.crafting.RecipeGlossaryUI;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.InventoryObserver;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.InventorySlotData;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.storage.InventoryConverter;
import io.github.pokemeetup.utils.textures.TextureManager;

import java.util.ArrayList;
import java.util.List;

public class CraftingTableScreen implements Screen, InventoryScreenInterface, InventoryObserver, CraftingSystem.CraftingSystemObserver {
    public static final int SLOT_SIZE = 32;
    private static final int GRID_SIZE = 3;
    private final Stage stage;
    private final Player player;
    private final World world;
    private final GameClient gameClient;
    private final Inventory inventory;
    private final CraftingSystem craftingSystem;
    private final Skin skin;
    private final List<InventorySlotUI> inventorySlots = new ArrayList<>();
    private final GameScreen gameScreen;
    private final InputManager inputManager;
    private Vector2 craftingTablePosition;
    private List<InventorySlotUI> craftingSlots;
    private InventorySlotUI resultSlot;
    private boolean isVisible = false;
    private Item heldItem = null;
    private InventorySlotData[] inventorySlotData;
    private Group heldItemGroup;
    private Image heldItemImage;
    private Label heldItemCountLabel;
    private CraftingGrid craftingGrid; // New field

    public CraftingTableScreen(Player player, Skin skin, World world, GameClient gameClient, GameScreen screen, InputManager inputManager) {
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null");
        }
        this.player = player;
        this.world = world;
        this.gameClient = gameClient;
        this.stage = new Stage(new ScreenViewport());
        this.skin = skin;
        this.inventory = player.getInventory();
        this.craftingSlots = new ArrayList<>();
        this.gameScreen = screen;
        this.inputManager = inputManager;
        this.craftingGrid = new CraftingGrid(GRID_SIZE * GRID_SIZE);

        this.craftingSystem = new CraftingSystem(inventory, GRID_SIZE, craftingGrid); // 3x3 grid
        craftingSystem.addObserver(this);

        initializeInventoryData();
        setupHeldItemDisplay();
        initializeCraftingGrid();
        setupUI(skin);

        inventory.addObserver(this);
    }

    @Override
    public Item getHeldItemObject() {
        return heldItem;
    }

    @Override
    public CraftingSystem getCraftingSystem() {
        return craftingSystem;
    }

    @Override
    public ChestData getChestData() {
        return null;
    }

    private Table createInventorySection() {
        Table container = new Table();
        container.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("hotbar_bg")));
        container.pad(10);

        Table gridTable = new Table();
        gridTable.defaults().space(4);

        inventorySlots.clear();
        int cols = 9;

        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            InventorySlotUI slot = new InventorySlotUI(inventorySlotData[i], skin, this, SLOT_SIZE);
            inventorySlots.add(slot);

            gridTable.add(slot).size(SLOT_SIZE);
            if ((i + 1) % cols == 0) {
                gridTable.row();
            }
        }

        container.add(gridTable);
        return container;
    }

    private void initializeCraftingGrid() {
        craftingSlots.clear();

        for (int i = 0; i < GRID_SIZE * GRID_SIZE; i++) {
            InventorySlotData slotData = new InventorySlotData(i, InventorySlotData.SlotType.EXPANDED_CRAFTING, craftingGrid);
            InventorySlotUI slot = new InventorySlotUI(slotData, skin, this, SLOT_SIZE);
            craftingSlots.add(slot);
            craftingSystem.addSlotObserver(i, slot);
        }
    }

    public Stage getStage() {
        return stage;
    }

    public World getWorld() {
        return world;
    }

    public GameClient getGameClient() {
        return gameClient;
    }


    public Skin getSkin() {
        return skin;
    }
    private void validateInitialization() {
        if (craftingSlots == null || craftingSlots.isEmpty()) {
            GameLogger.error("Crafting slots not properly initialized!");
            throw new IllegalStateException("CraftingTableScreen not properly initialized");
        }
    }


    private Table createCraftingSection() {
        Table container = new Table();
        container.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("hotbar_bg")));
        container.pad(10);

        Table craftingGridTable = new Table();
        craftingGridTable.defaults().size(SLOT_SIZE).pad(2);
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int index = row * GRID_SIZE + col;
                InventorySlotUI slotUI = craftingSlots.get(index);
                craftingGridTable.add(slotUI);
            }
            craftingGridTable.row();
        }
        InventorySlotData resultSlotData = new InventorySlotData(-1, InventorySlotData.SlotType.CRAFTING_RESULT, craftingGrid);
        resultSlotData.setSlotType(InventorySlotData.SlotType.CRAFTING_RESULT);
        resultSlot = new InventorySlotUI(resultSlotData, skin, this, SLOT_SIZE);
        Table craftingTable = new Table();
        craftingTable.add(craftingGridTable);
        craftingTable.add(new Image(TextureManager.ui.findRegion("arrow"))).padLeft(10).padRight(10);
        craftingTable.add(resultSlot).size(SLOT_SIZE);

        container.add(craftingTable);
        return container;
    }
    private void setupUI(Skin skin) {
        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();
        float baseSize = Math.min(screenWidth * 0.04f, screenHeight * 0.07f);
        float SLOT_SIZE = Math.max(baseSize, 40); // Minimum size of 40
        float containerPadding = SLOT_SIZE * 0.25f;

        Table mainTable = new Table();
        mainTable.setFillParent(true);
        mainTable.center();
        Pixmap bgPixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        bgPixmap.setColor(0, 0, 0, 0.2f);
        bgPixmap.fill();
        mainTable.setBackground(new TextureRegionDrawable(new TextureRegion(new Texture(bgPixmap))));
        bgPixmap.dispose();
        Table contentContainer = new Table();
        Table splitContainer = new Table();
        Table craftingContainer = createCraftingSection();
        craftingContainer.pad(containerPadding);
        RecipeGlossaryUI recipeGlossary = new RecipeGlossaryUI(stage, skin, this, craftingSystem);
        ScrollPane recipeScroll = recipeGlossary.getRecipeScroll();
        Table recipeContainer = new Table();
        recipeContainer.add(new Label("Available Recipes", skin)).pad(containerPadding).row();
        recipeContainer.add(recipeScroll)
            .width(screenWidth * 0.25f)  // 25% of screen width
            .minWidth(SLOT_SIZE * 6)     // Minimum width to show recipes properly
            .height(screenHeight * 0.4f)  // 40% of screen height
            .pad(containerPadding);
        splitContainer.add(craftingContainer).padRight(SLOT_SIZE * 0.5f);
        splitContainer.add(recipeContainer);

        contentContainer.add(splitContainer).pad(containerPadding).row();
        Table inventoryContainer = createInventorySection();
        contentContainer.add(inventoryContainer).padTop(SLOT_SIZE * 0.5f).row();
        TextButton closeButton = new TextButton("Close", skin);
        closeButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                gameScreen.closeExpandedCrafting();
            }
        });

        contentContainer.add(closeButton)
            .size(SLOT_SIZE * 2.5f, SLOT_SIZE)
            .pad(SLOT_SIZE * 0.5f);

        mainTable.add(contentContainer);
        stage.addActor(mainTable);
    }


    @Override
    public void resize(int width, int height) {
        if (stage != null) {
            stage.getViewport().update(width, height, true);
            stage.clear();
            setupUI(skin);
        }
    }
    private void initializeInventoryData() {
        this.inventorySlotData = new InventorySlotData[Inventory.INVENTORY_SIZE];
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            inventorySlotData[i] = new InventorySlotData(i, InventorySlotData.SlotType.INVENTORY, inventory);
            inventorySlotData[i].setSlotType(InventorySlotData.SlotType.INVENTORY);
        }
        List<ItemData> items = inventory.getAllItems();
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            if (i < items.size() && items.get(i) != null) {
                ItemData item = items.get(i);
                inventorySlotData[i].setItem(item.getItemId(), item.getCount(), item.getUuid());
            }
        }
    }

    private void returnItemsToInventory() {
        if (!isVisible) {
            if (heldItem != null) {
                inventory.addItem(new ItemData(heldItem.getName(), heldItem.getCount(), heldItem.getUuid()));
                setHeldItem(null);
            }
            craftingSystem.returnItemsToInventory();
        }
    }

    @Override
    public void onCraftingResultChanged(ItemData newResult) {
        GameLogger.info("Crafting result changed - updating result slot");
        resultSlot.updateSlot();
    }


    @Override
    public void show() {
        isVisible = true;
        setupInputProcessors();
        updateInventorySlots(); // Refresh inventory display
    }

    private void updateInventorySlots() {
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            InventorySlotData slotData = inventorySlotData[i];
            ItemData item = inventory.getItemAt(i);
            if (item != null) {
                slotData.setItem(item.getItemId(), item.getCount(), item.getUuid());
            } else {
                slotData.clear();
            }
        }

        for (InventorySlotUI slot : inventorySlots) {
            slot.forceUpdate();
        }
    }


    private void setupInputProcessors() {
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(stage);
        Gdx.input.setInputProcessor(multiplexer);
    }

    @Override
    public void render(float delta) {
        if (!isVisible) return;
        if (!isPlayerInRange()) {
            close();
            return;
        }

        Gdx.gl.glClearColor(0, 0, 0, 0.5f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stage.act(delta);
        stage.draw();
        updateHeldItemPosition();
    }

    private void close() {
        GameLogger.info("Closing crafting table screen");
        hide();  // This will handle cleanup properly
        setupInputProcessors(); // Reset input handling
    }


    private void returnHeldItemToInventory() {
        if (heldItem != null) {
            ItemData itemData = new ItemData(heldItem.getName(), heldItem.getCount(), heldItem.getUuid());
            if (inventory.addItem(itemData)) {
                setHeldItem(null);
                GameLogger.info("Returned held item to inventory: " + itemData.getItemId());
            }
        }
    }


    private void setupHeldItemDisplay() {
        heldItemImage = new Image();
        heldItemImage.setSize(SLOT_SIZE, SLOT_SIZE);
        heldItemImage.setVisible(false);

        heldItemCountLabel = new Label("", skin);
        heldItemCountLabel.setVisible(false);

        heldItemGroup = new Group();
        heldItemGroup.addActor(heldItemImage);
        heldItemGroup.addActor(heldItemCountLabel);

        stage.addActor(heldItemGroup);

        heldItemGroup.setTouchable(Touchable.disabled);
    }

    private void cleanupHeldItemResources() {
        if (heldItemGroup != null) {
            heldItemGroup.clear();
            heldItemGroup.remove();
            heldItemGroup = null;
        }
    }



    @Override
    public void onInventoryChanged() {
        GameLogger.info("Inventory changed - updating inventory slots");

    }


    @Override
    public void dispose() {
        GameLogger.info("Disposing CraftingTableScreen resources");

        try {
            if (heldItem != null) {
                returnHeldItemToInventory();
            }
            returnItemsToInventory();
            if (stage != null) {
                stage.dispose();
            }
            if (craftingSlots != null) {
                craftingSlots.clear();
            }
            if (heldItemGroup != null) {
                heldItemGroup.remove();
                heldItemGroup = null;
            }
            craftingSlots = null;
            resultSlot = null;
            heldItem = null;

            GameLogger.info("CraftingTableScreen disposed successfully");

        } catch (Exception e) {
            GameLogger.error("Error during CraftingTableScreen disposal: " + e.getMessage());
        }
    }


    @Override
    public void pause() {

    }

    @Override
    public void resume() {

    }


    public void updateHeldItemDisplay() {
        if (heldItemGroup == null) {
            setupHeldItemDisplay();
        }

        heldItemGroup.clear();

        if (heldItem != null) {
            TextureRegion texture = TextureManager.items.findRegion(heldItem.getName().toLowerCase() + "_item");
            if (texture == null) {
                texture = TextureManager.items.findRegion(heldItem.getName().toLowerCase());
            }

            if (texture != null) {
                heldItemImage = new Image(texture);
                heldItemImage.setSize(32, 32);
                heldItemGroup.addActor(heldItemImage);

                if (heldItem.getCount() > 1) {
                    heldItemCountLabel = new Label(String.valueOf(heldItem.getCount()), skin);
                    heldItemCountLabel.setPosition(24, 0);
                    heldItemGroup.addActor(heldItemCountLabel);
                }

                heldItemGroup.setVisible(true);
                heldItemGroup.toFront();
            }
        } else {
            heldItemGroup.setVisible(false);
        }
    }

    @Override
    public void hide() {
        isVisible = false;
        if (heldItem != null) {
            returnHeldItemToInventory();
        }
        returnItemsToInventory();
        cleanupHeldItemResources();
        GameLogger.info("CraftingTableScreen hidden");
    }

    private boolean isPlayerInRange() {
        if (craftingTablePosition == null) return false;
        float distance = Vector2.dst(
            player.getTileX(), player.getTileY(),
            craftingTablePosition.x, craftingTablePosition.y
        );
        return distance <= 2; // Within 2 tiles
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public ItemData getHeldItem() {
        return InventoryConverter.itemToItemData(heldItem);
    }

    @Override
    public void setHeldItem(Item item) {
        this.heldItem = item;
        updateHeldItemDisplay();
    }

    public boolean isVisible() {
        return isVisible;
    }

    public void updatePosition(Vector2 newPosition) {
        this.craftingTablePosition = newPosition;
    }

    private void updateHeldItemPosition() {
        if (heldItem != null && heldItemGroup != null) {
            float x = Gdx.input.getX() - SLOT_SIZE / 2f;
            float y = Gdx.graphics.getHeight() - Gdx.input.getY() - SLOT_SIZE / 2f;

            heldItemGroup.setPosition(x, y);
            heldItemGroup.toFront();
        }
    }


}
