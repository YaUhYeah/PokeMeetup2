package io.github.pokemeetup.screens.otherui;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.PokemonParty;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;

public class HotbarUI {
    private static final int HOTBAR_SLOTS = 9;
    private final Table mainTable;
    private final Stage stage;
    private final Skin skin;
    private final Player player;
    private final TextureAtlas atlas;

    private Table hotbarTable;
    private boolean buildMode = false;

    public HotbarUI(Stage stage, Skin skin, Player player, TextureAtlas atlas) {
        this.stage = stage;
        this.skin = skin;
        this.player = player;
        this.atlas = atlas;
        this.mainTable = new Table();

        createUI();
    }

    private void createUI() {
        mainTable.setFillParent(true);
        mainTable.bottom();
        mainTable.pad(20);

        hotbarTable = new Table();
        updateHotbarContent();

        mainTable.add(hotbarTable).expandX().bottom();
        stage.addActor(mainTable);
    }

    public void toggleBuildMode() {
        buildMode = !buildMode;
        updateHotbarContent();
    }

    public void updateHotbarContent() {
        hotbarTable.clear();
        hotbarTable.setBackground(new TextureRegionDrawable(
            atlas.findRegion("hotbar_bg")
        ));
        hotbarTable.pad(4);

        if (buildMode) {
            createBuildModeSlots();
        } else {
            createPokemonSlots();
        }
    }

    private void createBuildModeSlots() {
        Inventory buildInventory = player.getBuildInventory();

        for (int i = 0; i < HOTBAR_SLOTS; i++) {
            Table slotCell = new Table();
            slotCell.setBackground(new TextureRegionDrawable(
                atlas.findRegion("slot_normal")
            ));

            ItemData item = buildInventory.getItemAt(i);
            if (item != null) {
                // Add item icon
                Image itemIcon = new Image(ItemManager.getItem(item.getItemId()).getIcon());
                slotCell.add(itemIcon).size(32).center();

                // Add count label if more than 1
                if (item.getCount() > 1) {
                    Label countLabel = new Label(String.valueOf(item.getCount()), skin);
                    countLabel.setPosition(
                        slotCell.getWidth() - 10,
                        10
                    );
                    slotCell.addActor(countLabel);
                }
            }

            hotbarTable.add(slotCell).size(40).pad(2);
        }
    }

    private void createPokemonSlots() {
        PokemonParty party = player.getPokemonParty();

        for (int i = 0; i < HOTBAR_SLOTS; i++) {
            Table slotCell = new Table();
            slotCell.setBackground(new TextureRegionDrawable(
                atlas.findRegion(i == 0 ? "slot_selected" : "slot_normal")
            ));

            if (i < party.getSize()) {
                Pokemon pokemon = party.getPokemon(i);
                if (pokemon != null) {
                    // Add Pokemon icon
                    Image pokemonIcon = new Image(pokemon.getCurrentIconFrame(0));
                    slotCell.add(pokemonIcon).size(32).center();

                    // Add level label
                    Label levelLabel = new Label("Lv." + pokemon.getLevel(), skin);
                    levelLabel.setPosition(
                        slotCell.getWidth() - 10,
                        10
                    );
                    slotCell.addActor(levelLabel);
                }
            }

            hotbarTable.add(slotCell).size(40).pad(2);
        }
    }

    public void update(float delta) {
        // Update selected slot highlighting and animations
        if (buildMode != player.isBuildMode()) {
            toggleBuildMode();
        }
    }
}
