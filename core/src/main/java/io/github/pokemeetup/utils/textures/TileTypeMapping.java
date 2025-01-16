package io.github.pokemeetup.utils.textures;

import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType.PLAINS;

public class TileTypeMapping {


    public enum SpriteCategory {
        GROUND,      // Basic terrain
        DECORATIVE,  // Surface decorations
        WATER,       // Water, ice, lava
        PATH,        // Walkable paths

        STRUCTURE,    // Walls, rocks, built elements
        OBJECT    // Walls, rocks, built elements
    }
    private final Map<String, SpriteInfo> spriteInfoMap = new HashMap<>();

    public static class SpriteInfo {
        public final SpriteCategory category;
        public final String baseKey;
        public final int variance; // Number of variations
        public final boolean isAnimated;

        public SpriteInfo(SpriteCategory category, String baseKey, int variance, boolean isAnimated) {
            this.category = category;
            this.baseKey = baseKey;
            this.variance = variance;
            this.isAnimated = isAnimated;
        }
    }

    public void registerSprite(String tileKey, SpriteCategory category, int variance, boolean isAnimated) {
        String baseKey = tileKey.split("_\\d+")[0]; // Strip variation number
        spriteInfoMap.put(tileKey, new SpriteInfo(category, baseKey, variance, isAnimated));
    }

    public SpriteInfo getSpriteInfo(String tileKey) {
        return spriteInfoMap.get(tileKey);
    }

    public List<String> getTilesByCategory(SpriteCategory category) {
        return spriteInfoMap.entrySet().stream()
            .filter(e -> e.getValue().category == category)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    // Initialize standard mappings for each biome
    public void initializeBiomeMappings(BiomeType biomeType) {
        String prefix = biomeType.name().toLowerCase();
        switch(biomeType) {
            case PLAINS:
                registerSprite(prefix + "_grass", SpriteCategory.GROUND, 3, false);
                registerSprite(prefix + "_flowers", SpriteCategory.DECORATIVE, 2, false);
                registerSprite(prefix + "_ground", SpriteCategory.GROUND, 2, false);
                registerSprite(prefix + "_water", SpriteCategory.WATER, 1, true);
                break;
            case DESERT:
                registerSprite(prefix + "_ground", SpriteCategory.GROUND, 2, false);
                registerSprite(prefix + "_vegetation", SpriteCategory.DECORATIVE, 2, false);
                break;
        }
    }
}
