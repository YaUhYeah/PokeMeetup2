package io.github.pokemeetup;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.TextureAtlasLoader;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.managers.BiomeManager;
import io.github.pokemeetup.managers.DisconnectionManager;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.client.GameClientSingleton;
import io.github.pokemeetup.multiplayer.server.GameStateHandler;
import io.github.pokemeetup.multiplayer.server.config.ServerConfigManager;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import io.github.pokemeetup.pokemon.data.PokemonDatabase;
import io.github.pokemeetup.screens.*;
import io.github.pokemeetup.screens.otherui.HotbarSystem;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.data.WorldData;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldManager;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;
import io.github.pokemeetup.utils.storage.DesktopFileSystem;
import io.github.pokemeetup.utils.storage.GameFileSystem;

import java.io.IOException;
import java.util.Map;

public class CreatureCaptureGame extends Game implements GameStateHandler {
    public static final String MULTIPLAYER_WORLD_NAME = "multiplayer_world";
    private GameClient gameClient;
    private BiomeManager biomeManager;
    private Player player;
    private World currentWorld;
    private AssetManager assetManager;

    public CreatureCaptureGame(boolean isAndroid) {
        if (
            !isAndroid) {
            GameFileSystem system = GameFileSystem.getInstance();
            DesktopFileSystem delegate = new DesktopFileSystem();
            system.setDelegate(delegate);
        }
    }

    public CreatureCaptureGame() {
    }

    public boolean isMultiplayerMode() {
        return GameContext.get().isMultiplayer();
    }

    @Override
    public void create() {
        GameLogger.error("Working directory: " + System.getProperty("user.dir"));


        assetManager = new AssetManager();
        queueAssets();
        GameLogger.info("Loading assets...");
        assetManager.finishLoading();
        initializeManagers();


        setScreen(new ModeSelectionScreen(this));

        Gdx.app.setLogLevel(Application.LOG_INFO);
        GameLogger.info("Game initialization complete");
    }

    public void saveAndDispose() {
        try {
            GameLogger.info("Starting game state save...");
            if (GameContext.get().isMultiplayer()) {
                return;
            }
            World world = GameContext.get().getWorld();
            if (world != null) {
                // Save all chunks
                for (Map.Entry<Vector2, Chunk> entry : world.getChunks().entrySet()) {
                    try {
                        Vector2 chunkPos = entry.getKey();
                        Chunk chunk = entry.getValue();
                        world.saveChunkData(chunkPos, chunk);
                    } catch (Exception e) {
                        GameLogger.error("Failed to save chunk: " + e.getMessage());
                    }
                }
                WorldData worldData = world.getWorldData();
                Player player = GameContext.get().getPlayer();
                if (player != null) {
                    worldData.savePlayerData(player.getUsername(), player.getPlayerData(), false);
                }
                GameContext.get().getWorldManager().saveWorld(worldData);
                // (Optionally: flush or check that the file exists here.)
                world.dispose();
                GameLogger.info("World disposed successfully");
            }
        } catch (Exception e) {
            GameLogger.error("Error during game state save: " + e.getMessage());
        }
    }
    public void reinitializeGame() {
        try {
            // Reinitialize managers, etc.
            GameContext.get().setWorldManager(WorldManager.getInstance());
            this.biomeManager = new BiomeManager(System.currentTimeMillis());
            GameContext.get().getWorldManager().init();

            // Reinitialize the GameClient for singleplayer if needed.
            if (GameContext.get().getGameClient() == null || !GameContext.get().isMultiplayer()) {
                GameContext.get().setGameClient(GameClientSingleton.getSinglePlayerInstance());
                GameLogger.info("Reinitialized singleplayer GameClient in reinitializeGame()");
            }

            // Dispose of the old UI stage
            Stage oldStage = GameContext.get().getUiStage();
            if (oldStage != null) {
                oldStage.clear();
                oldStage.dispose();
            }

            // Create a new UI stage and update input processor
            Stage newUiStage = new Stage(new ScreenViewport(), new SpriteBatch());
            GameContext.get().setUiStage(newUiStage);
            Gdx.input.setInputProcessor(newUiStage);

            // Ensure skin is available.
            Skin skin = GameContext.get().getSkin();
            if (skin == null) {
                skin = new Skin(Gdx.files.internal("Skins/uiskin.json"));
                GameContext.get().setSkin(skin);
            }

            // Reinitialize the player's hotbar system.
            if (GameContext.get().getPlayer() != null) {
                GameContext.get().getPlayer().setHotbarSystem(null);
                HotbarSystem newHotbar = new HotbarSystem(newUiStage, skin);
                GameContext.get().getPlayer().setHotbarSystem(newHotbar);
                GameLogger.info("Hotbar system reinitialized on the new UI stage");
            }

            // (Optionally update input processors in your InputManager)
            // GameContext.get().getGameScreen().getInputManager().updateInputProcessors();

            GameLogger.info("Game state reinitialized");
        } catch (Exception e) {
            GameLogger.error("Failed to reinitialize game: " + e.getMessage());
        }
    }



