package io.github.pokemeetup.utils.textures;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.utils.GameLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static io.github.pokemeetup.utils.textures.TileType.*;

public class TextureManager {
    public static final int TYPE_ICON_WIDTH = 64;
    public static final int TYPE_ICON_HEIGHT = 38;
    public static final int STATUS_ICON_WIDTH = 44;
    public static final int STATUS_ICON_HEIGHT = 16;
    public static final Map<Integer, TextureRegion> tileTextures = new HashMap<>();
    private static final Map<Pokemon.Status, TextureRegion> statusIcons = new HashMap<>();
    private static final Map<Pokemon.Status, Color> STATUS_COLORS = new HashMap<>();
    private static final int[][] RMXP_SUBTILE_MAP = {
        {0, 0}, {1, 0}, {2, 0}, {3, 0}, {4, 0}, {5, 0},
        {0, 1}, {1, 1}, {2, 1}, {3, 1}, {4, 1}, {5, 1},
        {0, 2}, {1, 2}, {2, 2}, {3, 2}, {4, 2}, {5, 2},
        {0, 3}, {1, 3}, {2, 3}, {3, 3}, {4, 3}, {5, 3},
        {0, 4}, {1, 4}, {2, 4}, {3, 4}, {4, 4}, {5, 4},
        {0, 5}, {1, 5}, {2, 5}, {3, 5}, {4, 5}, {5, 5},
        {0, 6}, {1, 6}, {2, 6}, {3, 6}, {4, 6}, {5, 6},
        {0, 7}, {1, 7}, {2, 7}, {3, 7}, {4, 7}, {5, 7}
    };
    private static final Map<BiomeType, Map<Integer, TextureRegion>> biomeTileTextures = new HashMap<>();
    private static final Map<Pokemon.PokemonType, TextureRegion> typeIcons = new HashMap<>();
    private static final Map<Pokemon.PokemonType, Color> TYPE_COLORS = new HashMap<>();
    private static final int SUBTILE_SIZE = 16;        // each sub‐tile is 16×16
    private static final int SUBTILE_COLS = 6;         // 96 / 16 = 6
    private static final int SUBTILE_ROWS = 8;         // 128 / 16 = 8
    private static final int MAX_SUBTILES = SUBTILE_COLS * SUBTILE_ROWS;  // 48
    private static final Map<String, TextureRegion[]> autotileFrames = new HashMap<>();
    private static final Map<String, TextureRegion[][]> autotileSubTiles = new HashMap<>();
    private static final int[] XP_AUTOTILE_TABLE = {
        0, 2, 8, 10,
        1, 3, 9, 11,
        24, 26, 16, 18,
        25, 27, 17, 19
    };
    /**
     * Further splits a single 96×128 frame into 6 columns × 8 rows of 16×16 = 48.
     */

    public static TextureAtlas ui;
    public static TextureAtlas pokemonback;
    public static TextureAtlas buildings;
    public static TextureAtlas owFx;
    public static TextureAtlas pokemonfront;
    public static TextureAtlas pokemonicon;
    public static TextureAtlas girl;
    public static TextureAtlas pokemonoverworld;
    public static TextureAtlas items;
    public static TextureAtlas steps;
    public static TextureAtlas boy;
    public static TextureAtlas tiles;
    public static TextureAtlas hairstyles;
    public static TextureAtlas battlebacks;
    public static TextureAtlas mountains;
    public static TextureAtlas effects;
    public static TextureAtlas blocks;
    public static TextureAtlas characters;
    public static TextureAtlas clothing;
    public static TextureAtlas autotiles;
    public static TextureAtlas capsuleThrow;
    private static boolean usingFallbackSystem = false;
    private static Texture whitePixel;

    static {
        STATUS_COLORS.put(Pokemon.Status.NONE, Color.WHITE);
        STATUS_COLORS.put(Pokemon.Status.ASLEEP, Color.GRAY);
        STATUS_COLORS.put(Pokemon.Status.POISONED, new Color(0.627f, 0.439f, 0.627f, 1));
        STATUS_COLORS.put(Pokemon.Status.BURNED, new Color(0.940f, 0.501f, 0.376f, 1));
        STATUS_COLORS.put(Pokemon.Status.FROZEN, new Color(0.564f, 0.815f, 0.940f, 1));
        STATUS_COLORS.put(Pokemon.Status.PARALYZED, new Color(0.972f, 0.815f, 0.376f, 1));
        STATUS_COLORS.put(Pokemon.Status.BADLY_POISONED, new Color(0.5f, 0.1f, 0.5f, 1));
        STATUS_COLORS.put(Pokemon.Status.FAINTED, new Color(0.2f, 0.2f, 0.2f, 1));
    }

