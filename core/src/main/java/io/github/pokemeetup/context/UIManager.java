package io.github.pokemeetup.context;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import io.github.pokemeetup.screens.otherui.HotbarSystem;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;

public class UIManager {
    private final Stage uiStage;
    private final Skin skin;

    public UIManager(Stage stage, Skin skin) {
        this.uiStage = stage;
        this.skin = skin;
        initializeComponents();
    }

    private void initializeComponents() {
        // Ensure the skin and required regions are loaded.
        TextureRegion hotbarBg = TextureManager.ui.findRegion("hotbar_bg");
        if (hotbarBg == null) {
            GameLogger.error("Missing 'hotbar_bg' region in skin!");
        }
    }


    public void resize(int width, int height) {
        // Update positions, if needed.
    }
}