    public void shutdown() {
        try {
            saveAndDispose();

            Gdx.app.postRunnable(() -> {
                try {
                    if (assetManager != null) {
                        assetManager.dispose();
                        assetManager = null;
                    }

                    AudioManager audioManager = AudioManager.getInstance();
                    if (audioManager != null) {
                        audioManager.dispose();
                    }

                    Gdx.app.exit();
                } catch (Exception e) {
                    GameLogger.error("Error during final shutdown: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            GameLogger.error("Error during shutdown: " + e.getMessage());
        }
    }


    @Override
    public void dispose() {
        try {
            GameLogger.info("Starting game disposal...");

            // Queue on main thread if we're not already there
            if (!Gdx.app.getType().equals(Application.ApplicationType.Desktop) ||
                !Thread.currentThread().getName().equals("LWJGL Application")) {

                Gdx.app.postRunnable(() -> {
                    try {
                        saveAndDispose();
                        if (assetManager != null) {
                            assetManager.dispose();
                        }
                    } catch (Exception e) {
                        GameLogger.error("Error during final disposal: " + e.getMessage());
                    }
                });
            } else {
                saveAndDispose();
                if (assetManager != null) {
                    assetManager.dispose();
                }
            }
        } catch (Exception e) {
            GameLogger.error("Error during disposal: " + e.getMessage());
        }
    }

    public void initializeWorld(String worldName, boolean isMultiplayer) throws IOException {
        GameLogger.info("Starting world initialization: " + worldName);

        try {
            if (GameContext.get().getGameClient() == null) {
                if (isMultiplayer) {
                    ServerConnectionConfig config = ServerConfigManager.getDefaultServerConfig();
                    GameContext.get().setGameClient(GameClientSingleton.getInstance(config));
                } else {
                    GameContext.get().setGameClient(GameClientSingleton.getSinglePlayerInstance());
                }

                if (GameContext.get().getGameClient() == null) {
                    throw new IllegalStateException("Failed to initialize GameClient");
                }
            }
            WorldData worldData = GameContext.get().getWorldManager().loadAndValidateWorld(worldName);
            if (worldData == null) {
                GameLogger.error("Failed to load world data for: " + worldName);
                throw new IOException("Failed to load world data");
            }
            String username = isMultiplayer ? GameContext.get().getGameClient().getLocalUsername() : "Player";
            PlayerData savedPlayerData = worldData.getPlayerData(username, false);

            if (savedPlayerData != null) {
                GameLogger.info("Found saved player data for: " + username +
                    " Items: " + savedPlayerData.getInventoryItems().size() +
                    " Pokemon: " + savedPlayerData.getPartyPokemon().size());

            }// Initialize world
            GameContext.get().setWorld(new World(worldName,
                worldData.getConfig().getSeed(), biomeManager));
            GameContext.get().getWorld().setWorldData(worldData);

            if (savedPlayerData != null) {
                GameContext.get().setPlayer(new Player(
                    0, 0,
                    GameContext.get().getWorld(),   // Use the newly created world!
                    username
                ));
                GameContext.get().getPlayer().initializeResources();
                savedPlayerData.applyToPlayer(GameContext.get().getPlayer());
                GameLogger.info("Restored player state - Items: " +
                    GameContext.get().getPlayer().getInventory().getAllItems().size() +
                    " Pokemon: " + GameContext.get().getPlayer().getPokemonParty().getSize());
            } else {
                GameLogger.info("Creating new player at default position");
                GameContext.get().setPlayer(new Player(World.DEFAULT_X_POSITION,
                    World.DEFAULT_Y_POSITION, GameContext.get().getWorld(), username));
                GameContext.get().getPlayer().initializeResources();
            }


            GameContext.get().getWorld().setPlayer(GameContext.get().getPlayer());
            GameLogger.info("World initialization complete: " + worldName);

        } catch (Exception e) {
            GameLogger.error("Failed to initialize world: " + e.getMessage());
            throw new IOException("World initialization failed", e);
        }
    }

    private void queueAssets() {
        String[] atlasFiles = {
            "atlas/steps.atlas",
            "atlas/ui-gfx-atlas.atlas",
            "atlas/back-gfx-atlas",
            "atlas/front-gfx-atlas",
            "atlas/boy-gfx-atlas",
            "atlas/tiles-gfx-atlas",
            "atlas/icon_gfx_atlas",
            "atlas/items-gfx-atlas",
            "atlas/overworld-gfx-atlas.atlas",
            "atlas/battlebacks-gfx-atlas",
            "atlas/move-effects-gfx",
            "atlas/haunted_biome.atlas",
            "atlas/mountain-atlas.atlas",
            "atlas/move_effects_gfx.atlas",
            "atlas/blocks.atlas",
            "atlas/characters.atlas",
            "atlas/clothing.atlas",
            "atlas/hairstyles.atlas",
            "atlas/buildings.atlas",
            "atlas/girl.atlas",
        };

        for (String path : atlasFiles) {
            verifyAssetExists(path);
            assetManager.load(path, TextureAtlas.class);
        }

        // Verify required data files exist
        String[] dataFiles = {
            "Data/pokemon.json",
            "Data/biomes.json",
            "Data/moves.json"
        };
        for (String dataFile : dataFiles) {
            verifyDataFileExists(dataFile);
        }

        assetManager.setLoader(TextureAtlas.class, new TextureAtlasLoader(new InternalFileHandleResolver()));

        GameLogger.info("Asset loading queued");
    }

    private void verifyDataFileExists(String path) {
        try {
            FileHandle fileHandle = Gdx.files.internal(path);
            if (!fileHandle.exists()) {
                throw new RuntimeException("Data file not found: " + path);
            }
            String content = fileHandle.readString();
            if (content == null || content.isEmpty()) {
                throw new RuntimeException("Empty data file: " + path);
            }
            GameLogger.info("Successfully verified data file: " + path);
        } catch (Exception e) {
            GameLogger.error("Failed to verify data file: " + path + " - " + e.getMessage());
            throw new RuntimeException("Required data file missing: " + path, e);
        }
    }

    @Override
    public void returnToLogin(String message) {
        try {
            if (screen != null) {
                screen.dispose();
            }
            setScreen(new LoginScreen(this));
        } catch (Exception e) {
            GameLogger.error("Error returning to login: " + e.getMessage());
        }
    }

    private void verifyAssetExists(String path) {
        try {
            if (!Gdx.files.internal(path).exists()) {
                String[] alternatives = {
                    path.toLowerCase(),
                    "assets/" + path,
                    path.replace("Data/", "data/")
                };

                boolean found = false;
                for (String alt : alternatives) {
                    if (Gdx.files.internal(alt).exists()) {
                        GameLogger.info("Found asset at alternate path: " + alt);
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    throw new RuntimeException("Required asset not found: " + path +
                        " (tried multiple path variants)");
                }
            }
        } catch (Exception e) {
            GameLogger.error("Error verifying asset: " + path + " - " + e.getMessage());
            throw new RuntimeException("Asset verification failed", e);
        }
    }

    private void initializeManagers() {
        try {

            GameLogger.info("Initializing managers with loaded assets...");
            TextureAtlas stepsAtlas = assetManager.get("atlas/steps.atlas", TextureAtlas.class);
            TextureAtlas battleAtlas = assetManager.get("atlas/battlebacks-gfx-atlas", TextureAtlas.class);
            TextureAtlas uiAtlas = assetManager.get("atlas/ui-gfx-atlas.atlas", TextureAtlas.class);
            TextureAtlas backAtlas = assetManager.get("atlas/back-gfx-atlas", TextureAtlas.class);
            TextureAtlas frontAtlas = assetManager.get("atlas/front-gfx-atlas", TextureAtlas.class);
            TextureAtlas iconAtlas = assetManager.get("atlas/icon_gfx_atlas", TextureAtlas.class);
            TextureAtlas overworldAtlas = assetManager.get("atlas/overworld-gfx-atlas.atlas", TextureAtlas.class);
            TextureAtlas itemsAtlas = assetManager.get("atlas/items-gfx-atlas", TextureAtlas.class);
            TextureAtlas boyAtlas = assetManager.get("atlas/boy-gfx-atlas", TextureAtlas.class);
            TextureAtlas girlAtlas = assetManager.get("atlas/girl.atlas", TextureAtlas.class);
            TextureAtlas effects = assetManager.get("atlas/move_effects_gfx.atlas", TextureAtlas.class);
            TextureAtlas mountains = assetManager.get("atlas/mountain-atlas.atlas", TextureAtlas.class);
            TextureAtlas tilesAtlas = assetManager.get("atlas/tiles-gfx-atlas", TextureAtlas.class);
            TextureAtlas blocks = assetManager.get("atlas/blocks.atlas", TextureAtlas.class);
            TextureAtlas clothing = assetManager.get("atlas/clothing.atlas", TextureAtlas.class);
            TextureAtlas buildings = assetManager.get("atlas/buildings.atlas", TextureAtlas.class);

            TextureAtlas characters = assetManager.get("atlas/characters.atlas", TextureAtlas.class);

            TextureAtlas hairstyles = assetManager.get("atlas/hairstyles.atlas", TextureAtlas.class);

            TextureManager.debugAtlasState("Boy", boyAtlas);

            if (!verifyAtlas(boyAtlas)) {
                throw new RuntimeException("Boy atlas verification failed");
            }

            TextureManager.initialize(
                stepsAtlas,
                battleAtlas,
                uiAtlas,
                backAtlas,
                frontAtlas,
                iconAtlas,
                overworldAtlas,
                itemsAtlas,
                boyAtlas,
                tilesAtlas,
                effects,
                mountains, blocks, characters, clothing, hairstyles, buildings, girlAtlas

            );

            PokemonDatabase.initialize();

            ItemManager.initialize(TextureManager.items);
            AudioManager.getInstance();

            SpriteBatch mainBatch = new SpriteBatch();
            SpriteBatch uiBatch = new SpriteBatch();
            Stage uiStage = new Stage();
            Stage battleStage = new Stage();
            GameContext.init(this, this.gameClient, this.currentWorld, this.player, mainBatch, uiBatch, uiStage, battleStage, null, null, null, null, null, null, null, WorldManager.getInstance(), null, null, new DisconnectionManager(this), false, null, null, null);

            this.biomeManager = new BiomeManager(System.currentTimeMillis());
            GameContext.get().getWorldManager().init();

            GameLogger.info("Managers initialized successfully");

        } catch (Exception e) {
            GameLogger.error("Failed to initialize managers: " + e.getMessage());
            throw new RuntimeException("Failed to initialize game managers", e);
        }
    }

    private boolean verifyAtlas(TextureAtlas atlas) {
        if (atlas == null) {
            GameLogger.error("Boy" + " atlas is null");
            return false;
        }

        try {
            for (Texture texture : atlas.getTextures()) {
                if (texture == null) {
                    GameLogger.error("Boy" + " atlas has invalid textures");
                    return false;
                }
            }
            if (atlas.getRegions().isEmpty()) {
                GameLogger.error("Boy" + " atlas has no regions");
                return false;
            }

            return true;
        } catch (Exception e) {
            GameLogger.error("Error verifying " + "Boy" + " atlas: " + e.getMessage());
            return false;
        }
    }

    public Player getPlayer() {
        return GameContext.get().getPlayer();
    }


    public WorldManager getWorldManager() {
        return GameContext.get().getWorldManager();
    }
}

