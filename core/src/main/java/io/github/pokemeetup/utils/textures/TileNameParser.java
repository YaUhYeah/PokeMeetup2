package io.github.pokemeetup.utils.textures;

import io.github.pokemeetup.utils.GameLogger;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TileNameParser {
    private static final Pattern COMPLEX_TILE_PATTERN =
        Pattern.compile("^([a-z]+)_([a-z]+)(?:_([a-z]+))?_(\\d+)_(\\d+)$");

    public static ParsedTileName parseTileName(String tileName) {
        Matcher matcher = COMPLEX_TILE_PATTERN.matcher(tileName.toLowerCase());
        if (!matcher.matches()) {
            GameLogger.error("Failed to parse tile name: " + tileName);
            return null;
        }

        try {
            String biome = matcher.group(1);
            String baseType = matcher.group(2);
            String modifier = matcher.group(3); // might be null
            int row = Integer.parseInt(matcher.group(4));
            int col = Integer.parseInt(matcher.group(5));

            return new ParsedTileName(biome, baseType, modifier, row, col);
        } catch (NumberFormatException e) {
            GameLogger.error("Failed to parse indices for tile: " + tileName);
            return null;
        }
    }

    public static class ParsedTileName {
        public final String biome;
        public final String baseType;
        public final String modifier;
        public final int row;
        public final int col;

        private ParsedTileName(String biome, String baseType, String modifier, int row, int col) {
            this.biome = biome;
            this.baseType = baseType;
            this.modifier = modifier;
            this.row = row;
            this.col = col;
        }
    }

}
