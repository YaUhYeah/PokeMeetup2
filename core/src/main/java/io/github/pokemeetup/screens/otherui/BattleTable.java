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
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
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
import java.util.List;

public class BattleTable extends Table {
    private static final float PLAYER_PLATFORM_Y = 0.12f; // Lowered from 0.18f
    private static final float ENEMY_PLATFORM_Y = 0.48f;  // Lowered from 0.58f
    private static final float CONTROLS_BOTTOM_PADDING = 20f;
    private static final float BUTTON_HEIGHT = 45f;
    private static final float BUTTON_PADDING = 10f;
    private static final float INFO_BOX_PADDING = 15f;
    private static final float POKEMON_SCALE = 1.5f;
    private static final float PLATFORM_SCALE = 1.0f;
    private static final float PLAYER_PLATFORM_X = 0.15f;
    private static final float ENEMY_PLATFORM_X = 0.75f;
    private static final float PLATFORM_WIDTH_RATIO = 0.25f;
    private static final float BUTTON_WIDTH = 160f;
    private static final float HP_BAR_WIDTH = 100;    // Adjust HP bar width to match HUD

    private static final float PLATFORM_VERTICAL_OFFSET = 20f;

    private static final float BASE_WIDTH = 800f;
    private static final float BASE_HEIGHT = 480f;
    private static final float POKEMON_BASE_SIZE = 85f;
    private static final float ANIMATION_DURATION = 0.5f;
    private static final float DAMAGE_FLASH_DURATION = 0.1f;
    private static final float HP_UPDATE_DURATION = 0.5f;
    private static final int MAX_TURN_COUNT = 20;
    private static final ObjectMap<Pokemon.PokemonType, ObjectMap<Pokemon.PokemonType, Float>> typeEffectiveness;
    private static final float PLATFORM_SHAKE_DECAY = 0.9f;
    private static final float MIN_SHAKE_INTENSITY = 0.1f;
    private static final float RUN_SUCCESS_BASE = 0.5f;
    private static final float LEVEL_FACTOR = 0.1f;
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

    private final Stage stage;
    private final Skin skin;
    private final Pokemon playerPokemon;
    private final Pokemon enemyPokemon;
    private final Array<Action> pendingActions = new Array<>();
    private final ShapeRenderer shapeRenderer;
    private TextureRegion platformTexture;
    private float playerPlatformX, playerPlatformY;
    private float enemyPlatformX, enemyPlatformY;
    private final Vector2 cameraShake;
    private float shakeDuration;
    private float shakeTimer;
    private Table battleScene;
    private Image playerPlatform;
    private Image enemyPlatform;
    private Image playerPokemonImage;
    private Image enemyPokemonImage;
    private Table actionMenu;
    private Table moveMenu;
    private ProgressBar playerHPBar;
    private ProgressBar enemyHPBar;
    private Label battleText;
    private TextButton fightButton;
    private TextButton bagButton;
    private TextButton pokemonButton;
    private TextButton runButton;
    private BattleState currentState;
    private BattleCallback callback;
    private float stateTimer = 0;
    private int turnCount = 0;
    private boolean isAnimating = false;
    private float currentShakeIntensity = 0;
    private PokemonHUD playerHUD;
    private PokemonHUD enemyHUD;
    private int selectedMoveIndex = 0;
    private Table moveSelectionMenu;
    private Label powerLabel;
    private Label accuracyLabel;
    private Label descriptionLabel;
    private Label moveTypeLabel;
    private boolean initialized = false;

    public BattleTable(Stage stage, Skin skin, Pokemon playerPokemon, Pokemon enemyPokemon) {
        super();
        this.stage = stage;
        this.skin = skin;
        this.playerPokemon = playerPokemon;
        this.enemyPokemon = enemyPokemon;
        this.shapeRenderer = new ShapeRenderer();
        this.cameraShake = new Vector2();
        this.currentState = BattleState.INTRO;
        this.isAnimating = true;
        setTouchable(Touchable.enabled);
        stage.addActor(this);

        stage.setViewport(new FitViewport(BASE_WIDTH, BASE_HEIGHT));
        stage.getViewport().update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);

