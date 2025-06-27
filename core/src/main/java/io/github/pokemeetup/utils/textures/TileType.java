package io.github.pokemeetup.utils.textures;

import java.util.HashMap;
import java.util.Map;

public class TileType {
    public static final int WATER = 0;
    public static final int GRASS = 1;
    public static final int SAND = 2;
    public static final int ROCK = 3;
    public static final int SNOW = 4;
    public static final int HAUNTED_GRASS = 5;
    public static final int SNOW_TALL_GRASS = 6;
    public static final int HAUNTED_TALL_GRASS = 7;
    public static final int HAUNTED_SHROOM = 8;
    public static final int HAUNTED_SHROOMS = 9;
    public static final int TALL_GRASS = 10;
    public static final int FOREST_GRASS = 11;
    public static final int FOREST_TALL_GRASS = 12;
    public static final int RAIN_FOREST_GRASS = 13;
    public static final int RAIN_FOREST_TALL_GRASS = 14;
    public static final int DESERT_SAND = 15;
    public static final int DESERT_ROCKS = 16;
    public static final int DESERT_GRASS = 17;
    public static final int FLOWER_1 = 18;
    public static final int FLOWER_2 = 19;
    public static final int FLOWER = 20;
    public static final int TALL_GRASS_2 = 21;
    public static final int GRASS_2 = 22;
    public static final int GRASS_3 = 23;
    public static final int TALL_GRASS_3 = 24;

