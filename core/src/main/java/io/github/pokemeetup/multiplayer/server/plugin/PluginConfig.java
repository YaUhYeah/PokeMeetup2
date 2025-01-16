package io.github.pokemeetup.multiplayer.server.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class PluginConfig {
    private String name;
    private String version;
    private String mainClass;
    private List<String> dependencies;


    public static PluginConfig load(InputStream input) {
        try {
            // Read the content as JSON
            StringBuilder content = new StringBuilder();
            try (Scanner scanner = new Scanner(input)) {
                while (scanner.hasNextLine()) {
                    content.append(scanner.nextLine()).append("\n");
                }
            }

            // Parse JSON to PluginConfig
            Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();

            PluginConfig config = gson.fromJson(content.toString(), PluginConfig.class);

            // Validate required fields
            if (config.name == null || config.name.isEmpty()) {
                throw new IllegalArgumentException("Plugin name is required");
            }
            if (config.mainClass == null || config.mainClass.isEmpty()) {
                throw new IllegalArgumentException("Main class is required");
            }
            if (config.version == null || config.version.isEmpty()) {
                config.version = "1.0.0";
            }
            if (config.dependencies == null) {
                config.dependencies = new ArrayList<>();
            }

            return config;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load plugin config: " + e.getMessage(), e);
        }
    }


    // Getters
    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getMainClass() { return mainClass; }
    public List<String> getDependencies() { return dependencies; }
}
