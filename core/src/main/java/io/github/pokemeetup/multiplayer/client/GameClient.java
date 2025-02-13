package io.github.pokemeetup.multiplayer.client;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.managers.BiomeManager;
import io.github.pokemeetup.managers.BiomeTransitionResult;
import io.github.pokemeetup.managers.DisconnectionManager;
import io.github.pokemeetup.multiplayer.OtherPlayer;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.screens.ChestScreen;
import io.github.pokemeetup.screens.GameScreen;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.data.ChestData;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.data.WorldData;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.WeatherSystem;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldManager;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4SafeDecompressor;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;


public class GameClient {
    private static final long CONNECTION_TIMEOUT_MS = 5000;
    private static final long CONNECTION_TIMEOUT = 45000;
    private static final long RECONNECT_DELAY = 3000;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final int MAX_CONCURRENT_CHUNK_REQUESTS = 4;
    private static final long CHUNK_REQUEST_INTERVAL = 50;
    private static final float SYNC_INTERVAL = 1 / 60f;
    private static final float INTERPOLATION_SPEED = 10f;
    private static final float UPDATE_INTERVAL = 1 / 20f;
    private static final int BUFFER_SIZE = 65536;
    private static final int INCREASED_BUFFER = 65536;
    private static final int CHUNK_LOAD_RADIUS = 3;
    private static final long PING_INTERVAL = 5000; // 5 seconds
    private static final int CONNECT_TIMEOUT_MS = 45000; // unify to 45s
    private final DisconnectionManager disconnectHandler;
    private final PlayerDataResponseHandler playerDataHandler = new PlayerDataResponseHandler();
    private final Queue<Vector2> chunkRequestQueue = new ConcurrentLinkedQueue<>();
    private final Set<Vector2> pendingChunks = new ConcurrentHashMap<Vector2, Boolean>().keySet(true);
    private final AtomicBoolean isAuthenticated = new AtomicBoolean(false);
    private final AtomicBoolean isDisposing = new AtomicBoolean(false);
    private final ReentrantLock connectionLock = new ReentrantLock();
    private final ConcurrentHashMap<String, OtherPlayer> otherPlayers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, WildPokemon> trackedWildPokemon = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, NetworkSyncData> syncedPokemonData = new ConcurrentHashMap<>();
    private final BlockingQueue<NetworkProtocol.ChatMessage> chatMessageQueue = new LinkedBlockingQueue<>();
    private final ConcurrentHashMap<String, NetworkProtocol.PlayerUpdate> playerUpdates = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final Queue<Object> pendingMessages = new ConcurrentLinkedQueue<>();
    private final Preferences credentials;
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);
    private final Map<Vector2, Future<Chunk>> loadingChunks = new ConcurrentHashMap<>();
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private final Set<String> recentJoinEvents = Collections.synchronizedSet(new HashSet<>());
    private final ConcurrentHashMap<String, Integer> playerPingMap = new ConcurrentHashMap<>();
    public AtomicBoolean shouldReconnect = new AtomicBoolean(true);
    private volatile boolean connecting = false;
    private volatile boolean connected = false;
    private boolean disposing = false;
    private boolean isSinglePlayer;
    private int reconnectAttempts = 0;
    private volatile boolean isInitializing = false;
    private long lastRequestTime = 0;
    private float syncTimer = 0;
    private PlayerData lastKnownState;
    private Consumer<NetworkProtocol.ChatMessage> chatMessageHandler;
    private PokemonUpdateHandler pokemonUpdateHandler;
    private WorldData worldData;
    private float updateAccumulator = 0;
    private volatile boolean isInitialized = false;
    private volatile boolean fullyInitialized = false;
    private InitializationListener initializationListener;
    private String pendingUsername;
    private String pendingPassword;
    private String currentPassword;
    private volatile boolean loginRequestSent = false;
    private volatile boolean processingMessages = false;
    private volatile ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private volatile Client client;
    private ServerConnectionConfig serverConfig;
    private String localUsername;
    private long worldSeed;
    private ReconnectionListener reconnectionListener;
    private int localPing;
    private long lastPingTime = 0;
    private Consumer<NetworkProtocol.LoginResponse> loginResponseListener;

    public GameClient(ServerConnectionConfig config) {

        this.disconnectHandler = GameContext.get().getDisconnectionManager();
        this.serverConfig = config;
        this.isSinglePlayer = !GameContext.get().isMultiplayer();
        this.lastKnownState = new PlayerData();

        this.credentials = Gdx.app.getPreferences("game-credentials");
        this.serverConfig = config;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.lastKnownState = new PlayerData();

        if (!isSinglePlayer) {
            setupReconnectionHandler();
            loadSavedCredentials();
            if (serverConfig != null) {
                setServerConfig(serverConfig);
            } else {
                GameLogger.info("Failed to load server config, multiplayer disabled.");
            }

            this.client = new Client(INCREASED_BUFFER, INCREASED_BUFFER);
            NetworkProtocol.registerClasses(client.getKryo());
            client.getKryo().setReferences(false);
        }
    }

    public void connectIfNeeded(Runnable onSuccess, Consumer<String> onError, long timeoutMs) {
        // 1) If client is already connected, just callback success
        if (client != null && client.isConnected()) {
            Gdx.app.postRunnable(onSuccess);
            return;
        }
        // 2) If we are in the middle of connecting, do not connect again.
        if (!isConnecting.compareAndSet(false, true)) {
            // means isConnecting was already true
            Gdx.app.postRunnable(() -> onError.accept("Already connecting..."));
            return;
        }

        // 3) Not connected, not connecting => proceed with new connection
        client = new Client(65536, 65536);
        NetworkProtocol.registerClasses(client.getKryo());

        client.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                // Mark as connected on the main thread:
                Gdx.app.postRunnable(() -> {
                    isConnecting.set(false);
                    // Mark the connection state as CONNECTED and set the isConnected flag.
                    connectionState = ConnectionState.CONNECTED;
                    isConnected.set(true);
                    isAuthenticated.set(false);
                    onSuccess.run();
                });
            }

            @Override
            public void disconnected(Connection connection) {
                if (!isDisposing.get()) {
                    if (!suppressDisconnectHandling) {
                        handleDisconnect("Disconnected from server");
                    } else {
                        // Optionally log that disconnect handling is suppressed.
                        GameLogger.info("Disconnect occurred while in registration mode – not triggering disconnect screen.");
                    }
                }
            }


            @Override
            public void received(Connection connection, Object object) {
                handleReceivedMessage(object);
            }
        });

        client.start();
        new Thread(() -> {
            try {
                client.connect((int)timeoutMs,
                    serverConfig.getServerIP(),
                    serverConfig.getTcpPort(),
                    serverConfig.getUdpPort());
            } catch (Exception e) {
                isConnecting.set(false);
                Gdx.app.postRunnable(() -> onError.accept("Connection failed: " + e.getMessage()));
            }
        }).start();
    }private static final long REGISTRATION_CONNECT_TIMEOUT_MS = 10000; // 10 seconds


    public void sendBuildingPlacement(NetworkProtocol.BuildingPlacement bp) {
        client.sendTCP(bp);
    }

    public void savePlayerData(UUID uuid, PlayerData data) {
        if (isSinglePlayer || !isConnected() || !isAuthenticated()) {
            GameLogger.error("Cannot save player data - not in multiplayer mode or not connected");
            return;
        }

        try {
            NetworkProtocol.SavePlayerDataRequest request = new NetworkProtocol.SavePlayerDataRequest();
            request.uuid = uuid;

            request.playerData = data;
            request.timestamp = System.currentTimeMillis();
            client.sendTCP(request);
            GameLogger.info("Sent player data save request for UUID: " + uuid);
        } catch (Exception e) {
            GameLogger.error("Failed to save player data: " + e.getMessage());
        }
    }

    public void update(float deltaTime) {
        if (!isAuthenticated.get() || connectionState != ConnectionState.CONNECTED) {
            return;
        }
        syncTimer += deltaTime;
        if (syncTimer >= SYNC_INTERVAL) {
            syncTimer = 0;
            processChatMessages();
            updatePokemonStates(deltaTime);
        }
        long now = System.currentTimeMillis();
        if (now - lastPingTime > PING_INTERVAL) {
            sendPingRequest();
            lastPingTime = now;
        }

        updateOtherPlayers(deltaTime);
        updateAccumulator += deltaTime;

        if (updateAccumulator >= UPDATE_INTERVAL) {
            updateAccumulator = 0;
            if (!isSinglePlayer && GameContext.get().getPlayer() != null && isAuthenticated() && isInitialized) {
                sendPlayerUpdate();
            }
            processChunkQueue();
            requestChunksAroundPlayer();
        }
    }

    private void handlePingResponse(NetworkProtocol.PingResponse response) {
        long now = System.currentTimeMillis();
        localPing = (int) (now - response.timestamp);

        // Now send our updated info to the server:
        NetworkProtocol.PlayerInfoUpdate update = new NetworkProtocol.PlayerInfoUpdate();
        update.username = localUsername;
        update.ping = localPing;
        client.sendTCP(update);
    }

    public void sendPokemonSpawn(NetworkProtocol.WildPokemonSpawn spawnData) {
        if (!isConnected() || !isAuthenticated() || isSinglePlayer) {
            return;
        }

        try {
            // Validate spawn data
            if (spawnData.data == null || spawnData.uuid == null) {
                GameLogger.error("Invalid Pokemon spawn data");
                return;
            }

            if (spawnData.timestamp == 0) {
                spawnData.timestamp = System.currentTimeMillis();
            }

            // Send spawn data to server
            client.sendTCP(spawnData);

            // Track locally
            if (!trackedWildPokemon.containsKey(spawnData.uuid)) {
                TextureRegion overworldSprite = TextureManager.getOverworldSprite(spawnData.data.getName());

                if (overworldSprite != null) {
                    WildPokemon pokemon = new WildPokemon(
                        spawnData.data.getName(),
                        spawnData.data.getLevel(),
                        (int) spawnData.x,
                        (int) spawnData.y,
                        overworldSprite
                    );
                    pokemon.setUuid(spawnData.uuid);
                    pokemon.setSpawnTime(spawnData.timestamp / 1000L);

                    if (GameContext.get().getWorld() != null) {
                        pokemon.setWorld(GameContext.get().getWorld());
                        Vector2 chunkPos = new Vector2(
                            Math.floorDiv((int) spawnData.x, World.CHUNK_SIZE * World.TILE_SIZE),
                            Math.floorDiv((int) spawnData.y, World.CHUNK_SIZE * World.TILE_SIZE)
                        );
                        GameContext.get().getWorld().getPokemonSpawnManager().addPokemonToChunk(pokemon, chunkPos);
                    }

                    trackedWildPokemon.put(spawnData.uuid, pokemon);
                    syncedPokemonData.put(spawnData.uuid, new NetworkSyncData());

                    GameLogger.info("Sent and tracked new Pokemon spawn: " + spawnData.data.getName() +
                        " at (" + spawnData.x + "," + spawnData.y + ")");
                } else {
                    GameLogger.error("Failed to load sprite for Pokemon: " + spawnData.data.getName());
                }
            } else {
                GameLogger.info("Pokemon already tracked locally with UUID: " + spawnData.uuid);
            }

        } catch (Exception e) {
            GameLogger.error("Failed to send Pokemon spawn: " + e.getMessage());
            if (!isConnected()) {
                handleConnectionFailure(e);
            }
        }
    }

    public Client getClient() {
        return client;
    }

    public void sendChestUpdate(ChestData chestData) {
        if (GameContext.get().isMultiplayer()) {
            try {
                NetworkProtocol.ChestUpdate update = new NetworkProtocol.ChestUpdate();
                // Use the public field chestId (a UUID) from ChestData
                update.chestId = chestData.chestId;
                update.username = GameContext.get().getPlayer().getUsername();
                // Use the items list from chestData (assumed non-null)
                update.items = chestData.getItems();
                update.timestamp = System.currentTimeMillis();
                client.sendTCP(update);
                GameLogger.info("Sent chest update: " + update.chestId + " with " + update.items.size() + " items");
            } catch (Exception e) {
                GameLogger.error("Failed to send chest update: " + e.getMessage());
            }
        }
    }

    private void loadSavedCredentials() {
        try {
            String savedUsername = credentials.getString("username", "");
            String savedPassword = credentials.getString("password", "");

            if (!savedUsername.isEmpty() && !savedPassword.isEmpty()) {
                this.localUsername = savedUsername;
                this.currentPassword = savedPassword;
                this.pendingUsername = savedUsername;
                this.pendingPassword = savedPassword;
                GameLogger.info("Loaded saved credentials for: " + savedUsername);
            } else {
                this.localUsername = "";
                this.currentPassword = "";
                this.pendingUsername = "";
                this.pendingPassword = "";
                GameLogger.info("No saved credentials found");
            }
        } catch (Exception e) {
            GameLogger.error("Failed to load credentials: " + e.getMessage());
        }
    }

    public void sendLoginRequest(String username, String password,
                                 Consumer<NetworkProtocol.LoginResponse> onResponse,
                                 Consumer<String> onError) {
        if (client == null || !client.isConnected()) {
            onError.accept("Not connected to server for login");
            return;
        }

        if (username == null || username.trim().isEmpty() ||
            password == null || password.trim().isEmpty()) {
            onError.accept("Username/password cannot be empty.");
            return;
        }
        NetworkProtocol.LoginRequest request = new NetworkProtocol.LoginRequest();
        request.username = username.trim();
        request.password = password.trim();
        request.timestamp = System.currentTimeMillis();
        GameLogger.info("Sending login request for: " + username);
        this.loginResponseListener = onResponse;
        client.sendTCP(request);
    }

    private void handleConnectionFailure(Exception e) {
        GameLogger.error("Connection failure: " + e.getMessage());
        synchronized (connectionLock) {
            connectionState = ConnectionState.DISCONNECTED;
            isConnected.set(false);
            isAuthenticated.set(false);
            cleanupExistingConnection();
            isConnecting.set(false);
            if (loginResponseListener != null) {
                Gdx.app.postRunnable(() -> {
                    NetworkProtocol.LoginResponse response = new NetworkProtocol.LoginResponse();
                    response.success = false;
                    response.message = "Connection failed: " + e.getMessage();
                    loginResponseListener.accept(response);
                });
            }
        }
    }

    private void cleanupExistingConnection() {
        if (client != null) {
            try {
                if (GameContext.get().getPlayer() != null) {
                    saveState(GameContext.get().getPlayer().getPlayerData());
                }
                if (client.isConnected()) {
                    client.close();
                }
            } catch (Exception e) {
                GameLogger.error("Error cleaning up connection: " + e.getMessage());
            } finally {
                try {
                    if (client != null) {
                        client.stop();
                    }
                } catch (Exception ex) {
                    GameLogger.error("Error stopping client: " + ex.getMessage());
                }
                client = null;
                isSinglePlayer = false;
            }
        }
    }


    public void sendItemDrop(ItemData itemData, Vector2 position) {
        NetworkProtocol.ItemDrop drop = new NetworkProtocol.ItemDrop();
        drop.itemData = itemData;
        drop.x = position.x;
        drop.y = position.y;
        drop.username = getLocalUsername();
        drop.timestamp = System.currentTimeMillis();

        client.sendTCP(drop);
    }

    public void sendItemPickup(NetworkProtocol.ItemPickup pickup) {
        NetworkProtocol.ItemPickup drop = new NetworkProtocol.ItemPickup();
        drop.entityId = pickup.entityId;
        drop.username = getLocalUsername();
        drop.timestamp = System.currentTimeMillis();

        client.sendTCP(drop);
    }

    public void sendPlayerUpdate() {
        if (!isConnected() || !isAuthenticated() || GameContext.get().getPlayer() == null) return;

        float playerX = GameContext.get().getPlayer().getX();
        float playerY = GameContext.get().getPlayer().getY();

        NetworkProtocol.PlayerUpdate update = new NetworkProtocol.PlayerUpdate();
        update.username = getLocalUsername();
        update.x = playerX;
        update.y = playerY;
        update.wantsToRun = GameContext.get().getPlayer().isRunning();
        update.direction = GameContext.get().getPlayer().getDirection();
        update.isMoving = GameContext.get().getPlayer().isMoving();
        update.inventoryItems = GameContext.get().getPlayer().getInventory().getAllItems().toArray(new ItemData[0]);
        update.timestamp = System.currentTimeMillis();
        client.sendTCP(update);
    }

    public void savePlayerState(PlayerData playerData) {
        if (isSinglePlayer) {
            if (GameContext.get().getWorld() != null) {
                GameContext.get().getWorld().getWorldData().savePlayerData(
                    playerData.getUsername(),
                    playerData,
                    false
                );
                WorldManager.getInstance().saveWorld(GameContext.get().getWorld().getWorldData());
                GameLogger.info("Saved singleplayer state for: " + playerData.getUsername());
            }
            return;
        }

        if (isAuthenticated.get()) {
            try {
                // Create save request
                NetworkProtocol.SavePlayerDataRequest request = new NetworkProtocol.SavePlayerDataRequest();
                request.playerData = playerData;
                request.timestamp = System.currentTimeMillis();

                // Send to server
                client.sendTCP(request);
                GameLogger.info("Sent player state update to server for: " + playerData.getUsername());

            } catch (Exception e) {
                GameLogger.error("Failed to send state to server: " + e.getMessage());
            }
        }
    }

    public Map<String, NetworkProtocol.PlayerUpdate> getPlayerUpdates() {
        Map<String, NetworkProtocol.PlayerUpdate> updates = new HashMap<>(playerUpdates);
        playerUpdates.clear();
        return updates;
    }

    public void saveState(PlayerData playerData) {
        if (isSinglePlayer) {
            if (GameContext.get().getWorld() != null) {
                GameContext.get().getWorld().getWorldData().savePlayerData(
                    playerData.getUsername(),
                    playerData,
                    false
                );
                GameContext.get().getWorldManager().saveWorld(GameContext.get().getWorld().getWorldData());
                GameLogger.info("Saved singleplayer state for: " + playerData.getUsername());
            }
        } else {
            if (isConnected() && isAuthenticated()) {
                try {
                    NetworkProtocol.SavePlayerDataRequest request = new NetworkProtocol.SavePlayerDataRequest();
                    request.playerData = playerData;
                    request.timestamp = System.currentTimeMillis();
                    client.sendTCP(request);
                    GameLogger.info("Sent player state to server for: " + playerData.getUsername());
                } catch (Exception e) {
                    GameLogger.error("Failed to send state to server: " + e.getMessage());
                }
            }
        }
    }

    private void handleWorldStateUpdate(NetworkProtocol.WorldStateUpdate update) {
        if (update == null) return;
        // Ensure that updates happen on the rendering thread
        Gdx.app.postRunnable(() -> {
            World world = GameContext.get().getWorld();
            if (world == null) return;
            // Update the local world data with the synchronized time and day length.
            world.getWorldData().setWorldTimeInMinutes(update.worldTimeInMinutes);
            world.getWorldData().setDayLength(update.dayLength);
            // Optionally, update the seed if needed:
            // world.getWorldData().getConfig().setSeed(update.seed);

            // Now update the weather system.
            WeatherSystem weatherSystem = world.getWeatherSystem();
            if (weatherSystem != null) {
                // You can simply “force” the weather parameters that the server sent:
                weatherSystem.setWeather(update.currentWeather, update.intensity);
                weatherSystem.setAccumulation(update.accumulation);
            }
            GameLogger.info("WorldStateUpdate applied: time " + update.worldTimeInMinutes +
                ", weather " + update.currentWeather);
        });
    }


    private void setupReconnectionHandler() {
        scheduler.scheduleWithFixedDelay(() -> {
            if (connectionState == ConnectionState.DISCONNECTED && shouldReconnect.get() && !isDisposing.get()) {
                attemptReconnection();
            }
        }, RECONNECT_DELAY, RECONNECT_DELAY, TimeUnit.MILLISECONDS);
    }

    public void setServerConfig(ServerConnectionConfig serverConfig) {
        this.serverConfig = serverConfig;
    }

    public boolean isSinglePlayer() {
        return !GameContext.get().isMultiplayer();
    }

    public void setSinglePlayer(boolean isSinglePlayer) {
        this.isSinglePlayer = isSinglePlayer;
    }

    public World getCurrentWorld() {
        return GameContext.get().getWorld();
    }

    public long getWorldSeed() {
        return worldSeed;
    }

    public WorldData getWorldData() {
        return worldData;
    }

    public void sendMessage(NetworkProtocol.ChatMessage message) {
        if (isSinglePlayer) {
            if (chatMessageHandler != null) {
                chatMessageHandler.accept(message);
            }
            return;
        }

        try {
            if (!isConnected() || !isAuthenticated()) {
                GameLogger.error("Cannot send chat message - not connected or authenticated");
                return;
            }
            if (message.timestamp == 0) {
                message.timestamp = System.currentTimeMillis();
            }
            if (message.type == null) {
                message.type = NetworkProtocol.ChatType.NORMAL;
            }
            client.sendTCP(message);

            GameLogger.info("Sent chat message from " + message.sender +
                ": " + message.content);

        } catch (Exception e) {
            GameLogger.error("Failed to send chat message: " + e.getMessage());

            if (!isConnected()) {
                handleConnectionFailure(e);
            }

            if (chatMessageHandler != null) {
                chatMessageHandler.accept(message);
            }
        }
    }

    public NetworkProtocol.ChatMessage createSystemMessage(String content) {
        NetworkProtocol.ChatMessage message = new NetworkProtocol.ChatMessage();
        message.sender = "System";
        message.content = content;
        message.timestamp = System.currentTimeMillis();
        message.type = NetworkProtocol.ChatType.SYSTEM;
        return message;
    }

    private void handleChatMessage(NetworkProtocol.ChatMessage message) {
        if (message == null || message.content == null) {
            return;
        }
        chatMessageQueue.offer(message);
        GameLogger.info("Client enqueued chat message: " + message.sender + ": " + message.content);

    }

    public String getLocalUsername() {
        return localUsername;
    }

    public void dispose() {
        synchronized (connectionLock) {
            if (isDisposing.get()) {
                return;
            }

            isDisposing.set(true);

            scheduler.shutdownNow();
            try {
                scheduler.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (client != null) {
                try {
                    NetworkProtocol.ForceDisconnect disconnect = new NetworkProtocol.ForceDisconnect();
                    disconnect.reason = "Client closing";
                    client.sendTCP(disconnect);
                } catch (Exception ignored) {
                }
                client.close();
            }

            if (disconnectHandler != null) {
                disconnectHandler.cleanup();
            }
            GameLogger.info("GameClient disposed");
        }
    }

    public void shutdown() {
        isShuttingDown.set(true);
        dispose();
    }

    private void attemptReconnection() {
        synchronized (connectionLock) {
            if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
                GameLogger.error("Max reconnection attempts reached");
                if (chatMessageHandler != null) {
                    NetworkProtocol.ChatMessage message = createSystemMessage(
                        "Failed to reconnect after " + MAX_RECONNECT_ATTEMPTS + " attempts"
                    );
                    chatMessageHandler.accept(message);
                }
                return;
            }

            reconnectAttempts++;
            connectIfNeeded(() -> {
                // Successfully connected
                reconnectAttempts = 0;
                GameLogger.info("Successfully reconnected to server");
            }, (errorMsg) -> {
                // Failed to connect
                reconnectAttempts++;
                GameLogger.error("Failed to reconnect to server: " + errorMsg);
            }, REGISTRATION_CONNECT_TIMEOUT_MS);
        }
    }
    private void handleCompressedChunkData(NetworkProtocol.CompressedChunkData compressed) {
        try {
            LZ4Factory factory = LZ4Factory.fastestInstance();
            LZ4SafeDecompressor decompressor = factory.safeDecompressor();
            // Allocate an array for the original uncompressed data.
            byte[] restored = new byte[compressed.originalLength];
            decompressor.decompress(compressed.data, 0, compressed.data.length, restored, 0);

            // Deserialize the uncompressed data to a ChunkData object using Kryo.
            ByteArrayInputStream bais = new ByteArrayInputStream(restored);
            Input input = new Input(bais);
            Kryo kryo = new Kryo();
            // IMPORTANT: Register all classes exactly as on the server!
            NetworkProtocol.registerClasses(kryo);
            kryo.setReferences(false);
            NetworkProtocol.ChunkData chunkData = kryo.readObject(input, NetworkProtocol.ChunkData.class);
            input.close();
            bais.close();

            final Vector2 chunkPos = new Vector2(chunkData.chunkX, chunkData.chunkY);
            Gdx.app.postRunnable(() -> {
                try {
                    World world = GameContext.get().getWorld();
                    if (world == null) return;

                    // Update the world’s chunk map from the received ChunkData.
                    world.processChunkData(chunkData);

                    // Build a BiomeTransitionResult from the new biome info.
                    BiomeTransitionResult transition = new BiomeTransitionResult(
                        world.getBiomeManager().getBiome(chunkData.primaryBiomeType),
                        (chunkData.secondaryBiomeType != null ? world.getBiomeManager().getBiome(chunkData.secondaryBiomeType) : null),
                        chunkData.biomeTransitionFactor
                    );
                    world.storeBiomeTransition(chunkPos, transition);
                    pendingChunks.remove(chunkPos);
                    GameLogger.info("Processed chunk " + chunkPos + " with " +
                        (chunkData.worldObjects != null ? chunkData.worldObjects.size() : 0) + " objects");
                } catch (Exception e) {
                    GameLogger.error("Error processing chunk data: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            GameLogger.error("Error decompressing chunk data: " + e.getMessage());
        }
    }


    /**
     * Recalculate the sliding window of chunks based on the player's current chunk position.
     * Enqueue any missing chunks.
     */
    private void requestChunksAroundPlayer() {
        if (!isAuthenticated.get() || !isConnected() || isSinglePlayer) {
            return;
        }
        World world = GameContext.get().getWorld();
        Player player = GameContext.get().getPlayer();
        if (world == null || player == null) return;

        int playerTileX = GameContext.get().getPlayer().getTileX();
        int playerTileY = GameContext.get().getPlayer().getTileY();
        int playerChunkX = Math.floorDiv(playerTileX, Chunk.CHUNK_SIZE);
        int playerChunkY = Math.floorDiv(playerTileY, Chunk.CHUNK_SIZE);
        Vector2 playerChunkPos = new Vector2(playerChunkX, playerChunkY);

        // For each chunk within CHUNK_LOAD_RADIUS around the player, if it’s missing and not already pending, request it.
        for (int dx = -CHUNK_LOAD_RADIUS; dx <= CHUNK_LOAD_RADIUS; dx++) {
            for (int dy = -CHUNK_LOAD_RADIUS; dy <= CHUNK_LOAD_RADIUS; dy++) {
                Vector2 chunkPos = new Vector2(playerChunkX + dx, playerChunkY + dy);
                if (!world.getChunks().containsKey(chunkPos) && !pendingChunks.contains(chunkPos)) {
                    chunkRequestQueue.offer(chunkPos);
                }
            }
        }

        // Optionally, unload chunks that are far away (outside the load radius)
        unloadFarChunks(playerChunkPos);
    }

    /**
     * Unload (remove from memory) any chunks that are outside the given radius.
     */
    private void unloadFarChunks(Vector2 playerChunkPos) {
        World world = GameContext.get().getWorld();
        if (world == null) return;
        int unloadThreshold = 5; // increase this threshold to keep more chunks loaded
        List<Vector2> keysToRemove = new ArrayList<>();
        for (Vector2 key : world.getChunks().keySet()) {
            if (Math.abs(key.x - playerChunkPos.x) > unloadThreshold || Math.abs(key.y - playerChunkPos.y) > unloadThreshold) {
                keysToRemove.add(key);
            }
        }
        for (Vector2 key : keysToRemove) {
            world.getChunks().remove(key);
            GameLogger.info("Unloaded chunk at " + key);
        }
    }

    private void handleBuildingPlacement(NetworkProtocol.BuildingPlacement bp) {
        // Do not process your own placement if already applied.
        if (bp.username.equals(getLocalUsername())) return;

        // Place the building in the local world.
        for (int x = 0; x < bp.width; x++) {
            for (int y = 0; y < bp.height; y++) {
                String typeId = bp.blockTypeIds[x][y];
                boolean isFlipped = bp.flippedFlags[x][y];
                if (typeId == null || typeId.isEmpty()) continue;
                PlaceableBlock.BlockType type = PlaceableBlock.BlockType.fromItemId(typeId);
                int tileX = bp.startX + x;
                int tileY = bp.startY + y;
                GameContext.get().getWorld().getBlockManager().placeBlock(type, tileX, tileY);
                PlaceableBlock block = GameContext.get().getWorld().getBlockManager().getBlockAt(tileX, tileY);
                if (block != null && isFlipped) {
                    block.toggleFlip();
                }
            }
        }
        GameLogger.info("Processed building placement from " + bp.username + " at (" + bp.startX + "," + bp.startY + ")");
    }

    private void handleReceivedMessage(Object object) {
        if (object instanceof NetworkProtocol.CompressedChunkData) {
            handleCompressedChunkData((NetworkProtocol.CompressedChunkData) object);
            return;
        }
        if (object instanceof NetworkProtocol.ServerShutdown) {
            NetworkProtocol.ServerShutdown shutdown = (NetworkProtocol.ServerShutdown) object;
            handleDisconnect(shutdown.reason);
            return;
        }
        if (object instanceof NetworkProtocol.LoginResponse) {
            NetworkProtocol.LoginResponse response = (NetworkProtocol.LoginResponse) object;
            handleLoginResponse(response);
        }
        if (object instanceof NetworkProtocol.ForceDisconnect) {
            NetworkProtocol.ForceDisconnect disconnect = (NetworkProtocol.ForceDisconnect) object;
            GameLogger.error("Received ForceDisconnect from server: " + disconnect.reason);
            handleDisconnect("Connection lost");
            return;
        }
        if (!isAuthenticated.get()) {
            if (!(object instanceof NetworkProtocol.LoginResponse ||
                object instanceof NetworkProtocol.ConnectionResponse)) {
                pendingMessages.offer(object);
                return;
            }
        }


        try {
            if (object instanceof NetworkProtocol.LoginResponse) {
                GameLogger.info("CRITICAL - Received LoginResponse");
                NetworkProtocol.LoginResponse response = (NetworkProtocol.LoginResponse) object;
                GameLogger.info("CRITICAL - Login success: " + response.success);
                GameLogger.info("CRITICAL - Login message: " + response.message);
                handleLoginResponse((NetworkProtocol.LoginResponse) object);
            } else if (object instanceof NetworkProtocol.ChatMessage) {
                handleChatMessage((NetworkProtocol.ChatMessage) object);
            } else if (object instanceof NetworkProtocol.ChestUpdate) {
                NetworkProtocol.ChestUpdate update = (NetworkProtocol.ChestUpdate) object;
                // Assume you store a reference to the open chest screen (if any) in GameContext:
                ChestScreen chestScreen = GameContext.get().getGameScreen().getChestScreen();
                if (chestScreen != null && chestScreen.getChestData().chestId.equals(update.chestId)) {
                    // Update the chestData with the items from the update
                    chestScreen.getChestData().setItems(update.items);
                    // Optionally, you can call your helper method:
                    // chestScreen.updateChestData(updatedChestData);
                    // (if you choose to construct an updated ChestData from update)
                    chestScreen.updateUI();
                    GameContext.get().getGameScreen().setChestScreen(chestScreen);
                }
                return;
            } else if (object instanceof NetworkProtocol.PlayerUpdate) {
                handlePlayerUpdate((NetworkProtocol.PlayerUpdate) object);
            } else if (object instanceof NetworkProtocol.PlayerJoined) {
                handlePlayerJoined((NetworkProtocol.PlayerJoined) object);
            } else if (object instanceof NetworkProtocol.PlayerLeft) {
                handlePlayerLeft((NetworkProtocol.PlayerLeft) object);
            } else if (object instanceof NetworkProtocol.BuildingPlacement) {
                handleBuildingPlacement((NetworkProtocol.BuildingPlacement) object);
            } else if (object instanceof NetworkProtocol.PlayerList) {
                handlePlayerList((NetworkProtocol.PlayerList) object);
            } else if (object instanceof NetworkProtocol.PingResponse) {
                handlePingResponse((NetworkProtocol.PingResponse) object);
                return;
            }

            if (object instanceof NetworkProtocol.ItemDrop) {
                handleItemDrop((NetworkProtocol.ItemDrop) object);
            } else if (object instanceof NetworkProtocol.PlayerPosition) {
                handlePlayerPosition((NetworkProtocol.PlayerPosition) object);
            } else if (object instanceof NetworkProtocol.WildPokemonSpawn) {
                handlePokemonSpawn((NetworkProtocol.WildPokemonSpawn) object);
            } else if (object instanceof NetworkProtocol.WildPokemonDespawn) {
                handlePokemonDespawn((NetworkProtocol.WildPokemonDespawn) object);
            } else if (object instanceof NetworkProtocol.PokemonUpdate) {
                handlePokemonUpdate((NetworkProtocol.PokemonUpdate) object);
            } else if (object instanceof NetworkProtocol.WorldStateUpdate) {
                handleWorldStateUpdate((NetworkProtocol.WorldStateUpdate) object);
            } else if (object instanceof NetworkProtocol.WorldObjectUpdate) {
                handleWorldObjectUpdate((NetworkProtocol.WorldObjectUpdate) object);
            } else if (object instanceof NetworkProtocol.BlockPlacement) {
                handleBlockPlacement((NetworkProtocol.BlockPlacement) object);
            } else if (object instanceof NetworkProtocol.PlayerAction) {
                handlePlayerAction((NetworkProtocol.PlayerAction) object);
            }
            if (object instanceof NetworkProtocol.PokemonBatchUpdate) {
                NetworkProtocol.PokemonBatchUpdate batchUpdate = (NetworkProtocol.PokemonBatchUpdate) object;
                for (NetworkProtocol.PokemonUpdate update : batchUpdate.updates) {
                    handlePokemonUpdate(update);
                }
            }

        } catch (Exception e) {
            GameLogger.error("Error handling network message: " + e.getMessage());
        }

    }

    private void handlePlayerList(NetworkProtocol.PlayerList list) {
        for (NetworkProtocol.PlayerInfo info : list.players) {
            playerPingMap.put(info.username, info.ping);
            // Optionally, if you maintain OtherPlayer objects:
            OtherPlayer op = otherPlayers.get(info.username);
            if (op != null) {
                op.setPing(info.ping);
            }
        }
    }

    public Map<String, Integer> getPlayerPingMap() {
        return new HashMap<>(playerPingMap);
    }

    public int getLocalPing() {
        return localPing;
    }

    private void sendPingRequest() {
        NetworkProtocol.PingRequest ping = new NetworkProtocol.PingRequest();
        ping.timestamp = System.currentTimeMillis();
        client.sendTCP(ping);
    }

    private void handleItemDrop(NetworkProtocol.ItemDrop drop) {
        if (drop.username.equals(getLocalUsername())) {
            // This is our own drop, already handled locally
            return;
        }

        // Spawn the dropped item for other players
        GameContext.get().getWorld().getItemEntityManager()
            .spawnItemEntity(drop.itemData, drop.x, drop.y);

        // Play drop sound for nearby drops
        float distance = Vector2.dst(
            GameContext.get().getPlayer().getX(),
            GameContext.get().getPlayer().getY(),
            drop.x, drop.y
        );
        if (distance < World.TILE_SIZE * 10) {
            AudioManager.getInstance().playSound(AudioManager.SoundEffect.ITEM_PICKUP_OW);
        }
    }

    private void handlePlayerAction(NetworkProtocol.PlayerAction action) {
        Gdx.app.postRunnable(() -> {
            OtherPlayer otherPlayer = otherPlayers.get(action.playerId);
            if (otherPlayer != null) {
                otherPlayer.updateAction(action);
            }
        });
    }

    private void handleBlockPlacement(NetworkProtocol.BlockPlacement placement) {
        Gdx.app.postRunnable(() -> {
            if (GameContext.get().getWorld() != null) {
                PlaceableBlock.BlockType type = PlaceableBlock.BlockType.fromItemId(placement.blockTypeId);
                if (type != null) {
                    if (placement.action == NetworkProtocol.BlockAction.PLACE) {
                        GameContext.get().getWorld().getBlockManager().placeBlock(type, placement.tileX, placement.tileY);
                        GameLogger.info("Block placed by " + placement.username + " at (" + placement.tileX + ", " + placement.tileY + ")");
                    } else if (placement.action == NetworkProtocol.BlockAction.REMOVE) {
                        GameContext.get().getWorld().getBlockManager().removeBlock(placement.tileX, placement.tileY);
                        GameLogger.info("Block removed by " + placement.username + " at (" + placement.tileX + ", " + placement.tileY + ")");
                    }
                } else {
                    GameLogger.error("Unknown block type: " + placement.blockTypeId);
                }
            }
        });
    }

    private void handlePlayerPosition(NetworkProtocol.PlayerPosition positionMsg) {
        if (positionMsg == null || positionMsg.players == null) {
            GameLogger.error("Received empty PlayerPosition message.");
            return;
        }

        Gdx.app.postRunnable(() -> {
            try {
                synchronized (otherPlayers) {
                    for (Map.Entry<String, NetworkProtocol.PlayerUpdate> entry : positionMsg.players.entrySet()) {
                        String username = entry.getKey();
                        if (username.equals(localUsername)) continue;

                        NetworkProtocol.PlayerUpdate update = entry.getValue();
                        GameLogger.error("Received update for " + username + " at (" + update.x + "," + update.y + ")");

                        OtherPlayer otherPlayer = otherPlayers.computeIfAbsent(username,
                            k -> new OtherPlayer(username, update.x, update.y));

                        otherPlayer.updateFromNetwork(update);
                        playerUpdates.put(username, update);
                    }
                }
            } catch (Exception e) {
                GameLogger.error("Error in handlePlayerPosition: " + e.getMessage());
            }
        });
    }



    public void handleDisconnect(String reason) {
        synchronized (connectionLock) {
            connectionState = ConnectionState.DISCONNECTED;
            isConnected.set(false);
            isAuthenticated.set(false);

            // Save any last known state, if needed:
            if (GameContext.get().getPlayer() != null) {
                lastKnownState = GameContext.get().getPlayer().getPlayerData();
            }

            // Actual cleanup of the network client:
            cleanupConnection();

            // Show the DisconnectionScreen (via DisconnectionManager) UNLESS we are already disposing:
            if (!isDisposing.get() && disconnectHandler != null) {
                disconnectHandler.handleDisconnect(reason);
            }
        }
    }


    private void cleanupConnection() {
        if (client != null) {
            try {
                client.close();
                client.stop();
                client = null;
            } catch (Exception e) {
                GameLogger.error("Error cleaning up connection: " + e.getMessage());
            }
        }
    }

    private void updatePokemonStates(float deltaTime) {
        for (Map.Entry<UUID, WildPokemon> entry : trackedWildPokemon.entrySet()) {
            WildPokemon pokemon = entry.getValue();
            NetworkSyncData syncData = syncedPokemonData.get(entry.getKey());

            if (syncData != null && syncData.targetPosition != null) {
                updatePokemonPosition(pokemon, syncData, deltaTime);
            }
            pokemon.update(deltaTime);
        }
    }

    private void updatePokemonPosition(WildPokemon pokemon, NetworkSyncData syncData, float deltaTime) {
        syncData.interpolationProgress = Math.min(1.0f,
            syncData.interpolationProgress + deltaTime * INTERPOLATION_SPEED);

        float newX = lerp(pokemon.getX(), syncData.targetPosition.x, syncData.interpolationProgress);
        float newY = lerp(pokemon.getY(), syncData.targetPosition.y, syncData.interpolationProgress);

        pokemon.setX(newX);
        pokemon.setY(newY);
        pokemon.updateBoundingBox();
    }

    private float lerp(float start, float end, float alpha) {
        return start + (end - start) * alpha;
    }

    private void updateOtherPlayers(float deltaTime) {
        otherPlayers.values().forEach(player -> {
            player.update(deltaTime);
        });
    }

    public void tick() {
        if (!isConnected() || isSinglePlayer) return;
        if (isInitialized && !processingMessages) {
            processQueuedMessages();
        }
    }


    public void sendRegisterRequest(String username, String password,
                                    Consumer<NetworkProtocol.RegisterResponse> onResponse,
                                    Consumer<String> onError) {
        if (!GameContext.get().isMultiplayer()) {
            GameLogger.info("Registration not needed in single player mode");
            return;
        }
        if (!isConnected.get() || client == null) {
            onError.accept("Not connected to server for registration");
            return;
        }
        if (username == null || username.trim().isEmpty() ||
            password == null || password.trim().isEmpty()) {
            onError.accept("Username and password are required");
            return;
        }
        String secureUsername = username.trim();
        String securePassword = password.trim();
        NetworkProtocol.RegisterRequest request = new NetworkProtocol.RegisterRequest();
        request.username = secureUsername;
        request.password = securePassword;
        GameLogger.info("Sending registration request for: " + secureUsername);
        client.sendTCP(request);
    }

    private void processChatMessages() {
        NetworkProtocol.ChatMessage message;
        while ((message = chatMessageQueue.poll()) != null) {
            final NetworkProtocol.ChatMessage finalMessage = message;
            Gdx.app.postRunnable(() -> {
                if (chatMessageHandler != null) {
                    chatMessageHandler.accept(finalMessage);
                }
            });
        }
    }

    public boolean isInitialized() {
        return fullyInitialized;
    }

    public void setInitialized(boolean initialized) {
        this.isInitialized = initialized;
    }

    public boolean isInitializing() {
        return isInitializing;
    }

    public void requestChunk(Vector2 chunkPos) {
        if (!isConnected() || !isAuthenticated()) {
            return;
        }

        try {
            if (pendingChunks.contains(chunkPos)) {
                GameLogger.info("Chunk already requested: " + chunkPos);
                return;
            }

            NetworkProtocol.ChunkRequest request = new NetworkProtocol.ChunkRequest();
            // Ensure coordinates are sent as-is, including negative values
            request.chunkX = (int) chunkPos.x;
            request.chunkY = (int) chunkPos.y;
            request.timestamp = System.currentTimeMillis();

            pendingChunks.add(chunkPos);
            client.sendTCP(request);
            GameLogger.info("Requested chunk at " + chunkPos);

            // Add timeout handling
            scheduler.schedule(() -> {
                if (pendingChunks.contains(chunkPos)) {
                    GameLogger.error("Chunk request timeout for " + chunkPos + " - retrying");
                    pendingChunks.remove(chunkPos);
                    requestChunk(chunkPos); // Retry the request
                }
            }, 5000, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            GameLogger.error("Failed to request chunk: " + e.getMessage());
            pendingChunks.remove(chunkPos);
        }
    }

    private void handleWorldObjectUpdate(NetworkProtocol.WorldObjectUpdate update) {
        if (update == null || GameContext.get().getWorld() == null) {
            return;
        }

        Gdx.app.postRunnable(() -> {
            try {
                WorldObject.WorldObjectManager objectManager = GameContext.get().getWorld().getObjectManager();
                switch (update.type) {
                    case ADD:
                        // Create and add new object from update data.
                        WorldObject newObj = new WorldObject();
                        newObj.updateFromData(update.data);
                        objectManager.addObjectToChunk(newObj);
                        break;

                    case REMOVE:
                        // Ensure tile coordinates are present in the update.
                        if (update.data == null || !update.data.containsKey("tileX") || !update.data.containsKey("tileY")) {
                            GameLogger.error("Client: WorldObjectUpdate REMOVE missing tile position data.");
                            return;
                        }
                        // Parse the canonical tile coordinates.
                        int baseTileX = Integer.parseInt(update.data.get("tileX").toString());
                        int baseTileY = Integer.parseInt(update.data.get("tileY").toString());
                        // Use Math.floorDiv to compute the chunk coordinates reliably.
                        int chunkX = Math.floorDiv(baseTileX, Chunk.CHUNK_SIZE);
                        int chunkY = Math.floorDiv(baseTileY, Chunk.CHUNK_SIZE);
                        Vector2 chunkPos = new Vector2(chunkX, chunkY);

                        // Remove the object from the WorldObjectManager.
                        objectManager.removeObjectFromChunk(chunkPos, update.objectId, baseTileX, baseTileY);

                        // **New Code:** Also update the local world's chunk.
                        World world = GameContext.get().getWorld();
                        if (world != null) {
                            // Here we assume that the world's chunk retrieval uses the same coordinate system.
                            // (If your chunk lookup method expects pixel coordinates, adjust accordingly.)
                            Chunk chunk = world.getChunkAtPosition(baseTileX * World.TILE_SIZE, baseTileY * World.TILE_SIZE);
                            if (chunk != null) {
                                // Remove the world object from the chunk's internal list.
                                // (If you don’t already have a helper method, you can iterate through the chunk’s object list.)
                                chunk.getWorldObjects().removeIf(obj -> obj.getId().equals(update.objectId));
                                // Mark the chunk as dirty so that it will be re–rendered.
                                chunk.setDirty(true);
                            }
                        }
                        break;

                    case UPDATE:
                        objectManager.updateObject(update);
                        break;

                    default:
                        GameLogger.error("Client: Unknown world object update type: " + update.type);
                        break;
                }
            } catch (Exception e) {
                GameLogger.error("Error processing world object update on client: " + e.getMessage());
            }
        });
    }

    public Player getActivePlayer() {
        return GameContext.get().getPlayer();
    }

    public void setActivePlayer(Player activePlayer) {
        GameContext.get().setPlayer(activePlayer);
    }

    private void processQueuedMessages() {
        if (pendingMessages.isEmpty()) {
            return;
        }

        GameLogger.info("Processing " + pendingMessages.size() + " queued messages");

        Object message;
        while ((message = pendingMessages.poll()) != null) {
            try {
                handleReceivedMessage(message);
            } catch (Exception e) {
                GameLogger.error("Error processing queued message: " + e.getMessage());
            }
        }
    }

    private void processChunkQueue() {
        long now = System.currentTimeMillis();
        if (!chunkRequestQueue.isEmpty() &&
            now - lastRequestTime >= CHUNK_REQUEST_INTERVAL &&
            pendingChunks.size() < MAX_CONCURRENT_CHUNK_REQUESTS) {

            Vector2 nextChunk = chunkRequestQueue.poll();
            // Before requesting, double-check that this chunk is still missing.
            if (nextChunk != null && !GameContext.get().getWorld().getChunks().containsKey(nextChunk)
                && !loadingChunks.containsKey(nextChunk)) {
                requestChunk(nextChunk);
                lastRequestTime = now;
            }
        }
    }

    private void handlePlayerUpdate(NetworkProtocol.PlayerUpdate update) {
        // Ignore our own update.
        if (update == null || update.username == null || update.username.equals(localUsername)) {
            return;
        }

        Gdx.app.postRunnable(() -> {
            synchronized (otherPlayers) {
                OtherPlayer otherPlayer = otherPlayers.get(update.username);
                if (otherPlayer == null) {
                    // Create a new OtherPlayer if one does not exist yet.
                    otherPlayer = new OtherPlayer(update.username, update.x, update.y);
                    // Set the run flag for the new player:
                    otherPlayer.setWantsToRun(update.wantsToRun);
                    otherPlayers.put(update.username, otherPlayer);
                    GameLogger.info("Created new OtherPlayer: " + update.username);
                }
                // Always update the remote player’s data:
                otherPlayer.setWantsToRun(update.wantsToRun);
                otherPlayer.updateFromNetwork(update);
                playerUpdates.put(update.username, update);
            }
        });
    }


    private void handlePlayerJoined(NetworkProtocol.PlayerJoined joinMsg) {
        final String joinKey = joinMsg.username + "_" + joinMsg.timestamp;
        Gdx.app.postRunnable(() -> {
            // =========== IMPORTANT FIX ===========
            if (joinMsg.username.equals(localUsername)) {
                // This "join" event is actually our own player.
                // Do NOT create an OtherPlayer. Just return.
                return;
            }
            // ======================================

            synchronized (otherPlayers) {
                if (recentJoinEvents.contains(joinKey)) {
                    // We’ve already processed this join event
                    return;
                }

                if (otherPlayers.containsKey(joinMsg.username)) {
                    GameLogger.info("Player " + joinMsg.username +
                        " already exists, updating position");
                    OtherPlayer existingPlayer = otherPlayers.get(joinMsg.username);
                    existingPlayer.setPosition(new Vector2(joinMsg.x, joinMsg.y));
                    return;
                }

                // Create a new OtherPlayer only for different usernames
                OtherPlayer newPlayer = new OtherPlayer(joinMsg.username,
                    joinMsg.x, joinMsg.y);
                otherPlayers.put(joinMsg.username, newPlayer);

                recentJoinEvents.add(joinKey);
                com.badlogic.gdx.utils.Timer.schedule(new com.badlogic.gdx.utils.Timer.Task() {
                    @Override
                    public void run() {
                        recentJoinEvents.remove(joinKey);
                    }
                }, 5);

                if (chatMessageHandler != null) {
                    NetworkProtocol.ChatMessage joinNotification = new NetworkProtocol.ChatMessage();
                    joinNotification.sender = "System";
                    joinNotification.content = joinMsg.username + " has joined the game";
                    joinNotification.type = NetworkProtocol.ChatType.SYSTEM;
                    joinNotification.timestamp = System.currentTimeMillis();

                    chatMessageHandler.accept(joinNotification);
                }
            }
        });
    }

    public void sendBlockPlacement(NetworkProtocol.BlockPlacement placement) {
        if (!isConnected() || !isAuthenticated()) {
            return;
        }
        try {
            client.sendTCP(placement);
        } catch (Exception e) {
            GameLogger.error("Failed to send block placement: " + e.getMessage());
        }
    }

    private void handlePlayerLeft(NetworkProtocol.PlayerLeft leftMsg) {
        Gdx.app.postRunnable(() -> {
            // Remove the OtherPlayer instance
            OtherPlayer leftPlayer = otherPlayers.remove(leftMsg.username);
            if (leftPlayer != null) {
                leftPlayer.dispose();
            }

            // Remove any pending player updates for this username
            playerUpdates.remove(leftMsg.username);

            // **** Remove the player's ping data ****
            playerPingMap.remove(leftMsg.username);

            // Optionally, notify via chat that the player has left:
            if (chatMessageHandler != null) {
                NetworkProtocol.ChatMessage leaveNotification = new NetworkProtocol.ChatMessage();
                leaveNotification.sender = "System";
                leaveNotification.content = leftMsg.username + " has left the game";
                leaveNotification.type = NetworkProtocol.ChatType.SYSTEM;
                leaveNotification.timestamp = System.currentTimeMillis();
                chatMessageHandler.accept(leaveNotification);
            }
        });
    }

    private void handlePokemonSpawn(NetworkProtocol.WildPokemonSpawn spawnData) {
        if (spawnData == null || spawnData.uuid == null || spawnData.data == null) {
            GameLogger.error("Received invalid Pokemon spawn data");
            return;
        }

        Gdx.app.postRunnable(() -> {
            try {
                if (trackedWildPokemon.containsKey(spawnData.uuid)) {
                    return;
                }
                TextureRegion overworldSprite = TextureManager.getOverworldSprite(spawnData.data.getName());

                if (overworldSprite == null) {
                    GameLogger.error("Could not load sprite for Pokemon: " + spawnData.data.getName());
                    return;
                }

                WildPokemon pokemon = new WildPokemon(
                    spawnData.data.getName(),
                    spawnData.data.getLevel(),
                    (int) spawnData.x,
                    (int) spawnData.y,
                    overworldSprite
                );
                pokemon.setWorld(GameContext.get().getWorld());

                pokemon.setUuid(spawnData.uuid);
                pokemon.setDirection("down");
                pokemon.setSpawnTime(spawnData.timestamp / 1000L);

                trackedWildPokemon.put(spawnData.uuid, pokemon);
                syncedPokemonData.put(spawnData.uuid, new NetworkSyncData());

                if (GameContext.get().getWorld() != null && GameContext.get().getWorld().getPokemonSpawnManager() != null) {
                    GameContext.get().getWorld().getPokemonSpawnManager().addPokemonToChunk(
                        pokemon,
                        new Vector2(spawnData.x, spawnData.y)
                    );
                }
            } catch (Exception e) {
                GameLogger.error("Error handling Pokemon spawn: " + e.getMessage());
            }
        });
    }

    private void handlePokemonDespawn(NetworkProtocol.WildPokemonDespawn despawnData) {
        if (despawnData == null || despawnData.uuid == null) {
            return;
        }

        Gdx.app.postRunnable(() -> {
            try {
                WildPokemon pokemon = trackedWildPokemon.remove(despawnData.uuid);
                syncedPokemonData.remove(despawnData.uuid);

                if (pokemon != null && GameContext.get().getWorld() != null) {
                    pokemon.startDespawnAnimation();

                    com.badlogic.gdx.utils.Timer.schedule(new com.badlogic.gdx.utils.Timer.Task() {
                        @Override
                        public void run() {
                            if (GameContext.get().getWorld() == null || GameContext.get().getWorld().getPokemonSpawnManager() == null) {
                                GameLogger.error("World or its PokemonSpawnManager is null; cannot remove spawned Pokémon.");
                                return;
                            }

                            GameContext.get().getWorld().getPokemonSpawnManager()
                                .removePokemon(despawnData.uuid);
                        }
                    }, 1.0f);
                }

                GameLogger.info("Handled Pokemon despawn for UUID: " + despawnData.uuid);

            } catch (Exception e) {
                GameLogger.error("Error handling Pokemon despawn: " + e.getMessage());
            }
        });
    }

    private void handlePokemonUpdate(NetworkProtocol.PokemonUpdate update) {
        if (update == null || update.uuid == null) return;

        Gdx.app.postRunnable(() -> {
            WildPokemon pokemon = trackedWildPokemon.get(update.uuid);
            NetworkSyncData syncData = syncedPokemonData.get(update.uuid);

            if (pokemon == null || syncData == null) {
                requestPokemonSpawnData(update.uuid);
                return;
            }

            syncData.targetPosition = new Vector2(update.x, update.y);
            syncData.direction = update.direction;
            syncData.isMoving = update.isMoving;
            syncData.lastUpdateTime = System.currentTimeMillis();
            syncData.interpolationProgress = 0f;

            pokemon.setDirection(update.direction);
            pokemon.setMoving(update.isMoving);

            if (update.level > 0) pokemon.setLevel(update.level);
            if (update.currentHp > 0) pokemon.setCurrentHp(update.currentHp);
            pokemon.setSpawnTime(update.timestamp / 1000L);

            if (pokemonUpdateHandler != null) {
                pokemonUpdateHandler.onUpdate(update);
            }
        });
    }

    public void sendPokemonUpdate(NetworkProtocol.PokemonUpdate update) {
        if (!isAuthenticated.get() || connectionState != ConnectionState.CONNECTED) return;

        try {
            if (update.timestamp == 0) {
                update.timestamp = System.currentTimeMillis();
            }
            client.sendTCP(update);
        } catch (Exception e) {
            GameLogger.error("Failed to send Pokemon update: " + e.getMessage());
        }
    }

    private void requestPokemonSpawnData(UUID pokemonId) {
        if (!isAuthenticated.get() || connectionState != ConnectionState.CONNECTED) return;

        try {
            NetworkProtocol.PokemonSpawnRequest request = new NetworkProtocol.PokemonSpawnRequest();
            request.uuid = pokemonId;
            client.sendTCP(request);
        } catch (Exception e) {
            GameLogger.error("Failed to request Pokemon spawn data: " + e.getMessage());
        }
    }

    public void sendWorldObjectUpdate(NetworkProtocol.WorldObjectUpdate update) {
        if (isSinglePlayer || !isConnected() || !isAuthenticated()) {
            return;
        }

        try {


            client.sendTCP(update);

        } catch (Exception e) {
            GameLogger.error("Failed to send world object update: " + e.getMessage());
            handleConnectionFailure(e);
        }
    }

    public void sendPlayerAction(NetworkProtocol.PlayerAction action) {
        if (!isConnected() || !isAuthenticated()) {
            return;
        }

        try {
            // Add position data if missing
            if (action.tileX == 0 && action.tileY == 0 && GameContext.get().getPlayer() != null) {
                action.tileX = (int) (GameContext.get().getPlayer().getX() / World.TILE_SIZE);
                action.tileY = (int) (GameContext.get().getPlayer().getY() / World.TILE_SIZE);
                GameLogger.info("Sending player action " + action.actionType +
                    " at position (" + action.tileX + "," + action.tileY +
                    ") direction: " + action.direction);
            }

            client.sendTCP(action);
        } catch (Exception e) {
            GameLogger.error("Failed to send player action: " + e.getMessage());
        }
    }

    public void sendPokemonDespawn(UUID pokemonId) {
        if (!isConnected() || client == null || isSinglePlayer) {
            return;
        }

        try {
            NetworkProtocol.WildPokemonDespawn despawnUpdate = new NetworkProtocol.WildPokemonDespawn();
            despawnUpdate.uuid = pokemonId;
            despawnUpdate.timestamp = System.currentTimeMillis();

            client.sendTCP(despawnUpdate);
            trackedWildPokemon.remove(pokemonId);
            syncedPokemonData.remove(pokemonId);

            GameLogger.info("Sent Pokemon despawn for ID: " + pokemonId);
        } catch (Exception e) {
            GameLogger.error("Failed to send Pokemon despawn: " + e.getMessage());
            if (!isConnected()) {
                handleConnectionFailure(e);
            }
        }
    }

    public void setChatMessageHandler(Consumer<NetworkProtocol.ChatMessage> handler) {
        this.chatMessageHandler = handler;
    }

    public Map<String, OtherPlayer> getOtherPlayers() {
        return new HashMap<>(otherPlayers);
    }

    public boolean isAuthenticated() {
        return isAuthenticated.get();
    }

    public boolean isConnected() {
        return connectionState == ConnectionState.CONNECTED || connectionState == ConnectionState.AUTHENTICATED;
    }private boolean suppressDisconnectHandling = false;

    public void setSuppressDisconnectHandling(boolean suppress) {
        this.suppressDisconnectHandling = suppress;
    }

    private void handleLoginResponse(NetworkProtocol.LoginResponse response) {
        if (response.success) {
            isAuthenticated.set(true);
            localUsername = response.username;

            try {
                // Initialize basic world settings from the server response.
                initializeWorldBasic(response.seed, response.worldTimeInMinutes, response.dayLength);

                // **FIX:** If the world is null (for example, because the previous world was disposed),
                // force a reinitialization of the world.
                if (GameContext.get().getWorld() == null) {
                    GameContext.get().setWorld(new World(worldData));
                    GameLogger.info("Reinitialized world for multiplayer session.");
                }

                Gdx.app.postRunnable(() -> {
                    if (GameContext.get().getPlayer() == null) {
                        Player player = new Player(response.username, GameContext.get().getWorld());
                        player.setPlayerData(response.playerData);
                        GameContext.get().setPlayer(player);
                    }
                    GameContext.get().getPlayer().setX(response.x);
                    GameContext.get().getPlayer().setY(response.y);
                    GameContext.get().getPlayer().setPlayerData(response.playerData);
                    GameContext.get().getPlayer().updateFromPlayerData(response.playerData);
                    processQueuedMessages();

                    if (loginResponseListener != null) {
                        loginResponseListener.accept(response);
                    }
                });
            } catch (Exception e) {
                GameLogger.error("Initial world setup failed: " + e.getMessage());
                handleLoginFailure("World initialization failed: " + e.getMessage());
            }
        } else {
            handleLoginFailure(response.message);
        }
    }

    private void handleLoginFailure(String message) {
        loginRequestSent = false;
        GameLogger.error("Login failed: " + message);

        if (loginResponseListener != null) {
            Gdx.app.postRunnable(() -> {
                NetworkProtocol.LoginResponse response = new NetworkProtocol.LoginResponse();
                response.success = false;
                response.message = "Connection failed: " + message;
                loginResponseListener.accept(response);
            });
        }

    }

    private void initializeWorldBasic(long seed, double worldTimeInMinutes, float dayLength) {
        try {
            GameLogger.info("Initializing basic world with seed: " + seed);
            if (worldData == null) {
                worldData = new WorldData("multiplayer_world");
                GameLogger.info("Created new WorldData instance");
            }

            WorldData.WorldConfig config = new WorldData.WorldConfig(seed);
            worldData.setConfig(config);
            worldData.setWorldTimeInMinutes(worldTimeInMinutes);
            worldData.setDayLength(dayLength);

            this.worldSeed = seed;
            GameContext.get().getBiomeManager().setBaseSeed(seed);
            if (GameContext.get().getWorld() == null) {
                GameContext.get().setWorld(new World(worldData));
                GameLogger.info("Basic world initialization complete");
            }

        } catch (Exception e) {
            GameLogger.error("Failed to initialize basic world: " + e.getMessage());
            throw new RuntimeException("World initialization failed", e);
        }
    }


    private enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        AUTHENTICATED
    }

    public interface LoginResponseListener {
        void onResponse(NetworkProtocol.LoginResponse response);
    }


    public interface RegistrationResponseListener {
        void onResponse(NetworkProtocol.RegisterResponse response);
    }

    public interface ReconnectionListener {
        void onReconnectionSuccess();

        void onReconnectionFailure(String reason);
    }

    public interface InitializationListener {
        void onInitializationComplete(boolean success);
    }


    public interface PokemonUpdateHandler {
        void onUpdate(NetworkProtocol.PokemonUpdate update);
    }

    private static class NetworkSyncData {
        Vector2 targetPosition;
        String direction;
        boolean isMoving;
        long lastUpdateTime;
        float interpolationProgress;

        NetworkSyncData() {
            this.lastUpdateTime = System.currentTimeMillis();
            this.interpolationProgress = 0f;
        }
    }
}
