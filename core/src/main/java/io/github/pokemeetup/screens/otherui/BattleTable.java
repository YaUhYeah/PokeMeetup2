package io.github.pokemeetup.screens.otherui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.actions.SequenceAction;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.FitViewport;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.PokemonCaptureAnimation;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.pokemon.attacks.Move;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;

import java.util.HashMap;

public class BattleTable extends Table {
    public static final int STATUS_ICON_WIDTH = 44;
    public static final int STATUS_ICON_HEIGHT = 16;
    // Constants for layout and animation
    private static final float BATTLE_SCREEN_WIDTH = 800f;
    private static final float BATTLE_SCREEN_HEIGHT = 480f;
    private static final float HP_BAR_WIDTH = 100f;
    private static final float ANIMATION_DURATION = 0.5f;
    private static final float DAMAGE_FLASH_DURATION = 0.1f;
    private static final HashMap<Pokemon.PokemonType, Color> TYPE_COLORS = new HashMap<Pokemon.PokemonType, Color>() {{
        put(Pokemon.PokemonType.FIRE, new Color(1, 0.3f, 0.3f, 1));
        put(Pokemon.PokemonType.WATER, new Color(0.2f, 0.6f, 1, 1));
        put(Pokemon.PokemonType.GRASS, new Color(0.2f, 0.8f, 0.2f, 1));
        put(Pokemon.PokemonType.NORMAL, new Color(0.8f, 0.8f, 0.8f, 1));
        put(Pokemon.PokemonType.ELECTRIC, new Color(1, 0.9f, 0.3f, 1));
        put(Pokemon.PokemonType.ICE, new Color(0.6f, 0.9f, 1, 1));
        put(Pokemon.PokemonType.FIGHTING, new Color(0.8f, 0.3f, 0.2f, 1));
        put(Pokemon.PokemonType.POISON, new Color(0.6f, 0.3f, 0.6f, 1));
        put(Pokemon.PokemonType.GROUND, new Color(0.9f, 0.7f, 0.3f, 1));
        put(Pokemon.PokemonType.FLYING, new Color(0.6f, 0.6f, 1, 1));
        put(Pokemon.PokemonType.PSYCHIC, new Color(1, 0.3f, 0.6f, 1));
        put(Pokemon.PokemonType.BUG, new Color(0.6f, 0.8f, 0.3f, 1));
        put(Pokemon.PokemonType.ROCK, new Color(0.7f, 0.6f, 0.3f, 1));
        put(Pokemon.PokemonType.GHOST, new Color(0.4f, 0.3f, 0.6f, 1));
        put(Pokemon.PokemonType.DRAGON, new Color(0.5f, 0.3f, 1, 1));
        put(Pokemon.PokemonType.DARK, new Color(0.4f, 0.3f, 0.3f, 1));
        put(Pokemon.PokemonType.STEEL, new Color(0.7f, 0.7f, 0.8f, 1));
        put(Pokemon.PokemonType.FAIRY, new Color(1, 0.6f, 0.8f, 1));
    }};// Fine-tuned positioning constants// Update these constants for better positioning
    private static ObjectMap<Pokemon.PokemonType, ObjectMap<Pokemon.PokemonType, Float>> typeEffectiveness = new ObjectMap<>();

