package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.math.MathUtils;
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

    // We reuse a small array for smoothing/erosion steps
    private static final ThreadLocal<int[][]> smoothingTemp = ThreadLocal.withInitial(() -> {
        int[][] arr = new int[TEMP_SIZE][TEMP_SIZE];
        for (int i = 0; i < TEMP_SIZE; i++) {
            arr[i] = new int[TEMP_SIZE];
        }
        return arr;
    });

    public static Chunk generateChunk(int chunkX, int chunkY, long worldSeed, BiomeManager biomeManager) {
        // determine an approximate biome at chunk center
        float centerWorldX = (chunkX * Chunk.CHUNK_SIZE + Chunk.CHUNK_SIZE * 0.5f) * World.TILE_SIZE;
        float centerWorldY = (chunkY * Chunk.CHUNK_SIZE + Chunk.CHUNK_SIZE * 0.5f) * World.TILE_SIZE;

        BiomeTransitionResult centerBTR = GameContext.get().getBiomeManager().getBiomeAt(centerWorldX, centerWorldY);
        Biome primary = centerBTR.getPrimaryBiome();
        if (primary == null) primary = GameContext.get().getBiomeManager().getBiome(BiomeType.PLAINS);

        Chunk chunk = new Chunk(chunkX, chunkY, primary, worldSeed);

        // The new "fill"
        fillChunkTiles(chunk, worldSeed, GameContext.get().getBiomeManager());

        return chunk;
    }

    public static Chunk generateChunkForServer(int chunkX, int chunkY, long worldSeed, BiomeManager biomeManager) {
        // Determine an approximate biome at the chunk center.
        float centerWorldX = (chunkX * CHUNK_SIZE + CHUNK_SIZE * 0.5f) * World.TILE_SIZE;
        float centerWorldY = (chunkY * CHUNK_SIZE + CHUNK_SIZE * 0.5f) * World.TILE_SIZE;
        BiomeTransitionResult centerBTR = biomeManager.getBiomeAt(centerWorldX, centerWorldY);
        Biome primary = centerBTR.getPrimaryBiome();
        if (primary == null) {
            primary = biomeManager.getBiome(BiomeType.PLAINS);
        }

        // Create a new chunk with the determined primary biome.
        Chunk chunk = new Chunk(chunkX, chunkY, primary, worldSeed);

        final int size = Chunk.CHUNK_SIZE;
        final int MARGIN = 2;
        final int sampleW = size + 2 * MARGIN;
        final int sampleH = size + 2 * MARGIN;
        int[][] sampleTiles = new int[sampleW][sampleH];

        // Phase 1: Sample tiles over an expanded region for smoother edges.
        for (int sx = 0; sx < sampleW; sx++) {
            for (int sy = 0; sy < sampleH; sy++) {
                // Convert sample coordinates to world tile coordinates.
                int worldTileX = (chunkX * size) + (sx - MARGIN);
                int worldTileY = (chunkY * size) + (sy - MARGIN);
                float worldX = worldTileX * World.TILE_SIZE;
                float worldY = worldTileY * World.TILE_SIZE;

                // 1) Domain–warp using the provided biomeManager.
                float[] warped = biomeManager.domainWarp(worldX, worldY);

                // 2) Find the closest island.
                BiomeManager.Island isl = biomeManager.findClosestIsland(warped[0], warped[1]);
                if (isl == null) {
                    sampleTiles[sx][sy] = TileType.WATER;
                    continue;
                }

                // Compute distance and distortion.
                float dx = warped[0] - isl.centerX;
                float dy = warped[1] - isl.centerY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float angle = MathUtils.atan2(dy, dx);
                float distort = OpenSimplex2.noise2(isl.seed, MathUtils.cos(angle), MathUtils.sin(angle));
                distort = Math.max(0, distort);

                float newExpandFactor = 1.3f;
                float reducedFactor = 0.1f;
                float effectiveRadius = isl.radius * newExpandFactor + (isl.radius * newExpandFactor * reducedFactor * distort);

// Define a fixed beach band (10% of the effective radius)
                float beachBand = effectiveRadius * 0.1f;
                float innerThreshold = effectiveRadius;           // land ends here
                float outerThreshold = effectiveRadius + beachBand; // beach occupies this band

                if (dist < innerThreshold) {
                    // Land tile: use the land biome Voronoi method.
                    BiomeTransitionResult landTrans = GameContext.get().getBiomeManager().landBiomeVoronoi(warped[0], warped[1]);
                    sampleTiles[sx][sy] = TileDataPicker.pickTileFromBiomeOrBlend(landTrans, worldX, worldY, worldSeed);
                } else if (dist < outerThreshold) {
                    // Entirely beach – no blending.
                    BiomeTransitionResult beachTrans = new BiomeTransitionResult(
                        GameContext.get().getBiomeManager().getBiome(BiomeType.BEACH),
                        null,
                        1f
                    );
                    sampleTiles[sx][sy] = TileDataPicker.pickBeachTile(beachTrans, worldX, worldY, worldSeed);
                } else {
                    // Outside the island – ocean.
                    sampleTiles[sx][sy] = TileType.WATER;
                }
            }
        }

        // Phase 2: Remove inland ocean pockets.
        removeInlandOceanPockets(sampleTiles);

        // Phase 3: Copy the central region (the real chunk) from sampleTiles.
        int[][] tiles = new int[size][size];
        for (int lx = 0; lx < size; lx++) {
            System.arraycopy(sampleTiles[lx + MARGIN], MARGIN, tiles[lx], 0, size);
        }
        chunk.setTileData(tiles);

        // Apply auto-tiling.
        try {
            new AutoTileSystem().applyShorelineAutotiling(chunk, 0);
        } catch (Exception e) {
            GameLogger.error("Error during autotiling: " + e.getMessage());
        }

        // Apply mountain generation if needed.
        applyMountainsIfNeeded(chunk, tiles, worldSeed);
        chunk.setDirty(true);

        // Spawn world objects (e.g. trees, stones) inside the chunk.
        List<WorldObject> objects = spawnWorldObjects(chunk, tiles, worldSeed);
        GameLogger.info("spawnWorldObjects produced " + objects.size() + " objects for chunk (" +
            chunk.getChunkX() + "," + chunk.getChunkY() + ").");
        chunk.setWorldObjects(objects);

        // Determine the dominant biome across the chunk.
        Biome chunkBiome = findDominantBiomeInChunk(chunk,biomeManager);
        chunk.setBiome(chunkBiome);

        return chunk;
    }


    /**
     * Fills the chunk's tile data with a large island ring, ensuring beach outside
     * the island boundary, then ocean beyond, plus smoothing any random pockets.
     */
    // The new method in UnifiedWorldGenerator:
    private static void fillChunkTiles(Chunk chunk, long worldSeed, BiomeManager biomeManager) {
        final int size = Chunk.CHUNK_SIZE;
        final int chunkX = chunk.getChunkX();
        final int chunkY = chunk.getChunkY();

        // Prepare the tile array
        int[][] tiles = new int[size][size];

        // We'll sample a slightly bigger region around the chunk
        // so that transitions along chunk edges are smoother.
        final int MARGIN = 2;
        final int sampleW = size + 2 * MARGIN;
        final int sampleH = size + 2 * MARGIN;
        int[][] sampleTiles = new int[sampleW][sampleH];

        // Phase 1: For each tile in the expanded sample area:
        for (int sx = 0; sx < sampleW; sx++) {
            for (int sy = 0; sy < sampleH; sy++) {
                // Convert to actual world tile coords:
                int worldTileX = (chunkX * size) + (sx - MARGIN);
                int worldTileY = (chunkY * size) + (sy - MARGIN);

                float worldX = worldTileX * World.TILE_SIZE;
                float worldY = worldTileY * World.TILE_SIZE;

                // 1) domain-warp
                float[] warped = GameContext.get().getBiomeManager().domainWarp(worldX, worldY);

                // 2) find closest island => ocean/land
                BiomeManager.Island isl = GameContext.get().getBiomeManager().findClosestIsland(warped[0], warped[1]);
                if (isl == null) {
                    sampleTiles[sx][sy] = TileType.WATER;
                    continue;
                }

                // distance, distortion => effectiveRadius
                float dx = warped[0] - isl.centerX;
                float dy = warped[1] - isl.centerY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);

                float angle = MathUtils.atan2(dy, dx);
                float distort = OpenSimplex2.noise2(isl.seed, MathUtils.cos(angle), MathUtils.sin(angle));
                distort = Math.max(0, distort);

                float newExpandFactor = 1.3f;
                float reducedFactor = 0.1f;
                float effectiveRadius = isl.radius * newExpandFactor
                    + (isl.radius * newExpandFactor * reducedFactor * distort);

                float beachBand = effectiveRadius * 0.1f;
                float innerThreshold = effectiveRadius - (beachBand * 0.5f);
                float outerThreshold = effectiveRadius + (beachBand * 0.5f);

                if (dist < innerThreshold) {
                    // land => pick tile from the land Voronoi approach
                    BiomeTransitionResult landTrans = GameContext.get().getBiomeManager().landBiomeVoronoi(warped[0], warped[1]);
                    sampleTiles[sx][sy] = TileDataPicker.pickTileFromBiomeOrBlend(
                        landTrans, worldX, worldY, worldSeed
                    );
                } else if (dist > outerThreshold) {
                    // ocean
                    sampleTiles[sx][sy] = TileType.WATER;
                } else {
                    // beach ring => blend from beach → ocean
                    float t = (dist - innerThreshold) / (outerThreshold - innerThreshold);
                    BiomeTransitionResult beachTrans = new BiomeTransitionResult(
                        GameContext.get().getBiomeManager().getBiome(BiomeType.BEACH),
                        GameContext.get().getBiomeManager().getBiome(BiomeType.OCEAN),
                        t
                    );
                    sampleTiles[sx][sy] = TileDataPicker.pickBeachTile(beachTrans, worldX, worldY, worldSeed);
                }
            }
        }

        // Phase 2: remove small "inland pockets" of water if you want
        removeInlandOceanPockets(sampleTiles);

        // Phase 3: copy the central region (the real chunk) out of sampleTiles
        for (int lx = 0; lx < size; lx++) {
            System.arraycopy(sampleTiles[lx + MARGIN], MARGIN, tiles[lx], 0, size);
        }

        // set chunk tile data
        chunk.setTileData(tiles);
        try {
            new AutoTileSystem().applyShorelineAutotiling(chunk, 0);
        } catch (Exception e) {
            GameLogger.error("Error during autotiling: " + e.getMessage());
        }
        applyMountainsIfNeeded(chunk, tiles, worldSeed);
        chunk.setDirty(true);

        // spawn objects (trees, stones, etc.)
        List<WorldObject> objects = spawnWorldObjects(chunk, tiles, worldSeed);
        GameLogger.info("spawnWorldObjects produced " + objects.size() + " objects for chunk (" +
            chunk.getChunkX() + "," + chunk.getChunkY() + ").");
        chunk.setWorldObjects(objects);
        // find "dominant" chunk–wide biome for display
        Biome chunkBiome = findDominantBiomeInChunk(chunk, biomeManager);
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

        // We'll keep track of visited, and anything that is water connected to boundary is "ocean"
        boolean[][] visited = new boolean[w][h];

        // We'll BFS from edges: any water tile on the boundary means that region is ocean
        Queue<Point> queue = new LinkedList<>();

        // 1) Add edge water tiles to queue
        for (int x = 0; x < w; x++) {
            // top row
            if (sampleTiles[x][0] == TileType.WATER) {
                visited[x][0] = true;
                queue.add(new Point(x, 0));
            }
            // bottom row
            if (sampleTiles[x][h - 1] == TileType.WATER) {
                visited[x][h - 1] = true;
                queue.add(new Point(x, h - 1));
            }
        }
        for (int y = 0; y < h; y++) {
            // left col
            if (sampleTiles[0][y] == TileType.WATER) {
                visited[0][y] = true;
                queue.add(new Point(0, y));
            }
            // right col
            if (sampleTiles[w - 1][y] == TileType.WATER) {
                visited[w - 1][y] = true;
                queue.add(new Point(w - 1, y));
            }
        }

        // 2) BFS to mark all connected "ocean"
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

        // 3) Any water tile that is NOT visited => it's an inland pocket => fill it with random land
        // For simplicity, fill with grass or something
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (sampleTiles[x][y] == TileType.WATER && !visited[x][y]) {
                    // We fill it with some land tile, e.g. grass
                    sampleTiles[x][y] = TileType.GRASS; // or pick something random
                }
            }
        }
    }

    /**
     * If the chunk's main biome calls for mountains, we generate them and apply
     * them to the chunk's tile array.
     */
    private static void applyMountainsIfNeeded(Chunk chunk, int[][] tiles, long worldSeed) {
        // We'll skip if the chunk is purely ocean or purely beach
        BiomeType mainBiome = chunk.getBiome().getType();
        if (mainBiome == BiomeType.OCEAN || mainBiome == BiomeType.BEACH) {
            return;
        }
        // Decide how many layers
        long chunkSeed = generateChunkSeed(worldSeed, chunk.getChunkX(), chunk.getChunkY());
        Random rng = new Random(chunkSeed);

        int maxLayers = ElevationLogic.determineLayersForBiome(rng, mainBiome);
        if (maxLayers <= 0) {
            return;
        }

        // Build an array for mountain bands
        int[][] elevationBands = new int[CHUNK_SIZE][CHUNK_SIZE];
        // Zero out ocean & beach
        for (int lx = 0; lx < CHUNK_SIZE; lx++) {
            for (int ly = 0; ly < CHUNK_SIZE; ly++) {
                if (tiles[lx][ly] == TileType.WATER || tiles[lx][ly] == TileType.BEACH_SAND) {
                    elevationBands[lx][ly] = 0;
                }
            }
        }

        // Actually generate the shape
        ElevationLogic.generateMountainShape(
            maxLayers, rng, elevationBands, chunk.getChunkX(), chunk.getChunkY(), worldSeed
        );

        // Additional smoothing if needed
        ElevationLogic.smoothElevationBands(elevationBands, rng);

        // Now apply the mountain tile-IDs to the chunk's tile array
        ElevationLogic.applyMountainTiles(tiles, elevationBands);
        ElevationLogic.autotileCliffs(elevationBands, tiles);
        ElevationLogic.addStairsBetweenLayers(elevationBands, tiles);
        ElevationLogic.finalizeStairAccess(tiles, elevationBands);
        ElevationLogic.maybeAddCaveEntrance(elevationBands, tiles, rng);

        // Store it in the chunk
        chunk.setElevationBands(elevationBands);
    }

    /**
     * Figure out which biome is "dominant" in this chunk by counting tile frequencies.
     */
    private static Biome findDominantBiomeInChunk(Chunk chunk, BiomeManager biomeManager) {
        Map<Biome, Integer> freq = new HashMap<>();
        int[][] tileData = chunk.getTileData();
        for (int lx = 0; lx < CHUNK_SIZE; lx++) {
            for (int ly = 0; ly < CHUNK_SIZE; ly++) {
                float worldX = (chunk.getChunkX() * CHUNK_SIZE + lx) * World.TILE_SIZE;
                float worldY = (chunk.getChunkY() * CHUNK_SIZE + ly) * World.TILE_SIZE;
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
            best = biomeManager.getBiome(BiomeType.PLAINS);
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

    /**
     * Spawns trees, stones, etc. in the chunk interior, skipping ocean/beach,
     * and ensuring a buffer from water. Reuses your original logic, but placed
     * into a method for clarity.
     */

    private static List<WorldObject> spawnWorldObjects(Chunk chunk, int[][] tiles, long worldSeed) {
        List<WorldObject> spawned = new ArrayList<>();
        Random rng = new Random(worldSeed + (chunk.getChunkX() * 31L) ^ (chunk.getChunkY() * 1337L));
        Biome b = chunk.getBiome();
        List<WorldObject.ObjectType> spawnable = b.getSpawnableObjects();
        if (spawnable == null || spawnable.isEmpty()) {
            GameLogger.error("Biome " + b.getName() + " returned no spawnable objects.");
            return spawned;
        }
        for (WorldObject.ObjectType ot : spawnable) {
            double multiplier = isTreeType(ot) ? 0.5 : 0.1;
            double baseChance = b.getSpawnChanceForObject(ot);
            double spawnChance = baseChance * multiplier;
            int attempts = (int) (CHUNK_SIZE * CHUNK_SIZE * spawnChance);
            GameLogger.info("For " + ot.name() + ": spawnChance=" + spawnChance + ", attempts=" + attempts);
            for (int i = 0; i < attempts; i++) {
                int lx = rng.nextInt(CHUNK_SIZE);
                int ly = rng.nextInt(CHUNK_SIZE);
                int tileType = tiles[lx][ly];
                if (tileType == TileType.WATER || tileType == TileType.BEACH_SAND) continue;
                // For trees, skip the water-buffer check.
                if (!isTreeType(ot) && isNextToWater(tiles, lx, ly)) continue;
                if (!b.getAllowedTileTypes().contains(tileType)) continue;
                if (!chunk.isPassable(lx, ly)) continue;
                int worldTileX = chunk.getChunkX() * CHUNK_SIZE + lx;
                int worldTileY = chunk.getChunkY() * CHUNK_SIZE + ly;
                WorldObject candidate = new WorldObject(worldTileX, worldTileY, null, ot);
                candidate.ensureTexture();
                if (!collidesWithAny(candidate, spawned)) {
                    if (canPlaceWorldObject(chunk, lx, ly, spawned, b, ot)) {
                        spawned.add(candidate);
                        GameLogger.info("Added " + ot.name() + " at (" + worldTileX + "," + worldTileY + ")");
                    } else {
                        GameLogger.info("Rejected " + ot.name() + " at (" + worldTileX + "," + worldTileY + ") by placement rules.");
                    }
                } else {
                    GameLogger.info("Rejected " + ot.name() + " at (" + worldTileX + "," + worldTileY + ") due to collision.");
                }
            }
        }
        return spawned;
    }
    public static boolean canPlaceWorldObject(Chunk chunk, int localX, int localY,
                                              List<WorldObject> currentChunkObjects,
                                              Biome biome,
                                              WorldObject.ObjectType objectType) {
        // Convert local coordinates (within this chunk) to world tile coordinates.
        int worldTileX = chunk.getChunkX() * Chunk.CHUNK_SIZE + localX;
        int worldTileY = chunk.getChunkY() * Chunk.CHUNK_SIZE + localY;
        int tileType = chunk.getTileType(localX, localY);

        // Basic checks
        if (!chunk.isPassable(localX, localY)) {
            return false;
        }
        if (!biome.getAllowedTileTypes().contains(tileType)) {
            return false;
        }
        // Extra restrictions: do not place on disallowed terrain types.
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
        // Create a candidate object at the desired location.
        WorldObject candidate = new WorldObject(worldTileX, worldTileY, null, objectType);
        candidate.ensureTexture();


        return true;
    }

    private static boolean collidesWithAny(WorldObject candidate, List<WorldObject> existing) {
        // Use the placement bounding box for overlap checks
        Rectangle candidateBounds = candidate.getPlacementBoundingBox();
        // Define a spacing margin equal to one tile
        float spacing = (float) (World.TILE_SIZE * 1.5);

        // Inflate candidate bounds by the spacing margin
        Rectangle paddedCandidate = new Rectangle(
            candidateBounds.x - spacing,
            candidateBounds.y - spacing,
            candidateBounds.width + 2 * spacing,
            candidateBounds.height + 2 * spacing
        );

        // Check overlap against every placed object using their placement bounding boxes
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

    // ─────────────────────────────────────────────────────────────────────────
    // Below: Smaller "helper classes" (TileDataPicker, ElevationLogic, BFS, etc.)
    // ─────────────────────────────────────────────────────────────────────────

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
                    // Weighted random pick
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
            // fallback
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

            // fallback
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
            // Simple logic: reduce the chance of random lumps
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
                    // Dist from nearest peak
                    double minDist = Double.MAX_VALUE;
                    for (Point p : peaks) {
                        double dx = x - p.x;
                        double dy = y - p.y;
                        double dd = Math.sqrt(dx * dx + dy * dy);
                        if (dd < minDist) {
                            minDist = dd;
                        }
                    }
                    // Convert distance to an "elevation" 0..1
                    double distFactor = minDist / baseRadius;
                    double elev = Math.max(0, 1.0 - distFactor);

                    // Add some noise
                    float tileWX = (chunkX * size + x) * 0.5f;
                    float tileWY = (chunkY * size + y) * 0.5f;
                    double coarse = OpenSimplex2.noise2(worldSeed + 777, tileWX * 0.05f, tileWY * 0.05f) * 0.15;
                    double detail = OpenSimplex2.noise2(worldSeed + 999, tileWX * 0.1f, tileWY * 0.1f) * 0.1;
                    elev += coarse + detail;

                    elev = Math.max(0, Math.min(1, elev));

                    // band thresholds
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
            // Erode + smooth
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
                    // if it’s too isolated, reduce band
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

        // The rest is your same logic for applying mountain tiles, autotiling cliffs, etc.
        public static void applyMountainTiles(int[][] tiles, int[][] bands) {
            int size = tiles.length;
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    int b = bands[x][y];
                    if (b > 0) {
                        assignMountainTile(x, y, tiles, bands);
                    }
                }
            }
        }

        private static void assignMountainTile(int x, int y, int[][] tiles, int[][] bands) {
            int band = bands[x][y];
            if (band <= 0) return;
            boolean isLowest = (band == 1);

            // We'll do your same approach of checking neighbors
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

            // Then the usual set of conditions (same as your code)
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
            // Same logic as your "chooseCliffTile" snippet
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
            // fallback
            return original;
        }

        public static void addStairsBetweenLayers(int[][] bands, int[][] tiles) {
            // same BFS approach as your original code for placing stairs
            int size = bands.length;
            for (int from = 0; from < 2; from++) {
                int to = from + 1;
                if (!layerExists(bands, to)) continue;
                int stairsPlaced = 0;
                int required = (from == 0) ? 4 : 2;
                if (tryPlaceStairsSide(bands, tiles, from, to, "north")) stairsPlaced++;
                if (tryPlaceStairsSide(bands, tiles, from, to, "south")) stairsPlaced++;
                if (tryPlaceStairsSide(bands, tiles, from, to, "east")) stairsPlaced++;
                if (tryPlaceStairsSide(bands, tiles, from, to, "west")) stairsPlaced++;
                while (stairsPlaced < required) {
                    if (tryPlaceStairsRandom(bands, tiles, from, to)) stairsPlaced++;
                    else break;
                }
            }
        }

        public static void finalizeStairAccess(int[][] tiles, int[][] bands) {
            // same idea as your code
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
            // if top < band but bottom=band, left=band, right=band => let's call it an "edge"
            // etc
            if (up < band && down == band && left == band && right == band) return true;
            if (down < band && up == band && left == band && right == band) return true;
            if (left < band && right == band && up == band && down == band) return true;
            return right < band && left == band && up == band && down == band;
        }

        // same as your code
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

        private static boolean tryPlaceStairsSide(int[][] bands, int[][] tiles, int from, int to, String side) {
            int size = bands.length;
            int startX, endX, startY, endY;
            switch (side) {
                case "north":
                    startX = 1;
                    endX = size - 1;
                    startY = size - 2;
                    endY = size - 1;
                    break;
                case "south":
                    startX = 1;
                    endX = size - 1;
                    startY = 1;
                    endY = 2;
                    break;
                case "east":
                    startX = size - 2;
                    endX = size - 1;
                    startY = 1;
                    endY = size - 1;
                    break;
                case "west":
                    startX = 1;
                    endX = 2;
                    startY = 1;
                    endY = size - 1;
                    break;
                default:
                    return false;
            }
            for (int x = startX; x < endX; x++) {
                for (int y = startY; y < endY; y++) {
                    if (canPlaceStairsHere(x, y, bands, tiles, from, to)) {
                        tiles[x][y] = TileType.STAIRS;
                        return true;
                    }
                }
            }
            return false;
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

    // Simple BFS point
    private static class Point {
        int x, y;

        Point(int xx, int yy) {
            x = xx;
            y = yy;
        }
    }
}
