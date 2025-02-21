package io.github.pokemeetup.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.screens.otherui.InventorySlotUI;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.data.ChestData;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.gameplay.inventory.crafting.CraftingSystem;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.storage.InventoryConverter;
import io.github.pokemeetup.utils.textures.TextureManager;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.InventorySlotData;

public class ChestScreen implements Screen, InventoryScreenInterface {
    private static final int SLOT_SIZE = 40;
    private final ChestData chestData;
    private final SpriteBatch batch;
    private final Vector2 chestPosition;
    private final GameScreen gameScreen;
    private final Stage stage;
    private final Skin skin;
    private final Table inventoryTable;
    private final Table mainTable;
    private final Table chestTable;
    private final Table closeButtonTable;
    private boolean isVisible = false;
    private Item heldItem = null;
    private Group heldItemGroup;
    private Image heldItemImage;
    private Label heldItemCountLabel;
    private boolean isClosing = false;

    public ChestScreen(Skin skin, ChestData chestData, Vector2 chestPosition, GameScreen gameScreen) {
        this.skin = skin;
        this.chestData = ensureChestData(chestPosition, chestData);
        this.chestPosition = chestPosition;
        this.stage = new Stage(new ScreenViewport());
        this.batch = new SpriteBatch();
        this.gameScreen = gameScreen;
        this.mainTable = new Table();
        this.chestTable = new Table();
        this.inventoryTable = new Table();
        this.closeButtonTable = new Table();
        setupHeldItemDisplay();
        setupUI();
    }
    public Stage getStage() {
        return stage;
    }

    @Override
    public Inventory getInventory() {
        return GameContext.get().getPlayer().getInventory();
    }

    @Override
    public Player getPlayer() {
        return GameContext.get().getPlayer();
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

    @Override
    public CraftingSystem getCraftingSystem() {
        return null; // Chest doesn't use crafting
    }

    @Override
    public ChestData getChestData() {
        return chestData;
    }

    private void createChestInventoryGrid() {
        chestTable.clear();
        chestTable.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("hotbar_bg")));
        chestTable.pad(10);

        Label titleLabel = new Label("Storage", skin);
        chestTable.add(titleLabel).colspan(9).pad(5).row();

