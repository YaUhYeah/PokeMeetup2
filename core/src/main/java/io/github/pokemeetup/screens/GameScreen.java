package io.github.pokemeetup.screens;

import com.badlogic.gdx.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.chat.ChatSystem;
import io.github.pokemeetup.chat.CommandManager;
import io.github.pokemeetup.chat.commands.GiveCommand;
import io.github.pokemeetup.chat.commands.SetWorldSpawnCommand;
import io.github.pokemeetup.chat.commands.SpawnCommand;
import io.github.pokemeetup.chat.commands.TeleportPositionCommand;
import io.github.pokemeetup.managers.BiomeManager;
import io.github.pokemeetup.multiplayer.OtherPlayer;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.multiplayer.server.ServerStorageSystem;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.PokemonParty;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.pokemon.attacks.Move;
import io.github.pokemeetup.screens.otherui.*;
import io.github.pokemeetup.system.*;
import io.github.pokemeetup.system.battle.BattleInitiationHandler;
import io.github.pokemeetup.system.battle.BattleSystemHandler;
import io.github.pokemeetup.system.data.ChestData;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.gameplay.inventory.ChestInteractionHandler;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.system.gameplay.overworld.*;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldManager;
import io.github.pokemeetup.utils.textures.BattleAssets;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.storage.InventoryConverter;
import io.github.pokemeetup.utils.textures.TextureManager;

import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.github.pokemeetup.system.gameplay.overworld.World.INITIAL_LOAD_RADIUS;
import static io.github.pokemeetup.system.gameplay.overworld.World.TILE_SIZE;

public class GameScreen implements Screen, PickupActionHandler, BattleInitiationHandler {
    public void setChestScreen(ChestScreen chestScreen) {
        this.chestScreen = chestScreen;
    }

    private static final float TARGET_VIEWPORT_WIDTH_TILES = 24f;
    private static final float UPDATE_INTERVAL = 0.1f;
    private static final float CAMERA_LERP = 5.0f;
    private static final float BATTLE_UI_FADE_DURATION = 0.5f;
    private static final float BATTLE_SCREEN_WIDTH = 800;
    private static final float BATTLE_SCREEN_HEIGHT = 480;
    private static final float MOVEMENT_REPEAT_DELAY = 0.1f;
    public static boolean SHOW_DEBUG_INFO = false;
    private static float DPAD_BUTTON_SIZE = 145f;
    private final CreatureCaptureGame game;
    private final GameClient gameClient;
    private final ScheduledExecutorService screenInitScheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean initializationComplete = new AtomicBoolean(false);
    private final AtomicBoolean starterSelectionInProgress = new AtomicBoolean(false);
    private final CommandManager commandManager;
    public boolean isMultiplayer;
    Actor aButton;
    Actor zButton;
    Actor xButton;
    Actor yButton;
    Actor startButton;
    Actor selectButton;
    private ChestInteractionHandler chestHandler = new ChestInteractionHandler();
    private float ACTION_BUTTON_SIZE;
    private float DPAD_SIZE;
    private float BUTTON_PADDING;
    private SpriteBatch uiBatch;
    private Vector2 joystickCenter = new Vector2();
    private Vector2 joystickCurrent = new Vector2();
    private int initializationTimer = 0;
    private ServerStorageSystem storageSystem;
    private WorldManager worldManager;
    private SpriteBatch batch;
    private Stage pokemonPartyStage;
    private PokemonPartyUI pokemonPartyUI;
    private ChatSystem chatSystem;
    private Player player;
    private Table partyDisplay;
    private GameMenu gameMenu;
    private float updateTimer = 0;
    private World world;
    private Stage uiStage;
    private Skin uiSkin;
    private BitmapFont font;
    private OrthographicCamera camera;
    private InputHandler inputHandler;
    private String username;
    private InventoryScreen inventoryScreen;
    private Inventory inventory;
    private BuildModeUI buildModeUI;
    private Rectangle inventoryButton;
    private Rectangle menuButton;
    private PokemonSpawnManager spawnManager;
    private ShapeRenderer shapeRenderer;
    private Skin skin;
    private Stage stage;
    private FitViewport cameraViewport;
    private boolean transitioning = false;
    private boolean controlsInitialized = false;
    private boolean waitingForInitialization = true;
    private StarterSelectionTable starterTable;
    private boolean inputBlocked = false;
    private float debugTimer = 0;
    private boolean initialized = false;
    private boolean initializedworld = false;
    private boolean starterSelectionComplete = false;
    private boolean initializationHandled = false;
    private volatile boolean isDisposing = false;
    private BattleTable battleTable;
    private BattleAssets battleAssets;
    private Skin battleSkin;
    private boolean inBattle = false;
    private Stage battleStage;
    private boolean battleInitialized = false;
    private boolean battleUIFading = false;
    private Table androidControlsTable;
    private Table closeButtonTable;
    private Table dpadTable;
    private Rectangle upButton, downButton, leftButton, rightButton;
    private String currentDpadDirection = null;
    // In GameScreen class, update handleBattleInitiation method:
    private float movementTimer = 0f;
    private boolean isHoldingDirection = false;
    private boolean isRunPressed = false;
    private AndroidMovementController movementController;
    private Runnable pendingStarterInit = null;
    private volatile boolean awaitingStarterSelection = false;
    private BattleSystemHandler battleSystem;
    private CraftingTableScreen craftingScreen;
    private boolean buildMode = false;
    private boolean craftingTableOpen = false;
    private boolean commandsEnabled = false;
    private float alpha = 0f;
    private Action currentTransition;
    private InputManager inputManager;
    private ChestScreen chestScreen;
    private float lastChestOpenTime = 0f;
    public GameScreen(CreatureCaptureGame game, String username, GameClient gameClient, World world) {
        this.game = game;
        this.world = world;
        this.username = username;
        this.gameClient = gameClient;
        this.commandManager = new CommandManager();
        registerAllCommands();
        this.isMultiplayer = !gameClient.isSinglePlayer();

        uiStage = new Stage(new ScreenViewport());
        this.battleSystem = new BattleSystemHandler();
        try {
            // Initialize basic UI first
            initializeBasicResources();

            initializeWorldAndPlayer();
            this.inputManager = new InputManager(this);

            // Check if new player needs starter
            if (player != null && player.getPokemonParty().getSize() == 0) {
                GameLogger.info("New player detected - handling starter selection");
                handleNewPlayer();
            } else {
                completeInitialization();
            }
            this.inputManager.updateInputProcessors();

        } catch (Exception e) {
            GameLogger.error("GameScreen initialization failed: " + e.getMessage());
            throw new RuntimeException("Failed to initialize game screen", e);
        }
    }

    public GameScreen(CreatureCaptureGame game, String username, GameClient gameClient, World world, boolean commandsEnabled) {
        this.game = game;
        this.world = world;
        this.username = username;
        this.commandsEnabled = commandsEnabled;
        this.commandManager = new CommandManager();
        registerAllCommands();

        this.gameClient = gameClient;
        this.isMultiplayer = !gameClient.isSinglePlayer();
        uiStage = new Stage(new ScreenViewport());

        this.battleSystem = new BattleSystemHandler();
        try {
            // Initialize basic UI first
            initializeBasicResources();

            initializeWorldAndPlayer();
            this.inputManager = new InputManager(this);

            // Check if new player needs starter
            if (player != null && player.getPokemonParty().getSize() == 0) {
                GameLogger.info("New player detected - handling starter selection");
                handleNewPlayer();
            } else {
                // Complete normal initialization
                completeInitialization();
            }

        } catch (Exception e) {
            GameLogger.error("GameScreen initialization failed: " + e.getMessage());
            throw new RuntimeException("Failed to initialize game screen", e);
        }
    }

    public Skin getSkin() {
        return skin;
    }

    public InputManager getInputManager() {
        return inputManager;
    }

    public CreatureCaptureGame getGame() {
        return game;
    }

    public StarterSelectionTable getStarterTable() {
        return starterTable;
    }

    public InputHandler getInputHandler() {
        return inputHandler;
    }

    public Stage getUiStage() {
        return uiStage;
    }

    public SpriteBatch getUiBatch() {
        return uiBatch;
    }

    public CraftingTableScreen getCraftingScreen() {
        return craftingScreen;
    }

    public void setCraftingScreen(CraftingTableScreen craftingScreen) {
        this.craftingScreen = craftingScreen;
    }

    public BattleTable getBattleTable() {
        return battleTable;
    }

    public GameMenu getGameMenu() {
        return gameMenu;
    }

    public void setGameMenu(GameMenu gameMenu) {
        this.gameMenu = gameMenu;
    }

