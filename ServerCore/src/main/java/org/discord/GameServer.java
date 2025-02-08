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
import io.github.pokemeetup.system.gameplay.inventory.ItemEntity;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.PasswordUtils;
import io.github.pokemeetup.utils.textures.TextureManager;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import org.discord.context.ServerGameContext;
import org.discord.utils.BiomeData;
import org.discord.utils.ServerBiomeManager;
import org.discord.utils.ServerPokemonSpawnManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import static io.github.pokemeetup.CreatureCaptureGame.MULTIPLAYER_WORLD_NAME;
import static io.github.pokemeetup.system.gameplay.overworld.World.CHUNK_SIZE;
import static io.github.pokemeetup.system.gameplay.overworld.World.TILE_SIZE;
import static io.github.pokemeetup.system.gameplay.overworld.WorldObject.WorldObjectManager.MAX_POKEBALLS_PER_CHUNK;
import static io.github.pokemeetup.system.gameplay.overworld.WorldObject.WorldObjectManager.POKEBALL_SPAWN_CHANCE;

public class GameServer {
    private static final int WRITE_BUFFER = 65536;
    private static final int OBJECT_BUFFER = 65536;
    private static final int SCHEDULER_POOL_SIZE = 3;
    private static final long AUTH_TIMEOUT = 15000;
    private static final long SAVE_INTERVAL = 300000;
    private static final ConcurrentHashMap<UUID, Object> chestLocks = new ConcurrentHashMap<>();
    private final Server networkServer;
    private final ServerConnectionConfig config;
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
    private final ConcurrentHashMap<String, Integer> playerPingMap = new ConcurrentHashMap<>();
    private final Map<String, Vector2> playerChunkMap = new ConcurrentHashMap<>();
    private volatile boolean running;
    private ServerPokemonSpawnManager serverPokemonSpawnManager;

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


        this.databaseManager = new DatabaseManager();
        this.connectedPlayers = new ConcurrentHashMap<>();
        this.playerManager = new PlayerManager(ServerGameContext.get().getStorageSystem());


