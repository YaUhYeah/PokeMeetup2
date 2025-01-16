package io.github.pokemeetup.chat.commands;

import io.github.pokemeetup.chat.ChatSystem;
import io.github.pokemeetup.chat.Command;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.utils.storage.InventoryConverter;

public class GiveCommand implements Command {

    @Override
    public String getName() {
        return "give";
    }

    @Override
    public String[] getAliases() {
        return new String[0];
    }

    @Override
    public String getDescription() {
        return "gives item to player";
    }

    @Override
    public String getUsage() {
        return "/give <item> <amount> || Optional: <player>";
    }

    @Override
    public boolean isMultiplayerOnly() {
        return false;
    }

    @Override
    public void execute(String args, GameClient gameClient, ChatSystem chatSystem) {
        String[] argsArray = args.split(" ");

        if (argsArray.length != 2) {
            chatSystem.addSystemMessage("Usage: /give <item> <amount>");
            return;
        }

        String itemId = argsArray[0];
        try {
            int count = Integer.parseInt(argsArray[1]);
            ItemData item = InventoryConverter.itemToItemData(ItemManager.getItem(itemId));

            if (item == null) {
                chatSystem.addSystemMessage("Item " + itemId + " not found");
                return;
            }

            if (count <= 0 || count > 64) {
                chatSystem.addSystemMessage("Invalid count; must be between 1 and 64");
                return;
            }

            item.setCount(count);
            boolean added = gameClient.getActivePlayer().getInventory().addItem(item);

            if (added) {
                chatSystem.addSystemMessage("You got " + count + "x " + itemId);
            } else {
                chatSystem.addSystemMessage("Inventory full! Could not add item.");
            }

        } catch (NumberFormatException e) {
            chatSystem.addSystemMessage("Invalid number format: " + argsArray[1]);
        } catch (Exception e) {
            chatSystem.addSystemMessage("Error executing command: " + e.getMessage());
        }
    }
}
