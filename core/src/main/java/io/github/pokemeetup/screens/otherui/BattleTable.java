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
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
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
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

public class BattleTable extends Table {
    private final Stage stage;
    private final Skin skin;
    private final Pokemon enemyPokemon;
    private Pokemon playerPokemon;

    // UI Elements
    private Image playerPokemonImage, enemyPokemonImage;
    private ProgressBar playerHPBar, enemyHPBar, expBar;
    private Label playerInfoLabel, enemyInfoLabel, battleText;
    private Image playerStatusIcon, enemyStatusIcon;
    private Table actionMenu, moveSelectionTable;
    private TextButton fightButton, bagButton, pokemonButton, runButton;

    // State Management
    private BattleState currentState;
    private BattleCallback callback;
    private boolean isAnimating = false;
    private boolean playerActionTaken = false;
    private final Queue<BattleMessage> messageQueue = new LinkedList<>();
    private boolean processingMessage = false;
    public static final int STATUS_ICON_WIDTH = 44;
    public static final int STATUS_ICON_HEIGHT = 16;
    private static final float BATTLE_SCREEN_WIDTH = 800f;
    private static final float BATTLE_SCREEN_HEIGHT = 480f;
    private static final float HP_BAR_WIDTH = 100f;
    private static final float DAMAGE_FLASH_DURATION = 0.1f;
    private static final float MOVE_EXECUTION_DELAY = 0.7f;
    private static final float POST_DAMAGE_DELAY = 0.8f;
    private static final float POST_EFFECT_DELAY = 1.0f;
    private static final float MULTI_HIT_DELAY = 0.3f;

