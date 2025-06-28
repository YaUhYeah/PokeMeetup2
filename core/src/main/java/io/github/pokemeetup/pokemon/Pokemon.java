
package io.github.pokemeetup.pokemon;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.pokemon.attacks.Move;
import io.github.pokemeetup.pokemon.data.PokemonDatabase;
import io.github.pokemeetup.screens.otherui.BattleTable;
import io.github.pokemeetup.system.gameplay.PokemonAnimations;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;

import java.util.*;

public class Pokemon {
    private static Weather currentWeather = Weather.CLEAR;
    private static int weatherTurns = 0;
    public String name;
    public int level;
    public Stats stats;
    public List<Move> moves;
    public PokemonType primaryType;
    public PokemonType secondaryType;
    public float currentHp;
    private Status status = Status.NONE;
    private int sleepTurns = 0;
    private int toxicCounter = 1;
    private boolean flinched = false;
    private int confusedTurns = 0;
    private String nature;
    private Map<String, Integer> statStages; // "attack", "defense", "spAtk", "spDef", "speed", "accuracy", "evasion"
    private int paralysisTurns = 0; // Example, may not be needed depending on paralysis implementation
    private int confusionTurns = 0;
    private boolean confused = false; // Separate flag for confusion state
    private boolean isShiny;
    private UUID uuid;
    private int currentExperience;
    private TextureRegion iconSprite;
    private TextureRegion frontSprite;
    private TextureRegion backSprite;
    private TextureRegion frontShinySprite;
    private TextureRegion backShinySprite;
    private PokemonAnimations animations;
    private int speciesBaseHp;
    private int speciesBaseAttack;
    private int speciesBaseDefense;
    private int speciesBaseSpAtk;
    private int speciesBaseSpDef;
    private int speciesBaseSpeed;
    private Vector2 position;
    private String direction;
    private boolean isMoving;
    private String growthRate;
    private TextureRegion[] iconFrames; // Array to hold both frames
    private int currentIconFrame;       // Index to track the current frame
    private float frameDuration = 0.5f; // Duration each frame is shown in seconds
    private float frameTimer = 0;

    public Pokemon(String name, int level,
                   int speciesBaseHp, int speciesBaseAttack, int speciesBaseDefense,
                   int speciesBaseSpAtk, int speciesBaseSpDef, int speciesBaseSpeed) {
        this.speciesBaseHp = speciesBaseHp;
        this.speciesBaseAttack = speciesBaseAttack;
        this.speciesBaseDefense = speciesBaseDefense;
        this.speciesBaseSpAtk = speciesBaseSpAtk;
        this.speciesBaseSpDef = speciesBaseSpDef;
        this.speciesBaseSpeed = speciesBaseSpeed;

        this.uuid = UUID.randomUUID();
        this.name = name;
        this.level = level;
        this.nature = generateNature();
        this.isShiny = calculateShinyStatus();
        this.stats = new Stats();
        this.moves = new ArrayList<>();
        this.statStages = new HashMap<>();
        resetStatStages(); // Initialize all stages to 0
        TextureRegion iconSheet = TextureManager.getPokemonicon().findRegion(name.toUpperCase() + "_icon");
        iconFrames = new TextureRegion[2];
        iconFrames[0] = new TextureRegion(iconSheet, 0, 0, iconSheet.getRegionWidth() / 2, iconSheet.getRegionHeight());
        iconFrames[1] = new TextureRegion(iconSheet, iconSheet.getRegionWidth() / 2, 0, iconSheet.getRegionWidth() / 2, iconSheet.getRegionHeight());
        PokemonDatabase.PokemonTemplate template = PokemonDatabase.getTemplate(name);
        if (template != null && template.growthRate != null && !template.growthRate.isEmpty()) {
            this.growthRate = template.growthRate;
        } else {
            this.growthRate = "Medium Fast"; // default growth rate
        }
        this.position = new Vector2();
        this.direction = "down";
        this.isMoving = false;
        this.currentHp = stats.getHp();
        loadIcons(TextureManager.getPokemonicon());
        loadFront(TextureManager.getPokemonfront());
        loadBack(TextureManager.getPokemonback());
        loadOverworld(TextureManager.getPokemonoverworld());
        calculateStats();
        this.currentHp = stats.getHp();
    }
    public void resetStatStages() {
        statStages.put("attack", 0);
        statStages.put("defense", 0);
        statStages.put("spAtk", 0);
        statStages.put("spDef", 0);
        statStages.put("speed", 0);
        statStages.put("accuracy", 0); // Often handled differently, but include for completeness
        statStages.put("evasion", 0);  // Often handled differently
    }
    protected Pokemon(boolean noTexture) {
        this.uuid = UUID.randomUUID();
        this.statStages = new HashMap<>(); // Initialize stages here too
        this.name = "";         // Will be set later by the subclass
        this.level = 1;         // Will be overwritten later
        this.nature = generateNature();
        this.isShiny = calculateShinyStatus();
        this.stats = new Stats();
        this.moves = new ArrayList<>();
        this.growthRate = "Medium Fast"; // default (or you can load from the database if needed)
        this.position = new Vector2();
        this.direction = "down";
        this.isMoving = false;
        this.currentHp = 0; // will be set later
    }

