package io.github.pokemeetup.multiplayer.server.entity;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

import java.util.UUID;

public abstract class Entity {
    private final UUID id;
    protected Vector2 position;
    protected Vector2 velocity;
    protected EntityType type;
    protected boolean isDead;
    protected float width;
    protected float height;

    protected Entity(EntityType type, float x, float y) {
        this.id = UUID.randomUUID();
        this.type = type;
        this.position = new Vector2(x, y);
        this.velocity = new Vector2();
        this.isDead = false;
    }   protected Entity() {
        this.id = UUID.randomUUID();
    }

    public abstract void update(float deltaTime);
    public abstract void handleCollision();
    public abstract void handleCollision(Entity other);

    public UUID getId() { return id; }
    public Vector2 getPosition() { return position; }
    public Vector2 getVelocity() { return velocity; }
    public EntityType getType() { return type; }
    public boolean isDead() { return isDead; }
    public Rectangle getBounds() {
        return new Rectangle(position.x, position.y, width, height);
    }
}
