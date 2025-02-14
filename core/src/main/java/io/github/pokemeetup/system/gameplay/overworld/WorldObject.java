package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonValue;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.system.gameplay.overworld.biomes.Biome;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;
import io.github.pokemeetup.utils.textures.TileType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.badlogic.gdx.math.MathUtils.random;

public class WorldObject {
    private static final float POKEBALL_DESPAWN_TIME = 300f;
    private static final Map<ObjectType, TextureRegion> textureCache = new HashMap<>();

    static {
        initializeTextures();
    }

    public ObjectType type;
    private WorldObject attachedTo;
    private float pixelX;
    private float pixelY;
    private TextureRegion texture;
    private String id;
    private boolean isCollidable;
    private float spawnTime;
    private int tileX, tileY;

    public WorldObject(int tileX, int tileY, TextureRegion texture, ObjectType type) {
        this.id = UUID.randomUUID().toString();
        this.tileX = tileX;
        this.tileY = tileY;
        this.pixelX = tileX * World.TILE_SIZE;
        this.pixelY = tileY * World.TILE_SIZE;
        this.texture = texture;
        this.attachedTo = null;
        this.type = type;
        this.isCollidable = type.isCollidable;
        this.spawnTime = type.isPermanent ? 0 : System.currentTimeMillis() / 1000f;
    }

    public WorldObject() {
        this.id = UUID.randomUUID().toString();
        this.isCollidable = false;
        this.spawnTime = 0f;
        this.attachedTo = null;
    }

    private static void initializeTextures() {
        try {
            // Initialize texture cache if not already done
            if (textureCache.isEmpty()) {
                TextureAtlas atlas = TextureManager.tiles;
                if (atlas != null) {
                    textureCache.put(ObjectType.TREE_0, atlas.findRegion("treeONE"));
                    textureCache.put(ObjectType.TREE_1, atlas.findRegion("treeTWO"));
                    textureCache.put(ObjectType.SNOW_TREE, atlas.findRegion("snow_tree"));
                    textureCache.put(ObjectType.HAUNTED_TREE, atlas.findRegion("haunted_tree"));
                    textureCache.put(ObjectType.POKEBALL, atlas.findRegion("pokeball"));
                    textureCache.put(ObjectType.CACTUS, atlas.findRegion("desert_cactus"));
                    textureCache.put(ObjectType.BUSH, atlas.findRegion("bush"));
                    textureCache.put(ObjectType.SUNFLOWER, atlas.findRegion("sunflower"));
                    textureCache.put(ObjectType.VINES, atlas.findRegion("vines"));
                    textureCache.put(ObjectType.DEAD_TREE, atlas.findRegion("dead_tree"));
                    textureCache.put(ObjectType.SMALL_HAUNTED_TREE, atlas.findRegion("small_haunted_tree"));
                    textureCache.put(ObjectType.RUIN_POLE, atlas.findRegion("ruins_pole"));
                    textureCache.put(ObjectType.RUINS_TREE, atlas.findRegion("ruins_tree"));
                    textureCache.put(ObjectType.RAIN_TREE, atlas.findRegion("rain_tree"));
                    textureCache.put(ObjectType.APRICORN_TREE, atlas.findRegion("apricorn_tree_grown"));
                    textureCache.put(ObjectType.CHERRY_TREE, atlas.findRegion("CherryTree"));
                    textureCache.put(ObjectType.BEACH_TREE, atlas.findRegion("beach_tree"));
                    // Add other object types as needed
                }
            }
        } catch (Exception e) {
            GameLogger.error("Failed to initialize textures: " + e.getMessage());
        }
    }


