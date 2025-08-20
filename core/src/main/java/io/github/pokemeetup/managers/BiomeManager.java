// Fixed BiomeManager.java - Stable version with proper world loading

package io.github.pokemeetup.managers;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.google.gson.*;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.NoiseCache;
import io.github.pokemeetup.utils.OpenSimplex2;
import io.github.pokemeetup.utils.storage.GameFileSystem;
import io.github.pokemeetup.utils.textures.TileType;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed BiomeManager with stable world generation and proper island boundaries.
 * Removed problematic optimizations that caused loading issues.
 */
public class BiomeManager {

    // Large biome scales
    private static final float TEMPERATURE_SCALE = 0.00008f;
    private static final float MOISTURE_SCALE = 0.00009f;
    private static final float ALTITUDE_SCALE = 0.0001f;
    private static final float TEMPERATURE_SCALE_DETAIL = 0.0004f;
    private static final float MOISTURE_SCALE_DETAIL = 0.00045f;

    // Domain warping
    private static final float FREQ_WARP_1 = 0.00015f;
    private static final float AMP_WARP_1 = 3f;
    private static final float FREQ_WARP_2 = 0.0003f;
    private static final float AMP_WARP_2 = 1.2f;

    // Island parameters
    private static final int ISLAND_COUNT = 70;
    private static final float ISLAND_MIN_RADIUS = 4000f;
    private static final float ISLAND_MAX_RADIUS = 10000f;
    private static final float WORLD_RADIUS = 50000f;

    // Biome parameters for large regions
    private static final int NUM_BIOME_SITES = 60;
    private static final float BIOME_CLUSTER_RADIUS = 10000f;

    // Beach parameters
    private static final float MIN_BEACH_WIDTH = 1000f;
    private static final float BEACH_WIDTH_FACTOR = 0.12f;

    private final Map<Vector2, BiomeTransitionResult[][]> chunkBiomeCache = new ConcurrentHashMap<>();
    private final long temperatureSeed;
    private final long moistureSeed;
    private final long altitudeSeed;
    private final long detailSeed;
    private final long clusterSeed;
    private final long warpSeed;

    private final List<BiomeSite> biomeSites = new ArrayList<>();
    private final List<BiomeCluster> biomeClusters = new ArrayList<>();
    private final List<Island> islandSites = new ArrayList<>();
    private final List<Island> sortedIslandSites = new ArrayList<>();

    private final Map<BiomeType, Biome> biomes;
    private long baseSeed;

    public BiomeManager(long baseSeed) {
        this.baseSeed = baseSeed;
        this.temperatureSeed = baseSeed + 1000;
        this.moistureSeed = baseSeed + 2000;
        this.detailSeed = baseSeed + 3000;
        this.warpSeed = baseSeed + 4000;
        this.altitudeSeed = baseSeed + 5000;
        this.clusterSeed = baseSeed + 6000;

        this.biomes = new HashMap<>();

        // Initialize in correct order
        loadBiomesFromJson();
        generateBiomeClusters(baseSeed);
        generateBiomeSites(baseSeed);
        generateRandomIslands(baseSeed);

        // Sort islands for faster lookup
        sortedIslandSites.addAll(this.islandSites);
        sortedIslandSites.sort(Comparator.comparingLong(a -> a.seed));

        GameLogger.info("BiomeManager initialized successfully with " + islandSites.size() + " islands");
    }

    /**
     * Generate biome clusters for coherent regions
     */
    private void generateBiomeClusters(long seed) {
        biomeClusters.clear();
        Random rng = new Random(seed ^ 0xCAFEBABEL);

        BiomeClusterType[] clusterTypes = BiomeClusterType.values();
        int clusterCount = 10 + rng.nextInt(6);

        for (int i = 0; i < clusterCount; i++) {
            float x = (rng.nextFloat() - 0.5f) * 2f * WORLD_RADIUS * 0.7f;
            float y = (rng.nextFloat() - 0.5f) * 2f * WORLD_RADIUS * 0.7f;
            BiomeClusterType type = clusterTypes[rng.nextInt(clusterTypes.length)];
            float radius = BIOME_CLUSTER_RADIUS * (0.8f + rng.nextFloat() * 0.4f);

            biomeClusters.add(new BiomeCluster(x, y, radius, type));
        }

        GameLogger.info("Created " + biomeClusters.size() + " biome clusters");
    }

