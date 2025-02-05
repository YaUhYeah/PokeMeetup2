package io.github.pokemeetup.screens.otherui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.*;
import com.badlogic.gdx.utils.Align;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.screens.InventoryScreenInterface;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.system.gameplay.inventory.crafting.CraftingSystem;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.InventoryLock;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.InventorySlotData;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.InventorySlotDataObserver;
import io.github.pokemeetup.system.gameplay.inventory.secureinventories.ItemContainer;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;

import java.util.UUID;

public class InventorySlotUI extends Table implements InventorySlotDataObserver {
    public static final int ITEM_SIZE = 32;
    private static final int SLOT_SIZE = 40;
    private static final float DURABILITY_BAR_HEIGHT = 4f;
    private static final float DURABILITY_BAR_PADDING = 2f;
    private final InventorySlotData slotData;
    private final Skin skin;
    private final InventoryScreenInterface screenInterface;
    private Image itemImage;
    private Label countLabel;
    private Label itemNameLabel;
    private Label durabilityLabel;
    private Image durabilityBar;

    public InventorySlotUI(InventorySlotData slotData, Skin skin, InventoryScreenInterface screenInterface) {
        this.slotData = slotData;
        this.skin = skin;
        this.screenInterface = screenInterface;

        setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("slot_normal")));
        setSize(SLOT_SIZE, SLOT_SIZE);
        setTouchable(Touchable.enabled);

        // Order of calls adjusted:
        setupContents();
        setupTooltip();           // Setup tooltip labels before updateSlot()
        slotData.addObserver(this);
        updateSlot();             // Now we can safely call updateSlot()
        setupInput();
    }

    public void forceUpdate() {
        updateSlot();
    }

    private void setupTooltip() {
        Table tooltipTable = new Table();
        tooltipTable.setBackground(skin.newDrawable("white", new Color(0, 0, 0, 0.7f)));
        tooltipTable.pad(5);

        Label.LabelStyle tooltipStyle = new Label.LabelStyle(skin.getFont("default-font"), Color.WHITE);

        // Initialize labels here
        itemNameLabel = new Label("", tooltipStyle);
        durabilityLabel = new Label("", tooltipStyle);

        tooltipTable.add(itemNameLabel).left().row();
        tooltipTable.add(durabilityLabel).left();

        Tooltip<Table> tooltip = new Tooltip<>(tooltipTable);
        TooltipManager tooltipManager = TooltipManager.getInstance();
        tooltipManager.initialTime = 0f;
        tooltipManager.subsequentTime = 0f;
        addListener(tooltip);
    }

    public void updateSlot() {
        ItemData itemData = getSlotItemData();
        if (slotData.getSlotType() == InventorySlotData.SlotType.CRAFTING_RESULT) {
            CraftingSystem cs = screenInterface.getCraftingSystem();
            itemData = cs != null ? cs.getCraftingResult() : null;
        }

        if (itemData != null && itemData.getCount() > 0) {
            TextureRegion itemTexture = getItemTexture(itemData.getItemId());
            if (itemTexture != null) {
                itemImage.setDrawable(new TextureRegionDrawable(itemTexture));
                itemImage.setVisible(true);

                countLabel.setVisible(itemData.getCount() > 1);
                if (itemData.getCount() > 1) {
                    countLabel.setText(String.valueOf(itemData.getCount()));
                }

                if (itemData.getMaxDurability() > 0) {
                    float percentage = itemData.getDurabilityPercentage();
                    percentage = Math.max(0f, Math.min(1f, percentage));
                    durabilityBar.setColor(getDurabilityColor(percentage));
                    float barWidth = (SLOT_SIZE - DURABILITY_BAR_PADDING * 2) * percentage;
                    durabilityBar.setSize(barWidth, DURABILITY_BAR_HEIGHT);
                    durabilityBar.setPosition(DURABILITY_BAR_PADDING, DURABILITY_BAR_PADDING);
                    durabilityBar.setVisible(true);
                } else {
                    durabilityBar.setVisible(false);
                }
            } else {
                // Texture missing fallback
                itemImage.setVisible(false);
                countLabel.setVisible(false);
                durabilityBar.setVisible(false);
            }
        } else {
            itemImage.setVisible(false);
            countLabel.setVisible(false);
            durabilityBar.setVisible(false);
        }

        updateTooltip();
    }

    private void setupContents() {
        itemImage = new Image();
        itemImage.setSize(ITEM_SIZE, ITEM_SIZE);
        itemImage.setVisible(false);

        countLabel = new Label("", skin);
        countLabel.setAlignment(Align.bottomRight);
        countLabel.setVisible(false);

        durabilityBar = new Image(skin.newDrawable("white"));
        durabilityBar.setVisible(false);
        durabilityBar.setSize(0, DURABILITY_BAR_HEIGHT);

        Container<Image> durabilityContainer = new Container<>(durabilityBar);
        durabilityContainer.align(Align.bottomLeft);
        durabilityContainer.pad(DURABILITY_BAR_PADDING);
        durabilityContainer.setTouchable(Touchable.disabled);
        durabilityContainer.setFillParent(false);

        Stack stack = new Stack();
        stack.setSize(SLOT_SIZE, SLOT_SIZE);
        stack.add(itemImage);
        stack.add(durabilityContainer);
        stack.add(countLabel);

        add(stack).expand().fill().size(SLOT_SIZE, SLOT_SIZE);
    }

    @Override
    public void onSlotDataChanged() {
        updateSlot();
    }

    private void updateTooltip() {
        ItemData itemData = getSlotItemData();
        if (itemData != null) {
            itemNameLabel.setText(itemData.getItemId());
            if (itemData.getMaxDurability() > 0) {
                durabilityLabel.setText("Durability: " +
                    itemData.getDurability() + "/" +
                    itemData.getMaxDurability());
                durabilityLabel.setVisible(true);
            } else {
                durabilityLabel.setVisible(false);
            }
        } else {
            itemNameLabel.setText("");
            durabilityLabel.setVisible(false);
        }
    }

    private TextureRegion getItemTexture(String itemId) {
        TextureRegion texture = TextureManager.items.findRegion(itemId.toLowerCase() + "_item");
        if (texture == null) {
            texture = TextureManager.items.findRegion(itemId.toLowerCase());
        }
        return texture;
    }

    private void setupInput() {
        addListener(new InputListener() {
            private boolean isHovered = false;
            private Vector2 touchDownPos = new Vector2();
            private long touchDownTime;

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                touchDownPos.set(x, y);
                touchDownTime = System.currentTimeMillis();
                setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("slot_selected")));
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                float moveDistance = touchDownPos.dst(x, y);
                long clickDuration = System.currentTimeMillis() - touchDownTime;

                // Simple click check
                if (moveDistance < 5 && clickDuration < 200) {
                    boolean shiftHeld = isShiftDown();
                    if (button == Input.Buttons.LEFT) {
                        handleLeftClick(shiftHeld);
                    } else if (button == Input.Buttons.RIGHT) {
                        handleRightClick();
                    }
                }
                updateSlotBackground();
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                isHovered = true;
                updateSlotBackground();
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                isHovered = false;
                updateSlotBackground();
            }

            private void updateSlotBackground() {
                if (isHovered) {
                    setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("slot_selected")));
                } else {
                    setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("slot_normal")));
                }
            }
        });
    }

    private boolean isShiftDown() {
        return Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
    }

    private void handleLeftClick(boolean shiftHeld) {
        InventoryLock.writeLock();
        try {
            Item heldItem = screenInterface.getHeldItemObject();
            ItemData currentSlotItem = getSlotItemData();
            InventorySlotData.SlotType slotType = slotData.getSlotType();

            if (slotType == InventorySlotData.SlotType.CRAFTING_RESULT) {
                handleCraftingResultClick(shiftHeld);
                return;
            }

            if (shiftHeld && currentSlotItem != null) {
                handleShiftClickMove(currentSlotItem);
                return;
            }

            if (currentSlotItem == null && heldItem != null) {
                // Place entire held stack into empty slot
                placeStackIntoEmptySlot(heldItem, slotType, heldItem.getCount());
            } else if (currentSlotItem != null && heldItem == null) {
                pickUpEntireStack(currentSlotItem, slotType);
            } else if (currentSlotItem != null && heldItem != null) {
                if (canStackTogether(currentSlotItem, heldItem)) {
                    mergeStacks(currentSlotItem, heldItem, slotType);
                } else {
                    swapItems(currentSlotItem, heldItem, slotType);
                }
            }
        } catch (Exception e) {
            GameLogger.error("Error in handleLeftClick: " + e.getMessage());
            e.printStackTrace();
        } finally {
            InventoryLock.writeUnlock();
            updateSlot();
            screenInterface.updateHeldItemDisplay();
            if (slotData.getSlotType() == InventorySlotData.SlotType.CHEST) {
                GameContext.get().getGameClient().sendChestUpdate(screenInterface.getChestData());
            }
        }
    }

    private void handleRightClick() {
        InventoryLock.writeLock();
        try {
            Item heldItem = screenInterface.getHeldItemObject();
            ItemData currentSlotItem = getSlotItemData();
            InventorySlotData.SlotType slotType = slotData.getSlotType();

            if (slotType == InventorySlotData.SlotType.CRAFTING_RESULT) {
                pickUpOneCraftedItem();
            } else if (currentSlotItem == null && heldItem != null) {
                // Place one item from held stack into empty slot
                placeStackIntoEmptySlot(heldItem, slotType, 1);
            } else if (currentSlotItem != null && heldItem == null) {
                // Pick up half the stack
                pickUpHalfStack(currentSlotItem, slotType);
            } else if (currentSlotItem != null && heldItem != null && canStackTogether(currentSlotItem, heldItem)) {
                addOneItemToSlot(currentSlotItem, heldItem, slotType);
            }
        } catch (Exception e) {
            GameLogger.error("Error in handleRightClick: " + e.getMessage());
            e.printStackTrace();
        } finally {
            InventoryLock.writeUnlock();
            updateSlot();
            screenInterface.updateHeldItemDisplay();if (slotData.getSlotType() == InventorySlotData.SlotType.CHEST) {
                GameContext.get().getGameClient().sendChestUpdate(screenInterface.getChestData());
            }
        }
    }

    private void pickUpOneCraftedItem() {
        CraftingSystem cs = screenInterface.getCraftingSystem();
        if (cs == null) return;
        ItemData result = cs.getCraftingResult();
        if (result == null) return;
        Item heldItem = screenInterface.getHeldItemObject();
        if (heldItem != null) return; // Hand not empty

        if (cs.craftOneItem()) {
            Item crafted = new Item(result.getItemId());
            crafted.setCount(1);
            crafted.setUuid(UUID.randomUUID());
            crafted.setDurability(result.getDurability());
            crafted.setMaxDurability(result.getMaxDurability());
            screenInterface.setHeldItem(crafted);
            AudioManager.getInstance().playSound(AudioManager.SoundEffect.CRAFT);
        }
    }

    private void handleShiftClickMove(ItemData currentSlotItem) {
        InventorySlotData.SlotType slotType = slotData.getSlotType();
        if (currentSlotItem == null) return;

        if (slotType == InventorySlotData.SlotType.CHEST) {
            // Move from chest to player inventory
            int remainder = fullyTryAddItem(screenInterface.getInventory(), currentSlotItem);
            if (remainder <= 0) {
                // Everything moved to inventory
                slotData.setItemData(null);
            } else {
                // Some items couldn't fit
                currentSlotItem.setCount(remainder);
                slotData.setItemData(currentSlotItem);
            }
        } else if (slotType == InventorySlotData.SlotType.INVENTORY) {
            // Move from inventory to chest
            ItemContainer chest = screenInterface.getChestData();
            if (chest == null) return;

            int remainder = fullyTryAddItem(chest, currentSlotItem);
            if (remainder <= 0) {
                // Everything moved to chest
                slotData.setItemData(null);
            } else {
                // Some items remain
                currentSlotItem.setCount(remainder);
                slotData.setItemData(currentSlotItem);
            }
        } else if (slotType == InventorySlotData.SlotType.CRAFTING_RESULT) {
            // Mass craft all possible items into inventory
            handleMassCraftToInventory();
        }

        // Update UI after shift-click
        updateSlot();
        screenInterface.updateHeldItemDisplay();if (slotData.getSlotType() == InventorySlotData.SlotType.CHEST) {
            GameContext.get().getGameClient().sendChestUpdate(screenInterface.getChestData());
        }
    }


    private void handleMassCraftToInventory() {
        CraftingSystem cs = screenInterface.getCraftingSystem();
        if (cs == null) return;
        ItemData result = cs.getCraftingResult();
        if (result == null) return;

        while (cs.craftOneItem()) {
            ItemData single = result.copy();
            single.setCount(1);
            single.setUuid(UUID.randomUUID());
            int remainder = fullyTryAddItem(screenInterface.getInventory(), single);
            if (remainder > 0) {
                // Can't fit this crafted item entirely, so break
                // Return it to the crafting table or discard depending on logic
                break;
            }
        }
    }


    private void moveAllToInventory(ItemData itemData) {
        if (itemData == null) return;
        int remaining = addItemToContainer(screenInterface.getInventory(), itemData);
        if (remaining <= 0) {
            slotData.setItemData(null);
        } else {
            itemData.setCount(remaining);
            slotData.setItemData(itemData);
        }
    }


    private int addItemToContainer(ItemContainer container, ItemData itemData) {
        if (container instanceof Inventory) {
            Inventory inv = (Inventory) container;
            int originalCount = itemData.getCount();
            ItemData copy = itemData.copy();
            if (inv.addItem(copy)) {
                return 0;
            } else {
                return originalCount;
            }
        }
        return itemData.getCount();
    }

    private boolean canStackTogether(ItemData slotItem, Item heldItem) {
        if (!slotItem.getItemId().equals(heldItem.getName())) return false;
        Item template = ItemManager.getItemTemplate(heldItem.getName());
        return template != null && template.isStackable();
    }

    private void placeStackIntoEmptySlot(Item heldItem, InventorySlotData.SlotType slotType, int amount) {
        if (heldItem == null || heldItem.getCount() < amount) {
            return;
        }

        ItemData currentItem = slotData.getItemData();
        if (currentItem != null && currentItem.getCount() > 0) {
            return;
        }

        ItemData newItem = new ItemData(heldItem.getName(), amount, UUID.randomUUID());
        newItem.setDurability(heldItem.getDurability());
        newItem.setMaxDurability(heldItem.getMaxDurability());

        slotData.setItemData(newItem);

        int newCount = heldItem.getCount() - amount;
        if (newCount <= 0) {
            screenInterface.setHeldItem(null);
        } else {
            heldItem.setCount(newCount);
            screenInterface.setHeldItem(heldItem);
        }

        AudioManager.getInstance().playSound(AudioManager.SoundEffect.ITEM_PICKUP);
    }

    private void pickUpEntireStack(ItemData currentSlotItem, InventorySlotData.SlotType slotType) {
        if (currentSlotItem == null) return;
        slotData.setItemData(null);

        Item newHeld = new Item(currentSlotItem.getItemId());
        newHeld.setCount(currentSlotItem.getCount());
        newHeld.setDurability(currentSlotItem.getDurability());
        newHeld.setMaxDurability(currentSlotItem.getMaxDurability());
        newHeld.setUuid(UUID.randomUUID());

        screenInterface.setHeldItem(newHeld);
        AudioManager.getInstance().playSound(AudioManager.SoundEffect.ITEM_PICKUP);
    }

    private int tryMergeItemData(ItemContainer container, ItemData itemToMove) {
        int remainder = itemToMove.getCount();

        for (int i = 0; i < container.getSize(); i++) {
            InventorySlotData slotData = container.getSlotData(i);
            ItemData slotItem = slotData.getItemData();
            if (slotItem != null
                && slotItem.getItemId().equals(itemToMove.getItemId())
                && slotItem.getCount() < Item.MAX_STACK_SIZE) {

                int space = Item.MAX_STACK_SIZE - slotItem.getCount();
                int toMove = Math.min(remainder, space);
                slotItem.setCount(slotItem.getCount() + toMove);
                slotData.setItemData(slotItem);
                remainder -= toMove;
                if (remainder <= 0) break;
            }
        }

        return remainder;
    }

    private int tryPlaceInEmptySlot(ItemContainer container, ItemData itemToMove) {
        int remainder = itemToMove.getCount();

        for (int i = 0; i < container.getSize(); i++) {
            InventorySlotData slotData = container.getSlotData(i);
            if (slotData.getItemData() == null) {
                // Place as many as we can into this empty slot
                int toPlace = Math.min(remainder, Item.MAX_STACK_SIZE);
                ItemData newData = itemToMove.copy();
                newData.setCount(toPlace);
                slotData.setItemData(newData);
                remainder -= toPlace;
                if (remainder <= 0) break;
            }
        }

        return remainder;
    }

    private int fullyTryAddItem(ItemContainer container, ItemData itemData) {
        // First try merging into existing stacks
        int remainder = tryMergeItemData(container, itemData);

        // If still have remainder, try placing into empty slots
        if (remainder > 0) {
            ItemData remainderData = itemData.copy();
            remainderData.setCount(remainder);
            remainder = tryPlaceInEmptySlot(container, remainderData);
        }
        return remainder;
    }


    private void mergeStacks(ItemData currentSlotItem, Item heldItem, InventorySlotData.SlotType slotType) {
        int maxStack = Item.MAX_STACK_SIZE;
        int total = currentSlotItem.getCount() + heldItem.getCount();
        int newSlotCount = Math.min(total, maxStack);
        int remainder = total - newSlotCount;

        ItemData updated = currentSlotItem.copy();
        updated.setCount(newSlotCount);
        slotData.setItemData(updated);

        if (remainder <= 0) {
            screenInterface.setHeldItem(null);
        } else {
            heldItem.setCount(remainder);
            screenInterface.setHeldItem(heldItem);
        }

        AudioManager.getInstance().playSound(AudioManager.SoundEffect.ITEM_PICKUP);
    }

    private void swapItems(ItemData currentSlotItem, Item heldItem, InventorySlotData.SlotType slotType) {
        ItemData slotNew = new ItemData(heldItem.getName(), heldItem.getCount(), UUID.randomUUID());
        slotNew.setDurability(heldItem.getDurability());
        slotNew.setMaxDurability(heldItem.getMaxDurability());
        slotData.setItemData(slotNew);

        Item newHeld = new Item(currentSlotItem.getItemId());
        newHeld.setCount(currentSlotItem.getCount());
        newHeld.setUuid(UUID.randomUUID());
        newHeld.setDurability(currentSlotItem.getDurability());
        newHeld.setMaxDurability(currentSlotItem.getMaxDurability());
        screenInterface.setHeldItem(newHeld);

        AudioManager.getInstance().playSound(AudioManager.SoundEffect.ITEM_PICKUP);
    }

    private void pickUpHalfStack(ItemData currentSlotItem, InventorySlotData.SlotType slotType) {
        if (currentSlotItem == null) return;
        int half = (currentSlotItem.getCount() + 1) / 2;
        int remain = currentSlotItem.getCount() - half;

        ItemData updated = currentSlotItem.copy();
        updated.setCount(remain <= 0 ? 0 : remain);
        slotData.setItemData(remain <= 0 ? null : updated);

        Item newHeld = new Item(currentSlotItem.getItemId());
        newHeld.setCount(half);
        newHeld.setDurability(currentSlotItem.getDurability());
        newHeld.setMaxDurability(currentSlotItem.getMaxDurability());
        newHeld.setUuid(UUID.randomUUID());
        screenInterface.setHeldItem(newHeld);

        AudioManager.getInstance().playSound(AudioManager.SoundEffect.ITEM_PICKUP);
    }

    private void addOneItemToSlot(ItemData currentSlotItem, Item heldItem, InventorySlotData.SlotType slotType) {
        if (currentSlotItem.getCount() >= Item.MAX_STACK_SIZE) return;

        ItemData updated = currentSlotItem.copy();
        updated.setCount(currentSlotItem.getCount() + 1);
        slotData.setItemData(updated);

        int newHeldCount = heldItem.getCount() - 1;
        if (newHeldCount <= 0) {
            screenInterface.setHeldItem(null);
        } else {
            heldItem.setCount(newHeldCount);
            screenInterface.setHeldItem(heldItem);
        }

        AudioManager.getInstance().playSound(AudioManager.SoundEffect.ITEM_PICKUP);
    }

    private void handleCraftingResultClick(boolean shiftHeld) {
        CraftingSystem cs = screenInterface.getCraftingSystem();
        if (cs == null) return;

        if (shiftHeld) {
            handleMassCraftToInventory();
        } else {
            pickUpOneCraftedItem();
        }
    }

    private ItemData getSlotItemData() {
        InventorySlotData.SlotType type = slotData.getSlotType();
        if (type == InventorySlotData.SlotType.CRAFTING ||
            type == InventorySlotData.SlotType.EXPANDED_CRAFTING) {
            CraftingSystem cs = screenInterface.getCraftingSystem();
            return cs != null ? cs.getItemInGrid(slotData.getSlotIndex()) : null;
        } else if (type == InventorySlotData.SlotType.CRAFTING_RESULT) {
            return null;
        } else {
            return slotData.getItemData();
        }
    }

    private Color getDurabilityColor(float percentage) {
        if (percentage > 0.6f) {
            return new Color(0.2f, 0.8f, 0.2f, 1f);
        } else if (percentage > 0.3f) {
            return new Color(0.8f, 0.8f, 0.2f, 1f);
        } else {
            return new Color(0.8f, 0.2f, 0.2f, 1f);
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);
    }
}
