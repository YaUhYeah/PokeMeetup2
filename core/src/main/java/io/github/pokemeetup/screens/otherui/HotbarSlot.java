package io.github.pokemeetup.screens.otherui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.utils.textures.TextureManager;

public class HotbarSlot extends Stack {
    private static final float SLOT_SIZE = 40f;
    private final Table content;
    private final Image background;
    private final Label countLabel;
    private final Skin skin;

    public HotbarSlot(boolean selected, Skin skin) {
        this.skin = skin;
        // Get the background region based on selection.
        TextureRegion bgRegion = TextureManager.ui.findRegion(
            selected ? "slot_selected" : "slot_normal"
        );
        background = new Image(bgRegion);
        addActor(background);

        content = new Table();
        content.setFillParent(true);
        addActor(content);

        Label.LabelStyle labelStyle = new Label.LabelStyle(skin.getFont("default-font"), Color.WHITE);
        countLabel = new Label("", labelStyle);
        countLabel.setVisible(false);
        countLabel.setFontScale(0.8f);

        setSize(SLOT_SIZE, SLOT_SIZE);
        setTouchable(Touchable.enabled);
    }

    public void setItem(ItemData item) {
        content.clear();
        if (item == null) return;

        // Try to obtain the texture for the item.
        TextureRegion texture = TextureManager.items.findRegion(item.getItemId().toLowerCase() + "_item");
        if (texture == null) {
            texture = TextureManager.items.findRegion(item.getItemId().toLowerCase());
        }
        if (texture != null) {
            Image itemImage = new Image(texture);
            content.add(itemImage).size(32).expand().center();
            if (item.getCount() > 1) {
                countLabel.setText(String.valueOf(item.getCount()));
                countLabel.setVisible(true);
                content.add(countLabel).expand().bottom().right().pad(2);
            } else {
                countLabel.setVisible(false);
            }
        }
        content.invalidate();
    }

    public void clear() {
        content.clear();
        countLabel.setVisible(false);
    }
}
