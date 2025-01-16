package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.math.Vector2;

public class RemoveOperation extends WorldObjectOperation {
    public final Vector2 chunkPos;
    public final String objectId;

    public RemoveOperation(Vector2 chunkPos, String objectId) {
        super(OperationType.REMOVE);
        this.chunkPos = chunkPos;
        this.objectId = objectId;
    }
}
