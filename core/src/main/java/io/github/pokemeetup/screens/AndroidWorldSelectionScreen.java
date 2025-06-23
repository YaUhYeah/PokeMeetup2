package io.github.pokemeetup.screens;

import com.badlogic.gdx.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.system.data.WorldData;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

import static io.github.pokemeetup.screens.AndroidLoginScreen.INPUT_FIELD_HEIGHT;

public class AndroidWorldSelectionScreen extends WorldSelectionScreen {

    // Android-specific constants - optimized for better visuals
    private static final float MOBILE_PADDING = 16f;
    private static final float MOBILE_FONT_SCALE = 1.3f;
    private static final float WORLD_ENTRY_HEIGHT = 120f;
    private static final float WORLD_THUMBNAIL_SIZE = 100f; // Much larger thumbnail
    private static final float BUTTON_HEIGHT = 56f;
    private static final float FAB_SIZE = 56f;
    private static final float MOBILE_SPACING = 12f;

    private Table mobileLayout;
    private ScrollPane worldScrollPane;
    private Container<Table> detailPanel;
    private TextButton playFab;
    private TextButton createFab;
    private boolean isTablet;
    private Table bottomBar;
    private Dialog activeDialog;
    private String currentFilter = "All";

    public AndroidWorldSelectionScreen(CreatureCaptureGame game) {
        super(game);
        detectDeviceType();
        setupMobileUI();
    }

    private void detectDeviceType() {
        float screenInches = calculateScreenSizeInches();
        isTablet = screenInches >= 7.0f;
        GameLogger.info("Device type: " + (isTablet ? "Tablet" : "Phone"));
    }

    private float calculateScreenSizeInches() {
        float widthPixels = Gdx.graphics.getWidth();
        float heightPixels = Gdx.graphics.getHeight();
        float widthDpi = Gdx.graphics.getPpiX();
        float heightDpi = Gdx.graphics.getPpiY();
        float widthInches = widthPixels / widthDpi;
        float heightInches = heightPixels / heightDpi;
        return (float) Math.sqrt(widthInches * widthInches + heightInches * heightInches);
    }

    private void setupMobileUI() {
        stage.clear();

        mobileLayout = new Table();
        mobileLayout.setFillParent(true);
        mobileLayout.setBackground(createBackground(new Color(0.08f, 0.08f, 0.08f, 1f)));

        // Top bar
        createMobileTopBar();

        // Main content
        if (isTablet) {
            createTabletLayout();
        } else {
            createPhoneLayout();
        }

        // Bottom delete bar (hidden initially)
        createBottomActionBar();

        stage.addActor(mobileLayout);

        // Floating action buttons
        createFloatingActionButtons();

        refreshWorldList();
    }

