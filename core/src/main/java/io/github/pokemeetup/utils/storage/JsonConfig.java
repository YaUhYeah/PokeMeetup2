package io.github.pokemeetup.utils.storage;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.system.data.*;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.utils.GameLogger;

import java.util.*;

public class JsonConfig {
    private static final String SINGLE_PLAYER_DIR = "worlds/singleplayer/";
    private static Json instance;

    public static WorldData loadWorldData(String worldName) {
        // Add check for multiplayer mode
        if (GameContext.get().getGameClient() != null &&
            !GameContext.get().getGameClient().isSinglePlayer()) {
            GameLogger.info("Skipping local world load in multiplayer mode");
            return null;
        }

        try {
            FileHandle worldDir = Gdx.files.local(SINGLE_PLAYER_DIR + worldName);
            FileHandle worldFile = worldDir.child("world.json");

            if (!worldFile.exists()) {
                GameLogger.error("World file not found: " + worldFile.path());
                return null;
            }
            String jsonContent = worldFile.readString();
            Json json = getInstance();

            return json.fromJson(WorldData.class, jsonContent);

        } catch (Exception e) {
            GameLogger.error("Error loading world data: " + e.getMessage());
            return null;
        }
    }

    public static synchronized Json getInstance() {
        if (instance == null) {
            instance = new Json();
            instance.setOutputType(JsonWriter.OutputType.json);
            instance.setTypeName(null);
            instance.setUsePrototypes(false);
            setupSerializers(instance);
        }
        return instance;
    }


