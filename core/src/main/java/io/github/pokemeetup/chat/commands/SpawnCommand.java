package io.github.pokemeetup.chat.commands;

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

    @Override
    public void execute(String args, GameClient gameClient, ChatSystem chatSystem) {
        try {
            Player player = GameContext.get().getPlayer();
            World currentWorld = GameContext.get().getWorld();
            if (player == null || currentWorld == null) {
                chatSystem.addSystemMessage("Error: Cannot execute spawn command. Player or World not found.");
                return;
            }

            int tileX = currentWorld.getWorldData().getConfig().getTileSpawnX();
            int tileY = currentWorld.getWorldData().getConfig().getTileSpawnY();

            // ✅ **REFACTOR:** Centralized teleport logic is now handled by the Player object.
            player.teleportTo(tileX, tileY);

            chatSystem.addSystemMessage("Teleported to spawn point (" + tileX + ", " + tileY + ")");
            GameLogger.info("Player teleported to spawn via command.");

        } catch (Exception e) {
            GameLogger.error("Spawn command failed: " + e.getMessage());
            chatSystem.addSystemMessage("Error executing spawn command.");
        }
    }
}
