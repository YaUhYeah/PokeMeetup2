package io.github.pokemeetup.managers;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.google.gson.*;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.NoiseCache;
import io.github.pokemeetup.utils.OpenSimplex2;
import io.github.pokemeetup.utils.storage.GameFileSystem;
import io.github.pokemeetup.utils.textures.TileType;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced BiomeManager for large, expansive biomes with smooth transitions.
 *
 * Key improvements:
 * - Larger biome scales for more expansive regions
 * - Biome clustering to group similar biomes together
 * - Multi-scale noise for better variety
 * - Reduced Voronoi site count for larger regions
 * - Smoother transitions between biomes
 */
public class BiomeManager {

    // ─────────────────────────────────────────────────────────────────────────
    // 1. TUNING CONSTANTS & FIELD DEFINITIONS
    // ─────────────────────────────────────────────────────────────────────────

    // MAJOR CHANGE: Increased scales for larger biomes (lower frequency = larger features)
    private static final float TEMPERATURE_SCALE = 0.00015f; // Was 0.0008f - now 5x larger
    private static final float MOISTURE_SCALE = 0.00018f;    // Was 0.0008f - now 4.5x larger
    private static final float ALTITUDE_SCALE = 0.0002f;     // Was 0.001f - now 5x larger

    // Multi-scale noise for more interesting variation
    private static final float TEMPERATURE_SCALE_DETAIL = 0.001f;  // Fine detail
    private static final float MOISTURE_SCALE_DETAIL = 0.0012f;    // Fine detail

    // Domain warp parameters (slightly reduced for smoother transitions)
    private static final float FREQ_WARP_1 = 0.0002f; // Was 0.0003f
    private static final float AMP_WARP_1 = 4f;       // Was 5f
    private static final float FREQ_WARP_2 = 0.0005f; // Was 0.0006f
    private static final float AMP_WARP_2 = 1.5f;     // Was 2f

    // Island parameters
    private static final int ISLAND_COUNT = 50;
    private static final float ISLAND_MIN_RADIUS = 3000f;
    private static final float ISLAND_MAX_RADIUS = 8000f;
    private static final float WORLD_RADIUS = 50000f;

    // MAJOR CHANGE: Reduced Voronoi sites for larger biome regions
    private static final int NUM_BIOME_SITES = 120; // Was 400 - now 3x larger regions

    // Biome clustering radius - biomes within this distance tend to be similar
    private static final float BIOME_CLUSTER_RADIUS = 8000f;

    // Transition smoothing distance
    private static final float TRANSITION_DISTANCE = 2000f;

    // Cache and state
    private final Map<Vector2, BiomeTransitionResult[][]> chunkBiomeCache = new ConcurrentHashMap<>();
    private final long temperatureSeed;
    private final long moistureSeed;
    private final long altitudeSeed;
    private final long detailSeed;
    private final long clusterSeed;
    private final List<BiomeSite> biomeSites = new ArrayList<>();
    private final List<BiomeCluster> biomeClusters = new ArrayList<>();
    private final List<Island> islandSites = new ArrayList<>();
    private final long warpSeed;
    private final Map<BiomeType, Biome> biomes;
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
        this.altitudeSeed = baseSeed + 5000;
        this.clusterSeed = baseSeed + 6000;

        this.biomes = new HashMap<>();