    public Pokemon(String name, int level) {
        this(name, level,
            getBaseStat(name, "hp"),
            getBaseStat(name, "attack"),
            getBaseStat(name, "defense"),
            getBaseStat(name, "spAtk"),
            getBaseStat(name, "spDef"),
            getBaseStat(name, "speed"));
    }


    /**
     * Helper method to look up a given stat for a Pokémon by name.
     *
     * @param name The Pokémon’s name.
     * @param stat A string identifying the stat (“hp”, “attack”, “defense”, “spAtk”, “spDef”, or “speed”)
     * @return The value from the template if found; otherwise a default value.
     */
    private static int getBaseStat(String name, String stat) {
        PokemonDatabase.PokemonTemplate template = PokemonDatabase.getTemplate(name);
        if (template != null) {
            switch (stat) {
                case "hp":
                    return template.baseStats.baseHp;
                case "attack":
                    return template.baseStats.baseAttack;
                case "defense":
                    return template.baseStats.baseDefense;
                case "spAtk":
                    return template.baseStats.baseSpAtk;
                case "spDef":
                    return template.baseStats.baseSpDef;
                case "speed":
                    return template.baseStats.baseSpeed;
                default:
                    return 45; // fallback default
            }
        }
        return 45; // fallback default if template not found
    }

    public static void applyWeatherEffects(Pokemon pokemon) {
        if (weatherTurns > 0) {
            switch (currentWeather) {
                case SANDSTORM:
                    if (pokemon.primaryType != PokemonType.ROCK &&
                        pokemon.primaryType != PokemonType.GROUND &&
                        pokemon.primaryType != PokemonType.STEEL) {
                        pokemon.currentHp -= pokemon.stats.getHp() / 16;
                    }
                    break;

                case HAIL:
                    if (pokemon.primaryType != PokemonType.ICE) {
                        pokemon.currentHp -= pokemon.stats.getHp() / 16;
                    }
                    break;
            }
            weatherTurns--;
            if (weatherTurns <= 0) {
                currentWeather = Weather.CLEAR;
            }
        }
    }

    private int totalExpForLevel(int L) {
        double exp;
        switch (growthRate.toLowerCase()) {
            case "fast":
                exp = (4.0 * Math.pow(L, 3)) / 5.0;
                break;
            case "medium slow":
                exp = ((6.0 / 5.0) * Math.pow(L, 3)) - (15 * Math.pow(L, 2)) + (100 * L) - 140;
                break;
            case "slow":
                exp = (5.0 * Math.pow(L, 3)) / 4.0;
                break;
            case "medium fast":
            default:
                exp = Math.pow(L, 3);
                break;
        }
        return (int) exp;
    }

