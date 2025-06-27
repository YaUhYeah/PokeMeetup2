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
import io.github.pokemeetup.system.gameplay.overworld.*;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.system.gameplay.overworld.mechanics.AutoTileSystem;
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
        if (client != null && client.isConnected()) {
            Gdx.app.postRunnable(onSuccess);
            return;
        }
        if (!isConnecting.compareAndSet(false, true)) {
            Gdx.app.postRunnable(() -> onError.accept("Already connecting..."));
            return;
        }
        client = new Client(65536, 65536);
        NetworkProtocol.registerClasses(client.getKryo());

        client.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                Gdx.app.postRunnable(() -> {
                    isConnecting.set(false);
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

        pokemonUpdateAccumulator += deltaTime;
        if (pokemonUpdateAccumulator >= POKEMON_UPDATE_THRESHOLD) {
            pokemonUpdateAccumulator = 0f;
            updatePokemonStates(deltaTime);
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
            if (spawnData.data == null || spawnData.uuid == null) {
                GameLogger.error("Invalid Pokemon spawn data");
                return;
            }

            if (spawnData.timestamp == 0) {
                spawnData.timestamp = System.currentTimeMillis();
            }
            client.sendTCP(spawnData);
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
                update.chestId = chestData.chestId;
                update.username = GameContext.get().getPlayer().getUsername();
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

    private void handleItemPickup(NetworkProtocol.ItemPickup pickup) {
        if (pickup == null || pickup.entityId == null) {
            return;
        }
        Gdx.app.postRunnable(() -> {
            if (GameContext.get().getWorld() != null && GameContext.get().getWorld().getItemEntityManager() != null) {
                GameContext.get().getWorld().getItemEntityManager().removeItemEntity(pickup.entityId);
                GameLogger.info("Removed item entity " + pickup.entityId + " picked up by " + pickup.username);
            }
        });
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
                NetworkProtocol.SavePlayerDataRequest request = new NetworkProtocol.SavePlayerDataRequest();
                request.playerData = playerData;
                request.timestamp = System.currentTimeMillis();
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
        Gdx.app.postRunnable(() -> {
            World world = GameContext.get().getWorld();
            if (world == null) return;
            world.getWorldData().setWorldTimeInMinutes(update.worldTimeInMinutes);
            world.getWorldData().setDayLength(update.dayLength);
            WeatherSystem weatherSystem = world.getWeatherSystem();
            if (weatherSystem != null) {
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
                reconnectAttempts = 0;
                GameLogger.info("Successfully reconnected to server");
            }, (errorMsg) -> {
                reconnectAttempts++;
                GameLogger.error("Failed to reconnect to server: " + errorMsg);
            }, REGISTRATION_CONNECT_TIMEOUT_MS);
        }
    }

    private void handleCompressedChunkData(NetworkProtocol.CompressedChunkData compressed) {
        try {
            LZ4Factory factory = LZ4Factory.fastestInstance();
            LZ4SafeDecompressor decompressor = factory.safeDecompressor();

            byte[] restored = new byte[compressed.originalLength];
            int decompressedSize = decompressor.decompress(
                compressed.data, 0, compressed.data.length,
                restored, 0, compressed.originalLength
            );

            if (decompressedSize != compressed.originalLength) {
                GameLogger.error("Incomplete decompression for chunk " + compressed.chunkX + "," + compressed.chunkY);
                pendingChunks.remove(new Vector2(compressed.chunkX, compressed.chunkY));
                return;
            }

            ByteArrayInputStream bais = new ByteArrayInputStream(restored);
            Input input = new Input(bais);
            Kryo kryo = new Kryo();
            NetworkProtocol.registerClasses(kryo);
            kryo.setReferences(false);
            NetworkProtocol.ChunkData chunkData;
            try {
                chunkData = kryo.readObject(input, NetworkProtocol.ChunkData.class);
            } finally {
                input.close();
                bais.close();
            }

            final Vector2 chunkPos = new Vector2(chunkData.chunkX, chunkData.chunkY);

            Gdx.app.postRunnable(() -> {
                World world = GameContext.get().getWorld();
                if (world == null) {
                    GameLogger.error("World is null when processing chunk " + chunkPos);
                    pendingChunks.remove(chunkPos);
                    return;
                }
                world.processChunkData(chunkData);
                pendingChunks.remove(chunkPos);
            });
        } catch (Exception e) {
            GameLogger.error("Error handling compressed chunk data: " + e.getMessage());
            if (compressed != null) {
                pendingChunks.remove(new Vector2(compressed.chunkX, compressed.chunkY));
            }
        }
    }


    /**
     * Request adjacent chunks if needed to ensure smooth transitions
     */
    private void requestAdjacentChunksIfNeeded(Vector2 processedChunkPos) {
        int[][] adjacentOffsets = {
            {0, 1}, {1, 1}, {1, 0}, {1, -1},
            {0, -1}, {-1, -1}, {-1, 0}, {-1, 1}
        };

        World world = GameContext.get().getWorld();
        if (world == null) return;
        Player player = GameContext.get().getPlayer();
        if (player != null) {
            int playerChunkX = Math.floorDiv(player.getTileX(), Chunk.CHUNK_SIZE);
            int playerChunkY = Math.floorDiv(player.getTileY(), Chunk.CHUNK_SIZE);
            final Vector2 playerChunkPos = new Vector2(playerChunkX, playerChunkY);
            Arrays.sort(adjacentOffsets, (a, b) -> {
                Vector2 posA = new Vector2(processedChunkPos.x + a[0], processedChunkPos.y + a[1]);
                Vector2 posB = new Vector2(processedChunkPos.x + b[0], processedChunkPos.y + b[1]);
                float distA = Math.abs(posA.x - playerChunkPos.x) + Math.abs(posA.y - playerChunkPos.y);
                float distB = Math.abs(posB.x - playerChunkPos.x) + Math.abs(posB.y - playerChunkPos.y);
                return Float.compare(distA, distB);
            });
        }
        for (int[] offset : adjacentOffsets) {
            Vector2 adjPos = new Vector2(
                processedChunkPos.x + offset[0],
                processedChunkPos.y + offset[1]
            );
            if (!world.getChunks().containsKey(adjPos) && !pendingChunks.contains(adjPos)) {
                if (isChunkNearPlayer(adjPos, 4)) { // Within 4 chunks of player
                    requestChunk(adjPos);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }

    /**
     * Determines if a chunk is within a given distance from the player
     */
    private boolean isChunkNearPlayer(Vector2 chunkPos, float radius) {
        Player player = GameContext.get().getPlayer();
        if (player == null) return false;

        int playerChunkX = Math.floorDiv(player.getTileX(), Chunk.CHUNK_SIZE);
        int playerChunkY = Math.floorDiv(player.getTileY(), Chunk.CHUNK_SIZE);
        Vector2 playerChunkPos = new Vector2(playerChunkX, playerChunkY);
        float distance = Math.abs(chunkPos.x - playerChunkPos.x) +
            Math.abs(chunkPos.y - playerChunkPos.y);

        return distance <= radius;
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
        for (int dx = -CHUNK_LOAD_RADIUS; dx <= CHUNK_LOAD_RADIUS; dx++) {
            for (int dy = -CHUNK_LOAD_RADIUS; dy <= CHUNK_LOAD_RADIUS; dy++) {
                Vector2 chunkPos = new Vector2(playerChunkX + dx, playerChunkY + dy);
                if (!world.getChunks().containsKey(chunkPos) && !pendingChunks.contains(chunkPos)) {
                    chunkRequestQueue.offer(chunkPos);
                }
            }
        }
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
        if (bp.username.equals(getLocalUsername())) return;
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
        if (object instanceof NetworkProtocol.ItemPickup) {
            handleItemPickup((NetworkProtocol.ItemPickup) object);
            return; // Message handled
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
                ChestScreen chestScreen = GameContext.get().getGameScreen().getChestScreen();
                if (chestScreen != null && chestScreen.getChestData().chestId.equals(update.chestId)) {
                    chestScreen.getChestData().setItems(update.items);
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
        GameContext.get().getWorld().getItemEntityManager()
            .spawnItemEntityFromNetwork(drop.itemData, drop.x, drop.y);
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
            if (GameContext.get().getPlayer() != null) {
                lastKnownState = GameContext.get().getPlayer().getPlayerData();
            }
            cleanupConnection();
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
                GameLogger.error("Chunk already requested: " + chunkPos);
                return;
            }
            pendingChunks.add(chunkPos);
            NetworkProtocol.ChunkRequest request = new NetworkProtocol.ChunkRequest();
            request.chunkX = (int) chunkPos.x;
            request.chunkY = (int) chunkPos.y;
            request.timestamp = System.currentTimeMillis();
            boolean isPlayerCurrentChunk = false;
            if (GameContext.get().getPlayer() != null) {
                int playerChunkX = Math.floorDiv(GameContext.get().getPlayer().getTileX(), Chunk.CHUNK_SIZE);
                int playerChunkY = Math.floorDiv(GameContext.get().getPlayer().getTileY(), Chunk.CHUNK_SIZE);
                isPlayerCurrentChunk = (chunkPos.x == playerChunkX && chunkPos.y == playerChunkY);
            }
            client.sendTCP(request);

            if (isPlayerCurrentChunk) {
                GameLogger.info("Requested player's current chunk at " + chunkPos + " (HIGH PRIORITY)");
            } else {
                GameLogger.info("Requested chunk at " + chunkPos);
            }
            setupChunkRequestTimeout(chunkPos, 1);

        } catch (Exception e) {
            GameLogger.error("Failed to request chunk at " + chunkPos + ": " + e.getMessage());
            pendingChunks.remove(chunkPos);
        }
    }

    private void setupChunkRequestTimeout(Vector2 chunkPos, int attempt) {
        long timeoutMs = Math.min(2000 + (attempt * 1000), 8000); // 2s first try, +1s per attempt, max 8s

        scheduler.schedule(() -> {
            if (pendingChunks.contains(chunkPos) &&
                GameContext.get().getWorld() != null &&
                !GameContext.get().getWorld().getChunks().containsKey(chunkPos)) {

                if (attempt <= 5) { // Max 5 retry attempts
                    GameLogger.error("Chunk request timeout for " + chunkPos +
                        " - retry attempt " + attempt + "/5");
                    if (isConnected() && isAuthenticated()) {
                        pendingChunks.remove(chunkPos);
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        NetworkProtocol.ChunkRequest request = new NetworkProtocol.ChunkRequest();
                        request.chunkX = (int) chunkPos.x;
                        request.chunkY = (int) chunkPos.y;
                        request.timestamp = System.currentTimeMillis();
                        pendingChunks.add(chunkPos);
                        client.sendTCP(request);
                        setupChunkRequestTimeout(chunkPos, attempt + 1);
                    } else {
                        GameLogger.error("Cannot retry chunk request - not connected");
                        pendingChunks.remove(chunkPos);
                    }
                } else {
                    GameLogger.error("Max retry attempts reached for chunk " + chunkPos + " - giving up");
                    pendingChunks.remove(chunkPos);
                    if (isPlayerCurrentChunk(chunkPos)) {
                        GameLogger.error("Generating emergency fallback chunk for player position");
                        generateFallbackChunk(chunkPos);
                    }
                }
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);
    }

    private void generateFallbackChunk(Vector2 chunkPos) {
        if (!GameContext.get().isMultiplayer() || GameContext.get().getWorld() == null) return;

        try {
            GameLogger.error("Generating emergency fallback chunk at " + chunkPos);
            long seed = worldSeed;
            BiomeManager biomeManager = GameContext.get().getBiomeManager();
            Chunk fallbackChunk = UnifiedWorldGenerator.generateChunk(
                (int)chunkPos.x, (int)chunkPos.y, seed, biomeManager);
            if (fallbackChunk.getBiome() == null) {
                fallbackChunk.setBiome(biomeManager.getBiome(BiomeType.PLAINS));
            }
            GameContext.get().getWorld().getChunks().put(chunkPos, fallbackChunk);
            GameLogger.error("Added emergency fallback chunk at " + chunkPos);

        } catch (Exception e) {
            GameLogger.error("Failed to generate fallback chunk: " + e.getMessage());
        }
    }
    /**
     * Checks if the given chunk position is the player's current chunk
     */
    private boolean isPlayerCurrentChunk(Vector2 chunkPos) {
        if (GameContext.get().getPlayer() == null) return false;

        int playerChunkX = Math.floorDiv(GameContext.get().getPlayer().getTileX(), Chunk.CHUNK_SIZE);
        int playerChunkY = Math.floorDiv(GameContext.get().getPlayer().getTileY(), Chunk.CHUNK_SIZE);
        return (chunkPos.x == playerChunkX && chunkPos.y == playerChunkY);
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
                        WorldObject newObj = new WorldObject();
                        newObj.updateFromData(update.data);
                        objectManager.addObjectToChunk(newObj);
                        break;

                    case REMOVE:
                        if (update.data == null || !update.data.containsKey("tileX") || !update.data.containsKey("tileY")) {
                            GameLogger.error("Client: WorldObjectUpdate REMOVE missing tile position data.");
                            return;
                        }
                        int baseTileX = Integer.parseInt(update.data.get("tileX").toString());
                        int baseTileY = Integer.parseInt(update.data.get("tileY").toString());
                        int chunkX = Math.floorDiv(baseTileX, Chunk.CHUNK_SIZE);
                        int chunkY = Math.floorDiv(baseTileY, Chunk.CHUNK_SIZE);
                        Vector2 chunkPos = new Vector2(chunkX, chunkY);
                        objectManager.removeObjectFromChunk(chunkPos, update.objectId, baseTileX, baseTileY);
                        World world = GameContext.get().getWorld();
                        if (world != null) {
                            Chunk chunk = world.getChunkAtPosition(baseTileX * World.TILE_SIZE, baseTileY * World.TILE_SIZE);
                            if (chunk != null) {
                                chunk.getWorldObjects().removeIf(obj -> obj.getId().equals(update.objectId));
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
        if (chunkRequestQueue.isEmpty() || !isConnected() || !isAuthenticated()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastRequestTime < CHUNK_REQUEST_INTERVAL) {
            return;
        }
        if (pendingChunks.size() >= MAX_CONCURRENT_CHUNK_REQUESTS) {
            return;
        }
        Vector2 playerChunkPos;
        if (GameContext.get().getPlayer() != null) {
            int playerChunkX = Math.floorDiv(GameContext.get().getPlayer().getTileX(), Chunk.CHUNK_SIZE);
            int playerChunkY = Math.floorDiv(GameContext.get().getPlayer().getTileY(), Chunk.CHUNK_SIZE);
            playerChunkPos = new Vector2(playerChunkX, playerChunkY);
        } else {
            playerChunkPos = null;
        }
        if (playerChunkPos != null && chunkRequestQueue.size() > 1) {
            List<Vector2> sortedQueue = new ArrayList<>(chunkRequestQueue);
            sortedQueue.sort((a, b) -> {
                float distA = Vector2.dst(a.x, a.y, playerChunkPos.x, playerChunkPos.y);
                float distB = Vector2.dst(b.x, b.y, playerChunkPos.x, playerChunkPos.y);
                return Float.compare(distA, distB);
            });
            chunkRequestQueue.clear();
            chunkRequestQueue.addAll(sortedQueue);
        }
        Vector2 nextChunk = chunkRequestQueue.poll();
        if (nextChunk != null &&
            GameContext.get().getWorld() != null &&
            !GameContext.get().getWorld().getChunks().containsKey(nextChunk) &&
            !pendingChunks.contains(nextChunk) &&
            !loadingChunks.containsKey(nextChunk)) {
            requestChunk(nextChunk);
            lastRequestTime = now;
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
                    otherPlayer = new OtherPlayer(update.username, update.x, update.y);
                    otherPlayer.setWantsToRun(update.wantsToRun);
                    otherPlayers.put(update.username, otherPlayer);
                    GameLogger.info("Created new OtherPlayer: " + update.username);
                }
                otherPlayer.setWantsToRun(update.wantsToRun);
                otherPlayer.updateFromNetwork(update);
                playerUpdates.put(update.username, update);
            }
        });
    }


    private void handlePlayerJoined(NetworkProtocol.PlayerJoined joinMsg) {
        final String joinKey = joinMsg.username + "_" + joinMsg.timestamp;
        Gdx.app.postRunnable(() -> {
            if (joinMsg.username.equals(localUsername)) {
                return;
            }

            synchronized (otherPlayers) {
                if (recentJoinEvents.contains(joinKey)) {
                    return;
                }

                if (otherPlayers.containsKey(joinMsg.username)) {
                    GameLogger.info("Player " + joinMsg.username +
                        " already exists, updating position");
                    OtherPlayer existingPlayer = otherPlayers.get(joinMsg.username);
                    existingPlayer.setPosition(new Vector2(joinMsg.x, joinMsg.y));
                    return;
                }
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
            OtherPlayer leftPlayer = otherPlayers.remove(leftMsg.username);
            if (leftPlayer != null) {
                leftPlayer.dispose();
            }
            playerUpdates.remove(leftMsg.username);
            playerPingMap.remove(leftMsg.username);
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
                pokemon.setUuid(spawnData.uuid);
                pokemon.setWorld(GameContext.get().getWorld());
                pokemon.setDirection("down");
                pokemon.setSpawnTime(spawnData.timestamp / 1000L);
                pokemon.setNetworkControlled(true);
                trackedWildPokemon.put(spawnData.uuid, pokemon);
                if (GameContext.get().getWorld() != null &&
                    GameContext.get().getWorld().getPokemonSpawnManager() != null) {
                    GameContext.get().getWorld().getPokemonSpawnManager().addPokemonToChunk(
                        pokemon,
                        new Vector2(
                            Math.floorDiv((int)spawnData.x, World.CHUNK_SIZE * World.TILE_SIZE),
                            Math.floorDiv((int)spawnData.y, World.CHUNK_SIZE * World.TILE_SIZE)
                        )
                    );
                }

                GameLogger.info("Spawned network Pokemon: " + spawnData.data.getName() +
                    " at (" + spawnData.x + "," + spawnData.y + ")");
            } catch (Exception e) {
                GameLogger.error("Error handling Pokemon spawn: " + e.getMessage());
                e.printStackTrace();
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

            if (pokemon == null) {
                requestPokemonSpawnData(update.uuid);
                return;
            }
            pokemon.setNetworkControlled(true);
            pokemon.applyNetworkUpdate(update.x, update.y, update.direction, update.isMoving, update.timestamp);
            if (update.level > 0) pokemon.setLevel(update.level);
            if (update.currentHp > 0) pokemon.setCurrentHp(update.currentHp);
            pokemon.setSpawnTime(update.timestamp / 1000L);

            if (pokemonUpdateHandler != null) {
                pokemonUpdateHandler.onUpdate(update);
            }
        });
    }
    private void handlePokemonBatchUpdate(NetworkProtocol.PokemonBatchUpdate batchUpdate) {
        if (batchUpdate == null || batchUpdate.updates == null) return;

        for (NetworkProtocol.PokemonUpdate update : batchUpdate.updates) {
            handlePokemonUpdate(update);
        }
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
                initializeWorldBasic(response.seed, response.worldTimeInMinutes, response.dayLength);
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
            BiomeManager biomeManager = new BiomeManager(seed);
            GameContext.get().setBiomeManager(biomeManager);

            if (GameContext.get().getWorld() == null) {
                GameContext.get().setWorld(new World(worldData));
                GameLogger.info("Basic world initialization complete");
            }

        } catch (Exception e) {
            GameLogger.error("Failed to initialize basic world: " + e.getMessage());
            throw new RuntimeException("World initialization failed", e);
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



    private static final float POKEMON_UPDATE_THRESHOLD = 0.1f;
    private float pokemonUpdateAccumulator = 0f;

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
