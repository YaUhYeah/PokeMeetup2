package org.discord;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryonet.FrameworkMessage;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.managers.BiomeManager;
import io.github.pokemeetup.managers.BiomeTransitionResult;
import io.github.pokemeetup.managers.DatabaseManager;
import io.github.pokemeetup.multiplayer.PlayerManager;
import io.github.pokemeetup.multiplayer.ServerPlayer;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.system.data.*;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.PasswordUtils;
import io.github.pokemeetup.utils.textures.TextureManager;
import org.discord.context.ServerGameContext;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import static io.github.pokemeetup.CreatureCaptureGame.MULTIPLAYER_WORLD_NAME;
import static io.github.pokemeetup.system.gameplay.overworld.WorldObject.WorldObjectManager.MAX_POKEBALLS_PER_CHUNK;
import static io.github.pokemeetup.system.gameplay.overworld.WorldObject.WorldObjectManager.POKEBALL_SPAWN_CHANCE;

public class GameServer {

    private static final int WRITE_BUFFER = 32768; // 32KB
    private static final int OBJECT_BUFFER = 32768;
    private static final int SCHEDULER_POOL_SIZE = 3;
    private static final long AUTH_TIMEOUT = 10000;
    private static final long SAVE_INTERVAL = 300000;
    private final Map<String, Map<Vector2, Set<Integer>>> receivedFragmentAcks = new ConcurrentHashMap<>();
    private final ServerWorldObjectManager worldObjectManager;
    private final Server networkServer;
    private final ServerConnectionConfig config;
    private final BiomeManager biomeManager;
    private final DatabaseManager databaseManager;
    private final ConcurrentHashMap<Integer, String> connectedPlayers;
    private final PlayerManager playerManager;
    private final ScheduledExecutorService scheduler;
    private final Map<String, Integer> activeUserConnections = new ConcurrentHashMap<>();
    private final Map<String, ServerPlayer> activePlayers = new ConcurrentHashMap<>();
    private final Map<String, ConnectionInfo> activeConnections = new ConcurrentHashMap<>();
    private final Map<String, Long> recentDisconnects = new ConcurrentHashMap<>();
    private final WorldData worldData;
    private final PluginManager pluginManager;
    private final ServerBlockManager blockManager;
    private volatile boolean running;


    public GameServer(ServerConnectionConfig config) {
        this.scheduler = Executors.newScheduledThreadPool(SCHEDULER_POOL_SIZE, r -> {
            Thread thread = new Thread(r, "GameServer-Scheduler");
            thread.setDaemon(true);
            return thread;
        });
        ItemManager.setServerMode(true);
        ItemManager.initialize(null);
        this.config = config;
        this.networkServer = new Server(WRITE_BUFFER, OBJECT_BUFFER);
        NetworkProtocol.registerClasses(networkServer.getKryo());
        scheduler.scheduleAtFixedRate(() -> {
            try {
                ServerGameContext.get().getStorageSystem().getPlayerDataManager().flush();
            } catch (Exception e) {
                GameLogger.error("Scheduled player data flush failed: " + e.getMessage());
            }
        }, 300000, 300000, TimeUnit.MILLISECONDS);

        networkServer.getKryo().setReferences(false);

        this.worldObjectManager = new ServerWorldObjectManager();

        this.databaseManager = new DatabaseManager();
        this.connectedPlayers = new ConcurrentHashMap<>();
        this.playerManager = new PlayerManager(ServerGameContext.get().getStorageSystem());


        try {
            this.worldData = initializeMultiplayerWorld();
            this.blockManager = new ServerBlockManager();
            setupNetworkListener();
            this.pluginManager = new PluginManager(worldData);
            this.biomeManager = new BiomeManager(worldData.getConfig().getSeed());
        } catch (Exception e) {
            GameLogger.error("Failed to initialize game world: " + e.getMessage());
            throw new RuntimeException("Failed to initialize server world", e);
        }
    }

