package io.github.pokemeetup.screens.otherui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.utils.GameLogger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BagScreen extends Window {

    public interface BagSelectionListener {
        void onItemSelected(ItemData item);
    }

    private final Inventory inventory;
    private final BagSelectionListener listener;
    private Runnable onClose; // Callback to run when the bag is closed

    // Define explicit lists for filtering by category.
    private static final List<String> HEALING_ITEMS = Arrays.asList("potion", "elixir");
    private static final List<String> CATCHING_ITEMS = Arrays.asList("pokeball", "greatball", "ultraball");
    private static final List<String> BATTLE_ITEMS = List.of("wooden_axe"); // example for battle items
    private static final List<String> ACTION_ITEMS = List.of("action"); // adjust as needed

    public BagScreen(Skin skin, Inventory inventory, BagSelectionListener listener) {
        super("Bag", skin);
        this.inventory = inventory;
        this.listener = listener;
        setSize(600, 400);
        center();
        setModal(true);    // Make this window modal so it captures input
        setMovable(false); // Fixed position
        buildUI(skin);
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    @Override
    public boolean remove() {
        if (onClose != null) {
            onClose.run();
        }
        return super.remove();
    }

    private void buildUI(Skin skin) {
        // Create a tab header with four buttons.
        Table tabTable = new Table(skin);
        final TextButton healingTab = new TextButton("Healing", skin);
        final TextButton battleTab = new TextButton("Battle", skin);
        final TextButton catchingTab = new TextButton("Catching", skin);
        final TextButton actionTab = new TextButton("Action", skin);
        tabTable.add(healingTab).pad(5);
        tabTable.add(battleTab).pad(5);
        tabTable.add(catchingTab).pad(5);
        tabTable.add(actionTab).pad(5);
        add(tabTable).expandX().fillX().row();

        // Use a table to hold the content, wrapped in a scroll pane for better layout.
        final Table contentTable = new Table(skin);
        ScrollPane scrollPane = new ScrollPane(contentTable, skin);
        scrollPane.setFadeScrollBars(false);
        add(scrollPane).expand().fill().row();

        // Initially show the "Catching" section.
        populateSection(contentTable, "catching");

        healingTab.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                populateSection(contentTable, "healing");
            }
        });
        battleTab.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                populateSection(contentTable, "battle");
            }
        });
        catchingTab.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                populateSection(contentTable, "catching");
            }
        });
        actionTab.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                populateSection(contentTable, "action");
            }
        });

        // A close button at the bottom.
        TextButton closeButton = new TextButton("Close", skin);
        add(closeButton).pad(10).expandX().fillX();
        closeButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                remove();
            }
        });
    }

    private void populateSection(Table contentTable, String section) {
        contentTable.clear();
        List<ItemData> items = inventory.getAllItems();
        List<ItemData> filtered = new ArrayList<>();
        for (ItemData item : items) {
            if (item == null) continue;
            // Use the item ID directly (which is used for identification) and compare exactly.
            String normalizedId = item.getItemId().toLowerCase();
            switch (section) {
                case "healing":
                    if (HEALING_ITEMS.contains(normalizedId))
                        filtered.add(item);
                    break;
                case "battle":
                    if (BATTLE_ITEMS.contains(normalizedId))
                        filtered.add(item);
                    break;
                case "catching":
                    if (CATCHING_ITEMS.contains(normalizedId))
                        filtered.add(item);
                    break;
                case "action":
                    if (ACTION_ITEMS.contains(normalizedId))
                        filtered.add(item);
                    break;
            }
        }
        if (filtered.isEmpty()) {
            Label emptyLabel = new Label("No items in this section.", getSkin());
            emptyLabel.setAlignment(Align.center);
            contentTable.add(emptyLabel).expand().fill().center();
        } else {
            for (final ItemData item : filtered) {
                // Display the item name (or any other display property) along with its count.
                TextButton itemButton = new TextButton(item.getItemId() + " x" + item.getCount(), getSkin());
                itemButton.addListener(new ChangeListener() {
                    @Override
                    public void changed(ChangeEvent event, Actor actor) {
                        if (listener != null)
                            listener.onItemSelected(item);
                        remove();
                    }
                });
                contentTable.add(itemButton).pad(5).fillX().expandX();
                contentTable.row();
            }
        }
    }
}
