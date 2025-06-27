package io.github.pokemeetup.screens.otherui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Scaling;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.PokemonParty;
import io.github.pokemeetup.pokemon.attacks.Move;
import io.github.pokemeetup.screens.GameScreen;
import io.github.pokemeetup.utils.textures.TextureManager;

import java.util.ArrayList;
import java.util.List;

import static com.badlogic.gdx.scenes.scene2d.actions.Actions.hide;

public class PokemonPartyWindow extends Window {
    private final PokemonParty party;
    private final boolean battleMode;
    private final PartySelectionListener selectionListener;
    private final Runnable cancelCallback;

    private Table contentTable;
    private int currentPokemonIndex = -1; // Index of the Pokemon currently in battle
    private ArrayList<PokemonSlot> pokemonSlots = new ArrayList<>();
    private int selectedIndex = -1;
    private PokemonSlot selectedSlot = null;
    private ScrollPane scrollPane;

    public PokemonPartyWindow(
        Skin skin,
        PokemonParty party,
        boolean battleMode,
        PartySelectionListener selectionListener,
        Runnable cancelCallback) {

        super("", skin);
        this.party = party;
        this.battleMode = battleMode;
        this.selectionListener = selectionListener;
        this.cancelCallback = cancelCallback;
        setModal(true);
        setMovable(false);
        if (battleMode && GameContext.get().getBattleTable() != null) {
            Pokemon activePokemon = GameContext.get().getBattleTable().getPlayerPokemon(); // Assuming BattleTable has getPlayerPokemon()
            if (activePokemon != null) {
                List<Pokemon> partyList = party.getParty();
                for (int i = 0; i < partyList.size(); i++) {
                    if (partyList.get(i) == activePokemon) {
                        currentPokemonIndex = i;
                        break;
                    }
                }
            }
        }
        initialize();
    }

    /**
     * Tints the progress bar's background to a neutral grey and the fill portion
     * (knobBefore) to green/yellow/red based on HP percentage.
     */
    private static void updateHPBarColor(ProgressBar bar, float percentage, Skin skin) {
        ProgressBar.ProgressBarStyle baseStyle = skin.get("default-horizontal", ProgressBar.ProgressBarStyle.class);
        ProgressBar.ProgressBarStyle newStyle = new ProgressBar.ProgressBarStyle(baseStyle);
        if (baseStyle.background instanceof TextureRegionDrawable) {
            TextureRegionDrawable bg = new TextureRegionDrawable(((TextureRegionDrawable) baseStyle.background).getRegion());
            bg.tint(new Color(0.35f, 0.35f, 0.35f, 1f));
            newStyle.background = bg;
        }
        if (baseStyle.knobBefore instanceof TextureRegionDrawable) {
            TextureRegionDrawable fill = new TextureRegionDrawable(((TextureRegionDrawable) baseStyle.knobBefore).getRegion());
            if (percentage > 0.5f) {
                fill.tint(skin.getColor("green"));
            } else if (percentage > 0.2f) {
                fill.tint(Color.YELLOW);
            } else {
                fill.tint(skin.getColor("red"));
            }
            newStyle.knobBefore = fill;
        }
        newStyle.knob = null;

        bar.setStyle(newStyle);
    }

    /**
     * Resizes/positions the window to about 70% of the screen width and 50% height, then centers it.
     */
    public void show(Stage stage) {
        pack();
        float maxW = stage.getWidth() * 0.70f;
        float maxH = stage.getHeight() * 0.50f;
        if (getWidth() > maxW) setWidth(maxW);
        if (getHeight() > maxH) setHeight(maxH);
        setPosition(
            (stage.getWidth() - getWidth()) / 2f,
            (stage.getHeight() - getHeight()) / 2f
        );

        layout();
        addAction(Actions.fadeIn(0.3f));
    }

    public void updateSlots() {
        rebuildSlots();
    }