        try {
            this.worldData = initializeMultiplayerWorld();
            serverPokemonSpawnManager = new ServerPokemonSpawnManager(MULTIPLAYER_WORLD_NAME);
            setupNetworkListener();
            scheduler.scheduleAtFixedRate(() -> {
                // You can use a delta of 0.1f or any suitable value here.
                // First update wild Pokémon state, then broadcast updates.
                serverPokemonSpawnManager.update(0.1f);
                serverPokemonSpawnManager.broadcastPokemonUpdates();
            }, 0, 100, TimeUnit.MILLISECONDS);
            this.pluginManager = new PluginManager(worldData);
        } catch (Exception e) {
            GameLogger.error("Failed to initialize game world: " + e.getMessage());
            throw new RuntimeException("Failed to initialize server world", e);
        }
    }
    public Set<Vector2> getPlayerOccupiedChunks() {
        Set<Vector2> occupied = new HashSet<>();
        for (Map.Entry<String, Vector2> entry : playerChunkMap.entrySet()) {
            String user = entry.getKey();
            if (!activePlayers.containsKey(user)) {
                continue;
            }
            Vector2 pos = entry.getValue();
            occupied.add(pos);
        }
        return occupied;
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
                    // Existing cleanup code…
                    recentDisconnects.put(username, System.currentTimeMillis());
                    activeConnections.remove(username);
                    cleanupPlayerSession(connection.getID(), username);

                    // **** REMOVE the player's ping entry ****
                    playerPingMap.remove(username);
                    // Immediately broadcast the updated list:
                    broadcastPlayerList();

                    // Notify all clients that this player left:
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

    private void handleItemDrop(Connection connection, NetworkProtocol.ItemDrop drop) {
        String username = connectedPlayers.get(connection.getID());
        if (username == null || !username.equals(drop.username)) {
            GameLogger.error("Unauthorized item drop attempt");
            return;
        }

        // Validate the drop position isn't too far from player
        ServerPlayer player = activePlayers.get(username);
        if (player == null) {
            GameLogger.error("No player found for item drop");
            return;
        }

        float distance = Vector2.dst(
            player.getPosition().x, player.getPosition().y,
            drop.x, drop.y
        );

        if (distance > TILE_SIZE * 2) {
            GameLogger.error("Item drop position too far from player");
            return;
        }

        // Broadcast the drop to all other players
        networkServer.sendToAllExceptTCP(connection.getID(), drop);
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
            ServerPlayer serverPlayer = activePlayers.get(username);
            if (serverPlayer == null) {
                GameLogger.error("No ServerPlayer instance found for: " + username);
                return;
            }

            // 1. Update the player's position & state

            serverPlayer.setPosition(update.x, update.y);
            serverPlayer.setDirection(update.direction);
            serverPlayer.setMoving(update.isMoving);

            // Convert pixel coords => chunk coords
            int cX = (int)Math.floor(update.x / (World.CHUNK_SIZE * World.TILE_SIZE));
            int cY = (int)Math.floor(update.y / (World.CHUNK_SIZE * World.TILE_SIZE));
            Vector2 chunkPos = new Vector2(cX, cY);
            // Store that in the map
            playerChunkMap.put(username, chunkPos);
            // Then update persistent data
            PlayerData playerData = ServerGameContext.get().getStorageSystem()
                .getPlayerDataManager().loadPlayerData(UUID.nameUUIDFromBytes(update.username.getBytes()));

            if (playerData == null) {
                GameLogger.error("No player data found for active player: " + username);
                return;
            }

            // Update player data
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
            ServerGameContext.get().getStorageSystem()
                .getPlayerDataManager().savePlayerData(playerUUID, playerData);

            // Broadcast update to other players
            networkServer.sendToAllExceptTCP(connection.getID(), update);

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
        int tileX = (int) (x / TILE_SIZE);
        int tileY = (int) (y / TILE_SIZE);

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
                NetworkProtocol.PlayerJoined joinedMsg = new NetworkProtocol.PlayerJoined();
                joinedMsg.username = request.username;
                joinedMsg.x = playerData.getX();     // or however you track player’s X
                joinedMsg.y = playerData.getY();     // similarly for Y
                joinedMsg.timestamp = System.currentTimeMillis();

                // Send to everyone (including the newly joined player)
                networkServer.sendToAllTCP(joinedMsg);

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

    public void handleChunkRequest(Connection connection, NetworkProtocol.ChunkRequest request) {
        Vector2 chunkPos = new Vector2(request.chunkX, request.chunkY);

        try {
            WorldData worldData = ServerGameContext.get().getWorldManager().loadWorld(MULTIPLAYER_WORLD_NAME);
            if (worldData == null) {
                GameLogger.error("Failed to load world data for chunk request at " + chunkPos);
                return;
            }

            // Load or generate the chunk
            Chunk chunk = ServerGameContext.get().getWorldManager().loadChunk(MULTIPLAYER_WORLD_NAME, request.chunkX, request.chunkY);
            if (chunk == null) {
                GameLogger.error("Failed to load/generate chunk at " + chunkPos);
                return;
            }

            // Ensure objects are populated
            List<WorldObject> objects = ServerGameContext.get().getWorldObjectManager()
                .getObjectsForChunk(MULTIPLAYER_WORLD_NAME, chunkPos);

            if (objects == null || objects.isEmpty()) {
                objects = ServerGameContext.get().getWorldObjectManager()
                    .generateObjectsForChunk(MULTIPLAYER_WORLD_NAME, chunkPos, chunk);
            }

            NetworkProtocol.ChunkData chunkData = new NetworkProtocol.ChunkData();
            chunkData.chunkX = request.chunkX;
            chunkData.chunkY = request.chunkY;
            chunkData.primaryBiomeType = chunk.getBiome().getType();
            chunkData.tileData = chunk.getTileData();
            chunkData.blockData = chunk.getBlockDataForSave();
            chunkData.timestamp = System.currentTimeMillis();
            chunkData.worldObjects = new ArrayList<>();

            // Add all valid objects
            if (objects != null) {
                for (WorldObject obj : objects) {
                    if (obj != null) {
                        HashMap<String, Object> objData = obj.getSerializableData();
                        if (objData != null) {
                            chunkData.worldObjects.add(objData);
                        }
                    }
                }
            }

            NetworkProtocol.CompressedChunkData compressed = compressChunkData(chunkData);
            if (compressed == null) {
                GameLogger.error("Failed to compress chunk data for " + chunkPos);
                return;
            }

            // Send compressed chunk data
            connection.sendTCP(compressed);

        } catch (Exception e) {
            GameLogger.error("Error processing chunk request at " + chunkPos + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private NetworkProtocol.CompressedChunkData compressChunkData(NetworkProtocol.ChunkData chunkData) {
        try {
            // First serialize the ChunkData to an uncompressed byte array using Kryo.
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int initialBufferSize = 16 * 1024;   // 16 KB
            int maxBufferSize = 256 * 1024;        // 256 KB
            Output output = new Output(initialBufferSize, maxBufferSize);
            output.setOutputStream(baos);


            Kryo kryo = new Kryo();
            NetworkProtocol.registerClasses(kryo);
            kryo.setReferences(false);
            kryo.writeObject(output, chunkData);
            output.close();
            byte[] uncompressedData = baos.toByteArray();

            // Compress using LZ4.
            LZ4Factory factory = LZ4Factory.fastestInstance();
            LZ4Compressor compressor = factory.fastCompressor();
            int maxCompressedLength = compressor.maxCompressedLength(uncompressedData.length);
            byte[] compressedBuffer = new byte[maxCompressedLength];
            int compressedLength = compressor.compress(uncompressedData, 0, uncompressedData.length,
                compressedBuffer, 0, maxCompressedLength);
            // Create a new array of the actual compressed size.
            byte[] finalCompressedData = Arrays.copyOf(compressedBuffer, compressedLength);

            // Build the compressed chunk message.
            NetworkProtocol.CompressedChunkData compressed = new NetworkProtocol.CompressedChunkData();
            compressed.chunkX = chunkData.chunkX;
            compressed.chunkY = chunkData.chunkY;
            compressed.primaryBiomeType = chunkData.primaryBiomeType;
            compressed.secondaryBiomeType = chunkData.secondaryBiomeType; // if any
            compressed.biomeTransitionFactor = chunkData.biomeTransitionFactor; // if any
            compressed.generationSeed = worldData.getConfig().getSeed();
            // New field: the original uncompressed length.
            compressed.originalLength = uncompressedData.length;
            compressed.data = finalCompressedData;
            return compressed;
        } catch (Exception e) {
            GameLogger.error("Chunk compression failed: " + e.getMessage());
            return null;
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
                        handleWorldObjectUpdate(connection, (NetworkProtocol.WorldObjectUpdate) object);
                        return;
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
                    if (object instanceof NetworkProtocol.ItemDrop) {
                        handleItemDrop(connection, (NetworkProtocol.ItemDrop) object);
                        return;
                    }
                    if (object instanceof NetworkProtocol.BuildingPlacement) {
                        handleBuildingPlacement(connection, (NetworkProtocol.BuildingPlacement) object);
                    }
                    if (object instanceof NetworkProtocol.ChestUpdate) {
                        handleChestUpdate(connection, (NetworkProtocol.ChestUpdate) object);
                        return;
                    }
                    if (object instanceof NetworkProtocol.ItemPickup) {
                        handleItemPickup(connection, (NetworkProtocol.ItemPickup) object);
                        return;
                    }
                    if (object instanceof NetworkProtocol.PlayerInfoUpdate) {
                        NetworkProtocol.PlayerInfoUpdate update = (NetworkProtocol.PlayerInfoUpdate) object;
                        playerPingMap.put(update.username, update.ping);
                        broadcastPlayerList();
                    }

                    if (object instanceof NetworkProtocol.PingRequest) {
                        NetworkProtocol.PingRequest pingRequest = (NetworkProtocol.PingRequest) object;
                        NetworkProtocol.PingResponse pingResponse = new NetworkProtocol.PingResponse();
                        pingResponse.timestamp = pingRequest.timestamp; // echo back the timestamp
                        connection.sendTCP(pingResponse);
                        return;
                    }

                    if (object instanceof NetworkProtocol.SavePlayerDataRequest) {
                        NetworkProtocol.SavePlayerDataRequest saveRequest =
                            (NetworkProtocol.SavePlayerDataRequest) object;

                        try {
                            ServerGameContext.get().getStorageSystem()
                                .savePlayerData(saveRequest.playerData.getUsername(), saveRequest.playerData);

                            ServerGameContext.get().getStorageSystem().getPlayerDataManager().flush();


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

    private void broadcastPlayerList() {
        NetworkProtocol.PlayerList list = new NetworkProtocol.PlayerList();
        List<NetworkProtocol.PlayerInfo> infos = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : playerPingMap.entrySet()) {
            NetworkProtocol.PlayerInfo info = new NetworkProtocol.PlayerInfo();
            info.username = entry.getKey();
            info.ping = entry.getValue();
            infos.add(info);
        }
        list.players = infos;
        networkServer.sendToAllTCP(list);
    }

    private void handleItemPickup(Connection connection, NetworkProtocol.ItemPickup pickup) {
        // Validate the message
        if (pickup == null || pickup.entityId == null) {
            GameLogger.error("Received invalid ItemPickup message.");
            return;
        }

        // Validate that the sender’s username matches the connection.
        String sender = connectedPlayers.get(connection.getID());
        if (sender == null || !sender.equals(pickup.username)) {
            GameLogger.error("Item pickup username mismatch: expected " + sender + " but got " + pickup.username);
            return;
        }

        // Retrieve the item entity from the server’s item entity manager.
        // (Assuming ServerGameContext.get().getItemEntityManager() exists.)
        ItemEntity itemEntity = ServerGameContext.get().getItemEntityManager().getItemEntity(pickup.entityId);
        if (itemEntity == null) {
            GameLogger.error("Item entity not found for pickup: " + pickup.entityId);
            return;
        }

        // Remove the item from the server state.
        ServerGameContext.get().getItemEntityManager().removeItemEntity(pickup.entityId);
        GameLogger.info("Item " + pickup.entityId + " picked up by " + pickup.username);

        // Optionally: update the player's inventory on the server side here.
        // For example, retrieve the ServerPlayer for pickup.username and add itemEntity.getItemData() to their inventory.
        // (This depends on how you want to handle authoritative inventory data on the server.)

        // Broadcast the pickup to all other clients so they remove the item.
        networkServer.sendToAllExceptTCP(connection.getID(), pickup);
    }

    private void handleChestUpdate(Connection connection, NetworkProtocol.ChestUpdate update) {
        // Validate sender...
        String username = connectedPlayers.get(connection.getID());
        if (username == null || !username.equals(update.username)) {
            GameLogger.error("Unauthorized chest update from " + update.username);
            return;
        }

        // 1) We must figure out which chunk the chest is on.
        //    Typically, you do so by scanning placedBlocks or
        //    storing a "chestId -> (x,y) position" map.
        //    For example:
        Vector2 chestPos = findChestPositionInPlacedBlocks(update.chestId);
        if (chestPos == null) {
            // If still not found, log or fail out
            GameLogger.error("Could not find chestPos for chestId = " + update.chestId);
            return;
        }

        // 2) Force-load that chunk to ensure the block is in placedBlocks
        int chunkX = chestPos.x >= 0 ? (int) (chestPos.x / World.CHUNK_SIZE) :
            Math.floorDiv((int) chestPos.x, World.CHUNK_SIZE);
        int chunkY = chestPos.y >= 0 ? (int) (chestPos.y / World.CHUNK_SIZE) :
            Math.floorDiv((int) chestPos.y, World.CHUNK_SIZE);

        Chunk chunk = ServerGameContext.get().getWorldManager()
            .loadChunk("multiplayer_world", chunkX, chunkY);

        // 3) Now find the chest in placedBlocks
        PlaceableBlock chestBlock = ServerGameContext.get()
            .getServerBlockManager().getChestBlock(update.chestId);

        if (chestBlock == null) {
            GameLogger.error("Chest block with id " + update.chestId + " not found, even after loading chunk");
            return;
        }

        // 4) Synchronize & update
        Object lock = chestLocks.computeIfAbsent(update.chestId, id -> new Object());
        synchronized (lock) {
            ChestData currentChest = chestBlock.getChestData();
            if (currentChest == null) {
                currentChest = new ChestData(
                    (int) chestPos.x, (int) chestPos.y
                );
            }
            currentChest.setItems(new ArrayList<>(update.items));
            chestBlock.setChestData(currentChest.copy());

            chunk.setDirty(true);
            ServerGameContext.get().getWorldManager()
                .saveChunk("multiplayer_world", chunk);

            networkServer.sendToAllTCP(update);
            GameLogger.info("Processed chest update " + update.chestId + " from " + update.username);
        }
    }

    private Vector2 findChestPositionInPlacedBlocks(UUID chestId) {
        for (Map.Entry<Vector2, PlaceableBlock> entry :
            ServerGameContext.get().getServerBlockManager().getPlacedBlocks().entrySet()) {
            PlaceableBlock block = entry.getValue();
            if (block.getType() == PlaceableBlock.BlockType.CHEST) {
                ChestData cd = block.getChestData();
                if (cd != null && cd.chestId.equals(chestId)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private void handleBuildingPlacement(Connection connection, NetworkProtocol.BuildingPlacement bp) {
        // Validate that the sender’s username matches the connection
        String username = connectedPlayers.get(connection.getID());
        if (username == null || !username.equals(bp.username)) {
            GameLogger.error("Unauthorized building placement attempt by " + bp.username);
            return;
        }

        // Loop over the layout and place each block.
        for (int x = 0; x < bp.width; x++) {
            for (int y = 0; y < bp.height; y++) {
                String typeId = bp.blockTypeIds[x][y];
                boolean isFlipped = bp.flippedFlags[x][y];
                if (typeId == null || typeId.isEmpty()) continue;
                PlaceableBlock.BlockType type = PlaceableBlock.BlockType.fromItemId(typeId);
                int tileX = bp.startX + x;
                int tileY = bp.startY + y;
                boolean placed = ServerGameContext.get().getServerBlockManager().placeBlock(type, tileX, tileY, isFlipped);
                if (!placed) {
                    GameLogger.error("Failed to place block at (" + tileX + "," + tileY + ") of type " + type);
                    return;
                }
            }
        }

        // (Optionally mark affected chunks as dirty, etc.)

        // Broadcast the building placement to all other clients
        networkServer.sendToAllExceptTCP(connection.getID(), bp);
        GameLogger.info("Building placement by " + bp.username + " placed at (" + bp.startX + "," + bp.startY + ")");
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
        if (message == null || message.content == null) return;
        if (message.timestamp == 0) {
            message.timestamp = System.currentTimeMillis();
        }
        GameLogger.info("Server broadcasting chat message from " + message.sender + ": " + message.content);
        networkServer.sendToAllTCP(message);
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
            } else if (message instanceof NetworkProtocol.PlayerAction) {
                handlePlayerAction(connection, (NetworkProtocol.PlayerAction) message);
            }
        } catch (Exception e) {
            GameLogger.error("Error processing message for " + username + ": " + e.getMessage());
        }

    }
    private void handlePlayerAction(Connection connection, NetworkProtocol.PlayerAction action) {
        if (action == null || action.playerId == null) {
            GameLogger.error("Invalid player action received");
            return;
        }

        ServerPlayer player = activePlayers.get(action.playerId);
        if (player == null) {
            GameLogger.error("No player found for action: " + action.playerId);
            return;
        }

        switch (action.actionType) {
            case CHOP_START:
            case PUNCH_START:
                // Find a choppable world object (e.g. a tree) near the player.
                WorldObject targetObject = findServerChoppableObject(player, action.direction);
                if (targetObject != null) {
                    // Save the target object in the player’s state
                    player.setChoppingObject(targetObject);
                    GameLogger.info("Player " + action.playerId + " started chopping/punching object: " +
                        targetObject.getId() + " at (" + targetObject.getTileX() + "," +
                        targetObject.getTileY() + ")");
                } else {
                    GameLogger.error("No choppable object found near player " + action.playerId);
                }
                // Broadcast the start action to all clients (including the sender)
                networkServer.sendToAllTCP(action);
                break;

            case CHOP_STOP:
            case PUNCH_STOP:
                WorldObject choppedObject = player.getChoppingObject();
                if (choppedObject != null) {
                    Vector2 chunkPos = new Vector2(
                        (int) Math.floor(choppedObject.getPixelX() / (World.CHUNK_SIZE * World.TILE_SIZE)),
                        (int) Math.floor(choppedObject.getPixelY() / (World.CHUNK_SIZE * World.TILE_SIZE))
                    );
                    // Remove the object from the server’s world–object manager.
                    ServerGameContext.get().getWorldObjectManager().removeObject(MULTIPLAYER_WORLD_NAME, chunkPos, choppedObject.getId());
                    NetworkProtocol.WorldObjectUpdate removalUpdate = new NetworkProtocol.WorldObjectUpdate();
                    removalUpdate.objectId = choppedObject.getId();
                    removalUpdate.type = NetworkProtocol.NetworkObjectUpdateType.REMOVE;
                    removalUpdate.data = choppedObject.getSerializableData();
                    // Reinsert the raw tile coordinates so the client computes the correct chunk:
                    removalUpdate.data.put("tileX", choppedObject.getTileX());
                    removalUpdate.data.put("tileY", choppedObject.getTileY());
                    networkServer.sendToAllTCP(removalUpdate);
                    player.setChoppingObject(null);
                }
                // Broadcast the stop action to all clients so that remote players can stop the animation.
                networkServer.sendToAllTCP(action);
                break;

            default:
                GameLogger.error("Unhandled player action type: " + action.actionType);
                break;
        }
    }

    private void handleWorldObjectUpdate(Connection connection, NetworkProtocol.WorldObjectUpdate update) {
        // Ensure that only authenticated users can send updates.
        String username = connectedPlayers.get(connection.getID());
        if (username == null) {
            GameLogger.error("WorldObjectUpdate received from unauthenticated connection.");
            return;
        }

        switch (update.type) {
            case REMOVE:
                // Instead of checking for "x" and "y", check for "tileX" and "tileY".
                if (update.data == null || !update.data.containsKey("tileX") || !update.data.containsKey("tileY")) {
                    GameLogger.error("WorldObjectUpdate REMOVE missing tile position data.");
                    return;
                }

                float tileX, tileY;
                try {
                    tileX = Float.parseFloat(update.data.get("tileX").toString());
                    tileY = Float.parseFloat(update.data.get("tileY").toString());
                } catch (Exception e) {
                    GameLogger.error("Error parsing world object tile position: " + e.getMessage());
                    return;
                }
                // Convert tile coordinates to pixel coordinates.
                float x = tileX * World.TILE_SIZE;
                float y = tileY * World.TILE_SIZE;

                // Compute which chunk this object belongs to.
                int chunkX = (int) Math.floor(x / (World.TILE_SIZE * Chunk.CHUNK_SIZE));
                int chunkY = (int) Math.floor(y / (World.TILE_SIZE * Chunk.CHUNK_SIZE));
                Vector2 chunkPos = new Vector2(chunkX, chunkY);

                // Remove the object from the server’s world–object manager.
                ServerGameContext.get().getWorldObjectManager().removeObject(MULTIPLAYER_WORLD_NAME, chunkPos, update.objectId);
                GameLogger.info("Removed world object " + update.objectId + " from chunk " + chunkPos);

                // Mark the corresponding chunk as dirty and save it so that the change is persisted.
                Chunk chunk = ServerGameContext.get().getWorldManager().loadChunk(MULTIPLAYER_WORLD_NAME, chunkX, chunkY);
                if (chunk != null) {
                    chunk.setDirty(true);
                    ServerGameContext.get().getWorldManager().saveChunk(MULTIPLAYER_WORLD_NAME, chunk);
                }

                // Broadcast the removal update to all connected clients.
                networkServer.sendToAllTCP(update);
                break;

            case ADD:
                // (Handle object addition if needed.)
                break;

            case UPDATE:
                // (Handle object update if needed.)
                break;

            default:
                GameLogger.error("Unknown world object update type: " + update.type);
                break;
        }
    }

    public Server getNetworkServer() {
        return networkServer;
    }

    /**
     * Returns the first WorldObject that is choppable and whose bounding box
     * overlaps a search rectangle in front of the player.
     * <p>
     * This version uses the player's current pixel position and computes a target
     * point based on the chop direction. It then constructs a search rectangle (2×tile size)
     * centered at that target and searches current and adjacent chunks for a matching object.
     *
     * @param player    The ServerPlayer performing the chop action.
     * @param direction The chop direction (e.g. "up", "down", "left", or "right")
     * @return A choppable WorldObject if found; otherwise null.
     */
    private WorldObject findServerChoppableObject(ServerPlayer player, String direction) {
        // Use the player's current position in pixels.
        Vector2 playerPos = player.getPosition();

        // Calculate target point by offsetting the player's position.
        float searchDistance = TILE_SIZE * 1.5f;
        float targetX = playerPos.x;
        float targetY = playerPos.y;

        switch (direction.toLowerCase()) {
            case "up":
                targetY += searchDistance;
                break;
            case "down":
                targetY -= searchDistance;
                break;
            case "left":
                targetX -= searchDistance;
                break;
            case "right":
                targetX += searchDistance;
                break;
            default:
                GameLogger.error("Unknown chopping direction: " + direction);
                return null;
        }

        // Create a search rectangle centered at the target position.
        // Here the rectangle is 2 tiles wide and 2 tiles high.
        Rectangle searchArea = new Rectangle(targetX - TILE_SIZE, targetY - TILE_SIZE, TILE_SIZE * 2, TILE_SIZE * 2);
        GameLogger.info("Chop search area: x=" + searchArea.x + " y=" + searchArea.y +
            " width=" + searchArea.width + " height=" + searchArea.height);

        // Determine chunk coordinates based on the target position.
        int chunkX = (int) Math.floor(targetX / (TILE_SIZE * CHUNK_SIZE));
        int chunkY = (int) Math.floor(targetY / (TILE_SIZE * CHUNK_SIZE));

        // Search the current chunk and adjacent chunks.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                Vector2 searchChunkPos = new Vector2(chunkX + dx, chunkY + dy);
                List<WorldObject> objects = ServerGameContext.get()
                    .getWorldObjectManager()
                    .getObjectsForChunk(MULTIPLAYER_WORLD_NAME, searchChunkPos);
                if (objects == null || objects.isEmpty()) {
                    GameLogger.error("No objects found in chunk " + searchChunkPos);
                    continue;
                }
                for (WorldObject obj : objects) {
                    if (isChoppable(obj.getType())) {
                        // Use the object's bounding box (which is already defined for collision)
                        Rectangle objBounds = obj.getBoundingBox();
                        if (objBounds.overlaps(searchArea)) {
                            GameLogger.info("Found choppable object: " + obj.getType() +
                                " (ID: " + obj.getId() + ") in chunk " + searchChunkPos);
                            return obj;
                        }
                    }
                }
            }
        }

        GameLogger.info("No choppable objects found in search area: " + searchArea);
        return null;
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

    private void handleBlockPlacement(Connection connection, NetworkProtocol.BlockPlacement placement) {
        String username = connectedPlayers.get(connection.getID());
        if (username == null || !username.equals(placement.username)) {
            GameLogger.error("Unauthorized block placement attempt by " + placement.username);
            return;
        }

        switch (placement.action) {
            case PLACE:
                // Place the block using our block manager.
                PlaceableBlock.BlockType type = PlaceableBlock.BlockType.fromItemId(placement.blockTypeId);
                boolean placed = ServerGameContext.get().getServerBlockManager().placeBlock(type, placement.tileX, placement.tileY, false);
                if (placed) {
                    // *** NEW CODE: Update the corresponding chunk ***
                    int chunkX = Math.floorDiv(placement.tileX, World.CHUNK_SIZE);
                    int chunkY = Math.floorDiv(placement.tileY, World.CHUNK_SIZE);
                    Chunk chunk = ServerGameContext.get().getWorldManager().loadChunk("multiplayer_world", chunkX, chunkY);
                    if (chunk != null) {
                        // (Assume that blockManager.placeBlock() internally creates and returns the new block.)
                        // If not, then get the block from your block manager’s internal map:
                        Vector2 blockPos = new Vector2(placement.tileX, placement.tileY);
                        PlaceableBlock block = ServerGameContext.get().getServerBlockManager().getBlockAt(blockPos);
                        if (block != null) {
                            chunk.getBlocks().put(blockPos, block);
                            chunk.setDirty(true);
                            ServerGameContext.get().getWorldManager().saveChunk("multiplayer_world", chunk);
                        }
                    }
                    // Broadcast to other clients.
                    networkServer.sendToAllExceptTCP(connection.getID(), placement);
                } else {
                    GameLogger.error("Failed to place block at (" + placement.tileX + ", " + placement.tileY + ")");
                }
                break;
            case REMOVE:
                // Remove the block from our world.
                ServerGameContext.get().getServerBlockManager().removeBlock(placement.tileX, placement.tileY);
                // Also update the chunk data.
                int chunkX = Math.floorDiv(placement.tileX, World.CHUNK_SIZE);
                int chunkY = Math.floorDiv(placement.tileY, World.CHUNK_SIZE);
                Chunk chunk = ServerGameContext.get().getWorldManager().loadChunk("multiplayer_world", chunkX, chunkY);
                if (chunk != null) {
                    chunk.getBlocks().remove(new Vector2(placement.tileX, placement.tileY));
                    chunk.setDirty(true);
                    ServerGameContext.get().getWorldManager().saveChunk("multiplayer_world", chunk);
                }
                // Broadcast to other clients.
                networkServer.sendToAllExceptTCP(connection.getID(), placement);
                break;
        }
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
