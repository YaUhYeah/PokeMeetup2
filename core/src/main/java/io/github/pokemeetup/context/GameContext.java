package io.github.pokemeetup.context;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Stage;

import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.screens.ChestScreen;
import io.github.pokemeetup.screens.CraftingTableScreen;
import io.github.pokemeetup.screens.GameScreen;
import io.github.pokemeetup.screens.InventoryScreen;
import io.github.pokemeetup.screens.otherui.BuildModeUI;
import io.github.pokemeetup.chat.ChatSystem;
import io.github.pokemeetup.screens.otherui.GameMenu;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.battle.BattleSystemHandler;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldManager;

public final class GameContext {

    private static GameContext instance;

    private final CreatureCaptureGame game;
    private final ChatSystem chatSystem;
    private final BattleSystemHandler battleSystem;
    private GameClient gameClient;
    private World world;
    private Player player;
    private SpriteBatch batch;
    private SpriteBatch uiBatch;
    private Stage uiStage;
    private Stage battleStage;
    private InventoryScreen inventoryScreen;
    private BuildModeUI buildModeUI;
    private ChestScreen chestScreen;
    private CraftingTableScreen craftingScreen;
    private GameMenu gameMenu;
    private WorldManager worldManager;
    private GameScreen gameScreen;

    /**
     * Private constructor to enforce singleton usage.
     */
    private GameContext(
        CreatureCaptureGame game,
        GameClient gameClient,
        World world,
        Player player,
        SpriteBatch batch,
        SpriteBatch uiBatch,
        Stage uiStage,
        Stage battleStage,
        ChatSystem chatSystem,
        BattleSystemHandler battleSystem,
        InventoryScreen inventoryScreen,
        BuildModeUI buildModeUI,
        CraftingTableScreen craftingScreen,
        GameMenu gameMenu,
        ChestScreen chestScreen, WorldManager worldManager,
        GameScreen gameScreen
    ) {
        this.game = game;
        this.gameClient = gameClient;
        this.world = world;
        this.player = player;
        this.batch = batch;
        this.uiBatch = uiBatch;
        this.uiStage = uiStage;
        this.battleStage = battleStage;
        this.chatSystem = chatSystem;
        this.battleSystem = battleSystem;
        this.inventoryScreen = inventoryScreen;
        this.buildModeUI = buildModeUI;
        this.craftingScreen = craftingScreen;
        this.gameMenu = gameMenu;
        this.chestScreen = chestScreen;
        this.worldManager = worldManager;
        this.gameScreen = gameScreen;
    }

    public static void init(
        CreatureCaptureGame game,
        GameClient gameClient,
        World world,
        Player player,
        SpriteBatch batch,
        SpriteBatch uiBatch,
        Stage uiStage,
        Stage battleStage,
        ChatSystem chatSystem,
        BattleSystemHandler battleSystem,
        InventoryScreen inventoryScreen,
        BuildModeUI buildModeUI,
        CraftingTableScreen craftingScreen,
        GameMenu gameMenu,
        ChestScreen chestScreen,WorldManager worldManager,
        GameScreen gameScreen
    ) {
        if (instance != null) {
            throw new IllegalStateException("GameContext already initialized!");
        }
        instance = new GameContext(
            game,
            gameClient,
            world,
            player,
            batch,
            uiBatch,
            uiStage,
            battleStage,
            chatSystem,
            battleSystem,
            inventoryScreen,
            buildModeUI,
            craftingScreen,
            gameMenu,
            chestScreen,worldManager,gameScreen
        );
    }

    /**
     * Returns the singleton instance of the GameContext. Make sure {@link #(...)}
     * has been called first.
     */
    public static GameContext get() {
        if (instance == null) {
            throw new IllegalStateException("GameContext not initialized yet!");
        }
        return instance;
    }

    public CraftingTableScreen getCraftingScreen() {
        return craftingScreen;
    }

    public void setCraftingScreen(CraftingTableScreen craftingScreen) {
        this.craftingScreen = craftingScreen;
    }

    public GameScreen getGameScreen() {
        return gameScreen;
    }

    public void setGameScreen(GameScreen gameScreen) {
        this.gameScreen = gameScreen;
    }

    public GameMenu getGameMenu() {
        return gameMenu;
    }

    public WorldManager getWorldManager() {
        return worldManager;
    }

    public void setWorldManager(WorldManager worldManager) {
        this.worldManager = worldManager;
    }

    public void setGameMenu(GameMenu gameMenu) {
        this.gameMenu = gameMenu;
    }

    public CreatureCaptureGame getGame() {
        return game;
    }

    public GameClient getGameClient() {
        return gameClient;
    }

    public void setGameClient(GameClient gameClient) {
        this.gameClient = gameClient;
    }

    public World getWorld() {
        return world;
    }

    public void setWorld(World world) {
        this.world = world;
    }

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    public SpriteBatch getBatch() {
        return batch;
    }

    public void setBatch(SpriteBatch batch) {
        this.batch = batch;
    }

    public SpriteBatch getUiBatch() {
        return uiBatch;
    }

    public void setUiBatch(SpriteBatch uiBatch) {
        this.uiBatch = uiBatch;
    }

    public Stage getUiStage() {
        return uiStage;
    }

    public void setUiStage(Stage uiStage) {
        this.uiStage = uiStage;
    }

    public Stage getBattleStage() {
        return battleStage;
    }

    public void setBattleStage(Stage battleStage) {
        this.battleStage = battleStage;
    }

    public ChatSystem getChatSystem() {
        return chatSystem;
    }

    public BattleSystemHandler getBattleSystem() {
        return battleSystem;
    }

    public InventoryScreen getInventoryScreen() {
        return inventoryScreen;
    }

    public void setInventoryScreen(InventoryScreen inventoryScreen) {
        this.inventoryScreen = inventoryScreen;
    }

    public BuildModeUI getBuildModeUI() {
        return buildModeUI;
    }

    public void setBuildModeUI(BuildModeUI buildModeUI) {
        this.buildModeUI = buildModeUI;
    }

    public ChestScreen getChestScreen() {
        return chestScreen;
    }

    public void setChestScreen(ChestScreen chestScreen) {
        this.chestScreen = chestScreen;
    }

    public void dispose() {
        // Dispose of SpriteBatches
        if (batch != null) {
            batch.dispose();
        }
        if (uiBatch != null) {
            uiBatch.dispose();
        }
        // Dispose of Stages
        if (uiStage != null) {
            uiStage.dispose();
        }
        if (battleStage != null) {
            battleStage.dispose();
        }
        if (battleSystem != null) {
            battleSystem.endBattle(); // or other cleanup
        }

        instance = null;
    }
}
