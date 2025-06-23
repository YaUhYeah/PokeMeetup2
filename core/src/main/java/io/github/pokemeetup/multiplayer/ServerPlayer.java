// ... inside file: src/main/java/io/github/pokemeetup/multiplayer/ServerPlayer.java

package io.github.pokemeetup.multiplayer;

import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.data.PokemonData;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager; // Added import
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.WorldObject;
import io.github.pokemeetup.utils.GameLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ServerPlayer {
    private final String username;
    private final UUID uuid;
    private final Inventory inventory;
    private final List<PokemonData> partyPokemon;
    private final Object inventoryLock = new Object();
    private final Object positionLock = new Object();
    private final Object dataLock = new Object();
    private PlayerData playerData;
    private WorldObject choppingObject;
    private PlaceableBlock breakingBlock; // This field was added in the previous step

    public ServerPlayer(String username, PlayerData playerData) {
        List<PokemonData> partyPokemon1;
        this.username = username;
        this.uuid = UUID.nameUUIDFromBytes(username.getBytes());
        this.playerData = playerData;

        this.inventory = new Inventory();
        partyPokemon1 = new ArrayList<>();
        if (playerData.getInventoryItems() != null) {
            this.inventory.setAllItems(playerData.getInventoryItems());
        }
        if (playerData.getPartyPokemon() != null) {
            partyPokemon1 = playerData.getPartyPokemon();
        }

        this.partyPokemon = partyPokemon1;
        GameLogger.info("Created ServerPlayer: " + username + " (UUID: " + uuid + ") " +
            "at position (" + playerData.getX() + ", " + playerData.getY() + ")");
    }

    public WorldObject getChoppingObject() {
        return choppingObject;
    }

    public void setChoppingObject(WorldObject object) {
        this.choppingObject = object;
    }

    public PlaceableBlock getBreakingBlock() {
        return breakingBlock;
    }

    public void setBreakingBlock(PlaceableBlock block) {
        this.breakingBlock = block;
    }

    /**
     * Checks if the player has a functional wooden axe in their inventory.
     * @return true if a non-broken axe is found, false otherwise.
     */
    public boolean hasAxe() {
        synchronized (inventoryLock) {
            for (ItemData item : inventory.getAllItems()) {
                if (item != null && item.getItemId().equals(ItemManager.ItemIDs.WOODEN_AXE)) {
                    // Also check if the axe has durability left
                    if (item.getDurability() > 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public UUID getUUID() {
        return uuid;
    }

    public PlayerData getData() {
        synchronized (dataLock) {
            // Create a fresh copy with current state
            PlayerData currentData = playerData.copy();

            // Ensure inventory is synced
            synchronized (inventoryLock) {
                currentData.setInventoryItems(new ArrayList<>(inventory.getAllItems()));
            }

            // Ensure pokemon party is synced
            synchronized (partyPokemon) {
                currentData.setPartyPokemon(new ArrayList<>(partyPokemon));
            }

            return currentData;
        }
    }

    public void setData(PlayerData newData) {
        synchronized (dataLock) {
            if (newData == null) {
                GameLogger.error("Attempted to set null PlayerData for " + username);
                return;
            }

            try {
                // Create deep copy and validate
                PlayerData validatedData = newData.copy();

                this.playerData = validatedData;

                // Update inventory atomically
                synchronized (inventoryLock) {
                    if (validatedData.getInventoryItems() != null) {
                        this.inventory.clear();
                        for (ItemData item : validatedData.getInventoryItems()) {
                            if (item != null) {
                                this.inventory.addItem(item.copy());
                            }
                        }
                    }
                }

                // Update party atomically
                synchronized (partyPokemon) {
                    this.partyPokemon.clear();
                    if (validatedData.getPartyPokemon() != null) {
                        for (PokemonData pokemon : validatedData.getPartyPokemon()) {
                            if (pokemon != null) {
                                this.partyPokemon.add(pokemon.copy());
                            }
                        }
                    }
                }

            } catch (Exception e) {
                GameLogger.error("Failed to update player data for " + username + ": " + e.getMessage());
            }
        }
    }

    public void setPosition(float x, float y) {
        synchronized (positionLock) {
            playerData.setX(x);
            playerData.setY(y);
        }
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

    public void setDirection(String direction) {
        synchronized (positionLock) {
            playerData.setDirection(direction);
        }
    }

    public boolean isMoving() {
        synchronized (positionLock) {
            return playerData.isMoving();
        }
    }

    public void setMoving(boolean moving) {
        synchronized (positionLock) {
            playerData.setMoving(moving);
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