    static {
        TYPE_COLORS.put(Pokemon.PokemonType.NORMAL, new Color(0.658f, 0.658f, 0.658f, 1));    // A8A878
        TYPE_COLORS.put(Pokemon.PokemonType.FIGHTING, new Color(0.752f, 0.470f, 0.470f, 1));  // C03028
        TYPE_COLORS.put(Pokemon.PokemonType.FLYING, new Color(0.658f, 0.564f, 0.940f, 1));    // A890F0
        TYPE_COLORS.put(Pokemon.PokemonType.POISON, new Color(0.627f, 0.439f, 0.627f, 1));    // A040A0
        TYPE_COLORS.put(Pokemon.PokemonType.GROUND, new Color(0.878f, 0.752f, 0.470f, 1));    // E0C068
        TYPE_COLORS.put(Pokemon.PokemonType.ROCK, new Color(0.752f, 0.658f, 0.439f, 1));      // B8A038
        TYPE_COLORS.put(Pokemon.PokemonType.BUG, new Color(0.658f, 0.752f, 0.439f, 1));       // A8B820
        TYPE_COLORS.put(Pokemon.PokemonType.GHOST, new Color(0.439f, 0.439f, 0.627f, 1));     // 705898
        TYPE_COLORS.put(Pokemon.PokemonType.STEEL, new Color(0.752f, 0.752f, 0.815f, 1));     // B8B8D0
        TYPE_COLORS.put(Pokemon.PokemonType.FIRE, new Color(0.940f, 0.501f, 0.376f, 1));      // F08030
        TYPE_COLORS.put(Pokemon.PokemonType.WATER, new Color(0.376f, 0.564f, 0.940f, 1));     // 6890F0
        TYPE_COLORS.put(Pokemon.PokemonType.GRASS, new Color(0.470f, 0.815f, 0.376f, 1));     // 78C850
        TYPE_COLORS.put(Pokemon.PokemonType.ELECTRIC, new Color(0.972f, 0.815f, 0.376f, 1));  // F8D030
        TYPE_COLORS.put(Pokemon.PokemonType.PSYCHIC, new Color(0.940f, 0.376f, 0.564f, 1));   // F85888
        TYPE_COLORS.put(Pokemon.PokemonType.ICE, new Color(0.564f, 0.815f, 0.940f, 1));       // 98D8D8
        TYPE_COLORS.put(Pokemon.PokemonType.DRAGON, new Color(0.439f, 0.376f, 0.940f, 1));    // 7038F8
        TYPE_COLORS.put(Pokemon.PokemonType.DARK, new Color(0.439f, 0.376f, 0.376f, 1));      // 705848
        TYPE_COLORS.put(Pokemon.PokemonType.FAIRY, new Color(0.940f, 0.627f, 0.940f, 1));     // F0B6BC
        TYPE_COLORS.put(Pokemon.PokemonType.UNKNOWN, new Color(0.470f, 0.470f, 0.470f, 1));   // 68A090

    }

    /**
     * Loads autotile sheets.
     * For sand shores we now split the frame into 3 columns and 4 rows (32×32 each)
     * rather than slicing out 40 (or 48) 16×16 sub‐tiles.
     */
    public static void loadAutoTiles() {
        if (autotiles == null) {
            GameLogger.error("autotiles atlas not loaded!");
            return;
        }
        TextureRegion seaReg = autotiles.findRegion("Sea");
        if (seaReg != null) {
            TextureRegion[] seaFrames = splitIntoFrames(seaReg, 96, 128);
            TextureRegion[][] seaAllSubs = new TextureRegion[seaFrames.length][MAX_SUBTILES];
            for (int f = 0; f < seaFrames.length; f++) {
                seaAllSubs[f] = splitSubtiles(seaFrames[f]); // Splits into 6 cols × 8 rows (16×16 each)
            }
            autotileSubTiles.put("sea", seaAllSubs);
        }
        TextureRegion sandReg = autotiles.findRegion("SandShore");
        if (sandReg != null) {
            TextureRegion[] frames = splitIntoFrames(sandReg, 96, 128);
            TextureRegion[][] allSubs = new TextureRegion[frames.length][];
            for (int f = 0; f < frames.length; f++) {
                allSubs[f] = splitSubtilesCustom(frames[f], 32, 3, 4);
            }
            autotileSubTiles.put("sand_shore", allSubs);
        }
    }

