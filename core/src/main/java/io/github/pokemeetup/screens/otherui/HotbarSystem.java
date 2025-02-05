package io.github.pokemeetup.screens.otherui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;
public class HotbarSystem {
    private static final int HOTBAR_SIZE = 9;
    private static final float SLOT_SIZE = 40f;
    private static final float VERTICAL_OFFSET = 30f; // Increased from 10f for better visibility
    private final Table hotbarTable;
    private final Skin skin;
    private int selectedSlot = 0;

    public HotbarSystem(Stage stage, Skin skin) {
        GameLogger.info("Creating HotbarSystem with skin: " + skin);
        this.hotbarTable = new Table();
        this.skin = skin;

        // Create container table for centered positioning
        Table containerTable = new Table();
        containerTable.setFillParent(true);
        containerTable.bottom().padBottom(VERTICAL_OFFSET);

        TextureRegion hotbarBg = TextureManager.ui.findRegion("hotbar_bg");
        if (hotbarBg == null) {
            GameLogger.error("Skin is missing region 'hotbar_bg'! Hotbar will not be visible.");
        } else {
            GameLogger.info("Found 'hotbar_bg' region.");
            hotbarTable.setBackground(new TextureRegionDrawable(hotbarBg));
        }

        // Add hotbar to centered container
        containerTable.add(hotbarTable).center();
        stage.addActor(containerTable);

        updateHotbar();
    }

    public void setSelectedSlot(int slot) {
        if (slot >= 0 && slot < HOTBAR_SIZE) {
            this.selectedSlot = slot;
            updateHotbar();
        }
    }

    public void updateHotbar() {
        hotbarTable.clear();
        hotbarTable.defaults().size(SLOT_SIZE).space(2f);

        // Create row for slots
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            boolean isSelected = i == selectedSlot;
            HotbarSlot slot = new HotbarSlot(isSelected, skin);

            ItemData item = GameContext.get().getPlayer().getInventory().getItemAt(i);
            if (item != null) {
                slot.setItem(item);
            }

            hotbarTable.add(slot);
        }

        // Force layout update
        hotbarTable.pack();
    }

    public int getSelectedSlot() {
        return selectedSlot;
    }

    public ItemData getSelectedItem() {
        return GameContext.get().getPlayer().getInventory().getItemAt(selectedSlot);
    }

    public void resize(int width, int height) {
        // No need to manually position since we're using Table layout
        hotbarTable.invalidate();
    }
}
