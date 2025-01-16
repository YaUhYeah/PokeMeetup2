package io.github.pokemeetup.system;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.blocks.PlaceableBlock;
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
import java.util.Objects;
import java.util.UUID;

public class InputHandler extends InputAdapter {
    private static final float SWING_INTERVAL = 0.5f;
    private static final int DURABILITY_LOSS_PER_SWING = 1;
    private static final float TREE_CHOP_WITH_AXE_TIME = 2.0f;
    private static final float TREE_CHOP_WITHOUT_AXE_TIME = 5.0f;
    private static final float CHOP_SOUND_INTERVAL_WITH_AXE = 0.6f;
    private static final float CHOP_SOUND_INTERVAL_WITHOUT_AXE = 0.6f;
    private final Player player;
    private final PickupActionHandler pickupHandler;
    private final BattleInitiationHandler battleInitiationHandler;
    private final GameScreen gameScreen;
    private final ChestInteractionHandler chestHandler;
    private final InputManager inputManager;
    private boolean upPressed, downPressed, leftPressed, rightPressed;
    private float chopProgress = 0f;
    private boolean isChopping = false;
    private WorldObject targetObject = null;
    private float lastChopSoundTime = 0f;
    private float swingTimer = 0f;
    private boolean hasAxe = false;
    private boolean isBreaking = false;
    private float breakProgress = 0f;
    private PlaceableBlock targetBlock = null;
    private float lastBreakSoundTime = 0f;

    public InputHandler(Player player, PickupActionHandler pickupHandler, BattleInitiationHandler battleInitiationHandler, GameScreen gameScreen, ChestInteractionHandler handler, InputManager uiControlManager) {
        this.player = player;
        this.pickupHandler = pickupHandler;
        this.inputManager = uiControlManager;
        this.battleInitiationHandler = battleInitiationHandler;
        this.gameScreen = gameScreen;
        this.chestHandler = handler;
    }


    public void handleInteraction() {
        GameLogger.info("handleInteraction() called");

        // Check if chest is already open
        if (chestHandler.isChestOpen()) {
            GameLogger.info("Chest is already open");
            return;
        }

        if (chestHandler.canInteractWithChest(player)) {
            GameLogger.info("Interacting with chest");
            handleChestInteraction();
            return;
        }

        if (player.isBuildMode()) {
            GameLogger.info("Player is in build mode, handling block placement");
            handleBlockPlacement();
            return;
        }

        if (canInteractWithCraftingTable()) {
            GameLogger.info("Interacting with crafting table");
            handleCraftingTableInteraction();
            return;
        }

        GameLogger.info("Handling pickup action");
        pickupHandler.handlePickupAction();

        GameLogger.info("Attempting to initiate battle");
        battleInitiationHandler.handleBattleInitiation();
    }


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

