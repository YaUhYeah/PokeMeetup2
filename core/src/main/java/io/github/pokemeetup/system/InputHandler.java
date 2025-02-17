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
    private float breakProgress = 0f;
    private float swingTimer = 0f;
    private float lastChopSoundTime = 0f;
    private float lastBreakSoundTime = 0f;

    private boolean hasAxe = false; // do we have a wooden axe?

    // The current target for chopping (e.g. a tree) or breaking
    private WorldObject targetObject = null;
    private PlaceableBlock targetBlock = null;
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
        if (keycode >= Input.Keys.NUM_1 && keycode <= Input.Keys.NUM_9) {
            int slot = keycode - Input.Keys.NUM_1;
            GameContext.get().getPlayer().getHotbarSystem().setSelectedSlot(slot);
            return true;
        }

        if (keycode == Input.Keys.O && !isChopping && !isPunching) {
            ItemData selectedItem = GameContext.get().getPlayer().getHotbarSystem().getSelectedItem();
            if (selectedItem != null) dropItem(selectedItem);
            return true;
        }
        if (keycode == Input.Keys.G) {
            // Only toggle building mode if the player is already in build mode.
            if (GameContext.get().getPlayer().isBuildMode()) {
                // Toggle the building mode inside the BuildModeUI
                GameContext.get().getBuildModeUI().toggleBuildingMode();
                return true;
            }
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

        if (keycode == KeyBinds.getBinding(KeyBinds.MOVE_UP)) {
            moveUp(true);
            return true;
        }
        if (keycode == Input.Keys.P) {
            togglePartyWindow();
            return true;
        }
        if (keycode == KeyBinds.getBinding(KeyBinds.MOVE_DOWN)) {
            moveDown(true);
            return true;
        }

        if (keycode == KeyBinds.getBinding(KeyBinds.MOVE_LEFT)) {
            moveLeft(true);
            return true;
        }

        if (keycode == KeyBinds.getBinding(KeyBinds.MOVE_RIGHT)) {
            moveRight(true);
            return true;
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

    private void togglePartyWindow() {
        Stage uiStage = GameContext.get().getUiStage();
        if (uiStage == null) {
            GameLogger.info("UI stage is null, cannot show party window.");
            return;
        }
        if (partyWindow == null) {
            // Create and show the party window.
            partyWindow = new PokemonPartyWindow(
                GameContext.get().getSkin(),                                   // Skin to use
                GameContext.get().getPlayer().getPokemonParty(),                 // The player's party
                false,                                                           // Not in battle mode
                new PokemonPartyWindow.PartySelectionListener() {                // Selection listener (if needed)
                    @Override
                    public void onPokemonSelected(Pokemon pokemon) {
                        // (In battle mode you might handle selecting a Pokémon)
                    }
                },
                new Runnable() {                                                 // Cancel callback
                    @Override
                    public void run() {
                        closePartyWindow();
                    }
                }
            );
            partyWindow.show(uiStage); // show() centers and fades in the window.
        } else {
            // Already showing? Remove (close) it.
            closePartyWindow();
        }
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
        return keycode == KeyBinds.getBinding(KeyBinds.ACTION); // Action key up is handled elsewhere
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

    public void startChopOrPunch() {
        // Query the current action state from PlayerAnimations.
        PlayerAnimations anim = GameContext.get().getPlayer().getAnimations();
        if (anim.isChopping() || anim.isPunching()) {
            // An action is already in progress—do not restart.
            return;
        }
        // Reset local action flags.
        isChopping = false;
        isPunching = false;
        isBreaking = false;
        chopComplete = false;

        // Check if the player has a wooden axe.
        checkForAxe();

        // Try to find a choppable world object.
        targetObject = findChoppableObject();
        // Also try to find a breakable block.
        PlaceableBlock breakableBlock = findBreakableBlock();

        // If neither a valid world object nor a breakable block is found, cancel the action.
        if (targetObject == null && breakableBlock == null) {
            GameLogger.info("No valid target for chop/punch found. Aborting action.");
            // Ensure animations are not set.
            anim.stopChopping();
            anim.stopPunching();
            return;
        }

        // Prioritize world objects over blocks.
        if (targetObject != null) {
            isChopping = true;
            chopProgress = 0f;
            swingTimer = 0f;
            lastChopSoundTime = 0f;
            if (hasAxe) {
                anim.startChopping();
                AudioManager.getInstance().playSound(AudioManager.SoundEffect.BLOCK_BREAK_WOOD);
            } else {
                isPunching = true;
                anim.startPunching();
                AudioManager.getInstance().playSound(AudioManager.SoundEffect.BLOCK_BREAK_WOOD_HAND);
            }
            boolean isMultiplayer = (GameContext.get().getGameClient() != null
                && GameContext.get().isMultiplayer());
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
            // If no choppable object, try breaking a block.
            PlaceableBlock block = findBreakableBlock();
            if (block != null) {
                startBreaking(block);
            } else {
                isPunching = true;
                anim.startPunching();
                AudioManager.getInstance().playSound(AudioManager.SoundEffect.BLOCK_BREAK_WOOD_HAND);
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

    public void stopChopOrPunch() {
        // Stop all chop/punch/break actions and reset progress.
        isChopping = false;
        isBreaking = false;
        isPunching = false;
        chopProgress = 0f;
        breakProgress = 0f;
        targetObject = null;
        targetBlock = null;
        GameContext.get().getPlayer().getAnimations().stopChopping();
        GameContext.get().getPlayer().getAnimations().stopPunching();
        if (GameContext.get().getGameClient() != null && GameContext.get().isMultiplayer()) {
            NetworkProtocol.PlayerAction action = new NetworkProtocol.PlayerAction();
            action.playerId = GameContext.get().getPlayer().getUsername();
            action.actionType = NetworkProtocol.ActionType.PUNCH_STOP;
            GameContext.get().getGameClient().sendPlayerAction(action);
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
        return obj.getType() == WorldObject.ObjectType.TREE_0 ||
            obj.getType() == WorldObject.ObjectType.TREE_1 ||
            obj.getType() == WorldObject.ObjectType.SNOW_TREE ||
            obj.getType() == WorldObject.ObjectType.HAUNTED_TREE ||
            obj.getType() == WorldObject.ObjectType.RAIN_TREE ||
            obj.getType() == WorldObject.ObjectType.APRICORN_TREE ||
            obj.getType() == WorldObject.ObjectType.RUINS_TREE ||
            obj.getType() == WorldObject.ObjectType.CHERRY_TREE ||
            obj.getType() == WorldObject.ObjectType.BEACH_TREE;
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
        try {
            World world = GameContext.get().getPlayer().getWorld();
            if (world == null) return;
            Vector2 pos = block.getPosition();
            Chunk chunk = world.getChunkAtPosition(pos.x, pos.y);
            if (chunk != null) {
                chunk.removeBlock(pos);
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
                String itemId = block.getType().itemId;
                if (itemId != null) {
                    ItemData blockItem = new ItemData(itemId, 1, UUID.randomUUID());
                    GameContext.get().getPlayer().getInventory().addItem(blockItem);
                    GameLogger.info("Added item to inventory: " + itemId);
                } else {
                    GameLogger.error("No item ID found for block type: " + block.getType().id);
                }
                AudioManager.getInstance().playSound(AudioManager.SoundEffect.BLOCK_BREAK_WOOD);
                if (block.getType() == PlaceableBlock.BlockType.CHEST) {
                    ChestData chestData = block.getChestData();
                    if (chestData != null) {
                        GameContext.get().getWorld().getItemEntityManager()
                            .spawnItemsFromChest(chestData, pos.x * World.TILE_SIZE, pos.y * World.TILE_SIZE);
                        chestData.items.clear();
                    }
                }
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
            World world = GameContext.get().getPlayer().getWorld();
            if (world == null) return;
            WorldObject.WorldObjectManager manager = world.getObjectManager();
            if (manager == null) return;

            // Play the appropriate sound
            if (hasAxe) {
                AudioManager.getInstance().playSound(AudioManager.SoundEffect.BLOCK_BREAK_WOOD);
            } else {
                AudioManager.getInstance().playSound(AudioManager.SoundEffect.BLOCK_BREAK_WOOD_HAND);
            }

            // Compute the chunk in which the object resides.
            Vector2 chunkPos = new Vector2(
                (int) Math.floor(obj.getPixelX() / (World.CHUNK_SIZE * World.TILE_SIZE)),
                (int) Math.floor(obj.getPixelY() / (World.CHUNK_SIZE * World.TILE_SIZE))
            );

            // If we’re in multiplayer, send the removal update.
            if (GameContext.get().getGameClient() != null && GameContext.get().isMultiplayer()) {
                NetworkProtocol.WorldObjectUpdate update = new NetworkProtocol.WorldObjectUpdate();
                update.objectId = obj.getId();
                update.type = NetworkProtocol.NetworkObjectUpdateType.REMOVE;
                update.data = obj.getSerializableData();  // This now contains the canonical coordinates.
                GameContext.get().getGameClient().sendWorldObjectUpdate(update);
            }

            // IMPORTANT: Remove using the canonical coordinates!
            int removalTileX = obj.getTileX();
            int removalTileY = obj.getTileY();
            // For tree objects, our serialization subtracts 1 from tileX.
            if (obj.getType() == WorldObject.ObjectType.TREE_0 ||
                obj.getType() == WorldObject.ObjectType.TREE_1 ||
                obj.getType() == WorldObject.ObjectType.SNOW_TREE ||
                obj.getType() == WorldObject.ObjectType.HAUNTED_TREE ||
                obj.getType() == WorldObject.ObjectType.RUINS_TREE ||
                obj.getType() == WorldObject.ObjectType.RAIN_TREE ||
                obj.getType() == WorldObject.ObjectType.CHERRY_TREE || obj.getType() == WorldObject.ObjectType.BEACH_TREE) {
                removalTileX = removalTileX - 1;
            }
            manager.removeObjectFromChunk(chunkPos, obj.getId(), removalTileX, removalTileY);

            // Award resources (e.g. wood planks)
            int planks = hasAxe ? 4 : 1;
            ItemData woodPlanks = new ItemData("wooden_planks", planks, UUID.randomUUID());
            GameContext.get().getPlayer().getInventory().addItem(woodPlanks);

            // Save the chunk if available.
            Chunk chunk = world.getChunkAtPosition(chunkPos.x * Chunk.CHUNK_SIZE, chunkPos.y * Chunk.CHUNK_SIZE);
            if (chunk != null) {
                world.saveChunkData(chunkPos, chunk);
            }
        } catch (Exception e) {
            GameLogger.error("Failed to destroy object: " + e.getMessage());
            stopChopOrPunch();
        }
    }

    /************************************************************************
     *  Update method called each frame
     ************************************************************************/
    public void update(float deltaTime) {
        // Update any chopping/punching progress first.
        if (isChopping && targetObject != null) {
            updateChopping(deltaTime);
        }
        if (isBreaking && targetBlock != null) {
            updateBreaking(deltaTime);
        }

        // Process movement only if in a valid state.
        if (inputManager.getCurrentState() == InputManager.UIState.NORMAL ||
            inputManager.getCurrentState() == InputManager.UIState.BUILD_MODE) {

            Player player = GameContext.get().getPlayer();
            // If the player is not already moving, start a move based on key input.
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
                // If the player is moving and a key is still held, buffer that direction.
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
