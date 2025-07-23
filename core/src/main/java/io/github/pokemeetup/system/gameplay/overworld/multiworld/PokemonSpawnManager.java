package io.github.pokemeetup.system.gameplay.overworld.multiworld;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.DayNightCycle;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonAI;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.PokemonLevelCalculator;
import io.github.pokemeetup.utils.textures.TextureManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static io.github.pokemeetup.system.gameplay.overworld.World.TILE_SIZE;

public class PokemonSpawnManager {
    public static final float POKEMON_DESPAWN_TIME = 300f;
    private static final float BASE_SPAWN_RATE = 0.275f;
    private static final float SPAWN_CHECK_INTERVAL = 2.5f;
    private static final Map<BiomeType, Map<TimeOfDay, String[]>> POKEMON_SPAWNS = new HashMap<>();

    // NEW: Thresholds for evolution chances based on distance from spawn (0,0)
    private static final float EVOLUTION_THRESHOLD_1 = 300 * TILE_SIZE; // Approx 300 tiles for 1st evolution
    private static final float EVOLUTION_THRESHOLD_2 = 800 * TILE_SIZE; // Approx 800 tiles for 2nd evolution

    // NEW: A map to store simple, two-stage evolution chains. E.g. "Pidgey" -> ["Pidgeotto", "Pidgeot"]
    private static final Map<String, String[]> EVOLUTION_CHAINS = new HashMap<>();

    private static final float MIN_SPAWN_DISTANCE_PIXELS = 10 * TILE_SIZE;
    private static final float MAX_SPAWN_DISTANCE_PIXELS = 20 * TILE_SIZE;
    private static final int MAX_POKEMON_PER_CHUNK = 8; // Increased slightly for more density
    private static final float MIN_POKEMON_SPACING = TILE_SIZE * 1.5f;
    private static final float PACK_SPAWN_CHANCE = 0.3f;
    private static final int MIN_PACK_SIZE = 2;
    private static final int MAX_PACK_SIZE = 5;
    private static final float PACK_BASE_RADIUS = TILE_SIZE * 2;
    private static final float PACK_MAX_RADIUS = TILE_SIZE * 4;
    private static final int MAX_PACK_SPAWN_ATTEMPTS = 15;
    private static final Map<String, PackBehaviorInfo> PACK_SPECIES = new HashMap<>();

    static {
        PACK_SPECIES.put("rattata", new PackBehaviorInfo(0.4f, 2, 4, true));
        PACK_SPECIES.put("pidgey", new PackBehaviorInfo(0.3f, 3, 6, false));
        PACK_SPECIES.put("zubat", new PackBehaviorInfo(0.5f, 4, 8, true));
        PACK_SPECIES.put("caterpie", new PackBehaviorInfo(0.2f, 2, 3, false));
        PACK_SPECIES.put("weedle", new PackBehaviorInfo(0.2f, 2, 3, false));
        PACK_SPECIES.put("sentret", new PackBehaviorInfo(0.3f, 2, 4, true));
        PACK_SPECIES.put("hoppip", new PackBehaviorInfo(0.4f, 3, 5, false));
        PACK_SPECIES.put("poochyena", new PackBehaviorInfo(0.6f, 3, 6, true));
        PACK_SPECIES.put("zigzagoon", new PackBehaviorInfo(0.3f, 2, 4, false));
    }

    private final TextureAtlas atlas;
    private final Random random;
    private final Map<Vector2, List<WildPokemon>> pokemonByChunk;
    private final Map<UUID, WildPokemon> pokemonById;
    private final Map<UUID, NetworkSyncData> syncedPokemon = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> pokemonPacks = new ConcurrentHashMap<>();
    private float spawnTimer = 0;

    public PokemonSpawnManager(TextureAtlas atlas) {
        this.atlas = atlas;
        this.random = new Random();
        this.pokemonByChunk = new ConcurrentHashMap<>();
        this.pokemonById = new ConcurrentHashMap<>();
        initializePokemonSpawns();
        initializeEvolutionChains(); // NEW: Initialize evolution data
    }

