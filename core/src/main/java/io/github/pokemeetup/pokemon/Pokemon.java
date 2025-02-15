package io.github.pokemeetup.pokemon;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.pokemon.attacks.Move;
import io.github.pokemeetup.pokemon.data.PokemonDatabase;
import io.github.pokemeetup.system.gameplay.PokemonAnimations;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

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
        // Save species base stats for later use
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

    protected Pokemon(boolean noTexture) {
        // We assume 'noTexture' is true
        this.uuid = UUID.randomUUID();
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

    public static Pokemon createServerPokemon(String name, int level) {
        Pokemon p = new Pokemon(true);
        p.name = name;
        p.level = level;
        // Optionally, if you want to load base stats from your database template:
        PokemonDatabase.PokemonTemplate template = PokemonDatabase.getTemplate(name);
        if (template != null) {
            p.growthRate = template.growthRate != null ? template.growthRate : "Medium Fast";
            // You might assign speciesBase* values here if needed.
        }
        // Then calculate stats:
        p.calculateStats();
        p.currentHp = p.stats.getHp();
        return p;
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
        this.confusedTurns = 0;
    }

    public boolean hasStatus() {
        return status != Status.NONE;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status newStatus) {
        if (newStatus == null) {
            GameLogger.error("Attempted to set a null status on " + name + "; ignoring.");
            return;
        }
        // Some types are immune to certain statuses
        if (newStatus == Status.PARALYZED &&
            (primaryType == PokemonType.ELECTRIC || secondaryType == PokemonType.ELECTRIC)) {
            return;
        }
        if (newStatus == Status.POISONED &&
            (primaryType == PokemonType.POISON || secondaryType == PokemonType.POISON ||
                primaryType == PokemonType.STEEL || secondaryType == PokemonType.STEEL)) {
            return;
        }
        if (newStatus == Status.BURNED &&
            (primaryType == PokemonType.FIRE || secondaryType == PokemonType.FIRE)) {
            return;
        }
        if (newStatus == Status.FROZEN &&
            (primaryType == PokemonType.ICE || secondaryType == PokemonType.ICE)) {
            return;
        }

        this.status = newStatus;

        // Initialize status-specific counters
        switch (newStatus) {
            case ASLEEP:
                sleepTurns = new Random().nextInt(3) + 2; // Sleep lasts 2-4 turns
                break;
            case BADLY_POISONED:
                toxicCounter = 1;
                break;
            default:
                break;
        }
    }

    public void cureStatus() {
        this.status = Status.NONE;
        this.sleepTurns = 0;
        this.toxicCounter = 1;
        this.flinched = false;
        this.confusedTurns = 0;
    }

    public boolean canAttack() {
        if (currentHp <= 0) {
            return false;
        }

        if (flinched) {
            flinched = false; // Reset flinch
            return false;
        }

        // Handle confusion
        if (confusedTurns > 0) {
            confusedTurns--;
            if (new Random().nextFloat() < 0.33f) { // 33% chance to hurt itself
                float damage = calculateStat(40, 0, 0); // Base power of confusion self-hit
                currentHp = Math.max(0, currentHp - damage);
                return false;
            }
        }

        // Handle status effects
        switch (status) {
            case PARALYZED:
                if (new Random().nextFloat() < 0.25f) { // 25% chance to be fully paralyzed
                    return false;
                }
                break;

            case FROZEN:
                if (new Random().nextFloat() < 0.20f) { // 20% chance to thaw each turn
                    status = Status.NONE;
                    return true;
                }
                return false;

            case ASLEEP:
                sleepTurns--;
                if (sleepTurns <= 0) {
                    status = Status.NONE;
                    return true;
                }
                return false;

            default:
                break;
        }

        return true;
    }
    public void applyEndOfTurnEffects() {
        switch (status) {
            case POISONED:
                currentHp = Math.max(0, currentHp - (stats.getHp() / 8));
                break;
            case BADLY_POISONED:
                currentHp = Math.max(0, currentHp - ((float) (stats.getHp() * toxicCounter) / 16));
                toxicCounter = Math.min(toxicCounter + 1, 15);
                break;
            case BURNED:
                currentHp = Math.max(0, currentHp - ((float) stats.getHp() / 16));
                break;
            case LEECH_SEED:
                // For example, take 1/8th max HP damage each turn.
                int leechDamage = stats.getHp() / 8;
                currentHp = Math.max(0, currentHp - leechDamage);
                // Optionally, add healing to the attacker.
                break;
            default:
                break;
        }
        if (currentHp <= 0) {
            status = Status.FAINTED;
        }
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

            // Update experience for next level check
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

        // Use the ChatSystem for a level-up message.
        GameContext.get().getChatSystem().addSystemMessage(getName() + " leveled up to " + level + "!");

        // Save old stats for comparison...
        int oldHp = stats.getHp();
        int oldAttack = stats.getAttack();
        int oldDefense = stats.getDefense();
        int oldSpAtk = stats.getSpecialAttack();
        int oldSpDef = stats.getSpecialDefense();
        int oldSpeed = stats.getSpeed();

        // Recalculate stats...
        calculateStats();

        // Optionally, show stat increases (you might log these or add additional system messages)
        showStatIncrease("HP", oldHp, stats.getHp());
        showStatIncrease("Attack", oldAttack, stats.getAttack());
        showStatIncrease("Defense", oldDefense, stats.getDefense());
        showStatIncrease("Sp. Atk", oldSpAtk, stats.getSpecialAttack());
        showStatIncrease("Sp. Def", oldSpDef, stats.getSpecialDefense());
        showStatIncrease("Speed", oldSpeed, stats.getSpeed());

        // Heal the Pokémon upon level-up
        currentHp = stats.getHp();

        // Check for new moves
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
        // Update the timer
        frameTimer += delta;
        if (frameTimer >= frameDuration) {
            frameTimer = 0;
            currentIconFrame = (currentIconFrame + 1) % iconFrames.length; // Toggle between frames
        }
        return iconFrames[currentIconFrame];
    }

    private void loadIcons(TextureAtlas atlas) {
        String baseName = name.toUpperCase();
//        GameLogger.info("Loading sprites for: " + baseName);
        // Load battle and icon sprites
        iconSprite = atlas.findRegion(baseName + "_icon");
        // Load overworld sprite sheet
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
        // Get the list of move entries from the Pokémon Database
        List<PokemonDatabase.MoveEntry> moveEntries = PokemonDatabase.getTemplate(name).moves;
        for (PokemonDatabase.MoveEntry entry : moveEntries) {
            if (entry.level == level) {
                // Learn the new move:
                Move newMove = PokemonDatabase.getMoveByName(entry.name);
                if (newMove != null) {
                    Move clonedMove = PokemonDatabase.cloneMove(newMove);
                    // If fewer than 4 moves, simply add the new move.
                    if (moves.size() < 4) {
                        moves.add(clonedMove);
                        GameLogger.info(name + " learned " + entry.name + "!");
                        if (GameContext.get().getBattleTable() != null) {
                            GameContext.get().getBattleTable().displayMessage(name + " learned " + entry.name + "!");
                        }
                    } else {
                        // Otherwise, prompt the user to choose which move to replace.
                        if (GameContext.get().getBattleTable() != null) {
                            GameContext.get().getBattleTable().showMoveReplacementDialog(clonedMove);
                        } else {
                            // Fallback: auto–replace the first move.
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
        // Load battle and icon sprites
        frontSprite = atlas.findRegion(baseName + "_front");
        frontShinySprite = atlas.findRegion(baseName + "_front_shiny");
        // Load overworld sprite sheet

    }

    private void loadBack(TextureAtlas atlas) {
        String baseName = name.toUpperCase();
        // Load battle and icon sprites
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
//            GameLogger.info("Rendering Pokémon: " + name + " at position: " + position);
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

    // Getters and setters
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
        // Example uses a nature modifier with ±10% random variation.
        float natureModifier = 1.0f + (new Random().nextFloat() * 0.2f - 0.1f);
        return (int) (((2 * base + iv + (float) ev / 4) * level / 100f + 5) * natureModifier);
    }

    public void calculateStats() {
        stats.setHp(((2 * speciesBaseHp + stats.ivs[0] + stats.evs[0] / 4) * level / 100) + level + 10);
        stats.setAttack(calculateStat(speciesBaseAttack, stats.ivs[1], stats.evs[1]));
        stats.setDefense(calculateStat(speciesBaseDefense, stats.ivs[2], stats.evs[2]));
        stats.setSpecialAttack(calculateStat(speciesBaseSpAtk, stats.ivs[3], stats.evs[3]));
        stats.setSpecialDefense(calculateStat(speciesBaseSpDef, stats.ivs[4], stats.evs[4]));
        stats.setSpeed(calculateStat(speciesBaseSpeed, stats.ivs[5], stats.evs[5]));

        // For example, if paralyzed, halve the speed
        if (status == Status.PARALYZED) {
            stats.setSpeed(stats.getSpeed() / 2);
        }
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
