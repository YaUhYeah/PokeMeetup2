package io.github.pokemeetup.multiplayer.server.events;

public interface EventListener<T extends ServerEvent> {
    void onEvent(T event);
}
