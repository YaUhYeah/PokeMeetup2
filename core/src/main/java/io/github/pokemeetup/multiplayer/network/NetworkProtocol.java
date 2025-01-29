package io.github.pokemeetup.multiplayer.network;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryonet.FrameworkMessage;
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.multiplayer.server.entity.CreatureEntity;
import io.github.pokemeetup.multiplayer.server.entity.Entity;
import io.github.pokemeetup.multiplayer.server.entity.EntityType;
import io.github.pokemeetup.multiplayer.server.entity.PokeballEntity;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.system.data.BlockSaveData;
import io.github.pokemeetup.system.data.ItemData;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.data.PokemonData;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.utils.ChunkPos;
import io.github.pokemeetup.utils.UUIDSerializer;

import java.io.Serializable;
import java.util.*;

public class NetworkProtocol {
    public static void registerClasses(Kryo kryo) {
        // Basic and commonly used classes
        kryo.register(UUID.class, new UUIDSerializer());
        kryo.register(Vector2.class);
        kryo.register(ArrayList.class);
        kryo.register(CompressedChunkData.class);
        kryo.register(List.class);
        kryo.register(ChunkPos.class);
        kryo.register(int[][].class); // For the tileData 2D array
        kryo.register(HashMap.class);
        kryo.register(Map.class);
        kryo.register(java.util.concurrent.ConcurrentHashMap.class);
        kryo.register(PokemonData.Stats.class);
        kryo.register(PlayerAction.class);
        kryo.register(BlockPlacement.class);
        kryo.register(BlockAction.class);

        kryo.register(WorldData.class);
        kryo.register(WorldObjectData.class);
        kryo.register(io.github.pokemeetup.system.data.WorldData.class);
        kryo.register(io.github.pokemeetup.system.data.WorldData.WorldObjectData.class);
        kryo.register(io.github.pokemeetup.system.data.WorldData.WorldConfig.class);

        // Enums
        kryo.register(NetworkObjectUpdateType.class);
        kryo.register(NetworkedWorldObject.ObjectType.class);
        kryo.register(ChatType.class);
        kryo.register(ForceDisconnect.class);

        // Request and response classes
        kryo.register(LoginRequest.class);
        kryo.register(LoginResponse.class);
        kryo.register(RegisterRequest.class);
        kryo.register(RegisterResponse.class);
        kryo.register(ItemData.class);
        kryo.register(ItemData[].class);
        kryo.register(UUID.class);
        kryo.register(InventoryUpdate.class);
        kryo.register(ChunkData.class);
        kryo.register(BiomeType.class);
        // Game state and network classes
        kryo.register(PlayerPosition.class);
        kryo.register(PlayerUpdate.class);
        kryo.register(InventoryUpdate.class);
        kryo.register(PlayerJoined.class);
        kryo.register(PlayerLeft.class);
        kryo.register(WorldObjectUpdate.class);
        kryo.register(BlockSaveData.BlockData.class);
        // Register PlaceableBlock.BlockType
        kryo.register(PlaceableBlock.BlockType.class);
        kryo.register(Keepalive.class);
        // Complex data models and entities
        kryo.register(WorldState.class);
        kryo.register(PlayerState.class);
        kryo.register(ChunkUpdate.class);
        kryo.register(ChunkRequest.class);
        kryo.register(EntityUpdate.class);
        kryo.register(Entity.class);
        kryo.register(EntityType.class);
        registerPokemonClasses(kryo);
        // Networked entities
        kryo.register(NetworkedWorldObject.class);
        kryo.register(NetworkedTree.class);
        kryo.register(WorldStateUpdate.class);
        kryo.register(NetworkedPokeball.class);
        kryo.register(ConnectionResponse.class);
        kryo.register(ConnectionRequest.class);
        kryo.register(PokemonData.class);
        kryo.register(ConnectionStatus.class);
        kryo.register(Logout.class);
        kryo.register(UsernameCheckRequest.class);
        kryo.register(UsernameCheckResponse.class);
        kryo.register(BlockPlacement.class);
        kryo
            .register(io.github.pokemeetup.system.data.WorldData.class);
        kryo.register(PlayerData.class);
        kryo.register(PlayerData[].class);
        // Miscellaneous
        kryo.register(io.github.pokemeetup.system.data.WorldData.WorldConfig.class);

        kryo.register(TeamCreate.class);

        kryo.register(ServerShutdown.class);
        kryo.register(TeleportRequest.class);
        kryo.register(TeleportResponse.class);
        kryo.register(World.WorldObjectData.class);
        kryo.register(World.ChunkData.class);
        kryo.register(ChatMessage.class);
        kryo.register(TeamInvite.class);
        kryo.register(TeamHQUpdate.class);
        kryo.register(ChunkDataFragment.class);
        kryo.register(ChunkDataComplete.class);
        kryo.register(WorldInitData.class);
        kryo.register(ServerInfoRequest.class);
        kryo.register(ServerInfoResponse.class);
        kryo.register(SavePlayerDataRequest.class);
        kryo.register(SavePlayerDataResponse.class);
        kryo.register(GetPlayerDataRequest.class);
        kryo.register(GetPlayerDataResponse.class);
        kryo.register(FrameworkMessage.KeepAlive.class);
        kryo.register(FrameworkMessage.Ping.class);
        kryo.register(FrameworkMessage.RegisterTCP.class);
        kryo.register(FrameworkMessage.RegisterUDP.class);
        kryo.register(ServerInfo.class);
        // Additional Entity subclasses
        kryo.register(WorldObject.class);
        kryo.register(WorldObject.ObjectType.class);
        kryo.register(CreatureEntity.class);
        kryo.register(PokeballEntity.class);
        kryo.register(ForceDisconnect.class);
        kryo.register(ForceLogout.class);
        kryo.register(Object.class);
        kryo.register(ReliableUpdate.class);
        kryo.register(ClientMessage.class);
        kryo.register(ServerResponse.class);
        kryo.register(ConnectionValidation.class);
        kryo.register(UUID.class, new com.esotericsoftware.kryo.Serializer<UUID>() {

            @Override
            public void write(Kryo kryo, Output output, UUID uuid) {
                output.writeLong(uuid.getMostSignificantBits());
                output.writeLong(uuid.getLeastSignificantBits());
            }

            @Override
            public UUID read(Kryo kryo, Input input, Class<UUID> type) {
                return new UUID(input.readLong(), input.readLong());
            }
        });
        kryo.setReferences(false);  // Disable object references
        kryo.setRegistrationRequired(false);  // Require class registration
    }

