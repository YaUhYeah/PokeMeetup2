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
    private static final float BASE_TITLE_SCALE = 2.0f;
    private final Label pokemonInfoLabel;
    private final TextButton confirmButton;
    private Pokemon selectedStarter;
    private Table selectedCell = null;
    private SelectionListener selectionListener;
    private boolean selectionMade = false;


    private final Skin skin;
    private final Label titleLabel;
    public StarterSelectionTable(Skin skin) {
        this.skin = skin;
        GameLogger.info("Creating StarterSelectionTable");
        Gdx.graphics.setWindowedMode(
            Math.max(800, Gdx.graphics.getWidth()),
            Math.max(600, Gdx.graphics.getHeight())
        );
        // Setup main table properties
        setFillParent(true);
        setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("starter-bg")));
        setTouchable(Touchable.enabled);
        Table mainContainer = new Table();
        mainContainer.center();
        mainContainer.defaults().center().pad(20);

        // Add top spacing for vertical centering
        mainContainer.add().expandY().row();

        // Title
        titleLabel = new Label("Choose Your First Partner!", skin);
        titleLabel.setFontScale(BASE_TITLE_SCALE);
        titleLabel.setAlignment(Align.center);
        mainContainer.add(titleLabel).expandX().center().padBottom(40).row();

        // Pokemon selection area
        starters = new Table();
        starters.defaults().pad(BASE_PADDING).space(40);
        starters.center();

        // Add starter options
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

        // Add bottom spacing for vertical centering
        mainContainer.add().expandY().row();

        // Add main container to this table
        add(mainContainer).expand().fill();

        GameLogger.info("StarterSelectionTable setup complete");
    }


private void addStarterOption(Table container, String pokemonName, String description) {
        Table cell = new Table();
        cell.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("slot_normal")));
        cell.center(); // Center contents of the cell

        // Pokemon sprite
        TextureRegion sprite = TextureManager.getPokemonfront().findRegion(pokemonName + "_front");
        if (sprite != null) {
            Image image = new Image(sprite);
            image.setScaling(Scaling.fit);
            Vector2 imageSize = ResponsiveLayout.getElementSize(120, 120);
            cell.add(image).size(imageSize.x, imageSize.y)
                .center() // Center the image
                .pad(ResponsiveLayout.getPadding())
                .row();
        }

        // Pokemon name
        Label nameLabel = new Label(pokemonName, skin);
        nameLabel.setFontScale(ResponsiveLayout.getFontScale());
        cell.add(nameLabel).center().pad(ResponsiveLayout.getPadding());

        // Click listener
        cell.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                selectStarter(pokemonName, description, cell);
            }
        });

        // Add cell to container with proper sizing
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
                starter.setCurrentHp(starter.getStats().getHp());
                starter.setLevel(5);
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
    private final Table starters;  // Table containing Pokemon options

    private static final float BASE_POKEMON_SIZE = 160f;
    private static final float BASE_PADDING = 20f;
    private static final float BASE_BUTTON_WIDTH = 300f;
    private static final float BASE_BUTTON_HEIGHT = 80f;

    public void setSelectionListener(SelectionListener listener) {
        this.selectionListener = listener;
    }

    public void resize(int width, int height) {
        GameLogger.info("Resizing StarterSelectionTable to: " + width + "x" + height);

        // Calculate scale based on screen size
        float scaleFactor = Math.min(width / 1920f, height / 1080f);
        scaleFactor = Math.max(scaleFactor, 0.3f);
        float buttonWidth = BASE_BUTTON_WIDTH * scaleFactor;
        float buttonHeight = BASE_BUTTON_HEIGHT * scaleFactor;
        float padding = BASE_PADDING * scaleFactor;

        // Update font scales
        titleLabel.setFontScale(BASE_TITLE_SCALE * scaleFactor);
        pokemonInfoLabel.setFontScale(1.3f * scaleFactor);
        confirmButton.getLabel().setFontScale(1.5f * scaleFactor);

        // Update Pokemon container
        starters.clear();
        starters.defaults().pad(padding).space(padding * 2);

        // Recreate Pokemon options with new sizes
        addStarterOption(starters, "BULBASAUR", "A reliable grass-type partner with a mysterious bulb.");
        addStarterOption(starters, "CHARMANDER", "A fierce fire-type partner with a burning tail.");
        addStarterOption(starters, "SQUIRTLE", "A sturdy water-type partner with a protective shell.");

        // Update info label width
        pokemonInfoLabel.setWidth(width * 0.6f);

        // Update button size
        confirmButton.setSize(buttonWidth, buttonHeight);

        // Force layout update
        invalidateHierarchy();
        validate();

        // Center the table
        setPosition((width - getWidth()) / 2, (height - getHeight()) / 2);

        GameLogger.info("StarterSelectionTable resize complete - Scale factor: " + scaleFactor);
    }





    public interface SelectionListener {
        void onStarterSelected(Pokemon starter);

        void onSelectionStart();
    }

}
