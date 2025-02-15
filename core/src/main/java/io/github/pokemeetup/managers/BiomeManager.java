package io.github.pokemeetup.managers;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.OpenSimplex2;
import io.github.pokemeetup.utils.storage.GameFileSystem;
import io.github.pokemeetup.utils.textures.TileType;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Updated BiomeManager for large, diverse, AAA–style worlds.
 * <p>
 * Features:
 * - Domain–warp once to decide ocean/beach/land.
 * - If land, we do a Voronoi fallback approach to pick a local site → get "base" temperature & moisture → final classification.
 * - Islands are generated randomly (for the chunk generator).
 * - Gradual transitions, more realistic biome decisions, special “rare” biomes.
 */
public class BiomeManager {

    // ─────────────────────────────────────────────────────────────────────────
    // 1. TUNING CONSTANTS & FIELD DEFINITIONS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * How many random islands to scatter.
     */
    private static final int ISLAND_COUNT = 50;
    private static final int NUM_BIOME_SITES = 300; // reduced from 1000

    // Lower noise frequencies for broader, smoother variations.
    private static final float TEMPERATURE_SCALE = 0.00002f; // slower changes for larger biomes
    private static final float MOISTURE_SCALE = 0.00002f;    // slower changes for larger biomes

    // Adjust domain warp parameters to produce a smoother warp.
    private static final float FREQ_WARP_1 = 0.0003f; // reduced frequency
    private static final float AMP_WARP_1 = 5f;       // keep amplitude, or adjust if needed
    private static final float FREQ_WARP_2 = 0.0006f;  // reduced frequency
    private static final float AMP_WARP_2 = 2f;        // keep amplitude
    /**
     * Min & max radius for each island.
     */
    private static final float ISLAND_MIN_RADIUS = 3000f;
    private static final float ISLAND_MAX_RADIUS = 8000f;

    /**
     * Overall world boundary radius (in world pixels).
     */
    private static final float WORLD_RADIUS = 50000f;

    /**
     * The number of Voronoi sites used as fallback.
     */

    // If we want to or not store chunk→biome transitions
    private final Map<Vector2, BiomeTransitionResult[][]> chunkBiomeCache = new ConcurrentHashMap<>();
    private final long temperatureSeed;
    private final long moistureSeed;
    private final long detailSeed;
    // Voronoi fallback & island data
    private final List<BiomeSite> biomeSites = new ArrayList<>();
    private final List<Island> islandSites = new ArrayList<>();
    // The warp seed used for domain–warping
    private final long warpSeed;
    // Our loaded or default biomes
    private final Map<BiomeType, Biome> biomes;
    // Seeds
    private long baseSeed;

    // ─────────────────────────────────────────────────────────────────────────
    // 2. CONSTRUCTOR & INITIALIZATION
    // ─────────────────────────────────────────────────────────────────────────
    public BiomeManager(long baseSeed) {
        this.baseSeed = baseSeed;
        this.temperatureSeed = baseSeed + 1000;
        this.moistureSeed = baseSeed + 2000;
        this.detailSeed = baseSeed + 3000;
        this.warpSeed = baseSeed + 4000;

        this.biomes = new HashMap<>();

        loadBiomesFromJson();           // load from your Data/biomes.json or fallback
        generateBiomeSites(baseSeed);    // create random Voronoi sites
        generateRandomIslands(baseSeed); // create random islands
    }