    public Stage getBattleStage() {
        return battleStage;
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

    public ChestInteractionHandler getChestHandler() {
        return chestHandler;
    }

    public OrthographicCamera getCamera() {
        return camera;
    }

    private void handleNewPlayer() {
        starterSelectionInProgress.set(true);
        Gdx.app.postRunnable(() -> {
            try {
                starterTable = new StarterSelectionTable(skin);
                starterTable.setSelectionListener(new StarterSelectionTable.SelectionListener() {
                    @Override
                    public void onStarterSelected(Pokemon starter) {
                        handleStarterSelection(starter);
                    }

                    @Override
                    public void onSelectionStart() {
                        // Input is now going to the starter table
                        inputBlocked = true;
                    }
                });

                starterTable.setFillParent(true);
                starterTable.setTouchable(Touchable.enabled); // Ensure table is interactable
                uiStage.addActor(starterTable);

                // Give UI stage input focus
                Gdx.input.setInputProcessor(uiStage);
                uiStage.setKeyboardFocus(starterTable);

                // Update input manager state
                inputManager.setUIState(InputManager.UIState.STARTER_SELECTION);
                inputManager.updateInputProcessors();

                GameLogger.info("Starter selection UI initialized and input set to uiStage.");

            } catch (Exception e) {
                GameLogger.error("Error creating starter selection UI: " + e.getMessage() + e);
            }
        });
    }



    public void openChestScreen(Vector2 chestPosition, ChestData chestData) {
        // Initialize chest screen as needed
        if (chestScreen == null) {
            chestScreen = new ChestScreen(player, skin, chestData, chestPosition, this);
        }
        chestScreen.show();
        inputManager.setUIState(InputManager.UIState.CHEST_SCREEN);
    }

    public void closeChestScreen() {
        if (chestScreen != null) {
            chestScreen.hide();
        }
        inputManager.setUIState(InputManager.UIState.NORMAL);
    }

    // In GameScreen.java
    public void openExpandedCrafting(Vector2 craftingTablePosition) {
        if (craftingScreen == null) {
            craftingScreen = new CraftingTableScreen(
                player,
                skin,
                world,
                gameClient, this, inputManager
            );
        }
        craftingScreen.updatePosition(craftingTablePosition);
        inputManager.setUIState(InputManager.UIState.CRAFTING);
    }

    public void closeExpandedCrafting() {
        inputManager.setUIState(InputManager.UIState.NORMAL);
        if (craftingScreen != null) {
            craftingScreen.hide();
        }
    }


    private void handleMultiplayerInitialization(boolean success) {
        if (success) {
            try {
                initializeWorldAndPlayer();

                if (player != null && player.getPokemonParty().getSize() == 0) {
                    GameLogger.info("New player detected - handling starter selection");
                    awaitingStarterSelection = true;
                    starterSelectionInProgress.set(true);
                    initializationComplete.set(false);
                    initialized = false;

                    Gdx.app.postRunnable(() -> {
                        try {
                            if (starterTable != null) {
                                starterTable.remove();
                            }

                            GameLogger.info("Creating StarterSelectionTable");
                            starterTable = new StarterSelectionTable(skin);
                            starterTable.setSelectionListener(new StarterSelectionTable.SelectionListener() {
                                @Override
                                public void onStarterSelected(Pokemon starter) {
                                    handleStarterSelection(starter);
                                }

                                @Override
                                public void onSelectionStart() {
                                    inputBlocked = true;
                                }
                            });

                            starterTable.setFillParent(true);
                            stage.addActor(starterTable);
                            starterTable.toFront();
                            InputMultiplexer multiplexer = new InputMultiplexer();
                            multiplexer.addProcessor(stage);

                            GameLogger.info("Starter selection UI initialized");

                        } catch (Exception e) {
                            GameLogger.error("Failed to create starter selection: " + e.getMessage());
                            handleInitializationFailure();
                        }
                    });

                    return;
                }
                completeInitialization();

            } catch (Exception e) {
                GameLogger.error("Failed to initialize multiplayer: " + e.getMessage());
                handleInitializationFailure();
            }
        } else {
            handleInitializationFailure();
        }
    }
    @Override
    public void show() {
        if (player != null) {
            player.initializeResources();
            GameLogger.info("Reinitialized player resources on screen show");
        }
        initializeBuildMode();

        if (pendingStarterInit != null) {
            Gdx.app.postRunnable(pendingStarterInit);
            pendingStarterInit = null;
            return;
        }

        inputManager.updateInputProcessors(); // Ensure InputManager sets up InputProcessors

        if (world != null && player != null) {
            Vector2 playerPos = new Vector2(player.getTileX(), player.getTileY());
            world.requestInitialChunks(playerPos);
            GameLogger.info("Requested initial chunks around player at: " + playerPos);
        }       uiStage.setKeyboardFocus(null);
        GameLogger.info("Keyboard focus set to null to prevent input blocking");
    }
    private void handleClientInitialization(boolean success) {
        if (success) {
            Gdx.app.postRunnable(() -> {
                try {
                    initializeWorld();

                    if (player != null) {
                        if (player.getPokemonParty() == null) {
                            player.setPokemonParty(new PokemonParty());
                        }

                        // Initialize player data if needed
                        if (player.getPlayerData() == null) {
                            PlayerData newData = new PlayerData(player.getUsername());
                            player.updateFromPlayerData(newData);
                        }

                        player.initializeResources();
                    }

                    // Check for starter Pokemon requirement
                    if (player != null && player.getPokemonParty().getSize() == 0) {
                        GameLogger.info("New player detected - initiating starter selection");
                        awaitingStarterSelection = true;
                        initiateStarterSelection();
                        return;
                    }

                    completeInitialization();

                    GameLogger.info("Client initialization complete");

                } catch (Exception e) {
                    GameLogger.error("CRITICAL - Error during initialization: " + e.getMessage());
                    handleInitializationFailure();
                }
            });
        } else {
            handleInitializationFailure();
        }
    }

    private void registerAllCommands() {
        GameLogger.info("Registering commands...");
        commandManager.registerCommand(new GiveCommand());
        commandManager.registerCommand(new SpawnCommand());
        commandManager.registerCommand(new SetWorldSpawnCommand());
        commandManager.registerCommand(new TeleportPositionCommand());
    }

    private void completeInitialization() {
        try {
            initializeGameSystems();
            inputManager.updateInputProcessors();

            initializationComplete.set(true);
            GameLogger.info("Game initialization complete");

        } catch (Exception e) {
            GameLogger.error("Failed to complete initialization: " + e.getMessage());
            game.setScreen(new LoginScreen(game));
        }
    }

    public boolean isInitialized() {
        boolean isInitComplete = initializationComplete.get();
        boolean isStarterInProgress = starterSelectionInProgress.get();
        GameLogger.info("isInitialized() called - initializationComplete: " + isInitComplete +
            ", starterSelectionInProgress: " + isStarterInProgress);
        return isInitComplete || isStarterInProgress;
    }

    public void setInitialized(boolean initialized) {
        this.initializedworld = initialized;
    }

    public GameClient getGameClient() {
        return gameClient;
    }

    private void initializeBasicResources() {
        GameLogger.info("Initializing basic resources");

        try {
            // 1. Graphics resources
            this.batch = new SpriteBatch();
            this.uiBatch = new SpriteBatch();
            this.shapeRenderer = new ShapeRenderer();

            // 2. Camera and viewport setup - CRITICAL: Do this first
            this.camera = new OrthographicCamera();
            float baseWidth = TARGET_VIEWPORT_WIDTH_TILES * TILE_SIZE;
            float baseHeight = baseWidth * ((float) Gdx.graphics.getHeight() / Gdx.graphics.getWidth());
            this.cameraViewport = new FitViewport(baseWidth, baseHeight, camera);
            camera.position.set(baseWidth / 2f, baseHeight / 2f, 0); // Set initial position
            camera.update();
            cameraViewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);

            // 3. UI resources
            this.skin = new Skin(Gdx.files.internal("Skins/uiskin.json"));
            this.uiSkin = this.skin;
            this.font = new BitmapFont(Gdx.files.internal("Skins/default.fnt"));

            // 4. Stages with viewports
            this.uiStage = new Stage(new ScreenViewport(), uiBatch);
            this.pokemonPartyStage = new Stage(new ScreenViewport());
            this.stage = new Stage(new ScreenViewport());
            this.battleStage = new Stage(new FitViewport(BATTLE_SCREEN_WIDTH, BATTLE_SCREEN_HEIGHT));

            GameLogger.info("Basic resources initialized successfully");
        } catch (Exception e) {
            GameLogger.error("Failed to initialize basic resources: " + e.getMessage());
            throw new RuntimeException("Failed to initialize basic resources", e);
        }
    }

    private void initializeChatSystem() {
        if (chatSystem != null) {
            return;
        }

        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();
        float chatWidth = Math.max(ChatSystem.MIN_CHAT_WIDTH, screenWidth * 0.25f);
        float chatHeight = Math.max(ChatSystem.MIN_CHAT_HEIGHT, screenHeight * 0.3f);

        // Pass commandsEnabled to ChatSystem
        chatSystem = new ChatSystem(uiStage, skin, gameClient, username, commandManager, commandsEnabled);
        GameLogger.info("Chat system initialized. Commands " +
            (commandsEnabled ? "enabled" : "disabled"));
        chatSystem.setSize(chatWidth, chatHeight);
        chatSystem.setPosition(
            ChatSystem.CHAT_PADDING,
            screenHeight - chatHeight - ChatSystem.CHAT_PADDING
        );

        // Set up chat system properties
        chatSystem.setZIndex(Integer.MAX_VALUE);
        chatSystem.setVisible(true);
        chatSystem.setTouchable(Touchable.enabled);

        // Create background
        Pixmap bgPixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        bgPixmap.setColor(0, 0, 0, 0.8f);
        bgPixmap.fill();
        TextureRegion bgTexture = new TextureRegion(new Texture(bgPixmap));
        chatSystem.setBackground(new TextureRegionDrawable(bgTexture));
        bgPixmap.dispose();

        // Add to UI stage
        uiStage.addActor(chatSystem);
        chatSystem.toFront();

        GameLogger.info("Chat system initialized at: " + ChatSystem.CHAT_PADDING + "," +
            (screenHeight - chatHeight - ChatSystem.CHAT_PADDING));
    }