    public static void registerPokemonClasses(Kryo kryo) {
        kryo.register(PokemonUpdate.class);
        kryo.register(PokemonSpawn.class);
        kryo.register(PokemonDespawn.class);
        kryo.register(PokemonSpawnRequest.class);
        kryo.register(PartyUpdate.class);
        kryo.register(WildPokemonSpawn.class);
        kryo.register(WildPokemonDespawn.class);
        kryo.register(PokemonData.class);
        kryo.register(Pokemon.Stats.class);
        kryo.register(Pokemon.PokemonType.class);
        kryo.register(ArrayList.class);
        kryo.register(int[].class);
    }

    public enum ActionType {
        CHOP_START,
        CHOP_STOP,
        CHEST_OPEN,
        CHEST_CLOSE,
        PUNCH_START,
        PUNCH_STOP,
        PICKUP_ITEM
    }

    public enum BlockAction {
        PLACE,
        REMOVE
    }

    public enum ChatType {
        NORMAL,
        SYSTEM,
        WHISPER,
        TEAM
    }

    public enum NetworkObjectUpdateType {
        ADD,
        UPDATE,
        REMOVE

    }

    public static class CompressedChunkData {
        public int chunkX;
        public int chunkY;
        public byte[] data;
        public BiomeType biomeType;
        public long generationSeed;  // Add this field
    }
    public static class WorldObjectData {
        public float x;
        public float y;
        public WorldObject.ObjectType type;
    }

    // In NetworkProtocol.java, add:
    public static class WorldInitData {
        public long seed;
        public double worldTimeInMinutes;
        public float dayLength;
    }

    public static class ChunkRequest {
        public int chunkX;
        public int chunkY;
        public long timestamp;
    }


    public static class ChunkData {
        public int chunkX;
        public int chunkY;
        public BiomeType biomeType;
        public int[][] tileData;
        public List<Map<String, Object>> worldObjects;
        public List<BlockSaveData.BlockData> blockData;
        public long generationSeed; // Added field
        public long timestamp;

        public void write(Json json) {
            json.writeObjectStart();
            json.writeValue("chunkX", chunkX);
            json.writeValue("chunkY", chunkY);
            json.writeValue("biomeType", biomeType.name());
            json.writeValue("tileData", tileData);
            json.writeValue("worldObjects", worldObjects);
            json.writeValue("blockData", blockData);
            json.writeValue("generationSeed", generationSeed); // Add to serialization
            json.writeValue("timestamp", timestamp);
            json.writeObjectEnd();
        }

        public void read(JsonValue jsonData, Json json) {
            chunkX = jsonData.getInt("chunkX");
            chunkY = jsonData.getInt("chunkY");
            biomeType = BiomeType.valueOf(jsonData.getString("biomeType"));
            tileData = json.readValue(int[][].class, jsonData.get("tileData"));
            worldObjects = json.readValue(ArrayList.class, Map.class, jsonData.get("worldObjects"));
            blockData = json.readValue(ArrayList.class, BlockSaveData.BlockData.class, jsonData.get("blockData"));
            generationSeed = jsonData.getLong("generationSeed", 0); // Add to deserialization
            timestamp = jsonData.getLong("timestamp", System.currentTimeMillis());
        }
    }

