package io.github.pokemeetup.multiplayer.server.events.player;

import io.github.pokemeetup.multiplayer.server.events.BaseServerEvent;
import io.github.pokemeetup.system.data.PlayerData;

public class PlayerJoinEvent extends BaseServerEvent {
    private final String username;
    private PlayerData playerData;

    public PlayerJoinEvent(String username, PlayerData playerData) {
        super();
        this.playerData = playerData;
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public PlayerData getPlayerData() {
        return playerData;
    }

    @Override
    public String getEventName() {
        return "PLAYER_JOIN";
    }
}