    /**
     * MODIFIED: Initializes the evolution chains for Pokémon that can evolve, now including up to Gen 7.
     */
    private void initializeEvolutionChains() {
        // Gen 1
        EVOLUTION_CHAINS.put("Bulbasaur", new String[]{"Ivysaur", "Venusaur"});
        EVOLUTION_CHAINS.put("Charmander", new String[]{"Charmeleon", "Charizard"});
        EVOLUTION_CHAINS.put("Squirtle", new String[]{"Wartortle", "Blastoise"});
        EVOLUTION_CHAINS.put("Caterpie", new String[]{"Metapod", "Butterfree"});
        EVOLUTION_CHAINS.put("Weedle", new String[]{"Kakuna", "Beedrill"});
        EVOLUTION_CHAINS.put("Pidgey", new String[]{"Pidgeotto", "Pidgeot"});
        EVOLUTION_CHAINS.put("Rattata", new String[]{"Raticate"});
        EVOLUTION_CHAINS.put("Spearow", new String[]{"Fearow"});
        EVOLUTION_CHAINS.put("Ekans", new String[]{"Arbok"});
        EVOLUTION_CHAINS.put("Pikachu", new String[]{"Raichu"});
        EVOLUTION_CHAINS.put("Sandshrew", new String[]{"Sandslash"});
        EVOLUTION_CHAINS.put("Nidoran♀", new String[]{"Nidorina", "Nidoqueen"});
        EVOLUTION_CHAINS.put("Nidoran♂", new String[]{"Nidorino", "Nidoking"});
        EVOLUTION_CHAINS.put("Clefairy", new String[]{"Clefable"});
        EVOLUTION_CHAINS.put("Vulpix", new String[]{"Ninetales"});
        EVOLUTION_CHAINS.put("Jigglypuff", new String[]{"Wigglytuff"});
        EVOLUTION_CHAINS.put("Zubat", new String[]{"Golbat", "Crobat"});
        EVOLUTION_CHAINS.put("Oddish", new String[]{"Gloom", "Vileplume"});
        EVOLUTION_CHAINS.put("Paras", new String[]{"Parasect"});
        EVOLUTION_CHAINS.put("Venonat", new String[]{"Venomoth"});
        EVOLUTION_CHAINS.put("Diglett", new String[]{"Dugtrio"});
        EVOLUTION_CHAINS.put("Meowth", new String[]{"Persian"});
        EVOLUTION_CHAINS.put("Psyduck", new String[]{"Golduck"});
        EVOLUTION_CHAINS.put("Mankey", new String[]{"Primeape"});
        EVOLUTION_CHAINS.put("Growlithe", new String[]{"Arcanine"});
        EVOLUTION_CHAINS.put("Poliwag", new String[]{"Poliwhirl", "Poliwrath"});
        EVOLUTION_CHAINS.put("Abra", new String[]{"Kadabra", "Alakazam"});
        EVOLUTION_CHAINS.put("Machop", new String[]{"Machoke", "Machamp"});
        EVOLUTION_CHAINS.put("Bellsprout", new String[]{"Weepinbell", "Victreebel"});
        EVOLUTION_CHAINS.put("Tentacool", new String[]{"Tentacruel"});
        EVOLUTION_CHAINS.put("Geodude", new String[]{"Graveler", "Golem"});
        EVOLUTION_CHAINS.put("Ponyta", new String[]{"Rapidash"});
        EVOLUTION_CHAINS.put("Slowpoke", new String[]{"Slowbro", "Slowking"});
        EVOLUTION_CHAINS.put("Magnemite", new String[]{"Magneton", "Magnezone"});
        EVOLUTION_CHAINS.put("Doduo", new String[]{"Dodrio"});
        EVOLUTION_CHAINS.put("Seel", new String[]{"Dewgong"});
        EVOLUTION_CHAINS.put("Grimer", new String[]{"Muk"});
        EVOLUTION_CHAINS.put("Shellder", new String[]{"Cloyster"});
        EVOLUTION_CHAINS.put("Gastly", new String[]{"Haunter", "Gengar"});
        EVOLUTION_CHAINS.put("Drowzee", new String[]{"Hypno"});
        EVOLUTION_CHAINS.put("Krabby", new String[]{"Kingler"});
        EVOLUTION_CHAINS.put("Voltorb", new String[]{"Electrode"});
        EVOLUTION_CHAINS.put("Exeggcute", new String[]{"Exeggutor"});
        EVOLUTION_CHAINS.put("Cubone", new String[]{"Marowak"});
        EVOLUTION_CHAINS.put("Koffing", new String[]{"Weezing"});
        EVOLUTION_CHAINS.put("Rhyhorn", new String[]{"Rhydon", "Rhyperior"});
        EVOLUTION_CHAINS.put("Horsea", new String[]{"Seadra", "Kingdra"});
        EVOLUTION_CHAINS.put("Goldeen", new String[]{"Seaking"});
        EVOLUTION_CHAINS.put("Staryu", new String[]{"Starmie"});
        EVOLUTION_CHAINS.put("Magikarp", new String[]{"Gyarados"});
        EVOLUTION_CHAINS.put("Dratini", new String[]{"Dragonair", "Dragonite"});

        // Gen 2
        EVOLUTION_CHAINS.put("Chikorita", new String[]{"Bayleef", "Meganium"});
        EVOLUTION_CHAINS.put("Cyndaquil", new String[]{"Quilava", "Typhlosion"});
        EVOLUTION_CHAINS.put("Totodile", new String[]{"Croconaw", "Feraligatr"});
        EVOLUTION_CHAINS.put("Sentret", new String[]{"Furret"});
        EVOLUTION_CHAINS.put("Hoothoot", new String[]{"Noctowl"});
        EVOLUTION_CHAINS.put("Ledyba", new String[]{"Ledian"});
        EVOLUTION_CHAINS.put("Spinarak", new String[]{"Ariados"});
        EVOLUTION_CHAINS.put("Chinchou", new String[]{"Lanturn"});
        EVOLUTION_CHAINS.put("Pichu", new String[]{"Pikachu", "Raichu"});
        EVOLUTION_CHAINS.put("Cleffa", new String[]{"Clefairy", "Clefable"});
        EVOLUTION_CHAINS.put("Igglybuff", new String[]{"Jigglypuff", "Wigglytuff"});
        EVOLUTION_CHAINS.put("Togepi", new String[]{"Togetic", "Togekiss"});
        EVOLUTION_CHAINS.put("Natu", new String[]{"Xatu"});
        EVOLUTION_CHAINS.put("Mareep", new String[]{"Flaaffy", "Ampharos"});
        EVOLUTION_CHAINS.put("Marill", new String[]{"Azumarill"});
        EVOLUTION_CHAINS.put("Hoppip", new String[]{"Skiploom", "Jumpluff"});
        EVOLUTION_CHAINS.put("Sunkern", new String[]{"Sunflora"});
        EVOLUTION_CHAINS.put("Wooper", new String[]{"Quagsire"});
        EVOLUTION_CHAINS.put("Swinub", new String[]{"Piloswine", "Mamoswine"});
        EVOLUTION_CHAINS.put("Remoraid", new String[]{"Octillery"});
        EVOLUTION_CHAINS.put("Houndour", new String[]{"Houndoom"});
        EVOLUTION_CHAINS.put("Phanpy", new String[]{"Donphan"});
        EVOLUTION_CHAINS.put("Larvitar", new String[]{"Pupitar", "Tyranitar"});

        // Gen 3
        EVOLUTION_CHAINS.put("Treecko", new String[]{"Grovyle", "Sceptile"});
        EVOLUTION_CHAINS.put("Torchic", new String[]{"Combusken", "Blaziken"});
        EVOLUTION_CHAINS.put("Mudkip", new String[]{"Marshtomp", "Swampert"});
        EVOLUTION_CHAINS.put("Poochyena", new String[]{"Mightyena"});
        EVOLUTION_CHAINS.put("Zigzagoon", new String[]{"Linoone"});
        EVOLUTION_CHAINS.put("Wurmple", new String[]{"Silcoon", "Beautifly"}); // Or Cascoon -> Dustox
        EVOLUTION_CHAINS.put("Lotad", new String[]{"Lombre", "Ludicolo"});
        EVOLUTION_CHAINS.put("Seedot", new String[]{"Nuzleaf", "Shiftry"});
        EVOLUTION_CHAINS.put("Taillow", new String[]{"Swellow"});
        EVOLUTION_CHAINS.put("Wingull", new String[]{"Pelipper"});
        EVOLUTION_CHAINS.put("Ralts", new String[]{"Kirlia", "Gardevoir"}); // Or Gallade
        EVOLUTION_CHAINS.put("Surskit", new String[]{"Masquerain"});
        EVOLUTION_CHAINS.put("Shroomish", new String[]{"Breloom"});
        EVOLUTION_CHAINS.put("Slakoth", new String[]{"Vigoroth", "Slaking"});
        EVOLUTION_CHAINS.put("Nincada", new String[]{"Ninjask"}); // And Shedinja
        EVOLUTION_CHAINS.put("Whismur", new String[]{"Loudred", "Exploud"});
        EVOLUTION_CHAINS.put("Makuhita", new String[]{"Hariyama"});
        EVOLUTION_CHAINS.put("Azurill", new String[]{"Marill", "Azumarill"});
        EVOLUTION_CHAINS.put("Aron", new String[]{"Lairon", "Aggron"});
        EVOLUTION_CHAINS.put("Meditite", new String[]{"Medicham"});
        EVOLUTION_CHAINS.put("Electrike", new String[]{"Manectric"});
        EVOLUTION_CHAINS.put("Gulpin", new String[]{"Swalot"});
        EVOLUTION_CHAINS.put("Carvanha", new String[]{"Sharpedo"});
        EVOLUTION_CHAINS.put("Wailmer", new String[]{"Wailord"});
        EVOLUTION_CHAINS.put("Numel", new String[]{"Camerupt"});
        EVOLUTION_CHAINS.put("Spoink", new String[]{"Grumpig"});
        EVOLUTION_CHAINS.put("Trapinch", new String[]{"Vibrava", "Flygon"});
        EVOLUTION_CHAINS.put("Cacnea", new String[]{"Cacturne"});
        EVOLUTION_CHAINS.put("Swablu", new String[]{"Altaria"});
        EVOLUTION_CHAINS.put("Barboach", new String[]{"Whiscash"});
        EVOLUTION_CHAINS.put("Corphish", new String[]{"Crawdaunt"});
        EVOLUTION_CHAINS.put("Baltoy", new String[]{"Claydol"});
        EVOLUTION_CHAINS.put("Shuppet", new String[]{"Banette"});
        EVOLUTION_CHAINS.put("Duskull", new String[]{"Dusclops", "Dusknoir"});
        EVOLUTION_CHAINS.put("Snorunt", new String[]{"Glalie", "Froslass"});
        EVOLUTION_CHAINS.put("Spheal", new String[]{"Sealeo", "Walrein"});
        EVOLUTION_CHAINS.put("Bagon", new String[]{"Shelgon", "Salamence"});
        EVOLUTION_CHAINS.put("Beldum", new String[]{"Metang", "Metagross"});

        // Gen 4
        EVOLUTION_CHAINS.put("Turtwig", new String[]{"Grotle", "Torterra"});
        EVOLUTION_CHAINS.put("Chimchar", new String[]{"Monferno", "Infernape"});
        EVOLUTION_CHAINS.put("Piplup", new String[]{"Prinplup", "Empoleon"});
        EVOLUTION_CHAINS.put("Starly", new String[]{"Staravia", "Staraptor"});
        EVOLUTION_CHAINS.put("Bidoof", new String[]{"Bibarel"});
        EVOLUTION_CHAINS.put("Kricketot", new String[]{"Kricketune"});
        EVOLUTION_CHAINS.put("Shinx", new String[]{"Luxio", "Luxray"});
        EVOLUTION_CHAINS.put("Cranidos", new String[]{"Rampardos"});
        EVOLUTION_CHAINS.put("Shieldon", new String[]{"Bastiodon"});
        EVOLUTION_CHAINS.put("Drifloon", new String[]{"Drifblim"});
        EVOLUTION_CHAINS.put("Buneary", new String[]{"Lopunny"});
        EVOLUTION_CHAINS.put("Gible", new String[]{"Gabite", "Garchomp"});
        EVOLUTION_CHAINS.put("Riolu", new String[]{"Lucario"});
        EVOLUTION_CHAINS.put("Hippopotas", new String[]{"Hippowdon"});
        EVOLUTION_CHAINS.put("Skorupi", new String[]{"Drapion"});
        EVOLUTION_CHAINS.put("Croagunk", new String[]{"Toxicroak"});
        EVOLUTION_CHAINS.put("Snover", new String[]{"Abomasnow"});

        // Gen 5
        EVOLUTION_CHAINS.put("Lillipup", new String[]{"Herdier", "Stoutland"});
        EVOLUTION_CHAINS.put("Pidove", new String[]{"Tranquill", "Unfezant"});
        EVOLUTION_CHAINS.put("Roggenrola", new String[]{"Boldore", "Gigalith"});
        EVOLUTION_CHAINS.put("Timburr", new String[]{"Gurdurr", "Conkeldurr"});
        EVOLUTION_CHAINS.put("Tympole", new String[]{"Palpitoad", "Seismitoad"});
        EVOLUTION_CHAINS.put("Venipede", new String[]{"Whirlipede", "Scolipede"});
        EVOLUTION_CHAINS.put("Sandile", new String[]{"Krokorok", "Krookodile"});
        EVOLUTION_CHAINS.put("Darumaka", new String[]{"Darmanitan"});
        EVOLUTION_CHAINS.put("Dwebble", new String[]{"Crustle"});
        EVOLUTION_CHAINS.put("Scraggy", new String[]{"Scrafty"});
        EVOLUTION_CHAINS.put("Yamask", new String[]{"Cofagrigus"});
        EVOLUTION_CHAINS.put("Tirtouga", new String[]{"Carracosta"});
        EVOLUTION_CHAINS.put("Archen", new String[]{"Archeops"});
        EVOLUTION_CHAINS.put("Zorua", new String[]{"Zoroark"});
        EVOLUTION_CHAINS.put("Gothita", new String[]{"Gothorita", "Gothitelle"});
        EVOLUTION_CHAINS.put("Solosis", new String[]{"Duosion", "Reuniclus"});
        EVOLUTION_CHAINS.put("Vanillite", new String[]{"Vanillish", "Vanilluxe"});
        EVOLUTION_CHAINS.put("Axew", new String[]{"Fraxure", "Haxorus"});
        EVOLUTION_CHAINS.put("Litwick", new String[]{"Lampent", "Chandelure"});
        EVOLUTION_CHAINS.put("Golett", new String[]{"Golurk"});
        EVOLUTION_CHAINS.put("Deino", new String[]{"Zweilous", "Hydreigon"});

        // Gen 6
        EVOLUTION_CHAINS.put("Bunnelby", new String[]{"Diggersby"});
        EVOLUTION_CHAINS.put("Fletchling", new String[]{"Fletchinder", "Talonflame"});
        EVOLUTION_CHAINS.put("Litleo", new String[]{"Pyroar"});
        EVOLUTION_CHAINS.put("Skiddo", new String[]{"Gogoat"});
        EVOLUTION_CHAINS.put("Pancham", new String[]{"Pangoro"});
        EVOLUTION_CHAINS.put("Honedge", new String[]{"Doublade", "Aegislash"});
        EVOLUTION_CHAINS.put("Spritzee", new String[]{"Aromatisse"});
        EVOLUTION_CHAINS.put("Swirlix", new String[]{"Slurpuff"});
        EVOLUTION_CHAINS.put("Inkay", new String[]{"Malamar"});
        EVOLUTION_CHAINS.put("Binacle", new String[]{"Barbaracle"});
        EVOLUTION_CHAINS.put("Helioptile", new String[]{"Heliolisk"});
        EVOLUTION_CHAINS.put("Goomy", new String[]{"Sliggoo", "Goodra"});
        EVOLUTION_CHAINS.put("Phantump", new String[]{"Trevenant"});
        EVOLUTION_CHAINS.put("Pumpkaboo", new String[]{"Gourgeist"});
        EVOLUTION_CHAINS.put("Bergmite", new String[]{"Avalugg"});
        EVOLUTION_CHAINS.put("Noibat", new String[]{"Noivern"});

        // Gen 7
        EVOLUTION_CHAINS.put("Rowlet", new String[]{"Dartrix", "Decidueye"});
        EVOLUTION_CHAINS.put("Litten", new String[]{"Torracat", "Incineroar"});
        EVOLUTION_CHAINS.put("Popplio", new String[]{"Brionne", "Primarina"});
        EVOLUTION_CHAINS.put("Pikipek", new String[]{"Trumbeak", "Toucannon"});
        EVOLUTION_CHAINS.put("Yungoos", new String[]{"Gumshoos"});
        EVOLUTION_CHAINS.put("Grubbin", new String[]{"Charjabug", "Vikavolt"});
        EVOLUTION_CHAINS.put("Rockruff", new String[]{"Lycanroc"});
        EVOLUTION_CHAINS.put("Mareanie", new String[]{"Toxapex"});
        EVOLUTION_CHAINS.put("Dewpider", new String[]{"Araquanid"});
        EVOLUTION_CHAINS.put("Fomantis", new String[]{"Lurantis"});
        EVOLUTION_CHAINS.put("Morelull", new String[]{"Shiinotic"});
        EVOLUTION_CHAINS.put("Salandit", new String[]{"Salazzle"});
        EVOLUTION_CHAINS.put("Stufful", new String[]{"Bewear"});
        EVOLUTION_CHAINS.put("Bounsweet", new String[]{"Steenee", "Tsareena"});
        EVOLUTION_CHAINS.put("Wimpod", new String[]{"Golisopod"});
        EVOLUTION_CHAINS.put("Sandygast", new String[]{"Palossand"});
        EVOLUTION_CHAINS.put("Jangmo-o", new String[]{"Hakamo-o", "Kommo-o"});

        GameLogger.info("Initialized " + EVOLUTION_CHAINS.size() + " evolution chains.");
    }