    private static void setupSerializers(Json json) {


        json.setSerializer(WorldData.class, new Json.Serializer<>() {
            @Override
            public void write(Json json, WorldData world, Class knownType) {
                json.writeObjectStart();
                synchronized (world.getTimeLock()) {
                    json.writeValue("worldTimeInMinutes", world.getWorldTimeInMinutes());
                    json.writeValue("playedTime", world.getPlayedTime());
                    json.writeValue("dayLength", world.getDayLength());
                }
                json.writeValue("name", world.getName());
                json.writeValue("lastPlayed", world.getLastPlayed());
                json.writeValue("config", world.getConfig());
                json.writeValue("players", world.getPlayers());
                json.writeValue("pokemonData", world.getPokemonData());
                json.writeValue("commands_allowed", world.commandsAllowed());
                json.writeObjectEnd();
            }


            @Override
            public WorldData read(Json json, JsonValue jsonData, Class type) {
                WorldData world = new WorldData();
                JsonValue timeValue = jsonData.get("worldTimeInMinutes");
                if (timeValue != null) {
                    world.setWorldTimeInMinutes(timeValue.asDouble());
                }

                JsonValue playedValue = jsonData.get("playedTime");
                if (playedValue != null) {
                    world.setPlayedTime(playedValue.asLong());
                }

                JsonValue dayLengthValue = jsonData.get("dayLength");
                if (dayLengthValue != null) {
                    world.setDayLength(dayLengthValue.asFloat());
                }

                world.setName(jsonData.getString("name", ""));
                world.setLastPlayed(jsonData.getLong("lastPlayed", System.currentTimeMillis()));
                WorldData.WorldConfig config = json.readValue(WorldData.WorldConfig.class, jsonData.get("config"));
                if (config == null) {
                    config = new WorldData.WorldConfig(System.currentTimeMillis());
                }
                world.setConfig(config);

                // Players
                JsonValue playersObject = jsonData.get("players");
                if (playersObject != null && playersObject.isObject()) {
                    HashMap<String, PlayerData> players = new HashMap<>();
                    for (JsonValue playerEntry = playersObject.child; playerEntry != null; playerEntry = playerEntry.next) {
                        String username = playerEntry.name;
                        PlayerData playerData = json.readValue(PlayerData.class, playerEntry);
                        if (playerData != null) {
                            players.put(username, playerData);
                        }
                    }
                    world.setPlayers(players);
                }

                // PokemonData
                PokemonData pokemonData = json.readValue(PokemonData.class, jsonData.get("pokemonData"));
                if (pokemonData == null) {
                    pokemonData = new PokemonData();
                }
                world.setPokemonData(pokemonData);

                // Commands Allowed
                world.setCommandsAllowed(jsonData.getBoolean("commands_allowed", false));

                return world;
            }
        });


        json.setSerializer(WorldData.WorldConfig.class, new Json.Serializer<>() {
            @Override
            public void write(Json json, WorldData.WorldConfig config, Class knownType) {
                json.writeObjectStart();
                json.writeValue("seed", config.getSeed());
                json.writeValue("treeSpawnRate", config.getTreeSpawnRate());
                json.writeValue("pokemonSpawnRate", config.getPokemonSpawnRate());
                json.writeValue("spawnTileX", config.getTileSpawnX());
                json.writeValue("spawnTileY", config.getTileSpawnY());
                json.writeObjectEnd();
            }

            @Override
            public WorldData.WorldConfig read(Json json, JsonValue jsonData, Class type) {
                WorldData.WorldConfig config = new WorldData.WorldConfig();
                config.setSeed(jsonData.getLong("seed", System.currentTimeMillis()));
                config.setTreeSpawnRate(jsonData.getFloat("treeSpawnRate", 0.15f));
                config.setPokemonSpawnRate(jsonData.getFloat("pokemonSpawnRate", 0.05f));
                config.setTileSpawnX(jsonData.getInt("spawnTileX", 0));
                config.setTileSpawnY(jsonData.getInt("spawnTileY", 0));
                return config;
            }
        });

        json.setSerializer(PlayerData.class, new Json.Serializer<>() {
            @Override
            public void write(Json json, PlayerData playerData, Class knownType) {
                json.writeObjectStart();
                json.writeValue("username", playerData.getUsername());
                json.writeValue("x", playerData.getX());
                json.writeValue("y", playerData.getY());
                json.writeValue("direction", playerData.getDirection());
                json.writeValue("isMoving", playerData.isMoving());
                json.writeValue("wantsToRun", playerData.isWantsToRun());

                // Inventory Items
                json.writeArrayStart("inventoryItems");
                if (playerData.getInventoryItems() != null) {
                    for (ItemData item : playerData.getInventoryItems()) {
                        json.writeValue(item);
                    }
                }
                json.writeArrayEnd();

                // Party Pokemon
                json.writeArrayStart("partyPokemon");
                if (playerData.getPartyPokemon() != null) {
                    for (PokemonData pokemon : playerData.getPartyPokemon()) {
                        json.writeValue(pokemon);
                    }
                }
                json.writeArrayEnd();

                json.writeObjectEnd();
            }

            @Override
            public PlayerData read(Json json, JsonValue jsonData, Class type) {
                PlayerData playerData = new PlayerData();
                playerData.setUsername(jsonData.getString("username", "Player"));
                playerData.setX(jsonData.getFloat("x", 0f));
                playerData.setY(jsonData.getFloat("y", 0f));
                playerData.setDirection(jsonData.getString("direction", "down"));
                playerData.setMoving(jsonData.getBoolean("isMoving", false));
                playerData.setWantsToRun(jsonData.getBoolean("wantsToRun", false));

                // Inventory Items
                JsonValue inventoryArray = jsonData.get("inventoryItems");
                List<ItemData> inventory = new ArrayList<>(Inventory.INVENTORY_SIZE);
                for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
                    inventory.add(null);
                }
                if (inventoryArray != null && inventoryArray.isArray()) {
                    int index = 0;
                    for (JsonValue itemValue = inventoryArray.child; itemValue != null && index < Inventory.INVENTORY_SIZE; itemValue = itemValue.next, index++) {
                        ItemData item = json.readValue(ItemData.class, itemValue);
                        inventory.set(index, item);
                    }
                }
                playerData.setInventoryItems(inventory);

                // Party Pokemon
                JsonValue partyArray = jsonData.get("partyPokemon");
                List<PokemonData> party = new ArrayList<>(6);
                for (int i = 0; i < 6; i++) {
                    party.add(null);
                }
                if (partyArray != null && partyArray.isArray()) {
                    int index = 0;
                    for (JsonValue pokemonValue = partyArray.child; pokemonValue != null && index < 6; pokemonValue = pokemonValue.next, index++) {
                        PokemonData pokemon = json.readValue(PokemonData.class, pokemonValue);
                        party.set(index, pokemon);
                    }
                }
                playerData.setPartyPokemon(party);

                return playerData;
            }
        });
        json.setSerializer(ItemData.class, new Json.Serializer<>() {
            @Override
            public void write(Json json, ItemData itemData, Class knownType) {
                if (itemData == null) {
                    json.writeValue(null);
                    return;
                }

                json.writeObjectStart();
                json.writeValue("itemId", itemData.getItemId());
                json.writeValue("count", itemData.getCount());
                json.writeValue("uuid", itemData.getUuid() != null ? itemData.getUuid().toString() : UUID.randomUUID().toString());
                json.writeValue("durability", itemData.getDurability());
                json.writeValue("maxDurability", itemData.getMaxDurability());
                json.writeObjectEnd();
            }

            @Override
            public ItemData read(Json json, JsonValue jsonData, Class type) {
                if (jsonData == null || jsonData.isNull()) {
                    return null;
                }

                ItemData itemData = new ItemData();

                String itemId;
                try {
                    itemId = jsonData.getString("itemId");
                } catch (Exception e) {
                    GameLogger.error("ItemData deserialization error: Missing 'itemId'. Skipping item.");
                    return null; // Skip this item
                }

                itemData.setCount(jsonData.getInt("count", 1));
                try {
                    String uuidStr = jsonData.getString("uuid", null);
                    itemData.setUuid(uuidStr != null ? UUID.fromString(uuidStr) : UUID.randomUUID());
                } catch (IllegalArgumentException e) {
                    itemData.setUuid(UUID.randomUUID());
                }

                itemData.setDurability(jsonData.getInt("durability", -1));
                itemData.setMaxDurability(jsonData.getInt("maxDurability", -1));

                // Set itemId after other fields
                itemData.setItemId(itemId);

                return itemData;
            }
        });

