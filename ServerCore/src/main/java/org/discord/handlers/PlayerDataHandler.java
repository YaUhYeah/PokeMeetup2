package org.discord.handlers;

import com.esotericsoftware.kryonet.Connection;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.multiplayer.server.PlayerDataManager;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.utils.GameLogger;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class PlayerDataHandler {
    private static final Logger logger = Logger.getLogger(PlayerDataHandler.class.getName());
    private final PlayerDataManager playerDataManager;
    private final ConcurrentHashMap<UUID, Long> lastSaveTime;
    private static final long SAVE_COOLDOWN = 1000; // 1 second cooldown between saves

    public PlayerDataHandler(PlayerDataManager playerDataManager) {
        this.playerDataManager = playerDataManager;
        this.lastSaveTime = new ConcurrentHashMap<>();
    }

    public void handleSaveRequest(Connection connection, NetworkProtocol.SavePlayerDataRequest request) {
        try {
            if (request.uuid == null || request.playerData == null) {
                sendSaveResponse(connection, request.uuid, false, "Invalid request data");
                return;
            }

            // Check save cooldown
            long currentTime = System.currentTimeMillis();
            Long lastSave = lastSaveTime.get(request.uuid);
            if (lastSave != null && currentTime - lastSave < SAVE_COOLDOWN) {
                sendSaveResponse(connection, request.uuid, false, "Save request too frequent");
                return;
            }

            // Save the player data
            playerDataManager.savePlayerData(request.uuid, request.playerData);
            lastSaveTime.put(request.uuid, currentTime);

            // Send success response
            sendSaveResponse(connection, request.uuid, true, "Player data saved successfully");
            GameLogger.info("Saved player data for UUID: " + request.uuid);

        } catch (Exception e) {
            GameLogger.error("Failed to save player data: " + e.getMessage());
            sendSaveResponse(connection, request.uuid, false, "Server error: " + e.getMessage());
        }
    }

    public void handleGetRequest(Connection connection, NetworkProtocol.GetPlayerDataRequest request) {
        try {
            if (request.uuid == null) {
                sendGetResponse(connection, request.uuid, null, false, "Invalid UUID");
                return;
            }

            // Load the player data
            PlayerData playerData = playerDataManager.loadPlayerData(request.uuid);
            
            if (playerData != null) {
                sendGetResponse(connection, request.uuid, playerData, true, "Player data retrieved successfully");
                GameLogger.info("Retrieved player data for UUID: " + request.uuid);
            } else {
                sendGetResponse(connection, request.uuid, null, false, "Player data not found");
            }

        } catch (Exception e) {
            GameLogger.error("Failed to get player data: " + e.getMessage());
            sendGetResponse(connection, request.uuid, null, false, "Server error: " + e.getMessage());
        }
    }

    private void sendSaveResponse(Connection connection, UUID uuid, boolean success, String message) {
        NetworkProtocol.SavePlayerDataResponse response = new NetworkProtocol.SavePlayerDataResponse();
        response.uuid = uuid;
        response.success = success;
        response.message = message;
        response.timestamp = System.currentTimeMillis();
        connection.sendTCP(response);
    }

    private void sendGetResponse(Connection connection, UUID uuid, PlayerData playerData, boolean success, String message) {
        NetworkProtocol.GetPlayerDataResponse response = new NetworkProtocol.GetPlayerDataResponse();
        response.uuid = uuid;
        response.playerData = playerData;
        response.success = success;
        response.message = message;
        response.timestamp = System.currentTimeMillis();
        connection.sendTCP(response);
    }
}