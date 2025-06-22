
package io.github.pokemeetup.system.gameplay.overworld.entityai;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.entityai.behaviors.*;
import io.github.pokemeetup.utils.GameLogger;

import java.util.*;

public class PokemonAI {
    private static final float BASE_DETECTION_RANGE = 4.0f * World.TILE_SIZE;
    private static final float UPDATE_INTERVAL = 0.1f;

    private final WildPokemon pokemon;
    private final Set<PokemonPersonalityTrait> personalityTraits;
    private final List<PokemonBehavior> behaviors;
    private final Map<String, Float> behaviorCooldowns;
    private final Vector2 territoryCenter;
    private final float territoryRadius;

    private float updateTimer = 0f;
    private boolean isPaused = false;
    private AIState currentState = AIState.IDLE;
    private PokemonBehavior activeBehavior;
    private UUID packLeaderId;
    private Set<UUID> packMembers = new HashSet<>();

    // Movement state
    private float stateTimer = 0f;
    private Vector2 patrolTarget;
    private List<Vector2> patrolRoute = new ArrayList<>();
    private int currentPatrolIndex = 0;

    public PokemonAI(WildPokemon pokemon) {
        this.pokemon = pokemon;
        this.personalityTraits = generatePersonalityTraits();
        this.behaviors = new ArrayList<>();
        this.behaviorCooldowns = new HashMap<>();
        this.territoryCenter = new Vector2(pokemon.getX(), pokemon.getY());
        this.territoryRadius = calculateTerritoryRadius();

        initializeBehaviors();
        setupPatrolRoute();

        GameLogger.info("Created enhanced AI for " + pokemon.getName() +
            " with traits: " + personalityTraits.toString());
    }

    private Set<PokemonPersonalityTrait> generatePersonalityTraits() {
        Set<PokemonPersonalityTrait> traits = new HashSet<>();

        // Primary personality (always has one)
        PokemonPersonalityTrait[] primaryTraits = {
            PokemonPersonalityTrait.AGGRESSIVE, PokemonPersonalityTrait.PASSIVE,
            PokemonPersonalityTrait.CURIOUS, PokemonPersonalityTrait.TIMID,
            PokemonPersonalityTrait.TERRITORIAL, PokemonPersonalityTrait.LAZY
        };
        traits.add(primaryTraits[MathUtils.random(primaryTraits.length - 1)]);

        // Social behavior (70% chance)
        if (MathUtils.random() < 0.7f) {
            PokemonPersonalityTrait[] socialTraits = {
                PokemonPersonalityTrait.PACK_LEADER, PokemonPersonalityTrait.FOLLOWER,
                PokemonPersonalityTrait.SOLITARY
            };
            traits.add(socialTraits[MathUtils.random(socialTraits.length - 1)]);
        }

        // Time preference (30% chance)
        if (MathUtils.random() < 0.3f) {
            traits.add(MathUtils.randomBoolean() ?
                PokemonPersonalityTrait.NOCTURNAL : PokemonPersonalityTrait.DIURNAL);
        }

        // Species-specific traits
        addSpeciesSpecificTraits(traits);

        return traits;
    }

    private void addSpeciesSpecificTraits(Set<PokemonPersonalityTrait> traits) {
        String species = pokemon.getName().toLowerCase();

        switch (species) {
            case "growlithe":
            case "arcanine":
            case "manectric":
                traits.add(PokemonPersonalityTrait.PROTECTIVE);
                break;
            case "snorlax":
            case "sloth":
                traits.add(PokemonPersonalityTrait.LAZY);
                break;
            case "primeape":
            case "mankey":
                traits.add(PokemonPersonalityTrait.AGGRESSIVE);
                break;
            case "eevee":
            case "skitty":
                traits.add(PokemonPersonalityTrait.CURIOUS);
                break;
        }
    }

    private void initializeBehaviors() {
        behaviors.add(new IdleBehavior(pokemon, this));
        behaviors.add(new WanderBehavior(pokemon, this));
        behaviors.add(new FleeBehavior(pokemon, this));

        // Conditional behaviors based on personality
        if (hasPersonalityTrait(PokemonPersonalityTrait.AGGRESSIVE)) {
            behaviors.add(new ApproachPlayerBehavior(pokemon, this));
            behaviors.add(new TerritorialBehavior(pokemon, this));
        }

        if (hasPersonalityTrait(PokemonPersonalityTrait.CURIOUS)) {
            behaviors.add(new InvestigateBehavior(pokemon, this));
        }

        if (hasPersonalityTrait(PokemonPersonalityTrait.TERRITORIAL)) {
            behaviors.add(new PatrolBehavior(pokemon, this));
            behaviors.add(new DefendTerritoryBehavior(pokemon, this));
        }

        if (hasPersonalityTrait(PokemonPersonalityTrait.PACK_LEADER)) {
            behaviors.add(new PackLeaderBehavior(pokemon, this));
        }

        if (hasPersonalityTrait(PokemonPersonalityTrait.FOLLOWER)) {
            behaviors.add(new FollowPackBehavior(pokemon, this));
        }

        // Sort behaviors by priority
        behaviors.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
    }

