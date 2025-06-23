package io.github.pokemeetup.blocks;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.system.data.ChestData;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;

public class PlaceableBlock {
    private static final float CHEST_ANIMATION_DURATION = 0.3f;
    private final String id;
    private final Vector2 position;
    private final BlockType type;
    private transient TextureRegion texture;
    private boolean isChestOpen = false;
    private float animationTime = 0;
    private ChestData chestData;
    private boolean isFlipped = false;

    public PlaceableBlock(BlockType type, Vector2 position, TextureRegion texture, boolean isFlipped) {
        this.type = type;
        this.id = type.id;
        this.isFlipped = isFlipped;
        this.position = position;
        this.texture = texture;
        if (type == BlockType.CHEST) {
            this.chestData = new ChestData((int) position.x, (int) position.y);
        }
    }

    public PlaceableBlock(BlockType type, Vector2 position) {
        this.type = type;
        this.position = position;
        this.texture = null;
        this.id = type.id;
    }

    public boolean isChestOpen() {
        return isChestOpen;
    }

    public void setChestOpen(boolean isOpen) {
        this.isChestOpen = isOpen;
    }

    public boolean isFlipped() {
        return isFlipped;
    }

    public void toggleFlip() {
        if (type.isFlippable) {
            isFlipped = !isFlipped;
        }
    }


    public void render(SpriteBatch batch, float x, float y) {
        if (type == BlockType.CHEST) {
            TextureRegion chestTexture = isChestOpen ?
                TextureManager.blocks.findRegion("chest", 1) :
                TextureManager.blocks.findRegion("chest", 0);
            float width = chestTexture.getRegionWidth();
            float height = chestTexture.getRegionHeight();

            if (isFlipped) {
                batch.draw(chestTexture,
                    x + width, y,        // Position (x + width for flip)
                    -width, height);     // Negative width for horizontal flip
            } else {
                batch.draw(chestTexture, x, y);
            }
        } else if (texture != null) {
            float width = texture.getRegionWidth();
            float height = texture.getRegionHeight();

            if (isFlipped) {
                batch.draw(texture,
                    x + width, y,        // Position (x + width for flip)
                    -width, height);
            } else {
                batch.draw(texture, x, y);
            }
        }
    }

    public ChestData getChestData() {
        return chestData;
    }

    public void setChestData(ChestData chestData) {
        this.chestData = chestData;
    }

    public void update(float delta) {
        if (type == BlockType.CHEST) {
            if (isChestOpen) {
                animationTime = Math.min(animationTime + delta, CHEST_ANIMATION_DURATION);
            } else {
                animationTime = Math.max(animationTime - delta, 0);
            }
        }
    }

    public BlockType getType() {
        return type;
    }

    public Vector2 getPosition() {
        return position;
    }

    public TextureRegion getTexture() {
        return texture;
    }

    public void setTexture(TextureRegion texture) {
        this.texture = texture;
    }

    public String getId() {
        return id;
    }


    public enum BlockType {
        CRAFTINGTABLE("craftingtable", true, true, 4.0f, ItemManager.ItemIDs.CRAFTING_TABLE, false),
        WOODEN_PLANKS("wooden_planks", true, true, 3.0f, ItemManager.ItemIDs.WOODEN_PLANKS, true),
        HOUSE_PLANKS("house_planks", true, true, 3.0f, ItemManager.ItemIDs.HOUSE_PLANKS, true),
        WOODEN_DOOR("wooden_door", true, true, 3.0f, ItemManager.ItemIDs.WOODEN_DOOR, true),
        CHEST("chest", true, true, 4.0f, ItemManager.ItemIDs.CHEST, false),
        FURNACE("furnace", true, true, 8.0f, ItemManager.ItemIDs.FURNACE, false),
        ROOF_MIDDLE("roof_middle", true, true, 6.0f, ItemManager.ItemIDs.ROOF_MIDDLE, true),
        ROOF_CORNER("roof_corner", true, true, 3.0f, ItemManager.ItemIDs.ROOF_CORNER, true),
        HOUSE_PART("house_part", true, true, 3.0f, ItemManager.ItemIDs.HOUSE_PART, true),
        ROOF_INSIDE("roof_inside", true, true, 3.0f, ItemManager.ItemIDs.ROOF_INSIDE, true),
        HOUSE_MIDDLE_PART("house_midsection_part", true, true, 4.0f, ItemManager.ItemIDs.HOUSE_MIDDLE_PART, true),
        HOUSE_MIDDLE_PART_0("house_middlesection", true, true, 4.0f, ItemManager.ItemIDs.HOUSE_MIDDLE_PART_0, true),
        HOUSE_MIDDLE_PART_1("house_middlesection_part", true, true, 4.0f, ItemManager.ItemIDs.HOUSE_MIDDLE_PART_1, true),
        ROOF_CORNER_1("roof_middle_part", true, true, 4.0f, ItemManager.ItemIDs.ROOF_CORNER_1, true),
        ROOF_MIDDLE_OUTSIDE("roof_middle_outside", true, true, 4.0f, ItemManager.ItemIDs.ROOF_CORNER_1, true),
        ROOFINNER("roofinner", true, true, 3.0f, ItemManager.ItemIDs.ROOFINNER, true),
        ROOF_MIDDLE_OUTERSIDE("roof_middle_outerside", true, true, 4.0f, ItemManager.ItemIDs.ROOF_CORNER_1, true),
        ROOF_MIDDLE_OUTER("roof_middle_outer", true, true, 4.0f, ItemManager.ItemIDs.ROOF_CORNER_1, true);

        public final String id;
        public final boolean interactive;
        public final boolean isFlippable;
        public final boolean hasCollision;
        public final float breakTime;
        public final String itemId;

        BlockType(String id, boolean interactive, boolean hasCollision, float breakTime, String itemId, boolean isFlippable) {
            this.id = id;
            this.isFlippable = isFlippable;
            this.interactive = interactive;
            this.hasCollision = hasCollision;
            this.breakTime = breakTime;
            this.itemId = itemId;

        }

        public static BlockType fromId(String id) {
            for (BlockType type : values()) {
                if (type.id.equalsIgnoreCase(id)) {
                    return type;
                }
            }
            return null;
        }


        public static BlockType fromItemId(String itemId) {
            if (itemId == null) return null;
            try {
                return valueOf(itemId.toUpperCase());
            } catch (IllegalArgumentException e) {
                for (BlockType type : values()) {
                    if (type.itemId.equalsIgnoreCase(itemId) ||
                        type.id.equalsIgnoreCase(itemId.replace("_item", ""))) {
                        return type;
                    }
                }
                GameLogger.error("No matching block type for item: " + itemId);
                return null;
            }
        }

        public float getBreakTime(boolean hasAxe) {
            return hasAxe ? breakTime * 0.5f : breakTime * 1.5f;
        }

        public String getId() {
            return id;
        }
    }

}