    public int getExperienceForNextLevel() {
        return totalExpForLevel(level + 1) - totalExpForLevel(level);
    }

    public int getSpeciesBaseHp() {
        return speciesBaseHp;
    }

    public int getSpeciesBaseAttack() {
        return speciesBaseAttack;
    }

    public int getSpeciesBaseDefense() {
        return speciesBaseDefense;
    }

    public int getSpeciesBaseSpAtk() {
        return speciesBaseSpAtk;
    }

    public int getSpeciesBaseSpDef() {
        return speciesBaseSpDef;
    }

    public int getSpeciesBaseSpeed() {
        return speciesBaseSpeed;
    }

    public int getCurrentExperience() {
        return currentExperience;
    }


    public void heal() {
        this.currentHp = this.stats.getHp();
        this.status = Status.NONE;
        this.toxicCounter = 1;
        this.flinched = false;
        this.confused = false;
        this.confusionTurns = 0;
        this.sleepTurns = 0;
        resetStatStages(); // Reset stat stages on heal
        calculateStats(); // Recalculate stats after resetting stages
    }
    public void heal(int amount) {
        restoreHealth(amount);
    }
    public void restoreHealth(int amount) {
        this.currentHp = Math.min(this.stats.getHp(), this.currentHp + amount);
    }

    public boolean hasStatus() {
        return status != Status.NONE;
    }

    public Status getStatus() {
        return status;
    }
    public void setStatus(Status newStatus) {
        if (newStatus == null || this.status != Status.NONE) {
            if (newStatus == Status.ASLEEP && this.status == Status.ASLEEP) {
            } else if (newStatus != Status.NONE) {
                GameLogger.info(name + " is already " + this.status + ", cannot apply " + newStatus);
            }
            return;
        }
        if ((newStatus == Status.POISONED || newStatus == Status.BADLY_POISONED) &&
            (primaryType == PokemonType.POISON || secondaryType == PokemonType.POISON ||
                primaryType == PokemonType.STEEL || secondaryType == PokemonType.STEEL)) {
            GameLogger.info(name + " is immune to poison!");
            return;
        }
        if (newStatus == Status.BURNED &&
            (primaryType == PokemonType.FIRE || secondaryType == PokemonType.FIRE)) {
            GameLogger.info(name + " is immune to burn!");
            return;
        }
        if (newStatus == Status.PARALYZED &&
            (primaryType == PokemonType.ELECTRIC || secondaryType == PokemonType.ELECTRIC)) {
            GameLogger.info(name + " is immune to paralysis!");
            return;
        }
        if (newStatus == Status.FROZEN &&
            (primaryType == PokemonType.ICE || secondaryType == PokemonType.ICE)) {
            GameLogger.info(name + " is immune to freeze!");
            return;
        }

        this.status = newStatus;

        if (GameContext.get() != null && GameContext.get().getBattleTable() != null) {
            GameContext.get().getBattleTable().queueMessage(name + " was " + newStatus.name().toLowerCase() + "!");
        } else {
            GameLogger.info(name + " is now " + newStatus);
        }
        switch (newStatus) {
            case ASLEEP:
                sleepTurns = MathUtils.random(1, 3); // Standard sleep 1-3 turns
                break;
            case BADLY_POISONED:
                toxicCounter = 1;
                break;
            default:
                break;
        }
        calculateStats();
    }

