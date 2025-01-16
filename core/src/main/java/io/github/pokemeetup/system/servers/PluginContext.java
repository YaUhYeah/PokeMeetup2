package io.github.pokemeetup.system.servers;

import io.github.pokemeetup.system.data.WorldData;

import java.util.Map;

public class PluginContext {
    private WorldData world;
    private Map<String, Object> config;

    public PluginContext(WorldData world, Map<String, Object> config) {
        this.world = world;
        this.config = config;
    }

    public WorldData getWorld() { return world; }
    public Map<String, Object> getConfig() { return config; }
}
