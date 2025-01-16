package io.github.pokemeetup.multiplayer.server.entity;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.multiplayer.ServerPlayer;
import io.github.pokemeetup.multiplayer.server.events.BaseServerEvent;
import io.github.pokemeetup.multiplayer.server.events.EventManager;
import io.github.pokemeetup.system.gameplay.overworld.World;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EntityManager {
    private final Map<UUID, Entity> entities;
    private final EventManager eventManager;
    private final World world;

    public EntityManager(World world, EventManager eventManager) {
        this.entities = new ConcurrentHashMap<>();
        this.eventManager = eventManager;
        this.world = world;
    }

    public Entity spawnEntity(EntityType type, float x, float y) {
        Entity entity = EntityFactory.createEntity(type, x, y);
        entities.put(entity.getId(), entity);
        eventManager.fireEvent(new EntityEvents.EntitySpawnEvent(entity));
        return entity;
    }

    public void removeEntity(UUID entityId) {
        Entity entity = entities.remove(entityId);
        if (entity != null) {
            eventManager.fireEvent(new EntityEvents.EntityRemoveEvent(entity));
        }
    }

    public void updateEntities(float deltaTime) {
        entities.values().forEach(entity -> {
            entity.update(deltaTime);
            handleEntityCollisions(entity);
        });
    }

    private void handleEntityCollisions(Entity entity) {
        // Check collision with world
        if (!world.isPassable((int)entity.getPosition().x, (int)entity.getPosition().y)) {
            entity.handleCollision();
        }

        // Check collisions with other entities
        entities.values().stream()
            .filter(other -> other != entity && entity.getBounds().overlaps(other.getBounds()))
            .forEach(other -> {
                entity.handleCollision(other);
                eventManager.fireEvent(new EntityEvents.EntityCollisionEvent(entity, other));
            });
    }
}

// Example entity implementations

