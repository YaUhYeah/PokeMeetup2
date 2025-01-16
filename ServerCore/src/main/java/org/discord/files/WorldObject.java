package org.discord.files;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WorldObject {
    private String id;  // Unique identifier
    private io.github.pokemeetup.system.gameplay.overworld.WorldObject.ObjectType type;
    private int tileX, tileY;
    private boolean isCollidable;

    public WorldObject(int tileX, int tileY, io.github.pokemeetup.system.gameplay.overworld.WorldObject.ObjectType type) {
        this.id = UUID.randomUUID().toString();
        this.tileX = tileX;
        this.tileY = tileY;
        this.type = type;
        this.isCollidable = type.isCollidable;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setType(io.github.pokemeetup.system.gameplay.overworld.WorldObject.ObjectType type) {
        this.type = type;
    }

    public void setTileX(int tileX) {
        this.tileX = tileX;
    }

    public void setTileY(int tileY) {
        this.tileY = tileY;
    }

    public void setCollidable(boolean collidable) {
        isCollidable = collidable;
    }

    public String getId() {
        return id;
    }

    public io.github.pokemeetup.system.gameplay.overworld.WorldObject.ObjectType getType() {
        return type;
    }

    public int getTileX() {
        return tileX;
    }

    public int getTileY() {
        return tileY;
    }

    public boolean isCollidable() {
        return isCollidable;
    }

    // Getters and network serialization methods
    public Map<String, Object> getSerializableData() {
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("tileX", tileX);
        data.put("tileY", tileY);
        data.put("type", type.name());
        return data;
    }
}