    // Type effectiveness colors
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
    }};

    private static ObjectMap<Pokemon.PokemonType, ObjectMap<Pokemon.PokemonType, Float>> typeEffectiveness = new ObjectMap<>();

    static {
        initializeTypeEffectiveness();
    }

    // Instance fields
    private TextureRegion platformTexture;
    private Image playerPlatform, enemyPlatform;
    private float stateTimer = 0;

    // Enhanced UI elements
    private Table weatherDisplay;
    private Label weatherLabel;
    private boolean moveSelectionActive = false;

    // Enhanced battle tracking
    private int turnCount = 0;
    private float criticalHitChance = 0.0625f; // Base 6.25% crit chance
    private Map<Pokemon, Integer> leechSeedTargets = new HashMap<>();

    // Message queue for better text flow

    private enum BattleState {
        INTRO,
        PLAYER_CHOICE,
        PLAYER_MOVE_SELECT,
        PLAYER_MOVE_EXECUTE,
        ENEMY_TURN,
        PLAYER_SWITCHING,
        ENEMY_SWITCHING,
        WAITING_FOR_FAINT,
        PLAYER_PARTY_SCREEN,
        BAG_SCREEN,
        BATTLE_OVER_VICTORY,
        BATTLE_OVER_DEFEAT,
        RUN_ATTEMPT,
        CATCH_ATTEMPT,
        FORCED_SWITCH,
        CATCHING,
        MESSAGE_DISPLAY
    }

    // Message class for queue
    private static class BattleMessage {
        String text;
        float duration;
        Runnable onComplete;

        BattleMessage(String text, float duration, Runnable onComplete) {
            this.text = text;
            this.duration = duration;
            this.onComplete = onComplete;
        }
    }    public BattleTable(Stage stage, Skin skin, Pokemon playerPokemon, Pokemon enemyPokemon) {
        super();
        this.stage = stage;
        this.currentState = BattleState.INTRO;
        this.skin = skin;
        this.playerPokemon = playerPokemon;
        this.enemyPokemon = enemyPokemon;
        setFillParent(true);
        setTouchable(Touchable.enabled);
        setZIndex(100);
        // stage.addActor(this); // REMOVED - GameScreen is responsible for adding this table.
        stage.setViewport(new FitViewport(BATTLE_SCREEN_WIDTH, BATTLE_SCREEN_HEIGHT));
        stage.getViewport().update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);

        ensureHPBarStyles();

        try {
            initializeTextures();
            initializeUIComponents();
            initializePlatforms();
            initializePokemonSprites();
            setupHPBars();
            setupWeatherDisplay();
            setupContainer();
            startBattleAnimation();
        } catch (Exception e) {
            GameLogger.error("Error initializing battle table: " + e.getMessage());
        }
    }

    private static void initializeTypeEffectiveness() {
        // Initialize all type effectiveness
        for (Pokemon.PokemonType type : Pokemon.PokemonType.values()) {
            typeEffectiveness.put(type, new ObjectMap<>());
            for (Pokemon.PokemonType defType : Pokemon.PokemonType.values()) {
                typeEffectiveness.get(type).put(defType, 1.0f);
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

        // Poison type
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

    private static void initTypeEffectiveness(Pokemon.PokemonType attackType,
                                              ObjectMap<Pokemon.PokemonType, Float> effectiveness) {
        typeEffectiveness.get(attackType).putAll(effectiveness);
    }

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

    // Enhanced message system
    public void queueMessage(String text, float duration, Runnable onComplete) {
        messageQueue.offer(new BattleMessage(text, duration, onComplete));
        if (!processingMessage) {
            processNextMessage();
        }
    }

    private void queueMessage(String text, float duration) {
        queueMessage(text, duration, null);
    }

    public void queueMessage(String text) {
        queueMessage(text, 1.5f, null);
    }

    private void processNextMessage() {
        if (messageQueue.isEmpty()) {
            processingMessage = false;
            return;
        }

        processingMessage = true;
        BattleMessage msg = messageQueue.poll();
        displayMessage(msg.text);

        addAction(Actions.sequence(
            Actions.delay(msg.duration),
            Actions.run(() -> {
                if (msg.onComplete != null) {
                    msg.onComplete.run();
                }
                processNextMessage();
            })
        ));
    }

    public void displayMessage(String message) {
        battleText.setText(message);
        battleText.clearActions();
        battleText.getColor().a = 1f;
    }

    // Enhanced move replacement dialog
    public void showMoveReplacementDialog(final Move newMove) {
        final Window replacementWindow = new Window("Learn New Move", skin);
        replacementWindow.setModal(true);
        replacementWindow.setMovable(false);
        replacementWindow.pad(20);

        Table content = new Table();

        Label promptLabel = new Label(playerPokemon.getName() + " wants to learn " + newMove.getName() + "!", skin);
        promptLabel.setWrap(true);
        promptLabel.setAlignment(Align.center);
        content.add(promptLabel).colspan(2).width(300).pad(10).row();

        Label instructionLabel = new Label("But " + playerPokemon.getName() + " already knows 4 moves.\nSelect a move to forget:", skin);
        instructionLabel.setAlignment(Align.center);
        content.add(instructionLabel).colspan(2).pad(10).row();

        // Display current moves with details
        final java.util.List<Move> currentMoves = playerPokemon.getMoves();
        Table movesTable = new Table();

        for (int i = 0; i < currentMoves.size(); i++) {
            final int index = i;
            Move move = currentMoves.get(i);

            Table moveInfo = new Table();
            moveInfo.setBackground(createTranslucentBackground(0.3f));
            moveInfo.pad(10);

            Label nameLabel = new Label(move.getName(), skin);
            nameLabel.setColor(TYPE_COLORS.get(move.getType()));
            moveInfo.add(nameLabel).expandX().left();

            Label ppLabel = new Label("PP: " + move.getPp() + "/" + move.getMaxPp(), skin);
            ppLabel.setFontScale(0.8f);
            moveInfo.add(ppLabel).padLeft(10).row();

            Label powerLabel = new Label("Power: " + (move.getPower() > 0 ? move.getPower() : "-"), skin);
            powerLabel.setFontScale(0.8f);
            moveInfo.add(powerLabel).expandX().left();

            Label accLabel = new Label("Acc: " + move.getAccuracy() + "%", skin);
            accLabel.setFontScale(0.8f);
            moveInfo.add(accLabel).padLeft(10);

            TextButton selectButton = new TextButton("Replace", skin);
            selectButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    currentMoves.set(index, newMove);
                    queueMessage(playerPokemon.getName() + " forgot " + move.getName() + " and learned " + newMove.getName() + "!");
                    AudioManager.getInstance().playSound(AudioManager.SoundEffect.MOVE_SELECT);
                    replacementWindow.remove();
                }
            });

            movesTable.add(moveInfo).width(250).pad(5);
            movesTable.add(selectButton).width(80).pad(5).row();
        }

        content.add(movesTable).colspan(2).row();

        // Option to not learn the move
        TextButton cancelButton = new TextButton("Don't learn " + newMove.getName(), skin);
        cancelButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                queueMessage(playerPokemon.getName() + " did not learn " + newMove.getName() + ".");
                replacementWindow.remove();
            }
        });
        content.add(cancelButton).colspan(2).width(200).height(40).padTop(10);

        replacementWindow.add(content);
        replacementWindow.pack();
        replacementWindow.setPosition(
            (stage.getWidth() - replacementWindow.getWidth()) / 2,
            (stage.getHeight() - replacementWindow.getHeight()) / 2
        );

        stage.addActor(replacementWindow);
    }

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
     * Sets up the main layout of the battle screen, including backgrounds, platforms,
     * Pokémon sprites, and the info/action boxes. This creates the classic
     * "player on bottom-left, enemy on top-right" view.
     */
    private void setupLayout() {
        clear(); // Clear any previous actors from the table

        // 1. Background
        // A random battle background is selected.
        String bgName = "bg-grass"; // Default
        // You could add logic here to pick a background based on biome
        TextureRegion background = TextureManager.battlebacks.findRegion(bgName);
        if (background != null) {
            setBackground(new TextureRegionDrawable(background));
        }

        // 2. Platforms for the Pokémon
        // These are images that the Pokémon sprites will "stand" on.
        TextureRegion platformTexture = TextureManager.battlebacks.findRegion("battle_platform");
        Image playerPlatform = new Image(platformTexture);
        Image enemyPlatform = new Image(platformTexture);
        playerPlatform.setScaling(Scaling.fit);
        enemyPlatform.setScaling(Scaling.fit);

        // 3. Main container table to organize the screen
        Table mainContainer = new Table();
        mainContainer.setFillParent(true);

        // This Stack will hold the enemy's platform and sprite
        Stack enemyStack = new Stack();
        enemyStack.add(enemyPlatform);
        enemyStack.add(enemyPokemonImage);

        // This Stack will hold the player's platform and sprite
        Stack playerStack = new Stack();
        playerStack.add(playerPlatform);
        playerStack.add(playerPokemonImage);

        // The info boxes for HP, level, etc.
        Table enemyInfoBox = createInfoBox(enemyPokemon, enemyInfoLabel, enemyHPBar, enemyStatusIcon);
        Table playerInfoBox = createInfoBox(playerPokemon, playerInfoLabel, playerHPBar, playerStatusIcon);

        // Add the EXP bar to the player's info box
        playerInfoBox.row();
        playerInfoBox.add(expBar).colspan(2).width(HP_BAR_WIDTH).height(6).pad(2, 5, 5, 5).left();

        // Arrange the main components on the screen
        mainContainer.add(enemyInfoBox).expand().top().left().pad(20).width(250);
        mainContainer.add(enemyStack).expand().top().right().pad(20, 0, 0, 50).size(200, 100);
        mainContainer.row();
        mainContainer.add(playerStack).expand().bottom().left().pad(0, 50, 80, 0).size(200, 100);
        mainContainer.add(playerInfoBox).expand().bottom().right().pad(0, 0, 80, 20).width(250);

        addActor(mainContainer);

        // The message box at the bottom
        Table messageBox = new Table(skin);
        messageBox.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("battle_message_box")));
        battleText.setWrap(true);
        messageBox.add(battleText).expand().fill().pad(10, 20, 10, 20);

        // Use a separate Stack to layer the message box and action menu
        Stack bottomUI = new Stack();
        bottomUI.setFillParent(true);
        bottomUI.add(messageBox);
        bottomUI.add(actionMenu); // The action menu will sit on top

        addActor(bottomUI);

        // Position the action menu (initially invisible)
        actionMenu.pack();
        actionMenu.setPosition(
            getWidth() - actionMenu.getWidth() - 10,
            messageBox.getHeight() + 10 // Position it right above the message box
        );
    }

    /**
     * Creates the main action menu with FIGHT, BAG, POKEMON, and RUN buttons.
     * This menu is shown when it's the player's turn to choose an action.
     * @return A Table containing the configured action buttons.
     */
    private Table createActionMenu() {
        Table menu = new Table(skin);
        // Using a semi-transparent background for a modern feel
        menu.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("battle_choice_box")));

        // Button style for a cleaner look
        TextButton.TextButtonStyle buttonStyle = new TextButton.TextButtonStyle(skin.get("default", TextButton.TextButtonStyle.class));
        buttonStyle.fontColor = Color.BLACK;

        fightButton = new TextButton("FIGHT", buttonStyle);
        bagButton = new TextButton("BAG", buttonStyle);
        pokemonButton = new TextButton("POKEMON", buttonStyle);
        runButton = new TextButton("RUN", buttonStyle);

        // Arrange buttons in a 2x2 grid
        menu.add(fightButton).width(120).height(50).pad(5);
        menu.add(bagButton).width(120).height(50).pad(5).row();
        menu.add(pokemonButton).width(120).height(50).pad(5);
        menu.add(runButton).width(120).height(50).pad(5);

        // Add listeners to handle clicks
        fightButton.addListener(new ClickListener() { @Override public void clicked(InputEvent e, float x, float y) { if (!isAnimating) handleFightButton(); } });
        bagButton.addListener(new ClickListener() { @Override public void clicked(InputEvent e, float x, float y) { if (!isAnimating) handleBagButton(); } });
        pokemonButton.addListener(new ClickListener() { @Override public void clicked(InputEvent e, float x, float y) { if (!isAnimating) handlePokemonButton(); } });
        runButton.addListener(new ClickListener() { @Override public void clicked(InputEvent e, float x, float y) { if (!isAnimating) attemptRun(); } });

        menu.setVisible(false); // Start hidden
        return menu;
    }

    /**
     * Updates all dynamic UI elements to reflect the current state of the Pokémon in battle.
     * This includes names, levels, HP bars, and status icons.
     */
    private void updateUI() {
        if (playerPokemon == null || enemyPokemon == null) return;

        // Player UI Update
        playerInfoLabel.setText(String.format("%s Lv.%d", playerPokemon.getName(), playerPokemon.getLevel()));
        playerHPBar.setRange(0, playerPokemon.getStats().getHp());
        playerHPBar.setValue(playerPokemon.getCurrentHp());
        updateHPBarColor(playerHPBar, playerPokemon.getCurrentHp() / (float) playerPokemon.getStats().getHp(), skin);
        expBar.setRange(0, playerPokemon.getExperienceForNextLevel());
        expBar.setValue(playerPokemon.getCurrentExperience());
        updateStatusIcon(playerPokemon, playerStatusIcon);

        // Enemy UI Update
        enemyInfoLabel.setText(String.format("%s Lv.%d", enemyPokemon.getName(), enemyPokemon.getLevel()));
        enemyHPBar.setRange(0, enemyPokemon.getStats().getHp());
        enemyHPBar.setValue(enemyPokemon.getCurrentHp());
        updateHPBarColor(enemyHPBar, enemyPokemon.getCurrentHp() / (float) enemyPokemon.getStats().getHp(), skin);
        updateStatusIcon(enemyPokemon, enemyStatusIcon);
    }

    /**
     * Helper method to create a standardized info box for a Pokémon.
     */
    private Table createInfoBox(Pokemon pokemon, Label nameLabel, ProgressBar hpBar, Image statusIcon) {
        Table infoBox = new Table(skin);
        infoBox.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("battle_info_box")));

        // First row: Name and Level
        Table nameRow = new Table();
        nameRow.add(nameLabel).expandX().left();
        infoBox.add(nameRow).left().pad(8, 12, 0, 12).row();

        // Second row: Status icon and HP bar
        Table hpRow = new Table();
        hpRow.add(statusIcon).size(STATUS_ICON_WIDTH, STATUS_ICON_HEIGHT).left().padRight(5);
        hpRow.add(hpBar).width(HP_BAR_WIDTH).height(10).expandX().fillX();
        infoBox.add(hpRow).left().pad(0, 12, 8, 12);

        return infoBox;
    }

    /**
     * Sets the visibility and texture of a status icon based on the Pokémon's status.
     * @param pokemon The Pokémon to check.
     * @param statusIcon The Image widget to update.
     */
    private void updateStatusIcon(Pokemon pokemon, Image statusIcon) {
        Pokemon.Status status = pokemon.getStatus();
        if (status != null && status != Pokemon.Status.NONE) {
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

    /**
     * Tints the progress bar's fill portion (knobBefore) to green, yellow, or red
     * based on the Pokémon's current HP percentage.
     * @param bar The ProgressBar to update.
     * @param percentage The current HP percentage (0.0 to 1.0).
     * @param skin The skin to use for styling.
     */
    private static void updateHPBarColor(ProgressBar bar, float percentage, Skin skin) {
        ProgressBar.ProgressBarStyle style = new ProgressBar.ProgressBarStyle(bar.getStyle());
        Drawable fill = style.knobBefore;

        if (fill instanceof TextureRegionDrawable) {
            Color color;
            if (percentage > 0.5f) {
                color = new Color(0.18f, 0.82f, 0.32f, 1f); // Green
            } else if (percentage > 0.2f) {
                color = new Color(0.98f, 0.82f, 0.2f, 1f); // Yellow
            } else {
                color = new Color(0.95f, 0.3f, 0.2f, 1f); // Red
            }
            ((TextureRegionDrawable) fill).tint(color);
        }
        bar.setStyle(style);
    }

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

    private void initializeTextures() {
        platformTexture = TextureManager.battlebacks.findRegion("battle_platform");
        if (platformTexture == null) {
            throw new RuntimeException("Failed to load battle platform texture");
        }
    }

    private void initializeUIComponents() {
        // Create battle text label
        battleText = new Label("", skin);
        battleText.setWrap(true);
        battleText.setAlignment(Align.center);
        battleText.setTouchable(Touchable.disabled);
        float tableWidth = (getWidth() > 0 ? getWidth() : BATTLE_SCREEN_WIDTH);
        float tableHeight = (getHeight() > 0 ? getHeight() : BATTLE_SCREEN_HEIGHT);
        battleText.setSize(tableWidth, 30);
        battleText.setPosition(0, tableHeight - 40);

        // Create info labels
        playerInfoLabel = new Label("", skin);
        enemyInfoLabel = new Label("", skin);
        updateInfoLabels();

        // Create action menu
        actionMenu = new Table(skin);
        actionMenu.setBackground(createTranslucentBackground(0.5f));
        actionMenu.defaults().space(10);

        // Create buttons with enhanced styling
        fightButton = new TextButton("FIGHT", skin);
        bagButton = new TextButton("BAG", skin);
        pokemonButton = new TextButton("POKEMON", skin);
        runButton = new TextButton("RUN", skin);

        // Add listeners
        fightButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                handleFightButton();
            }
        });

        bagButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                handleBagButton();
            }
        });

        pokemonButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                handlePokemonButton();
            }
        });

        runButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                attemptRun();
            }
        });

        // Arrange buttons in a 2x2 grid
        actionMenu.add(fightButton).width(180).height(50);
        actionMenu.add(bagButton).width(180).height(50);
        actionMenu.row();
        actionMenu.add(pokemonButton).width(180).height(50);
        actionMenu.add(runButton).width(180).height(50);
        actionMenu.pack();

        updateActionMenuPosition();

        // Initially hide the menu
        actionMenu.setVisible(false);
        actionMenu.setTouchable(Touchable.disabled);

        addActor(battleText);
        addActor(actionMenu);
    }

    private void handleBagButton() {
        if (actionMenu != null) {
            actionMenu.setVisible(false);
            actionMenu.setTouchable(Touchable.disabled);
        }

        currentState = BattleState.BAG_SCREEN;

        BagScreen bagScreen = new BagScreen(skin,
            GameContext.get().getPlayer().getInventory(),
            (ItemData selectedItem) -> {
                if (selectedItem.getItemId().toLowerCase().contains("pokeball")) {
                    handlePokeBallUse(selectedItem);
                } else if (selectedItem.getItemId().toLowerCase().contains("potion")) {
                    handlePotionUse(selectedItem);
                } else {
                    queueMessage("This item cannot be used in battle!");
                    currentState = BattleState.PLAYER_CHOICE;
                    showActionMenu(true);
                }
            }
        );

        bagScreen.setOnClose(() -> {
            currentState = BattleState.PLAYER_CHOICE;
            showActionMenu(true);
        });

        stage.addActor(bagScreen);
        bagScreen.pack();
        float w = stage.getWidth();
        float h = stage.getHeight();
        bagScreen.setPosition((w - bagScreen.getWidth()) / 2f, (h - bagScreen.getHeight()) / 2f);
        bagScreen.toFront();
        bagScreen.setZIndex(stage.getActors().size - 1);
    }
    private void handlePokeBallUse(ItemData pokeball) {
        pokeball.setCount(pokeball.getCount() - 1);
        if (pokeball.getCount() <= 0) {
            GameContext.get().getPlayer().getInventory().removeItem(pokeball);
        }

        float hpRatio = enemyPokemon.getCurrentHp() / (float) enemyPokemon.getStats().getHp();
        float captureChance = MathUtils.clamp(1 - hpRatio, 0.1f, 0.9f);

        // Adjust capture chance based on ball type
        if (pokeball.getItemId().toLowerCase().contains("great")) {
            captureChance *= 1.5f;
        } else if (pokeball.getItemId().toLowerCase().contains("ultra")) {
            captureChance *= 2.0f;
        }

        attemptCapture((WildPokemon) enemyPokemon, captureChance);
    }

    private void handlePotionUse(ItemData potion) {
        // Show party screen for potion use
        showPartyScreenForItem(potion);
    }

    private void showPartyScreenForItem(ItemData item) {
        PokemonPartyWindow partyScreen = new PokemonPartyWindow(
            skin,
            GameContext.get().getPlayer().getPokemonParty(),
            false, // Not in battle mode for item use
            (selectedIndex) -> {
                Pokemon selectedPokemon = GameContext.get().getPlayer().getPokemonParty().getPokemon(selectedIndex);
                if (selectedPokemon != null) {
                    useItemOnPokemon(item, selectedPokemon);
                }
            },
            () -> {
                currentState = BattleState.PLAYER_CHOICE;
                showActionMenu(true);
            }
        );

        stage.addActor(partyScreen);
        partyScreen.show(stage);
    }

    private void useItemOnPokemon(ItemData item, Pokemon pokemon) {
        if (item.getItemId().toLowerCase().contains("potion")) {
            int healAmount = 20; // Basic potion
            if (item.getItemId().toLowerCase().contains("super")) {
                healAmount = 50;
            } else if (item.getItemId().toLowerCase().contains("hyper")) {
                healAmount = 200;
            }

            int oldHp = pokemon.getCurrentHp();
            pokemon.heal(Math.min(healAmount, pokemon.getStats().getHp() - oldHp));
            updateHPBars();

            queueMessage(pokemon.getName() + " recovered HP!");

            item.setCount(item.getCount() - 1);
            if (item.getCount() <= 0) {
                GameContext.get().getPlayer().getInventory().removeItem(item);
            }

            playerActionTaken = true;
            transitionToState(BattleState.ENEMY_TURN);
        }
    }

    private void handlePokemonButton() {
        if (actionMenu != null) {
            actionMenu.setVisible(false);
            actionMenu.setTouchable(Touchable.disabled);
        }
        currentState = BattleState.PLAYER_PARTY_SCREEN;
        showPartyScreen();
    }

    private void showPartyScreen() {
        PokemonPartyWindow partyScreen = new PokemonPartyWindow(
            skin,
            GameContext.get().getPlayer().getPokemonParty(),
            true, // battle mode
            (selectedIndex) -> {
                Pokemon selectedPokemon = GameContext.get().getPlayer().getPokemonParty().getPokemon(selectedIndex);
                if (selectedPokemon != null && selectedPokemon.getCurrentHp() > 0 && selectedPokemon != playerPokemon) {
                    handleSwitchPokemon(selectedIndex);
                }
            },
            () -> {
                currentState = BattleState.PLAYER_CHOICE;
                showActionMenu(true);
            }
        );

        stage.addActor(partyScreen);
        partyScreen.show(stage);
    }

    private void setupWeatherDisplay() {
        weatherDisplay = new Table();
        weatherDisplay.setBackground(createTranslucentBackground(0.3f));
        weatherLabel = new Label("", skin);
        weatherLabel.setFontScale(0.8f);
        weatherDisplay.add(weatherLabel).pad(5);
        weatherDisplay.setVisible(false);
        addActor(weatherDisplay);
    }

    private void updateWeatherDisplay() {
        // This would be connected to a weather system if implemented
        weatherDisplay.setVisible(false);
    }

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

        // Enemy section
        Table enemySection = new Table();
        enemySection.add(enemyInfoLabel).expandX().right().pad(10).row();

        Table enemyHPContainer = new Table();
        enemyHPContainer.add(enemyHPBar).width(HP_BAR_WIDTH).height(8);
        enemyHPContainer.add(enemyStatusIcon).size(STATUS_ICON_WIDTH, STATUS_ICON_HEIGHT).padLeft(5);
        enemySection.add(enemyHPContainer).expandX().right().pad(10).row();

        Stack enemyStack = new Stack();
        enemyStack.add(enemyPlatform);
        enemyStack.add(enemyPokemonImage);
        enemySection.add(enemyStack).expand().right().padRight(stage.getWidth() * 0.1f).row();

        // Player section
        Table playerSection = new Table();
        playerSection.add(playerInfoLabel).expandX().left().pad(10).row();

        Table playerHPContainer = new Table();
        playerHPContainer.add(playerHPBar).width(HP_BAR_WIDTH).height(8);
        playerHPContainer.add(playerStatusIcon).size(STATUS_ICON_WIDTH, STATUS_ICON_HEIGHT).padLeft(5);
        playerSection.add(playerHPContainer).expandX().left().pad(10).row();

        playerSection.add(expBar).width(HP_BAR_WIDTH).height(4).padLeft(10).row();

        Stack playerStack = new Stack();
        playerStack.add(playerPlatform);
        playerStack.add(playerPokemonImage);
        playerSection.add(playerStack).expand().left().padLeft(stage.getWidth() * 0.1f).row();

        // Control section
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
        playerPlatform.setScaling(Scaling.none);
        enemyPlatform.setScaling(Scaling.none);
    }

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

        float playerAspect = (float) playerTexture.getRegionWidth() / playerTexture.getRegionHeight();
        float enemyAspect = (float) enemyTexture.getRegionWidth() / enemyTexture.getRegionHeight();
        float baseSize = 85f;
        playerPokemonImage.setSize(baseSize * playerAspect, baseSize);
        enemyPokemonImage.setSize(baseSize * enemyAspect, baseSize);
        playerPokemonImage.setScaling(Scaling.none);
        enemyPokemonImage.setScaling(Scaling.none);

        // Create status icon images
        playerStatusIcon = new Image();
        enemyStatusIcon = new Image();
        playerStatusIcon.setVisible(false);
        enemyStatusIcon.setVisible(false);
        playerStatusIcon.setSize(STATUS_ICON_WIDTH, STATUS_ICON_HEIGHT);
        enemyStatusIcon.setSize(STATUS_ICON_WIDTH, STATUS_ICON_HEIGHT);
    }

    private void setupHPBars() {
        playerHPBar = new ProgressBar(0, playerPokemon.getStats().getHp(), 1, false,
            createHPBarStyle(playerPokemon.getCurrentHp() / (float) playerPokemon.getStats().getHp()));
        playerHPBar.setSize(HP_BAR_WIDTH, 8);
        playerHPBar.setValue(playerPokemon.getCurrentHp());

        enemyHPBar = new ProgressBar(0, enemyPokemon.getStats().getHp(), 1, false,
            createHPBarStyle(enemyPokemon.getCurrentHp() / (float) enemyPokemon.getStats().getHp()));
        enemyHPBar.setSize(HP_BAR_WIDTH, 8);
        enemyHPBar.setValue(enemyPokemon.getCurrentHp());

        expBar = new ProgressBar(0, playerPokemon.getExperienceForNextLevel(), 1, false,
            skin.get("default-horizontal", ProgressBar.ProgressBarStyle.class));
        expBar.setSize(HP_BAR_WIDTH, 6);
        expBar.setValue(playerPokemon.getCurrentExperience());
    }

    public void updateExpBar() {
        int nextLevelExp = playerPokemon.getExperienceForNextLevel();
        expBar.setRange(0, nextLevelExp);
        expBar.setValue(playerPokemon.getCurrentExperience());
    }

    private void startBattleAnimation() {
        GameLogger.info("Starting battle animation");
        isAnimating = true;
        currentState = BattleState.INTRO;
        setBattleInterfaceEnabled(false);

        SequenceAction introSequence = Actions.sequence(
            Actions.run(() -> queueMessage("Wild " + enemyPokemon.getName() + " appeared!")),
            Actions.delay(1.5f),
            Actions.run(() -> {
                GameLogger.info("Battle intro complete – player turn begins");
                isAnimating = false;
                currentState = BattleState.PLAYER_CHOICE;
                battleText.setText("What will " + playerPokemon.getName() + " do?");
                setBattleInterfaceEnabled(true);
            })
        );
        addAction(introSequence);
    }

    public void updateHPBars() {
        float playerHPPercent = playerPokemon.getCurrentHp() / (float) playerPokemon.getStats().getHp();
        animateHPBar(playerHPBar, playerPokemon.getCurrentHp(), playerHPPercent);

        float enemyHPPercent = enemyPokemon.getCurrentHp() / (float) enemyPokemon.getStats().getHp();
        animateHPBar(enemyHPBar, enemyPokemon.getCurrentHp(), enemyHPPercent);
    }

    private void animateHPBar(ProgressBar bar, float targetValue, float percentage) {
        bar.clearActions();
        bar.addAction(Actions.sequence(
            Actions.run(() -> updateHPBarColor(bar, percentage,skin)),
            Actions.delay(0.1f),
            Actions.run(() -> {
                float currentValue = bar.getValue();
                bar.addAction(Actions.parallel(
                    Actions.run(() -> {
                        // Animate value change
                        bar.setValue(targetValue);
                    }),
                    Actions.alpha(0.8f, 0.1f),
                    Actions.alpha(1f, 0.1f)
                ));
            })
        ));
    }

    private void updateInfoLabels() {
        String playerInfo = playerPokemon.getName() + " Lv." + playerPokemon.getLevel() +
            " HP: " + playerPokemon.getCurrentHp() + "/" + playerPokemon.getStats().getHp();
        playerInfoLabel.setText(playerInfo);

        String enemyInfo = enemyPokemon.getName() + " Lv." + enemyPokemon.getLevel();
        enemyInfoLabel.setText(enemyInfo);
    }


    @Override
    public void act(float delta) {
        super.act(delta);
        if (isAnimating) return;

        stateTimer += delta;

        switch (currentState) {
            case INTRO:
                break;
            case PLAYER_CHOICE:
                if (!actionMenu.isVisible()) {
                    showActionMenu(true);
                    battleText.setText("What will " + playerPokemon.getName() + " do?");
                }
                break;
            case PLAYER_MOVE_SELECT:
                break;
            case PLAYER_MOVE_EXECUTE:
                break;
            case ENEMY_TURN:
                if (stateTimer > 0.5f) {
                    executeEnemyMove();
                    stateTimer = 0f;
                }
                break;
            case PLAYER_SWITCHING:
                break;
            default:
                break;
        }

        updateHPBars();
        updateExpBar();
        updateStatusIcon(playerPokemon, playerStatusIcon);
        updateStatusIcon(enemyPokemon, enemyStatusIcon);
        updateInfoLabels();
        updateWeatherDisplay();
    }


    private void handleEnemyFaint() {
        enemyPokemon.setStatus(Pokemon.Status.FAINTED);
        queueMessage(enemyPokemon.getName() + " fainted!");
        GameLogger.info(enemyPokemon.getName() + " fainted.");

        if (callback != null) {
            callback.onStatusChange(enemyPokemon, Pokemon.Status.FAINTED);
        }

        // Faint animation
        enemyPokemonImage.addAction(Actions.sequence(
            Actions.parallel(
                Actions.fadeOut(1.0f), Actions.moveBy(0, -20, 1.0f)
            ),
            Actions.run(() -> {
                int expGained = calculateExperienceGain((WildPokemon) enemyPokemon);
                playerPokemon.addExperience(expGained);
                queueMessage(playerPokemon.getName() + " gained " + expGained + " EXP!");
                updateExpBar();
                // -- MODIFY THIS LINE --
                queueMessage("", 1.5f, () -> finishBattle(BattleOutcome.WIN));
            })
        ));
    }

    private void handlePlayerFaint() {
        playerPokemon.setStatus(Pokemon.Status.FAINTED);
        queueMessage(playerPokemon.getName() + " fainted!");
        GameLogger.info(playerPokemon.getName() + " fainted.");

        if (callback != null) {
            callback.onStatusChange(playerPokemon, Pokemon.Status.FAINTED);
        }

        // Faint animation
        playerPokemonImage.addAction(Actions.sequence(
            Actions.parallel(
                Actions.fadeOut(1.0f), Actions.moveBy(0, -20, 1.0f)
            ),
            Actions.run(() -> {
                if (hasAvailablePokemon()) {
                    transitionToState(BattleState.FORCED_SWITCH); showForcedSwitchPartyScreen();
                } else {
                    // -- MODIFY THIS LINE --
                    finishBattle(BattleOutcome.LOSS);
                }
            })
        ));
    }



    private void finishBattle(BattleOutcome outcome) {
        isAnimating = true;
        SequenceAction endSequence = Actions.sequence(
            // Add a small delay for final messages to be read
            Actions.delay(1.15f),
            Actions.run(() -> {
                if (callback != null) {
                    callback.onBattleEnd(outcome);
                }
            })
        );
        addAction(endSequence);
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
            if (move.getPp() <= 0) continue;

            float expectedDamage = computeExpectedDamage(move, enemyPokemon, playerPokemon);
            if (expectedDamage > highestExpectedDamage) {
                highestExpectedDamage = expectedDamage;
                selectedMove = move;
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
        queueMessage(attacker.getName() + " used Struggle!");
        finishMoveExecution(false);
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
        setBattleInterfaceEnabled(false);
        if (isPlayerMove) playerActionTaken = true;

        move.setPp(move.getPp() - 1);

        if (!attacker.canAttack()) {
            queueMessage("", 0.5f, () -> finishMoveExecution(isPlayerMove));
            return;
        }

        SequenceAction moveSequence = Actions.sequence();
        final float[] damageHolder = {0f}; // Use an array to make it effectively final for the lambda

        moveSequence.addAction(Actions.run(() -> queueMessage(attacker.getName() + " used " + move.getName() + "!")));
        moveSequence.addAction(Actions.delay(1.0f));

        moveSequence.addAction(Actions.run(() -> {
            if (MathUtils.random() * 100 >= move.getAccuracy()) {
                queueMessage("The attack missed!", 1.0f, () -> finishMoveExecution(isPlayerMove));
                moveSequence.getActions().clear();
            }
        }));

        moveSequence.addAction(Actions.run(() -> {
            damageHolder[0] = calculateDamage(move, attacker, defender);
            if (damageHolder[0] > 0) {
                applyDamage(defender, damageHolder[0]);
                float effectiveness = calculateTypeEffectiveness(move, defender);
                String effectivenessMessage = getEffectivenessMessage(effectiveness);
                if (!effectivenessMessage.isEmpty()) {
                    queueMessage(effectivenessMessage);
                }
            }
        }));
        moveSequence.addAction(Actions.delay(1.0f));

        moveSequence.addAction(Actions.run(() -> {
            if (checkFaintConditions()) {
                moveSequence.getActions().clear();
            }
        }));

        moveSequence.addAction(Actions.run(() -> {
            // Pass the calculated damage to applyMoveEffect
            applyMoveEffect(move.getEffect(), attacker, defender, damageHolder[0]);
        }));
        moveSequence.addAction(Actions.delay(1.0f));

        moveSequence.addAction(Actions.run(() -> finishMoveExecution(isPlayerMove)));

        addAction(moveSequence);
    }


    private void applyMoveEffect(Move.MoveEffect effect, Pokemon attacker, Pokemon target, float damageDealt) {
        if (effect == null) return;

        // Apply Status Effect
        Pokemon.Status statusToApply = effect.getStatusEffect();
        if (statusToApply != null && statusToApply != Pokemon.Status.NONE) {
            Pokemon.Status previousStatus = target.getStatus();
            target.setStatus(statusToApply);

            if (target.getStatus() != previousStatus) {
                queueMessage(target.getName() + " became " + statusToApply.name().toLowerCase() + "!");

                switch (statusToApply) {
                    case PARALYZED: case BURNED: case FROZEN: case POISONED: case BADLY_POISONED:
                        AudioManager.getInstance().playSound(AudioManager.SoundEffect.DAMAGE);
                        break;
                    case ASLEEP:
                        break;
                }

                GameLogger.info(target.getName() + " afflicted with " + statusToApply);
                updateStatusIcon(target, target == playerPokemon ? playerStatusIcon : enemyStatusIcon);

                if (callback != null) {
                    callback.onStatusChange(target, statusToApply);
                }
            } else {
                queueMessage("But it failed!");
            }
        }

        // Apply Stat Changes
        Map<String, Integer> statChanges = effect.getStatModifiers();
        if (statChanges != null && !statChanges.isEmpty()) {
            Pokemon effectTarget = target;
            for (Map.Entry<String, Integer> entry : statChanges.entrySet()) {
                String statName = entry.getKey();
                int change = entry.getValue();
                if (effectTarget.modifyStatStage(statName, change)) {
                    queueMessage(effectTarget.getName() + "'s " + formatStatName(statName) + (change > 0 ? " rose!" : " fell!"));
                    AudioManager.getInstance().playSound(AudioManager.SoundEffect.CURSOR_MOVE);
                } else {
                    queueMessage(effectTarget.getName() + "'s " + formatStatName(statName) + " won't go " + (change > 0 ? "higher!" : "lower!"));
                }
            }
        }

        // NEW: Handle DRAIN effect
        if (effect.getEffectType() != null && effect.getEffectType().equalsIgnoreCase("DRAIN")) {
            int healAmount = Math.max(1, (int)(damageDealt * 0.5f)); // Heal 50% of damage, at least 1 HP
            float oldHp = attacker.getCurrentHp();
            attacker.restoreHealth(healAmount); // Use restoreHealth to avoid side effects
            float newHp = attacker.getCurrentHp();

            if (newHp > oldHp) {
                queueMessage(attacker.getName() + "'s health was restored!");
                ProgressBar attackerBar = (attacker == playerPokemon) ? playerHPBar : enemyHPBar;
                animateHPChange(attackerBar, oldHp, newHp, attacker.getStats().getHp());
            }
        }
    }

    private String formatStatName(String statKey) {
        switch (statKey.toLowerCase()) {
            case "attack": return "Attack";
            case "defense": return "Defense";
            case "spatk": return "Special Attack";
            case "spdef": return "Special Defense";
            case "speed": return "Speed";
            case "accuracy": return "Accuracy";
            case "evasion": return "Evasion";
            default: return statKey;
        }
    }

    private void finishMoveExecution(boolean isPlayerMove) {
        isAnimating = false;

        if (checkFaintConditions()) {
            return;
        }

        if (!isPlayerMove) {
            applyEndOfTurnEffectsForTurn();
            if (checkFaintConditions()) {
                return;
            }
        }

        if (isPlayerMove) {
            transitionToState(BattleState.ENEMY_TURN);
        } else {
            if (callback != null) {
                callback.onTurnEnd(playerPokemon);
            }
            transitionToState(BattleState.PLAYER_CHOICE);
            turnCount++;
        }
    }

    private void handleFightButton() {
        if (currentState != BattleState.PLAYER_CHOICE) return;
        transitionToState(BattleState.PLAYER_MOVE_SELECT);
        showMoveSelection();
    }

    public void handleSwitchPokemon(int partyIndex) {
        if (currentState != BattleState.PLAYER_CHOICE && currentState != BattleState.FORCED_SWITCH) {
            GameLogger.error("Switch attempted in invalid state: " + currentState);
            return;
        }

        Pokemon newPokemon = GameContext.get().getPlayer().getPokemonParty().getPokemon(partyIndex);
        if (newPokemon == null || newPokemon == playerPokemon) {
            queueMessage("Cannot switch to this Pokemon!");
            return;
        }

        if (newPokemon.getCurrentHp() <= 0) {
            queueMessage(newPokemon.getName() + " is unable to battle!");
            if (currentState == BattleState.FORCED_SWITCH) {
                showForcedSwitchPartyScreen();
            }
            return;
        }

        isAnimating = true;
        setBattleInterfaceEnabled(false);
        transitionToState(BattleState.PLAYER_SWITCHING);

        SequenceAction switchSequence = Actions.sequence();

        switchSequence.addAction(Actions.run(() -> {
            queueMessage(playerPokemon.getName() + ", come back!");
            AudioManager.getInstance().playSound(AudioManager.SoundEffect.POKEMON_RETURN);
            playerPokemonImage.addAction(Actions.scaleTo(0.1f, 0.1f, 0.3f));
        }));
        switchSequence.addAction(Actions.delay(0.5f));

        switchSequence.addAction(Actions.run(() -> {
            Pokemon oldPokemon = playerPokemon;
            playerPokemon = newPokemon;
            updatePlayerPokemonDisplay();
            playerPokemonImage.setScale(0.1f);
            playerPokemonImage.getColor().a = 1f;

            if (callback != null) {
                callback.onTurnEnd(oldPokemon);
            }
        }));

        switchSequence.addAction(Actions.run(() -> {
            queueMessage("Go! " + newPokemon.getName() + "!");
            AudioManager.getInstance().playSound(AudioManager.SoundEffect.POKEMON_SENDOUT);
            playerPokemonImage.addAction(Actions.scaleTo(1.0f, 1.0f, 0.3f));
        }));
        switchSequence.addAction(Actions.delay(0.8f));

        switchSequence.addAction(Actions.run(() -> {
            isAnimating = false;
            playerActionTaken = true;
            applyEndOfTurnEffectsForTurn();
            if (checkFaintConditions()) return;
            transitionToState(BattleState.ENEMY_TURN);
        }));

        this.addAction(switchSequence);
    }

    private boolean checkFaintConditions() {
        if (enemyPokemon.getCurrentHp() <= 0) {
            handleEnemyFaint();
            return true;
        }
        if (playerPokemon.getCurrentHp() <= 0) {
            handlePlayerFaint();
            return true;
        }
        return false;
    }

    private float calculateDamage(Move move, Pokemon attacker, Pokemon defender) {
        if (move.getPower() <= 0) return 0;

        int level = attacker.getLevel();
        float attackStat = move.isSpecial() ? attacker.getStats().getSpecialAttack() : attacker.getStats().getAttack();
        float defenseStat = move.isSpecial() ? defender.getStats().getSpecialDefense() : defender.getStats().getDefense();

        // Apply Burn modifier to physical attack
        if (attacker.getStatus() == Pokemon.Status.BURNED && !move.isSpecial()) {
            attackStat *= 0.5f;
        }

        float baseDamage = (((2 * level) / 5f + 2) * move.getPower() * attackStat / defenseStat) / 50f + 2;

        // STAB (Same-Type Attack Bonus)
        float stab = (attacker.getPrimaryType() == move.getType() || attacker.getSecondaryType() == move.getType()) ? 1.5f : 1.0f;

        // Type Effectiveness
        float typeMultiplier = calculateTypeEffectiveness(move, defender);

        // Critical Hit
        boolean isCritical = MathUtils.random() < 0.0625f;
        if (isCritical) {
            queueMessage("A critical hit!");
            baseDamage *= 1.5f;
        }

        // Random variance
        float randomModifier = MathUtils.random(0.85f, 1.0f);

        return baseDamage * stab * typeMultiplier * randomModifier;
    }

    private float calculateTypeEffectiveness(Move move, Pokemon defender) {
        if (move.getType() == null || defender.getPrimaryType() == null) return 1.0f;

        float effectiveness = getTypeEffectiveness(move.getType(), defender.getPrimaryType());
        if (defender.getSecondaryType() != null) {
            effectiveness *= getTypeEffectiveness(move.getType(), defender.getSecondaryType());
        }
        return effectiveness;
    }

    private String getEffectivenessMessage(float multiplier) {
        if (multiplier >= 2.0f) {
            AudioManager.getInstance().playSound(AudioManager.SoundEffect.SUPER_EFFECTIVE);
            return "It's super effective!";
        } else if (multiplier > 0f && multiplier < 1.0f) {
            AudioManager.getInstance().playSound(AudioManager.SoundEffect.NOT_EFFECTIVE);
            return "It's not very effective...";
        } else if (multiplier == 0f) {
            return "It doesn't affect " + enemyPokemon.getName() + "...";
        }
        return "";
    }

    private void updatePlayerPokemonDisplay() {
        TextureRegion newTexture = playerPokemon.getBackSprite();
        if (newTexture != null) {
            playerPokemonImage.setDrawable(new TextureRegionDrawable(newTexture));
            float aspect = (float) newTexture.getRegionWidth() / newTexture.getRegionHeight();
            float baseSize = 85f;
            playerPokemonImage.setSize(baseSize * aspect, baseSize);
        }
        playerHPBar.setRange(0, playerPokemon.getStats().getHp());
        playerHPBar.setValue(playerPokemon.getCurrentHp());
        updateHPBarColor(playerHPBar, playerPokemon.getCurrentHp() / (float) playerPokemon.getStats().getHp(),skin);
        updateInfoLabels();
        expBar.setRange(0, playerPokemon.getExperienceForNextLevel());
        expBar.setValue(playerPokemon.getCurrentExperience());
        updateStatusIcon(playerPokemon, playerStatusIcon);
        invalidate();
        centerPlayerPokemon();
    }

    private void centerPlayerPokemon() {
        if (playerPlatform != null && playerPokemonImage != null) {
            float offsetX = (playerPlatform.getWidth() - playerPokemonImage.getWidth()) / 2f;
            float offsetY = (playerPlatform.getHeight() - playerPokemonImage.getHeight()) / 2f;
            playerPokemonImage.setPosition(offsetX, offsetY);
        }
    }

    private void updateActionMenuPosition() {
        actionMenu.pack();
        float tableWidth = getWidth() > 0 ? getWidth() : BATTLE_SCREEN_WIDTH;
        float tableHeight = getHeight() > 0 ? getHeight() : BATTLE_SCREEN_HEIGHT;
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

    public void applyDamage(Pokemon target, float damage) {
        Image targetSprite = (target == playerPokemon) ? playerPokemonImage : enemyPokemonImage;
        targetSprite.addAction(Actions.sequence(
            Actions.color(Color.RED, 0.1f),
            Actions.color(Color.WHITE, 0.1f)
        ));

        float oldHP = target.getCurrentHp();
        float newHP = Math.max(0, oldHP - damage);
        target.setCurrentHp(newHP);

        // Animate the HP bar
        ProgressBar targetBar = (target == playerPokemon) ? playerHPBar : enemyHPBar;
        animateHPChange(targetBar, oldHP, newHP, target.getStats().getHp());
    }

    private void animateHPChange(ProgressBar bar, float fromValue, float toValue, float maxValue) {
        float duration = 0.5f;
        bar.addAction(new Action() {
            float time = 0;
            @Override
            public boolean act(float delta) {
                time += delta;
                float progress = Math.min(1f, time / duration);
                float currentValue = MathUtils.lerp(fromValue, toValue, progress);
                bar.setValue(currentValue);
                updateHPBarColor(bar, currentValue / maxValue,skin);
                return progress >= 1f;
            }
        });
    }


    private void applyEndOfTurnEffectsForTurn() {
        Pokemon.Status oldPlayerStatus = playerPokemon.getStatus();
        Pokemon.Status oldEnemyStatus = enemyPokemon.getStatus();

        playerPokemon.applyEndOfTurnEffects();
        enemyPokemon.applyEndOfTurnEffects();

        // Handle Leech Seed
        if (leechSeedTargets.containsKey(playerPokemon)) {
            int damage = playerPokemon.getStats().getHp() / 8;
            playerPokemon.setCurrentHp(Math.max(0, playerPokemon.getCurrentHp() - damage));
            enemyPokemon.heal(damage);
            queueMessage(playerPokemon.getName() + "'s health is sapped by Leech Seed!");
            updateHPBars();
        }

        if (leechSeedTargets.containsKey(enemyPokemon)) {
            int damage = enemyPokemon.getStats().getHp() / 8;
            enemyPokemon.setCurrentHp(Math.max(0, enemyPokemon.getCurrentHp() - damage));
            playerPokemon.heal(damage);
            queueMessage(enemyPokemon.getName() + "'s health is sapped by Leech Seed!");
            updateHPBars();
        }

        if (oldPlayerStatus != playerPokemon.getStatus() && callback != null) {
            callback.onStatusChange(playerPokemon, playerPokemon.getStatus());
        }

        if (oldEnemyStatus != enemyPokemon.getStatus() && callback != null) {
            callback.onStatusChange(enemyPokemon, enemyPokemon.getStatus());
        }

        updateHPBars();
        updateInfoLabels();
    }

    private void showForcedSwitchPartyScreen() {
        PokemonPartyWindow partyScreen = new PokemonPartyWindow(
            skin,
            GameContext.get().getPlayer().getPokemonParty(),
            true,
            (selectedIndex) -> {
                Pokemon selectedPokemon = GameContext.get().getPlayer().getPokemonParty().getPokemon(selectedIndex);
                if (selectedPokemon != null && selectedPokemon.getCurrentHp() > 0) {
                    handleSwitchPokemon(selectedIndex);
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
        GameLogger.info("Transitioning from " + currentState + " to " + newState);
        currentState = newState;
        stateTimer = 0;
        playerActionTaken = false;

        switch (newState) {
            case PLAYER_CHOICE:
                showActionMenu(true);
                setBattleInterfaceEnabled(true);
                battleText.setText("What will " + playerPokemon.getName() + " do?");
                break;
            case PLAYER_MOVE_SELECT:
                setBattleInterfaceEnabled(false);
                break;
            case PLAYER_MOVE_EXECUTE:
            case ENEMY_TURN:
            case PLAYER_SWITCHING:
            case BATTLE_OVER_VICTORY:
            case BATTLE_OVER_DEFEAT:
                showActionMenu(false);
                setBattleInterfaceEnabled(false);
                break;
        }
    }

    private void showActionMenu(boolean show) {
        if (actionMenu != null) {
            actionMenu.setVisible(show);
            actionMenu.setTouchable(show ? Touchable.enabled : Touchable.disabled);
            actionMenu.toFront();
        }
    }

    private void showMoveSelection() {
        if (moveSelectionTable != null) {
            moveSelectionTable.remove();
        }

        moveSelectionTable = new Table(skin);
        moveSelectionTable.setBackground(createTranslucentBackground(0.8f));
        moveSelectionTable.defaults().pad(10).space(10);

        java.util.List<Move> moves = playerPokemon.getMoves();
        if (moves == null || moves.isEmpty()) {
            queueMessage(playerPokemon.getName() + " has no moves!");
            actionMenu.setVisible(true);
            actionMenu.setTouchable(Touchable.enabled);
            return;
        }

        Label titleLabel = new Label("Select a move:", skin);
        titleLabel.setFontScale(1.1f);
        moveSelectionTable.add(titleLabel).colspan(2).padBottom(10).row();

        // Display moves in a 2x2 grid with details
        int moveCount = 0;
        for (final Move move : moves) {
            Table moveButton = new Table();
            moveButton.setBackground(createTranslucentBackground(0.4f));
            moveButton.pad(8);

            Label nameLabel = new Label(move.getName(), skin);
            nameLabel.setColor(TYPE_COLORS.get(move.getType()));
            moveButton.add(nameLabel).expandX().left().row();

            Table detailsTable = new Table();
            Label ppLabel = new Label("PP: " + move.getPp() + "/" + move.getMaxPp(), skin);
            ppLabel.setFontScale(0.8f);
            Label powerLabel = new Label("Power: " + (move.getPower() > 0 ? move.getPower() : "-"), skin);
            powerLabel.setFontScale(0.8f);
            detailsTable.add(ppLabel).padRight(10);
            detailsTable.add(powerLabel);
            moveButton.add(detailsTable).expandX().left();

            moveButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (move.getPp() <= 0) {
                        queueMessage("No PP left for this move!");
                        return;
                    }
                    moveSelectionActive = false;
                    moveSelectionTable.remove();
                    executeMove(move, playerPokemon, enemyPokemon, true);
                }
            });

            moveSelectionTable.add(moveButton).width(200).height(60);

            moveCount++;
            if (moveCount % 2 == 0) {
                moveSelectionTable.row();
            }
        }

        if (moveCount % 2 != 0) {
            moveSelectionTable.add(); // Empty cell for alignment
            moveSelectionTable.row();
        }

        TextButton cancelButton = new TextButton("Cancel", skin);
        cancelButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                moveSelectionActive = false;
                moveSelectionTable.remove();
                currentState = BattleState.PLAYER_CHOICE;
                actionMenu.setVisible(true);
                actionMenu.setTouchable(Touchable.enabled);
                actionMenu.toFront();
            }
        });
        moveSelectionTable.add(cancelButton).colspan(2).width(150).height(40).padTop(10);

        moveSelectionTable.pack();
        float tableWidth = (getWidth() > 0 ? getWidth() : BATTLE_SCREEN_WIDTH);
        float tableHeight = (getHeight() > 0 ? getHeight() : BATTLE_SCREEN_HEIGHT);
        float posX = (tableWidth - moveSelectionTable.getWidth()) / 2f;
        float posY = (tableHeight - moveSelectionTable.getHeight()) / 2f;
        moveSelectionTable.setPosition(posX, posY);
        moveSelectionTable.toFront();
        addActor(moveSelectionTable);

        moveSelectionActive = true;
    }

    private void attemptRun() {
        if (isAnimating) return;

        float runChance = 0.8f;
        if (MathUtils.random() < runChance) {
            queueMessage("Got away safely!");
            SequenceAction escapeSequence = Actions.sequence(
                Actions.delay(1.0f),
                Actions.run(() -> {
                    if (callback != null) {
                        callback.onBattleEnd(BattleOutcome.ESCAPE);
                    }
                })
            );
            addAction(escapeSequence);
            isAnimating = true;
        } else {
            queueMessage("Can't escape!");
            transitionToState(BattleState.ENEMY_TURN);
        }
    }
