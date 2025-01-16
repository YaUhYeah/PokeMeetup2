package io.github.pokemeetup.multiplayer.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.minlog.Log;
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.managers.BiomeManager;
import io.github.pokemeetup.multiplayer.OtherPlayer;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.multiplayer.server.GameStateHandler;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import io.github.pokemeetup.multiplayer.server.storage.FileStorage;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.data.WorldData;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldManager;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;


public class GameClient {
    private static final int CHUNK_REQUEST_BATCH_SIZE = 4; // Request fewer chunks at once
    private static final long CHUNK_REQUEST_DELAY = 100; // ms between chunk request batches
    private static final int MAX_PENDING_CHUNKS = 8;
    private static final int WRITE_BUFFER = 1024 * 1024;
    private static final int OBJECT_BUFFER = 512 * 1024;
    private static final long CONNECTION_TIMEOUT = 45000; // 45s connection timeout
    private static final long RECONNECT_DELAY = 3000; // 3s base delay
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private final PlayerDataResponseHandler playerDataHandler = new PlayerDataResponseHandler();
    private static final int MAX_CONCURRENT_CHUNK_REQUESTS = 4;
    private static final long CHUNK_REQUEST_INTERVAL = 50; // 50ms between requests
    private static final float SYNC_INTERVAL = 1 / 60f; // 60Hz sync rate
    private static final float INTERPOLATION_SPEED = 10f;
    private static final float UPDATE_INTERVAL = 1 / 20f;
    private static final int BUFFER_SIZE = 8192;
    private final Queue<Vector2> chunkRequestQueue = new ConcurrentLinkedQueue<>();
    private final Map<Vector2, ChunkFragmentAssembler> fragmentAssemblers = new ConcurrentHashMap<>();
    private final Set<Vector2> pendingChunks = new ConcurrentHashMap<Vector2, Boolean>().keySet(true);
    private final AtomicBoolean isAuthenticated = new AtomicBoolean(false);
    private final AtomicBoolean shouldReconnect = new AtomicBoolean(true);
    private final AtomicBoolean isDisposing = new AtomicBoolean(false);
    private final ReentrantLock connectionLock = new ReentrantLock();
    private final ConcurrentHashMap<String, OtherPlayer> otherPlayers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, WildPokemon> trackedWildPokemon = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, NetworkSyncData> syncedPokemonData = new ConcurrentHashMap<>();
    private final BlockingQueue<NetworkProtocol.ChatMessage> chatMessageQueue = new LinkedBlockingQueue<>();
    private final boolean isSinglePlayer;
    private final ConcurrentHashMap<String, NetworkProtocol.PlayerUpdate> playerUpdates = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final WorldManager worldManager;
    private final FileStorage fileStorage;
    private final Queue<Object> pendingMessages = new ConcurrentLinkedQueue<>();
    private final Preferences credentials;
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final Queue<Object> sendQueue = new ConcurrentLinkedQueue<>();
    private final GameStateHandler gameHandler;
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);
    private final Map<Vector2, Chunk> chunks = new ConcurrentHashMap<>();
    private final Map<Vector2, Future<Chunk>> loadingChunks = new ConcurrentHashMap<>();
    private final Object messageLock = new Object();
    private final ConcurrentLinkedQueue<Object> messageQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private int reconnectAttempts = 0;
    private volatile boolean isInitializing = false;
    private long lastRequestTime = 0;
    private float syncTimer = 0;
    private PlayerData lastKnownState;
    private Consumer<NetworkProtocol.ChatMessage> chatMessageHandler;
    private LoginResponseListener loginResponseListener;
    private RegistrationResponseListener registrationResponseListener;
    private PokemonUpdateHandler pokemonUpdateHandler;
    private WorldData worldData;
    private float updateAccumulator = 0;
    private float tickAccumulator = 0;
    private volatile boolean isInitialized = false;
    private volatile boolean fullyInitialized = false;
    private InitializationListener initializationListener;
    private String pendingUsername;
    private String pendingPassword;
    private String currentPassword;
    private volatile boolean loginRequestSent = false;
    private String pendingRegistrationUsername;
    private String pendingRegistrationPassword;
    private volatile boolean processingMessages = false;
    private volatile ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private volatile Client client;
    private ServerConnectionConfig serverConfig;
    private String localUsername;
    private Player activePlayer;
    private World currentWorld;
    private long worldSeed;

    public void setActivePlayer(Player activePlayer) {
        this.activePlayer = activePlayer;
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

    public CompletableFuture<PlayerData> getPlayerData(UUID uuid) {
        if (isSinglePlayer || !isConnected() || !isAuthenticated()) {
            GameLogger.error("Cannot get player data - not in multiplayer mode or not connected");
            return CompletableFuture.failedFuture(new IllegalStateException("Not connected or not in multiplayer mode"));
        }

        try {
            CompletableFuture<PlayerData> future = playerDataHandler.createRequest(uuid);

            NetworkProtocol.GetPlayerDataRequest request = new NetworkProtocol.GetPlayerDataRequest();
            request.uuid = uuid;
            request.timestamp = System.currentTimeMillis();
            client.sendTCP(request);

            return future;
        } catch (Exception e) {
            GameLogger.error("Failed to get player data: " + e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    public PlayerData getPlayerDataSync(UUID uuid) {
        try {
            return getPlayerData(uuid).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            GameLogger.error("Failed to get player data synchronously: " + e.getMessage());
            return null;
        }
    }

    public GameClient(ServerConnectionConfig config, boolean isSinglePlayer, String serverIP, int tcpPort, int udpPort, GameStateHandler gameHandler) {
        this.gameHandler = gameHandler;

        Log.set(Log.LEVEL_DEBUG);
        this.serverConfig = config;
        this.isSinglePlayer = isSinglePlayer;
        this.lastKnownState = new PlayerData();
        new BiomeManager(System.currentTimeMillis());
        this.worldManager = WorldManager.getInstance(null, !isSinglePlayer);
        this.fileStorage = isSinglePlayer ? null : new FileStorage(config.getDataDirectory());

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

            this.client = new Client(WRITE_BUFFER, OBJECT_BUFFER);
            NetworkProtocol.registerClasses(client.getKryo());
            client.getKryo().setReferences(false);
        }
    }


    public void update(float deltaTime) {
        if (!isAuthenticated.get() || connectionState != ConnectionState.CONNECTED) {
            return;
        }

        // Handle network updates
        syncTimer += deltaTime;
        if (syncTimer >= SYNC_INTERVAL) {
            syncTimer = 0;
            processChatMessages();
            updatePokemonStates(deltaTime);
        }

        updateOtherPlayers(deltaTime);
        updateAccumulator += deltaTime;

        if (updateAccumulator >= UPDATE_INTERVAL) {
            updateAccumulator = 0;
            if (!isSinglePlayer && activePlayer != null && isAuthenticated() && isInitialized) {
                sendPlayerUpdate();
            }

            // Process any pending chunk requests
            processChunkQueue();
        }
    }


    private void completeInitialization() {
        if (isInitialized) return;

        isInitialized = true;
        fullyInitialized = true;
        GameLogger.info("Game client initialization complete");

        if (initializationListener != null) {
            Gdx.app.postRunnable(() -> {
                try {
                    initializationListener.onInitializationComplete(true);
                } catch (Exception e) {
                    GameLogger.error("Error notifying initialization completion: " + e.getMessage());
                }
            });
        }
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

            // Set timestamp if not already set
            if (spawnData.timestamp == 0) {
                spawnData.timestamp = System.currentTimeMillis();
            }

            // Send spawn data to server
            client.sendTCP(spawnData);

            // Track locally
            if (!trackedWildPokemon.containsKey(spawnData.uuid)) {
                // Create local Pokemon instance
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

                    if (currentWorld != null) {
                        pokemon.setWorld(currentWorld);
                        Vector2 chunkPos = new Vector2(
                            Math.floorDiv((int) spawnData.x, World.CHUNK_SIZE * World.TILE_SIZE),
                            Math.floorDiv((int) spawnData.y, World.CHUNK_SIZE * World.TILE_SIZE)
                        );
                        currentWorld.getPokemonSpawnManager().addPokemonToChunk(pokemon, chunkPos);
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

    public void setInitializationListener(InitializationListener listener) {
        this.initializationListener = listener;
        // If already initialized, notify immediately
        if (isInitialized()) {
            Gdx.app.postRunnable(() -> listener.onInitializationComplete(true));
        }
    }

    private void loadSavedCredentials() {
        try {
            this.localUsername = credentials.getString("username", null);
            this.currentPassword = credentials.getString("password", null); // Load password or token

            if (localUsername != null && currentPassword != null) {
                this.pendingUsername = localUsername;
                this.pendingPassword = currentPassword;
                GameLogger.info("Loaded saved credentials for: " + localUsername);
            } else {
                GameLogger.error("No saved credentials found. Authentication will not proceed automatically.");
                // Optionally, set default credentials for testing
                this.pendingUsername = "testUser";
                this.pendingPassword = "testPass";
                GameLogger.info("Using default credentials for testing: " + pendingUsername);
            }
        } catch (Exception e) {
            GameLogger.error("Failed to load credentials: " + e.getMessage());
        }
    }


    public void connect() {
        if (isConnecting.get() || isConnected()) {
            GameLogger.info("Already connecting or connected");
            return;
        }

        synchronized (connectionLock) {
            try {
                isConnecting.set(true);
                cleanupExistingConnection();

                client = new Client(BUFFER_SIZE, BUFFER_SIZE);

                // Configure Kryo first
                Kryo kryo = client.getKryo();
                kryo.setReferences(false);
                NetworkProtocol.registerClasses(kryo);

                // Add listener BEFORE starting
                client.addListener(new Listener() {
                    @Override
                    public void connected(Connection connection) {
                        GameLogger.info("Connected to server");
                        connectionState = ConnectionState.CONNECTED;
                        isConnected.set(true);
                        isConnecting.set(false);

                        // Try login if we have credentials
                        if (pendingUsername != null && pendingPassword != null) {
                            Gdx.app.postRunnable(() -> {
                                try {
                                    sendLoginRequest(pendingUsername, pendingPassword);
                                } catch (Exception e) {
                                    GameLogger.error("Login failed: " + e.getMessage());
                                    handleConnectionFailure(e);
                                }
                            });
                        } else {
                            GameLogger.error("No credentials available. Cannot authenticate.");
                            handleConnectionFailure(new Exception("Missing credentials"));
                        }
                    }

                    @Override
                    public void received(Connection connection, Object object) {
                        if (object instanceof NetworkProtocol.GetPlayerDataResponse ||
                            object instanceof NetworkProtocol.SavePlayerDataResponse) {
                            handlePlayerDataResponse(object);
                            return;
                        }
                        handleReceivedMessage(object);
                    }

                    @Override
                    public void disconnected(Connection connection) {
                        handleDisconnect();
                    }
                });

                // Start client and connect
                client.start();

                GameLogger.info("Connecting to " + serverConfig.getServerIP() + ":" + serverConfig.getTcpPort());
                client.connect((int) CONNECTION_TIMEOUT, serverConfig.getServerIP(),
                    serverConfig.getTcpPort(), serverConfig.getUdpPort());

            } catch (Exception e) {
                GameLogger.error("Connection failed: " + e.getMessage());
                handleConnectionFailure(e);
                isConnecting.set(false);
            }
        }
    }


    private void sendLoginRequest(String username, String password) {
        if (!isConnected() || client == null) {
            GameLogger.error("Cannot send login - not connected");
            return;
        }

        try {
            NetworkProtocol.LoginRequest request = new NetworkProtocol.LoginRequest();
            request.username = username;
            request.password = password;
            request.timestamp = System.currentTimeMillis();

            GameLogger.info("Sending login request for: " + username);
            client.sendTCP(request);

        } catch (Exception e) {
            GameLogger.error("Failed to send login request: " + e.getMessage());
            handleConnectionFailure(e);
        }
    }


    private void handleConnectionFailure(Exception e) {
        GameLogger.error("Connection failure: " + e.getMessage());

        synchronized (connectionLock) {
            connectionState = ConnectionState.DISCONNECTED;
            isConnected.set(false);
            isAuthenticated.set(false);

            cleanupExistingConnection();
            isConnecting.set(false);

            // Notify listeners
            if (loginResponseListener != null) {
                Gdx.app.postRunnable(() -> {
                    NetworkProtocol.LoginResponse response = new NetworkProtocol.LoginResponse();
                    response.success = false;
                    response.message = "Connection failed: " + e.getMessage();
                    loginResponseListener.onResponse(response);
                });
            }
        }
    }

    private void cleanupExistingConnection() {
        if (client != null) {
            try {
                if (client.isConnected()) {
                    client.close();
                }
                client.stop();
                client = null;
            } catch (Exception e) {
                GameLogger.error("Error cleaning up connection: " + e.getMessage());
            }
        }
    }

    public void setPendingCredentials(String username, String password) {
        GameLogger.info("Setting pending credentials for: " + username);
        this.pendingUsername = username;
        this.pendingPassword = password;

        // If we're already connected but haven't logged in, do it now
        if (connectionState == ConnectionState.CONNECTED && !loginRequestSent) {
            try {
                sendLoginRequest(username, password);
            } catch (Exception e) {
                GameLogger.error("Failed to send pending login request: " + e.getMessage());
                handleConnectionFailure(e);
            }
        }
    }

    private void requestInitialChunks() {
        if (activePlayer == null || currentWorld == null) return;

        int playerChunkX = (int) Math.floor(activePlayer.getX() / (World.CHUNK_SIZE * World.TILE_SIZE));
        int playerChunkY = (int) Math.floor(activePlayer.getY() / (World.CHUNK_SIZE * World.TILE_SIZE));

        for (int dx = -World.INITIAL_LOAD_RADIUS; dx <= World.INITIAL_LOAD_RADIUS; dx++) {
            for (int dy = -World.INITIAL_LOAD_RADIUS; dy <= World.INITIAL_LOAD_RADIUS; dy++) {
                Vector2 chunkPos = new Vector2(playerChunkX + dx, playerChunkY + dy);
                requestChunk(chunkPos);
            }
        }
    }

    public void sendPlayerUpdate() {
        if (!isConnected() || !isAuthenticated() || activePlayer == null) return;

        float playerX = activePlayer.getX();
        float playerY = activePlayer.getY();

        GameLogger.info("sendPlayerUpdate() - activePlayer position: x=" + playerX + ", y=" + playerY);

        NetworkProtocol.PlayerUpdate update = new NetworkProtocol.PlayerUpdate();
        update.username = getLocalUsername();
        update.x = playerX;
        update.y = playerY;
        update.direction = activePlayer.getDirection();
        update.isMoving = activePlayer.isMoving();
        update.timestamp = System.currentTimeMillis();

        GameLogger.info("Sending PlayerUpdate: username=" + update.username + ", x=" + update.x + ", y=" + update.y);
        client.sendTCP(update);
    }


    public void saveWorldState() {
        if (isSinglePlayer) {
            if (currentWorld != null) {
                worldManager.saveWorld(currentWorld.getWorldData());
            }
        } else if (isAuthenticated.get() && fileStorage != null) {
            try {
                // Save only to server storage
                fileStorage.saveWorldData(currentWorld.getWorldData().getName(), currentWorld.getWorldData());
            } catch (IOException e) {
                GameLogger.error("Failed to save world data to server: " + e.getMessage());
            }
        }
    }

    public void savePlayerState(PlayerData playerData) {
        if (isSinglePlayer) {
            if (currentWorld != null) {
                currentWorld.getWorldData().savePlayerData(playerData.getUsername(), playerData,false);
                worldManager.saveWorld(currentWorld.getWorldData());
                GameLogger.info("Saved singleplayer state for: " + playerData.getUsername());
            }
            return;
        }

        if (isAuthenticated.get()) {
            try {
                PlayerData saveData = playerData.copy();
                saveData.setX(playerData.getX() / World.TILE_SIZE);
                saveData.setY(playerData.getY() / World.TILE_SIZE);
                saveData.setInventoryItems(playerData.getInventoryItems());
                saveData.setPartyPokemon(playerData.getPartyPokemon());

                if (fileStorage != null) {
                    fileStorage.savePlayerData(playerData.getUsername(), saveData);
                }
                sendPlayerUpdateToServer(saveData);
            } catch (IOException e) {
                GameLogger.error("Failed to save player data to server: " + e.getMessage());
            }
        }
    }

    public void sendPlayerUpdateToServer(PlayerData playerData) {
        if (isSinglePlayer || !isConnected() || !isAuthenticated()) {
            return;
        }

        try {
            NetworkProtocol.PlayerUpdate update = new NetworkProtocol.PlayerUpdate();
            update.username = playerData.getUsername();
            update.x = playerData.getX();
            update.y = playerData.getY();
            update.direction = playerData.getDirection();
            update.isMoving = playerData.isMoving();
            update.wantsToRun = playerData.isWantsToRun();
            update.timestamp = System.currentTimeMillis();
            if (playerData.getInventoryItems() != null) {
                update.inventoryItems = playerData.getInventoryItems().toArray(new ItemData[0]);
            }
            if (playerData.getPartyPokemon() != null) {
                update.partyPokemon = playerData.getPartyPokemon();
            }

            client.sendTCP(update);
            GameLogger.info("Sent player update to server for: " + playerData.getUsername() +
                " at (" + update.x + "," + update.y + ")");

        } catch (Exception e) {
            GameLogger.error("Failed to send player update: " + e.getMessage());
            handleConnectionFailure(e);
        }
    }

    public Map<String, NetworkProtocol.PlayerUpdate> getPlayerUpdates() {
        Map<String, NetworkProtocol.PlayerUpdate> updates = new HashMap<>(playerUpdates);
        playerUpdates.clear();
        return updates;
    }


    public void saveState(PlayerData playerData) {
        if (isSinglePlayer) {
            if (currentWorld != null) {
                worldManager.saveWorld(currentWorld.getWorldData());
            }
        } else if (isAuthenticated.get()) {
            // For multiplayer, only send state to server
            sendPlayerUpdateToServer(playerData);
        }
    }

    private void handleWorldStateUpdate(NetworkProtocol.WorldStateUpdate update) {
        if (update == null || update.worldData == null) return;

        Gdx.app.postRunnable(() -> {
            try {
                // Replace the current world's data with the new data
                currentWorld.setWorldData(update.worldData);

                // Optionally, reinitialize world components if needed
                currentWorld.initializeWorldFromData(update.worldData);

                GameLogger.info("World state updated from server.");

            } catch (Exception e) {
                GameLogger.error("Error handling world state update: " + e.getMessage());
            }
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
        return isSinglePlayer;
    }

    public World getCurrentWorld() {
        return currentWorld;
    }

    public void setCurrentWorld(World world) {
        if (world == null) {
            GameLogger.error("Cannot set null world");
            return;
        }
        this.currentWorld = world;
        this.worldData = world.getWorldData();
        GameLogger.info("Set current world in GameClient");

        if (activePlayer != null) {
            currentWorld.setPlayer(activePlayer);
            GameLogger.info("Set active player in new world: " + activePlayer.getUsername());
        }
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
        GameLogger.info("Chat message queued from " + message.sender +
            " of type " + message.type);
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

            if (currentWorld != null) {
                saveWorldState();
            }
            if (activePlayer != null) {
                savePlayerState(activePlayer.getPlayerData());
            }

            scheduler.shutdownNow();
            try {
                scheduler.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Clean up network
            if (client != null) {
                try {
                    NetworkProtocol.ForceDisconnect disconnect = new NetworkProtocol.ForceDisconnect();
                    disconnect.reason = "Client closing";
                    client.sendTCP(disconnect);
                } catch (Exception ignored) {
                }
                client.close();
            }

            GameLogger.info("GameClient disposed");
        }
    }

    public void shutdown() {
        isShuttingDown.set(true);
        dispose();
    }

    public void clearCredentials() {
        this.localUsername = null;
        this.currentPassword = null;
        credentials.clear();
        credentials.flush();
    }

    private void scheduleReconnection() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            GameLogger.error("Max reconnection attempts reached");
            return;
        }

        long delay = RECONNECT_DELAY * (long) Math.pow(2, reconnectAttempts);
        reconnectAttempts++;

        scheduler.schedule(() -> {
            if (shouldReconnect.get() && !isDisposing.get()) {
                GameLogger.info("Attempting reconnection " + reconnectAttempts);
                cleanupExistingConnection();
                connect();
            }
        }, delay, TimeUnit.MILLISECONDS);
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
            connect();
        }
    }

    private void handleReceivedMessage(Object object) {
        if (object instanceof NetworkProtocol.ChunkData) {
            handleChunkResponse((NetworkProtocol.ChunkData) object);
            return;
        }
        if (object instanceof NetworkProtocol.ForceDisconnect) {
            NetworkProtocol.ForceDisconnect disconnect = (NetworkProtocol.ForceDisconnect) object;
            GameLogger.error("Received ForceDisconnect from server: " + disconnect.reason);
            handleDisconnect();
            return;
        }
        if (!isInitialized) {
            GameLogger.info("Received message type: " + object.getClass().getSimpleName());

            if (object instanceof NetworkProtocol.LoginResponse) {
                NetworkProtocol.LoginResponse response = (NetworkProtocol.LoginResponse) object;
                GameLogger.info("Login response received - Success: " + response.success);
                handleLoginResponse(response);
            }
        }
        if (object instanceof NetworkProtocol.ChunkDataFragment) {
            GameLogger.info("Received chunk fragment");
            handleChunkDataFragment((NetworkProtocol.ChunkDataFragment) object);
            return;
        }
        if (object instanceof NetworkProtocol.ChunkDataComplete) {
            GameLogger.info("Received chunk complete signal");
            handleChunkDataComplete((NetworkProtocol.ChunkDataComplete) object);
            return;
        }

        GameLogger.info("Received message: " + object.getClass().getName());
        try {
            if (object instanceof NetworkProtocol.LoginResponse) {
                GameLogger.info("CRITICAL - Received LoginResponse");
                NetworkProtocol.LoginResponse response = (NetworkProtocol.LoginResponse) object;
                GameLogger.info("CRITICAL - Login success: " + response.success);
                GameLogger.info("CRITICAL - Login message: " + response.message);
                handleLoginResponse((NetworkProtocol.LoginResponse) object);
            } else if (!isAuthenticated.get()) {
                GameLogger.info("Received message before authentication, queueing");
                pendingMessages.offer(object);
            } else if (object instanceof NetworkProtocol.ChatMessage) {
                handleChatMessage((NetworkProtocol.ChatMessage) object);
            } else if (object instanceof NetworkProtocol.PlayerUpdate) {
                handlePlayerUpdate((NetworkProtocol.PlayerUpdate) object);
            } else if (object instanceof NetworkProtocol.PlayerJoined) {
                handlePlayerJoined((NetworkProtocol.PlayerJoined) object);
            } else if (object instanceof NetworkProtocol.PlayerLeft) {
                handlePlayerLeft((NetworkProtocol.PlayerLeft) object);
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
        } catch (Exception e) {
            GameLogger.error("Error handling network message: " + e.getMessage());
        }

    }private void handlePlayerAction(NetworkProtocol.PlayerAction action) {
        Gdx.app.postRunnable(() -> {
            OtherPlayer otherPlayer = otherPlayers.get(action.playerId);
            if (otherPlayer != null) {
                otherPlayer.updateAction(action);
            }
        });
    }


    private void handleBlockPlacement(NetworkProtocol.BlockPlacement placement) {
        Gdx.app.postRunnable(() -> {
            if (currentWorld != null && currentWorld.getBlockManager() != null) {
                PlaceableBlock.BlockType type = PlaceableBlock.BlockType.fromItemId(placement.blockTypeId);
                if (type != null) {
                    if (placement.action == NetworkProtocol.BlockAction.PLACE) {
                        currentWorld.getBlockManager().placeBlock(type, placement.tileX, placement.tileY);
                        GameLogger.info("Block placed by " + placement.username + " at (" + placement.tileX + ", " + placement.tileY + ")");
                    } else if (placement.action == NetworkProtocol.BlockAction.REMOVE) {
                        currentWorld.getBlockManager().removeBlock(placement.tileX, placement.tileY);
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

    private void handlePlayerDataResponse(Object object) {
        try {
            if (object instanceof NetworkProtocol.GetPlayerDataResponse) {
                playerDataHandler.handleGetResponse((NetworkProtocol.GetPlayerDataResponse) object);
            } else if (object instanceof NetworkProtocol.SavePlayerDataResponse) {
                playerDataHandler.handleSaveResponse((NetworkProtocol.SavePlayerDataResponse) object);
            }
        } catch (Exception e) {
            GameLogger.error("Error handling player data response: " + e.getMessage());
        }
    }

    private void handleDisconnect() {
        synchronized (connectionLock) {
            connectionState = ConnectionState.DISCONNECTED;
            isConnected.set(false);
            isAuthenticated.set(false);

            // Clean up connection
            cleanupConnection();

            if (!isDisposing.get() && shouldReconnect.get()) {
                scheduleReconnection();
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

    public void tick(float deltaTime) {
        if (!isConnected() || isSinglePlayer) return;

        // Process any pending messages
        if (isInitialized && !processingMessages) {
            processQueuedMessages();
        }

        // Update game state
        if (isInitialized && activePlayer != null) {
            update(deltaTime);
        }
    }


    public void sendRegisterRequest(String username, String password) {
        if (isSinglePlayer) {
            GameLogger.info("Registration not needed in single player mode");
            return;
        }
        if (!isConnected() || client == null) {
            GameLogger.info("Client not connected - attempting to connect before registration");
            connect();

            pendingRegistrationUsername = username;
            pendingRegistrationPassword = password;
            return;
        }
        try {
            if (username == null || username.trim().isEmpty() ||
                password == null || password.trim().isEmpty()) {
                handleRegistrationFailure("Username and password are required");
                return;
            }

            String secureUsername = username.trim();
            String securePassword = password.trim();

            NetworkProtocol.RegisterRequest request = new NetworkProtocol.RegisterRequest();
            request.username = secureUsername;
            request.password = securePassword;

            GameLogger.info("Sending registration request for: " + secureUsername);
            client.sendTCP(request);

            this.localUsername = secureUsername;

        } catch (Exception e) {
            GameLogger.error("Failed to send registration request: " + e.getMessage());
            handleRegistrationFailure("Failed to send registration request: " + e.getMessage());
        }
    }

    private void handleRegistrationFailure(String message) {
        String failedUsername = pendingRegistrationUsername;

        pendingRegistrationUsername = null;
        pendingRegistrationPassword = null;

        if (registrationResponseListener != null) {
            NetworkProtocol.RegisterResponse response = new NetworkProtocol.RegisterResponse();
            response.success = false;
            response.message = message;
            response.username = failedUsername;

            Gdx.app.postRunnable(() -> {
                registrationResponseListener.onResponse(response);
            });
        }

        GameLogger.error("Registration failed for " + failedUsername + ": " + message);
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
            GameLogger.error("Attempted to request chunk without authentication: " + chunkPos);
            return;
        }

        try {
            NetworkProtocol.ChunkRequest request = new NetworkProtocol.ChunkRequest();
            request.chunkX = (int) chunkPos.x;
            request.chunkY = (int) chunkPos.y;
            request.timestamp = System.currentTimeMillis();

            pendingChunks.add(chunkPos);
            client.sendTCP(request);
            GameLogger.info("Requesting chunk: " + chunkPos + " (Pending: " + pendingChunks.size() + ")");

        } catch (Exception e) {
            GameLogger.error("Failed to request chunk: " + e.getMessage());
            pendingChunks.remove(chunkPos);
        }
    }


    public void handleChunkResponse(NetworkProtocol.ChunkData chunkData) {
        if (chunkData == null) return;

        Vector2 chunkPos = new Vector2(chunkData.chunkX, chunkData.chunkY);
        GameLogger.info("Received chunk data for: " + chunkPos);

        if (currentWorld != null) {
            Gdx.app.postRunnable(() -> {
                try {
                    // Process the chunk data
                    currentWorld.processChunkData(chunkData);
                    pendingChunks.remove(chunkPos);

                    int totalRequired = (World.INITIAL_LOAD_RADIUS * 2 + 1) * (World.INITIAL_LOAD_RADIUS * 2 + 1);
                    int loaded = currentWorld.getChunks().size();

                    if (loaded >= totalRequired) {
                        GameLogger.info("All chunks loaded, completing initialization");
                        completeInitialization();
                    }
                } catch (Exception e) {
                    GameLogger.error("Error processing chunk data: " + e.getMessage());
                }
            });
        }
    }private void handleWorldObjectUpdate(NetworkProtocol.WorldObjectUpdate update) {
        if (update == null || currentWorld == null) {
            return;
        }

        Gdx.app.postRunnable(() -> {
            try {
                WorldObject.WorldObjectManager objectManager = currentWorld.getObjectManager();
                switch (update.type) {
                    case ADD:
                        WorldObject newObj = new WorldObject();
                        newObj.updateFromData(update.data);
                        objectManager.addObjectToChunk(newObj);
                        break;

                    case REMOVE:
                        objectManager.removeObjectById(update.objectId);
                        break;

                    case UPDATE:
                        objectManager.updateObject(update);
                        break;
                }
            } catch (Exception e) {
                GameLogger.error("Error processing world object update: " + e.getMessage());
            }
        });
    }

    public Player getActivePlayer() {
        return activePlayer;
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


    private void handleLoginFailure(String message) {
        loginRequestSent = false;
        GameLogger.error("Login failed: " + message);
        if (loginResponseListener != null) {
            NetworkProtocol.LoginResponse failResponse = new NetworkProtocol.LoginResponse();
            failResponse.success = false;
            failResponse.message = message;
            Gdx.app.postRunnable(() -> loginResponseListener.onResponse(failResponse));
        }
        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            handleConnectionFailure(new Exception("Login failed: " + message));
        }
    }

    private void handleChunkDataFragment(NetworkProtocol.ChunkDataFragment fragment) {
        Vector2 chunkPos = new Vector2(fragment.chunkX, fragment.chunkY);
        ChunkFragmentAssembler assembler = fragmentAssemblers.computeIfAbsent(chunkPos,
            k -> new ChunkFragmentAssembler(fragment.totalFragments, fragment.fragmentSize));

        assembler.addFragment(fragment);
    }

    public void processChunkQueue() {
        long now = System.currentTimeMillis();
        if (!chunkRequestQueue.isEmpty() &&
            now - lastRequestTime >= CHUNK_REQUEST_INTERVAL &&
            pendingChunks.size() < MAX_CONCURRENT_CHUNK_REQUESTS) {

            Vector2 nextChunk = chunkRequestQueue.poll();
            if (nextChunk != null && !chunks.containsKey(nextChunk) && !loadingChunks.containsKey(nextChunk)) {
                requestChunk(nextChunk);
                lastRequestTime = now;
            }
        }
    }

    private void handlePlayerUpdate(NetworkProtocol.PlayerUpdate update) {
        if (update == null || update.username == null || update.username.equals(localUsername)) {
            return;
        }

        Gdx.app.postRunnable(() -> {
            synchronized (otherPlayers) {
                OtherPlayer otherPlayer = otherPlayers.get(update.username);
                if (otherPlayer == null) {
                    otherPlayer = new OtherPlayer(
                        update.username,
                        update.x,
                        update.y
                    );
                    otherPlayers.put(update.username, otherPlayer);
                    GameLogger.info("Created new player: " + update.username);
                }

                otherPlayer.updateFromNetwork(update);
                playerUpdates.put(update.username, update);
            }
        });
    }

    private void handlePlayerJoined(NetworkProtocol.PlayerJoined joinMsg) {

        // Add timestamp tracking to detect duplicates
        final String joinKey = joinMsg.username + "_" + joinMsg.timestamp;

        Gdx.app.postRunnable(() -> {
            synchronized (otherPlayers) {
                // Check if we've already handled this join message
                if (recentJoinEvents.contains(joinKey)) {
                    GameLogger.info("Skipping duplicate join event for: " + joinMsg.username);
                    return;
                }

                // Check if player already exists
                if (otherPlayers.containsKey(joinMsg.username)) {
                    GameLogger.info("Player " + joinMsg.username + " already exists, updating position");
                    OtherPlayer existingPlayer = otherPlayers.get(joinMsg.username);
                    existingPlayer.setPosition(new Vector2(joinMsg.x, joinMsg.y));
                    return;
                }

                // Create new player instance
                OtherPlayer newPlayer = new OtherPlayer(
                    joinMsg.username,
                    joinMsg.x,
                    joinMsg.y
                );
                otherPlayers.put(joinMsg.username, newPlayer);

                // Track this join event
                recentJoinEvents.add(joinKey);
                // Clean up old events after 5 seconds
                com.badlogic.gdx.utils.Timer.schedule(new com.badlogic.gdx.utils.Timer.Task() {
                    @Override
                    public void run() {
                        recentJoinEvents.remove(joinKey);
                    }
                }, 5);

                // Only send system message for new players
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

    private final Set<String> recentJoinEvents = Collections.synchronizedSet(new HashSet<>());

    private void handlePlayerLeft(NetworkProtocol.PlayerLeft leftMsg) {
        Gdx.app.postRunnable(() -> {
            OtherPlayer leftPlayer = otherPlayers.remove(leftMsg.username);
            if (leftPlayer != null) {
                leftPlayer.dispose();
            }

            playerUpdates.remove(leftMsg.username);
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
                pokemon.setWorld(currentWorld);

                pokemon.setUuid(spawnData.uuid);
                pokemon.setDirection("down");
                pokemon.setSpawnTime(spawnData.timestamp / 1000L);

                trackedWildPokemon.put(spawnData.uuid, pokemon);
                syncedPokemonData.put(spawnData.uuid, new NetworkSyncData());

                if (currentWorld != null && currentWorld.getPokemonSpawnManager() != null) {
                    currentWorld.getPokemonSpawnManager().addPokemonToChunk(
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

                if (pokemon != null && currentWorld != null) {
                    // Start despawn animation
                    pokemon.startDespawnAnimation();

                    // Remove from world after animation completes
                    com.badlogic.gdx.utils.Timer.schedule(new com.badlogic.gdx.utils.Timer.Task() {
                        @Override
                        public void run() {
                            currentWorld.getPokemonSpawnManager()
                                .removePokemon(despawnData.uuid);
                        }
                    }, 1.0f); // Animation duration
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
            // Check object type from data
            if (update.data != null && update.data.containsKey("type")) {
                String objectType = (String) update.data.get("type");
                // Skip static decorative objects
                if (objectType.equals("SUNFLOWER") ||
                    objectType.equals("BUSH") ||
                    objectType.equals("DEAD_TREE") ||
                    objectType.equals("CACTUS")) {
                    return;
                }
            }


            client.sendTCP(update);

        } catch (Exception e) {
            GameLogger.error("Failed to send world object update: " + e.getMessage());
            handleConnectionFailure(e);
        }
    }
    public void sendPlayerAction(NetworkProtocol.PlayerAction action) {
        if (client != null && client.isConnected()) {
            client.sendTCP(action);
        } else {
            GameLogger.error("Cannot send PlayerAction, client is not connected.");
        }
    }


    private WorldObject createWorldObjectFromData(Map<String, Object> data) {
        try {
            String typeStr = (String) data.get("type");
            if (typeStr == null) {
                GameLogger.error("Missing type in world object data");
                return null;
            }

            WorldObject.ObjectType type = WorldObject.ObjectType.valueOf(typeStr);
            int tileX = ((Number) data.get("tileX")).intValue();
            int tileY = ((Number) data.get("tileY")).intValue();

            WorldObject obj = new WorldObject(
                tileX,
                tileY,
                null, // Client will handle textures
                type
            );

            // Set ID if present
            if (data.containsKey("id")) {
                obj.setId((String) data.get("id"));
            } else {
                obj.setId(UUID.randomUUID().toString());
            }

            return obj;
        } catch (Exception e) {
            GameLogger.error("Error creating WorldObject from data: " + e.getMessage());
            return null;
        }
    }
    private void handleChunkData(NetworkProtocol.ChunkData chunkData) {
        if (chunkData == null) return;

        Vector2 chunkPos = new Vector2(chunkData.chunkX, chunkData.chunkY);
        GameLogger.info("Received chunk data for: " + chunkPos);

        if (currentWorld != null) {
            Gdx.app.postRunnable(() -> {
                try {
                    // Process the chunk data
                    currentWorld.processChunkData(chunkData);

                    // Process blocks
                    if (chunkData.blockData != null) {
                        for (Map<String, Object> blockData : chunkData.blocks) {
                            String typeId = (String) blockData.get("type");
                            int tileX = ((Number) blockData.get("tileX")).intValue();
                            int tileY = ((Number) blockData.get("tileY")).intValue();
                            boolean isFlipped = blockData.get("isFlipped") != null && (Boolean) blockData.get("isFlipped");

                            PlaceableBlock.BlockType blockType = PlaceableBlock.BlockType.fromId(typeId);
                            if (blockType != null) {
                                PlaceableBlock block = new PlaceableBlock(blockType, new Vector2(tileX, tileY), null, isFlipped);
                                currentWorld.getBlockManager().placeBlock(blockType, tileX, tileY);
                            } else {
                                GameLogger.error("Unknown block type ID in chunk data: " + typeId);
                            }
                        }
                    }

                    // Process world objects
                    if (chunkData.worldObjects != null) {
                        List<WorldObject> objects = new ArrayList<>();
                        for (Map<String, Object> data : chunkData.worldObjects) {
                            WorldObject obj = new WorldObject();
                            obj.updateFromData(data);
                            objects.add(obj);
                        }
                        currentWorld.getObjectManager().setObjectsForChunk(chunkPos, objects);
                    }

                } catch (Exception e) {
                    GameLogger.error("Error processing chunk data: " + e.getMessage());
                }
            });
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

    public void setLoginResponseListener(LoginResponseListener listener) {
        this.loginResponseListener = listener;
    }

    public void setRegistrationResponseListener(RegistrationResponseListener listener) {
        this.registrationResponseListener = listener;
    }

    public Map<String, OtherPlayer> getOtherPlayers() {
        return new HashMap<>(otherPlayers);
    }

    public boolean isAuthenticated() {
        return isAuthenticated.get();
    }

    public boolean isConnected() {
        return connectionState == ConnectionState.CONNECTED || connectionState == ConnectionState.AUTHENTICATED;
    }

    private void handleChunkDataComplete(NetworkProtocol.ChunkDataComplete complete) {
        Vector2 chunkPos = new Vector2(complete.chunkX, complete.chunkY);
        ChunkFragmentAssembler assembler = fragmentAssemblers.get(chunkPos);

        if (assembler != null && assembler.isComplete()) {
            NetworkProtocol.ChunkData fullChunk = assembler.buildCompleteChunk(complete.chunkX, complete.chunkY);
            handleChunkData(fullChunk);
            fragmentAssemblers.remove(chunkPos);
            pendingChunks.remove(chunkPos);
            processChunkQueue();
        }
    }

    private enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        AUTHENTICATED
    }

    public interface InitializationListener {
        void onInitializationComplete(boolean success);
    }

    public interface LoginResponseListener {
        void onResponse(NetworkProtocol.LoginResponse response);
    }

    public interface RegistrationResponseListener {
        void onResponse(NetworkProtocol.RegisterResponse response);
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

    private void handleLoginResponse(NetworkProtocol.LoginResponse response) {
        if (response.success) {
            isAuthenticated.set(true);
            localUsername = response.username;

            Gdx.app.postRunnable(() -> {
                try {
                    initializeWorldBasic(response.seed, response.worldTimeInMinutes, response.dayLength);
                    if (activePlayer == null) {
                        activePlayer = new Player(response.username, currentWorld);
                    }
                    activePlayer.setX(response.x);
                    activePlayer.setY(response.y);

                    requestInitialChunks();

                } catch (Exception e) {
                    GameLogger.error("Initial world setup failed: " + e.getMessage());
                    handleLoginFailure("World initialization failed");
                }
            });
        } else {
            handleLoginFailure(response.message);
        }
    }

    private void initializeWorldBasic(long seed, double worldTimeInMinutes, float dayLength) {
        try {
            GameLogger.info("Initializing basic world with seed: " + seed);

            // Create new WorldData if it doesn't exist
            if (worldData == null) {
                worldData = new WorldData("multiplayer");
                GameLogger.info("Created new WorldData instance");
            }

            WorldData.WorldConfig config = new WorldData.WorldConfig(seed);
            worldData.setConfig(config);
            worldData.setWorldTimeInMinutes(worldTimeInMinutes);
            worldData.setDayLength(dayLength);

            this.worldSeed = seed;
            new BiomeManager(seed);
            if (currentWorld == null) {
                currentWorld = new World(worldData, this);
                GameLogger.info("Basic world initialization complete");
            }

        } catch (Exception e) {
            GameLogger.error("Failed to initialize basic world: " + e.getMessage());
            throw new RuntimeException("World initialization failed", e);
        }
    }


    public static class ChunkFragmentAssembler {
        private final int[][] tileData = new int[World.CHUNK_SIZE][World.CHUNK_SIZE];
        private final BitSet receivedFragments;
        private final int totalFragments;
        private BiomeType biomeType;
        private int fragmentsReceived = 0;

        ChunkFragmentAssembler(int totalFragments, int fragmentSize) {
            this.totalFragments = totalFragments;
            this.receivedFragments = new BitSet(totalFragments);
        }

        synchronized void addFragment(NetworkProtocol.ChunkDataFragment fragment) {
            if (!receivedFragments.get(fragment.fragmentIndex)) {
                System.arraycopy(fragment.tileData, 0, tileData[fragment.startX],
                    fragment.startY, fragment.fragmentSize);
                biomeType = fragment.biomeType;
                receivedFragments.set(fragment.fragmentIndex);
                fragmentsReceived++;
            }
        }

        boolean isComplete() {
            return fragmentsReceived == totalFragments;
        }

        NetworkProtocol.ChunkData buildCompleteChunk(int chunkX, int chunkY) {
            NetworkProtocol.ChunkData data = new NetworkProtocol.ChunkData();
            data.chunkX = chunkX;
            data.chunkY = chunkY;
            data.biomeType = biomeType;
            data.tileData = tileData;
            return data;
        }
    }
}
