package io.github.pokemeetup.system.gameplay.inventory.crafting;

public class CraftingResult {
    private final String itemId;
    private final int count;

    public CraftingResult(String itemId, int count) {
        this.itemId = itemId;
        this.count = count;
    }

    public String getItemId() {
        return itemId;
    }

    public int getCount() {
        return count;
    }
}