    /**
     * Returns the proper autotile region given a key, a 4–bit neighbor mask, and an animation frame.
     * For most autotiles we take one 16×16 sub‐tile from our 48–cell RMXP sheet and scale it up.
     * However, for "sand_shore" we use a custom mapping (see below) and do not scale (since our cells are already 32×32).
     */
    public static TextureRegion getAutoTileRegion(String key, int cornerMask, int animFrame) {
        cornerMask &= 0xF;
        TextureRegion[][] frames = autotileSubTiles.get(key);
        if (frames == null || frames.length == 0) return null;
        animFrame = animFrame % frames.length;
        TextureRegion[] subtiles = frames[animFrame];

        if (key.equals("sand_shore")) {
            int[] sandMapping = { 4, 1, 5, 2, 7, 4, 8, 5, 3, 0, 4, 1, 6, 3, 7, 4 };
            int baseIndex = sandMapping[cornerMask];
            int fullIndex = ((baseIndex / 3) + 1) * 3 + (baseIndex % 3);
            if (fullIndex < 0 || fullIndex >= subtiles.length) return null;
            return subtiles[fullIndex];
        } else {
            int subTileIndex = XP_AUTOTILE_TABLE[cornerMask];
            if (subTileIndex < 0 || subTileIndex >= subtiles.length) return null;
            TextureRegion mini16 = subtiles[subTileIndex];
            if (mini16 == null) return null;
            TextureRegion scaled = new TextureRegion(
                mini16.getTexture(),
                mini16.getRegionX(),
                mini16.getRegionY(),
                mini16.getRegionWidth() * 2,
                mini16.getRegionHeight() * 2
            );
            return scaled;
        }
    }



    private static TextureRegion[] splitIntoFrames(TextureRegion source, int frameW, int frameH) {
        int totalCols = source.getRegionWidth() / frameW;
        int totalRows = source.getRegionHeight() / frameH;
        TextureRegion[] frames = new TextureRegion[totalCols * totalRows];
        int idx = 0;
        for (int ry = 0; ry < totalRows; ry++) {
            for (int rx = 0; rx < totalCols; rx++) {
                int x = source.getRegionX() + rx * frameW;
                int y = source.getRegionY() + ry * frameH;
                frames[idx++] = new TextureRegion(source.getTexture(), x, y, frameW, frameH);
            }
        }
        return frames;
    }
    /**
     * Splits a single 96×128 frame into 48 sub-tiles (16×16 each).
     */
    private static TextureRegion[] splitSubtiles(TextureRegion frame) {
        TextureRegion[] result = new TextureRegion[MAX_SUBTILES];
        int idx = 0;
        for (int row = 0; row < SUBTILE_ROWS; row++) {
            for (int col = 0; col < SUBTILE_COLS; col++) {
                int sx = frame.getRegionX() + col * SUBTILE_SIZE;
                int sy = frame.getRegionY() + row * SUBTILE_SIZE;
                result[idx++] = new TextureRegion(
                    frame.getTexture(), sx, sy,
                    SUBTILE_SIZE, SUBTILE_SIZE
                );
            }
        }
        return result;
    }


    /**
     * Returns one sub‐tile from the given autotile key, frame, and (col,row) within that frame.
     * For “sand_shore” we use 3 columns (otherwise we default to SUBTILE_COLS).
     */
    public static TextureRegion getSubTile(String autotileKey, int animFrame, int col, int row) {
        TextureRegion[][] frames = autotileSubTiles.get(autotileKey);
        if (frames == null || frames.length == 0) {
            return null;
        }
        animFrame = animFrame % frames.length;
        TextureRegion[] subtiles = frames[animFrame];
        if (subtiles == null) {
            return null;
        }
        int cols = autotileKey.equals("sand_shore") ? 3 : SUBTILE_COLS;
        int index = row * cols + col;
        if (index < 0 || index >= subtiles.length) {
            return null;
        }
        return subtiles[index];
    }


    /**
     * Splits a single frame into sub‐tiles given custom parameters.
     * For example, for sand_shore, split into 3 columns and 4 rows of 32×32 tiles.
     */
    private static TextureRegion[] splitSubtilesCustom(TextureRegion frame, int subTileSize, int subTileCols, int subTileRows) {
        TextureRegion[] result = new TextureRegion[subTileCols * subTileRows];
        int index = 0;
        for (int row = 0; row < subTileRows; row++) {
            for (int col = 0; col < subTileCols; col++) {
                int sx = frame.getRegionX() + col * subTileSize;
                int sy = frame.getRegionY() + row * subTileSize;
                result[index++] = new TextureRegion(frame.getTexture(), sx, sy, subTileSize, subTileSize);
            }
        }
        return result;
    }


    /**
     * Splits an entire 768×128 region into an array of 8 frames (each 96×128).
     */


    /**
     * Splits a single frame (a 96×128 TextureRegion) into 48 sub‐tiles (16×16 each).
     * This method uses the frame’s own regionX and regionY as the starting offset.
     */


