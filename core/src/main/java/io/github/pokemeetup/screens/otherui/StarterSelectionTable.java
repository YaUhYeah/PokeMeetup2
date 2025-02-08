package io.github.pokemeetup.screens.otherui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.data.PokemonDatabase;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.ResponsiveLayout;
import io.github.pokemeetup.utils.textures.TextureManager;

public class StarterSelectionTable extends Table {
    // Use a static instance variable to enforce one instance at a time.
    private static StarterSelectionTable instance = null;

    // UI elements and fields (non‑final so they can be initialized in every run)
    private Label pokemonInfoLabel;
    private TextButton confirmButton;
    private Pokemon selectedStarter;
    private Table selectedCell = null;
    private SelectionListener selectionListener;
    private boolean selectionMade = false;

    private Skin skin;
    private Label titleLabel;
    private Table starters;  // Container for starter options

    // Base constants for layout
    private static final float BASE_TITLE_SCALE = 2.0f;
    private static final float BASE_PADDING = 20f;
    private static final float BASE_BUTTON_WIDTH = 300f;
    private static final float BASE_BUTTON_HEIGHT = 80f;

    // Private constructor
    public StarterSelectionTable(Skin skin) {
        this.skin = skin;
        GameLogger.info("Creating StarterSelectionTable");

        // (Optional) Adjust window size for testing.
        Gdx.graphics.setWindowedMode(
            Math.max(800, Gdx.graphics.getWidth()),
            Math.max(600, Gdx.graphics.getHeight())
        );

        // Setup this table
        setFillParent(true);
        setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("starter-bg")));
        setTouchable(Touchable.enabled);

        Table mainContainer = new Table();
        mainContainer.center();
        mainContainer.defaults().center().pad(20);

        // Top spacer for vertical centering
        mainContainer.add().expandY().row();

        // Title label
        titleLabel = new Label("Choose Your First Partner!", skin);
        titleLabel.setFontScale(BASE_TITLE_SCALE);
        titleLabel.setAlignment(Align.center);
        mainContainer.add(titleLabel).expandX().center().padBottom(40).row();

        // Starter options container
        starters = new Table();
        starters.defaults().pad(BASE_PADDING).space(40);
        starters.center();
        addStarterOption(starters, "BULBASAUR", "A reliable grass-type partner with a mysterious bulb.");
        addStarterOption(starters, "CHARMANDER", "A fierce fire-type partner with a burning tail.");
        addStarterOption(starters, "SQUIRTLE", "A sturdy water-type partner with a protective shell.");
        mainContainer.add(starters).expandX().center().padBottom(40).row();

        // Info label
        pokemonInfoLabel = new Label("Click on a Pokemon to learn more!", skin);
        pokemonInfoLabel.setWrap(true);
        pokemonInfoLabel.setAlignment(Align.center);
        pokemonInfoLabel.setFontScale(1.3f);
        Table infoContainer = new Table();
        infoContainer.add(pokemonInfoLabel).width(Gdx.graphics.getWidth() * 0.6f).pad(30);
        mainContainer.add(infoContainer).expandX().center().padBottom(30).row();

        // Confirm button
        confirmButton = new TextButton("Choose Pokemon!", skin);
        confirmButton.setDisabled(true);
        confirmButton.getLabel().setFontScale(1.5f);
        confirmButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (!confirmButton.isDisabled() && selectedStarter != null) {
                    confirmSelection();
                }
            }
        });
        mainContainer.add(confirmButton).size(BASE_BUTTON_WIDTH, BASE_BUTTON_HEIGHT).padBottom(40).row();

        // Bottom spacer for vertical centering
        mainContainer.add().expandY().row();

        add(mainContainer).expand().fill();

        GameLogger.info("StarterSelectionTable setup complete");
    }

    /**
     * Static factory method – returns the current instance if available;
     * otherwise creates a new one.
     */
    public static StarterSelectionTable getInstance(Skin skin) {
        if (instance == null) {
            instance = new StarterSelectionTable(skin);
        } else {
            GameLogger.info("Returning existing StarterSelectionTable instance.");
        }
        return instance;
    }

    @Override
    public boolean remove() {
        instance = null; // allow a new instance next time
        return super.remove();
    }

    private void addStarterOption(Table container, String pokemonName, String description) {
        Table cell = new Table();
        cell.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("slot_normal")));
        cell.center();

        // Add Pokemon sprite
        TextureRegion sprite = TextureManager.getPokemonfront().findRegion(pokemonName + "_front");
        if (sprite != null) {
            Image image = new Image(sprite);
            image.setScaling(Scaling.fit);
            Vector2 imageSize = ResponsiveLayout.getElementSize(120, 120);
            cell.add(image)
                .size(imageSize.x, imageSize.y)
                .center().pad(ResponsiveLayout.getPadding()).row();
        }
        // Add Pokemon name label
        Label nameLabel = new Label(pokemonName, skin);
        nameLabel.setFontScale(ResponsiveLayout.getFontScale());
        cell.add(nameLabel).center().pad(ResponsiveLayout.getPadding());

        // Add click listener
        cell.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                selectStarter(pokemonName, description, cell);
            }
        });

        // Add the cell with fixed size
        Vector2 cellSize = ResponsiveLayout.getElementSize(180, 200);
        container.add(cell).size(cellSize.x, cellSize.y).center();
    }

    private void setupStarterPokemon(Pokemon starter) {
        switch (starter.getName()) {
            case "BULBASAUR":
                starter.setPrimaryType(Pokemon.PokemonType.GRASS);
                starter.getMoves().add(PokemonDatabase.getMoveByName("Tackle"));
                starter.getMoves().add(PokemonDatabase.getMoveByName("Growl"));
                starter.setSecondaryType(Pokemon.PokemonType.POISON);
                starter.setLevel(5);
                starter.setCurrentHp(starter.getStats().getHp());
                break;
            case "CHARMANDER":
                starter.setPrimaryType(Pokemon.PokemonType.FIRE);
                starter.getMoves().add(PokemonDatabase.getMoveByName("Tackle"));
                starter.getMoves().add(PokemonDatabase.getMoveByName("Growl"));
                starter.setLevel(5);
                starter.setCurrentHp(starter.getStats().getHp());
                break;
            case "SQUIRTLE":
                starter.setPrimaryType(Pokemon.PokemonType.WATER);
                starter.getMoves().add(PokemonDatabase.getMoveByName("Tackle"));
                starter.getMoves().add(PokemonDatabase.getMoveByName("Withdraw"));
                starter.setLevel(5);
                starter.setCurrentHp(starter.getStats().getHp());
                break;
        }
        // Set base stats for all starters
        Pokemon.Stats stats = starter.getStats();
        stats.setHp(20);
        stats.setAttack(12);
        stats.setDefense(12);
        stats.setSpecialAttack(12);
        stats.setSpecialDefense(12);
        stats.setSpeed(12);
        starter.setCurrentHp(stats.getHp());
    }

    private void selectStarter(String pokemonName, String description, Table pokemonCell) {
        if (selectionMade) return;

        GameLogger.info("Selecting starter: " + pokemonName);
        selectedStarter = new Pokemon(pokemonName, 5);
        setupStarterPokemon(selectedStarter);

        if (selectedCell != null) {
            selectedCell.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("slot_normal")));
            selectedCell.setColor(1, 1, 1, 1);
        }
        pokemonCell.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("slot_selected")));
        selectedCell = pokemonCell;

        confirmButton.setDisabled(false);
        pokemonInfoLabel.setText(description);

        if (selectionListener != null) {
            selectionListener.onSelectionStart();
        }
    }

    private void confirmSelection() {
        if (selectedStarter != null && selectionListener != null && !selectionMade) {
            GameLogger.info("Confirming starter selection: " + selectedStarter.getName());
            selectionMade = true;
            selectionListener.onStarterSelected(selectedStarter);
        }
    }

    public void setSelectionListener(SelectionListener listener) {
        this.selectionListener = listener;
    }

    public void resize(int width, int height) {
        GameLogger.info("Resizing StarterSelectionTable to: " + width + "x" + height);
        float scaleFactor = Math.min(width / 1920f, height / 1080f);
        scaleFactor = Math.max(scaleFactor, 0.3f);
        float buttonWidth = BASE_BUTTON_WIDTH * scaleFactor;
        float buttonHeight = BASE_BUTTON_HEIGHT * scaleFactor;
        float padding = BASE_PADDING * scaleFactor;

        titleLabel.setFontScale(BASE_TITLE_SCALE * scaleFactor);
        pokemonInfoLabel.setFontScale(1.3f * scaleFactor);
        confirmButton.getLabel().setFontScale(1.5f * scaleFactor);

        starters.clear();
        starters.defaults().pad(padding).space(padding * 2);
        addStarterOption(starters, "BULBASAUR", "A reliable grass-type partner with a mysterious bulb.");
        addStarterOption(starters, "CHARMANDER", "A fierce fire-type partner with a burning tail.");
        addStarterOption(starters, "SQUIRTLE", "A sturdy water-type partner with a protective shell.");

        pokemonInfoLabel.setWidth(width * 0.6f);
        confirmButton.setSize(buttonWidth, buttonHeight);
        invalidateHierarchy();
        validate();
        setPosition((width - getWidth()) / 2, (height - getHeight()) / 2);
        GameLogger.info("StarterSelectionTable resize complete - Scale factor: " + scaleFactor);
    }

    public interface SelectionListener {
        void onStarterSelected(Pokemon starter);
        void onSelectionStart();
    }
}
