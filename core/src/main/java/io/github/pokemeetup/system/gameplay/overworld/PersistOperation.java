package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.math.Vector2;

import java.util.ArrayList;
import java.util.List;

public class PersistOperation extends WorldObjectOperation {
    public final Vector2 chunkPos;
    public final List<WorldObject> objects;

    public PersistOperation(Vector2 chunkPos, List<WorldObject> objects) {
        super(WorldObjectOperation.OperationType.PERSIST);
        this.chunkPos = chunkPos;
        this.objects = new ArrayList<>(objects);
    }
}
