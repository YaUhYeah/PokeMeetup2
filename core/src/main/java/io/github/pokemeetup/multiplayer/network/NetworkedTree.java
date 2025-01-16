package io.github.pokemeetup.multiplayer.network;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

public class NetworkedTree extends NetworkedWorldObject {
    public NetworkedTree() {
        super();
    }

    public NetworkedTree(float x, float y, String textureName) {
        super(x, y, ObjectType.TREE, textureName);
    }

}
