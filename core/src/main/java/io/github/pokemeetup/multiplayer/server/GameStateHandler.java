package io.github.pokemeetup.multiplayer.server;

import com.badlogic.gdx.Screen;

public interface GameStateHandler {
    void returnToLogin(String message);
    boolean isMultiplayerMode();
    void setScreen(Screen screen);
}