    private void setupPatrolRoute() {
        if (!hasPersonalityTrait(PokemonPersonalityTrait.TERRITORIAL)) return;

        Vector2 center = territoryCenter;
        float radius = territoryRadius * 0.7f; // Patrol within territory

        // Create a circular patrol route
        int numPoints = MathUtils.random(3, 6);
        for (int i = 0; i < numPoints; i++) {
            float angle = (i * MathUtils.PI2) / numPoints;
            float x = center.x + MathUtils.cos(angle) * radius;
            float y = center.y + MathUtils.sin(angle) * radius;

            // Snap to tile grid
            int tileX = Math.round(x / World.TILE_SIZE);
            int tileY = Math.round(y / World.TILE_SIZE);

            patrolRoute.add(new Vector2(tileX * World.TILE_SIZE, tileY * World.TILE_SIZE));
        }
    }

    public void update(float delta, World world) {
        if (world == null || pokemon == null || isPaused) return;

        updateTimer += delta;
        if (updateTimer < UPDATE_INTERVAL) return;
        updateTimer = 0f;

        stateTimer += delta;
        updateBehaviorCooldowns(delta);

        // Handle species-specific special abilities
        handleSpecialAbilities(world);

        // Find and execute highest priority behavior
        PokemonBehavior newBehavior = selectBehavior();
        if (newBehavior != activeBehavior) {
            if (activeBehavior != null) {
                GameLogger.info(pokemon.getName() + " switching from " +
                    activeBehavior.getName() + " to " + newBehavior.getName());
            }
            activeBehavior = newBehavior;
            stateTimer = 0f;
        }

        if (activeBehavior != null) {
            activeBehavior.execute(delta);
        }
    }

    private PokemonBehavior selectBehavior() {
        for (PokemonBehavior behavior : behaviors) {
            if (behavior.canExecute() && !isOnCooldown(behavior.getName())) {
                return behavior;
            }
        }

        // Default to idle if no other behavior can execute
        return behaviors.stream()
            .filter(b -> b instanceof IdleBehavior)
            .findFirst()
            .orElse(null);
    }

    private void updateBehaviorCooldowns(float delta) {
        behaviorCooldowns.replaceAll((behavior, cooldown) -> Math.max(0f, cooldown - delta));
        behaviorCooldowns.entrySet().removeIf(entry -> entry.getValue() <= 0f);
    }

    private void handleSpecialAbilities(World world) {
        if (pokemon.isMoving()) return;

        String species = pokemon.getName().toLowerCase();
        float abilityChance = 0.01f;

        if (MathUtils.random() > abilityChance) return;

        switch (species) {
            case "abra":
                performTeleport(world);
                break;
            case "diglett":
            case "dugtrio":
                performBurrow(world);
                break;
            case "haunter":
            case "gastly":
                performPhase(world);
                break;
            case "pikachu":
            case "raichu":
                performElectricDisplay();
                break;
        }
    }

    private void performTeleport(World world) {
        int currentTileX = (int)(pokemon.getX() / World.TILE_SIZE);
        int currentTileY = (int)(pokemon.getY() / World.TILE_SIZE);

        int maxDistance = hasPersonalityTrait(PokemonPersonalityTrait.TERRITORIAL) ?
            (int)(territoryRadius / World.TILE_SIZE) : 8;

        for (int attempts = 0; attempts < 10; attempts++) {
            int distance = MathUtils.random(3, maxDistance);
            float angle = MathUtils.random(MathUtils.PI2);
            int targetTileX = currentTileX + Math.round(MathUtils.cos(angle) * distance);
            int targetTileY = currentTileY + Math.round(MathUtils.sin(angle) * distance);

            if (world.isPassable(targetTileX, targetTileY)) {
                GameLogger.info(pokemon.getName() + " teleports to (" + targetTileX + "," + targetTileY + ")");
                pokemon.setX(targetTileX * World.TILE_SIZE);
                pokemon.setY(targetTileY * World.TILE_SIZE);
                enterIdleState();
                setCooldown("teleport", 30f);
                break;
            }
        }
    }

