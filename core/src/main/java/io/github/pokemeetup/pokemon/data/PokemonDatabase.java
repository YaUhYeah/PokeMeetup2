package io.github.pokemeetup.pokemon.data;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.pokemeetup.FileSystemDelegate;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.attacks.Move;
import io.github.pokemeetup.pokemon.attacks.MoveLoader;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.storage.GameFileSystem;

import java.util.*;

public class PokemonDatabase {
    private static final String POKEMON_DATA_FILE = "Data/pokemon.json";
    private static final String MOVE_DATA_FILE = "Data/moves.json";
    private static final Map<String, PokemonTemplate> pokemonTemplates = new HashMap<>();
    private static final Map<String, BaseStats> pokemonStats = new HashMap<>();
    private static boolean isInitialized = false;
    private static final Map<String, Move> allMoves = new HashMap<>();

    public static PokemonTemplate getTemplate(String name) {
        if (!isInitialized) {
            initialize();
        }
        return pokemonTemplates.get(name.toLowerCase());
    }


    public static void initialize() {
        if (isInitialized) {
            return;
        }
        try {
            GameLogger.info("Initializing Pokemon Database...");
            FileSystemDelegate delegate = GameFileSystem.getInstance().getDelegate();

            // Load moves first
            try {
                String movesJson = delegate.readString(MOVE_DATA_FILE);
                GameLogger.info("Loaded moves.json content (length: " + movesJson.length() + ")");
                allMoves.putAll(MoveLoader.loadMovesFromJson(movesJson));
                GameLogger.info("Successfully loaded " + allMoves.size() + " moves");
                int count = 0;
                for (Map.Entry<String, Move> entry : allMoves.entrySet()) {
                    if (count++ < 3) {
                        GameLogger.info("Loaded move: " + entry.getKey() + " (" +
                            entry.getValue().getType() + ", Power: " +
                            entry.getValue().getPower() + ")");
                    }
                }
            } catch (Exception e) {
                GameLogger.error("Failed to load moves: " + e.getMessage());
                throw e;
            }

            // Then load Pokémon data
            try {
                String pokemonJson = delegate.readString(POKEMON_DATA_FILE);
                GameLogger.info("Loaded pokemon.json content (length: " + pokemonJson.length() + ")");
                JsonReader reader = new JsonReader();
                JsonValue root = reader.parse(pokemonJson);
                JsonValue pokemonArray = root.get("pokemon");
                if (pokemonArray == null) {
                    throw new RuntimeException("Invalid pokemon.json format - missing 'pokemon' array");
                }
                int pokemonCount = 0;
                for (JsonValue pokemonValue = pokemonArray.child; pokemonValue != null; pokemonValue = pokemonValue.next) {
                    try {
                        String name = pokemonValue.getString("name");
                        if (name == null || name.isEmpty()) {
                            continue;
                        }
                        Pokemon.PokemonType primaryType = Pokemon.PokemonType.valueOf(
                            pokemonValue.getString("primaryType").toUpperCase());
                        Pokemon.PokemonType secondaryType = getSecondaryType(pokemonValue);
                        List<MoveEntry> moves = loadPokemonMoves(pokemonValue.get("moves"));
                        BaseStats stats = new BaseStats(
                            name,
                            pokemonValue.getInt("baseHp"),
                            pokemonValue.getInt("baseAttack"),
                            pokemonValue.getInt("baseDefense"),
                            pokemonValue.getInt("baseSpAtk"),
                            pokemonValue.getInt("baseSpDef"),
                            pokemonValue.getInt("baseSpeed"),
                            primaryType,
                            secondaryType,
                            moves
                        );
                        pokemonStats.put(name, stats);

                        // Create template and load growth rate
                        PokemonTemplate template = new PokemonTemplate();
                        template.name = name;
                        template.primaryType = primaryType;
                        template.secondaryType = secondaryType;
                        template.baseStats = stats;
                        template.moves = moves;
                        template.width = pokemonValue.getFloat("width", 1.0f);
                        template.height = pokemonValue.getFloat("height", 1.0f);
                        template.growthRate = pokemonValue.getString("growthRate", "Medium Fast");
                        pokemonTemplates.put(name.toLowerCase(), template);

                        pokemonCount++;
                        if (pokemonCount <= 3) {
                            GameLogger.info("Loaded Pokemon: " + name + " (" +
                                primaryType + (secondaryType != null ? "/" + secondaryType : "") +
                                ") with " + moves.size() + " moves");
                        }
                    } catch (Exception e) {
                        GameLogger.error("Error loading Pokemon entry: " + e.getMessage());
                    }
                }
                GameLogger.info("Successfully loaded " + pokemonCount + " Pokemon");
                isInitialized = true;
            } catch (Exception e) {
                GameLogger.error("Failed to load Pokemon data: " + e.getMessage());
                throw e;
            }
        } catch (Exception e) {
            GameLogger.error("Pokemon database initialization failed: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize Pokemon database", e);
        }
    }

