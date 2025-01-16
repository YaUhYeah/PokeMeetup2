package io.github.pokemeetup.multiplayer.server.entity;

import io.github.pokemeetup.multiplayer.ServerPlayer;
import io.github.pokemeetup.multiplayer.server.entity.Entity;
import io.github.pokemeetup.multiplayer.server.entity.EntityType;

public class PokeballEntity extends Entity {
    public PokeballEntity(float x, float y) {
        super(EntityType.POKEBALL, x, y);
        this.width = 16;
        this.height = 16;
    }

    @Override
    public void update(float deltaTime) {
        // Update position based on velocity
        position.add(velocity.x * deltaTime, velocity.y * deltaTime);
    }

    public PokeballEntity() {
    }

    @Override
    public void handleCollision() {
        // Stop movement on collision
        velocity.setZero();
    }

    @Override
    public void handleCollision(Entity other) {
        if (other != null && other.getType() != EntityType.PLAYER) {
            isDead = true; // Mark for removal when picked up
        }
    }
}
