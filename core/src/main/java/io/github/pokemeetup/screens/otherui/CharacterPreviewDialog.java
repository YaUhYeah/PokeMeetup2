package io.github.pokemeetup.screens.otherui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Timer;
import io.github.pokemeetup.system.gameplay.PlayerAnimations;

import java.util.Arrays;

public class CharacterPreviewDialog extends Dialog {
    private static final float PREVIEW_WIDTH = 64f;
    private static final float PREVIEW_HEIGHT = 96f;
    private static final float BUTTON_WIDTH = 120f;
    private static final float BUTTON_HEIGHT = 40f;

    private final Stage stage;
    private final Skin skin;
    private String selectedCharacterType = "boy";
    private float stateTime = 0f;
    private Table previewTable;
    private PlayerAnimations boyAnimations;
    private PlayerAnimations girlAnimations;
    private PlayerAnimations currentAnimations;
    private TextureRegion currentFrame;
    private String currentAnimation = "down";
    private boolean isAnimating = true;
    private TextButton boyButton;
    private TextButton girlButton;

    public CharacterPreviewDialog(Stage stage, Skin skin, DialogCallback callback) {
        super("Choose Your Character", skin);
        this.stage = stage;
        this.skin = skin;
        boyAnimations = new PlayerAnimations("boy");
        girlAnimations = new PlayerAnimations("girl");
        currentAnimations = boyAnimations;

        createLayout();
        setupPreviewArea();
        setupConfirmationButtons(callback);
        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                updatePreviewAnimation();
            }
        }, 0, 0.016f);
    }

    private void createLayout() {
        Table mainTable = getContentTable();
        mainTable.pad(10);
        Table buttonTable = new Table();
        buttonTable.defaults().pad(5).width(BUTTON_WIDTH).height(BUTTON_HEIGHT);

        boyButton = new TextButton("Boy", skin);
        girlButton = new TextButton("Girl", skin);

        boyButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (boyButton.isChecked()) {
                    updateCharacterType("boy");
                    boyButton.setColor(Color.SKY);
                    girlButton.setColor(Color.WHITE);
                }
            }
        });

        girlButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (girlButton.isChecked()) {
                    updateCharacterType("girl");
                    girlButton.setColor(Color.SKY);
                    boyButton.setColor(Color.WHITE);
                }
            }
        });
        ButtonGroup<TextButton> characterGroup = new ButtonGroup<>(boyButton, girlButton);
        characterGroup.setMinCheckCount(1);
        characterGroup.setMaxCheckCount(1);

        buttonTable.add(boyButton);
        buttonTable.add(girlButton);
        previewTable = new Table();
        previewTable.setBackground(new TextureRegionDrawable(new TextureRegion(new Texture(createPreviewBackground()))));
        Table contentTable = new Table();
        contentTable.add(buttonTable).padBottom(10).row();
        contentTable.add(previewTable).size(PREVIEW_WIDTH + 20, PREVIEW_HEIGHT + 20).padBottom(10).row();
        Table controlsTable = new Table();
        TextButton prevAnim = new TextButton("<", skin);
        TextButton nextAnim = new TextButton(">", skin);
        Label animLabel = new Label("Down", skin);

        prevAnim.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                cycleAnimation(-1);
                updateAnimationLabel(animLabel);
            }
        });

        nextAnim.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                cycleAnimation(1);
                updateAnimationLabel(animLabel);
            }
        });

        controlsTable.add(prevAnim).width(30);
        controlsTable.add(animLabel).width(60).pad(0, 10, 0, 10);
        controlsTable.add(nextAnim).width(30);

        contentTable.add(controlsTable);

        mainTable.add(contentTable);
        boyButton.setChecked(true);
        boyButton.setColor(Color.SKY);
    }

    private void setupPreviewArea() {
        previewTable.add(new Image(currentAnimations.getStandingFrame("down"))).size(PREVIEW_WIDTH, PREVIEW_HEIGHT);
    }

    private void setupConfirmationButtons(DialogCallback callback) {
        getButtonTable().defaults().width(BUTTON_WIDTH).height(BUTTON_HEIGHT).pad(10);

        TextButton confirmButton = new TextButton("Confirm", skin);
        TextButton cancelButton = new TextButton("Cancel", skin);

        confirmButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                callback.onConfirm(selectedCharacterType);
                hide();
            }
        });

        cancelButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                hide();
            }
        });

        button(confirmButton, true);
        button(cancelButton, false);
    }

    private void updateCharacterType(String type) {
        selectedCharacterType = type;
        currentAnimations = type.equals("boy") ? boyAnimations : girlAnimations;
        stateTime = 0f;
    }

    private void updatePreviewAnimation() {
        if (isAnimating) {
            stateTime += Gdx.graphics.getDeltaTime();
            currentFrame = currentAnimations.getCurrentFrame(currentAnimation, true, false, stateTime);

            Gdx.app.postRunnable(() -> {
                previewTable.clearChildren();
                previewTable.add(new Image(currentFrame)).size(PREVIEW_WIDTH, PREVIEW_HEIGHT);
            });
        }
    }

    private void cycleAnimation(int direction) {
        String[] animTypes = {"down", "up", "left", "right"};
        int currentIndex = Arrays.asList(animTypes).indexOf(currentAnimation);
        if (currentIndex == -1) currentIndex = 0;
        currentIndex = (currentIndex + direction) % animTypes.length;
        if (currentIndex < 0) currentIndex = animTypes.length - 1;

        currentAnimation = animTypes[currentIndex];
        stateTime = 0f;
        if (currentAnimations != null) {
            currentFrame = currentAnimations.getCurrentFrame(currentAnimation, true, false, stateTime);
        }
    }

    private void updateAnimationLabel(Label label) {
        label.setText(currentAnimation.substring(0, 1).toUpperCase() + currentAnimation.substring(1));
    }

    private Pixmap createPreviewBackground() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(0.2f, 0.2f, 0.2f, 1);
        pixmap.fill();
        return pixmap;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);
    }

    public void dispose() {
        if (boyAnimations != null) {
            boyAnimations.dispose();
        }
        if (girlAnimations != null) {
            girlAnimations.dispose();
        }
    }

    public interface DialogCallback {
        void onConfirm(String characterType);
    }
}