        json.setSerializer(PokemonData.class, new Json.Serializer<PokemonData>() {
            @Override
            public void write(Json json, PokemonData pokemonData, Class knownType) {
                if (pokemonData == null) {
                    json.writeValue(null);
                    return;
                }

                json.writeObjectStart();

                // Basic Info
                json.writeValue("name", pokemonData.getName());
                json.writeValue("uuid", pokemonData.getUuid() != null ? pokemonData.getUuid().toString() : UUID.randomUUID().toString());
                json.writeValue("level", pokemonData.getLevel());
                json.writeValue("nature", pokemonData.getNature());

                // Primary Type
                if (pokemonData.getPrimaryType() != null) {
                    json.writeValue("primaryType", pokemonData.getPrimaryType().name());
                } else {
                    json.writeValue("primaryType", "NORMAL"); // Default to NORMAL if null
                }

                // Secondary Type
                if (pokemonData.getSecondaryType() != null) {
                    json.writeValue("secondaryType", pokemonData.getSecondaryType().name());
                }

                // Stats
                if (pokemonData.getStats() != null) {
                    json.writeValue("stats", pokemonData.getStats());
                }

                // Base Stats
                json.writeValue("baseHp", pokemonData.getBaseHp());
                json.writeValue("baseAttack", pokemonData.getBaseAttack());
                json.writeValue("baseDefense", pokemonData.getBaseDefense());
                json.writeValue("baseSpAtk", pokemonData.getBaseSpAtk());
                json.writeValue("baseSpDef", pokemonData.getBaseSpDef());
                json.writeValue("baseSpeed", pokemonData.getBaseSpeed());

                // Experience
                json.writeValue("currentExperience", pokemonData.getCurrentExperience());
                json.writeValue("experienceToNextLevel", pokemonData.getExperienceToNextLevel());

                // Current HP
                json.writeValue("currentHp", pokemonData.getCurrentHp());

                // Moves
                json.writeArrayStart("moves");
                if (pokemonData.getMoves() != null) {
                    for (PokemonData.MoveData move : pokemonData.getMoves()) {
                        json.writeValue(move);
                    }
                }
                json.writeArrayEnd();

                json.writeObjectEnd();
            }

            @Override
            public PokemonData read(Json json, JsonValue jsonData, Class type) {
                if (jsonData == null || jsonData.isNull()) {
                    return null;
                }

                PokemonData pokemonData = new PokemonData();

                pokemonData.setName(jsonData.getString("name", "Unknown"));
                String uuidStr = jsonData.getString("uuid", null);
                pokemonData.setUuid(uuidStr != null ? UUID.fromString(uuidStr) : UUID.randomUUID());
                pokemonData.setLevel(jsonData.getInt("level", 1));
                pokemonData.setNature(jsonData.getString("nature", "Unknown"));

                // Primary Type
                String primaryTypeStr = jsonData.getString("primaryType", "NORMAL");
                try {
                    pokemonData.setPrimaryType(Pokemon.PokemonType.valueOf(primaryTypeStr));
                } catch (IllegalArgumentException e) {
                    GameLogger.error("Invalid primary type '" + primaryTypeStr + "'. Defaulting to NORMAL.");
                    pokemonData.setPrimaryType(Pokemon.PokemonType.NORMAL);
                }

                // Secondary Type
                if (jsonData.has("secondaryType")) {
                    String secondaryTypeStr = jsonData.getString("secondaryType");
                    try {
                        pokemonData.setSecondaryType(Pokemon.PokemonType.valueOf(secondaryTypeStr));
                    } catch (IllegalArgumentException e) {
                        GameLogger.error("Invalid secondary type '" + secondaryTypeStr + "'. Setting to null.");
                        pokemonData.setSecondaryType(null);
                    }
                }

                // Stats
                JsonValue statsValue = jsonData.get("stats");
                if (statsValue != null) {
                    PokemonData.Stats stats = json.readValue(PokemonData.Stats.class, statsValue);
                    pokemonData.setStats(stats);
                }

                // Base Stats
                pokemonData.setBaseHp(jsonData.getInt("baseHp", 1));
                pokemonData.setBaseAttack(jsonData.getInt("baseAttack", 1));
                pokemonData.setBaseDefense(jsonData.getInt("baseDefense", 1));
                pokemonData.setBaseSpAtk(jsonData.getInt("baseSpAtk", 1));
                pokemonData.setBaseSpDef(jsonData.getInt("baseSpDef", 1));
                pokemonData.setBaseSpeed(jsonData.getInt("baseSpeed", 1));

                // Experience
                pokemonData.setCurrentExperience(jsonData.getInt("currentExperience", 0));
                pokemonData.setExperienceToNextLevel(jsonData.getInt("experienceToNextLevel", 100));

                // Current HP
                pokemonData.setCurrentHp(jsonData.getInt("currentHp", pokemonData.getBaseHp()));

                // Moves
                JsonValue movesArray = jsonData.get("moves");
                if (movesArray != null && movesArray.isArray()) {
                    List<PokemonData.MoveData> moves = new ArrayList<>();
                    for (JsonValue moveValue = movesArray.child; moveValue != null; moveValue = moveValue.next) {
                        PokemonData.MoveData moveData = json.readValue(PokemonData.MoveData.class, moveValue);
                        moves.add(moveData);
                    }
                    pokemonData.setMoves(moves);
                }

                return pokemonData;
            }
        });
        json.setSerializer(PokemonData.Stats.class, new Json.Serializer<PokemonData.Stats>() {
            @Override
            public void write(Json json, PokemonData.Stats stats, Class knownType) {
                if (stats == null) {
                    json.writeValue(null);
                    return;
                }

                json.writeObjectStart();
                json.writeValue("hp", stats.getHp());
                json.writeValue("attack", stats.getAttack());
                json.writeValue("defense", stats.getDefense());
                json.writeValue("specialAttack", stats.getSpecialAttack());
                json.writeValue("specialDefense", stats.getSpecialDefense());
                json.writeValue("speed", stats.getSpeed());

                // IVs
                json.writeArrayStart("ivs");
                for (int iv : stats.ivs) {
                    json.writeValue(iv);
                }
                json.writeArrayEnd();

                // EVs
                json.writeArrayStart("evs");
                for (int ev : stats.evs) {
                    json.writeValue(ev);
                }
                json.writeArrayEnd();

                json.writeObjectEnd();
            }

            @Override
            public PokemonData.Stats read(Json json, JsonValue jsonData, Class type) {
                if (jsonData == null || jsonData.isNull()) {
                    return new PokemonData.Stats(); // Return default stats
                }

                PokemonData.Stats stats = new PokemonData.Stats();
                stats.setHp(jsonData.getInt("hp", 1));
                stats.setAttack(jsonData.getInt("attack", 1));
                stats.setDefense(jsonData.getInt("defense", 1));
                stats.setSpecialAttack(jsonData.getInt("specialAttack", 1));
                stats.setSpecialDefense(jsonData.getInt("specialDefense", 1));
                stats.setSpeed(jsonData.getInt("speed", 1));

                // IVs
                JsonValue ivsArray = jsonData.get("ivs");
                if (ivsArray != null && ivsArray.isArray()) {
                    int[] ivs = new int[6];
                    int index = 0;
                    for (JsonValue ivValue = ivsArray.child; ivValue != null && index < 6; ivValue = ivValue.next, index++) {
                        ivs[index] = ivValue.asInt();
                    }
                    stats.ivs = ivs;
                }

                // EVs
                JsonValue evsArray = jsonData.get("evs");
                if (evsArray != null && evsArray.isArray()) {
                    int[] evs = new int[6];
                    int index = 0;
                    for (JsonValue evValue = evsArray.child; evValue != null && index < 6; evValue = evValue.next, index++) {
                        evs[index] = evValue.asInt();
                    }
                    stats.evs = evs;
                }

                return stats;
            }
        });

