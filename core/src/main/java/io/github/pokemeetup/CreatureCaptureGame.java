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
import java.util.Random;

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

    /**
     * Saves the current world, disposes the game screen, and transitions back to the world selection screen.
     * This is the proper way to "quit" a world without closing the application.
     */
    public void exitToMenu() {
        GameLogger.info("Exiting game to menu...");
        if (getScreen() instanceof GameScreen) {
            World currentWorld = GameContext.get().getWorld();
            if (currentWorld != null && !isMultiplayerMode()) {
                currentWorld.save(); // Centralized save logic
            }
        }

        // Dispose the current screen
        if (getScreen() != null) {
            getScreen().dispose();
        }

        // Reset game-specific context but keep application-level context
        // This prevents holding onto disposed objects like the old GameScreen
        if (GameContext.get() != null) {
            GameContext.get().setWorld(null);
            GameContext.get().setPlayer(null);
            GameContext.get().setHotbarSystem(null);
            GameContext.get().setGameMenu(null);
            GameContext.get().setInventoryScreen(null);
            GameContext.get().setCraftingScreen(null);
            GameContext.get().setChatSystem(null);
            // DO NOT dispose the uiStage, batch, or skin here. They persist.
        }

        // Transition to the world selection screen
        setScreen(new WorldSelectionScreen(this));
    }


    /**
     * Fully saves the game state and closes the application.
     */
    public void shutdown() {
        Gdx.app.postRunnable(() -> {
            try {
                // Save the current world if we are in a GameScreen
                if (getScreen() instanceof GameScreen && GameContext.get().getWorld() != null && !isMultiplayerMode()) {
                    GameLogger.info("Performing final save before shutdown...");
                    GameContext.get().getWorld().save();
                }

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
                        if (getScreen() instanceof GameScreen && GameContext.get().getWorld() != null && !isMultiplayerMode()) {
                            GameContext.get().getWorld().save();
                        }
                        if (assetManager != null) {
                            assetManager.dispose();
                        }
                    } catch (Exception e) {
                        GameLogger.error("Error during final disposal: " + e.getMessage());
                    }
                });
            } else {
                if (getScreen() instanceof GameScreen && GameContext.get().getWorld() != null && !isMultiplayerMode()) {
                    GameContext.get().getWorld().save();
                }
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

            long worldSeed = worldData.getConfig().getSeed();
            this.biomeManager = new BiomeManager(worldSeed);
            if (savedPlayerData != null) {
                GameLogger.info("Found saved player data for: " + username +
                    " Items: " + savedPlayerData.getInventoryItems().size() +
                    " Pokemon: " + savedPlayerData.getPartyPokemon().size());

            }// Initialize world
            GameContext.get().setWorld(new World(worldName,
                worldData.getConfig().getSeed()));
            GameContext.get().getWorld().setWorldData(worldData);

            if (savedPlayerData != null) {
                World currentWorld = GameContext.get().getWorld();
                // We'll pick a safe spawn tile from the islands
                Random rng = new Random(currentWorld.getWorldData().getConfig().getSeed());

                Vector2 safeTile = biomeManager.findSafeSpawnLocation(currentWorld, rng);


                Player newPlayer = new Player(
                    (int) safeTile.x,
                    (int) safeTile.y,
                    currentWorld,
                    username
                );
                GameContext.get().setPlayer(newPlayer);
                GameContext.get().getPlayer().initializeResources();
                savedPlayerData.applyToPlayer(GameContext.get().getPlayer());
                GameLogger.info("Restored player state - Items: " +
                    GameContext.get().getPlayer().getInventory().getAllItems().size() +
                    " Pokemon: " + GameContext.get().getPlayer().getPokemonParty().getSize());
            } else {
                World currentWorld = GameContext.get().getWorld();
                BiomeManager bm = currentWorld.getBiomeManager();
                // We'll pick a safe spawn tile from the islands
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
            "atlas/autotiles_sheets.atlas",
            "atlas/capsule_throw.atlas",
            "atlas/ow-effects.atlas"
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
            TextureAtlas characters = assetManager.get("atlas/characters.atlas", TextureAtlas.class);
            TextureAtlas clothing = assetManager.get("atlas/clothing.atlas", TextureAtlas.class);
            TextureAtlas hairstyles = assetManager.get("atlas/hairstyles.atlas", TextureAtlas.class);
            TextureAtlas buildings = assetManager.get("atlas/buildings.atlas", TextureAtlas.class);
            TextureAtlas autotiles = assetManager.get("atlas/autotiles_sheets.atlas", TextureAtlas.class);
            TextureAtlas capsuleThrow = assetManager.get("atlas/capsule_throw.atlas", TextureAtlas.class);
            TextureAtlas owEffectAtlas = assetManager.get("atlas/ow-effects.atlas", TextureAtlas.class);


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
                mountains, blocks, characters, clothing, hairstyles, buildings, girlAtlas, autotiles, capsuleThrow, owEffectAtlas

            );

            PokemonDatabase.initialize();

            ItemManager.initialize(TextureManager.items);
            AudioManager.getInstance();

            SpriteBatch mainBatch = new SpriteBatch();
            SpriteBatch uiBatch = new SpriteBatch();
            // This UI Stage will persist across screens.
            Stage uiStage = new Stage(new ScreenViewport(), uiBatch);
            Stage battleStage = new Stage();
            Skin skin = new Skin(Gdx.files.internal("Skins/uiskin.json"));

            GameContext.init(this, this.gameClient, this.currentWorld, this.player, mainBatch, uiBatch, uiStage, battleStage, null, null, null, null, null, null, null, WorldManager.getInstance(), null, null, new DisconnectionManager(this), false, skin, null, null, new BiomeManager(System.currentTimeMillis()));

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
