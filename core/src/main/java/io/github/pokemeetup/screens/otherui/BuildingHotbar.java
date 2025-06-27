package io.github.pokemeetup.screens.otherui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.utils.Scaling;
import io.github.pokemeetup.blocks.BuildingData;
import io.github.pokemeetup.blocks.BuildingTemplate;
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.textures.BlockTextureManager;
import io.github.pokemeetup.utils.textures.TextureManager;
import io.github.pokemeetup.utils.GameLogger;

import java.util.ArrayList;
import java.util.List;

public class BuildingHotbar extends Table {
    private static final float SLOT_SIZE = 50f;
    private static final float PADDING = 5f;
    private final List<BuildingData> buildingSlots = new ArrayList<>();
    private final ScrollPane scrollPane;
    private final Table slotsTable;
    private int selectedIndex = 0;

    public BuildingHotbar(Skin skin) {
        this.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("hotbar_bg")));
        this.pad(PADDING);
        slotsTable = new Table();
        ScrollPane.ScrollPaneStyle scrollStyle = new ScrollPane.ScrollPaneStyle(
            skin.get(ScrollPane.ScrollPaneStyle.class));
        scrollStyle.hScrollKnob = new TextureRegionDrawable(TextureManager.ui.findRegion("scrollbar_knob"));
        scrollPane = new ScrollPane(slotsTable, scrollStyle);
        scrollPane.setScrollingDisabled(false, true);
        scrollPane.setFadeScrollBars(false);
        float totalWidth = Gdx.graphics.getWidth() - 20f;  // a margin from screen edges
        float totalHeight = SLOT_SIZE + PADDING * 2;

        this.add(scrollPane).width(totalWidth).height(totalHeight);
        initializeBuildings();
        refreshSlots();

        GameLogger.info("BuildingHotbar initialized horizontally: " + totalWidth + "x" + totalHeight);
    }

    private void initializeBuildings() {
        BuildingData woodenHouse = new BuildingData(
            "wooden_house",
            "Wooden House",
            BuildingTemplate.createWoodenHouse()
        );
        woodenHouse.addRequirement("wooden_planks", 128);
        buildingSlots.add(woodenHouse);

        GameLogger.info("Added wooden house template to building hotbar");
    }

    private void refreshSlots() {
        slotsTable.clear();

        for (int i = 0; i < buildingSlots.size(); i++) {
            final int index = i;
            BuildingData building = buildingSlots.get(i);
            Table slot = new Table();
            slot.setBackground(new TextureRegionDrawable(
                TextureManager.ui.findRegion(index == selectedIndex ? "slot_selected" : "slot_normal")));
            Table previewContainer = createBuildingPreview(building);
            slot.add(previewContainer).size(SLOT_SIZE).pad(PADDING);
            slot.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    selectSlot(index);
                }
            });

            slotsTable.add(slot).size(SLOT_SIZE).pad(PADDING);
        }
    }

    private Table createBuildingPreview(BuildingData building) {
        Table container = new Table();
        if (building != null) {
            TextureRegion finalHouseTexture = TextureManager.buildings.findRegion(building.getId());
            if (finalHouseTexture != null) {
                Image previewImage = new Image(finalHouseTexture);
                previewImage.setScaling(Scaling.fit);
                container.add(previewImage).size(SLOT_SIZE, SLOT_SIZE).center();
            } else {
                Label label = new Label("No Preview", getSkin());
                container.add(label).center();
            }
        }
        return container;
    }



    @Override
    public void act(float delta) {
        super.act(delta);
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            scroll(-5);
        } else if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            scroll(5);
        }
    }

    public void selectSlot(int index) {
        if (index >= 0 && index < buildingSlots.size()) {
            selectedIndex = index;
            refreshSlots();
            GameLogger.info("Selected building slot: " + index);
        }
    }

    public BuildingData getSelectedBuilding() {
        if (selectedIndex >= 0 && selectedIndex < buildingSlots.size()) {
            return buildingSlots.get(selectedIndex);
        }
        return null;
    }

    public void scroll(int amount) {
        float currentX = scrollPane.getScrollX();
        scrollPane.setScrollX(currentX + amount);
    }
}
