package org.discord.utils;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.managers.BiomeManager;
import io.github.pokemeetup.managers.BiomeTransitionResult;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.OpenSimplex2;
import io.github.pokemeetup.utils.NoiseCache;
import io.github.pokemeetup.utils.textures.TileType;
import org.discord.utils.ServerWorldManager.TimedChunk;

import java.util.*;

/**
 * A server-side helper class that smooths chunk edges
 * by reassigning tiles along boundaries if two chunks have drastically different tiles.
 */
public class EdgeSmoother {

    private static final int CHUNK_SIZE = 16; // same as in your Chunk class
    private static final int TILE_SIZE = 32;  // same as your server's definition
    private static final int EDGE_WIDTH = 2;  // how many tiles from the edge to unify

    /**
     * After you load or generate a chunk, call this to unify edges with any loaded neighbors.
     *
     * @param chunk          the newly loaded or generated chunk
     * @param chunkMap       a map of Vector2 → TimedChunk (the cache in your ServerWorldManager)
     * @param biomeManager   your server's BiomeManager
     * @param worldSeed      the seed from the world config
     */
    public static void smoothEdgesWithNeighbors(Chunk chunk,
                                                Map<Vector2, TimedChunk> chunkMap,
                                                BiomeManager biomeManager,
                                                long worldSeed)
    {
        if (chunk == null) return;

        int cx = chunk.getChunkX();
        int cy = chunk.getChunkY();

        // offsets for the four neighbors
        Vector2[] neighbors = {
            new Vector2(cx, cy+1), // north
            new Vector2(cx, cy-1), // south
            new Vector2(cx+1, cy), // east
            new Vector2(cx-1, cy)  // west
        };

        for (Vector2 nPos : neighbors) {
            TimedChunk timed = chunkMap.get(nPos);
            if (timed == null) {
                // neighbor not loaded
                continue;
            }
            Chunk neighbor = timed.chunk;
            if (neighbor == null) continue;
            unifyBoundaryTiles(chunk, neighbor, biomeManager, worldSeed);
        }
    }

    /**
     * Merges the boundary between two adjacent chunks by forcibly picking transitional tiles
     * if their current tiles are drastically different.
     */
    private static void unifyBoundaryTiles(Chunk cA,
                                           Chunk cB,
                                           BiomeManager biomeManager,
                                           long worldSeed)
    {
        int dx = cB.getChunkX() - cA.getChunkX();
        int dy = cB.getChunkY() - cA.getChunkY();

        int[][] tilesA = cA.getTileData();
        int[][] tilesB = cB.getTileData();

        // figure out direction
        if (dy == 1 && dx == 0) {
            // cB is north
            for (int x = 0; x < CHUNK_SIZE; x++) {
                for (int offset = 0; offset < EDGE_WIDTH; offset++) {
                    int yA = CHUNK_SIZE - 1 - offset;
                    int yB = offset;
                    unifyTilePair(tilesA, x, yA, tilesB, x, yB, cA, cB, biomeManager, worldSeed);
                }
            }
        }
        else if (dy == -1 && dx == 0) {
            // cB is south
            for (int x = 0; x < CHUNK_SIZE; x++) {
                for (int offset = 0; offset < EDGE_WIDTH; offset++) {
                    int yA = offset;
                    int yB = CHUNK_SIZE - 1 - offset;
                    unifyTilePair(tilesA, x, yA, tilesB, x, yB, cA, cB, biomeManager, worldSeed);
                }
            }
        }
        else if (dx == 1 && dy == 0) {
            // cB is east
            for (int y = 0; y < CHUNK_SIZE; y++) {
                for (int offset = 0; offset < EDGE_WIDTH; offset++) {
                    int xA = CHUNK_SIZE - 1 - offset;
                    int xB = offset;
                    unifyTilePair(tilesA, xA, y, tilesB, xB, y, cA, cB, biomeManager, worldSeed);
                }
            }
        }
        else if (dx == -1 && dy == 0) {
            // cB is west
            for (int y = 0; y < CHUNK_SIZE; y++) {
                for (int offset = 0; offset < EDGE_WIDTH; offset++) {
                    int xA = offset;
                    int xB = CHUNK_SIZE - 1 - offset;
                    unifyTilePair(tilesA, xA, y, tilesB, xB, y, cA, cB, biomeManager, worldSeed);
                }
            }
        }

        cA.setDirty(true);
        cB.setDirty(true);
    }