    /**
     * Generate biome sites with cluster influence
     */
    private void generateBiomeSites(long seed) {
        biomeSites.clear();
        Random rng = new Random(seed ^ 0xDEADBEEFL);

        for (int i = 0; i < NUM_BIOME_SITES; i++) {
            float x = (rng.nextFloat() - 0.5f) * 2f * WORLD_RADIUS;
            float y = (rng.nextFloat() - 0.5f) * 2f * WORLD_RADIUS;

            BiomeCluster nearestCluster = findNearestCluster(x, y);
            double tOff = 0.0;
            double mOff = 0.0;

            if (nearestCluster != null) {
                float distToCluster = Vector2.dst(x, y, nearestCluster.x, nearestCluster.y);
                float influence = 1.0f - Math.min(1.0f, distToCluster / (nearestCluster.radius * 2f));

                switch (nearestCluster.type) {
                    case HOT_DRY:
                        tOff = 0.3 * influence;
                        mOff = -0.3 * influence;
                        break;
                    case HOT_WET:
                        tOff = 0.25 * influence;
                        mOff = 0.3 * influence;
                        break;
                    case COLD_DRY:
                        tOff = -0.3 * influence;
                        mOff = -0.2 * influence;
                        break;
                    case COLD_WET:
                        tOff = -0.25 * influence;
                        mOff = 0.2 * influence;
                        break;
                    case TEMPERATE:
                        tOff = (rng.nextFloat() - 0.5) * 0.1 * influence;
                        mOff = (rng.nextFloat() - 0.5) * 0.1 * influence;
                        break;
                    case MYSTICAL:
                        tOff = 0.1 * influence;
                        mOff = (rng.nextFloat() - 0.3) * 0.2 * influence;
                        break;
                }
            } else {
                tOff = (rng.nextFloat() - 0.5) * 0.1;
                mOff = (rng.nextFloat() - 0.5) * 0.1;
            }

            long detail = rng.nextLong();
            biomeSites.add(new BiomeSite(x, y, tOff, mOff, detail));
        }

        GameLogger.info("Created " + biomeSites.size() + " biome sites");
    }

    /**
     * Generate islands with proper separation
     */
    private void generateRandomIslands(long seed) {
        islandSites.clear();
        Random rng = new Random(seed ^ 0xBEEF9876L);
        float regionSize = WORLD_RADIUS * 0.75f;

        for (int i = 0; i < ISLAND_COUNT; i++) {
            float cx = -regionSize + rng.nextFloat() * (2 * regionSize);
            float cy = -regionSize + rng.nextFloat() * (2 * regionSize);
            float r = ISLAND_MIN_RADIUS + rng.nextFloat() * (ISLAND_MAX_RADIUS - ISLAND_MIN_RADIUS);
            long islandSeed = rng.nextLong();
            Island isl = new Island(cx, cy, r, islandSeed);
            islandSites.add(isl);
        }

        // Add central island
        Island centerIsland = new Island(0, 0, ISLAND_MAX_RADIUS, baseSeed);
        islandSites.add(centerIsland);

        GameLogger.info("Created " + islandSites.size() + " islands including central island");
    }

    /**
     * Main biome determination with strict island boundaries
     */
    public BiomeTransitionResult getBiomeFromAlreadyWarped(float wx, float wy) {
        Island isl = findClosestIsland(wx, wy);
        if (isl == null) {
            return new BiomeTransitionResult(getBiome(BiomeType.OCEAN), null, 1f);
        }

        float dx = wx - isl.centerX;
        float dy = wy - isl.centerY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        // Apply organic distortion
        float angle = MathUtils.atan2(dy, dx);
        float distort = (float)OpenSimplex2.noise2(isl.seed,
            MathUtils.cos(angle) * 2, MathUtils.sin(angle) * 2);
        distort = Math.max(0f, distort) * 0.15f;

        float expandFactor = 1.2f;
        float effectiveRadius = isl.radius * expandFactor + (isl.radius * expandFactor * distort);

        // Calculate beach boundaries
        float beachWidth = Math.max(MIN_BEACH_WIDTH, effectiveRadius * BEACH_WIDTH_FACTOR);
        float landRadius = effectiveRadius - beachWidth;

        if (dist < landRadius) {
            // Interior land
            return landBiomeVoronoi(wx, wy);
        } else if (dist < effectiveRadius) {
            // Beach zone
            return new BiomeTransitionResult(getBiome(BiomeType.BEACH), null, 1f);
        } else {
            // Ocean
            return new BiomeTransitionResult(getBiome(BiomeType.OCEAN), null, 1f);
        }
    }

