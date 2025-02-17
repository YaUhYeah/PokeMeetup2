package io.github.pokemeetup.system.keybinds;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import java.util.Map;
import java.util.HashMap;

/**
 * Dialog to rebind controller keys.
 * Currently shows the button code as text (you can later replace this with friendly names).
 */
public class ControllerBindsDialog extends Dialog {
    private Table bindsTable;
    private Actor waitingButton = null;
    private String waitingAction = null;

    public ControllerBindsDialog(Skin skin) {
        super("Controller Key Bindings", skin);
        bindsTable = new Table(skin);
        bindsTable.defaults().pad(5);
        createLayout(skin);
        getContentTable().add(bindsTable).grow().pad(20);

        // Reset and Close buttons
        TextButton resetButton = new TextButton("Reset to Defaults", skin);
        resetButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                ControllerBinds.resetToDefaults();
                refreshBindings();
            }
        });

        TextButton closeButton = new TextButton("Close", skin);
        closeButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                ControllerBinds.saveBindings();
                hide();
            }
        });

        Table buttonTable = new Table();
        buttonTable.add(resetButton).pad(10);
        buttonTable.add(closeButton).pad(10);
        getButtonTable().add(buttonTable);

        // Listen for numeric or other inputs to set new bindings
        addListener(new InputListener(){
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if(waitingButton != null) {
                    // For simplicity, we use the numeric value as the new binding.
                    // In a real app, you’d probably want to wait for a controller button event.
                    ControllerBinds.setBinding(waitingAction, keycode);
                    ((TextButton)waitingButton).setText(String.valueOf(ControllerBinds.getBinding(waitingAction)));
                    waitingButton = null;
                    waitingAction = null;
                    return true;
                }
                return false;
            }
        });
    }

    private void createLayout(Skin skin) {
        bindsTable.clear();
        // Create a row for each binding.
        for (Map.Entry<String, Integer> entry : ControllerBinds.getCurrentBinds().entrySet()) {
            String action = entry.getKey();
            String bindingText = String.valueOf(entry.getValue());
            Label actionLabel = new Label(action, skin);
            actionLabel.setAlignment(Align.left);
            TextButton bindButton = new TextButton(bindingText, skin);
            bindButton.addListener(new ChangeListener(){
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if(waitingButton != null) {
                        ((TextButton)waitingButton).setText(String.valueOf(ControllerBinds.getBinding(waitingAction)));
                    }
                    waitingButton = actor;
                    waitingAction = action;
                    ((TextButton)actor).setText("Press button...");
                }
            });
            bindsTable.add(actionLabel).width(150).padRight(20);
            bindsTable.add(bindButton).width(150).row();
        }
    }

    private void refreshBindings() {
        createLayout(getSkin());
    }
}
