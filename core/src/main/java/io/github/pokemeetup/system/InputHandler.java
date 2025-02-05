package io.github.pokemeetup.system;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.screens.GameScreen;
import io.github.pokemeetup.screens.otherui.BuildModeUI;
import io.github.pokemeetup.system.battle.BattleInitiationHandler;
import io.github.pokemeetup.system.data.ChestData;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.ChestInteractionHandler;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.utils.GameLogger;

import java.util.List;
import java.util.UUID;

public class InputHandler extends InputAdapter {
    private static final float SWING_INTERVAL = 0.5f;
    private static final int DURABILITY_LOSS_PER_SWING = 1;
    private static final float TREE_CHOP_WITH_AXE_TIME = 2.0f;
    private static final float TREE_CHOP_WITHOUT_AXE_TIME = 5.0f;
    private static final float CHOP_SOUND_INTERVAL_WITH_AXE = 0.6f;
    private static final float CHOP_SOUND_INTERVAL_WITHOUT_AXE = 0.6f;

    private final PickupActionHandler pickupHandler;
    private final BattleInitiationHandler battleInitiationHandler;
    private final GameScreen gameScreen;
    private final ChestInteractionHandler chestHandler;
    private final InputManager inputManager;

    private boolean upPressed, downPressed, leftPressed, rightPressed;

    // Chopping/punching states
    private boolean isChopping = false;         // are we actively chopping a tree?
    private boolean isPunching = false;         // are we actively punching (no axe found)?
    private boolean isBreaking = false;         // are we actively breaking a block?

    private float chopProgress = 0f;
    private float breakProgress = 0f;
    private float swingTimer = 0f;
    private float lastChopSoundTime = 0f;
    private float lastBreakSoundTime = 0f;

    private boolean hasAxe = false;             // do we have a wooden axe?

    private WorldObject targetObject = null;    // the tree / object being chopped
    private PlaceableBlock targetBlock = null;  // the block being broken

    public InputHandler(
        PickupActionHandler pickupHandler,
        BattleInitiationHandler battleInitiationHandler,
        GameScreen gameScreen,
        ChestInteractionHandler handler,
        InputManager uiControlManager
    ) {
        this.pickupHandler = pickupHandler;
        this.inputManager = uiControlManager;
        this.battleInitiationHandler = battleInitiationHandler;
        this.gameScreen = gameScreen;
        this.chestHandler = handler;
    }

    /************************************************************************
     *  Interaction / chest / crafting / build logic
     ************************************************************************/
    public void handleInteraction() {
        GameLogger.info("handleInteraction() called");

        // Check if chest is open
        if (chestHandler.isChestOpen()) {
            GameLogger.info("Chest is already open");
            return;
        }

        // Chest
        if (chestHandler.canInteractWithChest()) {
            GameLogger.info("Interacting with chest");
            handleChestInteraction();
            return;
        }

        // Build mode
        if (GameContext.get().getPlayer().isBuildMode()) {
            GameLogger.info("Player is in build mode, handling block placement");
            handleBlockPlacement();
            return;
        }

        // Crafting table
        if (canInteractWithCraftingTable()) {
            GameLogger.info("Interacting with crafting table");
            handleCraftingTableInteraction();
            return;
        }

        // If none of the above => pick up items or initiate a battle
        GameLogger.info("Handling pickup action");
        pickupHandler.handlePickupAction();

        GameLogger.info("Attempting to initiate battle");
        battleInitiationHandler.handleBattleInitiation();
    }

    private void handleChestInteraction() {
        if (chestHandler.isChestOpen()) {
            GameLogger.info("Chest is already open, not handling interaction");
            return;
        }

        if (chestHandler.canInteractWithChest()) {
            Vector2 chestPos = chestHandler.getCurrentChestPosition();
            PlaceableBlock chestBlock = GameContext.get().getWorld().getBlockManager()
                .getBlockAt((int) chestPos.x, (int) chestPos.y);

            if (chestBlock != null && chestBlock.getType() == PlaceableBlock.BlockType.CHEST) {
                chestBlock.setChestOpen(true);
                ChestData chestData = chestBlock.getChestData();
                chestHandler.setChestOpen(true);
                GameContext.get().getGameScreen().openChestScreen(chestPos, chestData);
                AudioManager.getInstance().playSound(AudioManager.SoundEffect.CHEST_OPEN);
            } else {
                GameLogger.error("No chest block found at position: " + chestPos);
            }
        }
    }

