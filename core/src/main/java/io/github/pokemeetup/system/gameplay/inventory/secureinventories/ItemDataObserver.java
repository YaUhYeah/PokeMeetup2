package io.github.pokemeetup.system.gameplay.inventory.secureinventories;

import io.github.pokemeetup.system.data.ItemData;

public interface ItemDataObserver {
    void onItemDataChanged(ItemData itemData);
}