    public static TextureRegion getAutoTileSubTile(String key, int frameIndex, int col, int row) {
        TextureRegion[][] frames = autotileSubTiles.get(key);
        if (frames == null || frameIndex < 0 || frameIndex >= frames.length) return null;
        TextureRegion[] subtiles = frames[frameIndex];
        int idx = row * SUBTILE_COLS + col;
        if (idx < 0 || idx >= subtiles.length) return null;

        TextureRegion base16 = subtiles[idx];
        if (base16 == null) return null;
        TextureRegion scaled = new TextureRegion(
            base16.getTexture(),
            base16.getRegionX(),
            base16.getRegionY(),
            base16.getRegionWidth() * 2,
            base16.getRegionHeight() * 2
        );
        return scaled;
    }
    /**
     * Returns a 32×32 TextureRegion representing the ocean’s main center
     * (the fully “water‐filled” block) as defined in Pokémon Essentials’ RMXP
     * autotile layout.
     *
     * <p>
     * This method works as follows:
     * <ul>
     *   <li>It retrieves the full “Sea” region from the autotiles atlas.</li>
     *   <li>It splits that region into 96×128 frames (of which there are eight in your sheet).</li>
     *   <li>It selects the desired animation frame (wrapping the index if needed).</li>
     *   <li>It then extracts the central 32×32 block from that frame—i.e. the block starting at (32,32),
     *       which is considered the “ocean center” in Pokémon Essentials.</li>
     * </ul>
     * </p>
     *
     * @param animFrame the animation frame index to use (it will be wrapped into range)
     * @return a TextureRegion of size 32×32 for the ocean center, or null if something is missing.
     */
    /**
     * Returns a 32×32 TextureRegion representing the animated ocean center.
     * <p>
     * The method retrieves the full "Sea" region from the autotiles atlas and splits it
     * into eight 96×128 frames (one per animation step). Each frame is conceptually divided
     * into 32×32 blocks (3 columns × 4 rows). Previously the center block was taken as (1,1)
     * (starting at (32,32)). Now we choose the block one tile down—that is, block (1,2) starting
     * at (32,48)—to be the ocean center.
     * </p>
     *
     * @param animFrame the animation frame index to use (wrapped to the available frames)
     * @return a TextureRegion of size 32×32 for the ocean center, or null if unavailable.
     */
    public static TextureRegion getOceanCenterFrame(int animFrame) {
        TextureRegion seaRegion = autotiles.findRegion("Sea");
        if (seaRegion == null) {
            GameLogger.error("Cannot find 'Sea' region in the autotiles atlas!");
            return null;
        }
        TextureRegion[] seaFrames = splitIntoFrames(seaRegion, 96, 128);
        if (seaFrames == null || seaFrames.length == 0) {
            GameLogger.error("No sea frames available!");
            return null;
        }
        int safeFrame = animFrame % seaFrames.length;
        TextureRegion seaFrame = seaFrames[safeFrame];
        int centerX = seaFrame.getRegionX() + 32;
        int centerY = seaFrame.getRegionY() + 48;
        TextureRegion oceanCenter = new TextureRegion(seaFrame.getTexture(), centerX, centerY, 32, 32);
        return oceanCenter;
    }


    public static TextureRegion[][] getAutoTileSubTiles(String key) {
        return autotileSubTiles.get(key);
    }

    /**
     * Get the final 16×16 sub‐tile region for a given (key, cornerMask, animFrame).
     */
    /**
     * For “sand_shore,” we store each 16×16 in a 6×8 grid => 48 sub‐tiles.
     * This method takes a `maskIndex` in [0..46] (the 47 RMXP sub‐tiles)
     * and an animFrame in [0..7], then returns a 32×32 scaled region.
     */
    public static TextureAtlas getGirl() {
        return girl;
    }


    /**
     * Access the auto‐tile frames by key (e.g. "sand_shore" or "sea") and frame index.
     * Returns null if out of bounds or missing key.
     */
    public static TextureRegion getAutotileFrame(String key, int index) {
        TextureRegion[] frames = autotileFrames.get(key);
        if (frames == null) {
            GameLogger.error("No autotile frames for key: " + key);
            return null;
        }
        if (index < 0 || index >= frames.length) {
            GameLogger.error("Frame index out of range for key: " + key + " (index=" + index + ")");
            return null;
        }
        return frames[index];
    }

    private static void createFallbackIcons() {
        for (Pokemon.PokemonType type : Pokemon.PokemonType.values()) {
            Color color = TYPE_COLORS.get(type);
            TextureRegion icon = createColoredIcon(color, TYPE_ICON_WIDTH, TYPE_ICON_HEIGHT);
            typeIcons.put(type, icon);
        }
        for (Pokemon.Status status : Pokemon.Status.values()) {
            if (status != Pokemon.Status.NONE) {
                Color color = STATUS_COLORS.get(status);
                TextureRegion icon = createColoredIcon(color, STATUS_ICON_WIDTH, STATUS_ICON_HEIGHT);
                statusIcons.put(status, icon);
            }
        }
    }