    private void initializeWorldAndPlayer() {
        GameLogger.info("Initializing world and player");

        try {
            // 1. Initialize or obtain world
            if (world == null) {
                if (isMultiplayer) {
                    world = gameClient.getCurrentWorld();
                    if (world == null) {
                        throw new IllegalStateException("No world available from GameClient");
                    }
                } else {
                    world = new World(
                        "singleplayer_world",
                        System.currentTimeMillis(),
                        gameClient,
                        new BiomeManager(System.currentTimeMillis())
                    );
                }
            }

            // 2. Set up player
            if (isMultiplayer) {
                player = gameClient.getActivePlayer();
                if (player == null) {
                    throw new IllegalStateException("No player available from GameClient");
                }
            } else {
                player = game.getPlayer();
                if (player == null) {
                    player = new Player(username, world);
                    game.setPlayer(player);
                }
            }

            // 3. Initialize world data and components
            if (isMultiplayer) {
                world.initializeFromServer(
                    gameClient.getWorldSeed(),
                    world.getWorldData().getWorldTimeInMinutes(),
                    world.getWorldData().getDayLength()
                );
            }

            // 4. Initialize player resources and world connection
            player.initializeResources();
            player.initializeInWorld(world);
            world.setPlayer(player); // This also initializes player data in World

            // 5. Load initial chunks around player
            Vector2 playerPos = new Vector2(
                (float) player.getTileX() / World.CHUNK_SIZE,
                (float) player.getTileY() / World.CHUNK_SIZE
            );
            world.loadChunksAroundPositionSynchronously(playerPos, INITIAL_LOAD_RADIUS);

            if (!world.areAllChunksLoaded()) {
                GameLogger.info("Forcing load of missing chunks...");
                world.forceLoadMissingChunks();
            }

            if (camera != null) {
                camera.position.set(
                    player.getX() + Player.FRAME_WIDTH / 2f,
                    player.getY() + Player.FRAME_HEIGHT / 2f,
                    0
                );
                camera.update();
            }
            logWorldInitializationState();

            GameLogger.info("World and player initialized successfully");

        } catch (Exception e) {
            GameLogger.error("Failed to initialize world and player: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    private void logWorldInitializationState() {
        if (world != null) {
            GameLogger.info("=== World Initialization State ===");
            GameLogger.info("World: " + world.getName());
            GameLogger.info("Loaded chunks: " + world.getChunks().size());
            GameLogger.info("Required chunks: " +
                ((INITIAL_LOAD_RADIUS * 2 + 1) * (INITIAL_LOAD_RADIUS * 2 + 1)));
            if (player != null) {
                GameLogger.info("Player position: " + player.getX() + "," + player.getY());
                GameLogger.info("Player chunk: " + (player.getTileX() / World.CHUNK_SIZE) + "," +
                    (player.getTileY() / World.CHUNK_SIZE));
            }
            GameLogger.info("All chunks loaded: " + world.areAllChunksLoaded());
            GameLogger.info("==============================");
        }
    }

    private void initializeGameSystems() {
        GameLogger.info("Initializing game systems");

        // 1. Camera and viewport
        setupCamera();

        initializeChatSystem();

        // 2. Input handling
        this.chestHandler = new ChestInteractionHandler();
        this.inputHandler = new InputHandler(player, this, this, this, chestHandler, inputManager);

        // 3. Game systems
        this.inventory = player.getInventory();
        this.spawnManager = world.getPokemonSpawnManager();

        // 4. UI Components
        this.gameMenu = new GameMenu(game, uiSkin, player, gameClient, inputManager);
        createPartyDisplay();

        // 5. Battle assets
        initializeBattleAssets();

        // 6. Platform specific
        if (Gdx.app.getType() == Application.ApplicationType.Android) {
            this.movementController = new AndroidMovementController(player, inputHandler);
            initializeAndroidControls();
        }

        GameLogger.info("Game systems initialized successfully");
    }

    private void initializeBattleAssets() {
        try {
            battleAssets = new BattleAssets();
            battleAssets.initialize();


            try {
                FileHandle skinFile = Gdx.files.internal("assets/atlas/ui-gfx-atlas.json");
                if (skinFile.exists()) {
                    battleSkin = new Skin(skinFile);
                    battleSkin.addRegions(TextureManager.getUi());
                    GameLogger.info("Battle skin loaded successfully");
                } else {
                    GameLogger.info("No battle skin found - using default styles");
                    // Continue without skin - will use direct texture regions
                }
            } catch (Exception skinEx) {
                GameLogger.error("Could not load battle skin: " + skinEx.getMessage() + " - continuing without skin");
                // Continue without skin
                if (battleSkin != null) {
                    battleSkin.dispose();
                    battleSkin = null;
                }
            }

        } catch (Exception e) {
            GameLogger.error("Failed to initialize battle assets: " + e.getMessage());
            cleanup();
            throw new RuntimeException("Battle initialization failed", e);
        }
    }

    private void retryInitialization() {
        waitingForInitialization = true;
        initializationComplete.set(false);

        try {
            if (gameClient != null) {
                gameClient.connect();
            }
        } catch (Exception e) {
            GameLogger.error("Failed to retry initialization: " + e.getMessage());
            handleClientInitialization(false);
        }
    }

    private void initializeWorld() {
        try {
            if (isMultiplayer) {
                this.player = this.gameClient.getActivePlayer();
            } else {
                this.player = game.getPlayer();
            }
            if (gameClient.getCurrentWorld() != null) {
                this.world = gameClient.getCurrentWorld();
                GameLogger.info("Using existing world from GameClient");
                return;
            }
            if (this.world == null) {
                String defaultWorldName = isMultiplayer ?
                    CreatureCaptureGame.MULTIPLAYER_WORLD_NAME :
                    "singleplayer_world";
                GameLogger.info("No world name provided, using default: " + defaultWorldName);
                this.world = new World(
                    defaultWorldName,
                    gameClient.getWorldSeed(),
                    gameClient,
                    new BiomeManager(gameClient.getWorldSeed())
                );
            }
            if (player != null) {
                player.initializeInWorld(world);
                world.setPlayer(player);
                player.setWorld(world);
            }
            if (gameClient.getCurrentWorld() == null) {
                gameClient.setCurrentWorld(world);
            }
            this.storageSystem = new ServerStorageSystem();
            this.worldManager = WorldManager.getInstance(storageSystem, isMultiplayer);
            try {
                worldManager.init();
                GameLogger.info("World manager initialized successfully");
            } catch (Exception e) {
                GameLogger.error("Failed to initialize world manager: " + e.getMessage());
                throw e;
            }

            GameLogger.info("World initialization complete");

        } catch (Exception e) {
            GameLogger.error("Failed to initialize world: " + e.getMessage());
            throw new RuntimeException("World initialization failed", e);
        }
    }

    private void handleInitializationFailure() {
        Gdx.app.postRunnable(() -> {
            Dialog dialog = new Dialog("Initialization Error", skin) {
                @Override
                protected void result(Object obj) {
                    if ((Boolean) obj) {
                        // Retry initialization
                        handleMultiplayerInitialization(true);
                    } else {
                        // Return to login screen
                        game.setScreen(new LoginScreen(game));
                    }
                }
            };

            dialog.text("Failed to initialize game. Would you like to retry?");
            dialog.button("Retry", true);
            dialog.button("Cancel", false);
            dialog.show(stage);
        });
    }

    public ChatSystem getChatSystem() {
        return chatSystem;
    }

    public void toggleInventory() {
        if (inputManager.getCurrentState() == InputManager.UIState.INVENTORY) {
            inputManager.setUIState(InputManager.UIState.NORMAL);
            if (inventoryScreen != null) {
                inventoryScreen.hide();
            }
        } else {
            inputManager.setUIState(InputManager.UIState.INVENTORY);
            if (inventoryScreen == null) {
                inventoryScreen = new InventoryScreen(player, skin, inventory, inputManager);
            }
            inventoryScreen.show();
            inputManager.updateInputProcessors();
        }
    }

    public void toggleGameMenu() {
        if (inputManager.getCurrentState() == InputManager.UIState.MENU) {
            inputManager.setUIState(InputManager.UIState.NORMAL);
        } else {
            inputManager.setUIState(InputManager.UIState.MENU);
        }
    }

    private void initializeBattleComponents(Pokemon validPokemon, WildPokemon nearestPokemon) {
        // Lock Pokemon in place
        battleSystem.lockPokemonForBattle(nearestPokemon);

        // Initialize battle stage
        battleStage = new Stage(new FitViewport(800, 480));
        battleStage.getViewport().update(
            Gdx.graphics.getWidth(),
            Gdx.graphics.getHeight(),
            true
        );

        // Create battle table
        battleTable = new BattleTable(
            battleStage,
            battleSkin,
            validPokemon,
            nearestPokemon
        );

        battleTable.setFillParent(true);
        battleTable.setVisible(true);
        battleStage.addActor(battleTable);

        setupBattleCallbacks(nearestPokemon);
        battleInitialized = true;
    }

    private void handleStarterSelection(Pokemon starter) {
        try {
            GameLogger.info("Processing starter selection: " + starter.getName());
            player.getPokemonParty().addPokemon(starter);
            player.updatePlayerData();

            // Save if multiplayer
            if (!gameClient.isSinglePlayer()) {
                gameClient.savePlayerState(player.getPlayerData());
            }

            // Remove starter selection UI
            if (starterTable != null) {
                starterTable.remove();
                starterTable = null;
            }

            // Reset input block and state
            inputBlocked = false;
            inputManager.setUIState(InputManager.UIState.NORMAL);
            inputManager.updateInputProcessors();

            // Also clear keyboard focus
            if (uiStage != null) {
                uiStage.setKeyboardFocus(null);
                uiStage.unfocusAll();
            }

            completeInitialization();
            GameLogger.info("Starter selection complete - proceeding with initialization");

        } catch (Exception e) {
            GameLogger.error("Failed to process starter selection: " + e.getMessage() + e);
        }
    }

    private void initiateStarterSelection() {
        GameLogger.info("CRITICAL - Initiating starter selection");
        starterSelectionInProgress.set(true);

        try {

            if (uiStage == null) {
                uiStage = new Stage(new ScreenViewport());
            }

            inputManager.updateInputProcessors();

            if (starterTable == null) {
                starterTable = new StarterSelectionTable(skin);
                starterTable.setSelectionListener(new StarterSelectionTable.SelectionListener() {
                    @Override
                    public void onStarterSelected(Pokemon starter) {
                        handleStarterSelection(starter);
                    }

                    @Override
                    public void onSelectionStart() {
                        inputBlocked = true;
                    }
                });

                starterTable.setFillParent(true);
                uiStage.addActor(starterTable);
                starterTable.toFront();
            }

            GameLogger.info("Starter selection UI initialized");

        } catch (Exception e) {
            GameLogger.error("Failed to initialize starter selection: " + e.getMessage());
            handleStarterSelectionError(e);
        }
    }

    private void handleStarterSelectionError(Exception e) {
        GameLogger.error("Starter selection error: " + e.getMessage());

        Gdx.app.postRunnable(() -> {
            // Clean up any partial state
            if (starterTable != null) {
                starterTable.remove();
                starterTable = null;
            }

            // Show error dialog
            Dialog dialog = new Dialog("Error", skin) {
                @Override
                protected void result(Object obj) {
                    if ((Boolean) obj) {
                        // Retry starter selection
                        initiateStarterSelection();
                    } else {
                        // Return to login screen
                        game.setScreen(new LoginScreen(game));
                    }
                }
            };

            dialog.text("Failed to process starter selection.\nWould you like to try again?");
            dialog.button("Retry", true);
            dialog.button("Back to Login", false);
            dialog.show(uiStage);
        });
    }

    private void handleInitializationError() {
        GameLogger.error("Game initialization failed");

        Gdx.app.postRunnable(() -> {
            Dialog dialog = new Dialog("Error", skin) {
                @Override
                protected void result(Object obj) {
                    if ((Boolean) obj) {
                        // Retry initialization
                        retryInitialization();
                    } else {
                        // Return to login screen
                        game.setScreen(new LoginScreen(game));
                    }
                }
            };

            dialog.text("Failed to initialize game. Would you like to retry?");
            dialog.button("Retry", true);
            dialog.button("Cancel", false);
            dialog.show(stage);
        });
    }

    private void createPartyDisplay() {
        partyDisplay = new Table();
        partyDisplay.setFillParent(true);
        partyDisplay.bottom();
        partyDisplay.padBottom(20f);

        Table slotsTable = new Table();
        slotsTable.setBackground(
            new TextureRegionDrawable(TextureManager.ui.findRegion("hotbar_bg"))
        );
        slotsTable.pad(4f);

        List<Pokemon> party = player.getPokemonParty().getParty();

        for (int i = 0; i < PokemonParty.MAX_PARTY_SIZE; i++) {
            Pokemon pokemon = (party.size() > i) ? party.get(i) : null;
            Table slotCell = createPartySlotCell(i, pokemon);
            slotsTable.add(slotCell).size(64).pad(2);
        }

        partyDisplay.add(slotsTable);
        uiStage.addActor(partyDisplay);
    }

    @Override
    public void hide() {

    }

    private void ensureSaveDirectories() {
        FileHandle saveDir = Gdx.files.local("save");
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }
    }

    public World getWorld() {
        return world;
    }

    public Player getPlayer() {
        return player;
    }

    private Table createPartySlotCell(int index, Pokemon pokemon) {
        Table cell = new Table();
        boolean isSelected = index == 0;

        TextureRegionDrawable slotBg = new TextureRegionDrawable(
            TextureManager.ui.findRegion(isSelected ? "slot_selected" : "slot_normal")
        );
        cell.setBackground(slotBg);

        if (pokemon != null) {
            Table contentStack = new Table();
            contentStack.setFillParent(true);

            Image pokemonIcon = new Image(pokemon.getCurrentIconFrame(Gdx.graphics.getDeltaTime()));
            pokemonIcon.setScaling(Scaling.fit);

            contentStack.add(pokemonIcon).size(40).padTop(4).row();

            Label levelLabel = new Label("Lv." + pokemon.getLevel(), skin);
            levelLabel.setFontScale(0.8f);
            contentStack.add(levelLabel).padTop(2).row();

            ProgressBar hpBar = new ProgressBar(0, pokemon.getStats().getHp(), 1, false, skin);
            hpBar.setValue(pokemon.getCurrentHp());
            contentStack.add(hpBar).width(40).height(4).padTop(2);

            cell.add(contentStack).expand().fill();
        }

        return cell;
    }

    private void updateSlotVisuals() {
        partyDisplay.clearChildren();
        createPartyDisplay();
    }

    public PlayerData getCurrentPlayerState() {
        PlayerData currentState = new PlayerData(player.getUsername());
        // Use InventoryConverter to extract inventory data
        InventoryConverter.extractInventoryDataFromPlayer(player, currentState);
        return currentState;
    }

    private boolean isPokemonPartyVisible() {
        return pokemonPartyUI != null && pokemonPartyUI.isVisible();
    }

    private void updateAndroidControlPositions() {
        if (Gdx.app.getType() != Application.ApplicationType.Android) {
            return;
        }

        try {
            float screenWidth = Gdx.graphics.getWidth();
            float screenHeight = Gdx.graphics.getHeight();
            float buttonSize = screenHeight * 0.1f;
            float padding = buttonSize * 0.5f;

            if (joystickCenter == null) {
                joystickCenter = new Vector2(screenWidth * 0.15f, screenHeight * 0.2f);
            } else {
                joystickCenter.set(screenWidth * 0.15f, screenHeight * 0.2f);
            }

            if (joystickCurrent == null) {
                joystickCurrent = new Vector2(joystickCenter);
            } else {
                joystickCurrent.set(joystickCenter);
            }
            if (inventoryButton == null) {
                inventoryButton = new Rectangle(
                    screenWidth - (buttonSize * 2 + padding * 2),
                    screenHeight - (buttonSize + padding),
                    buttonSize,
                    buttonSize
                );
            } else {
                inventoryButton.set(
                    screenWidth - (buttonSize * 2 + padding * 2),
                    screenHeight - (buttonSize + padding),
                    buttonSize,
                    buttonSize
                );
            }

            if (menuButton == null) {
                menuButton = new Rectangle(
                    screenWidth - (buttonSize + padding),
                    screenHeight - (buttonSize + padding),
                    buttonSize,
                    buttonSize
                );
            } else {
                menuButton.set(
                    screenWidth - (buttonSize + padding),
                    screenHeight - (buttonSize + padding),
                    buttonSize,
                    buttonSize
                );
            }

            GameLogger.info("Updated Android controls - Screen: " + screenWidth + "x" + screenHeight +
                ", Joystick at: " + joystickCenter.x + "," + joystickCenter.y);

        } catch (Exception e) {
            GameLogger.error("Error updating Android controls: " + e.getMessage());
            e.printStackTrace();

            initializeAndroidControlsSafe();
        }
    }

    private void initializeAndroidControlsSafe() {
        try {
            float screenWidth = Math.max(Gdx.graphics.getWidth(), 480); // Minimum safe width
            float screenHeight = Math.max(Gdx.graphics.getHeight(), 320); // Minimum safe height
            float buttonSize = Math.min(screenHeight * 0.1f, 64); // Limit maximum size
            float padding = buttonSize * 0.5f;

            joystickCenter = new Vector2(screenWidth * 0.15f, screenHeight * 0.2f);
            joystickCurrent = new Vector2(joystickCenter);

            inventoryButton = new Rectangle(
                screenWidth - (buttonSize * 2 + padding * 2),
                screenHeight - (buttonSize + padding),
                buttonSize,
                buttonSize
            );

            menuButton = new Rectangle(
                screenWidth - (buttonSize + padding),
                screenHeight - (buttonSize + padding),
                buttonSize,
                buttonSize
            );

            GameLogger.info("Initialized safe Android controls");
        } catch (Exception e) {
            GameLogger.error("Failed to initialize safe Android controls: " + e.getMessage());
        }
    }

    private void ensureAndroidControlsInitialized() {
        if (Gdx.app.getType() == Application.ApplicationType.Android &&
            (joystickCenter == null || joystickCurrent == null ||
                inventoryButton == null || menuButton == null)) {

            initializeAndroidControlsSafe();
        }
    }

    private boolean canInteract() {
        // Check if player is in battle or menu
        if (inBattle) {
            return false;
        }

        // Check if any animations or transitions are happening
        return !transitioning && !inputBlocked;
    }

    @Override
    public void handleBattleInitiation() {
        if (!canInteract()) {
            GameLogger.info("Cannot start battle - player is busy");
            return;
        }

        if (battleSystem.isInBattle()) {
            GameLogger.info("Battle already in progress");
            return;
        }

        WildPokemon nearestPokemon = world.getNearestInteractablePokemon(player);
        if (nearestPokemon == null || nearestPokemon.isAddedToParty()) {
            return;
        }

        // Check for valid Pokemon
        if (player.getPokemonParty() == null || player.getPokemonParty().getSize() == 0) {
            if (chatSystem != null) {
                NetworkProtocol.ChatMessage message = createSystemMessage(
                    "You need a Pokemon to battle!");
                chatSystem.handleIncomingMessage(message);
            }
            GameLogger.info("Cannot battle - player has no Pokemon");
            return;
        }

        Pokemon validPokemon = findFirstValidPokemon(player.getPokemonParty());
        if (validPokemon == null) {
            if (chatSystem != null) {
                NetworkProtocol.ChatMessage message = createSystemMessage(
                    "All your Pokemon need to be healed!");
                chatSystem.handleIncomingMessage(message);
            }
            GameLogger.info("Cannot battle - no healthy Pokemon");
            return;
        }

        try {
            // Initialize battle components first
            initializeBattleComponents(validPokemon, nearestPokemon);

            // Then let UIControlManager handle the state transition
            inputManager.setUIState(InputManager.UIState.BATTLE);

            // Start battle system after UI transition begins
            battleSystem.startBattle();
            inBattle = true;

            GameLogger.info("Battle initialized successfully with " +
                validPokemon.getName() + " vs " + nearestPokemon.getName());

        } catch (Exception e) {
            GameLogger.error("Failed to initialize battle: " + e.getMessage());
            cleanup();
            battleSystem.endBattle();
        }
    }

    private Pokemon findFirstValidPokemon(PokemonParty party) {
        if (party == null) return null;

        for (Pokemon pokemon : party.getParty()) {
            if (pokemon != null && pokemon.getCurrentHp() > 0) {
                return pokemon;
            }
        }
        return null;
    }

    private void setupBattleCallbacks(WildPokemon wildPokemon) {
        if (battleTable == null) {
            GameLogger.error("Cannot setup callbacks - battleTable is null");
            return;
        }

        battleTable.setCallback(new BattleTable.BattleCallback() {
            @Override
            public void onBattleEnd(boolean playerWon) {
                if (playerWon) {
                    handleBattleVictory(wildPokemon);
                    wildPokemon.startDespawnAnimation();

                    Timer.schedule(new Timer.Task() {
                        @Override
                        public void run() {
                            if (world != null && world.getPokemonSpawnManager() != null) {
                                world.getPokemonSpawnManager().removePokemon(wildPokemon.getUuid());
                            }
                        }
                    }, 1.5f);

                    if (chatSystem != null) {
                        NetworkProtocol.ChatMessage message = createSystemMessage(
                            "Victory! " + player.getPokemonParty().getFirstPokemon().getName() +
                                " defeated " + wildPokemon.getName() + "!"
                        );
                        chatSystem.handleIncomingMessage(message);
                    }
                } else {
                    // Handle defeat
                    handleBattleDefeat();

                    // Release wild Pokemon
                    if (wildPokemon.getAi() != null) {
                        wildPokemon.getAi().setPaused(false);
                    }

                    if (chatSystem != null) {
                        NetworkProtocol.ChatMessage message = createSystemMessage(
                            player.getPokemonParty().getFirstPokemon().getName() +
                                " was defeated by wild " + wildPokemon.getName() + "!"
                        );
                        chatSystem.handleIncomingMessage(message);
                    }
                }

                // Clean up battle state
                battleSystem.endBattle();
                endBattle(playerWon, wildPokemon);
            }

            @Override
            public void onTurnEnd(Pokemon activePokemon) {
                GameLogger.info("Turn ended for: " + activePokemon.getName());

                // Update UI if needed
                if (pokemonPartyUI != null) {
                    pokemonPartyUI.updateUI();
                }
            }

            @Override
            public void onStatusChange(Pokemon pokemon, Pokemon.Status newStatus) {
                GameLogger.info("Status changed for " + pokemon.getName() + ": " + newStatus);

                // Show status message
                String statusMessage = pokemon.getName() + " is now " + newStatus.toString().toLowerCase() + "!";
                if (chatSystem != null) {
                    chatSystem.handleIncomingMessage(createSystemMessage(statusMessage));
                }

                // Update UI
                if (pokemonPartyUI != null) {
                    pokemonPartyUI.updateUI();
                }

                // Play status effect sound
                if (newStatus != null) {
                    switch (newStatus) {
                        case BURNED:
                            AudioManager.getInstance().playSound(AudioManager.SoundEffect.DAMAGE);
                            break;
                        case PARALYZED:
                        case POISONED:
                        case BADLY_POISONED:
                            AudioManager.getInstance().playSound(AudioManager.SoundEffect.NOT_EFFECTIVE);
                            break;
                    }
                }
            }

            @Override
            public void onMoveUsed(Pokemon user, Move move, Pokemon target) {
                GameLogger.info(user.getName() + " used " + move.getName());

                // Play move sound
                AudioManager.getInstance().playSound(AudioManager.SoundEffect.MOVE_SELECT);

                // Show move message
                String moveMessage = user.getName() + " used " + move.getName() + "!";
                if (chatSystem != null) {
                    chatSystem.handleIncomingMessage(createSystemMessage(moveMessage));
                }
            }
        });
    }

    public void endBattle(boolean playerWon, WildPokemon wildPokemon) {
        if (battleTable != null && battleTable.hasParent()) {
            battleTable.addAction(Actions.sequence(
                Actions.fadeOut(BATTLE_UI_FADE_DURATION),
                Actions.run(() -> {
                    try {
                        if (playerWon) {
                            handleBattleVictory(wildPokemon);
                        } else {
                            handleBattleDefeat();
                        }

                        cleanup();

                    } catch (Exception e) {
                        GameLogger.error("Error ending battle: " + e.getMessage());
                        cleanup();
                    }
                })
            ));
            battleUIFading = true;
            inBattle = false;
        } else {
            cleanup();
        }
    }

    private void cleanup() {
        if (isDisposing) return;
        isDisposing = true;

        try {
            Gdx.app.postRunnable(() -> {
                try {
                    // Only clear references without disposing textures
                    if (battleTable != null) {
                        battleTable.clear(); // Just clear children, don't dispose
                    }

                    if (battleStage != null) {
                        battleStage.clear();
                    }
                    if (battleSystem != null) {
                        battleSystem.endBattle();
                    }

                    // Reset states
                    inBattle = false;
                    transitioning = false;
                    inputBlocked = false;
                    battleInitialized = false;
                    battleUIFading = false;
                    isDisposing = false;
                    inputManager.updateInputProcessors();

                    GameLogger.info("Battle cleanup complete - Resources preserved for future battles");
                } catch (Exception e) {
                    GameLogger.error("Error during battle cleanup: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            GameLogger.error("Error queuing cleanup: " + e.getMessage());
        }
    }

    private void handleBattleVictory(WildPokemon wildPokemon) {
        // Award experience
        int expGain = calculateExperienceGain(wildPokemon);
        player.getPokemonParty().getFirstPokemon().addExperience(expGain);

        // Show victory message and sound
        AudioManager.getInstance().playSound(AudioManager.SoundEffect.BATTLE_WIN);
        if (chatSystem != null) {
            chatSystem.handleIncomingMessage(createSystemMessage(
                "Victory! " + player.getPokemonParty().getFirstPokemon().getName() +
                    " gained " + expGain + " experience!"
            ));
        }
    }

    private void handleBattleDefeat() {
        // Heal the party
        player.getPokemonParty().healAllPokemon();

        // Play defeat sound and show message
        AudioManager.getInstance().playSound(AudioManager.SoundEffect.DAMAGE);
        if (chatSystem != null) {
            chatSystem.handleIncomingMessage(createSystemMessage(
                "Your Pokémon have been healed!"
            ));
        }
    }

    private int calculateExperienceGain(WildPokemon wildPokemon) {
        // Basic experience calculation
        return wildPokemon.getBaseExperience() * wildPokemon.getLevel() / 7;
    }

    private NetworkProtocol.ChatMessage createSystemMessage(String content) {
        NetworkProtocol.ChatMessage message = new NetworkProtocol.ChatMessage();
        message.sender = "System";
        message.content = content;
        message.timestamp = System.currentTimeMillis();
        message.type = NetworkProtocol.ChatType.SYSTEM;
        return message;
    }

    private void renderOtherPlayers(SpriteBatch batch, Rectangle viewBounds) {
        if (gameClient == null || gameClient.isSinglePlayer()) {
            return;
        }

        Map<String, OtherPlayer> otherPlayers = gameClient.getOtherPlayers();

        synchronized (otherPlayers) {
            // Sort players by Y position for correct depth
            List<OtherPlayer> sortedPlayers = new ArrayList<>(otherPlayers.values());
            sortedPlayers.sort((p1, p2) -> Float.compare(p2.getY(), p1.getY()));

            for (OtherPlayer otherPlayer : sortedPlayers) {
                if (otherPlayer == null) continue;

                float playerX = otherPlayer.getX();
                float playerY = otherPlayer.getY();

                // Only render if within view bounds
                if (viewBounds.contains(playerX, playerY)) {
                    otherPlayer.render(batch);
                }
            }
        }
    }

    // Also modify setupCamera to handle null player safely:
    private void setupCamera() {
        if (camera == null) {
            camera = new OrthographicCamera();
        }

        float baseWidth = TARGET_VIEWPORT_WIDTH_TILES * TILE_SIZE;
        float baseHeight = baseWidth * ((float) Gdx.graphics.getHeight() / Gdx.graphics.getWidth());

        if (cameraViewport == null) {
            cameraViewport = new FitViewport(baseWidth, baseHeight, camera);
        }

        cameraViewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), false);

        // Set initial camera position
        if (player != null) {
            camera.position.set(
                player.getX() + Player.FRAME_WIDTH / 2f,
                player.getY() + Player.FRAME_HEIGHT / 2f,
                0
            );
        } else {
            // Default position if no player
            camera.position.set(baseWidth / 2f, baseHeight / 2f, 0);
        }

        camera.update();
        GameLogger.info("Camera setup - viewport: " + baseWidth + "x" + baseHeight);
    }

    private void updateCamera() {
        if (player != null) {
            float targetX = player.getX() + Player.FRAME_WIDTH / 2f;
            float targetY = player.getY() + Player.FRAME_HEIGHT / 2f;
            float lerp = CAMERA_LERP * Gdx.graphics.getDeltaTime();
            camera.position.x += (targetX - camera.position.x) * lerp;
            camera.position.y += (targetY - camera.position.y) * lerp;

            camera.update();
        }
    }

    private void renderLoadingScreen() {
        batch.begin();
        font.draw(batch, "Loading world...",
            (float) Gdx.graphics.getWidth() / 2 - 50,
            (float) Gdx.graphics.getHeight() / 2);
        batch.end();
    }

    @Override
    public void render(float delta) {

        if (gameClient != null && gameClient.isConnected()) {
            gameClient.update(delta);
            gameClient.tick(delta);
        }
        if (camera != null && starterTable == null) {
            updateCamera();
        }
        if (player != null && player.getPokemonParty().getSize() == 0 && !starterSelectionComplete) {
            uiStage.act(delta);
            uiStage.draw();
            return;
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.F3)) {
            world.getBiomeManager().debugBiomeDistribution(10000);
            world.getBiomeManager().debugNoiseDistribution(10000);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.W)) {
            GameLogger.info("Current Weather: " + world.getWeatherSystem().getCurrentWeather());
            GameLogger.info("Weather Intensity: " + world.getWeatherSystem().getIntensity());
            GameLogger.info("Particle Count: " + world.getWeatherSystem().getParticles().size());
        }

        if (!world.areInitialChunksLoaded()) {
            world.requestInitialChunks(new Vector2(player.getTileX(), player.getTileY()));
            renderLoadingScreen();
            return;
        }

        // Clear screen
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        if (movementController != null) {
            movementController.update();
        }
        if (player != null && player.getPokemonParty().getSize() == 0) {
            Gdx.gl.glClearColor(0.1f, 0.1f, 0.2f, 1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

            uiStage.act(delta);
            uiStage.draw();

            debugTimer += delta;
            if (debugTimer >= 1.0f) {
                debugInputState();
                debugTimer = 0;
            }
            return;
        }
        batch.begin();
        batch.setProjectionMatrix(camera.combined);

        if (world != null && player != null) {
            Rectangle viewBounds = new Rectangle(
                camera.position.x - (camera.viewportWidth * camera.zoom) / 2,
                camera.position.y - (camera.viewportHeight * camera.zoom) / 2,
                camera.viewportWidth * camera.zoom,
                camera.viewportHeight * camera.zoom
            );
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

            world.render(batch, viewBounds, player, this);

            assert gameClient != null;
            if (!gameClient.isSinglePlayer()) {
                renderOtherPlayers(batch, viewBounds);

            }

        }           // Debug info
        if (SHOW_DEBUG_INFO) {
            renderDebugInfo();
        }
        if (inputManager.getCurrentState() == InputManager.UIState.CRAFTING) {
            if (craftingScreen != null) {
                // End the batch to avoid conflicts
                batch.end();

                // Render crafting UI
                craftingScreen.render(delta);

                // Restart the batch for other rendering
                batch.begin();
            }
        }


        batch.end();


        if (inputManager.getCurrentState() == InputManager.UIState.BUILD_MODE) {
            if (buildModeUI != null) {
                buildModeUI.render(batch, camera);
            }
        }
        // Handle world initialization
        if (world != null && !initializedworld) {
            if (!world.areAllChunksLoaded()) {
                initializationTimer += (int) delta;
                if (initializationTimer > 5f) {
                    GameLogger.info("Attempting to force load missing chunks...");
                    world.forceLoadMissingChunks();
                    initializationTimer = 0;
                }
            } else {
                initializedworld = true;
                GameLogger.info("All chunks successfully loaded");
            }
        }
        if (isHoldingDirection && currentDpadDirection != null) {
            movementTimer += delta;
            if (movementTimer >= MOVEMENT_REPEAT_DELAY) {
                player.move(currentDpadDirection);
                movementTimer = 0f;
            }
            player.setRunning(isRunPressed);
        }

        // Enable blending for UI elements
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        if (uiStage != null) {
            uiStage.getViewport().apply();
            uiStage.act(delta);
            uiStage.draw();
        }

        // Then draw battleStage
        if (inBattle) {
            if (battleStage != null && !isDisposing) {
                battleStage.act(delta);
                if (battleTable != null && battleTable.hasParent()) {
                    battleStage.draw();
                }
            }
        }


        if (inputManager.getCurrentState() == InputManager.UIState.MENU) {
            if (gameMenu != null) {
                gameMenu.render();
            }
        }
        if (inputManager.getCurrentState() == InputManager.UIState.INVENTORY) {
            if (inventoryScreen != null) {
                // Enable blending for semi-transparent background
                Gdx.gl.glEnable(GL20.GL_BLEND);
                Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

                // Draw dark background
                shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
                shapeRenderer.setColor(0, 0, 0, 0.7f);
                shapeRenderer.rect(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
                shapeRenderer.end();

                // Render inventory
                inventoryScreen.render(delta);

                Gdx.gl.glDisable(GL20.GL_BLEND);
            }
        }


        if (chestScreen != null && chestScreen.isVisible()) {
            chestScreen.render(delta);
        }

        // Pokemon party rendering
        if (isPokemonPartyVisible()) {
            pokemonPartyStage.act(delta);
            pokemonPartyStage.draw();
        }


        // Android controls
        if (Gdx.app.getType() == Application.ApplicationType.Android && controlsInitialized) {
            ensureAndroidControlsInitialized();
            renderAndroidControls();
        }

        Gdx.gl.glDisable(GL20.GL_BLEND);

        // Game state updates
        if (world != null && player != null) {
            float deltaTime = Gdx.graphics.getDeltaTime();
            player.update(deltaTime);
            // Camera update
            if (!inBattle && !transitioning) {
                updateCamera();
            }

            if (isMultiplayer) {
                // Other systems updates
                updateOtherPlayers(delta);

                if (gameClient != null) {
                    gameClient.tick(delta);
                }
                if (world != null) {
                    world.update(delta, new Vector2(player.getTileX(), player.getTileY()),
                        Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), this);
                }
            } else {
                player.validateResources();
                float viewportWidthPixels = camera.viewportWidth * camera.zoom;
                float viewportHeightPixels = camera.viewportHeight * camera.zoom;
                world.update(delta,
                    new Vector2(player.getTileX(), player.getTileY()),
                    viewportWidthPixels,
                    viewportHeightPixels, this
                );
            }

            if (worldManager != null) {
                worldManager.checkAutoSave();
            }
            handleInput();
            if (inputHandler != null &&
                (inputManager.getCurrentState() == InputManager.UIState.NORMAL ||
                    inputManager.getCurrentState() == InputManager.UIState.BUILD_MODE)) {
                inputHandler.update(delta);
            }

            updateTimer += delta;

            // Handle multiplayer updates
            if (isMultiplayer && updateTimer >= UPDATE_INTERVAL) {
                updateTimer = 0;
                NetworkProtocol.PlayerUpdate update = new NetworkProtocol.PlayerUpdate();
                update.username = player.getUsername();
                update.x = player.getX();
                update.y = player.getY();
                update.direction = player.getDirection();
                update.isMoving = player.isMoving();
                update.wantsToRun = player.isRunning();
                update.timestamp = System.currentTimeMillis();
                assert gameClient != null;
                gameClient.sendPlayerUpdate();

                // Handle incoming updates
                Map<String, NetworkProtocol.PlayerUpdate> updates = gameClient.getPlayerUpdates();
                if (!updates.isEmpty()) {
                    synchronized (gameClient.getOtherPlayers()) {
                        for (NetworkProtocol.PlayerUpdate playerUpdate : updates.values()) {
                            if (!playerUpdate.username.equals(player.getUsername())) {
                                OtherPlayer op = gameClient.getOtherPlayers().get(playerUpdate.username);
                                if (op == null) {
                                    op = new OtherPlayer(playerUpdate.username, playerUpdate.x, playerUpdate.y);
                                    gameClient.getOtherPlayers().put(playerUpdate.username, op);
                                    GameLogger.info("Created new player: " + playerUpdate.username);
                                }
                                op.updateFromNetwork(playerUpdate);
                            }
                        }
                    }
                }
            }
        }
    }

    private String formatPlayedTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        seconds = seconds % 60;
        minutes = minutes % 60;

        return String.format("%02dh %02dm %02ds", hours, minutes, seconds);
    }

    private void updateOtherPlayers(float delta) {
        if (isMultiplayer) {
            Map<String, OtherPlayer> others = gameClient.getOtherPlayers();
            GameLogger.info("Number of other players: " + others.size());
            if (!others.isEmpty()) {
                for (OtherPlayer otherPlayer : others.values()) {
                    try {
                        otherPlayer.update(delta);
                    } catch (Exception e) {
                        GameLogger.error("Error updating other player: " + e.getMessage());
                    }
                }
            }
        }
    }

    private void renderDebugInfo() {
        batch.setProjectionMatrix(uiStage.getCamera().combined);
        font.setColor(Color.WHITE);

        float debugY = 25;

        // Add FPS display at the top
        int fps = Gdx.graphics.getFramesPerSecond();
        font.draw(batch, "FPS: " + fps, 10, debugY);
        debugY += 20;

        float pixelX = player.getX();
        float pixelY = player.getY();
        int tileX = (int) Math.floor(pixelX / TILE_SIZE);
        int tileY = (int) Math.floor(pixelY / TILE_SIZE);
        Biome currentBiome = world.getBiomeAt(tileX, tileY);
        font.draw(batch, String.format("Pixels: (%d, %d)", (int) pixelX, (int) pixelY), 10, debugY);
        debugY += 20;
        font.draw(batch, String.format("Tiles: (%d, %d)", tileX, tileY), 10, debugY);
        debugY += 20;
        font.draw(batch, "Direction: " + player.getDirection(), 10, debugY);
        debugY += 20;
        font.draw(batch, "Biome: " + currentBiome.getName(), 10, debugY);
        debugY += 20;

        font.draw(batch, "Active Pokemon: " + getTotalPokemonCount(), 10, debugY);
        debugY += 20;

        String timeString = DayNightCycle.getTimeString(world.getWorldData().getWorldTimeInMinutes());
        font.draw(batch, "Time: " + timeString, 10, debugY);
        debugY += 20;

        if (!isMultiplayer) {
            long playedTimeMillis = world.getWorldData().getPlayedTime();
            String playedTimeStr = formatPlayedTime(playedTimeMillis);
            font.draw(batch, "Total Time Played: " + playedTimeStr, 10, debugY);
        }
    }

    private void handleInput() {

        if (chatSystem != null && chatSystem.isActive()) {
            return;
        }

        if (inBattle) {
            return;
        }

        // Only handle input when in NORMAL state
        if (inputManager.getCurrentState() == InputManager.UIState.NORMAL) {
            handleGameInput();
        }
    }



    public ChestScreen getChestScreen() {
        return chestScreen;
    }

    private void handleGameInput() {
        if (inBattle) {
            return;
        }
        if (inputBlocked) {
            return;
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.F3)) {
            SHOW_DEBUG_INFO = !SHOW_DEBUG_INFO;
        }


        handleMovementInput();
    }


