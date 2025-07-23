package io.github.pokemeetup.screens.otherui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.actions.SequenceAction;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.utils.textures.TextureManager;

public class EvolutionScreen extends Table {

    private final Pokemon evolvingPokemon;
    private final String newPokemonName;
    private final Runnable onComplete;
    private final Skin skin;

    private Image oldPokemonImage;
    private Image newPokemonImage;
    private Label messageLabel;

    public interface EvolutionListener {
        void onEvolutionTriggered(Pokemon evolvingPokemon, String newPokemonName);
    }

    public EvolutionScreen(Pokemon evolvingPokemon, String newPokemonName, Skin skin, Runnable onComplete) {
        this.evolvingPokemon = evolvingPokemon;
        this.newPokemonName = newPokemonName;
        this.skin = skin;
        this.onComplete = onComplete;

        setFillParent(true);
        setBackground(skin.newDrawable("white", new Color(0, 0, 0, 0.9f)));
        buildUI();
    }

    private void buildUI() {
        // Message Label
        messageLabel = new Label("", skin);
        messageLabel.setFontScale(2.0f);
        messageLabel.setAlignment(Align.center);
        add(messageLabel).expandX().top().padTop(50).row();

        // Pokémon Sprites in a Stack
        Stack pokemonStack = new Stack();
        TextureRegion oldSprite = evolvingPokemon.getFrontSprite();
        oldPokemonImage = new Image(oldSprite);
        oldPokemonImage.setScaling(Scaling.fit);

        TextureRegion newSprite = TextureManager.getPokemonfront().findRegion(newPokemonName.toUpperCase() + "_front");
        newPokemonImage = new Image(newSprite);
        newPokemonImage.setScaling(Scaling.fit);
        newPokemonImage.setVisible(false); // Start hidden

        pokemonStack.add(oldPokemonImage);
        pokemonStack.add(newPokemonImage);

        add(pokemonStack).expand().center();
        add().expandY(); // Pushes the stack up a bit
    }

    public void startAnimation() {
        // getStage().addActor(this); // <-- REMOVE THIS LINE

        // The sequence of actions for the evolution animation
        SequenceAction sequence = Actions.sequence(
            // 1. Initial message
            Actions.run(() -> messageLabel.setText("What? " + evolvingPokemon.getName() + " is evolving!")),
            Actions.delay(2.5f),

            // 2. The "cool" morphing effect
            Actions.run(this::runMorphEffect),
            Actions.delay(3.0f), // Wait for the morph effect to complete

            // 3. Confirmation message
            Actions.run(() -> {
                AudioManager.getInstance().playSound(AudioManager.SoundEffect.BATTLE_WIN); // Play a success sound
                messageLabel.setText("Congratulations! Your " + evolvingPokemon.getName() + " evolved into " + newPokemonName + "!");
            }),
            Actions.delay(3.5f),

            // 4. Finalize and clean up
            Actions.fadeOut(0.5f),
            Actions.run(() -> {
                if(onComplete != null) {
                    onComplete.run();
                }
                remove(); // Remove this screen from the stage
            })
        );

        this.addAction(sequence);
    }


    private void runMorphEffect() {
        messageLabel.setText(""); // Clear the message during the animation

        // --- Old Pokémon Actions ---
        // Creates a rapid flashing and pulsing effect before shrinking away
        oldPokemonImage.addAction(Actions.sequence(
            Actions.repeat(4, Actions.sequence(
                Actions.color(Color.WHITE, 0.15f),
                Actions.scaleTo(1.1f, 1.1f, 0.15f),
                Actions.color(Color.BLUE, 0.15f), // Use original color if possible, blue is a good substitute
                Actions.scaleTo(1.0f, 1.0f, 0.15f)
            )),
            Actions.parallel(
                Actions.scaleTo(0, 0, 0.5f),
                Actions.fadeOut(0.5f)
            )
        ));

        // --- New Pokémon Actions ---
        // Fades in and grows from the center, timed to start as the old one shrinks
        newPokemonImage.setVisible(true);
        newPokemonImage.setColor(1,1,1,0);
        newPokemonImage.setScale(0f);
        newPokemonImage.addAction(Actions.sequence(
            Actions.delay(2.0f), // Wait for the old one to almost finish its effect
            Actions.parallel(
                Actions.scaleTo(1, 1, 0.8f),
                Actions.fadeIn(0.8f)
            )
        ));
    }


}