    public static BaseStats getStats(String name) {
        if (!isInitialized) {
            initialize();
        }
        BaseStats stats = pokemonStats.get(name);
        if (stats == null) {
            GameLogger.error("No stats found for Pokemon: " + name);
            return new BaseStats(
                name,
                45, 45, 45, 45, 45, 45,
                Pokemon.PokemonType.NORMAL,
                null,
                new ArrayList<>()
            );
        }
        return stats;
    }

    private static List<MoveEntry> loadPokemonMoves(JsonValue movesArray) {
        List<MoveEntry> moves = new ArrayList<>();
        if (movesArray != null && movesArray.isArray()) {
            for (JsonValue moveValue = movesArray.child; moveValue != null; moveValue = moveValue.next) {
                try {
                    String moveName = moveValue.getString("name");
                    int level = moveValue.getInt("level");
                    moves.add(new MoveEntry(moveName, level));
                } catch (Exception e) {
                    GameLogger.error("Error loading move: " + e.getMessage());
                }
            }
        }
        return moves;
    }

    private static Pokemon.PokemonType getSecondaryType(JsonValue pokemonValue) {
        try {
            if (pokemonValue.has("secondaryType")) {
                String secondaryType = pokemonValue.getString("secondaryType", "").trim();
                if (!secondaryType.isEmpty()) {
                    return Pokemon.PokemonType.valueOf(secondaryType.toUpperCase());
                }
            }
        } catch (Exception e) {
            GameLogger.info("No secondary type for Pokemon: " + pokemonValue.getString("name", "unknown"));
        }
        return null;
    }

    public static Pokemon createPokemon(String name, int level) {
        if (!isInitialized) {
            initialize();
        }
        PokemonTemplate template = pokemonTemplates.get(name);
        if (template == null) {
            GameLogger.error("Pokemon template not found: " + name);
            return null;
        }
        try {
            Pokemon.Builder builder = new Pokemon.Builder(name, level)
                .withType(template.primaryType, template.secondaryType);
            int hp = calculateStat(template.baseStats.baseHp, level, true);
            int attack = calculateStat(template.baseStats.baseAttack, level, false);
            int defense = calculateStat(template.baseStats.baseDefense, level, false);
            int spAtk = calculateStat(template.baseStats.baseSpAtk, level, false);
            int spDef = calculateStat(template.baseStats.baseSpDef, level, false);
            int speed = calculateStat(template.baseStats.baseSpeed, level, false);
            builder.withStats(hp, attack, defense, spAtk, spDef, speed);
            List<Move> startingMoves = getMovesForLevel(template.moves, level);
            builder.withMoves(startingMoves);
            return builder.build();
        } catch (Exception e) {
            GameLogger.error("Error creating Pokemon: " + e.getMessage());
            return null;
        }
    }

