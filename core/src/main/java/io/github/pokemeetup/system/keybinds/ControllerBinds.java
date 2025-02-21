package io.github.pokemeetup.system.keybinds;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

import java.util.HashMap;
import java.util.Map;

/**
 * Allows you to rebind controller buttons.
 *
 * This version maps the controls to the ROG Ally controller by default.
 * Assumptions for the default mapping (based on an XInput layout for ROG Ally):
 *   - Face Buttons are reported in the order: X, A, B, Y.
 *     • INTERACT: X button (button index 0)
 *     • ACTION (e.g., chop/punch): A button (button index 1)
 *     • SPRINT: B button (button index 2)
 *     • INVENTORY: Y button (button index 3)
 *   - Shoulder and trigger buttons remain the same:
 *     • BUILD_MODE: Left bumper (button index 4)
 *     • BREAK: Left trigger (button index 6)
 *     • PLACE: Right trigger (button index 7)
 *   - D-Pad directions are mapped to:
 *     • MOVE_UP: D-pad up (button index 19)
 *     • MOVE_DOWN: D-pad down (button index 20)
 *     • MOVE_LEFT: D-pad left (button index 21)
 *     • MOVE_RIGHT: D-pad right (button index 22)
 *
 * Modify these values as needed based on your testing with the actual device.
 */
public class ControllerBinds {
    // Action names (matching your game actions)
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
        // Default controller bindings for the ROG Ally controller:

        // D-Pad bindings (assuming these indices for the D-Pad):
        defaultBinds.put(MOVE_UP, 19);    // D-pad Up
        defaultBinds.put(MOVE_DOWN, 20);  // D-pad Down
        defaultBinds.put(MOVE_LEFT, 21);  // D-pad Left
        defaultBinds.put(MOVE_RIGHT, 22); // D-pad Right

        defaultBinds.put(INTERACT, 1);  // X button
        defaultBinds.put(ACTION, 0);  // A button for chop/punch
        defaultBinds.put(SPRINT, 2);  // B button (held for sprint)
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
