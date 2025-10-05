package io.github.pokemeetup.system.gameplay.inventory;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.system.data.ChestData;
import io.github.pokemeetup.system.data.ItemData;

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

    public ItemEntity spawnItemEntity(ItemData itemData, float x, float y) {
        ItemEntity entity = new ItemEntity(itemData, x, y);
        itemEntities.put(entity.getEntityId(), entity);
        return entity;
    }
    public ItemEntity removeItemEntity(UUID entityId) {
        ItemEntity entity = itemEntities.remove(entityId);
        if (entity != null) {
            entity.markPickedUp();
        }
        return entity;
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

    public void spawnItemEntityFromNetwork(ItemData itemData, float x, float y, UUID entityId) {
        ItemEntity entity = new ItemEntity(itemData, x, y, entityId);
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
