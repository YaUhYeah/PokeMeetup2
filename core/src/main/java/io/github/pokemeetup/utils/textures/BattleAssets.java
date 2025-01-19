package io.github.pokemeetup.utils.textures;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.utils.GameLogger;

import java.util.HashMap;
import java.util.Map;

public class BattleAssets {
    private static final int STATUS_ICON_HEIGHT = 16;
    private static final int TYPE_ICON_SIZE = 32;
    private static final Color HP_HIGH = new Color(0.28f, 0.78f, 0.45f, 1f);    // Green
    private static final Color HP_MED = new Color(1f, 0.85f, 0f, 1f);          // Yellow
    private static final Color HP_LOW = new Color(1f, 0.22f, 0.38f, 1f);       // Red

    private TextureAtlas uiAtlas;
    private TextureRegionDrawable battleBackground;
    private TextureRegionDrawable menuBackground;
    private TextureRegionDrawable buttonNormal;
    private TextureRegionDrawable buttonPressed;
    private Map<String, TextureRegionDrawable> backgrounds;
    private Map<String, TextureRegionDrawable> buttons;
    private Map<String, TextureRegionDrawable> hpBars;
    private Map<String, TextureRegionDrawable> databoxes;
    private Map<String, TextureRegion> statusIcons;
    private Map<Pokemon.PokemonType, TextureRegion> typeIcons;
    public BattleAssets() {
        // Initialize all maps in constructor
        backgrounds = new HashMap<>();
        buttons = new HashMap<>();
        hpBars = new HashMap<>();
        databoxes = new HashMap<>();
        statusIcons = new HashMap<>();
        typeIcons = new HashMap<>();
    }


    private void loadButtons() {
        buttons = new HashMap<>();
        TextureRegion buttonSheet = uiAtlas.findRegion("battle-buttons");

        if (buttonSheet == null) {
            GameLogger.error( "Failed to find battle-buttons region in atlas");
            return;
        }

        int buttonWidth = buttonSheet.getRegionWidth() / 2;
        int buttonHeight = buttonSheet.getRegionHeight() / 2;

        // Move buttons
        TextureRegion moveNormal = new TextureRegion(buttonSheet, 0, 0, buttonWidth, buttonHeight);
        TextureRegion movePressed = new TextureRegion(buttonSheet, buttonWidth, 0, buttonWidth, buttonHeight);

        // Action buttons
        TextureRegion actionNormal = new TextureRegion(buttonSheet, 0, buttonHeight,
            (int)(buttonWidth/1.5f), buttonHeight);
        TextureRegion actionPressed = new TextureRegion(buttonSheet,
            (int)(buttonWidth/1.5f), buttonHeight, (int)(buttonWidth/1.5f), buttonHeight);

        buttons.put("move-normal", new TextureRegionDrawable(moveNormal));
        buttons.put("move-pressed", new TextureRegionDrawable(movePressed));
        buttons.put("action-normal", new TextureRegionDrawable(actionNormal));
        buttons.put("action-pressed", new TextureRegionDrawable(actionPressed));
    }

    private void loadDataboxes() {
        databoxes = new HashMap<>();
        TextureRegion normal = uiAtlas.findRegion("hotbar_bg");
        TextureRegion foe = uiAtlas.findRegion("hotbar_bg");

        if (normal == null || foe == null) {
            GameLogger.error( "Failed to find databox regions in atlas");
            return;
        }

        databoxes.put("normal", new TextureRegionDrawable(normal));
        databoxes.put("foe", new TextureRegionDrawable(foe));
    }

    private void loadBackgrounds() {
        backgrounds = new HashMap<>();
        TextureRegion menuBg = uiAtlas.findRegion("battle-menu-bg");
        TextureRegion window = uiAtlas.findRegion("window");

        if (menuBg != null) {
            backgrounds.put("battle-menu-bg", new TextureRegionDrawable(menuBg));
        }
        if (window != null) {
            backgrounds.put("window", new TextureRegionDrawable(window));
        }
    }

