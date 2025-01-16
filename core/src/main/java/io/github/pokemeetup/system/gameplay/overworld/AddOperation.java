package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.math.Vector2;

public class AddOperation extends WorldObjectOperation {
    public Vector2 chunkPos;
    public WorldObject object;

    public AddOperation(Vector2 chunkPos, WorldObject object) {
        super(OperationType.ADD);
        this.type = OperationType.ADD;
        this.chunkPos = chunkPos;
        this.object = object;
    }
}
