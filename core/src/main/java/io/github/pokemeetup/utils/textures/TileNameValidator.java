package io.github.pokemeetup.utils.textures;

import io.github.pokemeetup.utils.GameLogger;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
public class TileNameValidator {
    private static final Pattern TILE_NAME_PATTERN = Pattern.compile("^[a-z]+_[a-z_]+_\\d+_\\d+$");

    public  void validateBiomeTileNames(Collection<String> tileNames, String biomeName) {
        List<String> invalidTiles = tileNames.stream()
            .filter(name -> !isValidTileName(name))
            .collect(Collectors.toList());

        if (!invalidTiles.isEmpty()) {
            GameLogger.error("Invalid tile names found in " + biomeName + " biome:");
            invalidTiles.forEach(name -> GameLogger.error(" - " + name));

        }
    }

    public static boolean isValidTileName(String tileName) {
        if (tileName == null || tileName.isEmpty()) {
            return false;
        }

        // Special case for simplified formats (e.g., snow_ground_0)
        if (tileName.matches("\\w+_\\w+_\\d+$")) {
            return true;
        }

        // Standard format (e.g., biome_type_x_y)
        String[] parts = tileName.split("_");

        // Must have at least 3 parts: biome_type_number
        if (parts.length < 3) {
            return false;
        }

        // The last two parts should be numbers
        try {
            // Handle both x_y and single number formats
            if (parts.length >= 4) {
                Integer.parseInt(parts[parts.length - 2]);
                Integer.parseInt(parts[parts.length - 1]);
            } else {
                Integer.parseInt(parts[parts.length - 1]);
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    public static String normalizeTileName(String tileName) {
        // Handle the simplified format (e.g., snow_ground_0)
        if (tileName.matches("\\w+_\\w+_\\d+$")) {
            String[] parts = tileName.split("_");
            // Convert to standard format biome_type_x_y
            return String.format("%s_%s_%s_0", parts[0], parts[1], parts[2]);
        }
        return tileName;
    }
}