    private void handleBlockPlacement() {
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

        BuildModeUI buildUI = GameContext.get().getBuildModeUI();
        if (buildUI.isInBuildingMode()) {
            if (buildUI.tryPlaceBuilding(targetX, targetY)) {
                AudioManager.getInstance().playSound(AudioManager.SoundEffect.BLOCK_PLACE_0);
                GameLogger.info("Building placed at " + targetX + "," + targetY);
            }
        } else {
            if (buildUI.tryPlaceBlock(targetX, targetY)) {
                AudioManager.getInstance().playSound(AudioManager.SoundEffect.BLOCK_PLACE_0);
                GameLogger.info("Block placed at " + targetX + "," + targetY);
            }
        }
    }

    private boolean canInteractWithCraftingTable() {
        if (GameContext.get().getPlayer() == null || GameContext.get().getPlayer().getWorld() == null) {
            GameLogger.info("Cannot interact: player or world is null");
            return false;
        }
        int targetX = GameContext.get().getPlayer().getTileX();
        int targetY = GameContext.get().getPlayer().getTileY();
        String direction = GameContext.get().getPlayer().getDirection();
        switch (direction) {
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

        PlaceableBlock block = GameContext.get().getPlayer().getWorld().getBlockManager().getBlockAt(targetX, targetY);
        return (block != null && block.getType() == PlaceableBlock.BlockType.CRAFTINGTABLE);
    }

    private void handleCraftingTableInteraction() {
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
        Vector2 craftingTablePosition = new Vector2(targetX, targetY);
        gameScreen.openExpandedCrafting(craftingTablePosition);
    }

    /************************************************************************
     *  Movement key handling
     ************************************************************************/
    public void moveUp(boolean pressed) {
        upPressed = pressed;
    }

    public void moveDown(boolean pressed) {
        downPressed = pressed;
    }

    public void moveLeft(boolean pressed) {
        leftPressed = pressed;
    }

    public void moveRight(boolean pressed) {
        rightPressed = pressed;
    }

    /************************************************************************
     *  KeyDown / KeyUp
     ************************************************************************/
    @Override
    public boolean keyDown(int keycode) {
        InputManager.UIState currentState = inputManager.getCurrentState();

        // Number keys 1-9 for hotbar selection
        if (keycode >= Input.Keys.NUM_1 && keycode <= Input.Keys.NUM_9) {
            int slot = keycode - Input.Keys.NUM_1;
            GameContext.get().getPlayer().getHotbarSystem().setSelectedSlot(slot);
            return true;
        }

        if (keycode == Input.Keys.O && !isChopping && !isPunching) {
            ItemData selectedItem = GameContext.get().getPlayer().getHotbarSystem().getSelectedItem();
            if (selectedItem != null) {
                dropItem(selectedItem);
            }
            return true;
        }
        // If in BUILD_MODE, handle block flipping if R is pressed
        if (currentState == InputManager.UIState.BUILD_MODE) {
            if (keycode == Input.Keys.R) {
                handleBlockFlip();
                return true;
            }
        }

        // Only process input in NORMAL and BUILD_MODE
        if (currentState != InputManager.UIState.NORMAL &&
            currentState != InputManager.UIState.BUILD_MODE) {
            return false;
        }

        // Interaction
        if (keycode == Input.Keys.X) {
            handleInteraction();
            return true;
        }

        // Movement / actions
        switch (keycode) {
            case Input.Keys.G:
                if (GameContext.get().getBuildModeUI() != null) {
                    GameContext.get().getBuildModeUI().toggleBuildingMode();
                }
                return true;

            case Input.Keys.W:
            case Input.Keys.UP:
                moveUp(true);
                return true;

            case Input.Keys.S:
            case Input.Keys.DOWN:
                moveDown(true);
                return true;

            case Input.Keys.A:
            case Input.Keys.LEFT:
                moveLeft(true);
                return true;

            case Input.Keys.D:
            case Input.Keys.RIGHT:
                moveRight(true);
                return true;

            case Input.Keys.Z: // run
                GameContext.get().getPlayer().setRunning(true);
                return true;

            case Input.Keys.Q: // Chop or punch
                startChopOrPunch();
                return true;

            case Input.Keys.B: // build mode
                toggleBuildMode();
                return true;

            default:
                if (currentState == InputManager.UIState.BUILD_MODE &&
                    keycode >= Input.Keys.NUM_1 && keycode <= Input.Keys.NUM_9) {
                    int slot = (keycode - Input.Keys.NUM_1);
                    GameContext.get().getBuildModeUI().selectSlot(slot);
                    GameLogger.info("Hotbar Slot Selected: " + slot);
                    return true;
                }
                return false;
        }
    }

    private void dropItem(ItemData itemData) {
        if (itemData == null) return;

        Player player = GameContext.get().getPlayer();
        if (player == null) return;

        // Calculate drop position in front of player
        float dropX = player.getX();
        float dropY = player.getY();
        switch (player.getDirection()) {
            case "up":
                dropY += 32;
                break;
            case "down":
                dropY -= 32;
                break;
            case "left":
                dropX -= 32;
                break;
            case "right":
                dropX += 32;
                break;
        }

        // Remove item from inventory and spawn entity
        if (player.getInventory().removeItem(itemData)) {
            player.getHotbarSystem().updateHotbar();

            if (GameContext.get().isMultiplayer()) {
                GameContext.get().getGameClient().sendItemDrop(
                    itemData, new Vector2(dropX, dropY)
                );
            } else {
                GameContext.get().getWorld().getItemEntityManager()
                    .spawnItemEntity(itemData, dropX, dropY);
            }

            // Play drop sound
            AudioManager.getInstance().playSound(AudioManager.SoundEffect.ITEM_PICKUP_OW);
        }
    }

    @Override
    public boolean keyUp(int keycode) {
        InputManager.UIState currentState = inputManager.getCurrentState();

        if (currentState != InputManager.UIState.NORMAL &&
            currentState != InputManager.UIState.BUILD_MODE) {
            return false;
        }

        switch (keycode) {
            case Input.Keys.W:
            case Input.Keys.UP:
                moveUp(false);
                return true;
            case Input.Keys.S:
            case Input.Keys.DOWN:
                moveDown(false);
                return true;
            case Input.Keys.A:
            case Input.Keys.LEFT:
                moveLeft(false);
                return true;
            case Input.Keys.D:
            case Input.Keys.RIGHT:
                moveRight(false);
                return true;
            case Input.Keys.Z:
                GameContext.get().getPlayer().setRunning(false);
                GameLogger.info("Running Stopped");
                return true;
            case Input.Keys.Q:
                stopChopOrPunch();
                return true;
        }
        return false;
    }

    public void toggleBuildMode() {
        if (inputManager.getCurrentState() == InputManager.UIState.BUILD_MODE) {
            inputManager.setUIState(InputManager.UIState.NORMAL);
            GameContext.get().getPlayer().setBuildMode(false);
            if (GameContext.get().getGameScreen().getHouseToggleButton() != null) {
                GameContext.get().getGameScreen().getHouseToggleButton().setVisible(false);
            }
        } else {
            inputManager.setUIState(InputManager.UIState.BUILD_MODE);
            GameContext.get().getPlayer().setBuildMode(true);
            if (GameContext.get().getGameScreen().getHouseToggleButton() != null) {
                GameContext.get().getGameScreen().getHouseToggleButton().setVisible(true);
            }
        }
    }

    public void startChopOrPunch() {
        // If already chopping, breaking, or punching, do nothing.
        if (isChopping || isBreaking || isPunching) return;

        checkForAxe(); // sets hasAxe true if a wooden axe is found

        // Try to find a choppable world object (i.e. tree) in front of the player.
        targetObject = findChoppableObject();
        if (targetObject != null) {
            // Begin chopping locally.
            isChopping = true;
            chopProgress = 0f;
            swingTimer = 0f;
            lastChopSoundTime = 0f;
            if (hasAxe) {
                GameContext.get().getPlayer().getAnimations().startChopping();
                AudioManager.getInstance().playSound(AudioManager.SoundEffect.BLOCK_BREAK_WOOD);
            } else {
                GameContext.get().getPlayer().getAnimations().startPunching();
                AudioManager.getInstance().playSound(AudioManager.SoundEffect.BLOCK_BREAK_WOOD_HAND);
            }
            // If we’re in multiplayer, also send a CHOP_START/PUNCH_START network action for immediate feedback.
            boolean isMultiplayer = (GameContext.get().getGameClient() != null
                && !GameContext.get().getGameClient().isSinglePlayer());
            if (isMultiplayer) {
                NetworkProtocol.PlayerAction action = new NetworkProtocol.PlayerAction();
                action.playerId = GameContext.get().getPlayer().getUsername();
                // Calculate target tile based on the player's current tile and direction.
                int targetTileX = GameContext.get().getPlayer().getTileX();
                int targetTileY = GameContext.get().getPlayer().getTileY();
                switch (GameContext.get().getPlayer().getDirection()) {
                    case "up":
                        targetTileY++;
                        break;
                    case "down":
                        targetTileY--;
                        break;
                    case "left":
                        targetTileX--;
                        break;
                    case "right":
                        targetTileX++;
                        break;
                }
                action.tileX = targetTileX;
                action.tileY = targetTileY;
                action.direction = GameContext.get().getPlayer().getDirection();
                // Use CHOP_START if using an axe; otherwise use PUNCH_START.
                action.actionType = hasAxe ? NetworkProtocol.ActionType.CHOP_START : NetworkProtocol.ActionType.PUNCH_START;
                GameLogger.info("Multiplayer: Sending " + action.actionType + " for target tile: (" + targetTileX +
                    "," + targetTileY + ") direction: " + action.direction);
                GameContext.get().getGameClient().sendPlayerAction(action);
            }
        } else {
            // No choppable world object found. Fallback: try to break a block.
            PlaceableBlock block = findBreakableBlock();
            if (block != null) {
                startBreaking(block);
            } else {
                // Fallback to punching with no object.
                isPunching = true;
                GameContext.get().getPlayer().getAnimations().startPunching();
                AudioManager.getInstance().playSound(AudioManager.SoundEffect.BLOCK_BREAK_WOOD_HAND);
            }
        }
    }

    public void stopChopOrPunch() {
        // If we were chopping or breaking, stop and reset progress.
        if (isChopping || isBreaking) {
            isChopping = false;
            isBreaking = false;
            chopProgress = 0f;
            breakProgress = 0f;
            targetObject = null;
            targetBlock = null;
            GameContext.get().getPlayer().getAnimations().stopChopping();
            GameContext.get().getPlayer().getAnimations().stopPunching();

            // Send CHOP_STOP in multiplayer mode.
            if (GameContext.get().getGameClient() != null && !GameContext.get().getGameClient().isSinglePlayer()) {
                NetworkProtocol.PlayerAction action = new NetworkProtocol.PlayerAction();
                action.playerId = GameContext.get().getPlayer().getUsername();
                action.actionType = NetworkProtocol.ActionType.CHOP_STOP;
                GameContext.get().getGameClient().sendPlayerAction(action);
            }
        } else if (isPunching) {
            isPunching = false;
            GameContext.get().getPlayer().getAnimations().stopPunching();

            // Send PUNCH_STOP in multiplayer.
            if (GameContext.get().getGameClient() != null && !GameContext.get().getGameClient().isSinglePlayer()) {
                NetworkProtocol.PlayerAction action = new NetworkProtocol.PlayerAction();
                action.playerId = GameContext.get().getPlayer().getUsername();
                action.actionType = NetworkProtocol.ActionType.PUNCH_STOP;
                GameContext.get().getGameClient().sendPlayerAction(action);
            }
        }
    }


    /************************************************************************
     *  Block flipping (in build mode)
     ************************************************************************/
    private void handleBlockFlip() {
        if (!GameContext.get().getPlayer().isBuildMode()) {
            GameLogger.info("Not in build mode, can't flip");
            return;
        }

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

        PlaceableBlock block = GameContext.get().getWorld().getBlockManager().getBlockAt(targetX, targetY);
        if (block == null) {
            GameLogger.info("No block found at target position");
            return;
        }
        if (!block.getType().isFlippable) {
            GameLogger.info("Block is not flippable: " + block.getType().id);
            return;
        }

        GameLogger.info("Flipping block " + block.getType().id + " at " + targetX + "," + targetY);
        block.toggleFlip();

        // Save updated block in chunk
        Vector2 chunkPos = new Vector2(
            Math.floorDiv(targetX, World.CHUNK_SIZE),
            Math.floorDiv(targetY, World.CHUNK_SIZE)
        );
        Chunk chunk = GameContext.get().getWorld().getChunks().get(chunkPos);
        if (chunk != null) {
            chunk.getBlocks().put(new Vector2(targetX, targetY), block);
            GameContext.get().getWorld().saveChunkData(chunkPos, chunk);
            AudioManager.getInstance().playSound(AudioManager.SoundEffect.BLOCK_PLACE_0);
        } else {
            GameLogger.error("Failed to find chunk for saving flipped block");
        }
    }

    /************************************************************************
     *  Update method called each frame
     ************************************************************************/
    public void update(float deltaTime) {
        // If we are actively chopping, punching, or breaking a block => handle progress
        if (isChopping && targetObject != null) {
            updateChopping(deltaTime);
        }
        if (isBreaking && targetBlock != null) {
            updateBreaking(deltaTime);
        }
        // (If isPunching with no block or object, we do not have a “progress.”
        //  So punching is purely an animation until user releases Q.)

        // Movement
        if (inputManager.getCurrentState() == InputManager.UIState.NORMAL ||
            inputManager.getCurrentState() == InputManager.UIState.BUILD_MODE) {
            if (upPressed) GameContext.get().getPlayer().move("up");
            if (downPressed) GameContext.get().getPlayer().move("down");
            if (leftPressed) GameContext.get().getPlayer().move("left");
            if (rightPressed) GameContext.get().getPlayer().move("right");
        }
    }

    /************************************************************************
     *  Chopping logic
     ************************************************************************/
    private void updateChopping(float deltaTime) {
        if (!isValidTarget(targetObject)) {
            stopChopOrPunch();
            return;
        }

        chopProgress += deltaTime;
        lastChopSoundTime += deltaTime;
        swingTimer += deltaTime;

        // If we have an actual wooden axe item => faster chop times
        ItemData axeItem = hasAxe ? findAxeInInventory() : null;
        float chopTime = (hasAxe && axeItem != null) ? TREE_CHOP_WITH_AXE_TIME : TREE_CHOP_WITHOUT_AXE_TIME;
        float soundInterval = hasAxe ? CHOP_SOUND_INTERVAL_WITH_AXE : CHOP_SOUND_INTERVAL_WITHOUT_AXE;

        // Play repeated chop/punch sounds
        if (lastChopSoundTime >= soundInterval) {
            if (hasAxe && axeItem != null) {
                AudioManager.getInstance().playSound(AudioManager.SoundEffect.BLOCK_BREAK_WOOD);
                if (swingTimer >= SWING_INTERVAL) {
                    handleToolDurabilityPerSwing(axeItem);
                    swingTimer = 0f;
                }
            } else {
                AudioManager.getInstance().playSound(AudioManager.SoundEffect.BLOCK_BREAK_WOOD_HAND);
            }
            lastChopSoundTime = 0f;
        }

        // If we have chopped long enough => remove object
        if (chopProgress >= chopTime) {
            GameLogger.info("Tree chopped down! " + (hasAxe ? "(with axe)" : "(without axe)"));
            destroyObject(targetObject);
            stopChopOrPunch();
        }
    }

    // degrade the axe by 1 per swing
    private void handleToolDurabilityPerSwing(ItemData axeItem) {
        if (axeItem == null) return;
        axeItem.updateDurability(-DURABILITY_LOSS_PER_SWING);

        GameContext.get().getPlayer().getInventory().notifyObservers();

        if (axeItem.isBroken()) {
            playToolBreakEffect();
            // Remove broken axe
            for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
                ItemData item = GameContext.get().getPlayer().getInventory().getItemAt(i);
                if (item != null && item.getUuid().equals(axeItem.getUuid())) {
                    GameContext.get().getPlayer().getInventory().removeItemAt(i);
                    break;
                }
            }
            hasAxe = false;
            stopChopOrPunch();
            GameLogger.info("Axe broke during use!");
        } else {
            if (axeItem.getDurabilityPercentage() <= 0.1f) {
                AudioManager.getInstance().playSound(AudioManager.SoundEffect.DAMAGE);
            }
        }
    }

