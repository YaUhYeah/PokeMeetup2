package io.github.pokemeetup.multiplayer.server;

import io.github.pokemeetup.multiplayer.PlayerManager;
import io.github.pokemeetup.multiplayer.ServerPlayer;
import io.github.pokemeetup.multiplayer.server.events.BaseServerEvent;

public class PlayerEvents {
    public static class PlayerLoginEvent extends BaseServerEvent {
        private final ServerPlayer player;

        public PlayerLoginEvent(ServerPlayer player) {
            this.player = player;
        }

        @Override
        public String getEventName() {
            return "PlayerLogin";
        }

        public ServerPlayer getPlayer() {
            return player;
        }
    }

    public static class PlayerLogoutEvent extends BaseServerEvent {
        private final ServerPlayer player;

        public PlayerLogoutEvent(ServerPlayer player) {
            this.player = player;
        }

        @Override
        public String getEventName() {
            return "PlayerLogout";
        }

        public ServerPlayer getPlayer() {
            return player;
        }
    }
}
