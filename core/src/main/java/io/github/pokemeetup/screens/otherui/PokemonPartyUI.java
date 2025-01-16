package io.github.pokemeetup.screens.otherui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.PokemonParty;
import io.github.pokemeetup.utils.textures.TextureManager;

import java.util.ArrayList;
import java.util.List;

public class PokemonPartyUI extends Table {
    private static final float PARTY_WIDTH = 300f;
    private static final float PARTY_HEIGHT = 400f;
    private static final float SLOT_SIZE = 80f;
    private static final float PADDING = 10f;

    private final PokemonParty party;
    private final Skin skin;
    private final List<PokemonSlot> slots;
    private int selectedIndex;

    public PokemonPartyUI(PokemonParty party, Skin skin) {
        this.party = party;
        this.skin = skin;
        this.slots = new ArrayList<>();
        this.selectedIndex = 0;

        setBackground(createBackground());
        pad(PADDING);
        setupUI();

        // Center the party UI
        setPosition(
            (Gdx.graphics.getWidth() - PARTY_WIDTH) / 2,
            (Gdx.graphics.getHeight() - PARTY_HEIGHT) / 2
        );
        setSize(PARTY_WIDTH, PARTY_HEIGHT);
    }

    private void setupUI() {
        for (int i = 0; i < PokemonParty.MAX_PARTY_SIZE; i++) {
            PokemonSlot slot = new PokemonSlot(i, skin);
            slots.add(slot);
            add(slot).size(SLOT_SIZE).pad(PADDING);
            row();
        }
        updateUI();
    }

    public void updateUI() {
        for (int i = 0; i < slots.size(); i++) {
            Pokemon pokemon = party.getPokemon(i);
            PokemonSlot slot = slots.get(i);
            slot.update(pokemon);
            slot.setSelected(i == selectedIndex);
        }
    }

    public void selectPokemon(int index) {
        if (index >= 0 && index < party.getSize()) {
            selectedIndex = index;
            updateUI();
        }
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public Pokemon getSelectedPokemon() {
        return party.getPokemon(selectedIndex);
    }

    private Drawable createBackground() {
        return new TextureRegionDrawable(TextureManager.ui
            .findRegion("hotbar_bg"))
            .tint(new Color(0.2f, 0.2f, 0.2f, 0.9f));
    }

    private class PokemonSlot extends Table {
        private final int index;
        private final Image pokemonIcon;
        private final Label nameLabel;
        private final Label levelLabel;
        private final ProgressBar hpBar;

        public PokemonSlot(int index, Skin skin) {
            this.index = index;
            setBackground(createSlotBackground());
            pad(5);

            pokemonIcon = new Image();
            nameLabel = new Label("", skin);
            levelLabel = new Label("", skin);
            hpBar = new ProgressBar(0, 100, 1, false, skin);

            add(pokemonIcon).size(50).padRight(10);
            Table infoTable = new Table();
            infoTable.add(nameLabel).left().row();
            infoTable.add(levelLabel).left().row();
            infoTable.add(hpBar).width(100).padTop(5);
            add(infoTable).expandX().left();
        }

        public void update(Pokemon pokemon) {
            if (pokemon != null) {
                pokemonIcon.setDrawable(new TextureRegionDrawable(pokemon.getIconSprite()));
                nameLabel.setText(pokemon.getName());
                levelLabel.setText("Lv. " + pokemon.getLevel());
                hpBar.setValue(((float) pokemon.getCurrentHp() / pokemon.getStats().getHp()) * 100);
                setVisible(true);
            } else {
                setVisible(index < party.getSize());
            }
        }

        public void setSelected(boolean selected) {
            setBackground(createSlotBackground(selected));
        }

        private Drawable createSlotBackground(boolean selected) {
            return new TextureRegionDrawable(TextureManager.ui
                .findRegion(selected ? "slot_selected" : "slot_normal"))
                .tint(new Color(0.3f, 0.3f, 0.3f, 0.9f));
        }

        private Drawable createSlotBackground() {
            return createSlotBackground(false);
        }
    }
}
