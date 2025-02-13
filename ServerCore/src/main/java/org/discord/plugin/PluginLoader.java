package org.discord.plugin;

import java.io.File;

public interface PluginLoader {
    boolean canLoad(File file);
    ServerPlugin loadPlugin(File file) throws Exception;
}
