package io.github.pokemeetup;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.TextureAtlasLoader;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.managers.BiomeManager;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.client.GameClientSingleton;
import io.github.pokemeetup.multiplayer.server.GameStateHandler;
import io.github.pokemeetup.multiplayer.server.ServerStorageSystem;
import io.github.pokemeetup.multiplayer.server.config.ServerConfigManager;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import io.github.pokemeetup.pokemon.data.PokemonDatabase;
import io.github.pokemeetup.screens.*;
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
    public static final long MULTIPLAYER_WORLD_SEED = System.currentTimeMillis();
    private boolean isMultiplayerMode = false;
    private WorldManager worldManager;
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
        return isMultiplayerMode;
    }

    @Override
    public void create() {

        assetManager = new AssetManager();
        queueAssets();
        GameLogger.info("Loading assets...");
        assetManager.finishLoading();
        initializeManagers();

        setScreen(new ModeSelectionScreen(this));

        GameLogger.info("Game initialization complete");
    }



    public void saveAndDispose() {
        try {
            GameLogger.info("Starting game state save...");

            if (currentWorld != null) {
                // Ensure all chunks are saved first
                for (Map.Entry<Vector2, Chunk> entry : currentWorld.getChunks().entrySet()) {
                    try {
                        Vector2 chunkPos = entry.getKey();
                        Chunk chunk = entry.getValue();
                        currentWorld.saveChunkData(chunkPos, chunk, isMultiplayerMode);
                        GameLogger.info("Saved chunk at " + chunkPos);
                    } catch (Exception e) {
                        GameLogger.error("Failed to save chunk: " + e.getMessage());
                    }
                }

                // Save world data after all chunks are saved
                World world = currentWorld;
                WorldData worldData = world.getWorldData();

                // Update player data if needed
                if (player != null) {
                    PlayerData currentState = new PlayerData(player.getUsername());
                    currentState.updateFromPlayer(player);
                    worldData.savePlayerData(player.getUsername(), currentState,false);
                }

                // Force one final save
                worldManager.saveWorld(worldData);

                // Verify the save
                FileHandle worldFile = Gdx.files.local("worlds/singleplayer/" + world.getName() + "/world.json");
                if (!worldFile.exists()) {
                    GameLogger.error("World file not created after final save!");
                } else {
                    GameLogger.info("Final world save successful");
                }

                // Clean up
                currentWorld.dispose();
                GameLogger.info("World disposed successfully");
            }

            // Clean up resources but don't dispose asset manager
            cleanupCurrentWorld();

        } catch (Exception e) {
            GameLogger.error("Error during game state save: " + e.getMessage());
            e.printStackTrace();
        }
    }


    // Add this new method to reinitialize the world
    public void reinitializeGame() {
        try {
            // Create fresh managers
            this.worldManager = WorldManager.getInstance(new ServerStorageSystem(), isMultiplayerMode);
            this.biomeManager = new BiomeManager(System.currentTimeMillis());
            this.worldManager.init();

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
    }public void cleanupCurrentWorld() {
        try {
            GameLogger.info("Cleaning up current world state...");

            // Save current world if exists
            if (currentWorld != null) {
                // Validate world data before saving
                if (currentWorld.getWorldData() == null) {
                    GameLogger.error("World data is null - creating new");
                    WorldData newData = new WorldData(currentWorld.getName());
                    currentWorld.setWorldData(newData);
                }

                // Ensure player data is saved to world
                if (player != null) {
                    PlayerData currentState = player.getPlayerData();
                    currentWorld.getWorldData().savePlayerData(player.getUsername(), currentState,false);
                    GameLogger.info("Saved player data for: " + player.getUsername());
                }

                // Force a final save
                currentWorld.save();

                // Validate save was successful
                FileHandle worldFile = Gdx.files.local("worlds/singleplayer/" +
                    currentWorld.getName() + "/world.json");
                if (!worldFile.exists()) {
                    GameLogger.error("World file not created after save");
                }

                // Clean up world resources
                currentWorld.dispose();
                currentWorld = null;
            }

            // Clean up player
            if (player != null) {
                player.dispose();
                player = null;
            }

            // Reset game state
            isMultiplayerMode = false;

            GameLogger.info("World state cleaned up successfully");

        } catch (Exception e) {
            GameLogger.error("Error cleaning up world state: " + e.getMessage());
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
            e.printStackTrace();
        }
    }

    public World getCurrentWorld() {
        return currentWorld;
    }

    public void initializeWorld(String worldName, boolean isMultiplayer) throws IOException {
        GameLogger.info("Starting world initialization: " + worldName);
        this.isMultiplayerMode = isMultiplayer;

        try {
            if (gameClient == null) {
                if (isMultiplayer) {
                    ServerConnectionConfig config = ServerConfigManager.getDefaultServerConfig();
                    gameClient = GameClientSingleton.getInstance(config);
                } else {
                    gameClient = GameClientSingleton.getSinglePlayerInstance();
                }

                if (gameClient == null) {
                    throw new IllegalStateException("Failed to initialize GameClient");
                }
            }
            WorldData worldData = worldManager.loadAndValidateWorld(worldName);
            if (worldData == null) {
                GameLogger.error("Failed to load world data for: " + worldName);
                throw new IOException("Failed to load world data");
            }
            String username = isMultiplayer ? gameClient.getLocalUsername() : "Player";
            PlayerData savedPlayerData = worldData.getPlayerData(username, false);

            if (savedPlayerData != null) {
                GameLogger.info("Found saved player data for: " + username +
                    " Items: " + savedPlayerData.getInventoryItems().size() +
                    " Pokemon: " + savedPlayerData.getPartyPokemon().size());

            }

            // Initialize world
            this.currentWorld = new World(worldName,
                worldData.getConfig().getSeed(), gameClient, biomeManager);
            this.currentWorld.setWorldData(worldData);

            // Initialize player
            if (savedPlayerData != null) {
                this.player = new Player((int) savedPlayerData.getX(),
                    (int) savedPlayerData.getY(), currentWorld, username);
                player.initializeResources();
                savedPlayerData.applyToPlayer(player);
                GameLogger.info("Restored player state - Items: " +
                    player.getInventory().getAllItems().size() +
                    " Pokemon: " + player.getPokemonParty().getSize());
            } else {
                GameLogger.info("Creating new player at default position");
                this.player = new Player(World.DEFAULT_X_POSITION,
                    World.DEFAULT_Y_POSITION, currentWorld, username);
                player.initializeResources();
            }

            currentWorld.setPlayer(player);
            GameLogger.info("World initialization complete: " + worldName);

        } catch (Exception e) {
            GameLogger.error("Failed to initialize world: " + e.getMessage());
            throw new IOException("World initialization failed", e);
        }
    }

    private void queueAssets() {
        String[] atlasFiles = {
            "assets/atlas/ui-gfx-atlas.atlas",
            "assets/atlas/back-gfx-atlas",
            "assets/atlas/front-gfx-atlas",
            "assets/atlas/boy-gfx-atlas",
            "assets/atlas/tiles-gfx-atlas",
            "assets/atlas/icon_gfx_atlas",
            "assets/atlas/items-gfx-atlas",
            "assets/atlas/overworld-gfx-atlas.atlas",
            "assets/atlas/battlebacks-gfx-atlas",
            "assets/atlas/move-effects-gfx",
            "assets/atlas/haunted_biome.atlas",
            "assets/atlas/mountain-atlas.atlas",
            "assets/atlas/move_effects_gfx.atlas",
            "assets/atlas/blocks.atlas",
            "assets/atlas/characters.atlas",
            "assets/atlas/clothing.atlas",
            "assets/atlas/hairstyles.atlas",
            "assets/atlas/buildings.atlas",
        };

        for (String path : atlasFiles) {
            verifyAssetExists(path);
            assetManager.load(path, TextureAtlas.class);
        }

        // Verify required data files exist
        String[] dataFiles = {
            "assets/Data/pokemon.json",
            "assets/Data/biomes.json",
            "assets/Data/moves.json"
        };
        for (String dataFile : dataFiles) {
            verifyDataFileExists(dataFile);
        }

        assetManager.setLoader(TextureAtlas.class, new TextureAtlasLoader(new InternalFileHandleResolver()));

        GameLogger.info("Asset loading queued");
    }

    private void verifyDataFileExists(String path) {
        try {
            String content = GameFileSystem.getInstance().getDelegate().readString(path);
            if (content == null || content.isEmpty()) {
                throw new RuntimeException("Empty data file: " + path);
            }
            GameLogger.info("Successfully verified data file: " + path);
        } catch (Exception e) {
            GameLogger.error("Failed to verify data file: " + path);
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
            TextureAtlas battleAtlas = assetManager.get("assets/atlas/battlebacks-gfx-atlas", TextureAtlas.class);
            TextureAtlas uiAtlas = assetManager.get("assets/atlas/ui-gfx-atlas.atlas", TextureAtlas.class);
            TextureAtlas backAtlas = assetManager.get("assets/atlas/back-gfx-atlas", TextureAtlas.class);
            TextureAtlas frontAtlas = assetManager.get("assets/atlas/front-gfx-atlas", TextureAtlas.class);
            TextureAtlas iconAtlas = assetManager.get("assets/atlas/icon_gfx_atlas", TextureAtlas.class);
            TextureAtlas overworldAtlas = assetManager.get("assets/atlas/overworld-gfx-atlas.atlas", TextureAtlas.class);
            TextureAtlas itemsAtlas = assetManager.get("assets/atlas/items-gfx-atlas", TextureAtlas.class);
            TextureAtlas boyAtlas = assetManager.get("assets/atlas/boy-gfx-atlas", TextureAtlas.class);
            TextureAtlas effects = assetManager.get("assets/atlas/move_effects_gfx.atlas", TextureAtlas.class);
            TextureAtlas mountains = assetManager.get("assets/atlas/mountain-atlas.atlas", TextureAtlas.class);
            TextureAtlas tilesAtlas = assetManager.get("assets/atlas/tiles-gfx-atlas", TextureAtlas.class);
            TextureAtlas blocks = assetManager.get("assets/atlas/blocks.atlas", TextureAtlas.class);
            TextureAtlas clothing = assetManager.get("assets/atlas/clothing.atlas", TextureAtlas.class);
            TextureAtlas buildings = assetManager.get("assets/atlas/buildings.atlas", TextureAtlas.class);

            TextureAtlas characters = assetManager.get("assets/atlas/characters.atlas", TextureAtlas.class);

            TextureAtlas hairstyles = assetManager.get("assets/atlas/hairstyles.atlas", TextureAtlas.class);

            TextureManager.debugAtlasState("Boy", boyAtlas);

            if (!verifyAtlas(boyAtlas)) {
                throw new RuntimeException("Boy atlas verification failed");
            }

            TextureManager.initialize(
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
                mountains, blocks, characters, clothing, hairstyles,buildings

            );

            PokemonDatabase.initialize();

            ItemManager.initialize(TextureManager.items);
            AudioManager.getInstance();

            ServerStorageSystem serverStorageSystem = new ServerStorageSystem();

            this.worldManager = WorldManager.getInstance(serverStorageSystem, isMultiplayerMode);
            this.biomeManager = new BiomeManager(System.currentTimeMillis());
            this.worldManager.init();

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
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    public WorldManager getWorldManager() {
        return worldManager;
    }
}

