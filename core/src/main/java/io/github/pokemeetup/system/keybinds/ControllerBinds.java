package io.github.pokemeetup.system.keybinds;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

import java.util.HashMap;
import java.util.Map;

/**
 * Allows you to rebind controller buttons.
 * Default mappings here are examples (adjust the button indexes as needed).
 */
public class ControllerBinds {
    // Action names (matching your keyboard actions)
    public static final String MOVE_UP = "Move Up";
    public static final String MOVE_DOWN = "Move Down";
    public static final String MOVE_LEFT = "Move Left";
    public static final String MOVE_RIGHT = "Move Right";
    public static final String INTERACT = "Interact";
    public static final String ACTION = "Action";
    public static final String SPRINT = "Sprint";
    public static final String INVENTORY = "Inventory";
    public static final String BUILD_MODE = "Build Mode";
    public static final String BREAK = "Break";
    public static final String PLACE = "Place";
    private static final String PREF_NAME = "controller_keybinds";
    private static final Map<String, Integer> defaultBinds = new HashMap<>();
    private static Map<String, Integer> currentBinds = new HashMap<>();

    static {
        // Default controller bindings (example button indexes):
        // (These numbers may vary by controller and backend.)
        defaultBinds.put(MOVE_UP, 10); // usually mapped via POV hat
        defaultBinds.put(MOVE_DOWN, 11);
        defaultBinds.put(MOVE_LEFT, 12);
        defaultBinds.put(MOVE_RIGHT, 13);
        defaultBinds.put(INTERACT, 2);  // X button
        defaultBinds.put(ACTION, 0);  // A button for chop/punch
        defaultBinds.put(SPRINT, 1);  // B button (held for sprint)
        defaultBinds.put(INVENTORY, 3);  // Y button
        defaultBinds.put(BUILD_MODE, 4);  // Left bumper for build mode
        defaultBinds.put(BREAK, 6);  // Left trigger for breaking
        defaultBinds.put(PLACE, 7);  // Right trigger for placing blocks

        loadBindings();
    }

    public static Map<String, Integer> getCurrentBinds() {
        return currentBinds;
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

    public static void setBinding(String action, int buttonCode) {
        currentBinds.put(action, buttonCode);
    }
}
