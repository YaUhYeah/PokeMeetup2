package io.github.pokemeetup.multiplayer.network;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NetworkedWorldObject {
    protected String id;
    protected float x;
    protected float y;
    protected ObjectType type;
    protected boolean isDirty;
    protected Map<String, Object> additionalData;
    protected String textureName;

    public NetworkedWorldObject() {
        this.id = UUID.randomUUID().toString();
    }

    public NetworkedWorldObject(float x, float y, ObjectType type, String textureName) {
        this.id = UUID.randomUUID().toString();
        this.x = x;
        this.y = y;
        this.type = type;
        this.textureName = textureName;
        this.isDirty = true;
        this.additionalData = new HashMap<>();
    }


    public void updateFromNetwork(NetworkProtocol.WorldObjectUpdate update) {
        this.x = update.x;
        this.y = update.y;
        this.textureName = update.textureName; // Update texture identifier
        this.additionalData.clear();
        this.additionalData.putAll(update.data);
        isDirty = false;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    public ObjectType getType() {
        return type;
    }

    public void setType(ObjectType type) {
        this.type = type;
    }

    public boolean isDirty() {
        return isDirty;
    }

    public void setDirty(boolean dirty) {
        isDirty = dirty;
    }

    public Map<String, Object> getAdditionalData() {
        return additionalData;
    }

    public enum ObjectType {
        TREE,
        POKEBALL,
        ITEM,
        NPC
    }
}