        json.setSerializer(PokemonData.MoveData.class, new Json.Serializer<PokemonData.MoveData>() {
            @Override
            public void write(Json json, PokemonData.MoveData moveData, Class knownType) {
                if (moveData == null) {
                    json.writeValue(null);
                    return;
                }

                json.writeObjectStart();
                json.writeValue("name", moveData.getName());
                json.writeValue("type", moveData.getType() != null ? moveData.getType().name() : "NORMAL");
                json.writeValue("power", moveData.getPower());
                json.writeValue("accuracy", moveData.getAccuracy());
                json.writeValue("pp", moveData.getPp());
                json.writeValue("maxPp", moveData.getMaxPp());
                json.writeValue("isSpecial", moveData.isSpecial());
                json.writeValue("description", moveData.getDescription());
                json.writeValue("canFlinch", moveData.isCanFlinch());
                json.writeValue("effect", moveData.effect);
                json.writeObjectEnd();
            }

            @Override
            public PokemonData.MoveData read(Json json, JsonValue jsonData, Class type) {
                if (jsonData == null || jsonData.isNull()) {
                    return null;
                }

                PokemonData.MoveData moveData = new PokemonData.MoveData();
                moveData.setName(jsonData.getString("name", "Unknown"));
                String typeStr = jsonData.getString("type", "NORMAL");
                try {
                    moveData.setType(Pokemon.PokemonType.valueOf(typeStr));
                } catch (IllegalArgumentException e) {
                    GameLogger.error("Invalid move type '" + typeStr + "'. Defaulting to NORMAL.");
                    moveData.setType(Pokemon.PokemonType.NORMAL);
                }

                moveData.setPower(jsonData.getInt("power", 0));
                moveData.setAccuracy(jsonData.getInt("accuracy", 100));
                moveData.setPp(jsonData.getInt("pp", 0));
                moveData.setMaxPp(jsonData.getInt("maxPp", 0));
                moveData.setSpecial(jsonData.getBoolean("isSpecial", false));
                moveData.setDescription(jsonData.getString("description", ""));
                moveData.setCanFlinch(jsonData.getBoolean("canFlinch", false));

                // Effect
                JsonValue effectValue = jsonData.get("effect");
                if (effectValue != null && effectValue.isObject()) {
                    moveData.effect = json.readValue(PokemonData.MoveData.MoveEffectData.class, effectValue);
                }

                return moveData;
            }
        });
        json.setSerializer(PokemonData.MoveData.MoveEffectData.class, new Json.Serializer<PokemonData.MoveData.MoveEffectData>() {
            @Override
            public void write(Json json, PokemonData.MoveData.MoveEffectData effectData, Class knownType) {
                if (effectData == null) {
                    json.writeValue(null);
                    return;
                }

                json.writeObjectStart();
                if (effectData.getStatusEffect() != null) {
                    json.writeValue("statusEffect", effectData.getStatusEffect().name());
                }
                if (effectData.getStatModifiers() != null && !effectData.getStatModifiers().isEmpty()) {
                    json.writeObjectStart("statModifiers");
                    for (Map.Entry<String, Integer> entry : effectData.getStatModifiers().entrySet()) {
                        json.writeValue(entry.getKey(), entry.getValue());
                    }
                    json.writeObjectEnd();
                }
                json.writeValue("effectType", effectData.getEffectType());
                json.writeValue("chance", effectData.getChance());
                json.writeValue("animation", effectData.getAnimation());
                json.writeValue("sound", effectData.getSound());
                json.writeValue("duration", effectData.getDuration());
                json.writeObjectEnd();
            }

            @Override
            public PokemonData.MoveData.MoveEffectData read(Json json, JsonValue jsonData, Class type) {
                if (jsonData == null || jsonData.isNull()) {
                    return null;
                }

                PokemonData.MoveData.MoveEffectData effectData = new PokemonData.MoveData.MoveEffectData();

                if (jsonData.has("statusEffect")) {
                    String statusStr = jsonData.getString("statusEffect");
                    try {
                        effectData.setStatusEffect(Pokemon.Status.valueOf(statusStr));
                    } catch (IllegalArgumentException e) {
                        GameLogger.error("Invalid status effect '" + statusStr + "'. Ignoring.");
                    }
                }

                JsonValue statModifiersValue = jsonData.get("statModifiers");
                if (statModifiersValue != null && statModifiersValue.isObject()) {
                    Map<String, Integer> statModifiers = new HashMap<>();
                    for (JsonValue statEntry = statModifiersValue.child; statEntry != null; statEntry = statEntry.next) {
                        statModifiers.put(statEntry.name, statEntry.asInt());
                    }
                    effectData.setStatModifiers(statModifiers);
                }

                effectData.setEffectType(jsonData.getString("effectType", ""));
                effectData.setChance(jsonData.getFloat("chance", 0f));
                effectData.setAnimation(jsonData.getString("animation", ""));
                effectData.setSound(jsonData.getString("sound", ""));
                effectData.setDuration(jsonData.getInt("duration", 0));

                return effectData;
            }
        });