        loadBiomesFromJson();
        generateBiomeClusters(baseSeed);     // NEW: Generate biome clusters first
        generateBiomeSites(baseSeed);        // Then create sites within clusters
        generateRandomIslands(baseSeed);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. BIOME CLUSTERING SYSTEM (NEW)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generate biome clusters to ensure similar biomes group together
     */
    private void generateBiomeClusters(long seed) {
        biomeClusters.clear();
        Random rng = new Random(seed ^ 0xCAFEBABEL);

        // Define cluster archetypes
        BiomeClusterType[] clusterTypes = {
            BiomeClusterType.HOT_DRY,      // Desert regions
            BiomeClusterType.HOT_WET,      // Rainforest regions
            BiomeClusterType.COLD_DRY,     // Tundra/mountain regions
            BiomeClusterType.COLD_WET,     // Snow regions
            BiomeClusterType.TEMPERATE,    // Forest/plains regions
            BiomeClusterType.MYSTICAL      // Cherry/haunted regions
        };

        // Generate 15-25 major cluster regions
        int clusterCount = 15 + rng.nextInt(11);

        for (int i = 0; i < clusterCount; i++) {
            float x = (rng.nextFloat() - 0.5f) * 2f * WORLD_RADIUS * 0.8f;
            float y = (rng.nextFloat() - 0.5f) * 2f * WORLD_RADIUS * 0.8f;
            BiomeClusterType type = clusterTypes[rng.nextInt(clusterTypes.length)];
            float radius = BIOME_CLUSTER_RADIUS * (0.8f + rng.nextFloat() * 0.4f);

            BiomeCluster cluster = new BiomeCluster(x, y, radius, type);
            biomeClusters.add(cluster);
        }

        GameLogger.info("BiomeManager => created " + biomeClusters.size() + " biome clusters.");
    }