    static {
        typeEffectiveness = new ObjectMap<>();
        for (Pokemon.PokemonType type : Pokemon.PokemonType.values()) {
            typeEffectiveness.put(type, new ObjectMap<>());
            for (Pokemon.PokemonType defType : Pokemon.PokemonType.values()) {
                typeEffectiveness.get(type).put(defType, 1.0f); // Default effectiveness
            }
        }

        // Normal type
        initTypeEffectiveness(Pokemon.PokemonType.NORMAL, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.ROCK, 0.5f);
            put(Pokemon.PokemonType.GHOST, 0.0f);
            put(Pokemon.PokemonType.STEEL, 0.5f);
        }});

        // Fire type
        initTypeEffectiveness(Pokemon.PokemonType.FIRE, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.FIRE, 0.5f);
            put(Pokemon.PokemonType.WATER, 0.5f);
            put(Pokemon.PokemonType.GRASS, 2.0f);
            put(Pokemon.PokemonType.ICE, 2.0f);
            put(Pokemon.PokemonType.BUG, 2.0f);
            put(Pokemon.PokemonType.ROCK, 0.5f);
            put(Pokemon.PokemonType.DRAGON, 0.5f);
            put(Pokemon.PokemonType.STEEL, 2.0f);
        }});

        // Water type
        initTypeEffectiveness(Pokemon.PokemonType.WATER, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.FIRE, 2.0f);
            put(Pokemon.PokemonType.WATER, 0.5f);
            put(Pokemon.PokemonType.GRASS, 0.5f);
            put(Pokemon.PokemonType.GROUND, 2.0f);
            put(Pokemon.PokemonType.ROCK, 2.0f);
            put(Pokemon.PokemonType.DRAGON, 0.5f);
        }});

        // Electric type
        initTypeEffectiveness(Pokemon.PokemonType.ELECTRIC, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.WATER, 2.0f);
            put(Pokemon.PokemonType.ELECTRIC, 0.5f);
            put(Pokemon.PokemonType.GRASS, 0.5f);
            put(Pokemon.PokemonType.GROUND, 0.0f);
            put(Pokemon.PokemonType.FLYING, 2.0f);
            put(Pokemon.PokemonType.DRAGON, 0.5f);
        }});

        // Grass type
        initTypeEffectiveness(Pokemon.PokemonType.GRASS, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.FIRE, 0.5f);
            put(Pokemon.PokemonType.WATER, 2.0f);
            put(Pokemon.PokemonType.GRASS, 0.5f);
            put(Pokemon.PokemonType.POISON, 0.5f);
            put(Pokemon.PokemonType.GROUND, 2.0f);
            put(Pokemon.PokemonType.FLYING, 0.5f);
            put(Pokemon.PokemonType.BUG, 0.5f);
            put(Pokemon.PokemonType.ROCK, 2.0f);
            put(Pokemon.PokemonType.DRAGON, 0.5f);
            put(Pokemon.PokemonType.STEEL, 0.5f);
        }});

        // Ice type
        initTypeEffectiveness(Pokemon.PokemonType.ICE, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.FIRE, 0.5f);
            put(Pokemon.PokemonType.WATER, 0.5f);
            put(Pokemon.PokemonType.GRASS, 2.0f);
            put(Pokemon.PokemonType.ICE, 0.5f);
            put(Pokemon.PokemonType.GROUND, 2.0f);
            put(Pokemon.PokemonType.FLYING, 2.0f);
            put(Pokemon.PokemonType.DRAGON, 2.0f);
            put(Pokemon.PokemonType.STEEL, 0.5f);
        }});

        // Fighting type
        initTypeEffectiveness(Pokemon.PokemonType.FIGHTING, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.NORMAL, 2.0f);
            put(Pokemon.PokemonType.ICE, 2.0f);
            put(Pokemon.PokemonType.POISON, 0.5f);
            put(Pokemon.PokemonType.FLYING, 0.5f);
            put(Pokemon.PokemonType.PSYCHIC, 0.5f);
            put(Pokemon.PokemonType.BUG, 0.5f);
            put(Pokemon.PokemonType.ROCK, 2.0f);
            put(Pokemon.PokemonType.GHOST, 0.0f);
            put(Pokemon.PokemonType.DARK, 2.0f);
            put(Pokemon.PokemonType.STEEL, 2.0f);
            put(Pokemon.PokemonType.FAIRY, 0.5f);
        }});
        initTypeEffectiveness(Pokemon.PokemonType.POISON, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.GRASS, 2.0f);
            put(Pokemon.PokemonType.POISON, 0.5f);
            put(Pokemon.PokemonType.GROUND, 0.5f);
            put(Pokemon.PokemonType.ROCK, 0.5f);
            put(Pokemon.PokemonType.GHOST, 0.5f);
            put(Pokemon.PokemonType.STEEL, 0.0f);
            put(Pokemon.PokemonType.FAIRY, 2.0f);
        }});

        // Ground type
        initTypeEffectiveness(Pokemon.PokemonType.GROUND, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.FIRE, 2.0f);
            put(Pokemon.PokemonType.ELECTRIC, 2.0f);
            put(Pokemon.PokemonType.GRASS, 0.5f);
            put(Pokemon.PokemonType.POISON, 2.0f);
            put(Pokemon.PokemonType.FLYING, 0.0f);
            put(Pokemon.PokemonType.BUG, 0.5f);
            put(Pokemon.PokemonType.ROCK, 2.0f);
            put(Pokemon.PokemonType.STEEL, 2.0f);
        }});

        // Flying type
        initTypeEffectiveness(Pokemon.PokemonType.FLYING, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.ELECTRIC, 0.5f);
            put(Pokemon.PokemonType.GRASS, 2.0f);
            put(Pokemon.PokemonType.FIGHTING, 2.0f);
            put(Pokemon.PokemonType.BUG, 2.0f);
            put(Pokemon.PokemonType.ROCK, 0.5f);
            put(Pokemon.PokemonType.STEEL, 0.5f);
        }});

        // Psychic type
        initTypeEffectiveness(Pokemon.PokemonType.PSYCHIC, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.FIGHTING, 2.0f);
            put(Pokemon.PokemonType.POISON, 2.0f);
            put(Pokemon.PokemonType.PSYCHIC, 0.5f);
            put(Pokemon.PokemonType.DARK, 0.0f);
            put(Pokemon.PokemonType.STEEL, 0.5f);
        }});

        // Bug type
        initTypeEffectiveness(Pokemon.PokemonType.BUG, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.FIRE, 0.5f);
            put(Pokemon.PokemonType.GRASS, 2.0f);
            put(Pokemon.PokemonType.FIGHTING, 0.5f);
            put(Pokemon.PokemonType.POISON, 0.5f);
            put(Pokemon.PokemonType.FLYING, 0.5f);
            put(Pokemon.PokemonType.PSYCHIC, 2.0f);
            put(Pokemon.PokemonType.GHOST, 0.5f);
            put(Pokemon.PokemonType.DARK, 2.0f);
            put(Pokemon.PokemonType.STEEL, 0.5f);
            put(Pokemon.PokemonType.FAIRY, 0.5f);
        }});

        // Rock type
        initTypeEffectiveness(Pokemon.PokemonType.ROCK, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.FIRE, 2.0f);
            put(Pokemon.PokemonType.ICE, 2.0f);
            put(Pokemon.PokemonType.FIGHTING, 0.5f);
            put(Pokemon.PokemonType.GROUND, 0.5f);
            put(Pokemon.PokemonType.FLYING, 2.0f);
            put(Pokemon.PokemonType.BUG, 2.0f);
            put(Pokemon.PokemonType.STEEL, 0.5f);
        }});

        // Ghost type
        initTypeEffectiveness(Pokemon.PokemonType.GHOST, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.NORMAL, 0.0f);
            put(Pokemon.PokemonType.PSYCHIC, 2.0f);
            put(Pokemon.PokemonType.GHOST, 2.0f);
            put(Pokemon.PokemonType.DARK, 0.5f);
        }});

        // Dragon type
        initTypeEffectiveness(Pokemon.PokemonType.DRAGON, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.DRAGON, 2.0f);
            put(Pokemon.PokemonType.STEEL, 0.5f);
            put(Pokemon.PokemonType.FAIRY, 0.0f);
        }});

        // Dark type
        initTypeEffectiveness(Pokemon.PokemonType.DARK, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.FIGHTING, 0.5f);
            put(Pokemon.PokemonType.PSYCHIC, 2.0f);
            put(Pokemon.PokemonType.GHOST, 2.0f);
            put(Pokemon.PokemonType.DARK, 0.5f);
            put(Pokemon.PokemonType.FAIRY, 0.5f);
        }});

        // Steel type
        initTypeEffectiveness(Pokemon.PokemonType.STEEL, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.FIRE, 0.5f);
            put(Pokemon.PokemonType.WATER, 0.5f);
            put(Pokemon.PokemonType.ELECTRIC, 0.5f);
            put(Pokemon.PokemonType.ICE, 2.0f);
            put(Pokemon.PokemonType.ROCK, 2.0f);
            put(Pokemon.PokemonType.STEEL, 0.5f);
            put(Pokemon.PokemonType.FAIRY, 2.0f);
        }});

        // Fairy type
        initTypeEffectiveness(Pokemon.PokemonType.FAIRY, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.FIRE, 0.5f);
            put(Pokemon.PokemonType.FIGHTING, 2.0f);
            put(Pokemon.PokemonType.POISON, 0.5f);
            put(Pokemon.PokemonType.DRAGON, 2.0f);
            put(Pokemon.PokemonType.DARK, 2.0f);
            put(Pokemon.PokemonType.STEEL, 0.5f);
        }});
    }

    // Instance fields
    private final Stage stage;
    private final Skin skin;
    private final Pokemon enemyPokemon;
    private Pokemon playerPokemon;
    private TextureRegion platformTexture;
    private Image playerPlatform, enemyPlatform;
    private Image playerPokemonImage, enemyPokemonImage;
    private ProgressBar playerHPBar, enemyHPBar;
    private Label battleText;
    private Table actionMenu;
    private TextButton fightButton, bagButton, pokemonButton, runButton;
    private BattleState currentState;
    private BattleCallback callback;
    private float stateTimer = 0;
    private boolean isAnimating = false;
    private ProgressBar expBar;

    // ––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––
    // SKIN STYLE REGISTRATION FOR HP BARS
    // ––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––
    private boolean moveSelectionActive = false;
    private Image playerStatusIcon;
    private Image enemyStatusIcon;

    // –––––––––––––––––––
    // CONSTRUCTOR
    // –––––––––––––––––––
    public BattleTable(Stage stage, Skin skin, Pokemon playerPokemon, Pokemon enemyPokemon) {
        super();
        this.stage = stage;
        this.skin = skin;
        this.playerPokemon = playerPokemon;
        this.enemyPokemon = enemyPokemon;
        this.currentState = BattleState.INTRO;
        setFillParent(true);
        setTouchable(Touchable.enabled);
        setZIndex(100);
        stage.addActor(this);
        stage.setViewport(new FitViewport(BATTLE_SCREEN_WIDTH, BATTLE_SCREEN_HEIGHT));
        stage.getViewport().update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);

        // Ensure the skin contains our HP bar styles so updateHPBarColor() won’t fail
        ensureHPBarStyles();

        // Initialize battle assets – textures, UI components, platforms, Pokémon sprites, HP bars, HUD, etc.
        try {
            initializeTextures();
            initializeUIComponents();
            initializePlatforms();
            initializePokemonSprites();
            setupHPBars();
            // (Other initialization such as HUD and move menus may follow here.)
            setupContainer();
            startBattleAnimation();
        } catch (Exception e) {
            GameLogger.error("Error initializing battle table: " + e.getMessage());
        }
    }

    private static void initTypeEffectiveness(Pokemon.PokemonType attackType,
                                              ObjectMap<Pokemon.PokemonType, Float> effectiveness) {
        typeEffectiveness.get(attackType).putAll(effectiveness);

    }

    // Inside your BattleTable class

    /**
     * Creates a ProgressBar style with a background and a colored knob. The color of the knob
     * is chosen based on the given “percentage” value:
     * – > 0.5 uses green,
     * – between 0.2 and 0.5 uses yellow,
     * – below 0.2 uses red.
     */
    private static ProgressBar.ProgressBarStyle createHPBarStyle(float percentage) {
        ProgressBar.ProgressBarStyle style = new ProgressBar.ProgressBarStyle();

        // Background drawable
        Pixmap bgPixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        bgPixmap.setColor(0.2f, 0.2f, 0.2f, 0.8f);
        bgPixmap.fill();
        Texture bgTexture = new Texture(bgPixmap);
        style.background = new TextureRegionDrawable(new TextureRegion(bgTexture));
        bgPixmap.dispose();

        // Foreground (knob) drawable
        Pixmap fgPixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        Color barColor;
        if (percentage > 0.5f) {
            barColor = new Color(0.2f, 0.8f, 0.2f, 1f); // Green
        } else if (percentage > 0.2f) {
            barColor = new Color(0.9f, 0.9f, 0.2f, 1f); // Yellow
        } else {
            barColor = new Color(0.8f, 0.2f, 0.2f, 1f); // Red
        }
        fgPixmap.setColor(barColor);
        fgPixmap.fill();
        Texture fgTexture = new Texture(fgPixmap);
        TextureRegionDrawable knobDrawable = new TextureRegionDrawable(new TextureRegion(fgTexture));
        style.knob = knobDrawable;
        style.knobBefore = knobDrawable;
        fgPixmap.dispose();

        return style;
    }

    public void showMoveReplacementDialog(final Move newMove) {
        final Table replacementTable = new Table(skin);
        replacementTable.setBackground(createTranslucentBackground(0.7f));
        replacementTable.defaults().pad(10);

        Label promptLabel = new Label("Select a move to replace with " + newMove.getName() + ":", skin);
        promptLabel.setWrap(true);
        promptLabel.setAlignment(Align.center);
        replacementTable.add(promptLabel).colspan(2).row();

        final java.util.List<Move> currentMoves = playerPokemon.getMoves();
        // For each current move, create a button.
        for (int i = 0; i < currentMoves.size(); i++) {
            final int index = i;
            TextButton moveButton = new TextButton(currentMoves.get(i).getName(), skin);
            moveButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    // Replace the selected move with the new move.
                    currentMoves.set(index, newMove);
                    battleText.setText(playerPokemon.getName() + " learned " + newMove.getName() + "!");
                    // Optionally play a “new move” sound:
                    replacementTable.remove();
                }
            });
            replacementTable.add(moveButton).width(150).height(40);
            if ((i + 1) % 2 == 0) {
                replacementTable.row();
            }
        }
        // A cancel button lets the player opt out; in this example we auto–replace the first move.
        TextButton cancelButton = new TextButton("Cancel", skin);
        cancelButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                // Fallback: remove the first move and add the new one.
                currentMoves.remove(0);
                currentMoves.add(newMove);
                battleText.setText(playerPokemon.getName() + " learned " + newMove.getName() + " by replacing an old move!");
                replacementTable.remove();
            }
        });
        replacementTable.add(cancelButton).colspan(2).width(150).height(40);

        replacementTable.pack();
        // Center the table on the battle screen.
        replacementTable.setPosition((getWidth() - replacementTable.getWidth()) / 2,
            (getHeight() - replacementTable.getHeight()) / 2);
        addActor(replacementTable);
    }

    public void displayMessage(String message) {
        // Set the battle text label.
        battleText.setText(message);
        // Optionally, you can add an action to fade the text out after a delay.
        battleText.clearActions();
        battleText.addAction(Actions.sequence(
            Actions.delay(2.0f),
            Actions.fadeOut(1.0f),
            Actions.run(() -> {
                battleText.setText(""); // Clear message after fade
                battleText.getColor().a = 1f; // Reset alpha for next message
            })
        ));
    }

    /**
     * Ensures that the skin contains ProgressBar styles for HP bars (green, yellow, red).
     */
    private void ensureHPBarStyles() {
        if (!skin.has("hp-bar-green", ProgressBar.ProgressBarStyle.class)) {
            skin.add("hp-bar-green", createHPBarStyle(1.0f), ProgressBar.ProgressBarStyle.class);
        }
        if (!skin.has("hp-bar-yellow", ProgressBar.ProgressBarStyle.class)) {
            skin.add("hp-bar-yellow", createHPBarStyle(0.35f), ProgressBar.ProgressBarStyle.class);
        }
        if (!skin.has("hp-bar-red", ProgressBar.ProgressBarStyle.class)) {
            skin.add("hp-bar-red", createHPBarStyle(0.1f), ProgressBar.ProgressBarStyle.class);
        }
    }

    /**
     * Updates the HP bar’s style based on the current percentage. If the percentage is high,
     * it uses the “hp-bar-green” style; medium uses “hp-bar-yellow” and low uses “hp-bar-red”.
     */
    private void updateHPBarColor(ProgressBar bar, float percentage) {
        String styleKey;
        if (percentage > 0.5f) {
            styleKey = "hp-bar-green";
        } else if (percentage > 0.2f) {
            styleKey = "hp-bar-yellow";
        } else {
            styleKey = "hp-bar-red";
        }
        // Use the registered style from the skin
        bar.setStyle(skin.get(styleKey, ProgressBar.ProgressBarStyle.class));
    }

    /**
     * Enables or disables the battle interface by setting the touchability and visibility
     * of the action menu and its buttons.
     */
    private void setBattleInterfaceEnabled(boolean enabled) {
        if (actionMenu != null) {
            actionMenu.setTouchable(enabled ? Touchable.enabled : Touchable.disabled);
            actionMenu.setVisible(enabled);
        }
        if (fightButton != null) {
            fightButton.setDisabled(!enabled);
            fightButton.setTouchable(enabled ? Touchable.enabled : Touchable.disabled);
        }
        if (bagButton != null) {
            bagButton.setDisabled(!enabled);
            bagButton.setTouchable(enabled ? Touchable.enabled : Touchable.disabled);
        }
        if (pokemonButton != null) {
            pokemonButton.setDisabled(!enabled);
            pokemonButton.setTouchable(enabled ? Touchable.enabled : Touchable.disabled);
        }
        if (runButton != null) {
            runButton.setDisabled(!enabled);
            runButton.setTouchable(enabled ? Touchable.enabled : Touchable.disabled);
        }
    }

    /**
     * Loads the required textures for the battle scene. Here we load the battle platform
     * texture from the TextureManager. You can add more textures (for backgrounds, etc.)
     * as needed.
     */
    private void initializeTextures() {
        platformTexture = TextureManager.battlebacks.findRegion("battle_platform");
        if (platformTexture == null) {
            throw new RuntimeException("Failed to load battle platform texture");
        }
    }

    /**
     * Initializes the UI components for battle.
     * The action menu containing the four buttons is now positioned using the local
     * BattleTable coordinates and brought to the front.
     */
