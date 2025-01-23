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
            this.x = player.getTileX();
            this.y = player.getTileY();
            this.direction = player.getDirection();
            this.isMoving = player.isMoving();
            this.wantsToRun = player.isRunning();

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

    public int getValidItemCount() {
        if (inventoryItems == null) return 0;
        return (int) inventoryItems.stream()
            .filter(item -> item != null && item.isValid())
            .count();
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
    public int getValidPokemonCount() {
        if (partyPokemon == null) return 0;
        return (int) partyPokemon.stream()
            .filter(pokemon -> pokemon != null && pokemon.verifyIntegrity())
            .count();
    }

    public void applyToPlayer(Player player) {
        if (player == null) return;

        GameLogger.info("Applying PlayerData to player: " + this.username);
        int validItems = getValidItemCount();
        int validPokemon = getValidPokemonCount();
        GameLogger.info("Initial PlayerData state - Valid Items: " + validItems +
            " Valid Pokemon: " + validPokemon);

        try {
            if (validateAndRepairState()) {
                GameLogger.info("Data was repaired during validation");
            }

            player.setX(x * World.TILE_SIZE);
            player.setY(y * World.TILE_SIZE);
            player.setRenderPosition(new Vector2(x * World.TILE_SIZE, y * World.TILE_SIZE));
            player.setDirection(direction);
            player.setMoving(isMoving);
            player.setRunning(wantsToRun);

            if (validItems > 0) {
                player.getInventory().clear();
                for (ItemData item : inventoryItems) {
                    if (item != null && item.isValid()) {
                        player.getInventory().addItem(item.copy());
                        GameLogger.info("Restored item: " + item.getItemId() + " x" + item.getCount());
                    }
                }
            }

            // Only clear if we're actually going to add Pokemon
            if (validPokemon > 0) {
                player.getPokemonParty().clearParty();
                for (PokemonData pokemonData : partyPokemon) {
                    if (pokemonData != null && pokemonData.verifyIntegrity()) {
                        Pokemon pokemon = pokemonData.toPokemon();
                        player.getPokemonParty().addPokemon(pokemon);
                        GameLogger.info("Restored Pokemon: " + pokemon.getName());
                    }
                }
            }

            // Log final valid counts
            GameLogger.info("Final player state - Items: " + player.getInventory().getAllItems().size() +
                " Pokemon: " + player.getPokemonParty().getSize());

        } catch (Exception e) {
            GameLogger.error("Error applying PlayerData: " + e.getMessage());
        }
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


    public PlayerData copy() {
        PlayerData copy = new PlayerData(this.username);

        copy.setX(this.x);
        copy.setY(this.y);
        copy.setDirection(this.direction);
        copy.setMoving(this.isMoving);
        copy.setWantsToRun(this.wantsToRun);

        // Deep copy inventory items
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

        // Deep copy Pokemon party
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

    @Override
    public String toString() {
        return "io.github.pokemeetup.system.data.PlayerData{" +
            "username='" + username + '\'' +
            ", position=(" + x + "," + y + ")" +
            ", direction='" + direction + '\'' +
            ", inventory=" + (inventoryItems != null ? inventoryItems.size() : "null") + " items" +
            ", party=" + (partyPokemon != null ? partyPokemon.size() : "null") + " pokemon" +
            '}';
    }
}
