package org.discord.plugin;


import io.github.pokemeetup.system.servers.PluginContext;

public interface ServerPlugin {
    String getId();

    void onLoad(PluginContext manager);

    void onEnable();

    void onDisable();
}