    /**
     * Modifies a specific stat stage, clamped between -6 and +6.
     * @param statName "attack", "defense", "spAtk", "spDef", "speed", "accuracy", "evasion"
     * @param change Amount to change by (e.g., -1 for Growl, +2 for Swords Dance)
     * @return True if the stage was changed, false if it was already at the limit.
     */
    public boolean modifyStatStage(String statName, int change) {
        String key = statName.toLowerCase();
        if (!statStages.containsKey(key)) {
            GameLogger.error("Attempted to modify unknown stat stage: " + statName);
            return false;
        }

        int currentStage = statStages.get(key);
        int newStage = MathUtils.clamp(currentStage + change, -6, 6);

        if (newStage == currentStage) {
            GameLogger.info(name + "'s " + statName + " won't go any " + (change > 0 ? "higher!" : "lower!"));
            return false;
        }

        statStages.put(key, newStage);
        calculateStats();
        GameLogger.info(name + "'s " + statName + (change > 0 ? " rose!" : " fell!"));
        return true;
    }

    /**
     * Gets the multiplier for a stat based on its current stage.
     * Formula: stage > 0 ? (2 + stage) / 2 : 2 / (2 - stage)
     * Accuracy/Evasion use a different formula: stage > 0 ? (3 + stage) / 3 : 3 / (3 - stage)
     * @param statName "attack", "defense", "spAtk", "spDef", "speed", "accuracy", "evasion"
     * @return The multiplier (e.g., 1.0 for stage 0, 1.5 for stage +1, 0.66 for stage -1)
     */
    public float getStatModifier(String statName) {
        String key = statName.toLowerCase();
        int stage = statStages.getOrDefault(key, 0);

        if (key.equals("accuracy") || key.equals("evasion")) {
            if (stage > 0) {
                return (3.0f + stage) / 3.0f;
            } else if (stage < 0) {
                return 3.0f / (3.0f - stage); // Note: stage is negative here
            } else {
                return 1.0f;
            }
        } else { // Attack, Defense, SpAtk, SpDef, Speed
            if (stage > 0) {
                return (2.0f + stage) / 2.0f;
            } else if (stage < 0) {
                return 2.0f / (2.0f - stage); // Note: stage is negative here
            } else {
                return 1.0f;
            }
        }
    }
    public boolean canAttack() {
        if (currentHp <= 0) {
            return false;
        }
        if (flinched) {
            flinched = false; // Flinch lasts only one turn
            GameContext.get().getBattleTable().queueMessage(name + " flinched!");
            return false;
        }
        switch (status) {
            case ASLEEP:
                sleepTurns--;
                if (sleepTurns <= 0) {
                    GameContext.get().getBattleTable().queueMessage(name + " woke up!");
                    cureStatus(); // Clears sleep status
                    return true; // Can attack this turn
                } else {
                    GameContext.get().getBattleTable().queueMessage(name + " is fast asleep.");
                    return false;
                }
            case FROZEN:
                if (MathUtils.random() < 0.20f) { // 20% chance to thaw
                    GameContext.get().getBattleTable().queueMessage(name + " thawed out!");
                    cureStatus(); // Clears frozen status
                    return true; // Can attack this turn
                } else {
                    GameContext.get().getBattleTable().queueMessage(name + " is frozen solid!");
                    return false;
                }
            case PARALYZED:
                if (MathUtils.random() < 0.25f) { // 25% chance of full paralysis
                    GameContext.get().getBattleTable().queueMessage(name + " is fully paralyzed!");
                    return false;
                }
                break;
            default:
                break;
        }
        if (confused) {
            GameContext.get().getBattleTable().queueMessage(name + " is confused!");
            confusionTurns--;
            if (confusionTurns <= 0) {
                GameContext.get().getBattleTable().queueMessage(name + " snapped out of its confusion!", 1.0f, () -> this.confused = false);
            } else {
                if (MathUtils.random() < 0.33f) { // 33% chance to hit self
                    GameContext.get().getBattleTable().queueMessage("It hurt itself in its confusion!");
                    float damage = calculateConfusionDamage();
                    GameContext.get().getBattleTable().applyDamage(this, damage);
                    if (currentHp <= 0) {
                        setStatus(Status.FAINTED);
                    }
                    return false; // Cannot attack this turn
                }
            }
        }

        return true;
    }

