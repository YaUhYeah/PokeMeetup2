package io.github.pokemeetup.system.gameplay.overworld.entityai.behaviors;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.pokemon.WildPokemon;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.entityai.PokemonAI;
public class FleeBehavior implements PokemonBehavior {
    private static final float FLEE_SPEED_MULTIPLIER = 0.5f; // Faster movement
    private static final int MAX_FLEE_STEPS = 32;

    private final WildPokemon pokemon;
    private final PokemonAI ai;
    private int fleeStepsRemaining = 0;
    private String fleeDirection;
    private boolean hasPlayedFleeSound = false; // Track if we've played the flee sound

    public FleeBehavior(WildPokemon pokemon, PokemonAI ai) {
        this.pokemon = pokemon;
        this.ai = ai;
    }

    @Override
    public void execute(float delta) {
        if (!pokemon.isMoving()) {
            if (fleeStepsRemaining <= 0) {
                initiateFlee();
            } else {
                continueFleeMovement();
            }
        }
    }

    private void initiateFlee() {
        Vector2 playerPos = ai.getSafePlayerPosition();
        if (playerPos == null) {
            fleeStepsRemaining = 0;
            hasPlayedFleeSound = false;
            return;
        }

        // Play flee sound and log message when first starting to flee
        if (!hasPlayedFleeSound) {
            try {
                io.github.pokemeetup.audio.AudioManager.getInstance().playSound(
                    io.github.pokemeetup.audio.AudioManager.SoundEffect.RUN_AWAY
                );
                io.github.pokemeetup.utils.GameLogger.info(pokemon.getName() + " is fleeing from the player!");
                hasPlayedFleeSound = true;
            } catch (Exception e) {
                // Silently fail if audio isn't available
            }
        }

        fleeDirection = calculateFleeDirection(playerPos);
        fleeStepsRemaining = MathUtils.random(2, MAX_FLEE_STEPS);
        continueFleeMovement();
    }

    private void continueFleeMovement() {
        World world = null;
        try {
            world = GameContext.get().getWorld();
        } catch (IllegalStateException e) {
            // Server-side, end flee behavior
            fleeStepsRemaining = 0;
            return;
        }
        if (world == null) return;

        int currentTileX = (int) (pokemon.getX() / World.TILE_SIZE);
        int currentTileY = (int) (pokemon.getY() / World.TILE_SIZE);

        int targetTileX = currentTileX;
        int targetTileY = currentTileY;

        switch (fleeDirection) {
            case "up":
                targetTileY++;
                break;
            case "down":
                targetTileY--;
                break;
            case "left":
                targetTileX--;
                break;
            case "right":
                targetTileX++;
                break;
        }

        if (ai.checkPassable(world, targetTileX, targetTileY)) {
            pokemon.moveToTile(targetTileX, targetTileY, fleeDirection);
            fleeStepsRemaining--;
            ai.setCurrentState(PokemonAI.AIState.FLEEING);
        } else {
            tryAlternativeFleeDirection(world);
        }

        if (fleeStepsRemaining <= 0) {
            ai.setCooldown(getName(), 5.0f);
            hasPlayedFleeSound = false; // Reset for next flee event
        }
    }

    private void tryAlternativeFleeDirection(World world) {
        String[] alternatives = {"up", "down", "left", "right"};
        int currentTileX = (int) (pokemon.getX() / World.TILE_SIZE);
        int currentTileY = (int) (pokemon.getY() / World.TILE_SIZE);

        for (String direction : alternatives) {
            if (direction.equals(fleeDirection)) continue;

            int targetTileX = currentTileX;
            int targetTileY = currentTileY;

            switch (direction) {
                case "up":
                    targetTileY++;
                    break;
                case "down":
                    targetTileY--;
                    break;
                case "left":
                    targetTileX--;
                    break;
                case "right":
                    targetTileX++;
                    break;
            }

            if (ai.checkPassable(world, targetTileX, targetTileY)) {
                pokemon.moveToTile(targetTileX, targetTileY, direction);
                fleeDirection = direction;
                fleeStepsRemaining--;
                return;
            }
        }
        fleeStepsRemaining = 0;
    }

    private String calculateFleeDirection(Vector2 playerPos) {
        int pokemonTileX = (int) (pokemon.getX() / World.TILE_SIZE);
        int pokemonTileY = (int) (pokemon.getY() / World.TILE_SIZE);
        int playerTileX = (int) (playerPos.x / World.TILE_SIZE);
        int playerTileY = (int) (playerPos.y / World.TILE_SIZE);

        int dx = pokemonTileX - playerTileX;
        int dy = pokemonTileY - playerTileY;

        if (Math.abs(dx) >= Math.abs(dy)) {
            return (dx >= 0) ? "right" : "left";
        } else {
            return (dy >= 0) ? "up" : "down";
        }
    }

    @Override
    public boolean canExecute() {
        // Works on both client and server using safe player position
        Vector2 playerPos = ai.getSafePlayerPosition();
        if (playerPos == null) return false;

        float distance = Vector2.dst(pokemon.getX(), pokemon.getY(),
            playerPos.x, playerPos.y);
        return distance < ai.getFleeThreshold();
    }

    @Override
    public int getPriority() {
        return 8; // High priority
    }

    @Override
    public String getName() {
        return "flee";
    }
}
