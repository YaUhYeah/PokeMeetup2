package io.github.pokemeetup.multiplayer.server.entity;

public class EntityFactory {
    public static Entity createEntity(EntityType type, float x, float y) {
        switch (type) {
            case POKEBALL:
                return new PokeballEntity(x, y);
//            case ITEM:
//                return new ItemEntity(x, y);
//            case NPC:
//                return new NPCEntity(x, y);
            case CREATURE:
                return new CreatureEntity(x, y);
            default:
                throw new IllegalArgumentException("Unknown entity type: " + type);
        }
    }
}
