package org.discord;

import com.badlogic.gdx.math.Vector2;
import com.esotericsoftware.kryonet.FrameworkMessage;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.minlog.Log;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.managers.BiomeManager;
import io.github.pokemeetup.managers.BiomeTransitionResult;
import io.github.pokemeetup.managers.DatabaseManager;
import io.github.pokemeetup.multiplayer.PlayerManager;
import io.github.pokemeetup.multiplayer.ServerPlayer;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.multiplayer.server.ServerStorageSystem;
import io.github.pokemeetup.multiplayer.server.storage.FileStorage;
import io.github.pokemeetup.system.data.BlockSaveData;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import io.github.pokemeetup.multiplayer.server.events.EventManager;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.data.PokemonData;
import io.github.pokemeetup.system.data.WorldData;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldManager;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.PasswordUtils;
import io.github.pokemeetup.utils.textures.TextureManager;
import org.discord.context.ServerGameContext;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static io.github.pokemeetup.system.gameplay.overworld.WorldObject.WorldObjectManager.MAX_POKEBALLS_PER_CHUNK;
import static io.github.pokemeetup.system.gameplay.overworld.WorldObject.WorldObjectManager.POKEBALL_SPAWN_CHANCE;

public class GameServer {
    private static final int WRITE_BUFFER = 1024 * 1024;
    private static final int OBJECT_BUFFER = 512 * 1024;
    private static final int SCHEDULER_POOL_SIZE = 3;
    private static final long AUTH_TIMEOUT = 10000;
    private static final int SYNC_BATCH_SIZE = 10;
    private static final float SYNC_INTERVAL = 1 / 20f;
    private static final long SAVE_INTERVAL = 300000;
    private static final long RECONNECT_COOLDOWN = 0; // 2 second cooldown
    private final ServerWorldObjectManager worldObjectManager;
    private final Server networkServer;
    private final ServerConnectionConfig config;
    private final BiomeManager biomeManager;
    private final ServerStorageSystem storageSystem;
    private final EventManager eventManager;
    private final DatabaseManager databaseManager;
    private final ConcurrentHashMap<Integer, String> connectedPlayers;
    private final PlayerManager playerManager;
    private final ScheduledExecutorService scheduler;
    private final Queue<NetworkProtocol.PlayerUpdate> pendingUpdates = new ConcurrentLinkedQueue<>();
    private final Map<String, Integer> activeUserConnections = new ConcurrentHashMap<>();
    private final Map<String, ServerPlayer> activePlayers = new ConcurrentHashMap<>();
    private final Map<String, ConnectionInfo> activeConnections = new ConcurrentHashMap<>();
    private final Map<String, Long> recentDisconnects = new ConcurrentHashMap<>();
    private PluginManager pluginManager = null;
    private WorldData worldData;
    private FileStorage fileStorage;
    private ServerBlockManager blockManager;
    private volatile boolean running;
    private NetworkProtocol.ServerInfo serverInfo;


    public GameServer(ServerConnectionConfig config) {
        this.scheduler = Executors.newScheduledThreadPool(SCHEDULER_POOL_SIZE, r -> {
            Thread thread = new Thread(r, "GameServer-Scheduler");
            thread.setDaemon(true);
            return thread;
        });

        Log.set(Log.LEVEL_DEBUG);
        this.config = config;
        this.storageSystem = new ServerStorageSystem();
        this.networkServer = new Server(WRITE_BUFFER, OBJECT_BUFFER);
        NetworkProtocol.registerClasses(networkServer.getKryo());

        networkServer.getKryo().setReferences(false);

        this.worldObjectManager = new ServerWorldObjectManager();

        this.databaseManager = new DatabaseManager();
        this.eventManager = new EventManager();
        this.connectedPlayers = new ConcurrentHashMap<>();
        this.playerManager = new PlayerManager(storageSystem, eventManager);


        try {
            this.worldData = initializeMultiplayerWorld();
            this.blockManager = new ServerBlockManager();
            setupNetworkListener();
            this.pluginManager = new PluginManager( worldData);
            this.biomeManager = new BiomeManager(worldData.getConfig().getSeed());
        } catch (Exception e) {
            GameLogger.error("Failed to initialize game world: " + e.getMessage());
            throw new RuntimeException("Failed to initialize server world", e);
        }
    }

    private WorldData initializeMultiplayerWorld() {
        try {
            // Load or create WorldData
            WorldData worldData = ServerGameContext.get().getWorldManager().getWorld(CreatureCaptureGame.MULTIPLAYER_WORLD_NAME);
            if (worldData == null) {
                // Create new WorldData
                worldData = ServerGameContext.get().getWorldManager().createWorld(
                    CreatureCaptureGame.MULTIPLAYER_WORLD_NAME,
                    System.currentTimeMillis(),
                    0.15f,
                    0.05f
                );
                ServerGameContext.get().getWorldManager().saveWorld(worldData);
            }

            return worldData;
        } catch (Exception e) {
            GameLogger.error("Failed to initialize multiplayer world: " + e.getMessage());
            throw new RuntimeException("WorldData initialization failed", e);
        }
    }

    private void broadcastPlayerStates() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                List<NetworkProtocol.PlayerUpdate> updates = new ArrayList<>();
                NetworkProtocol.PlayerUpdate update;

                // Batch pending updates
                while ((update = pendingUpdates.poll()) != null && updates.size() < SYNC_BATCH_SIZE) {
                    updates.add(update);
                }

                if (updates.isEmpty()) return;

                NetworkProtocol.PlayerPosition position = new NetworkProtocol.PlayerPosition();
                position.players = new HashMap<>();
                updates.forEach(u -> position.players.put(u.username, u));