    private static TextureRegion createColoredIcon(Color color, int width, int height) {
        if (color == null) {
            GameLogger.error("createColoredIcon received a null color; defaulting to white.");
            color = Color.WHITE;
        }
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fillRectangle(0, 0, width, height);
        pixmap.setColor(Color.WHITE);
        pixmap.drawRectangle(0, 0, width, height);

        Texture texture = new Texture(pixmap);
        pixmap.dispose();

        return new TextureRegion(texture);
    }
    private static void loadTypeAndStatusIcons() {
        TextureRegion typesSheet = ui.findRegion("pokemon-type-icons");
        TextureRegion statusSheet = ui.findRegion("status-icons");

        if (typesSheet == null || statusSheet == null) {
            GameLogger.info("Sprite sheets not found, using fallback system");
            usingFallbackSystem = true;
            createFallbackIcons();
            return;
        }
        TextureRegion[][] statusFrames = statusSheet.split(STATUS_ICON_WIDTH, STATUS_ICON_HEIGHT);
        if (statusFrames.length > 0) {
            TextureRegion[] framesInFirstRow = statusFrames[0];
            for (Pokemon.Status status : Pokemon.Status.values()) {
                if (status == Pokemon.Status.NONE) continue;
                int index = status.ordinal() - 1;

                if (index >= 0 && index < framesInFirstRow.length) {
                    statusIcons.put(status, framesInFirstRow[index]);
                } else {
                    GameLogger.error("Missing status icon for: " + status.name() + " at index " + index);
                }
            }
        }
        boolean hasAllIcons = true;
        for (Pokemon.PokemonType type : Pokemon.PokemonType.values()) {
            if (!typeIcons.containsKey(type)) {
                hasAllIcons = false;
                break;
            }
        }
        for (Pokemon.Status status : Pokemon.Status.values()) {
            if (status != Pokemon.Status.NONE && !statusIcons.containsKey(status)) {
                hasAllIcons = false;
                break;
            }
        }
        if (!hasAllIcons) {
            GameLogger.info("Missing icons detected, using fallback system");
            usingFallbackSystem = true;
            createFallbackIcons();
        }
    }
    public static TextureRegion getStatusIcon(Pokemon.Status status) {
        return statusIcons.get(status);
    }
    public static Color getTypeColor(Pokemon.PokemonType type) {
        return TYPE_COLORS.getOrDefault(type, Color.WHITE);
    }

    public static TextureRegion getOverworldSprite(String name) {
        if (name == null) {
            GameLogger.error("Attempted to get overworld sprite with null name.");
            return null;
        }
        String normalizedName = name.toUpperCase();

        TextureRegion sprite = pokemonoverworld.findRegion(normalizedName + "_overworld");
        if (sprite == null) {
            GameLogger.error("Overworld sprite for Pokémon '" + name + "' not found.");
            return null;
        }
        return sprite;
    }


    public static TextureAtlas getUi() {
        return ui;
    }

    public static TextureAtlas getPokemonback() {
        return pokemonback;
    }

    public static TextureAtlas getPokemonfront() {
        return pokemonfront;
    }

    public static TextureAtlas getPokemonicon() {
        return pokemonicon;
    }

    public static TextureAtlas getPokemonoverworld() {
        return pokemonoverworld;
    }

    public static TextureAtlas getItems() {
        return items;
    }

    public static TextureAtlas getBoy() {
        try {
            if (boy == null) {
                GameLogger.error("Boy atlas is null");
                return null;
            }
            for (Texture texture : boy.getTextures()) {
                if (texture == null) {
                    GameLogger.error("Boy atlas texture is null or disposed");
                    return null;
                }
            }
            String[] testRegions = {
                "boy_walk_down",
                "boy_walk_up",
                "boy_run_down",
                "boy_run_up"
            };

            for (String regionName : testRegions) {
                TextureAtlas.AtlasRegion region = boy.findRegion(regionName, 1);
                if (region == null || region.getTexture() == null) {
                    GameLogger.error("Critical region missing or invalid: " + regionName);
                    return null;
                }
            }

            return boy;
        } catch (Exception e) {
            GameLogger.error("Error accessing boy atlas: " + e.getMessage());
            return null;
        }

    }

    public static TextureAtlas getBattlebacks() {
        return battlebacks;
    }

    public static TextureAtlas getTiles() {
        return tiles;
    }

    public static Texture getWhitePixel() {
        if (whitePixel == null) {
            Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pixmap.setColor(Color.WHITE);
            pixmap.fill();
            whitePixel = new Texture(pixmap);
            pixmap.dispose();
        }
        return whitePixel;
    }