        int cols = 9;
        for (int i = 0; i < chestData.getSize(); i++) {
            InventorySlotData slotData = chestData.getSlotData(i);
            InventorySlotUI slot = new InventorySlotUI(slotData, skin, this, SLOT_SIZE);
            chestTable.add(slot).size(SLOT_SIZE);
            if ((i + 1) % cols == 0) {
                chestTable.row();
            }
        }
    }private ChestData ensureChestData(Vector2 chestPos, ChestData incomingData) {
        PlaceableBlock block = GameContext.get().getWorld().getBlockManager().getBlockAt((int) chestPos.x, (int) chestPos.y);
        if (block != null) {
            ChestData data = block.getChestData();
            if (data != null) {
                return data; // Use the already–assigned unique ChestData
            } else {
                // No chest data exists; create one now
                ChestData newData = new ChestData((int) chestPos.x, (int) chestPos.y);
                block.setChestData(newData);
                // Immediately notify the server of the new chest state.
                GameContext.get().getGameClient().sendChestUpdate(newData);
                return newData;
            }
        }
        // If no block was found, return incomingData (or create a new one as a last resort)
        return incomingData != null ? incomingData : new ChestData((int) chestPos.x, (int) chestPos.y);
    }



    @Override
    public Item getHeldItemObject() {
        return heldItem;
    }

    private void createPlayerInventoryGrid() {
        inventoryTable.clear();
        inventoryTable.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("hotbar_bg")));
        inventoryTable.pad(10);

        // Title
        Label titleLabel = new Label("Inventory", skin);
        inventoryTable.add(titleLabel).colspan(9).pad(5).row();

        // Grid of slots
        Table grid = new Table();
        grid.defaults().space(4);

        int cols = 9;
        for (int i = 0; i < GameContext.get().getPlayer().getInventory().getSize(); i++) {
            InventorySlotData slotData = new InventorySlotData(i, InventorySlotData.SlotType.INVENTORY, GameContext.get().getPlayer().getInventory());
// No need to set slotType again

            InventorySlotUI slot = new InventorySlotUI(slotData, skin, this, SLOT_SIZE);


            grid.add(slot).size(SLOT_SIZE);
            if ((i + 1) % cols == 0) {
                grid.row();
            }
        }
        inventoryTable.add(grid);
    }

    public void updateUI() {
        createChestInventoryGrid();
        createPlayerInventoryGrid();
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
        stage.addActor(heldItemGroup); // Add heldItemGroup to the stage
    }

    private void setupUI() {
        stage.clear();
        mainTable.clear();

        mainTable.setFillParent(true);
        mainTable.center();

        // Set mainTable as touchable
        mainTable.setTouchable(Touchable.enabled);

        // Add a listener to consume input events
        mainTable.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                // Consume the event to prevent it from propagating
                event.stop();
            }
        });

        // Set up background
        Pixmap bgPixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        bgPixmap.setColor(0, 0, 0, 0.5f);
        bgPixmap.fill();
        Texture bgTexture = new Texture(bgPixmap);
        mainTable.setBackground(new TextureRegionDrawable(bgTexture) {
            @Override
            public void draw(com.badlogic.gdx.graphics.g2d.Batch batch, float x, float y, float width, float height) {
                batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
                super.draw(batch, x, y, width, height);
            }
        });
        bgPixmap.dispose();

        Label titleLabel = new Label("Chest", skin);
        titleLabel.setFontScale(1.5f);
        mainTable.add(titleLabel).pad(20).row();

        chestTable.clear();
        inventoryTable.clear();
        chestTable.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("hotbar_bg")));
        chestTable.pad(10);
        createChestInventoryGrid();
        mainTable.add(chestTable).pad(10).row();
        inventoryTable.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("hotbar_bg")));
        inventoryTable.pad(10);
        createPlayerInventoryGrid();
        mainTable.add(inventoryTable).pad(10).row();
        TextButton closeButton = new TextButton("Close", skin);
        closeButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (gameScreen != null) {
                    gameScreen.closeChestScreen();
                }
            }
        });

        closeButtonTable.clear();
        closeButtonTable.add(closeButton).size(100, 40).pad(10);
        mainTable.add(closeButtonTable).row();
        mainTable.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                event.stop();
                return true;
            }

            @Override
            public boolean keyUp(InputEvent event, int keycode) {
                event.stop();
                return true;
            }

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                event.stop();
                return true;
            }
        });

        stage.addActor(mainTable);
        stage.addActor(heldItemGroup);
    }

    @Override
    public void show() {
        if (isVisible) return;
        isVisible = true;
        isClosing = false;

        try {
            this.heldItem = null; // Clear any previously held item
            updateHeldItemDisplay(); // Update the UI accordingly
            setupUI();
            updateUI();

            if (stage != null) {
                stage.getViewport().update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
            }

            GameLogger.info("Chest screen shown successfully");
        } catch (Exception e) {
            GameLogger.error("Error showing chest screen: " + e.getMessage());
            isVisible = false;
        }
    }

    @Override
    public void hide() {
        if (isClosing) return; // Prevent multiple hide() calls
        isClosing = true;
        if (!isVisible) {
            isClosing = false;
            return;
        }

        // Transfer any held item (if present) to the player's inventory
        ItemData heldItemData = InventoryConverter.itemToItemData(this.heldItem);
        isVisible = false;
        try {
            if (heldItemData != null) {
                GameContext.get().getPlayer().getInventory().addItem(heldItemData);
                this.heldItem = null;
                updateHeldItemDisplay();
            }

            // Save the chest state – note that saveChestState() should simply send the current chestData to the server
            saveChestState();

            // Clean up the UI elements
            if (stage != null) {
                stage.clear();
            }
            if (gameScreen != null && gameScreen.getChestHandler() != null) {
                gameScreen.getChestHandler().setChestOpen(false);
                gameScreen.getChestHandler().reset();
            }
            AudioManager.getInstance().playSound(AudioManager.SoundEffect.CHEST_CLOSE);
        } catch (Exception e) {
            GameLogger.error("Error during chest close: " + e.getMessage());
        } finally {
            isClosing = false;
        }
    }

    private void saveChestState() {
        // Play the chest close sound
        AudioManager.getInstance().playSound(AudioManager.SoundEffect.CHEST_CLOSE);

        // Save final chest state locally.
        if (GameContext.get().getPlayer() != null && GameContext.get().getWorld() != null) {
            PlaceableBlock block = GameContext.get().getPlayer().getWorld().getBlockManager().getBlockAt(
                (int) chestPosition.x, (int) chestPosition.y);
            if (block != null) {
                block.setChestOpen(false);
                block.setChestData(chestData); // Update block with the current chest data

                // Force-save the chunk containing the chest.
                int chunkX = Math.floorDiv((int) chestPosition.x, World.CHUNK_SIZE);
                int chunkY = Math.floorDiv((int) chestPosition.y, World.CHUNK_SIZE);
                Vector2 chunkPos = new Vector2(chunkX, chunkY);

                Chunk chunk = GameContext.get().getWorld().getChunks().get(chunkPos);
                if (chunk != null) {
                    GameContext.get().getWorld().saveChunkData(chunkPos, chunk);
                }
            }
        }

        // *** NEW: Send a chest update to the server ***
        GameContext.get().getGameClient().sendChestUpdate(chestData);

        // Reset the chest handler state (client–side cleanup)
        if (gameScreen != null && gameScreen.getChestHandler() != null) {
            gameScreen.getChestHandler().setChestOpen(false);
            gameScreen.getChestHandler().reset();
        }
    }


    @Override
    public void render(float delta) {
        if (!isVisible) return;

        // Don't clear the screen - we want to see the game world behind
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // Update the UI
        stage.act(delta);
        stage.draw();
        updateHeldItemPosition();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    public void updateHeldItemDisplay() {
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
                heldItemGroup.setVisible(true); // Make the group visible
            } else {
                heldItemImage.setVisible(false);
                heldItemCountLabel.setVisible(false);
                heldItemGroup.setVisible(false);
            }
        } else {
            heldItemImage.setVisible(false);
            heldItemCountLabel.setVisible(false);
            heldItemGroup.setVisible(false); // Hide the group when no held item
        }
    }

    private void updateHeldItemPosition() {
        if (heldItemGroup != null && heldItemGroup.isVisible()) {
            float x = Gdx.input.getX() - 16;
            float y = Gdx.graphics.getHeight() - Gdx.input.getY() - 16;
            heldItemGroup.setPosition(x, y);
        }
    }


    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        updateUI();
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void dispose() {
        if (stage != null) {
            stage.dispose();
        }
        if (batch != null) {
            batch.dispose();
        }
    }

    public boolean isVisible() {
        return isVisible;
    }

    public void setVisible(boolean visible) {
        this.isVisible = visible;
    }
}