// In src/main/java/io/github/pokemeetup/screens/otherui/BattleTable.java

    public void attemptCapture(WildPokemon wildPokemon, float captureChance) {
        if (currentState == BattleState.CATCHING) return;

        currentState = BattleState.CATCHING;
        setBattleInterfaceEnabled(false);
        queueMessage("Throwing Pokéball...");

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
                    queueMessage("Gotcha! " + wildPokemon.getName() + " was caught!");
                    GameContext.get().getPlayer().getPokemonParty().addPokemon(wildPokemon);
                    if (GameContext.get().getGameScreen() != null) {
                        GameContext.get().getGameScreen().updatePartyDisplay();
                    }
                    addAction(Actions.sequence(
                        Actions.delay(1.0f),
                        Actions.run(() -> {
                            if (callback != null) {
                                // *** FIX IS HERE ***
                                // A successful capture is a WIN
                                callback.onBattleEnd(BattleOutcome.WIN);
                            }
                        })
                    ));
                } else {
                    queueMessage(wildPokemon.getName() + " broke free!");
                    transitionToState(BattleState.ENEMY_TURN);
                }
            },
            enemyPokemonImage);
        addActor(captureAnimation);
    }

    public enum BattleOutcome {
        WIN, LOSS, ESCAPE
    }
    public void setCallback(BattleCallback callback) {
        this.callback = callback;
    }

    private void cleanup() {
        if (playerPokemonImage != null) playerPokemonImage.clear();
        if (enemyPokemonImage != null) enemyPokemonImage.clear();
        clearActions();
        messageQueue.clear();
        leechSeedTargets.clear();
    }

    private int calculateExperienceGain(WildPokemon defeatedPokemon) {
        float a = 1.0f;  // Wild battle
        float t = 1.0f;  // No modifiers
        int b = defeatedPokemon.getBaseExperience();
        int L = defeatedPokemon.getLevel();

        float levelRatio = Math.max(0.5f, Math.min(1.5f, (float)L / playerPokemon.getLevel()));

        int expGained = Math.round((a * t * b * L * levelRatio) / 7);
        GameLogger.info(String.format("Experience gained: %d (Base: %d, Level: %d)",
            expGained, b, L));

        return Math.max(1, expGained);
    }

    public Pokemon getPlayerPokemon() {
        return playerPokemon;
    }

    public interface BattleCallback {
        void onBattleEnd(BattleOutcome outcome);
        void onTurnEnd(Pokemon activePokemon);
        void onStatusChange(Pokemon pokemon, Pokemon.Status newStatus);
        void onMoveUsed(Pokemon user, Move move, Pokemon target);
    }
}