        try {
            initializeTextures();
            initializeUIComponents();
            initializePlatforms();
            initializePokemonSprites();
            setupHPBars();
            initializeHUDElements();
            initializeMoveMenu();
            initializeMoveLabels();
            setupContainer();

            initialized = true;
            startBattleAnimation();
        } catch (Exception e) {
            GameLogger.error("Error initializing battle table: " + e.getMessage());
        }
    }

    private static void initTypeEffectiveness(Pokemon.PokemonType attackType,
                                              ObjectMap<Pokemon.PokemonType, Float> effectiveness) {
        typeEffectiveness.get(attackType).putAll(effectiveness);

    }

    private static ProgressBar.ProgressBarStyle createHPBarStyle(float percentage) {
        ProgressBar.ProgressBarStyle style = new ProgressBar.ProgressBarStyle();

        // Background
        Pixmap bgPixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        bgPixmap.setColor(0.2f, 0.2f, 0.2f, 0.8f);
        bgPixmap.fill();
        Texture bgTexture = new Texture(bgPixmap);
        style.background = new TextureRegionDrawable(new TextureRegion(bgTexture));
        bgPixmap.dispose();

        // Foreground
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
        TextureRegionDrawable knob = new TextureRegionDrawable(new TextureRegion(fgTexture));
        style.knob = knob;
        style.knobBefore = knob;
        fgPixmap.dispose();

        return style;
    }

    private void setupContainer() {
        setFillParent(true);
        setTouchable(Touchable.childrenOnly);
        setZIndex(100);
    }


    @Override
    protected void sizeChanged() {
        super.sizeChanged();
        if (initialized) {
            updateSizes();
            updateLayout();
            updatePokemonPositions();
            if (currentState == BattleState.INTRO && isAnimating) {
                clearActions();
                resetPokemonPositions();
            }
        }
    }

    public BattleState getCurrentState() {
        return currentState;
    }

    public boolean isAnimating() {
        return isAnimating;
    }

    private void updateSizes() {
        float viewportWidth = stage.getViewport().getWorldWidth();
        float viewportHeight = stage.getViewport().getWorldHeight();

        // Calculate platform sizes
        float platformWidth = viewportWidth * PLATFORM_WIDTH_RATIO;
        float platformHeight = platformWidth * 0.3f; // Keep platforms relatively flat

        // Update platform sizes
        playerPlatform.setSize(platformWidth, platformHeight);
        enemyPlatform.setSize(platformWidth, platformHeight);

        // Calculate Pokemon sizes
        float pokemonSize = platformWidth * 0.8f;
        playerPokemonImage.setSize(pokemonSize, pokemonSize);
        enemyPokemonImage.setSize(pokemonSize, pokemonSize);
        updatePokemonPositions();
    }


    private void updateLayout() {
        if (!initialized) return;

        clear();
        setTouchable(Touchable.enabled);

        // Main container

        Table mainContainer = new Table();
        mainContainer.setName("MainContainer");
        mainContainer.setFillParent(true);
        mainContainer.top().padTop(10);
        mainContainer.setTouchable(Touchable.enabled);  // Changed from childrenOnly


        Table controlSection = new Table() {
            @Override
            public Actor hit(float x, float y, boolean touchable) {
                Actor hit = super.hit(x, y, touchable);
                GameLogger.info("Control section hit check at " + x + "," + y +
                    " result: " + (hit != null ? hit.getName() : "null"));
                return hit;
            }
        };
        controlSection.setName("ControlSection");
        controlSection.setBackground(createTranslucentBackground(0.7f));
        controlSection.setTransform(false);
        controlSection.setTouchable(Touchable.enabled);


        // Enemy section
        Table enemySection = new Table();
        enemySection.setTouchable(Touchable.disabled);
        enemySection.add(enemyHUD).expandX().right().pad(INFO_BOX_PADDING).row();

        Stack enemyStack = new Stack();
        enemyStack.setTouchable(Touchable.disabled);
        enemyStack.add(enemyPlatform);
        enemyStack.add(enemyPokemonImage);
        enemySection.add(enemyStack).expand().right().padRight(stage.getWidth() * 0.1f);

        // Player section
        Table playerSection = new Table();
        playerSection.setTouchable(Touchable.disabled);
        playerSection.add(playerHUD).expandX().left().pad(INFO_BOX_PADDING).row();

        Stack playerStack = new Stack();
        playerStack.setTouchable(Touchable.disabled);
        playerStack.add(playerPlatform);
        playerStack.add(playerPokemonImage);
        playerSection.add(playerStack).expand().left().padLeft(stage.getWidth() * 0.1f);

        Table buttonContainer = new Table();
        buttonContainer.setName("ButtonContainer");
        buttonContainer.setTouchable(Touchable.enabled);  // Changed from childrenOnly
        setupBattleButtons(buttonContainer);
        // Add components with explicit layout
        controlSection.add(battleText).expandX().fillX().pad(10).row();
        controlSection.add(buttonContainer).expandX().fillX().padBottom(5).padTop(5).height(BUTTON_HEIGHT * 2 + BUTTON_PADDING * 3);


        // Add sections to main container
        mainContainer.add(enemySection).expand().fill().row();
        mainContainer.add(playerSection).expand().fill().row();
        mainContainer.add(controlSection).expandX().fillX().bottom().padBottom(CONTROLS_BOTTOM_PADDING);
        add(mainContainer).expand().fill();

        boolean canInteract = currentState == BattleState.PLAYER_TURN && !isAnimating;
        enableBattleButtons(canInteract);

        GameLogger.info("Layout updated - Current state: " + currentState +
            ", Animating: " + isAnimating +
            ", Buttons touchable: " + (fightButton != null ? fightButton.getTouchable() : "null"));
    }

    private void updatePokemonPositions() {
        float viewportWidth = stage.getViewport().getWorldWidth();
        float viewportHeight = stage.getViewport().getWorldHeight();

        // Update platform positions
        playerPlatformX = viewportWidth * PLAYER_PLATFORM_X;
        playerPlatformY = viewportHeight * PLAYER_PLATFORM_Y;
        enemyPlatformX = viewportWidth * ENEMY_PLATFORM_X;
        enemyPlatformY = viewportHeight * ENEMY_PLATFORM_Y;

        playerPlatform.setPosition(playerPlatformX, playerPlatformY);
        enemyPlatform.setPosition(enemyPlatformX, enemyPlatformY);

        // Calculate Pokemon positions relative to platforms
        float playerPokemonOffsetX = (playerPlatform.getWidth() - (playerPokemonImage.getWidth() * POKEMON_SCALE)) / 2f;
        float playerPokemonOffsetY = (playerPlatform.getHeight() - (playerPokemonImage.getHeight() * POKEMON_SCALE)) / 2f;

        float enemyPokemonOffsetX = (enemyPlatform.getWidth() - (enemyPokemonImage.getWidth() * POKEMON_SCALE)) / 2f;
        float enemyPokemonOffsetY = (enemyPlatform.getHeight() - (enemyPokemonImage.getHeight() * POKEMON_SCALE)) / 2f;

        // Position Pokemon with proper centering and vertical offset
        playerPokemonImage.setPosition(
            playerPlatformX + playerPokemonOffsetX,
            playerPlatformY + playerPokemonOffsetY + PLATFORM_VERTICAL_OFFSET
        );

        enemyPokemonImage.setPosition(
            enemyPlatformX + enemyPokemonOffsetX,
            enemyPlatformY + enemyPokemonOffsetY + PLATFORM_VERTICAL_OFFSET
        );

        // Ensure proper scaling
        playerPokemonImage.setScale(POKEMON_SCALE);
        enemyPokemonImage.setScale(POKEMON_SCALE);
    }


    private TextureRegionDrawable createButtonBackground(Color color) {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return new TextureRegionDrawable(new TextureRegion(texture));
    }

    private void updateUI() {
        if (battleText == null || actionMenu == null || moveMenu == null) {
            GameLogger.error("UI components not properly initialized");
            return;
        }
        if (!initialized) {
            GameLogger.error("Battle table not properly initialized");
            return;
        }


        boolean isPlayerTurn = currentState == BattleState.PLAYER_TURN;
        boolean isBattleEnded = currentState == BattleState.ENDED;
        actionMenu.setVisible(isPlayerTurn && !isAnimating);
        moveMenu.setVisible(false);
        if (fightButton != null) fightButton.setDisabled(!isPlayerTurn || isBattleEnded);
        if (bagButton != null) bagButton.setDisabled(!isPlayerTurn || isBattleEnded);
        if (pokemonButton != null) pokemonButton.setDisabled(!isPlayerTurn || isBattleEnded);
        if (runButton != null) runButton.setDisabled(!isPlayerTurn || isBattleEnded);
        updateHPBars();
        updateStatusEffects();
        updateBattleText();
    }


    private TextureRegionDrawable createTranslucentBackground(float alpha) {
        Pixmap bgPixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        bgPixmap.setColor(0, 0, 0, alpha);
        bgPixmap.fill();
        TextureRegion region = new TextureRegion(new Texture(bgPixmap));
        bgPixmap.dispose();
        return new TextureRegionDrawable(region);
    }

    @Override
    public Stage getStage() {
        return stage;
    }

    @Override
    public Skin getSkin() {
        return skin;
    }

    public void setCallback(BattleCallback callback) {
        this.callback = callback;
    }

    private void startShakeEffect() {
        currentShakeIntensity = (float) 5.0;
        shakeDuration = (float) 0.3;
        shakeTimer = 0;
    }

    private void updateShakeEffects(float delta) {
        if (shakeTimer < shakeDuration) {
            shakeTimer += delta;

            // Calculate shake offset
            float xOffset = MathUtils.random(-currentShakeIntensity, currentShakeIntensity);
            float yOffset = MathUtils.random(-currentShakeIntensity, currentShakeIntensity);
            cameraShake.set(xOffset, yOffset);

            // Apply shake to platforms and Pokemon
            playerPlatform.setPosition(
                playerPlatformX + xOffset,
                playerPlatformY + yOffset
            );
            playerPokemonImage.setPosition(
                playerPlatformX + platformTexture.getRegionWidth() / 2 - playerPokemonImage.getWidth() / 2 + xOffset,
                playerPlatformY + platformTexture.getRegionHeight() + yOffset
            );

            // Decay shake intensity
            currentShakeIntensity *= PLATFORM_SHAKE_DECAY;
            if (currentShakeIntensity < MIN_SHAKE_INTENSITY) {
                currentShakeIntensity = 0;
                resetPositions();
            }
        }
    }

    private void resetPositions() {
        playerPlatform.setPosition(playerPlatformX, playerPlatformY);
        enemyPlatform.setPosition(enemyPlatformX, enemyPlatformY);
        playerPokemonImage.setPosition(
            playerPlatformX + platformTexture.getRegionWidth() / 2f - playerPokemonImage.getWidth() / 2f,
            playerPlatformY + platformTexture.getRegionHeight()
        );
        enemyPokemonImage.setPosition(
            enemyPlatformX + platformTexture.getRegionWidth() / 2f - enemyPokemonImage.getWidth() / 2f,
            enemyPlatformY + platformTexture.getRegionHeight()
        );
    }


    @Override
    public void act(float delta) {
        super.act(delta);

        // Always increment stateTimer, regardless of isAnimating
        stateTimer += delta;

        if (Gdx.input.justTouched()) {
            Vector2 stageCoords = stage.screenToStageCoordinates(new Vector2(Gdx.input.getX(), Gdx.input.getY()));
            float x = stageCoords.x;
            float y = stageCoords.y;
            Actor hit = stage.hit(x, y, true);

            if (hit != null) {
                GameLogger.info("Touch detected on: " + hit.getClass().getSimpleName() +
                    " at " + x + "," + y +
                    " State: " + currentState +
                    " Animating: " + isAnimating +
                    " Button enabled: " + (hit instanceof TextButton ? !((TextButton) hit).isDisabled() : "N/A"));

                Actor current = hit;
                StringBuilder hierarchy = new StringBuilder();
                while (current != null) {
                    hierarchy.insert(0, current.getClass().getSimpleName() + " -> ");
                    current = current.getParent();
                }
                GameLogger.info("Touch hierarchy: " + hierarchy);
            }
        }

        if (playerHPBar == null || enemyHPBar == null) {
            return;
        }

        updateShakeEffects(delta);
        updateUI();

        switch (currentState) {
            case INTRO:
                updateIntroState();
                break;

            case PLAYER_TURN:
                if (!actionMenu.isVisible() && !moveMenu.isVisible()) {
                    showActionMenu(true);
                }
                break;

            case ENEMY_TURN:
                if (stateTimer >= 0.5f) {
                    executeEnemyMove();
                }
                break;

        }
    }



    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);
    }

    private void executeEnemyMove() {
        if (isAnimating || enemyPokemon.getCurrentHp() <= 0) return;

        // Simple AI - prioritize super effective moves
        Move selectedMove = null;
        float bestEffectiveness = 0f;

        for (Move move : enemyPokemon.getMoves()) {
            if (move.getPp() > 0) {
                float effectiveness = getTypeEffectiveness(
                    move.getType(),
                    playerPokemon.getPrimaryType()
                );

                if (playerPokemon.getSecondaryType() != null) {
                    effectiveness *= getTypeEffectiveness(
                        move.getType(),
                        playerPokemon.getSecondaryType()
                    );
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

    private void applyEndOfTurnEffects(Pokemon pokemon) {
        if (!pokemon.hasStatus()) return;

        switch (pokemon.getStatus()) {
            case BURNED:
                float burnDamage = pokemon.getStats().getHp() * 0.0625f;
                applyDamage(pokemon, burnDamage);
                showBattleText(pokemon.getName() + " was hurt by its burn!");
                break;

            case POISONED:
                float poisonDamage = pokemon.getStats().getHp() * 0.125f;
                applyDamage(pokemon, poisonDamage);
                showBattleText(pokemon.getName() + " was hurt by poison!");
                break;

            case BADLY_POISONED:
                float toxicDamage = pokemon.getStats().getHp() * (0.0625f * pokemon.getToxicCounter());
                applyDamage(pokemon, toxicDamage);
                pokemon.incrementToxicCounter();
                showBattleText(pokemon.getName() + " was hurt by toxic!");
                break;
        }

        updateHPBars();
    }

    private void attemptRun() {
        if (isAnimating) return;

        float runChance = calculateRunChance();
        if (MathUtils.random() < runChance) {
            showBattleText("Got away safely!");

            // Create fade out sequence without using battleScene
            SequenceAction escapeSequence = Actions.sequence(
                Actions.parallel(
                    Actions.run(() -> {
                        // Fade out Pokemon sprites
                        if (playerPokemonImage != null) {
                            playerPokemonImage.addAction(Actions.fadeOut(0.5f));
                        }
                        if (enemyPokemonImage != null) {
                            enemyPokemonImage.addAction(Actions.fadeOut(0.5f));
                        }
                    }),
                    Actions.delay(0.5f)
                ),
                Actions.parallel(
                    Actions.run(() -> {
                        // Fade out HUD elements
                        if (playerHUD != null) {
                            playerHUD.addAction(Actions.fadeOut(0.5f));
                        }
                        if (enemyHUD != null) {
                            enemyHUD.addAction(Actions.fadeOut(0.5f));
                        }
                        // Fade out action menu
                        if (actionMenu != null) {
                            actionMenu.addAction(Actions.fadeOut(0.5f));
                        }
                    }),
                    Actions.delay(0.5f)
                ),
                // Final cleanup
                Actions.run(() -> {
                    // Play sound effect
//                    AudioManager.getInstance().playSound(AudioManager.SoundEffect.BATTLE_RUN);

                    // Call callback
                    if (callback != null) {
                        callback.onBattleEnd(true);
                    }
                    // Clean up battle
                    cleanup();
                    remove();
                })
            );

            addAction(escapeSequence);
            isAnimating = true; // Set animating state
            currentState = BattleState.RUNNING;

        } else {
            showBattleText("Can't escape!");
//            AudioManager.getInstance().playSound(AudioManager.SoundEffect.MOVE_MISS);
            transitionToState(BattleState.ENEMY_TURN);
        }
    }

    // Helper method to clean up battle gracefully
    private void cleanup() {
        isAnimating = false;

        // Clear all menus
        if (actionMenu != null) actionMenu.remove();
        if (moveMenu != null) moveMenu.remove();
        if (moveSelectionMenu != null) moveSelectionMenu.remove();

        // Clear sprites
        if (playerPokemonImage != null) playerPokemonImage.remove();
        if (enemyPokemonImage != null) enemyPokemonImage.remove();

        // Clear HUD
        if (playerHUD != null) playerHUD.remove();
        if (enemyHUD != null) enemyHUD.remove();
    }

    public void dispose() {
        // Clean up resources
        if (playerPokemonImage != null) {
            playerPokemonImage.remove();
        }
        if (shapeRenderer != null) {
            shapeRenderer.dispose();
        }
        if (enemyPokemonImage != null) {
            enemyPokemonImage.remove();
        }
        if (battleScene != null) {
            battleScene.remove();
        }
        if (enemyHPBar != null && enemyHPBar.getStyle().knob instanceof TextureRegionDrawable) {
            ((TextureRegionDrawable) enemyHPBar.getStyle().knob).getRegion().getTexture().dispose();
        }
        if (playerHPBar != null && playerHPBar.getStyle().knob instanceof TextureRegionDrawable) {
            ((TextureRegionDrawable) playerHPBar.getStyle().knob).getRegion().getTexture().dispose();
        }
        // Dispose of any textures created for HP bars
        Array<Cell> cells = playerHUD.getCells();
        for (Cell cell : cells) {
            Actor actor = cell.getActor();
            if (actor instanceof ProgressBar) {
                ProgressBar bar = (ProgressBar) actor;
                Drawable knob = bar.getStyle().knob;
                if (knob instanceof TextureRegionDrawable) {
                    ((TextureRegionDrawable) knob).getRegion().getTexture().dispose();
                }
            }
        }
        clearActions();
        remove();
    }



    private float calculateRunChance() {
        // Make it very easy to run
        float baseChance = 0.9f; // 90% base chance

        // Small bonus based on level difference
        float levelBonus = Math.max(0, playerPokemon.getLevel() - enemyPokemon.getLevel()) * 0.02f;

        // Cap at 95% chance
        return Math.min(0.95f, baseChance + levelBonus);
    }

    private void initializeMoveMenu() {
        moveMenu = new Table(skin);
        moveMenu.setBackground(createTranslucentBackground(0.8f));
        moveMenu.defaults().pad(10).size(180, 45);

        // Create move buttons grid
        Table moveGrid = new Table();
        moveGrid.defaults().pad(5);

        // Add moves
        for (int i = 0; i < playerPokemon.getMoves().size(); i++) {
            final int moveIndex = i;
            Move move = playerPokemon.getMoves().get(i);

            Table moveButton = createMoveButton(move);
            moveButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (!isAnimating && currentState == BattleState.PLAYER_TURN) {
                        selectedMoveIndex = moveIndex;
                        executeMove(move, playerPokemon, enemyPokemon, true);
                    }
                }
            });

            if (i % 2 == 0) {
                moveGrid.add(moveButton).padRight(10);
            } else {
                moveGrid.add(moveButton).row();
            }
        }
        TextButton backButton = new TextButton("BACK", skin);
        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showActionMenu(true);
            }
        });

        moveMenu.add(moveGrid).expand().fill().row();
        moveMenu.add(backButton).size(150, 40).pad(10);
        moveMenu.setVisible(false);  // Initially hidden
        addActor(moveMenu);
    }

    private void updateBattleText() {
        if (battleText == null) {
            GameLogger.error("Battle text not initialized");
            return;
        }

        String message = "";
        switch (currentState) {
            case INTRO:
                message = "Wild " + enemyPokemon.getName() + " appeared!";
                break;
            case PLAYER_TURN:
                message = "What will " + playerPokemon.getName() + " do?";
                break;
            case ENEMY_TURN:
                message = "Wild " + enemyPokemon.getName() + " is thinking...";
                break;
            case ENDED:
                message = playerPokemon.getCurrentHp() > 0 ? "Victory!" : "Defeat!";
                break;
            default:
                break;
        }
        battleText.setText(message);
    }


    private void initializePlatforms() {
        platformTexture = TextureManager.getBattlebacks().findRegion("battle_platform");
        if (platformTexture == null) {
            throw new RuntimeException("Failed to load battle platform texture");
        }

        playerPlatform = new Image(platformTexture);
        enemyPlatform = new Image(platformTexture);

        // Remove scaling
        playerPlatform.setScaling(Scaling.none);
        enemyPlatform.setScaling(Scaling.none);

        // Force visibility
        playerPlatform.setVisible(true);
        enemyPlatform.setVisible(true);

        // Do not add platforms directly to the stage
        // They will be added to the layout in updateLayout()
    }

    private void resetPokemonPositions() {
        if (playerPokemonImage != null && enemyPokemonImage != null) {
            float stageWidth = stage.getWidth();
            float stageHeight = stage.getHeight();

            // Position platforms
            playerPlatform.setScale(PLATFORM_SCALE);
            enemyPlatform.setScale(PLATFORM_SCALE);

            float platformWidth = platformTexture.getRegionWidth() * PLATFORM_SCALE;
            float platformHeight = platformTexture.getRegionHeight() * PLATFORM_SCALE;

            playerPlatform.setPosition(
                stageWidth * PLAYER_PLATFORM_X,
                stageHeight * PLAYER_PLATFORM_Y
            );

            enemyPlatform.setPosition(
                stageWidth * ENEMY_PLATFORM_X,
                stageHeight * ENEMY_PLATFORM_Y
            );

            // Position Pokemon with proper offset
            playerPokemonImage.setScale(POKEMON_SCALE);
            enemyPokemonImage.setScale(POKEMON_SCALE);

            playerPokemonImage.setPosition(
                playerPlatformX + (platformWidth - playerPokemonImage.getWidth()) / 2f,
                playerPlatformY + platformHeight * 0.5f + PLATFORM_VERTICAL_OFFSET
            );

            enemyPokemonImage.setPosition(
                enemyPlatformX + (platformWidth - enemyPokemonImage.getWidth()) / 2f,
                enemyPlatformY + platformHeight * 0.5f + PLATFORM_VERTICAL_OFFSET
            );
        }
    }


    private void initializePokemonSprites() {
        TextureRegion playerTexture = playerPokemon.getBackSprite();
        TextureRegion enemyTexture = enemyPokemon.getFrontSprite();

        if (playerTexture == null || enemyTexture == null) {
            throw new RuntimeException("Failed to load Pokemon sprites");
        }

        playerPokemonImage = new Image(playerTexture);
        enemyPokemonImage = new Image(enemyTexture);

        // Set base size while maintaining aspect ratio
        float playerAspect = playerTexture.getRegionWidth() / (float) playerTexture.getRegionHeight();
        float enemyAspect = enemyTexture.getRegionWidth() / (float) enemyTexture.getRegionHeight();

        playerPokemonImage.setSize(POKEMON_BASE_SIZE * playerAspect, POKEMON_BASE_SIZE);
        enemyPokemonImage.setSize(POKEMON_BASE_SIZE * enemyAspect, POKEMON_BASE_SIZE);

        // Remove scaling
        playerPokemonImage.setScaling(Scaling.none);
        enemyPokemonImage.setScaling(Scaling.none);
    }


    private void initializeHUDElements() {
        // Player HP bar
        playerHUD = new PokemonHUD(skin, playerPokemon, true);
        playerHUD.setHPBar(playerHPBar);

        // Enemy HP bar
        enemyHUD = new PokemonHUD(skin, enemyPokemon, false);
        enemyHUD.setHPBar(enemyHPBar);
    }

    private void initializeUIComponents() {
        // Initialize battle text
        battleText = new Label("", skin);
        battleText.setWrap(true);
        battleText.setAlignment(Align.center);
        battleText.setTouchable(Touchable.disabled);


        // Create button style with better visibility
        TextButton.TextButtonStyle buttonStyle = new TextButton.TextButtonStyle();
        buttonStyle.font = skin.getFont("default");
        buttonStyle.up = createButtonBackground(new Color(0.2f, 0.2f, 0.5f, 0.9f));
        buttonStyle.down = createButtonBackground(new Color(0.15f, 0.15f, 0.4f, 0.9f));
        buttonStyle.over = createButtonBackground(new Color(0.25f, 0.25f, 0.6f, 0.9f));
        buttonStyle.fontColor = Color.WHITE;

        // Initialize buttons with fixed size
        fightButton = new TextButton("FIGHT", buttonStyle);
        bagButton = new TextButton("BAG", buttonStyle);
        pokemonButton = new TextButton("POKEMON", buttonStyle);
        runButton = new TextButton("RUN", buttonStyle);

        // Enable button interaction and make text bigger
        TextButton[] buttons = {fightButton, bagButton, pokemonButton, runButton};
        for (TextButton button : buttons) {
            button.setTouchable(Touchable.enabled);
            button.getLabel().setFontScale(1.2f);
            button.setSize(BUTTON_WIDTH, BUTTON_HEIGHT);
        }

        fightButton.setTouchable(Touchable.enabled);
        bagButton.setTouchable(Touchable.enabled);
        pokemonButton.setTouchable(Touchable.enabled);
        runButton.setTouchable(Touchable.enabled);
        // Create action menu
        actionMenu = new Table();
        actionMenu.setTouchable(Touchable.childrenOnly); // Important: allow touch events to reach buttons
        actionMenu.defaults().size(BUTTON_WIDTH, BUTTON_HEIGHT).pad(BUTTON_PADDING);
        actionMenu.setBackground(createTranslucentBackground(0.5f));

        // Add buttons to actionMenu with proper spacing
        actionMenu.add(fightButton).size(BUTTON_WIDTH, BUTTON_HEIGHT);
        actionMenu.add(bagButton).size(BUTTON_WIDTH, BUTTON_HEIGHT);
        actionMenu.add(pokemonButton).size(BUTTON_WIDTH, BUTTON_HEIGHT);
        actionMenu.add(runButton).size(BUTTON_WIDTH, BUTTON_HEIGHT);

        // Set up container properties
        actionMenu.pack(); // Important: properly size the container
        actionMenu.setTransform(false); // Better touch handling
        actionMenu.setTouchable(Touchable.enabled);
    }

    private void showActionMenu(boolean show) {
        if (actionMenu != null) {
            actionMenu.setVisible(show);
            actionMenu.setTouchable(show ? Touchable.enabled : Touchable.disabled);

            // Make sure buttons are properly enabled/disabled
            if (show) {
                fightButton.setDisabled(false);
                bagButton.setDisabled(false);
                pokemonButton.setDisabled(playerPokemon.getCurrentHp() <= 0);
                runButton.setDisabled(false);

                // Refresh touchable states
                fightButton.setTouchable(Touchable.enabled);
                bagButton.setTouchable(Touchable.enabled);
                pokemonButton.setTouchable(Touchable.enabled);
                runButton.setTouchable(Touchable.enabled);
            }
        }

        if (moveMenu != null) {
            moveMenu.setVisible(!show);
            moveMenu.setTouchable(!show ? Touchable.enabled : Touchable.disabled);
        }
        GameLogger.info("Action menu " + (show ? "shown" : "hidden") + " - State: " + currentState);
    }

    private void setBattleInterfaceEnabled(boolean enabled) {
        GameLogger.info("Setting battle interface enabled: " + enabled);

        // Update control section
        if (actionMenu != null) {
            actionMenu.setTouchable(enabled ? Touchable.enabled : Touchable.disabled);
            actionMenu.setVisible(enabled);
        }

        // Update button container
        if (fightButton != null) {
            TextButton[] buttons = {fightButton, bagButton, pokemonButton, runButton};
            for (TextButton button : buttons) {
                button.setDisabled(!enabled);
                button.setTouchable(enabled ? Touchable.enabled : Touchable.disabled);
                GameLogger.info(button.getName() + " enabled: " + enabled);
            }
        }
    }

    private void startBattleAnimation() {
        GameLogger.info("Starting battle animation");
        isAnimating = true;
        currentState = BattleState.INTRO;
        setBattleInterfaceEnabled(false);  // Disable interface during intro

        SequenceAction introSequence = Actions.sequence(
            Actions.run(() -> {
                battleText.setText("Wild " + enemyPokemon.getName() + " appeared!");
                GameLogger.info("Battle intro started");
            }),
            Actions.delay(1.0f),
            Actions.run(() -> {
                GameLogger.info("Battle intro complete - transitioning to player turn");
                isAnimating = false;
                currentState = BattleState.PLAYER_TURN;
                battleText.setText("What will " + playerPokemon.getName() + " do?");
                setBattleInterfaceEnabled(true);  // Enable interface after intro
                updateUI();
            })
        );

        addAction(introSequence);
    }

    private Table createTypeIcon(Pokemon.PokemonType type) {
        Table iconContainer = new Table();
        Color typeColor = TYPE_COLORS.get(type);

        // Create circular background
        Pixmap iconPixmap = new Pixmap(30, 30, Pixmap.Format.RGBA8888);
        iconPixmap.setColor(typeColor);
        iconPixmap.fillCircle(15, 15, 15);

        TextureRegionDrawable iconBg = new TextureRegionDrawable(
            new TextureRegion(new Texture(iconPixmap)));
        iconPixmap.dispose();

        iconContainer.setBackground(iconBg);
        return iconContainer;
    }

    private void updateHPBarColor(ProgressBar bar, float percentage) {
        String styleKey;
        if (percentage > 0.5f) {
            styleKey = "hp-bar-green";
        } else if (percentage > 0.2f) {
            styleKey = "hp-bar-yellow";
        } else {
            styleKey = "hp-bar-red";
        }

        bar.setStyle(skin.get(styleKey, ProgressBar.ProgressBarStyle.class));
    }

    private void initializeTextures() {
        // Load platform texture
        platformTexture = TextureManager.battlebacks.findRegion("battle_platform");
        if (platformTexture == null) {
            throw new RuntimeException("Failed to load battle platform texture");
        }

        // Load background texture
        TextureRegion battleBackground = TextureManager.battlebacks.findRegion("battle_bg_plains");
        if (battleBackground == null) {
            throw new RuntimeException("Failed to load battle background texture");
        }
    }

    private void setupHPBars() {
        // Player HP bar
        playerHPBar = new ProgressBar(0, playerPokemon.getStats().getHp(), 1, false,
            createHPBarStyle(playerPokemon.getCurrentHp() / (float) playerPokemon.getStats().getHp()));
        playerHPBar.setSize(HP_BAR_WIDTH, 8); // Reduce height
        playerHPBar.setValue(playerPokemon.getCurrentHp());

        // Enemy HP bar
        enemyHPBar = new ProgressBar(0, enemyPokemon.getStats().getHp(), 1, false,
            createHPBarStyle(enemyPokemon.getCurrentHp() / (float) enemyPokemon.getStats().getHp()));
        enemyHPBar.setSize(HP_BAR_WIDTH, 8); // Reduce height
        enemyHPBar.setValue(enemyPokemon.getCurrentHp());
    }

    private void handleBagButton() {
        // Implement bag functionality
        showNotImplementedMessage("Bag feature coming soon!");
    }

    private void handlePokemonButton() {
        // Implement Pokemon switch functionality
        showNotImplementedMessage("Pokemon switch feature coming soon!");
    }

    private void showNotImplementedMessage(String message) {
        // Show a message for unimplemented features
        battleText.setText(message);
    }

    private void createMoveMenu() {
        moveMenu = new Table();
        moveMenu.setFillParent(true);
        moveMenu.center();
        moveMenu.defaults().pad(5).size(200, 50);

        // Add moves from player's Pokemon
        for (Move move : playerPokemon.getMoves()) {
            TextButton moveButton = new TextButton(move.getName(), skin, "battle");
            moveButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    GameLogger.info("Move selected: " + move.getName());
                    executeMove(move, playerPokemon, enemyPokemon, true);
                }
            });
            moveMenu.add(moveButton).row();
        }

        // Add back button
        TextButton backButton = new TextButton("BACK", skin, "battle");
        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showMoveMenu(false);
            }
        });
        moveMenu.add(backButton).row();

        // Initially hide move menu
        moveMenu.setVisible(false);
        stage.addActor(moveMenu);
    }

    public void showMoveMenu(boolean show) {
        GameLogger.info("Showing move menu: " + show);
        if (moveMenu == null) {
            createMoveMenu();
        }
        moveMenu.setVisible(show);
        actionMenu.setVisible(!show);
    }

    private void showMoveSelection() {
        if (isAnimating || currentState != BattleState.PLAYER_TURN) return;

        GameLogger.info("Showing move selection menu");

        // Hide main action menu
        showActionMenu(false);

        if (moveSelectionMenu != null) {
            moveSelectionMenu.remove();
        }

        // Initialize move menu
        moveSelectionMenu = new Table();
        moveSelectionMenu.setBackground(createTranslucentBackground(0.8f));
        moveSelectionMenu.defaults().pad(10).size(200, 50);
        moveSelectionMenu.setTouchable(Touchable.enabled);

        // Create move grid
        Table moveGrid = new Table();
        moveGrid.defaults().pad(5).size(180, 45);
        moveGrid.setTouchable(Touchable.enabled);

        // Add moves
        for (Move move : playerPokemon.getMoves()) {
            Table moveButton = createMoveButton(move);
            moveButton.setTouchable(Touchable.enabled);

            moveButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (!isAnimating && currentState == BattleState.PLAYER_TURN) {
                        executeMove(move, playerPokemon, enemyPokemon, true);
                        hideMoveSelection();
                    }
                }
            });

            moveGrid.add(moveButton).pad(5);
            if (moveGrid.getCells().size % 2 == 0) {
                moveGrid.row();
            }
        }

        // Add back button
        TextButton backButton = new TextButton("BACK", skin);
        backButton.setTouchable(Touchable.enabled);
        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                hideMoveSelection();
                showActionMenu(true);
            }
        });

        moveSelectionMenu.add(moveGrid).expand().fill().pad(10).row();
        moveSelectionMenu.add(backButton).size(150, 40).pad(10);

        // Position the menu
        moveSelectionMenu.setPosition(
            (stage.getWidth() - moveSelectionMenu.getPrefWidth()) / 2,
            (stage.getHeight() - moveSelectionMenu.getPrefHeight()) / 2
        );

        stage.addActor(moveSelectionMenu);
    }

    private void updateMoveInfo(Move move) {
        if (powerLabel == null || accuracyLabel == null ||
            descriptionLabel == null || moveTypeLabel == null) {
            GameLogger.error("Move info labels not initialized");
            return;
        }

        // Update power
        String powerText = move.getPower() > 0 ? String.valueOf(move.getPower()) : "-";
        powerLabel.setText(powerText);

        // Update accuracy
        String accuracyText = move.getAccuracy() > 0 ? move.getAccuracy() + "%" : "-";
        accuracyLabel.setText(accuracyText);

        // Update type
        moveTypeLabel.setText(move.getType().toString());
        moveTypeLabel.setColor(TextureManager.getTypeColor(move.getType()));

        // Update description
        descriptionLabel.setText(move.getDescription());
    }

    private void hideMoveSelection() {
        if (moveSelectionMenu != null) {
            moveSelectionMenu.addAction(Actions.sequence(
                Actions.fadeOut(0.2f),
                Actions.removeActor()
            ));
        }
    }

    private void initializeMoveLabels() {
        // Create labels with default style
        powerLabel = new Label("", skin);
        accuracyLabel = new Label("", skin);
        descriptionLabel = new Label("", skin);
        moveTypeLabel = new Label("", skin);

        // Configure label properties
        powerLabel.setFontScale(0.9f);
        accuracyLabel.setFontScale(0.9f);
        descriptionLabel.setFontScale(0.8f);
        moveTypeLabel.setFontScale(0.9f);

        // Enable text wrapping for description
        descriptionLabel.setWrap(true);
    }

    private Table createMoveButton(final Move move) {
        Table button = new Table();

        // Get type-based style
        Color typeColor = TYPE_COLORS.getOrDefault(move.getType(), TYPE_COLORS.get(Pokemon.PokemonType.NORMAL));
        button.setBackground(createGradientBackground(typeColor));

        // Move name
        Label nameLabel = new Label(move.getName(), new Label.LabelStyle(skin.getFont("default"), Color.WHITE));
        nameLabel.setFontScale(1.2f);

        // PP counter
        Label ppLabel = new Label(move.getPp() + "/" + move.getMaxPp(),
            new Label.LabelStyle(skin.getFont("default"), Color.WHITE));

        // Type icon
        Table typeIcon = createTypeIcon(move.getType());

        // Layout
        Table content = new Table();
        content.add(nameLabel).left().expandX().row();
        content.add(ppLabel).left().padTop(5);

        button.add(content).expand().fill().pad(10);
        button.add(typeIcon).size(30).right().pad(10);

        // Click handling
        button.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                executeMove(move, playerPokemon, enemyPokemon, true);
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                button.setColor(button.getColor().mul(1.2f));
                updateMoveInfo(move);
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                button.setColor(typeColor);
            }
        });
        setTouchable(Touchable.enabled);
        return button;
    }

    private TextureRegionDrawable createGradientBackground(Color baseColor) {
        Pixmap pixmap = new Pixmap(300, 80, Pixmap.Format.RGBA8888);

        // Create gradient effect
        for (int x = 0; x < 300; x++) {
            float alpha = 0.9f;
            float factor = x / 300f;
            Color gradientColor = new Color(
                baseColor.r + (factor * 0.2f),
                baseColor.g + (factor * 0.2f),
                baseColor.b + (factor * 0.2f),
                alpha
            );
            pixmap.setColor(gradientColor);
            for (int y = 0; y < 80; y++) {
                pixmap.drawPixel(x, y);
            }
        }

        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return new TextureRegionDrawable(new TextureRegion(texture));
    }

    public void update(float delta) {
        stateTimer += delta;

        // Process any pending animations
        if (!pendingActions.isEmpty() && !isAnimating) {
            Action nextAction = pendingActions.removeIndex(0);
            addAction(nextAction);
        }

        // Update battle state
        switch (currentState) {
            case INTRO:
                updateIntroState();
                break;
            case PLAYER_TURN:
                updatePlayerTurn();
                break;
            case ENEMY_TURN:
                updateEnemyTurn();
                break;
            case ANIMATING:
                updateAnimations(delta);
                break;
            case ENDED:
                updateEndState();
                break;
        }

        // Update UI elements
        updateHPBars();
        updateStatusEffects();
    }

    private void finishMoveExecution(boolean isPlayerMove) {
        Move usedMove;
        if (isPlayerMove) {
            usedMove = playerPokemon.getMoves().get(selectedMoveIndex);
        } else {
            usedMove = enemyPokemon.getMoves().get(0);
        }
        applyEndOfTurnEffects(isPlayerMove ? playerPokemon : enemyPokemon);

        if (checkBattleEnd()) {
            return;
        }
        if (callback != null) {
            callback.onMoveUsed(
                isPlayerMove ? playerPokemon : enemyPokemon,
                usedMove,
                isPlayerMove ? enemyPokemon : playerPokemon
            );
            callback.onTurnEnd(isPlayerMove ? playerPokemon : enemyPokemon);
        }
        isAnimating = false;
        transitionToState(isPlayerMove ? BattleState.ENEMY_TURN : BattleState.PLAYER_TURN);

        playerHUD.update();
        enemyHUD.update();
    }

    private void updateEndState() {
        if (!isAnimating) {
            boolean playerWon = playerPokemon.getCurrentHp() > 0;

            SequenceAction endSequence = Actions.sequence(
                Actions.run(() -> showBattleText(
                    playerWon ? "Victory!" :
                        playerPokemon.getName() + " fainted!"
                )),
                Actions.delay(2.0f),
                Actions.parallel(
                    Actions.run(() -> {
                        playerPokemonImage.addAction(Actions.fadeOut(1.0f));
                        enemyPokemonImage.addAction(Actions.fadeOut(1.0f));
                    })
                ),
                Actions.delay(1.0f),
                Actions.run(() -> {
                    if (callback != null) {
                        callback.onBattleEnd(playerWon);
                    }
                })
            );

            addAction(endSequence);
            isAnimating = true;
        }
    }

    private void updateIntroState() {
        if (stateTimer >= ANIMATION_DURATION) {
            transitionToState(BattleState.PLAYER_TURN);
            showBattleText(generateBattleStartText());
        }
    }

    private void updatePlayerTurn() {
        if (!actionMenu.isVisible() && !isAnimating) {
            showActionMenu(true);
        }
    }

    private void updateEnemyTurn() {
        if (!isAnimating && stateTimer >= 0.5f) { // Small delay before enemy action
            executeEnemyMove();
        }
    }

    private void updateAnimations(float delta) {
        if (!isAnimating) {
            if (currentState == BattleState.PLAYER_TURN) {
                checkBattleEnd();
            } else if (currentState == BattleState.ENEMY_TURN) {
                transitionToState(BattleState.PLAYER_TURN);
            }
        }
    }

    private void executeMove(Move move, Pokemon attacker, Pokemon defender, boolean isPlayerMove) {
        if (isAnimating) return;

        isAnimating = true;
        showActionMenu(false); // Hide action menu when move starts
        if (moveSelectionMenu != null) {
            moveSelectionMenu.remove(); // Remove the move selection menu
        }


        SequenceAction moveSequence = Actions.sequence(
            // Hide all menus first
            Actions.run(() -> {
                if (actionMenu != null) actionMenu.setVisible(false);
                if (moveMenu != null) moveMenu.setVisible(false);
            }),
            Actions.delay(0.5f),

            Actions.run(() -> {
                showBattleText(attacker.getName() + " used " + move.getName() + "!");
            }),
            Actions.delay(0.5f),

            // 3. Show damage effect
            Actions.run(() -> {
                float damage = calculateDamage(move, attacker, defender);
                applyDamage(defender, damage);

                // Flash the defender sprite
                Image defenderSprite = (defender == playerPokemon) ? playerPokemonImage : enemyPokemonImage;
                defenderSprite.addAction(Actions.sequence(
                    Actions.color(Color.RED),
                    Actions.delay(0.1f),
                    Actions.color(Color.WHITE)
                ));

                // Shake effect for powerful moves
                if (move.getPower() > 80) {
                    startShakeEffect();
                }
            }),
            Actions.delay(0.5f),

            // 4. Show effectiveness message
            Actions.run(() -> {
                showEffectivenessMessage(move, defender);
            }),
            Actions.delay(0.5f),

            // 5. End move execution
            Actions.run(() -> {
                finishMoveExecution(isPlayerMove);
            })
        );

        // Add the sequence to the stage
        stage.addAction(moveSequence);
    }

    private void showEffectivenessMessage(Move move, Pokemon defender) {
        float effectiveness = calculateTypeEffectiveness(move.getType(), defender);

        if (effectiveness > 1.5f) {
            showBattleText("It's super effective!");
        } else if (effectiveness < 0.5f) {
            showBattleText("It's not very effective...");
        } else if (effectiveness == 0) {
            showBattleText("It had no effect...");
        }
    }

    private float calculateTypeEffectiveness(Pokemon.PokemonType moveType, Pokemon defender) {
        float effectiveness = getTypeEffectiveness(moveType, defender.getPrimaryType());

        if (defender.getSecondaryType() != null) {
            effectiveness *= getTypeEffectiveness(moveType, defender.getSecondaryType());
        }

        return effectiveness;
    }

    private float calculateDamage(Move move, Pokemon attacker, Pokemon defender) {
        float baseDamage = move.getPower() * (move.isSpecial() ?
            (float) attacker.getStats().getSpecialAttack() / defender.getStats().getSpecialDefense() :
            (float) attacker.getStats().getAttack() / defender.getStats().getDefense());

        float typeMultiplier = getTypeEffectiveness(move.getType(), defender.getPrimaryType());
        if (defender.getSecondaryType() != null) {
            typeMultiplier *= getTypeEffectiveness(move.getType(), defender.getSecondaryType());
        }

        float variation = MathUtils.random(0.85f, 1.0f);

        float statusMultiplier = attacker.getStatusModifier(move);

        return baseDamage * typeMultiplier * variation * statusMultiplier;
    }

    private void applyDamage(Pokemon target, float damage) {
        // Flash the Pokemon sprite
        Image targetSprite = target == playerPokemon ? playerPokemonImage : enemyPokemonImage;
        targetSprite.addAction(Actions.sequence(
            Actions.color(Color.RED),
            Actions.delay(DAMAGE_FLASH_DURATION),
            Actions.color(Color.WHITE)
        ));

        float newHP = Math.max(0, target.getCurrentHp() - damage);
        animateHPBar(target, newHP);
        target.setCurrentHp(newHP);
    }

    private void animateHPBar(Pokemon pokemon, float newHP) {
        ProgressBar hpBar = pokemon == playerPokemon ? playerHPBar : enemyHPBar;
        float startHP = hpBar.getValue();

        hpBar.addAction(Actions.sequence(
            Actions.run(() -> updateHPBarColor(hpBar, newHP / pokemon.getStats().getHp())),
            Actions.moveTo(startHP, newHP, HP_UPDATE_DURATION, Interpolation.smooth)
        ));
    }

    private void setupBattleButtons(Table buttonContainer) {
        GameLogger.info("Setting up battle buttons");

        // Create button style with proper states
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = skin.getFont("default");
        style.up = createButtonBackground(new Color(0.2f, 0.2f, 0.5f, 0.9f));
        style.down = createButtonBackground(new Color(0.15f, 0.15f, 0.4f, 1f));
        style.over = createButtonBackground(new Color(0.25f, 0.25f, 0.6f, 0.9f));
        style.fontColor = Color.WHITE;
        style.disabledFontColor = new Color(0.7f, 0.7f, 0.7f, 1f);

        // Initialize buttons
        fightButton = new TextButton("FIGHT", style);
        fightButton.setName("FightButton");
        fightButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                GameLogger.info("Fight button clicked - State: " + currentState + " Animating: " + isAnimating);
                if (!isAnimating && currentState == BattleState.PLAYER_TURN) {
                    showMoveSelection();
                    event.stop();
                }
            }
        });

        bagButton = new TextButton("BAG", style);
        bagButton.setName("BagButton");
        bagButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                GameLogger.info("Bag button clicked - State: " + currentState + " Animating: " + isAnimating);
                if (!isAnimating && currentState == BattleState.PLAYER_TURN) {
                    handleBagButton();
                    event.stop();
                }
            }
        });

        pokemonButton = new TextButton("POKEMON", style);
        pokemonButton.setName("PokemonButton");
        pokemonButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                GameLogger.info("Pokemon button clicked - State: " + currentState + " Animating: " + isAnimating);
                if (!isAnimating && currentState == BattleState.PLAYER_TURN) {
                    handlePokemonButton();
                    event.stop();
                }
            }
        });

        runButton = new TextButton("RUN", style);
        runButton.setName("RunButton");
        runButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                GameLogger.info("Run button clicked - State: " + currentState + " Animating: " + isAnimating);
                if (!isAnimating && currentState == BattleState.PLAYER_TURN) {
                    attemptRun();
                    event.stop();
                }
            }
        });

        // Set up button grid
        Table buttonGrid = new Table();
        buttonGrid.setName("ButtonGrid");
        buttonGrid.setTouchable(Touchable.enabled);
        buttonGrid.defaults().size(BUTTON_WIDTH, BUTTON_HEIGHT).pad(BUTTON_PADDING);

        // Add buttons to grid with proper layout
        buttonGrid.add(fightButton).expand().fill().pad(BUTTON_PADDING);
        buttonGrid.add(bagButton).expand().fill().pad(BUTTON_PADDING).row();
        buttonGrid.add(pokemonButton).expand().fill().pad(BUTTON_PADDING);
        buttonGrid.add(runButton).expand().fill().pad(BUTTON_PADDING);

        // Set up button container
        buttonContainer.setName("ButtonContainer");
        buttonContainer.setTouchable(Touchable.enabled);
        buttonContainer.setTransform(false);
        buttonContainer.add(buttonGrid).expand().fill().pad(5);

        // Ensure buttons are touchable and enabled
        TextButton[] buttons = {fightButton, bagButton, pokemonButton, runButton};
        for (TextButton btn : buttons) {
            btn.setTouchable(Touchable.enabled);
        }

        enableBattleButtons(currentState == BattleState.PLAYER_TURN && !isAnimating);
        GameLogger.info("Battle buttons setup complete - Current state: " + currentState +
            " Animating: " + isAnimating);
    }


    private float calculateTypeEffectiveness(Move move, Pokemon defender) {
        if (move == null || defender == null) {
            return 1.0f;
        }

        float effectiveness = getTypeEffectiveness(move.getType(), defender.getPrimaryType());

        // Consider secondary type if present
        if (defender.getSecondaryType() != null) {
            effectiveness *= getTypeEffectiveness(move.getType(), defender.getSecondaryType());
        }

        return effectiveness;
    }

    private void showEffectivenessMessage(float effectiveness) {
        if (effectiveness > 1.5f) {
            showBattleText("It's super effective!");
            AudioManager.getInstance().playSound(AudioManager.SoundEffect.SUPER_EFFECTIVE);
        } else if (effectiveness < 0.5f) {
            showBattleText("It's not very effective...");
            AudioManager.getInstance().playSound(AudioManager.SoundEffect.NOT_EFFECTIVE);
        } else if (effectiveness == 0) {
            showBattleText("It had no effect...");
        }
    }
    private void enableBattleButtons(boolean enable) {
        if (fightButton == null) return;

        TextButton[] buttons = {fightButton, bagButton, pokemonButton, runButton};
        for (TextButton button : buttons) {
            button.setDisabled(!enable);
            button.setTouchable(enable ? Touchable.enabled : Touchable.childrenOnly);
            GameLogger.info(button.getName() + " enabled: " + enable +
                " touchable: " + button.getTouchable());
        }
    }

    private boolean checkBattleEnd() {
        if (playerPokemon.getCurrentHp() <= 0 || enemyPokemon.getCurrentHp() <= 0 ||
            turnCount >= MAX_TURN_COUNT) {

            boolean playerWon = playerPokemon.getCurrentHp() > 0 && enemyPokemon.getCurrentHp() <= 0;
            currentState = BattleState.ENDED;
            isAnimating = true;

            // Create ending sequence
            SequenceAction endSequence = Actions.sequence(
                Actions.run(() -> {
                    if (turnCount >= MAX_TURN_COUNT) {
                        showBattleText("Battle ended in a draw!");
                    } else {
                        showBattleText(playerWon ? "Victory!" : playerPokemon.getName() + " fainted!");
                    }
                }),
                Actions.delay(2.0f),
                Actions.parallel(
                    Actions.run(() -> {
                        if (playerPokemonImage != null) {
                            playerPokemonImage.addAction(Actions.fadeOut(1.0f));
                        }
                        if (enemyPokemonImage != null) {
                            enemyPokemonImage.addAction(Actions.fadeOut(1.0f));
                        }
                    })
                ),
                Actions.delay(1.0f),
                Actions.run(() -> {
                    if (callback != null) {
                        callback.onBattleEnd(playerWon);
                    }
                    dispose();
                })
            );

            addAction(endSequence); // Add to BattleTable instead of battleScene
            return true;
        }
        return false;
    }

    private void showBattleText(String text) {
        battleText.setText(text);
        battleText.addAction(Actions.sequence(
            Actions.alpha(0),
            Actions.fadeIn(0.2f)
        ));
    }

    private void transitionToState(BattleState newState) {
        GameLogger.info("Transitioning from " + currentState + " to " + newState);

        boolean wasAnimating = isAnimating;
        currentState = newState;
        stateTimer = 0;

        switch (newState) {
            case INTRO:
                isAnimating = true;
                enableBattleButtons(false);
                break;

            case PLAYER_TURN:
                isAnimating = false;
                enableBattleButtons(true);
                showActionMenu(true);
                break;

            case ENEMY_TURN:
                isAnimating = false;
                enableBattleButtons(false);
                showActionMenu(false);
                break;

            case ENDED:
                isAnimating = false;
                enableBattleButtons(false);
                showActionMenu(false);
                break;
        }

        // Update UI if animation state changed
        if (wasAnimating != isAnimating) {
            updateUI();
        }

        GameLogger.info("State transition complete - " +
            "State: " + currentState +
            " Animating: " + isAnimating +
            " Buttons enabled: " + (currentState == BattleState.PLAYER_TURN && !isAnimating));
    }

    private String generateBattleStartText() {
        return "Wild " + enemyPokemon.getName() + " appeared!";
    }

    private void updateHPBars() {
        // Update player HP bar
        float playerHPPercent = playerPokemon.getCurrentHp() / playerPokemon.getStats().getHp();
        playerHPBar.setValue(playerPokemon.getCurrentHp());
        updateHPBarColor(playerHPBar, playerHPPercent);

        // Update enemy HP bar
        float enemyHPPercent = enemyPokemon.getCurrentHp() / enemyPokemon.getStats().getHp();
        enemyHPBar.setValue(enemyPokemon.getCurrentHp());
        updateHPBarColor(enemyHPBar, enemyHPPercent);
    }

    private void updateStatusEffects() {
        // Update player Pokemon status
        if (playerPokemon.hasStatus()) {
            playerHUD.updateStatusIcon(TextureManager.getStatusIcon(
                TextureManager.StatusCondition.valueOf(playerPokemon.getStatus().name())));
        }

        // Update enemy Pokemon status
        if (enemyPokemon.hasStatus()) {
            enemyHUD.updateStatusIcon(TextureManager.getStatusIcon(
                TextureManager.StatusCondition.valueOf(enemyPokemon.getStatus().name())));
        }
    }

    private void executeStruggle(Pokemon attacker, Pokemon defender) {
        // Struggle implementation - damages both Pokemon
        float damage = attacker.getStats().getAttack() * 0.5f;
        float recoil = damage * 0.25f;

        applyDamage(defender, damage);
        applyDamage(attacker, recoil);

        showBattleText(attacker.getName() + " used Struggle!");
    }

    private float getTypeEffectiveness(Pokemon.PokemonType attackType, Pokemon.PokemonType defendType) {
        if (attackType == null || defendType == null) {
            GameLogger.error("Null type detected in getTypeEffectiveness: attackType=" + attackType + ", defendType=" + defendType);
            return 1.0f; // Neutral effectiveness when types are unknown
        }
        ObjectMap<Pokemon.PokemonType, Float> effectivenessMap = typeEffectiveness.get(attackType);
        if (effectivenessMap == null) {
            GameLogger.error("No effectiveness data for attackType: " + attackType);
            return 1.0f; // Default to neutral effectiveness
        }
        return effectivenessMap.get(defendType, 1.0f);
    }

    private enum BattleState {
        INTRO,        // Initial battle animation
        PLAYER_TURN,  // Player selecting action
        ENEMY_TURN,   // Enemy AI selecting action
        ANIMATING,    // Move animations playing
        ENDED,        // Battle complete
        RUNNING,      // Attempting to flee
        CATCHING      // Pokeball throw animation
    }


    public interface BattleCallback {
        void onBattleEnd(boolean playerWon);

        void onTurnEnd(Pokemon activePokemon);

        void onStatusChange(Pokemon pokemon, Pokemon.Status newStatus);

        void onMoveUsed(Pokemon user, Move move, Pokemon target);
    }

    private static class PokemonHUD extends Table {
        private final Table statusContainer;
        private final Pokemon pokemon;
        private Label hpLabel;
        private ProgressBar hpBar;
        private Label nameLabel;
        private Label levelLabel;
        private Skin skin;

        public PokemonHUD(Skin skin, Pokemon pokemon, boolean isPlayer) {
            this.pokemon = pokemon;
            this.skin = skin;


            // Name and Level
            nameLabel = new Label(pokemon.getName(), skin);
            levelLabel = new Label("Lv." + pokemon.getLevel(), skin);

            // Top row
            Table topRow = new Table();
            topRow.add(nameLabel).left().expandX();
            topRow.add(levelLabel).right();

            // HP Label
            hpLabel = new Label((int) pokemon.getCurrentHp() + "/" + (int) pokemon.getStats().getHp(), skin);
            hpLabel.setFontScale(0.7f);

            statusContainer = new Table();
            statusContainer.setName("statusContainer");

            // Layout without backgrounds
            add(topRow).expandX().fillX().pad(2).row();
            add(hpLabel).expandX().pad(2);

            setVisible(true);
        }

        private Drawable createHUDBackground() {
            Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pixmap.setColor(0, 0, 0, 0.5f);
            pixmap.fill();
            TextureRegion region = new TextureRegion(new Texture(pixmap));
            pixmap.dispose();
            return new TextureRegionDrawable(region);
        }

        private String getHPStyleKey(float percentage) {
            if (percentage > 0.5f) {
                return "hp-bar-green";
            } else if (percentage > 0.2f) {
                return "hp-bar-yellow";
            } else {
                return "hp-bar-red";
            }
        }

        public void update() {
            levelLabel.setText("Lv." + pokemon.getLevel());

            float currentPercentage = pokemon.getCurrentHp() / (float) pokemon.getStats().getHp();
            String newStyleKey = getHPStyleKey(currentPercentage);

            hpBar.setStyle(skin.get(newStyleKey, ProgressBar.ProgressBarStyle.class));
            hpBar.setValue(pokemon.getCurrentHp());

            hpLabel.setText((int) pokemon.getCurrentHp() + "/" + (int) pokemon.getStats().getHp());
        }


        public void setHPBar(ProgressBar hpBar) {
            this.hpBar = hpBar;

            // Remove existing HP bar if present
            for (Cell<?> cell : getCells()) {
                if (cell.getActor() == hpBar) {
                    cell.setActor(null);
                }
            }

            // Add HP bar directly without any container
            add(hpBar).expandX().fillX().height(8).pad(4).row();
        }

        @Override
        public void layout() {
            super.layout();
            // Ensure proper scaling of the HUD components
            setScale(1.0f);
            invalidateHierarchy();
        }

        public void updateStatusIcon(TextureRegion statusIcon) {
            statusContainer.clear();
            if (statusIcon != null) {
                Image statusImage = new Image(statusIcon);
                statusContainer.add(statusImage).size(32).pad(2);
            }
        }
    }

    public class ActionButton extends TextButton {
        private final Runnable action;
        private final BattleTable battleTable;

        public ActionButton(String text, TextButtonStyle style, Runnable action, BattleTable battleTable) {
            super(text, style);
            this.action = action;
            this.battleTable = battleTable;

            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    GameLogger.info(text + " button clicked - State: " + battleTable.getCurrentState() +
                        " Animating: " + battleTable.isAnimating() +
                        " Disabled: " + isDisabled());

                    if (!isDisabled() && !battleTable.isAnimating() &&
                        battleTable.getCurrentState() == BattleState.PLAYER_TURN) {
                        action.run();
                    }
                }
            });
        }

        @Override
        public void act(float delta) {
            super.act(delta);
            boolean enabled = battleTable.getCurrentState() == BattleState.PLAYER_TURN &&
                !battleTable.isAnimating();
            setDisabled(!enabled);
        }
    }
}
