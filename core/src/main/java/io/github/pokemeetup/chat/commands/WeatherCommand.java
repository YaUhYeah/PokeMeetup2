package io.github.pokemeetup.chat.commands;

import io.github.pokemeetup.chat.ChatSystem;
import io.github.pokemeetup.chat.Command;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.gameplay.overworld.WeatherSystem;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;

public class WeatherCommand implements Command {

    @Override
    public String getName() {
        return "weather";
    }

    @Override
    public String[] getAliases() {
        return new String[0];
    }

    @Override
    public String getDescription() {
        return "Gets or sets the weather. When setting, an optional time (in seconds) can be provided to override biome changes.";
    }

    @Override
    public String getUsage() {
        // Note: The 4th argument is optional
        return "/weather <set|get> <weatherType|blank> <intensity|blank> [time in seconds]";
    }

    @Override
    public boolean isMultiplayerOnly() {
        return false;
    }

    @Override
    public void execute(String args, GameClient gameClient, ChatSystem chatSystem) {
        String[] argsArray = args.split(" ");
        try {
            GameLogger.info("Executing weather command");

            // Retrieve the player and world context.
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

            // Get current weather
            if (argsArray[0].equalsIgnoreCase("get")) {
                chatSystem.addSystemMessage("Current weather is: "
                    + currentWorld.getWeatherSystem().getCurrentWeather().name());
                return;
            }

            // Set new weather
            if (argsArray[0].equalsIgnoreCase("set")) {
                if (argsArray.length < 3) {
                    chatSystem.addSystemMessage("Usage: " + getUsage());
                    return;
                }
                float intensity = Float.parseFloat(argsArray[2]);
                if (intensity < 0) {
                    chatSystem.addSystemMessage("Weather intensity must be a positive number");
                    return;
                }
                boolean weatherFound = false;
                for (WeatherSystem.WeatherType weather : WeatherSystem.WeatherType.values()) {
                    if (weather.name().equalsIgnoreCase(argsArray[1])) {
                        currentWorld.getWeatherSystem().setWeather(weather, intensity);
                        weatherFound = true;
                        String response = "Weather set to: " + weather.name() + " with intensity: " + intensity;
                        // Check for an optional time argument to override automatic biome changes.
                        if (argsArray.length >= 4) {
                            float duration = Float.parseFloat(argsArray[3]);
                            currentWorld.getWeatherSystem().setManualOverrideTimer(duration);
                            response += " for " + duration + " seconds";
                        }
                        chatSystem.addSystemMessage(response);
                        break;
                    }
                }
                if (!weatherFound) {
                    chatSystem.addSystemMessage("Invalid weather type specified. Available types: "
                        + "CLEAR, RAIN, HEAVY_RAIN, SNOW, BLIZZARD, SANDSTORM, FOG, THUNDERSTORM");
                    return;
                }
            }
        } catch (Exception e) {
            GameLogger.error("Error executing weather command: " + e);
        }
    }
}
