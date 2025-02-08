package io.github.pokemeetup.screens.otherui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Scaling;
import io.github.pokemeetup.blocks.BuildingData;
import io.github.pokemeetup.blocks.BuildingTemplate;
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.blocks.SmartBuildingManager;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.UITransitionManager;
import io.github.pokemeetup.system.data.BlockSaveData;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.BlockTextureManager;
import io.github.pokemeetup.utils.textures.TextureManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BuildModeUI extends Group {
    private static final float PADDING = 10f;
    private static final float PARTY_UI_HEIGHT = 50f;
    private static final float SLOT_SIZE = 40f;
    private static final int HOTBAR_SLOTS = 9;
    private static final Color VALID_PREVIEW_COLOR = new Color(0, 1, 0, 0.3f);
    private static final Color INVALID_PREVIEW_COLOR = new Color(1, 0, 0, 0.3f);
    private final Skin skin;
    private final BuildingHotbar buildingHotbar;
    private final Table mainTable;
    private final Table hotbarTable;
    private final SmartBuildingManager smartBuildingManager;
    private final ShapeRenderer shapeRenderer;
    private final BlockTextureManager blockTextureManager;
    private final boolean disposed = false;
    private final Vector2 previewPosition;
    private final UITransitionManager transitionManager = new UITransitionManager();
    private final BitmapFont font;
    private boolean inBuildingMode = false;
    private int selectedSlot = 0;
    private boolean canPlaceAtPreview;
    private float stateTime = 0;

    public BuildModeUI(Skin skin) {
        this.skin = skin;
        this.font = new BitmapFont(Gdx.files.internal("Skins/default.fnt"));

        this.buildingHotbar = new BuildingHotbar(skin);
        buildingHotbar.setVisible(false);

        float hotbarY = PARTY_UI_HEIGHT + PADDING;
        buildingHotbar.setPosition(PADDING, hotbarY);
        this.addActor(buildingHotbar);

        this.blockTextureManager = new BlockTextureManager();
        this.shapeRenderer = new ShapeRenderer();
        this.previewPosition = new Vector2();

        this.mainTable = new Table();
        this.mainTable.setFillParent(true);
        this.mainTable.bottom().center();
        this.mainTable.padBottom(PARTY_UI_HEIGHT + PADDING);

        this.hotbarTable = new Table();
        this.hotbarTable.setTouchable(Touchable.enabled);

        // Initialize and populate the build inventory with all placeable blocks.
        refreshBuildInventory();

        this.smartBuildingManager = new SmartBuildingManager(GameContext.get().getWorld());
        this.mainTable.add(hotbarTable).expandX().center().bottom();

        this.mainTable.setTouchable(Touchable.disabled);
        this.addActor(mainTable);

        float hotbarHeight = SLOT_SIZE + PADDING * 2;
        setSize(Gdx.graphics.getWidth(), hotbarHeight);
        setPosition(0, PARTY_UI_HEIGHT + PADDING);

        GameContext.get().getPlayer().getInventory().addObserver(() -> {
            refreshBuildInventory();
        });
    }

    public boolean isInBuildingMode() {
        return inBuildingMode;
    }

    public void resize(int width) {
        setSize(width, SLOT_SIZE + PADDING * 2);
        setPosition(0, PARTY_UI_HEIGHT + PADDING);
        mainTable.invalidateHierarchy();
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);
    }

    public void renderPlacementPreview(SpriteBatch batch, OrthographicCamera camera) {
        if (!GameContext.get().getPlayer().isBuildMode() || !this.isVisible()) return;

        int targetX = GameContext.get().getPlayer().getTileX();
        int targetY = GameContext.get().getPlayer().getTileY();

        switch (GameContext.get().getPlayer().getDirection()) {
            case "up":
                targetY++;
                break;
            case "down":
                targetY--;
                break;
            case "left":
                targetX--;
                break;
            case "right":
                targetX++;
                break;
        }

        previewPosition.set(targetX, targetY);
        canPlaceAtPreview = canPlaceBlockAt(targetX, targetY);

        renderPlacementIndicator(camera, targetX, targetY);

        // If placement is valid, render block preview
        if (canPlaceAtPreview) {
            renderBlockPreview(batch, targetX, targetY);
        }
    }

    private void renderPlacementIndicator(OrthographicCamera camera, int targetX, int targetY) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        Color previewColor = canPlaceAtPreview ? VALID_PREVIEW_COLOR : INVALID_PREVIEW_COLOR;
        shapeRenderer.setColor(previewColor);

        float previewX = targetX * World.TILE_SIZE;
        float previewY = targetY * World.TILE_SIZE;
        shapeRenderer.rect(previewX, previewY, World.TILE_SIZE, World.TILE_SIZE);

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void renderBlockPreview(SpriteBatch batch, int targetX, int targetY) {
        ItemData selectedItem = GameContext.get().getPlayer().getBuildInventory().getItemAt(selectedSlot);
        if (selectedItem == null) return;

        PlaceableBlock.BlockType baseType = PlaceableBlock.BlockType.fromItemId(selectedItem.getItemId());
        if (baseType == null) return;

        String groupId = smartBuildingManager.getGroupIdForBlockType(baseType);
        PlaceableBlock.BlockType previewType = groupId != null ?
            smartBuildingManager.getSmartBlockType(groupId, targetX, targetY) : baseType;

        if (previewType != null) {
            Vector2 blockPosition = new Vector2(targetX, targetY);
            PlaceableBlock previewBlock = new PlaceableBlock(previewType, blockPosition, null, false);
            TextureRegion blockTexture = BlockTextureManager.getBlockFrame(previewBlock, stateTime);

            if (blockTexture != null) {
                batch.begin();
                float previewX = targetX * World.TILE_SIZE;
                float previewY = targetY * World.TILE_SIZE;

                batch.setColor(1, 1, 1, 0.7f);
                batch.draw(blockTexture, previewX, previewY);
                batch.setColor(Color.WHITE);
                batch.end();
            }
        }
    }

    public void toggleBuildingMode() {
        inBuildingMode = !inBuildingMode;

        if (inBuildingMode) {
            // Hide regular hotbar
            hotbarTable.setVisible(false);
            // Show building hotbar
            buildingHotbar.setVisible(true);
            mainTable.clear();
            mainTable.add(buildingHotbar).expandX().center().bottom();
            buildingHotbar.selectSlot(0);
        } else {
            // Hide building hotbar
            buildingHotbar.setVisible(false);
            mainTable.clear();
            mainTable.add(hotbarTable).expandX().center().bottom();
            hotbarTable.setVisible(true);
            selectSlot(0);
        }
    }


    public boolean tryPlaceBuilding(int tileX, int tileY) {
        if (!inBuildingMode) return false;

        BuildingData building = buildingHotbar.getSelectedBuilding();
        if (building == null) {
            GameLogger.info("No building selected");
            return false;
        }

        for (Map.Entry<String, Integer> req : building.getRequirements().entrySet()) {
            if (!hasEnoughMaterials(req.getKey(), req.getValue())) {
                GameLogger.info("Not enough materials: " + req.getKey() +
                    " (Need: " + req.getValue() +
                    ", Have: " + countPlayerItems(req.getKey()) + ")");
                return false;
            }
        }

        BuildingTemplate template = building.getTemplate();
        if (template == null) {
            GameLogger.error("Building template is null");
            return false;
        }

        if (!canPlaceBuilding(template, tileX, tileY)) {
            GameLogger.info("Cannot place building at " + tileX + "," + tileY);
            return false;
        }

        boolean placed = template.placeBuilding(GameContext.get().getWorld(), tileX, tileY);
        if (placed) {
            consumeMaterials(building.getRequirements());

            String username = GameContext.get().getPlayer().getUsername();
            if (GameContext.get().isMultiplayer()) {
                NetworkProtocol.BuildingPlacement bp = template.toNetworkMessage(username, tileX, tileY);

                // Send the building placement to the server.
                GameContext.get().getGameClient().sendBuildingPlacement(bp);
            }
            GameLogger.info("Successfully placed building: " + building.getName());
            return true;
        }

        GameLogger.info("Failed to place building");
        return false;
    }

    private boolean hasEnoughMaterials(String itemId, int required) {
        int count = 0;
        for (int i = 0; i < GameContext.get().getPlayer().getInventory().getSize(); i++) {
            ItemData item = GameContext.get().getPlayer().getInventory().getItemAt(i);
            if (item != null && item.getItemId().equals(itemId)) {
                count += item.getCount();
            }
        }
        return count >= required;
    }

    private void consumeMaterials(Map<String, Integer> requirements) {
        for (Map.Entry<String, Integer> req : requirements.entrySet()) {
            int remaining = req.getValue();
            for (int i = 0; i < GameContext.get().getPlayer().getInventory().getSize() && remaining > 0; i++) {
                ItemData item = GameContext.get().getPlayer().getInventory().getItemAt(i);
                if (item != null && item.getItemId().equals(req.getKey())) {
                    if (item.getCount() <= remaining) {
                        remaining -= item.getCount();
                        GameContext.get().getPlayer().getInventory().removeItemAt(i);
                        i--;
                    } else {
                        item.setCount(item.getCount() - remaining);
                        remaining = 0;
                    }
                }
            }
        }
        refreshBuildInventory();
    }

    public void show() {
        if (!disposed) {
            this.setVisible(true);
            this.setTouchable(Touchable.enabled);
            mainTable.setVisible(true);
            refreshBuildInventory();
        }
    }

    public void startHideTransition(float duration) {
        transitionManager.startHideTransition(duration, () -> {
            mainTable.setVisible(false);
            this.setVisible(false);
            this.setTouchable(Touchable.disabled);
        });
    }

    public void hide() {
        startHideTransition(UITransitionManager.DEFAULT_TRANSITION_TIME);
    }

    public void updateHotbarContent() {
        hotbarTable.clear();
        hotbarTable.setBackground(new TextureRegionDrawable(
            TextureManager.ui.findRegion("hotbar_bg")
        ));
        hotbarTable.pad(4);

        for (int i = 0; i < HOTBAR_SLOTS; i++) {
            final int slotIndex = i;
            Table slotCell = createSlotCell(i);

            slotCell.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    selectSlot(slotIndex);
                    event.stop();
                }
            });

            hotbarTable.add(slotCell).size(SLOT_SIZE).pad(2);
        }
    }

    private boolean isBlockItem(String itemId) {
        // Normalize item ID
        String normalizedId = itemId.toLowerCase().replace("_item", "");
        try {
            PlaceableBlock.BlockType.valueOf(normalizedId.toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private Table createSlotCell(int index) {
        Table slotCell = new Table();
        slotCell.setBackground(new TextureRegionDrawable(
            TextureManager.ui.findRegion(index == selectedSlot ? "slot_selected" : "slot_normal")
        ));

        ItemData item = GameContext.get().getPlayer().getBuildInventory().getItemAt(index);
        if (item != null) {
            TextureRegion itemIcon = blockTextureManager.getItemIcon(item.getItemId());
            if (itemIcon != null) {
                Image itemImage = new Image(itemIcon);
                itemImage.setScaling(Scaling.fit);
                slotCell.add(itemImage).width(SLOT_SIZE - 8).center().expand();

                if (item.getCount() > 1) {
                    Label countLabel = new Label(String.valueOf(item.getCount()), skin);
                    countLabel.setColor(Color.WHITE);
                    countLabel.setPosition(
                        slotCell.getWidth() - countLabel.getWidth() - 2,
                        2
                    );
                    slotCell.addActor(countLabel);
                }
            }
        }

        return slotCell;
    }

    public void dispose() {
        if (shapeRenderer != null) {
            shapeRenderer.dispose();
        }
    }

    public void selectSlot(int index) {
        if (index >= 0 && index < HOTBAR_SLOTS) {
            selectedSlot = index;
            ItemData selectedItem = GameContext.get().getPlayer().getBuildInventory().getItemAt(index);

            if (selectedItem != null) {
                GameContext.get().getPlayer().selectBlockItem(index);
                PlaceableBlock.BlockType blockType = PlaceableBlock.BlockType.fromItemId(selectedItem.getItemId());
                if (blockType != null) {
                    GameLogger.info("Selected block: " + blockType.id);
                }
            }

            updateHotbarContent();
        }
    }

    public void update(float deltaTime) {
        stateTime += deltaTime;
        ItemData selectedItem = GameContext.get().getPlayer().getBuildInventory().getItemAt(selectedSlot);
        if (selectedItem != null) {
            GameLogger.info("Selected: " + selectedItem.getItemId() +
                " at pos: " + previewPosition +
                " canPlace: " + canPlaceAtPreview);
        }
    }

    public void render(SpriteBatch batch, OrthographicCamera camera) {
        GameLogger.info("BuildModeUI rendering - BuildingMode: " + inBuildingMode +
            ", Hotbar visible: " + buildingHotbar.isVisible());
        if (!this.isVisible() || !GameContext.get().getPlayer().isBuildMode() || !mainTable.isVisible()) return;

        stateTime += Gdx.graphics.getDeltaTime();

        if (inBuildingMode) {
            renderBuildingPreview(batch, camera);
        } else {
            renderPlacementPreview(batch, camera);
        }
    }

    private void renderBuildingPreview(SpriteBatch batch, OrthographicCamera camera) {
        if (!GameContext.get().getPlayer().isBuildMode() || !this.isVisible()) return;

        int targetX = GameContext.get().getPlayer().getTileX();
        int targetY = GameContext.get().getPlayer().getTileY();

        switch (GameContext.get().getPlayer().getDirection()) {
            case "up":
                targetY++;
                break;
            case "down":
                targetY--;
                break;
            case "left":
                targetX--;
                break;
            case "right":
                targetX++;
                break;
        }

        BuildingData buildingData = buildingHotbar.getSelectedBuilding();
        if (buildingData == null) return;

        BuildingTemplate template = buildingData.getTemplate();
        boolean canPlace = canPlaceBuilding(template, targetX, targetY);

        renderBuildingPlacementArea(camera, targetX, targetY, template, canPlace);

        if (canPlace) {
            renderBuildingBlocks(batch, targetX, targetY, template);
        }

        renderBuildingRequirements(batch, camera, buildingData, canPlace);
    }

    private boolean canPlaceBuilding(BuildingTemplate template, int startX, int startY) {
        int width = template.getWidth();
        int height = template.getHeight();

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                BuildingTemplate.BlockData blockData = template.getBlockAt(x, y);
                if (blockData != null) {
                    int worldX = startX + x;
                    int worldY = startY + y;

                    if (!GameContext.get().getWorld().isPassable(worldX, worldY)) {
                        return false;
                    }

                    if (GameContext.get().getWorld().getBlockManager().getBlockAt(worldX, worldY) != null) {
                        return false;
                    }

                    PlaceableBlock.BlockType blockType = blockData.type;
                    if (blockType.hasCollision && !isSpaceClear(worldX, worldY)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean isSpaceClear(int x, int y) {
        List<WorldObject> objects = GameContext.get().getWorld().getObjectManager()
            .getObjectsNearPosition(x * World.TILE_SIZE, y * World.TILE_SIZE);

        for (WorldObject obj : objects) {
            Rectangle objBounds = obj.getCollisionBox();
            if (objBounds != null) {
                Rectangle blockBounds = new Rectangle(
                    x * World.TILE_SIZE,
                    y * World.TILE_SIZE,
                    World.TILE_SIZE,
                    World.TILE_SIZE
                );
                if (objBounds.overlaps(blockBounds)) {
                    return false;
                }
            }
        }

        Chunk chunk = GameContext.get().getWorld().getChunkAtPosition(x, y);
        if (chunk != null) {
            int localX = Math.floorMod(x, Chunk.CHUNK_SIZE);
            int localY = Math.floorMod(y, Chunk.CHUNK_SIZE);
            return chunk.isPassable(localX, localY);
        }

        return false;
    }

    private void renderBuildingPlacementArea(OrthographicCamera camera, int startX, int startY,
                                             BuildingTemplate template, boolean canPlace) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        Color areaColor = canPlace ? VALID_PREVIEW_COLOR : INVALID_PREVIEW_COLOR;
        shapeRenderer.setColor(areaColor);

        float previewX = startX * World.TILE_SIZE;
        float previewY = startY * World.TILE_SIZE;
        shapeRenderer.rect(
            previewX,
            previewY,
            template.getWidth() * World.TILE_SIZE,
            template.getHeight() * World.TILE_SIZE
        );

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void renderBuildingBlocks(SpriteBatch batch, int startX, int startY,
                                      BuildingTemplate template) {
        batch.begin();
        batch.setColor(1, 1, 1, 0.7f);

        for (int x = 0; x < template.getWidth(); x++) {
            for (int y = 0; y < template.getHeight(); y++) {
                BuildingTemplate.BlockData blockData = template.getBlockAt(x, y);
                if (blockData != null) {
                    PlaceableBlock previewBlock = new PlaceableBlock(
                        blockData.type,
                        new Vector2(startX + x, startY + y),
                        null,
                        blockData.isFlipped
                    );

                    TextureRegion blockTexture = BlockTextureManager.getBlockFrame(previewBlock, stateTime);
                    if (blockTexture != null) {
                        float blockX = (startX + x) * World.TILE_SIZE;
                        float blockY = (startY + y) * World.TILE_SIZE;

                        if (blockData.isFlipped) {
                            batch.draw(
                                blockTexture,
                                blockX + blockTexture.getRegionWidth(),
                                blockY,
                                -blockTexture.getRegionWidth(),
                                blockTexture.getRegionHeight()
                            );
                        } else {
                            batch.draw(blockTexture, blockX, blockY);
                        }
                    }
                }
            }
        }

        batch.setColor(Color.WHITE);
        batch.end();
    }

    private void renderBuildingRequirements(SpriteBatch batch, OrthographicCamera camera,
                                            BuildingData building, boolean canPlace) {
        batch.begin();
        String title = building.getName();

        // Calculate starting Y from top of screen, and center X
        float startY = Gdx.graphics.getHeight() - 60;
        float centerX = Gdx.graphics.getWidth() / 2f;

        BitmapFont.BitmapFontData fontData = font.getData();
        fontData.setScale(1f);
        font.getData().markupEnabled = true;

        // Draw building name centered
        float titleWidth = font.getRegion().getTexture().getWidth(); // Approximation, better use GlyphLayout
        // Use GlyphLayout for precise width calculation
        com.badlogic.gdx.graphics.g2d.GlyphLayout layout = new com.badlogic.gdx.graphics.g2d.GlyphLayout();

        layout.setText(font, title);
        float textWidth = layout.width;
        font.setColor(canPlace ? Color.WHITE : Color.RED);
        font.draw(batch, title, centerX - textWidth / 2, startY);

        float requirementY = startY - 20;
        for (Map.Entry<String, Integer> req : building.getRequirements().entrySet()) {
            String itemId = req.getKey();
            int required = req.getValue();
            int available = countPlayerItems(itemId);

            Color textColor = available >= required ? Color.GREEN : Color.RED;
            font.setColor(textColor);

            String text = String.format("%s: %d/%d", getItemDisplayName(itemId), available, required);
            layout.setText(font, text);
            textWidth = layout.width;

            font.draw(batch, text, centerX - textWidth / 2, requirementY);
            requirementY -= 20;
        }

        font.setColor(Color.WHITE);
        batch.end();
    }


    private int countPlayerItems(String itemId) {
        int count = 0;
        for (int i = 0; i < GameContext.get().getPlayer().getInventory().getSize(); i++) {
            ItemData item = GameContext.get().getPlayer().getInventory().getItemAt(i);
            if (item != null && item.getItemId().equals(itemId)) {
                count += item.getCount();
            }
        }
        return count;
    }

    private String getItemDisplayName(String itemId) {
        return itemId.substring(0, 1).toUpperCase() +
            itemId.substring(1).toLowerCase().replace('_', ' ');
    }

    private boolean canPlaceBlockAt(int tileX, int tileY) {
        int playerTileX = GameContext.get().getPlayer().getTileX();
        int playerTileY = GameContext.get().getPlayer().getTileY();

        boolean isAdjacent = false;
        switch (GameContext.get().getPlayer().getDirection()) {
            case "up":
                isAdjacent = (tileX == playerTileX && tileY == playerTileY + 1);
                break;
            case "down":
                isAdjacent = (tileX == playerTileX && tileY == playerTileY - 1);
                break;
            case "left":
                isAdjacent = (tileX == playerTileX - 1 && tileY == playerTileY);
                break;
            case "right":
                isAdjacent = (tileX == playerTileX + 1 && tileY == playerTileY);
                break;
        }

        if (!isAdjacent) {
            GameLogger.info("Position not adjacent to player in facing direction");
            return false;
        }

        PlaceableBlock existingBlock = GameContext.get().getWorld().getBlockManager().getBlockAt(tileX, tileY);
        if (existingBlock != null) {
            GameLogger.info("Block already exists at target position");
            return false;
        }

        return GameContext.get().getWorld().isPassable(tileX, tileY);
    }

    public boolean tryPlaceBlock(int tileX, int tileY) {
        if (!GameContext.get().getPlayer().isBuildMode()) return false;
        if (!canPlaceBlockAt(tileX, tileY)) return false;
        if (inBuildingMode) {
            return tryPlaceBuilding(tileX, tileY);
        }

        synchronized (GameContext.get().getPlayer().getInventory().getInventoryLock()) {
            ItemData selectedItem = GameContext.get().getPlayer().getBuildInventory().getItemAt(selectedSlot);
            if (selectedItem == null) {
                GameLogger.info("No item selected in build inventory");
                return false;
            }

            try {
                UUID selectedItemUUID = selectedItem.getUuid();
                ItemData inventoryItem = findInventoryItem(selectedItemUUID);
                if (inventoryItem == null) {
                    GameLogger.info("Item not found in main inventory: " + selectedItem.getItemId());
                    updateHotbarContent();
                    return false;
                }

                PlaceableBlock.BlockType blockType = PlaceableBlock.BlockType.fromItemId(inventoryItem.getItemId());
                if (blockType == null) {
                    GameLogger.error("Invalid block type for item: " + inventoryItem.getItemId());
                    return false;
                }

                String groupId = smartBuildingManager.getGroupIdForBlockType(blockType);
                if (groupId != null) {
                    PlaceableBlock.BlockType smartType = smartBuildingManager.getSmartBlockType(groupId, tileX, tileY);
                    if (smartType != null) {
                        blockType = smartType;
                    }
                }

                boolean placed = GameContext.get().getWorld().getBlockManager()
                    .placeBlockFromPlayer(blockType);

                if (placed) {
                    if (inventoryItem.getCount() > 1) {
                        inventoryItem.setCount(inventoryItem.getCount() - 1);
                    } else {
                        removeInventoryItem(selectedItemUUID);
                    }

                    if (groupId != null) {
                        smartBuildingManager.updateSurroundingBlocks(tileX, tileY, groupId);
                    }

                    saveBlockData(blockType, tileX, tileY);
                    updateHotbarContent();

                    return true;
                }
            } catch (Exception e) {
                GameLogger.error("Error placing block: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Refreshes the build inventory by clearing all slots and repopulating them
     * from the player's main inventory. It includes all placeable blocks the player has,
     * up to the hotbar slot limit.
     */
    public void refreshBuildInventory() {
        // First, clear all build inventory slots
        for (int i = 0; i < HOTBAR_SLOTS; i++) {
            GameContext.get().getPlayer().getBuildInventory().removeItemAt(i);
        }

        int buildSlot = 0;
        // Fill slots with placeable blocks from player's main inventory
        for (int i = 0; i < GameContext.get().getPlayer().getInventory().getSize() && buildSlot < HOTBAR_SLOTS; i++) {
            ItemData item = GameContext.get().getPlayer().getInventory().getItemAt(i);
            if (item != null && isBlockItem(item.getItemId())) {
                GameContext.get().getPlayer().getBuildInventory().setItemAt(buildSlot++, item);
            }
        }

        updateHotbarContent();
    }

    private ItemData findInventoryItem(UUID itemUUID) {
        for (int i = 0; i < GameContext.get().getPlayer().getInventory().getSize(); i++) {
            ItemData item = GameContext.get().getPlayer().getInventory().getItemAt(i);
            if (item != null && item.getUuid().equals(itemUUID)) {
                return item;
            }
        }
        return null;
    }

    private void removeInventoryItem(UUID itemUUID) {
        for (int i = 0; i < GameContext.get().getPlayer().getInventory().getSize(); i++) {
            ItemData item = GameContext.get().getPlayer().getInventory().getItemAt(i);
            if (item != null && item.getUuid().equals(itemUUID)) {
                GameContext.get().getPlayer().getInventory().removeItemAt(i);
                break;
            }
        }
    }

    private void saveBlockData(PlaceableBlock.BlockType blockType, int tileX, int tileY) {
        if (GameContext.get().getWorld().getWorldData() != null) {
            BlockSaveData blockData = GameContext.get().getWorld().getWorldData().getBlockData();
            if (blockData == null) {
                blockData = new BlockSaveData();
                GameContext.get().getWorld().getWorldData().setBlockData(blockData);
            }

            String chunkKey = getChunkKeyForPosition(tileX, tileY);
            BlockSaveData.BlockData data = new BlockSaveData.BlockData(blockType.getId(), tileX, tileY);
            blockData.addBlock(chunkKey, data);
            GameContext.get().getWorld().getWorldData().setDirty(true);
            GameContext.get().getWorld().save();
        }
    }

    private String getChunkKeyForPosition(int tileX, int tileY) {
        int chunkX = Math.floorDiv(tileX, World.CHUNK_SIZE);
        int chunkY = Math.floorDiv(tileY, World.CHUNK_SIZE);
        return chunkX + "," + chunkY;
    }
}