        json.setSerializer(PokemonData.WildPokemonData.class, new Json.Serializer<PokemonData.WildPokemonData>() {
            @Override
            public void write(Json json, PokemonData.WildPokemonData wildPokemonData, Class knownType) {
                if (wildPokemonData == null) {
                    json.writeValue(null);
                    return;
                }

                json.writeObjectStart();
                json.writeValue("name", wildPokemonData.getName());
                json.writeValue("level", wildPokemonData.getLevel());
                json.writeValue("position", wildPokemonData.getPosition());
                json.writeValue("direction", wildPokemonData.getDirection());
                json.writeValue("isMoving", wildPokemonData.isMoving());
                json.writeValue("spawnTime", wildPokemonData.getSpawnTime());

                // Primary Type
                if (wildPokemonData.getPrimaryType() != null) {
                    json.writeValue("primaryType", wildPokemonData.getPrimaryType().name());
                } else {
                    json.writeValue("primaryType", "NORMAL");
                }

                // Secondary Type
                if (wildPokemonData.getSecondaryType() != null) {
                    json.writeValue("secondaryType", wildPokemonData.getSecondaryType().name());
                }

                json.writeValue("currentHp", wildPokemonData.getCurrentHp());

                // Stats
                if (wildPokemonData.getStats() != null) {
                    json.writeValue("stats", wildPokemonData.getStats());
                }

                // Moves
                json.writeArrayStart("moves");
                if (wildPokemonData.getMoves() != null) {
                    for (PokemonData.MoveData move : wildPokemonData.getMoves()) {
                        json.writeValue(move);
                    }
                }
                json.writeArrayEnd();

                json.writeValue("uuid", wildPokemonData.getUuid() != null ? wildPokemonData.getUuid().toString() : UUID.randomUUID().toString());

                json.writeObjectEnd();
            }

            @Override
            public PokemonData.WildPokemonData read(Json json, JsonValue jsonData, Class type) {
                if (jsonData == null || jsonData.isNull()) {
                    return null;
                }

                PokemonData.WildPokemonData wildPokemonData = new PokemonData.WildPokemonData();
                wildPokemonData.setName(jsonData.getString("name", "Unknown"));
                wildPokemonData.setLevel(jsonData.getInt("level", 1));

                // Position
                JsonValue positionValue = jsonData.get("position");
                if (positionValue != null && positionValue.isObject()) {
                    float x = positionValue.getFloat("x", 0f);
                    float y = positionValue.getFloat("y", 0f);
                    wildPokemonData.setPosition(new Vector2(x, y));
                } else {
                    wildPokemonData.setPosition(new Vector2(0f, 0f));
                }

                wildPokemonData.setDirection(jsonData.getString("direction", "down"));
                wildPokemonData.setMoving(jsonData.getBoolean("isMoving", false));
                wildPokemonData.setSpawnTime(jsonData.getLong("spawnTime", System.currentTimeMillis()));

                // Primary Type
                String primaryTypeStr = jsonData.getString("primaryType", "NORMAL");
                try {
                    wildPokemonData.setPrimaryType(Pokemon.PokemonType.valueOf(primaryTypeStr));
                } catch (IllegalArgumentException e) {
                    GameLogger.error("Invalid primary type '" + primaryTypeStr + "' in WildPokemonData. Defaulting to NORMAL.");
                    wildPokemonData.setPrimaryType(Pokemon.PokemonType.NORMAL);
                }

                // Secondary Type
                if (jsonData.has("secondaryType")) {
                    String secondaryTypeStr = jsonData.getString("secondaryType");
                    try {
                        wildPokemonData.setSecondaryType(Pokemon.PokemonType.valueOf(secondaryTypeStr));
                    } catch (IllegalArgumentException e) {
                        GameLogger.error("Invalid secondary type '" + secondaryTypeStr + "' in WildPokemonData. Setting to null.");
                        wildPokemonData.setSecondaryType(null);
                    }
                }

                wildPokemonData.setCurrentHp(jsonData.getFloat("currentHp", 1f));

                // Stats
                JsonValue statsValue = jsonData.get("stats");
                if (statsValue != null) {
                    PokemonData.Stats stats = json.readValue(PokemonData.Stats.class, statsValue);
                    wildPokemonData.setStats(stats);
                }

                // Moves
                JsonValue movesValue = jsonData.get("moves");
                if (movesValue != null && movesValue.isArray()) {
                    List<PokemonData.MoveData> moves = new ArrayList<>();
                    for (JsonValue moveValue = movesValue.child; moveValue != null; moveValue = moveValue.next) {
                        PokemonData.MoveData moveData = json.readValue(PokemonData.MoveData.class, moveValue);
                        moves.add(moveData);
                    }
                    wildPokemonData.setMoves(moves);
                }

                String uuidStr = jsonData.getString("uuid", null);
                wildPokemonData.setUuid(uuidStr != null ? UUID.fromString(uuidStr) : UUID.randomUUID());

                return wildPokemonData;
            }
        });
    }

    private static void validatePlayerInventory(PlayerData playerData) {
        if (playerData.getInventoryItems() == null) {
            List<ItemData> items = new ArrayList<>(Inventory.INVENTORY_SIZE);
            for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
                items.add(null);
            }
            playerData.setInventoryItems(items);
        }
    }

    private static void validatePlayerPokemon(PlayerData playerData) {
        if (playerData.getPartyPokemon() == null) {
            List<PokemonData> pokemon = new ArrayList<>(6);
            for (int i = 0; i < 6; i++) {
                pokemon.add(null);
            }
            playerData.setPartyPokemon(pokemon);
        }
    }


    private static int calculateExperienceForLevel(int level) {
        return (int) (100 * Math.pow(level, 3) / 5); // Basic Pokemon experience formula
    }


}

