package io.github.pokemeetup.multiplayer.server.config;

import java.io.IOException;
import java.util.Objects;

public class ServerConnectionConfig {
    private static ServerConnectionConfig instance;
    private String serverIP;
    private int tcpPort;
    private int maxPlayers;
    private String iconPath;
    private String motd;
    private String version;
    private String dataDirectory;
    private int udpPort;
    private String serverName;

    public ServerConnectionConfig(String serverIP, int tcpPort, int udpPort, String serverName, int maxPlayers) {
        this.serverIP = serverIP;
        this.tcpPort = tcpPort;
        this.udpPort = udpPort;
        this.maxPlayers = maxPlayers;
        this.serverName = serverName;
        setDataDirectory("worlds");
    }

    public ServerConnectionConfig() {
        setDataDirectory("worlds");
    }

    public static ServerConnectionConfig getInstance() {
        if (instance == null) {
            synchronized (ServerConnectionConfig.class) {
                if (instance == null) {
                    instance = new ServerConnectionConfig(
                        "170.64.156.89",
                        54555,
                        54556,
                        "Default Server",
                        100
                    );
                }
            }
        }
        return instance;
    }

    private static void validateServerConnection(ServerConnectionConfig config) throws IOException {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(config.getServerIP(), config.getTcpPort()), 2000);
        } catch (IOException e) {
            throw new IOException("Cannot connect to server at " +
                config.getServerIP() + ":" + config.getTcpPort() +
                " - " + e.getMessage());
        }
    }

    public void validate() {
        if (serverIP == null || serverIP.isEmpty()) {
            throw new IllegalArgumentException("Server IP cannot be empty");
        }
        if (tcpPort <= 0 || tcpPort > 65535) {
            throw new IllegalArgumentException("Invalid TCP port: " + tcpPort);
        }
        if (udpPort <= 0 || udpPort > 65535) {
            throw new IllegalArgumentException("Invalid UDP port: " + udpPort);
        }
        if (serverName == null || serverName.isEmpty()) {
            throw new IllegalArgumentException("Server name cannot be empty");
        }
        if (maxPlayers <= 0) {
            throw new IllegalArgumentException("Max players must be greater than 0");
        }
    }

    public static synchronized void setInstance(ServerConnectionConfig config) {
        instance = config;
    }

    public static ServerConnectionConfig getDefault() {
        return new ServerConnectionConfig("localhost", 55555, 55556, "Local Server", 100);
    }

    // Getters and Setters
    public String getIconPath() { return iconPath; }
    public void setIconPath(String iconPath) { this.iconPath = iconPath; }
    public String getMotd() { return motd; }
    public void setMotd(String motd) { this.motd = motd; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }
    public String getDataDirectory() { return dataDirectory; }
    public void setDataDirectory(String dataDirectory) { this.dataDirectory = dataDirectory; }
    public String getServerIP() { return serverIP; }
    public void setServerIP(String serverIP) { this.serverIP = serverIP; }
    public int getTcpPort() { return tcpPort; }
    public void setTcpPort(int tcpPort) { this.tcpPort = tcpPort; }
    public int getUdpPort() { return udpPort; }
    public void setUdpPort(int udpPort) { this.udpPort = udpPort; }
    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }

    @Override
    public String toString() {
        return serverName + " (" + serverIP + ":" + tcpPort + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServerConnectionConfig that = (ServerConnectionConfig) o;
        return tcpPort == that.tcpPort && Objects.equals(serverIP, that.serverIP);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serverIP, tcpPort);
    }
}
