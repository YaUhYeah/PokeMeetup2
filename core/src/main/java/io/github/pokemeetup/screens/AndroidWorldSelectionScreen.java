package io.github.pokemeetup.screens;

import com.badlogic.gdx.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.screens.otherui.CharacterPreviewDialog;
import io.github.pokemeetup.system.data.WorldData;
import io.github.pokemeetup.utils.GameLogger;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class AndroidWorldSelectionScreen extends WorldSelectionScreen {
    private static final float MOBILE_PADDING = 16f;
    private static final float MOBILE_FONT_SCALE = 1.3f;
    private static final float WORLD_THUMBNAIL_SIZE = 100f;
    private static final float BUTTON_HEIGHT = 56f;
    private static final float FAB_SIZE = 64f; // Increased from 56f
    private static final float MOBILE_SPACING = 12f;

    private Table mobileLayout;
    private ScrollPane worldScrollPane;
    private Container<Table> detailPanel;
    private TextButton playFab;
    private TextButton createFab;
    private TextButton deleteFab;
    private boolean isTablet;
    private Table bottomBar;
    private Dialog activeDialog;
    private String currentFilter = "All";
    private Label emptyStateLabel;
    private Table worldListContent;

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

        createMobileTopBar();

        if (isTablet) {
            createTabletLayout();
        } else {
            createPhoneLayout();
        }

        stage.addActor(mobileLayout);

        // Create FABs after main layout
        createFloatingActionButtons();

        // Initial refresh
        refreshWorldList();
    }

    private void createMobileTopBar() {
        Table topBar = new Table();
        topBar.setBackground(createBackground(new Color(0.12f, 0.12f, 0.12f, 1f)));
        topBar.pad(MOBILE_PADDING);

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

        Label titleLabel = new Label("My Worlds", skin);
        titleLabel.setFontScale(MOBILE_FONT_SCALE * 1.4f);

        topBar.add(backBtn).size(48f);
        topBar.add(titleLabel).expandX().padLeft(MOBILE_SPACING);

        mobileLayout.add(topBar).fillX().height(72f).row();
    }

    private void createPhoneLayout() {
        Table contentArea = new Table();
        contentArea.pad(MOBILE_PADDING);
        contentArea.padBottom(100f); // Extra space for FABs

        createMobileTabs(contentArea);
        createMobileWorldList(contentArea, 1.0f);

        mobileLayout.add(contentArea).expand().fill().row();
    }

    private void createTabletLayout() {
        Table contentArea = new Table();
        contentArea.pad(MOBILE_PADDING);
        contentArea.padBottom(100f);

        Table leftColumn = new Table();
        createMobileTabs(leftColumn);
        createMobileWorldList(leftColumn, 0.5f);

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
        worldListContent = new Table();
        worldListContent.top();
        worldListContent.defaults().padBottom(MOBILE_SPACING);

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
            Table emptyState = new Table();
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

            Label nameLabel = new Label(selectedWorld.getName(), skin);
            nameLabel.setFontScale(MOBILE_FONT_SCALE * 1.5f);
            nameLabel.setAlignment(Align.center);
            details.add(nameLabel).padBottom(MOBILE_SPACING * 2).row();

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

    private void createFloatingActionButtons() {
        // Remove existing FABs
        if (playFab != null) playFab.remove();
        if (createFab != null) createFab.remove();
        if (deleteFab != null) deleteFab.remove();

        Table fabContainer = new Table();
        fabContainer.setFillParent(true);
        fabContainer.bottom().right().pad(MOBILE_PADDING * 2f);

        // Create FAB - always visible
        createFab = createFAB("+", new Color(0.2f, 0.4f, 0.8f, 1f), "Create World");
        createFab.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showCreateWorldDialog();
            }
        });

        if (selectedWorld != null) {
            // Delete FAB
            deleteFab = createFAB("🗑", new Color(0.8f, 0.2f, 0.2f, 1f), "Delete");
            deleteFab.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    showDeleteConfirmDialog();
                }
            });

            // Play FAB - larger and prominent
            playFab = createFAB("▶", new Color(0.2f, 0.7f, 0.2f, 1f), "Play");
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

            // Layout: vertical stack
            fabContainer.add(deleteFab).size(FAB_SIZE).padBottom(MOBILE_SPACING).row();
            fabContainer.add(createFab).size(FAB_SIZE).padBottom(MOBILE_SPACING).row();
            fabContainer.add(playFab).size(FAB_SIZE * 1.2f); // Larger play button
        } else {
            fabContainer.add(createFab).size(FAB_SIZE);
        }

        stage.addActor(fabContainer);
    }

    private TextButton createFAB(String icon, Color color, String tooltip) {
        TextButton.TextButtonStyle fabStyle = new TextButton.TextButtonStyle();
        fabStyle.up = skin.newDrawable("default-round-large", color);
        fabStyle.down = skin.newDrawable("default-round-large", color.cpy().mul(0.8f));
        fabStyle.over = skin.newDrawable("default-round-large", color.cpy().mul(1.1f));
        fabStyle.font = skin.getFont("default-font");

        TextButton fab = new TextButton(icon, fabStyle);
        fab.getLabel().setFontScale(2.2f);
        fab.setColor(1, 1, 1, 0.95f);

        // Add shadow effect
        fab.padBottom(4f);

        // Add tooltip on long press (simulated with hover for now)
        fab.addListener(new InputListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                fab.addAction(Actions.scaleTo(1.1f, 1.1f, 0.1f, Interpolation.fastSlow));
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                fab.addAction(Actions.scaleTo(1f, 1f, 0.1f, Interpolation.slowFast));
            }
        });

        return fab;
    }

    public void refreshWorldList() {
        if (worldListContent == null) return;

        worldListContent.clear();

        List<WorldData> worldList = new ArrayList<>(GameContext.get().getWorldManager().getWorlds().values());
        worldList.removeIf(world -> !shouldShowWorld(world));
        worldList.sort(Comparator.comparingLong(WorldData::getLastPlayed).reversed());

        if (worldList.isEmpty()) {
            // Create proper empty state
            Table emptyStateContainer = new Table();
            emptyStateContainer.setFillParent(true);

            if (placeholderRegion != null) {
                Image icon = new Image(placeholderRegion);
                icon.setColor(0.3f, 0.3f, 0.3f, 1f);
                emptyStateContainer.add(icon).size(120f).padBottom(20f).row();
            }

            emptyStateLabel = new Label("No worlds found", skin);
            emptyStateLabel.setFontScale(MOBILE_FONT_SCALE * 1.2f);
            emptyStateLabel.setAlignment(Align.center);
            emptyStateLabel.setColor(0.6f, 0.6f, 0.6f, 1f);
            emptyStateContainer.add(emptyStateLabel).row();

            Label instructionLabel = new Label("Tap + to create one!", skin);
            instructionLabel.setFontScale(MOBILE_FONT_SCALE);
            instructionLabel.setAlignment(Align.center);
            instructionLabel.setColor(0.5f, 0.5f, 0.5f, 1f);
            emptyStateContainer.add(instructionLabel).padTop(8f);

            worldListContent.add(emptyStateContainer).expand().center();
        } else {
            for (WorldData world : worldList) {
                Table entry = createMobileWorldEntry(world);
                worldListContent.add(entry).fillX().expandX();
                worldListContent.row();
            }
        }

        // Update FABs
        createFloatingActionButtons();

        // Update detail panel for tablets
        if (isTablet && detailPanel != null) {
            detailPanel.setActor(createDetailPanel());
        }

        // Force layout update
        worldListContent.invalidateHierarchy();
        worldScrollPane.invalidate();
        stage.act(0);
    }

    private Table createMobileWorldEntry(WorldData world) {
        Table entry = new Table();
        entry.setTouchable(Touchable.enabled);
        entry.pad(MOBILE_SPACING);

        Drawable normalBg = createRoundedBackground(new Color(0.15f, 0.15f, 0.15f, 0.8f));
        Drawable selectedBg = createRoundedBackground(new Color(0.2f, 0.4f, 0.7f, 0.9f));
        entry.setBackground(selectedWorld == world ? selectedBg : normalBg);

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

        Table infoTable = new Table();
        infoTable.left();

        Label nameLabel = new Label(world.getName(), skin);
        nameLabel.setFontScale(MOBILE_FONT_SCALE * 1.2f);
        nameLabel.setEllipsis(true);
        infoTable.add(nameLabel).left().colspan(3).padBottom(6f).row();

        Label lastPlayedLabel = new Label(formatRelativeDate(world.getLastPlayed()), skin);
        lastPlayedLabel.setFontScale(MOBILE_FONT_SCALE * 0.9f);
        lastPlayedLabel.setColor(0.6f, 0.6f, 0.6f, 1f);

        Label dot1 = new Label(" • ", skin);
        dot1.setFontScale(MOBILE_FONT_SCALE * 0.9f);
        dot1.setColor(0.4f, 0.4f, 0.4f, 1f);

        Label playTimeLabel = new Label(formatShortPlayTime(world.getPlayedTime()), skin);
        playTimeLabel.setFontScale(MOBILE_FONT_SCALE * 0.9f);
        playTimeLabel.setColor(0.6f, 0.6f, 0.6f, 1f);

        Table statsRow = new Table();
        statsRow.add(lastPlayedLabel);
        statsRow.add(dot1);
        statsRow.add(playTimeLabel);

        infoTable.add(statsRow).left().row();

        if (world.commandsAllowed()) {
            Label commandsLabel = new Label("⚡ Commands Enabled", skin);
            commandsLabel.setFontScale(MOBILE_FONT_SCALE * 0.85f);
            commandsLabel.setColor(0.7f, 0.7f, 0.2f, 1f);
            infoTable.add(commandsLabel).left().padTop(4f);
        }

        entry.add(thumbnailFrame).size(WORLD_THUMBNAIL_SIZE).padRight(MOBILE_SPACING);
        entry.add(infoTable).expand().fill();

        entry.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                selectWorld(world);
                refreshWorldList();

                entry.addAction(Actions.sequence(
                    Actions.scaleTo(0.95f, 0.95f, 0.05f, Interpolation.fastSlow),
                    Actions.scaleTo(1f, 1f, 0.05f, Interpolation.slowFast)
                ));
            }
        });

        return entry;
    }

    @Override
    protected void showCreateWorldDialog() {
        CharacterPreviewDialog characterDialog = new CharacterPreviewDialog(stage, skin,
            (selectedCharacterType) -> {
                showMobileWorldCreationDialog(selectedCharacterType);
            });
        characterDialog.show(stage);
    }

    private void showMobileWorldCreationDialog(String characterType) {
        Dialog dialog = new Dialog("Create New World", skin) {
            @Override
            protected void result(Object object) {
                if ((Boolean) object) {
                    TextField nameField = findActor("nameField");
                    CheckBox cheatsAllowed = findActor("cheatsAllowed");
                    TextField seedField = findActor("seedField");
                    TextField dialogUsernameField = findActor("usernameField");

                    boolean commandsEnabled = cheatsAllowed != null && cheatsAllowed.isChecked();
                    String worldName = nameField.getText().trim();
                    String seedText = seedField.getText().trim();
                    String username = dialogUsernameField.getText().trim();

                    if (worldName.isEmpty()) {
                        showError("World name cannot be empty");
                        return;
                    }
                    if (username.isEmpty()) {
                        username = "Player";
                    }

                    long seed;
                    if (seedText.isEmpty()) {
                        seed = System.currentTimeMillis();
                    } else {
                        try {
                            seed = Long.parseLong(seedText);
                        } catch (NumberFormatException e) {
                            showError("Seed must be a valid number");
                            return;
                        }
                    }

                    createNewWorld(worldName, seed, username, commandsEnabled, characterType);
                }
            }
        };

        // Make dialog mobile-friendly
        dialog.setMovable(false);
        dialog.setModal(true);
        dialog.setKeepWithinStage(true);

        Table content = dialog.getContentTable();
        content.pad(MOBILE_PADDING * 2);

        float inputWidth = Math.min(Gdx.graphics.getWidth() * 0.8f, 400f);

        TextField nameField = new TextField("", skin);
        nameField.setName("nameField");
        nameField.setMessageText("World name");

        TextField seedField = new TextField("", skin);
        seedField.setName("seedField");
        seedField.setMessageText("Seed (optional)");

        TextField dialogUsernameField = new TextField("", skin);
        dialogUsernameField.setName("usernameField");
        dialogUsernameField.setMessageText("Your username");

        CheckBox cheatsAllowed = new CheckBox(" Enable Commands", skin);
        cheatsAllowed.setName("cheatsAllowed");
        cheatsAllowed.setChecked(false);
        cheatsAllowed.getLabel().setFontScale(MOBILE_FONT_SCALE);
        cheatsAllowed.getImageCell().size(32f);

        // Add fields with proper mobile spacing
        content.add(createMobileLabel("World Name:")).left().padBottom(8f).row();
        content.add(nameField).width(inputWidth).height(BUTTON_HEIGHT).padBottom(20f).row();

        content.add(createMobileLabel("Seed (optional):")).left().padBottom(8f).row();
        content.add(seedField).width(inputWidth).height(BUTTON_HEIGHT).padBottom(20f).row();

        content.add(createMobileLabel("Username:")).left().padBottom(8f).row();
        content.add(dialogUsernameField).width(inputWidth).height(BUTTON_HEIGHT).padBottom(20f).row();

        content.add(cheatsAllowed).left().padBottom(20f).row();

        // Mobile-friendly buttons
        TextButton createBtn = new TextButton("Create", skin);
        TextButton cancelBtn = new TextButton("Cancel", skin);
        createBtn.getLabel().setFontScale(MOBILE_FONT_SCALE);
        cancelBtn.getLabel().setFontScale(MOBILE_FONT_SCALE);

        dialog.button(createBtn, true).getButtonTable().getCell(createBtn).size(150f, BUTTON_HEIGHT).pad(10f);
        dialog.button(cancelBtn, false).getButtonTable().getCell(cancelBtn).size(150f, BUTTON_HEIGHT).pad(10f);

        dialog.show(stage);
        activeDialog = dialog;

        // Center dialog
        dialog.setPosition(
            (Gdx.graphics.getWidth() - dialog.getWidth()) / 2f,
            (Gdx.graphics.getHeight() - dialog.getHeight()) / 2f
        );
    }

    private Label createMobileLabel(String text) {
        Label label = new Label(text, skin);
        label.setFontScale(MOBILE_FONT_SCALE);
        return label;
    }

    @Override
    protected void showDeleteConfirmDialog() {
        Dialog dialog = new Dialog("Delete World", skin) {
            @Override
            protected void result(Object object) {
                if ((Boolean) object) {
                    deleteSelectedWorld();
                }
            }
        };

        dialog.setMovable(false);
        dialog.setModal(true);

        Label messageLabel = new Label("Are you sure you want to delete\n'" + selectedWorld.getName() + "'?\n\nThis cannot be undone!", skin);
        messageLabel.setFontScale(MOBILE_FONT_SCALE);
        messageLabel.setAlignment(Align.center);
        messageLabel.setWrap(true);

        dialog.getContentTable().pad(MOBILE_PADDING * 2);
        dialog.getContentTable().add(messageLabel).width(Math.min(Gdx.graphics.getWidth() * 0.8f, 400f));

        TextButton deleteBtn = new TextButton("Delete", skin);
        TextButton cancelBtn = new TextButton("Cancel", skin);
        deleteBtn.getLabel().setFontScale(MOBILE_FONT_SCALE);
        cancelBtn.getLabel().setFontScale(MOBILE_FONT_SCALE);
        deleteBtn.setColor(1f, 0.3f, 0.3f, 1f);

        dialog.button(deleteBtn, true).getButtonTable().getCell(deleteBtn).size(150f, BUTTON_HEIGHT).pad(10f);
        dialog.button(cancelBtn, false).getButtonTable().getCell(cancelBtn).size(150f, BUTTON_HEIGHT).pad(10f);

        dialog.show(stage);
        activeDialog = dialog;

        dialog.setPosition(
            (Gdx.graphics.getWidth() - dialog.getWidth()) / 2f,
            (Gdx.graphics.getHeight() - dialog.getHeight()) / 2f
        );
    }

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
        if (activeDialog != null && activeDialog.isVisible()) {
            activeDialog.setPosition(
                (width - activeDialog.getWidth()) / 2f,
                (height - activeDialog.getHeight()) / 2f
            );
        }
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