    private void initialize() {
        Table rootTable = new Table();
        rootTable.setFillParent(true);
        contentTable = new Table();
        contentTable.pad(10);
        scrollPane = new ScrollPane(contentTable, getSkin());
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(false, false);
        rootTable.add(scrollPane).expand().fill().row();
        if (cancelCallback != null) {
            TextButton cancelButton = new TextButton("Cancel", getSkin());
            cancelButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    cancelCallback.run();
                    addAction(Actions.sequence(hide(), Actions.removeActor()));
                }
            });
            rootTable.add(cancelButton).expandX().center().padTop(8);
        }

        add(rootTable).expand().fill();

        getColor().a = 0;
        addAction(Actions.sequence(
            Actions.fadeIn(0.3f),
            Actions.run(() -> AudioManager.getInstance().playSound(AudioManager.SoundEffect.MENU_SELECT))
        ));

        rebuildSlots();
    }

    private void rebuildSlots() {
        contentTable.clearChildren();

        Label title = new Label(battleMode ? "Choose Pokémon" : "Pokémon Party", getSkin());
        title.setFontScale(1.2f);
        contentTable.add(title).expandX().center().pad(10).row();

        pokemonSlots.clear();
        List<Pokemon> partyList = party.getParty();
        for (int i = 0; i < partyList.size(); i++) {
            Pokemon pokemon = partyList.get(i);
            final int slotIndex = i;
            PokemonSlot slot = new PokemonSlot(this, pokemon, getSkin(), battleMode, slotIndex, i == currentPokemonIndex); // NEW: pass active status
            pokemonSlots.add(slot);
            slot.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (selectionMade) return;

                    if (battleMode) {
                        if (pokemon.getCurrentHp() <= 0) {
                            return;
                        }
                        if (slotIndex == currentPokemonIndex) {
                            return;
                        }

                        selectionMade = true;
                        selectionListener.onPokemonSelected(slotIndex); // MODIFIED

                        PokemonPartyWindow.this.addAction(Actions.sequence(
                            Actions.fadeOut(0.3f),
                            Actions.run(PokemonPartyWindow.this::remove)
                        ));
                    } else {
                        handleSlotClick(slotIndex, slot);
                    }
                }
            });

            contentTable.add(slot).expandX().fillX().pad(5).row();
        }
    }

    private boolean selectionMade = false;


    /**
     * Swapping logic for non-battle mode.
     */
    private void handleSlotClick(int slotIndex, PokemonSlot slot) {
        if (selectedSlot == null) {
            selectedSlot = slot;
            selectedIndex = slotIndex;
            slot.setSelected(true);
        } else {
            if (selectedSlot == slot) {
                slot.setSelected(false);
                selectedSlot = null;
                selectedIndex = -1;
                return;
            }
            party.swapPositions(selectedIndex, slotIndex);
            selectedSlot.setSelected(false);
            selectedSlot = null;
            selectedIndex = -1;
            rebuildSlots();
            GameContext.get().getGameScreen().updatePartyDisplay();
        }
    }


    public interface PartySelectionListener {
        void onPokemonSelected(int partyIndex); // MODIFIED
    }

    /**
     * Represents a single row (slot) in the party list.
     */
    private static class PokemonSlot extends Table {
        private final PokemonPartyWindow parentWindow;  // reference to the entire window
        private final Pokemon pokemon;
        private final Image icon;
        private final TextureRegion[] frames;
        private PokemonTooltip tooltip;
        private float animTime = 0f;
        private boolean isHovered = false;
        private boolean isSelected = false;

        private final boolean isActiveInBattle; // NEW field
        public PokemonSlot(PokemonPartyWindow parentWindow,
                           Pokemon pokemon,
                           Skin skin,
                           boolean battleMode,
                           int slotIndex,
                           boolean isActive) { // NEW parameter

            super(skin);
            this.parentWindow = parentWindow;  // store reference
            this.pokemon = pokemon;

            this.isActiveInBattle = isActive; // Store active status
            TextureRegion fullIcon = pokemon.getIconSprite();
            if (fullIcon == null) {
                fullIcon = new TextureRegion(TextureManager.getWhitePixel());
            }
            frames = new TextureRegion[2];
            if (fullIcon.getRegionWidth() == 128 && fullIcon.getRegionHeight() == 64) {
                frames[0] = new TextureRegion(fullIcon, 0, 0, 64, 64);
                frames[1] = new TextureRegion(fullIcon, 64, 0, 64, 64);
            } else {
                frames[0] = fullIcon;
                frames[1] = fullIcon;
            }

            icon = new Image(new TextureRegionDrawable(frames[0]));
            icon.setScaling(Scaling.fit);
            Table infoTable = new Table();
            Label nameLabel = new Label(pokemon.getName(), skin);
            Label levelLabel = new Label("Lv." + pokemon.getLevel(), skin);
            ProgressBar hpBar = new ProgressBar(0, pokemon.getStats().getHp(), 1, false, skin);
            hpBar.setValue(pokemon.getCurrentHp());
            updateHPBarColor(hpBar, pokemon.getCurrentHp() / (float) pokemon.getStats().getHp(), skin);
            Label hpLabel = new Label(pokemon.getCurrentHp() + "/" + pokemon.getStats().getHp(), skin);

            infoTable.add(nameLabel).left().padRight(10);
            infoTable.add(levelLabel).right().row();
            infoTable.add(hpBar).colspan(2).fillX().pad(2).row();
            infoTable.add(hpLabel).colspan(2).right().row();

            add(icon).width(Value.percentWidth(0.15f, this))
                .height(Value.percentWidth(0.15f, this))
                .padRight(10);
            add(infoTable).expand().fill().row();

            if (battleMode && (pokemon.getCurrentHp() <= 0 || isActiveInBattle)) {
                setColor(getColor().mul(0.6f));
                setTouchable(Touchable.disabled); // Make untappable
            } else {
                setTouchable(Touchable.enabled); // Ensure tappable otherwise
            }
            addListener(new InputListener() {
                @Override
                public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                    isHovered = true;
                    Stage currentStage = getStage();
                    if (tooltip == null && currentStage != null) {
                        tooltip = new PokemonTooltip(pokemon, skin);
                        currentStage.addActor(tooltip);
                        tooltip.toFront();
                        updateTooltipPosition(event.getStageX(), event.getStageY());
                    }
                }

                @Override
                public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                    isHovered = false;
                    removeTooltip();
                }

                @Override
                public boolean mouseMoved(InputEvent event, float x, float y) {
                    if (tooltip != null) {
                        updateTooltipPosition(event.getStageX(), event.getStageY());
                    }
                    return true;
                }
            });
        }

        public void setSelected(boolean selected) {
            isSelected = selected;
            if (selected) {
                addAction(Actions.scaleTo(1.08f, 1.08f, 0.2f));
                setBackground(new TextureRegionDrawable(TextureManager.getUi().findRegion("slot_selected")));
            } else {
                addAction(Actions.scaleTo(1f, 1f, 0.2f));
                setBackground(new TextureRegionDrawable(TextureManager.getUi().findRegion("slot_normal")));
            }
        }

        @Override
        public void act(float delta) {
            super.act(delta);
            if (isHovered || isSelected) {
                animTime += delta;
                int index = (int) (animTime / 0.5f) % frames.length;
                if (frames[index] != null) {
                    icon.setDrawable(new TextureRegionDrawable(frames[index]));
                }
            } else {
                icon.setDrawable(new TextureRegionDrawable(frames[0]));
            }
        }

        private void updateTooltipPosition(float stageX, float stageY) {
            if (tooltip != null && getStage() != null) {
                float tooltipX = stageX + 15;
                float tooltipY = stageY - tooltip.getHeight();
                if (tooltipX + tooltip.getWidth() > getStage().getWidth()) {
                    tooltipX = stageX - tooltip.getWidth() - 15;
                }
                if (tooltipY < 0) {
                    tooltipY = stageY + 15;
                }

                tooltip.setPosition(tooltipX, tooltipY);
            }
        }

        private void removeTooltip() {
            if (tooltip != null) {
                tooltip.remove();
                tooltip = null;
            }
        }
    }

    /**
     * Tooltip window for a Pokémon.
     * It is made non-interactive so it does not capture input.
     */
    private static class PokemonTooltip extends Window {
        public PokemonTooltip(Pokemon pokemon, Skin skin) {
            super("", skin);
            setBackground(createTooltipBackground());
            pad(12);
            setTouchable(Touchable.disabled);
            setVisible(true);
            setZIndex(1000);
            Table content = new Table();
            content.defaults().pad(4).left();
            Label nameLabel = new Label(pokemon.getName(), skin);
            content.add(nameLabel).padBottom(8).row();
            content.add(new Label("Nature: " + pokemon.getNature(), skin)).row();
            content.add(new Label("Stats:", skin)).padTop(8).row();
            Table statsTable = new Table();
            statsTable.defaults().pad(2);
            Pokemon.Stats stats = pokemon.getStats();
            addStatRow(statsTable, "HP", stats.getHp(), skin);
            addStatRow(statsTable, "Attack", stats.getAttack(), skin);
            addStatRow(statsTable, "Defense", stats.getDefense(), skin);
            addStatRow(statsTable, "Sp. Atk", stats.getSpecialAttack(), skin);
            addStatRow(statsTable, "Sp. Def", stats.getSpecialDefense(), skin);
            addStatRow(statsTable, "Speed", stats.getSpeed(), skin);
            content.add(statsTable).padBottom(8).row();
            content.add(new Label("Moves:", skin)).padTop(8).row();
            Table movesTable = new Table();
            movesTable.defaults().pad(2);
            for (Move move : pokemon.getMoves()) {
                Table moveRow = new Table();
                moveRow.add(new Label(move.getName(), skin)).expandX().left();
                moveRow.add(new Label("PP: " + move.getPp() + "/" + move.getMaxPp(), skin)).right();
                movesTable.add(moveRow).fillX().row();
            }
            content.add(movesTable).fillX();

            add(content);
            pack();
            getColor().a = 0;
            addAction(Actions.fadeIn(0.2f));
        }

        @Override
        public void act(float delta) {
            super.act(delta);
            toFront();
        }

        private static Drawable createTooltipBackground() {
            Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pixmap.setColor(0, 0, 0, 0.85f);
            pixmap.fill();
            TextureRegionDrawable bg = new TextureRegionDrawable(new TextureRegion(new Texture(pixmap)));
            pixmap.dispose();
            return bg;
        }

        private void addStatRow(Table container, String statName, int value, Skin skin) {
            Table row = new Table();
            row.add(new Label(statName + ":", skin)).left().width(80);
            row.add(new Label(String.valueOf(value), skin)).left();
            container.add(row).fillX().row();
        }
    }
}