    private void playToolBreakEffect() {
        AudioManager.getInstance().playSound(AudioManager.SoundEffect.BLOCK_BREAK_WOOD);
        AudioManager.getInstance().playSound(AudioManager.SoundEffect.TOOL_BREAK);

        // Switch from chop anim to "punch" anim
        GameContext.get().getPlayer().getAnimations().stopChopping();
        GameContext.get().getPlayer().getAnimations().startPunching();
    }

    private boolean isValidTarget(WorldObject obj) {
        if (obj == null || GameContext.get().getPlayer() == null ||
            GameContext.get().getPlayer().getWorld() == null) {
            return false;
        }

        float playerCenterX = GameContext.get().getPlayer().getTileX() * World.TILE_SIZE + (World.TILE_SIZE / 2f);
        float playerCenterY = GameContext.get().getPlayer().getTileY() * World.TILE_SIZE + (World.TILE_SIZE / 2f);
        Rectangle treeBox = obj.getCollisionBox();
        if (treeBox == null) return false;

        float treeCenterX = treeBox.x + treeBox.width / 2f;
        float treeCenterY = treeBox.y + treeBox.height / 2f;
        float distance = Vector2.dst(playerCenterX, playerCenterY, treeCenterX, treeCenterY);

        float maxRange = World.TILE_SIZE * 2.5f;
        Vector2 chunkPos = new Vector2(
            (int) Math.floor(obj.getPixelX() / (World.CHUNK_SIZE * World.TILE_SIZE)),
            (int) Math.floor(obj.getPixelY() / (World.CHUNK_SIZE * World.TILE_SIZE))
        );

        return GameContext.get().getPlayer().getWorld().getChunks().containsKey(chunkPos) && distance <= maxRange;
    }

