package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.managers.BiomeManager;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.utils.GameLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages the asynchronous loading of the initial world chunks on a background thread
 * to prevent the game from freezing at spawn.
 */
public class WorldLoader {

    private final ExecutorService executorService;
    private final BiomeManager biomeManager;
    private final long worldSeed;

    private List<Future<Chunk>> chunkFutures = new ArrayList<>();
    private final AtomicInteger totalChunksToLoad = new AtomicInteger(0);
    private final AtomicInteger chunksLoaded = new AtomicInteger(0);
    private volatile boolean isLoading = false;

    public WorldLoader(BiomeManager biomeManager, long worldSeed) {
        this.biomeManager = biomeManager;
        this.worldSeed = worldSeed;
        // Use a thread pool that matches the number of available processors for efficiency
        int numThreads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        this.executorService = Executors.newFixedThreadPool(numThreads);
        GameLogger.info("WorldLoader initialized with " + numThreads + " threads.");
    }

    public void startInitialLoad(Player player, int radius) {
        if (isLoading) {
            GameLogger.info("Initial load is already in progress.");
            return;
        }

        GameLogger.info("Starting initial asynchronous world load...");
        isLoading = true;
        chunksLoaded.set(0);
        chunkFutures.clear();

        int playerChunkX = (int) Math.floor(player.getTileX() / (float) Chunk.CHUNK_SIZE);
        int playerChunkY = (int) Math.floor(player.getTileY() / (float) Chunk.CHUNK_SIZE);

        List<Vector2> chunksToLoad = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                chunksToLoad.add(new Vector2(playerChunkX + dx, playerChunkY + dy));
            }
        }
        totalChunksToLoad.set(chunksToLoad.size());

        for (Vector2 pos : chunksToLoad) {
            Callable<Chunk> task = () -> {
                // Generate chunk but DON'T apply autotiling here
                Chunk chunk = UnifiedWorldGenerator.generateChunkForServer(
                    (int) pos.x, (int) pos.y, worldSeed, biomeManager
                );
                chunksLoaded.incrementAndGet();
                return chunk;
            };
            chunkFutures.add(executorService.submit(task));
        }
    }
    /**
     * Checks if the initial world load is complete.
     * @return true if all chunks have been generated, false otherwise.
     */
    public boolean isLoadComplete() {
        if (!isLoading) {
            return false;
        }
        for (Future<Chunk> future : chunkFutures) {
            if (!future.isDone()) {
                return false;
            }
        }
        isLoading = false; // Mark loading as complete
        return true;
    }

    /**
     * Retrieves the list of generated chunks once loading is complete.
     * This method should only be called after isLoadComplete() returns true.
     * @return A list of the newly generated Chunks.
     */
    public List<Chunk> getLoadedChunks() {
        if (isLoading) {
            throw new IllegalStateException("Cannot get chunks while loading is still in progress.");
        }
        List<Chunk> loadedChunks = new ArrayList<>();
        for (Future<Chunk> future : chunkFutures) {
            try {
                loadedChunks.add(future.get());
            } catch (InterruptedException | ExecutionException e) {
                GameLogger.error("Failed to retrieve a generated chunk: " + e.getMessage());
            }
        }
        return loadedChunks;
    }

    /**
     * Gets the current loading progress as a value between 0.0 and 1.0.
     * @return The loading progress.
     */
    public float getProgress() {
        if (totalChunksToLoad.get() == 0) {
            return 0;
        }
        return (float) chunksLoaded.get() / totalChunksToLoad.get();
    }

    /**
     * Shuts down the background thread pool. Should be called when the game closes.
     */
    public void shutdown() {
        executorService.shutdownNow();
    }
}
