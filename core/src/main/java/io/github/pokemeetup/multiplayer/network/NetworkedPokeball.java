package io.github.pokemeetup.multiplayer.network;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

public class NetworkedPokeball extends NetworkedWorldObject {
    public NetworkedPokeball() {
        super();
    }

    public NetworkedPokeball(float x, float y, String textureName) {
        super(x, y, ObjectType.POKEBALL, textureName);
    }


}