    private void createMobileTopBar() {
        Table topBar = new Table();
        topBar.setBackground(createBackground(new Color(0.12f, 0.12f, 0.12f, 1f)));
        topBar.pad(MOBILE_PADDING);

        // Back button
        TextButton backBtn = new TextButton("◄", skin);
        backBtn.getLabel().setFontScale(2f);
        backBtn.pad(12f);
        backBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.setScreen(new ModeSelectionScreen(game));
                dispose();
            }
        });

        // Title
        Label titleLabel = new Label("My Worlds", skin);
        titleLabel.setFontScale(MOBILE_FONT_SCALE * 1.4f);

        topBar.add(backBtn).size(48f);
        topBar.add(titleLabel).expandX().padLeft(MOBILE_SPACING);

        mobileLayout.add(topBar).fillX().height(72f).row();
    }

    private void createPhoneLayout() {
        Table contentArea = new Table();
        contentArea.pad(MOBILE_PADDING);
        contentArea.padBottom(80f); // Space for FAB

        // Tabs
        createMobileTabs(contentArea);

        // World list
        createMobileWorldList(contentArea, 1.0f);

        mobileLayout.add(contentArea).expand().fill().row();
    }

    private void createTabletLayout() {
        Table contentArea = new Table();
        contentArea.pad(MOBILE_PADDING);
        contentArea.padBottom(80f);

        // Two-column layout
        Table leftColumn = new Table();
        createMobileTabs(leftColumn);
        createMobileWorldList(leftColumn, 0.5f);

        // Detail panel
        detailPanel = new Container<>();
        Table detailContent = createDetailPanel();
        detailPanel.setActor(detailContent);
        detailPanel.setBackground(createRoundedBackground(new Color(0.15f, 0.15f, 0.15f, 0.9f)));
        detailPanel.pad(MOBILE_PADDING * 1.5f);

        contentArea.add(leftColumn).expandY().fillY().width(Value.percentWidth(0.5f, contentArea)).padRight(MOBILE_SPACING);
        contentArea.add(detailPanel).expand().fill();

        mobileLayout.add(contentArea).expand().fill().row();
    }

    private void createMobileTabs(Table container) {
        Table tabBar = new Table();
        tabBar.setBackground(createBackground(new Color(0.15f, 0.15f, 0.15f, 1f)));

        String[] tabs = {"All", "Recent", "Multiplayer"};
        ButtonGroup<TextButton> tabGroup = new ButtonGroup<>();

        for (String tab : tabs) {
            TextButton tabButton = createTabButton(tab, skin);
            tabGroup.add(tabButton);
            tabBar.add(tabButton).expandX().fillX().uniformX();

            tabButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (tabButton.isChecked()) {
                        currentFilter = tab;
                        currentTab = tab;
                        refreshWorldList();
                    }
                }
            });
        }

        tabGroup.setMaxCheckCount(1);
        tabGroup.setMinCheckCount(1);
        tabGroup.setUncheckLast(true);
        tabGroup.getButtons().get(0).setChecked(true);

        container.add(tabBar).fillX().height(56f).padBottom(MOBILE_SPACING).row();
    }

    private void createMobileWorldList(Table container, float widthPercent) {
        Table worldListContent = new Table();
        worldListContent.top();
        worldListContent.defaults().padBottom(MOBILE_SPACING);

        // Scroll pane
        ScrollPane.ScrollPaneStyle scrollStyle = new ScrollPane.ScrollPaneStyle();
        scrollStyle.background = createBackground(new Color(0.1f, 0.1f, 0.1f, 0.3f));

        worldScrollPane = new ScrollPane(worldListContent, scrollStyle);
        worldScrollPane.setFadeScrollBars(true);
        worldScrollPane.setScrollingDisabled(true, false);
        worldScrollPane.setOverscroll(false, true);
        worldScrollPane.setSmoothScrolling(true);
        worldScrollPane.setFlickScroll(true);

        container.add(worldScrollPane).expand().fill();
    }

    private Table createDetailPanel() {
        Table details = new Table();
        details.pad(MOBILE_PADDING);

        if (selectedWorld == null) {
            // Empty state with icon and text
            Table emptyState = new Table();

            // Add a world icon or placeholder graphic if available
            if (placeholderRegion != null) {
                Image worldIcon = new Image(placeholderRegion);
                worldIcon.setColor(0.4f, 0.4f, 0.4f, 1f);
                emptyState.add(worldIcon).size(120f).padBottom(MOBILE_SPACING * 2).row();
            }

            Label placeholder = new Label("Select a world", skin);
            placeholder.setFontScale(MOBILE_FONT_SCALE * 1.3f);
            placeholder.setAlignment(Align.center);
            placeholder.setColor(0.7f, 0.7f, 0.7f, 1f);
            emptyState.add(placeholder).row();

            Label subText = new Label("to view details", skin);
            subText.setFontScale(MOBILE_FONT_SCALE);
            subText.setAlignment(Align.center);
            subText.setColor(0.5f, 0.5f, 0.5f, 1f);
            emptyState.add(subText).padTop(4f);

            details.add(emptyState).expand().center();
        } else {
            // Large thumbnail with frame
            Table thumbnailFrame = new Table();
            thumbnailFrame.setBackground(createBackground(new Color(0.05f, 0.05f, 0.05f, 1f)));

            Image thumbnail = new Image();
            FileHandle thumbnailFile = Gdx.files.local("thumbnails/" + selectedWorld.getName() + ".png");
            if (thumbnailFile.exists()) {
                thumbnail.setDrawable(new TextureRegionDrawable(new Texture(thumbnailFile)));
            } else if (placeholderRegion != null) {
                thumbnail.setDrawable(new TextureRegionDrawable(placeholderRegion));
            }
            thumbnail.setScaling(Scaling.fit);

            thumbnailFrame.add(thumbnail).size(220f).pad(8f);
            details.add(thumbnailFrame).padBottom(MOBILE_SPACING * 2).row();

            // World name
            Label nameLabel = new Label(selectedWorld.getName(), skin);
            nameLabel.setFontScale(MOBILE_FONT_SCALE * 1.5f);
            nameLabel.setAlignment(Align.center);
            details.add(nameLabel).padBottom(MOBILE_SPACING * 2).row();

            // Info table
            Table infoTable = new Table();
            infoTable.defaults().padBottom(MOBILE_SPACING);

            addDetailRow(infoTable, "Last Played", formatDate(selectedWorld.getLastPlayed()));
            addDetailRow(infoTable, "Seed", String.valueOf(getSeedFromWorld(selectedWorld)));
            addDetailRow(infoTable, "Play Time", formatPlayedTime(selectedWorld.getPlayedTime()));
            addDetailRow(infoTable, "Commands", selectedWorld.commandsAllowed() ? "Enabled" : "Disabled");

            details.add(infoTable).expand().top();
        }

        return details;
    }

    private void addDetailRow(Table table, String label, String value) {
        Table row = new Table();

        Label labelWidget = new Label(label + ":", skin);
        labelWidget.setFontScale(MOBILE_FONT_SCALE * 0.9f);
        labelWidget.setColor(0.5f, 0.5f, 0.5f, 1f);

        Label valueWidget = new Label(value, skin);
        valueWidget.setFontScale(MOBILE_FONT_SCALE);
        valueWidget.setColor(0.9f, 0.9f, 0.9f, 1f);

        row.add(labelWidget).left().width(120f);
        row.add(valueWidget).expandX().left();

        table.add(row).fillX().row();
    }

    private void createBottomActionBar() {
        bottomBar = new Table();
        bottomBar.setBackground(createBackground(new Color(0.12f, 0.12f, 0.12f, 1f)));
        bottomBar.pad(MOBILE_PADDING);

        TextButton deleteBtn = new TextButton("Delete World", skin);
        deleteBtn.getLabel().setFontScale(MOBILE_FONT_SCALE);
        deleteBtn.pad(12f, 24f, 12f, 24f);
        deleteBtn.setColor(1f, 0.3f, 0.3f, 1f);

        deleteBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (selectedWorld != null) {
                    showDeleteConfirmDialog();
                }
            }
        });

        bottomBar.add(deleteBtn).expandX().fillX().height(BUTTON_HEIGHT);
        bottomBar.setVisible(false);

        mobileLayout.add(bottomBar).fillX().height(80f);
    }

    private void createFloatingActionButtons() {
        if (playFab != null) playFab.remove();
        if (createFab != null) createFab.remove();

        // FAB container
        Table fabContainer = new Table();
        fabContainer.setFillParent(true);
        fabContainer.bottom().right().pad(MOBILE_PADDING * 1.5f);

        if (selectedWorld != null) {
            // Play FAB (primary action)
            playFab = createFAB("▶", new Color(0.2f, 0.7f, 0.2f, 1f));
            playFab.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (selectedWorld != null) {
                        String username = selectedWorld.getPlayers().isEmpty()
                            ? "Player"
                            : selectedWorld.getPlayers().keySet().iterator().next();
                        loadSelectedWorld(username);
                    }
                }
            });

            // Create FAB (secondary)
            createFab = createFAB("+", new Color(0.2f, 0.4f, 0.8f, 1f));
            createFab.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    showCreateWorldDialog();
                }
            });

            fabContainer.add(createFab).size(FAB_SIZE).padBottom(MOBILE_SPACING).row();
            fabContainer.add(playFab).size(FAB_SIZE + 12); // Larger play button
        } else {
            // Only create FAB
            createFab = createFAB("+", new Color(0.2f, 0.4f, 0.8f, 1f));
            createFab.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    showCreateWorldDialog();
                }
            });

            fabContainer.add(createFab).size(FAB_SIZE);
        }

        stage.addActor(fabContainer);
    }

    private TextButton createFAB(String text, Color color) {
        TextButton.TextButtonStyle fabStyle = new TextButton.TextButtonStyle();
        fabStyle.up = skin.newDrawable("default-round-large", color);
        fabStyle.down = skin.newDrawable("default-round-large", color.cpy().mul(0.8f));
        fabStyle.font = skin.getFont("default-font");

        TextButton fab = new TextButton(text, fabStyle);
        fab.getLabel().setFontScale(2f);
        fab.setColor(1, 1, 1, 0.95f);

        return fab;
    }

    public void refreshWorldList() {
        if (worldScrollPane == null) return;

        Table content = (Table) worldScrollPane.getWidget();
        content.clear();

        List<WorldData> worldList = new ArrayList<>(GameContext.get().getWorldManager().getWorlds().values());
        worldList.removeIf(world -> !shouldShowWorld(world));

        // Sort by last played
        worldList.sort(Comparator.comparingLong(WorldData::getLastPlayed).reversed());

        if (worldList.isEmpty()) {
            Label emptyLabel = new Label("No worlds found.\nTap + to create one!", skin);
            emptyLabel.setFontScale(MOBILE_FONT_SCALE * 1.1f);
            emptyLabel.setAlignment(Align.center);
            emptyLabel.setWrap(true);
            emptyLabel.setColor(0.5f, 0.5f, 0.5f, 1f);
            content.add(emptyLabel).expand().center().pad(60f);
        } else {
            for (WorldData world : worldList) {
                Table entry = createMobileWorldEntry(world);
                content.add(entry).fillX().expandX();
                content.row();
            }
        }

        // Update UI
        createFloatingActionButtons();
        if (bottomBar != null) {
            bottomBar.setVisible(selectedWorld != null);
        }

        if (isTablet && detailPanel != null) {
            detailPanel.setActor(createDetailPanel());
        }
    }

    private Table createMobileWorldEntry(WorldData world) {
        Table entry = new Table();
        entry.setTouchable(Touchable.enabled);
        entry.pad(MOBILE_SPACING);

        // Background
        Drawable normalBg = createRoundedBackground(new Color(0.15f, 0.15f, 0.15f, 0.8f));
        Drawable selectedBg = createRoundedBackground(new Color(0.2f, 0.4f, 0.7f, 0.9f));
        entry.setBackground(selectedWorld == world ? selectedBg : normalBg);

        // Large thumbnail with frame
        Table thumbnailFrame = new Table();
        thumbnailFrame.setBackground(createBackground(new Color(0.05f, 0.05f, 0.05f, 1f)));

        Image thumbnail = new Image();
        FileHandle thumbnailFile = Gdx.files.local("thumbnails/" + world.getName() + ".png");
        if (thumbnailFile.exists()) {
            thumbnail.setDrawable(new TextureRegionDrawable(new Texture(thumbnailFile)));
        } else if (placeholderRegion != null) {
            thumbnail.setDrawable(new TextureRegionDrawable(placeholderRegion));
        }
        thumbnail.setScaling(Scaling.fit);

        thumbnailFrame.add(thumbnail).size(WORLD_THUMBNAIL_SIZE - 8).pad(4f);

        // Info section - FULLY HORIZONTAL
        Table infoTable = new Table();
        infoTable.left();

        // World name - larger
        Label nameLabel = new Label(world.getName(), skin);
        nameLabel.setFontScale(MOBILE_FONT_SCALE * 1.2f);
        nameLabel.setEllipsis(true);

        // First row: Name only
        infoTable.add(nameLabel).left().colspan(3).padBottom(6f).row();

        // Second row: All stats in one line
        Label lastPlayedLabel = new Label(formatRelativeDate(world.getLastPlayed()), skin);
        lastPlayedLabel.setFontScale(MOBILE_FONT_SCALE * 0.9f);
        lastPlayedLabel.setColor(0.6f, 0.6f, 0.6f, 1f);

        Label dot1 = new Label(" • ", skin);
        dot1.setFontScale(MOBILE_FONT_SCALE * 0.9f);
        dot1.setColor(0.4f, 0.4f, 0.4f, 1f);

        Label playTimeLabel = new Label(formatShortPlayTime(world.getPlayedTime()), skin);
        playTimeLabel.setFontScale(MOBILE_FONT_SCALE * 0.9f);
        playTimeLabel.setColor(0.6f, 0.6f, 0.6f, 1f);

        Label dot2 = new Label(" • ", skin);
        dot2.setFontScale(MOBILE_FONT_SCALE * 0.9f);
        dot2.setColor(0.4f, 0.4f, 0.4f, 1f);

        Label seedLabel = new Label("Seed: " + getSeedFromWorld(world), skin);
        seedLabel.setFontScale(MOBILE_FONT_SCALE * 0.9f);
        seedLabel.setColor(0.6f, 0.6f, 0.6f, 1f);

        // Add all to same row
        Table statsRow = new Table();
        statsRow.add(lastPlayedLabel);
        statsRow.add(dot1);
        statsRow.add(playTimeLabel);
        statsRow.add(dot2);
        statsRow.add(seedLabel);

        infoTable.add(statsRow).left().row();

        // Third row: Commands indicator if enabled
        if (world.commandsAllowed()) {
            Label commandsLabel = new Label("⚡ Commands Enabled", skin);
            commandsLabel.setFontScale(MOBILE_FONT_SCALE * 0.85f);
            commandsLabel.setColor(0.7f, 0.7f, 0.2f, 1f);
            infoTable.add(commandsLabel).left().padTop(4f);
        }

        // Layout
        entry.add(thumbnailFrame).size(WORLD_THUMBNAIL_SIZE).padRight(MOBILE_SPACING);
        entry.add(infoTable).expand().fill();

        // Touch feedback
        entry.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                selectWorld(world);
                refreshWorldList();

                // Animate
                entry.addAction(Actions.sequence(
                    Actions.scaleTo(0.95f, 0.95f, 0.05f, Interpolation.fastSlow),
                    Actions.scaleTo(1f, 1f, 0.05f, Interpolation.slowFast)
                ));
            }
        });

        return entry;
    }

    // Helper methods
    private String formatRelativeDate(long timestamp) {
        if (timestamp == 0) return "Never";

        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        if (diff < 60000) return "Just now";
        if (diff < 3600000) return (diff / 60000) + "m ago";
        if (diff < 86400000) return (diff / 3600000) + "h ago";
        if (diff < 604800000) return (diff / 86400000) + "d ago";

        return new SimpleDateFormat("MMM d").format(new Date(timestamp));
    }

    private String formatShortPlayTime(long millis) {
        long minutes = millis / 60000;
        long hours = minutes / 60;

        if (hours == 0) return minutes + "m played";
        if (hours < 100) return hours + "h " + (minutes % 60) + "m";

        return hours + "h";
    }

    private TextButton createTabButton(String text, Skin skin) {
        TextButton.TextButtonStyle tabStyle = new TextButton.TextButtonStyle(skin.get(TextButton.TextButtonStyle.class));
        tabStyle.font.getData().setScale(MOBILE_FONT_SCALE);
        tabStyle.up = null;
        tabStyle.down = createBackground(new Color(0.2f, 0.4f, 0.7f, 0.3f));
        tabStyle.checked = createBackground(new Color(0.2f, 0.4f, 0.7f, 0.6f));

        TextButton button = new TextButton(text, tabStyle);
        button.pad(14f);
        return button;
    }

    private Drawable createRoundedBackground(Color color) {
        return skin.newDrawable("default-round", color);
    }

    private Drawable createBackground(Color color) {
        return skin.newDrawable("white", color);
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        createFloatingActionButtons();
    }

    @Override
    public void show() {
        super.show();
        stage.getRoot().setColor(1, 1, 1, 0);
        stage.getRoot().addAction(Actions.fadeIn(0.3f));
    }

    @Override
    public void dispose() {
        if (activeDialog != null) {
            activeDialog.hide();
        }
        super.dispose();
    }
}
