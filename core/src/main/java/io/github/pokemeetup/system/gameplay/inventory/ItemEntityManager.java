package io.github.pokemeetup.system.gameplay.inventory;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.system.data.ChestData;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.utils.GameLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ItemEntityManager {
    private final Map<UUID, ItemEntity> itemEntities = new ConcurrentHashMap<>();

    public void update(float delta) {
        Iterator<Map.Entry<UUID, ItemEntity>> it = itemEntities.entrySet().iterator();
        while (it.hasNext()) {
            ItemEntity entity = it.next().getValue();
            entity.update(delta);
            if (entity.shouldDespawn()) {
                // Mark as picked up so we don’t send duplicate messages.
                entity.markPickedUp();
                it.remove();
                if (GameContext.get().isMultiplayer()) {
                    NetworkProtocol.ItemPickup pickup = new NetworkProtocol.ItemPickup();
                    pickup.entityId = entity.getEntityId();
                    GameContext.get().getGameClient().sendItemPickup(pickup);
                }
            }
        }
    }

    public ItemEntity getItemEntity(UUID entityId) {
        return itemEntities.get(entityId);
    }

    public void render(SpriteBatch batch) {
        for (ItemEntity entity : itemEntities.values()) {
            entity.render(batch);
        }
    }

    public void spawnItemEntity(ItemData itemData, float x, float y) {
        ItemEntity entity = new ItemEntity(itemData, x, y);
        itemEntities.put(entity.getEntityId(), entity);

        if (GameContext.get().isMultiplayer()) {
            NetworkProtocol.ItemDrop drop = new NetworkProtocol.ItemDrop();
            drop.itemData = itemData;
            drop.x = x;
            drop.y = y;
            GameContext.get().getGameClient().sendItemDrop(itemData, new Vector2(x, y));
        }
    }

    public void spawnItemsFromChest(ChestData chest, float x, float y) {
        if (chest == null || chest.items == null) return;

        Random rand = new Random();
        for (ItemData item : chest.items) {
            if (item != null) {
                // Scatter items around the chest position
                float offsetX = rand.nextFloat() * 32 - 16;
                float offsetY = rand.nextFloat() * 32 - 16;
                spawnItemEntity(item, x + offsetX, y + offsetY);
            }
        }
    }


    public void removeItemEntity(UUID entityId) {
        ItemEntity entity = itemEntities.get(entityId);
        if (entity != null && !entity.canBePickedUp()) {
            // The item is either not ready or already picked up.
            return;
        }
        if (entity != null) {
            // Mark it so that any further pickup requests are ignored.
            entity.markPickedUp();
            // Remove it from the manager.
            itemEntities.remove(entityId);

            // FIX: Removed the redundant network call from here.
            // The Player class is now solely responsible for initiating the pickup message.
        }
    }

    public void handleRemoteItemDrop(NetworkProtocol.ItemDrop drop) {
        // Only handle drops from other players
        if (!drop.username.equals(GameContext.get().getGameClient().getLocalUsername())) {
            ItemEntity entity = new ItemEntity(drop.itemData, drop.x, drop.y);
            itemEntities.put(entity.getEntityId(), entity);
        }
    }

    public void handleRemoteItemPickup(NetworkProtocol.ItemPickup pickup) {
        // Only handle pickups from other players
        if (!pickup.username.equals(GameContext.get().getGameClient().getLocalUsername())) {
            itemEntities.remove(pickup.entityId);
        }
    }

    public void spawnItemEntityFromNetwork(ItemData itemData, float x, float y) {
        ItemEntity entity = new ItemEntity(itemData, x, y);
        itemEntities.put(entity.getEntityId(), entity);
    }
    public ItemEntity getClosestPickableItem(float x, float y, float range) {
        ItemEntity closest = null;
        float closestDist = range;

        for (ItemEntity entity : itemEntities.values()) {
            if (!entity.canBePickedUp()) continue;

            float dist = Vector2.dst(x, y,
                entity.getPosition().x,
                entity.getPosition().y);

            if (dist < closestDist) {
                closestDist = dist;
                closest = entity;
            }
        }

        return closest;
    }

    public Collection<ItemEntity> getAllItems() {
        return itemEntities.values();
    }
}
