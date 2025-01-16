package io.github.pokemeetup.multiplayer;

import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.utils.GameLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ServerPlayer {
    private final String username;
    private final UUID uuid;
    private final PlayerData playerData;
    private final Inventory inventory;
    private final Object inventoryLock = new Object();
    private final Object positionLock = new Object();
    private WorldObject choppingObject;

    public ServerPlayer(String username, PlayerData playerData) {
        this.username = username;
        this.uuid = UUID.nameUUIDFromBytes(username.getBytes());
        this.playerData = playerData;

        // Initialize inventory from playerData
        this.inventory = new Inventory();
        if (playerData.getInventoryItems() != null) {
            this.inventory.setAllItems(playerData.getInventoryItems());
        }

        GameLogger.info("Created ServerPlayer: " + username + " (UUID: " + uuid + ") " +
            "at position (" + playerData.getX() + ", " + playerData.getY() + ")");
    }

    public WorldObject getChoppingObject() {
        return choppingObject;
    }

    public void setChoppingObject(WorldObject object) {
        this.choppingObject = object;
    }

    public UUID getUUID() {
        return uuid;
    }

    public void updateInventory(ItemData[] inventoryItems) {
        synchronized (inventoryLock) {
            try {
                if (inventoryItems != null) {
                    List<ItemData> validatedInventoryItems = validateItems(inventoryItems);
                    inventory.setAllItems(validatedInventoryItems);
                    playerData.setInventoryItems(validatedInventoryItems);
                }

                GameLogger.info("Updated inventory for " + username);
            } catch (Exception e) {
                GameLogger.error("Failed to update inventory for " + username + ": " + e.getMessage());
            }
        }
    }

    private List<ItemData> validateItems(ItemData[] items) {
        List<ItemData> validatedItems = new ArrayList<>();
        for (ItemData item : items) {
            if (item != null) {
                if (item.getUuid() == null) {
                    item.setUuid(UUID.randomUUID());
                }
                if (item.getCount() <= 0 || item.getCount() > 64) {
                    item.setCount(1);
                }
                validatedItems.add(item);
            } else {
                validatedItems.add(null);
            }
        }
        return validatedItems;
    }

    public PlayerData getData() {
        synchronized (inventoryLock) {
            playerData.setInventoryItems(inventory.getAllItems());
        }
        return playerData;
    }

    public void updatePosition(float x, float y, String direction, boolean isMoving) {
        synchronized (positionLock) {
            playerData.setX(x);
            playerData.setY(y);
            playerData.setDirection(direction);
            playerData.setMoving(isMoving);
        }
        GameLogger.info("Player " + username + " moved to (" +
            (x / World.TILE_SIZE) + ", " + (y / World.TILE_SIZE) +
            ") facing " + direction);
    }

    public int getTileX() {
        return (int) (playerData.getX() / World.TILE_SIZE);
    }

    public int getTileY() {
        return (int) (playerData.getY() / World.TILE_SIZE);
    }

    public Vector2 getPosition() {
        synchronized (positionLock) {
            return new Vector2(playerData.getX(), playerData.getY());
        }
    }

    public List<ItemData> getInventoryItems() {
        synchronized (inventoryLock) {
            return inventory.getAllItems();
        }
    }

    public String getUsername() {
        return username;
    }

    public String getDirection() {
        synchronized (positionLock) {
            return playerData.getDirection();
        }
    }

    public boolean isMoving() {
        synchronized (positionLock) {
            return playerData.isMoving();
        }
    }

    public boolean isRunning() {
        return playerData.isWantsToRun();
    }

    public void setRunning(boolean running) {
        playerData.setWantsToRun(running);
    }

    public Inventory getInventory() {
        return inventory;
    }
}
