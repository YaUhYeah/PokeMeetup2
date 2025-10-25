package io.github.pokemeetup.screens.otherui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
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
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.PokemonCaptureAnimation;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.pokemon.attacks.Move;
import io.github.pokemeetup.screens.GameScreen;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.overworld.WeatherSystem;
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
    private Image playerPokemonImage, enemyPokemonImage;
    private ProgressBar playerHPBar, enemyHPBar, expBar;
    private Label playerInfoLabel, enemyInfoLabel, battleText;
    private Image playerStatusIcon, enemyStatusIcon;
    private Table actionMenu, moveSelectionTable;
    private TextButton fightButton, bagButton, pokemonButton, runButton;
    private BattleState currentState;
    private BattleCallback callback;
    private boolean isAnimating = false;
    private boolean playerActionTaken = false;
    private final Queue<BattleMessage> messageQueue = new LinkedList<>();
    private boolean processingMessage = false;

    private static final float POKEBALL_THROW_DURATION = 1.2f;
    private static final float POKEMON_REVEAL_DURATION = 0.8f;
    private static final float MESSAGE_DISPLAY_DURATION = 2.0f;
    private Image playerPokeballImage, enemyPokeballImage;
    private boolean introAnimationComplete = false;

    public static final int STATUS_ICON_WIDTH = 44;
    public static final int STATUS_ICON_HEIGHT = 16;
    private static final float BATTLE_SCREEN_WIDTH = 800f;
    private static final float BATTLE_SCREEN_HEIGHT = 480f;
    private static final float HP_BAR_WIDTH = 100f;

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

    private TextureRegion platformTexture;
    private Image playerPlatform, enemyPlatform;
    private float stateTimer = 0;
    private Table weatherDisplay;
    private Label weatherLabel;
    private WeatherSystem.WeatherType battleWeather;
    private boolean moveSelectionActive = false;
    private int turnCount = 0;
    // MODIFICATION: Changed to Map<Pokemon, Pokemon> for better tracking
    private Map<Pokemon, Pokemon> leechSeedTargets = new HashMap<>();

    // Path: src/main/java/io/github/pokemeetup/screens/otherui/BattleTable.java

    // In the BattleState enum, add AWAITING_ITEM_TARGET
    private enum BattleState {
        BATTLE_INTRO,
        ENEMY_ENTRANCE,
        PLAYER_ENTRANCE,
        INTRO_COMPLETE,
        PLAYER_CHOICE,
        PLAYER_MOVE_SELECT,
        AWAITING_ITEM_TARGET, // <--- ADD THIS NEW STATE
        PLAYER_ACTION_EXECUTE, // Let's rename this for clarity
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

    private ItemData itemToUse;

    private static class BattleMessage {
        String text;
        float duration;
        Runnable onComplete;

        BattleMessage(String text, float duration, Runnable onComplete) {
            this.text = text;
            this.duration = duration;
            this.onComplete = onComplete;
        }
    }

    // Constructor with weather parameter
    public BattleTable(Stage stage, Skin skin, Pokemon playerPokemon, Pokemon enemyPokemon, WeatherSystem.WeatherType weather) {
        super();
        this.stage = stage;
        this.currentState = BattleState.BATTLE_INTRO;
        this.skin = skin;
        this.playerPokemon = playerPokemon;
        this.enemyPokemon = enemyPokemon;
        this.battleWeather = weather != null ? weather : WeatherSystem.WeatherType.CLEAR;
        setFillParent(true);
        setTouchable(Touchable.enabled);
        setZIndex(100);
        stage.setViewport(new FitViewport(BATTLE_SCREEN_WIDTH, BATTLE_SCREEN_HEIGHT));
        stage.getViewport().update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);

        ensureHPBarStyles();

        try {
            initializeTextures();
            initializeUIComponents();
            initializePlatforms();
            initializePokemonSprites();
            initializePokeballSprites();
            setupHPBars();
            setupWeatherDisplay();
            setupContainer();
            startEnhancedBattleIntro();
        } catch (Exception e) {
            GameLogger.error("Error initializing battle table: " + e.getMessage());
        }
    }

    // Constructor without weather parameter (defaults to CLEAR)
    public BattleTable(Stage stage, Skin skin, Pokemon playerPokemon, Pokemon enemyPokemon) {
        this(stage, skin, playerPokemon, enemyPokemon, WeatherSystem.WeatherType.CLEAR);
    }// In class BattleTable

    private void handlePokemonButton() {
        if (actionMenu != null) {
            actionMenu.setVisible(false);
        }
        transitionToState(BattleState.PLAYER_PARTY_SCREEN); // Use a dedicated state for switching
        showPartyScreenForSwitch();
    }

    private void showPartyScreenForSwitch() {
        PokemonPartyWindow partyScreen = new PokemonPartyWindow(
            skin,
            GameContext.get().getPlayer().getPokemonParty(),
            true,
            this::startSwitchPokemonSequence,
            () -> transitionToState(BattleState.PLAYER_CHOICE),
            true // <-- FIX: disable active Pokémon for switching
        );
        partyScreen.show(stage);
    }
    public void handleSwitchPokemon(int partyIndex) {
        // This now uses the same, correct logic as a voluntary switch.
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
            // MODIFICATION: Clear Leech Seed when switching
            leechSeedTargets.remove(oldPokemon);
            leechSeedTargets.values().removeIf(healer -> healer == oldPokemon);

            playerPokemon = newPokemon;
            GameContext.get().getPlayer().getPokemonParty().setActivePokemon(partyIndex); // Correctly set active Pokemon
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
            AudioManager.getInstance().playPokemonCry(newPokemon.getName());
            playerPokemonImage.addAction(Actions.scaleTo(1.0f, 1.0f, 0.3f));
        }));
        switchSequence.addAction(Actions.delay(0.8f));

        switchSequence.addAction(Actions.run(this::finishPlayerSwitchAndProceed));

        this.addAction(switchSequence);
    }

    public void startSwitchPokemonSequence(int partyIndex) {
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
            GameContext.get().getPlayer().getPokemonParty().setActivePokemon(partyIndex); // Set the active pokemon in the party
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
            AudioManager.getInstance().playPokemonCry(newPokemon.getName());
            playerPokemonImage.addAction(Actions.scaleTo(1.0f, 1.0f, 0.3f));
        }));
        switchSequence.addAction(Actions.delay(0.8f));

        // MODIFIED: Use a dedicated method to finish the sequence, ensuring state is reset.
        switchSequence.addAction(Actions.run(this::finishPlayerSwitchAndProceed));

        this.addAction(switchSequence);
    }

    // NEW: This method cleanly ends the switching state.
    private void finishPlayerSwitchAndProceed() {
        isAnimating = false;
        playerActionTaken = true; // Switching counts as a turn

        // Don't apply end-of-turn effects here, they happen after both players move.
        // The enemy gets to attack right after the switch.

        if (checkFaintConditions()) return; // Should not happen, but a good safe check

        transitionToState(BattleState.ENEMY_TURN);
    }

    private static void initializeTypeEffectiveness() {
        for (Pokemon.PokemonType type : Pokemon.PokemonType.values()) {
            typeEffectiveness.put(type, new ObjectMap<>());
            for (Pokemon.PokemonType defType : Pokemon.PokemonType.values()) {
                typeEffectiveness.get(type).put(defType, 1.0f);
            }
        }
        initTypeEffectiveness(Pokemon.PokemonType.NORMAL, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.ROCK, 0.5f);
            put(Pokemon.PokemonType.GHOST, 0.0f);
            put(Pokemon.PokemonType.STEEL, 0.5f);
        }});
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
        initTypeEffectiveness(Pokemon.PokemonType.WATER, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.FIRE, 2.0f);
            put(Pokemon.PokemonType.WATER, 0.5f);
            put(Pokemon.PokemonType.GRASS, 0.5f);
            put(Pokemon.PokemonType.GROUND, 2.0f);
            put(Pokemon.PokemonType.ROCK, 2.0f);
            put(Pokemon.PokemonType.DRAGON, 0.5f);
        }});
        initTypeEffectiveness(Pokemon.PokemonType.ELECTRIC, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.WATER, 2.0f);
            put(Pokemon.PokemonType.ELECTRIC, 0.5f);
            put(Pokemon.PokemonType.GRASS, 0.5f);
            put(Pokemon.PokemonType.GROUND, 0.0f);
            put(Pokemon.PokemonType.FLYING, 2.0f);
            put(Pokemon.PokemonType.DRAGON, 0.5f);
        }});
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
        initTypeEffectiveness(Pokemon.PokemonType.FLYING, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.ELECTRIC, 0.5f);
            put(Pokemon.PokemonType.GRASS, 2.0f);
            put(Pokemon.PokemonType.FIGHTING, 2.0f);
            put(Pokemon.PokemonType.BUG, 2.0f);
            put(Pokemon.PokemonType.ROCK, 0.5f);
            put(Pokemon.PokemonType.STEEL, 0.5f);
        }});
        initTypeEffectiveness(Pokemon.PokemonType.PSYCHIC, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.FIGHTING, 2.0f);
            put(Pokemon.PokemonType.POISON, 2.0f);
            put(Pokemon.PokemonType.PSYCHIC, 0.5f);
            put(Pokemon.PokemonType.DARK, 0.0f);
            put(Pokemon.PokemonType.STEEL, 0.5f);
        }});
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
        initTypeEffectiveness(Pokemon.PokemonType.ROCK, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.FIRE, 2.0f);
            put(Pokemon.PokemonType.ICE, 2.0f);
            put(Pokemon.PokemonType.FIGHTING, 0.5f);
            put(Pokemon.PokemonType.GROUND, 0.5f);
            put(Pokemon.PokemonType.FLYING, 2.0f);
            put(Pokemon.PokemonType.BUG, 2.0f);
            put(Pokemon.PokemonType.STEEL, 0.5f);
        }});
        initTypeEffectiveness(Pokemon.PokemonType.GHOST, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.NORMAL, 0.0f);
            put(Pokemon.PokemonType.PSYCHIC, 2.0f);
            put(Pokemon.PokemonType.GHOST, 2.0f);
            put(Pokemon.PokemonType.DARK, 0.5f);
        }});
        initTypeEffectiveness(Pokemon.PokemonType.DRAGON, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.DRAGON, 2.0f);
            put(Pokemon.PokemonType.STEEL, 0.5f);
            put(Pokemon.PokemonType.FAIRY, 0.0f);
        }});
        initTypeEffectiveness(Pokemon.PokemonType.DARK, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.FIGHTING, 0.5f);
            put(Pokemon.PokemonType.PSYCHIC, 2.0f);
            put(Pokemon.PokemonType.GHOST, 2.0f);
            put(Pokemon.PokemonType.DARK, 0.5f);
            put(Pokemon.PokemonType.FAIRY, 0.5f);
        }});
        initTypeEffectiveness(Pokemon.PokemonType.STEEL, new ObjectMap<Pokemon.PokemonType, Float>() {{
            put(Pokemon.PokemonType.FIRE, 0.5f);
            put(Pokemon.PokemonType.WATER, 0.5f);
            put(Pokemon.PokemonType.ELECTRIC, 0.5f);
            put(Pokemon.PokemonType.ICE, 2.0f);
            put(Pokemon.PokemonType.ROCK, 2.0f);
            put(Pokemon.PokemonType.STEEL, 0.5f);
            put(Pokemon.PokemonType.FAIRY, 2.0f);
        }});
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


    /**
     * Creates a ProgressBar style with a fill color based on HP percentage.
     * @param percentage The current HP percentage (0.0 to 1.0).
     * @return A new ProgressBarStyle.
     */
    private static ProgressBar.ProgressBarStyle createHPBarStyle(float percentage) {
        ProgressBar.ProgressBarStyle style = new ProgressBar.ProgressBarStyle();

        // Background
        Pixmap bgPixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        bgPixmap.setColor(0.2f, 0.2f, 0.2f, 0.8f); // Dark grey background
        bgPixmap.fill();
        style.background = new TextureRegionDrawable(new TextureRegion(new Texture(bgPixmap)));
        bgPixmap.dispose();

        // Foreground (knob)
        Pixmap fgPixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        Color barColor;
        if (percentage > 0.5f) {
            barColor = new Color(0.18f, 0.82f, 0.32f, 1f); // Green
        } else if (percentage > 0.2f) {
            barColor = new Color(0.98f, 0.82f, 0.2f, 1f); // Yellow
        } else {
            barColor = new Color(0.95f, 0.3f, 0.2f, 1f); // Red
        }
        fgPixmap.setColor(barColor);
        fgPixmap.fill();
        TextureRegionDrawable knobDrawable = new TextureRegionDrawable(new TextureRegion(new Texture(fgPixmap)));
        fgPixmap.dispose();

        style.knob = knobDrawable;
        style.knobBefore = knobDrawable;

        return style;
    }

    private void initializePokeballSprites() {
        // Get the first frame from capsuleThrow atlas - this is the closed pokeball
        TextureRegion pokeballRegion = TextureManager.capsuleThrow.findRegion("ball_POKEBALL");
        if (pokeballRegion != null) {
            // Split the 256x64 region into 8 frames of 32x64 each
            TextureRegion firstFrame = new TextureRegion(pokeballRegion, 0, 0, 32, 64);

            playerPokeballImage = new Image(firstFrame);
            enemyPokeballImage = new Image(firstFrame);
        } else {
            // Fallback - create a simple colored circle
            GameLogger.error("ball_POKEBALL not found in capsuleThrow atlas, using fallback");
            Pixmap pixmap = new Pixmap(32, 64, Pixmap.Format.RGBA8888);
            pixmap.setColor(1, 1, 1, 1);
            pixmap.fillCircle(16, 32, 15);
            pixmap.setColor(1, 0, 0, 1);
            pixmap.fillCircle(16, 32, 12);
            TextureRegion fallbackTexture = new TextureRegion(new Texture(pixmap));
            pixmap.dispose();

            playerPokeballImage = new Image(fallbackTexture);
            enemyPokeballImage = new Image(fallbackTexture);
        }

        playerPokeballImage.setSize(32, 64);
        enemyPokeballImage.setSize(32, 64);
        playerPokeballImage.setVisible(false);
        enemyPokeballImage.setVisible(false);
    }

    private void startEnhancedBattleIntro() {
        GameLogger.info("Starting enhanced Fire Red style battle intro");
        isAnimating = true;
        currentState = BattleState.BATTLE_INTRO;
        setBattleInterfaceEnabled(false);

        // Hide Pokemon initially
        if (playerPokemonImage != null) {
            playerPokemonImage.setVisible(false);
        }
        if (enemyPokemonImage != null) {
            enemyPokemonImage.setVisible(false);
        }

        SequenceAction introSequence = Actions.sequence(
            // Initial battle message
            Actions.run(() -> {
                queueMessage("A wild " + enemyPokemon.getName() + " appeared!", MESSAGE_DISPLAY_DURATION);
            }),
            Actions.delay(MESSAGE_DISPLAY_DURATION),

            // Enemy Pokemon entrance
            Actions.run(() -> startEnemyEntrance()),
            Actions.delay(POKEBALL_THROW_DURATION + POKEMON_REVEAL_DURATION),

            // Player Pokemon entrance
            Actions.run(() -> startPlayerEntrance()),
            Actions.delay(POKEBALL_THROW_DURATION + POKEMON_REVEAL_DURATION),

            // Complete intro
            Actions.run(() -> completeIntro())
        );

        addAction(introSequence);
    }

    private void startEnemyEntrance() {
        GameLogger.info("Starting enemy Pokemon entrance");
        currentState = BattleState.ENEMY_ENTRANCE;

        // Enemy Pokemon just appears (wild Pokemon don't come from pokeballs)
        if (enemyPokemonImage != null) {
            enemyPokemonImage.setVisible(true);
            enemyPokemonImage.setScale(0.1f);
            enemyPokemonImage.addAction(Actions.sequence(
                Actions.scaleTo(1.2f, 1.2f, POKEMON_REVEAL_DURATION * 0.6f, Interpolation.bounceOut),
                Actions.scaleTo(1.0f, 1.0f, POKEMON_REVEAL_DURATION * 0.4f, Interpolation.sineOut),
                Actions.run(() -> {
                    // Play enemy Pokemon cry
                    AudioManager.getInstance().playPokemonCry(enemyPokemon.getName());
                })
            ));
        }
    }

    private void startPlayerEntrance() {
        GameLogger.info("Starting player Pokemon entrance");
        currentState = BattleState.PLAYER_ENTRANCE;

        queueMessage("Go! " + playerPokemon.getName() + "!", 1.5f);
        AudioManager.getInstance().playSound(AudioManager.SoundEffect.POKEMON_SENDOUT);

        // Calculate pokeball throw trajectory
        Vector2 throwStart = new Vector2(50, 100); // Bottom left area
        Vector2 throwTarget = new Vector2(200, 150); // Player Pokemon position

        // Setup pokeball for throw
        playerPokeballImage.setPosition(throwStart.x, throwStart.y);
        playerPokeballImage.setVisible(true);
        playerPokeballImage.setScale(1.0f);
        addActor(playerPokeballImage);

        // Animate pokeball throw with arc
        playerPokeballImage.addAction(Actions.sequence(
            Actions.parallel(
                // Move in arc
                createArcMovement(throwStart, throwTarget, POKEBALL_THROW_DURATION)
            ),
            Actions.run(() -> {
                // Pokeball opens and reveals Pokemon
                playerPokeballImage.setVisible(false);
                revealPlayerPokemon();
            })
        ));
    }

    private Action createArcMovement(Vector2 start, Vector2 end, float duration) {
        return new Action() {
            private float time = 0;
            private final float arcHeight = 80f;

            @Override
            public boolean act(float delta) {
                time += delta;
                float progress = Math.min(time / duration, 1f);

                float x = MathUtils.lerp(start.x, end.x, progress);
                float baseY = MathUtils.lerp(start.y, end.y, progress);
                float y = baseY + arcHeight * MathUtils.sin(MathUtils.PI * progress);

                getActor().setPosition(x, y);
                return progress >= 1f;
            }
        };
    }

    private void revealPlayerPokemon() {
        if (playerPokemonImage != null) {
            playerPokemonImage.setVisible(true);
            playerPokemonImage.setScale(0.1f);
            playerPokemonImage.setColor(1, 1, 1, 0.7f); // Slightly transparent initially

            // Create pokeball opening effect
            createPokeballOpenEffect(playerPokemonImage.getX(), playerPokemonImage.getY());

            playerPokemonImage.addAction(Actions.sequence(
                Actions.parallel(
                    Actions.scaleTo(1.2f, 1.2f, POKEMON_REVEAL_DURATION * 0.6f, Interpolation.bounceOut),
                    Actions.fadeIn(POKEMON_REVEAL_DURATION * 0.3f)
                ),
                Actions.scaleTo(1.0f, 1.0f, POKEMON_REVEAL_DURATION * 0.4f, Interpolation.sineOut),
                Actions.run(() -> {
                    // Play player Pokemon cry
                    AudioManager.getInstance().playPokemonCry(playerPokemon.getName());
                })
            ));
        }
    }

    private void createPokeballOpenEffect(float x, float y) {
        // Create simple particle effect for pokeball opening
        for (int i = 0; i < 6; i++) {
            Actor particle = new Actor() {
                @Override
                public void draw(Batch batch, float parentAlpha) {
                    // Draw a small white circle
                    batch.setColor(1, 1, 1, getColor().a * parentAlpha);
                    // Simple particle effect - would be better with actual textures
                    batch.setColor(Color.WHITE);
                }
            };

            particle.setSize(4, 4);
            particle.setPosition(x, y);

            float angle = i * 60f; // Spread particles in circle
            float distance = 30f;
            float targetX = x + MathUtils.cosDeg(angle) * distance;
            float targetY = y + MathUtils.sinDeg(angle) * distance;

            particle.addAction(Actions.sequence(
                Actions.parallel(
                    Actions.moveTo(targetX, targetY, 0.5f, Interpolation.sineOut),
                    Actions.fadeOut(0.5f)
                ),
                Actions.removeActor()
            ));

            addActor(particle);
        }
    }

    private void completeIntro() {
        GameLogger.info("Battle intro complete - starting player turn");
        isAnimating = false;
        introAnimationComplete = true;
        currentState = BattleState.INTRO_COMPLETE;

        // Remove pokeball sprites
        if (playerPokeballImage != null) {
            playerPokeballImage.remove();
        }
        if (enemyPokeballImage != null) {
            enemyPokeballImage.remove();
        }

        // Transition to player choice
        transitionToState(BattleState.PLAYER_CHOICE);
        battleText.setText("What will " + playerPokemon.getName() + " do?");
        setBattleInterfaceEnabled(true);
    }

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

    private Table createInfoBox(Pokemon pokemon, Label nameLabel, ProgressBar hpBar, Image statusIcon) {
        Table infoBox = new Table(skin);
        infoBox.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("battle_info_box")));
        Table nameRow = new Table();
        nameRow.add(nameLabel).expandX().left();
        infoBox.add(nameRow).left().pad(8, 12, 0, 12).row();
        Table hpRow = new Table();
        hpRow.add(statusIcon).size(STATUS_ICON_WIDTH, STATUS_ICON_HEIGHT).left().padRight(5);
        hpRow.add(hpBar).width(HP_BAR_WIDTH).height(10).expandX().fillX();
        infoBox.add(hpRow).left().pad(0, 12, 8, 12);

        return infoBox;
    }

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
    private static void updateHPBarColor(ProgressBar bar, float percentage, Skin skin) {
        // Use the same logic as the initial creation
        bar.setStyle(createHPBarStyle(percentage));
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
        battleText = new Label("", skin);
        battleText.setWrap(true);
        battleText.setAlignment(Align.center);
        battleText.setTouchable(Touchable.disabled);
        float tableWidth = (getWidth() > 0 ? getWidth() : BATTLE_SCREEN_WIDTH);
        float tableHeight = (getHeight() > 0 ? getHeight() : BATTLE_SCREEN_HEIGHT);
        battleText.setSize(tableWidth, 30);
        battleText.setPosition(0, tableHeight - 40);
        playerInfoLabel = new Label("", skin);
        enemyInfoLabel = new Label("", skin);
        updateInfoLabels();
        actionMenu = new Table(skin);
        actionMenu.setBackground(createTranslucentBackground(0.5f));
        actionMenu.defaults().space(10);
        fightButton = new TextButton("FIGHT", skin);
        bagButton = new TextButton("BAG", skin);
        pokemonButton = new TextButton("POKEMON", skin);
        runButton = new TextButton("RUN", skin);
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
        actionMenu.add(fightButton).width(180).height(50);
        actionMenu.add(bagButton).width(180).height(50);
        actionMenu.row();
        actionMenu.add(pokemonButton).width(180).height(50);
        actionMenu.add(runButton).width(180).height(50);
        actionMenu.pack();

        updateActionMenuPosition();
        actionMenu.setVisible(false);
        actionMenu.setTouchable(Touchable.disabled);

        addActor(battleText);
        addActor(actionMenu);
    }

    private void handleBagButton() {
        if (actionMenu != null) {
            actionMenu.setVisible(false);
        }
        transitionToState(BattleState.BAG_SCREEN);

        BagScreen bagScreen = new BagScreen(skin,
            GameContext.get().getPlayer().getInventory(),
            (ItemData selectedItem) -> {
                // This is the listener for when an item is chosen from the bag.
                String itemId = selectedItem.getItemId().toLowerCase();

                // *** FIX: Added specific logic for Pokéballs ***
                if (itemId.contains("ball")) {
                    // If it's a ball, we don't need to select a Pokémon. We use it immediately.
                    handlePokeBallUse(selectedItem);
                } else {
                    // For other items (Potions, Elixirs), store the item and open the party screen.
                    this.itemToUse = selectedItem;
                    showPartyScreenForItemUse();
                }
            }
        );

        bagScreen.setOnClose(() -> {
            // If the player closes the bag without choosing, return to the main menu.
            if (currentState == BattleState.BAG_SCREEN) {
                transitionToState(BattleState.PLAYER_CHOICE);
            }
        });

        stage.addActor(bagScreen);
    }

    // Replace the handlePokeBallUse() method to use queueMessage correctly
    private void handlePokeBallUse(ItemData pokeball) {
        // Consume the item first
        GameContext.get().getPlayer().getInventory().removeItem(pokeball.getItemId(), 1);

        // Calculate capture chance
        float hpRatio = enemyPokemon.getCurrentHp() / (float) enemyPokemon.getStats().getHp();
        float captureChance = MathUtils.clamp(1 - hpRatio, 0.1f, 0.9f);
        if (pokeball.getItemId().toLowerCase().contains("great")) {
            captureChance *= 1.5f;
        } else if (pokeball.getItemId().toLowerCase().contains("ultra")) {
            captureChance *= 2.0f;
        }

        // Use the queueMessage system to show the text, THEN start the animation.
        float finalCaptureChance = captureChance;
        queueMessage("You used a " + formatItemName(pokeball.getItemId()) + "!", 1.5f, () -> {
            attemptCapture((WildPokemon) enemyPokemon, finalCaptureChance);
        });

        // We no longer transition state here; the animation callback will do it.
    }

    // Add this new method to BattleTable to show the party screen specifically for item use
    private void showPartyScreenForItemUse() {
        transitionToState(BattleState.AWAITING_ITEM_TARGET);

        PokemonPartyWindow partyScreen = new PokemonPartyWindow(
            skin,
            GameContext.get().getPlayer().getPokemonParty(),
            true, // It's still battle mode
            (int selectedIndex) -> {
                // This is the listener for the PokemonPartyWindow.
                // It runs when a Pokémon is selected as the target.
                Pokemon targetPokemon = GameContext.get().getPlayer().getPokemonParty().getPokemon(selectedIndex);
                if (itemToUse != null && targetPokemon != null) {
                    if (itemToUse.getItemId().toLowerCase().contains("potion")) {
                        useItemOnPokemon(itemToUse, targetPokemon);
                    } else if (itemToUse.getItemId().toLowerCase().contains("elixir") || itemToUse.getItemId().toLowerCase().contains("ether")) {
                        useElixirOnPokemon(itemToUse, targetPokemon);
                    } else {
                        // Handle other item types here
                        queueMessage("This item can't be used right now.");
                        transitionToState(BattleState.PLAYER_CHOICE);
                    }
                }
                itemToUse = null; // Clear the stored item
            },
            () -> {
                // This runs if the player cancels the party screen.
                itemToUse = null;
                transitionToState(BattleState.PLAYER_CHOICE);
            }
       ,false );
        partyScreen.show(stage);
    }

    private void handlePotionUse(ItemData potion) {
        showPartyScreenForItem(potion);
    }

    private void handleElixirUse(ItemData elixir) {
        showPartyScreenForElixir(elixir);
    }


    private void showPartyScreenForElixir(ItemData elixir) {
        PokemonPartyWindow partyScreen = new PokemonPartyWindow(
            skin,
            GameContext.get().getPlayer().getPokemonParty(),
            false,
            (selectedIndex) -> {
                Pokemon selectedPokemon = GameContext.get().getPlayer().getPokemonParty().getPokemon(selectedIndex);
                if (selectedPokemon != null) {
                    useElixirOnPokemon(elixir, selectedPokemon);
                }
            },
            () -> {
                currentState = BattleState.PLAYER_CHOICE;
                showActionMenu(true);
            }
        ,false);

        stage.addActor(partyScreen);
        partyScreen.show(stage);
    }

    private void useElixirOnPokemon(ItemData elixir, Pokemon pokemon) {
        // CHECK 1: Don't use if all PP is full.
        if (!pokemon.needsPpRestore()) {
            queueMessage("It won't have any effect!");
            transitionToState(BattleState.PLAYER_CHOICE); // Go back to the main menu
            return;
        }

        String elixirType = elixir.getItemId().toLowerCase();
        queueMessage("Used an " + formatItemName(elixir.getItemId()) + "!");
        AudioManager.getInstance().playSound(AudioManager.SoundEffect.ITEM_PICKUP);

        // Apply effect
        if (elixirType.contains("max_elixir")) {
            pokemon.restorePpToAllMovesMax();
        } else if (elixirType.contains("elixir")) {
            pokemon.restorePpToAllMoves(10);
        } else if (elixirType.contains("max_ether")) {
            // Simplification: Ethers would normally require move selection UI.
            // For now, they act like Max Elixirs.
            pokemon.restorePpToAllMovesMax();
        } else if (elixirType.contains("ether")) {
            // For now, Ether acts like an Elixir.
            pokemon.restorePpToAllMoves(10);
        }

        // Consume item
        elixir.setCount(elixir.getCount() - 1);
        if (elixir.getCount() <= 0) {
            GameContext.get().getPlayer().getInventory().removeItem(elixir);
        }

        // End the player's turn
        playerActionTaken = true;
        transitionToState(BattleState.ENEMY_TURN);
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
        ,false);

        stage.addActor(partyScreen);
        partyScreen.show(stage);
    }

    private void useItemOnPokemon(ItemData item, Pokemon pokemon) {
        String itemId = item.getItemId().toLowerCase();

        // Check if the item is a Potion type
        if (itemId.contains("potion")) {
            // CHECK 1: Don't use on a fainted or full-HP Pokémon.
            if (pokemon.getCurrentHp() <= 0 || !pokemon.isHurt()) {
                queueMessage("It won't have any effect!");
                transitionToState(BattleState.PLAYER_CHOICE); // Go back to the main menu
                return;
            }

            // Determine heal amount
            int healAmount;
            if (itemId.contains("max")) {
                healAmount = pokemon.getStats().getHp();
            } else if (itemId.contains("hyper")) {
                healAmount = 200;
            } else if (itemId.contains("super")) {
                healAmount = 50;
            } else {
                healAmount = 20;
            }

            // Apply effect and update UI
            queueMessage("Used a " + formatItemName(item.getItemId()) + "!");
            AudioManager.getInstance().playSound(AudioManager.SoundEffect.ITEM_PICKUP);
            pokemon.heal(healAmount);
            updateHPBars();

            // Consume item
            item.setCount(item.getCount() - 1);
            if (item.getCount() <= 0) {
                GameContext.get().getPlayer().getInventory().removeItem(item);
            }

            // End the player's turn
            playerActionTaken = true;
            transitionToState(BattleState.ENEMY_TURN);
        }
        // Other item types would go here...
    }


    private void showPartyScreen() {
        PokemonPartyWindow partyScreen = new PokemonPartyWindow(
            skin,
            GameContext.get().getPlayer().getPokemonParty(),
            true, // battle mode
            (selectedIndex) -> {
                Pokemon selectedPokemon = GameContext.get().getPlayer().getPokemonParty().getPokemon(selectedIndex);
                if (selectedPokemon != null && selectedPokemon.getCurrentHp() > 0 && selectedPokemon != playerPokemon) {
                    startSwitchPokemonSequence(selectedIndex); // MODIFIED: Call the renamed method
                }
            },
            () -> {
                currentState = BattleState.PLAYER_CHOICE;
                showActionMenu(true);
            }
       ,true );

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
        if (battleWeather == null || battleWeather == WeatherSystem.WeatherType.CLEAR) {
            weatherDisplay.setVisible(false);
            return;
        }

        // Show weather indicator
        String weatherText = "";
        switch (battleWeather) {
            case RAIN:
            case HEAVY_RAIN:
                weatherText = "Rain";
                break;
            case SNOW:
            case BLIZZARD:
                weatherText = "Hail";  // Snow/Blizzard acts like Hail in battles
                break;
            case SANDSTORM:
                weatherText = "Sandstorm";
                break;
            case FOG:
                weatherText = "Fog";
                break;
            case THUNDERSTORM:
                weatherText = "Rain";  // Thunderstorm acts like heavy rain
                break;
            default:
                weatherDisplay.setVisible(false);
                return;
        }

        weatherLabel.setText(weatherText);
        weatherDisplay.setVisible(true);

        // Position in top-right corner
        weatherDisplay.pack();
        float tableWidth = getWidth() > 0 ? getWidth() : BATTLE_SCREEN_WIDTH;
        weatherDisplay.setPosition(tableWidth - weatherDisplay.getWidth() - 10, BATTLE_SCREEN_HEIGHT - weatherDisplay.getHeight() - 10);
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
        if (move.getName().equalsIgnoreCase("Night-Shade")) {
            float typeMultiplier = calculateTypeEffectiveness(move, defender);
            return typeMultiplier > 0 ? attacker.getLevel() : 0;
        }

        int level = attacker.getLevel();
        float attackStat = move.isSpecial() ? attacker.getStats().getSpecialAttack() : attacker.getStats().getAttack();
        float defenseStat = move.isSpecial() ? defender.getStats().getSpecialDefense() : defender.getStats().getDefense();

        float baseDamage = (((2 * level) / 5f + 2) * move.getPower() * attackStat / defenseStat) / 50f + 2;

        float stab = (attacker.getPrimaryType() == move.getType() ||
            attacker.getSecondaryType() == move.getType()) ? 1.5f : 1.0f;

        float typeMultiplier = calculateTypeEffectiveness(move, defender);
        if (typeMultiplier == 0) {
            return 0;
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
        playerStatusIcon = new Image();
        enemyStatusIcon = new Image();
        playerStatusIcon.setVisible(false);
        enemyStatusIcon.setVisible(false);
        playerStatusIcon.setSize(STATUS_ICON_WIDTH, STATUS_ICON_HEIGHT);
        enemyStatusIcon.setSize(STATUS_ICON_WIDTH, STATUS_ICON_HEIGHT);
    }


    private void setupHPBars() {
        // Use the new method to set the initial style
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

    public void updateHPBars() {
        float playerHPPercent = playerPokemon.getCurrentHp() / (float) playerPokemon.getStats().getHp();
        animateHPBar(playerHPBar, playerPokemon.getCurrentHp(), playerHPPercent);

        float enemyHPPercent = enemyPokemon.getCurrentHp() / (float) enemyPokemon.getStats().getHp();
        animateHPBar(enemyHPBar, enemyPokemon.getCurrentHp(), enemyHPPercent);
    }

    private void animateHPBar(ProgressBar bar, float targetValue, float percentage) {
        bar.clearActions();
        bar.addAction(Actions.sequence(
            Actions.run(() -> updateHPBarColor(bar, percentage, skin)),
            Actions.delay(0.1f),
            Actions.run(() -> {
                float currentValue = bar.getValue();
                bar.addAction(Actions.parallel(
                    Actions.run(() -> {
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
            case BATTLE_INTRO:
            case ENEMY_ENTRANCE:
            case PLAYER_ENTRANCE:
                break;
            case INTRO_COMPLETE:
            case PLAYER_CHOICE:
                if (!actionMenu.isVisible()) {
                    showActionMenu(true);
                    battleText.setText("What will " + playerPokemon.getName() + " do?");
                }
                break;
            case PLAYER_MOVE_SELECT:
                break;
            case PLAYER_ACTION_EXECUTE:
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

        enemyPokemonImage.addAction(Actions.sequence(
            Actions.parallel(
                Actions.fadeOut(1.0f), Actions.moveBy(0, -20, 1.0f)
            ),
            Actions.run(() -> {
                int expGained = calculateExperienceGain((WildPokemon) enemyPokemon);

                // Award experience and check if an evolution was triggered
                boolean isEvolving = playerPokemon.addExperience(expGained);

                queueMessage(playerPokemon.getName() + " gained " + expGained + " EXP!");
                updateExpBar();

                // If no evolution was triggered, end the battle after a delay.
                // If an evolution WAS triggered, the EvolutionScreen is now responsible for ending the battle.
                if (!isEvolving) {
                    addAction(Actions.sequence(
                        Actions.delay(2.0f), // Delay to let EXP message show
                        Actions.run(() -> finishBattle(BattleOutcome.WIN))
                    ));
                }
            })
        ));
    }
    private void handlePlayerFaint() {
        playerPokemon.setStatus(Pokemon.Status.FAINTED);
        queueMessage(playerPokemon.getName() + " fainted!");
        GameLogger.info(playerPokemon.getName() + " fainted.");
        // MODIFICATION: Clear Leech Seed on faint
        leechSeedTargets.remove(playerPokemon);
        leechSeedTargets.values().removeIf(healer -> healer == playerPokemon);

        if (callback != null) {
            callback.onStatusChange(playerPokemon, Pokemon.Status.FAINTED);
        }
        playerPokemonImage.addAction(Actions.sequence(
            Actions.parallel(
                Actions.fadeOut(1.0f), Actions.moveBy(0, -20, 1.0f)
            ),
            Actions.run(() -> {
                if (hasAvailablePokemon()) {
                    transitionToState(BattleState.FORCED_SWITCH);
                    showForcedSwitchPartyScreen();
                } else {
                    finishBattle(BattleOutcome.LOSS);
                }
            })
        ));
    }

    public void finishBattle(BattleOutcome outcome) {
        isAnimating = true;
        SequenceAction endSequence = Actions.sequence(
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
    private String formatItemName(String itemId) {
        String[] words = itemId.replace("_", " ").split(" ");
        StringBuilder formatted = new StringBuilder();
        for (String word : words) {
            if (word.length() > 0) {
                formatted.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    formatted.append(word.substring(1).toLowerCase());
                }
                formatted.append(" ");
            }
        }
        return formatted.toString().trim();
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

        // --- FIX: Force apply hardcoded effects for special moves ---
        if (move.getName().equalsIgnoreCase("Poison-Sting")) {
            Move.MoveEffect psEffect = new Move.MoveEffect();
            psEffect.setStatusEffect(Pokemon.Status.POISONED);
            psEffect.setChance(0.3f); // 30% chance
            move.setEffect(psEffect);
        }
        if (move.getName().equalsIgnoreCase("Leech-Seed")) {
            Move.MoveEffect lsEffect = new Move.MoveEffect();
            lsEffect.setStatusEffect(Pokemon.Status.LEECH_SEED);
            lsEffect.setChance(1.0f); // Accuracy check is separate
            move.setEffect(lsEffect);
        }
        if (move.getName().equalsIgnoreCase("Teleport")) {
            queueMessage(attacker.getName() + " used Teleport!");
            SequenceAction teleportSequence = Actions.sequence(
                Actions.delay(1.0f),
                Actions.run(() -> {
                    if (!isPlayerMove) { // Wild Pokemon fled
                        queueMessage(attacker.getName() + " fled!");
                    } else { // Player fled
                        queueMessage("Got away safely!");
                    }
                }),
                Actions.delay(1.5f),
                Actions.run(() -> {
                    if (callback != null) {
                        callback.onBattleEnd(BattleOutcome.ESCAPE);
                    }
                })
            );
            this.addAction(teleportSequence);
            isAnimating = true;
            return; // End execution for this move
        }

        if (move.getName().equalsIgnoreCase("Ember")) {
            Move.MoveEffect emberEffect = new Move.MoveEffect();
            emberEffect.setStatusEffect(Pokemon.Status.BURNED);
            emberEffect.setChance(0.1f); // 10% chance
            move.setEffect(emberEffect);
        }
        if (move.getName().equalsIgnoreCase("Poison Powder") || move.getName().equalsIgnoreCase("Poison-Powder")) {
            Move.MoveEffect ppEffect = new Move.MoveEffect();
            ppEffect.setStatusEffect(Pokemon.Status.POISONED);
            ppEffect.setChance(1.0f); // Accuracy check handles the hit chance
            move.setEffect(ppEffect);
        }
        if (move.getName().equalsIgnoreCase("Thunder-Wave")) {
            Move.MoveEffect twEffect = new Move.MoveEffect();
            twEffect.setStatusEffect(Pokemon.Status.PARALYZED);
            twEffect.setChance(1.0f); // Accuracy check handles the hit chance
            move.setEffect(twEffect);
        }

        if (bidingPokemon.containsKey(attacker)) {
            queueMessage(attacker.getName() + " is storing energy!");
            finishMoveExecution(isPlayerMove); // End turn without attacking
            return;
        }

        if (move.getName().equalsIgnoreCase("Bide")) {
            bidingPokemon.put(attacker, new BideState());
            queueMessage(attacker.getName() + " is storing energy!");
            finishMoveExecution(isPlayerMove); // End turn
            return;
        }

        isAnimating = true;
        setBattleInterfaceEnabled(false);
        if (isPlayerMove) playerActionTaken = true;

        move.setPp(move.getPp() - 1);

        if (!attacker.canAttack()) {
            queueMessage("", 0.5f, () -> finishMoveExecution(isPlayerMove));
            return;
        }

        SequenceAction moveSequence = Actions.sequence();
        final float[] damageHolder = {0f};

        moveSequence.addAction(Actions.run(() -> {
            AudioManager.getInstance().playSound(AudioManager.SoundEffect.MOVE_SELECT);
            queueMessage(attacker.getName() + " used " + move.getName() + "!");
        }));
        moveSequence.addAction(Actions.delay(1.2f));

        moveSequence.addAction(Actions.run(() -> {
            if (move.getAccuracy() > 0 && MathUtils.random() * 100 >= move.getAccuracy()) {
                queueMessage("The attack missed!", 1.5f, () -> finishMoveExecution(isPlayerMove));
                moveSequence.getActions().clear();
                return;
            }

            // BEGIN: NECESSARY CHANGE
            if (move.getPower() > 0) {
                // This is a damaging move
                float effectiveness = calculateTypeEffectiveness(move, defender);
                String effectivenessMessage = getEffectivenessMessage(effectiveness);
                if (!effectivenessMessage.isEmpty()) {
                    queueMessage(effectivenessMessage, 1.0f);
                }
                playEffectivenessSound(effectiveness);

                if (effectiveness > 0f) {
                    damageHolder[0] = calculateDamage(move, attacker, defender);
                    applyDamage(defender, damageHolder[0]);
                }
            }
            // Apply secondary effects for both damaging and status moves
            applyMoveEffect(move.getEffect(), attacker, defender, damageHolder[0]);
            // END: NECESSARY CHANGE
        }));
        moveSequence.addAction(Actions.delay(1.2f));

        moveSequence.addAction(Actions.run(() -> {
            if (checkFaintConditions()) {
                moveSequence.getActions().clear();
                return;
            }
        }));

        moveSequence.addAction(Actions.delay(0.8f));

        moveSequence.addAction(Actions.run(() -> finishMoveExecution(isPlayerMove)));

        addAction(moveSequence);
    }



    private void playEffectivenessSound(float effectiveness) {
        if (effectiveness >= 2.0f) {
            AudioManager.getInstance().playSound(AudioManager.SoundEffect.SUPER_EFFECTIVE);
        } else if (effectiveness > 0f && effectiveness < 1.0f) {
            AudioManager.getInstance().playSound(AudioManager.SoundEffect.NOT_EFFECTIVE);
        } else {
            // Normal effectiveness - play damage sound
            AudioManager.getInstance().playSound(AudioManager.SoundEffect.DAMAGE);
        }
    }


    /**
     * Applies the secondary effect of a move, such as status conditions, stat changes, or health drain.
     * This method is called after a move successfully hits.
     *
     * @param effect The effect to apply.
     * @param attacker The Pokémon using the move.
     * @param target The Pokémon being hit.
     * @param damageDealt The damage that was dealt by the primary effect of the move.
     */
    private void applyMoveEffect(Move.MoveEffect effect, Pokemon attacker, Pokemon target, float damageDealt) {
        if (effect == null) return;

        // Check the chance of the effect happening
        if (MathUtils.random() >= effect.getChance()) {
            GameLogger.info("Effect " + (effect.getStatusEffect() != null ? effect.getStatusEffect().name() : "stat/other") + " did not trigger due to chance.");
            return;
        }

        Pokemon.Status statusToApply = effect.getStatusEffect();
        // Handle volatile status (Leech Seed) separately from primary statuses.
        if (statusToApply == Pokemon.Status.LEECH_SEED) {
            if (target.getPrimaryType() == Pokemon.PokemonType.GRASS || (target.getSecondaryType() != null && target.getSecondaryType() == Pokemon.PokemonType.GRASS)) {
                queueMessage("It doesn't affect " + target.getName() + "...");
            } else if (leechSeedTargets.containsKey(target)) {
                queueMessage(target.getName() + " is already seeded!");
            } else {
                leechSeedTargets.put(target, attacker);
                queueMessage(target.getName() + " was seeded!");
            }
        } else if (statusToApply != null && statusToApply != Pokemon.Status.NONE) {
            // Handle primary statuses (Poison, Burn, etc.)
            Pokemon.Status previousStatus = target.getStatus();
            target.setStatus(statusToApply);

            if (target.getStatus() != previousStatus) {
                queueMessage(target.getName() + " became " + statusToApply.name().toLowerCase() + "!");
                switch (statusToApply) {
                    case PARALYZED:
                    case BURNED:
                    case FROZEN:
                    case POISONED:
                    case BADLY_POISONED:
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
        // BEGIN: NECESSARY CHANGE
        Map<String, Integer> statChanges = effect.getStatModifiers();
        if (statChanges != null && !statChanges.isEmpty()) {
            // Determine who the effect targets: the user or the opponent
            Pokemon effectTarget = "SELF".equalsIgnoreCase(effect.getEffectType()) ? attacker : target;

            for (Map.Entry<String, Integer> entry : statChanges.entrySet()) {
                String statName = entry.getKey();
                int change = entry.getValue();

                // Attempt to modify the stat and check if it was successful
                if (effectTarget.modifyStatStage(statName, change)) {
                    // It worked, so show the appropriate message.
                    String message = effectTarget.getName() + "'s " + formatStatName(statName) + (change > 0 ? " rose!" : " fell!");
                    queueMessage(message);
                    AudioManager.getInstance().playSound(AudioManager.SoundEffect.CURSOR_MOVE); // A fitting sound for stat changes
                } else {
                    // It failed, so the stat must be maxed out.
                    String message = effectTarget.getName() + "'s " + formatStatName(statName) + " won't go any " + (change > 0 ? "higher!" : "lower!");
                    queueMessage(message);
                }
            }
        }
        // END: NECESSARY CHANGE

        if (effect.getEffectType() != null && effect.getEffectType().equalsIgnoreCase("DRAIN")) {
            int healAmount = Math.max(1, (int) (damageDealt * 0.5f)); // Heal 50% of damage, at least 1 HP
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
            case "attack":
                return "Attack";
            case "defense":
                return "Defense";
            case "spatk":
                return "Special Attack";
            case "spdef":
                return "Special Defense";
            case "speed":
                return "Speed";
            case "accuracy":
                return "Accuracy";
            case "evasion":
                return "Evasion";
            default:
                return statKey;
        }
    }


    private void finishMoveExecution(boolean isPlayerMove) {
        // Handle Bide state after a move is executed.
        Pokemon attacker = isPlayerMove ? playerPokemon : enemyPokemon;
        Pokemon defender = isPlayerMove ? enemyPokemon : playerPokemon;

        // Check if the current attacker just finished its biding period.
        BideState bideState = bidingPokemon.get(attacker);
        if (bideState != null) {
            bideState.turnsLeft--;
            if (bideState.turnsLeft <= 0) {
                // Unleash Bide
                queueMessage(attacker.getName() + " unleashed energy!");
                float damageToDeal = bideState.damageTaken * 2;
                if (damageToDeal > 0) {
                    // We add a small delay so the message shows before the damage animation
                    addAction(Actions.sequence(
                        Actions.delay(1.5f),
                        Actions.run(() -> applyDamage(defender, damageToDeal))
                    ));
                }
                bidingPokemon.remove(attacker);
            }
        }

        isAnimating = false;

        if (checkFaintConditions()) {
            // If a faint occurred, the faint handler will proceed the battle state.
            return;
        }

        if (!isPlayerMove) {
            // MODIFICATION: Replaced individual calls with the new central method
            applyEndOfTurnEffectsForTurn();
            if (checkFaintConditions()) {
                // A faint occurred from end-of-turn effects.
                return;
            }
        }

        // If it was the player's move, it's now the enemy's turn.
        if (isPlayerMove) {
            transitionToState(BattleState.ENEMY_TURN);
        } else {
            // If it was the enemy's move, the round is over. It's the player's choice again.
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
        if (move.getName().equalsIgnoreCase("Night-Shade")) {
            return attacker.getLevel();
        }

        if (move.getPower() <= 0) return 0;

        int level = attacker.getLevel();
        float attackStat = move.isSpecial() ? attacker.getStats().getSpecialAttack() : attacker.getStats().getAttack();
        float defenseStat = move.isSpecial() ? defender.getStats().getSpecialDefense() : defender.getStats().getDefense();
        if (attacker.getStatus() == Pokemon.Status.BURNED && !move.isSpecial()) {
            attackStat *= 0.5f;
        }

        float baseDamage = (((2 * level) / 5f + 2) * move.getPower() * attackStat / defenseStat) / 50f + 2;
        float stab = (attacker.getPrimaryType() == move.getType() || attacker.getSecondaryType() == move.getType()) ? 1.5f : 1.0f;
        float typeMultiplier = calculateTypeEffectiveness(move, defender);
        boolean isCritical = MathUtils.random() < 0.0625f;
        if (isCritical) {
            queueMessage("A critical hit!");
            baseDamage *= 1.5f;
            AudioManager.getInstance().playSound(AudioManager.SoundEffect.CRITICAL_HIT);
        }
        float randomModifier = MathUtils.random(0.85f, 1.0f);

        return baseDamage * stab * typeMultiplier * randomModifier;
    }

    private Map<Pokemon, BideState> bidingPokemon = new HashMap<>();

    private static class BideState {
        int turnsLeft = 2; // Bide stores energy for 2 full turns
        float damageTaken = 0;
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
            return "It's super effective!";
        } else if (multiplier > 0f && multiplier < 1.0f) {
            return "It's not very effective...";
        } else if (multiplier == 0f) {
            return "It had no effect...";
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
        updateHPBarColor(playerHPBar, playerPokemon.getCurrentHp() / (float) playerPokemon.getStats().getHp(), skin);
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

        // ADDED: Bide damage accumulation
        if (bidingPokemon.containsKey(target)) {
            BideState bideState = bidingPokemon.get(target);
            bideState.damageTaken += damage;
            GameLogger.info(target.getName() + " is biding, accumulated " + bideState.damageTaken + " damage.");
        }

        float oldHP = target.getCurrentHp();
        float newHP = Math.max(0, oldHP - damage);
        target.setCurrentHp(newHP);
        ProgressBar targetBar = (target == playerPokemon) ? playerHPBar : enemyHPBar;
        animateHPChange(targetBar, oldHP, newHP, target.getStats().getHp());
    }

// ... (rest of the class up to applyEndOfTurnEffectsForTurn) ...

    /**
     * Applies all end-of-turn effects like poison damage, Leech Seed drain, and weather damage
     * for both Pokémon at the conclusion of a full battle round.
     */
    private void applyEndOfTurnEffectsForTurn() {
        // Apply weather damage to both Pokemon at end of turn
        applyWeatherDamage();

        if (playerPokemon.getCurrentHp() > 0) {
            playerPokemon.applyEndOfTurnEffects();
            if (leechSeedTargets.containsKey(playerPokemon)) {
                Pokemon healer = leechSeedTargets.get(playerPokemon);
                if (healer != null && healer.getCurrentHp() > 0) {
                    queueMessage(playerPokemon.getName() + "'s health was sapped by Leech Seed!");
                    int damage = Math.max(1, playerPokemon.getStats().getHp() / 8);
                    applyDamage(playerPokemon, damage);
                    healer.heal(damage);
                    updateHPBars();
                }
            }
        }
        if (checkFaintConditions()) return;
        if (enemyPokemon.getCurrentHp() > 0) {
            enemyPokemon.applyEndOfTurnEffects();
            if (leechSeedTargets.containsKey(enemyPokemon)) {
                Pokemon healer = leechSeedTargets.get(enemyPokemon);
                if (healer != null && healer.getCurrentHp() > 0) {
                    queueMessage(enemyPokemon.getName() + "'s health was sapped by Leech Seed!");
                    int damage = Math.max(1, enemyPokemon.getStats().getHp() / 8);
                    applyDamage(enemyPokemon, damage);
                    healer.heal(damage);
                    updateHPBars();
                    GameLogger.info(healer.getName() + " healed " + damage + " from " + enemyPokemon.getName() + " via Leech Seed.");
                }
            }
        }
        checkFaintConditions();
    }

    /**
     * Applies weather damage to both Pokemon if applicable.
     * Sandstorm and Hail deal 1/16 of max HP damage to non-immune Pokemon.
     */
    private void applyWeatherDamage() {
        if (battleWeather == null) return;

        switch (battleWeather) {
            case SANDSTORM:
                // Sandstorm damages all Pokemon except Rock, Ground, and Steel types
                if (playerPokemon.getCurrentHp() > 0 && !isImmuneToSandstorm(playerPokemon)) {
                    int damage = Math.max(1, playerPokemon.getStats().getHp() / 16);
                    queueMessage(playerPokemon.getName() + " is buffeted by the sandstorm!");
                    applyDamage(playerPokemon, damage);
                }
                if (checkFaintConditions()) return;
                if (enemyPokemon.getCurrentHp() > 0 && !isImmuneToSandstorm(enemyPokemon)) {
                    int damage = Math.max(1, enemyPokemon.getStats().getHp() / 16);
                    queueMessage(enemyPokemon.getName() + " is buffeted by the sandstorm!");
                    applyDamage(enemyPokemon, damage);
                }
                break;

            case SNOW:
            case BLIZZARD:
                // Hail (Snow/Blizzard) damages all Pokemon except Ice types
                if (playerPokemon.getCurrentHp() > 0 && !isImmuneToHail(playerPokemon)) {
                    int damage = Math.max(1, playerPokemon.getStats().getHp() / 16);
                    queueMessage(playerPokemon.getName() + " is buffeted by the hail!");
                    applyDamage(playerPokemon, damage);
                }
                if (checkFaintConditions()) return;
                if (enemyPokemon.getCurrentHp() > 0 && !isImmuneToHail(enemyPokemon)) {
                    int damage = Math.max(1, enemyPokemon.getStats().getHp() / 16);
                    queueMessage(enemyPokemon.getName() + " is buffeted by the hail!");
                    applyDamage(enemyPokemon, damage);
                }
                break;

            default:
                // Other weather types don't deal damage
                break;
        }
    }

    /**
     * Checks if a Pokemon is immune to Sandstorm damage.
     * Rock, Ground, and Steel types are immune.
     */
    private boolean isImmuneToSandstorm(Pokemon pokemon) {
        Pokemon.PokemonType primary = pokemon.getPrimaryType();
        Pokemon.PokemonType secondary = pokemon.getSecondaryType();

        return primary == Pokemon.PokemonType.ROCK ||
               primary == Pokemon.PokemonType.GROUND ||
               primary == Pokemon.PokemonType.STEEL ||
               secondary == Pokemon.PokemonType.ROCK ||
               secondary == Pokemon.PokemonType.GROUND ||
               secondary == Pokemon.PokemonType.STEEL;
    }

    /**
     * Checks if a Pokemon is immune to Hail damage.
     * Ice types are immune.
     */
    private boolean isImmuneToHail(Pokemon pokemon) {
        Pokemon.PokemonType primary = pokemon.getPrimaryType();
        Pokemon.PokemonType secondary = pokemon.getSecondaryType();

        return primary == Pokemon.PokemonType.ICE ||
               secondary == Pokemon.PokemonType.ICE;
    }


    // A small change inside animateHPChange is also needed to use the new method
    private void animateHPChange(ProgressBar bar, float fromValue, float toValue, float maxValue) {
        float duration = 0.5f;

        // The target percentage for the color
        float targetPercentage = toValue / maxValue;

        bar.addAction(new Action() {
            float time = 0;

            @Override
            public boolean act(float delta) {
                time += delta;
                float progress = Math.min(1f, time / duration);
                float currentValue = MathUtils.lerp(fromValue, toValue, progress);
                bar.setValue(currentValue);
                // Update the color continuously during the animation
                updateHPBarColor(bar, currentValue / maxValue, skin);
                return progress >= 1f;
            }
        });
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
       ,true );
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
            case PLAYER_ACTION_EXECUTE:
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
                transitionToState(BattleState.PLAYER_CHOICE);
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

    public void attemptCapture(WildPokemon wildPokemon, float captureChance) {
        if (currentState == BattleState.CATCHING) return;

        currentState = BattleState.CATCHING;
        setBattleInterfaceEnabled(false);
        queueMessage("Throwing Pokéball...");

        AudioManager.getInstance().playSound(AudioManager.SoundEffect.THROW_BALL);
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

    public interface BattleCallback {
        void onBattleEnd(BattleOutcome outcome);

        void onTurnEnd(Pokemon activePokemon);

        void onStatusChange(Pokemon pokemon, Pokemon.Status newStatus);

        void onMoveUsed(Pokemon user, Move move, Pokemon target);
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

        float levelRatio = Math.max(0.5f, Math.min(1.5f, (float) L / playerPokemon.getLevel()));

        int expGained = Math.round((a * t * b * L * levelRatio) / 7);
        GameLogger.info(String.format("Experience gained: %d (Base: %d, Level: %d)",
            expGained, b, L));

        return Math.max(1, expGained);
    }

    public Pokemon getPlayerPokemon() {
        return playerPokemon;
    }
}
