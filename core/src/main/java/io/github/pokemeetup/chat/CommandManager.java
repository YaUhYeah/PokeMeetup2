package io.github.pokemeetup.chat;

import io.github.pokemeetup.utils.GameLogger;

import java.util.HashMap;
import java.util.Map;

public class CommandManager {
    private final Map<String, Command> commands = new HashMap<>();


    public Command getCommand(String name) {
        if (name == null) {
            GameLogger.error("Attempted to get null command name");
            return null;
        }
        Command cmd = commands.get(name.toLowerCase());
        if (cmd == null) {
            GameLogger.info("Command not found: " + name);
        }
        return cmd;
    }

    public void registerCommand(Command command) {
        if (command == null) {
            GameLogger.error("Attempted to register null command");
            return;
        }
        commands.put(command.getName().toLowerCase(), command);
        for (String alias : command.getAliases()) {
            commands.put(alias.toLowerCase(), command);
        }
        GameLogger.info("Registered command: " + command.getName() +
            " with " + command.getAliases().length + " aliases");
    }
}
