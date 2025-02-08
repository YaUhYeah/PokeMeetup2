package io.github.pokemeetup.system.data;

import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.PokemonParty;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;

import java.util.*;

public class PlayerData {
    private String username;
    private float x;
    private float y;
    private String direction;
    private boolean isMoving;
    private boolean wantsToRun;
    private List<ItemData> inventoryItems;
    private List<PokemonData> partyPokemon;

    private String characterType = "boy";

    public PlayerData() {
        this.direction = "down";
        this.inventoryItems = new ArrayList<>();
        this.partyPokemon = new ArrayList<>();
    }

    public PlayerData(String username) {
        this();
        this.username = username;
    }

    public void updateFromPlayer(Player player) {
        if (player == null) {
            GameLogger.error("Cannot update from null player");
            return;
        }

        try {
            this.x = player.getX();
            this.y = player.getY();
            this.direction = player.getDirection();
            this.isMoving = player.isMoving();
            this.wantsToRun = player.isRunning();

            // Update the character type from the player instance.
            this.setCharacterType(player.getCharacterType());

            this.inventoryItems = new ArrayList<>(Collections.nCopies(Inventory.INVENTORY_SIZE, null));
            this.partyPokemon = new ArrayList<>(Collections.nCopies(PokemonParty.MAX_PARTY_SIZE, null));

            if (player.getInventory() != null) {
                List<ItemData> items = player.getInventory().getAllItems();
                for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
                    if (i < items.size() && items.get(i) != null) {
                        ItemData itemData = items.get(i);
                        if (validateItemData(itemData)) {
                            this.inventoryItems.set(i, itemData.copy());
                        }
                    }
                }
            }

            if (player.getPokemonParty() != null) {
                List<Pokemon> currentParty = player.getPokemonParty().getParty();
                for (int i = 0; i < PokemonParty.MAX_PARTY_SIZE; i++) {
                    if (i < currentParty.size() && currentParty.get(i) != null) {
                        Pokemon pokemon = currentParty.get(i);
                        try {
                            PokemonData pokemonData = PokemonData.fromPokemon(pokemon);
                            if (pokemonData.verifyIntegrity()) {
                                this.partyPokemon.set(i, pokemonData);
                            }
                        } catch (Exception e) {
                            GameLogger.error("Failed to convert Pokemon at slot " + i + ": " + e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }


    public boolean validateAndRepairState() {
        boolean wasRepaired = false;

        // Validate username
        if (username == null || username.trim().isEmpty()) {
            GameLogger.error("Critical: PlayerData has null/empty username");
            return false;
        }

        // Initialize/repair collections
        if (inventoryItems == null) {
            inventoryItems = new ArrayList<>();
            wasRepaired = true;
        }

        if (partyPokemon == null) {
            partyPokemon = new ArrayList<>();
            wasRepaired = true;
        }

        // Validate position
        if (Float.isNaN(x) || Float.isInfinite(x)) {
            x = 0;
            wasRepaired = true;
        }
        if (Float.isNaN(y) || Float.isInfinite(y)) {
            y = 0;
            wasRepaired = true;
        }

        // Validate direction
        if (direction == null) {
            direction = "down";
            wasRepaired = true;
        }

        if (wasRepaired) {
            GameLogger.info("Repaired PlayerData for: " + username);
        }

        return true;
    }


    /**
     * Applies this saved state to the given player.
     * Note that this implementation always clears the current inventory and Pokémon party.
     */
    public void applyToPlayer(Player player) {
        if (player == null) {
            GameLogger.error("Cannot apply PlayerData to a null player.");
            return;
        }

        // Update basic state (position, direction, movement)
        player.setX(this.x);
        player.setY(this.y);
        player.setDirection(this.direction);
        player.setMoving(this.isMoving);
        player.setRunning(this.wantsToRun);

        // Also update the character type in the Player instance.
        player.setCharacterType(this.characterType);

        if (player.getInventory() != null) {
            player.getInventory().clear();
        }
        if (this.inventoryItems != null) {
            for (ItemData item : this.inventoryItems) {
                if (item != null && item.isValid()) {
                    player.getInventory().addItem(item.copy());
                }
            }
        } else {
            GameLogger.info("No saved inventory items for player: " + username);
        }

        // Always update the Pokémon party: clear the party then add saved Pokémon.
        if (player.getPokemonParty() != null) {
            player.getPokemonParty().clearParty();
        }
        if (this.partyPokemon != null) {
            for (PokemonData pData : this.partyPokemon) {
                if (pData != null && pData.verifyIntegrity()) {
                    Pokemon pokemon = pData.toPokemon();
                    if (pokemon != null) {
                        player.getPokemonParty().addPokemon(pokemon);
                    } else {
                        GameLogger.error("Failed to convert PokemonData to Pokemon for player: " + username);
                    }
                }
            }
        } else {
            GameLogger.info("No saved Pokémon for player: " + username);
        }

        GameLogger.info("Applied saved PlayerData to player: " + username);
    }

    // --- New methods for character type support ---
    public String getCharacterType() {
        return characterType;
    }

    public void setCharacterType(String characterType) {
        this.characterType = characterType;
    }

    public PlayerData copy() {
        PlayerData copy = new PlayerData(this.username);

        copy.setX(this.x);
        copy.setY(this.y);
        copy.setDirection(this.direction);
        copy.setMoving(this.isMoving);
        copy.setWantsToRun(this.wantsToRun);
        copy.setCharacterType(this.characterType);
        if (this.inventoryItems != null) {
            List<ItemData> inventoryCopy = new ArrayList<>();
            for (ItemData item : this.inventoryItems) {
                if (item != null) {
                    inventoryCopy.add(item.copy());
                } else {
                    inventoryCopy.add(null);
                }
            }
            copy.setInventoryItems(inventoryCopy);
        }

        if (this.partyPokemon != null) {
            List<PokemonData> partyCopy = new ArrayList<>();
            for (PokemonData pokemon : this.partyPokemon) {
                if (pokemon != null) {
                    partyCopy.add(pokemon.copy());
                } else {
                    partyCopy.add(null);
                }
            }
            copy.setPartyPokemon(partyCopy);
        }

        return copy;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
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

    public boolean isWantsToRun() {
        return wantsToRun;
    }

    public void setWantsToRun(boolean wantsToRun) {
        this.wantsToRun = wantsToRun;
    }

    public List<ItemData> getInventoryItems() {
        return inventoryItems;
    }

    public void setInventoryItems(List<ItemData> items) {
        this.inventoryItems = new ArrayList<>(items);
    }

    public List<PokemonData> getPartyPokemon() {
        return partyPokemon;
    }

    public void setPartyPokemon(List<PokemonData> partyPokemon) {
        this.partyPokemon = partyPokemon;
    }

    private boolean validateItemData(ItemData item) {
        return item != null &&
            item.getItemId() != null &&
            !item.getItemId().isEmpty() &&
            item.getCount() > 0 &&
            item.getCount() <= Item.MAX_STACK_SIZE &&
            ItemManager.getItem(item.getItemId()) != null &&
            item.getUuid() != null;
    }

    @Override
    public String toString() {
        return "io.github.pokemeetup.system.data.PlayerData{" +
            "username='" + username + '\'' +
            ", position=(" + x + "," + y + ")" +
            ", direction='" + direction + '\'' +
            ", inventory=" + (inventoryItems != null ? inventoryItems.size() : "null") + " items" +
            ", party=" + (partyPokemon != null ? partyPokemon.size() : "null") + " pokemon" +
            ", characterType='" + characterType + '\'' +
            '}';
    }
}
