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
                float offsetX = rand.nextFloat() * 32 - 16;
                float offsetY = rand.nextFloat() * 32 - 16;
                spawnItemEntity(item, x + offsetX, y + offsetY);
            }
        }
    }


    public void removeItemEntity(UUID entityId) {
        ItemEntity entity = itemEntities.get(entityId);
        if (entity != null && !entity.canBePickedUp()) {
            return;
        }
        if (entity != null) {
            entity.markPickedUp();
            itemEntities.remove(entityId);
        }
    }

    public void handleRemoteItemDrop(NetworkProtocol.ItemDrop drop) {
        if (!drop.username.equals(GameContext.get().getGameClient().getLocalUsername())) {
            ItemEntity entity = new ItemEntity(drop.itemData, drop.x, drop.y);
            itemEntities.put(entity.getEntityId(), entity);
        }
    }

    public void handleRemoteItemPickup(NetworkProtocol.ItemPickup pickup) {
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