    public static final int MOUNTAIN_CORNER_INNER_TOPLEFT = 27;
    public static final int MOUNTAIN_CORNER_INNER_BOTTOMRIGHT = 30;
    public static final int MOUNTAIN_STAIRS_LEFT = 31; // Left side stairs
    public static final int MOUNTAIN_STAIRS_RIGHT = 32; // Right side stairs
    public static final int STAIRS = 235;
    public static final int MOUNTAIN_SNOW_BASE = 36;
    public static final int MOUNTAIN_BASE = 25;       // Base mountain tile
    public static final int MOUNTAIN_PEAK = 26;       // Mountain peak
    public static final int MOUNTAIN_SLOPE_LEFT = 27; // Left slope
    public static final int MOUNTAIN_SLOPE_RIGHT = 28; // Right slope
    public static final int MOUNTAIN_WALL = 29;       // Mountain wall
    public static final int MOUNTAIN_STAIRS = 30;     // Standard stairs
    public static final int MOUNTAIN_CORNER_TL = 33;  // Top-left corner
    public static final int MOUNTAIN_CORNER_TR = 34;  // Top-right corner
    public static final int MOUNTAIN_CORNER_BL = 35;  // Bottom-left corner
    public static final int MOUNTAIN_CORNER_BR = 36;  // Bottom-right corner
    public static final int MOUNTAIN_CORNER_OUTER_TOPLEFT = 39;
    public static final int MOUNTAIN_CORNER_OUTER_BOTTOMRIGHT = 42;
    public static final int MOUNTAIN_BASE_EDGE = 38;      // Basic mountain edge tile
    public static final int MOUNTAIN_PATH = 40;           // Walkable mountain path
    public static final int MOUNTAIN_EDGE_LEFT = 47;      // Left edge
    public static final int MOUNTAIN_EDGE_RIGHT = 48;     // Right edge
    public static final int MOUNTAIN_EDGE_TOP = 49;       // Top edge
    public static final int MOUNTAIN_EDGE_BOTTOM = 50;    // Bottom edge
    public static final int SNOW_2 = 51;
    public static final int SNOW_3 = 52;
    public static final int RUINS_GRASS = 53;
    public static final int RUINS_GRASS_0 = 54;
    public static final int RUINS_TALL_GRASS = 55;
    public static final int RUINS_BRICKS = 56;
    public static final int WATER_PUDDLE = 140;
    public static final int WATER_PUDDLE_TOP_LEFT = 141;
    public static final int WATER_PUDDLE_TOP_MIDDLE = 142;
    public static final int WATER_PUDDLE_TOP_RIGHT = 143;
    public static final int WATER_PUDDLE_LEFT_MIDDLE = 144;
    public static final int WATER_PUDDLE_RIGHT_MIDDLE = 145;
    public static final int WATER_PUDDLE_BOTTOM_LEFT = 146;
    public static final int WATER_PUDDLE_BOTTOM_MIDDLE = 147;
    public static final int WATER_PUDDLE_BOTTOM_RIGHT = 148;
    public static final int MOUNTAIN_TILE_TOP_LEFT_GRASS_BG = 209;
    public static final int MOUNTAIN_TILE_TOP_RIGHT_GRASS_BG = 210;
    public static final int MOUNTAIN_TILE_BOT_RIGHT_GRASS_BG = 211;
    public static final int MOUNTAIN_TILE_BOT_LEFT_GRASS_BG = 212;
    public static final int MOUNTAIN_TILE_TOP_LEFT_ROCK_BG = 200;
    public static final int MOUNTAIN_TILE_TOP_MID = 201;
    public static final int MOUNTAIN_TILE_TOP_RIGHT_ROCK_BG = 202;
    public static final int MOUNTAIN_TILE_MID_LEFT = 203;
    public static final int MOUNTAIN_TILE_CENTER = 204;
    public static final int MOUNTAIN_TILE_MID_RIGHT = 205;
    public static final int MOUNTAIN_TILE_BOT_LEFT_ROCK_BG = 206;
    public static final int MOUNTAIN_TILE_BOT_MID = 207;
    public static final int MOUNTAIN_TILE_BOT_RIGHT_ROCK_BG = 208;
    public static final int MOUNTAIN_TILE_CONNECTING_CORNER_BOTTOM_LEFT = 213;
    public static final int MOUNTAIN_TILE_CONNECTING_CORNER_BOTTOM_RIGHT = 214;
    public static final int MOUNTAIN_TILE_CONNECTING_CORNER_TOP_LEFT = 215;
    public static final int MOUNTAIN_TILE_CONNECTING_CORNER_TOP_RIGHT = 216;
    public static final int FAIRY_ROCK = 217;
    public static final int CRYSTAL_ROCK = 218;
    public static final int SNOWY_GRASS = 219;
    public static final int BEACH_SAND = 220;
    public static final int BEACH_GRASS = 221;
    public static final int BEACH_GRASS_2 = 222;
    public static final int BEACH_STARFISH = 223;
    public static final int BEACH_SHELL = 224;
    public static final int CAVE_ENTRANCE = 149;

    public static final int TALL_GRASS_OVERLAY = 225;
    public static final int TALL_GRASS_OVERLAY_2 = 226;
    public static final int TALL_GRASS_OVERLAY_3 = 227;
    public static final int SNOW_TALL_GRASS_OVERLAY = 228;
    public static final int HAUNTED_TALL_GRASS_OVERLAY = 229;
    public static final int RAINFOREST_TALL_GRASS_OVERLAY = 230;
    public static final int RUINS_TALL_GRASS_OVERLAY = 231;
    public static final int FOREST_TALL_GRASS_OVERLAY = 232;
    public static final int DESERT_TALL_GRASS_OVERLAY = 233;
    public static final int BEACH_TALL_GRASS_OVERLAY = 234;
    private static final Map<Integer, String> tileTypeNames = new HashMap<>();
    private static final Map<Integer, String> mountainTileNames = new HashMap<>();

