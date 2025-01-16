package io.github.pokemeetup.chat;

import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.system.Player;

public interface Command {
    String getName();
    String[] getAliases();
    String getDescription();
    String getUsage();
    boolean isMultiplayerOnly();
    void execute(String args, GameClient player, ChatSystem chatSystem);
}
