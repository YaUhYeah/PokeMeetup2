package io.github.pokemeetup.system.gameplay.inventory;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.system.data.ItemData;
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
    // NEW: flag to ensure we only pick up once.
    private boolean pickedUp;

    public ItemEntity(ItemData itemData, float x, float y) {
        this.entityId = UUID.randomUUID();
        this.itemData = itemData;
        this.position = new Vector2(x, y);
        this.bounds = new Rectangle(x - ITEM_SIZE/2, y - ITEM_SIZE/2, ITEM_SIZE, ITEM_SIZE);
        this.pickupDelay = PICKUP_DELAY;
        this.canBePickedUp = false;
        this.pickedUp = false;  // Initially not picked up.

        // Load texture
        String textureKey = itemData.getItemId().toLowerCase() + "_item";
        this.texture = TextureManager.items.findRegion(textureKey);
        if (this.texture == null) {
            this.texture = TextureManager.items.findRegion(itemData.getItemId().toLowerCase());
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

    public void render(SpriteBatch batch) {
        if (texture != null) {
            batch.draw(texture,
                position.x - ITEM_SIZE/2,
                position.y - ITEM_SIZE/2,
                ITEM_SIZE,
                ITEM_SIZE);
        }
    }

    public boolean canBePickedUp() {
        return canBePickedUp && !pickedUp;
    }

    public boolean shouldDespawn() {
        return timeAlive >= DESPAWN_TIME;
    }

    // NEW: mark the item as picked up
    public void markPickedUp() {
        pickedUp = true;
    }

    // Getters
    public UUID getEntityId() { return entityId; }
    public ItemData getItemData() { return itemData; }
    public Vector2 getPosition() { return position; }
    public Rectangle getBounds() { return bounds; }
}