    private String getEvolvedForm(String baseSpecies, float distance) {
        if (!EVOLUTION_CHAINS.containsKey(baseSpecies)) {
            return baseSpecies;
        }

        String[] chain = EVOLUTION_CHAINS.get(baseSpecies);
        float rand = random.nextFloat();

        if (chain.length > 1 && distance > EVOLUTION_THRESHOLD_2) {
            float chance = (distance - EVOLUTION_THRESHOLD_2) / (2000 * TILE_SIZE);
            if (rand < Math.min(0.6f, chance)) return chain[1];
        }

        if (distance > EVOLUTION_THRESHOLD_1) {
            float chance = (distance - EVOLUTION_THRESHOLD_1) / (1000 * TILE_SIZE);
            if (rand < Math.min(0.7f, chance)) return chain[0];
        }

        return baseSpecies;
    }

    private void checkSpawns(Vector2 playerPos) {
        if (random.nextFloat() > BASE_SPAWN_RATE) {
            return;
        }

        Set<Vector2> loadedChunks = getLoadedChunksAroundPlayer(playerPos);
        if (loadedChunks.isEmpty()) {
            GameLogger.error("No loaded chunks found around player");
            return;
        }

        SpawnAttemptResult result = attemptSpawn(playerPos, loadedChunks);
        if (!result.successful) {
            GameLogger.info("Failed to spawn after " + result.attempts + " attempts");
        }
    }

