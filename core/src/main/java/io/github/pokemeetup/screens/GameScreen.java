package io.github.pokemeetup.screens;

import com.badlogic.gdx.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.profiling.GLProfiler;
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
import io.github.pokemeetup.chat.commands.*;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.context.UIManager;
import io.github.pokemeetup.managers.BiomeManager;
import io.github.pokemeetup.multiplayer.OtherPlayer;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.client.GameClientSingleton;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
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
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.system.gameplay.overworld.*;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldManager;
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
    public static final boolean DEBUG_MODE = false;
    private static final float TARGET_VIEWPORT_WIDTH_TILES = 24f;
    private static final float UPDATE_INTERVAL = 0.1f;
    private static final float CAMERA_LERP = 5.0f;
    private static final float BATTLE_UI_FADE_DURATION = 0.5f;
    private static final float BATTLE_SCREEN_WIDTH = 800;
    private static final float BATTLE_SCREEN_HEIGHT = 480;
    private static final float MOVEMENT_REPEAT_DELAY = 0.1f;
    public static boolean SHOW_DEBUG_INFO = false;
    private final CreatureCaptureGame game;
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
    private Vector2 joystickCenter = new Vector2();
    private Vector2 joystickCurrent = new Vector2();
    private float initializationTimer = 0f;
    private Stage pokemonPartyStage;
    private Table partyDisplay;
    private float updateTimer = 0;
    private BitmapFont font;
    private OrthographicCamera camera;
    private InputHandler inputHandler;
    private String username;
    private Rectangle inventoryButton;
    private Rectangle menuButton;
    private ShapeRenderer shapeRenderer;
    private Skin skin;
    private Skin uiSkin;
    private Stage stage;
    private FitViewport cameraViewport;
    private boolean transitioning = false;
    private boolean controlsInitialized = false;
    private StarterSelectionTable starterTable;
    private boolean inputBlocked = false;
    private float debugTimer = 0;
    private boolean initializedworld = false;
    private volatile boolean isDisposing = false;
    private BattleTable battleTable;
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
    private float movementTimer = 0f;
    private boolean isHoldingDirection = false;
    private boolean isRunPressed = false;
    private AndroidMovementController movementController;
    private Runnable pendingStarterInit = null;
    private volatile boolean awaitingStarterSelection = false;
    private BattleSystemHandler battleSystem;
    private boolean commandsEnabled = false;
    private InputManager inputManager;
    private ChestScreen chestScreen;
    private Actor houseToggleButton;
    private GLProfiler glProfiler;
    private boolean initialChunksLoadedOnce = false;

    public GameScreen(CreatureCaptureGame game, String username, GameClient gameClient) {
        this.game = game;
        this.username = username;
        GameContext.get().setGameClient(gameClient);
        this.commandManager = new CommandManager();
        registerAllCommands();
        this.isMultiplayer = GameContext.get().isMultiplayer();

        GameContext.get().setUiStage(new Stage(new ScreenViewport()));
        this.battleSystem = new BattleSystemHandler();
        GameContext.get().setBattleSystem(this.battleSystem);
        try {
            initializeBasicResources();

            initializeWorldAndPlayer(CreatureCaptureGame.MULTIPLAYER_WORLD_NAME);
            this.inputManager = new InputManager(this);
            Player player = GameContext.get().getPlayer();
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

    public GameScreen(CreatureCaptureGame game, String username, GameClient gameClient, boolean commandsEnabled, String worldName) {
        this.game = game;
        this.username = username;
        this.commandsEnabled = commandsEnabled;
        this.commandManager = new CommandManager();
        registerAllCommands();
        GameContext.get().setGameClient(gameClient);
        this.isMultiplayer = GameContext.get().isMultiplayer();
        GameContext.get().setUiStage(new Stage(new ScreenViewport()));

        this.battleSystem = new BattleSystemHandler();
        GameContext.get().setBattleSystem(this.battleSystem);
        try {
            initializeBasicResources();

            initializeWorldAndPlayer(worldName);
            this.inputManager = new InputManager(this);
            Player player = GameContext.get().getPlayer();
            if (player != null && player.getPokemonParty().getSize() == 0) {
                GameLogger.info("New player detected - handling starter selection");
                handleNewPlayer();
            } else {
                completeInitialization();
            }

        } catch (Exception e) {
            GameLogger.error("GameScreen initialization failed: " + e.getMessage());
            throw new RuntimeException("Failed to initialize game screen", e);
        }
    }

    private static final float AUTO_SAVE_INTERVAL_SECONDS = 300f; // 5 minutes
    private float autoSaveTimer = 0f;
    public void updatePartyDisplay() {
        if (partyDisplay != null) {
            partyDisplay.remove();
        }
        createPartyDisplay();
    }

    public Table getPartyDisplay() {
        return partyDisplay;
    }

    public Skin getBattleSkin() {
        return battleSkin;
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
        return GameContext.get().getUiStage();
    }

    public CraftingTableScreen getCraftingScreen() {
        return GameContext.get().getCraftingScreen();
    }

    public void setCraftingScreen(CraftingTableScreen craftingScreen) {
        GameContext.get().setCraftingScreen(craftingScreen);
    }


    public GameMenu getGameMenu() {
        return GameContext.get().getGameMenu();
    }

    public void setGameMenu(GameMenu gameMenu) {
        GameContext.get().setGameMenu(gameMenu);
    }

    public Stage getBattleStage() {
        return battleStage;
    }

    public InventoryScreen getInventoryScreen() {
        return GameContext.get().getInventoryScreen();
    }

    public BuildModeUI getBuildModeUI() {
        return GameContext.get().getBuildModeUI();
    }

    public ChestInteractionHandler getChestHandler() {
        return chestHandler;
    }

    public OrthographicCamera getCamera() {
        return camera;
    }

    public void openChestScreen(Vector2 chestPosition, ChestData chestData) {
        if (chestScreen != null) {
            chestScreen.dispose();  // or hide() and then remove references if appropriate
        }
        chestScreen = new ChestScreen(skin, chestData, chestPosition, this);
        chestScreen.show();
        inputManager.setUIState(InputManager.UIState.CHEST_SCREEN);
    }

    public void closeChestScreen() {
        if (chestScreen != null) {
            chestScreen.hide();
        }
        inputManager.setUIState(InputManager.UIState.NORMAL);
    }

    public void openExpandedCrafting(Vector2 craftingTablePosition) {
        if (GameContext.get().getCraftingScreen() == null) {
            GameContext.get().setCraftingScreen(new CraftingTableScreen(
                GameContext.get().getPlayer(),
                skin,
                GameContext.get().getWorld(),
                GameContext.get().getGameClient(), this, inputManager
            ));
        }
        GameContext.get().getCraftingScreen().updatePosition(craftingTablePosition);
        inputManager.setUIState(InputManager.UIState.CRAFTING);
    }

    public void closeExpandedCrafting() {
        inputManager.setUIState(InputManager.UIState.NORMAL);
        if (GameContext.get().getCraftingScreen() != null) {
            GameContext.get().getCraftingScreen().hide();
        }
    }

    private void handleMultiplayerInitialization(boolean success) {
        if (success) {
            try {
                initializeWorldAndPlayer(CreatureCaptureGame.MULTIPLAYER_WORLD_NAME);
                if (GameContext.get().getPlayer() != null &&
                    GameContext.get().getPlayer().getPokemonParty().getSize() == 0 &&
                    starterTable == null) {
                    GameLogger.info("New player detected – initiating starter selection");
                    awaitingStarterSelection = true;
                    starterSelectionInProgress.set(true);
                    initializationComplete.set(false);
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
        GameLogger.info("GameScreen show() called. Setting up UI and input.");
        Stage uiStage = GameContext.get().getUiStage();
        if (uiStage == null) {
            GameLogger.error("UI Stage is null in GameScreen.show(), re-creating.");
            uiStage = new Stage(new ScreenViewport(), new SpriteBatch());
            GameContext.get().setUiStage(uiStage);
        }
        HotbarSystem hotbar = GameContext.get().getHotbarSystem();
        if (hotbar == null || hotbar.getHotbarTable().getStage() == null) {
            GameLogger.info("Re-initializing HotbarSystem.");
            hotbar = new HotbarSystem(uiStage, skin);
            GameContext.get().setHotbarSystem(hotbar);
        } else if (hotbar.getHotbarTable().getStage() != uiStage) {
            hotbar.getHotbarTable().remove();
            uiStage.addActor(hotbar.getHotbarTable().getParent());
        }
        if (GameContext.get().getChatSystem() != null && GameContext.get().getChatSystem().getStage() != null) {
            GameContext.get().getChatSystem().remove(); // Remove from old stage if it exists
        }
        initializeChatSystem();
        if(GameContext.get().getChatSystem().getStage() != uiStage) {
            uiStage.addActor(GameContext.get().getChatSystem());
        }
        if (GameContext.get().getGameMenu() != null) {
            GameContext.get().getGameMenu().dispose();
        }
        GameContext.get().setGameMenu(new GameMenu(game, skin, inputManager));
        if (GameContext.get().getBuildModeUI() != null) {
            GameContext.get().getBuildModeUI().remove();
        }
        initializeBuildMode();
        if(GameContext.get().getBuildModeUI().getStage() != uiStage) {
            uiStage.addActor(GameContext.get().getBuildModeUI());
        }
        GameContext.get().getBuildModeUI().setVisible(false);
        updatePartyDisplay();
        if (Gdx.app.getType() == Application.ApplicationType.Android) {
            if (movementController == null) {
                movementController = new AndroidMovementController(GameContext.get().getPlayer(), inputHandler);
            }
            if (!controlsInitialized) {
                initializeAndroidControls();
            }
        }
        if (GameContext.get().getPlayer() != null && GameContext.get().getPlayer().getPokemonParty().getSize() == 0) {
            if (starterTable == null) {
                handleNewPlayer();
            }
            inputManager.setUIState(InputManager.UIState.STARTER_SELECTION);
        } else {
            inputManager.setUIState(InputManager.UIState.NORMAL);
        }
        inputManager.updateInputProcessors();
        GameLogger.info("GameScreen show() completed successfully.");
    }


    private void handleNewPlayer() {
        if (starterTable != null) {
            GameLogger.info("Starter table already exists; skipping creation.");
            return;
        }
        starterSelectionInProgress.set(true);
        Gdx.app.postRunnable(() -> {
            starterTable = StarterSelectionTable.getInstance(skin);
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
            starterTable.setTouchable(Touchable.enabled);
            GameContext.get().getUiStage().addActor(starterTable);
            Gdx.input.setInputProcessor(GameContext.get().getUiStage());
            GameContext.get().getUiStage().setKeyboardFocus(starterTable);
            inputManager.setUIState(InputManager.UIState.STARTER_SELECTION);
            inputManager.updateInputProcessors();
            GameLogger.info("Starter selection UI initialized.");
        });
    }

    private void handleStarterSelection(Pokemon starter) {
        try {
            GameLogger.info("Processing starter selection: " + starter.getName());
            GameContext.get().getPlayer().getPokemonParty().addPokemon(starter);
            GameContext.get().getPlayer().updatePlayerData();
            if (!GameContext.get().getGameClient().isSinglePlayer()) {
                GameContext.get().getGameClient().savePlayerState(GameContext.get().getPlayer().getPlayerData());
            }
            if (starterTable != null) {
                starterTable.remove();
                starterTable = null;
            }
            inputBlocked = false;
            inputManager.setUIState(InputManager.UIState.NORMAL);
            inputManager.updateInputProcessors();
            if (GameContext.get().getUiStage() != null) {
                GameContext.get().getUiStage().setKeyboardFocus(null);
                GameContext.get().getUiStage().unfocusAll();
            }
            completeInitialization();
            GameLogger.info("Starter selection complete - proceeding with initialization");
        } catch (Exception e) {
            GameLogger.error("Failed to process starter selection: " + e.getMessage() + e);
        }
    }

    private void createStarterSelectionTable() {
        if (starterTable != null) {
            starterTable.remove();
            starterTable = null;
        }
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
        Stage uiStage = GameContext.get().getUiStage();
        if (uiStage != null) {
            uiStage.addActor(starterTable);
            starterTable.toFront();
        }
        inputManager.setUIState(InputManager.UIState.STARTER_SELECTION);
        inputManager.updateInputProcessors();

        GameLogger.info("StarterSelectionTable created and added to UI stage.");
    }

    private void registerAllCommands() {
        GameLogger.info("Registering commands...");
        commandManager.registerCommand(new GiveCommand());
        commandManager.registerCommand(new SpawnCommand());
        commandManager.registerCommand(new SetWorldSpawnCommand());
        commandManager.registerCommand(new TeleportPositionCommand());
        commandManager.registerCommand(new TimeCommand());
        commandManager.registerCommand(new WeatherCommand());
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
        return GameContext.get().getGameClient();
    }

    private void initializeBasicResources() {
        GameLogger.info("Initializing basic resources");

        try {
            GameContext.get().setBatch(new SpriteBatch());
            SpriteBatch uiBatch = new SpriteBatch();
            this.shapeRenderer = new ShapeRenderer();
            this.camera = new OrthographicCamera();
            float baseWidth = TARGET_VIEWPORT_WIDTH_TILES * TILE_SIZE;
            float baseHeight = baseWidth * ((float) Gdx.graphics.getHeight() / Gdx.graphics.getWidth());
            this.cameraViewport = new FitViewport(baseWidth, baseHeight, camera);
            camera.position.set(baseWidth / 2f, baseHeight / 2f, 0); // Set initial position
            camera.update();
            cameraViewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
            this.skin = new Skin(Gdx.files.internal("Skins/uiskin.json"));
            GameContext.get().setSkin(this.skin);
            this.uiSkin = this.skin;
            this.font = new BitmapFont(Gdx.files.internal("Skins/default.fnt"));
            GameContext.get().setUiStage(new Stage(new ScreenViewport(), uiBatch));
            this.pokemonPartyStage = new Stage(new ScreenViewport());
            this.stage = new Stage(new ScreenViewport());
            this.battleStage = new Stage(new FitViewport(BATTLE_SCREEN_WIDTH, BATTLE_SCREEN_HEIGHT));

            Stage uiStage = new Stage(new ScreenViewport(), new SpriteBatch());
            GameContext.get().setUiStage(uiStage);
            UIManager uiManager = new UIManager(uiStage, skin);
            GameContext.get().setUiManager(uiManager);
            GameContext.get().setSkin(skin);
            GameLogger.info("Basic resources initialized successfully");
        } catch (Exception e) {
            GameLogger.error("Failed to initialize basic resources: " + e.getMessage());
            throw new RuntimeException("Failed to initialize basic resources", e);
        }
    }

    private void initializeChatSystem() {
        if (GameContext.get().getChatSystem() != null) {
            return; // Already initialized
        }

        float screenW = Gdx.graphics.getWidth();
        float screenH = Gdx.graphics.getHeight();
        float chatWidth = Math.max(ChatSystem.MIN_CHAT_WIDTH, screenW * 0.25f);
        float chatHeight = Math.max(ChatSystem.MIN_CHAT_HEIGHT, screenH * 0.3f);

        ChatSystem chatSystem = new ChatSystem(
            GameContext.get().getUiStage(),
            skin,
            GameContext.get().getGameClient(),
            username,
            commandManager,
            commandsEnabled
        );
        chatSystem.setSize(chatWidth, chatHeight);
        chatSystem.setPosition(
            ChatSystem.CHAT_PADDING,
            screenH - chatHeight - ChatSystem.CHAT_PADDING
        );
        chatSystem.setVisible(true);
        chatSystem.setTouchable(Touchable.enabled);

        GameContext.get().getUiStage().addActor(chatSystem);
        GameContext.get().setChatSystem(chatSystem);
        GameLogger.info("ChatSystem created at: " + chatSystem.getX() + ", " + chatSystem.getY());
    }

    private void initializeWorldAndPlayer(String worldName) {
        GameLogger.info("Initializing world and player");

        try {
            if (!isMultiplayer && GameContext.get().getGameClient() == null) {
                GameContext.get().setGameClient(GameClientSingleton.getSinglePlayerInstance());
                GameLogger.info("Reinitialized singleplayer GameClient");
            }
            if (GameContext.get().getWorld() == null) {
                if (isMultiplayer) {
                    GameContext.get().setWorld(GameContext.get().getGameClient().getCurrentWorld());
                    if (GameContext.get().getWorld() == null) {
                        throw new IllegalStateException("No world available from GameClient");
                    }
                } else {
                    long seed = System.currentTimeMillis();
                    GameContext.get().getBiomeManager().setBaseSeed(seed);
                    GameContext.get().setWorld(new World(worldName, seed));
                }
            }
            if (isMultiplayer) {
                GameContext.get().setPlayer(GameContext.get().getGameClient().getActivePlayer());
                if (GameContext.get().getPlayer() == null) {
                    throw new IllegalStateException("No player available from GameClient");
                }
            } else {
                GameContext.get().setPlayer(game.getPlayer());
                if (GameContext.get().getPlayer() == null) {
                    World currentWorld = GameContext.get().getWorld();
                    BiomeManager bm = currentWorld.getBiomeManager();
                    Random rng = new Random(currentWorld.getWorldData().getConfig().getSeed());
                    Vector2 safeTile = bm.findSafeSpawnLocation(currentWorld, rng);

                    Player newPlayer = new Player(
                        (int) safeTile.x,
                        (int) safeTile.y,
                        currentWorld,
                        username
                    );
                    GameContext.get().setPlayer(newPlayer);
                }
            }
            if (isMultiplayer) {
                GameContext.get().getWorld().initializeFromServer(
                    GameContext.get().getGameClient().getWorldSeed(),
                    GameContext.get().getWorld().getWorldData().getWorldTimeInMinutes(),
                    GameContext.get().getWorld().getWorldData().getDayLength()
                );
            }
            GameContext.get().getPlayer().initializeResources();
            GameContext.get().getPlayer().initializeInWorld(GameContext.get().getWorld());
            GameContext.get().getWorld().setPlayer(GameContext.get().getPlayer());
            float px = GameContext.get().getPlayer().getX();
            float py = GameContext.get().getPlayer().getY();
            GameContext.get().getPlayer().setRenderPosition(new Vector2(px, py));
            Vector2 playerPos = new Vector2(
                (float) GameContext.get().getPlayer().getTileX() / World.CHUNK_SIZE,
                (float) GameContext.get().getPlayer().getTileY() / World.CHUNK_SIZE
            );
            GameContext.get().getWorld().loadChunksAroundPositionSynchronously(playerPos, INITIAL_LOAD_RADIUS);

            if (!GameContext.get().getWorld().areAllChunksLoaded()) {
                GameLogger.info("Forcing load of missing chunks...");
                GameContext.get().getWorld().forceLoadMissingChunks();
            }
            if (camera != null) {
                camera.position.set(
                    GameContext.get().getPlayer().getX() + Player.FRAME_WIDTH / 2f,
                    GameContext.get().getPlayer().getY() + Player.FRAME_HEIGHT / 2f,
                    0
                );
                camera.update();
            }

        } catch (Exception e) {
            GameLogger.error("Failed to initialize world and player: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    private void initializeGameSystems() {
        setupCamera();
        if (GameContext.get().getChatSystem() == null) {
            initializeChatSystem();
        }
        this.chestHandler = new ChestInteractionHandler();
        this.inputHandler = new InputHandler(this, this, this, chestHandler, inputManager);

        createPartyDisplay();
        initializeBattleAssets();

        if (Gdx.app.getType() == Application.ApplicationType.Android) {
            this.movementController = new AndroidMovementController(GameContext.get().getPlayer(), inputHandler);
            initializeAndroidControls();
        }

        GameLogger.info("Game systems initialized successfully");
    }

    private void initializeBattleAssets() {
        try {


            try {
                FileHandle skinFile = Gdx.files.internal("atlas/ui-gfx-atlas.json");
                if (skinFile.exists()) {
                    battleSkin = new Skin(skinFile);
                    battleSkin.addRegions(TextureManager.getUi());
                    GameLogger.info("Battle skin loaded successfully");
                } else {
                    GameLogger.info("No battle skin found - using default styles");
                }
            } catch (Exception skinEx) {
                GameLogger.error("Could not load battle skin: " + skinEx.getMessage() + " - continuing without skin");
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

    private void handleInitializationFailure() {
        Gdx.app.postRunnable(() -> {
            Dialog dialog = new Dialog("Initialization Error", skin) {
                @Override
                protected void result(Object obj) {
                    if ((Boolean) obj) {
                        handleMultiplayerInitialization(true);
                    } else {
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

    public void toggleInventory() {
        if (inputManager.getCurrentState() == InputManager.UIState.INVENTORY) {
            inputManager.setUIState(InputManager.UIState.NORMAL);
            if (GameContext.get().getInventoryScreen() != null) {
                GameContext.get().getInventoryScreen().hide();
            }
        } else {
            inputManager.setUIState(InputManager.UIState.INVENTORY);
            if (GameContext.get().getInventoryScreen() == null) {
                GameContext.get().setInventoryScreen(new InventoryScreen(GameContext.get().getPlayer(), skin, GameContext.get().getPlayer().getInventory(), inputManager));
            }
            GameContext.get().getInventoryScreen().show();
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
        battleSystem.lockPokemonForBattle(nearestPokemon);

        battleStage = new Stage(new FitViewport(800, 480));
        battleStage.getViewport().update(
            Gdx.graphics.getWidth(),
            Gdx.graphics.getHeight(),
            true
        );

        battleTable = new BattleTable(
            battleStage,
            battleSkin,
            validPokemon,
            nearestPokemon
        );
        GameContext.get().setBattleTable(battleTable);

        battleTable.setFillParent(true);
        battleTable.setVisible(true);
        battleStage.addActor(battleTable);

        setupBattleCallbacks(nearestPokemon);
        battleInitialized = true;
    }


    private void initiateStarterSelection() {
        GameLogger.info("CRITICAL - Initiating starter selection");
        starterSelectionInProgress.set(true);

        try {

            if (GameContext.get().getUiStage() == null) {
                GameContext.get().setUiStage(new Stage(new ScreenViewport()));
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
                GameContext.get().getUiStage().addActor(starterTable);
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
            if (starterTable != null) {
                starterTable.remove();
                starterTable = null;
            }
            Dialog dialog = new Dialog("Error", skin) {
                @Override
                protected void result(Object obj) {
                    if ((Boolean) obj) {
                        initiateStarterSelection();
                    } else {
                        game.setScreen(new LoginScreen(game));
                    }
                }
            };

            dialog.text("Failed to process starter selection.\nWould you like to try again?");
            dialog.button("Retry", true);
            dialog.button("Back to Login", false);
            dialog.show(GameContext.get().getUiStage());
        });
    }

    private void createPartyDisplay() {
        partyDisplay = new Table();
        partyDisplay.setFillParent(true);
        partyDisplay.top();  // Position at top
        partyDisplay.padTop(20f); // Add top padding

        Table slotsTable = new Table();
        slotsTable.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("hotbar_bg")));
        slotsTable.pad(4f);

        List<Pokemon> party = GameContext.get().getPlayer().getPokemonParty().getParty();

        for (int i = 0; i < PokemonParty.MAX_PARTY_SIZE; i++) {
            boolean isSelected = (i == 0);
            Pokemon pokemon = (party.size() > i) ? party.get(i) : null;
            PokemonPartySlot slot = new PokemonPartySlot(pokemon, isSelected, skin);
            slotsTable.add(slot).size(64).pad(2);
        }

        partyDisplay.add(slotsTable);
        GameContext.get().getUiStage().addActor(partyDisplay);
    }

    @Override
    public void hide() {

    }

    public World getWorld() {
        return GameContext.get().getWorld();
    }

    public Player getPlayer() {
        return GameContext.get().getPlayer();
    }

    public PlayerData getCurrentPlayerState() {
        PlayerData currentState = new PlayerData(GameContext.get().getPlayer().getUsername());
        InventoryConverter.extractInventoryDataFromPlayer(GameContext.get().getPlayer(), currentState);
        return currentState;
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
            if (houseToggleButton != null) {
                float houseX = joystickCenter.x - ACTION_BUTTON_SIZE / 2;
                float houseY = joystickCenter.y + DPAD_SIZE + padding;
                houseToggleButton.setPosition(houseX, houseY);
                houseToggleButton.setVisible(GameContext.get().getPlayer().isBuildMode());
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
        if (inBattle) {
            return false;
        }

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

        WildPokemon nearestPokemon = GameContext.get().getWorld().getNearestInteractablePokemon(GameContext.get().getPlayer());
        if (nearestPokemon == null || nearestPokemon.isAddedToParty()) {
            return;
        }
        if (GameContext.get().getPlayer().getPokemonParty() == null || GameContext.get().getPlayer().getPokemonParty().getSize() == 0) {
            if (GameContext.get().getChatSystem() != null) {
                NetworkProtocol.ChatMessage message = createSystemMessage(
                    "You need a Pokemon to battle!");
                GameContext.get().getChatSystem().handleIncomingMessage(message);
            }
            GameLogger.info("Cannot battle - player has no Pokemon");
            return;
        }

        Pokemon validPokemon = findFirstValidPokemon(GameContext.get().getPlayer().getPokemonParty());
        if (validPokemon == null) {
            if (GameContext.get().getChatSystem() != null) {
                NetworkProtocol.ChatMessage message = createSystemMessage(
                    "All your Pokemon need to be healed!");
                GameContext.get().getChatSystem().handleIncomingMessage(message);
            }
            GameLogger.info("Cannot battle - no healthy Pokemon");
            return;
        }
        if (GameContext.get().getPlayer().getAnimations().isChopping() ||
            GameContext.get().getPlayer().getAnimations().isPunching()) {
            GameContext.get().getPlayer().getAnimations().stopChopping();
            GameContext.get().getPlayer().getAnimations().stopPunching();
            if (inputHandler != null) {
                inputHandler.stopChopOrPunch();
            }
        }
        try {
            initializeBattleComponents(validPokemon, nearestPokemon);

            inputManager.setUIState(InputManager.UIState.BATTLE);

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


    public void forceBattleInitiation(WildPokemon aggressor) {
        if (!canInteract() || battleSystem.isInBattle()) {
            GameLogger.info("Cannot start forced battle - player is busy or already in battle");
            return;
        }
        if (aggressor == null || aggressor.isAddedToParty()) {
            return;
        }
        Pokemon validPokemon = findFirstValidPokemon(GameContext.get().getPlayer().getPokemonParty());
        if (validPokemon == null) {
            return;
        }
        if (GameContext.get().getPlayer().getAnimations().isChopping() ||
            GameContext.get().getPlayer().getAnimations().isPunching()) {
            GameContext.get().getPlayer().getAnimations().stopChopping();
            GameContext.get().getPlayer().getAnimations().stopPunching();
            if (inputHandler != null) {
                inputHandler.stopChopOrPunch();
            }
        }

        try {
            initializeBattleComponents(validPokemon, aggressor); // Use aggressor here

            inputManager.setUIState(InputManager.UIState.BATTLE);

            battleSystem.startBattle();
            inBattle = true;

            GameLogger.info("Forced battle initiated by " + aggressor.getName() + " with " + validPokemon.getName());

        } catch (Exception e) {
            GameLogger.error("Failed to initialize forced battle: " + e.getMessage());
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
            public void onBattleEnd(BattleTable.BattleOutcome outcome) {
                switch (outcome) {
                    case WIN:
                        handleBattleVictory(wildPokemon);
                        Pokemon victoriousPokemon = GameContext.get().getPlayer().getPokemonParty().getFirstHealthyPokemon();
                        if (victoriousPokemon != null) {
                            NetworkProtocol.ChatMessage message = createSystemMessage(
                                "Victory! " + victoriousPokemon.getName() +
                                    " defeated " + wildPokemon.getName() + "!"
                            );
                            GameContext.get().getChatSystem().handleIncomingMessage(message);
                        }
                        break;
                    case LOSS:
                        handleBattleDefeat();
                        if (GameContext.get().getChatSystem() != null) {
                            NetworkProtocol.ChatMessage message = createSystemMessage(
                                GameContext.get().getPlayer().getUsername() + " has no more usable Pokémon and blacks out!"
                            );
                            GameContext.get().getChatSystem().handleIncomingMessage(message);
                        }
                        break;
                    case ESCAPE:
                        GameLogger.info("Player escaped from battle.");
                        if (GameContext.get().getChatSystem() != null) {
                            GameContext.get().getChatSystem().handleIncomingMessage(createSystemMessage(
                                "Got away safely!"
                            ));
                        }
                        break;
                }
                wildPokemon.startDespawnAnimation();
                Timer.schedule(new Timer.Task() {
                    @Override
                    public void run() {
                        if (GameContext.get().getWorld() != null && GameContext.get().getWorld().getPokemonSpawnManager() != null) {
                            GameContext.get().getWorld().getPokemonSpawnManager().removePokemon(wildPokemon.getUuid());
                        }
                    }
                }, 1.5f);
                battleSystem.endBattle();
                endBattle(outcome == BattleTable.BattleOutcome.WIN, wildPokemon);
            }

            @Override
            public void onTurnEnd(Pokemon activePokemon) {
                GameLogger.info("Turn ended for: " + activePokemon.getName());
            }

            @Override
            public void onStatusChange(Pokemon pokemon, Pokemon.Status newStatus) {
                GameLogger.info("Status changed for " + pokemon.getName() + ": " + newStatus);
                String statusMessage = pokemon.getName() + " is now " + newStatus.toString().toLowerCase() + "!";
                if (GameContext.get().getChatSystem() != null) {
                    GameContext.get().getChatSystem().handleIncomingMessage(createSystemMessage(statusMessage));
                }

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

            @Override
            public void onMoveUsed(Pokemon user, Move move, Pokemon target) {
                GameLogger.info(user.getName() + " used " + move.getName());
                AudioManager.getInstance().playSound(AudioManager.SoundEffect.MOVE_SELECT);
                String moveMessage = user.getName() + " used " + move.getName() + "!";
                if (GameContext.get().getChatSystem() != null) {
                    GameContext.get().getChatSystem().handleIncomingMessage(createSystemMessage(moveMessage));
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
                        cleanup();
                    } catch (Exception e) {
                        GameLogger.error("Error ending battle: " + e.getMessage());
                        cleanup();
                    }
                })
            ));
            battleUIFading = true;
        } else {
            cleanup();
        }
    }

    private void cleanup() {
        if (isDisposing) return;
        isDisposing = true;

        Gdx.app.postRunnable(() -> {
            try {
                if (battleTable != null) {
                    battleTable.clear();
                    if (battleStage != null) {
                        battleTable.remove();
                    }
                }

                if (battleStage != null) {
                    battleStage.clear();
                    battleStage.dispose();
                    battleStage = null;
                }

                if (battleSystem != null) {
                    battleSystem.endBattle();
                }
                inBattle = false;
                transitioning = false;
                inputBlocked = false;
                battleInitialized = false;
                battleUIFading = false;
                isDisposing = false;
                inputManager.setUIState(InputManager.UIState.NORMAL);
                inputManager.updateInputProcessors();

                GameLogger.info("Battle cleanup complete - battle Stage disposed and UI state reset");
            } catch (Exception e) {
                GameLogger.error("Error during battle cleanup: " + e.getMessage());
            }
        });
    }

    private void handleBattleVictory(WildPokemon wildPokemon) {
        int expGain = calculateExperienceGain(wildPokemon);
        GameContext.get().getPlayer().getPokemonParty().getFirstPokemon().addExperience(expGain);
        AudioManager.getInstance().playSound(AudioManager.SoundEffect.BATTLE_WIN);
        if (GameContext.get().getChatSystem() != null) {
            GameContext.get().getChatSystem().handleIncomingMessage(createSystemMessage(
                "Victory! " + GameContext.get().getPlayer().getPokemonParty().getFirstPokemon().getName() +
                    " gained " + expGain + " experience!"
            ));
        }
    }

    private void teleportPlayerToSpawn(Player player, World world) {
        try {
            GameLogger.info("Teleporting player to spawn...");
            if (player == null || world == null) {
                GameLogger.error("Cannot teleport: player or world is null.");
                return;
            }
            int tileX = world.getWorldData().getConfig().getTileSpawnX();
            int tileY = world.getWorldData().getConfig().getTileSpawnY();
            float pixelX = tileX * World.TILE_SIZE;
            float pixelY = tileY * World.TILE_SIZE;
            player.getPosition().set(pixelX, pixelY);
            player.setTileX(tileX);
            player.setTileY(tileY);
            player.setX(pixelX);
            player.setY(pixelY);
            player.setMoving(false);
            player.setRenderPosition(new Vector2(pixelX, pixelY));

            GameLogger.info("Player teleported to spawn: (" + pixelX + ", " + pixelY + ")");
            world.clearChunks();
            world.loadChunksAroundPlayer();
            if (GameContext.get().isMultiplayer()) {
                GameContext.get().getGameClient().sendPlayerUpdate();
                GameContext.get().getGameClient().savePlayerState(player.getPlayerData());
            }
        } catch (Exception e) {
            GameLogger.error("Teleport to spawn failed: " + e.getMessage());
        }
    }
    private void handleBattleDefeat() {
        Player player = GameContext.get().getPlayer();
        World world = GameContext.get().getWorld();

        if (player == null || world == null) {
            GameLogger.error("Cannot handle battle defeat: Player or World is null.");
            return;
        }
        float dropX = player.getX();
        float dropY = player.getY();
        List<ItemData> itemsToDrop = new ArrayList<>();
        for (ItemData item : player.getInventory().getAllItems()) {
            if (item != null) {
                itemsToDrop.add(item.copy());
            }
        }
        player.getInventory().clear();
        if (GameContext.get().getHotbarSystem() != null) {
            GameContext.get().getHotbarSystem().updateHotbar(); // Visually update the hotbar
        }
        Random rand = new Random();
        for (ItemData item : itemsToDrop) {
            float offsetX = (rand.nextFloat() - 0.5f) * 64; // Scatter within one tile
            float offsetY = (rand.nextFloat() - 0.5f) * 64;
            world.getItemEntityManager().spawnItemEntity(item, dropX + offsetX, dropY + offsetY);
        }
        player.getPokemonParty().healAllPokemon();
        AudioManager.getInstance().playSound(AudioManager.SoundEffect.DAMAGE);
        if (GameContext.get().getChatSystem() != null) {
            GameContext.get().getChatSystem().handleIncomingMessage(createSystemMessage(
                player.getUsername() + " blacked out, dropped all items, and was teleported to spawn!"
            ));
        }
        teleportPlayerToSpawn(player, world);
    }
    private int calculateExperienceGain(WildPokemon wildPokemon) {
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

        if (GameContext.get().getPlayer() != null) {
            camera.position.set(
                GameContext.get().getPlayer().getX() + Player.FRAME_WIDTH / 2f,
                GameContext.get().getPlayer().getY() + Player.FRAME_HEIGHT / 2f,
                0
            );
        } else {
            camera.position.set(baseWidth / 2f, baseHeight / 2f, 0);
        }

        camera.update();
        GameLogger.info("Camera setup - viewport: " + baseWidth + "x" + baseHeight);
    }

    private void updateCamera() {
        if (GameContext.get().getPlayer() != null) {
            float targetX = GameContext.get().getPlayer().getX() + Player.FRAME_WIDTH / 2f;
            float targetY = GameContext.get().getPlayer().getY() + Player.FRAME_HEIGHT / 2f;
            float lerp = CAMERA_LERP * Gdx.graphics.getDeltaTime();
            camera.position.x += (targetX - camera.position.x) * lerp;
            camera.position.y += (targetY - camera.position.y) * lerp;

            camera.update();
        }
    }

    private void renderLoadingScreen() {
        GameContext.get().getBatch().begin();
        font.draw(GameContext.get().getBatch(), "Loading world...",
            (float) Gdx.graphics.getWidth() / 2 - 50,
            (float) Gdx.graphics.getHeight() / 2);
        GameContext.get().getBatch().end();
    }

    @Override
    public void render(float delta) {

        if (GameContext.get().getGameClient() != null && GameContext.get().getGameClient().isConnected()) {
            GameContext.get().getGameClient().tick();
            GameContext.get().getGameClient().update(delta);
        } if (!isMultiplayer) {
            autoSaveTimer += delta;
            if (autoSaveTimer >= AUTO_SAVE_INTERVAL_SECONDS) {
                autoSaveTimer = 0f;
                if (GameContext.get().getWorld() != null) {
                    GameLogger.info("Performing periodic auto-save...");
                    GameContext.get().getWorld().save();
                }
            }
        }
        if (DEBUG_MODE && glProfiler != null) {
            glProfiler.reset();
        }
        if (DEBUG_MODE && glProfiler != null) {
            int draws = glProfiler.getDrawCalls();
            if (draws > 200) {
                Gdx.app.log("GLProfiler", "Draw Calls: " + draws);
            }
        }
        if (camera != null && starterTable == null) {
            updateCamera();
        }
        if (GameContext.get().getPlayer() != null && GameContext.get().getPlayer().getPokemonParty().getSize() == 0) {
            GameContext.get().getUiStage().act(delta);
            GameContext.get().getUiStage().draw();
            return;
        }
        if (!initialChunksLoadedOnce) {
            renderLoadingOverlay();
        }

        if (GameContext.get().getWorld() == null) {
            return;
        }
        if (GameContext.get().getPlayer() != null) {
            if (!GameContext.get().getWorld().areInitialChunksLoaded()) {
                long elapsed = System.currentTimeMillis() - GameContext.get().getWorld().getInitialChunkRequestTime();
                if (elapsed > 5000) {
                    int playerTileX = GameContext.get().getPlayer().getTileX();
                    int playerTileY = GameContext.get().getPlayer().getTileY();
                    int playerChunkX = Math.floorDiv(playerTileX, World.CHUNK_SIZE);
                    int playerChunkY = Math.floorDiv(playerTileY, World.CHUNK_SIZE);
                } else {
                    GameContext.get().getWorld().requestInitialChunks();
                    renderLoadingScreen();
                    return;
                }
            }
        }
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        if (GameContext.get().getPlayer() != null && GameContext.get().getPlayer().getPokemonParty().getSize() == 0) {
            Gdx.gl.glClearColor(0.1f, 0.1f, 0.2f, 1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

            GameContext.get().getUiStage().act(delta);
            GameContext.get().getUiStage().draw();

            debugTimer += delta;
            if (debugTimer >= 1.0f) {
                debugTimer = 0;
            }
            return;
        }
        GameContext.get().getBatch().begin();
        GameContext.get().getBatch().setProjectionMatrix(camera.combined);

        if (GameContext.get().getWorld() != null && GameContext.get().getPlayer() != null) {
            Rectangle viewBounds = new Rectangle(
                camera.position.x - (camera.viewportWidth * camera.zoom) / 2,
                camera.position.y - (camera.viewportHeight * camera.zoom) / 2,
                camera.viewportWidth * camera.zoom,
                camera.viewportHeight * camera.zoom
            );
            GameContext.get().getBatch().setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

            GameContext.get().getWorld().render(GameContext.get().getBatch(), viewBounds, GameContext.get().getPlayer(), this);


        }
        if (SHOW_DEBUG_INFO) {
            renderDebugInfo();

        }
        if (inputManager.getCurrentState() == InputManager.UIState.CRAFTING) {
            if (GameContext.get().getCraftingScreen() != null) {
                GameContext.get().getBatch().end();

                GameContext.get().getCraftingScreen().render(delta);

                GameContext.get().getBatch().begin();
            }
        }


        GameContext.get().getBatch().end();

        if (inputManager.getCurrentState() == InputManager.UIState.BUILD_MODE && GameContext.get().getBuildModeUI() != null) {
            GameContext.get().getBuildModeUI().renderPlacementPreview(GameContext.get().getBatch(), camera);
        }
        if (GameContext.get().getWorld() != null && !initializedworld) {
            if (GameContext.get().getWorld().areAllChunksLoaded()) {
                initializedworld = true;
                GameLogger.info("All chunks successfully loaded");
            }
        }

        if (isHoldingDirection && currentDpadDirection != null) {
            movementTimer += delta;
            if (movementTimer >= MOVEMENT_REPEAT_DELAY) {
                GameContext.get().getPlayer().move(currentDpadDirection);
                movementTimer = 0f;
            }
            GameContext.get().getPlayer().setRunning(isRunPressed);
        }
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        if (GameContext.get().getUiStage() != null) {
            GameContext.get().getUiStage().getViewport().apply();
            GameContext.get().getUiStage().act(delta);
            GameContext.get().getUiStage().draw();
        }


        if (Gdx.input.isKeyPressed(Input.Keys.TAB)) {
            renderPlayerListOverlay();
        }
        if (inBattle) {
            if (battleStage != null && !isDisposing) {
                battleStage.act(delta);
                if (battleTable != null && battleTable.hasParent()) {
                    battleStage.draw();
                }
            }
        }


        if (inputManager.getCurrentState() == InputManager.UIState.MENU) {
            if (GameContext.get().getGameMenu() != null) {
                GameContext.get().getGameMenu().render();
            }
        }
        if (inputManager.getCurrentState() == InputManager.UIState.INVENTORY) {
            if (GameContext.get().getInventoryScreen() != null) {
                Gdx.gl.glEnable(GL20.GL_BLEND);
                Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
                shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
                shapeRenderer.setColor(0, 0, 0, 0.7f);
                shapeRenderer.rect(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
                shapeRenderer.end();
                GameContext.get().getInventoryScreen().render(delta);

                Gdx.gl.glDisable(GL20.GL_BLEND);
            }
        }


        if (chestScreen != null && chestScreen.isVisible()) {
            chestScreen.render(delta);
        }
        if (Gdx.app.getType() == Application.ApplicationType.Android && controlsInitialized) {
            ensureAndroidControlsInitialized();
            renderAndroidControls();
        }

        Gdx.gl.glDisable(GL20.GL_BLEND);
        if (GameContext.get().getWorld() != null && GameContext.get().getPlayer() != null) {
            float deltaTime = Gdx.graphics.getDeltaTime();
            if (!inBattle && !transitioning) {
                updateCamera();
            }

            if (isMultiplayer) {
                updateOtherPlayers(delta);
                if (GameContext.get().getWorld() != null) {
                    GameContext.get().getWorld().update(delta, new Vector2(GameContext.get().getPlayer().getTileX(), GameContext.get().getPlayer().getTileY()),
                        Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), this);
                }
            } else {
                GameContext.get().getPlayer().validateResources();
                float viewportWidthPixels = camera.viewportWidth * camera.zoom;
                float viewportHeightPixels = camera.viewportHeight * camera.zoom;
                GameContext.get().getWorld().update(delta,
                    new Vector2(GameContext.get().getPlayer().getTileX(), GameContext.get().getPlayer().getTileY()),
                    viewportWidthPixels,
                    viewportHeightPixels, this
                );
            }

            handleInput();
            if (inputHandler != null) {
                inputHandler.update(delta);
            }
            if (GameContext.get().getPlayer() != null) {
                GameContext.get().getPlayer().update(delta);
            }

            updateTimer += delta;

            if (isMultiplayer && updateTimer >= UPDATE_INTERVAL) {
                updateTimer = 0;
                NetworkProtocol.PlayerUpdate update = new NetworkProtocol.PlayerUpdate();
                update.username = GameContext.get().getPlayer().getUsername();
                update.x = GameContext.get().getPlayer().getX();
                update.y = GameContext.get().getPlayer().getY();
                update.characterType = GameContext.get().getPlayer().getCharacterType();

                update.direction = GameContext.get().getPlayer().getDirection();
                update.isMoving = GameContext.get().getPlayer().isMoving();
                update.wantsToRun = GameContext.get().getPlayer().isRunning();
                update.inventoryItems = GameContext.get().getPlayer().getInventory().getAllItems().toArray(new ItemData[0]);
                update.timestamp = System.currentTimeMillis();
                if (GameContext.get().getGameClient() == null) {
                    return;
                }
                GameContext.get().getGameClient().sendPlayerUpdate();
                if (GameContext.get().getGameClient() != null) {
                    Map<String, NetworkProtocol.PlayerUpdate> updates = GameContext.get().getGameClient().getPlayerUpdates();
                    if (!updates.isEmpty()) {
                        synchronized (GameContext.get().getGameClient().getOtherPlayers()) {
                            for (NetworkProtocol.PlayerUpdate playerUpdate : updates.values()) {
                                if (!playerUpdate.username.equals(GameContext.get().getPlayer().getUsername())) {
                                    OtherPlayer op = GameContext.get().getGameClient().getOtherPlayers().get(playerUpdate.username);
                                    if (op == null) {
                                        op = new OtherPlayer(playerUpdate.username, playerUpdate.x, playerUpdate.y);
                                        GameContext.get().getGameClient().getOtherPlayers().put(playerUpdate.username, op);
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
    }
    private void renderLoadingOverlay() {
        GameContext.get().getBatch().begin();
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0, 0, 0, 0.5f);
        shapeRenderer.rect(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        shapeRenderer.end();
        font.draw(GameContext.get().getBatch(), "Loading world...",
            Gdx.graphics.getWidth() * 0.5f - 50,
            Gdx.graphics.getHeight() * 0.5f);
        GameContext.get().getBatch().end();
    }


    private void renderPlayerListOverlay() {
        Table table = new Table();
        table.setFillParent(true);
        table.center();
        Skin skin = this.skin;
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(0, 0, 0, 0.6f); // 60% opaque black
        pixmap.fill();
        table.setBackground(new TextureRegionDrawable(new TextureRegion(new Texture(pixmap))));
        pixmap.dispose();
        Label header = new Label("Players Online", skin);
        table.add(header).colspan(2).padBottom(10);
        table.row();
        GameClient client = GameContext.get().getGameClient();
        if (client == null) return;
        Map<String, Integer> pingMap = client.getPlayerPingMap();
        String localName = client.getLocalUsername();
        int localPing = client.getLocalPing();
        Label localLabel = new Label(localName, skin);
        Label localPingLabel = new Label(localPing + " ms", skin);
        table.add(localLabel).pad(5);
        table.add(localPingLabel).pad(5);
        table.row();
        for (Map.Entry<String, Integer> entry : pingMap.entrySet()) {
            String username = entry.getKey();
            if (username.equals(localName)) continue;  // already added
            Label nameLabel = new Label(username, skin);
            Label pingLabel = new Label(entry.getValue() + " ms", skin);
            table.add(nameLabel).pad(5);
            table.add(pingLabel).pad(5);
            table.row();
        }
        Stage overlayStage = new Stage(new ScreenViewport());
        overlayStage.addActor(table);
        overlayStage.act();
        overlayStage.draw();
        overlayStage.dispose();
    }

    private void renderHotbar(float delta) {
        if (GameContext.get().getPlayer() != null &&
            GameContext.get().getPlayer().getHotbarSystem() != null) {
            GameContext.get().getPlayer().getHotbarSystem().updateHotbar();
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
            if (GameContext.get().getGameClient() == null) {
                return;
            }
            Map<String, OtherPlayer> others = GameContext.get().getGameClient().getOtherPlayers();
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
        GameContext.get().getBatch().setProjectionMatrix(GameContext.get().getUiStage().getCamera().combined);
        font.setColor(Color.WHITE);

        float debugY = 25;
        int fps = Gdx.graphics.getFramesPerSecond();
        font.draw(GameContext.get().getBatch(), "FPS: " + fps, 10, debugY);
        debugY += 20;

        float pixelX = GameContext.get().getPlayer().getX();
        float pixelY = GameContext.get().getPlayer().getY();
        int tileX = (int) Math.floor(pixelX / TILE_SIZE);
        int tileY = (int) Math.floor(pixelY / TILE_SIZE);
        Biome currentBiome = GameContext.get().getWorld().getBiomeAt(tileX, tileY);
        String biomeName = (currentBiome != null) ? currentBiome.getName() : "Unknown";
        font.draw(GameContext.get().getBatch(), String.format("Pixels: (%d, %d)", (int) pixelX, (int) pixelY), 10, debugY);
        debugY += 20;
        font.draw(GameContext.get().getBatch(), String.format("Tiles: (%d, %d)", tileX, tileY), 10, debugY);
        debugY += 20;
        font.draw(GameContext.get().getBatch(), "Direction: " + GameContext.get().getPlayer().getDirection(), 10, debugY);
        debugY += 20;
        font.draw(GameContext.get().getBatch(), "Biome: " + biomeName, 10, debugY); // Use the fetched name
        debugY += 20;

        font.draw(GameContext.get().getBatch(), "Active Pokemon: " + getTotalPokemonCount(), 10, debugY);
        debugY += 20;

        String timeString = DayNightCycle.getTimeString(GameContext.get().getWorld().getWorldData().getWorldTimeInMinutes());
        font.draw(GameContext.get().getBatch(), "Time: " + timeString, 10, debugY);
        debugY += 20;

        if (!GameContext.get().isMultiplayer()) {
            long playedTimeMillis = GameContext.get().getWorld().getWorldData().getPlayedTime();
            String playedTimeStr = formatPlayedTime(playedTimeMillis);
            font.draw(GameContext.get().getBatch(), "Total Time Played: " + playedTimeStr, 10, debugY);
        }
    }

    private void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.F3)) {
            SHOW_DEBUG_INFO = !SHOW_DEBUG_INFO;
        }
        if (GameContext.get().getChatSystem() != null && GameContext.get().getChatSystem().isActive()) {
            return;
        }
        if (inBattle) {
            return;
        }
        if (inputManager.getCurrentState() == InputManager.UIState.NORMAL) {
            handleGameInput();
        }
    }

    public ChestScreen getChestScreen() {
        return chestScreen;
    }

    public void setChestScreen(ChestScreen chestScreen) {
        this.chestScreen = chestScreen;
    }

    private void handleGameInput() {
        if (inBattle) {
            return;
        }
        if (inputBlocked) {
            return;
        }


    }

    public void initializeBuildMode() {
        if (GameContext.get().getBuildModeUI() == null) {
            BuildModeUI buildUI = new BuildModeUI(skin);
            GameContext.get().setBuildModeUI(buildUI);
            GameContext.get().getUiStage().addActor(buildUI);
            buildUI.setVisible(false); // Initially hidden
        }
    }

    private int getTotalPokemonCount() {
        if (GameContext.get().getWorld() != null && GameContext.get().getWorld().getPokemonSpawnManager() != null) {
            return GameContext.get().getWorld().getPokemonSpawnManager().getAllWildPokemon().size();
        }
        return 0;
    }

    @Override
    public void resize(int width, int height) {
        if (GameContext.get().getPlayer() != null) {
            GameContext.get().getPlayer().validateResources();
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


        if (dpadTable != null) {
            dpadTable.clear();
            createDpad();
        }

        cameraViewport.update(width, height, false);

        if (GameContext.get().getInventoryScreen() != null) {
            GameContext.get().getInventoryScreen().resize(width, height);

            if (closeButtonTable != null && closeButtonTable.getParent() != null) {
                closeButtonTable.invalidate();
            }
        }
        if (GameContext.get().getCraftingScreen() != null) {
            GameContext.get().getCraftingScreen().resize(width, height);
        }
        for (Actor actor : stage.getActors()) {
            if (actor instanceof StarterSelectionTable) {
                ((StarterSelectionTable) actor).resize(width, height);
                starterTable.resize(width, height);
                break;
            }
        }
        if (GameContext.get().getUiStage() != null) {
            GameContext.get().getUiStage().getViewport().update(width, height, true);
            GameLogger.info("Stage viewport updated to: " + width + "x" + height);

            if (starterTable != null && GameContext.get().getPlayer().getPokemonParty().getSize() == 0) {
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
        if (GameContext.get().getUiStage() != null) {
            GameContext.get().getUiStage().getViewport().update(width, height, true);
        }
        if (GameContext.get().getGameMenu() != null && GameContext.get().getGameMenu().getStage() != null) {
            GameContext.get().getGameMenu().getStage().getViewport().update(width, height, true);
            GameContext.get().getGameMenu().resize(width, height);
        }
        if (pokemonPartyStage != null) {
            pokemonPartyStage.getViewport().update(width, height, true);
        }
        if (GameContext.get().getChatSystem() != null) {
            float chatWidth = Math.max(ChatSystem.MIN_CHAT_WIDTH, width * 0.25f);
            float chatHeight = Math.max(ChatSystem.MIN_CHAT_HEIGHT, height * 0.3f);

            GameContext.get().getChatSystem().setSize(chatWidth, chatHeight);
            GameContext.get().getChatSystem().setPosition(
                ChatSystem.CHAT_PADDING,
                height - chatHeight - ChatSystem.CHAT_PADDING
            );
            GameContext.get().getChatSystem().resize(width, height);
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

    private NetworkProtocol.ChatMessage createPickupMessage(ItemData itemData) {
        NetworkProtocol.ChatMessage pickupMessage = new NetworkProtocol.ChatMessage();
        pickupMessage.sender = "System";
        pickupMessage.content = "You found: " + itemData.getItemId() + " (×" + itemData.getCount() + ")";
        pickupMessage.timestamp = System.currentTimeMillis();
        pickupMessage.type = NetworkProtocol.ChatType.SYSTEM;

        return pickupMessage;
    }

    public void handlePickupAction() {
        WorldObject nearestPokeball = GameContext.get().getWorld().getNearestPokeball();
        if (nearestPokeball == null) {
            return;
        }

        if (GameContext.get().getPlayer().canPickupItem(nearestPokeball.getPixelX(), nearestPokeball.getPixelY())) {
            GameContext.get().getWorld().removeWorldObject(nearestPokeball);

            ItemData randomItemData = generateRandomItemData();
            if (randomItemData == null) {
                return;
            }

            boolean added;
            try {
                added = InventoryConverter.addItemToInventory(GameContext.get().getPlayer().getInventory(), randomItemData);

                if (added) {
                    AudioManager.getInstance().playSound(AudioManager.SoundEffect.ITEM_PICKUP);

                    NetworkProtocol.ChatMessage pickupMessage = createPickupMessage(randomItemData);
                    if (GameContext.get().getChatSystem() != null) {
                        GameContext.get().getChatSystem().handleIncomingMessage(pickupMessage);
                    }

                    if (GameContext.get().getGameClient() != null && !GameContext.get().getGameClient().isSinglePlayer()) {
                        PlayerData currentState = GameContext.get().getPlayer().getPlayerData();
                        currentState.updateFromPlayer(GameContext.get().getPlayer());
                        GameContext.get().getGameClient().savePlayerState(currentState);
                    }

                } else {
                    if (GameContext.get().getChatSystem() != null) {
                        NetworkProtocol.ChatMessage message = createSystemMessage(
                            "Inventory full! Couldn't pick up: " + randomItemData.getItemId());
                        GameContext.get().getChatSystem().handleIncomingMessage(message);
                    }
                }

            } catch (Exception e) {
                GameLogger.error("Error handling item pickup: " + e.getMessage());
            }
        }
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    public SpriteBatch getBatch() {
        return GameContext.get().getBatch();
    }

    @Override
    public void dispose() {
        if (isDisposing) return;
        isDisposing = true;

        GameLogger.info("Disposing GameScreen...");
        if (shapeRenderer != null) {
            shapeRenderer.dispose();
            shapeRenderer = null;
        }

        if (battleStage != null) {
            battleStage.dispose();
            battleStage = null;
        }

        if (pokemonPartyStage != null) {
            pokemonPartyStage.dispose();
            pokemonPartyStage = null;
        }

        if (GameContext.get().getInventoryScreen() != null) {
            GameContext.get().getInventoryScreen().dispose();
            GameContext.get().setInventoryScreen(null);
        }

        if (GameContext.get().getCraftingScreen() != null) {
            GameContext.get().getCraftingScreen().dispose();
            GameContext.get().setCraftingScreen(null);
        }
        if (GameContext.get().getGameMenu() != null) {
            GameContext.get().getGameMenu().dispose();
            GameContext.get().setGameMenu(null);
        }

        GameLogger.info("GameScreen disposed.");
    }

    private void initializeAndroidControls() {
        if (Gdx.app.getType() != Application.ApplicationType.Android || controlsInitialized) {
            return;
        }

        try {
            float screenWidth = Gdx.graphics.getWidth();
            float screenHeight = Gdx.graphics.getHeight();

            ACTION_BUTTON_SIZE = screenHeight * 0.12f;
            DPAD_SIZE = screenHeight * 0.3f;
            BUTTON_PADDING = screenWidth * 0.02f;
            androidControlsTable = new Table();
            androidControlsTable.setFillParent(true);
            createDpad();
            createActionButtons();
            houseToggleButton = createColoredButton("House", Color.ORANGE, ACTION_BUTTON_SIZE);
            houseToggleButton.setVisible(false);
            houseToggleButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (GameContext.get().getPlayer().isBuildMode()) {
                        BuildModeUI buildUI = GameContext.get().getBuildModeUI();
                        if (buildUI != null) {
                            buildUI.toggleBuildingMode();
                        }
                    }
                }
            });
            GameContext.get().getUiStage().addActor(houseToggleButton);

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
        aButton = createColoredButton("A", Color.GREEN, ACTION_BUTTON_SIZE);
        xButton = createColoredButton("X", Color.BLUE, ACTION_BUTTON_SIZE);
        yButton = createColoredButton("Y", Color.YELLOW, ACTION_BUTTON_SIZE);
        zButton = createColoredButton("Z", Color.PURPLE, ACTION_BUTTON_SIZE);
        startButton = createColoredButton("Start", Color.WHITE, ACTION_BUTTON_SIZE);
        selectButton = createColoredButton("Select", Color.GRAY, ACTION_BUTTON_SIZE);
        aButton.setTouchable(Touchable.enabled);
        xButton.setTouchable(Touchable.enabled);
        yButton.setTouchable(Touchable.enabled);
        zButton.setTouchable(Touchable.enabled);
        startButton.setTouchable(Touchable.enabled);
        selectButton.setTouchable(Touchable.enabled);
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

        GameContext.get().getUiStage().addActor(actionButtonTable);

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
                inputHandler.startChopOrPunch();
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                inputHandler.stopChopOrPunch();
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

    public Actor getHouseToggleButton() {
        return houseToggleButton;
    }

    private void handleStartButtonPress() {
        toggleGameMenu();
    }

    private void handleSelectButtonPress() {

        SHOW_DEBUG_INFO = !SHOW_DEBUG_INFO;
    }

    private void createDpad() {

        float dpadSize = DPAD_SIZE;
        float paddingLeft = BUTTON_PADDING * 2;
        float paddingBottom = BUTTON_PADDING * 2;
        Image dpadTouchArea = new Image();
        dpadTouchArea.setSize(dpadSize, dpadSize);
        dpadTouchArea.setPosition(paddingLeft, paddingBottom);
        dpadTouchArea.setColor(1, 1, 1, 0);
        dpadTouchArea.setTouchable(Touchable.enabled);
        dpadTouchArea.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                float absoluteX = event.getStageX();
                float absoluteY = event.getStageY();
                if (movementController != null) {
                    movementController.handleTouchDown(absoluteX, absoluteY);
                } else {
                    GameLogger.error("movementController is null on touchDown!");
                }
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

        GameContext.get().getUiStage().addActor(dpadTouchArea);
    }

    private void createDpadHitboxes() {
        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();
        float dpadCenterX = screenWidth * 0.15f;
        float dpadCenterY = screenHeight * 0.2f;
        float buttonSize = 145f;
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
            Vector2 center = movementController.getJoystickCenter();
            float maxRadius = movementController.getMaxRadius();
            shapeRenderer.setColor(0.3f, 0.3f, 0.3f, 0.3f);
            shapeRenderer.circle(center.x, center.y, maxRadius);
            Vector2 current = movementController.getJoystickCurrent();
            float knobSize = maxRadius * 0.3f;
            shapeRenderer.setColor(0.0f, 0.0f, 0.0f, 0.4f);
            shapeRenderer.circle(current.x, current.y, knobSize + 2);
            float intensity = 0.5f + (movementController.getMagnitude() * 0.5f);
            shapeRenderer.setColor(1.0f, 1.0f, 1.0f, intensity);
            shapeRenderer.circle(current.x, current.y, knobSize);
        }

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private static class PokemonPartySlot extends Table {
        private final Pokemon pokemon;
        private final Label levelLabel;
        private final ProgressBar hpBar;

        public PokemonPartySlot(Pokemon pokemon, boolean isSelected, Skin skin) {
            this.pokemon = pokemon;
            TextureRegionDrawable slotBg = new TextureRegionDrawable(
                TextureManager.ui.findRegion(isSelected ? "slot_selected" : "slot_normal")
            );
            setBackground(slotBg);

            if (pokemon != null) {
                Image pokemonIcon = new Image(pokemon.getCurrentIconFrame(Gdx.graphics.getDeltaTime()));
                pokemonIcon.setScaling(Scaling.fit);
                add(pokemonIcon).size(40).padTop(4).row();
                levelLabel = new Label("Lv." + pokemon.getLevel(), skin);
                levelLabel.setFontScale(0.8f);
                add(levelLabel).padTop(2).row();
                hpBar = new ProgressBar(0, pokemon.getStats().getHp(), 1, false, skin);
                hpBar.setValue(pokemon.getCurrentHp());
                add(hpBar).width(40).height(4).padTop(2);
            } else {
                levelLabel = null;
                hpBar = null;
            }
        }

        @Override
        public void act(float delta) {
            super.act(delta);
            if (pokemon != null) {
                levelLabel.setText("Lv." + pokemon.getLevel());
                hpBar.setValue(pokemon.getCurrentHp());
            }
        }
    }
}
