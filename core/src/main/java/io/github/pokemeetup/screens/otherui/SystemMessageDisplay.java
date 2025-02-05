package io.github.pokemeetup.screens.otherui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.Align;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.utils.GameLogger;

public class SystemMessageDisplay {
    private static SystemMessageDisplay instance;
    private final Label messageLabel;

    private SystemMessageDisplay(Skin skin) {
        // Create a label with your desired style.
        messageLabel = new Label("", skin);
        messageLabel.setWrap(true);
        messageLabel.setAlignment(Align.center);

        // Position the label at the top of the screen.
        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();
        messageLabel.setWidth(screenWidth);
        messageLabel.setPosition(0, screenHeight - messageLabel.getHeight() - 20);

        // Optionally, set an initial transparency.
        messageLabel.getColor().a = 1f;
    }

    public static void init(Skin skin) {
        if (instance == null) {
            instance = new SystemMessageDisplay(skin);
            // Add the label to the UI stage.
            GameContext.get().getUiStage().addActor(instance.messageLabel);
            GameLogger.info("SystemMessageDisplay initialized");
        }
    }

    public static SystemMessageDisplay getInstance() {
        if (instance == null) {
            throw new IllegalStateException("SystemMessageDisplay not initialized!");
        }
        return instance;
    }

    public void displayMessage(String message) {
        messageLabel.clearActions();
        messageLabel.setText(message);
        // Fade out after 2 seconds, then clear the text and reset alpha.
        messageLabel.addAction(Actions.sequence(
            Actions.delay(2f),
            Actions.fadeOut(1f),
            Actions.run(() -> {
                messageLabel.setText("");
                messageLabel.getColor().a = 1f;
            })
        ));
    }
}
