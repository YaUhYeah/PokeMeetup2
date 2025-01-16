package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;

public class UpdateOperation extends WorldObjectOperation {
    public Vector2 chunkPos;
    public NetworkProtocol.WorldObjectUpdate update;

    public UpdateOperation(Vector2 chunkPos, NetworkProtocol.WorldObjectUpdate update) {
        super(OperationType.UPDATE);
        this.type = OperationType.UPDATE;
        this.chunkPos = chunkPos;
        this.update = update;
    }
}
