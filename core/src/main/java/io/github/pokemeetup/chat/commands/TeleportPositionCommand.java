package io.github.pokemeetup.chat.commands;

import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.chat.ChatSystem;
import io.github.pokemeetup.chat.Command;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;

public class TeleportPositionCommand implements Command {

    @Override
    public String getName() {
        return "tp";
    }

    @Override
    public String[] getAliases() {
        return new String[0];
    }

    @Override
    public String getDescription() {
        return "Teleports user to specified location.";
    }

    @Override
    public String getUsage() {
        return "tp <x> <y>";
    }

    @Override
    public boolean isMultiplayerOnly() {
        return false;
    }

    @Override
    public void execute(String args, GameClient gameClient, ChatSystem chatSystem) {
        String[] argsArray = args.split(" ");
        try {
            GameLogger.info("Executing tp command...");

            // Get player and world from the global context.
            Player player = GameContext.get().getPlayer();
            if (player == null) {
                chatSystem.addSystemMessage("Error: Player not found");
                return;
            }
            World currentWorld = GameContext.get().getWorld();
            if (currentWorld == null) {
                chatSystem.addSystemMessage("Error: World not found");
                return;
            }
            if (argsArray.length != 2) {
                chatSystem.addSystemMessage("Invalid arguments. Use: " + getUsage());
                return;
            }

            // Parse target tile coordinates.
            int tileX = Integer.parseInt(argsArray[0]);
            int tileY = Integer.parseInt(argsArray[1]);

            // *** NEW CHECK: Ensure the target is within the world border ***
            if (!currentWorld.isWithinWorldBounds(tileX, tileY)) {
                chatSystem.addSystemMessage("Error: Teleport location (" + tileX + ", " + tileY + ") is outside the world border.");
                return;
            }

            float pixelX = tileX * World.TILE_SIZE;
            float pixelY = tileY * World.TILE_SIZE;

            // Teleport the player by updating his position and tile coordinates.
            player.getPosition().set(pixelX, pixelY);
            player.setTileX(tileX);
            player.setTileY(tileY);
            player.setX(pixelX);
            player.setY(pixelY);
            player.setRenderPosition(new Vector2(pixelX, pixelY));
            player.setMoving(false);

            // Reset the chunk state so that a fresh set of chunks are loaded around the new position.
            currentWorld.clearChunks();
            currentWorld.loadChunksAroundPlayer();

            // If in multiplayer mode, update the server.
            if (GameContext.get().isMultiplayer()) {
                gameClient.sendPlayerUpdate();
                gameClient.savePlayerState(player.getPlayerData());
            }
            // Multiplayer Mode: Send position update to server
            if (GameContext.get().isMultiplayer()) {
                NetworkProtocol.PlayerUpdate update = new NetworkProtocol.PlayerUpdate();
                update.username = player.getUsername();
                update.x = pixelX;
                update.y = pixelY;
                update.direction = player.getDirection();
                update.isMoving = false;
                update.timestamp = System.currentTimeMillis();
                gameClient.getClient().sendTCP(update);
                chatSystem.addSystemMessage("Teleported and updated position on server.");
            } else {
                chatSystem.addSystemMessage("Teleported successfully.");
            }

        } catch (Exception e) {
            GameLogger.error("Error executing tp command: " + e.getMessage());
            chatSystem.addSystemMessage("Error: " + e.getMessage());
        }
    }
}
