package org.discord.utils;

import com.badlogic.gdx.math.Vector2;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.pokemeetup.managers.BiomeManager;
import io.github.pokemeetup.managers.BiomeTransitionResult;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.utils.GameLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerBiomeManager {
    private final Map<String, Map<Vector2, BiomeData>> worldBiomeCache;
    private final BiomeManager biomeManager;
    private final Path baseBiomePath;
    private final Gson gson;
    private static final String BIOME_DATA_DIR = "server/data/biomes";

    public ServerBiomeManager(BiomeManager biomeManager) {
        this.biomeManager = biomeManager;
        this.worldBiomeCache = new ConcurrentHashMap<>();
        this.baseBiomePath = Paths.get(BIOME_DATA_DIR);
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

        initializeDirectory();
    }

    private void initializeDirectory() {
        try {
            Files.createDirectories(baseBiomePath);
        } catch (IOException e) {
            GameLogger.error("Failed to create biome data directory: " + e.getMessage());
        }
    }

    public BiomeManager getBiomeManager() {
        return biomeManager;
    }

    public BiomeData getBiomeData(String worldName, Vector2 chunkPos) {
        Map<Vector2, BiomeData> worldBiomes = worldBiomeCache.computeIfAbsent(
            worldName, k -> new ConcurrentHashMap<>()
        );

        BiomeData data = worldBiomes.get(chunkPos);
        if (data != null) {
            return data;
        }

        // Try to load from disk
        data = loadBiomeData(worldName, chunkPos);
        if (data == null) {
            data = generateBiomeData(worldName, chunkPos);
        }

        // Cache and return
        worldBiomes.put(chunkPos, data);
        return data;
    }

    private BiomeData loadBiomeData(String worldName, Vector2 chunkPos) {
        Path biomePath = getBiomeFilePath(worldName, chunkPos);
        try {
            if (Files.exists(biomePath)) {
                String jsonContent = Files.readString(biomePath);
                return gson.fromJson(jsonContent, BiomeData.class);
            }
        } catch (IOException e) {
            GameLogger.error("Failed to load biome data for " + worldName +
                " at " + chunkPos + ": " + e.getMessage());
        }
        return null;
    }

    private BiomeData generateBiomeData(String worldName, Vector2 chunkPos) {
        float worldX = (chunkPos.x * Chunk.CHUNK_SIZE + Chunk.CHUNK_SIZE / 2f) * World.TILE_SIZE;
        float worldY = (chunkPos.y * Chunk.CHUNK_SIZE + Chunk.CHUNK_SIZE / 2f) * World.TILE_SIZE;
BiomeTransitionResult transition = biomeManager.getBiomeAt(worldX, worldY);

        BiomeData data = new BiomeData();
        data.setPrimaryBiomeType(transition.getPrimaryBiome().getType());
        data.setTransitionFactor(transition.getTransitionFactor());

        Biome secondaryBiome = transition.getSecondaryBiome();
        if (secondaryBiome != null) {
            data.setSecondaryBiomeType(secondaryBiome.getType());
        }

        saveBiomeData(worldName, chunkPos, data);
        return data;
    }

    public void saveBiomeData(String worldName, Vector2 chunkPos, BiomeData data) {
        try {
            Path biomePath = getBiomeFilePath(worldName, chunkPos);
            Files.createDirectories(biomePath.getParent());

            String jsonContent = gson.toJson(data);
            Files.writeString(biomePath, jsonContent);

            worldBiomeCache
                .computeIfAbsent(worldName, k -> new ConcurrentHashMap<>())
                .put(chunkPos, data);

            GameLogger.info("Saved biome data for " + worldName + " at " + chunkPos);
        } catch (IOException e) {
            GameLogger.error("Failed to save biome data: " + e.getMessage());
        }
    }

    private Path getBiomeFilePath(String worldName, Vector2 chunkPos) {
        return baseBiomePath.resolve(
            String.format("%s/biome_%d_%d.json",
                worldName, (int)chunkPos.x, (int)chunkPos.y)
        );
    }

    public void clearCache(String worldName) {
        worldBiomeCache.remove(worldName);
    }

    public void clearCache() {
        worldBiomeCache.clear();
    }
}