    private float calculateConfusionDamage() {
        int level = this.getLevel();
        float attackStat = this.getStats().getAttack();
        float defenseStat = this.getStats().getDefense();
        int basePower = 40;

        float baseDamage = (((2 * level) / 5f + 2) * basePower * attackStat / defenseStat) / 50f + 2;
        return baseDamage * MathUtils.random(0.85f, 1.0f);
    }

    public void setConfused(boolean confused) {
        if (confused && !this.confused) {
            this.confused = true;
            this.confusionTurns = MathUtils.random(1, 4);
            GameLogger.info(name + " became confused!");
        } else if (!confused) {
            this.confused = false;
            this.confusionTurns = 0;
        }
    }

    public void applyEndOfTurnEffects() {
        if (currentHp <= 0) return;

        BattleTable battleTable = GameContext.get().getBattleTable();
        switch (status) {
            case POISONED:
                battleTable.queueMessage(name + " was hurt by poison!");
                battleTable.applyDamage(this, stats.getHp() / 8f);
                break;
            case BADLY_POISONED:
                battleTable.queueMessage(name + " was badly hurt by poison!");
                battleTable.applyDamage(this, (stats.getHp() * toxicCounter) / 16f);
                toxicCounter = Math.min(toxicCounter + 1, 15);
                break;
            case BURNED:
                battleTable.queueMessage(name + " was hurt by its burn!");
                battleTable.applyDamage(this, stats.getHp() / 16f);
                break;
            default:
                break;
        }

        if (currentHp <= 0) {
            status = Status.FAINTED;
            battleTable.queueMessage(name + " fainted!");
        }
    }



    public void cureStatus() {
        this.status = Status.NONE;
        this.sleepTurns = 0;
        this.toxicCounter = 1;
        this.flinched = false;
        this.confusedTurns = 0;
    }

    public float getStatusModifier(Move move) {
        if (status == Status.BURNED && !move.isSpecial()) {
            return 0.5f; // Burn halves physical attack
        }
        if (status == Status.PARALYZED) {
            return 0.5f; // Paralysis halves speed (implement in speed calculations)
        }
        return 1.0f;
    }

    private void checkLevelUp() {
        int currentLevel = level;
        int expNeeded = getExperienceForNextLevel();

        while (currentExperience >= expNeeded) {
            levelUp();
            GameLogger.info(String.format("%s gained %d experience points!",
                getName(), currentExperience - expNeeded));
            currentExperience -= expNeeded;
            expNeeded = getExperienceForNextLevel();
        }
    }

    public void addExperience(int exp) {
        currentExperience += exp;
        checkLevelUp();
    }

    private void levelUp() {
        level++;
        GameLogger.info(getName() + " leveled up to " + level + "!");
        GameContext.get().getChatSystem().addSystemMessage(getName() + " leveled up to " + level + "!");
        int oldHp = stats.getHp();
        int oldAttack = stats.getAttack();
        int oldDefense = stats.getDefense();
        int oldSpAtk = stats.getSpecialAttack();
        int oldSpDef = stats.getSpecialDefense();
        int oldSpeed = stats.getSpeed();
        calculateStats();
        showStatIncrease("HP", oldHp, stats.getHp());
        showStatIncrease("Attack", oldAttack, stats.getAttack());
        showStatIncrease("Defense", oldDefense, stats.getDefense());
        showStatIncrease("Sp. Atk", oldSpAtk, stats.getSpecialAttack());
        showStatIncrease("Sp. Def", oldSpDef, stats.getSpecialDefense());
        showStatIncrease("Speed", oldSpeed, stats.getSpeed());

        calculateStats();
        int hpIncrease = stats.getHp() - oldHp;
        currentHp += hpIncrease; // Also increase current HP
        learnNewMovesAtLevel(level);
    }


    public int getBaseExperience() {
        switch (name.toUpperCase()) {
            case "CHARMANDER":
            case "BULBASAUR":
            case "SQUIRTLE":
                return 64;
            case "PIDGEY":
            case "RATTATA":
                return 50;
            case "PIKACHU":
                return 112;
            default:
                return 60;
        }
    }

