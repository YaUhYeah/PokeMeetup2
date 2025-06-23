// Fixed: src/main/java/io/github/pokemeetup/system/InputHandler.java

package io.github.pokemeetup.system;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.screens.GameScreen;
import io.github.pokemeetup.screens.otherui.BuildModeUI;
import io.github.pokemeetup.screens.otherui.PokemonPartyWindow;
import io.github.pokemeetup.system.battle.BattleInitiationHandler;
import io.github.pokemeetup.system.data.ChestData;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.PlayerAnimations;
import io.github.pokemeetup.system.gameplay.inventory.ChestInteractionHandler;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.system.keybinds.KeyBinds;
import io.github.pokemeetup.utils.GameLogger;

import java.util.List;
import java.util.UUID;

public class InputHandler extends InputAdapter {

    // Constants for chopping / swinging
    private static final float SWING_INTERVAL = 0.5f;
    private static final int DURABILITY_LOSS_PER_SWING = 1;
    private static final float TREE_CHOP_WITH_AXE_TIME = 2.0f;
    private static final float TREE_CHOP_WITHOUT_AXE_TIME = 5.0f;
    private static final float CHOP_SOUND_INTERVAL_WITH_AXE = 0.6f;
    private static final float CHOP_SOUND_INTERVAL_WITHOUT_AXE = 0.6f;

    // References to other systems
    private boolean isChoppingOrBreaking = false;
    private float breakProgress = 0f;
    private float lastSwingSoundTime = 0f;
    private WorldObject targetObject = null;
    private PlaceableBlock targetBlock = null;
    private boolean hasAxe = false;
    private final PickupActionHandler pickupHandler;
    private final BattleInitiationHandler battleInitiationHandler;
    private final GameScreen gameScreen;
    private final ChestInteractionHandler chestHandler;
    private final InputManager inputManager;

    // Movement key flags
    private boolean upPressed, downPressed, leftPressed, rightPressed;

    // Chopping / punching / breaking flags and progress
    private boolean isChopping = false;    // true while a chop action is underway
    private boolean isPunching = false;    // true if punching (when no axe)
    private boolean isBreaking = false;    // for block breaking
    // New flag to mark that the chopping action has reached the required progress
    private boolean chopComplete = false;

    private float chopProgress = 0f;
    private float swingTimer = 0f;
    private float lastChopSoundTime = 0f;
    private float lastBreakSoundTime = 0f;


    // The current target for chopping (e.g. a tree) or breaking
    private PokemonPartyWindow partyWindow;

    public InputHandler(
        PickupActionHandler pickupHandler,
        BattleInitiationHandler battleInitiationHandler,
        GameScreen gameScreen,
        ChestInteractionHandler chestHandler,
        InputManager inputManager
    ) {
        this.pickupHandler = pickupHandler;
        this.battleInitiationHandler = battleInitiationHandler;
        this.gameScreen = gameScreen;
        this.chestHandler = chestHandler;
        this.inputManager = inputManager;
    }