    public void initializeBuildMode() {
        if (buildModeUI == null && player != null) {
            buildModeUI = new BuildModeUI(skin, player);
            uiStage.addActor(buildModeUI); // Add BuildModeUI to the uiStage
            buildModeUI.setVisible(false); // Start hidden
            GameLogger.info("BuildModeUI initialized");
        }
    }


    private void handleMovementInput() {
        if (Gdx.input.isKeyPressed(Input.Keys.ANY_KEY)) {
            String direction = null;
            if (Gdx.input.isKeyPressed(Input.Keys.UP)) direction = "up";
            else if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) direction = "down";
            else if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) direction = "left";
            else if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) direction = "right";

            if (direction != null) {
                player.move(direction);
            }
        }
    }

    private int getTotalPokemonCount() {
        if (world != null && world.getPokemonSpawnManager() != null) {
            return world.getPokemonSpawnManager().getAllWildPokemon().size();
        }
        return 0;
    }

    private void logInventoryState(String context) {
        if (player == null || player.getInventory() == null) {
            GameLogger.error(context + ": Player or inventory is null");
            return;
        }

        GameLogger.info("\n=== Inventory State: " + context + " ===");
        List<ItemData> items = player.getInventory().getAllItems();
        GameLogger.info("Total slots: " + items.size());
        GameLogger.info("Non-null items: " + items.stream().filter(Objects::nonNull).count());

        GameLogger.info("=====================================\n");
    }

    private void debugInputState() {
        InputProcessor current = Gdx.input.getInputProcessor();
        GameLogger.info("Current input processor: " + (current == null ? "null" : current.getClass().getName()));
        if (current instanceof InputMultiplexer) {
            InputMultiplexer multiplexer = (InputMultiplexer) current;
            for (int i = 0; i < multiplexer.size(); i++) {
                GameLogger.info("Processor " + i + ": " + multiplexer.getProcessors().get(i).getClass().getName());
            }
        }
    }

    @Override
    public void resize(int width, int height) {
        if (player != null) {
            player.validateResources();
        }
        if (starterTable != null) {
            starterTable.resize(width, height);
        }

        ACTION_BUTTON_SIZE = height * 0.12f;
        DPAD_SIZE = height * 0.3f;
        BUTTON_PADDING = width * 0.02f;

        if (controlsInitialized) {
            androidControlsTable.clearChildren();
            createDpad();
            createActionButtons();
        }
        if (buildModeUI != null) {
            buildModeUI.resize(width);
        }

        if (dpadTable != null) {
            dpadTable.clear();
            createDpad();
        }

        cameraViewport.update(width, height, false);

        if (inventoryScreen != null) {
            inventoryScreen.resize(width, height);

            if (closeButtonTable != null && closeButtonTable.getParent() != null) {
                closeButtonTable.invalidate();
            }
        }
        if (craftingScreen != null) {
            craftingScreen.resize(width, height);
        }
        for (Actor actor : stage.getActors()) {
            if (actor instanceof StarterSelectionTable) {
                ((StarterSelectionTable) actor).resize(width, height);
                starterTable.resize(width, height);
                break;
            }
        }
        if (uiStage != null) {
            uiStage.getViewport().update(width, height, true);
            GameLogger.info("Stage viewport updated to: " + width + "x" + height);

            if (starterTable != null && player.getPokemonParty().getSize() == 0) {
                starterTable.setFillParent(true);
                GameLogger.info("Starter table position after resize: " +
                    starterTable.getX() + "," + starterTable.getY());
            }
        }
        if (battleStage != null) {
            battleStage.getViewport().update(width, height, true);
            if (battleTable != null) {
                battleTable.invalidate();
                battleTable.validate();
            }
        }
        if (uiStage != null) {
            uiStage.getViewport().update(width, height, true);
        }
        if (gameMenu != null && gameMenu.getStage() != null) {
            gameMenu.getStage().getViewport().update(width, height, true);
        }
        if (pokemonPartyStage != null) {
            pokemonPartyStage.getViewport().update(width, height, true);
        }
        if (chatSystem != null) {
            float chatWidth = Math.max(ChatSystem.MIN_CHAT_WIDTH, width * 0.25f);
            float chatHeight = Math.max(ChatSystem.MIN_CHAT_HEIGHT, height * 0.3f);

            chatSystem.setSize(chatWidth, chatHeight);
            chatSystem.setPosition(
                ChatSystem.CHAT_PADDING,
                height - chatHeight - ChatSystem.CHAT_PADDING
            );
            chatSystem.resize(width, height);
        }
        if (controlsInitialized) {
            joystickCenter.set(width * 0.2f, height * 0.25f);
            joystickCurrent.set(joystickCenter);

            if (androidControlsTable != null) {
                androidControlsTable.invalidateHierarchy();
            }
        }
        ensureAndroidControlsInitialized();
        updateAndroidControlPositions();

        updateCamera();
        GameLogger.info("Screen resized to: " + width + "x" + height);
    }

    private ItemData generateRandomItemData() {
        List<String> itemNames = new ArrayList<>(ItemManager.getAllFindableItemNames());
        if (itemNames.isEmpty()) {
            GameLogger.error("No items available in ItemManager to generate random item.");
            return null;
        }
        int index = MathUtils.random(itemNames.size() - 1);
        String itemName = itemNames.get(index);
        ItemData itemData = InventoryConverter.itemToItemData(ItemManager.getItem(itemName));
        if (itemData != null) {
            itemData.setCount(1);
            itemData.setUuid(UUID.randomUUID());
            return itemData;
        }
        GameLogger.error("Failed to retrieve ItemData for item: " + itemName);
        return null;
    }


    public void handlePickupAction() {
        WorldObject nearestPokeball = world.getNearestPokeball();
        if (nearestPokeball == null) {
            GameLogger.info("No pokeball found nearby");
            return;
        }
        GameLogger.info("Player position: " + player.getX() + "," + player.getY());
        GameLogger.info("Pokeball position: " + nearestPokeball.getPixelX() + "," + nearestPokeball.getPixelY());

        if (player.canPickupItem(nearestPokeball.getPixelX(), nearestPokeball.getPixelY())) {
            world.removeWorldObject(nearestPokeball);

            ItemData randomItemData = generateRandomItemData();
            if (randomItemData == null) {
                GameLogger.error("Failed to generate random item data.");
                return;
            }

            boolean added = false;
            try {
                added = InventoryConverter.addItemToInventory(inventory, randomItemData);
            } catch (Exception e) {
                GameLogger.error("Error adding item to inventory: " + e.getMessage());
            }

            NetworkProtocol.ChatMessage pickupMessage = new NetworkProtocol.ChatMessage();
            pickupMessage.sender = "System";
            pickupMessage.timestamp = System.currentTimeMillis();

            if (added) {
                pickupMessage.content = "You found: " + randomItemData.getItemId() + " (×" + randomItemData.getCount() + ")";
                pickupMessage.type = NetworkProtocol.ChatType.SYSTEM;
                GameLogger.info("Item added to inventory: " + randomItemData.getItemId());

                AudioManager.getInstance().playSound(AudioManager.SoundEffect.ITEM_PICKUP);

                player.updatePlayerData();

                logInventoryState("Post-pickup inventory state:");
            } else {
                pickupMessage.content = "Inventory full! Couldn't pick up: " + randomItemData.getItemId();
                pickupMessage.type = NetworkProtocol.ChatType.SYSTEM;
                GameLogger.info("Inventory full. Cannot add: " + randomItemData.getItemId());
            }

            // Send message to chat
            if (chatSystem != null) {
                chatSystem.handleIncomingMessage(pickupMessage);
            }
        } else {
            GameLogger.info("Cannot pick up pokeball - too far or wrong direction");
        }
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    public SpriteBatch getBatch() {
        return batch;
    }

    @Override
    public void dispose() {
        if (isDisposing) return;
        isDisposing = true;

        try {
            if (player != null && world != null) {
                PlayerData finalState = getCurrentPlayerState();
                world.getWorldData().savePlayerData(player.getUsername(), finalState, false);

                world.save();

                GameLogger.info("Final save complete for player: " + player.getUsername() +
                    " in world: " + world.getName());
            }

            Gdx.app.postRunnable(() -> {
                try {
                    if (chestScreen != null) {
                        chestScreen.dispose();
                    }
                    if (buildModeUI != null) {
                        buildModeUI.dispose();
                        buildModeUI = null;
                    }

                    if (pokemonPartyStage != null) {
                        pokemonPartyStage.clear();
                        pokemonPartyStage.dispose();
                        pokemonPartyStage = null;
                    }

                    if (uiStage != null) {
                        uiStage.clear();
                        uiStage.dispose();
                        uiStage = null;
                    }

                    if (batch != null) {
                        batch.dispose();
                        batch = null;
                    }

                    if (shapeRenderer != null) {
                        shapeRenderer.dispose();
                        shapeRenderer = null;
                    }

                    if (player != null) {
                        player.dispose();
                        player = null;
                    }

                    for (OtherPlayer op : gameClient.getOtherPlayers().values()) {
                        if (op != null) op.dispose();
                    }
                    gameClient.dispose();

                    if (!screenInitScheduler.isShutdown()) {
                        screenInitScheduler.shutdown();
                        try {
                            if (!screenInitScheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                                screenInitScheduler.shutdownNow();
                            }
                        } catch (InterruptedException e) {
                            screenInitScheduler.shutdownNow();
                            Thread.currentThread().interrupt();
                        }
                    }

                    GameLogger.info("GameScreen disposed successfully");
                } catch (Exception e) {
                    GameLogger.error("Error during GameScreen disposal: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            GameLogger.error("Error during disposal: " + e.getMessage());
        }
    }

    private void initializeAndroidControls() {
        if (Gdx.app.getType() != Application.ApplicationType.Android || controlsInitialized) {
            return;
        }

        try {
            float screenWidth = Gdx.graphics.getWidth();
            float screenHeight = Gdx.graphics.getHeight();

            ACTION_BUTTON_SIZE = screenHeight * 0.12f; // Increase from 0.1f to 0.12f
            DPAD_SIZE = screenHeight * 0.3f;
            BUTTON_PADDING = screenWidth * 0.02f;      // Padding between buttons
            androidControlsTable = new Table();
            androidControlsTable.setFillParent(true);

            createDpad();

            createActionButtons();

            uiStage.addActor(androidControlsTable);

            createDpadHitboxes();

            controlsInitialized = true;
            GameLogger.info("Android controls initialized");

        } catch (Exception e) {
            GameLogger.error("Failed to initialize Android controls: " + e.getMessage());
        }
    }


    private Actor createColoredButton(String label, Color color, float size) {
        Pixmap pixmap = new Pixmap((int) size, (int) size, Pixmap.Format.RGBA8888);
        pixmap.setColor(color.r, color.g, color.b, 0.8f);
        pixmap.fillCircle((int) size / 2, (int) size / 2, (int) size / 2);
        TextureRegionDrawable drawable = new TextureRegionDrawable(new Texture(pixmap));
        pixmap.dispose();

        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.up = drawable;
        style.down = drawable.tint(Color.DARK_GRAY);
        style.font = skin.getFont("default-font");
        style.fontColor = Color.WHITE;

        TextButton button = new TextButton(label, style);
        Container<TextButton> buttonContainer = new Container<>(button);
        buttonContainer.setTransform(true);
        buttonContainer.size(size * 1.5f);
        buttonContainer.setOrigin(Align.center);
        buttonContainer.setTouchable(Touchable.enabled);
        float fontScale = size / 60f;
        button.getLabel().setFontScale(fontScale);

        button.getLabel().setAlignment(Align.center);

        button.setSize(size, size);

        return buttonContainer;
    }

    private void createActionButtons() {
        // Create buttons
        aButton = createColoredButton("A", Color.GREEN, ACTION_BUTTON_SIZE);
        xButton = createColoredButton("X", Color.BLUE, ACTION_BUTTON_SIZE);
        yButton = createColoredButton("Y", Color.YELLOW, ACTION_BUTTON_SIZE);
        zButton = createColoredButton("Z", Color.PURPLE, ACTION_BUTTON_SIZE);
        startButton = createColoredButton("Start", Color.WHITE, ACTION_BUTTON_SIZE);
        selectButton = createColoredButton("Select", Color.GRAY, ACTION_BUTTON_SIZE);

        // Set touchable
        aButton.setTouchable(Touchable.enabled);
        xButton.setTouchable(Touchable.enabled);
        yButton.setTouchable(Touchable.enabled);
        zButton.setTouchable(Touchable.enabled);
        startButton.setTouchable(Touchable.enabled);
        selectButton.setTouchable(Touchable.enabled);

        // Position action buttons
        Table actionButtonTable = new Table();
        actionButtonTable.setFillParent(true);
        actionButtonTable.bottom().right().pad(BUTTON_PADDING * 2);
        actionButtonTable.row();
        actionButtonTable.add(startButton).size(ACTION_BUTTON_SIZE).pad(BUTTON_PADDING);
        actionButtonTable.add(selectButton).size(ACTION_BUTTON_SIZE).pad(BUTTON_PADDING);
        actionButtonTable.row();
        actionButtonTable.add(yButton).size(ACTION_BUTTON_SIZE).pad(BUTTON_PADDING);
        actionButtonTable.add(zButton).size(ACTION_BUTTON_SIZE).pad(BUTTON_PADDING);
        actionButtonTable.row();
        actionButtonTable.add(xButton).size(ACTION_BUTTON_SIZE).pad(BUTTON_PADDING);
        actionButtonTable.add(aButton).size(ACTION_BUTTON_SIZE).pad(BUTTON_PADDING);

        uiStage.addActor(actionButtonTable);

        addButtonListeners();
    }

    private void addButtonListeners() {
        ((Container<TextButton>) aButton).getActor().addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                inputHandler.handleInteraction();
            }
        });

        ((Container<TextButton>) xButton).getActor().addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                inputHandler.startChopping();
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                inputHandler.stopChopping();
            }
        });

        ((Container<TextButton>) yButton).getActor().addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                inputHandler.toggleBuildMode();
            }
        });

        ((Container<TextButton>) zButton).getActor().addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                toggleInventory();
            }
        });

        ((Container<TextButton>) startButton).getActor().addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                handleStartButtonPress();
            }
        });

        ((Container<TextButton>) selectButton).getActor().addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                handleSelectButtonPress();
            }
        });
    }

    private void handleStartButtonPress() {
        toggleGameMenu();
    }

    private void handleSelectButtonPress() {

        SHOW_DEBUG_INFO = !SHOW_DEBUG_INFO;
    }

    private void createDpad() {

        float dpadSize = DPAD_SIZE; // Use the adjusted size constant
        float paddingLeft = BUTTON_PADDING * 2; // Left padding
        float paddingBottom = BUTTON_PADDING * 2; // Bottom padding

        // Create D-pad touch area
        Image dpadTouchArea = new Image();
        dpadTouchArea.setSize(dpadSize, dpadSize);
        dpadTouchArea.setPosition(paddingLeft, paddingBottom);
        dpadTouchArea.setColor(1, 1, 1, 0); // Fully transparent
        dpadTouchArea.setTouchable(Touchable.enabled);

        // Add touch listener to the D-pad area
        dpadTouchArea.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                float absoluteX = event.getStageX();
                float absoluteY = event.getStageY();
                movementController.handleTouchDown(absoluteX, absoluteY);
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                float absoluteX = event.getStageX();
                float absoluteY = event.getStageY();
                movementController.handleTouchDragged(absoluteX, absoluteY);
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                movementController.handleTouchUp();
            }
        });

        uiStage.addActor(dpadTouchArea);
    }

    private void createDpadHitboxes() {
        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();
        float dpadCenterX = screenWidth * 0.15f;
        float dpadCenterY = screenHeight * 0.2f;
        float buttonSize = DPAD_BUTTON_SIZE;
        upButton = new Rectangle(
            dpadCenterX - buttonSize / 2,
            dpadCenterY + buttonSize / 4,
            buttonSize,
            buttonSize
        );

        downButton = new Rectangle(
            dpadCenterX - buttonSize / 2,
            dpadCenterY - buttonSize - buttonSize / 4,
            buttonSize,
            buttonSize
        );

        leftButton = new Rectangle(
            dpadCenterX - buttonSize - buttonSize / 4,
            dpadCenterY - buttonSize / 2,
            buttonSize,
            buttonSize
        );

        rightButton = new Rectangle(
            dpadCenterX + buttonSize / 4,
            dpadCenterY - buttonSize / 2,
            buttonSize,
            buttonSize
        );

    }


    private void renderAndroidControls() {
        if (!controlsInitialized || movementController == null) return;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        if (movementController.isActive()) {
            // Draw base circle
            Vector2 center = movementController.getJoystickCenter();
            float maxRadius = movementController.getMaxRadius();

            // Base circle (lighter)
            shapeRenderer.setColor(0.3f, 0.3f, 0.3f, 0.3f);
            shapeRenderer.circle(center.x, center.y, maxRadius);

            // Draw joystick knob
            Vector2 current = movementController.getJoystickCurrent();
            float knobSize = maxRadius * 0.3f;

            // Knob shadow
            shapeRenderer.setColor(0.0f, 0.0f, 0.0f, 0.4f);
            shapeRenderer.circle(current.x, current.y, knobSize + 2);

            // Knob (brighter when moved further)
            float intensity = 0.5f + (movementController.getMagnitude() * 0.5f);
            shapeRenderer.setColor(intensity, intensity, intensity, 0.8f);
            shapeRenderer.circle(current.x, current.y, knobSize);
        }

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }


    public class AndroidInputProcessor extends InputAdapter {
        private final AndroidMovementController movementController;
        private final Actor aButton;
        private final Actor zButton;
        private final Actor xButton;
        private final Actor yButton;
        private final Actor startButton;
        private final Actor selectButton;

        public AndroidInputProcessor(AndroidMovementController movementController,
                                     Actor aButton, Actor zButton,
                                     Actor xButton, Actor yButton,
                                     Actor startButton, Actor selectButton) {
            this.movementController = movementController;
            this.aButton = aButton;
            this.zButton = zButton;
            this.xButton = xButton;
            this.yButton = yButton;
            this.startButton = startButton;
            this.selectButton = selectButton;
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            float touchX = screenX;
            float touchY = Gdx.graphics.getHeight() - screenY; // Flip Y coordinate

            if (isTouchOnButton(touchX, touchY, aButton)) {
                inputHandler.handleInteraction();
                return true;
            }

            if (isTouchOnButton(touchX, touchY, xButton)) {
                inputHandler.startChopping();
                return true;
            }

            if (isTouchOnButton(touchX, touchY, yButton)) {
                inputHandler.toggleBuildMode();
                return true;
            }

            if (isTouchOnButton(touchX, touchY, zButton)) {
                toggleInventory();
                return true;
            }

            if (isTouchOnButton(touchX, touchY, startButton)) {
                handleStartButtonPress();
                return true;
            }

            if (isTouchOnButton(touchX, touchY, selectButton)) {
                handleSelectButtonPress();
                return true;
            }

            // D-pad handling
            movementController.handleTouchDown((int) touchX, (int) touchY);
            return true;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            float touchX = screenX;
            float touchY = Gdx.graphics.getHeight() - screenY;
            movementController.handleTouchDragged((int) touchX, (int) touchY);
            return true;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            movementController.handleTouchUp();
            inputHandler.stopChopping();
            return false;
        }

        private boolean isTouchOnButton(float x, float y, Actor button) {
            return x >= button.getX() && x <= button.getX() + button.getWidth()
                && y >= button.getY() && y <= button.getY() + button.getHeight();
        }
    }

}