    public TextureRegion getCurrentIconFrame(float delta) {
        frameTimer += delta;
        if (frameTimer >= frameDuration) {
            frameTimer = 0;
            currentIconFrame = (currentIconFrame + 1) % iconFrames.length; // Toggle between frames
        }
        return iconFrames[currentIconFrame];
    }

    private void loadIcons(TextureAtlas atlas) {
        String baseName = name.toUpperCase();
        iconSprite = atlas.findRegion(baseName + "_icon");
    }

    private void loadOverworld(TextureAtlas atlas) {
        String baseName = name.toUpperCase();
        TextureRegion overworldSheet = atlas.findRegion(baseName + "_overworld");
        if (overworldSheet != null) {
            animations = new PokemonAnimations(overworldSheet);
        } else {
            GameLogger.error("Failed to load overworld sprite sheet for: " + name);
        }
    }


    private void showStatIncrease(String statName, int oldValue, int newValue) {
        int increase = newValue - oldValue;
        if (increase > 0) {
            GameLogger.info(getName() + "'s " + statName + " increased by " + increase + "!");
        }
    }

    private void learnNewMovesAtLevel(int level) {
        List<PokemonDatabase.MoveEntry> moveEntries = PokemonDatabase.getTemplate(name).moves;
        for (PokemonDatabase.MoveEntry entry : moveEntries) {
            if (entry.level == level) {
                Move newMove = PokemonDatabase.getMoveByName(entry.name);
                if (newMove != null) {
                    Move clonedMove = PokemonDatabase.cloneMove(newMove);
                    if (moves.size() < 4) {
                        moves.add(clonedMove);
                        GameLogger.info(name + " learned " + entry.name + "!");
                        if (GameContext.get().getBattleTable() != null) {
                            GameContext.get().getBattleTable().displayMessage(name + " learned " + entry.name + "!");
                        }
                    } else {
                        if (GameContext.get().getBattleTable() != null) {
                            GameContext.get().getBattleTable().showMoveReplacementDialog(clonedMove);
                        } else {
                            moves.remove(0);
                            moves.add(clonedMove);
                            GameLogger.info(name + " learned " + entry.name + " by replacing an old move!");
                            if (GameContext.get().getBattleTable() != null) {
                                GameContext.get().getBattleTable().displayMessage(name + " learned " + entry.name + " by replacing an old move!");
                            }
                        }
                    }
                }
            }
        }
    }


    private void loadFront(TextureAtlas atlas) {
        String baseName = name.toUpperCase();
        frontSprite = atlas.findRegion(baseName + "_front");
        frontShinySprite = atlas.findRegion(baseName + "_front_shiny");

    }

    private void loadBack(TextureAtlas atlas) {
        String baseName = name.toUpperCase();
        backSprite = atlas.findRegion(baseName + "_back");
        backShinySprite = atlas.findRegion(baseName + "_back_shiny");

    }

    public void update(float delta) {
        if (animations != null) {
            animations.update(delta);
        }
    }

    public TextureRegion getIconSprite() {
        return iconSprite;
    }

    public void setIconSprite(TextureRegion iconSprite) {
        this.iconSprite = iconSprite;
    }

    public TextureRegion getFrontSprite() {
        return frontSprite;
    }

    public void setFrontSprite(TextureRegion frontSprite) {
        this.frontSprite = frontSprite;
    }

    public TextureRegion getBackSprite() {
        return backSprite;
    }

    public void setBackSprite(TextureRegion backSprite) {
        this.backSprite = backSprite;
    }

    public TextureRegion getFrontShinySprite() {
        return frontShinySprite;
    }

    public void setFrontShinySprite(TextureRegion frontShinySprite) {
        this.frontShinySprite = frontShinySprite;
    }

    public TextureRegion getBackShinySprite() {
        return backShinySprite;
    }

