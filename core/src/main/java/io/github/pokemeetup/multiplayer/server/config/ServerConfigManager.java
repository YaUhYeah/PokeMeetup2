package io.github.pokemeetup.multiplayer.server.config;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.storage.JsonConfig;

import java.util.HashSet;

public class ServerConfigManager {
    public static final String CONFIG_DIR = "configs";
    public static final String CONFIG_FILE = "servers.json";
    private static ServerConfigManager instance;
    private Array<ServerConnectionConfig> servers;

    public static ServerConnectionConfig getDefaultServerConfig() {
        return new ServerConnectionConfig(
            "localhost",
            54555,
            54556,
            "Default Server",
            100
        );
    }

    private ServerConfigManager() {
        servers = new Array<>();
        ensureConfigDirectory();
        loadServers();
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
        } else {
            GameLogger.info("Attempted to add a duplicate server, ignoring: " + config.getServerName());
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
            if (file.exists() && file.length() > 0) {
                Json json = new Json();
                String fileContent = file.readString();
                GameLogger.info("Loading servers from: " + file.path());

                @SuppressWarnings("unchecked")
                Array<ServerConnectionConfig> loadedServers = json.fromJson(Array.class,
                    ServerConnectionConfig.class, fileContent);

                if (loadedServers != null && loadedServers.size > 0) {
                    HashSet<ServerConnectionConfig> uniqueSet = new HashSet<>();
                    for (ServerConnectionConfig server : loadedServers) {
                        if (!uniqueSet.add(server)) {
                            GameLogger.info("Removed duplicate server on load: " + server.getServerName());
                        }
                    }
                    boolean wasCleaned = uniqueSet.size() < loadedServers.size;
                    servers.clear();
                    for(ServerConnectionConfig uniqueServer : uniqueSet) {
                        servers.add(uniqueServer);
                    }

                    GameLogger.info("Loaded " + servers.size + " unique servers.");
                    if(wasCleaned) {
                        saveServers();
                    }
                }
            }
        } catch (Exception e) {
            GameLogger.info("Error loading servers: " + e.getMessage());
            e.printStackTrace();
        }
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
            file.parent().mkdirs();
            String jsonStr = json.prettyPrint(servers);
            file.writeString(jsonStr, false);
            GameLogger.info("Saved " + servers.size + " servers to: " + file.path());
        } catch (Exception e) {
            GameLogger.info("Error saving servers: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
