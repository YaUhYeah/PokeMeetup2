package io.github.pokemeetup.system.gameplay.inventory;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.textures.TextureManager;

import java.util.UUID;

public class ItemEntity {
    private static final float ITEM_SIZE = 24f; // Size of item in the world
    private static final float PICKUP_DELAY = 0.5f; // Delay before item can be picked up
    private static final float DESPAWN_TIME = 300f; // 5 minutes before despawning

    private final UUID entityId;
    private final ItemData itemData;
    private final Vector2 position;
    private final Rectangle bounds;
    private TextureRegion texture;
    private float timeAlive;
    private float pickupDelay;
    private boolean canBePickedUp;
    private boolean pickedUp;

    public ItemEntity(ItemData itemData, float x, float y) {
        this(itemData, x, y, UUID.randomUUID());
    }

    public ItemEntity(ItemData itemData, float x, float y, UUID entityId) {
        this.entityId = entityId;
        this.itemData = itemData;
        this.position = new Vector2(x, y);
        this.bounds = new Rectangle(x - ITEM_SIZE/2, y - ITEM_SIZE/2, ITEM_SIZE, ITEM_SIZE);
        this.pickupDelay = PICKUP_DELAY;
        this.canBePickedUp = false;
        this.pickedUp = false;  // Initially not picked up.

        // Only load textures if TextureManager is available (client-side only)
        if (TextureManager.items != null) {
            String textureKey = itemData.getItemId().toLowerCase() + "_item";
            this.texture = TextureManager.items.findRegion(textureKey);
            if (this.texture == null) {
                this.texture = TextureManager.items.findRegion(itemData.getItemId().toLowerCase());
            }
        } else {
            // Server-side: no textures needed
            this.texture = null;
        }
    }

    public void update(float delta) {
        timeAlive += delta;
        if (pickupDelay > 0) {
            pickupDelay -= delta;
            if (pickupDelay <= 0) {
                canBePickedUp = true;
            }
        }
    }

    public void render(SpriteBatch batch, World world) {
        if (texture == null) return;

        // Save original batch color components (not reference, to avoid flickering)
        Color batchColor = batch.getColor();
        float originalR = batchColor.r;
        float originalG = batchColor.g;
        float originalB = batchColor.b;
        float originalA = batchColor.a;

        // Apply per-tile lighting like BiomeRenderer does
        if (world != null) {
            // Calculate which tile the item is on
            int tileX = (int) Math.floor(position.x / World.TILE_SIZE);
            int tileY = (int) Math.floor(position.y / World.TILE_SIZE);

            // Get the chunk at this tile position
            Chunk chunk = world.getChunkAtTile(tileX, tileY);
            if (chunk != null) {
                // Get local tile coordinates within the chunk
                int chunkX = Math.floorDiv(tileX, Chunk.CHUNK_SIZE);
                int chunkY = Math.floorDiv(tileY, Chunk.CHUNK_SIZE);
                int localTileX = tileX - (chunkX * Chunk.CHUNK_SIZE);
                int localTileY = tileY - (chunkY * Chunk.CHUNK_SIZE);

                // Get light map and apply lighting if available
                Color[][] lightMap = chunk.getLightMap();
                if (lightMap != null && localTileX >= 0 && localTileX < Chunk.CHUNK_SIZE
                    && localTileY >= 0 && localTileY < Chunk.CHUNK_SIZE
                    && lightMap[localTileX] != null && lightMap[localTileX][localTileY] != null) {
                    batch.setColor(lightMap[localTileX][localTileY]);
                }
            }
        }

        // Draw the item
        batch.draw(texture,
            position.x - ITEM_SIZE/2,
            position.y - ITEM_SIZE/2,
            ITEM_SIZE,
            ITEM_SIZE);

        // Restore original color using saved components
        batch.setColor(originalR, originalG, originalB, originalA);
    }

    public boolean canBePickedUp() {
        return canBePickedUp && !pickedUp;
    }

    public boolean shouldDespawn() {
        return timeAlive >= DESPAWN_TIME;
    }

    public void markPickedUp() {
        pickedUp = true;
    }
    public UUID getEntityId() { return entityId; }
    public ItemData getItemData() { return itemData; }
    public Vector2 getPosition() { return position; }
    public Rectangle getBounds() { return bounds; }
}