    private SpawnAttemptResult attemptSpawn(Vector2 playerPos, Set<Vector2> loadedChunks) {
        int maxAttempts = 15;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            SpawnPosition spawnPos = generateSpawnPosition(playerPos, loadedChunks);
            if (spawnPos == null) continue;

            String baseSpecies = selectPokemonForBiome(spawnPos.biome);
            if (baseSpecies == null) continue;

            float distanceFromOrigin = Vector2.dst(spawnPos.pixelX, spawnPos.pixelY, 0, 0);
            String finalSpecies = getEvolvedForm(baseSpecies, distanceFromOrigin);
            boolean shouldSpawnPack = shouldSpawnAsPack(finalSpecies);

            if (shouldSpawnPack) {
                PackSpawnResult packResult = spawnPokemonPack(spawnPos.pixelX, spawnPos.pixelY, spawnPos.chunkPos, finalSpecies);
                if (packResult.spawned > 0) {
                    GameLogger.info(String.format("Successfully spawned pack of %d %s at (%.0f, %.0f)",
                        packResult.spawned, finalSpecies, spawnPos.pixelX, spawnPos.pixelY));
                    return new SpawnAttemptResult(true, attempt + 1);
                }
            } else {
                if (spawnSinglePokemon(spawnPos.pixelX, spawnPos.pixelY, spawnPos.chunkPos, finalSpecies)) {
                    GameLogger.info(String.format("Successfully spawned %s (Lvl %d) at (%.0f, %.0f)",
                        finalSpecies, PokemonLevelCalculator.calculateLevel(spawnPos.pixelX, spawnPos.pixelY, TILE_SIZE), spawnPos.pixelX, spawnPos.pixelY));
                    return new SpawnAttemptResult(true, attempt + 1);
                }
            }
        }

