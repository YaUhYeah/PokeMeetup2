package io.github.pokemeetup.system.keybinds;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;

public class KeyBindsDialog extends Dialog {
    private Table keyBindsTable;
    private Actor waitingButton = null;
    private String waitingAction = null;


    public KeyBindsDialog(Skin skin) {
        super("Key Bindings", skin);

        // Main container for our rows
        keyBindsTable = new Table(skin);
        keyBindsTable.defaults().pad(5);

        // Build the layout with headings
        createLayout(skin);

        // Add the entire table to the dialog
        getContentTable().add(keyBindsTable).grow().pad(20);

        // Add Reset and Close buttons at the bottom
        TextButton resetButton = new TextButton("Reset to Defaults", skin);
        resetButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                KeyBinds.resetToDefaults();
                refreshBindings();
            }
        });

        TextButton closeButton = new TextButton("Close", skin);
        closeButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                KeyBinds.saveBindings();
                hide();
            }
        });

        Table buttonTable = new Table();
        buttonTable.add(resetButton).pad(10);
        buttonTable.add(closeButton).pad(10);

        getButtonTable().add(buttonTable);

        // Capture keyboard events to set new key bindings
        addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                // If we're waiting for a key press to set a new binding:
                if (waitingButton != null) {
                    // Escape can cancel the rebind
                    if (keycode != Input.Keys.ESCAPE) {
                        KeyBinds.setBinding(waitingAction, keycode);
                    }
                    // Update button text to newly bound key (or revert if ESC)
                    ((TextButton)waitingButton).setText(
                        KeyBinds.getKeyName(KeyBinds.getBinding(waitingAction))
                    );
                    waitingButton = null;
                    waitingAction = null;
                    return true;
                }
                return false;
            }
        });
    }

    private void createLayout(Skin skin) {
        keyBindsTable.clear();

        // We'll separate actions into categories for clarity:
        keyBindsTable.add(new Label("Movement Keys", skin))
            .colspan(2).align(Align.left).padBottom(5).row();

        // Movement actions in a fixed order:
        addKeyBindRow(KeyBinds.MOVE_UP, skin);
        addKeyBindRow(KeyBinds.MOVE_DOWN, skin);
        addKeyBindRow(KeyBinds.MOVE_LEFT, skin);
        addKeyBindRow(KeyBinds.MOVE_RIGHT, skin);
        addKeyBindRow(KeyBinds.SPRINT, skin);

        // Add a small separator or extra padding
        keyBindsTable.add().colspan(2).height(15).row();

        keyBindsTable.add(new Label("Other Actions", skin))
            .colspan(2).align(Align.left).padBottom(5).row();

        // Other actions in a fixed order:
        addKeyBindRow(KeyBinds.INTERACT, skin);
        addKeyBindRow(KeyBinds.ACTION, skin);
        addKeyBindRow(KeyBinds.BUILD_MODE, skin);
        addKeyBindRow(KeyBinds.INVENTORY, skin);
    }

    /**
     * Builds a single row: Label + Rebind-Button
     */
    private void addKeyBindRow(String action, Skin skin) {
        // Safeguard if the user manually removed or changed the map
        if (!KeyBinds.getCurrentBinds().containsKey(action)) {
            return;
        }

        Label actionLabel = new Label(action, skin);
        actionLabel.setAlignment(Align.left);

        TextButton bindButton = new TextButton(
            KeyBinds.getKeyName(KeyBinds.getBinding(action)),
            skin
        );

        bindButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                // If we're already waiting for a key, revert the old button text
                if (waitingButton != null) {
                    ((TextButton)waitingButton).setText(
                        KeyBinds.getKeyName(KeyBinds.getBinding(waitingAction))
                    );
                }
                waitingButton = actor;
                waitingAction = action;
                ((TextButton)actor).setText("Press any key...");
            }
        });

        keyBindsTable.add(actionLabel).width(150).padRight(20);
        keyBindsTable.add(bindButton).width(150).row();
    }

    /**
     * Re-create the layout after a reset or other event that changes the bindings.
     */
    private void refreshBindings() {
        createLayout(getSkin());
    }
}