    public static class ChunkDataFragment {
        public int chunkX;
        public int chunkY;
        public int startX;
        public int startY;
        public int fragmentSize;
        public int[][] tileData;
        public BiomeType biomeType;
        public int fragmentIndex;
        public int totalFragments;
    }

    public static class ChunkDataComplete {
        public int chunkX;
        public int chunkY;
    }

    public static
    class LogoutResponse {
        public boolean success;
        public String message;
    }

    public static class ConnectionValidation {
        public String username;
        public long timestamp;
        public String sessionId; // Add a unique session ID for each connection
    }

    public static class ClientMessage {
        public static final int TYPE_LOGOUT = 1;

        public int type;
        public String username;
        public long timestamp;
        public Map<String, Object> data;
    }

    public static class ServerResponse {
        public boolean success;
        public String message;
        public long timestamp;
        public Map<String, Object> data;
    }

    public static class Logout {
        public String username;
        public long timestamp;
    }

    public static class ForceLogout {
        public String reason;
        public boolean serverShutdown;
    }

    public static class ServerInfo {
        public String name;
        public String motd; // Message of the day
        public String iconBase64; // Base64 encoded server icon
        public int playerCount;
        public int maxPlayers;
        public long ping;
        public String version;
    }

    public static class ServerInfoRequest {
        public long timestamp;
    }

    public static class ServerInfoResponse {
        public ServerInfo serverInfo;
        public long timestamp;
    }

    public static class ConnectionResponse {
        public boolean success;
        public String message;
        public String username;
        public String token;
    }

    public static class ConnectionRequest {
        public String version;
        public long timestamp;
    }

    public static class ConnectionStatus {
        public int connectionId;
        public String status;
        public long timestamp;
    }

    public static class PokemonUpdate implements Serializable {
        public UUID uuid;
        public float x;
        public float y;
        public String direction;
        public boolean isMoving;
        public boolean isAttacking;
        public PokemonData data;
        public long timestamp;
        public String status;  // e.g., "NORMAL", "FAINTED", "SLEEPING"
        public Map<String, Object> extraData = new HashMap<>();  // For extensibility
        public int level;
        public float currentHp;
    }

    public static class PokemonSpawnRequest implements Serializable {
        public UUID uuid;
        public long timestamp = System.currentTimeMillis();
        public String requestingPlayer; // Username of requesting client
    }

    public static class ChunkUpdate {
        public Vector2 position;
        public World.ChunkData chunkData;
        public List<World.WorldObjectData> objects;
    }

    public static class LoginResponse {
        public boolean success;
        public double worldTimeInMinutes;
        public float dayLength;
        public long seed;               // World seed
        public String message;
        public String username;
        public int x;
        public int y;
        public String worldName;
        public long timestamp;
        public long worldSeed;
        public PlayerData playerData;
        public io.github.pokemeetup.system.data.WorldData worldData;  // Add serializable world data
    }

    public static class WorldData implements Serializable {
        // Add fields that need to be synced to clients
        public long seed;
        public String name;
        public Map<String, Object> worldProperties;
        // Add other necessary world data
    }

    public static class UsernameCheckRequest {
        public String username;
        public long timestamp;
    }

    public static class UsernameCheckResponse {
        public String username;
        public boolean available;
        public String message;
    }

    public static class ServerShutdown {
        public String reason;
    }

    public static class PlayerJoined {
        public String username;
        public float x;
        public float y;
        public String direction = "down";
        public boolean isMoving = false;
        public ItemData[] inventoryItems;
        public long timestamp;
        public ItemData[] hotbarItems;
    }

    public static class PlayerLeft {
        public String username;
        public long timestamp;
    }

    public static class ReliableUpdate {
        public int sequence;
        public long timestamp;
        public NetworkProtocol.PlayerUpdate playerUpdate;
    }

    public static class ForceDisconnect {
        public String reason;
    }

    public static class Keepalive {
        public long timestamp;
    }

    public static class PlayerUpdate {
        public String username;
        public float x;
        public float y;
        public String direction;
        public List<PokemonData> partyPokemon;
        public boolean isMoving;
        // Add velocity for smoother movement
        public float velocityX;
        public float velocityY;
        public boolean wantsToRun;
        public ItemData[] inventoryItems;
        public ItemData[] hotbarItems;
        public long timestamp = System.currentTimeMillis();
    }