    static {
        tileTypeNames.put(WATER, "water");
        tileTypeNames.put(GRASS, "grass");
        tileTypeNames.put(SAND, "sand");
        tileTypeNames.put(ROCK, "rock");
        tileTypeNames.put(SNOW, "snow_base");
        tileTypeNames.put(WATER_PUDDLE, "water_puddle");
        tileTypeNames.put(WATER_PUDDLE_TOP_LEFT, "water_puddle_top_left_corner");
        tileTypeNames.put(WATER_PUDDLE_TOP_MIDDLE, "water_puddle_top_middle");
        tileTypeNames.put(WATER_PUDDLE_TOP_RIGHT, "water_puddle_top_right_corner");
        tileTypeNames.put(WATER_PUDDLE_LEFT_MIDDLE, "water_puddle_left_middle");
        tileTypeNames.put(WATER_PUDDLE_RIGHT_MIDDLE, "water_puddle_right_middle");
        tileTypeNames.put(WATER_PUDDLE_BOTTOM_LEFT, "water_puddle_bottom_left_corner");
        tileTypeNames.put(WATER_PUDDLE_BOTTOM_MIDDLE, "water_puddle_bottom_middle");
        tileTypeNames.put(WATER_PUDDLE_BOTTOM_RIGHT, "water_puddle_bottom_right_corner");
        tileTypeNames.put(HAUNTED_GRASS, "haunted_grass");
        tileTypeNames.put(HAUNTED_SHROOM, "haunted_shroom");
        tileTypeNames.put(HAUNTED_SHROOMS, "haunted_shrooms");
        tileTypeNames.put(FOREST_GRASS, "forest_grass");
        tileTypeNames.put(RAIN_FOREST_GRASS, "rainforest_grass");
        tileTypeNames.put(STAIRS, "tile052");
        tileTypeNames.put(DESERT_SAND, "desert_sand");
        tileTypeNames.put(FLOWER, "flower");
        tileTypeNames.put(FLOWER_1, "flower");
        tileTypeNames.put(FLOWER_2, "flower");
        tileTypeNames.put(SNOW_2, "snow_grass");
        tileTypeNames.put(SNOW_3, "snow_base");
        tileTypeNames.put(GRASS_2, "grass");
        tileTypeNames.put(GRASS_3, "grass");
        tileTypeNames.put(MOUNTAIN_BASE_EDGE, "tile038");
        tileTypeNames.put(TileType.MOUNTAIN_SLOPE_RIGHT, "tile046");
        tileTypeNames.put(MOUNTAIN_WALL, "mountainBASEMIDDLE");
        tileTypeNames.put(MOUNTAIN_PEAK, "mountaintopRIGHT");
        tileTypeNames.put(MOUNTAIN_PATH, "tile081");
        tileTypeNames.put(MOUNTAIN_STAIRS, "mountainstairsMiddle");
        tileTypeNames.put(MOUNTAIN_BASE, "mountainBASEMIDDLE");
        tileTypeNames.put(MOUNTAIN_STAIRS_LEFT, "mountainstairsLEFT");  // Left stairs
        tileTypeNames.put(MOUNTAIN_STAIRS_RIGHT, "mountainstarsRIGHT"); // Right stairs
        tileTypeNames.put(MOUNTAIN_CORNER_TL, "mountainTOPLEFT"); // Top-left corner
        tileTypeNames.put(MOUNTAIN_CORNER_TR, "mountaintopRIGHT"); // Top-right corner
        tileTypeNames.put(MOUNTAIN_CORNER_BL, "mountainBASELEFT"); // Bottom-left corner
        tileTypeNames.put(MOUNTAIN_CORNER_OUTER_BOTTOMRIGHT, "mountainbaseRIGHT"); // Bottom-right corner
        tileTypeNames.put(MOUNTAIN_CORNER_BR, "mountainbaseRIGHT"); // Bottom-right corner
        tileTypeNames.put(MOUNTAIN_EDGE_LEFT, "mountainTOPLEFT");
        tileTypeNames.put(MOUNTAIN_EDGE_RIGHT, "mountaintopRIGHT");
        tileTypeNames.put(MOUNTAIN_EDGE_TOP, "tile050");
        tileTypeNames.put(MOUNTAIN_CORNER_INNER_TOPLEFT, "tile029");
        tileTypeNames.put(MOUNTAIN_EDGE_BOTTOM, "tile051");
        tileTypeNames.put(RUINS_GRASS, "ruins_grass");
        tileTypeNames.put(RUINS_GRASS_0, "ruins_grass");
        tileTypeNames.put(RUINS_BRICKS, "ruin_bricks");
        tileTypeNames.put(CAVE_ENTRANCE, "cave_entrance");
        tileTypeNames.put(MOUNTAIN_TILE_TOP_LEFT_ROCK_BG, "MOUNTAIN_TILE_TOP_LEFT_ROCK_BG");
        tileTypeNames.put(MOUNTAIN_TILE_TOP_MID, "MOUNTAIN_TILE_TOP_MID");
        tileTypeNames.put(MOUNTAIN_TILE_TOP_RIGHT_ROCK_BG, "MOUNTAIN_TILE_TOP_RIGHT_ROCK_BG");
        tileTypeNames.put(MOUNTAIN_TILE_MID_LEFT, "MOUNTAIN_TILE_MID_LEFT");
        tileTypeNames.put(MOUNTAIN_TILE_CENTER, "MOUNTAIN_TILE_CENTER");
        tileTypeNames.put(MOUNTAIN_TILE_MID_RIGHT, "MOUNTAIN_TILE_MID_RIGHT");
        tileTypeNames.put(MOUNTAIN_TILE_BOT_LEFT_ROCK_BG, "MOUNTAIN_TILE_BOT_LEFT_ROCK_BG");
        tileTypeNames.put(MOUNTAIN_TILE_BOT_MID, "MOUNTAIN_TILE_BOT_MID");
        tileTypeNames.put(MOUNTAIN_TILE_BOT_RIGHT_ROCK_BG, "MOUNTAIN_TILE_BOT_RIGHT_ROCK_BG");
        tileTypeNames.put(MOUNTAIN_TILE_TOP_LEFT_GRASS_BG, "MOUNTAIN_TILE_TOP_LEFT_GRASS_BG");
        tileTypeNames.put(MOUNTAIN_TILE_TOP_RIGHT_GRASS_BG, "MOUNTAIN_TILE_TOP_RIGHT_GRASS_BG");
        tileTypeNames.put(MOUNTAIN_TILE_BOT_LEFT_GRASS_BG, "MOUNTAIN_TILE_BOT_LEFT_GRASS_BG");
        tileTypeNames.put(MOUNTAIN_TILE_CONNECTING_CORNER_TOP_LEFT, "MOUNTAIN_TILE_CONNECTING_CORNER_TOP_LEFT");
        tileTypeNames.put(MOUNTAIN_TILE_CONNECTING_CORNER_TOP_RIGHT, "MOUNTAIN_TILE_CONNECTING_CORNER_TOP_RIGHT");
        tileTypeNames.put(MOUNTAIN_TILE_CONNECTING_CORNER_BOTTOM_LEFT, "MOUNTAIN_TILE_CONNECTING_CORNER_BOTTOM_LEFT");
        tileTypeNames.put(MOUNTAIN_TILE_CONNECTING_CORNER_BOTTOM_RIGHT, "MOUNTAIN_TILE_CONNECTING_CORNER_BOTTOM_RIGHT");
        tileTypeNames.put(SNOWY_GRASS, "snowy_grass");
        tileTypeNames.put(BEACH_SAND, "beach_sand");
        tileTypeNames.put(BEACH_GRASS, "beach_tall_grass");
        tileTypeNames.put(BEACH_GRASS_2, "beach_grass");
        tileTypeNames.put(BEACH_STARFISH, "beach_starfish");
        tileTypeNames.put(BEACH_SHELL, "beach_shell");
        tileTypeNames.put(TALL_GRASS_OVERLAY, "tall_grass_overlay");
        tileTypeNames.put(TALL_GRASS_OVERLAY_2, "tall_grass_2_overlay");
        tileTypeNames.put(TALL_GRASS_OVERLAY_3, "tall_grass_3_overlay");
        tileTypeNames.put(SNOW_TALL_GRASS_OVERLAY, "snow_tall_grass_overlay");
        tileTypeNames.put(HAUNTED_TALL_GRASS_OVERLAY, "haunted_tall_grass_overlay");
        tileTypeNames.put(RAINFOREST_TALL_GRASS_OVERLAY, "rain_forest_tall_grass_overlay");
        tileTypeNames.put(RUINS_TALL_GRASS_OVERLAY, "ruins_tall_grass_overlay");
        tileTypeNames.put(FOREST_TALL_GRASS_OVERLAY, "forest_tall_grass_overlay");
        tileTypeNames.put(DESERT_TALL_GRASS_OVERLAY, "desert_grass_overlay");
        tileTypeNames.put(BEACH_TALL_GRASS_OVERLAY, "beach_tall_grass_overlay");
        tileTypeNames.putAll(mountainTileNames);
    }

