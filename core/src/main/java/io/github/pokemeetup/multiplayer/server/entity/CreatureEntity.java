package io.github.pokemeetup.multiplayer.server.entity;

import com.badlogic.gdx.math.Vector2;

public class CreatureEntity extends Entity {
    private AIController aiController;
    private float movementSpeed = 100f;
    private float updateTimer = 0f;
    private static final float UPDATE_INTERVAL = 0.1f;

    public CreatureEntity(float x, float y) {
        super(EntityType.CREATURE, x, y);
        this.width = 32;
        this.height = 32;
        this.aiController = new SimpleAIController(this);
    }

    public CreatureEntity(AIController aiController) {
        this.aiController = aiController;
    }

    @Override
    public void update(float deltaTime) {
        // Update AI
        updateTimer += deltaTime;
        if (updateTimer >= UPDATE_INTERVAL) {
            updateTimer = 0;
            aiController.update();
        }

        // Update position
        position.add(
            velocity.x * movementSpeed * deltaTime,
            velocity.y * movementSpeed * deltaTime
        );
    }

    @Override
    public void handleCollision() {
        // Bounce off walls
        velocity.scl(-1);
    }

    @Override
    public void handleCollision(Entity other) {
        if (other instanceof CreatureEntity) {
            // Simple bounce off other creatures
            Vector2 normal = new Vector2(
                position.x - other.position.x,
                position.y - other.position.y
            ).nor();
            velocity.set(normal);
        }
    }
}
