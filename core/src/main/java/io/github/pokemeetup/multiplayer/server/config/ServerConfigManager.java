// ServerConfigManager.java
package io.github.pokemeetup.multiplayer.server.config;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.storage.JsonConfig;


public class ServerConfigManager {
    private static final String CONFIG_DIR = "configs";
    private static final String CONFIG_FILE = "servers.json";
    private static ServerConfigManager instance;
    private Array<ServerConnectionConfig> servers;

    public static ServerConnectionConfig getDefaultServerConfig() {
        return new ServerConnectionConfig(
            "localhost",
            54555,
            54556,
            "Default Server",
            true,
            100
        );
    }
    private ServerConfigManager() {
        servers = new Array<>();
        ensureConfigDirectory();
        loadServers();
        if (servers.isEmpty()) {
            addDefaultServer();
            saveServers();
        }
    }

    public static synchronized ServerConfigManager getInstance() {
        if (instance == null) {
            instance = new ServerConfigManager();
        }
        return instance;
    }


    public Array<ServerConnectionConfig> getServers() {
        return servers;
    }

    public void addServer(ServerConnectionConfig config) {
        if (!servers.contains(config, false)) {
            servers.add(config);
            saveServers();
            GameLogger.info("Added server: " + config.getServerName());
        }
    }

    private void ensureConfigDirectory() {
        try {
            FileHandle dir = Gdx.files.local(CONFIG_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
        } catch (Exception e) {
            GameLogger.info("Failed to create config directory: " + e.getMessage());
        }
    }

    private void loadServers() {
        try {
            FileHandle file = Gdx.files.local(CONFIG_DIR + "/" + CONFIG_FILE);
            if (file.exists()) {
                Json json = new Json();
                String fileContent = file.readString();
                GameLogger.info("Loading servers from: " + file.path());
                GameLogger.info("File content: " + fileContent);

                @SuppressWarnings("unchecked")
                Array<ServerConnectionConfig> loadedServers = json.fromJson(Array.class,
                    ServerConnectionConfig.class, fileContent);

                if (loadedServers != null && loadedServers.size > 0) {
                    servers = loadedServers;
                    GameLogger.info("Loaded " + servers.size + " servers");
                }
            }
        } catch (Exception e) {
            GameLogger.info("Error loading servers: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private void addDefaultServer() {
        servers.add(new ServerConnectionConfig(
            "170.64.156.89",
            54555,
            54556,
            "Local Server",
            true,
            100
        ));
    }



    public void removeServer(ServerConnectionConfig server) {
        if (servers.removeValue(server, false)) {
            saveServers();
            GameLogger.info("Removed server: " + server.getServerName());
        }
    }

    private void saveServers() {
        try {
            FileHandle file = Gdx.files.local(CONFIG_DIR + "/" + CONFIG_FILE);

            Json json = JsonConfig.getInstance();
            json.setOutputType(JsonWriter.OutputType.json);

            // Create parent directories if they don't exist
            file.parent().mkdirs();

            String jsonStr = json.prettyPrint(servers);
            file.writeString(jsonStr, false);
            GameLogger.info("Saved " + servers.size + " servers to: " + file.path());
            GameLogger.info("Content: " + jsonStr);
        } catch (Exception e) {
            GameLogger.info("Error saving servers: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
