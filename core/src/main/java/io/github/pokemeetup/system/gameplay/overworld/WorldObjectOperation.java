package io.github.pokemeetup.system.gameplay.overworld;

public class WorldObjectOperation {
    public enum OperationType {
        ADD,
        REMOVE,
        UPDATE,
        PERSIST
    }

    public OperationType type;

    public WorldObjectOperation(OperationType type) {
        this.type = type;
    }
}