    private void loadHPBars() {
        TextureRegion hpSheet = uiAtlas.findRegion("hp-bars");
        if (hpSheet == null) {
            GameLogger.error( "HP bars texture region not found");
            return;
        }

        int barHeight = hpSheet.getRegionHeight() / 4;
        hpBars.put("background", new TextureRegionDrawable(
            new TextureRegion(hpSheet, 0, 0, hpSheet.getRegionWidth(), barHeight)));
        hpBars.put("green", new TextureRegionDrawable(
            new TextureRegion(hpSheet, 0, barHeight, hpSheet.getRegionWidth(), barHeight)));
        hpBars.put("yellow", new TextureRegionDrawable(
            new TextureRegion(hpSheet, 0, barHeight * 2, hpSheet.getRegionWidth(), barHeight)));
        hpBars.put("red", new TextureRegionDrawable(
            new TextureRegion(hpSheet, 0, barHeight * 3, hpSheet.getRegionWidth(), barHeight)));
    }

    private void loadStatusIcons() {
        TextureRegion statusSheet = uiAtlas.findRegion("status-icons");
        if (statusSheet == null) {
            GameLogger.error( "Status icons texture region not found");
            return;
        }

        String[] conditions = {"PSN", "PAR", "BRN", "FRZ", "SLP", "TOX"};
        int iconWidth = statusSheet.getRegionWidth() / conditions.length;

        for (int i = 0; i < conditions.length; i++) {
            TextureRegion icon = new TextureRegion(statusSheet,
                i * iconWidth, 0, iconWidth, STATUS_ICON_HEIGHT);
            statusIcons.put(conditions[i], icon);
        }
    }    public TextureRegionDrawable getButtonDrawable(String key) {
        return buttons.get(key);
    }

    public TextureRegionDrawable getDataboxNormal() {
        return databoxes.get("normal");
    }

    public TextureRegionDrawable getDataboxFoe() {
        return databoxes.get("foe");
    }

    public TextureRegionDrawable getBackground(String key) {
        return backgrounds.get(key);
    }

    public TextureRegion getStatusIcon(String status) {
        return statusIcons.get(status);
    }

    public TextureAtlas getUiAtlas() {
        return uiAtlas;
    }

    public Map<String, TextureRegionDrawable> getBackgrounds() {
        return backgrounds;
    }

    public Map<String, TextureRegionDrawable> getButtons() {
        return buttons;
    }

    public Map<String, TextureRegionDrawable> getHpBars() {
        return hpBars;
    }

    public Map<String, TextureRegionDrawable> getDataboxes() {
        return databoxes;
    }

    public Map<String, TextureRegion> getStatusIcons() {
        return statusIcons;
    }

    public Map<Pokemon.PokemonType, TextureRegion> getTypeIcons() {
        return typeIcons;
    }

    private void loadTypeIcons() {
        TextureRegion typeSheet = uiAtlas.findRegion("pokemon-type-icons");
        if (typeSheet == null) {
            GameLogger.error( "Pokemon type icons texture region not found");
            return;
        }

        int cols = typeSheet.getRegionWidth() / TYPE_ICON_SIZE;
        Pokemon.PokemonType[] types = Pokemon.PokemonType.values();

        for (int i = 0; i < types.length; i++) {
            int x = (i % cols) * TYPE_ICON_SIZE;
            int y = (i / cols) * TYPE_ICON_SIZE;
            TextureRegion icon = new TextureRegion(typeSheet, x, y, TYPE_ICON_SIZE, TYPE_ICON_SIZE);
            typeIcons.put(types[i], icon);
        }
    }



