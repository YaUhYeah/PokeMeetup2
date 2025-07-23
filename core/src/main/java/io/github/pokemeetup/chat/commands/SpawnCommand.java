package io.github.pokemeetup.chat.commands;

import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.chat.ChatSystem;
import io.github.pokemeetup.chat.Command;
import io.github.pokemeetup.context.GameContext;
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
    public String getDescription() { return "Teleports the player to spawn."; }

    @Override
    public String getUsage() { return "/spawn"; }

    @Override
    public boolean isMultiplayerOnly() { return false; }

    // --- FIX: Logic to handle teleportation without blocking the main thread.
    @Override
    public void execute(String args, GameClient gameClient, ChatSystem chatSystem) {
        try {
            GameLogger.info("Executing spawn command...");
            Player player = GameContext.get().getPlayer();
            if (player == null) {
                chatSystem.addSystemMessage("Error: Player not found.");
                return;
            }
            World currentWorld = GameContext.get().getWorld();
            if (currentWorld == null) {
                chatSystem.addSystemMessage("Error: World not found.");
                return;
            }
            int tileX = currentWorld.getWorldData().getConfig().getTileSpawnX();
            int tileY = currentWorld.getWorldData().getConfig().getTileSpawnY();
            float pixelX = tileX * World.TILE_SIZE;
            float pixelY = tileY * World.TILE_SIZE;

            // Update player position directly and robustly.
            player.setX(pixelX);
            player.setY(pixelY);
            player.setRenderPosition(new Vector2(pixelX, pixelY));
            player.setMoving(false);

            // Let the world's update loop handle asynchronous chunk loading.
            // REMOVED: currentWorld.clearChunks();
            // REMOVED: currentWorld.loadChunksAroundPlayer();

            chatSystem.addSystemMessage("Teleported to spawn point! (" + tileX + ", " + tileY + ")");
            GameLogger.info("Player teleported to spawn: " + pixelX + ", " + pixelY);

            // Invalidate the render cache to ensure new entities are drawn correctly.
            currentWorld.markYSortDirty();

            if (GameContext.get().isMultiplayer()) {
                gameClient.sendPlayerUpdate();
                gameClient.savePlayerState(player.getPlayerData());
            }
        } catch (Exception e) {
            GameLogger.error("Spawn command failed: " + e.getMessage());
            chatSystem.addSystemMessage("Error executing spawn command: " + e.getMessage());
        }
    }
}
