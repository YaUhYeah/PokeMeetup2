package io.github.pokemeetup.multiplayer.server.plugin;

import io.github.pokemeetup.multiplayer.server.plugin.ServerPlugin;

import java.io.File;

public interface PluginLoader {
    boolean canLoad(File file);
    ServerPlugin loadPlugin(File file) throws Exception;
}
