package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.math.Rectangle;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.managers.BiomeManager;
import io.github.pokemeetup.managers.BiomeTransitionResult;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.system.gameplay.overworld.mechanics.AutoTileSystem;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.NoiseCache;
import io.github.pokemeetup.utils.OpenSimplex2;
import io.github.pokemeetup.utils.textures.TileType;

import java.util.*;

/**
 * Fully refactored world generator that:
 * - Creates large islands with a guaranteed beach ring, then ocean beyond.
 * - Ensures smoother biome transitions (reduces abrupt chunk boundary changes).
 * - Preserves mountain logic while reducing stray or random lumps.
 * - Spawns WorldObjects with a beach buffer.
 * - Optionally BFS‐cleans any weird "inland ocean pockets" inside the island.
 */
public class UnifiedWorldGenerator {

    public static final int CHUNK_SIZE = 16; // Must match Chunk.CHUNK_SIZE
    private static final int TEMP_SIZE = CHUNK_SIZE;

    private static final ThreadLocal<int[][]> smoothingTemp = ThreadLocal.withInitial(() -> {
        int[][] arr = new int[TEMP_SIZE][TEMP_SIZE];
        for (int i = 0; i < TEMP_SIZE; i++) {
            arr[i] = new int[TEMP_SIZE];
        }
        return arr;
    });

    public static Chunk generateChunk(int chunkX, int chunkY, long worldSeed, BiomeManager biomeManager) {
        // 1. Determine the dominant biome first.
        Biome primary = findDominantBiomeInChunk(chunkX, chunkY, biomeManager);
        Chunk chunk = new Chunk(chunkX, chunkY, primary, worldSeed);

        // 2. Fill the chunk with its base tiles according to the biome.
        fillChunkTiles(chunk, worldSeed, biomeManager);

        // 3. Apply all terrain modifications, like mountains and elevation.
        // This process might change the base tiles.
        applyMountainsIfNeeded(chunk, chunk.getTileData(), worldSeed, primary);

        // 4. CRITICAL FIX: Spawn world objects ONLY AFTER the terrain is final.
        List<WorldObject> objects = spawnWorldObjects(chunk, chunk.getTileData(), worldSeed);
        chunk.setWorldObjects(objects);

        return chunk;
    }
    private static List<WorldObject> spawnWorldObjects(Chunk chunk, int[][] tiles, long worldSeed) {
        return EnhancedWorldObjectSpawner.spawnWorldObjects(chunk, tiles, worldSeed);
    }

