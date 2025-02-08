package io.github.pokemeetup.chat.commands;

import io.github.pokemeetup.chat.ChatSystem;
import io.github.pokemeetup.chat.Command;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;

public class TimeCommand implements Command {
    @Override
    public String getName() {
        return "time";
    }

    @Override
    public String[] getAliases() {
        return new String[0];
    }

    @Override
    public String getDescription() {
        return "sets or gets the time";
    }

    @Override
    public String getUsage() {
        return "/time <set|get> <time>";
    }

    @Override
    public boolean isMultiplayerOnly() {
        return false;
    }

    @Override
    public void execute(String args, GameClient gameClient, ChatSystem chatSystem) {
        String[] argsArray = args.split(" ");

        try {
            GameLogger.info("executing time command");

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

            if (argsArray[0].equals("get")) {
                chatSystem.addSystemMessage("the current world time is: " + currentWorld.getWorldData().getWorldTimeInMinutes());
                return;
            }
            if (argsArray[0].equals("set")) {
                float playerTimeInput = Float.parseFloat(argsArray[1]);

                if (playerTimeInput < 0) {
                    chatSystem.addSystemMessage("time must be a positive number");
                }

                currentWorld.getWorldData().setWorldTimeInMinutes(playerTimeInput);
                currentWorld.getWorldData().save();
                chatSystem.addSystemMessage("set world time to " + playerTimeInput);
            }
        } catch (Exception e) {
            GameLogger.error("error executing /time command: " + e);
        }

    }
}