    /**
     * Find closest island
     */
    public Island findClosestIsland(float wx, float wy) {
        // Find closest island directly without caching to ensure continuous data.
        Island best = null;
        float bestDistSq = Float.MAX_VALUE;

        // Check center island first
        if (!sortedIslandSites.isEmpty()) {
            Island centerIsland = sortedIslandSites.get(sortedIslandSites.size() - 1);
            float dx = wx - centerIsland.centerX;
            float dy = wy - centerIsland.centerY;
            float distSq = dx * dx + dy * dy;

            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = centerIsland;
            }
        }

        // Check other islands
        for (int i = 0; i < sortedIslandSites.size() - 1; i++) {
            Island isl = sortedIslandSites.get(i);
            float dx = wx - isl.centerX;
            float dy = wy - isl.centerY;
            float distSq = dx * dx + dy * dy;

            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = isl;

                // Early exit if clearly inside
                if (distSq < isl.radius * isl.radius * 0.25f) {
                    break;
                }
            }
        }

        return best;
    }

    /**
     * Voronoi biome determination for land
     */
    public BiomeTransitionResult landBiomeVoronoi(float wx, float wy) {
        List<SiteDistance> nearestSites = findNearestSites(wx, wy);

        if (nearestSites.isEmpty()) {
            return new BiomeTransitionResult(getBiome(BiomeType.PLAINS), null, 1f);
        }

        if (nearestSites.size() == 1) {
            Biome b = classifySiteToBiome(nearestSites.get(0).site, wx, wy);
            return new BiomeTransitionResult(b, null, 1f);
        }

        Biome primary = classifySiteToBiome(nearestSites.get(0).site, wx, wy);
        Biome secondary = classifySiteToBiome(nearestSites.get(1).site, wx, wy);

        float d1 = nearestSites.get(0).distance;
        float d2 = nearestSites.get(1).distance;
        float raw = d2 / (d1 + d2);
        float t = smoothStep(smoothStep(raw));

        // Prevent incompatible transitions
        if ((primary.getType() == BiomeType.SNOW && secondary.getType() == BiomeType.DESERT) ||
            (primary.getType() == BiomeType.DESERT && secondary.getType() == BiomeType.SNOW)) {
            return new BiomeTransitionResult(getBiome(BiomeType.PLAINS), null, 1f);
        }

        return new BiomeTransitionResult(primary, secondary, t);
    }

    /**
     * Find nearest biome sites
     */
    private List<SiteDistance> findNearestSites(float wx, float wy) {
        List<SiteDistance> distances = new ArrayList<>();

        for (BiomeSite site : biomeSites) {
            float dx = wx - site.x;
            float dy = wy - site.y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            distances.add(new SiteDistance(site, dist));
        }

        distances.sort(Comparator.comparingDouble(sd -> sd.distance));
        return distances.subList(0, Math.min(3, distances.size()));
    }

    /**
     * Classify site to biome type
     */
    private Biome classifySiteToBiome(BiomeSite site, float wx, float wy) {
        double baseTemp = NoiseCache.getNoise(temperatureSeed, wx, wy, TEMPERATURE_SCALE);
        double detailTemp = NoiseCache.getNoise(temperatureSeed + 1, wx, wy, TEMPERATURE_SCALE_DETAIL) * 0.1;
        double baseMoist = NoiseCache.getNoise(moistureSeed, wx, wy, MOISTURE_SCALE);
        double detailMoist = NoiseCache.getNoise(moistureSeed + 1, wx, wy, MOISTURE_SCALE_DETAIL) * 0.1;
        double baseAltitude = NoiseCache.getNoise(altitudeSeed, wx, wy, ALTITUDE_SCALE);

        double temp = baseTemp + detailTemp + site.tempOffset;
        double moist = baseMoist + detailMoist + site.moistOffset;
        double localVar = OpenSimplex2.noise2(detailSeed ^ site.siteDetail, wx * 0.0001, wy * 0.0001) * 0.02;

        temp = Math.min(Math.max(0, temp + localVar), 1);
        moist = Math.min(Math.max(0, moist + localVar), 1);
        double altitude = Math.min(Math.max(0, baseAltitude), 1);

        BiomeType type = BiomeClassifier.determineBiomeType(temp, moist, altitude, detailSeed, site.siteDetail);
        return getBiome(type);
    }

    /**
     * Compute biome matrix for chunk
     */
    public BiomeTransitionResult[][] computeBiomeMatrixForChunk(int chunkX, int chunkY) {
        Vector2 key = new Vector2(chunkX, chunkY);

        // Check cache
        BiomeTransitionResult[][] cached = chunkBiomeCache.get(key);
        if (cached != null) {
            return cached;
        }

        int size = Chunk.CHUNK_SIZE;
        int outW = size + 1;
        int outH = size + 1;
        BiomeTransitionResult[][] matrix = new BiomeTransitionResult[outW][outH];

        float baseWX = chunkX * size * World.TILE_SIZE;
        float baseWY = chunkY * size * World.TILE_SIZE;

        for (int bx = 0; bx < outW; bx++) {
            for (int by = 0; by < outH; by++) {
                float fx = baseWX + bx * World.TILE_SIZE;
                float fy = baseWY + by * World.TILE_SIZE;

                float[] warped = domainWarp(fx, fy);
                matrix[bx][by] = getBiomeFromAlreadyWarped(warped[0], warped[1]);
            }
        }

        chunkBiomeCache.put(key, matrix);
        return matrix;
    }

    /**
     * Domain warping for organic shapes
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

    private BiomeCluster findNearestCluster(float x, float y) {
        BiomeCluster nearest = null;
        float minDist = Float.MAX_VALUE;

        for (BiomeCluster cluster : biomeClusters) {
            float dist = Vector2.dst(x, y, cluster.x, cluster.y);
            if (dist < minDist) {
                minDist = dist;
                nearest = cluster;
            }
        }
        return nearest;
    }

    private static float smoothStep(float t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3f - 2f * t);
    }

    public BiomeTransitionResult getBiomeAtTile(int tileX, int tileY) {
        float worldX = (tileX + 0.5f) * World.TILE_SIZE;
        float worldY = (tileY + 0.5f) * World.TILE_SIZE;
        float[] warped = domainWarp(worldX, worldY);
        return getBiomeFromAlreadyWarped(warped[0], warped[1]);
    }

    public BiomeTransitionResult getBiomeAt(float worldPixelX, float worldPixelY) {
        int tileX = (int) Math.floor(worldPixelX / World.TILE_SIZE);
        int tileY = (int) Math.floor(worldPixelY / World.TILE_SIZE);
        return getBiomeAtTile(tileX, tileY);
    }

    public Biome getBiome(BiomeType type) {
        Biome b = biomes.get(type);
        if (b == null) {
            GameLogger.error("Biome data missing for " + type + ", fallback to PLAINS");
            return biomes.get(BiomeType.PLAINS);
        }
        return b;
    }

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

        GameLogger.error("Failed to find safe island spawn; use center");
        int cx = MathUtils.floor(isl.centerX / World.TILE_SIZE);
        int cy = MathUtils.floor(isl.centerY / World.TILE_SIZE);
        if (!world.isWithinWorldBounds(cx, cy)) {
            return new Vector2(0, 0);
        }
        return new Vector2(cx, cy);
    }

    // JSON loading methods
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
                }
            }

            // Ensure default biomes exist
            initializeDefaultBiomes();

        } catch (Exception e) {
            GameLogger.error("Failed to load biomes: " + e.getMessage());
            initializeDefaultBiomes();
        }
    }

    private void initializeDefaultBiomes() {
        if (!biomes.containsKey(BiomeType.OCEAN)) {
            Biome ocean = new Biome("Ocean", BiomeType.OCEAN);
            ocean.setAllowedTileTypes(List.of());
            ocean.getTileDistribution().put(TileType.WATER, 100);
            biomes.put(BiomeType.OCEAN, ocean);
        }
        if (!biomes.containsKey(BiomeType.BEACH)) {
            Biome beach = new Biome("Beach", BiomeType.BEACH);
            beach.setAllowedTileTypes(List.of());
            beach.getTileDistribution().put(TileType.BEACH_SAND, 100);
            biomes.put(BiomeType.BEACH, beach);
        }
        if (!biomes.containsKey(BiomeType.PLAINS)) {
            Biome plains = new Biome("Plains", BiomeType.PLAINS);
            plains.setAllowedTileTypes(List.of());
            plains.getTileDistribution().put(TileType.GRASS, 100);
            biomes.put(BiomeType.PLAINS, plains);
        }
    }

    private BiomeData parseBiomeData(JsonObject json) {
        BiomeData data = new BiomeData();

        data.setName(json.get("name").getAsString());
        data.setType(json.get("type").getAsString());

        List<Integer> atypes = new ArrayList<>();
        if (json.has("allowedTileTypes")) {
            JsonArray arr = json.getAsJsonArray("allowedTileTypes");
            for (JsonElement e : arr) {
                atypes.add(e.getAsInt());
            }
        }
        data.setAllowedTileTypes(atypes);

        if (json.has("tileDistribution")) {
            JsonObject distObj = json.getAsJsonObject("tileDistribution");
            Map<Integer, Integer> dist = new HashMap<>();
            for (Map.Entry<String, JsonElement> en : distObj.entrySet()) {
                int tid = Integer.parseInt(en.getKey());
                double w = en.getValue().getAsDouble();
                dist.put(tid, (int) Math.round(w));
            }
            data.setTileDistribution(dist);
        }

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

        if (json.has("spawnableObjects")) {
            JsonArray arr = json.getAsJsonArray("spawnableObjects");
            List<String> sObjs = new ArrayList<>();
            for (JsonElement e : arr) {
                sObjs.add(e.getAsString());
            }
            data.setSpawnableObjects(sObjs);
        }

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
        return data.getName() != null &&
            data.getType() != null &&
            !data.getAllowedTileTypes().isEmpty() &&
            !data.getTileDistribution().isEmpty();
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

    // Getters
    public long getWarpSeed() { return warpSeed; }
    public void setBaseSeed(long baseSeed) { this.baseSeed = baseSeed; }

    // Inner classes
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

    private static class BiomeCluster {
        public float x, y, radius;
        public BiomeClusterType type;

        public BiomeCluster(float x, float y, float radius, BiomeClusterType type) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.type = type;
        }
    }

    private enum BiomeClusterType {
        HOT_DRY, HOT_WET, COLD_DRY, COLD_WET, TEMPERATE, MYSTICAL
    }

    private static class SiteDistance {
        public BiomeSite site;
        public float distance;
        public float weight;

        public SiteDistance(BiomeSite site, float distance) {
            this.site = site;
            this.distance = distance;
            this.weight = 0;
        }
    }

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

        public String getName() { return name; }
        public void setName(String n) { name = n; }
        public String getType() { return type; }
        public void setType(String t) { type = t; }

        public ArrayList<Integer> getAllowedTileTypes() { return allowedTileTypes; }
        public void setAllowedTileTypes(List<Integer> t) {
            if (t != null) {
                allowedTileTypes.clear();
                allowedTileTypes.addAll(t);
            }
        }

        public HashMap<Integer, Integer> getTileDistribution() { return tileDistribution; }
        public void setTileDistribution(Map<Integer, Integer> dist) {
            if (dist != null) {
                tileDistribution.clear();
                tileDistribution.putAll(dist);
            }
        }

        public HashMap<Integer, Integer> getTransitionTileDistribution() { return transitionTileDistribution; }
        public void setTransitionTileDistribution(Map<Integer, Integer> dist) {
            if (dist != null) {
                transitionTileDistribution.clear();
                transitionTileDistribution.putAll(dist);
            }
        }

        public List<String> getSpawnableObjects() { return spawnableObjects; }
        public void setSpawnableObjects(List<String> s) { spawnableObjects = s; }

        public Map<String, Double> getSpawnChances() { return spawnChances; }
        public void setSpawnChances(Map<String, Double> c) { spawnChances = c; }
    }

    private static class BiomeClassifier {
        public static BiomeType determineBiomeType(double temperature, double moisture, double altitude,
                                                   long detailSeed, long siteDetail) {
            double adjustedTemp = temperature - (altitude * 0.25f);
            adjustedTemp = Math.min(Math.max(0, adjustedTemp), 1);

            double t = adjustedTemp;
            double m = moisture;

            if (t < 0.25) { // Cold
                if (m < 0.3) return BiomeType.HAUNTED;
                if (m > 0.65) return BiomeType.SNOW;
                if (altitude > 0.6) return BiomeType.SNOW;
                return BiomeType.PLAINS;
            } else if (t < 0.45) { // Cool
                if (m < 0.25 && altitude > 0.5) return BiomeType.HAUNTED;
                if (m > 0.7) return BiomeType.FOREST;
                if (m > 0.5) {
                    double variety = OpenSimplex2.noise2(siteDetail, t * 5, m * 5);
                    if (variety > 0.5) return BiomeType.CHERRY_GROVE;
                    return BiomeType.FOREST;
                }
                return BiomeType.PLAINS;
            } else if (t < 0.65) { // Warm
                if (m > 0.7) return BiomeType.RAIN_FOREST;
                if (m > 0.45) {
                    double variety = OpenSimplex2.noise2(siteDetail ^ detailSeed, t * 6, m * 6);
                    if (variety > 0.6) return BiomeType.CHERRY_GROVE;
                    if (variety < -0.6) return BiomeType.HAUNTED;
                    return BiomeType.FOREST;
                }
                if (m < 0.3) return BiomeType.DESERT;
                return BiomeType.PLAINS;
            } else { // Hot
                if (m > 0.65) return BiomeType.RAIN_FOREST;
                if (m < 0.4) return BiomeType.DESERT;
                if (altitude > 0.7) return BiomeType.RUINS;
                return BiomeType.PLAINS;
            }
        }
    }
}