    /**
     * Enhanced biome site generation that respects clusters
     */
    private void generateBiomeSites(long seed) {
        biomeSites.clear();
        Random rng = new Random(seed ^ 0xDEADBEEFL);

        for (int i = 0; i < NUM_BIOME_SITES; i++) {
            float x = (rng.nextFloat() - 0.5f) * 2f * WORLD_RADIUS;
            float y = (rng.nextFloat() - 0.5f) * 2f * WORLD_RADIUS;

            // Find nearest cluster to influence this site
            BiomeCluster nearestCluster = findNearestCluster(x, y);

            // MAJOR CHANGE: Reduced random offsets for more consistent biomes
            double tOff = 0.0;
            double mOff = 0.0;

            if (nearestCluster != null) {
                float distToCluster = Vector2.dst(x, y, nearestCluster.x, nearestCluster.y);
                float influence = 1.0f - Math.min(1.0f, distToCluster / (nearestCluster.radius * 2));

                // Apply cluster-based offsets
                switch (nearestCluster.type) {
                    case HOT_DRY:
                        tOff = 0.3 * influence;   // Higher temperature
                        mOff = -0.3 * influence;  // Lower moisture
                        break;
                    case HOT_WET:
                        tOff = 0.25 * influence;  // Higher temperature
                        mOff = 0.3 * influence;   // Higher moisture
                        break;
                    case COLD_DRY:
                        tOff = -0.3 * influence;  // Lower temperature
                        mOff = -0.2 * influence;  // Lower moisture
                        break;
                    case COLD_WET:
                        tOff = -0.25 * influence; // Lower temperature
                        mOff = 0.2 * influence;   // Higher moisture
                        break;
                    case TEMPERATE:
                        // Small random variation for temperate zones
                        tOff = (rng.nextFloat() - 0.5) * 0.1 * influence;
                        mOff = (rng.nextFloat() - 0.5) * 0.1 * influence;
                        break;
                    case MYSTICAL:
                        // Moderate temperature with varied moisture
                        tOff = 0.1 * influence;
                        mOff = (rng.nextFloat() - 0.3) * 0.2 * influence;
                        break;
                }
            } else {
                // Sites far from clusters get small random variation
                tOff = (rng.nextFloat() - 0.5) * 0.1;
                mOff = (rng.nextFloat() - 0.5) * 0.1;
            }

            long detail = rng.nextLong();
            BiomeSite site = new BiomeSite(x, y, tOff, mOff, detail);
            biomeSites.add(site);
        }

        GameLogger.info("BiomeManager => created " + biomeSites.size() + " Voronoi sites with cluster influence.");
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

    // ─────────────────────────────────────────────────────────────────────────
    // 4. ENHANCED BIOME CLASSIFICATION
    // ─────────────────────────────────────────────────────────────────────────

    private Biome classifySiteToBiome(BiomeSite site, float wx, float wy) {
        // Multi-scale temperature sampling
        double baseTemp = NoiseCache.getNoise(temperatureSeed, wx, wy, TEMPERATURE_SCALE);
        double detailTemp = NoiseCache.getNoise(temperatureSeed + 1, wx, wy, TEMPERATURE_SCALE_DETAIL) * 0.1;

        // Multi-scale moisture sampling
        double baseMoist = NoiseCache.getNoise(moistureSeed, wx, wy, MOISTURE_SCALE);
        double detailMoist = NoiseCache.getNoise(moistureSeed + 1, wx, wy, MOISTURE_SCALE_DETAIL) * 0.1;

        // Altitude with less variation
        double baseAltitude = NoiseCache.getNoise(altitudeSeed, wx, wy, ALTITUDE_SCALE);

        // Combine base and detail
        double temp = baseTemp + detailTemp + site.tempOffset;
        double moist = baseMoist + detailMoist + site.moistOffset;

        // Add very subtle local variation
        double localVar = OpenSimplex2.noise2(detailSeed ^ site.siteDetail, wx * 0.0001, wy * 0.0001) * 0.02;

        temp = Math.min(Math.max(0, temp + localVar), 1);
        moist = Math.min(Math.max(0, moist + localVar), 1);
        double altitude = Math.min(Math.max(0, baseAltitude), 1);

        BiomeType type = BiomeClassifier.determineBiomeType(temp, moist, altitude, detailSeed, site.siteDetail);
        return getBiome(type);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. SMOOTH TRANSITIONS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Enhanced Voronoi with smoother transitions
     */
    public BiomeTransitionResult landBiomeVoronoi(float wx, float wy) {
        // Find nearest sites (up to 4 for smoother blending)
        List<SiteDistance> nearestSites = findNearestSites(wx, wy, 4);

        if (nearestSites.isEmpty()) {
            return new BiomeTransitionResult(getBiome(BiomeType.PLAINS), null, 1f);
        }

        if (nearestSites.size() == 1) {
            Biome b = classifySiteToBiome(nearestSites.get(0).site, wx, wy);
            return new BiomeTransitionResult(b, null, 1f);
        }

        // Calculate smooth blending weights
        float totalWeight = 0;
        for (SiteDistance sd : nearestSites) {
            // Use inverse distance with smoothing
            sd.weight = 1f / (1f + sd.distance / TRANSITION_DISTANCE);
            totalWeight += sd.weight;
        }

        // Normalize weights
        for (SiteDistance sd : nearestSites) {
            sd.weight /= totalWeight;
        }

        // Get primary and secondary biomes
        Biome primary = classifySiteToBiome(nearestSites.get(0).site, wx, wy);
        Biome secondary = classifySiteToBiome(nearestSites.get(1).site, wx, wy);

        // Calculate smooth transition factor
        float d1 = nearestSites.get(0).distance;
        float d2 = nearestSites.get(1).distance;
        float raw = d2 / (d1 + d2);

        // Enhanced smoothstep for even smoother transitions
        float t = smoothStep(smoothStep(raw));

        // Prevent harsh desert-snow transitions
        if ((primary.getType() == BiomeType.SNOW && secondary.getType() == BiomeType.DESERT) ||
            (primary.getType() == BiomeType.DESERT && secondary.getType() == BiomeType.SNOW)) {
            return new BiomeTransitionResult(getBiome(BiomeType.PLAINS), null, 1f);
        }

        return new BiomeTransitionResult(primary, secondary, t);
    }

    private List<SiteDistance> findNearestSites(float wx, float wy, int count) {
        List<SiteDistance> distances = new ArrayList<>();

        for (BiomeSite site : biomeSites) {
            float dx = wx - site.x;
            float dy = wy - site.y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            distances.add(new SiteDistance(site, dist));
        }

        distances.sort(Comparator.comparingDouble(sd -> sd.distance));

        return distances.subList(0, Math.min(count, distances.size()));
    }

    private static float smoothStep(float t) {
        return t * t * (3f - 2f * t);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. EXISTING METHODS (with minor adjustments)
    // ─────────────────────────────────────────────────────────────────────────

    public void setBaseSeed(long baseSeed) {
        this.baseSeed = baseSeed;
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

    public BiomeTransitionResult getBiomeFromAlreadyWarped(float wx, float wy) {
        Island isl = findClosestIsland(wx, wy);
        if (isl == null) {
            return new BiomeTransitionResult(getBiome(BiomeType.OCEAN), null, 1f);
        }

        float dx = wx - isl.centerX;
        float dy = wy - isl.centerY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        float angle = MathUtils.atan2(dy, dx);
        float distort = OpenSimplex2.noise2(isl.seed, MathUtils.cos(angle), MathUtils.sin(angle));
        distort = Math.max(0f, distort);

        float newExpandFactor = 1.3f;
        float reducedFactor = 0.1f;
        float effectiveRadius = isl.radius * newExpandFactor + (isl.radius * newExpandFactor * reducedFactor * distort);

        float beachBand = effectiveRadius * 0.1f;
        float innerThreshold = effectiveRadius;
        float outerThreshold = effectiveRadius + beachBand;

        if (dist < innerThreshold) {
            return landBiomeVoronoi(wx, wy);
        } else if (dist < outerThreshold) {
            return new BiomeTransitionResult(getBiome(BiomeType.BEACH), null, 1f);
        } else {
            return new BiomeTransitionResult(getBiome(BiomeType.OCEAN), null, 1f);
        }
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

    public long getWarpSeed() {
        return warpSeed;
    }

    public float[] domainWarp(float x, float y) {
        float dx1 = (float) OpenSimplex2.noise2(warpSeed, x * FREQ_WARP_1, y * FREQ_WARP_1) * AMP_WARP_1;
        float dy1 = (float) OpenSimplex2.noise2(warpSeed + 1337, x * FREQ_WARP_1, y * FREQ_WARP_1) * AMP_WARP_1;
        float wx1 = x + dx1;
        float wy1 = y + dy1;

        float dx2 = (float) OpenSimplex2.noise2(warpSeed + 999, wx1 * FREQ_WARP_2, wy1 * FREQ_WARP_2) * AMP_WARP_2;
        float dy2 = (float) OpenSimplex2.noise2(warpSeed + 1999, wx1 * FREQ_WARP_2, wy1 * FREQ_WARP_2) * AMP_WARP_2;

        return new float[]{wx1 + dx2, wy1 + dy2};
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

        Island centerIsland = new Island(0, 0, ISLAND_MAX_RADIUS, baseSeed);
        islandSites.add(centerIsland);

        GameLogger.info("Created " + islandSites.size() + " island sites (including central island).");
    }

    public Island findClosestIsland(float wx, float wy) {
        float roundedX = (float) (Math.floor(wx / 100.0f) * 100.0f);
        float roundedY = (float) (Math.floor(wy / 100.0f) * 100.0f);

        Island best = null;
        float bestDist = Float.MAX_VALUE;

        List<Island> sortedIslands = new ArrayList<>(islandSites);
        sortedIslands.sort((a, b) -> Long.compare(a.seed, b.seed));

        for (Island isl : sortedIslands) {
            float dx = roundedX - isl.centerX;
            float dy = roundedY - isl.centerY;
            float dist = dx * dx + dy * dy;
            if (dist < bestDist) {
                bestDist = dist;
                best = isl;
            }
        }

        return best;
    }

    public BiomeTransitionResult[][] computeBiomeMatrixForChunk(int chunkX, int chunkY) {
        Vector2 key = new Vector2(chunkX, chunkY);
        if (chunkBiomeCache.containsKey(key)) {
            return chunkBiomeCache.get(key);
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

    public Biome getBiome(BiomeType type) {
        Biome b = biomes.get(type);
        if (b == null) {
            GameLogger.error("Biome data missing for " + type + ", fallback to PLAINS");
            return biomes.get(BiomeType.PLAINS);
        }
        return b;
    }

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
        GameLogger.info("Default fallback biomes have been created");
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
            double sum = 0.0;
            for (Map.Entry<String, JsonElement> en : distObj.entrySet()) {
                int tid = Integer.parseInt(en.getKey());
                double w = en.getValue().getAsDouble();
                dist.put(tid, (int) Math.round(w));
                sum += w;
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
    // 7. HELPER CLASSES
    // ─────────────────────────────────────────────────────────────────────────

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

    /**
     * Biome cluster for grouping similar biomes
     */
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
        HOT_DRY,      // Desert clusters
        HOT_WET,      // Rainforest clusters
        COLD_DRY,     // Mountain/tundra clusters
        COLD_WET,     // Snow clusters
        TEMPERATE,    // Forest/plains clusters
        MYSTICAL      // Cherry/haunted clusters
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

    // ─────────────────────────────────────────────────────────────────────────
    // 8. ENHANCED BIOME CLASSIFIER
    // ─────────────────────────────────────────────────────────────────────────

    private static class BiomeClassifier {

        /**
         * Enhanced biome classification with better thresholds for expansive biomes
         */
        public static BiomeType determineBiomeType(double temperature, double moisture, double altitude, long detailSeed, long siteDetail) {
            // Adjust temperature based on altitude (gentler curve)
            double adjustedTemp = temperature - (altitude * 0.3f); // Was 0.4f
            adjustedTemp = Math.min(Math.max(0, adjustedTemp), 1);

            double t = adjustedTemp;
            double m = moisture;

            // === Improved Biome Thresholds for Larger Regions ===

            // Very Cold (expanded threshold)
            if (t < 0.2) { // Was 0.15
                if (m < 0.35) return BiomeType.HAUNTED; // Expanded haunted range
                return BiomeType.SNOW;
            }

            // Cold (expanded threshold)
            if (t < 0.35) { // Was 0.3
                if (m < 0.25 && altitude > 0.5) return BiomeType.HAUNTED;
                if (m > 0.6) return BiomeType.SNOW; // Cold and wet = snow
                return BiomeType.PLAINS; // Cold temperate
            }

            // Very Hot (expanded threshold)
            if (t > 0.7) { // Was 0.75
                if (m > 0.6) return BiomeType.RAIN_FOREST;
                if (m < 0.35) return BiomeType.DESERT; // Expanded desert range
                return BiomeType.PLAINS;
            }

            // Temperate zones (main biome variety)
            if (m > 0.65) { // Wet temperate
                if (t > 0.55) return BiomeType.RAIN_FOREST;
                return BiomeType.FOREST;
            } else if (m > 0.35) { // Moderate moisture
                // Use noise for special biome placement
                double detailNoise = OpenSimplex2.noise2(siteDetail ^ detailSeed, t * 8, m * 8);

                if (detailNoise > 0.6 && altitude < 0.5 && t > 0.45) {
                    return BiomeType.CHERRY_GROVE; // Cherry groves in warm, low areas
                }
                if (detailNoise < -0.5 && t < 0.45) {
                    return BiomeType.HAUNTED; // Haunted forests in cooler areas
                }

                // Default to forest with some plains
                if (detailNoise > 0.3 || detailNoise < -0.3) {
                    return BiomeType.FOREST;
                }
                return BiomeType.PLAINS;
            } else { // Dry temperate
                if (altitude > 0.7) return BiomeType.PLAINS; // High dry areas
                if (t > 0.6) return BiomeType.DESERT; // Warm and dry
                return BiomeType.PLAINS;
            }
        }
    }
}