    // In BattleAssets class
    public void initialize() {
        if (uiAtlas != null) {
            // Already initialized
            return;
        }

        try {
            FileHandle atlasFile = Gdx.files.internal("atlas/ui-gfx-atlas.atlas");
            if (!atlasFile.exists()) {
                GameLogger.error("UI atlas file not found at: atlas/ui-gfx-atlas.atlas");
                throw new RuntimeException("UI atlas file not found");
            }

            uiAtlas = new TextureAtlas(atlasFile);

            // Only load if not already loaded
            if (backgrounds.isEmpty()) loadBackgrounds();
            if (buttons.isEmpty()) loadButtons();
            if (hpBars.isEmpty()) loadHPBars();
            if (databoxes.isEmpty()) loadDataboxes();
            if (statusIcons.isEmpty()) loadStatusIcons();
            if (typeIcons.isEmpty()) loadTypeIcons();

            GameLogger.info("Battle assets initialized/reused successfully");

        } catch (Exception e) {
            GameLogger.error("Failed to initialize battle assets: " + e.getMessage());
            throw new RuntimeException("Failed to initialize battle assets", e);
        }
    }

    // Modify dispose to be more selective
    public void dispose() {
        // Don't dispose shared textures from TextureManager
        // Only clear references
        if (backgrounds != null) backgrounds.clear();
        if (buttons != null) buttons.clear();
        if (hpBars != null) hpBars.clear();
        if (databoxes != null) databoxes.clear();
        if (statusIcons != null) statusIcons.clear();
        if (typeIcons != null) typeIcons.clear();

        // Don't dispose uiAtlas as it's managed by TextureManager
        uiAtlas = null;
    }
    public ProgressBar.ProgressBarStyle createHPBarStyle(float percentage) {
        ProgressBar.ProgressBarStyle style = new ProgressBar.ProgressBarStyle();

        // Create background (dark gray)
        Pixmap bgPixmap = new Pixmap(32, 16, Pixmap.Format.RGBA8888);
        bgPixmap.setColor(51/255f, 51/255f, 51/255f, 1); // #333333
        bgPixmap.fill();
        style.background = new TextureRegionDrawable(new TextureRegion(new Texture(bgPixmap)));
        bgPixmap.dispose();

        // Create foreground bar
        Pixmap fgPixmap = new Pixmap(32, 14, Pixmap.Format.RGBA8888);

        // Set color based on percentage
        if (percentage > 0.5f) {
            fgPixmap.setColor(71/255f, 201/255f, 93/255f, 1);  // #47C95D
        } else if (percentage > 0.2f) {
            fgPixmap.setColor(255/255f, 217/255f, 0/255f, 1);  // #FFD900
        } else {
            fgPixmap.setColor(255/255f, 57/255f, 57/255f, 1);  // #FF3939
        }

        fgPixmap.fill();
        style.knob = new TextureRegionDrawable(new TextureRegion(new Texture(fgPixmap)));
        style.knobBefore = style.knob;
        fgPixmap.dispose();

        return style;
    }


    private void createFallbackBackgrounds() {
        // Create simple colored backgrounds as fallback
        Pixmap bgPixmap = new Pixmap(384, 128, Pixmap.Format.RGBA8888);
        bgPixmap.setColor(0, 0, 0, 0.8f);
        bgPixmap.fill();
        battleBackground = new TextureRegionDrawable(new TextureRegion(new Texture(bgPixmap)));
        menuBackground = battleBackground;
        bgPixmap.dispose();
    }

    private void createFallbackButtons() {
        // Create simple button backgrounds as fallback
        Pixmap buttonPixmap = new Pixmap(192, 48, Pixmap.Format.RGBA8888);
        buttonPixmap.setColor(0.3f, 0.3f, 0.3f, 1);
        buttonPixmap.fill();
        buttonNormal = new TextureRegionDrawable(new TextureRegion(new Texture(buttonPixmap)));

        buttonPixmap.setColor(0.4f, 0.4f, 0.4f, 1);
        buttonPixmap.fill();
        buttonPressed = new TextureRegionDrawable(new TextureRegion(new Texture(buttonPixmap)));
        buttonPixmap.dispose();
    }



}