    // Check if a world object is "choppable" (tree, etc.)
    private boolean isChoppable(WorldObject obj) {
        return obj.getType() == WorldObject.ObjectType.TREE_0 ||
            obj.getType() == WorldObject.ObjectType.TREE_1 ||
            obj.getType() == WorldObject.ObjectType.SNOW_TREE ||
            obj.getType() == WorldObject.ObjectType.HAUNTED_TREE ||
            obj.getType() == WorldObject.ObjectType.RAIN_TREE ||
            obj.getType() == WorldObject.ObjectType.APRICORN_TREE ||
            obj.getType() == WorldObject.ObjectType.RUINS_TREE;
    }

    private WorldObject findChoppableObject() {
        if (GameContext.get().getPlayer() == null ||
            GameContext.get().getPlayer().getWorld() == null) {
            return null;
        }

        float playerCenterX = (GameContext.get().getPlayer().getTileX() + 0.5f) * World.TILE_SIZE;
        float playerCenterY = (GameContext.get().getPlayer().getTileY() + 0.5f) * World.TILE_SIZE;
        String direction = GameContext.get().getPlayer().getDirection();

        // A small offset in front of the player
        float dirOffset = World.TILE_SIZE;
        float interactX = playerCenterX, interactY = playerCenterY;
        switch (direction) {
            case "up":
                interactY += dirOffset;
                break;
            case "down":
                interactY -= dirOffset;
                break;
            case "left":
                interactX -= dirOffset;
                break;
            case "right":
                interactX += dirOffset;
                break;
        }

        // Our search area
        Rectangle searchArea = new Rectangle(
            interactX - World.TILE_SIZE * 1.5f,
            interactY - World.TILE_SIZE * 1.5f,
            World.TILE_SIZE * 3,
            World.TILE_SIZE * 3
        );

        // Grab objects near that point
        List<WorldObject> nearby = GameContext.get().getPlayer().getWorld().getObjectManager()
            .getObjectsNearPosition(interactX, interactY);

        WorldObject bestObj = null;
        float bestDist = Float.MAX_VALUE;

        for (WorldObject obj : nearby) {
            if (!isChoppable(obj)) continue;
            Rectangle objBox = obj.getCollisionBox();
            if (objBox == null) continue;

            if (objBox.overlaps(searchArea)) {
                // measure distance
                float cx = objBox.x + objBox.width / 2f;
                float cy = objBox.y + objBox.height / 2f;
                float dist = Vector2.dst(interactX, interactY, cx, cy);

                // optionally weigh direction
                if (dist < bestDist) {
                    bestDist = dist;
                    bestObj = obj;
                }
            }
        }

        // Only return if we are within 2.5 tiles
        return (bestDist <= World.TILE_SIZE * 2.5f) ? bestObj : null;
    }

