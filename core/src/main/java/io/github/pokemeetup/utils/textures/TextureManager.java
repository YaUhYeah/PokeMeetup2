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

    import java.util.HashMap;
    import java.util.Map;

    import static io.github.pokemeetup.utils.textures.TileType.*;

    public class TextureManager {
        public static final int TYPE_ICON_WIDTH = 64;
        public static final int TYPE_ICON_HEIGHT = 38;
        public static final int STATUS_ICON_WIDTH = 44;
        public static final int STATUS_ICON_HEIGHT = 16;
        public static final Map<Integer, TextureRegion> tileTextures = new HashMap<>();
        private static final Map<BiomeType, Map<Integer, TextureRegion>> biomeTileTextures = new HashMap<>();
        private static final Map<Pokemon.PokemonType, TextureRegion> typeIcons = new HashMap<>();
        private static final Map<StatusCondition, TextureRegion> statusIcons = new HashMap<>();
        private static final Map<Pokemon.PokemonType, Color> TYPE_COLORS = new HashMap<>();
        private static final Map<StatusCondition, Color> STATUS_COLORS = new HashMap<>();
        public static TextureAtlas ui;

        public static TextureAtlas getGirl() {
            return girl;
        }

        public static TextureAtlas pokemonback;
        public static TextureAtlas buildings;
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

        private static boolean usingFallbackSystem = false;
        private static Texture whitePixel;

        static {
            // Initialize status colors
            STATUS_COLORS.put(StatusCondition.NONE, Color.WHITE);
            STATUS_COLORS.put(StatusCondition.SLEEP, Color.GRAY);
            STATUS_COLORS.put(StatusCondition.POISON, new Color(0.627f, 0.439f, 0.627f, 1));
            STATUS_COLORS.put(StatusCondition.BURN, new Color(0.940f, 0.501f, 0.376f, 1));
            STATUS_COLORS.put(StatusCondition.FREEZE, new Color(0.564f, 0.815f, 0.940f, 1));
            STATUS_COLORS.put(StatusCondition.PARALYSIS, new Color(0.972f, 0.815f, 0.376f, 1));
            STATUS_COLORS.put(StatusCondition.TOXIC, new Color(0.5f, 0.1f, 0.5f, 1));
            STATUS_COLORS.put(StatusCondition.CONFUSION, new Color(0.940f, 0.376f, 0.564f, 1));
        }

        static {
            // Update type color mappings
            // Initialize type colors
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



        private static void createFallbackIcons() {
            for (Pokemon.PokemonType type : Pokemon.PokemonType.values()) {
                Color color = TYPE_COLORS.get(type);
                TextureRegion icon = createColoredIcon(color, TYPE_ICON_WIDTH, TYPE_ICON_HEIGHT);
                typeIcons.put(type, icon);
            }

            for (StatusCondition status : StatusCondition.values()) {
                if (status != StatusCondition.NONE) {
                    Color color = STATUS_COLORS.get(status);
                    TextureRegion icon = createColoredIcon(color, STATUS_ICON_WIDTH, STATUS_ICON_HEIGHT);
                    statusIcons.put(status, icon);
                }
            }
        }

        public static TextureRegion getTextureForObjectType(WorldObject.ObjectType objectType) {
            switch (objectType) {
                case TREE_0:
                    return tiles.findRegion("treeONE");
                case TREE_1:
                    return tiles.findRegion("treeTWO");
                case SNOW_TREE:
                    return tiles.findRegion("snow_tree");
                case HAUNTED_TREE:
                    return tiles.findRegion("haunted_tree");
                case POKEBALL:
                    return items.findRegion("pokeball");
                case CACTUS:
                    return tiles.findRegion("desert_cactus");
                case SUNFLOWER:
                    return tiles.findRegion("sunflower");
                case VINES:
                    return tiles.findRegion("vines");
                case RAIN_TREE:
                    return tiles.findRegion("rain_tree");
                case BUSH:
                    return tiles.findRegion("bush");
                case DEAD_TREE:
                    return tiles.findRegion("dead_tree");
                case SMALL_HAUNTED_TREE:
                    return tiles.findRegion("small_haunted_tree");
                case RUINS_TREE:
                    return tiles.findRegion("ruins_tree");
                case RUIN_POLE:
                    return tiles.findRegion("ruins_pole");
                case APRICORN_TREE:
                    return tiles.findRegion("apricorn_tree_grown");
                default:
                    GameLogger.error("Missing texture for object type: " + objectType);
                    return null;
            }
        }

        private static TextureRegion createColoredIcon(Color color, int width, int height) {
            Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
            pixmap.setColor(color);
            pixmap.fillRectangle(0, 0, width, height);

            // Add a border
            pixmap.setColor(Color.WHITE);
            pixmap.drawRectangle(0, 0, width, height);

            Texture texture = new Texture(pixmap);
            pixmap.dispose();

            return new TextureRegion(texture);
        }

        private static void loadTypeAndStatusIcons() {
            TextureRegion typesSheet = ui.findRegion("types");
            TextureRegion statusSheet = ui.findRegion("statuses");

            if (typesSheet == null || statusSheet == null) {
                GameLogger.info("Sprite sheets not found, using fallback system");
                usingFallbackSystem = true;
                createFallbackIcons();
                return;
            }

            TextureRegion[][] statusFrames = statusSheet.split(STATUS_ICON_WIDTH, STATUS_ICON_HEIGHT);
            for (StatusCondition status : StatusCondition.values()) {
                if (status != StatusCondition.NONE && (status.getIndex() - 1) < statusFrames.length) { // Corrected condition
                    statusIcons.put(status, statusFrames[status.getIndex() - 1][0]); // Corrected access
                } else if (status != StatusCondition.NONE) {
                    GameLogger.error("Missing status icon for: " + status.name());
                }
            }

            boolean hasAllIcons = true;
            for (Pokemon.PokemonType type : Pokemon.PokemonType.values()) {
                if (!typeIcons.containsKey(type)) {
                    hasAllIcons = false;
                    break;
                }
            }

            for (StatusCondition status : StatusCondition.values()) {
                if (status != StatusCondition.NONE && !statusIcons.containsKey(status)) {
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

        public static TextureRegion getStatusIcon(StatusCondition status) {
            return statusIcons.get(status);
        }

        // Helper method to get type color
        public static Color getTypeColor(Pokemon.PokemonType type) {
            return TYPE_COLORS.getOrDefault(type, Color.WHITE);
        }

        public static TextureRegion getOverworldSprite(String name) {
            if (name == null) {
                GameLogger.error("Attempted to get overworld sprite with null name.");
                return null;
            }

            // Normalize the name to lowercase to ensure consistency
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

                // Verify atlas textures
                for (Texture texture : boy.getTextures()) {
                    if (texture == null) {
                        GameLogger.error("Boy atlas texture is null or disposed");
                        return null;
                    }
                }

                // Verify some key regions
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

        public static void debugAtlasState(String name, TextureAtlas atlas) {
            if (atlas == null) {
                return;
            }

            try {
            } catch (Exception e) {
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
                // Create on demand if not initialized
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
            , TextureAtlas blocks, TextureAtlas characters, TextureAtlas clothing, TextureAtlas hairstyles, TextureAtlas buildings, TextureAtlas girl) {
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

            TextureManager.girl = girl;
            // Create white pixel texture
            Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pixmap.setColor(Color.WHITE);
            pixmap.fill();
            whitePixel = new Texture(pixmap);
            for (Texture texture : tiles.getTextures()) {
                texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            }
            loadTypeAndStatusIcons();
            loadCentralTileTextures();


        }

        private static void loadCentralTileTextures() {
            // Ensure tiles atlas is loaded
            if (tiles == null) {
                GameLogger.error("Tiles atlas is not initialized!");
                return;
            }
    //            loadAllBiomeTextures();
            GameLogger.info("=== Starting Texture Loading ===");

            // First log all available regions
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
            tileTextures.put(FLOWER, tiles.findRegion("flower",1));
            tileTextures.put(FLOWER_1, tiles.findRegion("flower", 1));
            tileTextures.put(TALL_GRASS_2, tiles.findRegion("tall_grass", 3));
            tileTextures.put(TALL_GRASS_3, tiles.findRegion("tall_grass", 2));
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

            // Add other tile types as needed
            for (TextureRegion texture : tileTextures.values()) {
                if (texture != null && texture.getTexture() != null) {
                    texture.getTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
                }
            }
            GameLogger.info("TileType name mappings:");
            for (Map.Entry<Integer, String> entry : TileType.getTileTypeNames().entrySet()) {
                GameLogger.info(String.format("Tile ID %d -> Name '%s'", entry.getKey(), entry.getValue()));
            }

            // Log each texture loading attempt
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