    public void setBackShinySprite(TextureRegion backShinySprite) {
        this.backShinySprite = backShinySprite;
    }

    public Vector2 getPosition() {
        return position;
    }

    public void setPosition(Vector2 position) {
        this.position = position;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public boolean isMoving() {
        return isMoving;
    }

    public void setMoving(boolean moving) {
        isMoving = moving;
    }

    public void render(SpriteBatch batch) {
        if (animations != null) {
            TextureRegion currentFrame = animations.getCurrentFrame(direction, isMoving);
            batch.draw(currentFrame, position.x, position.y);
        }
    }

    private String generateNature() {
        String[] natures = {"Hardy", "Lonely", "Brave", "Adamant", "Naughty", "Bold", "Docile",
            "Relaxed", "Impish", "Lax", "Timid", "Hasty", "Serious", "Jolly",
            "Naive", "Modest", "Mild", "Quiet", "Bashful", "Rash", "Calm",
            "Gentle", "Sassy", "Careful", "Quirky"};
        return natures[new Random().nextInt(natures.length)];
    }

    private boolean calculateShinyStatus() {
        return new Random().nextInt(4096) == 0; // 1/4096 chance in modern games
    }
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String getNature() {
        return nature;
    }

    public void setNature(String nature) {
        this.nature = nature;
    }

    public boolean isShiny() {
        return isShiny;
    }

    public void setShiny(boolean shiny) {
        isShiny = shiny;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public Stats getStats() {
        return stats;
    }

    public void setStats(Stats stats) {
        this.stats = stats;
    }

    public List<Move> getMoves() {
        return moves;
    }

    public void setMoves(List<Move> moves) {
        this.moves = moves;
    }

    public PokemonType getPrimaryType() {
        return primaryType;
    }

    public void setPrimaryType(PokemonType type) {
        this.primaryType = type;
    }

    public PokemonType getSecondaryType() {
        return secondaryType;
    }

    public void setSecondaryType(PokemonType type) {
        this.secondaryType = type;
    }

    public int getCurrentHp() {
        return (int) currentHp;
    }

    public void setCurrentHp(float hp) {
        this.currentHp = hp;
    }

    public PokemonAnimations getAnimations() {
        return animations;
    }

    public void setAnimations(PokemonAnimations animations) {
        this.animations = animations;
    }

    public int getToxicCounter() {
        return toxicCounter;
    }

    public void incrementToxicCounter() {
        toxicCounter = Math.min(toxicCounter + 1, 15); // Cap at 15
    }

    public void resetToxicCounter() {
        toxicCounter = 1;
    }


    int calculateStat(int base, int iv, int ev) {
        float natureModifier = 1.0f + (new Random().nextFloat() * 0.2f - 0.1f);
        return (int) (((2 * base + iv + (float) ev / 4) * level / 100f + 5) * natureModifier);
    }
    public void calculateStats() {
        stats.setHp(((2 * speciesBaseHp + stats.ivs[0] + stats.evs[0] / 4) * level / 100) + level + 10);
        int baseAttack = calculateStat(speciesBaseAttack, stats.ivs[1], stats.evs[1]);
        int baseDefense = calculateStat(speciesBaseDefense, stats.ivs[2], stats.evs[2]);
        int baseSpAtk = calculateStat(speciesBaseSpAtk, stats.ivs[3], stats.evs[3]);
        int baseSpDef = calculateStat(speciesBaseSpDef, stats.ivs[4], stats.evs[4]);
        int baseSpeed = calculateStat(speciesBaseSpeed, stats.ivs[5], stats.evs[5]);
        stats.setAttack(Math.max(1, (int) (baseAttack * getStatModifier("attack"))));
        stats.setDefense(Math.max(1, (int) (baseDefense * getStatModifier("defense"))));
        stats.setSpecialAttack(Math.max(1, (int) (baseSpAtk * getStatModifier("spAtk"))));
        stats.setSpecialDefense(Math.max(1, (int) (baseSpDef * getStatModifier("spDef"))));
        stats.setSpeed(Math.max(1, (int) (baseSpeed * getStatModifier("speed"))));
        if (status == Status.PARALYZED) {
            stats.setSpeed(stats.getSpeed() / 2);
        }
        if (status == Status.BURNED) {
            stats.setAttack(stats.getAttack() / 2);
        }
        stats.setAttack(Math.max(1, stats.getAttack()));
        stats.setDefense(Math.max(1, stats.getDefense()));
        stats.setSpecialAttack(Math.max(1, stats.getSpecialAttack()));
        stats.setSpecialDefense(Math.max(1, stats.getSpecialDefense()));
        stats.setSpeed(Math.max(1, stats.getSpeed()));
    }

    public enum Status {
        NONE,
        PARALYZED,
        POISONED,
        BADLY_POISONED,
        BURNED,
        FROZEN,
        ASLEEP,
        LEECH_SEED,  // NEW!
        FAINTED
    }


    public enum Weather {
        CLEAR,
        RAIN,
        SUNNY,
        SANDSTORM,
        HAIL
    }

    public enum PokemonType {
        NORMAL, FIRE, WATER, ELECTRIC, GRASS, ICE, FIGHTING, POISON, GROUND, FLYING,
        PSYCHIC, BUG, ROCK, GHOST, DRAGON, DARK, STEEL, FAIRY, UNKNOWN
    }

    public static class Stats {
        public int[] ivs;
        public int[] evs;
        private int hp;
        private int attack;
        private int defense;
        private int specialAttack;
        private int specialDefense;
        private int speed;

        public Stats() {
            this.ivs = generateIVs();
            this.evs = new int[6];
        }

        private int[] generateIVs() {
            int[] ivs = new int[6];
            Random random = new Random();
            for (int i = 0; i < 6; i++) {
                ivs[i] = random.nextInt(32);
            }
            return ivs;
        }

        public int getHp() {
            return hp;
        }

        public void setHp(int hp) {
            this.hp = hp;
        }

        public int getAttack() {
            return attack;
        }

        public void setAttack(int attack) {
            this.attack = attack;
        }

        public int getDefense() {
            return defense;
        }

        public void setDefense(int defense) {
            this.defense = defense;
        }

        public int getSpecialAttack() {
            return specialAttack;
        }

        public void setSpecialAttack(int specialAttack) {
            this.specialAttack = specialAttack;
        }

        public int getSpecialDefense() {
            return specialDefense;
        }

        public void setSpecialDefense(int specialDefense) {
            this.specialDefense = specialDefense;
        }

        public int getSpeed() {
            return speed;
        }

        public void setSpeed(int speed) {
            this.speed = speed;
        }
    }

    public static class Builder {
        private final Pokemon pokemon;

        public Builder(String name, int level) {
            pokemon = new Pokemon(name, level);
        }

        public Builder withType(PokemonType primary, PokemonType secondary) {
            pokemon.setPrimaryType(primary);
            pokemon.setSecondaryType(secondary);
            return this;
        }

        public Builder withStats(int hp, int attack, int defense, int spAtk, int spDef, int speed) {
            pokemon.getStats().setHp(hp);
            pokemon.getStats().setAttack(attack);
            pokemon.getStats().setDefense(defense);
            pokemon.getStats().setSpecialAttack(spAtk);
            pokemon.getStats().setSpecialDefense(spDef);
            pokemon.getStats().setSpeed(speed);
            pokemon.setCurrentHp(hp);
            return this;
        }

        public Builder withMoves(List<Move> moves) {
            pokemon.setMoves(new ArrayList<>(moves));
            return this;
        }

        public Builder withStatus(Status status) {
            pokemon.setStatus(status);
            return this;
        }

        public Pokemon build() {
            if (pokemon.getMoves().isEmpty()) {
            }
            return pokemon;
        }
    }
}