    private ItemData findAxeInInventory() {
        Inventory inv = GameContext.get().getPlayer().getInventory();
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            ItemData item = inv.getItemAt(i);
            if (item != null && item.getItemId().equals(ItemManager.ItemIDs.WOODEN_AXE)) {
                return item;
            }
        }
        return null;
    }

    private void checkForAxe() {
        List<ItemData> items = GameContext.get().getPlayer().getInventory().getAllItems();
        hasAxe = items.stream()
            .anyMatch(it -> it != null && ItemManager.ItemIDs.WOODEN_AXE.equals(it.getItemId()));
    }

    /************************************************************************
     *  Block Breaking
     ************************************************************************/
    private PlaceableBlock findBreakableBlock() {
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
        return GameContext.get().getPlayer().getWorld().getBlockManager().getBlockAt(targetX, targetY);
    }

    private void startBreaking(PlaceableBlock block) {
        if (isBreaking) return;

        checkForAxe();
        isBreaking = true;
        targetBlock = block;
        breakProgress = 0f;
        lastBreakSoundTime = 0f;

        if (hasAxe) {
            GameContext.get().getPlayer().getAnimations().startChopping();
            AudioManager.getInstance().playSound(AudioManager.SoundEffect.BLOCK_BREAK_WOOD);
        } else {
            GameContext.get().getPlayer().getAnimations().startPunching();
            AudioManager.getInstance().playSound(AudioManager.SoundEffect.BLOCK_BREAK_WOOD_HAND);
        }
    }

    private void updateBreaking(float deltaTime) {
        if (targetBlock == null) {
            if (targetObject != null) {
                updateChopping(deltaTime);
            }
            return;
        }

        breakProgress += deltaTime;
        lastBreakSoundTime += deltaTime;

        float breakInterval = hasAxe ? CHOP_SOUND_INTERVAL_WITH_AXE : CHOP_SOUND_INTERVAL_WITHOUT_AXE;
        if (lastBreakSoundTime >= breakInterval) {
            if (hasAxe) {
                AudioManager.getInstance().playSound(AudioManager.SoundEffect.BLOCK_BREAK_WOOD);
            } else {
                AudioManager.getInstance().playSound(AudioManager.SoundEffect.BLOCK_BREAK_WOOD_HAND);
            }
            lastBreakSoundTime = 0f;
        }

        float needed = targetBlock.getType().getBreakTime(hasAxe);
        if (breakProgress >= needed) {
            destroyBlock(targetBlock);
            stopChopOrPunch(); // stop break
        }
    }

    private void destroyBlock(PlaceableBlock block) {
        if (block == null) return;

        try {
            World world = GameContext.get().getPlayer().getWorld();
            if (world == null) return;

            Vector2 pos = block.getPosition();
            Chunk chunk = world.getChunkAtPosition(pos.x, pos.y);
            if (chunk != null) {
                chunk.removeBlock(pos);

                // If multiplayer => send block remove
                if (GameContext.get().getGameClient() != null &&
                    !GameContext.get().getGameClient().isSinglePlayer()) {
                    NetworkProtocol.BlockPlacement removal = new NetworkProtocol.BlockPlacement();
                    removal.username = GameContext.get().getPlayer().getUsername();
                    removal.blockTypeId = block.getType().id;
                    removal.tileX = (int) pos.x;
                    removal.tileY = (int) pos.y;
                    removal.action = NetworkProtocol.BlockAction.REMOVE;
                    GameContext.get().getGameClient().sendBlockPlacement(removal);
                }

                // Give item to player
                String itemId = block.getType().itemId;
                if (itemId != null) {
                    ItemData blockItem = new ItemData(
                        itemId, 1, UUID.randomUUID()
                    );
                    GameContext.get().getPlayer().getInventory().addItem(blockItem);
                    GameLogger.info("Added item to inventory: " + itemId);
                } else {
                    GameLogger.error("No item ID found for block type: " + block.getType().id);
                }

                // break sound
                AudioManager.getInstance().playSound(AudioManager.SoundEffect.BLOCK_BREAK_WOOD);

                if (block.getType() == PlaceableBlock.BlockType.CHEST) {
                    ChestData chestData = block.getChestData();
                    if (chestData != null) {
                        GameContext.get().getWorld().getItemEntityManager()
                            .spawnItemsFromChest(chestData,
                                pos.x * World.TILE_SIZE,
                                pos.y * World.TILE_SIZE);
                        chestData.items.clear();

                    }
                }
                // Save chunk
                Vector2 chunkPos = new Vector2(
                    (int) Math.floor(pos.x / World.CHUNK_SIZE),
                    (int) Math.floor(pos.y / World.CHUNK_SIZE)
                );
                world.saveChunkData(chunkPos, chunk);
            }
        } catch (Exception e) {
            GameLogger.error("Failed to destroy block: " + e.getMessage());
        }
    }

    /************************************************************************
     *  Destroying world objects (trees, etc.)
     ************************************************************************/
    private void destroyObject(WorldObject obj) {
        if (obj == null) return;

        try {
            World w = GameContext.get().getPlayer().getWorld();
            if (w == null) return;

            WorldObject.WorldObjectManager manager = w.getObjectManager();
            if (manager == null) return;

            // play break sound
            if (hasAxe) {
                AudioManager.getInstance().playSound(AudioManager.SoundEffect.BLOCK_BREAK_WOOD);
            } else {
                AudioManager.getInstance().playSound(AudioManager.SoundEffect.BLOCK_BREAK_WOOD_HAND);
            }

            Vector2 chunkPos = new Vector2(
                (int) Math.floor(obj.getPixelX() / (World.CHUNK_SIZE * World.TILE_SIZE)),
                (int) Math.floor(obj.getPixelY() / (World.CHUNK_SIZE * World.TILE_SIZE))
            );

            // remove object locally
            manager.removeObjectFromChunk(chunkPos, obj.getId());

            // If multiplayer => broadcast removal
            if (GameContext.get().getGameClient() != null &&
                !GameContext.get().getGameClient().isSinglePlayer()) {
                NetworkProtocol.WorldObjectUpdate update = new NetworkProtocol.WorldObjectUpdate();
                update.objectId = obj.getId();
                update.type = NetworkProtocol.NetworkObjectUpdateType.REMOVE;
                update.data = obj.getSerializableData();
                GameContext.get().getGameClient().sendWorldObjectUpdate(update);
            }

            // Drop items
            int planks = hasAxe ? 4 : 1;
            ItemData woodPlanks = new ItemData("wooden_planks", planks, UUID.randomUUID());
            GameContext.get().getPlayer().getInventory().addItem(woodPlanks);

            // Save chunk
            Chunk chunk = w.getChunkAtPosition(chunkPos.x * Chunk.CHUNK_SIZE, chunkPos.y * Chunk.CHUNK_SIZE);
            if (chunk != null) {
                w.saveChunkData(chunkPos, chunk);
            }
        } catch (Exception e) {
            GameLogger.error("Failed to destroy object: " + e.getMessage());
            stopChopOrPunch();
        }
    }

    /************************************************************************
     *  Utility
     ************************************************************************/
    public void resetMovementFlags() {
        upPressed = downPressed = leftPressed = rightPressed = false;
        if (GameContext.get().getPlayer() != null) {
            GameContext.get().getPlayer().setRunning(false);
        }
    }

    public void setRunning(boolean running) {
        if (GameContext.get().getPlayer() != null) {
            GameContext.get().getPlayer().setRunning(running);
        }
    }
}
