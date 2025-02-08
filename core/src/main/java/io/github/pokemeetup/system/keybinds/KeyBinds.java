package io.github.pokemeetup.system.keybinds;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Preferences;
import java.util.HashMap;
import java.util.Map;

public class KeyBinds {
    private static final String PREF_NAME = "keybinds";
    private static Map<String, Integer> currentBinds = new HashMap<>();
    private static final Map<String, Integer> defaultBinds = new HashMap<>();

    // Action names
    public static final String MOVE_UP = "Move Up";
    public static final String MOVE_DOWN = "Move Down";
    public static final String MOVE_LEFT = "Move Left";
    public static final String MOVE_RIGHT = "Move Right";
    public static final String INTERACT = "Interact";
    public static final String BUILD_MODE = "Build Mode";
    public static final String INVENTORY = "Inventory";
    public static final String SPRINT = "Sprint";
    public static final String ACTION = "Action";

    static {
        // Set default bindings
        defaultBinds.put(MOVE_UP, Input.Keys.W);
        defaultBinds.put(MOVE_DOWN, Input.Keys.S);
        defaultBinds.put(MOVE_LEFT, Input.Keys.A);
        defaultBinds.put(MOVE_RIGHT, Input.Keys.D);
        defaultBinds.put(INTERACT, Input.Keys.X);
        defaultBinds.put(BUILD_MODE, Input.Keys.B);
        defaultBinds.put(INVENTORY, Input.Keys.E);
        defaultBinds.put(SPRINT, Input.Keys.Z);
        defaultBinds.put(ACTION, Input.Keys.Q);

        loadBindings();
    }

    public static void loadBindings() {
        Preferences prefs = Gdx.app.getPreferences(PREF_NAME);
        currentBinds.clear();

        for (Map.Entry<String, Integer> entry : defaultBinds.entrySet()) {
            currentBinds.put(entry.getKey(), prefs.getInteger(entry.getKey(), entry.getValue()));
        }
    }

    public static void saveBindings() {
        Preferences prefs = Gdx.app.getPreferences(PREF_NAME);
        for (Map.Entry<String, Integer> entry : currentBinds.entrySet()) {
            prefs.putInteger(entry.getKey(), entry.getValue());
        }
        prefs.flush();
    }

    public static void resetToDefaults() {
        currentBinds.clear();
        currentBinds.putAll(defaultBinds);
        saveBindings();
    }

    public static int getBinding(String action) {
        return currentBinds.getOrDefault(action, defaultBinds.get(action));
    }

    public static void setBinding(String action, int keycode) {
        currentBinds.put(action, keycode);
    }

    public static String getKeyName(int keycode) {
        return Input.Keys.toString(keycode);
    }

    public static Map<String, Integer> getCurrentBinds() {
        return new HashMap<>(currentBinds);
    }
}
