package org.discord;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
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
import io.github.pokemeetup.multiplayer.server.events.blocks.BlockPlaceEvent;
import io.github.pokemeetup.multiplayer.server.events.player.PlayerJoinEvent;
import io.github.pokemeetup.system.data.*;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.gameplay.inventory.ItemEntity;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.WeatherSystem;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.PasswordUtils;
import io.github.pokemeetup.utils.storage.GameFileSystem;
import io.github.pokemeetup.utils.textures.TextureManager;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import org.discord.context.ServerGameContext;
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
    private static final int WRITE_BUFFER = 1048576; // 1MB buffer for large chunk data
    private static final int OBJECT_BUFFER = 1048576; // 1MB object buffer
    private static final int SCHEDULER_POOL_SIZE = 6; // Increased pool size for better parallelism
    private static final long AUTH_TIMEOUT = 30000; // 30 seconds for authentication
    private static final long SAVE_INTERVAL = 300000;
    private static final ConcurrentHashMap<UUID, Object> chestLocks = new ConcurrentHashMap<>();

    // Thread-local Kryo instance for chunk compression to avoid creating new instances
    private static final ThreadLocal<Kryo> kryoThreadLocal = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        NetworkProtocol.registerClasses(kryo);
        kryo.setReferences(false);
        return kryo;
    });

    // Cache for recently compressed chunks to avoid re-compression
    private final Map<String, NetworkProtocol.CompressedChunkData> chunkCache = new ConcurrentHashMap<>();
    private static final int MAX_CHUNK_CACHE_SIZE = 100;

    /**
     * Invalidate chunk cache for a specific chunk position
     */
    private void invalidateChunkCache(int chunkX, int chunkY) {
        String cacheKey = chunkX + "," + chunkY;
        chunkCache.remove(cacheKey);
    }

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
    private final ServerPokemonSpawnManager serverPokemonSpawnManager;
    private volatile boolean running;
    private final WeatherSystem weatherSystem;

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
            this.weatherSystem = new WeatherSystem();
            serverPokemonSpawnManager = new ServerPokemonSpawnManager(MULTIPLAYER_WORLD_NAME);
            setupNetworkListener();
            scheduler.scheduleAtFixedRate(() -> {
                serverPokemonSpawnManager.update(0.1f);
                serverPokemonSpawnManager.broadcastPokemonUpdates();
            }, 0, 100, TimeUnit.MILLISECONDS);// In your GameServer constructor, after worldData is initialized:
            scheduler.scheduleAtFixedRate(() -> {
                worldData.updateTime(1.0f);
            }, 0, 1, TimeUnit.SECONDS);

            scheduler.scheduleAtFixedRate(() -> {
                try {
                    broadcastWorldState();
                } catch (Exception e) {
                    GameLogger.error("Error broadcasting world state: " + e.getMessage());
                }
            }, 1000, 1000, TimeUnit.MILLISECONDS); // every 1 second

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

    /**
     * Gets all current player positions for Pokemon AI behaviors.
     * Enables Pokemon to react to players (flee, approach, investigate, etc.)
     */
    public Map<String, Vector2> getAllPlayerPositions() {
        Map<String, Vector2> positions = new HashMap<>();
        for (Map.Entry<String, ServerPlayer> entry : activePlayers.entrySet()) {
            ServerPlayer player = entry.getValue();
            if (player != null) {
                positions.put(entry.getKey(), new Vector2(player.getPosition().x, player.getPosition().y));
            }
        }
        return positions;
    }

    /**
     * Handles a request for server information from a client.
     * It reads the server icon, encodes it, and sends it back along with other server details.
     *
     * @param connection The client connection that sent the request.
     */
    private void handleServerInfoRequest(Connection connection) {
        GameLogger.info("Received ServerInfoRequest from: " + connection.getRemoteAddressTCP());
        ServerConnectionConfig serverConfig = this.config;
        byte[] iconBytes = null;
        String iconPath = serverConfig.getIconPath(); // e.g., "server-icon.png"

        if (iconPath != null && !iconPath.isEmpty()) {
            try {
                iconBytes = GameFileSystem.getInstance().getDelegate().openInputStream(iconPath).readAllBytes();
            } catch (IOException e) {
                GameLogger.error("Could not read server icon file at '" + iconPath + "': " + e.getMessage());
            }
        } else {
            GameLogger.info("No server icon path specified in config.");
        }
        String iconBase64 = null;
        if (iconBytes != null) {
            iconBase64 = Base64.getEncoder().encodeToString(iconBytes);
        }
        NetworkProtocol.ServerInfo info = new NetworkProtocol.ServerInfo();
        info.name = serverConfig.getServerName();
        info.motd = serverConfig.getMotd();
        info.playerCount = connectedPlayers.size();
        info.maxPlayers = serverConfig.getMaxPlayers();
        info.version = serverConfig.getVersion();
        info.iconBase64 = iconBase64; // Set the encoded string
        NetworkProtocol.ServerInfoResponse response = new NetworkProtocol.ServerInfoResponse();
        response.serverInfo = info;
        response.timestamp = System.currentTimeMillis();

        connection.sendTCP(response);
        GameLogger.info("Sent ServerInfoResponse to " + connection.getRemoteAddressTCP());
    }

    private WorldData initializeMultiplayerWorld() {
        try {
            WorldData worldData = ServerGameContext.get().getWorldManager().loadWorld(MULTIPLAYER_WORLD_NAME);
            if (worldData == null) {
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
                    recentDisconnects.put(username, System.currentTimeMillis());
                    activeConnections.remove(username);
                    cleanupPlayerSession(connection.getID(), username);
                    playerPingMap.remove(username);
                    broadcastPlayerList();
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

    /**
     * Handles an item drop request from a client.
     * The server now creates an authoritative ItemEntity with a server-generated UUID
     * and broadcasts this new, authoritative entity information to all clients.
     */
    private void handleItemDrop(Connection connection, NetworkProtocol.ItemDrop drop) {
        GameLogger.info("Received ItemDrop from connection " + connection.getID() +
                       " - item: " + (drop.itemData != null ? drop.itemData.getItemId() : "null") +
                       ", pos: (" + drop.x + "," + drop.y + "), username: " + drop.username);

        String username = connectedPlayers.get(connection.getID());
        if (username == null || !username.equals(drop.username)) {
            GameLogger.error("Unauthorized item drop attempt - connection user: " + username + ", drop username: " + drop.username);
            return;
        }
        ServerPlayer player = activePlayers.get(username);
        if (player == null) {
            GameLogger.error("No player found for item drop");
            return;
        }

        // Validate that the drop location is reasonably close to the player.
        float distance = Vector2.dst(player.getPosition().x, player.getPosition().y, drop.x, drop.y);
        if (distance > TILE_SIZE * 3) { // Using a slightly larger tolerance
            GameLogger.error("Item drop position too far from player");
            return;
        }

        // 1. Server creates the authoritative ItemEntity. This now returns the created entity.
        ItemEntity serverEntity = ServerGameContext.get().getItemEntityManager()
            .spawnItemEntity(drop.itemData, drop.x, drop.y);

        if (serverEntity == null) {
            GameLogger.error("Server failed to create ItemEntity for drop.");
            return;
        }

        GameLogger.info("Server created ItemEntity " + serverEntity.getEntityId() + " for user " + username);

        // 2. Create a new, authoritative drop message with the server's entity ID.
        NetworkProtocol.ItemDrop authoritativeDrop = new NetworkProtocol.ItemDrop();
        authoritativeDrop.itemData = drop.itemData;
        authoritativeDrop.x = serverEntity.getPosition().x;
        authoritativeDrop.y = serverEntity.getPosition().y;
        authoritativeDrop.username = drop.username;
        authoritativeDrop.entityId = serverEntity.getEntityId();
        authoritativeDrop.timestamp = System.currentTimeMillis();

        // 3. Broadcast the authoritative message to ALL clients so they create the same entity.
        networkServer.sendToAllTCP(authoritativeDrop);
    }


    /**
     * Handles an item pickup request from a client.
     * The server now validates the pickup, removes the item from its authoritative list,
     * and broadcasts the removal command to ALL clients, preventing duplication.
     */
    private void handleItemPickup(Connection connection, NetworkProtocol.ItemPickup pickup) {
        if (pickup == null || pickup.entityId == null) {
            GameLogger.error("Received invalid ItemPickup message.");
            return;
        }
        String senderUsername = connectedPlayers.get(connection.getID());
        if (senderUsername == null || !senderUsername.equals(pickup.username)) {
            GameLogger.error("Item pickup username mismatch: expected " + senderUsername + " but got " + pickup.username);
            return;
        }

        // 1. Atomically attempt to remove the item from the server's manager.
        // This now returns the removed entity, or null if it was already gone.
        ItemEntity removedEntity = ServerGameContext.get().getItemEntityManager().removeItemEntity(pickup.entityId);

        // 2. Check if the item actually existed and was removed.
        if (removedEntity == null) {
            // This is not an error; it prevents a race condition where two players pick up the same item.
            GameLogger.info("Item entity " + pickup.entityId + " already picked up or despawned.");
            return;
        }

        // 3. If removal was successful, notify ALL clients to remove the item.
        // This serves as both a confirmation for the picker and a despawn command for everyone else.
        GameLogger.info("Item " + pickup.entityId + " picked up by " + pickup.username + ". Broadcasting removal to all clients.");
        networkServer.sendToAllTCP(pickup);
    }


    // Legacy methods without player parameter - delegate to new methods with null player
    private void serverDestroyBlock(PlaceableBlock block) {
        serverDestroyBlock(block, null);
    }

    private void serverDestroyObject(WorldObject object) {
        serverDestroyObject(object, null);
    }

    private WorldObject findServerChoppableObject(int tileX, int tileY) {
        // Calculate the chunk position for the target tile
        int chunkX = Math.floorDiv(tileX, CHUNK_SIZE);
        int chunkY = Math.floorDiv(tileY, CHUNK_SIZE);
        Vector2 chunkPos = new Vector2(chunkX, chunkY);

        // Load chunk if not loaded
        Chunk chunk = ServerGameContext.get().getWorldManager().loadChunk(MULTIPLAYER_WORLD_NAME, chunkX, chunkY);
        if (chunk == null) {
            GameLogger.error("Failed to load chunk (" + chunkX + "," + chunkY + ") for tile (" + tileX + "," + tileY + ")");
            return null;
        }

        List<WorldObject> objects = ServerGameContext.get().getWorldObjectManager().getObjectsForChunk(MULTIPLAYER_WORLD_NAME, chunkPos);
        if (objects == null || objects.isEmpty()) {
            // Try to generate objects for this chunk
            objects = ServerGameContext.get().getWorldObjectManager().generateObjectsForChunk(MULTIPLAYER_WORLD_NAME, chunkPos, chunk);
            GameLogger.info("Generated " + (objects != null ? objects.size() : 0) + " objects for chunk (" + chunkX + "," + chunkY + ")");
        }

        if (objects == null || objects.isEmpty()) {
            GameLogger.info("No objects in chunk (" + chunkX + "," + chunkY + ") for tile (" + tileX + "," + tileY + ")");
            return null;
        }

        // Find the object at the exact tile position the player is facing
        float pixelX = tileX * TILE_SIZE + TILE_SIZE / 2f;
        float pixelY = tileY * TILE_SIZE + TILE_SIZE / 2f;

        for (WorldObject obj : objects) {
            if (isChoppable(obj.getType()) && obj.getBoundingBox().contains(pixelX, pixelY)) {
                GameLogger.info("Found choppable object " + obj.getId() + " (" + obj.getType() + ") at tile (" +
                    obj.getTileX() + "," + obj.getTileY() + ") matching target tile (" + tileX + "," + tileY + ")");
                return obj;
            }
        }

        GameLogger.info("No choppable object found at tile (" + tileX + "," + tileY + ") in chunk (" + chunkX + "," + chunkY + ") with " + objects.size() + " objects");
        return null;
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

            serverPlayer.setPosition(update.x, update.y);
            serverPlayer.setDirection(update.direction);
            serverPlayer.setMoving(update.isMoving);
            int cX = (int) Math.floor(update.x / (World.CHUNK_SIZE * World.TILE_SIZE));
            int cY = (int) Math.floor(update.y / (World.CHUNK_SIZE * World.TILE_SIZE));
            Vector2 chunkPos = new Vector2(cX, cY);
            playerChunkMap.put(username, chunkPos);
            PlayerData playerData = ServerGameContext.get().getStorageSystem()
                .getPlayerDataManager().loadPlayerData(UUID.nameUUIDFromBytes(update.username.getBytes()));

            if (playerData == null) {
                GameLogger.error("No player data found for active player: " + username);
                return;
            }
            playerData.setX(update.x);
            playerData.setY(update.y);
            playerData.setDirection(update.direction);
            playerData.setMoving(update.isMoving);
            playerData.setWantsToRun(update.wantsToRun);
            playerData.setCharacterType(update.characterType); // [NEW] Update character type

            if (update.inventoryItems != null) {
                playerData.setInventoryItems(Arrays.asList(update.inventoryItems));
            }
            if (update.partyPokemon != null) {
                playerData.setPartyPokemon(update.partyPokemon);
            }
            UUID playerUUID = UUID.nameUUIDFromBytes(username.getBytes());
            ServerGameContext.get().getStorageSystem()
                .getPlayerDataManager().savePlayerData(playerUUID, playerData);
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
            NetworkProtocol.WildPokemonSpawn broadcastSpawn = createSpawnBroadcast(pokemon);
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
        if (pokemon.getStats() != null) {
            PokemonData.Stats stats = new PokemonData.Stats(pokemon.getStats());
            pokemonData.setStats(stats);
        }
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
            UUID playerUUID = UUID.nameUUIDFromBytes(request.username.getBytes());
            GameLogger.info("Generated UUID for player: " + playerUUID);
            PlayerData playerData = ServerGameContext.get().getStorageSystem()
                .getPlayerDataManager().loadPlayerData(playerUUID);

            if (playerData == null) {
                GameLogger.info("Creating new player data for: " + request.username);
                playerData = new PlayerData(request.username);
                playerData.setX(0);
                playerData.setY(0);
                playerData.setDirection("down");
                playerData.setMoving(false);
                playerData.setInventoryItems(new ArrayList<>());
                playerData.setPartyPokemon(new ArrayList<>());
                ServerGameContext.get().getStorageSystem()
                    .getPlayerDataManager().savePlayerData(playerUUID, playerData);
                ServerGameContext.get().getStorageSystem()
                    .getPlayerDataManager().flush(); // Force write to disk
            }
            if (!authenticateUser(request.username, request.password)) {
                sendLoginFailure(connection, "Invalid credentials");
                return;
            }

            synchronized (activeConnections) {
                handleExistingConnection(request.username);
                ConnectionInfo newConnection = new ConnectionInfo(connection.getID());
                activeConnections.put(request.username, newConnection);
                ServerPlayer player;
                if (!activeConnections.containsKey(request.username)) {
                    player = new ServerPlayer(request.username, playerData);
                } else {
                    player = new ServerPlayer(request.username, ServerGameContext.get().getStorageSystem().getPlayerDataManager().playerCache.get(UUID.nameUUIDFromBytes(request.username.getBytes())));
                }
                activePlayers.put(request.username, player);
                connectedPlayers.put(connection.getID(), request.username);
                newConnection.isAuthenticated = true;
                sendSuccessfulLoginResponse(connection, player);
                NetworkProtocol.PlayerJoined joinedMsg = new NetworkProtocol.PlayerJoined();
                joinedMsg.username = request.username;
                joinedMsg.x = playerData.getX();     // or however you track player’s X
                joinedMsg.y = playerData.getY();     // similarly for Y
                joinedMsg.timestamp = System.currentTimeMillis();

                ServerGameContext.get().getEventManager().fireEvent(new PlayerJoinEvent(request.username, playerData));
                networkServer.sendToAllTCP(joinedMsg);
                sendActivePokemonToConnection(connection);

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
        String cacheKey = request.chunkX + "," + request.chunkY;

        try {
            // Check cache first for recently compressed chunks
            NetworkProtocol.CompressedChunkData cached = chunkCache.get(cacheKey);
            if (cached != null) {
                connection.sendTCP(cached);
                return;
            }

            WorldData worldData = ServerGameContext.get().getWorldManager().loadWorld(MULTIPLAYER_WORLD_NAME);
            if (worldData == null) {
                GameLogger.error("Failed to load world data for chunk request at " + chunkPos);
                return;
            }

            long chunkSeed = worldData.getConfig().getSeed() +
                (((long) request.chunkX << 32) | ((long) request.chunkY & 0xFFFFFFFFL));
            Chunk chunk = ServerGameContext.get().getWorldManager().loadChunk(MULTIPLAYER_WORLD_NAME, request.chunkX, request.chunkY);
            if (chunk == null) {
                GameLogger.error("Failed to load/generate chunk at " + chunkPos);
                return;
            }

            float centerPixelX = (request.chunkX * Chunk.CHUNK_SIZE + Chunk.CHUNK_SIZE * 0.5f) * World.TILE_SIZE;
            float centerPixelY = (request.chunkY * Chunk.CHUNK_SIZE + Chunk.CHUNK_SIZE * 0.5f) * World.TILE_SIZE;
            BiomeTransitionResult transition = ServerGameContext.get().getWorldManager().getBiomeTransitionAt(
                centerPixelX, centerPixelY
            );
            if (transition != null && transition.getPrimaryBiome() != null) {
                chunk.setBiome(transition.getPrimaryBiome());
            }

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
            if (transition != null && transition.getSecondaryBiome() != null) {
                chunkData.secondaryBiomeType = transition.getSecondaryBiome().getType();
                chunkData.biomeTransitionFactor = transition.getTransitionFactor();
            } else {
                chunkData.secondaryBiomeType = null;
                chunkData.biomeTransitionFactor = 1.0f;
            }

            chunkData.tileData = chunk.getTileData().clone();
            chunkData.blockData = chunk.getBlockDataForSave();
            chunkData.generationSeed = chunkSeed;
            chunkData.timestamp = System.currentTimeMillis();
            chunkData.worldObjects = new ArrayList<>();
            if (objects != null) {
                for (WorldObject obj : objects) {
                    if (obj != null) {
                        Map<String, Object> objData = obj.getSerializableData();
                        if (objData != null) {
                            chunkData.worldObjects.add(new HashMap<>(objData));
                        }
                    }
                }
            }

            NetworkProtocol.CompressedChunkData compressed = compressChunkData(chunkData);
            if (compressed == null) {
                GameLogger.error("Failed to compress chunk data for " + chunkPos);
                return;
            }

            // Log compression stats for monitoring
            int originalSize = (chunkData.tileData != null ? chunkData.tileData.length * chunkData.tileData[0].length * 4 : 0);
            float compressionRatio = originalSize > 0 ? (float) compressed.data.length / originalSize * 100 : 0;
            if (compressionRatio > 80) {
                GameLogger.info("Warning: Low compression ratio " + compressionRatio + "% for chunk " + chunkPos);
            }

            // Cache the compressed chunk if cache isn't full
            if (chunkCache.size() < MAX_CHUNK_CACHE_SIZE) {
                chunkCache.put(cacheKey, compressed);
            }

            connection.sendTCP(compressed);
            GameLogger.info("Sent chunk " + chunkPos + " (" + compressed.data.length + " bytes compressed) with " +
                (objects != null ? objects.size() : 0) + " objects");

        } catch (Exception e) {
            GameLogger.error("Error processing chunk request at " + chunkPos + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void broadcastWorldState() {
        if (activePlayers.isEmpty()) {
            return;
        }
        Map<BiomeType, Integer> biomeCounts = new HashMap<>();
        for (ServerPlayer player : activePlayers.values()) {
            BiomeTransitionResult btr = ServerGameContext.get().getWorldManager().getBiomeTransitionAt(player.getPosition().x, player.getPosition().y);
            if (btr != null && btr.getPrimaryBiome() != null) {
                biomeCounts.merge(btr.getPrimaryBiome().getType(), 1, Integer::sum);
            }
        }

        BiomeType dominantBiomeType = biomeCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(BiomeType.PLAINS);

        float temperature = computeTemperatureForBiome(dominantBiomeType);
        Biome dominantBiome = ServerGameContext.get().getWorldManager().getBiome(dominantBiomeType);

        if (dominantBiome == null) {
            GameLogger.error("Could not retrieve dominant biome object. Aborting weather update.");
            return;
        }
        weatherSystem.updateServerState(1.0f, // Use a fixed delta of 1 second for each update cycle
            new BiomeTransitionResult(dominantBiome, null, 1.0f),
            temperature,
            (float) (worldData.getWorldTimeInMinutes() % (24 * 60)) / 60f
        );
        NetworkProtocol.WorldStateUpdate update = new NetworkProtocol.WorldStateUpdate();
        update.seed = worldData.getConfig().getSeed();
        update.worldTimeInMinutes = worldData.getWorldTimeInMinutes();
        update.dayLength = worldData.getDayLength();
        update.currentWeather = weatherSystem.getCurrentWeather();
        update.intensity = weatherSystem.getIntensity();
        update.accumulation = weatherSystem.getAccumulation();
        update.timestamp = System.currentTimeMillis();

        networkServer.sendToAllTCP(update);
    }

    /**
     * Example helper that computes a temperature (in °C) based on a given biome type.
     */
    private float computeTemperatureForBiome(BiomeType type) {
        switch (type) {
            case SNOW:
                return 0f;
            case DESERT:
                return 40f;
            case HAUNTED:
                return 15f;
            case RAIN_FOREST:
                return 28f;
            case FOREST:
                return 22f;
            case PLAINS:
                return 25f;
            case BEACH:
                return 30f;
            default:
                return 20f;
        }
    }

    private NetworkProtocol.CompressedChunkData compressChunkData(NetworkProtocol.ChunkData chunkData) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int initialBufferSize = 64 * 1024;   // 64 KB
            int maxBufferSize = 2 * 1024 * 1024;  // 2 MB max buffer for large chunks
            Output output = new Output(initialBufferSize, maxBufferSize);
            output.setOutputStream(baos);

            // Use thread-local Kryo instance for better performance
            Kryo kryo = kryoThreadLocal.get();
            kryo.writeObject(output, chunkData);
            output.close();
            byte[] uncompressedData = baos.toByteArray();

            if (uncompressedData.length > 1024 * 1024) {
                GameLogger.info("Large chunk data before compression: " + uncompressedData.length + " bytes for chunk (" +
                    chunkData.chunkX + "," + chunkData.chunkY + ") with " +
                    (chunkData.worldObjects != null ? chunkData.worldObjects.size() : 0) + " objects");
            }

            LZ4Factory factory = LZ4Factory.fastestInstance();
            LZ4Compressor compressor = factory.fastCompressor();
            int maxCompressedLength = compressor.maxCompressedLength(uncompressedData.length);
            byte[] compressedBuffer = new byte[maxCompressedLength];
            int compressedLength = compressor.compress(uncompressedData, 0, uncompressedData.length,
                compressedBuffer, 0, maxCompressedLength);
            byte[] finalCompressedData = Arrays.copyOf(compressedBuffer, compressedLength);

            NetworkProtocol.CompressedChunkData compressed = new NetworkProtocol.CompressedChunkData();
            compressed.chunkX = chunkData.chunkX;
            compressed.chunkY = chunkData.chunkY;
            compressed.primaryBiomeType = chunkData.primaryBiomeType;
            compressed.secondaryBiomeType = chunkData.secondaryBiomeType;
            compressed.biomeTransitionFactor = chunkData.biomeTransitionFactor;
            compressed.generationSeed = worldData.getConfig().getSeed();
            compressed.originalLength = uncompressedData.length;
            compressed.data = finalCompressedData;

            return compressed;
        } catch (Exception e) {
            GameLogger.error("Chunk compression failed for (" + chunkData.chunkX + "," + chunkData.chunkY + "): " +
                e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }


    private void setupNetworkListener() {
        networkServer.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                try {
                    GameLogger.info("New connection attempt from: " + connection.getRemoteAddressTCP());
                    if (playerManager.getOnlinePlayers().size() >= config.getMaxPlayers()) {
                        GameLogger.info("Connection rejected: Max players reached");
                        sendConnectionResponse(connection, false, "Server is full");
                        scheduler.schedule(() -> connection.close(), 100, TimeUnit.MILLISECONDS);
                        return;
                    }
                    NetworkProtocol.ConnectionResponse response = new NetworkProtocol.ConnectionResponse();
                    response.success = true;
                    response.message = "Connection established";
                    connection.sendTCP(response);

                    GameLogger.info("Connection " + connection.getID() + " established - awaiting authentication");
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
                        return;
                    }
                    if (object instanceof NetworkProtocol.ItemDrop) {
                        handleItemDrop(connection, (NetworkProtocol.ItemDrop) object);
                        return;
                    }
                    if (object instanceof NetworkProtocol.BuildingPlacement) {
                        handleBuildingPlacement(connection, (NetworkProtocol.BuildingPlacement) object);
                    }
                    if (object instanceof NetworkProtocol.ServerInfoRequest) {
                        handleServerInfoRequest(connection);
                        return; // Message handled
                    }
                    if (object instanceof NetworkProtocol.ChestUpdate) {
                        handleChestUpdate(connection, (NetworkProtocol.ChestUpdate) object);
                        return;
                    }
                    if (object instanceof NetworkProtocol.ChestOperationRequest) {
                        handleChestOperation(connection, (NetworkProtocol.ChestOperationRequest) object);
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
                        return;
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


    private void handleChestUpdate(Connection connection, NetworkProtocol.ChestUpdate update) {
        String username = connectedPlayers.get(connection.getID());
        if (username == null || !username.equals(update.username)) {
            GameLogger.error("Unauthorized chest update from " + update.username);
            return;
        }

        // Find chest in placedBlocks or loaded chunks
        Vector2 chestPos = findChestPositionInPlacedBlocks(update.chestId);
        if (chestPos == null) {
            chestPos = findChestPositionInLoadedChunks(update.chestId);
        }

        if (chestPos == null) {
            GameLogger.error("Could not find chest position for chestId = " + update.chestId);
            return;
        }

        int chunkX = chestPos.x >= 0 ? (int) (chestPos.x / World.CHUNK_SIZE)
            : Math.floorDiv((int) chestPos.x, World.CHUNK_SIZE);
        int chunkY = chestPos.y >= 0 ? (int) (chestPos.y / World.CHUNK_SIZE)
            : Math.floorDiv((int) chestPos.y, World.CHUNK_SIZE);
        Chunk chunk = ServerGameContext.get().getWorldManager().loadChunk("multiplayer_world", chunkX, chunkY);
        PlaceableBlock chestBlock = ServerGameContext.get().getServerBlockManager().getChestBlock(update.chestId);
        if (chestBlock == null) {
            GameLogger.error("Chest block with id " + update.chestId + " not found");
            return;
        }

        // Synchronize on chest-specific lock to prevent concurrent modifications
        Object lock = chestLocks.computeIfAbsent(update.chestId, id -> new Object());
        synchronized (lock) {
            ChestData currentChest = chestBlock.getChestData();
            if (currentChest == null) {
                currentChest = new ChestData((int) chestPos.x, (int) chestPos.y);
                chestBlock.setChestData(currentChest);
            }

            // Apply the update
            currentChest.setItems(new ArrayList<>(update.items));
            chunk.setDirty(true);
            ServerGameContext.get().getWorldManager().saveChunk(MULTIPLAYER_WORLD_NAME, chunk);

            // Broadcast to all clients (including sender for confirmation)
            networkServer.sendToAllTCP(update);
            GameLogger.info("Processed chest update for chestId " + update.chestId + " from " + update.username);
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

    /**
     * Searches through all loaded chunks to find a chest with the given chestId.
     * If found, adds it to the ServerBlockManager's placedBlocks map for faster future lookups.
     *
     * @param chestId The UUID of the chest to find
     * @return The tile position of the chest, or null if not found
     */
    private Vector2 findChestPositionInLoadedChunks(UUID chestId) {
        Map<Vector2, Chunk> loadedChunks = ServerGameContext.get().getWorldManager()
            .getLoadedChunks(MULTIPLAYER_WORLD_NAME);

        if (loadedChunks == null) {
            return null;
        }

        for (Map.Entry<Vector2, Chunk> chunkEntry : loadedChunks.entrySet()) {
            Chunk chunk = chunkEntry.getValue();
            if (chunk == null || chunk.getBlocks() == null) {
                continue;
            }

            for (Map.Entry<Vector2, PlaceableBlock> blockEntry : chunk.getBlocks().entrySet()) {
                PlaceableBlock block = blockEntry.getValue();
                if (block != null && block.getType() == PlaceableBlock.BlockType.CHEST) {
                    ChestData cd = block.getChestData();
                    if (cd != null && cd.chestId.equals(chestId)) {
                        Vector2 position = blockEntry.getKey();
                        // Add to ServerBlockManager for faster future lookups
                        ServerGameContext.get().getServerBlockManager().getPlacedBlocks()
                            .put(position, block);
                        GameLogger.info("Found chest " + chestId + " in loaded chunk, added to ServerBlockManager");
                        return position;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Handles atomic chest operations with server-authoritative validation
     */
    private void handleChestOperation(Connection connection, NetworkProtocol.ChestOperationRequest request) {
        String username = connectedPlayers.get(connection.getID());
        if (username == null || !username.equals(request.username)) {
            sendChestOperationFailure(connection, request.chestId, "Unauthorized operation");
            return;
        }

        // Find chest position
        Vector2 chestPos = findChestPositionInPlacedBlocks(request.chestId);
        if (chestPos == null) {
            chestPos = findChestPositionInLoadedChunks(request.chestId);
        }

        if (chestPos == null) {
            sendChestOperationFailure(connection, request.chestId, "Chest not found");
            return;
        }

        // Get chest block
        PlaceableBlock chestBlock = ServerGameContext.get().getServerBlockManager().getChestBlock(request.chestId);
        if (chestBlock == null || chestBlock.getType() != PlaceableBlock.BlockType.CHEST) {
            sendChestOperationFailure(connection, request.chestId, "Invalid chest block");
            return;
        }

        // Synchronize on chest-specific lock for atomic operation
        Object lock = chestLocks.computeIfAbsent(request.chestId, id -> new Object());
        synchronized (lock) {
            ChestData chestData = chestBlock.getChestData();
            if (chestData == null) {
                chestData = new ChestData((int) chestPos.x, (int) chestPos.y);
                chestBlock.setChestData(chestData);
            }

            // Execute operation atomically
            NetworkProtocol.ChestOperationResponse response = executeChestOperation(request, chestData);
            response.username = username;
            response.chestId = request.chestId;

            if (response.success) {
                // Save chunk
                int chunkX = Math.floorDiv((int) chestPos.x, World.CHUNK_SIZE);
                int chunkY = Math.floorDiv((int) chestPos.y, World.CHUNK_SIZE);
                Chunk chunk = ServerGameContext.get().getWorldManager().loadChunk(MULTIPLAYER_WORLD_NAME, chunkX, chunkY);
                if (chunk != null) {
                    chunk.setDirty(true);
                    ServerGameContext.get().getWorldManager().saveChunk(MULTIPLAYER_WORLD_NAME, chunk);
                }

                // Broadcast to ALL clients (including requester for confirmation)
                networkServer.sendToAllTCP(response);
                GameLogger.info("Chest operation " + request.operation + " by " + username + " on chest " + request.chestId + " succeeded");
            } else {
                // Send failure only to requester
                connection.sendTCP(response);
                GameLogger.info("Chest operation " + request.operation + " by " + username + " failed: " + response.reason);
            }
        }
    }

    /**
     * Executes a single chest operation atomically
     */
    private NetworkProtocol.ChestOperationResponse executeChestOperation(
        NetworkProtocol.ChestOperationRequest request, ChestData chestData) {

        NetworkProtocol.ChestOperationResponse response = new NetworkProtocol.ChestOperationResponse();
        response.timestamp = System.currentTimeMillis();

        try {
            switch (request.operation) {
                case TAKE_ITEM:
                    // Validate slot has item
                    ItemData item = chestData.getItemAt(request.slotIndex);
                    if (item == null) {
                        response.success = false;
                        response.reason = "Slot is empty";
                        return response;
                    }

                    // Remove from chest
                    chestData.setItemAt(request.slotIndex, null);
                    response.success = true;
                    response.returnedItem = item.copy();
                    response.chestItems = new ArrayList<>(chestData.getItems());
                    break;

                case ADD_ITEM:
                    // Validate slot is empty
                    if (chestData.getItemAt(request.slotIndex) != null) {
                        response.success = false;
                        response.reason = "Slot is occupied";
                        return response;
                    }

                    // Add to chest
                    chestData.setItemAt(request.slotIndex, request.itemData);
                    response.success = true;
                    response.chestItems = new ArrayList<>(chestData.getItems());
                    break;

                case SWAP_ITEMS:
                    // Validate both slots exist
                    if (request.secondarySlotIndex < 0 || request.secondarySlotIndex >= ChestData.CHEST_SIZE) {
                        response.success = false;
                        response.reason = "Invalid secondary slot";
                        return response;
                    }

                    // Swap items
                    ItemData item1 = chestData.getItemAt(request.slotIndex);
                    ItemData item2 = chestData.getItemAt(request.secondarySlotIndex);
                    chestData.setItemAt(request.slotIndex, item2);
                    chestData.setItemAt(request.secondarySlotIndex, item1);
                    response.success = true;
                    response.chestItems = new ArrayList<>(chestData.getItems());
                    break;

                default:
                    response.success = false;
                    response.reason = "Unknown operation";
                    break;
            }
        } catch (Exception e) {
            GameLogger.error("Error executing chest operation: " + e.getMessage());
            response.success = false;
            response.reason = "Server error: " + e.getMessage();
        }

        return response;
    }

    private void sendChestOperationFailure(Connection connection, UUID chestId, String reason) {
        NetworkProtocol.ChestOperationResponse response = new NetworkProtocol.ChestOperationResponse();
        response.chestId = chestId;
        response.success = false;
        response.reason = reason;
        response.timestamp = System.currentTimeMillis();
        connection.sendTCP(response);
    }

    private void handleBuildingPlacement(Connection connection, NetworkProtocol.BuildingPlacement bp) {
        String username = connectedPlayers.get(connection.getID());
        if (username == null || !username.equals(bp.username)) {
            GameLogger.error("Unauthorized building placement attempt by " + bp.username);
            return;
        }
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

    private void sendRegistrationResponse(Connection connection, boolean success, String message, String username) {
        NetworkProtocol.RegisterResponse response = new NetworkProtocol.RegisterResponse();
        response.success = success;
        response.message = message;
        response.username = username;
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
            if (request.username == null || request.username.isEmpty() ||
                request.password == null || request.password.isEmpty()) {
                // MODIFICATION: Pass null for username as it's not relevant on failure here
                sendRegistrationResponse(connection, false, "Username and password are required.", null);
                return;
            }
            if (!isValidUsername(request.username)) {
                sendRegistrationResponse(connection, false,
                    "Username must be 3-20 characters long and contain only letters, numbers, and underscores.", null);
                return;
            }
            if (databaseManager.checkUsernameExists(request.username)) {
                sendRegistrationResponse(connection, false, "Username already exists.", null);
                return;
            }
            boolean success = databaseManager.registerPlayer(request.username, request.password);

            if (success) {
                GameLogger.info("Successfully registered new player: " + request.username);
                // MODIFICATION: Pass the username back on success
                sendRegistrationResponse(connection, true, "Registration successful!", request.username);
            } else {
                GameLogger.error("Failed to register player: " + request.username);
                sendRegistrationResponse(connection, false, "Registration failed. Please try again.", null);
            }

        } catch (Exception e) {
            GameLogger.error("Error during registration: " + e.getMessage());
            sendRegistrationResponse(connection, false, "An error occurred during registration.", null);
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

    public void sendActivePokemonToConnection(Connection connection) {
        List<NetworkProtocol.PokemonUpdate> updates = new ArrayList<>();
        for (WildPokemon pokemon : serverPokemonSpawnManager.getActivePokemon()) {
            NetworkProtocol.PokemonUpdate update = new NetworkProtocol.PokemonUpdate();
            update.uuid = pokemon.getUuid();
            update.x = pokemon.getX();
            update.y = pokemon.getY();
            update.direction = pokemon.getDirection();
            update.isMoving = pokemon.isMoving();
            update.level = pokemon.getLevel();
            update.timestamp = System.currentTimeMillis();
            updates.add(update);
        }
        if (!updates.isEmpty()) {
            NetworkProtocol.PokemonBatchUpdate batchUpdate = new NetworkProtocol.PokemonBatchUpdate();
            batchUpdate.updates = updates;
            connection.sendTCP(batchUpdate);
        }
    }

    private void handlePlayerAction(Connection connection, NetworkProtocol.PlayerAction action) {
        String username = connectedPlayers.get(connection.getID());
        if (username == null || !username.equals(action.playerId)) {
            GameLogger.error("Unauthorized PlayerAction from connection " + connection.getID());
            return;
        }
        ServerPlayer player = activePlayers.get(username);
        if (player == null) return;

        switch (action.actionType) {
            case CHOP_START:
            case PUNCH_START:
                PlaceableBlock blockTarget = ServerGameContext.get().getServerBlockManager().getBlockAt(action.tileX, action.tileY);
                if (blockTarget != null) {
                    player.setBreakingBlock(blockTarget);
                    player.setChoppingObject(null);
                    GameLogger.info("Player " + username + " started breaking block at " + action.tileX + "," + action.tileY);
                } else {
                    WorldObject objectTarget = findServerChoppableObject(action.tileX, action.tileY);
                    if (objectTarget != null) {
                        player.setChoppingObject(objectTarget);
                        player.setBreakingBlock(null);
                        GameLogger.info("Player " + username + " started chopping object " + objectTarget.getId());
                    } else {
                        GameLogger.error("Player " + username + " tried to chop but no valid target was found on server.");
                        player.setChoppingObject(null);
                        player.setBreakingBlock(null);
                        return;
                    }
                }
                networkServer.sendToAllExceptTCP(connection.getID(), action);
                break;

            case CHOP_STOP:
            case PUNCH_STOP:
                player.setChoppingObject(null);
                player.setBreakingBlock(null);
                networkServer.sendToAllExceptTCP(connection.getID(), action);
                break;

            case CHOP_COMPLETE:
                PlaceableBlock completedBlock = player.getBreakingBlock();
                WorldObject completedObject = player.getChoppingObject();
                boolean actionIsValid = false;

                if (completedBlock != null && completedBlock.getPosition().x == action.tileX && completedBlock.getPosition().y == action.tileY) {
                    serverDestroyBlock(completedBlock, player); // Pass the player
                    actionIsValid = true;
                } else if (completedObject != null) {
                    float dist = Vector2.dst(action.tileX, action.tileY, completedObject.getTileX(), completedObject.getTileY());
                    if (dist < 2.0f) {
                        serverDestroyObject(completedObject, player); // Pass the player
                        actionIsValid = true;
                    }
                }

                if (!actionIsValid) {
                    GameLogger.error("CHOP_COMPLETE received but target mismatch for " + username);
                }

                player.setChoppingObject(null);
                player.setBreakingBlock(null);
                break;
        }
    }

    private void serverDestroyBlock(PlaceableBlock block, ServerPlayer player) {
        if (block == null || player == null) return;
        Vector2 pos = block.getPosition();

        ServerGameContext.get().getServerBlockManager().removeBlock((int) pos.x, (int) pos.y);

        NetworkProtocol.BlockPlacement removalMsg = new NetworkProtocol.BlockPlacement();
        removalMsg.action = NetworkProtocol.BlockAction.REMOVE;
        removalMsg.blockTypeId = block.getType().id;
        removalMsg.tileX = (int) pos.x;
        removalMsg.tileY = (int) pos.y;
        networkServer.sendToAllTCP(removalMsg);

        String itemId = block.getType().itemId;
        if (itemId != null) {
            int dropCount = 1;

            ItemData dropData = new ItemData(itemId, dropCount);
            NetworkProtocol.ItemDrop dropMsg = new NetworkProtocol.ItemDrop();
            dropMsg.itemData = dropData;
            dropMsg.x = pos.x * TILE_SIZE + TILE_SIZE / 2f;
            dropMsg.y = pos.y * TILE_SIZE + TILE_SIZE / 2f;
            networkServer.sendToAllTCP(dropMsg);
        }

        if (block.getType() == PlaceableBlock.BlockType.CHEST) {
            ChestData chestData = block.getChestData();
            if (chestData != null && chestData.items != null) {
                for (ItemData item : chestData.items) {
                    if (item != null) {
                        NetworkProtocol.ItemDrop dropMsg = new NetworkProtocol.ItemDrop();
                        dropMsg.itemData = item;
                        dropMsg.x = pos.x * TILE_SIZE + TILE_SIZE / 2f + (MathUtils.random() * 16 - 8);
                        dropMsg.y = pos.y * TILE_SIZE + TILE_SIZE / 2f + (MathUtils.random() * 16 - 8);
                        networkServer.sendToAllTCP(dropMsg);
                    }
                }
            }
        }
    }

    private void serverDestroyObject(WorldObject object, ServerPlayer player) {
        if (object == null || player == null) return;

        Vector2 chunkPos = new Vector2((int) Math.floor(object.getPixelX() / (CHUNK_SIZE * TILE_SIZE)), (int) Math.floor(object.getPixelY() / (CHUNK_SIZE * TILE_SIZE)));
        ServerGameContext.get().getWorldObjectManager().removeObject(MULTIPLAYER_WORLD_NAME, chunkPos, object.getId());

        NetworkProtocol.WorldObjectUpdate removalMsg = new NetworkProtocol.WorldObjectUpdate();
        removalMsg.objectId = object.getId();
        removalMsg.type = NetworkProtocol.NetworkObjectUpdateType.REMOVE;
        removalMsg.data = object.getSerializableData();
        networkServer.sendToAllTCP(removalMsg);

        String dropItemId = object.getType().dropItemId;
        int dropCount = object.getType().dropItemCount;
        if (dropItemId != null && dropCount > 0) {
            if (player.hasAxe()) {
                int bonus = MathUtils.random(1, 3);
                dropCount += bonus;
                GameLogger.info("Player " + player.getUsername() + " gets axe bonus! +" + bonus + " " + dropItemId);
            }

            // Create server-authoritative item entity
            float dropX = object.getPixelX() + TILE_SIZE / 2f;
            float dropY = object.getPixelY();

            ItemEntity serverEntity = ServerGameContext.get().getItemEntityManager()
                .spawnItemEntity(new ItemData(dropItemId, dropCount), dropX, dropY);

            if (serverEntity != null) {
                // Broadcast to all clients with server's UUID
                NetworkProtocol.ItemDrop dropMsg = new NetworkProtocol.ItemDrop();
                dropMsg.itemData = new ItemData(dropItemId, dropCount);
                dropMsg.x = dropX;
                dropMsg.y = dropY;
                dropMsg.username = player.getUsername();
                dropMsg.entityId = serverEntity.getEntityId();
                dropMsg.timestamp = System.currentTimeMillis();
                networkServer.sendToAllTCP(dropMsg);

                GameLogger.info("Spawned item drop from object break: " + dropItemId + " x" + dropCount +
                               " at (" + dropX + "," + dropY + ") with UUID " + serverEntity.getEntityId());
            } else {
                GameLogger.error("Failed to spawn item entity on server for object break");
            }
        }
    }


    private void handleWorldObjectUpdate(Connection connection, NetworkProtocol.WorldObjectUpdate update) {
        String username = connectedPlayers.get(connection.getID());
        if (username == null) {
            GameLogger.error("WorldObjectUpdate received from unauthenticated connection.");
            return;
        }

        switch (update.type) {
            case REMOVE:
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
                float x = tileX * World.TILE_SIZE;
                float y = tileY * World.TILE_SIZE;
                int chunkX = (int) Math.floor(x / (World.TILE_SIZE * Chunk.CHUNK_SIZE));
                int chunkY = (int) Math.floor(y / (World.TILE_SIZE * Chunk.CHUNK_SIZE));
                Vector2 chunkPos = new Vector2(chunkX, chunkY);
                ServerGameContext.get().getWorldObjectManager().removeObject(MULTIPLAYER_WORLD_NAME, chunkPos, update.objectId);
                GameLogger.info("Removed world object " + update.objectId + " from chunk " + chunkPos);
                Chunk chunk = ServerGameContext.get().getWorldManager().loadChunk(MULTIPLAYER_WORLD_NAME, chunkX, chunkY);
                if (chunk != null) {
                    chunk.setDirty(true);
                    ServerGameContext.get().getWorldManager().saveChunk(MULTIPLAYER_WORLD_NAME, chunk);
                }
                networkServer.sendToAllTCP(update);
                break;

            case ADD:
                break;

            case UPDATE:
                break;

            default:
                GameLogger.error("Unknown world object update type: " + update.type);
                break;
        }
    }

    public Server getNetworkServer() {
        return networkServer;
    }


    private boolean isChoppable(WorldObject.ObjectType type) {
        return type == WorldObject.ObjectType.TREE_0 ||
            type == WorldObject.ObjectType.TREE_1 ||
            type == WorldObject.ObjectType.SNOW_TREE ||
            type == WorldObject.ObjectType.HAUNTED_TREE ||
            type == WorldObject.ObjectType.RAIN_TREE ||
            type == WorldObject.ObjectType.APRICORN_TREE ||
            type == WorldObject.ObjectType.RUINS_TREE || type == WorldObject.ObjectType.CACTUS || type == WorldObject.ObjectType.DEAD_TREE ||
            type == WorldObject.ObjectType.CHERRY_TREE;
    }

    private void handleBlockPlacement(Connection connection, NetworkProtocol.BlockPlacement placement) {
        String username = connectedPlayers.get(connection.getID());
        if (username == null || !username.equals(placement.username)) {
            GameLogger.error("Unauthorized block placement attempt by " + placement.username);
            return;
        }

        int chunkX = Math.floorDiv(placement.tileX, World.CHUNK_SIZE);
        int chunkY = Math.floorDiv(placement.tileY, World.CHUNK_SIZE);

        switch (placement.action) {
            case PLACE:
                PlaceableBlock.BlockType type = PlaceableBlock.BlockType.fromItemId(placement.blockTypeId);
                boolean placed = ServerGameContext.get().getServerBlockManager().placeBlock(type, placement.tileX, placement.tileY, false);
                if (placed) {
                    Chunk chunk = ServerGameContext.get().getWorldManager().loadChunk("multiplayer_world", chunkX, chunkY);
                    if (chunk != null) {
                        Vector2 blockPos = new Vector2(placement.tileX, placement.tileY);
                        PlaceableBlock block = ServerGameContext.get().getServerBlockManager().getBlockAt(blockPos);
                        if (block != null) {
                            chunk.getBlocks().put(blockPos, block);
                            chunk.setDirty(true);
                            ServerGameContext.get().getWorldManager().saveChunk("multiplayer_world", chunk);
                        }
                    }
                    // Invalidate chunk cache since the chunk has been modified
                    invalidateChunkCache(chunkX, chunkY);
                    ServerGameContext.get().getEventManager().fireEvent(
                        new BlockPlaceEvent(placement.username, placement.tileX, placement.tileY, placement.blockTypeId)
                    );
                    networkServer.sendToAllExceptTCP(connection.getID(), placement);
                } else {
                    GameLogger.error("Failed to place block at (" + placement.tileX + ", " + placement.tileY + ")");
                }
                break;
            case REMOVE:
                ServerGameContext.get().getServerBlockManager().removeBlock(placement.tileX, placement.tileY);
                Chunk chunk = ServerGameContext.get().getWorldManager().loadChunk("multiplayer_world", chunkX, chunkY);
                if (chunk != null) {
                    chunk.getBlocks().remove(new Vector2(placement.tileX, placement.tileY));
                    chunk.setDirty(true);
                    ServerGameContext.get().getWorldManager().saveChunk("multiplayer_world", chunk);
                }
                // Invalidate chunk cache since the chunk has been modified
                invalidateChunkCache(chunkX, chunkY);
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
