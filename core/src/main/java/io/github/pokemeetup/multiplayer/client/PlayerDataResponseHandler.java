package io.github.pokemeetup.multiplayer.client;

import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.utils.GameLogger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class PlayerDataResponseHandler {
    private final Map<UUID, CompletableFuture<PlayerData>> pendingRequests;
    private final Map<UUID, CachedPlayerData> dataCache;
    private static final long CACHE_DURATION = 60000; // 1 minute cache
    private static final int MAX_CACHE_SIZE = 100;

    private static class CachedPlayerData {
        final PlayerData data;
        final long timestamp;

        CachedPlayerData(PlayerData data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_DURATION;
        }
    }

    public PlayerDataResponseHandler() {
        this.pendingRequests = new ConcurrentHashMap<>();
        this.dataCache = new ConcurrentHashMap<>();
        startCacheCleanupTask();
    }

    private void startCacheCleanupTask() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PlayerDataCache-Cleanup");
            t.setDaemon(true);
            return t;
        });

        executor.scheduleWithFixedDelay(() -> {
            try {
                cleanupCache();
            } catch (Exception e) {
                GameLogger.error("Error during cache cleanup: " + e.getMessage());
            }
        }, CACHE_DURATION, CACHE_DURATION, TimeUnit.MILLISECONDS);
    }

    private void cleanupCache() {
        dataCache.entrySet().removeIf(entry -> entry.getValue().isExpired());

        // If still too many entries, remove oldest ones
        if (dataCache.size() > MAX_CACHE_SIZE) {
            dataCache.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e1.getValue().timestamp, e2.getValue().timestamp))
                .limit(dataCache.size() - MAX_CACHE_SIZE)
                .forEach(entry -> dataCache.remove(entry.getKey()));
        }
    }

    public CompletableFuture<PlayerData> createRequest(UUID uuid) {
        // Check cache first
        CachedPlayerData cached = dataCache.get(uuid);
        if (cached != null && !cached.isExpired()) {
            return CompletableFuture.completedFuture(cached.data);
        }

        // Create new request
        CompletableFuture<PlayerData> future = new CompletableFuture<>();
        pendingRequests.put(uuid, future);
        return future;
    }

    public void handleGetResponse(NetworkProtocol.GetPlayerDataResponse response) {
        CompletableFuture<PlayerData> future = pendingRequests.remove(response.uuid);
        if (future != null) {
            if (response.success && response.playerData != null) {
                // Cache the data
                dataCache.put(response.uuid, new CachedPlayerData(response.playerData));
                future.complete(response.playerData);
            } else {
                future.completeExceptionally(new RuntimeException(response.message));
            }
        }
    }

    public void handleSaveResponse(NetworkProtocol.SavePlayerDataResponse response) {
        if (response.success) {
            // Clear cache for this UUID to ensure fresh data on next load
            dataCache.remove(response.uuid);
            GameLogger.info("Player data saved successfully for UUID: " + response.uuid);
        } else {
            GameLogger.error("Failed to save player data for UUID: " + response.uuid + " - " + response.message);
        }
    }

    public void clearCache() {
        dataCache.clear();
    }

    public void cancelRequest(UUID uuid) {
        CompletableFuture<PlayerData> future = pendingRequests.remove(uuid);
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }

    public void shutdown() {
        // Cancel all pending requests
        pendingRequests.forEach((uuid, future) -> {
            if (!future.isDone()) {
                future.cancel(true);
            }
        });
        pendingRequests.clear();
        dataCache.clear();
    }
}