    private void handleCraftingTableInteraction() {
        int targetX = player.getTileX();
        int targetY = player.getTileY();
        switch (player.getDirection()) {
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

    private void handleBlockPlacement() {
        int targetX = player.getTileX();
        int targetY = player.getTileY();

        switch (player.getDirection()) {
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

        BuildModeUI buildUI = gameScreen.getBuildModeUI();
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

    @Override
    public boolean keyDown(int keycode) {
        InputManager.UIState currentState = inputManager.getCurrentState();
        GameLogger.info("InputHandler keyDown: keycode=" + keycode + ", currentState=" + currentState);

        if (currentState == InputManager.UIState.BUILD_MODE) {
            if (keycode == Input.Keys.R) {
                handleBlockFlip();
                return true;
            }
        }
        // Only process input in NORMAL and BUILD_MODE states
        if (currentState != InputManager.UIState.NORMAL && currentState != InputManager.UIState.BUILD_MODE) {
            return false; // Do not handle the input, allow it to propagate
        }


        // Handle interaction
        if (keycode == Input.Keys.X) {
            handleInteraction();
            return true;
        }

        // Handle movement and action keys
        switch (keycode) {// In InputHandler keyDown method
            case Input.Keys.G:
                GameLogger.info("G key pressed - toggling building mode");
                if (gameScreen.getBuildModeUI() != null) {
                    gameScreen.getBuildModeUI().toggleBuildingMode();
                    return true;
                }
                return false;
            case Input.Keys.W:
            case Input.Keys.UP:
                moveUp(true);
                GameLogger.info("Movement Key Pressed: UP");
                return true;

            case Input.Keys.S:
            case Input.Keys.DOWN:
                moveDown(true);
                GameLogger.info("Movement Key Pressed: DOWN");
                return true;

            case Input.Keys.A:
            case Input.Keys.LEFT:
                moveLeft(true);
                GameLogger.info("Movement Key Pressed: LEFT");
                return true;

            case Input.Keys.D:
            case Input.Keys.RIGHT:
                moveRight(true);
                GameLogger.info("Movement Key Pressed: RIGHT");
                return true;

            case Input.Keys.Z: // Running
                player.setRunning(true);
                GameLogger.info("Running Started");
                return true;

            case Input.Keys.Q: // Chopping
                startChopping();
                GameLogger.info("Chopping Started");
                return true;

            case Input.Keys.B: // Build mode
                toggleBuildMode();
                GameLogger.info("Build Mode Toggled");
                return true;

            default:
                // Handle hotbar number keys (1-9) in BUILD_MODE
                if (currentState == InputManager.UIState.BUILD_MODE &&
                    keycode >= Input.Keys.NUM_1 && keycode <= Input.Keys.NUM_9) {
                    int slot = (keycode - Input.Keys.NUM_1);
                    gameScreen.getBuildModeUI().selectSlot(slot);
                    GameLogger.info("Hotbar Slot Selected: " + slot);
                    return true;
                }
                return false;
        }
    }


    private void handleBlockFlip() {
        if (!player.isBuildMode()) {
            GameLogger.info("Not in build mode, can't flip");
            return;
        }

        int targetX = player.getTileX();
        int targetY = player.getTileY();

        switch (player.getDirection()) {
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

        PlaceableBlock block = player.getWorld().getBlockManager().getBlockAt(targetX, targetY);
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

        // Get chunk and update it
        Vector2 chunkPos = new Vector2(
            Math.floorDiv(targetX, World.CHUNK_SIZE),
            Math.floorDiv(targetY, World.CHUNK_SIZE)
        );

        Chunk chunk = player.getWorld().getChunks().get(chunkPos);
        if (chunk != null) {
            // Make sure the block in the chunk is updated
            chunk.getBlocks().put(new Vector2(targetX, targetY), block);

            // Save the chunk data
            player.getWorld().saveChunkData(chunkPos, chunk,
                gameScreen.getGameClient() != null &&
                    !gameScreen.getGameClient().isSinglePlayer());

            AudioManager.getInstance().playSound(AudioManager.SoundEffect.BLOCK_PLACE_0);
            GameLogger.info("Block flip saved successfully");
        } else {
            GameLogger.error("Failed to find chunk for saving flipped block");
        }
    }

    @Override
    public boolean keyUp(int keycode) {
        InputManager.UIState currentState = inputManager.getCurrentState();
        GameLogger.info("InputHandler keyUp: keycode=" + keycode + ", currentState=" + currentState);

        // Only process key releases in NORMAL and BUILD_MODE states
        if (currentState != InputManager.UIState.NORMAL && currentState != InputManager.UIState.BUILD_MODE) {
            return false; // Do not handle the input, allow it to propagate
        }

        // Handle movement and action keys
        switch (keycode) {
            case Input.Keys.W:
            case Input.Keys.UP:
                moveUp(false);
                GameLogger.info("Movement Key Released: UP");
                return true;

            case Input.Keys.S:
            case Input.Keys.DOWN:
                moveDown(false);
                GameLogger.info("Movement Key Released: DOWN");
                return true;

            case Input.Keys.A:
            case Input.Keys.LEFT:
                moveLeft(false);
                GameLogger.info("Movement Key Released: LEFT");
                return true;

            case Input.Keys.D:
            case Input.Keys.RIGHT:
                moveRight(false);
                GameLogger.info("Movement Key Released: RIGHT");
                return true;

            case Input.Keys.Z:
                player.setRunning(false);
                GameLogger.info("Running Stopped");
                return true;

            case Input.Keys.Q:
                stopChopping();
                GameLogger.info("Chopping Stopped");
                return true;

            default:
                return false;
        }
    }

    public void toggleBuildMode() {
        if (inputManager.getCurrentState() == InputManager.UIState.BUILD_MODE) {
            inputManager.setUIState(InputManager.UIState.NORMAL);
            player.setBuildMode(false);
        } else {
            inputManager.setUIState(InputManager.UIState.BUILD_MODE);
            player.setBuildMode(true);
        }
    }


    public void update(float deltaTime) {
        // Update chopping/breaking logic
        if (isChopping && targetObject != null) {
            updateChopping(deltaTime);
        }
        if (isBreaking && targetBlock != null) {
            updateBreaking(deltaTime);
        }

        // Handle continuous movement in NORMAL and BUILD_MODE states
        if (inputManager.getCurrentState() == InputManager.UIState.NORMAL || inputManager.getCurrentState() == InputManager.UIState.BUILD_MODE) {
            if (upPressed) player.move("up");
            if (downPressed) player.move("down");
            if (leftPressed) player.move("left");
            if (rightPressed) player.move("right");
        }
    }


    private boolean canInteractWithCraftingTable() {
        if (player == null || player.getWorld() == null) {
            GameLogger.info("Cannot interact: player or world is null");
            return false;
        }
        int targetX = player.getTileX();
        int targetY = player.getTileY();
        String direction = player.getDirection();
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

        PlaceableBlock block = player.getWorld().getBlockManager().getBlockAt(targetX, targetY);


        return block != null && block.getType() == PlaceableBlock.BlockType.CRAFTINGTABLE;
    }

    private ItemData findAxeInInventory() {
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            ItemData item = player.getInventory().getItemAt(i);
            if (item != null && item.getItemId().equals(ItemManager.ItemIDs.WOODEN_AXE)) {
                return item; // Return the actual ItemData from the slot
            }
        }
        return null;
    }

    private void playToolBreakEffect() {
        // Play break sound
        AudioManager.getInstance().playSound(AudioManager.SoundEffect.BLOCK_BREAK_WOOD);
        AudioManager.getInstance().playSound(AudioManager.SoundEffect.TOOL_BREAK);

        // Switch to hand animation
        player.getAnimations().stopChopping();
        player.getAnimations().startPunching();
    }

    private void handleChestInteraction() {
        if (chestHandler.isChestOpen()) {
            GameLogger.info("Chest is already open, not handling interaction");
            return;
        }

        if (chestHandler.canInteractWithChest(player)) {
            Vector2 chestPos = chestHandler.getCurrentChestPosition();
            PlaceableBlock chestBlock = player.getWorld().getBlockManager()
                .getBlockAt((int) chestPos.x, (int) chestPos.y);

            if (chestBlock != null && chestBlock.getType() == PlaceableBlock.BlockType.CHEST) {
                chestBlock.setChestOpen(true);
                ChestData chestData = chestBlock.getChestData();
                chestHandler.setChestOpen(true);
                gameScreen.openChestScreen(chestPos, chestData);
                AudioManager.getInstance().playSound(AudioManager.SoundEffect.CHEST_OPEN);
            } else {
                GameLogger.error("No chest block found at position: " + chestPos);
            }
        }
    }


    private void updateChopping(float deltaTime) {
        if (!isValidTarget(targetObject)) {
            stopChopping();
            return;
        }

        // Update timers
        chopProgress += deltaTime;
        lastChopSoundTime += deltaTime;
        swingTimer += deltaTime;

        ItemData axeItem = hasAxe ? findAxeInInventory() : null;
        float chopTime = (hasAxe && axeItem != null) ? TREE_CHOP_WITH_AXE_TIME : TREE_CHOP_WITHOUT_AXE_TIME;
        float soundInterval = hasAxe ? CHOP_SOUND_INTERVAL_WITH_AXE : CHOP_SOUND_INTERVAL_WITHOUT_AXE;

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


        if (chopProgress >= chopTime) {
            GameLogger.info("Tree chopped down! " + (hasAxe ? "(with axe)" : "(without axe)"));
            destroyObject(targetObject);
            stopChopping();
        }
    }

    private void handleToolDurabilityPerSwing(ItemData axeItem) {
        if (axeItem == null) return;
        // Update durabilit
        axeItem.updateDurability(-DURABILITY_LOSS_PER_SWING);

        player.getInventory().notifyObservers();

        if (axeItem.isBroken()) {
            playToolBreakEffect();

            for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
                ItemData item = player.getInventory().getItemAt(i);
                if (item != null && item.getUuid().equals(axeItem.getUuid())) {
                    player.getInventory().removeItemAt(i);
                    break;
                }
            }

            hasAxe = false;
            stopChopping();
            GameLogger.info("Axe broke during use!");
        } else {
            if (axeItem.getDurabilityPercentage() <= 0.1f) {
                AudioManager.getInstance().playSound(AudioManager.SoundEffect.DAMAGE);
            }
        }
    }

    private boolean isChoppable(WorldObject obj) {
        return obj.getType() == WorldObject.ObjectType.TREE_0
            || obj.getType() == WorldObject.ObjectType.TREE_1 ||
            obj.getType() == WorldObject.ObjectType.SNOW_TREE ||
            obj.getType() == WorldObject.ObjectType.HAUNTED_TREE ||
            obj.getType() == WorldObject.ObjectType.RAIN_TREE || obj.getType() == WorldObject.ObjectType.APRICORN_TREE ||
            obj.getType() == WorldObject.ObjectType.RUINS_TREE;
    }


    public void startChopping() {
        if (!isChopping && !isBreaking) {

            checkForAxe();
            targetObject = findChoppableObject();
            if (targetObject != null) {
                isChopping = true;
                chopProgress = 0f;
                swingTimer = 0f;
                lastChopSoundTime = 0f;

                if (hasAxe) {
                    player.getAnimations().startChopping();
                    AudioManager.getInstance().playSound(AudioManager.SoundEffect.BLOCK_BREAK_WOOD);
                } else {
                    player.getAnimations().startPunching();
                    AudioManager.getInstance().playSound(AudioManager.SoundEffect.BLOCK_BREAK_WOOD_HAND);
                }
                if (gameScreen.getGameClient() != null && !gameScreen.getGameClient().isSinglePlayer()) {
                    NetworkProtocol.PlayerAction action = new NetworkProtocol.PlayerAction();
                    action.playerId = player.getUsername();
                    action.actionType = NetworkProtocol.ActionType.CHOP_START;
                    action.targetPosition = new Vector2(targetObject.getPixelX(), targetObject.getPixelY());
                    gameScreen.getGameClient().sendPlayerAction(action);
                }
            } else {
                PlaceableBlock block = findBreakableBlock();
                if (block != null) {
                    startBreaking(block);
                } else {
                    // Optionally, play a sound or show a message indicating nothing to chop/break
                    GameLogger.info("Nothing to chop or break in front of the player.");
                }
            }
        }
    }


    public void stopChopping() {
        isChopping = false;
        isBreaking = false;
        chopProgress = 0f;
        breakProgress = 0f;
        targetObject = null;
        targetBlock = null;
        player.getAnimations().stopChopping();
        player.getAnimations().stopPunching();
        if (gameScreen.getGameClient() != null && !gameScreen.getGameClient().isSinglePlayer()) {
            NetworkProtocol.PlayerAction action = new NetworkProtocol.PlayerAction();
            action.playerId = player.getUsername();
            action.actionType = NetworkProtocol.ActionType.CHOP_STOP;
            gameScreen.getGameClient().sendPlayerAction(action);
        }
    }


    private PlaceableBlock findBreakableBlock() {
        int targetX = player.getTileX();
        int targetY = player.getTileY();

        switch (player.getDirection()) {
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

        return player.getWorld().getBlockManager().getBlockAt(targetX, targetY);
    }

    private void startBreaking(PlaceableBlock block) {
        if (!isBreaking) {
            checkForAxe();
            isBreaking = true;
            targetBlock = block;
            breakProgress = 0f;
            lastBreakSoundTime = 0f;

            if (hasAxe) {
                player.getAnimations().startChopping();
                AudioManager.getInstance().playSound(AudioManager.SoundEffect.BLOCK_BREAK_WOOD);
            } else {
                player.getAnimations().startPunching();
                AudioManager.getInstance().playSound(AudioManager.SoundEffect.BLOCK_BREAK_WOOD_HAND);
            }
        }
    }

    private void stopBreaking() {
        isBreaking = false;
        breakProgress = 0f;
        targetBlock = null;
        targetObject = null;
        if (player != null) {
            if (hasAxe) {
                player.getAnimations().stopChopping();
            } else {
                player.getAnimations().stopPunching();
            }
        }
    }

    private void updateBreaking(float deltaTime) {
        if (targetBlock != null) {
            breakProgress += deltaTime;
            lastBreakSoundTime += deltaTime;

            float breakInterval = hasAxe ? CHOP_SOUND_INTERVAL_WITH_AXE : CHOP_SOUND_INTERVAL_WITHOUT_AXE;
            if (lastBreakSoundTime >= breakInterval) {
                AudioManager.getInstance().playSound(hasAxe ?
                    AudioManager.SoundEffect.BLOCK_BREAK_WOOD :
                    AudioManager.SoundEffect.BLOCK_BREAK_WOOD_HAND);
                lastBreakSoundTime = 0f;
            }

            float breakTime = targetBlock.getType().getBreakTime(hasAxe);
            if (breakProgress >= breakTime) {
                destroyBlock(targetBlock);
                stopBreaking();
            }
        } else if (targetObject != null) {
            updateChopping(deltaTime);
        }
    }

    private void destroyBlock(PlaceableBlock block) {
        if (block == null) return;

        try {
            World currentWorld = player.getWorld();
            if (currentWorld != null) {
                Vector2 blockPos = block.getPosition();
                int chunkX = Math.floorDiv((int) blockPos.x, World.CHUNK_SIZE);
                int chunkY = Math.floorDiv((int) blockPos.y, World.CHUNK_SIZE);
                Vector2 chunkPos = new Vector2(chunkX, chunkY);

                // Remove block from chunk
                Chunk chunk = currentWorld.getChunkAtPosition(blockPos.x, blockPos.y);
                if (chunk != null) {
                    chunk.removeBlock(blockPos);
                    if (gameScreen.getGameClient() != null && !gameScreen.getGameClient().isSinglePlayer()) {
                        NetworkProtocol.BlockPlacement removal = new NetworkProtocol.BlockPlacement();
                        removal.username = player.getUsername();
                        removal.blockTypeId = block.getType().id;
                        removal.tileX = (int) blockPos.x;
                        removal.tileY = (int) blockPos.y;
                        removal.action = NetworkProtocol.BlockAction.REMOVE;
                        gameScreen.getGameClient().sendBlockPlacement(removal);
                    }



                    // Add block item to inventory
                    String itemId = block.getType().itemId;
                    if (itemId != null) {
                        ItemData blockItem = new ItemData(
                            itemId,
                            1,
                            UUID.randomUUID()
                        );
                        player.getInventory().addItem(blockItem);
                        GameLogger.info("Added item to inventory: " + itemId);
                    } else {
                        GameLogger.error("No item ID found for block type: " + block.getType().id);
                    }

                    // Play break sound
                    AudioManager.getInstance().playSound(AudioManager.SoundEffect.BLOCK_BREAK_WOOD);

                    // Save chunk
                    currentWorld.saveChunkData(chunkPos, chunk,
                        gameScreen.getGameClient() != null &&
                            !gameScreen.getGameClient().isSinglePlayer());
                }
            }
        } catch (Exception e) {
            GameLogger.error("Failed to destroy block: " + e.getMessage());
        }
    }


    public void setRunning(boolean running) {
        player.setRunning(running);
    }

    private void destroyObject(WorldObject obj) {
        if (obj == null) return;

        try {
            World currentWorld = player.getWorld();
            if (currentWorld != null) {
                WorldObject.WorldObjectManager objManager = currentWorld.getObjectManager();
                if (objManager != null) {
                    // Play break sound
                    if (hasAxe) {
                        AudioManager.getInstance().playSound(AudioManager.SoundEffect.BLOCK_BREAK_WOOD);
                    } else {
                        AudioManager.getInstance().playSound(AudioManager.SoundEffect.BLOCK_BREAK_WOOD_HAND);
                    }

                    // Calculate chunk position
                    Vector2 chunkPos = new Vector2(
                        (int) Math.floor(obj.getPixelX() / (World.CHUNK_SIZE * World.TILE_SIZE)),
                        (int) Math.floor(obj.getPixelY() / (World.CHUNK_SIZE * World.TILE_SIZE))
                    );

                    // Remove object locally
                    objManager.removeObjectFromChunk(chunkPos, obj.getId());

                    // Send network update in multiplayer
                    if (gameScreen.getGameClient() != null && !gameScreen.getGameClient().isSinglePlayer()) {
                        NetworkProtocol.WorldObjectUpdate update = new NetworkProtocol.WorldObjectUpdate();
                        update.objectId = obj.getId();
                        update.type = NetworkProtocol.NetworkObjectUpdateType.REMOVE;
                        update.data = obj.getSerializableData(); // Include any necessary data
                        gameScreen.getGameClient().sendWorldObjectUpdate(update);
                    }

                    // Handle drops
                    int planksToGive = hasAxe ? 4 : 1;
                    ItemData woodenPlanks = new ItemData("wooden_planks",
                        planksToGive,
                        UUID.randomUUID()
                    );
                    player.getInventory().addItem(woodenPlanks);

                    // Save chunk
                    Chunk chunk = currentWorld.getChunkAtPosition(
                        chunkPos.x * Chunk.CHUNK_SIZE,
                        chunkPos.y * Chunk.CHUNK_SIZE
                    );
                    if (chunk != null) {
                        currentWorld.saveChunkData(chunkPos, chunk,
                            gameScreen.getGameClient() != null &&
                                !gameScreen.getGameClient().isSinglePlayer());
                    }
                }
            }
        } catch (Exception e) {
            GameLogger.error("Failed to destroy object: " + e.getMessage());
            stopChopping();
        }
    }


    private void checkForAxe() {
        List<ItemData> inventory = player.getInventory().getAllItems();
        hasAxe = inventory.stream()
            .filter(Objects::nonNull)
            .anyMatch(item -> item.getItemId().equals(ItemManager.ItemIDs.WOODEN_AXE));
    }

    public void resetMovementFlags() {
        upPressed = false;
        downPressed = false;
        leftPressed = false;
        rightPressed = false;
        if (player != null) {
            player.setRunning(false);
        }
    }

    private boolean isValidTarget(WorldObject obj) {
        if (obj == null || player == null || player.getWorld() == null) return false;

        // Get interaction point based on player's direction
        float playerCenterX = player.getTileX() * World.TILE_SIZE + (World.TILE_SIZE / 2f);
        float playerCenterY = player.getTileY() * World.TILE_SIZE + (World.TILE_SIZE / 2f);

        // Get tree's actual collision box
        Rectangle treeBox = obj.getCollisionBox();
        if (treeBox == null) return false;

        // Get tree's center point including its base offset
        float treeCenterX = treeBox.x + (treeBox.width / 2f);
        float treeCenterY = treeBox.y + (treeBox.height / 2f);

        // Calculate distance to tree's center
        float distance = Vector2.dst(playerCenterX, playerCenterY, treeCenterX, treeCenterY);
        float maxRange = World.TILE_SIZE * 2.5f; // Keep reasonable range

        // Check if tree is in a loaded chunk
        Vector2 chunkPos = new Vector2(
            (int) Math.floor(obj.getPixelX() / (World.CHUNK_SIZE * World.TILE_SIZE)),
            (int) Math.floor(obj.getPixelY() / (World.CHUNK_SIZE * World.TILE_SIZE))
        );

        // Check chunk loaded and distance
        return player.getWorld().getChunks().containsKey(chunkPos) && distance <= maxRange;
    }

    private WorldObject findChoppableObject() {
        if (player == null || player.getWorld() == null) return null;

        // Get player's center position and direction
        float playerCenterX = player.getTileX() * World.TILE_SIZE + (World.TILE_SIZE / 2f);
        float playerCenterY = player.getTileY() * World.TILE_SIZE + (World.TILE_SIZE / 2f);
        String direction = player.getDirection();

        // Calculate interaction point based on direction
        float dirOffset = World.TILE_SIZE;
        float interactX = playerCenterX;
        float interactY = playerCenterY;

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

        // Create interaction area
        Rectangle searchArea = new Rectangle(
            interactX - World.TILE_SIZE * 1.5f,
            interactY - World.TILE_SIZE * 1.5f,
            World.TILE_SIZE * 3,
            World.TILE_SIZE * 3
        );

        // Get nearby objects
        List<WorldObject> nearbyObjects = player.getWorld().getObjectManager()
            .getObjectsNearPosition(interactX, interactY);

        WorldObject closestChoppable = null;
        float closestDistance = Float.MAX_VALUE;

        for (WorldObject obj : nearbyObjects) {
            if (!isChoppable(obj)) continue;

            Rectangle objBox = obj.getCollisionBox();
            if (objBox == null) continue;

            // Calculate tree center based on its collision box
            float treeCenterX = objBox.x + (objBox.width / 2f);
            float treeCenterY = objBox.y + (objBox.height / 2f);

            // Check if tree's collision box overlaps our search area
            if (objBox.overlaps(searchArea)) {
                float distance = Vector2.dst(interactX, interactY, treeCenterX, treeCenterY);

                // Apply direction-based preference without affecting actual distance calculation
                switch (direction) {
                    case "left":
                        // For left side, prioritize trees to the left
                        if (treeCenterX < interactX) distance *= 0.8f;
                        break;
                    case "up":
                        // For top side, prioritize trees above
                        if (treeCenterY > interactY) distance *= 0.8f;
                        break;
                    case "right":
                        if (treeCenterX > interactX) distance *= 0.8f;
                        break;
                    case "down":
                        if (treeCenterY < interactY) distance *= 0.8f;
                        break;
                }

                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestChoppable = obj;
                }
            }
        }

        return closestDistance <= World.TILE_SIZE * 2.5f ? closestChoppable : null;
    }
}