    /**
     * If these two tiles differ drastically, pick a transitional tile (like “snowy grass”)
     * from the combined distributions of their biomes.
     */
    private static void unifyTilePair(int[][] tilesA, int xA, int yA,
                                      int[][] tilesB, int xB, int yB,
                                      Chunk cA, Chunk cB,
                                      BiomeManager biomeManager,
                                      long worldSeed)
    {
        int oldA = tilesA[xA][yA];
        int oldB = tilesB[xB][yB];
        if (oldA == oldB) return;

        // guess which biome each tile belongs to
        Biome biomeA = guessBiomeForTile(cA, oldA, xA, yA, biomeManager);
        Biome biomeB = guessBiomeForTile(cB, oldB, xB, yB, biomeManager);
        if (biomeA == null || biomeB == null || biomeA == biomeB) {
            return;
        }

        // pick a mid‐blend tile from the combined distribution
        float worldCoordX = (cA.getChunkX() * CHUNK_SIZE + xA) * World.TILE_SIZE;
        float worldCoordY = (cA.getChunkY() * CHUNK_SIZE + yA) * World.TILE_SIZE;

        int newTile = pickTileFromBlendedBiomes(biomeA, biomeB, 0.5f,
            worldCoordX, worldCoordY, worldSeed);

        tilesA[xA][yA] = newTile;
        tilesB[xB][yB] = newTile;
    }

    /**
     * A simplified version that tries to figure out which biome
     * a tile belongs to by checking the chunk’s primary distribution or re–sampling.
     */
    private static Biome guessBiomeForTile(Chunk chunk, int tileId, int lx, int ly, BiomeManager manager) {
        // if the chunk’s main biome knows about the tileId, assume it’s that
        if (chunk.getBiome().getAllowedTileTypes().contains(tileId)) {
            return chunk.getBiome();
        }
        // or if chunk’s biome has a transitional set that includes tileId
        if (chunk.getBiome().getTransitionTileDistribution().containsKey(tileId)) {
            return chunk.getBiome();
        }

        // fallback: re–sample the biome manager at the tile’s real pixel coords
        float worldX = (chunk.getChunkX() * CHUNK_SIZE + lx) * World.TILE_SIZE;
        float worldY = (chunk.getChunkY() * CHUNK_SIZE + ly) * World.TILE_SIZE;
        BiomeTransitionResult btr = manager.getBiomeAt(worldX, worldY);
        if (btr == null) return null;

        Biome p = btr.getPrimaryBiome();
        Biome s = btr.getSecondaryBiome();
        if (p != null && p.getAllowedTileTypes().contains(tileId)) {
            return p;
        }
        if (s != null && s.getAllowedTileTypes().contains(tileId)) {
            return s;
        }
        return p != null ? p : s;
    }

    /**
     * Merges the distributions from two biomes at a fixed 50/50 blend and picks a tile ID.
     * This re–uses the logic from your transitional tile picking.
     */
    private static int pickTileFromBlendedBiomes(
        Biome primary, Biome secondary, float blend,
        float worldX, float worldY,
        long worldSeed
    ) {
        Map<Integer, Integer> pDist = primary.getTileDistribution();
        Map<Integer, Integer> sDist = secondary.getTileDistribution();
        Map<Integer, Integer> pTrans = primary.getTransitionTileDistribution();
        Map<Integer, Integer> sTrans = secondary.getTransitionTileDistribution();

        float transitionBoost = 1.0f;
        Set<Integer> allKeys = new HashSet<>(pDist.keySet());
        allKeys.addAll(sDist.keySet());
        if (pTrans != null) allKeys.addAll(pTrans.keySet());
        if (sTrans != null) allKeys.addAll(sTrans.keySet());

        Map<Integer, Double> finalDist = new HashMap<>();
        double totalWeight = 0.0;
        for (Integer tileId : allKeys) {
            double wp = pDist.getOrDefault(tileId, 0);
            double ws = sDist.getOrDefault(tileId, 0);
            double w = blend * wp + (1 - blend) * ws;
            if (pTrans != null && pTrans.containsKey(tileId)) {
                w += pTrans.get(tileId) * transitionBoost * blend;
            }
            if (sTrans != null && sTrans.containsKey(tileId)) {
                w += sTrans.get(tileId) * transitionBoost * (1 - blend);
            }
            if (w > 0) {
                finalDist.put(tileId, w);
                totalWeight += w;
            }
        }

        double noiseValue = NoiseCache.getNoise(worldSeed + 1000, worldX, worldY, 0.5f);
        double roll = noiseValue * totalWeight;
        double cumulative = 0.0;
        for (Map.Entry<Integer, Double> e : finalDist.entrySet()) {
            cumulative += e.getValue();
            if (roll <= cumulative) {
                return e.getKey();
            }
        }

        // fallback
        List<Integer> fallback = primary.getAllowedTileTypes();
        if (fallback != null && !fallback.isEmpty()) {
            return fallback.get(0);
        }
        return TileType.GRASS;
    }
}
