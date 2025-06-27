package io.github.pokemeetup.pokemon.attacks;

import com.google.gson.*;
import io.github.pokemeetup.pokemon.Pokemon;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

public class MoveLoader {
    public static Map<String, Move> loadMovesFromJson(String jsonContent) {
        Gson gson = new GsonBuilder().create();
        Map<String, Move> moves = new HashMap<>();

        try {
            JsonObject jsonObject = JsonParser.parseString(jsonContent).getAsJsonObject();
            JsonObject movesJson = jsonObject.getAsJsonObject("moves");
            for (Map.Entry<String, JsonElement> entry : movesJson.entrySet()) {
                String moveName = entry.getKey();
                JsonObject moveJson = entry.getValue().getAsJsonObject();

                Move move = parseMove(moveName, moveJson);
                moves.put(moveName, move);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse moves JSON: " + e.getMessage());
        }

        return moves;
    }
    public static Map<String, Move> loadMoves(String jsonFilePath) throws IOException {
        Gson gson = new GsonBuilder().create();
        Map<String, Move> moves = new HashMap<>();
        JsonObject jsonObject;
        try (FileReader reader = new FileReader(jsonFilePath)) {
            jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
        }
        JsonObject movesJson = jsonObject.getAsJsonObject("moves");
        for (Map.Entry<String, JsonElement> entry : movesJson.entrySet()) {
            String moveName = entry.getKey();
            JsonObject moveJson = entry.getValue().getAsJsonObject();

            Move move = parseMove(moveName, moveJson);
            moves.put(moveName, move);
        }

        return moves;
    }

    private static Move parseMove(String moveName, JsonObject moveJson) {
        String typeStr = moveJson.get("type").getAsString();
        int power = moveJson.get("power").getAsInt();
        int accuracy = moveJson.get("accuracy").getAsInt();
        int pp = moveJson.get("pp").getAsInt();
        boolean isSpecial = moveJson.get("isSpecial").getAsBoolean();
        String description = moveJson.get("description").getAsString();
        Pokemon.PokemonType type = Pokemon.PokemonType.valueOf(typeStr);
        Move.Builder builder = new Move.Builder(moveName, type)
            .power(power)
            .accuracy(accuracy)
            .pp(pp)
            .special(isSpecial)
            .description(description);
        if (moveJson.has("effects")) {
            JsonObject effectsJson = moveJson.getAsJsonObject("effects");
            Move.MoveEffect effect = parseMoveEffect(effectsJson);
            builder.effect(effect);
        }

        return builder.build();
    }

    private static Move.MoveEffect parseMoveEffect(JsonObject effectsJson) {
        Move.MoveEffect effect = new Move.MoveEffect();
        if (effectsJson.has("type")) {
            effect.setEffectType(effectsJson.get("type").getAsString());
        }
        if (effectsJson.has("chance")) {
            effect.setChance(effectsJson.get("chance").getAsFloat());
        } else {
            effect.setChance(1.0f);  // Default chance
        }
        if (effectsJson.has("status")) {
            String statusStr = effectsJson.get("status").getAsString();
            Pokemon.Status status = Pokemon.Status.valueOf(statusStr);
            effect.setStatusEffect(status);
        }
        if (effectsJson.has("statChanges")) {
            JsonObject statChangesJson = effectsJson.getAsJsonObject("statChanges");
            Map<String, Integer> statModifiers = new HashMap<>();
            for (Map.Entry<String, JsonElement> statEntry : statChangesJson.entrySet()) {
                String stat = statEntry.getKey();
                int change = statEntry.getValue().getAsInt();
                statModifiers.put(stat, change);
            }
            effect.setStatModifiers(statModifiers);
        }
        if (effectsJson.has("animation")) {
            effect.setAnimation(effectsJson.get("animation").getAsString());
        }
        if (effectsJson.has("sound")) {
            effect.setSound(effectsJson.get("sound").getAsString());
        }

        return effect;
    }
}
