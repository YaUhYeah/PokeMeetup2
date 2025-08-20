package io.github.pokemeetup.chat.commands;

import io.github.pokemeetup.chat.ChatSystem;
import io.github.pokemeetup.chat.Command;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;

public class TeleportPositionCommand implements Command {

    @Override
    public String getName() { return "tp"; }

    @Override
    public String[] getAliases() { return new String[0]; }

    @Override
    public String getDescription() { return "Teleports user to specified location."; }

    @Override
    public String getUsage() { return "/tp <x> <y>"; }

    @Override
    public boolean isMultiplayerOnly() { return false; }

    @Override
    public void execute(String args, GameClient gameClient, ChatSystem chatSystem) {
        String[] argsArray = args.split(" ");
        try {
            Player player = GameContext.get().getPlayer();
            World currentWorld = GameContext.get().getWorld();
            if (player == null || currentWorld == null) {
                chatSystem.addSystemMessage("Error: Player or World not found.");
                return;
            }
            if (argsArray.length != 2) {
                chatSystem.addSystemMessage("Invalid arguments. Use: " + getUsage());
                return;
            }
            int tileX = Integer.parseInt(argsArray[0]);
            int tileY = Integer.parseInt(argsArray[1]);
            if (!currentWorld.isWithinWorldBounds(tileX, tileY)) {
                chatSystem.addSystemMessage("Error: Teleport location is outside the world border.");
                return;
            }

            // ✅ **REFACTOR:** Centralized teleport logic is now handled by the Player object.
            player.teleportTo(tileX, tileY);

            chatSystem.addSystemMessage("Teleported successfully to (" + tileX + ", " + tileY + ").");
            GameLogger.info("Player teleported via /tp command.");

        } catch (NumberFormatException e) {
            chatSystem.addSystemMessage("Invalid coordinates. Use: " + getUsage());
        } catch (Exception e) {
            GameLogger.error("Error executing tp command: " + e.getMessage());
            chatSystem.addSystemMessage("An error occurred during teleport.");
        }
    }
}
