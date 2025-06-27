package org.discord.plugin;

import java.io.File;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.net.URLClassLoader;
import java.net.URL;


public class JarPluginLoader implements PluginLoader {
    @Override
    public boolean canLoad(File file) {
        return file.getName().endsWith(".jar");
    }

    @Override
    public ServerPlugin loadPlugin(File file) throws Exception {
        try (JarFile jarFile = new JarFile(file)) {
            JarEntry configEntry = jarFile.getJarEntry("plugin.yml");
            if (configEntry == null) {
                throw new Exception("Missing plugin.yml in " + file.getName());
            }
            PluginConfig config = PluginConfig.load(jarFile.getInputStream(configEntry));
            URL[] urls = { file.toURI().toURL() };
            try (URLClassLoader classLoader = new URLClassLoader(urls, getClass().getClassLoader())) {
                Class<?> mainClass = Class.forName(config.getMainClass(), true, classLoader);
                Class<? extends ServerPlugin> pluginClass = mainClass.asSubclass(ServerPlugin.class);
                ServerPlugin plugin = pluginClass.getDeclaredConstructor().newInstance();
                return plugin;
            }
        }
    }
}