    /**
     * Generates a chunk for server-side use with deterministic properties to ensure consistent
     * generation across server restarts and multiple client sessions.
     *
     * @param chunkX X coordinate of the chunk
     * @param chunkY Y coordinate of the chunk
     * @param worldSeed The world seed for deterministic generation
     * @param biomeManager The biome manager instance
     * @return A fully initialized chunk
     */
    public static Chunk generateChunkForServer(int chunkX, int chunkY, long worldSeed, BiomeManager biomeManager) {
        try {
            Biome primary = findDominantBiomeInChunk(chunkX, chunkY, biomeManager);
            if (primary == null) {
                GameLogger.error("Null primary biome at (" + chunkX + "," + chunkY + "), defaulting to PLAINS");
                primary = biomeManager.getBiome(BiomeType.PLAINS);
            }
            Chunk chunk = new Chunk(chunkX, chunkY, primary, worldSeed);

            fillChunkTiles(chunk, worldSeed, biomeManager);

            applyMountainsIfNeeded(chunk, chunk.getTileData(), worldSeed, primary);
            List<WorldObject> objects = spawnWorldObjects(chunk, chunk.getTileData(), worldSeed);
            GameLogger.info("Generated chunk (" + chunkX + "," + chunkY + ") with " +
                objects.size() + " objects, biome: " + primary.getType());
            chunk.setWorldObjects(objects);
            chunk.setDirty(true);
            chunk.setBiome(primary);

            return chunk;
        } catch (Exception e) {
            GameLogger.error("Error generating chunk at (" + chunkX + "," + chunkY + "): " + e.getMessage());
            e.printStackTrace();
            Biome fallbackBiome = biomeManager.getBiome(BiomeType.PLAINS);
            Chunk fallbackChunk = new Chunk(chunkX, chunkY, fallbackBiome, worldSeed);
            int[][] fallbackTiles = new int[CHUNK_SIZE][CHUNK_SIZE];
            for (int x = 0; x < CHUNK_SIZE; x++) {
                for (int y = 0; y < CHUNK_SIZE; y++) {
                    fallbackTiles[x][y] = TileType.GRASS;  // Simple fallback
                }
            }
            fallbackChunk.setTileData(fallbackTiles);
            fallbackChunk.setDirty(true);
            return fallbackChunk;
        }
    }
    private static void fillChunkTiles(Chunk chunk, long worldSeed, BiomeManager biomeManager) {
        final int size = Chunk.CHUNK_SIZE;
        final int chunkX = chunk.getChunkX();
        final int chunkY = chunk.getChunkY();
        int[][] tiles = new int[size][size];

        // --- OPTIMIZATION START ---
        // Pre-calculate the biome for the entire chunk and its border to avoid redundant calculations.
        BiomeTransitionResult[][] biomeMatrix = biomeManager.computeBiomeMatrixForChunk(chunkX, chunkY);
        // --- OPTIMIZATION END ---

        for (int lx = 0; lx < size; lx++) {
            for (int ly = 0; ly < size; ly++) {
                // Get the pre-calculated biome result for this tile.
                BiomeTransitionResult btr = biomeMatrix[lx][ly];

                // Determine the tile type based on the pre-calculated biome information.
                Biome primaryBiome = btr.getPrimaryBiome();
                if (primaryBiome.getType() == BiomeType.OCEAN) {
                    tiles[lx][ly] = TileType.WATER;
                } else if (primaryBiome.getType() == BiomeType.BEACH) {
                    float worldX = (chunkX * size + lx) * World.TILE_SIZE;
                    float worldY = (chunkY * size + ly) * World.TILE_SIZE;
                    tiles[lx][ly] = TileDataPicker.pickBeachTile(btr, worldX, worldY, worldSeed);
                } else {
                    float worldX = (chunkX * size + lx) * World.TILE_SIZE;
                    float worldY = (chunkY * size + ly) * World.TILE_SIZE;
                    tiles[lx][ly] = TileDataPicker.pickTileFromBiomeOrBlend(btr, worldX, worldY, worldSeed);
                }
            }
        }

        // After setting base tiles, perform cleanup and apply autotiling for shorelines.
        removeInlandOceanPockets(tiles);
        chunk.setTileData(tiles);

        // Mark the chunk as "dirty" to ensure it gets saved.
        chunk.setDirty(true);

        // This is where object spawning was incorrectly located. It has been moved to the main
        // generateChunk method to execute AFTER all terrain modifications (like mountains) are complete.
        // List<WorldObject> objects = spawnWorldObjects(chunk, tiles, worldSeed); // <- REMOVED FROM HERE
        // chunk.setWorldObjects(objects); // <- REMOVED FROM HERE

        Biome chunkBiome = findDominantBiomeInChunk(chunkX, chunkY, biomeManager);

        // The mountain logic is now correctly called from the main generation method *after* this one.
        // applyMountainsIfNeeded(chunk, tiles, worldSeed,chunkBiome);

        chunk.setBiome(chunkBiome);
    }