                // Broadcast to all connected clients
                networkServer.sendToAllTCP(position);

            } catch (Exception e) {
                GameLogger.error("Error broadcasting player states: " + e.getMessage());
            }
        }, 0, (long) (SYNC_INTERVAL * 1000), TimeUnit.MILLISECONDS);
    }

    private void handleDisconnect(Connection connection) {
        String username = connectedPlayers.get(connection.getID());
        if (username != null) {
            GameLogger.info("Handling disconnect for user: " + username);

            synchronized (activeConnections) {
                // Track disconnect time
                recentDisconnects.put(username, System.currentTimeMillis());

                // Clean up connection tracking
                activeConnections.remove(username);
                cleanupPlayerSession(connection.getID(), username);

                // Schedule disconnect broadcast with delay
                scheduler.schedule(() -> {
                    try {
                        NetworkProtocol.PlayerLeft leftMessage = new NetworkProtocol.PlayerLeft();
                        leftMessage.username = username;
                        leftMessage.timestamp = System.currentTimeMillis();
                        networkServer.sendToAllTCP(leftMessage);
                        GameLogger.info("Broadcast disconnect for: " + username);
                    } catch (Exception e) {
                        GameLogger.error("Failed to broadcast disconnect: " + e.getMessage());
                    }
                }, 500, TimeUnit.MILLISECONDS);

                GameLogger.info("Player disconnected: " + username);
            }
        }
    }

    // Clean up old disconnects periodically
    private void scheduleCleanup() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                long now = System.currentTimeMillis();
                recentDisconnects.entrySet().removeIf(entry ->
                    now - entry.getValue() > RECONNECT_COOLDOWN);
            } catch (Exception e) {
                GameLogger.error("Cleanup error: " + e.getMessage());
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    public void shutdown() {
        try {
            NetworkProtocol.ServerShutdown shutdownMsg = new NetworkProtocol.ServerShutdown();
            shutdownMsg.reason = "Server is shutting down";
            networkServer.sendToAllTCP(shutdownMsg);

            // Wait briefly for messages to be sent
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Proceed with shutdown
            stop();
            // Shut down network server
            networkServer.stop();

            // Shut down scheduler
            if (scheduler != null) {
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    scheduler.shutdownNow();
                }
            }

            GameLogger.info("Server shutdown complete");
        } catch (Exception e) {
            GameLogger.error("Error during shutdown: " + e.getMessage());
        }
    }

    private void handleServerInfoRequest(Connection connection, NetworkProtocol.ServerInfoRequest request) {
        try {
            NetworkProtocol.ServerInfoResponse response = new NetworkProtocol.ServerInfoResponse();
            serverInfo.playerCount = connectedPlayers.size();
            response.serverInfo = serverInfo;
            response.timestamp = System.currentTimeMillis();

            connection.sendTCP(response);
        } catch (Exception e) {
            GameLogger.error("Error handling server info request: " + e.getMessage());
        }
    }

    private void cleanupPlayerSession(int connectionId, String username) {
        synchronized (activeUserConnections) {
            activeUserConnections.remove(username);
            connectedPlayers.remove(connectionId);
            ServerPlayer player = activePlayers.remove(username);

            if (player != null) {
                try {
                    // Save player state before cleanup
                    PlayerData finalState = player.getData();
                    storageSystem.savePlayerData(finalState.getUsername(),finalState);
                } catch (Exception e) {
                    GameLogger.error("Error saving player state during cleanup: " + e.getMessage());
                }
            }
        }
    }

    private void sendSuccessfulLoginResponse(Connection connection, ServerPlayer player) {
        NetworkProtocol.LoginResponse response = new NetworkProtocol.LoginResponse();
        response.success = true;
        response.username = player.getUsername();
        response.message = "Login successful";
        response.x = (int) player.getPosition().x;
        response.y = (int) player.getPosition().y;
        response.seed = worldData.getConfig().getSeed();
        response.worldTimeInMinutes = worldData.getWorldTimeInMinutes();
        response.dayLength = worldData.getDayLength();
        response.timestamp = System.currentTimeMillis();

        connection.sendTCP(response);
    }

    private void broadcastPlayerJoin(Connection connection, ServerPlayer player) {
        NetworkProtocol.PlayerJoined joinMessage = new NetworkProtocol.PlayerJoined();
        joinMessage.username = player.getUsername();
        joinMessage.x = player.getPosition().x;
        joinMessage.y = player.getPosition().y;
        joinMessage.timestamp = System.currentTimeMillis();

        // Send to all except the joining player
        networkServer.sendToAllExceptTCP(connection.getID(), joinMessage);

        // Send system message about join
        NetworkProtocol.ChatMessage systemMessage = new NetworkProtocol.ChatMessage();
        systemMessage.sender = "System";
        systemMessage.content = player.getUsername() + " has joined the game";
        systemMessage.type = NetworkProtocol.ChatType.SYSTEM;
        systemMessage.timestamp = System.currentTimeMillis();

        // Send to everyone including the new player
        networkServer.sendToAllTCP(systemMessage);
    }

    private Connection findConnection(int connectionId) {
        for (Connection conn : networkServer.getConnections()) {
            if (conn.getID() == connectionId) {
                return conn;
            }
        }
        return null;
    }

    private void sendLoginFailure(Connection connection, String message) {
        NetworkProtocol.LoginResponse response = new NetworkProtocol.LoginResponse();
        response.success = false;
        response.message = message;
        response.timestamp = System.currentTimeMillis();

        try {
            connection.sendTCP(response);
            GameLogger.info("Sent login failure: " + message);
        } catch (Exception e) {
            GameLogger.error("Error sending login failure: " + e.getMessage());
        }
    }

    private void handlePlayerDisconnect(Connection connection) {
        String username = connectedPlayers.get(connection.getID());
        if (username != null) {
            synchronized (activeUserConnections) {
                // Save player data before removing
                ServerPlayer player = playerManager.getPlayer(username);
                if (player != null) {
                    try {
                        storageSystem.savePlayerData(player.getUsername(), player.getData());
                        GameLogger.info("Saved player data for " + username + " on disconnect");
                    } catch (Exception e) {
                        GameLogger.error("Error saving player data for " + username + ": " + e.getMessage());
                    }
                }

                // Remove player from manager
                playerManager.removePlayer(username);

                // Clean up connection
                activeUserConnections.remove(username);
                connectedPlayers.remove(connection.getID());

                // Broadcast disconnect to other players
                NetworkProtocol.PlayerLeft leftMessage = new NetworkProtocol.PlayerLeft();
                leftMessage.username = username;
                leftMessage.timestamp = System.currentTimeMillis();
                networkServer.sendToAllTCP(leftMessage);

                GameLogger.info("Player disconnected: " + username);
            }
        }
    }

    private void handlePlayerUpdate(Connection connection, NetworkProtocol.PlayerUpdate update) {
        try {
            String username = connectedPlayers.get(connection.getID());
            if (username == null || !username.equals(update.username)) {
                return;
            }

            ServerPlayer player = playerManager.getPlayer(username);
            if (player == null) return;

            player.updatePosition(update.x, update.y, update.direction, update.isMoving);
            player.setRunning(update.wantsToRun);

            PlayerData playerData = player.getData();
            playerData.setX(update.x);
            playerData.setY(update.y);
            playerData.setDirection(update.direction);
            playerData.setMoving(update.isMoving);
            playerData.setWantsToRun(update.wantsToRun);
            if (update.inventoryItems != null) {
                playerData.setInventoryItems(Arrays.asList(update.inventoryItems));
            }
            if (update.partyPokemon != null) {
                playerData.setPartyPokemon(update.partyPokemon);
            }

            // Save using storageSystem
            storageSystem.savePlayerData(player.getUsername(), playerData);

            // Broadcast update with running state
            networkServer.sendToAllTCP(update);

        } catch (Exception e) {
            GameLogger.error("Error handling player update: " + e.getMessage());
        }
    }

    private void handlePokemonSpawn(Connection connection, NetworkProtocol.WildPokemonSpawn spawnRequest) {
        try {
            WorldData world = ServerGameContext.get().getWorldManager().getWorld(CreatureCaptureGame.MULTIPLAYER_WORLD_NAME);
            if (world == null) {
                GameLogger.error("Cannot spawn Pokemon: World is null");
                return;
            }

            if (!isValidSpawnPosition(spawnRequest.x, spawnRequest.y)) {
                GameLogger.error("Invalid spawn position: " + spawnRequest.x + "," + spawnRequest.y);
                return;
            }

            WildPokemon pokemon = createWildPokemon(spawnRequest);
            if (pokemon == null) {
                GameLogger.error("Failed to create Pokemon from spawn request");
                return;
            }
            // Create broadcast message
            NetworkProtocol.WildPokemonSpawn broadcastSpawn = createSpawnBroadcast(pokemon);

            // Broadcast to all clients
            try {
                networkServer.sendToAllTCP(broadcastSpawn);
                GameLogger.info("Broadcast Pokemon spawn: " + pokemon.getName() +
                    " (UUID: " + pokemon.getUuid() + ")");
            } catch (Exception e) {
                GameLogger.error("Failed to broadcast Pokemon spawn: " + e.getMessage());
                world.removeWildPokemon(pokemon.getUuid());
            }

        } catch (Exception e) {
            GameLogger.error("Error handling Pokemon spawn: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean isValidSpawnPosition(float x, float y) {
        int tileX = (int) (x / World.TILE_SIZE);
        int tileY = (int) (y / World.TILE_SIZE);

        if (tileX < 0 || tileX >= World.WORLD_SIZE ||
            tileY < 0 || tileY >= World.WORLD_SIZE) {
            return false;
        }

        WorldData world = ServerGameContext.get().getWorldManager().getWorld(CreatureCaptureGame.MULTIPLAYER_WORLD_NAME);
        if (world == null) return false;

        return true;
    }

    private NetworkProtocol.WildPokemonSpawn createSpawnBroadcast(WildPokemon pokemon) {
        NetworkProtocol.WildPokemonSpawn broadcast = new NetworkProtocol.WildPokemonSpawn();
        broadcast.uuid = pokemon.getUuid();
        broadcast.x = pokemon.getX();
        broadcast.y = pokemon.getY();
        PokemonData pokemonData = new PokemonData();
        pokemonData.setName(pokemon.getName());
        pokemonData.setLevel(pokemon.getLevel());
        pokemonData.setPrimaryType(pokemon.getPrimaryType());
        pokemonData.setSecondaryType(pokemon.getSecondaryType());

        // Set stats
        if (pokemon.getStats() != null) {
            PokemonData.Stats stats = new PokemonData.Stats(pokemon.getStats());
            pokemonData.setStats(stats);
        }

        // Set moves
        List<PokemonData.MoveData> moves = pokemon.getMoves().stream()
            .map(PokemonData.MoveData::fromMove)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        pokemonData.setMoves(moves);

        broadcast.data = pokemonData;
        broadcast.timestamp = System.currentTimeMillis();

        return broadcast;
    }

    private WildPokemon createWildPokemon(NetworkProtocol.WildPokemonSpawn spawnRequest) {
        try {
            // Create Pokemon from the spawn data
            WildPokemon pokemon = new WildPokemon(
                spawnRequest.data.getName(),
                spawnRequest.data.getLevel(),
                (int) spawnRequest.x,
                (int) spawnRequest.y,
                TextureManager.getOverworldSprite(spawnRequest.data.getName())
            );

            // Set additional data
            pokemon.setUuid(spawnRequest.uuid != null ? spawnRequest.uuid : UUID.randomUUID());
            pokemon.setSpawnTime(System.currentTimeMillis() / 1000L);

            return pokemon;
        } catch (Exception e) {
            GameLogger.error("Error creating WildPokemon: " + e.getMessage());
            return null;
        }
    }

    private boolean authenticateUser(String username, String password) {
        // Retrieve the user's stored password hash from the database
        String storedHash = databaseManager.getPasswordHash(username);
        if (storedHash == null) {
            GameLogger.error("Authentication failed: Username '" + username + "' does not exist.");
            return false;
        }

        // Compare the provided password with the stored hash
        return PasswordUtils.verifyPassword(password, storedHash);
    }

    private void handleLoginRequest(Connection connection, NetworkProtocol.LoginRequest request) {
        try {
            GameLogger.info("Processing login request for: " + request.username);

            // Check for recent disconnect cooldown
            Long lastDisconnect = recentDisconnects.get(request.username);
            if (lastDisconnect != null &&
                System.currentTimeMillis() - lastDisconnect < RECONNECT_COOLDOWN) {
                sendLoginFailure(connection, "Please wait before reconnecting");
                return;
            }

            // Validate credentials
            if (!authenticateUser(request.username, request.password)) {
                sendLoginFailure(connection, "Invalid credentials");
                return;
            }

            synchronized (activeConnections) {
                // Check for existing connection
                ConnectionInfo existingConnection = activeConnections.get(request.username);
                if (existingConnection != null) {
                    // Handle existing connection
                    Connection oldConnection = findConnection(existingConnection.connectionId);
                    if (oldConnection != null && oldConnection.isConnected()) {
                        // Force disconnect old connection
                        NetworkProtocol.ForceDisconnect forceDisconnect = new NetworkProtocol.ForceDisconnect();
                        forceDisconnect.reason = "Logged in from another location";
                        oldConnection.sendTCP(forceDisconnect);

                        // Wait for disconnect to process
                        Thread.sleep(100);
                        oldConnection.close();

                        // Clean up old session
                        cleanupPlayerSession(existingConnection.connectionId, request.username);
                        Thread.sleep(500); // Delay to prevent race conditions
                    }
                }

                // Create new session
                ConnectionInfo newConnection = new ConnectionInfo(connection.getID());
                activeConnections.put(request.username, newConnection);

                ServerPlayer player = playerManager.createOrLoadPlayer(request.username);
                if (player == null) {
                    sendLoginFailure(connection, "Failed to initialize player data");
                    return;
                }

                // Register new connection
                activePlayers.put(request.username, player);
                connectedPlayers.put(connection.getID(), request.username);
                newConnection.isAuthenticated = true;

                // Send successful login response
                sendSuccessfulLoginResponse(connection, player);

                // Broadcast join only if not recently reconnected
                if (lastDisconnect == null ||
                    System.currentTimeMillis() - lastDisconnect > RECONNECT_COOLDOWN) {
                    broadcastPlayerJoin(connection, player);
                }

                GameLogger.info("Login successful for: " + request.username);
            }

        } catch (Exception e) {
            GameLogger.error("Login error: " + e.getMessage());
            sendLoginFailure(connection, "Server error occurred");
        }
    }

    private void setupNetworkListener() {
        networkServer.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                try {
                    GameLogger.info("New connection attempt from: " + connection.getRemoteAddressTCP());
                    // Check max players
                    if (playerManager.getOnlinePlayers().size() >= config.getMaxPlayers()) {
                        GameLogger.info("Connection rejected: Max players reached");
                        sendConnectionResponse(connection, false, "Server is full");
                        scheduler.schedule(() -> connection.close(), 100, TimeUnit.MILLISECONDS);
                        return;
                    }

                    // Send success response
                    NetworkProtocol.ConnectionResponse response = new NetworkProtocol.ConnectionResponse();
                    response.success = true;
                    response.message = "Connection established";
                    connection.sendTCP(response);

                    GameLogger.info("Connection " + connection.getID() + " established - awaiting authentication");

                    // Set authentication timeout
                    scheduler.schedule(() -> {
                        if (!connectedPlayers.containsKey(connection.getID())) {
                            GameLogger.info("Authentication timeout for connection: " + connection.getID());
                            connection.close();
                        }
                    }, AUTH_TIMEOUT, TimeUnit.MILLISECONDS);

                } catch (Exception e) {
                    GameLogger.error("Error handling connection: " + e.getMessage());
                    connection.close();
                }
            }

            @Override
            public void received(Connection connection, Object object) {
                try {
                    if (object instanceof FrameworkMessage) {
                        // Ignore KryoNet's internal messages
                        return;
                    }
                    if (object instanceof NetworkProtocol.WorldObjectUpdate) {
                        NetworkProtocol.WorldObjectUpdate update = (NetworkProtocol.WorldObjectUpdate) object;
                        // Validate object data
                        if (update.data == null || update.data.isEmpty()) {
                            GameLogger.error("Invalid WorldObjectUpdate received - empty data");
                            return;
                        }
                    }

                    if (object instanceof NetworkProtocol.LoginRequest) {
                        handleLoginRequest(connection, (NetworkProtocol.LoginRequest) object);
                        return;
                    }

                    if (object instanceof NetworkProtocol.RegisterRequest) {
                        handleRegisterRequest(connection, (NetworkProtocol.RegisterRequest) object);
                        return;
                    }

                    if (object instanceof NetworkProtocol.ChunkRequest) {
                        handleChunkRequest(connection, (NetworkProtocol.ChunkRequest) object);
                        return;
                    }
                    if (object instanceof NetworkProtocol.BlockPlacement) {
                        handleBlockPlacement(connection, (NetworkProtocol.BlockPlacement) object);
                    }

                    // Handle other message types based on authentication
                    String username = connectedPlayers.get(connection.getID());
                    if (username == null) {
                        GameLogger.error("Received unauthorized message from Connection " + connection.getID());
                        return;
                    }

                    processAuthenticatedMessage(connection, object);
                } catch (Exception e) {
                    GameLogger.error("Error handling message: " + e.getMessage());
                }
            }

            @Override
            public void disconnected(Connection connection) {
                handleDisconnect(connection);
            }
        });
    }

    private void initializePeriodicTasks() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                // Save world data periodically
                if (worldData != null) {
                    ServerGameContext.get().getWorldManager().saveWorld(worldData);
                    GameLogger.info("World data saved periodically.");
                }
            } catch (Exception e) {
                GameLogger.error("Error during periodic world save: " + e.getMessage());
            }
        }, SAVE_INTERVAL, SAVE_INTERVAL, TimeUnit.MILLISECONDS);
    }

    private void handleLogout(Connection connection, NetworkProtocol.Logout logout) {
        String username = connectedPlayers.get(connection.getID());
        if (username != null && username.equals(logout.username)) {
            try {
                // Save final state
                ServerPlayer player = playerManager.getPlayer(username);
                if (player != null) {
                    PlayerData finalState = player.getData();
                    WorldData worldData = ServerGameContext.get().getWorldManager().getWorld(CreatureCaptureGame.MULTIPLAYER_WORLD_NAME);
                    if (worldData != null) {
                        worldData.savePlayerData(username, finalState, true);
                        ServerGameContext.get().getWorldManager().saveWorld(worldData);
                    }
                }

                // Clean up connection
                handlePlayerDisconnect(connection);

                // Send acknowledgment
                NetworkProtocol.LogoutResponse response = new NetworkProtocol.LogoutResponse();
                response.success = true;
                connection.sendTCP(response);

            } catch (Exception e) {
                GameLogger.error("Error handling logout: " + e.getMessage());
                NetworkProtocol.LogoutResponse response = new NetworkProtocol.LogoutResponse();
                response.success = false;
                response.message = "Error saving state";
                connection.sendTCP(response);
            }
        }
    }

    private void sendConnectionResponse(Connection connection, boolean success, String message) {
        NetworkProtocol.ConnectionResponse response = new NetworkProtocol.ConnectionResponse();
        response.success = success;
        response.message = message;

        try {
            connection.sendTCP(response);
        } catch (Exception e) {
            GameLogger.error("Error sending connection response: " + e.getMessage());
        }
    }

    private void handleChatMessage(Connection connection, NetworkProtocol.ChatMessage message) {
        if (message == null || message.content == null || message.content.isEmpty()) {
            return;
        }

        if (message.timestamp == 0) {
            message.timestamp = System.currentTimeMillis();
        }

        for (Connection conn : networkServer.getConnections()) {
            if (conn.getID() != connection.getID()) {
                try {
                    networkServer.sendToTCP(conn.getID(), message);
                } catch (Exception e) {
                }
            }
        }
    }

    private void sendRegistrationResponse(Connection connection, boolean success, String message) {
        NetworkProtocol.RegisterResponse response = new NetworkProtocol.RegisterResponse();
        response.success = success;
        response.message = message;
        networkServer.sendToTCP(connection.getID(), response);
    }

    private boolean isValidUsername(String username) {
        return username != null &&
            username.length() >= 3 &&
            username.length() <= 20 &&
            username.matches("^[a-zA-Z0-9_]+$");
    }

    private void handleRegisterRequest(Connection connection, NetworkProtocol.RegisterRequest request) {
        try {
            GameLogger.info("Processing registration request for username: " + request.username);

            // Basic validation
            if (request.username == null || request.username.isEmpty() ||
                request.password == null || request.password.isEmpty()) {
                sendRegistrationResponse(connection, false, "Username and password are required.");
                return;
            }

            // Validate username format
            if (!isValidUsername(request.username)) {
                sendRegistrationResponse(connection, false,
                    "Username must be 3-20 characters long and contain only letters, numbers, and underscores.");
                return;
            }

            // Check if username already exists
            if (databaseManager.checkUsernameExists(request.username)) {
                sendRegistrationResponse(connection, false, "Username already exists.");
                return;
            }

            // Attempt to register the player in database
            boolean success = databaseManager.registerPlayer(request.username, request.password);

            if (success) {
                GameLogger.info("Successfully registered new player: " + request.username);
                sendRegistrationResponse(connection, true, "Registration successful!");
            } else {
                GameLogger.error("Failed to register player: " + request.username);
                sendRegistrationResponse(connection, false, "Registration failed. Please try again.");
            }

        } catch (Exception e) {
            GameLogger.error("Error during registration: " + e.getMessage());
            sendRegistrationResponse(connection, false, "An error occurred during registration.");
        }
    }

    public void start() {
        try {
            GameLogger.info("Starting server...");

            if (!isPortAvailable(config.getTcpPort())) {
                throw new IOException("TCP port " + config.getTcpPort() + " is already in use.");
            }

            if (!isPortAvailable(config.getUdpPort())) {
                throw new IOException("UDP port " + config.getUdpPort() + " is already in use.");
            }

            GameLogger.info("Storage system initialized");

            GameLogger.info("World manager initialized");
            initializePeriodicTasks();
            // Load plugins
            pluginManager.loadPlugins();
            pluginManager.enablePlugins();
            GameLogger.info("Plugins loaded");

            // Register network classes
            NetworkProtocol.registerClasses(networkServer.getKryo());
            GameLogger.info("Network classes registered");

            networkServer.start();

            broadcastPlayerStates();

            networkServer.bind(config.getTcpPort(), config.getUdpPort());
            running = true;

            GameLogger.info("Server started successfully on TCP port " + config.getTcpPort() +
                " and UDP port " + config.getUdpPort());
            GameLogger.info("Maximum players: " + config.getMaxPlayers());

        } catch (Exception e) {
            GameLogger.info("Failed to start server: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Server failed to start", e);
        }
    }

    private boolean isPortAvailable(int port) {
        try (ServerSocket ss = new ServerSocket(port)) {
            ss.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public void stop() {
        if (!running) return;
        running = false;

        GameLogger.info("Shutting down server...");
        if (worldData != null) {
            ServerGameContext.get().getWorldManager().saveWorld(worldData);
        }

        playerManager.getOnlinePlayers().forEach(player -> {
            try {
                Integer connectionId = activeUserConnections.get(player.getUsername());
                if (connectionId != null) {
                    Connection[] connections = networkServer.getConnections();
                    Connection targetConnection = null;
                    for (Connection conn : connections) {
                        if (conn.getID() == connectionId) {
                            targetConnection = conn;
                            break;
                        }
                    }
                    if (targetConnection != null) {
                        targetConnection.close();
                        GameLogger.info("Disconnected player: " + player.getUsername());
                    }
                }
            } catch (Exception e) {
                GameLogger.info("Error disconnecting player " + player.getUsername() + ": " + e.getMessage());
            }
        });
        pluginManager.disablePlugins();
        eventManager.shutdown();
        if (networkServer != null) {
            networkServer.stop();
        }

        GameLogger.info("Server shutdown complete.");
    }

    // In GameServer.java, modify handleChunkRequest:
    private void handleChunkRequest(Connection connection, NetworkProtocol.ChunkRequest request) {
        try {
            int chunkX = request.chunkX;
            int chunkY = request.chunkY;
            Vector2 chunkPos = new Vector2(chunkX, chunkY);

            // Get or generate the chunk
            Chunk chunk = worldData.getChunks().get(chunkPos);
            if (chunk == null) {
                chunk = generateNewChunk(chunkX, chunkY);
                worldData.getChunks().put(chunkPos, chunk);
            }

            // Create response
            NetworkProtocol.ChunkData response = new NetworkProtocol.ChunkData();
            response.chunkX = chunkX;
            response.chunkY = chunkY;
            response.biomeType = chunk.getBiome().getType();
            response.tileData = chunk.getTileData();
            response.timestamp = System.currentTimeMillis();

            // Convert block data to network format
            List<BlockSaveData.BlockData> blockDataList = chunk.getBlockDataForSave();
            if (blockDataList != null && !blockDataList.isEmpty()) {
                // Set both blockData and blocks fields for compatibility
                response.blockData = blockDataList;
                response.blocks = new ArrayList<>();

                // Convert BlockSaveData to Map format
                for (BlockSaveData.BlockData blockData : blockDataList) {
                    Map<String, Object> blockMap = new HashMap<>();
                    blockMap.put("type", blockData.type);
                    blockMap.put("tileX", blockData.x);
                    blockMap.put("tileY", blockData.y);
                    blockMap.put("isFlipped", blockData.isFlipped);
                    blockMap.put("isChestOpen", blockData.isChestOpen);
                    if (blockData.chestData != null) {
                        blockMap.put("chestData", blockData.chestData);
                    }
                    response.blocks.add(blockMap);
                }

                GameLogger.info("Sending " + blockDataList.size() + " blocks for chunk " + chunkPos);
            }

            // Get world objects for this chunk
            List<WorldObject> chunkObjects = worldData.getChunkObjects().get(chunkPos);
            if (chunkObjects == null || chunkObjects.isEmpty()) {
                // Generate objects if none exist
                chunkObjects = generateChunkObjects(chunk, chunkPos);
                worldData.addChunkObjects(chunkPos, chunkObjects);
            }

            // Convert objects to network format
            response.worldObjects = new ArrayList<>();
            for (WorldObject obj : chunkObjects) {
                Map<String, Object> objData = new HashMap<>();
                objData.put("type", obj.getType().name());
                objData.put("tileX", obj.getTileX());
                objData.put("tileY", obj.getTileY());
                objData.put("id", obj.getId());
                response.worldObjects.add(objData);
            }

            GameLogger.info("Sending chunk data for " + chunkPos + " with " +
                response.worldObjects.size() + " objects and " +
                (response.blocks != null ? response.blocks.size() : 0) + " blocks");

            connection.sendTCP(response);

        } catch (Exception e) {
            GameLogger.error("Error handling chunk request: " + e.getMessage());
        }
    }

    private void handleWorldObjectUpdate(Connection connection, NetworkProtocol.WorldObjectUpdate update) {
        String username = connectedPlayers.get(connection.getID());
        if (username == null) {
            GameLogger.error("Unauthorized world object update attempt.");
            return;
        }

        switch (update.type) {
            case ADD:
                // Optionally handle object addition from clients if allowed
                break;
            case REMOVE:
                // Validate and remove the object
                boolean removed = worldObjectManager.removeObject(update.objectId);
                if (removed) {
                    // Broadcast removal to other clients
                    networkServer.sendToAllExceptTCP(connection.getID(), update);
                } else {
                    GameLogger.error("Failed to remove object: " + update.objectId);
                }
                break;
            case UPDATE:
                // Handle object updates if necessary
                break;
        }
    }

    private void processAuthenticatedMessage(Connection connection, Object message) {
        String username = connectedPlayers.get(connection.getID());
        ServerPlayer player = playerManager.getPlayer(username);

        if (player == null) {
            GameLogger.error("No player found for connection: " + connection.getID());
            return;
        }

        try {
            if (message instanceof NetworkProtocol.PlayerUpdate) {
                handlePlayerUpdate(connection, (NetworkProtocol.PlayerUpdate) message);
            } else if (message instanceof NetworkProtocol.ChatMessage) {
                handleChatMessage(connection, (NetworkProtocol.ChatMessage) message);
            } else if (message instanceof NetworkProtocol.WorldObjectUpdate) {
                handleWorldObjectUpdate(connection, (NetworkProtocol.WorldObjectUpdate) message);
            }
            if (message instanceof NetworkProtocol.PlayerAction) {
                handlePlayerAction(connection, (NetworkProtocol.PlayerAction) message);
            }
        } catch (Exception e) {
            GameLogger.error("Error processing authenticated message: " + e.getMessage());
        }
    }

    private void handlePlayerAction(Connection connection, NetworkProtocol.PlayerAction action) {
        String username = connectedPlayers.get(connection.getID());
        if (username == null || !username.equals(action.playerId)) {
            GameLogger.error("Unauthorized player action attempt by " + action.playerId);
            return;
        }

        switch (action.actionType) {
            case CHOP_START:
                handleChopStart(action);
                networkServer.sendToAllExceptTCP(connection.getID(), action);
                break;
            case CHOP_STOP:
                handleChopProgress(action);
                networkServer.sendToAllExceptTCP(connection.getID(), action);
                break;

            // *** NEW: PUNCH START / STOP ***
            case PUNCH_START:
                // No server "tree removal" logic for punch, just broadcast
                networkServer.sendToAllExceptTCP(connection.getID(), action);
                GameLogger.info("Player " + action.playerId + " started punching");
                break;
            case PUNCH_STOP:
                networkServer.sendToAllExceptTCP(connection.getID(), action);
                GameLogger.info("Player " + action.playerId + " stopped punching");
                break;
        }
    }

    private void handleChopStart(NetworkProtocol.PlayerAction action) {
        if (action == null || action.playerId == null || action.targetPosition == null) {
            GameLogger.error("Invalid chop start action received");
            return;
        }

        try {
            // Validate player exists
            ServerPlayer player = playerManager.getPlayer(action.playerId);
            if (player == null) {
                GameLogger.error("Player not found for chop action: " + action.playerId);
                return;
            }

            // Calculate chunk position from target coordinates
            Vector2 chunkPos = new Vector2(
                (int) Math.floor(action.targetPosition.x / (World.CHUNK_SIZE * World.TILE_SIZE)),
                (int) Math.floor(action.targetPosition.y / (World.CHUNK_SIZE * World.TILE_SIZE))
            );

            // Get chunk's objects
            List<WorldObject> chunkObjects = worldData.getChunkObjects().get(chunkPos);
            if (chunkObjects == null) {
                GameLogger.error("No objects found in chunk: " + chunkPos);
                return;
            }

            // Find the targeted tree
            WorldObject targetTree = null;
            float minDistance = Float.MAX_VALUE;

            for (WorldObject obj : chunkObjects) {
                if (isChoppable(obj.getType())) {
                    float dist = Vector2.dst(
                        obj.getPixelX(),
                        obj.getPixelY(),
                        action.targetPosition.x,
                        action.targetPosition.y
                    );

                    if (dist < minDistance) {
                        minDistance = dist;
                        targetTree = obj;
                    }
                }
            }

            if (targetTree != null) {
                // Store the chopping state
                player.setChoppingObject(targetTree);

                // Broadcast the chop start action to other players
                NetworkProtocol.PlayerAction broadcastAction = new NetworkProtocol.PlayerAction();
                broadcastAction.playerId = action.playerId;
                broadcastAction.actionType = NetworkProtocol.ActionType.CHOP_START;
                broadcastAction.targetPosition = new Vector2(targetTree.getPixelX(), targetTree.getPixelY());

                networkServer.sendToAllExceptTCP(
                    activeUserConnections.get(action.playerId),
                    broadcastAction
                );

                GameLogger.info("Player " + action.playerId + " started chopping tree at " +
                    targetTree.getPixelX() + "," + targetTree.getPixelY());
            }
        } catch (Exception e) {
            GameLogger.error("Error handling chop start: " + e.getMessage());
        }
    }

    // Handle completion of tree chopping
    private void handleChopProgress(NetworkProtocol.PlayerAction action) {
        if (action == null || action.playerId == null) {
            return;
        }

        try {
            ServerPlayer player = playerManager.getPlayer(action.playerId);
            if (player == null) return;

            WorldObject choppedTree = player.getChoppingObject();
            if (choppedTree == null) return;

            // Clear the chopping state
            player.setChoppingObject(null);

            // Calculate chunk position
            Vector2 chunkPos = new Vector2(
                (int) Math.floor(choppedTree.getPixelX() / (World.CHUNK_SIZE * World.TILE_SIZE)),
                (int) Math.floor(choppedTree.getPixelY() / (World.CHUNK_SIZE * World.TILE_SIZE))
            );

            // Remove the tree
            List<WorldObject> chunkObjects = worldData.getChunkObjects().get(chunkPos);
            if (chunkObjects != null) {
                chunkObjects.remove(choppedTree);
                worldData.getChunkObjects().put(chunkPos, chunkObjects);

                // Create and broadcast tree removal update
                NetworkProtocol.WorldObjectUpdate update = new NetworkProtocol.WorldObjectUpdate();
                update.objectId = choppedTree.getId();
                update.type = NetworkProtocol.NetworkObjectUpdateType.REMOVE;

                // Broadcast to all players except the chopper
                networkServer.sendToAllExceptTCP(
                    activeUserConnections.get(action.playerId),
                    update
                );

                GameLogger.info("Player " + action.playerId + " finished chopping tree at " +
                    choppedTree.getPixelX() + "," + choppedTree.getPixelY());
            }
        } catch (Exception e) {
            GameLogger.error("Error handling chop progress: " + e.getMessage());
        }
    }

    private void handlePokeballSpawning(Vector2 chunkPos, Chunk chunk) {
        try {
            List<WorldObject> chunkObjects = worldData.getChunkObjects().computeIfAbsent(chunkPos, k -> new ArrayList<>());

            // Count existing pokeballs
            long pokeballCount = chunkObjects.stream()
                .filter(obj -> obj.getType() == WorldObject.ObjectType.POKEBALL)
                .count();
            Random random = new Random();
            // Check if we can spawn a new pokeball
            if (pokeballCount < MAX_POKEBALLS_PER_CHUNK && random.nextFloat() < POKEBALL_SPAWN_CHANCE) {
                // Find valid spawn location
                int attempts = 10;
                while (attempts > 0) {
                    int localX = random.nextInt(Chunk.CHUNK_SIZE);
                    int localY = random.nextInt(Chunk.CHUNK_SIZE);

                    int worldTileX = (int) (chunkPos.x * Chunk.CHUNK_SIZE + localX);
                    int worldTileY = (int) (chunkPos.y * Chunk.CHUNK_SIZE + localY);

                    // Check if location is valid (e.g., on grass or walkable terrain)
                    if (chunk.isPassable(localX, localY)) {
                        // Create new pokeball object
                        WorldObject pokeball = new WorldObject(
                            worldTileX,
                            worldTileY,
                            null, // Server doesn't need texture
                            WorldObject.ObjectType.POKEBALL
                        );

                        // Add to chunk objects
                        chunkObjects.add(pokeball);

                        // Create spawn update
                        NetworkProtocol.WorldObjectUpdate update = new NetworkProtocol.WorldObjectUpdate();
                        update.objectId = pokeball.getId();
                        update.type = NetworkProtocol.NetworkObjectUpdateType.ADD;
                        update.data = pokeball.getSerializableData();

                        // Broadcast to all players
                        networkServer.sendToAllTCP(update);

                        GameLogger.info("Spawned pokeball at " + worldTileX + "," + worldTileY);
                        break;
                    }
                    attempts--;
                }
            }
        } catch (Exception e) {
            GameLogger.error("Error handling pokeball spawn: " + e.getMessage());
        }
    }

    private boolean isChoppable(WorldObject.ObjectType type) {
        return type == WorldObject.ObjectType.TREE_0 ||
            type == WorldObject.ObjectType.TREE_1 ||
            type == WorldObject.ObjectType.SNOW_TREE ||
            type == WorldObject.ObjectType.HAUNTED_TREE ||
            type == WorldObject.ObjectType.RAIN_TREE ||
            type == WorldObject.ObjectType.APRICORN_TREE ||
            type == WorldObject.ObjectType.RUINS_TREE;
    }

    private float getObjectDensityMultiplier(BiomeType biomeType) {
        switch (biomeType) {
            case FOREST:
                return 1.5f;
            case DESERT:
                return 0.3f;
            case SNOW:
                return 0.8f;
            case HAUNTED:
                return 1.2f;
            default:
                return 1.0f;
        }
    }

    private void handleBlockPlacement(Connection connection, NetworkProtocol.BlockPlacement placement) {
        String username = connectedPlayers.get(connection.getID());
        if (username == null || !username.equals(placement.username)) {
            GameLogger.error("Unauthorized block placement attempt by " + placement.username);
            return;
        }

        switch (placement.action) {
            case PLACE:
                // Place the block in the server's world
                boolean placed = blockManager.placeBlock(PlaceableBlock.BlockType.fromItemId(placement.blockTypeId), placement.tileX, placement.tileY);
                if (placed) {
                    // Broadcast to other clients
                    networkServer.sendToAllExceptTCP(connection.getID(), placement);
                } else {
                    GameLogger.error("Failed to place block at (" + placement.tileX + ", " + placement.tileY + ")");
                }
                break;
            case REMOVE:
                // Remove the block from the server's world
                blockManager.removeBlock(placement.tileX, placement.tileY);
                // Broadcast to other clients
                networkServer.sendToAllExceptTCP(connection.getID(), placement);
                break;
        }
    }

    private Chunk generateNewChunk(int chunkX, int chunkY) {
        try {
            // Calculate world coordinates
            float worldX = chunkX * World.CHUNK_SIZE * World.TILE_SIZE;
            float worldY = chunkY * World.CHUNK_SIZE * World.TILE_SIZE;

            // Get biome for chunk
            BiomeTransitionResult biomeTransition = biomeManager.getBiomeAt(worldX, worldY);
            if (biomeTransition == null || biomeTransition.getPrimaryBiome() == null) {
                GameLogger.error("Invalid biome transition at: " + worldX + "," + worldY);
                return null;
            }

            Biome biome = biomeTransition.getPrimaryBiome();
            GameLogger.info("Generating chunk at (" + chunkX + "," + chunkY +
                ") with biome: " + biome.getType());

            Chunk chunk = new Chunk(chunkX, chunkY, biome, worldData.getConfig().getSeed(), biomeManager);

            int[][] tileData = new int[World.CHUNK_SIZE][World.CHUNK_SIZE];
            Random random = new Random(worldData.getConfig().getSeed() + (chunkX * 31L + chunkY * 17L));

            Map<Integer, Integer> distribution = biome.getTileDistribution();

            for (int x = 0; x < World.CHUNK_SIZE; x++) {
                for (int y = 0; y < World.CHUNK_SIZE; y++) {
                    int tileType = selectTileType(distribution, random);
                    tileData[x][y] = tileType;
                }
            }

            chunk.setTileData(tileData);
            try {
                List<io.github.pokemeetup.system.gameplay.overworld.WorldObject> objects = generateChunkObjects(chunk, new Vector2(chunkX, chunkY));
                if (!objects.isEmpty()) {
                    Vector2 chunkPos = new Vector2(chunkX, chunkY);
                    worldData.addChunkObjects(chunkPos, objects);
                }
            } catch (Exception e) {
                GameLogger.error("Error generating chunk objects: " + e.getMessage());
            }

            return chunk;

        } catch (Exception e) {
            GameLogger.error("Error generating chunk: " + e.getMessage());
            return null;
        }
    }

    private List<io.github.pokemeetup.system.gameplay.overworld.WorldObject> generateChunkObjects(Chunk chunk, Vector2 chunkPos) {
        List<io.github.pokemeetup.system.gameplay.overworld.WorldObject> objects = new ArrayList<>();
        Random random = new Random(worldData.getConfig().getSeed() + (chunk.getChunkX() * 31L + chunk.getChunkY() * 17L));
        float baseObjectDensity = worldData.getConfig().getTreeSpawnRate();

        float biomeMultiplier = getObjectDensityMultiplier(chunk.getBiome().getType());
        float density = baseObjectDensity * biomeMultiplier;
        List<ObjectType> possibleTypes = new ArrayList<>();
        switch (chunk.getBiome().getType()) {
            case FOREST:
                possibleTypes.add(ObjectType.TREE);
                possibleTypes.add(ObjectType.BUSH);
                break;
            case DESERT:
                possibleTypes.add(ObjectType.CACTUS);
                possibleTypes.add(ObjectType.DEAD_TREE);
                break;
            case SNOW:
                possibleTypes.add(ObjectType.SNOW_TREE);
                break;
            case HAUNTED:
                possibleTypes.add(ObjectType.HAUNTED_TREE);
                possibleTypes.add(ObjectType.SMALL_HAUNTED_TREE);
                break;
            default:
                possibleTypes.add(ObjectType.TREE);
                possibleTypes.add(ObjectType.BUSH);
        }

        for (int x = 0; x < World.CHUNK_SIZE; x++) {
            for (int y = 0; y < World.CHUNK_SIZE; y++) {
                if (random.nextFloat() < density) {
                    float worldX = (chunkPos.x * World.CHUNK_SIZE + x) * World.TILE_SIZE;
                    float worldY = (chunkPos.y * World.CHUNK_SIZE + y) * World.TILE_SIZE;

                    ObjectType type = possibleTypes.get(random.nextInt(possibleTypes.size()));
                    io.github.pokemeetup.system.gameplay.overworld.WorldObject obj = new io.github.pokemeetup.system.gameplay.overworld.WorldObject(
                        (int) worldY / World.TILE_SIZE,
                        (int) worldX / World.TILE_SIZE,
                        null,  // No texture on server
                        io.github.pokemeetup.system.gameplay.overworld.WorldObject.ObjectType.valueOf(type.name())
                    );

                    objects.add(obj);
                }
            }
        }

        return objects;
    }

    private int selectTileType(Map<Integer, Integer> distribution, Random random) {
        int roll = random.nextInt(100);
        int total = 0;

        for (Map.Entry<Integer, Integer> entry : distribution.entrySet()) {
            total += entry.getValue();
            if (roll < total) {
                return entry.getKey();
            }
        }

        return distribution.keySet().iterator().next();
    }

    public ServerConnectionConfig getConfig() {
        return config;
    }


    public enum ObjectType {
        TREE,
        TREE_1,
        TREE_0,
        RUINS_TREE,
        APRICORN_TREE,
        BUSH,
        CACTUS,
        SNOW_TREE,
        HAUNTED_TREE,
        DEAD_TREE,
        RAIN_TREE,
        SMALL_HAUNTED_TREE,
        POKEBALL,
        VINES;
    }

    private static class ConnectionInfo {
        final int connectionId;
        final long connectionTime;
        volatile boolean isAuthenticated;

        ConnectionInfo(int connectionId) {
            this.connectionId = connectionId;
            this.connectionTime = System.currentTimeMillis();
            this.isAuthenticated = false;
        }
    }

}
