package io.github.pokemeetup.screens.otherui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.actions.SequenceAction;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.FitViewport;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.attacks.Move;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;

import java.util.HashMap;

public class BattleTable extends Table {
    // Constants for layout and animation
    private static final float BATTLE_SCREEN_WIDTH = 800f;
    private static final float BATTLE_SCREEN_HEIGHT = 480f;
    private static final float HP_BAR_WIDTH = 100f;
    private static final float ANIMATION_DURATION = 0.5f;
    private static final float DAMAGE_FLASH_DURATION = 0.1f;
    private static final float HP_UPDATE_DURATION = 0.5f;
    private static final float PLATFORM_SHAKE_DECAY = 0.9f;
    private static final float MIN_SHAKE_INTENSITY = 0.1f;
    private static final float PLATFORM_VERTICAL_OFFSET = 20f;
    private static final float POKEMON_SCALE = 1.5f;
    private static final int MAX_TURN_COUNT = 20;
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
    private final Pokemon playerPokemon;
    private final Pokemon enemyPokemon;
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
    private boolean moveSelectionActive = false;

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

    // ––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––
    // SKIN STYLE REGISTRATION FOR HP BARS
    // ––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––

    private static void initTypeEffectiveness(Pokemon.PokemonType attackType,
                                              ObjectMap<Pokemon.PokemonType, Float> effectiveness) {
        typeEffectiveness.get(attackType).putAll(effectiveness);

    }

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

    // Inside your BattleTable class

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
    // Example: If you have a background texture, you could load it here:
    // TextureRegion battleBackground = TextureManager.battlebacks.findRegion("battle_bg");
    // if (battleBackground == null) {
    //     GameLogger.error("Battle background texture not found; using default color.");
    // }

    // --- UI INITIALIZATION METHODS ---

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
    private void initializeUIComponents() {
        // Create battle text label.
        battleText = new Label("", skin);
        battleText.setWrap(true);
        battleText.setAlignment(Align.center);
        battleText.setTouchable(Touchable.disabled);
        // Position the battleText at the top center.
        // (If the BattleTable’s size is not yet set, we use fallback constants.)
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
        runButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                attemptRun();
            }
        });
        // (bagButton and pokemonButton listeners can be added similarly.)

        // Arrange buttons in a 2x2 grid.
        actionMenu.add(fightButton).width(180).height(50);
        actionMenu.add(bagButton).width(180).height(50);
        actionMenu.row();
        actionMenu.add(pokemonButton).width(180).height(50);
        actionMenu.add(runButton).width(180).height(50);
        actionMenu.pack();

        // Update its position based on the BattleTable’s size.
        updateActionMenuPosition();

        // Initially hide the menu.
        actionMenu.setVisible(false);
        actionMenu.setTouchable(Touchable.disabled);
        addActor(battleText);
        addActor(actionMenu);
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
                        callback.onBattleEnd(true);
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

    /**
     * Called when the player clicks the "FIGHT" button.
     * Instead of immediately executing a move, we now show a move–selection UI.
     */


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
        enemySection.add(new Label(enemyPokemon.getName(), skin)).expandX().right().pad(10).row();
        enemySection.add(enemyHPBar).expandX().right().pad(10).row();
        Stack enemyStack = new Stack();
        enemyStack.add(enemyPlatform);
        enemyStack.add(enemyPokemonImage);
        enemySection.add(enemyStack).expand().right().padRight(stage.getWidth() * 0.1f).row();

        // Player section.
        Table playerSection = new Table();
        playerSection.add(new Label(playerPokemon.getName(), skin)).expandX().left().pad(10).row();
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

    @Override
    public void act(float delta) {
        super.act(delta);
        stateTimer += delta;
        updateHPBars();
        updateExpBar();

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
        float bestEffectiveness = 0f;
        for (Move move : enemyPokemon.getMoves()) {
            if (move.getPp() > 0) {
                float effectiveness = getTypeEffectiveness(move.getType(), playerPokemon.getPrimaryType());
                if (playerPokemon.getSecondaryType() != null) {
                    effectiveness *= getTypeEffectiveness(move.getType(), playerPokemon.getSecondaryType());
                }
                if (effectiveness > bestEffectiveness) {
                    bestEffectiveness = effectiveness;
                    selectedMove = move;
                }
            }
        }
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
        float baseDamage = move.getPower() * ((move.isSpecial() ?
            ((float) attacker.getStats().getSpecialAttack() / defender.getStats().getSpecialDefense()) :
            ((float) attacker.getStats().getAttack() / defender.getStats().getDefense())));
        float typeMultiplier = getTypeEffectiveness(move.getType(), defender.getPrimaryType());
        if (defender.getSecondaryType() != null) {
            typeMultiplier *= getTypeEffectiveness(move.getType(), defender.getSecondaryType());
        }
        float variation = MathUtils.random(0.85f, 1.0f);
        return baseDamage * typeMultiplier * variation;
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
        if (target.getCurrentHp() <= 0) {
            transitionToState(BattleState.ENDED);
        }

    }

    private void finishMoveExecution(boolean isPlayerMove) {
        // Ensure animation flag is cleared
        isAnimating = false;
        // Then check if either side has fainted:
        if (playerPokemon.getCurrentHp() <= 0 || enemyPokemon.getCurrentHp() <= 0) {
            transitionToState(BattleState.ENDED);
        } else if (isPlayerMove) {
            // After player’s attack, switch to enemy turn.
            transitionToState(BattleState.ENEMY_TURN);
            executeEnemyMove();
        } else {
            // Otherwise return to player turn.
            transitionToState(BattleState.PLAYER_TURN);
            showActionMenu(true);
        }
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
        CATCHING
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
