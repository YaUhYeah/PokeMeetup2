package io.github.pokemeetup.multiplayer.server.plugin;

import java.io.File;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.net.URLClassLoader;
import java.net.URL;
import java.util.List;



public class JarPluginLoader implements PluginLoader {
    @Override
    public boolean canLoad(File file) {
        return file.getName().endsWith(".jar");
    }

    @Override
    public ServerPlugin loadPlugin(File file) throws Exception {
        try (JarFile jarFile = new JarFile(file)) {
            // Get plugin.yml entry
            JarEntry configEntry = jarFile.getJarEntry("plugin.yml");
            if (configEntry == null) {
                throw new Exception("Missing plugin.yml in " + file.getName());
            }

            // Load plugin config
            PluginConfig config = PluginConfig.load(jarFile.getInputStream(configEntry));

            // Create class loader
            URL[] urls = { file.toURI().toURL() };
            try (URLClassLoader classLoader = new URLClassLoader(urls, getClass().getClassLoader())) {
                // Load main class
                Class<?> mainClass = Class.forName(config.getMainClass(), true, classLoader);
                Class<? extends ServerPlugin> pluginClass = mainClass.asSubclass(ServerPlugin.class);

                // Create plugin instance
                ServerPlugin plugin = pluginClass.getDeclaredConstructor().newInstance();
                return plugin;
            }
        }
    }
}
