package io.github.pokemeetup.multiplayer.server.events.blocks;

import io.github.pokemeetup.multiplayer.server.events.BaseServerEvent;

public class BlockPlaceEvent extends BaseServerEvent {
    private final String username;
    private final int tileX;
    private final int tileY;
    private final String blockTypeId;


    public BlockPlaceEvent(String username, int tileX, int tileY, String blockTypeId) {
        super();
        this.username = username;
        this.tileX = tileX;
        this.tileY = tileY;
        this.blockTypeId = blockTypeId;
    }

    public String getUsername() {
        return username;
    }

    public int getTileX() {
        return tileX;
    }

    public int getTileY() {
        return tileY;
    }

    public String getBlockTypeId() {
        return blockTypeId;
    }

    @Override
    public String getEventName() {
        return "";
    }
}
