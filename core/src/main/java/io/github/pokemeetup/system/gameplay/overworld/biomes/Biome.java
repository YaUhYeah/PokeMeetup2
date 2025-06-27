package io.github.pokemeetup.system.gameplay.overworld.biomes;

import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TileType;

import java.util.*;

public class Biome {
    private final BiomeType type;
    private HashMap<Integer, Integer> tileDistribution;
    private String name;
    private List<Integer> allowedTileTypes;
    private List<WorldObject.ObjectType> spawnableObjects;
    private Map<WorldObject.ObjectType, Double> spawnChances;

    public Biome(String name, BiomeType type) {
        this.name = name;
        this.type = type;
        this.allowedTileTypes = new ArrayList<>();
        this.spawnableObjects = new ArrayList<>();
        this.spawnChances = new HashMap<>();
        this.tileDistribution = new HashMap<>();
    }

    public List<WorldObject.ObjectType> getSpawnableObjects() {
        return spawnableObjects != null ? spawnableObjects : new ArrayList<>();
    }


    public Map<WorldObject.ObjectType, Double> getSpawnChances() {
        return spawnChances;
    }

    public void loadSpawnableObjects(List<String> objectStrings) {
        this.spawnableObjects = new ArrayList<>();
        for (String objStr : objectStrings) {
            try {
                WorldObject.ObjectType type = WorldObject.ObjectType.valueOf(objStr);
                this.spawnableObjects.add(type);
            } catch (IllegalArgumentException e) {
                GameLogger.error(name + ": Invalid object type: " + objStr);
            }
        }
    }
    public void loadSpawnChances(Map<String, Double> chanceMap) {
        this.spawnChances = new HashMap<>();
        for (Map.Entry<String, Double> entry : chanceMap.entrySet()) {
            try {
                WorldObject.ObjectType type = WorldObject.ObjectType.valueOf(entry.getKey());
                this.spawnChances.put(type, entry.getValue());
            } catch (IllegalArgumentException e) {
                GameLogger.error(name + ": Invalid object type in spawn chances: " + entry.getKey());
            }
        }
    }

    public double getSpawnChanceForObject(WorldObject.ObjectType objectType) {
        return spawnChances != null ? spawnChances.getOrDefault(objectType, 0.0) : 0.0;
    }

    public void setTileDistribution(Map<Integer, Integer> distribution) {
        if (distribution == null || distribution.isEmpty()) {
            throw new IllegalArgumentException("Tile distribution cannot be null or empty");
        }
        if (allowedTileTypes.isEmpty()) {
            allowedTileTypes = new ArrayList<>(distribution.keySet());
        }
        for (Integer tileType : distribution.keySet()) {
            if (!allowedTileTypes.contains(tileType)) {
                allowedTileTypes.add(tileType);
            }
        }



        double totalWeight = distribution.values().stream()
            .mapToDouble(Integer::doubleValue)
            .sum();
        if (Math.abs(totalWeight - 100.0) > 0.001) {
            Map<Integer, Integer> normalizedDist = new HashMap<>();

            for (Map.Entry<Integer, Integer> entry : distribution.entrySet()) {
                double normalizedValue = (entry.getValue() / totalWeight) * 100.0;
                normalizedDist.put(entry.getKey(), (int) Math.round(normalizedValue));
            }
            int finalTotal = normalizedDist.values().stream()
                .mapToInt(Integer::intValue)
                .sum();

            if (finalTotal != 100) {
                int diff = 100 - finalTotal;
                int highestKey = normalizedDist.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(distribution.keySet().iterator().next());

                normalizedDist.put(highestKey, normalizedDist.get(highestKey) + diff);
            }

            distribution = normalizedDist;
        }

        this.tileDistribution = new HashMap<>(distribution);

    }public void setBeachTileDistribution(Map<Integer, Integer> beachTileDistribution) {
        this.beachTileDistribution = beachTileDistribution;
    }

    private Map<Integer, Integer> beachTileDistribution;
    public Map<Integer, Integer> getBeachTileDistribution() {
        if (beachTileDistribution == null || beachTileDistribution.isEmpty()) {
            Map<Integer, Integer> defaultBeach = new HashMap<>();
            defaultBeach.put(TileType.BEACH_SAND, 60);
            defaultBeach.put(TileType.BEACH_GRASS, 20);
            defaultBeach.put(TileType.BEACH_GRASS_2, 20);
            defaultBeach.put(TileType.BEACH_SHELL, 5);
            defaultBeach.put(TileType.BEACH_STARFISH, 5);
            return defaultBeach;
        }
        return beachTileDistribution;
    }
    public BiomeType getType() {
        return type;
    }
    private Map<Integer, Integer> transitionTileDistribution = new HashMap<>();

    public Map<Integer, Integer> getTransitionTileDistribution() {
        return transitionTileDistribution;
    }

    public void setTransitionTileDistribution(Map<Integer, Integer> map) {
        this.transitionTileDistribution = map;
    }


    public HashMap<Integer, Integer> getTileDistribution() {
        return tileDistribution;
    }
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    public List<Integer> getAllowedTileTypes() {
        return allowedTileTypes;
    }

    public void setAllowedTileTypes(List<Integer> allowedTileTypes) {
        this.allowedTileTypes = allowedTileTypes;
    }
    private void useFallbackDistribution() {
        Map<Integer, Integer> fallback = new HashMap<>();
        fallback.put(1, 70);  // grass
        fallback.put(2, 20);  // dirt
        fallback.put(3, 10);  // stone

        this.allowedTileTypes = new ArrayList<>(fallback.keySet());
        this.tileDistribution = new HashMap<>(fallback);

        GameLogger.info(String.format("Biome %s using fallback distribution: %s",
            name, fallback));
    }
    public void validate() {
        if (allowedTileTypes == null) {
            allowedTileTypes = new ArrayList<>();
        }

        if (tileDistribution == null || tileDistribution.isEmpty()) {
            useFallbackDistribution();
        }

        if (spawnableObjects == null) {
            spawnableObjects = new ArrayList<>();
        }
    }
}