    private void performBurrow(World world) {
        // Similar to teleport but shorter range and different flavor
        performTeleport(world);
        setCooldown("burrow", 20f);
    }

    private void performPhase(World world) {
        // Ghost types can "phase" through one obstacle
        int currentTileX = (int)(pokemon.getX() / World.TILE_SIZE);
        int currentTileY = (int)(pokemon.getY() / World.TILE_SIZE);

        String[] directions = {"up", "down", "left", "right"};
        String direction = directions[MathUtils.random(directions.length - 1)];

        int targetTileX = currentTileX;
        int targetTileY = currentTileY;

        switch (direction) {
            case "up": targetTileY += 2; break;
            case "down": targetTileY -= 2; break;
            case "left": targetTileX -= 2; break;
            case "right": targetTileX += 2; break;
        }

        if (world.isPassable(targetTileX, targetTileY)) {
            GameLogger.info(pokemon.getName() + " phases through obstacles");
            pokemon.setX(targetTileX * World.TILE_SIZE);
            pokemon.setY(targetTileY * World.TILE_SIZE);
            enterIdleState();
            setCooldown("phase", 25f);
        }
    }

    private void performElectricDisplay() {
        // Visual/audio effect only - could trigger particle effects
        GameLogger.info(pokemon.getName() + " creates an electric display");
        setCooldown("electric_display", 15f);
    }

    // Getters and utility methods
    public boolean hasPersonalityTrait(PokemonPersonalityTrait trait) {
        return personalityTraits.contains(trait);
    }

    public float getDetectionRange() {
        float multiplier = personalityTraits.stream()
            .map(trait -> trait.detectionRangeMultiplier)
            .max(Float::compareTo)
            .orElse(1.0f);
        return BASE_DETECTION_RANGE * multiplier;
    }

    public float getFleeThreshold() {
        return personalityTraits.stream()
            .map(trait -> trait.fleeThreshold)
            .min(Float::compareTo)
            .orElse(1.0f) * World.TILE_SIZE;
    }

    public float getApproachFactor() {
        return personalityTraits.stream()
            .map(trait -> trait.approachFactor)
            .max(Float::compareTo)
            .orElse(1.0f);
    }

    public Vector2 getTerritoryCenter() { return territoryCenter; }
    public float getTerritoryRadius() { return territoryRadius; }
    public List<Vector2> getPatrolRoute() { return patrolRoute; }
    public Vector2 getCurrentPatrolTarget() { return patrolTarget; }
    public int getCurrentPatrolIndex() { return currentPatrolIndex; }

    public void setCurrentPatrolIndex(int index) {
        currentPatrolIndex = index;
        if (index >= 0 && index < patrolRoute.size()) {
            patrolTarget = patrolRoute.get(index);
        }
    }

    public void setCooldown(String behavior, float seconds) {
        behaviorCooldowns.put(behavior, seconds);
    }

    public boolean isOnCooldown(String behavior) {
        return behaviorCooldowns.containsKey(behavior) && behaviorCooldowns.get(behavior) > 0f;
    }

    public void enterIdleState() {
        currentState = AIState.IDLE;
        stateTimer = 0f;
        pokemon.setMoving(false);
    }

    public void setPaused(boolean paused) { this.isPaused = paused; }
    public AIState getCurrentState() { return currentState; }
    public void setCurrentState(AIState state) { this.currentState = state; }
    public float getStateTimer() { return stateTimer; }

    // Pack management
    public void setPackLeader(UUID leaderId) { this.packLeaderId = leaderId; }
    public UUID getPackLeaderId() { return packLeaderId; }
    public void addPackMember(UUID memberId) { packMembers.add(memberId); }
    public void removePackMember(UUID memberId) { packMembers.remove(memberId); }
    public Set<UUID> getPackMembers() { return packMembers; }

    private float calculateTerritoryRadius() {
        if (hasPersonalityTrait(PokemonPersonalityTrait.TERRITORIAL)) {
            return MathUtils.random(4, 8) * World.TILE_SIZE;
        }
        return 2 * World.TILE_SIZE; // Small default territory
    }

    public enum AIState {
        IDLE, WANDERING, FLEEING, APPROACHING, PATROLLING, INVESTIGATING, FOLLOWING
    }
}
