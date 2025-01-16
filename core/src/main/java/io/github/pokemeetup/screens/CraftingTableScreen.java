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
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.gameplay.inventory.crafting.CraftingGrid;
import io.github.pokemeetup.system.gameplay.inventory.crafting.CraftingSystem;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.InventoryObserver;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.InventorySlotData;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.ItemContainer;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.storage.InventoryConverter;
import io.github.pokemeetup.utils.textures.TextureManager;

import java.util.ArrayList;
import java.util.List;

public class CraftingTableScreen implements Screen, InventoryScreenInterface, InventoryObserver, CraftingSystem.CraftingSystemObserver {
    private static final int SLOT_SIZE = 32;
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

        // First initialize the crafting grid
        initializeCraftingGrid();

        // Then setup the UI which uses the initialized slots
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
    public ItemContainer getChestData() {
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
            InventorySlotUI slot = new InventorySlotUI(inventorySlotData[i], skin, this);
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
            InventorySlotUI slot = new InventorySlotUI(slotData, skin, this);
            craftingSlots.add(slot);

            // Register slot observer with crafting system
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


    // Add validation method
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

        // Create crafting slots and add them to the grid
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int index = row * GRID_SIZE + col;
                InventorySlotUI slotUI = craftingSlots.get(index);
                craftingGridTable.add(slotUI);
            }
            craftingGridTable.row();
        }

        // Create result slot
        InventorySlotData resultSlotData = new InventorySlotData(-1, InventorySlotData.SlotType.CRAFTING_RESULT, craftingGrid);
        resultSlotData.setSlotType(InventorySlotData.SlotType.CRAFTING_RESULT);
        resultSlot = new InventorySlotUI(resultSlotData, skin, this);

        // Create the crafting table layout
        Table craftingTable = new Table();
        craftingTable.add(craftingGridTable);
        craftingTable.add(new Image(TextureManager.ui.findRegion("arrow"))).padLeft(10).padRight(10);
        craftingTable.add(resultSlot).size(SLOT_SIZE);

        container.add(craftingTable);
        return container;
    }


    private void setupUI(Skin skin) {
        validateInitialization();

        Table mainTable = new Table();
        mainTable.setFillParent(true);

        // Semi-transparent background
        Pixmap bgPixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        bgPixmap.setColor(0, 0, 0, 0.2f);
        bgPixmap.fill();
        mainTable.setBackground(new TextureRegionDrawable(new TextureRegion(new Texture(bgPixmap))));
        bgPixmap.dispose();

        // Create sections
        Table craftingContainer = createCraftingSection();
        mainTable.add(craftingContainer).pad(10).row();

        Table inventoryContainer = createInventorySection();
        mainTable.add(inventoryContainer).pad(10).row();

        // Close button
        // Fix close button implementation
        // Close button
        TextButton closeButton = new TextButton("Close", skin);
        closeButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                gameScreen.closeExpandedCrafting();
            }
        });

        mainTable.add(closeButton).size(100, 40).pad(10);

        // Add main table to stage
        stage.addActor(mainTable);


    }

    private void initializeInventoryData() {
        this.inventorySlotData = new InventorySlotData[Inventory.INVENTORY_SIZE];
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            inventorySlotData[i] = new InventorySlotData(i, InventorySlotData.SlotType.INVENTORY, inventory);
            inventorySlotData[i].setSlotType(InventorySlotData.SlotType.INVENTORY);
        }

        // Load current inventory state
        List<ItemData> items = inventory.getAllItems();
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            if (i < items.size() && items.get(i) != null) {
                ItemData item = items.get(i);
                inventorySlotData[i].setItem(item.getItemId(), item.getCount(), item.getUuid());
            }
        }
    }

    private void returnItemsToInventory() {
        // Only return items when closing the crafting table
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

        // Check if player is still in range
        if (!isPlayerInRange()) {
            close();
            return;
        }

        Gdx.gl.glClearColor(0, 0, 0, 0.5f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stage.act(delta);
        stage.draw();

        // Update held item position if any
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
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void onInventoryChanged() {
        GameLogger.info("Inventory changed - updating inventory slots");

    }


    @Override
    public void dispose() {
        GameLogger.info("Disposing CraftingTableScreen resources");

        try {
            // Return any held items to inventory first
            if (heldItem != null) {
                returnHeldItemToInventory();
            }

            // Return crafting grid items to inventory
            returnItemsToInventory();

            // Dispose of stage and resources
            if (stage != null) {
                stage.dispose();
            }

            // Clear all slots
            if (craftingSlots != null) {
                craftingSlots.clear();
            }

            // Dispose of textures and other resources
            if (heldItemGroup != null) {
                heldItemGroup.remove();
                heldItemGroup = null;
            }

            // Clear references
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
            // Load texture
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
