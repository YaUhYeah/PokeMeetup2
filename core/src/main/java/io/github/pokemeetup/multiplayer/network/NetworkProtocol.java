package io.github.pokemeetup.multiplayer.network;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryonet.FrameworkMessage;
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.managers.BiomeManager;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.system.data.BlockSaveData;
import io.github.pokemeetup.system.data.ItemData;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.data.PokemonData;
import io.github.pokemeetup.system.gameplay.inventory.ItemEntity;
import io.github.pokemeetup.system.gameplay.overworld.WeatherSystem;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;

import java.io.Serializable;
import java.util.*;

public class NetworkProtocol {// In NetworkProtocol.java (or a new file in the same package)

    public static void registerClasses(Kryo kryo) {
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
        kryo.register(Vector2.class);
        kryo.register(PlayerInfo.class);
        kryo.register(PingRequest.class);
        kryo.register(PingResponse.class);
        kryo.register(ArrayList.class);
        kryo.register(BuildingPlacement.class);
        kryo.register(CompressedChunkData.class);
        kryo.register(List.class);
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
        kryo.register(NetworkObjectUpdateType.class);
        kryo.register(ChatType.class);
        kryo.register(ForceDisconnect.class);
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
        kryo.register(PlayerInfoUpdate.class);
        kryo.register(PlayerPosition.class);
        kryo.register(PlayerUpdate.class);
        kryo.register(InventoryUpdate.class);
        kryo.register(PlayerJoined.class);
        kryo.register(PlayerLeft.class);
        kryo.register(WorldObjectUpdate.class);
        kryo.register(BlockSaveData.BlockData.class);
        kryo.register(PlaceableBlock.BlockType.class);
        kryo.register(Keepalive.class);
        kryo.register(WorldState.class);
        kryo.register(PlayerState.class);
        kryo.register(ChunkUpdate.class);
        kryo.register(ChunkRequest.class);
        kryo.register(EntityUpdate.class);
        registerPokemonClasses(kryo);
        kryo.register(WorldStateUpdate.class);
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
        kryo.register(io.github.pokemeetup.system.data.WorldData.WorldConfig.class);

        kryo.register(TeamCreate.class);
        kryo.register(ItemPickup.class);
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
        kryo.register(ChestUpdate.class);
        kryo.register(SavePlayerDataResponse.class);
        kryo.register(GetPlayerDataRequest.class);
        kryo.register(GetPlayerDataResponse.class);
        kryo.register(FrameworkMessage.KeepAlive.class);
        kryo.register(FrameworkMessage.Ping.class);
        kryo.register(FrameworkMessage.RegisterTCP.class);
        kryo.register(FrameworkMessage.RegisterUDP.class);
        kryo.register(ServerInfo.class);
        kryo.register(WorldObject.class);
        kryo.register(PlayerList.class);
        kryo.register(WorldObject.ObjectType.class);
        kryo.register(ItemEntity.class);
        kryo.register(ItemEntityRemove.class);
        kryo.register(ForceDisconnect.class);
        kryo.register(ForceLogout.class);
        kryo.register(Object.class);
        kryo.register(ReliableUpdate.class);
        kryo.register(ClientMessage.class);
        kryo.register(ServerResponse.class);
        kryo.register(ItemDrop.class);
        kryo.register(ConnectionValidation.class);

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
        kryo.register(PokemonBatchUpdate.class);
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
        PICKUP_ITEM,
        CHOP_COMPLETE,
        PUNCH_COMPLETE
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

    public static class BuildingPlacement implements Serializable {
        public String username;
        public int startX;
        public int startY;
        public int width;
        public int height;
        public String[][] blockTypeIds;  // For each block position, the block type id (e.g. "wooden_planks")
        public boolean[][] flippedFlags; // For each block position, whether it is flipped
        public long timestamp;
    }

    public static class ChunkData {
        public int chunkX;
        public int chunkY;
        public BiomeType primaryBiomeType;
        public BiomeType secondaryBiomeType;   // Can be null
        public float biomeTransitionFactor;    // 0.0 to 1.0
        public int[][] tileData;
        public BiomeManager.BiomeData biomeData;
        public List<BlockSaveData.BlockData> blockData;
        public List<HashMap<String, Object>> worldObjects = new ArrayList<>();

        public long generationSeed;
        public long timestamp;
    }

    public static class CompressedChunkData {
        public int chunkX;
        public int chunkY;
        public BiomeType primaryBiomeType;
        public BiomeType secondaryBiomeType;
        public float biomeTransitionFactor;
        public byte[] data;
        public int originalLength;
        public long generationSeed;
    }

    public static class ItemPickup {
        public UUID entityId;
        public String username;
        public long timestamp;
    }

    public static class ItemEntitySpawn {
        public UUID entityId;
        public ItemData itemData;
        public float x;
        public float y;
    }

    public static class ItemEntityRemove {
        public UUID entityId;
    }

    public static class WorldObjectData {
        public float x;
        public float y;
        public WorldObject.ObjectType type;
    }
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

    public static class ItemDrop {
        public ItemData itemData;
        public float x;
        public float y;
        public String username;
        public long timestamp;
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
        public long seed;
        public String name;
        public Map<String, Object> worldProperties;
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
        public String characterType; // [NEW] Add character type
        public String direction;
        public List<PokemonData> partyPokemon;
        public boolean isMoving;
        public boolean wantsToRun;
        public ItemData[] inventoryItems;
        public ItemData[] hotbarItems;
        public long timestamp = System.currentTimeMillis();
    }
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

    public static class ChestUpdate implements Serializable {
        public String username;       // The player making the update
        public UUID chestId;           // (Assuming your ChestData has an integer id)
        public List<ItemData> items;  // The new list of items in the chest
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


    public static class WorldStateUpdate {
        public long seed;                    // world generation seed
        public double worldTimeInMinutes;    // current world time in minutes
        public float dayLength;              // length of a day in minutes (or seconds, as you require)
        public WeatherSystem.WeatherType currentWeather;
        public float intensity;
        public float accumulation;

        public long timestamp;
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
        public Map<String, Object> data;
    }

    public static class PokemonBatchUpdate {
        public List<PokemonUpdate> updates;
    }
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

    public static class PingRequest {
        public long timestamp;  // when the ping was sent
    }

    public static class PingResponse {
        public long timestamp;  // echoed timestamp
    }
    public static class PlayerInfo {
        public String username;
        public int ping; // in milliseconds
    }
    public static class PlayerInfoUpdate {
        public String username;
        public int ping;
    }
    public static class PlayerList {
        public List<PlayerInfo> players;
    }
}