    /**
     * BFS pass to remove "inland ocean pockets" from sampleTiles. If water is connected
     * to the edges, we keep it. Otherwise, we flood‐fill it with land. This helps
     * avoid random water holes in the interior.
     */
    private static void removeInlandOceanPockets(int[][] sampleTiles) {
        int w = sampleTiles.length;
        int h = sampleTiles[0].length;
        boolean[][] visited = new boolean[w][h];
        Queue<Point> queue = new LinkedList<>();
        for (int x = 0; x < w; x++) {
            if (sampleTiles[x][0] == TileType.WATER) {
                visited[x][0] = true;
                queue.add(new Point(x, 0));
            }
            if (sampleTiles[x][h - 1] == TileType.WATER) {
                visited[x][h - 1] = true;
                queue.add(new Point(x, h - 1));
            }
        }
        for (int y = 0; y < h; y++) {
            if (sampleTiles[0][y] == TileType.WATER) {
                visited[0][y] = true;
                queue.add(new Point(0, y));
            }
            if (sampleTiles[w - 1][y] == TileType.WATER) {
                visited[w - 1][y] = true;
                queue.add(new Point(w - 1, y));
            }
        }
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            Point p = queue.poll();
            for (int[] d : dirs) {
                int nx = p.x + d[0];
                int ny = p.y + d[1];
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                if (!visited[nx][ny] && sampleTiles[nx][ny] == TileType.WATER) {
                    visited[nx][ny] = true;
                    queue.add(new Point(nx, ny));
                }
            }
        }
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (sampleTiles[x][y] == TileType.WATER && !visited[x][y]) {
                    sampleTiles[x][y] = TileType.GRASS; // or pick something random
                }
            }
        }
    }

    // MODIFIED: Pass the biome to this method to check conditions.
    private static void applyMountainsIfNeeded(Chunk chunk, int[][] tiles, long worldSeed, Biome biome) {
        if (biome == null) return;
        BiomeType mainBiome = biome.getType();

        // MODIFIED: Explicitly prevent mountains on BEACH biomes.
        if (mainBiome == BiomeType.OCEAN || mainBiome == BiomeType.BEACH) {
            // Ensure no elevation data exists for these biomes.
            chunk.setElevationData(new int[CHUNK_SIZE][CHUNK_SIZE]); // An array of zeroes.
            return;
        }

        long chunkSeed = generateChunkSeed(worldSeed, chunk.getChunkX(), chunk.getChunkY());
        Random rng = new Random(chunkSeed);

        int maxLayers = ElevationLogic.determineLayersForBiome(rng, mainBiome);
        if (maxLayers <= 0) {
            chunk.setElevationData(new int[CHUNK_SIZE][CHUNK_SIZE]); // Set zero elevation if no mountains.
            return;
        }

        int[][] elevationBands = new int[CHUNK_SIZE][CHUNK_SIZE];
        for (int lx = 0; lx < CHUNK_SIZE; lx++) {
            for (int ly = 0; ly < CHUNK_SIZE; ly++) {
                // MODIFIED: Also prevent mountains from forming on any stray beach sand tiles.
                if (tiles[lx][ly] == TileType.WATER || tiles[lx][ly] == TileType.BEACH_SAND) {
                    elevationBands[lx][ly] = 0;
                }
            }
        }

        ElevationLogic.generateMountainShape(maxLayers, rng, elevationBands, chunk.getChunkX(), chunk.getChunkY(), worldSeed);
        ElevationLogic.smoothElevationBands(elevationBands, rng);
        ElevationLogic.applyMountainTiles(tiles, elevationBands);
        ElevationLogic.autotileCliffs(elevationBands, tiles);
        ElevationLogic.addStairsBetweenLayers(elevationBands, tiles, rng); // Pass Random for stair placement
        ElevationLogic.finalizeStairAccess(tiles, elevationBands);
        ElevationLogic.maybeAddCaveEntrance(elevationBands, tiles, rng);

        // NEW: Store the generated elevation data directly in the chunk.
        chunk.setElevationData(elevationBands);
    }

    /**
     * Figure out which biome is "dominant" in this chunk by counting tile frequencies.
     */
    private static Biome findDominantBiomeInChunk(int chunkX, int chunkY, BiomeManager biomeManager) {
        Map<Biome, Integer> freq = new HashMap<>();

        for (int lx = 0; lx < CHUNK_SIZE; lx++) {
            for (int ly = 0; ly < CHUNK_SIZE; ly++) {
                float worldX = (chunkX * CHUNK_SIZE + lx) * World.TILE_SIZE;
                float worldY = (chunkY * CHUNK_SIZE + ly) * World.TILE_SIZE;
                BiomeTransitionResult btr = biomeManager.getBiomeAt(worldX, worldY);
                Biome tileBiome = btr.getPrimaryBiome();
                freq.merge(tileBiome, 1, Integer::sum);
            }
        }

        Biome best = null;
        int bestCount = 0;
        for (Map.Entry<Biome, Integer> e : freq.entrySet()) {
            if (e.getValue() > bestCount) {
                bestCount = e.getValue();
                best = e.getKey();
            }
        }
        if (best == null) {
            float centerWorldX = (chunkX * CHUNK_SIZE + CHUNK_SIZE * 0.5f) * World.TILE_SIZE;
            float centerWorldY = (chunkY * CHUNK_SIZE + CHUNK_SIZE * 0.5f) * World.TILE_SIZE;
            best = biomeManager.getBiomeAt(centerWorldX, centerWorldY).getPrimaryBiome();
            if (best == null) {
                best = biomeManager.getBiome(BiomeType.PLAINS);
            }
        }
        return best;
    }

    private static boolean isTreeType(WorldObject.ObjectType type) {
        return type == WorldObject.ObjectType.TREE_0 ||
            type == WorldObject.ObjectType.TREE_1 ||
            type == WorldObject.ObjectType.SNOW_TREE ||
            type == WorldObject.ObjectType.HAUNTED_TREE ||
            type == WorldObject.ObjectType.RUINS_TREE ||
            type == WorldObject.ObjectType.APRICORN_TREE ||
            type == WorldObject.ObjectType.RAIN_TREE ||
            type == WorldObject.ObjectType.CHERRY_TREE ||
            type == WorldObject.ObjectType.BEACH_TREE;
    }


    public static boolean canPlaceWorldObject(Chunk chunk, int localX, int localY,
                                              List<WorldObject> currentChunkObjects,
                                              Biome biome,
                                              WorldObject.ObjectType objectType) {
        int worldTileX = chunk.getChunkX() * Chunk.CHUNK_SIZE + localX;
        int worldTileY = chunk.getChunkY() * Chunk.CHUNK_SIZE + localY;
        int tileType = chunk.getTileType(localX, localY);
        if (!chunk.isPassable(localX, localY)) {
            return false;
        }
        if (!biome.getAllowedTileTypes().contains(tileType)) {
            return false;
        }
        if (tileType == TileType.ROCK ||
            tileType == TileType.BEACH_STARFISH ||
            tileType == TileType.BEACH_SHELL ||
            tileType == TileType.WATER) {
            return false;
        }
        if (TileType.isMountainTile(tileType)) {
            return false;
        }
        if (tileType == TileType.FLOWER ||
            tileType == TileType.FLOWER_1 ||
            tileType == TileType.FLOWER_2) {
            return false;
        }
        WorldObject candidate = new WorldObject(worldTileX, worldTileY, null, objectType);
        candidate.ensureTexture();


        return true;
    }

    private static boolean collidesWithAny(WorldObject candidate, List<WorldObject> existing) {
        Rectangle candidateBounds = candidate.getPlacementBoundingBox();
        float spacing = (float) (World.TILE_SIZE * 1.5);
        Rectangle paddedCandidate = new Rectangle(
            candidateBounds.x - spacing,
            candidateBounds.y - spacing,
            candidateBounds.width + 2 * spacing,
            candidateBounds.height + 2 * spacing
        );
        for (WorldObject other : existing) {
            if (paddedCandidate.overlaps(other.getPlacementBoundingBox())) {
                return true;
            }
        }
        return false;
    }



    /**
     * If we want a buffer from water, we skip placing an object if
     * adjacent tile is water or beach. This is purely for aesthetics.
     */
    private static boolean isNextToWater(int[][] tiles, int x, int y) {
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] off : offsets) {
            int nx = x + off[0];
            int ny = y + off[1];
            if (nx < 0 || ny < 0 || nx >= tiles.length || ny >= tiles[0].length) {
                continue;
            }
            if (tiles[nx][ny] == TileType.WATER || tiles[nx][ny] == TileType.BEACH_SAND) {
                return true;
            }
        }
        return false;
    }

    /**
     * A stable hash method to produce a chunk seed from worldSeed + chunk coords.
     */
    private static long generateChunkSeed(long worldSeed, int cx, int cy) {
        long h = worldSeed;
        h = (h * 31) + cx;
        h = (h * 31) + cy;
        return h;
    }

    /**
     * A simpler tile–picking approach for land vs. beach. This is basically
     * your earlier logic, but fully separated for clarity.
     */
    private static class TileDataPicker {

        /**
         * If there's no secondary biome, pick from primary. Otherwise blend.
         */
        public static int pickTileFromBiomeOrBlend(
            BiomeTransitionResult btr, float wx, float wy, long worldSeed
        ) {
            Biome p = btr.getPrimaryBiome();
            Biome s = btr.getSecondaryBiome();
            if (s == null) {
                return pickTileFromBiome(p, wx, wy, worldSeed);
            }
            float factor = btr.getTransitionFactor();
            return pickBlendedTile(p, s, factor, wx, wy, worldSeed);
        }

        /**
         * Single–biome tile distribution.
         */
        public static int pickTileFromBiome(Biome biome, float wx, float wy, long seed) {
            Map<Integer, Integer> dist = biome.getTileDistribution();
            List<Integer> allowed = biome.getAllowedTileTypes();
            if (dist == null || dist.isEmpty() || allowed == null || allowed.isEmpty()) {
                GameLogger.error("Biome " + biome.getName()
                    + " is missing tileDistribution or allowedTileTypes!");
                return TileType.GRASS;
            }
            double sum = 0;
            for (int v : dist.values()) {
                sum += v;
            }
            double noiseValue = NoiseCache.getNoise(seed + 1000, wx, wy, 0.5f);
            double roll = noiseValue * sum;
            double running = 0;
            for (Map.Entry<Integer, Integer> e : dist.entrySet()) {
                running += e.getValue();
                if (roll <= running) {
                    return e.getKey();
                }
            }
            return allowed.get(0);
        }

        /**
         * If we are in the beach ring, possibly pick from "beachTileDistribution."
         */
        public static int pickBeachTile(
            BiomeTransitionResult btr, float wx, float wy, long seed
        ) {
            Biome primary = btr.getPrimaryBiome();
            if (primary != null) {
                Map<Integer, Integer> beachDist = primary.getBeachTileDistribution();
                if (beachDist != null && !beachDist.isEmpty()) {
                    int total = 0;
                    for (int v : beachDist.values()) {
                        total += v;
                    }
                    long localSeed = seed
                        ^ Float.floatToIntBits(wx)
                        ^ Float.floatToIntBits(wy);
                    Random rng = new Random(localSeed);
                    int roll = rng.nextInt(total);
                    int cumul = 0;
                    for (Map.Entry<Integer, Integer> e : beachDist.entrySet()) {
                        cumul += e.getValue();
                        if (roll < cumul) {
                            return e.getKey();
                        }
                    }
                }
            }
            return TileType.BEACH_SAND;
        }

        /**
         * Blends two biome distributions using transition factor.
         */
        private static int pickBlendedTile(
            Biome p, Biome s, float blend, float wx, float wy, long seed
        ) {
            Map<Integer, Integer> pDist = p.getTileDistribution();
            Map<Integer, Integer> sDist = s.getTileDistribution();
            Map<Integer, Integer> pTrans = p.getTransitionTileDistribution();
            Map<Integer, Integer> sTrans = s.getTransitionTileDistribution();
            Set<Integer> allKeys = new HashSet<>();
            if (pDist != null) allKeys.addAll(pDist.keySet());
            if (sDist != null) allKeys.addAll(sDist.keySet());
            if (pTrans != null) allKeys.addAll(pTrans.keySet());
            if (sTrans != null) allKeys.addAll(sTrans.keySet());

            double totalWeight = 0;
            Map<Integer, Double> finalDist = new HashMap<>();

            for (Integer tileId : allKeys) {
                double wp = (pDist != null) ? pDist.getOrDefault(tileId, 0) : 0;
                double ws = (sDist != null) ? sDist.getOrDefault(tileId, 0) : 0;
                double w = blend * wp + (1 - blend) * ws;

                if (pTrans != null && pTrans.containsKey(tileId)) {
                    w += pTrans.get(tileId) * blend; // slight boost for transitions
                }
                if (sTrans != null && sTrans.containsKey(tileId)) {
                    w += sTrans.get(tileId) * (1 - blend);
                }
                if (w > 0) {
                    finalDist.put(tileId, w);
                    totalWeight += w;
                }
            }
            double noiseVal = NoiseCache.getNoise(seed + 999, wx, wy, 0.5f);
            double roll = noiseVal * totalWeight;
            double cum = 0;
            for (Map.Entry<Integer, Double> e : finalDist.entrySet()) {
                cum += e.getValue();
                if (roll <= cum) {
                    return e.getKey();
                }
            }
            List<Integer> fallback = p.getAllowedTileTypes();
            if (fallback != null && !fallback.isEmpty()) {
                return fallback.get(0);
            }
            return TileType.GRASS;
        }
    }

    /**
     * Slightly simpler "ElevationLogic" with BFS-based smoothing
     * for consistent mountain shapes. Derived from your code.
     */
    private static class ElevationLogic {

        public static int determineLayersForBiome(Random rand, BiomeType biome) {
            float baseChance;
            switch (biome) {
                case SNOW:
                    baseChance = 0.83f;
                    break;
                case DESERT:
                    baseChance = 0.88f;
                    break;
                case PLAINS:
                    baseChance = 0.85f;
                    break;
                default:
                    baseChance = 0.93f;
            }
            float r = rand.nextFloat();
            if (r < baseChance) return 0;
            if (r < baseChance + 0.10f) return 1;
            if (r < baseChance + 0.13f) return 2;
            return 0;
        }

        public static void generateMountainShape(
            int maxLayers, Random rand, int[][] bands,
            int chunkX, int chunkY, long worldSeed
        ) {
            int size = CHUNK_SIZE;
            int peakCount = rand.nextInt(2) + 1;

            List<Point> peaks = new ArrayList<>();
            for (int i = 0; i < peakCount; i++) {
                int px = size / 4 + rand.nextInt(size / 2);
                int py = size / 4 + rand.nextInt(size / 2);
                peaks.add(new Point(px, py));
            }
            float baseRadius = size * (0.25f + 0.1f * maxLayers);

            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    double minDist = Double.MAX_VALUE;
                    for (Point p : peaks) {
                        double dx = x - p.x;
                        double dy = y - p.y;
                        double dd = Math.sqrt(dx * dx + dy * dy);
                        if (dd < minDist) {
                            minDist = dd;
                        }
                    }
                    double distFactor = minDist / baseRadius;
                    double elev = Math.max(0, 1.0 - distFactor);
                    float tileWX = (chunkX * size + x) * 0.5f;
                    float tileWY = (chunkY * size + y) * 0.5f;
                    double coarse = OpenSimplex2.noise2(worldSeed + 777, tileWX * 0.05f, tileWY * 0.05f) * 0.15;
                    double detail = OpenSimplex2.noise2(worldSeed + 999, tileWX * 0.1f, tileWY * 0.1f) * 0.1;
                    elev += coarse + detail;

                    elev = Math.max(0, Math.min(1, elev));
                    int band = 0;
                    if (maxLayers == 1) {
                        if (elev > 0.3) band = 1;
                    } else if (maxLayers == 2) {
                        if (elev > 0.65) band = 2;
                        else if (elev > 0.3) band = 1;
                    }
                    bands[x][y] = band;
                }
            }
            applyErosion(bands, rand);
            smoothForCohesion(bands);
        }

        public static void smoothElevationBands(int[][] bands, Random rand) {
            applyErosion(bands, rand);
            smoothForCohesion(bands);
            applyErosion(bands, rand);
            smoothForCohesion(bands);
        }

        /**
         * Slight erosion pass: if a tile is higher than most neighbors, or lower, we adjust it.
         */
        private static void applyErosion(int[][] bands, Random rand) {
            int size = bands.length;
            int[][] temp = smoothingTemp.get();

            for (int x = 1; x < size - 1; x++) {
                for (int y = 1; y < size - 1; y++) {
                    int band = bands[x][y];
                    if (band == 0) {
                        temp[x][y] = 0;
                        continue;
                    }
                    int higher = 0, lower = 0, same = 0;
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            if (dx == 0 && dy == 0) continue;
                            int nx = x + dx, ny = y + dy;
                            if (nx >= size || ny >= size) continue;
                            int nb = bands[nx][ny];
                            if (nb > band) higher++;
                            else if (nb < band) lower++;
                            else same++;
                        }
                    }
                    if (band > 1 && lower > higher + same) {
                        temp[x][y] = band - 1;
                    } else if (higher > lower + same && rand.nextFloat() < 0.1f) {
                        temp[x][y] = band + 1;
                    } else {
                        temp[x][y] = band;
                    }
                }
            }

            for (int x = 1; x < size - 1; x++) {
                System.arraycopy(temp[x], 1, bands[x], 1, size - 2);
            }
        }

        private static void smoothForCohesion(int[][] bands) {
            int size = bands.length;
            int[][] temp = new int[size][size];

            for (int x = 1; x < size - 1; x++) {
                for (int y = 1; y < size - 1; y++) {
                    int band = bands[x][y];
                    if (band == 0) {
                        temp[x][y] = 0;
                        continue;
                    }
                    int sameCount = 0;
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            int nx = x + dx, ny = y + dy;
                            if (nx >= size || ny >= size) continue;
                            if (bands[nx][ny] == band) sameCount++;
                        }
                    }
                    if (sameCount < 4 && band > 1) {
                        temp[x][y] = band - 1;
                    } else {
                        temp[x][y] = band;
                    }
                }
            }

            for (int x = 1; x < size - 1; x++) {
                System.arraycopy(temp[x], 1, bands[x], 1, size - 2);
            }
        }

        public static void applyMountainTiles(int[][] tiles, int[][] bands) {
            int size = tiles.length;
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    int b = bands[x][y];
                    if (b > 0) {
                        int originalTile = tiles[x][y];
                        if (originalTile == TileType.WATER || originalTile == TileType.BEACH_SAND) {
                            continue;
                        }
                        assignMountainTile(x, y, tiles, bands);
                    }
                }
            }
        }

        private static void assignMountainTile(int x, int y, int[][] tiles, int[][] bands) {
            int band = bands[x][y];
            if (band <= 0) return;
            boolean isLowest = (band == 1);
            int n = getBand(x, y + 1, bands);
            int s = getBand(x, y - 1, bands);
            int e = getBand(x + 1, y, bands);
            int w = getBand(x - 1, y, bands);
            boolean topLower = n < band;
            boolean botLower = s < band;
            boolean leftLower = w < band;
            boolean rightLower = e < band;

            int topLeftCorner = isLowest
                ? TileType.MOUNTAIN_TILE_TOP_LEFT_GRASS_BG
                : TileType.MOUNTAIN_TILE_TOP_LEFT_ROCK_BG;

            int topRightCorner = isLowest
                ? TileType.MOUNTAIN_TILE_TOP_RIGHT_GRASS_BG
                : TileType.MOUNTAIN_TILE_TOP_RIGHT_ROCK_BG;

            int botLeftCorner = isLowest
                ? TileType.MOUNTAIN_TILE_BOT_LEFT_GRASS_BG
                : TileType.MOUNTAIN_TILE_BOT_LEFT_ROCK_BG;

            int botRightCorner = isLowest
                ? TileType.MOUNTAIN_TILE_BOT_RIGHT_GRASS_BG
                : TileType.MOUNTAIN_TILE_BOT_RIGHT_ROCK_BG;
            if (!topLower && !botLower && !leftLower && !rightLower) {
                tiles[x][y] = TileType.MOUNTAIN_TILE_CENTER;
                return;
            }
            if (topLower && leftLower && !botLower && !rightLower) {
                tiles[x][y] = topLeftCorner;
                return;
            }
            if (topLower && rightLower && !botLower && !leftLower) {
                tiles[x][y] = topRightCorner;
                return;
            }
            if (botLower && leftLower && !topLower && !rightLower) {
                tiles[x][y] = botLeftCorner;
                return;
            }
            if (botLower && rightLower && !topLower && !leftLower) {
                tiles[x][y] = botRightCorner;
                return;
            }
            if (topLower && !(leftLower || rightLower || botLower)) {
                tiles[x][y] = TileType.MOUNTAIN_TILE_TOP_MID;
                return;
            }
            if (botLower && !(leftLower || rightLower || topLower)) {
                tiles[x][y] = TileType.MOUNTAIN_TILE_BOT_MID;
                return;
            }
            if (leftLower && !(topLower || botLower || rightLower)) {
                tiles[x][y] = TileType.MOUNTAIN_TILE_MID_LEFT;
                return;
            }
            if (rightLower && !(topLower || botLower || leftLower)) {
                tiles[x][y] = TileType.MOUNTAIN_TILE_MID_RIGHT;
                return;
            }
            tiles[x][y] = TileType.MOUNTAIN_TILE_CENTER;
        }

        public static void autotileCliffs(int[][] bands, int[][] tiles) {
            int size = bands.length;
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    int b = getBand(x, y, bands);
                    if (b <= 0) continue;
                    int up = getBand(x, y + 1, bands);
                    int down = getBand(x, y - 1, bands);
                    int left = getBand(x - 1, y, bands);
                    int right = getBand(x + 1, y, bands);

                    boolean topLower = (up < b);
                    boolean botLower = (down < b);
                    boolean leftLower = (left < b);
                    boolean rightLower = (right < b);
                    tiles[x][y] = pickCliffTile(topLower, botLower, leftLower, rightLower, tiles[x][y]);
                }
            }
        }

        private static int pickCliffTile(boolean top, boolean bot, boolean left, boolean right, int original) {
            int lowers = 0;
            if (top) lowers++;
            if (bot) lowers++;
            if (left) lowers++;
            if (right) lowers++;
            if (top && left && !bot && !right) return TileType.MOUNTAIN_TILE_TOP_LEFT_ROCK_BG;
            if (top && right && !bot && !left) return TileType.MOUNTAIN_TILE_TOP_RIGHT_ROCK_BG;
            if (bot && left && !top && !right) return TileType.MOUNTAIN_TILE_BOT_LEFT_ROCK_BG;
            if (bot && right && !top && !left) return TileType.MOUNTAIN_TILE_BOT_RIGHT_ROCK_BG;
            if (lowers == 0) return TileType.MOUNTAIN_TILE_CENTER; // no edges
            if (top && lowers == 1) return TileType.MOUNTAIN_TILE_TOP_MID;
            if (bot && lowers == 1) return TileType.MOUNTAIN_TILE_BOT_MID;
            if (left && lowers == 1) return TileType.MOUNTAIN_TILE_MID_LEFT;
            if (right && lowers == 1) return TileType.MOUNTAIN_TILE_MID_RIGHT;
            return original;
        }

        /**
         * NEW: Rewritten stair generation logic.
         * Places stairs only on south-facing cliff edges to create clear upward paths.
         * Removes the possibility of "side stairs".
         */
        public static void addStairsBetweenLayers(int[][] bands, int[][] tiles, Random rand) {
            int size = bands.length;
            // Iterate from the bottom layer up to the second-to-top layer
            for (int currentLayer = 0; currentLayer < 2; currentLayer++) {
                int upperLayer = currentLayer + 1;

                // Only proceed if the target upper layer actually exists in the chunk
                if (!layerExists(bands, upperLayer)) continue;

                List<Point> candidates = new ArrayList<>();
                // Find all valid south-facing cliff edges between the current and upper layer
                for (int x = 1; x < size - 1; x++) {
                    for (int y = 1; y < size - 1; y++) {
                        // A candidate is a tile on the current layer with a tile of the next layer directly north (up)
                        if (bands[x][y] == currentLayer && bands[x][y + 1] == upperLayer) {
                            // Ensure the ground is clear for access
                            if (isGroundTile(tiles[x][y]) && isGroundTile(tiles[x][y-1])) {
                                candidates.add(new Point(x, y));
                            }
                        }
                    }
                }

                // If we found valid spots, place one or two sets of stairs randomly
                if (!candidates.isEmpty()) {
                    int stairsToPlace = 1; // Can be adjusted, e.g., rand.nextInt(2) + 1 for 1-2 stairs
                    for(int i = 0; i < stairsToPlace && !candidates.isEmpty(); i++) {
                        Point stairPos = candidates.remove(rand.nextInt(candidates.size()));
                        tiles[stairPos.x][stairPos.y + 1] = TileType.STAIRS; // Place stairs on the upper tile
                        GameLogger.info("Placed stairs at " + stairPos.x + ", " + (stairPos.y + 1) + " leading to layer " + upperLayer);

                        // Prevent placing stairs right next to this one
                        candidates.removeIf(p -> Math.abs(p.x - stairPos.x) <= 2 && Math.abs(p.y - stairPos.y) <= 2);
                    }
                }
            }
        }

        // NEW: Helper to check if a tile is a valid ground type for stair placement.
        private static boolean isGroundTile(int tileType) {
            return tileType == TileType.GRASS ||
                tileType == TileType.MOUNTAIN_TILE_CENTER ||
                tileType == TileType.SAND ||
                tileType == TileType.SNOW ||
                tileType == TileType.SNOWY_GRASS;
        }

        public static void finalizeStairAccess(int[][] tiles, int[][] bands) {
            int size = tiles.length;
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    if (tiles[x][y] == TileType.STAIRS) {
                        int band = getBand(x, y, bands);
                        int ny = y + 1;
                        if (ny < size) {
                            int upband = getBand(x, ny, bands);
                            if (upband == band + 1) {
                                tiles[x][ny] = TileType.MOUNTAIN_TILE_CENTER;
                            }
                        }
                    }
                }
            }
        }

        public static void maybeAddCaveEntrance(int[][] bands, int[][] tiles, Random rand) {
            if (rand.nextFloat() > 0.025f) return; // small chance
            int size = tiles.length;
            for (int x = 1; x < size - 1; x++) {
                for (int y = 1; y < size - 1; y++) {
                    int band = getBand(x, y, bands);
                    if (band >= 2 && isCliffTile(tiles[x][y])) {
                        if (isStraightCliffEdge(x, y, bands)) {
                            tiles[x][y] = TileType.CAVE_ENTRANCE;
                            return;
                        }
                    }
                }
            }
        }

        private static boolean isStraightCliffEdge(int x, int y, int[][] bands) {
            int band = getBand(x, y, bands);
            int up = getBand(x, y + 1, bands);
            int down = getBand(x, y - 1, bands);
            int left = getBand(x - 1, y, bands);
            int right = getBand(x + 1, y, bands);
            if (up < band && down == band && left == band && right == band) return true;
            if (down < band && up == band && left == band && right == band) return true;
            if (left < band && right == band && up == band && down == band) return true;
            return right < band && left == band && up == band && down == band;
        }
        private static boolean isCliffTile(int tile) {
            return tile != TileType.MOUNTAIN_TILE_CENTER
                && tile != TileType.GRASS
                && tile != TileType.MOUNTAIN_PEAK
                && tile != TileType.MOUNTAIN_SNOW_BASE
                && tile != TileType.STAIRS
                && tile != TileType.CAVE_ENTRANCE
                && tile != TileType.FLOWER && tile != TileType.FLOWER_1 && tile != TileType.FLOWER_2
                && tile != TileType.GRASS_2 && tile != TileType.GRASS_3
                && tile != TileType.TALL_GRASS && tile != TileType.TALL_GRASS_2 && tile != TileType.TALL_GRASS_3
                && tile != TileType.SAND && tile != TileType.DESERT_SAND
                && tile != TileType.HAUNTED_GRASS && tile != TileType.HAUNTED_TALL_GRASS
                && tile != TileType.RAIN_FOREST_GRASS && tile != TileType.RAIN_FOREST_TALL_GRASS
                && tile != TileType.FOREST_GRASS && tile != TileType.FOREST_TALL_GRASS
                && tile != TileType.SNOW && tile != TileType.SNOW_2 && tile != TileType.SNOW_3 && tile != TileType.SNOW_TALL_GRASS
                && tile != TileType.RUINS_GRASS && tile != TileType.RUINS_GRASS_0 && tile != TileType.RUINS_TALL_GRASS
                && tile != TileType.RUINS_BRICKS;
        }
        private static boolean tryPlaceStairsRandom(int[][] bands, int[][] tiles, int from, int to) {
            int size = bands.length;
            for (int x = 1; x < size - 1; x++) {
                for (int y = 1; y < size - 1; y++) {
                    if (canPlaceStairsHere(x, y, bands, tiles, from, to)) {
                        tiles[x][y] = TileType.STAIRS;
                        return true;
                    }
                }
            }
            return false;
        }

        private static boolean canPlaceStairsHere(int x, int y, int[][] bands, int[][] tiles, int fromBand, int toBand) {
            if (bands[x][y] != fromBand) return false;
            if (!isCliffTile(tiles[x][y])) return false;
            if (!hasAdjacentBand(x, y, toBand, bands)) return false;
            return !hasNearbyStairs(x, y, tiles);
        }

        private static boolean hasAdjacentBand(int x, int y, int target, int[][] bands) {
            int up = getBand(x, y + 1, bands);
            int down = getBand(x, y - 1, bands);
            int left = getBand(x - 1, y, bands);
            int right = getBand(x + 1, y, bands);
            return (up == target || down == target || left == target || right == target);
        }

        private static boolean hasNearbyStairs(int x, int y, int[][] tiles) {
            int size = tiles.length;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -3; dy <= 3; dy++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx >= 0 && nx < size && ny >= 0 && ny < size) {
                        if (tiles[nx][ny] == TileType.STAIRS) return true;
                    }
                }
            }
            return false;
        }

        private static boolean layerExists(int[][] bands, int target) {
            for (int[] row : bands) {
                for (int b : row) {
                    if (b == target) return true;
                }
            }
            return false;
        }

        private static int getBand(int x, int y, int[][] arr) {
            if (x < 0 || y < 0 || x >= arr.length || y >= arr[0].length) return -1;
            return arr[x][y];
        }
    }
    private static class Point {
        int x, y;

        Point(int xx, int yy) {
            x = xx;
            y = yy;
        }
    }
}