        return new SpawnAttemptResult(false, maxAttempts);
    }

    private SpawnPosition generateSpawnPosition(Vector2 playerPos, Set<Vector2> loadedChunks) {
        float angle = random.nextFloat() * MathUtils.PI2;
        float distance = MathUtils.random(MIN_SPAWN_DISTANCE_PIXELS, MAX_SPAWN_DISTANCE_PIXELS);
        float spawnPixelX = playerPos.x * TILE_SIZE + MathUtils.cos(angle) * distance;
        float spawnPixelY = playerPos.y * TILE_SIZE + MathUtils.sin(angle) * distance;

        Vector2 chunkPos = getChunkPosition(spawnPixelX, spawnPixelY);
        if (!loadedChunks.contains(chunkPos)) {
            return null;
        }
        List<WildPokemon> chunkPokemon = pokemonByChunk.getOrDefault(chunkPos, new ArrayList<>());
        if (chunkPokemon.size() >= MAX_POKEMON_PER_CHUNK) {
            return null;
        }
        float snappedX = Math.round(spawnPixelX / TILE_SIZE) * TILE_SIZE;
        float snappedY = Math.round(spawnPixelY / TILE_SIZE) * TILE_SIZE;

        if (!isValidSpawnPosition(snappedX, snappedY)) {
            return null;
        }
        int tileX = (int) (snappedX / TILE_SIZE);
        int tileY = (int) (snappedY / TILE_SIZE);
        Biome biome = GameContext.get().getWorld().getBiomeAt(tileX, tileY);

        return new SpawnPosition(snappedX, snappedY, chunkPos, biome);
    }

    private boolean shouldSpawnAsPack(String species) {
        PackBehaviorInfo packInfo = PACK_SPECIES.get(species.toLowerCase());
        if (packInfo == null) return false;

        return random.nextFloat() < (PACK_SPAWN_CHANCE * packInfo.packChance);
    }

    private PackSpawnResult spawnPokemonPack(float centerX, float centerY, Vector2 chunkPos, String species) {
        PackBehaviorInfo packInfo = PACK_SPECIES.get(species.toLowerCase());
        if (packInfo == null) {
            packInfo = new PackBehaviorInfo(0.3f, MIN_PACK_SIZE, MAX_PACK_SIZE, false);
        }

        int packSize = MathUtils.random(packInfo.minPackSize, packInfo.maxPackSize);
        List<WildPokemon> packMembers = new ArrayList<>();
        Set<UUID> packMemberIds = new HashSet<>();

        GameLogger.info("Attempting to spawn pack of " + packSize + " " + species +
            " at (" + centerX + ", " + centerY + ")");
        WildPokemon leader = createPokemon(species, centerX, centerY, chunkPos);
        if (leader != null) {
            PokemonAI leaderAI = (PokemonAI) leader.getAi();
            if (leaderAI != null) {
                leaderAI.addPackMember(leader.getUuid());
            }

            packMembers.add(leader);
            packMemberIds.add(leader.getUuid());
            List<Vector2> spawnPositions = generatePackPositions(centerX, centerY, packSize - 1);

            for (Vector2 pos : spawnPositions) {
                WildPokemon member = createPokemon(species, pos.x, pos.y, chunkPos);
                if (member != null) {
                    packMembers.add(member);
                    packMemberIds.add(member.getUuid());
                    PokemonAI memberAI = (PokemonAI) member.getAi();
                    if (memberAI != null && leaderAI != null) {
                        memberAI.setPackLeader(leader.getUuid());
                        leaderAI.addPackMember(member.getUuid());
                    }
                }
            }
            if (packMembers.size() > 1) {
                UUID packId = UUID.randomUUID();
                pokemonPacks.put(packId, packMemberIds);

                GameLogger.info("Created pack " + packId + " with " + packMembers.size() +
                    " members of species " + species);
            }
        }

        return new PackSpawnResult(packMembers.size(), packMemberIds);
    }

    private List<Vector2> generatePackPositions(float centerX, float centerY, int memberCount) {
        List<Vector2> positions = new ArrayList<>();
        Set<Vector2> occupiedTiles = new HashSet<>();
        occupiedTiles.add(new Vector2(
            Math.round(centerX / TILE_SIZE) * TILE_SIZE,
            Math.round(centerY / TILE_SIZE) * TILE_SIZE
        ));

        for (int i = 0; i < memberCount; i++) {
            Vector2 position = findValidPackMemberPosition(centerX, centerY, occupiedTiles);
            if (position != null) {
                positions.add(position);
                occupiedTiles.add(position);
            } else {
                GameLogger.info("Could not find valid position for pack member " + (i + 1));
            }
        }

        return positions;
    }

    private Vector2 findValidPackMemberPosition(float centerX, float centerY, Set<Vector2> occupiedTiles) {
        for (float radius = PACK_BASE_RADIUS; radius <= PACK_MAX_RADIUS; radius += TILE_SIZE) {
            Vector2 position = tryCircularPattern(centerX, centerY, radius, occupiedTiles);
            if (position != null) return position;
        }
        Vector2 position = tryGridPattern(centerX, centerY, occupiedTiles);
        if (position != null) return position;
        return tryRandomPattern(centerX, centerY, occupiedTiles);
    }

    private Vector2 tryCircularPattern(float centerX, float centerY, float radius, Set<Vector2> occupied) {
        int numPoints = Math.max(8, (int) (radius / TILE_SIZE) * 4);

        for (int i = 0; i < numPoints; i++) {
            float angle = (i * MathUtils.PI2) / numPoints;
            float x = centerX + MathUtils.cos(angle) * radius;
            float y = centerY + MathUtils.sin(angle) * radius;
            float snappedX = Math.round(x / TILE_SIZE) * TILE_SIZE;
            float snappedY = Math.round(y / TILE_SIZE) * TILE_SIZE;
            Vector2 testPos = new Vector2(snappedX, snappedY);

            if (!occupied.contains(testPos) && isValidSpawnPosition(snappedX, snappedY)) {
                return testPos;
            }
        }

        return null;
    }

    private Vector2 tryGridPattern(float centerX, float centerY, Set<Vector2> occupied) {
        int maxOffset = (int) (PACK_MAX_RADIUS / TILE_SIZE);
        for (int offset = 1; offset <= maxOffset; offset++) {
            for (int dx = -offset; dx <= offset; dx++) {
                for (int dy = -offset; dy <= offset; dy++) {
                    if (Math.abs(dx) != offset && Math.abs(dy) != offset) continue;

                    float x = centerX + dx * TILE_SIZE;
                    float y = centerY + dy * TILE_SIZE;
                    Vector2 testPos = new Vector2(x, y);

                    if (!occupied.contains(testPos) && isValidSpawnPosition(x, y)) {
                        return testPos;
                    }
                }
            }
        }

        return null;
    }

    private Vector2 tryRandomPattern(float centerX, float centerY, Set<Vector2> occupied) {
        for (int attempt = 0; attempt < 20; attempt++) {
            float angle = random.nextFloat() * MathUtils.PI2;
            float distance = MathUtils.random(TILE_SIZE, PACK_MAX_RADIUS);
            float x = centerX + MathUtils.cos(angle) * distance;
            float y = centerY + MathUtils.sin(angle) * distance;
            float snappedX = Math.round(x / TILE_SIZE) * TILE_SIZE;
            float snappedY = Math.round(y / TILE_SIZE) * TILE_SIZE;
            Vector2 testPos = new Vector2(snappedX, snappedY);

            if (!occupied.contains(testPos) && isValidSpawnPosition(snappedX, snappedY)) {
                return testPos;
            }
        }

        return null;
    }

    private WildPokemon createPokemon(String species, float x, float y, Vector2 chunkPos) {
        try {
            TextureRegion sprite = TextureManager.getOverworldSprite(species);
            if (sprite == null) {
                GameLogger.error("Failed to load overworld sprite for " + species);
                return null;
            }

            WildPokemon pokemon = new WildPokemon(
                species,
                PokemonLevelCalculator.calculateLevel(x, y, TILE_SIZE),
                (int) x,
                (int) y,
                sprite
            );

            pokemon.setWorld(GameContext.get().getWorld());
            PokemonAI enhancedAI = new PokemonAI(pokemon);
            pokemon.setAi(enhancedAI);

            enhancedAI.enterIdleState();
            pokemonById.put(pokemon.getUuid(), pokemon);
            pokemonByChunk.computeIfAbsent(chunkPos, k -> new ArrayList<>()).add(pokemon);
            pokemon.updateBoundingBox();

            return pokemon;
        } catch (Exception e) {
            GameLogger.error("Failed to create Pokemon " + species + ": " + e.getMessage());
            return null;
        }
    }

    private boolean spawnSinglePokemon(float x, float y, Vector2 chunkPos, String species) {
        return createPokemon(species, x, y, chunkPos) != null;
    }

    private boolean isValidSpawnPosition(float pixelX, float pixelY) {
        int tileX = (int) (pixelX / TILE_SIZE);
        int tileY = (int) (pixelY / TILE_SIZE);

        if (GameContext.get().getWorld() == null) {
            GameLogger.error("World reference is null in spawn validation");
            return false;
        }
        if (!GameContext.get().getWorld().isPassable(tileX, tileY)) {
            return false;
        }
        Collection<WildPokemon> nearby = getPokemonInRange(pixelX, pixelY, MIN_POKEMON_SPACING);
        if (!nearby.isEmpty()) {
            return false;
        }
        Vector2 chunkPos = getChunkPosition(pixelX, pixelY);
        return GameContext.get().getWorld().getChunks().containsKey(chunkPos);
    }

    public void update(float delta, Vector2 playerPosition) {
        spawnTimer += delta;
        if (!GameContext.get().isMultiplayer()) {
            if (spawnTimer >= SPAWN_CHECK_INTERVAL) {
                spawnTimer = 0;
                checkSpawns(playerPosition);
                removeExpiredPokemon();
            }
        }

        // --- START: REPLACEMENT LOGIC FOR THE UPDATE LOOP ---
        Vector2 playerPixelPos = new Vector2(GameContext.get().getPlayer().getX(), GameContext.get().getPlayer().getY());

        // Define an update radius. Pokémon outside this range won't be updated.
        // 40 tiles is a generous radius. Use squared distance to avoid expensive square root calculations.
        float updateRadiusSquared = (40 * TILE_SIZE) * (40 * TILE_SIZE);

        for (WildPokemon pokemon : pokemonById.values()) {
            // Check squared distance for performance.
            if (pokemon.getPosition().dst2(playerPixelPos) < updateRadiusSquared) {
                try {
                    // Only update Pokémon that are nearby.
                    pokemon.update(delta, GameContext.get().getWorld());
                } catch (Exception e) {
                    GameLogger.error("Error updating " + pokemon.getName() + ": " + e.getMessage());
                    e.printStackTrace(); // Keep this for debugging
                }
            }
        }
        // --- END: REPLACEMENT LOGIC ---
    }

    public Collection<WildPokemon> getPokemonInRange(float centerPixelX, float centerPixelY, float rangePixels) {
        List<WildPokemon> inRange = new ArrayList<>();
        float rangeSquared = rangePixels * rangePixels;

        for (WildPokemon pokemon : pokemonById.values()) {
            float dx = pokemon.getX() - centerPixelX;
            float dy = pokemon.getY() - centerPixelY;

            if (dx * dx + dy * dy <= rangeSquared) {
                inRange.add(pokemon);
            }
        }

        return inRange;
    }

    private Set<Vector2> getLoadedChunksAroundPlayer(Vector2 playerPixelPos) {
        Set<Vector2> loadedChunks = new HashSet<>();

        Vector2 playerChunk = getChunkPosition(playerPixelPos.x * TILE_SIZE, playerPixelPos.y * TILE_SIZE);

        if (GameContext.get().getWorld() == null) {
            GameLogger.error("World reference is null!");
            return loadedChunks;
        }

        Map<Vector2, Chunk> worldChunks = GameContext.get().getWorld().getChunks();
        int radius = 1;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                Vector2 checkPos = new Vector2(playerChunk.x + dx, playerChunk.y + dy);
                if (worldChunks.containsKey(checkPos)) {
                    loadedChunks.add(checkPos);
                }
            }
        }

        return loadedChunks;
    }

    private Vector2 getChunkPosition(float pixelX, float pixelY) {
        int chunkX = Math.floorDiv((int) pixelX, World.CHUNK_SIZE * TILE_SIZE);
        int chunkY = Math.floorDiv((int) pixelY, World.CHUNK_SIZE * TILE_SIZE);
        return new Vector2(chunkX, chunkY);
    }

    public void removePokemon(UUID pokemonId) {
        WildPokemon pokemon = pokemonById.remove(pokemonId);
        if (pokemon != null) {
            Vector2 chunkPos = getChunkPosition(pokemon.getX(), pokemon.getY());
            List<WildPokemon> pokemonList = pokemonByChunk.get(chunkPos);
            if (pokemonList != null) {
                pokemonList.remove(pokemon);
                if (pokemonList.isEmpty()) {
                    pokemonByChunk.remove(chunkPos);
                }
            }
            syncedPokemon.remove(pokemonId);
            removeFromPacks(pokemonId);
        }
    }

    private void removeFromPacks(UUID pokemonId) {
        pokemonPacks.entrySet().removeIf(entry -> {
            entry.getValue().remove(pokemonId);
            return entry.getValue().isEmpty();
        });
    }

    private void removeExpiredPokemon() {
        List<UUID> toRemove = new ArrayList<>();
        for (WildPokemon pokemon : pokemonById.values()) {
            if (pokemon.isExpired()) {
                toRemove.add(pokemon.getUuid());
                Vector2 chunkPos = getChunkPosition(pokemon.getX(), pokemon.getY());
                List<WildPokemon> pokemonList = pokemonByChunk.get(chunkPos);
                if (pokemonList != null) {
                    pokemonList.remove(pokemon);
                }
            }
        }

        for (UUID id : toRemove) {
            pokemonById.remove(id);
        }
    }

    /**
     * MODIFIED: Massively expanded spawn lists for all biomes, separated by day and night.
     * Includes a wide variety of Pokémon from Generations 1 through 7.
     */
    private void initializePokemonSpawns() {
        // PLAINS
        Map<TimeOfDay, String[]> plainsSpawns = new HashMap<>();
        plainsSpawns.put(TimeOfDay.DAY, new String[]{"Pidgey", "Rattata", "Sentret", "Bidoof", "Lillipup", "Fletchling", "Bunnelby", "Doduo", "Tauros", "Miltank", "Deerling", "Skiddo", "Yungoos"});
        plainsSpawns.put(TimeOfDay.NIGHT, new String[]{"Hoothoot", "Rattata", "Meowth", "Shinx", "Litleo", "Starly", "Rockruff"});
        POKEMON_SPAWNS.put(BiomeType.PLAINS, plainsSpawns);

        // FOREST
        Map<TimeOfDay, String[]> forestSpawns = new HashMap<>();
        forestSpawns.put(TimeOfDay.DAY, new String[]{"Caterpie", "Weedle", "Pikachu", "Oddish", "Paras", "Venonat", "Scyther", "Pinsir", "Wurmple", "Seedot", "Slakoth", "Nincada", "Stantler", "Phantump", "Fomantis", "Morelull", "Grubbin"});
        forestSpawns.put(TimeOfDay.NIGHT, new String[]{"Zubat", "Hoothoot", "Spinarak", "Gastly", "Misdreavus", "Shuppet", "Zorua", "Pumpkaboo", "Rowlet"});
        POKEMON_SPAWNS.put(BiomeType.FOREST, forestSpawns);

        // SNOW
        Map<TimeOfDay, String[]> snowSpawns = new HashMap<>();
        snowSpawns.put(TimeOfDay.DAY, new String[]{"Swinub", "Snorunt", "Snover", "Spheal", "Cubchoo", "Vanillite", "Bergmite", "Delibird", "Crabrawler"});
        snowSpawns.put(TimeOfDay.NIGHT, new String[]{"Sneasel", "Snorunt", "Cryogonal", "Cubchoo", "Froslass"});
        POKEMON_SPAWNS.put(BiomeType.SNOW, snowSpawns);

        // DESERT
        Map<TimeOfDay, String[]> desertSpawns = new HashMap<>();
        desertSpawns.put(TimeOfDay.DAY, new String[]{"Sandshrew", "Trapinch", "Cacnea", "Sandile", "Diglett", "Helioptile", "Hippopotas", "Dwebble", "Darumaka"});
        desertSpawns.put(TimeOfDay.NIGHT, new String[]{"Sandshrew", "Cacnea", "Scraggy", "Yamask", "Krokorok", "Houndour", "Salandit"});
        POKEMON_SPAWNS.put(BiomeType.DESERT, desertSpawns);

        // HAUNTED
        Map<TimeOfDay, String[]> hauntedSpawns = new HashMap<>();
        hauntedSpawns.put(TimeOfDay.NIGHT, new String[]{"Gastly", "Haunter", "Misdreavus", "Shuppet", "Duskull", "Sableye", "Litwick", "Phantump", "Pumpkaboo", "Zorua", "Mimikyu", "Honedge"});
        POKEMON_SPAWNS.put(BiomeType.HAUNTED, hauntedSpawns);

        // RAIN FOREST
        Map<TimeOfDay, String[]> rainForestSpawns = new HashMap<>();
        rainForestSpawns.put(TimeOfDay.DAY, new String[]{"Bulbasaur", "Caterpie", "Bellsprout", "Exeggcute", "Scyther", "Treecko", "Shroomish", "Slakoth", "Turtwig", "Pansage", "Dewpider", "Bounsweet", "Comfey"});
        rainForestSpawns.put(TimeOfDay.NIGHT, new String[]{"Oddish", "Gloom", "Venonat", "Spinarak", "Hoothoot", "Foongus", "Croagunk", "Morelull"});
        POKEMON_SPAWNS.put(BiomeType.RAIN_FOREST, rainForestSpawns);

        // BIG_MOUNTAINS
        Map<TimeOfDay, String[]> mountainSpawns = new HashMap<>();
        mountainSpawns.put(TimeOfDay.DAY, new String[]{"Geodude", "Machop", "Onix", "Rhyhorn", "Larvitar", "Aron", "Meditite", "Numel", "Bagon", "Gible", "Riolu", "Jangmo-o", "Rockruff"});
        mountainSpawns.put(TimeOfDay.NIGHT, new String[]{"Geodude", "Zubat", "Clefairy", "Onix", "Aron", "Sableye", "Bagon", "Gible", "Carbink"});
        POKEMON_SPAWNS.put(BiomeType.BIG_MOUNTAINS, mountainSpawns);

        // RUINS
        Map<TimeOfDay, String[]> ruinsSpawns = new HashMap<>();
        ruinsSpawns.put(TimeOfDay.DAY, new String[]{"Zubat", "Geodude", "Cubone", "Baltoy", "Golett", "Yamask", "Bronzor", "Sigilyph"});
        ruinsSpawns.put(TimeOfDay.NIGHT, new String[]{"Zubat", "Gastly", "Duskull", "Unown", "Cofagrigus", "Spiritomb"});
        POKEMON_SPAWNS.put(BiomeType.RUINS, ruinsSpawns);

        // CHERRY_GROVE
        Map<TimeOfDay, String[]> cherryGroveSpawns = new HashMap<>();
        cherryGroveSpawns.put(TimeOfDay.DAY, new String[]{"Cherrim", "Budew", "Roselia", "Jigglypuff", "Flabébé", "Spritzee", "Swirlix", "Eevee", "Cutiefly"});
        cherryGroveSpawns.put(TimeOfDay.NIGHT, new String[]{"Cleffa", "Clefairy", "Jigglypuff", "Ralts", "Cottonee", "Togepi", "Morelull"});
        POKEMON_SPAWNS.put(BiomeType.CHERRY_GROVE, cherryGroveSpawns);

        // BEACH
        Map<TimeOfDay, String[]> beachSpawns = new HashMap<>();
        beachSpawns.put(TimeOfDay.DAY, new String[]{"Krabby", "Wingull", "Staryu", "Shellder", "Corphish", "Frillish", "Binacle", "Wimpod", "Crabrawler", "Mareanie", "Pyukumuku"});
        beachSpawns.put(TimeOfDay.NIGHT, new String[]{"Krabby", "Wingull", "Staryu", "Shellder", "Chinchou", "Frillish", "Mareanie"});
        POKEMON_SPAWNS.put(BiomeType.BEACH, beachSpawns);

        // OCEAN
        Map<TimeOfDay, String[]> oceanSpawns = new HashMap<>();
        oceanSpawns.put(TimeOfDay.DAY, new String[]{"Magikarp", "Tentacool", "Horsea", "Wailmer", "Finneon", "Frillish", "Skrelp", "Clauncher", "Wishiwashi"});
        oceanSpawns.put(TimeOfDay.NIGHT, new String[]{"Magikarp", "Tentacool", "Chinchou", "Luvdisc", "Mantyke", "Wishiwashi", "Dhelmise"});
        POKEMON_SPAWNS.put(BiomeType.OCEAN, oceanSpawns);
    }

    private String selectPokemonForBiome(Biome biome) {
        GameLogger.info("Selecting Pokemon for biome: " + biome.getType());

        double worldTimeInMinutes = GameContext.get().getWorld().getWorldData().getWorldTimeInMinutes();
        float hourOfDay = DayNightCycle.getHourOfDay(worldTimeInMinutes);
        TimeOfDay timeOfDay = (hourOfDay >= 6 && hourOfDay < 18) ? TimeOfDay.DAY : TimeOfDay.NIGHT;

        Map<TimeOfDay, String[]> biomeSpawns = POKEMON_SPAWNS.get(biome.getType());
        if (biomeSpawns == null) {
            return getDefaultPokemon(timeOfDay);
        }

        String[] possiblePokemon = biomeSpawns.get(timeOfDay);
        if (possiblePokemon == null || possiblePokemon.length == 0) {
            return getDefaultPokemon(timeOfDay);
        }

        return possiblePokemon[random.nextInt(possiblePokemon.length)];
    }

    private String getDefaultPokemon(TimeOfDay timeOfDay) {
        return timeOfDay == TimeOfDay.DAY ? "Rattata" : "Hoothoot";
    }

    public Collection<WildPokemon> getAllWildPokemon() {
        return pokemonById.values();
    }

    public void addPokemonToChunk(WildPokemon pokemon, Vector2 chunkPos) {
        try {
            List<WildPokemon> pokemonList = pokemonByChunk.computeIfAbsent(chunkPos, k -> new ArrayList<>());

            if (!pokemonList.contains(pokemon)) {
                pokemonList.add(pokemon);
                pokemonById.put(pokemon.getUuid(), pokemon);
            }
        } catch (Exception e) {
            GameLogger.error("Error adding Pokémon to chunk at " + chunkPos + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static class PackBehaviorInfo {
        final float packChance;
        final int minPackSize;
        final int maxPackSize;
        final boolean aggressive;

        PackBehaviorInfo(float packChance, int minPackSize, int maxPackSize, boolean aggressive) {
            this.packChance = packChance;
            this.minPackSize = minPackSize;
            this.maxPackSize = maxPackSize;
            this.aggressive = aggressive;
        }
    }

    private static class SpawnPosition {
        final float pixelX, pixelY;
        final Vector2 chunkPos;
        final Biome biome;

        SpawnPosition(float pixelX, float pixelY, Vector2 chunkPos, Biome biome) {
            this.pixelX = pixelX;
            this.pixelY = pixelY;
            this.chunkPos = chunkPos;
            this.biome = biome;
        }
    }

    private static class SpawnAttemptResult {
        final boolean successful;
        final int attempts;

        SpawnAttemptResult(boolean successful, int attempts) {
            this.successful = successful;
            this.attempts = attempts;
        }
    }

    private static class PackSpawnResult {
        final int spawned;
        final Set<UUID> memberIds;

        PackSpawnResult(int spawned, Set<UUID> memberIds) {
            this.spawned = spawned;
            this.memberIds = memberIds;
        }
    }

    public enum TimeOfDay {
        DAY, NIGHT
    }

    public static class NetworkSyncData {
        public long lastUpdateTime;
        public Vector2 targetPosition;
        public String direction;
        public boolean isMoving;

        public NetworkSyncData() {
            this.lastUpdateTime = System.currentTimeMillis();
        }
    }
}
