package io.github.pokemeetup.chat.commands;

import io.github.pokemeetup.chat.ChatSystem;
import io.github.pokemeetup.chat.Command;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;

public class SetWorldSpawnCommand implements Command {

    @Override
    public String getName() {
        return "setSpawn";
    }

    @Override
    public String[] getAliases() {
        return new String[0];
    }

    @Override
    public String getDescription() {
        return "sets the world spawn to the player";
    }

    @Override
    public String getUsage() {
        return "/setSpawn";
    }

    @Override
    public boolean isMultiplayerOnly() {
        return false;
    }

    @Override
    public void execute(String args, GameClient gameClient, ChatSystem chatSystem) {
        String[] argsArray = args.split(" ");

        try {
            GameLogger.info("Executing setSpawn command...");

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

            if (currentWorld.getWorldData() == null || currentWorld.getWorldData().getConfig() == null) {
                chatSystem.addSystemMessage("Error: World configuration not available");
                return;
            }

            if (argsArray.length > 2) {
                chatSystem.addSystemMessage("Invalid arguments: " + getUsage());
                return;
            }

            int tileX = player.getTileX();
            int tileY = player.getTileY();

            if (argsArray.length == 2) {
                tileX = Integer.parseInt(argsArray[0]);
                tileY = Integer.parseInt(argsArray[1]);
            }

            currentWorld.getWorldData().getConfig().setTileSpawnX(tileX);
            currentWorld.getWorldData().getConfig().setTileSpawnY(tileY);

            chatSystem.addSystemMessage("You set the world's spawn coords to " + tileX + " " + tileY);
        } catch (Exception e) {
            GameLogger.error("Error executing spawn command: " + e.getMessage());
        }
    }
}