    /************************************************************************
     *  Interaction / chest / crafting / build logic
     ************************************************************************/
    public void handleInteraction() {
        GameLogger.info("handleInteraction() called");
        if (chestHandler.isChestOpen()) {
            GameLogger.info("Chest is already open");
            return;
        }
        if (chestHandler.canInteractWithChest()) {
            GameLogger.info("Interacting with chest");
            handleChestInteraction();
            return;
        }
        if (GameContext.get().getPlayer().isBuildMode()) {
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

    void handleBlockPlacement() {
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
    // Replace the keyDown method in InputHandler with this updated version:
    @Override
    public boolean keyDown(int keycode) {
        InputManager.UIState currentState = inputManager.getCurrentState();

        if (keycode == KeyBinds.getBinding(KeyBinds.MOVE_UP) ||
            keycode == KeyBinds.getBinding(KeyBinds.MOVE_DOWN) ||
            keycode == KeyBinds.getBinding(KeyBinds.MOVE_LEFT) ||
            keycode == KeyBinds.getBinding(KeyBinds.MOVE_RIGHT)) {
            GameContext.get().getPlayer().setInputHeld(true);
        }
        // Hotbar selection via number keys
        if (currentState == InputManager.UIState.NORMAL && keycode >= Input.Keys.NUM_1 && keycode <= Input.Keys.NUM_9) {
            int slot = keycode - Input.Keys.NUM_1;
            GameContext.get().getPlayer().getHotbarSystem().setSelectedSlot(slot);
            return true;
        }

        if (keycode == Input.Keys.O && !isChopping && !isPunching) {
            ItemData selectedItem = GameContext.get().getPlayer().getHotbarSystem().getSelectedItem();
            if (selectedItem != null) dropItem(selectedItem);
            return true;
        }
        if (currentState == InputManager.UIState.BUILD_MODE && keycode == Input.Keys.G) {
            GameContext.get().getBuildModeUI().toggleBuildingMode();
            return true;
        }

        // In build mode, numbers select from the active hotbar
        if (currentState == InputManager.UIState.BUILD_MODE && keycode >= Input.Keys.NUM_1 && keycode <= Input.Keys.NUM_9) {
            int slot = keycode - Input.Keys.NUM_1;
            BuildModeUI buildUI = GameContext.get().getBuildModeUI();
            if (buildUI.isInBuildingMode()) {
                buildUI.buildingHotbar.selectSlot(slot);
            } else {
                buildUI.selectSlot(slot);
            }
            return true;
        }

        // In build mode, handle block flipping with R
        if (currentState == InputManager.UIState.BUILD_MODE) {
            if (keycode == Input.Keys.R) {
                handleBlockFlip();
                return true;
            }
        }

        // Only process input in NORMAL or BUILD_MODE
        if (currentState != InputManager.UIState.NORMAL &&
            currentState != InputManager.UIState.BUILD_MODE) {
            return false;
        }

        // Handle customizable inputs
        if (keycode == KeyBinds.getBinding(KeyBinds.INTERACT)) {
            handleInteraction();
            return true;
        }

        if (keycode == KeyBinds.getBinding(KeyBinds.BUILD_MODE)) {
            toggleBuildMode();
            return true;
        }

        if (!isChopping && !isPunching) {
            if (keycode == KeyBinds.getBinding(KeyBinds.MOVE_UP)) {
                moveUp(true);
                GameContext.get().getPlayer().setInputHeld(true);
                return true;
            }
            if (keycode == KeyBinds.getBinding(KeyBinds.MOVE_DOWN)) {
                moveDown(true);
                GameContext.get().getPlayer().setInputHeld(true);
                return true;
            }
            if (keycode == KeyBinds.getBinding(KeyBinds.MOVE_LEFT)) {
                moveLeft(true);
                GameContext.get().getPlayer().setInputHeld(true);
                return true;
            }
            if (keycode == KeyBinds.getBinding(KeyBinds.MOVE_RIGHT)) {
                moveRight(true);
                GameContext.get().getPlayer().setInputHeld(true);
                return true;
            }
        }

        if (keycode == KeyBinds.getBinding(KeyBinds.SPRINT)) {
            GameContext.get().getPlayer().setRunning(true);
            return true;
        }

        if (keycode == KeyBinds.getBinding(KeyBinds.ACTION)) {
            startChopOrPunch();
            return true;
        }

        if (currentState == InputManager.UIState.BUILD_MODE &&
            keycode >= Input.Keys.NUM_1 && keycode <= Input.Keys.NUM_9) {
            int slot = keycode - Input.Keys.NUM_1;
            GameContext.get().getBuildModeUI().selectSlot(slot);
            GameLogger.info("Hotbar Slot Selected: " + slot);
            return true;
        }

        return false;
    }


    private void closePartyWindow() {
        if (partyWindow != null) {
            partyWindow.remove(); // Remove the actor from its parent stage.
            partyWindow = null;
        }
    }



    // Similarly update keyUp to use KeyBinds:
    @Override
    public boolean keyUp(int keycode) {
        if (keycode == KeyBinds.getBinding(KeyBinds.MOVE_UP) ||
            keycode == KeyBinds.getBinding(KeyBinds.MOVE_DOWN) ||
            keycode == KeyBinds.getBinding(KeyBinds.MOVE_LEFT) ||
            keycode == KeyBinds.getBinding(KeyBinds.MOVE_RIGHT)) {
            GameContext.get().getPlayer().setInputHeld(false);
            GameContext.get().getPlayer().clearBufferedDirection();
        }
        InputManager.UIState currentState = inputManager.getCurrentState();
        if (currentState != InputManager.UIState.NORMAL &&
            currentState != InputManager.UIState.BUILD_MODE) {
            return false;
        }

        if (keycode == KeyBinds.getBinding(KeyBinds.MOVE_UP)) {
            moveUp(false);
            return true;
        }
        if (keycode == KeyBinds.getBinding(KeyBinds.MOVE_DOWN)) {
            moveDown(false);

            return true;
        }
        if (keycode == KeyBinds.getBinding(KeyBinds.MOVE_LEFT)) {
            moveLeft(false);
            return true;
        }
        if (keycode == KeyBinds.getBinding(KeyBinds.MOVE_RIGHT)) {
            moveRight(false);
            return true;
        }
        if (keycode == KeyBinds.getBinding(KeyBinds.SPRINT)) {
            GameContext.get().getPlayer().setRunning(false);
            return true;
        }
        if (keycode == KeyBinds.getBinding(KeyBinds.ACTION)) {
            // Releasing the key stops the chopping action.
            stopChopOrPunch();
            return true;
        }
        return false;
    }

    private void dropItem(ItemData itemData) {
        if (itemData == null) return;
        Player player = GameContext.get().getPlayer();
        if (player == null) return;
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
        if (player.getInventory().removeItem(itemData)) {
            player.getHotbarSystem().updateHotbar();
            if (GameContext.get().isMultiplayer()) {
                GameContext.get().getGameClient().sendItemDrop(itemData, new Vector2(dropX, dropY));
            } else {
                GameContext.get().getWorld().getItemEntityManager().spawnItemEntity(itemData, dropX, dropY);
            }
            AudioManager.getInstance().playSound(AudioManager.SoundEffect.ITEM_PICKUP_OW);
        }
    }


    public void toggleBuildMode() {
        // Switch the UI state between NORMAL and BUILD_MODE
        if (inputManager.getCurrentState() == InputManager.UIState.BUILD_MODE) {
            inputManager.setUIState(InputManager.UIState.NORMAL);
            GameContext.get().getPlayer().setBuildMode(false);
            if (gameScreen.getHouseToggleButton() != null) {
                gameScreen.getHouseToggleButton().setVisible(false);
            }
        } else {
            inputManager.setUIState(InputManager.UIState.BUILD_MODE);
            GameContext.get().getPlayer().setBuildMode(true);
            if (gameScreen.getHouseToggleButton() != null) {
                gameScreen.getHouseToggleButton().setVisible(true);
            }
        }
    }


    /**
     * Initiates a chop/punch/break action. It identifies a target and sets the state.
     * The actual progress is handled in the update loop.
     */
    public void startChopOrPunch() {
        if (isChoppingOrBreaking) return; // Action already in progress

        // Find a target (prioritize blocks, then objects)
        targetBlock = findBreakableBlock();
        if (targetBlock != null) {
            isChoppingOrBreaking = true;
        } else {
            targetObject = findChoppableObject();
            if (targetObject != null) {
                isChoppingOrBreaking = true;
            }
        }

        if (isChoppingOrBreaking) {
            // A target was found, initialize state
            breakProgress = 0f;
            lastSwingSoundTime = 0f;
            checkForAxe();

            // Start animation locally
            PlayerAnimations anims = GameContext.get().getPlayer().getAnimations();
            if (hasAxe) {
                anims.startChopping();
            } else {
                anims.startPunching();
            }

            // Send network message to start animation for other players
            if (GameContext.get().isMultiplayer()) {
                NetworkProtocol.PlayerAction action = new NetworkProtocol.PlayerAction();
                action.playerId = GameContext.get().getPlayer().getUsername();
                action.actionType = hasAxe ? NetworkProtocol.ActionType.CHOP_START : NetworkProtocol.ActionType.PUNCH_START;

                int targetX = targetBlock != null ? (int) targetBlock.getPosition().x : targetObject.getTileX();
                int targetY = targetBlock != null ? (int) targetBlock.getPosition().y : targetObject.getTileY();
                action.tileX = targetX;
                action.tileY = targetY;
                action.direction = GameContext.get().getPlayer().getDirection();

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


    /**
     * Stops any current chop/punch/break action, resetting all related state.
     */
    public void stopChopOrPunch() {
        if (!isChoppingOrBreaking) return;

        isChoppingOrBreaking = false;
        breakProgress = 0f;
        targetObject = null;
        targetBlock = null;

        PlayerAnimations anims = GameContext.get().getPlayer().getAnimations();
        anims.stopChopping();
        anims.stopPunching();

        if (GameContext.get().isMultiplayer()) {
            NetworkProtocol.PlayerAction action = new NetworkProtocol.PlayerAction();
            action.playerId = GameContext.get().getPlayer().getUsername();
            action.actionType = hasAxe ? NetworkProtocol.ActionType.CHOP_STOP : NetworkProtocol.ActionType.PUNCH_STOP;
            GameContext.get().getGameClient().sendPlayerAction(action);
        }
    }
    /**
     * Handles the logic for incrementing break progress while the action key is held.
     */
    private void updateBreakingProgress(float deltaTime) {
        // [FIXED] The check for isKeyPressed was removed, as it's incorrect for touch input.
        // The chopping state is now managed by the isChoppingOrBreaking flag, which is
        // toggled by touchDown/touchUp and keyDown/keyUp events.

        // Validate that the target is still in range and exists.
        if (targetBlock != null) {
            if (findBreakableBlock() != targetBlock) {
                stopChopOrPunch();
                return;
            }
        } else if (targetObject != null) {
            if (findChoppableObject() != targetObject) {
                stopChopOrPunch();
                return;
            }
        } else {
            stopChopOrPunch();
            return;
        }

        breakProgress += deltaTime;
        lastSwingSoundTime += deltaTime;

        // Determine required time and sound interval based on target and tool.
        float requiredTime;
        float soundInterval = hasAxe ? CHOP_SOUND_INTERVAL_WITH_AXE : CHOP_SOUND_INTERVAL_WITHOUT_AXE;

        if (targetBlock != null) {
            requiredTime = targetBlock.getType().getBreakTime(hasAxe);
        } else {
            requiredTime = targetObject.getType().getBreakTime(hasAxe);
        }

        // Play sound effect at intervals.
        if (lastSwingSoundTime >= soundInterval) {
            AudioManager.getInstance().playSound(hasAxe ? AudioManager.SoundEffect.BLOCK_BREAK_WOOD : AudioManager.SoundEffect.BLOCK_BREAK_WOOD_HAND);
            lastSwingSoundTime = 0f;
        }

        // Check for completion.
        if (breakProgress >= requiredTime) {
            if (GameContext.get().isMultiplayer()) {
                // In multiplayer, tell the server the action is complete.
                NetworkProtocol.PlayerAction action = new NetworkProtocol.PlayerAction();
                action.playerId = GameContext.get().getPlayer().getUsername();
                action.actionType = NetworkProtocol.ActionType.CHOP_COMPLETE;
                if (targetBlock != null) {
                    action.tileX = (int) targetBlock.getPosition().x;
                    action.tileY = (int) targetBlock.getPosition().y;
                } else {
                    action.tileX = targetObject.getTileX();
                    action.tileY = targetObject.getTileY();
                }
                GameContext.get().getGameClient().sendPlayerAction(action);
            } else {
                // In single-player, destroy the object directly.
                if (targetBlock != null) {
                    destroyBlock(targetBlock);
                } else {
                    destroyObject(targetObject);
                }
            }
            // Stop the action on the client side.
            stopChopOrPunch();
        }
    }

    // This method updates the chopping progress. It now waits for an additional delay equal to the full chop animation duration
    // before calling stopChopOrPunch(), ensuring the full 4-frame chop animation is shown.
    private void updateChopping(float deltaTime) {
        if (!isValidTarget(targetObject)) {
            stopChopOrPunch();
            return;
        }
        chopProgress += deltaTime;
        lastChopSoundTime += deltaTime;
        swingTimer += deltaTime;

        // Determine chop time based on whether the player has an axe.
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

        // Once chopProgress reaches the required chop time, mark the chop as complete
        if (chopProgress >= chopTime) {
            if (!chopComplete) {
                GameLogger.info("Tree chopped down! " + (hasAxe ? "(with axe)" : "(without axe)"));
                destroyObject(targetObject);
                chopComplete = true;
            }
            // Wait until an additional duration equal to CHOP_ANIMATION_DURATION has elapsed before stopping.
            if (chopProgress >= (chopTime + PlayerAnimations.CHOP_ANIMATION_DURATION)) {
                stopChopOrPunch();
                chopComplete = false;
            }
        }
    }

    // degrade the axe durability per swing
    private void handleToolDurabilityPerSwing(ItemData axeItem) {
        if (axeItem == null) return;
        axeItem.updateDurability(-DURABILITY_LOSS_PER_SWING);
        GameContext.get().getPlayer().getInventory().notifyObservers();
        if (axeItem.isBroken()) {
            playToolBreakEffect();
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
        // Switch from chop anim to punch anim
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

    private boolean isChoppable(WorldObject obj) {
        return obj.getType().breakTime < 9999f; // Any object with a finite break time
    }

    private WorldObject findChoppableObject() {
        if (GameContext.get().getPlayer() == null ||
            GameContext.get().getPlayer().getWorld() == null) {
            return null;
        }
        float playerCenterX = (GameContext.get().getPlayer().getTileX() + 0.5f) * World.TILE_SIZE;
        float playerCenterY = (GameContext.get().getPlayer().getTileY() + 0.5f) * World.TILE_SIZE;
        String direction = GameContext.get().getPlayer().getDirection();
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
        Rectangle searchArea = new Rectangle(
            interactX - World.TILE_SIZE * 1.5f,
            interactY - World.TILE_SIZE * 1.5f,
            World.TILE_SIZE * 3,
            World.TILE_SIZE * 3
        );
        List<WorldObject> nearby = GameContext.get().getPlayer().getWorld().getObjectManager()
            .getObjectsNearPosition(interactX, interactY);
        WorldObject bestObj = null;
        float bestDist = Float.MAX_VALUE;
        for (WorldObject obj : nearby) {
            if (!isChoppable(obj)) continue;
            Rectangle objBox = obj.getCollisionBox();
            if (objBox == null) continue;
            if (objBox.overlaps(searchArea)) {
                float cx = objBox.x + objBox.width / 2f;
                float cy = objBox.y + objBox.height / 2f;
                float dist = Vector2.dst(interactX, interactY, cx, cy);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestObj = obj;
                }
            }
        }
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
    PlaceableBlock findBreakableBlock() {
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

    void startBreaking(PlaceableBlock block) {
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
            stopChopOrPunch();
        }
    }
    private void destroyBlock(PlaceableBlock block) {
        if (block == null) return;
        World world = GameContext.get().getPlayer().getWorld();
        if (world == null) return;

        Vector2 pos = block.getPosition();
        world.getBlockManager().removeBlock((int) pos.x, (int) pos.y);

        String itemId = block.getType().itemId;
        if (itemId != null) {
            ItemData droppedItem = new ItemData(itemId, 1);
            float dropX = pos.x * World.TILE_SIZE + World.TILE_SIZE / 2f;
            float dropY = pos.y * World.TILE_SIZE + World.TILE_SIZE / 2f;
            world.getItemEntityManager().spawnItemEntity(droppedItem, dropX, dropY);
        }
        AudioManager.getInstance().playSound(AudioManager.SoundEffect.BLOCK_BREAK_WOOD);

        if (block.getType() == PlaceableBlock.BlockType.CHEST && block.getChestData() != null) {
            world.getItemEntityManager().spawnItemsFromChest(block.getChestData(), pos.x * World.TILE_SIZE, pos.y * World.TILE_SIZE);
        }
    }



    private void destroyObject(WorldObject obj) {
        if (obj == null) return;
        World world = GameContext.get().getPlayer().getWorld();
        if (world == null) return;

        world.removeWorldObject(obj);

        String dropItemId = obj.getType().dropItemId;
        int dropCount = obj.getType().dropItemCount;
        if (dropItemId != null && dropCount > 0) {
            ItemData droppedItem = new ItemData(dropItemId, dropCount);
            world.getItemEntityManager().spawnItemEntity(droppedItem, obj.getPixelX() + (obj.getTexture().getRegionWidth() / 2f), obj.getPixelY());
        }

        AudioManager.getInstance().playSound(hasAxe ? AudioManager.SoundEffect.BLOCK_BREAK_WOOD : AudioManager.SoundEffect.BLOCK_BREAK_WOOD_HAND);
    }

    /************************************************************************
     *  Update method called each frame
     ************************************************************************/
    public void update(float deltaTime) {
        // Update any chopping/punching progress first.

        if (isChoppingOrBreaking) {
            updateBreakingProgress(deltaTime);
        }

        // Only allow movement (or direction buffering) if NOT chopping/punching.
        if (!isChopping && !isPunching &&
            (inputManager.getCurrentState() == InputManager.UIState.NORMAL ||
                inputManager.getCurrentState() == InputManager.UIState.BUILD_MODE)) {

            Player player = GameContext.get().getPlayer();
            if (!player.isMoving()) {
                if (upPressed) {
                    player.move("up");
                } else if (downPressed) {
                    player.move("down");
                } else if (leftPressed) {
                    player.move("left");
                } else if (rightPressed) {
                    player.move("right");
                }
            } else {
                if (upPressed) {
                    player.setBufferedDirection("up");
                } else if (downPressed) {
                    player.setBufferedDirection("down");
                } else if (leftPressed) {
                    player.setBufferedDirection("left");
                } else if (rightPressed) {
                    player.setBufferedDirection("right");
                }
            }
        }
        // When chopping/punching, do not update or buffer a new direction.
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
