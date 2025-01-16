package io.github.pokemeetup.multiplayer.server.entity;

import io.github.pokemeetup.multiplayer.server.entity.AIController;
import io.github.pokemeetup.multiplayer.server.entity.CreatureEntity;

import java.util.Random;
public class SimpleAIController implements AIController {
    private final CreatureEntity entity;
    private float decisionTimer = 0f;
    private static final float DECISION_INTERVAL = 2f;
    private final Random random = new Random();

    public SimpleAIController(CreatureEntity entity) {
        this.entity = entity;
    }

    @Override
    public void update() {
        decisionTimer += 0.1f;
        if (decisionTimer >= DECISION_INTERVAL) {
            decisionTimer = 0;
            // Random movement decision
            entity.getVelocity().set(
                random.nextFloat() * 2 - 1,
                random.nextFloat() * 2 - 1
            ).nor();
        }
    }
}