// Inside io.github.pokemeetup.screens.otherui.BattleTable
    private void initializeUIComponents() {
        // Create battle text label.
        battleText = new Label("", skin);
        battleText.setWrap(true);
        battleText.setAlignment(Align.center);
        battleText.setTouchable(Touchable.disabled);
        float tableWidth = (getWidth() > 0 ? getWidth() : BATTLE_SCREEN_WIDTH);
        float tableHeight = (getHeight() > 0 ? getHeight() : BATTLE_SCREEN_HEIGHT);
        battleText.setSize(tableWidth, 30);
        battleText.setPosition(0, tableHeight - 40);

        // Create action menu.
        actionMenu = new Table(skin);
        actionMenu.setBackground(createTranslucentBackground(0.5f));
        actionMenu.defaults().space(10);

        // Create buttons.
        fightButton = new TextButton("FIGHT", skin);
        bagButton = new TextButton("BAG", skin);
        pokemonButton = new TextButton("POKEMON", skin);
        runButton = new TextButton("RUN", skin);

        // Add listeners.
        fightButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                handleFightButton();
            }
        });

        bagButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                // Hide the battle table's action menu.
                if (actionMenu != null) {
                    actionMenu.setVisible(false);
                    actionMenu.setTouchable(Touchable.disabled);
                }
                Stage stage = getStage();
                BagScreen bagScreen = new BagScreen(skin,
                    GameContext.get().getPlayer().getInventory(),
                    (ItemData selectedItem) -> {
                        if (!selectedItem.getItemId().toLowerCase().contains("pokeball")) {
                            battleText.setText("Please select a Pokéball for capturing!");
                            return;
                        }
                        selectedItem.setCount(selectedItem.getCount() - 1);
                        if (selectedItem.getCount() <= 0) {
                            GameContext.get().getPlayer().getInventory().removeItem(selectedItem);
                        }
                        float hpRatio = enemyPokemon.getCurrentHp() / (float) enemyPokemon.getStats().getHp();
                        float captureChance = MathUtils.clamp(1 - hpRatio, 0.1f, 0.9f);
                        attemptCapture((WildPokemon) enemyPokemon, captureChance);
                    }
                );
                bagScreen.setOnClose(() -> {
                    showActionMenu(true);
                });
                stage.addActor(bagScreen);
                bagScreen.pack();
                float w = stage.getWidth();
                float h = stage.getHeight();
                bagScreen.setPosition((w - bagScreen.getWidth()) / 2f,
                    (h - bagScreen.getHeight()) / 2f);
                bagScreen.toFront();
                bagScreen.setZIndex(stage.getActors().size - 1);
            }
        });

        runButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                attemptRun();
            }
        });

        // *** Pokémon Button Listener ***
        pokemonButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                // Hide the action menu.
                if (actionMenu != null) {
                    actionMenu.setVisible(false);
                    actionMenu.setTouchable(Touchable.disabled);
                }
                // Show the party screen.
                showPartyScreen();
            }
        });

        // Arrange buttons in a 2x2 grid.
        actionMenu.add(fightButton).width(180).height(50);
        actionMenu.add(bagButton).width(180).height(50);
        actionMenu.row();
        actionMenu.add(pokemonButton).width(180).height(50);
        actionMenu.add(runButton).width(180).height(50);
        actionMenu.pack();

        // Update the action menu's position based on the BattleTable’s size.
        updateActionMenuPosition();

        // Initially hide the menu.
        actionMenu.setVisible(false);
        actionMenu.setTouchable(Touchable.disabled);

        addActor(battleText);
        addActor(actionMenu);
    }


    private void showPartyScreen() {
        // Create and show party screen
        PokemonPartyWindow partyScreen = new PokemonPartyWindow(
            skin,
            GameContext.get().getPlayer().getPokemonParty(),
            true, // battle mode
            (selectedPokemon) -> {  // <-- Now only one parameter!
                if (selectedPokemon.getCurrentHp() > 0) {
                    // Switch Pokemon
                    switchPokemon(selectedPokemon);
                }
            },
            () -> {
                // Return to battle menu
                showActionMenu(true);
            }
        );

        stage.addActor(partyScreen);
        partyScreen.show(stage);
    }


    private void switchPokemon(Pokemon newPokemon) {
        if (newPokemon == playerPokemon) {
            battleText.setText("This Pokemon is already in battle!");
            return;
        }

        // Handle switch animation and logic
        SequenceAction switchSequence = Actions.sequence(
            Actions.run(() -> {
                battleText.setText(playerPokemon.getName() + ", come back!");
                // Play return sound
                AudioManager.getInstance().playSound(AudioManager.SoundEffect.POKEMON_RETURN);
            }),
            Actions.fadeOut(0.5f),
            Actions.run(() -> {
                playerPokemon = newPokemon;
                updatePlayerPokemonDisplay();
            }),
            Actions.fadeIn(0.5f),
            Actions.run(() -> {
                battleText.setText("Go! " + newPokemon.getName() + "!");
                // Play send out sound
                AudioManager.getInstance().playSound(AudioManager.SoundEffect.POKEMON_SENDOUT);
            }),
            Actions.run(() -> {
                // Enemy's turn after switch
                transitionToState(BattleState.ENEMY_TURN);
            })
        );

        playerPokemonImage.addAction(switchSequence);
    }

    /**
     * Updates the player's Pokémon display components after a switch
     * This includes the sprite, HP bar, name label, and any status effects
     */
    private void updatePlayerPokemonDisplay() {
        // Update sprite
        TextureRegion newTexture = playerPokemon.getBackSprite();
        if (newTexture != null) {
            playerPokemonImage.setDrawable(new TextureRegionDrawable(newTexture));
            // Maintain consistent sizing
            float aspect = (float) newTexture.getRegionWidth() / newTexture.getRegionHeight();
            float baseSize = 85f;
            playerPokemonImage.setSize(baseSize * aspect, baseSize);
        }

        // Update HP bar
        playerHPBar.setRange(0, playerPokemon.getStats().getHp());
        playerHPBar.setValue(playerPokemon.getCurrentHp());
        updateHPBarColor(playerHPBar, playerPokemon.getCurrentHp() / (float) playerPokemon.getStats().getHp());

        // Update info label with new Pokémon's details
        for (Actor actor : getChildren()) {
            if (actor instanceof Table) {
                Table mainContainer = (Table) actor;
                for (Actor child : mainContainer.getChildren()) {
                    if (child instanceof Table) {
                        Table section = (Table) child;
                        for (Actor label : section.getChildren()) {
                            if (label instanceof Label) {
                                Label infoLabel = (Label) label;
                                if (infoLabel.getText().toString().contains("Lv.")) {
                                    infoLabel.setText(
                                        playerPokemon.getName() + " Lv. " + playerPokemon.getLevel() +
                                            " (" + playerPokemon.getCurrentHp() + "/" + playerPokemon.getStats().getHp() + ")"
                                    );
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

        // Update exp bar for new Pokémon
        expBar.setRange(0, playerPokemon.getExperienceForNextLevel());
        expBar.setValue(playerPokemon.getCurrentExperience());

        // Update status icon
        updateStatusIcon(playerPokemon, playerStatusIcon);

        // Force layout update
        invalidate();

        // Center the new Pokémon image on the platform
        centerPlayerPokemon();
    }
    private void centerPlayerPokemon() {
        if (playerPlatform != null && playerPokemonImage != null) {
            // Calculate horizontal and vertical offsets so that the Pokemon image is centered
            float offsetX = (playerPlatform.getWidth() - playerPokemonImage.getWidth()) / 2f;
            float offsetY = (playerPlatform.getHeight() - playerPokemonImage.getHeight()) / 2f;
            playerPokemonImage.setPosition(offsetX, offsetY);
        }
    }


    private void attemptRun() {
        if (isAnimating) return;  // Don’t allow multiple run attempts during an animation

        float runChance = 0.8f;  // Example: 80% chance to run successfully
        if (MathUtils.random() < runChance) {
            battleText.setText("Got away safely!");
            // Create a sequence action to delay a bit before cleaning up the battle.
            SequenceAction escapeSequence = Actions.sequence(
                Actions.delay(1.0f),
                Actions.run(() -> {
                    // Notify any callback that the battle ended with a successful run.
                    if (callback != null) {
                        callback.onBattleEnd(false);
                    }
                    cleanup();
                    remove();  // Remove the battle table from the stage.
                })
            );
            addAction(escapeSequence);
            isAnimating = true;
        } else {
            battleText.setText("Can't escape!");
            // Let the enemy take its turn by transitioning to ENEMY_TURN.
            transitionToState(BattleState.ENEMY_TURN);
        }
    }

    public void attemptCapture(WildPokemon wildPokemon, float captureChance) {
        if (currentState == BattleState.CATCHING) return; // already capturing

        currentState = BattleState.CATCHING;
        setBattleInterfaceEnabled(false);
        battleText.setText("Throwing Pokéball...");

        // Convert the centers of the Pokémon images into stage coordinates.
        Vector2 playerCenter = playerPokemonImage.localToStageCoordinates(new Vector2(
            playerPokemonImage.getWidth() / 2, playerPokemonImage.getHeight() / 2));
        Vector2 enemyCenter = enemyPokemonImage.localToStageCoordinates(new Vector2(
            enemyPokemonImage.getWidth() / 2, enemyPokemonImage.getHeight() / 2));

        float throwDuration = 0.8f;
        TextureAtlas capsuleThrowAtlas = TextureManager.capsuleThrow;

        PokemonCaptureAnimation captureAnimation = new PokemonCaptureAnimation(
            capsuleThrowAtlas, playerCenter, enemyCenter, throwDuration, captureChance,
            success -> {
                if (success) {
                    battleText.setText("Gotcha! " + wildPokemon.getName() + " was caught!");
                    GameContext.get().getPlayer().getPokemonParty().addPokemon(wildPokemon);
                    // Update the party display from the main GameScreen.
                    if (GameContext.get().getGameScreen() != null) {
                        GameContext.get().getGameScreen().updatePartyDisplay();
                    }
                    addAction(Actions.sequence(
                        Actions.delay(1.0f),
                        Actions.run(() -> {
                            if (callback != null) callback.onBattleEnd(true);
                        })
                    ));
                } else {
                    battleText.setText(wildPokemon.getName() + " broke free!");
                    // Transition to enemy turn when capture fails.
                    transitionToState(BattleState.ENEMY_TURN);
                }
            }
        , enemyPokemonImage);
        addActor(captureAnimation);
    }


    private void handleFightButton() {
        // Hide the main action menu immediately:
        if (actionMenu != null) {
            actionMenu.setVisible(false);
            actionMenu.setTouchable(Touchable.disabled);
        }
        // Set flag to indicate that move selection is active.
        moveSelectionActive = true;
        // Then show the move selection UI:
        showMoveSelection();
    }

    /**
     * Helper method to create a translucent background drawable.
     */
    private TextureRegionDrawable createTranslucentBackground(float alpha) {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(0, 0, 0, alpha);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        TextureRegion region = new TextureRegion(texture);
        pixmap.dispose();
        return new TextureRegionDrawable(region);
    }

    private void setupContainer() {
        Table mainContainer = new Table();
        mainContainer.setFillParent(true);
        mainContainer.top().padTop(10);
        mainContainer.setBackground(createTranslucentBackground(0.5f));

        // Enemy section.
        Table enemySection = new Table();
        // Create a label that shows the enemy’s name and level (for example: "Pikachu Lv. 5")
        Label enemyInfoLabel = new Label(enemyPokemon.getName() + " Lv. " + enemyPokemon.getLevel(), skin);
        enemySection.add(enemyInfoLabel).expandX().right().pad(10).row();
        enemySection.add(enemyHPBar).expandX().right().pad(10).row();
        Stack enemyStack = new Stack();
        enemyStack.add(enemyPlatform);
        enemyStack.add(enemyPokemonImage);
        enemySection.add(enemyStack).expand().right().padRight(stage.getWidth() * 0.1f).row();

        // Player section.
        Table playerSection = new Table();
        // Create a label that shows the player's Pokémon name, level, and current HP/total HP.
        // For example: "Charmander Lv. 7 (18/39)"
        Label playerInfoLabel = new Label(
            playerPokemon.getName() + " Lv. " + playerPokemon.getLevel() +
                " (" + playerPokemon.getCurrentHp() + "/" + playerPokemon.getStats().getHp() + ")",
            skin);
        playerSection.add(playerInfoLabel).expandX().left().pad(10).row();
        playerSection.add(playerHPBar).expandX().left().pad(10).row();
        Stack playerStack = new Stack();
        playerStack.add(playerPlatform);
        playerStack.add(playerPokemonImage);
        playerSection.add(playerStack).expand().left().padLeft(stage.getWidth() * 0.1f).row();

        // Control section: displays battle text.
        Table controlSection = new Table();
        controlSection.setBackground(createTranslucentBackground(0.7f));
        controlSection.add(battleText).expandX().fillX().pad(10).row();
        controlSection.setTouchable(Touchable.enabled);

        mainContainer.add(enemySection).expand().fill().row();
        mainContainer.add(playerSection).expand().fill().row();
        mainContainer.add(controlSection).expandX().fillX().bottom().padBottom(20f);

        addActor(mainContainer);
    }

    private void initializePlatforms() {
        playerPlatform = new Image(platformTexture);
        enemyPlatform = new Image(platformTexture);
        // Ensure no scaling is applied here
        playerPlatform.setScaling(Scaling.none);
        enemyPlatform.setScaling(Scaling.none);
        // Their positions will be set later in updateLayout()
    }

    /**
     * Computes an expected damage value (without random variation) for a move.
     * This is used by the enemy AI to choose the move that is most effective.
     */
    private float computeExpectedDamage(Move move, Pokemon attacker, Pokemon defender) {
        int level = attacker.getLevel();
        float attackStat = move.isSpecial() ? attacker.getStats().getSpecialAttack() : attacker.getStats().getAttack();
        float defenseStat = move.isSpecial() ? defender.getStats().getSpecialDefense() : defender.getStats().getDefense();

        float baseDamage = (((2 * level) / 5f + 2) * move.getPower() * attackStat / defenseStat) / 50f + 2;

        float stab = (attacker.getPrimaryType() == move.getType() ||
            attacker.getSecondaryType() == move.getType()) ? 1.5f : 1.0f;

        float typeMultiplier = getTypeEffectiveness(move.getType(), defender.getPrimaryType());
        if (defender.getSecondaryType() != null) {
            typeMultiplier *= getTypeEffectiveness(move.getType(), defender.getSecondaryType());
        }

        // For expected damage, we can assume a neutral random factor of 1.
        return baseDamage * stab * typeMultiplier;
    }


    private void initializePokemonSprites() {
        TextureRegion playerTexture = playerPokemon.getBackSprite();
        TextureRegion enemyTexture = enemyPokemon.getFrontSprite();
        if (playerTexture == null || enemyTexture == null) {
            throw new RuntimeException("Failed to load Pokémon sprites");
        }
        playerPokemonImage = new Image(playerTexture);
        enemyPokemonImage = new Image(enemyTexture);
        // Set sizes keeping aspect ratio – adjust as needed
        float playerAspect = (float) playerTexture.getRegionWidth() / playerTexture.getRegionHeight();
        float enemyAspect = (float) enemyTexture.getRegionWidth() / enemyTexture.getRegionHeight();
        float baseSize = 85f;
        playerPokemonImage.setSize(baseSize * playerAspect, baseSize);
        enemyPokemonImage.setSize(baseSize * enemyAspect, baseSize);
        playerPokemonImage.setScaling(Scaling.none);
        enemyPokemonImage.setScaling(Scaling.none);

        // Create the status icon images (initially invisible)
        playerStatusIcon = new Image();
        enemyStatusIcon = new Image();
        playerStatusIcon.setVisible(false);
        enemyStatusIcon.setVisible(false);

        // Optionally, set a fixed size for the status icons.
        playerStatusIcon.setSize(STATUS_ICON_WIDTH, STATUS_ICON_HEIGHT);
        enemyStatusIcon.setSize(STATUS_ICON_WIDTH, STATUS_ICON_HEIGHT);

        // Add them on top of the Pokémon images using a Stack:
        Stack playerStack = new Stack();
        playerStack.add(playerPlatform);
        playerStack.add(playerPokemonImage);
        // Position the status icon at the top-right corner of the sprite.
        Table playerIconTable = new Table();
        playerIconTable.setFillParent(false);
        playerIconTable.add(playerStatusIcon).size(STATUS_ICON_WIDTH, STATUS_ICON_HEIGHT).pad(5).top().right();
        playerStack.add(playerIconTable);

        Stack enemyStack = new Stack();
        enemyStack.add(enemyPlatform);
        enemyStack.add(enemyPokemonImage);
        Table enemyIconTable = new Table();
        enemyIconTable.setFillParent(false);
        enemyIconTable.add(enemyStatusIcon).size(STATUS_ICON_WIDTH, STATUS_ICON_HEIGHT).pad(5).top().right();
        enemyStack.add(enemyIconTable);

        // Then, in setupContainer(), use these stacks instead of just the images.
        // (Update your enemySection and playerSection accordingly.)
    }


    private void setupHPBars() {
        // Create health bars using the current HP values.
        playerHPBar = new ProgressBar(0, playerPokemon.getStats().getHp(), 1, false,
            createHPBarStyle(playerPokemon.getCurrentHp() / (float) playerPokemon.getStats().getHp()));
        playerHPBar.setSize(HP_BAR_WIDTH, 8);
        playerHPBar.setValue(playerPokemon.getCurrentHp());

        enemyHPBar = new ProgressBar(0, enemyPokemon.getStats().getHp(), 1, false,
            createHPBarStyle(enemyPokemon.getCurrentHp() / (float) enemyPokemon.getStats().getHp()));
        enemyHPBar.setSize(HP_BAR_WIDTH, 8);
        enemyHPBar.setValue(enemyPokemon.getCurrentHp());
        expBar = new ProgressBar(0, playerPokemon.getExperienceForNextLevel(), 1, false, skin.get("default-horizontal", ProgressBar.ProgressBarStyle.class));
        expBar.setSize(HP_BAR_WIDTH, 6);
        expBar.setPosition(playerHPBar.getX(), playerHPBar.getY() - 10);
        addActor(expBar);
    }

    public void updateExpBar() {
        int nextLevelExp = playerPokemon.getExperienceForNextLevel();
        expBar.setRange(0, nextLevelExp);
        expBar.setValue(playerPokemon.getCurrentExperience());
    }

    // ––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––
    // BATTLE LOGIC, ANIMATION, AND STATE HANDLING
    // ––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––
    private void startBattleAnimation() {
        GameLogger.info("Starting battle animation");
        isAnimating = true;
        currentState = BattleState.INTRO;
        setBattleInterfaceEnabled(false);
        SequenceAction introSequence = Actions.sequence(
            Actions.run(() -> battleText.setText("Wild " + enemyPokemon.getName() + " appeared!")),
            Actions.delay(1.0f),
            Actions.run(() -> {
                GameLogger.info("Battle intro complete – player turn begins");
                isAnimating = false;
                currentState = BattleState.PLAYER_TURN;
                battleText.setText("What will " + playerPokemon.getName() + " do?");
                setBattleInterfaceEnabled(true);
            })
        );
        addAction(introSequence);
    }

    /**
     * Updates the HP bars (both value and color) according to current HP percentages.
     */
    public void updateHPBars() {
        float playerHPPercent = playerPokemon.getCurrentHp() / (float) playerPokemon.getStats().getHp();
        playerHPBar.setValue(playerPokemon.getCurrentHp());
        updateHPBarColor(playerHPBar, playerHPPercent);

        float enemyHPPercent = enemyPokemon.getCurrentHp() / (float) enemyPokemon.getStats().getHp();
        enemyHPBar.setValue(enemyPokemon.getCurrentHp());
        updateHPBarColor(enemyHPBar, enemyHPPercent);
    }

    private void updateStatusIcon(Pokemon pokemon, Image statusIcon) {
        // Get the status from the Pokémon (which is now of type Pokemon.Status).
        Pokemon.Status status = pokemon.getStatus();
        if (status != null && status != Pokemon.Status.NONE) {
            // Retrieve the icon from the TextureManager using the Pokémon status.
            TextureRegion iconRegion = TextureManager.getStatusIcon(status);
            if (iconRegion != null) {
                statusIcon.setDrawable(new TextureRegionDrawable(iconRegion));
                statusIcon.setVisible(true);
            } else {
                statusIcon.setVisible(false);
            }
        } else {
            statusIcon.setVisible(false);
        }
    }
    @Override
    public void act(float delta) {
        super.act(delta);
        stateTimer += delta;
        updateHPBars();
        updateExpBar();
        updateStatusIcon(playerPokemon, playerStatusIcon);
        updateStatusIcon(enemyPokemon, enemyStatusIcon);

        // Only update battleText if there are no pending actions.
        if (battleText.getActions().size == 0) {
            switch (currentState) {
                case INTRO:
                    if (stateTimer >= ANIMATION_DURATION) {
                        transitionToState(BattleState.PLAYER_TURN);
                        battleText.setText("What will " + playerPokemon.getName() + " do?");
                    }
                    break;
                case PLAYER_TURN:
                    if (!moveSelectionActive && actionMenu != null && !actionMenu.isVisible() && !isAnimating) {
                        showActionMenu(true);
                    }
                    break;
                case ENEMY_TURN:
                    if (!isAnimating && stateTimer >= 0.5f) {
                        executeEnemyMove();
                    }
                    break;
                case FORCED_SWITCH:
                    // Do nothing – wait until the player selects a new Pokémon.
                    break;
                case ENDED:
                    if (!isAnimating) {
                        finishBattle();
                    }
                    break;
                default:
                    break;
            }
        }
    }


    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);
    }

    private void executeEnemyMove() {
        if (isAnimating || enemyPokemon.getCurrentHp() <= 0) return;

        Move selectedMove = null;
        float highestExpectedDamage = 0f;

        for (Move move : enemyPokemon.getMoves()) {
            if (move.getPp() <= 0) continue;  // Skip moves with no PP

            float expectedDamage = computeExpectedDamage(move, enemyPokemon, playerPokemon);
            if (expectedDamage > highestExpectedDamage) {
                highestExpectedDamage = expectedDamage;
                selectedMove = move;
            }
        }

        // If no move was found (for example, if all moves are out of PP), use Struggle.
        if (selectedMove == null) {
            executeStruggle(enemyPokemon, playerPokemon);
            return;
        }

        executeMove(selectedMove, enemyPokemon, playerPokemon, false);
    }


    private void executeStruggle(Pokemon attacker, Pokemon defender) {
        float damage = attacker.getStats().getAttack() * 0.5f;
        float recoil = damage * 0.25f;
        applyDamage(defender, damage);
        applyDamage(attacker, recoil);
        battleText.setText(attacker.getName() + " used Struggle!");
    }

    private float getTypeEffectiveness(Pokemon.PokemonType attackType, Pokemon.PokemonType defendType) {
        if (attackType == null || defendType == null) {
            GameLogger.error("Null type encountered in getTypeEffectiveness: attackType=" + attackType + ", defendType=" + defendType);
            return 1.0f;
        }
        ObjectMap<Pokemon.PokemonType, Float> effectivenessMap = typeEffectiveness.get(attackType);
        if (effectivenessMap == null) return 1.0f;
        return effectivenessMap.get(defendType, 1.0f);
    }

    private void executeMove(Move move, Pokemon attacker, Pokemon defender, boolean isPlayerMove) {
        if (isAnimating) return;
        isAnimating = true;
        // Hide the main action menu if it’s showing.
        showActionMenu(false);
        SequenceAction moveSequence = Actions.sequence(
            Actions.run(() -> battleText.setText(attacker.getName() + " used " + move.getName() + "!")),
            Actions.delay(0.5f),
            Actions.run(() -> {
                float damage = calculateDamage(move, attacker, defender);
                applyDamage(defender, damage);
                // Flash the target sprite.
                Image targetSprite = (defender == playerPokemon ? playerPokemonImage : enemyPokemonImage);
                targetSprite.addAction(Actions.sequence(
                    Actions.color(Color.RED),
                    Actions.delay(DAMAGE_FLASH_DURATION),
                    Actions.color(Color.WHITE)
                ));
            }),
            Actions.delay(0.5f),
            Actions.run(() -> finishMoveExecution(isPlayerMove))
        );
        addAction(moveSequence);
    }

    private float calculateDamage(Move move, Pokemon attacker, Pokemon defender) {
        // Use the attacker's level in the calculation.
        int level = attacker.getLevel();

        // Choose the correct attack/defense stats based on move type.
        float attackStat = move.isSpecial() ? attacker.getStats().getSpecialAttack() : attacker.getStats().getAttack();
        float defenseStat = move.isSpecial() ? defender.getStats().getSpecialDefense() : defender.getStats().getDefense();

        // Base damage calculation following the Pokémon damage formula.
        float baseDamage = (((2 * level) / 5f + 2) * move.getPower() * attackStat / defenseStat) / 50f + 2;

        // Calculate modifiers:
        // Random factor between 0.85 and 1.0.
        float randomModifier = MathUtils.random(0.85f, 1.0f);

        // STAB (Same-Type Attack Bonus): 1.5 if the move's type matches one of the attacker's types.
        float stab = (attacker.getPrimaryType() == move.getType() ||
            attacker.getSecondaryType() == move.getType()) ? 1.5f : 1.0f;

        // Type effectiveness multiplier.
        float typeMultiplier = getTypeEffectiveness(move.getType(), defender.getPrimaryType());
        if (defender.getSecondaryType() != null) {
            typeMultiplier *= getTypeEffectiveness(move.getType(), defender.getSecondaryType());
        }

        // Combine all modifiers.
        float modifier = randomModifier * stab * typeMultiplier;

        return baseDamage * modifier;
    }


    private void updateActionMenuPosition() {
        actionMenu.pack();
        float tableWidth = getWidth() > 0 ? getWidth() : BATTLE_SCREEN_WIDTH;
        float tableHeight = getHeight() > 0 ? getHeight() : BATTLE_SCREEN_HEIGHT;
        // Center horizontally and place at 30% of the height from the bottom.
        float posX = (tableWidth - actionMenu.getWidth()) / 2f;
        float posY = tableHeight * 0.3f;
        actionMenu.setPosition(posX, posY);
        actionMenu.toFront();
    }

    @Override
    protected void sizeChanged() {
        super.sizeChanged();
        updateActionMenuPosition();
    }

    private void applyDamage(Pokemon target, float damage) {
        Image targetSprite = (target == playerPokemon ? playerPokemonImage : enemyPokemonImage);
        targetSprite.addAction(Actions.sequence(
            Actions.color(Color.RED),
            Actions.delay(DAMAGE_FLASH_DURATION),
            Actions.color(Color.WHITE)
        ));
        float newHP = Math.max(0, target.getCurrentHp() - damage);
        target.setCurrentHp(newHP);
        updateHPBars();
    }
    private void finishMoveExecution(boolean isPlayerMove) {
        isAnimating = false;

        if (enemyPokemon.getCurrentHp() <= 0) {
            // Enemy fainted
            finishBattle();
            return;
        } else if (playerPokemon.getCurrentHp() <= 0) {
            // Player's Pokémon fainted
            battleText.setText(playerPokemon.getName() + " fainted!");
            if (hasAvailablePokemon()) {
                // Only trigger the forced switch if we’re not already in forced switch mode.
                if (currentState != BattleState.FORCED_SWITCH) {
                    currentState = BattleState.FORCED_SWITCH;
                    showForcedSwitchPartyScreen(); // Force user to pick another Pokémon
                }
            } else {
                finishBattle(); // No Pokémon left
            }
            return;
        } else {
            // No one fainted; proceed as normal
            if (isPlayerMove) {
                transitionToState(BattleState.ENEMY_TURN);
                executeEnemyMove();
            } else {
                transitionToState(BattleState.PLAYER_TURN);
                showActionMenu(true);
            }
        }
    }

    private void showForcedSwitchPartyScreen() {
        // No cancel callback => forced switch
        PokemonPartyWindow partyScreen = new PokemonPartyWindow(
            skin,
            GameContext.get().getPlayer().getPokemonParty(),
            true,
            (selectedPokemon) -> {
                if (selectedPokemon.getCurrentHp() > 0) {
                    switchPokemon(selectedPokemon);
                }
            },
            null
        );
        stage.addActor(partyScreen);
        partyScreen.show(stage);
    }

    private boolean hasAvailablePokemon() {
        for (Pokemon poke : GameContext.get().getPlayer().getPokemonParty().getParty()) {
            if (poke != playerPokemon && poke.getCurrentHp() > 0) {
                return true;
            }
        }
        return false;
    }




    private void transitionToState(BattleState newState) {
        currentState = newState;
        stateTimer = 0;
        switch (newState) {
            case PLAYER_TURN:
                setBattleInterfaceEnabled(true);
                showActionMenu(true);
                break;
            case ENEMY_TURN:
                setBattleInterfaceEnabled(false);
                showActionMenu(false);
                break;
            case ENDED:
                setBattleInterfaceEnabled(false);
                showActionMenu(false);
                break;
            default:
                break;
        }
        GameLogger.info("Transitioned to state: " + currentState);
    }

    private void showActionMenu(boolean show) {
        if (actionMenu != null) {
            actionMenu.setVisible(show);
            actionMenu.setTouchable(show ? Touchable.enabled : Touchable.disabled);
            actionMenu.toFront();
        }
    }


    private void showMoveSelection() {
        final Table moveSelectionTable = new Table(skin);
        moveSelectionTable.setBackground(createTranslucentBackground(0.7f));
        moveSelectionTable.defaults().pad(10).space(10);

        java.util.List<Move> moves = playerPokemon.getMoves();
        if (moves == null || moves.isEmpty()) {
            battleText.setText(playerPokemon.getName() + " has no moves!");
            actionMenu.setVisible(true);
            actionMenu.setTouchable(Touchable.enabled);
            return;
        }
        // Create a button for each move.
        for (final Move move : moves) {
            TextButton moveButton = new TextButton(move.getName(), skin);
            moveButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    moveSelectionActive = false; // Clear the flag once a move is selected
                    moveSelectionTable.remove();
                    executeMove(move, playerPokemon, enemyPokemon, true);
                }
            });
            moveSelectionTable.add(moveButton).fillX().row();
        }
        TextButton cancelButton = new TextButton("Cancel", skin);
        cancelButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                moveSelectionActive = false; // Clear the flag when canceling
                moveSelectionTable.remove();
                actionMenu.setVisible(true);
                actionMenu.setTouchable(Touchable.enabled);
                actionMenu.toFront();
            }
        });
        moveSelectionTable.add(cancelButton).fillX();


        moveSelectionTable.pack();
        // Position the move selection UI in the center.
        float tableWidth = (getWidth() > 0 ? getWidth() : BATTLE_SCREEN_WIDTH);
        float tableHeight = (getHeight() > 0 ? getHeight() : BATTLE_SCREEN_HEIGHT);
        float posX = (tableWidth - moveSelectionTable.getWidth()) / 2f;
        float posY = (tableHeight - moveSelectionTable.getHeight()) / 2f;
        moveSelectionTable.setPosition(posX, posY);
        moveSelectionTable.toFront();
        addActor(moveSelectionTable);
    }


    private void finishBattle() {
        boolean playerWon = playerPokemon.getCurrentHp() > 0 && enemyPokemon.getCurrentHp() <= 0;
        SequenceAction endSequence = Actions.sequence(
            Actions.run(() -> battleText.setText(playerWon ? "Victory!" : playerPokemon.getName() + " fainted!")),
            Actions.delay(2.0f),
            Actions.run(() -> {
                if (callback != null) {
                    callback.onBattleEnd(playerWon);
                }
                cleanup();
                remove();
            })
        );
        addAction(endSequence);
        isAnimating = true;
    }

    public void setCallback(BattleCallback callback) {
        this.callback = callback;
    }

    private void cleanup() {
        // Dispose or remove any actors, textures, or listeners that are no longer needed.
        if (playerPokemonImage != null) playerPokemonImage.clear();
        if (enemyPokemonImage != null) enemyPokemonImage.clear();
        clearActions();
    }

    // ––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––
    // ENUM FOR BATTLE STATE
    // ––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––
    private enum BattleState {
        INTRO,
        PLAYER_TURN,
        ENEMY_TURN,
        ANIMATING,
        ENDED,
        RUNNING,
        CATCHING,
        FORCED_SWITCH  // New state: once the player's Pokémon faints, we switch to this state.
    }


    // ––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––
    // CALLBACK INTERFACE & CLEANUP
    // ––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––
    public interface BattleCallback {
        void onBattleEnd(boolean playerWon);

        void onTurnEnd(Pokemon activePokemon);

        void onStatusChange(Pokemon pokemon, Pokemon.Status newStatus);

        void onMoveUsed(Pokemon user, Move move, Pokemon target);
    }
}