    public static List<Move> getMovesForLevel(List<MoveEntry> moveEntries, int level) {
        List<Move> moves = new ArrayList<>();
        try {
            Map<String, Move> moveMap = new HashMap<>();
            for (Map.Entry<String, Move> entry : allMoves.entrySet()) {
                moveMap.put(entry.getKey().toLowerCase(), entry.getValue());
            }
            List<MoveEntry> learnedMoves = new ArrayList<>();
            for (MoveEntry entry : moveEntries) {
                if (entry.level <= level) {
                    learnedMoves.add(entry);
                }
            }
            learnedMoves.sort(Comparator.comparingInt(e -> e.level));
            int movesToAdd = Math.min(learnedMoves.size(), 4);
            for (int i = learnedMoves.size() - movesToAdd; i < learnedMoves.size(); i++) {
                MoveEntry moveEntry = learnedMoves.get(i);
                String moveName = moveEntry.name.toLowerCase();
                Move move = moveMap.get(moveName);
                if (move != null) {
                    moves.add(cloneMove(move));
                    GameLogger.info("Added move: " + moveEntry.name + " (Level " + moveEntry.level + ")");
                } else {
                    GameLogger.error("Move not found: " + moveEntry.name);
                    GameLogger.error("Available moves: " + String.join(", ", moveMap.keySet()));
                }
            }
        } catch (Exception e) {
            GameLogger.error("Error loading moves: " + e.getMessage());
            e.printStackTrace();
        }
        return moves;
    }

    public static Move cloneMove(Move move) {
        Move.MoveEffect clonedEffect = null;
        if (move.getEffect() != null) {
            clonedEffect = cloneMoveEffect(move.getEffect());
        }
        return new Move.Builder(move.getName(), move.getType())
            .power(move.getPower())
            .accuracy(move.getAccuracy())
            .pp(move.getPp())
            .special(move.isSpecial())
            .description(move.getDescription())
            .effect(clonedEffect)
            .build();
    }

    private static Move.MoveEffect cloneMoveEffect(Move.MoveEffect effect) {
        Move.MoveEffect clonedEffect = new Move.MoveEffect();
        clonedEffect.setEffectType(effect.getEffectType());
        clonedEffect.setChance(effect.getChance());
        clonedEffect.setAnimation(effect.getAnimation());
        clonedEffect.setSound(effect.getSound());
        clonedEffect.setStatusEffect(effect.getStatusEffect());
        clonedEffect.setDuration(effect.getDuration());
        clonedEffect.setStatModifiers(new HashMap<>(effect.getStatModifiers()));
        return clonedEffect;
    }

    public static Move getMoveByName(String moveName) {
        return allMoves.get(moveName);
    }

    private static int calculateStat(int base, int level, boolean isHp) {
        int iv = 15;
        int ev = 0;
        if (isHp) {
            return ((2 * base + iv + (ev / 4)) * level / 100) + level + 10;
        } else {
            return ((2 * base + iv + (ev / 4)) * level / 100) + 5;
        }
    }

    public static class MoveEntry {
        public final String name;
        public final int level;
        public MoveEntry(String name, int level) {
            this.name = name;
            this.level = level;
        }
    }

    public static class PokemonTemplate {
        public Pokemon.PokemonType primaryType;
        public Pokemon.PokemonType secondaryType;
        public BaseStats baseStats;
        public List<MoveEntry> moves;
        public String name;
        public float width;
        public float height;
        // NEW: growthRate field
        public String growthRate;
    }

    public static class BaseStats {
        public final String name;
        public final int baseHp;
        public final int baseAttack;
        public final int baseDefense;
        public final int baseSpAtk;
        public final int baseSpDef;
        public final int baseSpeed;
        public final Pokemon.PokemonType primaryType;
        public final Pokemon.PokemonType secondaryType;
        public final List<MoveEntry> moves;
        public BaseStats(String name, int baseHp, int baseAttack, int baseDefense,
                         int baseSpAtk, int baseSpDef, int baseSpeed,
                         Pokemon.PokemonType primaryType, Pokemon.PokemonType secondaryType,
                         List<MoveEntry> moves) {
            this.name = name;
            this.baseHp = baseHp;
            this.baseAttack = baseAttack;
            this.baseDefense = baseDefense;
            this.baseSpAtk = baseSpAtk;
            this.baseSpDef = baseSpDef;
            this.baseSpeed = baseSpeed;
            this.primaryType = primaryType;
            this.secondaryType = secondaryType;
            this.moves = moves != null ? moves : new ArrayList<>();
        }
    }
}
