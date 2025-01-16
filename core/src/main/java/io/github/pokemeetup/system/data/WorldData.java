package io.github.pokemeetup.system.data;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Json;
import io.github.pokemeetup.multiplayer.server.ServerStorageSystem;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.system.gameplay.overworld.multiworld.WorldManager;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.storage.JsonConfig;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WorldData {

    private final Object timeLock = new Object();
    private final Object saveLock = new Object();
    private final Map<UUID, WildPokemon> wildPokemonMap = new ConcurrentHashMap<>();
    private final Map<Vector2, List<WorldObject>> chunkObjects;
    private double worldTimeInMinutes = 480.0;
    private long playedTime = 0L;
    private float dayLength = 10.0f;
    private PokemonData pokemonData;
    private String name;
    private Set<UUID> playerUUIDs;
    private long lastPlayed;
    private WorldConfig config;
    private boolean isDirty;
    private BlockSaveData blockData;
    private Map<Vector2, Chunk> chunks;
    private HashMap<String, PlayerData> players;
    private String username;
    private Map<Vector2, List<WorldObject>> dynamicObjects;
    private boolean commandsAllowed = false;

    public WorldData(String name) {
        this();
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("World name cannot be null or empty");
        }
        this.name = name.trim();
    }

    public WorldData() {
        this.playerUUIDs = new HashSet<>();
        this.players = new HashMap<>();
        this.pokemonData = new PokemonData();
        this.lastPlayed = System.currentTimeMillis();
        this.chunks = new HashMap<>();
        this.chunkObjects = new HashMap<>();
        this.commandsAllowed = false;
    }

    public WorldData(String name, long lastPlayed, WorldConfig config) {
        this();
        this.name = name;
        this.pokemonData = new PokemonData();
        this.lastPlayed = lastPlayed;
        this.config = config;
        this.commandsAllowed = false;
    }

    public WorldData(String name, long lastPlayed, WorldConfig config, String username) {
        this(name, lastPlayed, config);
        this.username = username;
        this.commandsAllowed = false;
    }


    public static WorldData fromJson(String jsonStr) {
        try {
            Json json = JsonConfig.getInstance();
            return json.fromJson(WorldData.class, jsonStr);
        } catch (Exception e) {
            GameLogger.error("Failed to parse WorldData from JSON: " + e.getMessage());
            return null;
        }
    }

    public WorldData copy() {
        WorldData copy = new WorldData(this.name);
        synchronized (saveLock) {
            // Copy core settings
            copy.commandsAllowed = this.commandsAllowed;
            copy.worldTimeInMinutes = this.worldTimeInMinutes;
            copy.playedTime = this.playedTime;
            copy.dayLength = this.dayLength;
            copy.lastPlayed = this.lastPlayed;
            copy.isDirty = this.isDirty;
            copy.username = this.username;

            // Copy config
            if (this.config != null) {
                WorldConfig configCopy = new WorldConfig(this.config.getSeed());
                configCopy.setTreeSpawnRate(this.config.getTreeSpawnRate());
                configCopy.setPokemonSpawnRate(this.config.getPokemonSpawnRate());
                configCopy.setTileSpawnX(this.config.getTileSpawnX());
                configCopy.setTileSpawnY(this.config.getTileSpawnY());
                copy.config = configCopy;
            }
            if (this.players != null) {
                HashMap<String, PlayerData> playersCopy = new HashMap<>();
                for (Map.Entry<String, PlayerData> entry : this.players.entrySet()) {
                    playersCopy.put(entry.getKey(), entry.getValue().copy());
                }
                copy.setPlayers(playersCopy);
            } else {
                copy.setPlayers(new HashMap<>());
            }
            // Copy player UUIDs
            if (this.playerUUIDs != null) {
                copy.playerUUIDs = new HashSet<>(this.playerUUIDs);
            }

            // Copy Pokemon data
            if (this.pokemonData != null) {
                copy.pokemonData = this.pokemonData.copy();
            }

            // Copy block data if exists
            if (this.blockData != null) {
                copy.blockData = this.blockData.copy();
            }

            // Copy chunk references (chunks themselves are managed separately)
            if (this.chunks != null) {
                copy.chunks = new HashMap<>(this.chunks);
            }

            // Deep copy chunk objects
            if (this.chunkObjects != null) {
                copy.dynamicObjects = new HashMap<>();
                for (Map.Entry<Vector2, List<WorldObject>> entry : this.chunkObjects.entrySet()) {
                    List<WorldObject> objectsCopy = new ArrayList<>();
                    for (WorldObject obj : entry.getValue()) {
                        objectsCopy.add(obj.copy());
                    }
                    copy.dynamicObjects.put(entry.getKey().cpy(), objectsCopy);
                }
            }

            GameLogger.info("Created copy of world '" + this.name +
                "' - Commands enabled: " + copy.commandsAllowed);

            return copy;
        }
    }

    public Map<Vector2, Chunk> getChunks() {
        return chunks;
    }

    public void setChunks(Map<Vector2, Chunk> chunks) {
        this.chunks = chunks;
    }

    public Map<Vector2, List<WorldObject>> getChunkObjects() {
        return chunkObjects;
    }

    public double getWorldTimeInMinutes() {
        synchronized (timeLock) {
            return worldTimeInMinutes;
        }
    }

    public void setWorldTimeInMinutes(double time) {
        synchronized (timeLock) {
            this.worldTimeInMinutes = time;
            GameLogger.info("Set world time to: " + time);
        }
    }

    public long getPlayedTime() {
        synchronized (timeLock) {
            return playedTime;
        }
    }

    public void setPlayedTime(long time) {
        synchronized (timeLock) {
            this.playedTime = time;
            GameLogger.info("Set played time to: " + time);
        }
    }

    public void validateAndRepairWorld() {
        if (this.players == null) {
            this.players = new HashMap<>();
            isDirty = true;
        }
        if (this.pokemonData == null) {
            this.pokemonData = new PokemonData();
            setDirty(true);
        }

        // Validate Pokemon in player data
        if (players != null) {
            for (PlayerData player : players.values()) {
                if (player.getPartyPokemon() != null) {
                    List<PokemonData> validPokemon = new ArrayList<>();
                    for (PokemonData pokemon : player.getPartyPokemon()) {
                        if (pokemon != null) {
                            validPokemon.add(pokemon);
                            setDirty(true);
                        }
                    }
                    if (!validPokemon.isEmpty()) {
                        player.setPartyPokemon(validPokemon);
                    }
                }
            }
        }
    }

    public void validateAndRepair() {
        synchronized (timeLock) {
            // Validate time values
            if (worldTimeInMinutes < 0 || worldTimeInMinutes >= 24 * 60) {
                GameLogger.error("Repairing invalid world time: " + worldTimeInMinutes);
                worldTimeInMinutes = 480.0; // 8:00 AM
            }

            if (dayLength <= 0) {
                GameLogger.error("Repairing invalid day length: " + dayLength);
                dayLength = 10.0f;
            }

            if (playedTime < 0) {
                GameLogger.error("Repairing invalid played time: " + playedTime);
                playedTime = 0;
            }
        }

        if (blockData == null) {
            GameLogger.error("blockData is null during validation. Blocks may not be loaded correctly.");
            // Decide whether to initialize a new BlockSaveData or handle it differently
            // blockData = new BlockSaveData(); // Commented out to prevent overwriting valid data
        }

        // Validate players data
        if (players != null) {
            for (Map.Entry<String, PlayerData> entry : players.entrySet()) {
                PlayerData playerData = entry.getValue();
                if (playerData.getInventoryItems() == null) {
                    playerData.setInventoryItems(new ArrayList<>());
                }

                // Validate each inventory item
                for (int i = 0; i < playerData.getInventoryItems().size(); i++) {
                    ItemData item = playerData.getInventoryItems().get(i);
                    if (item != null && item.getUuid() == null) {
                        item.setUuid(UUID.randomUUID());
                        GameLogger.info("Generated new UUID for item: " + item.getItemId());
                    }
                }
            }
        }
    }

    public void save() {
        synchronized (saveLock) {
            try {
                GameLogger.info("Saving world data - Time: " + worldTimeInMinutes +
                    " Played Time: " + playedTime +
                    " Day Length: " + dayLength);

                setDirty(true);
                WorldManager worldManager = WorldManager.getInstance(null, true);
                worldManager.saveWorld(this);

                GameLogger.info("Successfully saved world: " + name);

            } catch (Exception e) {
                GameLogger.error("Failed to save world: " + name + " - " + e.getMessage());
            }
        }
    }

    public void save(boolean createBackup) {
        synchronized (saveLock) {
            try {
                validateAndRepairWorld();
                if (createBackup) {
                    // Create timestamp for backup
                    String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                    String backupName = name + "_backup_" + timestamp;

                    WorldData backup = new WorldData(backupName);
                    backup.setConfig(this.config);
                    backup.setWorldTimeInMinutes(this.worldTimeInMinutes);
                    backup.setPlayedTime(this.playedTime);
                    backup.setDayLength(this.dayLength);
                    backup.setPlayers(new HashMap<>(this.players));
                    backup.setBlockData(this.blockData);
                    // Save backup
                    WorldManager.getInstance(null, true).saveWorld(backup);
                    GameLogger.info("Created backup of world: " + name);
                }
                save();

            } catch (Exception e) {
                GameLogger.error("Failed to save world with backup: " + name + " - " + e.getMessage());
            }
        }
    }

    public boolean commandsAllowed() {
        return commandsAllowed;
    }

    public void setCommandsAllowed(boolean commandsAllowed) {
        synchronized (saveLock) {
            this.commandsAllowed = commandsAllowed;
            isDirty = true;
            GameLogger.info("Commands " + (commandsAllowed ? "enabled" : "disabled") +
                " for world: " + name);
        }
    }

    public void save(boolean createBackup, ServerStorageSystem storage) {
        synchronized (saveLock) {
            try {
                validateAndRepairWorld();
                if (createBackup) {
                    String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                    String backupName = name + "_backup_" + timestamp;

                    WorldData backup = new WorldData(backupName);
                    backup.setConfig(this.config);
                    backup.setWorldTimeInMinutes(this.worldTimeInMinutes);
                    backup.setPlayedTime(this.playedTime);
                    backup.setDayLength(this.dayLength);
                    backup.setPlayers(new HashMap<>(this.players));
                    backup.setBlockData(this.blockData);

                    WorldManager.getInstance(storage, true).saveWorld(backup);
                    GameLogger.info("Created backup of world: " + name);
                }
                save();

            } catch (Exception e) {
                GameLogger.error("Failed to save world with backup: " + name + " - " + e.getMessage());
            }
        }
    }

    public void addChunkObjects(Vector2 position, List<WorldObject> objects) {
        chunkObjects.put(position, new ArrayList<>(objects));
    }

    public Object getTimeLock() {
        return timeLock;
    }

    public void setSpawnX(int x) {
    }

    public void setSpawnY(int y) {
    }


    public Map<Vector2, List<WorldObject>> getDynamicObjects() {
        return dynamicObjects;
    }

    public void setDynamicObjects(Map<Vector2, List<WorldObject>> dynamicObjects) {
        this.dynamicObjects = dynamicObjects;
    }


    public void updateTime(float deltaTime) {
        synchronized (timeLock) {
            long deltaMillis = (long) (deltaTime * 1000);
            playedTime += deltaMillis;
            double gameMinutesPerSecond = (24 * 60.0) / (dayLength * 60.0);
            double timeToAdd = deltaTime * gameMinutesPerSecond;

            worldTimeInMinutes = (worldTimeInMinutes + timeToAdd) % (24 * 60);


        }
    }

    public float getDayLength() {
        return dayLength;
    }

    public void setDayLength(float dayLength) {
        this.dayLength = dayLength;
    }


    public PokemonData getPokemonData() {
        return pokemonData;
    }

    public void setPokemonData(PokemonData pokemonData) {
        this.pokemonData = pokemonData;
    }

    public BlockSaveData getBlockData() {
        return blockData;
    }

    public void setBlockData(BlockSaveData blockData) {
        this.blockData = blockData;
    }


    public void addPlayer(UUID uuid) {
        synchronized (saveLock) {
            if (uuid == null) {
                GameLogger.error("Cannot add null UUID");
                return;
            }

            playerUUIDs.add(uuid);
            isDirty = true;
            GameLogger.info("Added player UUID to world: " + uuid);
        }
    }

    public void removePlayer(UUID uuid) {
        synchronized (saveLock) {
            if (uuid == null) {
                GameLogger.error("Cannot remove null UUID");
                return;
            }

            if (playerUUIDs.remove(uuid)) {
                isDirty = true;
                GameLogger.info("Removed player UUID from world: " + uuid);
            }
        }
    }

    public Set<UUID> getPlayerUUIDs() {
        return new HashSet<>(playerUUIDs);
    }

    public void setPlayerUUIDs(Set<UUID> uuids) {
        synchronized (saveLock) {
            this.playerUUIDs = new HashSet<>(uuids);
            isDirty = true;
        }
    }

    public Map<String, PlayerData> getLegacyPlayers() {
        return players;
    }

    public void setLegacyPlayers(Map<String, PlayerData> players) {
        this.players = players != null ? new HashMap<>(players) : new HashMap<>();
    }

    public void saveLegacyPlayerData(String username, PlayerData data) {
        synchronized (saveLock) {
            if (username == null || data == null) {
                GameLogger.error("Cannot save null username or data");
                return;
            }

            try {
                PlayerData copy = data.copy();
                players.put(username, copy);
                isDirty = true;
                GameLogger.info("Saved legacy player data for: " + username);
            } catch (Exception e) {
                GameLogger.error("Failed to save legacy player data: " + e.getMessage());
            }
        }
    }

    public PlayerData getLegacyPlayerData(String username) {
        synchronized (saveLock) {
            PlayerData data = players.get(username);
            if (data != null) {
                return data.copy();
            }
            return null;
        }
    }

    public void savePlayerData(String username, PlayerData data, boolean isMultiplayer) {
        if (isMultiplayer) {
            // Multiplayer mode logic (just store UUID reference)
        } else {
            // Singleplayer mode: store actual data
            if (data != null && username != null) {
                this.players.put(username, data.copy());
                this.isDirty = true;
            }
        }
    }


    public PlayerData getPlayerData(String username, boolean isMultiplayer) {
        if (isMultiplayer) {
            // In multiplayer mode, this should not be used directly
            // Instead, use ServerStorageSystem to get player data
            GameLogger.error("Attempted to get player data directly in multiplayer mode");
            return null;
        } else {
            return getLegacyPlayerData(username);
        }
    }

    public WorldConfig getConfig() {
        return config;
    }

    public void setConfig(WorldConfig config) {
        synchronized (saveLock) {
            this.config = config;
            isDirty = true;
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        synchronized (saveLock) {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("World name cannot be null or empty");
            }
            this.name = name.trim();
            setDirty(true);
        }
    }

    public long getLastPlayed() {
        return lastPlayed;
    }

    public void setLastPlayed(long lastPlayed) {
        synchronized (saveLock) {
            this.lastPlayed = lastPlayed;
            isDirty = true;
        }
    }

    public Map<String, PlayerData> getPlayers() {
        if (players != null) {
            return Collections.unmodifiableMap(players);
        }
        return null;
    }

    public void setPlayers(HashMap<String, PlayerData> players) {
        this.players = players;
    }


    public void removeWildPokemon(UUID uuid) {
        wildPokemonMap.remove(uuid);
        if (pokemonData != null) {
            pokemonData.removeWildPokemon(uuid);
        }
    }

    // Now, update the Ga
    public boolean isDirty() {
        return isDirty;
    }

    public void setDirty(boolean dirty) {
        isDirty = dirty;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        synchronized (saveLock) {
            this.username = username;
            isDirty = true;
        }
    }


    @Override
    public String toString() {
        return "WorldData{" +
            "name='" + name + '\'' +
            ", players=" + players.size() +
            ", lastPlayed=" + lastPlayed +
            ", username='" + username + '\'' +
            '}';
    }

    public static class WorldConfig {
        private long seed;
        private float treeSpawnRate = 0.15f;
        private float pokemonSpawnRate = 0.05f;
        ;
        private int tileSpawnX;
        ;
        private int tileSpawnY;

        public WorldConfig() {
        }

        public WorldConfig(long seed) {
            this.seed = seed;
        }

        // Getters and Setters
        public long getSeed() {
            return seed;
        }

        public void setSeed(long seed) {
            this.seed = seed;
        }

        public int getTileSpawnX() {
            return tileSpawnX;
        }

        public void setTileSpawnX(int tileSpawnX) {
            this.tileSpawnX = tileSpawnX;
        }

        public int getTileSpawnY() {
            return tileSpawnY;
        }

        public void setTileSpawnY(int tileSpawnY) {
            this.tileSpawnY = tileSpawnY;
        }

        public float getTreeSpawnRate() {
            return treeSpawnRate;
        }

        public void setTreeSpawnRate(float rate) {
            this.treeSpawnRate = rate;
        }

        public float getPokemonSpawnRate() {
            return pokemonSpawnRate;
        }

        public void setPokemonSpawnRate(float rate) {
            this.pokemonSpawnRate = rate;
        }
    }

    public static class WorldObjectData implements Serializable {
        public String type;
        public float x, y;
        public String id;
        // Add other object properties
    }
}