    public void updateFromData(Map<String, Object> data) {
        if (data == null) return;
        try {
            // --- Handle tileX ---
            Object tileXObj = data.get("tileX");
            if (tileXObj instanceof JsonValue) {
                this.tileX = ((JsonValue) tileXObj).getInt("value", 0);
            } else if (tileXObj instanceof Number) {
                this.tileX = ((Number) tileXObj).intValue();
            }
            // --- Handle tileY ---
            Object tileYObj = data.get("tileY");
            if (tileYObj instanceof JsonValue) {
                this.tileY = ((JsonValue) tileYObj).getInt("value", 0);
            } else if (tileYObj instanceof Number) {
                this.tileY = ((Number) tileYObj).intValue();
            }
            this.pixelX = tileX * World.TILE_SIZE;
            this.pixelY = tileY * World.TILE_SIZE;

            // --- Handle spawnTime ---
            Object spawnTimeObj = data.get("spawnTime");
            if (spawnTimeObj instanceof JsonValue) {
                this.spawnTime = ((JsonValue) spawnTimeObj).getFloat("value", 0f);
            } else if (spawnTimeObj instanceof Number) {
                this.spawnTime = ((Number) spawnTimeObj).floatValue();
            } else {
                this.spawnTime = (type != null && type.isPermanent ? 0 : System.currentTimeMillis() / 1000f);
            }

            // --- Handle isCollidable ---
            Object collidableObj = data.get("isCollidable");
            if (collidableObj instanceof JsonValue) {
                this.isCollidable = ((JsonValue) collidableObj).getBoolean("value", (type != null && type.isCollidable));
            } else if (collidableObj instanceof Boolean) {
                this.isCollidable = (Boolean) collidableObj;
            } else {
                this.isCollidable = (type != null && type.isCollidable);
            }

            // --- Handle type ---
            Object raw = data.get("type");
            String typeStr = "";
            if (raw instanceof JsonValue) {
                // Extract the inner "value" (e.g. "SUNFLOWER")
                typeStr = ((JsonValue) raw).getString("value", "");
            } else if (raw instanceof String) {
                typeStr = (String) raw;
            } else if (raw != null) {
                typeStr = raw.toString();
            }
            if (typeStr.isEmpty()) {
                GameLogger.error("Empty type string in serialized data. Setting default type.");
                this.type = ObjectType.TREE_0;
            } else {
                try {
                    this.type = ObjectType.valueOf(typeStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    GameLogger.error("Invalid type string: " + typeStr + ". Setting default type.");
                    this.type = ObjectType.TREE_0;
                }
            }

            // --- Handle id ---
            Object idObj = data.get("id");
            if (idObj instanceof JsonValue) {
                this.id = ((JsonValue) idObj).getString("value", UUID.randomUUID().toString());
            } else if (idObj instanceof String) {
                this.id = (String) idObj;
            } else if (idObj != null) {
                this.id = idObj.toString();
            } else {
                this.id = UUID.randomUUID().toString();
            }

            // Ensure texture is set
        } catch (Exception e) {
            GameLogger.error("Error updating WorldObject from data: " + e.getMessage() + "\nData: " + data.toString());
        }
    }

    public HashMap<String, Object> getSerializableData() {
        HashMap<String, Object> data = new HashMap<>();
        data.put("tileX", tileX);
        data.put("tileY", tileY);

        data.put("type", type.name());
        data.put("spawnTime", spawnTime);
        data.put("isCollidable", isCollidable);
        data.put("id", id);
        return data;
    }


    public WorldObject copy() {
        // Create a new object with the same tile position and type
        WorldObject copy = new WorldObject(this.tileX, this.tileY, this.texture, this.type);

        // Copy all basic fields
        copy.id = this.id;  // Keep same ID for tracking
        copy.pixelX = this.pixelX;
        copy.pixelY = this.pixelY;
        copy.isCollidable = this.isCollidable;
        copy.spawnTime = this.spawnTime;

        copy.texture = this.texture;

        // If there's an attached object, copy that too
        if (this.attachedTo != null) {
            copy.attachedTo = this.attachedTo.copy();
        }

        return copy;
    }

    public TextureRegion getTexture() {
        ensureTexture();
        return texture;
    }

    public void setTexture(TextureRegion texture) {
        this.texture = texture;
    }

    public void ensureTexture() {
        if (texture == null && type != null) {
            texture = textureCache.get(type);
            if (texture == null) {

                texture = textureCache.get(ObjectType.TREE_0);
            }
        }
    }

    public boolean isStatic() {
        return type == ObjectType.TREE_0 ||
            type == ObjectType.TREE_1 ||
            type == ObjectType.BUSH || type == ObjectType.APRICORN_TREE ||
            type == ObjectType.DEAD_TREE || type == ObjectType.RAIN_TREE
            || type == ObjectType.RUINS_TREE || type == ObjectType.RUIN_POLE || type == ObjectType.SNOW_TREE || type == ObjectType.HAUNTED_TREE ||
            type == ObjectType.CACTUS;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }


    public void updateFromNetwork(NetworkProtocol.WorldObjectUpdate update) {
        this.tileX = (int) update.data.get("tileX");
        this.tileY = (int) update.data.get("tileY");
        this.pixelX = tileX * World.TILE_SIZE;
        this.pixelY = tileY * World.TILE_SIZE;
        this.spawnTime = (float) update.data.get("spawnTime");
        this.isCollidable = (boolean) update.data.get("isCollidable");
    }

    public ObjectType getType() {
        return type;
    }

    public void setType(ObjectType type) {
        this.type = type;
    }

    public boolean isExpired() {
        if (type.isPermanent) return false;
        float currentTime = System.currentTimeMillis() / 1000f;
        return currentTime - spawnTime > POKEBALL_DESPAWN_TIME;
    }

    public Rectangle getPlacementBoundingBox() {
        if (isTreeType(type)) {
            // For apricorn trees we already use a 3x3 area.
            if (type == ObjectType.APRICORN_TREE) {
                float treeBaseX = pixelX - World.TILE_SIZE;
                float treeBaseY = pixelY;
                return new Rectangle(treeBaseX, treeBaseY, World.TILE_SIZE * 3, World.TILE_SIZE * 3);
            } else {
                // For other trees, use a 2x3 area to cover the full sprite.
                float treeBaseX = pixelX - World.TILE_SIZE;
                float treeBaseY = pixelY;
                return new Rectangle(treeBaseX, treeBaseY, World.TILE_SIZE * 2, World.TILE_SIZE * 3);
            }
        }
        // For non-tree objects, the placement bounding box can be the same as their visual size.
        return new Rectangle(pixelX, pixelY, type.widthInTiles * World.TILE_SIZE, type.heightInTiles * World.TILE_SIZE);
    }


    public Rectangle getBoundingBox() {
        if (type == ObjectType.APRICORN_TREE) {
            float treeBaseX = pixelX - World.TILE_SIZE;  // Center the 3-tile width
            float treeBaseY = pixelY;  // Bottom of tree

            return new Rectangle(
                treeBaseX,
                treeBaseY,
                World.TILE_SIZE * 3,  // 3 tiles wide
                World.TILE_SIZE * 3   // 3 tiles high
            );
        }
        if (type == ObjectType.TREE_0 || type == ObjectType.RUINS_TREE || type == ObjectType.TREE_1 || type == ObjectType.SNOW_TREE || type == ObjectType.HAUNTED_TREE || type == ObjectType.RAIN_TREE) {
            // Tree collision box: 2x2 tiles at the base only
            float treeBaseX = pixelX - World.TILE_SIZE; // Center the 2-tile width base
            float treeBaseY = pixelY; // Bottom of tree

            return new Rectangle(
                treeBaseX,
                treeBaseY,
                World.TILE_SIZE * 2, // 2 tiles wide
                World.TILE_SIZE * 2  // 2 tiles high (base only)
            );
        } else {
            return new Rectangle(
                pixelX,
                pixelY,
                type.widthInTiles * World.TILE_SIZE,
                type.heightInTiles * World.TILE_SIZE
            );
        }
    }

    public float getPixelX() {
        return pixelX;
    }

    public float getPixelY() {
        return pixelY;
    }

    public int getTileX() {
        return tileX;
    }

    public void setTileX(int tileX) {
        this.tileX = tileX;
    }

    public int getTileY() {
        return tileY;
    }

    public void setTileY(int tileY) {
        this.tileY = tileY;
    }

    public Rectangle getCollisionBox() {
        if (!type.isCollidable) {
            return null;
        }

        if (type == ObjectType.APRICORN_TREE) {
            return new Rectangle(
                pixelX,               // start at center column
                pixelY,               // bottom of the tree
                World.TILE_SIZE,      // 32px wide, one tile
                World.TILE_SIZE * 2   // 64px tall, two tiles high
            );
        } else if (type == ObjectType.RUINS_TREE || type == ObjectType.CHERRY_TREE || type == ObjectType.BEACH_TREE) {
            // 2x2 collision as before
            float treeBaseX = pixelX - World.TILE_SIZE;
            float treeBaseY = pixelY;
            return new Rectangle(treeBaseX, treeBaseY, World.TILE_SIZE * 2, World.TILE_SIZE * 2);
        } else if (isTreeType(type)) {
            // Regular trees (2x2 base collision)
            float treeBaseX = pixelX - World.TILE_SIZE;
            float treeBaseY = pixelY;
            return new Rectangle(treeBaseX, treeBaseY, World.TILE_SIZE * 2, World.TILE_SIZE * 2);
        }

        // Standard objects
        return new Rectangle(
            pixelX,
            pixelY,
            type.widthInTiles * World.TILE_SIZE,
            type.heightInTiles * World.TILE_SIZE
        );
    }

    private boolean isTreeType(ObjectType type) {
        return type == ObjectType.TREE_0 ||
            type == ObjectType.TREE_1 ||
            type == ObjectType.SNOW_TREE ||
            type == ObjectType.HAUNTED_TREE ||
            type == ObjectType.RUINS_TREE ||
            type == ObjectType.APRICORN_TREE ||
            type == ObjectType.RAIN_TREE || type == ObjectType.BEACH_TREE;
    }

    // Add this to your WorldObject class
    public enum ObjectType {
        // Static objects
        TREE_0(true, true, 2, 3, RenderLayer.LAYERED),
        TREE_1(true, true, 2, 3, RenderLayer.LAYERED),
        SNOW_TREE(true, true, 2, 3, RenderLayer.LAYERED),
        HAUNTED_TREE(true, true, 2, 3, RenderLayer.LAYERED),
        RUINS_TREE(true, true, 2, 3, RenderLayer.LAYERED),
        APRICORN_TREE(true, true, 3, 3, RenderLayer.LAYERED),

        // Environmental objects
        CACTUS(true, true, 1, 2, RenderLayer.BELOW_PLAYER),
        DEAD_TREE(true, true, 1, 2, RenderLayer.BELOW_PLAYER),
        SMALL_HAUNTED_TREE(true, true, 1, 2, RenderLayer.BELOW_PLAYER),
        BUSH(true, true, 3,
            2, RenderLayer.BELOW_PLAYER),
        VINES(true, false, 1, 2, RenderLayer.BELOW_PLAYER),
        RUIN_POLE(true, true, 1, 3, RenderLayer.BELOW_PLAYER),
        POKEBALL(true, true, 1, 1, RenderLayer.BELOW_PLAYER),
        RAIN_TREE(true, true, 2, 3, RenderLayer.LAYERED),
        CHERRY_TREE(true, true, 2, 3, RenderLayer.LAYERED),
        SUNFLOWER(true, false, 1, 2, RenderLayer.BELOW_PLAYER),
        BEACH_TREE(true, true, 2, 3, RenderLayer.LAYERED);

        public final boolean isPermanent;    // Permanent or temporary object
        public final boolean isCollidable;   // Has collision or not
        public final int widthInTiles;       // Width in tiles
        public final int heightInTiles;      // Height in tiles

        public final RenderLayer renderLayer;

        ObjectType(boolean isPermanent, boolean isCollidable,
                   int widthInTiles, int heightInTiles, RenderLayer renderLayer) {
            this.isPermanent = isPermanent;
            this.isCollidable = isCollidable;
            this.widthInTiles = widthInTiles;
            this.heightInTiles = heightInTiles;
            this.renderLayer = renderLayer;
        }

        public enum RenderLayer {
            BELOW_PLAYER,
            ABOVE_PLAYER,
            LAYERED,
            ABOVE_TALL_GRASS
        }
    }

    public static class WorldObjectManager {
        public static final float POKEBALL_SPAWN_CHANCE = 0.025f;
        public static final int MAX_POKEBALLS_PER_CHUNK = 1;
        private static final float POKEBALL_SPAWN_INTERVAL = 5.0f; // Try every 5 seconds
        private final Map<Vector2, List<WorldObject>> objectsByChunk = new ConcurrentHashMap<>();
        private final Map<ObjectType, TextureRegion> objectTextures;
        private final long worldSeed;
        private final ConcurrentLinkedQueue<WorldObjectOperation> operationQueue = new ConcurrentLinkedQueue<>();
        private Set<String> removedObjectIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
        // New: a set to track removed objects by their base tile coordinate
        private Set<Vector2> removedObjectTiles = Collections.newSetFromMap(new ConcurrentHashMap<>());
        private float pokeballSpawnTimer = 0f;

        public WorldObjectManager(long seed) {
            this.worldSeed = seed;
            TextureAtlas atlas = TextureManager.tiles;
            this.objectTextures = new HashMap<>();
            objectTextures.put(ObjectType.TREE_0, atlas.findRegion("treeONE"));
            objectTextures.put(ObjectType.TREE_1, atlas.findRegion("treeTWO"));
            objectTextures.put(ObjectType.SNOW_TREE, atlas.findRegion("snow_tree"));
            objectTextures.put(ObjectType.HAUNTED_TREE, atlas.findRegion("haunted_tree"));
            objectTextures.put(ObjectType.POKEBALL, atlas.findRegion("pokeball"));
            objectTextures.put(ObjectType.CACTUS, atlas.findRegion("desert_cactus"));
            objectTextures.put(ObjectType.BUSH, atlas.findRegion("bush"));
            objectTextures.put(ObjectType.SUNFLOWER, atlas.findRegion("sunflower"));
            objectTextures.put(ObjectType.VINES, atlas.findRegion("vines"));
            objectTextures.put(ObjectType.DEAD_TREE, atlas.findRegion("dead_tree"));
            objectTextures.put(ObjectType.SMALL_HAUNTED_TREE, atlas.findRegion("small_haunted_tree"));
            objectTextures.put(ObjectType.RAIN_TREE, atlas.findRegion("rain_tree"));
            objectTextures.put(ObjectType.CHERRY_TREE, atlas.findRegion("CherryTree"));
            objectTextures.put(ObjectType.RUIN_POLE, atlas.findRegion("ruins_pole"));
            objectTextures.put(ObjectType.RUINS_TREE, atlas.findRegion("ruins_tree"));
            objectTextures.put(ObjectType.APRICORN_TREE, atlas.findRegion("apricorn_tree_grown"));
            objectTextures.put(ObjectType.BEACH_TREE, atlas.findRegion("beach_tree"));

        }

        private void sendChunkObjectSync(List<WorldObject> objects) {
            try {
                for (WorldObject obj : objects) {
                    NetworkProtocol.WorldObjectUpdate update = new NetworkProtocol.WorldObjectUpdate();
                    update.objectId = obj.getId();
                    update.type = NetworkProtocol.NetworkObjectUpdateType.ADD;
                    update.data = obj.getSerializableData();

                    GameContext.get().getGameClient().getClient().sendTCP(update);
                }
            } catch (Exception e) {
                GameLogger.error("Failed to send chunk object sync: " + e.getMessage());
            }
        }

        public void removeObjectFromChunk(Vector2 chunkPos, String objectId, int tileX, int tileY) {
            // Record that this object (at this tile) was removed.
            removedObjectIds.add(objectId);
            removedObjectTiles.add(new Vector2(tileX, tileY));
            List<WorldObject> objects = objectsByChunk.get(chunkPos);
            if (objects != null) {
                objects.removeIf(obj -> obj.getId().equals(objectId));
            }
        }

        public boolean isRemovedTile(int tileX, int tileY) {
            return removedObjectTiles.contains(new Vector2(tileX, tileY));
        }

        /**
         * Record that an object was removed at the given tile coordinate.
         */


        public void addObjectToChunk(WorldObject object) {
            int actualChunkX = (int) Math.floor(object.getPixelX() / (Chunk.CHUNK_SIZE * World.TILE_SIZE));
            int actualChunkY = (int) Math.floor(object.getPixelY() / (Chunk.CHUNK_SIZE * World.TILE_SIZE));
            Vector2 actualChunkPos = new Vector2(actualChunkX, actualChunkY);
            if (removedObjectIds.contains(object.getId())) {
                return;
            }
            objectsByChunk.computeIfAbsent(actualChunkPos, k -> new CopyOnWriteArrayList<>()).add(object);
        }

        public void setObjectsForChunk(Vector2 chunkPos, List<WorldObject> objects) {
            if (objects == null) {
                objectsByChunk.remove(chunkPos);
            } else {
                List<WorldObject> filtered = new ArrayList<>();
                for (WorldObject obj : objects) {
                    if (!removedObjectIds.contains(obj.getId())) {
                        filtered.add(obj);
                    }
                }
                objectsByChunk.put(chunkPos, new CopyOnWriteArrayList<>(filtered));
            }
        }

        private WorldObject createObject(ObjectType type, int localX, int localY, Vector2 chunkPos) {
            try {
                int worldTileX = (int) (chunkPos.x * Chunk.CHUNK_SIZE + localX);
                int worldTileY = (int) (chunkPos.y * Chunk.CHUNK_SIZE + localY);
                TextureRegion texture = objectTextures.get(type);
                if (texture != null) {
                    WorldObject object = new WorldObject(worldTileX, worldTileY, texture, type);
                    // IMPORTANT: When creating the object we generate a new UUID.
                    // (This is why tracking by removed tile is necessary.)
                    object.setId(UUID.randomUUID().toString());
                    return object;
                }
            } catch (Exception e) {
                GameLogger.error("Error creating object: " + e.getMessage());
            }
            return null;
        }


        private Rectangle getObjectBounds(int tileX, int tileY, ObjectType type) {
            float width = type.widthInTiles * World.TILE_SIZE;
            float height = type.heightInTiles * World.TILE_SIZE;

            // Special handling for trees which have different base positions
            float xOffset = 0;
            if (isTreeType(type)) {
                if (type == ObjectType.APRICORN_TREE) {
                    width = World.TILE_SIZE * 3;
                    height = World.TILE_SIZE * 3;
                    xOffset = -World.TILE_SIZE; // Center the 3-tile width
                } else {
                    width = World.TILE_SIZE * 2;
                    height = World.TILE_SIZE * 2;
                    xOffset = -World.TILE_SIZE; // Center the 2-tile width
                }
            }

            return new Rectangle(
                tileX * World.TILE_SIZE + xOffset,
                tileY * World.TILE_SIZE,
                width,
                height
            );
        }

        private boolean boundsOverlapWithPadding(Rectangle bounds1, Rectangle bounds2, int spacing) {
            // Use the full spacing value so that trees (and other objects) have enough room
            float padding = World.TILE_SIZE * spacing;
            Rectangle paddedBounds = new Rectangle(
                bounds1.x - padding,
                bounds1.y - padding,
                bounds1.width + (padding * 2),
                bounds1.height + (padding * 2)
            );
            return paddedBounds.overlaps(bounds2);
        }

        private boolean isTreeType(ObjectType type) {
            return type == ObjectType.TREE_0 ||
                type == ObjectType.TREE_1 ||
                type == ObjectType.SNOW_TREE ||
                type == ObjectType.HAUNTED_TREE ||
                type == ObjectType.RUINS_TREE ||
                type == ObjectType.APRICORN_TREE ||
                type == ObjectType.RAIN_TREE || type == ObjectType.CHERRY_TREE || type == ObjectType.BEACH_TREE;
        }

        private int getRequiredSpacing(ObjectType type) {
            switch (type) {
                case APRICORN_TREE:
                    return 3; // Slightly more strict for apricorn trees
                case TREE_0:
                case TREE_1:
                case SNOW_TREE:
                case HAUNTED_TREE:
                case RAIN_TREE:
                case CHERRY_TREE:
                case RUINS_TREE:
                case BEACH_TREE:
                    return 2; // More strict than before for large trees
                default:
                    return 1; // Smaller objects remain at spacing 1
            }
        }

        public void renderTreeBase(SpriteBatch batch, WorldObject tree, World world) {
            // Get texture and handle null case
            TextureRegion treeRegion = tree.getTexture();
            if (treeRegion == null) {
                // Try to re-initialize texture
                tree.ensureTexture();
                treeRegion = tree.getTexture();

                // If still null, skip rendering
                if (treeRegion == null) {
                    GameLogger.error("Failed to load texture for tree: " + tree.getId());
                    return;
                }
            }

            boolean flipY = treeRegion.isFlipY();

            int totalWidth = treeRegion.getRegionWidth();   // For apricorn: should be 96
            int totalHeight = treeRegion.getRegionHeight(); // Also 96 for apricorn
            int baseHeight = totalHeight / 3;               // For apricorn: 96/3 = 32

            float renderX = tree.getPixelX() - World.TILE_SIZE;
            float renderY = tree.getPixelY();

            Vector2 tilePos = new Vector2(tree.getTileX(), tree.getTileY());
            Float lightLevel = world.getLightLevelAtTile(tilePos);

            Color originalColor = batch.getColor().cpy();
            try {
                if (lightLevel != null && lightLevel > 0) {
                    Color lightColor = new Color(1f, 0.8f, 0.6f, 1f);
                    Color baseColor = world.getCurrentWorldColor().cpy();
                    baseColor.lerp(lightColor, lightLevel * 0.7f);
                    batch.setColor(baseColor);
                }

                int baseY = flipY ? 0 : totalHeight - baseHeight;
                TextureRegion baseRegion = new TextureRegion(treeRegion, 0, baseY, totalWidth, baseHeight);
                if (flipY != baseRegion.isFlipY()) {
                    baseRegion.flip(false, true);
                }

                float drawWidth = tree.getType() == ObjectType.APRICORN_TREE ?
                    World.TILE_SIZE * 3 : World.TILE_SIZE * 2;
                float drawHeight = World.TILE_SIZE;

                batch.draw(baseRegion, renderX, renderY, drawWidth, drawHeight);
            } finally {
                batch.setColor(originalColor);
            }
        }

        public void renderTreeTop(SpriteBatch batch, WorldObject tree, World world) {
            // Get texture and handle null case
            TextureRegion treeRegion = tree.getTexture();
            if (treeRegion == null) {
                // Try to re-initialize texture
                tree.ensureTexture();
                treeRegion = tree.getTexture();

                // If still null, skip rendering
                if (treeRegion == null) {
                    GameLogger.error("Failed to load texture for tree: " + tree.getId());
                    return;
                }
            }

            // Now we can safely use the texture
            boolean flipY = treeRegion.isFlipY();

            int totalWidth = treeRegion.getRegionWidth();
            int totalHeight = treeRegion.getRegionHeight();
            int topHeight = (totalHeight * 2) / 3; // top 2/3
            float renderX = tree.getPixelX() - World.TILE_SIZE;
            float renderY = tree.getPixelY() + World.TILE_SIZE;

            Vector2 tilePos = new Vector2(tree.getTileX(), tree.getTileY());
            Float lightLevel = world.getLightLevelAtTile(tilePos);

            Color originalColor = batch.getColor().cpy();
            try {
                if (lightLevel != null && lightLevel > 0) {
                    Color lightColor = new Color(1f, 0.8f, 0.6f, 1f);
                    Color baseColor = world.getCurrentWorldColor().cpy();
                    baseColor.lerp(lightColor, lightLevel * 0.7f);
                    batch.setColor(baseColor);
                }

                int topY = flipY ? totalHeight - topHeight : 0;
                TextureRegion topRegion = new TextureRegion(treeRegion, 0, topY, totalWidth, topHeight);
                if (flipY != topRegion.isFlipY()) {
                    topRegion.flip(false, true);
                }

                float drawWidth = tree.getType() == ObjectType.APRICORN_TREE ?
                    World.TILE_SIZE * 3 : World.TILE_SIZE * 2;
                float drawHeight = World.TILE_SIZE * 2;

                batch.draw(topRegion, renderX, renderY, drawWidth, drawHeight);
            } finally {
                batch.setColor(originalColor);
            }
        }

        public void renderObject(SpriteBatch batch, WorldObject object, World world) {
            // Skip layered objects as they're rendered separately
            if (object.getType().renderLayer == ObjectType.RenderLayer.LAYERED) {
                return;
            }

            TextureRegion objectTexture = object.getTexture();
            if (objectTexture == null) {
                return; // Skip if texture isn't available
            }

            float renderX = object.getPixelX();
            float renderY = object.getPixelY();

            // Get the object's width and height in pixels
            float width = object.getType().widthInTiles * World.TILE_SIZE;
            float height = object.getType().heightInTiles * World.TILE_SIZE;

            // Apply lighting based on world light levels
            Vector2 tilePos = new Vector2(object.getTileX(), object.getTileY());
            Float lightLevel = world.getLightLevelAtTile(tilePos);

            // Save the original batch color
            Color originalColor = batch.getColor().cpy();

            try {
                // Apply lighting if available
                if (lightLevel != null && lightLevel > 0) {
                    Color lightColor = new Color(1f, 0.8f, 0.6f, 1f);
                    Color baseColor = world.getCurrentWorldColor().cpy();
                    baseColor.lerp(lightColor, lightLevel * 0.7f);
                    batch.setColor(baseColor);
                } else {
                    // Ensure the current world color is applied
                    batch.setColor(world.getCurrentWorldColor());
                }

                // Render the object
                batch.draw(objectTexture, renderX, renderY, width, height);
            } finally {
                // Restore the original batch color
                batch.setColor(originalColor);
            }
        }

        public List<WorldObject> getObjectsNearPosition(float x, float y) {
            List<WorldObject> nearbyObjects = new ArrayList<>();
            int searchRadius = 2; // Search in nearby chunks

            int centerChunkX = (int) Math.floor(x / (Chunk.CHUNK_SIZE * World.TILE_SIZE));
            int centerChunkY = (int) Math.floor(y / (Chunk.CHUNK_SIZE * World.TILE_SIZE));

            for (int dx = -searchRadius; dx <= searchRadius; dx++) {
                for (int dy = -searchRadius; dy <= searchRadius; dy++) {
                    Vector2 chunkPos = new Vector2(centerChunkX + dx, centerChunkY + dy);
                    List<WorldObject> chunkObjectList = objectsByChunk.get(chunkPos);

                    if (chunkObjectList != null) {
                        for (WorldObject obj : chunkObjectList) {
                            float distX = Math.abs(obj.getPixelX() - x);
                            float distY = Math.abs(obj.getPixelY() - y);

                            if (distX <= World.TILE_SIZE * 3 && distY <= World.TILE_SIZE * 3) {
                                nearbyObjects.add(obj);
                            }
                        }
                    }
                }
            }

            return nearbyObjects;
        }

        public void updateObject(NetworkProtocol.WorldObjectUpdate update) {
            for (List<WorldObject> objects : objectsByChunk.values()) {
                for (WorldObject obj : objects) {
                    if (obj.getId().equals(update.objectId)) {
                        obj.updateFromData(update.data);
                        return;
                    }
                }
            }
        }

        public List<WorldObject> getObjectsForChunk(Vector2 chunkPos) {
            List<WorldObject> objects = objectsByChunk.get(chunkPos);
            return objects != null ? objects : Collections.emptyList();
        }

        private void handlePokeballSpawning(Vector2 chunkPos, Chunk chunk) {
            // Get or create the chunk's object list
            List<WorldObject> objects = objectsByChunk.computeIfAbsent(chunkPos,
                k -> new CopyOnWriteArrayList<>());

            // Count existing pokeballs in chunk
            long pokeballCount = objects.stream()
                .filter(obj -> obj.getType() == ObjectType.POKEBALL)
                .count();

            // Check if we can spawn a pokeball
            if (pokeballCount < MAX_POKEBALLS_PER_CHUNK && random() < POKEBALL_SPAWN_CHANCE) {
                int attempts = 10;
                while (attempts > 0) {
                    // Get random position within chunk
                    int localX = random.nextInt(Chunk.CHUNK_SIZE);
                    int localY = random.nextInt(Chunk.CHUNK_SIZE);

                    // Convert to world coordinates
                    int worldTileX = (int) (chunkPos.x * Chunk.CHUNK_SIZE + localX);
                    int worldTileY = (int) (chunkPos.y * Chunk.CHUNK_SIZE + localY);

                    // Check if location is valid (grass or sand tiles)
                    int tileType = chunk.getTileType(localX, localY);
                    if (tileType == TileType.GRASS || tileType == TileType.SAND) {
                        // Check area is clear of other objects
                        boolean locationClear = true;
                        for (WorldObject obj : objects) {
                            if (Math.abs(obj.getTileX() - worldTileX) < 2 &&
                                Math.abs(obj.getTileY() - worldTileY) < 2) {
                                locationClear = false;
                                break;
                            }
                        }

                        if (locationClear) {
                            // Create and add pokeball
                            TextureRegion pokeballTexture = objectTextures.get(ObjectType.POKEBALL);
                            if (pokeballTexture != null) {
                                WorldObject pokeball = new WorldObject(
                                    worldTileX, worldTileY,
                                    pokeballTexture, ObjectType.POKEBALL
                                );
                                objects.add(pokeball);

                                // Send network update in multiplayer
                                if (
                                    GameContext.get().getGameClient() != null && GameContext.get().isMultiplayer()) {
                                    NetworkProtocol.WorldObjectUpdate update = new NetworkProtocol.WorldObjectUpdate();
                                    update.objectId = pokeball.getId();
                                    update.type = NetworkProtocol.NetworkObjectUpdateType.ADD;
                                    update.data = pokeball.getSerializableData();


                                    GameContext.get().getGameClient().sendWorldObjectUpdate(update);
                                }

                                GameLogger.info("Spawned pokeball at " + worldTileX + "," + worldTileY);
                                break;
                            }
                        }
                    }
                    attempts--;
                }
            }
        }

        public void update(Map<Vector2, Chunk> loadedChunks) {
            WorldObjectOperation operation;
            pokeballSpawnTimer += Gdx.graphics.getDeltaTime();
            while ((operation = operationQueue.poll()) != null) {
                try {
                    switch (operation.type) {
                        case REMOVE:
                            RemoveOperation removeOp = (RemoveOperation) operation;
                            List<WorldObject> removeList = objectsByChunk.get(removeOp.chunkPos);
                            if (removeList != null) {
                                removeList.removeIf(obj -> obj.getId().equals(removeOp.objectId));
                                objectsByChunk.put(removeOp.chunkPos, new CopyOnWriteArrayList<>(removeList));

                                if (GameContext.get().isMultiplayer()) {
                                    if (
                                        GameContext.get().getGameClient() != null &&
                                            GameContext.get().getGameClient().getCurrentWorld() != null) {
                                        Chunk chunk =
                                            GameContext.get().getGameClient().getCurrentWorld().getChunkAtPosition(
                                                removeOp.chunkPos.x, removeOp.chunkPos.y);
                                        if (chunk != null) {

                                            GameContext.get().getGameClient().getCurrentWorld().saveChunkData(removeOp.chunkPos, chunk);
                                        }
                                    }
                                }
                            }
                            break;

                        case PERSIST:
                            PersistOperation persistOp = (PersistOperation) operation;
                            updateChunkObjectsList(persistOp.chunkPos, persistOp.objects);
                            break;

                        case ADD:
                            AddOperation addOp = (AddOperation) operation;
                            List<WorldObject> addList = objectsByChunk.computeIfAbsent(
                                addOp.chunkPos, k -> new CopyOnWriteArrayList<>());
                            addList.add(addOp.object);
                            break;

                        case UPDATE:
                            handleUpdateOperation((UpdateOperation) operation);
                            break;
                    }
                } catch (Exception e) {
                    GameLogger.error("Error processing operation: " + e.getMessage());
                }
            }

            if (pokeballSpawnTimer >= POKEBALL_SPAWN_INTERVAL) {
                // Update existing chunks
                for (Map.Entry<Vector2, Chunk> entry : loadedChunks.entrySet()) {
                    Vector2 chunkPos = entry.getKey();
                    List<WorldObject> objects = objectsByChunk.computeIfAbsent(chunkPos,
                        k -> new CopyOnWriteArrayList<>());

                    // Remove expired objects
                    boolean changed = objects.removeIf(WorldObject::isExpired);
                    if (changed) {
                        operationQueue.add(new PersistOperation(chunkPos, new ArrayList<>(objects)));
                    }

                    handlePokeballSpawning(chunkPos, entry.getValue());
                }

                pokeballSpawnTimer = 0f;
            }
            // Clean up unloaded chunks
            cleanupUnloadedChunks(loadedChunks);
        }

        private void handleUpdateOperation(UpdateOperation updateOp) {
            try {
                List<WorldObject> updateList = objectsByChunk.get(updateOp.chunkPos);
                if (updateList != null) {
                    for (WorldObject obj : updateList) {
                        if (obj.getId().equals(updateOp.update.objectId)) {
                            obj.updateFromNetwork(updateOp.update);
                            // Queue persist after update
                            operationQueue.add(new PersistOperation(updateOp.chunkPos, new ArrayList<>(updateList)));
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                GameLogger.error("Error handling update operation: " + e.getMessage());
            }
        }

        private void cleanupUnloadedChunks(Map<Vector2, Chunk> loadedChunks) {
            // Identify chunks to remove
            List<Vector2> chunksToRemove = new ArrayList<>();
            for (Vector2 chunkPos : objectsByChunk.keySet()) {
                if (!loadedChunks.containsKey(chunkPos)) {
                    chunksToRemove.add(chunkPos);
                }
            }

            // Remove chunks and persist their final state
            for (Vector2 chunkPos : chunksToRemove) {
                List<WorldObject> objects = objectsByChunk.get(chunkPos);
                if (objects != null) {
                    // Final persist operation before removal
                    operationQueue.add(new PersistOperation(chunkPos, new ArrayList<>(objects)));
                }
                objectsByChunk.remove(chunkPos);
            }
        }

        private void updateChunkObjectsList(Vector2 chunkPos, List<WorldObject> objects) {
            // Update the runtime state
            objectsByChunk.put(chunkPos, new CopyOnWriteArrayList<>(objects));

        }

        private boolean shouldSpawnPokeball(List<WorldObject> chunkObjects) {
            long pokeballCount = chunkObjects.stream()
                .filter(obj -> obj.getType() == ObjectType.POKEBALL)
                .count();
            return pokeballCount < MAX_POKEBALLS_PER_CHUNK &&
                new Random().nextInt(101) < POKEBALL_SPAWN_CHANCE;
        }

        private void spawnPokeball(Vector2 chunkPos, List<WorldObject> objects, Chunk chunk) {
            for (int attempts = 0; attempts < 10; attempts++) {
                int localX = random.nextInt(Chunk.CHUNK_SIZE);
                int localY = random.nextInt(Chunk.CHUNK_SIZE);

                // Only spawn on grass or sand
                int tileType = chunk.getTileType(localX, localY);
                if (tileType == TileType.GRASS || tileType == TileType.SAND) {

                    int worldTileX = (int) (chunkPos.x * Chunk.CHUNK_SIZE) + localX;
                    int worldTileY = (int) (chunkPos.y * Chunk.CHUNK_SIZE) + localY;

                    boolean locationClear = true;
                    for (WorldObject obj : objects) {
                        if (Math.abs(obj.getTileX() - worldTileX) < 2 &&
                            Math.abs(obj.getTileY() - worldTileY) < 2) {
                            locationClear = false;
                            break;
                        }
                    }
                    if (shouldSpawnPokeball(objects)) {
                        if (locationClear) {
                            TextureRegion pokeballTexture = objectTextures.get(ObjectType.POKEBALL);
                            if (pokeballTexture != null) {
                                WorldObject pokeball = new WorldObject(worldTileX, worldTileY,
                                    pokeballTexture, ObjectType.POKEBALL);
                                objects.add(pokeball);

                                if (
                                    GameContext.get().getGameClient() != null && !
                                        GameContext.get().getGameClient().isSinglePlayer()) {
                                    sendObjectSpawn(pokeball);
                                }
                            }
                        }
                    }
                }
            }
        }

        private void sendObjectSpawn(WorldObject object) {
            if (
                GameContext.get().getGameClient() == null || !GameContext.get().isMultiplayer()) return;

            NetworkProtocol.WorldObjectUpdate update = new NetworkProtocol.WorldObjectUpdate();
            update.objectId = object.getId();
            update.type = NetworkProtocol.NetworkObjectUpdateType.ADD;
            update.data = object.getSerializableData();


            GameContext.get().getGameClient().sendWorldObjectUpdate(update);
        }

        public WorldObject createObject(ObjectType type, float x, float y) {
            TextureRegion texture = objectTextures.get(type);
            if (texture == null) {
                throw new IllegalStateException("No texture found for object type: " + type);
            }

            int tileX = (int) (x / World.TILE_SIZE);
            int tileY = (int) (y / World.TILE_SIZE);

            return new WorldObject(tileX, tileY, texture, type);
        }


    }

}