    public static void initialize(TextureAtlas steps, TextureAtlas battlebacks, TextureAtlas ui,
                                  TextureAtlas pokemonback, TextureAtlas pokemonfront, TextureAtlas pokemonicon,
                                  TextureAtlas pokemonoverworld, TextureAtlas items, TextureAtlas boy,
                                  TextureAtlas tiles, TextureAtlas effects, TextureAtlas mountains
        , TextureAtlas blocks, TextureAtlas characters, TextureAtlas clothing, TextureAtlas hairstyles, TextureAtlas buildings, TextureAtlas girl, TextureAtlas autotiles, TextureAtlas capsuleThrow, TextureAtlas owFx) {
        TextureManager.steps = steps;
        TextureManager.effects = effects;
        TextureManager.battlebacks = battlebacks;
        TextureManager.ui = ui;
        TextureManager.pokemonback = pokemonback;
        TextureManager.pokemonfront = pokemonfront;
        TextureManager.pokemonicon = pokemonicon;
        TextureManager.pokemonoverworld = pokemonoverworld;
        TextureManager.items = items;
        TextureManager.boy = boy;
        TextureManager.tiles = tiles;
        TextureManager.mountains = mountains;
        TextureManager.blocks = blocks;
        TextureManager.characters = characters;
        TextureManager.clothing = clothing;
        TextureManager.hairstyles = hairstyles;
        TextureManager.buildings = buildings;
        TextureManager.capsuleThrow = capsuleThrow;
        TextureManager.autotiles = autotiles;
        TextureManager.girl = girl;
        TextureManager.owFx = owFx;
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        whitePixel = new Texture(pixmap);
        for (Texture texture : tiles.getTextures()) {
            texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        }
        loadTypeAndStatusIcons();
        loadCentralTileTextures();
        loadAutoTiles();


    }