    public static boolean isWaterPuddle(int tileType) {
        return tileType >= WATER_PUDDLE && tileType <= WATER_PUDDLE_BOTTOM_RIGHT;
    }

    public static Map<Integer, String> getTileTypeNames() {
        return tileTypeNames;
    }

    public static Map<Integer, String> getMountainTileNames() {
        return mountainTileNames;
    }

    public static boolean isMountainTile(int tileType) {
        return tileType >= MOUNTAIN_BASE && tileType <= MOUNTAIN_CORNER_OUTER_BOTTOMRIGHT;
    }

    public static boolean isPassableMountainTile(int tileType) {
        return tileType == MOUNTAIN_STAIRS_LEFT ||
            tileType == MOUNTAIN_STAIRS_RIGHT ||
            tileType == STAIRS ||
            tileType == MOUNTAIN_SNOW_BASE;
    }

    public static boolean isPassableTile(int tileType) {
        if (tileType == GRASS || tileType == SAND || tileType == SNOW_TALL_GRASS || tileType == SNOW || tileType == SNOW_2 || tileType == SNOW_3 || tileType == GRASS_3 || tileType == FOREST_TALL_GRASS || tileType == HAUNTED_SHROOM || tileType == HAUNTED_SHROOMS || tileType == MOUNTAIN_STAIRS ||
            tileType == HAUNTED_GRASS || tileType == HAUNTED_TALL_GRASS || tileType == FOREST_GRASS || tileType == RAIN_FOREST_TALL_GRASS ||
            tileType == RAIN_FOREST_GRASS || tileType == DESERT_SAND || tileType == DESERT_GRASS || tileType == FLOWER_2 || tileType == GRASS_2 || tileType == TALL_GRASS || tileType == TALL_GRASS_2 || tileType == TALL_GRASS_3 || tileType == FLOWER_1 || tileType == FLOWER
            || tileType == BEACH_GRASS || tileType == RUINS_BRICKS || tileType == RUINS_TALL_GRASS || tileType == RUINS_GRASS_0 || tileType == RUINS_GRASS || tileType == BEACH_GRASS_2 || tileType == BEACH_SHELL || tileType == BEACH_STARFISH || tileType == SNOWY_GRASS || tileType == BEACH_SAND) {
            return true;
        }
        if (isWaterPuddle(tileType)) {
            return true;
        }
        return tileType == MOUNTAIN_STAIRS_LEFT || tileType == MOUNTAIN_STAIRS_RIGHT || tileType == MOUNTAIN_TILE_CENTER ||
            tileType == STAIRS;
    }

    public static boolean isMountainCorner(int tileType) {
        return (tileType >= MOUNTAIN_CORNER_INNER_TOPLEFT && tileType <= MOUNTAIN_CORNER_INNER_BOTTOMRIGHT) ||
            (tileType >= MOUNTAIN_CORNER_OUTER_TOPLEFT && tileType <= MOUNTAIN_CORNER_OUTER_BOTTOMRIGHT);
    }

}
