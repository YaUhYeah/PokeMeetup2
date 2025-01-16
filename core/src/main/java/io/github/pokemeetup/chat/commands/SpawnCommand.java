package io.github.pokemeetup.chat.commands;

import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.chat.ChatSystem;
import io.github.pokemeetup.chat.Command;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;

public class SpawnCommand implements Command {

    @Override
    public String getName() { return "spawn"; }

    @Override
    public String[] getAliases() { return new String[0]; }

    @Override
    public String getDescription() { return "teleports player to spawn"; }

    @Override
    public String getUsage() { return "/spawn"; }

    @Override
    public boolean isMultiplayerOnly() { return false; }

    @Override
    public void execute(String args, GameClient gameClient, ChatSystem chatSystem) {
        try {
            GameLogger.info("Executing spawn command...");

            Player player = gameClient.getActivePlayer();
            if (player == null) {
                chatSystem.addSystemMessage("Error: Player not found");
                return;
            }

            World currentWorld = player.getWorld();
            if (currentWorld == null) {
                currentWorld = gameClient.getCurrentWorld();
            }

            if (currentWorld == null) {
                chatSystem.addSystemMessage("Error: World not found");
                return;
            }

            if (currentWorld.getWorldData() == null || currentWorld.getWorldData().getConfig() == null) {
                chatSystem.addSystemMessage("Error: World configuration not available");
                return;
            }

            int tileX = currentWorld.getWorldData().getConfig().getTileSpawnX();
            int tileY = currentWorld.getWorldData().getConfig().getTileSpawnY();

            float pixelX = tileX * World.TILE_SIZE;
            float pixelY = tileY * World.TILE_SIZE;

            player.getPosition().set(pixelX, pixelY);

            player.setMoving(false);

            player.setTileX(tileX);
            player.setTileY(tileY);
            player.setX(pixelX);
            player.setY(pixelY);

            player.setDirection(player.getDirection());
            player.setRenderPosition(new Vector2(pixelX, pixelY));
            player.setMoving(false);

            player.validateResources();
            currentWorld.setPlayer(player);

            if (!gameClient.isSinglePlayer()) {
                gameClient.sendPlayerUpdate();
                gameClient.savePlayerState(player.getPlayerData());
            }
            chatSystem.addSystemMessage("Teleported to spawn point! (" + tileX + ", " + tileY + ")");
            GameLogger.info("Player successfully teleported to spawn: " + pixelX + "," + pixelY);

        } catch (Exception e) {
            GameLogger.error("Spawn command failed: " + e.getMessage());
            chatSystem.addSystemMessage("Error executing spawn command: " + e.getMessage());
        }
    }
}