    // Add validation methods to request classes
    public static class LoginRequest {
        public String username;
        public String password;
        public long timestamp;
        public io.github.pokemeetup.system.data.WorldData worldData;

        @Override
        public String toString() {
            return "LoginRequest{" +
                "username='" + username + '\'' +
                ", hasPassword=" + (password != null && !password.isEmpty()) +
                ", timestamp=" + timestamp +
                '}';
        }
    }

    public static class RegisterRequest {
        public String username;
        public String password;
    }

    public static class InventoryUpdate {
        public String username;
        public ItemData[] inventoryItems;
    }

    public static class PlayerPosition {
        public HashMap<String, PlayerUpdate> players = new HashMap<>();
    }
    // Update the PlayerUpdate class in NetworkProtocol.java

    public static class ChatMessage {
        public String sender;
        public String content;
        public long timestamp;
        public ChatType type;
        public String recipient; // Add this field for private messages
    }

    public static class WeatherUpdate implements Serializable {
        public String weatherType;  // e.g., "RAIN", "CLEAR", "SNOW"
        public float intensity;     // 0.0 to 1.0
        public long duration;       // in milliseconds
        public long timestamp;
    }

    public static class SavePlayerDataRequest {
        public UUID uuid;
        public PlayerData playerData;
        public long timestamp;
    }

    public static class SavePlayerDataResponse {
        public UUID uuid;
        public boolean success;
        public String message;
        public long timestamp;
    }

    public static class GetPlayerDataRequest {
        public UUID uuid;
        public long timestamp;
    }

    public static class GetPlayerDataResponse {
        public UUID uuid;
        public PlayerData playerData;
        public boolean success;
        public String message;
        public long timestamp;
    }

    public static class TimeSync implements Serializable {
        public double worldTimeInMinutes;
        public long playedTime;
        public float dayLength;
        public long timestamp;
    }

    public static class WorldStateUpdate implements Serializable {
        public io.github.pokemeetup.system.data.WorldData worldData;
        public long timestamp;
        public Map<String, Object> extraData = new HashMap<>();  // For additional sync data
    }

    public static class TeamCreate {
        public String name;
        public String tag;
        public String leader;
        public long timestamp = System.currentTimeMillis();
    }

    public static class TeamInvite {
        public String teamName;
        public String inviter;
        public String invitee;
        public long timestamp = System.currentTimeMillis();
    }

    public static class TeamHQUpdate {
        public String teamName;
        public int x;
        public int y;
        public long timestamp = System.currentTimeMillis();
    }

    public static class PokemonSpawn {
        public UUID uuid;
        public String name;
        public int level;
        public float x;
        public float y;
        public PokemonData data;
        public long timestamp;
    }

    public static class PokemonDespawn {
        public UUID uuid;
        public long timestamp;
    }

    public static class PartyUpdate {
        public String username;
        public List<PokemonData> party;
        public long timestamp;
    }

    // Add to NetworkProtocol.java
    public static class TeleportRequest {
        public TeleportType type;
        public String player;
        public String target;  // For player teleports
        public String homeName;  // For home teleports
        public long timestamp;

        public enum TeleportType {
            SPAWN, HOME, PLAYER
        }
    }

    public static class WildPokemonSpawn {
        public UUID uuid;
        public float x;
        public float y;
        public PokemonData data;
        public long timestamp;
    }

    public static class WildPokemonDespawn {
        public UUID uuid;
        public long timestamp;
    }

    public static class RegisterResponse {
        public boolean success;
        public String message;
        public int x;
        public int y;
        public long worldSeed;  // Add this field
        public String username; // Added username field
    }

    public static class WorldObjectUpdate {
        public String objectId;
        public NetworkObjectUpdateType type;
        public float x;
        public float y;
        public String textureName;
        public NetworkedWorldObject.ObjectType objectType;
        public Map<String, Object> data;
    }

    // World State Classes
    public static class WorldState {
        public long timestamp;
        public List<EntityUpdate> entities;
        public List<PlayerState> players;
    }

    public static class PlayerState {
        public String username;
        public float x;
        public float y;
        public String direction;
        public boolean isMoving;
        public List<ItemData> inventory;
    }

    public static class EntityUpdate {
        public UUID entityId;
        public float x;
        public float y;
        public Vector2 velocity;
        public String entityType;
    }

    public static class PlayerAction {
        public String playerId;
        public ActionType actionType;
        public Vector2 targetPosition;
        public String objectId;
        public int tileX;
        public int tileY;
        public String direction;
    }

    public static class BlockPlacement {
        public String username;
        public String blockTypeId;
        public int tileX;
        public int tileY;
        public BlockAction action; // PLACE or REMOVE
    }

    public static class TeleportResponse {
        public String from;
        public String to;
        public boolean accepted;
        public long timestamp;
    }
}
