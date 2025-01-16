package io.github.pokemeetup.multiplayer.server.entity;

import io.github.pokemeetup.multiplayer.server.events.BaseServerEvent;

// Event classes
public class EntityEvents {
    public static class EntitySpawnEvent extends BaseServerEvent {
        private final Entity entity;

        public EntitySpawnEvent(Entity entity) {
            this.entity = entity;
        }

        @Override
        public String getEventName() {
            return "EntitySpawn";
        }

        public Entity getEntity() {
            return entity;
        }
    }

    public static class EntityRemoveEvent extends BaseServerEvent {
        private final Entity entity;

        public EntityRemoveEvent(Entity entity) {
            this.entity = entity;
        }

        @Override
        public String getEventName() {
            return "EntityRemove";
        }

        public Entity getEntity() {
            return entity;
        }
    }

    public static class EntityCollisionEvent extends BaseServerEvent {
        private final Entity entity1;
        private final Entity entity2;

        public EntityCollisionEvent(Entity entity1, Entity entity2) {
            this.entity1 = entity1;
            this.entity2 = entity2;
        }

        @Override
        public String getEventName() {
            return "EntityCollision";
        }

        public Entity getEntity1() { return entity1; }
        public Entity getEntity2() { return entity2; }
    }
}