    private static float smoothStep(float t) {
        return t * t * (3f - 2f * t);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. PUBLIC BIOME SAMPLING
    // ─────────────────────────────────────────────────────────────────────────

    public void setBaseSeed(long baseSeed) {
        this.baseSeed = baseSeed;
    }

    public BiomeTransitionResult getBiomeAt(float worldX, float worldY) {
        float[] warped = domainWarp(worldX, worldY);
        return getBiomeFromAlreadyWarped(warped[0], warped[1]);
    }

    /**
     * Called when we already have "warped" coordinates.
     * Checks nearest island => ocean or beach or land => if land => do landBiomeVoronoi.
     */
    public BiomeTransitionResult getBiomeFromAlreadyWarped(float wx, float wy) {
        Island isl = findClosestIsland(wx, wy);
        if (isl == null) {
            // No island found – return ocean.
            return new BiomeTransitionResult(getBiome(BiomeType.OCEAN), null, 1f);
        }
        // Distance from island center
        float dx = wx - isl.centerX;
        float dy = wy - isl.centerY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        // Compute angle and distortion value.
        float angle = MathUtils.atan2(dy, dx);
        float distort = OpenSimplex2.noise2(isl.seed, MathUtils.cos(angle), MathUtils.sin(angle));
        distort = Math.max(0f, distort);

        // Increase island size and reduce noise impact.

        float newExpandFactor = 1.3f;
        float reducedFactor = 0.1f;
        float effectiveRadius = isl.radius * newExpandFactor + (isl.radius * newExpandFactor * reducedFactor * distort);

        float beachBand = effectiveRadius * 0.1f;
        float innerThreshold = effectiveRadius;
        float outerThreshold = effectiveRadius + beachBand;

        if (dist < innerThreshold) {
            // Deep inside the island – use the land biome
            return landBiomeVoronoi(wx, wy);
        } else if (dist < outerThreshold) {
            // Entire beach region (no blending)
            return new BiomeTransitionResult(getBiome(BiomeType.BEACH), null, 1f);
        } else {
            // Outside island – pure ocean
            return new BiomeTransitionResult(getBiome(BiomeType.OCEAN), null, 1f);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. BIOME CLASSIFICATION & VORONOI
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * For AI spawns or players: find a "safe spawn" on an island interior (not too close
     * to water), checking passable tiles.
     */
    public Vector2 findSafeSpawnLocation(World world, Random rng) {
        if (islandSites.isEmpty()) {
            GameLogger.error("No islands found; defaulting spawn to (0,0)");
            return new Vector2(0, 0);
        }
        int maxAttempts = 200;
        Island isl = islandSites.get(rng.nextInt(islandSites.size()));
        float margin = 100f;
        float safeRange = Math.max(isl.radius - margin, 20f);

        for (int i = 0; i < maxAttempts; i++) {
            float angle = rng.nextFloat() * MathUtils.PI2;
            float dist = rng.nextFloat() * safeRange;
            float candX = isl.centerX + MathUtils.cos(angle) * dist;
            float candY = isl.centerY + MathUtils.sin(angle) * dist;

            int tileX = MathUtils.floor(candX / World.TILE_SIZE);
            int tileY = MathUtils.floor(candY / World.TILE_SIZE);

            if (world.isWithinWorldBounds(tileX, tileY) && world.isPassable(tileX, tileY)) {
                return new Vector2(tileX, tileY);
            }
        }
        // fallback
        GameLogger.error("Failed to find safe island spawn; use center");
        int cx = MathUtils.floor(isl.centerX / World.TILE_SIZE);
        int cy = MathUtils.floor(isl.centerY / World.TILE_SIZE);
        if (!world.isWithinWorldBounds(cx, cy)) {
            return new Vector2(0, 0);
        }
        return new Vector2(cx, cy);
    }

    /**
     * If the tile is land, we do a Voronoi approach: find the nearest & second–nearest site,
     * then do a blending factor. That site has a base temperature & moisture offset.
     * Then we pass them to the BiomeClassifier => final BiomeType
     */
    public BiomeTransitionResult landBiomeVoronoi(float wx, float wy) {
        BiomeSite nearest = null;
        BiomeSite second = null;
        double ndist = Double.MAX_VALUE, sdist = Double.MAX_VALUE;

        for (BiomeSite site : biomeSites) {
            double dx = wx - site.x;
            double dy = wy - site.y;
            double dist2 = dx * dx + dy * dy;
            if (dist2 < ndist) {
                second = nearest;
                sdist = ndist;
                nearest = site;
                ndist = dist2;
            } else if (dist2 < sdist) {
                second = site;
                sdist = dist2;
            }
        }
        if (nearest == null) {
            return new BiomeTransitionResult(
                getBiome(BiomeType.PLAINS), null, 1f
            );
        }
        if (second == null) {
            // single site
            Biome b = classifySiteToBiome(nearest, wx, wy);
            return new BiomeTransitionResult(b, null, 1f);
        }

        // Dist ratio
        float d1 = (float) Math.sqrt(ndist);
        float d2 = (float) Math.sqrt(sdist);
        if (d2 < 1e-6f) {
            Biome b = classifySiteToBiome(nearest, wx, wy);
            return new BiomeTransitionResult(b, null, 1f);
        }
        float raw = 1f - (d1 / d2);
        float t = smoothStep(MathUtils.clamp((raw - 0.4f) / 0.15f, 0f, 1f));

        // Then pick biome from each site
        Biome bA = classifySiteToBiome(nearest, wx, wy);
        Biome bB = classifySiteToBiome(second, wx, wy);
        if ((bA.getType() == BiomeType.SNOW && bB.getType() == BiomeType.DESERT) ||
            (bA.getType() == BiomeType.DESERT && bB.getType() == BiomeType.SNOW)) {
            return new BiomeTransitionResult(getBiome(BiomeType.PLAINS), null, 1f);
        }
        return new BiomeTransitionResult(bA, bB, t);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. NOISE & WARP UTILS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Given a BiomeSite (which has a base "tempOffset" & "moistOffset") and our
     * "global" temperature & moisture fields, we produce the final biome.
     */
    private Biome classifySiteToBiome(BiomeSite site, float wx, float wy) {
        double baseTemp = (OpenSimplex2.noise2(temperatureSeed, wx * TEMPERATURE_SCALE, wy * TEMPERATURE_SCALE) + 1) / 2.0;
        double baseMoist = (OpenSimplex2.noise2(moistureSeed, wx * MOISTURE_SCALE, wy * MOISTURE_SCALE) + 1) / 2.0;
        double temp = Math.min(Math.max(0, baseTemp + site.tempOffset), 1);
        double moist = Math.min(Math.max(0, baseMoist + site.moistOffset), 1);
        BiomeType type = BiomeClassifier.determineBiomeType(temp, moist, detailSeed, site.siteDetail);
        return getBiome(type);
    }

    public long getWarpSeed() {
        return warpSeed;
    }

    /**
     * Single domain warp call that uses the two frequencies & amplitudes above.
     */
    public float[] domainWarp(float x, float y) {
        float dx1 = (float) OpenSimplex2.noise2(warpSeed, x * FREQ_WARP_1, y * FREQ_WARP_1) * AMP_WARP_1;
        float dy1 = (float) OpenSimplex2.noise2(warpSeed + 1337, x * FREQ_WARP_1, y * FREQ_WARP_1) * AMP_WARP_1;
        float wx1 = x + dx1;
        float wy1 = y + dy1;

        float dx2 = (float) OpenSimplex2.noise2(warpSeed + 999, wx1 * FREQ_WARP_2, wy1 * FREQ_WARP_2) * AMP_WARP_2;
        float dy2 = (float) OpenSimplex2.noise2(warpSeed + 1999, wx1 * FREQ_WARP_2, wy1 * FREQ_WARP_2) * AMP_WARP_2;

        return new float[]{wx1 + dx2, wy1 + dy2};
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. ISLANDS, BIOME SITES, RANDOM GENERATION
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * We scatter random Voronoi "sites" across the world. Each site also gets
     * a random offset for temperature & moisture, plus a detail seed for local variation.
     */
    private void generateBiomeSites(long seed) {
        biomeSites.clear();
        Random rng = new Random(seed ^ 0xDEADBEEFL);

        for (int i = 0; i < NUM_BIOME_SITES; i++) {
            float x = (rng.nextFloat() - 0.5f) * 2f * WORLD_RADIUS;
            float y = (rng.nextFloat() - 0.5f) * 2f * WORLD_RADIUS;

            // random temp & moisture offset in range [-0.15..+0.15], for example
            double tOff = (rng.nextFloat() - 0.5) * 0.3;
            double mOff = (rng.nextFloat() - 0.5) * 0.3;

            long detail = rng.nextLong();

            BiomeSite site = new BiomeSite(x, y, tOff, mOff, detail);
            biomeSites.add(site);
        }
        GameLogger.info("BiomeManager => created " + biomeSites.size() + " Voronoi sites.");
    }

    private void generateRandomIslands(long seed) {
        islandSites.clear();
        Random rng = new Random(seed ^ 0xBEEF9876L);
        float regionSize = WORLD_RADIUS * 0.85f;
        for (int i = 0; i < ISLAND_COUNT; i++) {
            float cx = -regionSize + rng.nextFloat() * (2 * regionSize);
            float cy = -regionSize + rng.nextFloat() * (2 * regionSize);
            float r = ISLAND_MIN_RADIUS + rng.nextFloat() * (ISLAND_MAX_RADIUS - ISLAND_MIN_RADIUS);
            long islandSeed = rng.nextLong();
            Island isl = new Island(cx, cy, r, islandSeed);
            islandSites.add(isl);
        }
        // FIX: Always add a central island so that the spawn area is on land.
        Island centerIsland = new Island(0, 0, ISLAND_MAX_RADIUS, baseSeed);
        islandSites.add(centerIsland);

        GameLogger.info("Created " + islandSites.size() + " island sites (including central island).");
    }

    public Island findClosestIsland(float wx, float wy) {
        Island best = null;
        float bestDist = Float.MAX_VALUE;
        for (Island isl : islandSites) {
            float dx = wx - isl.centerX;
            float dy = wy - isl.centerY;
            float dd = dx * dx + dy * dy;
            if (dd < bestDist) {
                bestDist = dd;
                best = isl;
            }
        }
        return best;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. CHUNK BIOME SAMPLING (Optional Cache)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * If you want a low–res matrix of BiomeTransitionResult for each tile corner in the chunk.
     * This helps you do fancy multi–tile transitions, but is optional. Called by UnifiedWorldGenerator or something.
     */
    public BiomeTransitionResult[][] computeBiomeMatrixForChunk(int chunkX, int chunkY) {
        Vector2 key = new Vector2(chunkX, chunkY);
        if (chunkBiomeCache.containsKey(key)) {
            return chunkBiomeCache.get(key);
        }

        int size = Chunk.CHUNK_SIZE;
        int outW = size + 1;  // sample corners
        int outH = size + 1;
        BiomeTransitionResult[][] matrix = new BiomeTransitionResult[outW][outH];

        float baseWX = chunkX * size * World.TILE_SIZE;
        float baseWY = chunkY * size * World.TILE_SIZE;
        for (int bx = 0; bx < outW; bx++) {
            for (int by = 0; by < outH; by++) {
                float fx = baseWX + bx * World.TILE_SIZE;
                float fy = baseWY + by * World.TILE_SIZE;

                // Single warp:
                float[] warped = domainWarp(fx, fy);

                matrix[bx][by] = getBiomeFromAlreadyWarped(warped[0], warped[1]);
            }
        }

        chunkBiomeCache.put(key, matrix);
        return matrix;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. ACCESSORS & JSON LOADING
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Return a final Biome object for a given BiomeType, fallback if missing.
     */
    public Biome getBiome(BiomeType type) {
        Biome b = biomes.get(type);
        if (b == null) {
            GameLogger.error("Biome data missing for " + type + ", fallback to PLAINS");
            return biomes.get(BiomeType.PLAINS);
        }
        return b;
    }

    /**
     * Loads (or defaults) the set of known biomes from your "Data/biomes.json".
     * That JSON presumably includes tile distributions, spawnable objects, etc.
     */
    private void loadBiomesFromJson() {
        try {
            String content = GameFileSystem.getInstance().getDelegate().readString("Data/biomes.json");
            if (content == null) {
                initializeDefaultBiomes();
                return;
            }
            JsonParser parser = new JsonParser();
            JsonArray arr = parser.parse(content).getAsJsonArray();
            for (JsonElement elem : arr) {
                JsonObject obj = elem.getAsJsonObject();
                BiomeData data = parseBiomeData(obj);
                if (validateBiomeData(data)) {
                    Biome b = createBiomeFromData(data);
                    biomes.put(b.getType(), b);
                } else {
                    GameLogger.error("Invalid biome data => " + data.getName());
                }
            }
        } catch (Exception e) {
            GameLogger.error("BiomeManager => failed to load JSON => " + e.getMessage());
            initializeDefaultBiomes();
        }
    }

    /**
     * If no valid JSON is found, create some basic fallback.
     */
    private void initializeDefaultBiomes() {
        // We'll define some basic ones: OCEAN, BEACH, PLAINS, FOREST, DESERT, SNOW, RAIN_FOREST, etc.

        if (!biomes.containsKey(BiomeType.OCEAN)) {
            Biome ocean = new Biome("Ocean", BiomeType.OCEAN);
            /* watery tile IDs */
            ocean.setAllowedTileTypes(List.of());
            ocean.getTileDistribution().put(TileType.WATER, 100);
            biomes.put(BiomeType.OCEAN, ocean);
        }
        if (!biomes.containsKey(BiomeType.BEACH)) {
            Biome beach = new Biome("Beach", BiomeType.BEACH);
            /* beach sand IDs*/
            beach.setAllowedTileTypes(List.of());
            beach.getTileDistribution().put(TileType.BEACH_SAND, 100);
            biomes.put(BiomeType.BEACH, beach);
        }
        // Then add PLains, Forest, Desert, Snow, etc. as needed
        // ...
        GameLogger.info("Default fallback biomes have been created");
    }

    private BiomeData parseBiomeData(JsonObject json) {
        BiomeData data = new BiomeData();

        // name & type
        data.setName(json.get("name").getAsString());
        data.setType(json.get("type").getAsString());

        // allowed tile types
        List<Integer> atypes = new ArrayList<>();
        if (json.has("allowedTileTypes")) {
            JsonArray arr = json.getAsJsonArray("allowedTileTypes");
            for (JsonElement e : arr) {
                atypes.add(e.getAsInt());
            }
        }
        data.setAllowedTileTypes(atypes);

        // tileDistribution
        if (json.has("tileDistribution")) {
            JsonObject distObj = json.getAsJsonObject("tileDistribution");
            Map<Integer, Integer> dist = new HashMap<>();
            double sum = 0.0;
            for (Map.Entry<String, JsonElement> en : distObj.entrySet()) {
                int tid = Integer.parseInt(en.getKey());
                double w = en.getValue().getAsDouble();
                dist.put(tid, (int) Math.round(w));
                sum += w;
            }
            data.setTileDistribution(dist);
        }

        // transitionTileTypes
        if (json.has("transitionTileTypes")) {
            JsonObject trans = json.getAsJsonObject("transitionTileTypes");
            Map<Integer, Integer> tdist = new HashMap<>();
            for (Map.Entry<String, JsonElement> en : trans.entrySet()) {
                int tid = Integer.parseInt(en.getKey());
                double w = en.getValue().getAsDouble();
                tdist.put(tid, (int) Math.round(w));
            }
            data.setTransitionTileDistribution(tdist);
        }

        // spawnableObjects
        if (json.has("spawnableObjects")) {
            JsonArray arr = json.getAsJsonArray("spawnableObjects");
            List<String> sObjs = new ArrayList<>();
            for (JsonElement e : arr) {
                sObjs.add(e.getAsString());
            }
            data.setSpawnableObjects(sObjs);
        }

        // spawnChances
        if (json.has("spawnChances")) {
            JsonObject cobj = json.getAsJsonObject("spawnChances");
            Map<String, Double> sc = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : cobj.entrySet()) {
                sc.put(e.getKey(), e.getValue().getAsDouble());
            }
            data.setSpawnChances(sc);
        }

        data.validate();
        return data;
    }

    private boolean validateBiomeData(BiomeData data) {
        if (data.getName() == null || data.getType() == null) return false;
        if (data.getAllowedTileTypes().isEmpty()) return false;
        return !data.getTileDistribution().isEmpty();
    }

    private Biome createBiomeFromData(BiomeData data) {
        BiomeType btype = BiomeType.valueOf(data.getType());
        Biome b = new Biome(data.getName(), btype);
        b.setAllowedTileTypes(data.getAllowedTileTypes());
        b.setTileDistribution(data.getTileDistribution());
        b.setTransitionTileDistribution(data.getTransitionTileDistribution());
        if (data.getSpawnableObjects() != null) {
            b.loadSpawnableObjects(data.getSpawnableObjects());
        }
        if (data.getSpawnChances() != null) {
            b.loadSpawnChances(data.getSpawnChances());
        }
        return b;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. HELPER CLASSES
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Our random island definition.
     */
    public static class Island {
        public float centerX, centerY, radius;
        public long seed;

        public Island(float x, float y, float r, long s) {
            centerX = x;
            centerY = y;
            radius = r;
            seed = s;
        }
    }

    /**
     * Voronoi "site" with random temperature & moisture offset + local detail seed.
     */
    private static class BiomeSite {
        public float x, y;
        public double tempOffset, moistOffset;
        public long siteDetail;

        public BiomeSite(float x, float y, double tOff, double mOff, long detail) {
            this.x = x;
            this.y = y;
            this.tempOffset = tOff;
            this.moistOffset = mOff;
            this.siteDetail = detail;
        }
    }

    /**
     * Data structure for storing & reloading biome info.
     */
    public static class BiomeData implements Serializable {
        private String name;
        private String type;
        private ArrayList<Integer> allowedTileTypes = new ArrayList<>();
        private HashMap<Integer, Integer> tileDistribution = new HashMap<>();
        private HashMap<Integer, Integer> transitionTileDistribution = new HashMap<>();
        private List<String> spawnableObjects = new ArrayList<>();
        private Map<String, Double> spawnChances = new HashMap<>();

        public void validate() {
            if (tileDistribution == null) tileDistribution = new HashMap<>();
            if (transitionTileDistribution == null) transitionTileDistribution = new HashMap<>();
            if (allowedTileTypes == null) allowedTileTypes = new ArrayList<>();
            if (spawnableObjects == null) spawnableObjects = new ArrayList<>();
            if (spawnChances == null) spawnChances = new HashMap<>();
        }

        public String getName() {
            return name;
        }

        public void setName(String n) {
            name = n;
        }

        public String getType() {
            return type;
        }

        public void setType(String t) {
            type = t;
        }

        public ArrayList<Integer> getAllowedTileTypes() {
            return allowedTileTypes;
        }

        public void setAllowedTileTypes(List<Integer> t) {
            if (t != null) {
                allowedTileTypes.clear();
                allowedTileTypes.addAll(t);
            }
        }

        public HashMap<Integer, Integer> getTileDistribution() {
            return tileDistribution;
        }

        public void setTileDistribution(Map<Integer, Integer> dist) {
            if (dist != null) {
                tileDistribution.clear();
                tileDistribution.putAll(dist);
            }
        }

        public HashMap<Integer, Integer> getTransitionTileDistribution() {
            return transitionTileDistribution;
        }

        public void setTransitionTileDistribution(Map<Integer, Integer> dist) {
            if (dist != null) {
                transitionTileDistribution.clear();
                transitionTileDistribution.putAll(dist);
            }
        }

        public List<String> getSpawnableObjects() {
            return spawnableObjects;
        }

        public void setSpawnableObjects(List<String> s) {
            spawnableObjects = s;
        }

        public Map<String, Double> getSpawnChances() {
            return spawnChances;
        }

        public void setSpawnChances(Map<String, Double> c) {
            spawnChances = c;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10. BIOME CLASSIFIER FOR AAA–STYLE
    // ─────────────────────────────────────────────────────────────────────────

    private static class BiomeClassifier {
        /**
         * Example of a more “realistic” approach:
         * - temp < 0.25 => "cold" => maybe snow, taiga, or haunted
         * - temp > 0.75 => "hot" => maybe desert or badlands or rainforest
         * - else => "temperate" => forest, plains, swamp, etc.
         * - we might also do special "rare" or "ruins" checks
         */
        public static BiomeType determineBiomeType(double temperature, double moisture, long detailSeed, long siteDetail) {
            // Incorporate local variation with additional noise
            double localVar = OpenSimplex2.noise2(detailSeed ^ siteDetail, temperature * 5, moisture * 5) * 0.05;
            double t = Math.min(Math.max(0, temperature + localVar), 1);
            double m = Math.min(Math.max(0, moisture + localVar), 1);

            if (t > 0.4 && t < 0.6 && m > 0.4 && m < 0.6) {
                double ruinsNoise = OpenSimplex2.noise2(detailSeed ^ 12345, t * 10, m * 10);
                if (ruinsNoise > 0.92) {
                    return BiomeType.RUINS;
                }
            }

            // Extremely cold regions: differentiate between SNOW and HAUNTED based on moisture
            if (t < 0.15) {
                if (m > 0.5) {
                    return BiomeType.SNOW;    // Extremely cold and wet => SNOW
                } else {
                    return BiomeType.HAUNTED; // Extremely cold and very dry => HAUNTED
                }
            }

            // Moderately cold regions: normally PLAINS, but add a rare haunted override in extreme dryness
            if (t < 0.3) {
                if (m < 0.2) {
                    double hauntedChance = OpenSimplex2.noise2(detailSeed ^ 67890, t * 10, m * 10);
                    if (hauntedChance > 0.9) {
                        return BiomeType.HAUNTED;
                    }
                }
                return BiomeType.PLAINS;
            }

            // Hot regions: Rainforest for very wet, desert for very dry, else plains
            if (t > 0.75) {
                if (m > 0.65) {
                    return BiomeType.RAIN_FOREST;
                } else if (m < 0.3) {
                    return BiomeType.DESERT;
                } else {
                    return BiomeType.PLAINS;
                }
            }

            // Temperate regions: deep forest or cherry grove based on additional noise
            if (m > 0.75) {
                return BiomeType.RAIN_FOREST;
            } else if (m < 0.3) {
                return BiomeType.PLAINS;
            } else {
                double chanceCherry = OpenSimplex2.noise2(siteDetail + 555, t * 8, m * 8);
                if (chanceCherry > 0.6) {
                    return BiomeType.CHERRY_GROVE;
                }
                return BiomeType.FOREST;
            }
        }
    }
}
