package io.github.pokemeetup.multiplayer;

import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.data.PokemonData;
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
    private PlayerData playerData;
    private final Inventory inventory;
    private final List<PokemonData> partyPokemon;
    private final Object inventoryLock = new Object();
    private final Object positionLock = new Object();
    private WorldObject choppingObject;

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
        if (playerData.getPartyPokemon()!= null) {
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

    public UUID getUUID() {
        return uuid;
    }
    private final Object dataLock = new Object();


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
                if (validatedData.validateAndRepairState()) {
                    GameLogger.info("Repaired player data during update for: " + username);
                }

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

    public int getTileX() {
        return (int) (playerData.getX());
    }

    public int getTileY() {
        return (int) (playerData.getY() );
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