    private WorldData initializeMultiplayerWorld() {
        try {
            WorldData worldData = ServerGameContext.get().getWorldManager().loadWorld(MULTIPLAYER_WORLD_NAME);
            if (worldData == null) {
                // Create new WorldData
                worldData = ServerGameContext.get().getWorldManager().createWorld(
                    MULTIPLAYER_WORLD_NAME,
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


    private void handleDisconnect(Connection connection) {
        String username = connectedPlayers.get(connection.getID());
        if (username != null) {
            GameLogger.info("Handling disconnect for user: " + username);

            synchronized (activeConnections) {
                try {


                    // Existing cleanup code...
                    recentDisconnects.put(username, System.currentTimeMillis());
                    activeConnections.remove(username);
                    cleanupPlayerSession(connection.getID(), username);

                    // Broadcast disconnect
                    NetworkProtocol.PlayerLeft leftMessage = new NetworkProtocol.PlayerLeft();
                    leftMessage.username = username;
                    leftMessage.timestamp = System.currentTimeMillis();
                    networkServer.sendToAllTCP(leftMessage);

                } catch (Exception e) {
                    GameLogger.error("Error during disconnect handling: " + e.getMessage());
                }
            }
        } else {
            GameLogger.info("username null during disconnect?");
        }
    }

    public void shutdown() {
        try {
            GameLogger.info("Starting server shutdown sequence...");

            NetworkProtocol.ServerShutdown shutdownMsg = new NetworkProtocol.ServerShutdown();
            shutdownMsg.reason = "Server is shutting down";
            networkServer.sendToAllTCP(shutdownMsg);


            // Save world state if exists
            if (worldData != null) {
                try {
                    GameLogger.info("Saving world data during shutdown...");
                    worldData.setLastPlayed(System.currentTimeMillis());
                    ServerGameContext.get().getWorldManager().saveWorld(worldData);
                } catch (Exception e) {
                    GameLogger.error("Error saving world data during shutdown: " + e.getMessage());
                }
            }

            Thread.sleep(500);

            running = false;
            if (ServerGameContext.get().getWorldManager() != null) {
                ServerGameContext.get().getWorldManager().shutdown();
            }
            networkServer.stop();
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

            GameLogger.info("Server shutdown completed successfully");
        } catch (Exception e) {
            GameLogger.error("Error during server shutdown: " + e.getMessage());
            if (networkServer != null) {
                networkServer.stop();
            }
        }
    }

    private void cleanupPlayerSession(int connectionId, String username) {
        synchronized (activeUserConnections) {
            activeUserConnections.remove(username);
            connectedPlayers.remove(connectionId);

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

        response.playerData = player.getData();
        connection.sendTCP(response);
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


    private void handlePlayerUpdate(Connection connection, NetworkProtocol.PlayerUpdate update) {
        try {
            String username = connectedPlayers.get(connection.getID());
            if (username == null || !username.equals(update.username)) {
                GameLogger.error("Username mismatch in player update");
                return;
            }

            PlayerData playerData = ServerGameContext.get().getStorageSystem().getPlayerDataManager().loadPlayerData(UUID.nameUUIDFromBytes(update.username.getBytes()));
            if (playerData == null) {
                GameLogger.error("No player data found for active player: " + username);
                return;
            }

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

            // Save to storage
            UUID playerUUID = UUID.nameUUIDFromBytes(username.getBytes());
            ServerGameContext.get().getStorageSystem().getPlayerDataManager().savePlayerData(playerUUID, playerData);

            // Broadcast update
            networkServer.sendToAllTCP(update);

        } catch (Exception e) {
            GameLogger.error("Error handling player update: " + e.getMessage());
        }
    }

    private void handlePokemonSpawn(Connection connection, NetworkProtocol.WildPokemonSpawn spawnRequest) {
        try {
            WorldData world = ServerGameContext.get().getWorldManager().loadWorld(MULTIPLAYER_WORLD_NAME);
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

        WorldData world = ServerGameContext.get().getWorldManager().loadWorld(MULTIPLAYER_WORLD_NAME);
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
            WildPokemon pokemon = new WildPokemon(
                spawnRequest.data.getName(),
                spawnRequest.data.getLevel(),
                (int) spawnRequest.x,
                (int) spawnRequest.y,
                TextureManager.getOverworldSprite(spawnRequest.data.getName())
            );
            pokemon.setUuid(spawnRequest.uuid != null ? spawnRequest.uuid : UUID.randomUUID());
            pokemon.setSpawnTime(System.currentTimeMillis() / 1000L);

            return pokemon;
        } catch (Exception e) {
            GameLogger.error("Error creating WildPokemon: " + e.getMessage());
            return null;
        }
    }

    private boolean authenticateUser(String username, String password) {
        String storedHash = databaseManager.getPasswordHash(username);
        if (storedHash == null) {
            GameLogger.error("Authentication failed: Username '" + username + "' does not exist.");
            return false;
        }
        return PasswordUtils.verifyPassword(password, storedHash);
    }

    private void handleLoginRequest(Connection connection, NetworkProtocol.LoginRequest request) {
        try {
            GameLogger.info("Processing login request for: " + request.username);

            // Generate consistent UUID based on username
            UUID playerUUID = UUID.nameUUIDFromBytes(request.username.getBytes());
            GameLogger.info("Generated UUID for player: " + playerUUID);

            // Try to load existing player data first
            PlayerData playerData = ServerGameContext.get().getStorageSystem()
                .getPlayerDataManager().loadPlayerData(playerUUID);

            if (playerData == null) {
                // Only create new data if none exists
                GameLogger.info("Creating new player data for: " + request.username);
                playerData = new PlayerData(request.username);
                playerData.setX(0);
                playerData.setY(0);
                playerData.setDirection("down");
                playerData.setMoving(false);
                playerData.setInventoryItems(new ArrayList<>());
                playerData.setPartyPokemon(new ArrayList<>());

                // Save immediately and verify
                ServerGameContext.get().getStorageSystem()
                    .getPlayerDataManager().savePlayerData(playerUUID, playerData);
                ServerGameContext.get().getStorageSystem()
                    .getPlayerDataManager().flush(); // Force write to disk
            }

            // Validate authentication
            if (!authenticateUser(request.username, request.password)) {
                sendLoginFailure(connection, "Invalid credentials");
                return;
            }

            synchronized (activeConnections) {
                // Handle existing connection
                handleExistingConnection(request.username);

                // Create new connection
                ConnectionInfo newConnection = new ConnectionInfo(connection.getID());
                activeConnections.put(request.username, newConnection);
                ServerPlayer player;
                if (!activeConnections.containsKey(request.username)) {
                    player = new ServerPlayer(request.username, playerData);
                } else {
                    player = new ServerPlayer(request.username, ServerGameContext.get().getStorageSystem().getPlayerDataManager().playerCache.get(UUID.nameUUIDFromBytes(request.username.getBytes())));
                }
                activePlayers.put(request.username, player);
                // Reg ister the player
                connectedPlayers.put(connection.getID(), request.username);
                newConnection.isAuthenticated = true;

                // Send successful response
                sendSuccessfulLoginResponse(connection, player);

                GameLogger.info("Login successful for: " + request.username);
            }

        } catch (Exception e) {
            GameLogger.error("Login error for " + request.username + ": " + e.getMessage());
            e.printStackTrace();
            sendLoginFailure(connection, "Server error occurred");
        }
    }

    private void handleExistingConnection(String username) throws InterruptedException {
        ConnectionInfo existingConnection = activeConnections.get(username);
        if (existingConnection != null) {
            Connection oldConnection = findConnection(existingConnection.connectionId);
            if (oldConnection != null && oldConnection.isConnected()) {
                NetworkProtocol.ForceDisconnect forceDisconnect = new NetworkProtocol.ForceDisconnect();
                forceDisconnect.reason = "Logged in from another location";
                oldConnection.sendTCP(forceDisconnect);
                Thread.sleep(100);
                oldConnection.close();

                cleanupPlayerSession(existingConnection.connectionId, username);
                Thread.sleep(500);
            }
        }
    }

    // -------------------------------------------------------------------
// In GameServer.java (handleChunkRequest method), or a similar place
// -------------------------------------------------------------------
    public void handleChunkRequest(Connection connection, NetworkProtocol.ChunkRequest request) {
        Vector2 chunkPos = new Vector2(request.chunkX, request.chunkY);

        // 1. Load or generate the chunk (ensure it has up-to-date tile data)
        Chunk chunk = loadOrGenerateChunk(MULTIPLAYER_WORLD_NAME, chunkPos);
        if (chunk == null) {
            // Some error handling
            return;
        }

        // 2. Convert chunk data into the wire format
        NetworkProtocol.ChunkData chunkData = new NetworkProtocol.ChunkData();
        chunkData.chunkX = request.chunkX;
        chunkData.chunkY = request.chunkY;
        chunkData.biomeType = chunk.getBiome().getType();
        chunkData.tileData = chunk.getTileData();        // int[][] for the chunk
        chunkData.blockData = chunk.getBlockDataForSave();
        chunkData.timestamp = System.currentTimeMillis();

        // 3. Gather all WorldObjects for this chunk
        List<WorldObject> objects = worldData.getChunkObjects().get(chunkPos);
        if (objects == null) {
            // Possibly generate objects, or default to empty list
            objects = Collections.emptyList();
        }
        // Convert them to a serializable form
        chunkData.worldObjects = new ArrayList<>();
        for (WorldObject obj : objects) {
            Map<String, Object> objData = obj.getSerializableData();
            chunkData.worldObjects.add(objData);
        }

        // 4. (Optional) compress it using Kryo + GZIP, exactly once
        NetworkProtocol.CompressedChunkData compressed = compressChunkData(chunkData);

        // 5. Send to client
        connection.sendTCP(compressed);

        GameLogger.info("Sent FULL chunk data for (" + request.chunkX + "," + request.chunkY + ") to " + connection.getID());
    }

    private NetworkProtocol.CompressedChunkData compressChunkData(NetworkProtocol.ChunkData chunkData) {
        // Similar to your existing code
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            GZIPOutputStream gzip = new GZIPOutputStream(baos);
            Output output = new Output(gzip);

            Kryo kryo = new Kryo();
            NetworkProtocol.registerClasses(kryo);
            kryo.setReferences(false);
            kryo.writeObject(output, chunkData);

            output.close();
            gzip.close();

            NetworkProtocol.CompressedChunkData compressed = new NetworkProtocol.CompressedChunkData();
            compressed.chunkX = chunkData.chunkX;
            compressed.chunkY = chunkData.chunkY;
            compressed.biomeType = chunkData.biomeType;
            compressed.generationSeed = worldData.getConfig().getSeed();
            compressed.data = baos.toByteArray();
            return compressed;
        } catch (IOException e) {
            GameLogger.error("Chunk compression failed: " + e.getMessage());
            return null;
        }
    }

    private void sendChunkResponse(Connection connection, Vector2 chunkPos, NetworkProtocol.ChunkData chunkData) {
        try {
            // Ensure chunk data includes all necessary information
            chunkData.generationSeed = worldData.getConfig().getSeed(); // Add world seed

            // Save tiles in a way that's consistent across platforms
            int[][] normalizedTiles = new int[chunkData.tileData.length][chunkData.tileData[0].length];
            for(int x = 0; x < chunkData.tileData.length; x++) {
                for(int y = 0; y < chunkData.tileData[0].length; y++) {
                    normalizedTiles[x][y] = chunkData.tileData[x][y];
                }
            }
            chunkData.tileData = normalizedTiles;

            // Compress and send
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            GZIPOutputStream gzip = new GZIPOutputStream(baos);
            Output output = new Output(gzip);

            Kryo kryo = new Kryo();
            NetworkProtocol.registerClasses(kryo);

            // Configure Kryo for consistent serialization
            kryo.setReferences(false);
            kryo.setRegistrationRequired(false);

            kryo.writeObject(output, chunkData);

            output.flush();
            output.close();
            gzip.close();

            NetworkProtocol.CompressedChunkData compressedData = new NetworkProtocol.CompressedChunkData();
            compressedData.chunkX = (int)chunkPos.x;
            compressedData.chunkY = (int)chunkPos.y;
            compressedData.data = baos.toByteArray();
            compressedData.biomeType = chunkData.biomeType;
            compressedData.generationSeed = chunkData.generationSeed; // Include seed in compressed data

            connection.sendTCP(compressedData);

        } catch (Exception e) {
            GameLogger.error("Error sending chunk data: " + e.getMessage());
            e.printStackTrace();
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
                    if (object instanceof NetworkProtocol.SavePlayerDataRequest) {
                        NetworkProtocol.SavePlayerDataRequest saveRequest =
                            (NetworkProtocol.SavePlayerDataRequest) object;

                        try {
                            ServerGameContext.get().getStorageSystem()
                                .savePlayerData(saveRequest.playerData.getUsername(), saveRequest.playerData);

                            ServerGameContext.get().getStorageSystem().getPlayerDataManager().flush();

                            GameLogger.info("Saved player data for: " + saveRequest.playerData.getUsername());

                        } catch (Exception e) {
                            GameLogger.error("Failed to save player data: " + e.getMessage());
                        }
                    }

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
                if (worldData != null) {
                    ServerGameContext.get().getWorldManager().saveWorld(worldData);
                    GameLogger.info("World data saved periodically.");
                }
            } catch (Exception e) {
                GameLogger.error("Error during periodic world save: " + e.getMessage());
            }
        }, SAVE_INTERVAL, SAVE_INTERVAL, TimeUnit.MILLISECONDS);
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

            NetworkProtocol.registerClasses(networkServer.getKryo());
            GameLogger.info("Network classes registered");

            networkServer.start();

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

    private Chunk loadOrGenerateChunk(String worldName, Vector2 chunkPos) {
        try {
            // Try loading from server storage first
            Chunk chunk = ServerGameContext.get().getWorldManager().loadChunk(worldName,
                (int) chunkPos.x, (int) chunkPos.y);

            if (chunk != null) {
                return chunk;
            }

            // Generate new chunk if not found
            float worldX = chunkPos.x * World.CHUNK_SIZE * World.TILE_SIZE;
            float worldY = chunkPos.y * World.CHUNK_SIZE * World.TILE_SIZE;

            BiomeTransitionResult biomeResult = biomeManager.getBiomeAt(worldX, worldY);
            Biome biome = biomeResult.getPrimaryBiome();

            chunk = new Chunk((int) chunkPos.x, (int) chunkPos.y, biome,
                worldData.getConfig().getSeed(), biomeManager);

            // Generate tile data
            int[][] tileData = generateTileData(chunk, biome);
            chunk.setTileData(tileData);

            // Save the newly generated chunk
            ServerGameContext.get().getWorldManager().saveChunk(worldName, chunk);

            return chunk;

        } catch (Exception e) {
            GameLogger.error("Error loading/generating chunk: " + e.getMessage());
            return null;
        }
    }

    private int[][] generateTileData(Chunk chunk, Biome biome) {
        int[][] tileData = new int[World.CHUNK_SIZE][World.CHUNK_SIZE];
        Random random = new Random(worldData.getConfig().getSeed() +
            (chunk.getChunkX() * 31L + chunk.getChunkY() * 17L));

        Map<Integer, Integer> distribution = biome.getTileDistribution();

        for (int x = 0; x < World.CHUNK_SIZE; x++) {
            for (int y = 0; y < World.CHUNK_SIZE; y++) {
                int tileType = selectTileType(distribution, random);
                tileData[x][y] = tileType;
            }
        }
        return tileData;
    }

    private List<WorldObject> generateChunkObjects(Chunk chunk, Vector2 chunkPos) {
        List<WorldObject> objects = new ArrayList<>();

        try {
            Random random = new Random(worldData.getConfig().getSeed() +
                (chunk.getChunkX() * 31L + chunk.getChunkY() * 17L));

            float density = worldData.getConfig().getTreeSpawnRate() *
                getObjectDensityMultiplier(chunk.getBiome().getType());

            for (int x = 0; x < World.CHUNK_SIZE; x++) {
                for (int y = 0; y < World.CHUNK_SIZE; y++) {
                    if (random.nextFloat() < density) {
                        float worldX = (chunkPos.x * World.CHUNK_SIZE + x) * World.TILE_SIZE;
                        float worldY = (chunkPos.y * World.CHUNK_SIZE + y) * World.TILE_SIZE;

                        WorldObject.ObjectType type = getAppropriateObjectType(
                            chunk.getBiome().getType(), random);

                        if (type != null) {
                            // Create object without texture - it will be loaded on demand
                            WorldObject obj = new WorldObject(
                                (int) (worldX / World.TILE_SIZE),
                                (int) (worldY / World.TILE_SIZE),
                                null,  // Don't set texture here
                                type
                            );

                            // Ensure the object has an ID for networking
                            obj.setId(UUID.randomUUID().toString());

                            // Add to object list
                            objects.add(obj);
                        }
                    }
                }
            }
        } catch (Exception e) {
            GameLogger.error("Error generating chunk objects: " + e.getMessage());
        }

        return objects;
    }

    private WorldObject.ObjectType getAppropriateObjectType(BiomeType biomeType, Random random) {
        List<WorldObject.ObjectType> possibleTypes = new ArrayList<>();

        switch (biomeType) {
            case FOREST:
                possibleTypes.add(WorldObject.ObjectType.TREE_0);
                possibleTypes.add(WorldObject.ObjectType.TREE_1);
                break;
            case DESERT:
                possibleTypes.add(WorldObject.ObjectType.CACTUS);
                break;
            case SNOW:
                possibleTypes.add(WorldObject.ObjectType.SNOW_TREE);
                break;
            case HAUNTED:
                possibleTypes.add(WorldObject.ObjectType.HAUNTED_TREE);
                break;
            case RAIN_FOREST:
                possibleTypes.add(WorldObject.ObjectType.RAIN_TREE);
                break;
            case RUINS:
                possibleTypes.add(WorldObject.ObjectType.RUINS_TREE);
                break;
            default:
                possibleTypes.add(WorldObject.ObjectType.TREE_0);
        }

        return possibleTypes.get(random.nextInt(possibleTypes.size()));
    }

    private void handleWorldObjectUpdate(Connection connection, NetworkProtocol.WorldObjectUpdate update) {
        String username = connectedPlayers.get(connection.getID());
        if (username == null || update.data == null) {
            GameLogger.error("Invalid world object update");
            return;
        }

        Vector2 chunkPos = getChunkPosFromWorldCoords(
            convertToFloat(update.data.get("tileX")) * World.TILE_SIZE,
            convertToFloat(update.data.get("tileY")) * World.TILE_SIZE
        );

        try {
            switch (update.type) {
                case ADD:
                    handleObjectAdd(update, chunkPos, connection);
                    break;
                case REMOVE:
                    handleObjectRemove(update, chunkPos, connection);
                    break;
                case UPDATE:
                    handleObjectModify(update, chunkPos, connection);
                    break;
            }
        } catch (Exception e) {
            GameLogger.error("Error processing world object update: " + e.getMessage());
        }
    }

    private float convertToFloat(Object value) {
        if (value instanceof Integer) {
            return ((Integer) value).floatValue();
        } else if (value instanceof Float) {
            return (Float) value;
        } else if (value instanceof Double) {
            return ((Double) value).floatValue();
        }
        return 0f;
    }

    private void handleObjectAdd(NetworkProtocol.WorldObjectUpdate update, Vector2 chunkPos, Connection connection) {
        WorldObject obj = createWorldObjectFromData(update.data);
        if (obj == null) return;

        List<WorldObject> chunkObjects = worldData.getChunkObjects()
            .computeIfAbsent(chunkPos, k -> new ArrayList<>());

        if (isValidObjectPlacement(obj, chunkPos, chunkObjects)) {
            chunkObjects.add(obj);
            networkServer.sendToAllExceptTCP(connection.getID(), update);
            ServerGameContext.get().getWorldManager().saveWorld(worldData);
        }
    }

    private void handleObjectRemove(NetworkProtocol.WorldObjectUpdate update, Vector2 chunkPos, Connection connection) {
        List<WorldObject> objects = worldData.getChunkObjects().get(chunkPos);
        if (objects != null) {
            boolean removed = objects.removeIf(obj -> obj.getId().equals(update.objectId));
            if (removed) {
                networkServer.sendToAllExceptTCP(connection.getID(), update);
                ServerGameContext.get().getWorldManager().saveWorld(worldData);
            }
        }
    }

    private void handleObjectModify(NetworkProtocol.WorldObjectUpdate update, Vector2 chunkPos, Connection connection) {
        List<WorldObject> objects = worldData.getChunkObjects().get(chunkPos);
        if (objects != null) {
            for (WorldObject obj : objects) {
                if (obj.getId().equals(update.objectId)) {
                    obj.updateFromData(update.data);
                    networkServer.sendToAllExceptTCP(connection.getID(), update);
                    ServerGameContext.get().getWorldManager().saveWorld(worldData);
                    break;
                }
            }
        }
    }

    private WorldObject createWorldObjectFromData(Map<String, Object> data) {
        try {
            WorldObject obj = new WorldObject();
            obj.updateFromData(data);
            return obj;
        } catch (Exception e) {
            GameLogger.error("Failed to create world object: " + e.getMessage());
            return null;
        }
    }

    private Vector2 getChunkPosFromWorldCoords(float pixelX, float pixelY) {
        return new Vector2(
            (int) Math.floor(pixelX / (World.CHUNK_SIZE * World.TILE_SIZE)),
            (int) Math.floor(pixelY / (World.CHUNK_SIZE * World.TILE_SIZE))
        );
    }

    private boolean isValidObjectPlacement(WorldObject obj, Vector2 chunkPos, List<WorldObject> existingObjects) {
        if (obj.getType() == null) return false;

        // Check spacing against existing objects
        Rectangle newBounds = obj.getBoundingBox();
        for (WorldObject existing : existingObjects) {
            if (existing.getBoundingBox().overlaps(newBounds)) {
                return false;
            }
        }

        // Load chunk to check tile validity
        Chunk chunk = ServerGameContext.get().getWorldManager()
            .loadChunk(MULTIPLAYER_WORLD_NAME,
                (int) chunkPos.x, (int) chunkPos.y);

        if (chunk == null) return false;

        // Convert to local chunk coordinates
        int localX = Math.floorMod(obj.getTileX(), World.CHUNK_SIZE);
        int localY = Math.floorMod(obj.getTileY(), World.CHUNK_SIZE);

        return chunk.isPassable(localX, localY);
    }

    private void processAuthenticatedMessage(Connection connection, Object message) {
        String username = connectedPlayers.get(connection.getID());
        if (username == null) {
            GameLogger.error("Received message from non-authenticated connection: " + connection.getID());
            return;
        }

        ServerPlayer player = activePlayers.get(username);
        if (player == null) {
            GameLogger.error("No player instance found for authenticated user: " + username);
            // Attempt to recover
            PlayerData savedData = ServerGameContext.get().getStorageSystem().getPlayerDataManager().loadPlayerData(UUID.nameUUIDFromBytes(username.getBytes()));
            if (savedData != null) {
                player = new ServerPlayer(username, savedData);
                activePlayers.put(username, player);
                GameLogger.info("Recovered player instance for: " + username);
            } else {
                connection.close(); // Force disconnect if unrecoverable
                return;
            }
        }

        try {
            if (message instanceof NetworkProtocol.PlayerUpdate) {
                handlePlayerUpdate(connection, (NetworkProtocol.PlayerUpdate) message);
            } else if (message instanceof NetworkProtocol.ChatMessage) {
                handleChatMessage(connection, (NetworkProtocol.ChatMessage) message);
            } else if (message instanceof NetworkProtocol.WorldObjectUpdate) {
                handleWorldObjectUpdate(connection, (NetworkProtocol.WorldObjectUpdate) message);
            } else if (message instanceof NetworkProtocol.PlayerAction) {
                handlePlayerAction(connection, (NetworkProtocol.PlayerAction) message);
            }
        } catch (Exception e) {
            GameLogger.error("Error processing message for " + username + ": " + e.getMessage());
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
                case PICKUP_ITEM:
                handleItemPickup(action, connection);
                break;

        }
    }
    private void handleItemPickup(NetworkProtocol.PlayerAction action, Connection connection) {
        String username = connectedPlayers.get(connection.getID());
        if (username == null) {
            GameLogger.error("Unauthorized pickup attempt");
            return;
        }

        // Suppose action.targetPosition is the tile/pixel coords of the item
        Vector2 itemPos = action.targetPosition;
        if (itemPos == null) {
            GameLogger.error("No item position specified");
            return;
        }

        // Convert to chunk coords
        Vector2 chunkPos = new Vector2(
            (int)Math.floor(itemPos.x / (World.CHUNK_SIZE * World.TILE_SIZE)),
            (int)Math.floor(itemPos.y / (World.CHUNK_SIZE * World.TILE_SIZE))
        );

        // Look up chunk’s object list
        List<WorldObject> objects = worldData.getChunkObjects().get(chunkPos);
        if (objects == null) return;

        // Find the nearest item object
        WorldObject itemObject = null;
        float minDist = Float.MAX_VALUE;
        for (WorldObject obj : objects) {
            if (obj.getType() == WorldObject.ObjectType.POKEBALL /* or ITEM, ETC. */) {
                float dist = Vector2.dst(obj.getPixelX(), obj.getPixelY(), itemPos.x, itemPos.y);
                if (dist < minDist && dist < 48) {
                    minDist = dist;
                    itemObject = obj;
                }
            }
        }
        if (itemObject == null) {
            GameLogger.info("No item found at pickup location");
            return;
        }

        // Remove from chunk
        if (objects.remove(itemObject)) {
            worldData.getChunkObjects().put(chunkPos, objects);

            // Broadcast removal
            NetworkProtocol.WorldObjectUpdate update = new NetworkProtocol.WorldObjectUpdate();
            update.objectId = itemObject.getId();
            update.type = NetworkProtocol.NetworkObjectUpdateType.REMOVE;
            networkServer.sendToAllTCP(update);

            // Example: add to player’s inventory
            // e.g. "wooden_plank" or "pokeball" item
            // This will vary by your inventory logic
            PlayerData playerData = /* load or get from your manager */
                ServerGameContext.get().getStorageSystem().getPlayerDataManager()
                    .loadPlayerData(UUID.nameUUIDFromBytes(username.getBytes()));
            if (playerData != null) {
                // e.g. add 1 "pokeball" to inventory
                playerData.getInventoryItems().add(new ItemData("pokeball", 1, UUID.randomUUID()));
                // Then save
                ServerGameContext.get().getStorageSystem()
                    .getPlayerDataManager().savePlayerData(UUID.nameUUIDFromBytes(username.getBytes()), playerData);
            }

            // Save chunk or entire world
            ServerGameContext.get().getWorldManager().saveWorld(worldData);
            GameLogger.info(username + " picked up item " + itemObject.getId()
                + " at " + itemObject.getPixelX() + "," + itemObject.getPixelY());
        }
    }

    private void handleChopStart(NetworkProtocol.PlayerAction action) {
        if (action == null || action.playerId == null || action.targetPosition == null) {
            GameLogger.error("Invalid chop start action received");
            return;
        }

        try {
            ServerPlayer player = new ServerPlayer(ServerGameContext.get().getStorageSystem().getPlayerDataManager().loadPlayerData(UUID.nameUUIDFromBytes(action.playerId.getBytes())).getUsername(), (ServerGameContext.get().getStorageSystem().getPlayerDataManager().loadPlayerData(UUID.fromString(action.playerId))));

            Vector2 chunkPos = new Vector2(
                (int) Math.floor(action.targetPosition.x / (World.CHUNK_SIZE * World.TILE_SIZE)),
                (int) Math.floor(action.targetPosition.y / (World.CHUNK_SIZE * World.TILE_SIZE))
            );

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
    private void handleChopProgress(NetworkProtocol.PlayerAction action) {
        if (action == null || action.playerId == null) {
            return;
        }

        try {
            // Look up the ServerPlayer by username
            ServerPlayer player = new ServerPlayer(action.playerId,ServerGameContext.get().getStorageSystem().getPlayerDataManager().loadPlayerData(UUID.nameUUIDFromBytes(action.playerId.getBytes())));
            if (player == null) return;

            // Grab the tree the player was chopping
            WorldObject choppedTree = player.getChoppingObject();
            if (choppedTree == null) return;  // Player wasn't actually chopping anything?

            // Once we finish, clear the chopping state
            player.setChoppingObject(null);

            // Convert the chopped object’s pixel coords to chunk coords
            Vector2 chunkPos = new Vector2(
                (int) Math.floor(choppedTree.getPixelX() / (World.CHUNK_SIZE * World.TILE_SIZE)),
                (int) Math.floor(choppedTree.getPixelY() / (World.CHUNK_SIZE * World.TILE_SIZE))
            );

            // Remove from the chunk’s list
            List<WorldObject> chunkObjects = worldData.getChunkObjects().get(chunkPos);
            if (chunkObjects != null) {
                boolean removed = chunkObjects.remove(choppedTree);
                if (removed) {
                    // Update the chunk objects map
                    worldData.getChunkObjects().put(chunkPos, chunkObjects);

                    // Create a removal update
                    NetworkProtocol.WorldObjectUpdate update = new NetworkProtocol.WorldObjectUpdate();
                    update.objectId = choppedTree.getId();
                    update.type = NetworkProtocol.NetworkObjectUpdateType.REMOVE;
                    // (optionally) update.data = choppedTree.getSerializableData();

                    // Broadcast to all clients that the object is removed
                    networkServer.sendToAllTCP(update);

                    // Example: give the player some items for chopping the tree
                    // (e.g. wood planks or logs). This part is purely game logic:
                    // player.getData().inventoryItems.add("wood_plank"); // or however you add items

                    // Save updated world chunk or entire world
                    ServerGameContext.get().getWorldManager().saveWorld(worldData);

                    GameLogger.info("Player " + action.playerId + " finished chopping tree ("
                        + choppedTree.getPixelX() + "," + choppedTree.getPixelY() + ")");
                }
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
