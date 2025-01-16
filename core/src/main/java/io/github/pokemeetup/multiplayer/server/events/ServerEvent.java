package io.github.pokemeetup.multiplayer.server.events;

public interface ServerEvent {
    String getEventName();
    long getTimestamp();
}