    private static void loadCentralTileTextures() {
        if (tiles == null) {
            GameLogger.error("Tiles atlas is not initialized!");
            return;
        }
        GameLogger.info("=== Starting Texture Loading ===");
        GameLogger.info("Available regions in atlas:");
        if (tiles != null) {
            for (TextureAtlas.AtlasRegion region : tiles.getRegions()) {
                GameLogger.info(" - " + region.name);
            }
        }
        assert tiles != null;
        tileTextures.put(WATER, tiles.findRegion("water"));
        tileTextures.put(GRASS, tiles.findRegion("grass"));
        tileTextures.put(SAND, tiles.findRegion("sand"));
        tileTextures.put(ROCK, tiles.findRegion("rock"));
        tileTextures.put(SNOW, tiles.findRegion("snow_base"));
        tileTextures.put(HAUNTED_GRASS, tiles.findRegion("haunted_grass"));
        tileTextures.put(SNOW_TALL_GRASS, tiles.findRegion("snow_tall_grass"));
        tileTextures.put(HAUNTED_TALL_GRASS, tiles.findRegion("haunted_tall_grass"));
        tileTextures.put(HAUNTED_SHROOM, tiles.findRegion("haunted_shroom"));
        tileTextures.put(HAUNTED_SHROOMS, tiles.findRegion("haunted_shrooms"));
        tileTextures.put(TALL_GRASS, tiles.findRegion("tall_grass"));
        tileTextures.put(FOREST_GRASS, tiles.findRegion("forest_grass"));
        tileTextures.put(FOREST_TALL_GRASS, tiles.findRegion("forest_tall_grass"));
        tileTextures.put(RAIN_FOREST_GRASS, tiles.findRegion("forest_grass"));
        tileTextures.put(RAIN_FOREST_TALL_GRASS, tiles.findRegion("rain_forest_tall_grass"));
        tileTextures.put(DESERT_SAND, tiles.findRegion("desert_sand"));
        tileTextures.put(DESERT_ROCKS, tiles.findRegion("desert_rock"));
        tileTextures.put(DESERT_GRASS, tiles.findRegion("desert_grass"));
        tileTextures.put(GRASS_2, tiles.findRegion("grass", 2));
        tileTextures.put(FLOWER, tiles.findRegion("flower", 1));
        tileTextures.put(FLOWER_1, tiles.findRegion("flower", 1));
        tileTextures.put(TALL_GRASS_2, tiles.findRegion("tall_grass_two"));
        tileTextures.put(TALL_GRASS_3, tiles.findRegion("tall_grass_three"));
        tileTextures.put(FLOWER_2, tiles.findRegion("flower", 1));
        tileTextures.put(GRASS_3, tiles.findRegion("grass", 3));
        tileTextures.put(SNOW_2, tiles.findRegion("snow_grass"));
        tileTextures.put(SNOW_3, tiles.findRegion("snow_base"));
        tileTextures.put(RUINS_GRASS_0, tiles.findRegion("ruins_grass", 0));
        tileTextures.put(RUINS_GRASS, tiles.findRegion("ruins_grass", 1));
        tileTextures.put(RUINS_TALL_GRASS, tiles.findRegion("ruins_tall_grass", 0));
        tileTextures.put(RUINS_BRICKS, tiles.findRegion("ruin_bricks", 0));
        tileTextures.put(CAVE_ENTRANCE, tiles.findRegion("cave_entrance"));
        tileTextures.put(MOUNTAIN_TILE_TOP_LEFT_ROCK_BG, tiles.findRegion("MOUNTAIN_TILE_TOP_LEFT_ROCK_BG"));
        tileTextures.put(MOUNTAIN_TILE_TOP_MID, tiles.findRegion("MOUNTAIN_TILE_TOP_MID"));
        tileTextures.put(MOUNTAIN_TILE_TOP_RIGHT_ROCK_BG, tiles.findRegion("MOUNTAIN_TILE_TOP_RIGHT_ROCK_BG"));
        tileTextures.put(MOUNTAIN_TILE_MID_LEFT, tiles.findRegion("MOUNTAIN_TILE_MID_LEFT"));
        tileTextures.put(MOUNTAIN_TILE_CENTER, tiles.findRegion("MOUNTAIN_TILE_CENTER"));
        tileTextures.put(MOUNTAIN_TILE_MID_RIGHT, tiles.findRegion("MOUNTAIN_TILE_MID_RIGHT"));
        tileTextures.put(MOUNTAIN_TILE_BOT_LEFT_ROCK_BG, tiles.findRegion("MOUNTAIN_TILE_BOT_LEFT_ROCK_BG"));
        tileTextures.put(MOUNTAIN_TILE_BOT_MID, tiles.findRegion("MOUNTAIN_TILE_BOT_MID"));
        tileTextures.put(MOUNTAIN_TILE_BOT_RIGHT_ROCK_BG, tiles.findRegion("MOUNTAIN_TILE_BOT_RIGHT_ROCK_BG"));
        tileTextures.put(MOUNTAIN_TILE_TOP_LEFT_GRASS_BG, tiles.findRegion("MOUNTAIN_TILE_TOP_LEFT_GRASS_BG"));
        tileTextures.put(MOUNTAIN_TILE_TOP_RIGHT_GRASS_BG, tiles.findRegion("MOUNTAIN_TILE_TOP_RIGHT_GRASS_BG"));
        tileTextures.put(MOUNTAIN_TILE_BOT_RIGHT_GRASS_BG, tiles.findRegion("MOUNTAIN_TILE_BOT_RIGHT_GRASS_BG"));
        tileTextures.put(TileType.MOUNTAIN_WALL, tiles.findRegion("mountainBASEMIDDLE"));
        tileTextures.put(TileType.MOUNTAIN_CORNER_TL, tiles.findRegion("mountainTOPLEFT")); // Top-left corner
        tileTextures.put(TileType.MOUNTAIN_CORNER_TR, tiles.findRegion("mountaintopRIGHT")); // Top-right corner
        tileTextures.put(TileType.MOUNTAIN_CORNER_BL, tiles.findRegion("mountainBASELEFT")); // Bottom-left corner
        tileTextures.put(TileType.MOUNTAIN_CORNER_BR, tiles.findRegion("mountainbaseRIGHT")); // Bottom-right corner
        tileTextures.put(TileType.MOUNTAIN_SLOPE_LEFT, tiles.findRegion("tile080"));
        tileTextures.put(TileType.MOUNTAIN_SLOPE_RIGHT, tiles.findRegion("tile046"));
        tileTextures.put(TileType.MOUNTAIN_STAIRS, tiles.findRegion("mountainstairsMiddle"));
        tileTextures.put(TileType.MOUNTAIN_PATH, tiles.findRegion("tile081"));
        tileTextures.put(TileType.MOUNTAIN_BASE_EDGE, tiles.findRegion("tile038"));
        tileTextures.put(TileType.MOUNTAIN_STAIRS_LEFT, tiles.findRegion("mountainstairsLEFT")); // Left stairs
        tileTextures.put(TileType.MOUNTAIN_STAIRS_RIGHT, tiles.findRegion("mountainstarsRIGHT")); // Right stairs
        tileTextures.put(TileType.MOUNTAIN_PEAK, tiles.findRegion("tile0118"));
        tileTextures.put(TileType.MOUNTAIN_BASE, tiles.findRegion("mountainBASEMIDDLE"));
        tileTextures.put(TileType.MOUNTAIN_EDGE_LEFT, tiles.findRegion("MOUNTAINMIDDLELEFT"));
        tileTextures.put(TileType.MOUNTAIN_EDGE_RIGHT, tiles.findRegion("mountainMIDDLERIGHT"));
        tileTextures.put(TileType.MOUNTAIN_EDGE_TOP, tiles.findRegion("tile029"));
        tileTextures.put(TileType.MOUNTAIN_EDGE_BOTTOM, tiles.findRegion("tile089"));
        tileTextures.put(TileType.MOUNTAIN_CORNER_INNER_TOPLEFT, tiles.findRegion("tile029"));
        tileTextures.put(STAIRS, tiles.findRegion("tile052"));
        tileTextures.put(MOUNTAIN_TILE_CONNECTING_CORNER_TOP_LEFT, tiles.findRegion("MOUNTAIN_TILE_CONNECTING_CORNER_TOP_LEFT"));
        tileTextures.put(MOUNTAIN_TILE_CONNECTING_CORNER_TOP_RIGHT, tiles.findRegion("MOUNTAIN_TILE_CONNECTING_CORNER_TOP_RIGHT"));
        tileTextures.put(MOUNTAIN_TILE_CONNECTING_CORNER_BOTTOM_LEFT, tiles.findRegion("MOUNTAIN_TILE_CONNECTING_CORNER_BOTTOM_LEFT"));
        tileTextures.put(MOUNTAIN_TILE_CONNECTING_CORNER_BOTTOM_RIGHT, tiles.findRegion("MOUNTAIN_TILE_CONNECTING_CORNER_BOTTOM_RIGHT"));
        tileTextures.put(FAIRY_ROCK, tiles.findRegion("fairy_rock"));
        tileTextures.put(CRYSTAL_ROCK, tiles.findRegion("crystal"));
        tileTextures.put(SNOWY_GRASS, tiles.findRegion("snowy_grass"));
        tileTextures.put(BEACH_SAND, tiles.findRegion("beach_sand"));
        tileTextures.put(BEACH_GRASS, tiles.findRegion("beach_tall_grass"));
        tileTextures.put(BEACH_GRASS_2, tiles.findRegion("beach_grass"));
        tileTextures.put(BEACH_STARFISH, tiles.findRegion("beach_starfish"));
        tileTextures.put(BEACH_SHELL, tiles.findRegion("beach_shell"));
        tileTextures.put(TALL_GRASS_OVERLAY, tiles.findRegion("tall_grass_overlay"));
        tileTextures.put(TALL_GRASS_OVERLAY_2, tiles.findRegion("tall_grass_2_overlay"));
        tileTextures.put(TALL_GRASS_OVERLAY_3, tiles.findRegion("tall_grass_3_overlay"));
        tileTextures.put(SNOW_TALL_GRASS_OVERLAY, tiles.findRegion("snow_tall_grass_overlay"));
        tileTextures.put(HAUNTED_TALL_GRASS_OVERLAY, tiles.findRegion("haunted_tall_grass_overlay"));
        tileTextures.put(RAINFOREST_TALL_GRASS_OVERLAY, tiles.findRegion("rain_forest_tall_grass_overlay"));
        tileTextures.put(RUINS_TALL_GRASS_OVERLAY, tiles.findRegion("ruins_tall_grass_overlay"));
        tileTextures.put(FOREST_TALL_GRASS_OVERLAY, tiles.findRegion("forest_tall_grass_overlay"));
        tileTextures.put(DESERT_TALL_GRASS_OVERLAY, tiles.findRegion("desert_grass_overlay"));
        tileTextures.put(BEACH_TALL_GRASS_OVERLAY, tiles.findRegion("beach_tall_grass_overlay"));
        for (TextureRegion texture : tileTextures.values()) {
            if (texture != null && texture.getTexture() != null) {
                texture.getTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            }
        }
        GameLogger.info("TileType name mappings:");
        for (Map.Entry<Integer, String> entry : TileType.getTileTypeNames().entrySet()) {
            GameLogger.info(String.format("Tile ID %d -> Name '%s'", entry.getKey(), entry.getValue()));
        }
        for (Map.Entry<Integer, String> entry : TileType.getTileTypeNames().entrySet()) {
            int tileId = entry.getKey();
            String tileName = entry.getValue();
            TextureRegion region = tiles.findRegion(tileName);
            tileTextures.put(tileId, region);

            if (region == null) {
            } else {
                GameLogger.info(String.format("Successfully loaded texture for tile %d (name: %s)", tileId, tileName));
            }
        }
    }


    public static TextureRegion getTileTexture(int tileType) {
        return tileTextures.get(tileType);
    }

    private static void debugAtlas(String name, TextureAtlas atlas) {
        if (atlas == null) {
            GameLogger.error(name + " atlas is null!");
            return;
        }
        GameLogger.info(name + " atlas regions:");
        for (TextureAtlas.AtlasRegion region : atlas.getRegions()) {
            GameLogger.info("  - " + region.name +
                " (x=" + region.getRegionX() +
                ", y=" + region.getRegionY() +
                ", w=" + region.getRegionWidth() +
                ", h=" + region.getRegionHeight() + ")");
        }
    }


    public enum StatusCondition {
        NONE(0),
        SLEEP(1),
        POISON(2),
        BURN(3),
        FREEZE(4),
        PARALYSIS(5),
        TOXIC(6),    // Bad poison
        CONFUSION(7);

        private final int index;

        StatusCondition(int index) {
            this.index = index;
        }

        public int getIndex() {
            return index;
        }
    }
}


