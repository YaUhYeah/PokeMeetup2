    package io.github.pokemeetup.screens.otherui;

    import com.badlogic.gdx.Gdx;
    import com.badlogic.gdx.graphics.Color;
    import com.badlogic.gdx.graphics.GL20;
    import com.badlogic.gdx.graphics.OrthographicCamera;
    import com.badlogic.gdx.graphics.g2d.Batch;
    import com.badlogic.gdx.graphics.g2d.SpriteBatch;
    import com.badlogic.gdx.graphics.g2d.TextureRegion;
    import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
    import com.badlogic.gdx.math.Rectangle;
    import com.badlogic.gdx.math.Vector2;
    import com.badlogic.gdx.scenes.scene2d.*;
    import com.badlogic.gdx.scenes.scene2d.ui.*;
    import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
    import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
    import com.badlogic.gdx.utils.Align;
    import com.badlogic.gdx.utils.Scaling;
    import io.github.pokemeetup.audio.AudioManager;
    import io.github.pokemeetup.blocks.BuildingData;
    import io.github.pokemeetup.blocks.BuildingTemplate;
    import io.github.pokemeetup.blocks.PlaceableBlock;
    import io.github.pokemeetup.blocks.SmartBuildingManager;
    import io.github.pokemeetup.context.GameContext;
    import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
    import io.github.pokemeetup.system.Player;
    import io.github.pokemeetup.system.data.ItemData;
    import io.github.pokemeetup.system.gameplay.inventory.Inventory;
    import io.github.pokemeetup.system.gameplay.overworld.World;
    import io.github.pokemeetup.utils.GameLogger;
    import io.github.pokemeetup.utils.textures.BlockTextureManager;
    import io.github.pokemeetup.utils.textures.TextureManager;

    import java.util.List;
    import java.util.Map;
    import java.util.UUID;
    public class BuildModeUI extends Table {
        private static final float SLOT_SIZE = 40f;
        private static final int HOTBAR_SLOTS = 9;
        private static final Color VALID_PREVIEW_COLOR = new Color(0, 1, 0, 0.3f);
        private static final Color INVALID_PREVIEW_COLOR = new Color(1, 0, 0, 0.3f);

        private final Skin skin;
        private final Table hotbarTable;
        public final BuildingHotbar buildingHotbar;
        private final ShapeRenderer shapeRenderer;
        private final BlockTextureManager blockTextureManager;
        private final SmartBuildingManager smartBuildingManager;

        private boolean inBuildingMode = false;
        private int selectedSlot = 0;
        private final Vector2 previewPosition = new Vector2();
        private boolean canPlaceAtPreview;
        private float stateTime = 0;

        public BuildModeUI(Skin skin) {
            super(skin); // Use the Table(Skin) constructor
            this.skin = skin;
            this.blockTextureManager = new BlockTextureManager();
            this.shapeRenderer = new ShapeRenderer();
            this.smartBuildingManager = new SmartBuildingManager(GameContext.get().getWorld());
            this.setFillParent(true);
            this.bottom();
            this.hotbarTable = new Table();
            this.buildingHotbar = new BuildingHotbar(skin);
            buildingHotbar.setVisible(false);
            Stack hotbarStack = new Stack(hotbarTable, buildingHotbar);
            Label buildModeLabel = new Label("Build Mode", skin);
            buildModeLabel.setColor(Color.YELLOW);
            this.add(buildModeLabel).padBottom(5).row(); // .row() moves to the next line
            this.add(hotbarStack);

            GameContext.get().getPlayer().getInventory().addObserver(this::refreshBuildInventory);
            refreshBuildInventory();
        }


        public boolean isInBuildingMode() {
            return inBuildingMode;
        }

        @Override
        public void act(float delta) {
            super.act(delta);
            stateTime += delta;
        }

        public void renderPlacementPreview(SpriteBatch batch, OrthographicCamera camera) {
            if (!isVisible()) return;

            int targetX = GameContext.get().getPlayer().getTileX();
            int targetY = GameContext.get().getPlayer().getTileY();

            switch (GameContext.get().getPlayer().getDirection()) {
                case "up": targetY++; break;
                case "down": targetY--; break;
                case "left": targetX--; break;
                case "right": targetX++; break;
            }

            previewPosition.set(targetX, targetY);
            canPlaceAtPreview = canPlaceAt(targetX, targetY);

            renderPlacementIndicator(camera, targetX, targetY);

            if (canPlaceAtPreview) {
                renderPreview(batch, targetX, targetY);
            }
        }

        private boolean canPlaceAt(int tileX, int tileY) {
            if (inBuildingMode) {
                BuildingData building = buildingHotbar.getSelectedBuilding();
                return building != null && canPlaceBuilding(building.getTemplate(), tileX, tileY);
            } else {
                return canPlaceBlockAt(tileX, tileY);
            }
        }

        private void renderPlacementIndicator(OrthographicCamera camera, int targetX, int targetY) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            shapeRenderer.setProjectionMatrix(camera.combined);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

            shapeRenderer.setColor(canPlaceAtPreview ? VALID_PREVIEW_COLOR : INVALID_PREVIEW_COLOR);

            Rectangle bounds;
            if(inBuildingMode) {
                BuildingData building = buildingHotbar.getSelectedBuilding();
                if (building != null) {
                    BuildingTemplate template = building.getTemplate();
                    bounds = new Rectangle(targetX * World.TILE_SIZE, targetY * World.TILE_SIZE, template.getWidth() * World.TILE_SIZE, template.getHeight() * World.TILE_SIZE);
                } else {
                    bounds = new Rectangle(targetX * World.TILE_SIZE, targetY * World.TILE_SIZE, World.TILE_SIZE, World.TILE_SIZE);
                }
            } else {
                bounds = new Rectangle(targetX * World.TILE_SIZE, targetY * World.TILE_SIZE, World.TILE_SIZE, World.TILE_SIZE);
            }

            shapeRenderer.rect(bounds.x, bounds.y, bounds.width, bounds.height);
            shapeRenderer.end();
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }

        private void renderPreview(SpriteBatch batch, int targetX, int targetY) {
            if (inBuildingMode) {
                renderBuildingPreview(batch, targetX, targetY);
            } else {
                renderBlockPreview(batch, targetX, targetY);
            }
        }

        private void renderBlockPreview(SpriteBatch batch, int targetX, int targetY) {
            ItemData selectedItem = GameContext.get().getPlayer().getBuildInventory().getItemAt(selectedSlot);
            if (selectedItem == null) return;
            PlaceableBlock.BlockType baseType = PlaceableBlock.BlockType.fromItemId(selectedItem.getItemId());
            if (baseType == null) return;

            PlaceableBlock previewBlock = new PlaceableBlock(baseType, new Vector2(targetX, targetY), null, false);
            TextureRegion blockTexture = BlockTextureManager.getBlockFrame(previewBlock, stateTime);

            if (blockTexture != null) {
                batch.begin();
                batch.setColor(1, 1, 1, 0.7f);
                batch.draw(blockTexture, targetX * World.TILE_SIZE, targetY * World.TILE_SIZE);
                batch.setColor(Color.WHITE);
                batch.end();
            }
        }

        private void renderBuildingPreview(SpriteBatch batch, int startX, int startY) {
            BuildingData buildingData = buildingHotbar.getSelectedBuilding();
            if (buildingData == null) return;
            BuildingTemplate template = buildingData.getTemplate();

            batch.begin();
            batch.setColor(1, 1, 1, 0.7f);

            for (int x = 0; x < template.getWidth(); x++) {
                for (int y = 0; y < template.getHeight(); y++) {
                    BuildingTemplate.BlockData blockData = template.getBlockAt(x, y);
                    if (blockData != null) {
                        PlaceableBlock previewBlock = new PlaceableBlock(blockData.type, new Vector2(startX + x, startY + y), null, blockData.isFlipped);
                        TextureRegion blockTexture = BlockTextureManager.getBlockFrame(previewBlock, stateTime);
                        if (blockTexture != null) {
                            float blockX = (startX + x) * World.TILE_SIZE;
                            float blockY = (startY + y) * World.TILE_SIZE;

                            if (blockData.isFlipped) {
                                batch.draw(blockTexture, blockX + blockTexture.getRegionWidth(), blockY, -blockTexture.getRegionWidth(), blockTexture.getRegionHeight());
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

        public void toggleBuildingMode() {
            inBuildingMode = !inBuildingMode;
            hotbarTable.setVisible(!inBuildingMode);
            buildingHotbar.setVisible(inBuildingMode);
        }

        public boolean tryPlace() {
            if (!GameContext.get().getPlayer().isBuildMode()) return false;
            if (!canPlaceAtPreview) return false;

            int targetX = (int)previewPosition.x;
            int targetY = (int)previewPosition.y;

            if (inBuildingMode) {
                return tryPlaceBuilding(targetX, targetY);
            } else {
                return tryPlaceBlock(targetX, targetY);
            }
        }

        public boolean tryPlaceBuilding(int tileX, int tileY) {
            BuildingData building = buildingHotbar.getSelectedBuilding();
            if (building == null) return false;

            for (Map.Entry<String, Integer> req : building.getRequirements().entrySet()) {
                if (!hasEnoughMaterials(req.getKey(), req.getValue())) {
                    GameLogger.info("Not enough materials for " + building.getName());
                    return false;
                }
            }

            if (building.getTemplate().placeBuilding(GameContext.get().getWorld(), tileX, tileY)) {
                consumeMaterials(building.getRequirements());
                if (GameContext.get().isMultiplayer()) {
                    NetworkProtocol.BuildingPlacement bp = building.getTemplate().toNetworkMessage(GameContext.get().getPlayer().getUsername(), tileX, tileY);
                    GameContext.get().getGameClient().sendBuildingPlacement(bp);
                }
                return true;
            }
            return false;
        }

        private boolean hasEnoughMaterials(String itemId, int required) {
            return GameContext.get().getPlayer().getInventory().getAllItems().stream()
                .filter(item -> item != null && item.getItemId().equals(itemId))
                .mapToInt(ItemData::getCount).sum() >= required;
        }

        private void consumeMaterials(Map<String, Integer> requirements) {
            Inventory inventory = GameContext.get().getPlayer().getInventory();
            for (Map.Entry<String, Integer> req : requirements.entrySet()) {
                inventory.removeItem(req.getKey(), req.getValue());
            }
            refreshBuildInventory(); // Refresh hotbar after consumption
        }

        private boolean canPlaceBuilding(BuildingTemplate template, int startX, int startY) {
            for (int x = 0; x < template.getWidth(); x++) {
                for (int y = 0; y < template.getHeight(); y++) {
                    if (template.getBlockAt(x, y) != null && !canPlaceBlockAt(startX + x, startY + y)) {
                        return false;
                    }
                }
            }
            return true;
        }

        private boolean canPlaceBlockAt(int tileX, int tileY) {
            return GameContext.get().getWorld().isPassable(tileX, tileY) &&
                GameContext.get().getWorld().getBlockManager().getBlockAt(tileX, tileY) == null;
        }

        public boolean tryPlaceBlock(int tileX, int tileY) {
            ItemData selectedItem = GameContext.get().getPlayer().getBuildInventory().getItemAt(selectedSlot);
            if (selectedItem == null) return false;

            ItemData inventoryItem = findInventoryItem(selectedItem.getUuid());
            if (inventoryItem == null) {
                refreshBuildInventory();
                return false;
            }

            PlaceableBlock.BlockType blockType = PlaceableBlock.BlockType.fromItemId(inventoryItem.getItemId());
            if (blockType == null) return false;

            if (GameContext.get().getWorld().getBlockManager().placeBlock(blockType, tileX, tileY)) {
                consumeItem(inventoryItem);
                refreshBuildInventory();
                AudioManager.getInstance().playSound(AudioManager.SoundEffect.BLOCK_PLACE_0);
                return true;
            }
            return false;
        }

        private ItemData findInventoryItem(UUID uuid) {
            return GameContext.get().getPlayer().getInventory().getAllItems().stream()
                .filter(item -> item != null && item.getUuid().equals(uuid))
                .findFirst().orElse(null);
        }

        private void consumeItem(ItemData itemToConsume) {
            itemToConsume.setCount(itemToConsume.getCount() - 1);
            if (itemToConsume.getCount() <= 0) {
                GameContext.get().getPlayer().getInventory().removeItem(itemToConsume);
            } else {
                GameContext.get().getPlayer().getInventory().notifyObservers();
            }
        }

        public void refreshBuildInventory() {
            Inventory buildInv = GameContext.get().getPlayer().getBuildInventory();
            buildInv.clear();
            int buildSlot = 0;
            for (ItemData item : GameContext.get().getPlayer().getInventory().getAllItems()) {
                if (item != null && isBlockItem(item.getItemId())) {
                    if (buildSlot < HOTBAR_SLOTS) {
                        buildInv.setItemAt(buildSlot++, item);
                    } else break;
                }
            }
            updateHotbarContent();
        }

        private boolean isBlockItem(String itemId) {
            return PlaceableBlock.BlockType.fromItemId(itemId) != null;
        }

        private void updateHotbarContent() {
            hotbarTable.clear();
            hotbarTable.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("hotbar_bg")));
            hotbarTable.pad(4);

            for (int i = 0; i < HOTBAR_SLOTS; i++) {
                final int slotIndex = i;
                Table slotCell = createSlotCell(i);
                slotCell.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        selectSlot(slotIndex);
                    }
                });
                hotbarTable.add(slotCell).size(SLOT_SIZE).pad(2);
            }
        }

        private Table createSlotCell(int index) {
            Table slotCell = new Table();
            slotCell.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion(index == selectedSlot ? "slot_selected" : "slot_normal")));
            ItemData item = GameContext.get().getPlayer().getBuildInventory().getItemAt(index);

            if (item != null) {
                TextureRegion itemIcon = blockTextureManager.getItemIcon(item.getItemId());
                if (itemIcon != null) {
                    Image itemImage = new Image(itemIcon);
                    itemImage.setScaling(Scaling.fit);
                    slotCell.add(itemImage).size(SLOT_SIZE - 8).center().expand();

                    if (item.getCount() > 1) {
                        Label countLabel = new Label(String.valueOf(item.getCount()), skin);
                        countLabel.setColor(Color.WHITE);
                        slotCell.add(countLabel).align(Align.bottomRight).pad(2);
                    }
                }
            }
            return slotCell;
        }

        public void selectSlot(int index) {
            if (index >= 0 && index < HOTBAR_SLOTS) {
                selectedSlot = index;
                updateHotbarContent();
            }
        }
    }
