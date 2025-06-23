package io.github.pokemeetup.screens;

import com.badlogic.gdx.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
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

    // Android-specific constants
    private static final float MOBILE_PADDING = 24f;
    private static final float MOBILE_FONT_SCALE = 1.4f;
    private static final float WORLD_ENTRY_HEIGHT = 120f;
    private static final float BUTTON_HEIGHT = 72f;
    private static final float FAB_SIZE = 64f;
    private static final float MOBILE_SPACING = 16f;

    private Table mobileLayout;
    private ScrollPane worldScrollPane;
    private Container<Table> detailPanel;
    private TextButton playFab;
    private TextButton createFab;
    private boolean isTablet;
    private Table bottomBar;
    private Dialog activeDialog;

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
        mobileLayout.setBackground(new TextureRegionDrawable(
            createBackground(new Color(0.15f, 0.15f, 0.15f, 1f))
        ));

        // Top bar with title and back button
        createMobileTopBar();

        // Main content area
        if (isTablet) {
            createTabletLayout();
        } else {
            createPhoneLayout();
        }

        // Bottom action bar
        createBottomActionBar();

        stage.addActor(mobileLayout);

        // Floating action buttons
        createFloatingActionButtons();

        // Load worlds
        refreshWorldList();
    }

    private void createMobileTopBar() {
        Table topBar = new Table();
        topBar.setBackground(new TextureRegionDrawable(
            createBackground(new Color(0.2f, 0.2f, 0.2f, 1f))
        ));
        topBar.pad(MOBILE_PADDING);

        // Back button
        TextButton backBtn = createIconButton("←", skin);
        backBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.setScreen(new ModeSelectionScreen(game));
                dispose();
            }
        });

        // Title
        Label titleLabel = new Label("My Worlds", skin);
        titleLabel.setFontScale(MOBILE_FONT_SCALE * 1.2f);

        topBar.add(backBtn).size(48f);
        topBar.add(titleLabel).expandX().padLeft(MOBILE_SPACING);

        mobileLayout.add(topBar).fillX().height(80f).row();
    }

    private void createPhoneLayout() {
        // Single column layout for phones
        Table contentArea = new Table();
        contentArea.pad(MOBILE_PADDING);

        // Tabs for filtering
        createMobileTabs(contentArea);

        // World list takes full width
        createMobileWorldList(contentArea, 1.0f);

        mobileLayout.add(contentArea).expand().fill().row();
    }

    private void createTabletLayout() {
        // Two-column layout for tablets
        Table contentArea = new Table();
        contentArea.pad(MOBILE_PADDING);

        Table leftColumn = new Table();
        createMobileTabs(leftColumn);
        createMobileWorldList(leftColumn, 0.6f);

        // Detail panel on the right
        detailPanel = new Container<>();
        Table detailContent = createDetailPanel();
        detailPanel.setActor(detailContent);
        detailPanel.setBackground(new TextureRegionDrawable(
            createBackground(new Color(0.2f, 0.2f, 0.2f, 0.9f))
        ));
        detailPanel.pad(MOBILE_PADDING);

        contentArea.add(leftColumn).expandY().fillY().width(Value.percentWidth(0.6f, contentArea));
        contentArea.add(detailPanel).expand().fill().padLeft(MOBILE_SPACING);

        mobileLayout.add(contentArea).expand().fill().row();
    }

    private void createMobileTabs(Table container) {
        Table tabBar = new Table();
        tabBar.setBackground(new TextureRegionDrawable(
            createBackground(new Color(0.25f, 0.25f, 0.25f, 1f))
        ));

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

        ScrollPane.ScrollPaneStyle scrollStyle = new ScrollPane.ScrollPaneStyle();
        scrollStyle.vScroll = skin.getDrawable("scrollbar-v");
        scrollStyle.vScrollKnob = skin.getDrawable("scrollbar-knob-v");

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
            Label placeholder = new Label("Select a world to view details", skin);
            placeholder.setFontScale(MOBILE_FONT_SCALE);
            placeholder.setAlignment(Align.center);
            placeholder.setWrap(true);
            details.add(placeholder).expand().center();
        } else {
            // World thumbnail
            Image thumbnail = new Image();
            FileHandle thumbnailFile = Gdx.files.local("thumbnails/" + selectedWorld.getName() + ".png");
            if (thumbnailFile.exists()) {
                thumbnail.setDrawable(new TextureRegionDrawable(new Texture(thumbnailFile)));
            } else {
                thumbnail.setDrawable(new TextureRegionDrawable(TextureManager.ui.findRegion("placeholder-image")));
            }
            thumbnail.setScaling(Scaling.fit);

            details.add(thumbnail).size(200f).padBottom(MOBILE_SPACING * 2).row();

            // World name
            Label nameLabel = new Label(selectedWorld.getName(), skin);
            nameLabel.setFontScale(MOBILE_FONT_SCALE * 1.3f);
            details.add(nameLabel).padBottom(MOBILE_SPACING).row();

            // World info
            addDetailRow(details, "Last Played:", formatDate(selectedWorld.getLastPlayed()));
            addDetailRow(details, "Seed:", String.valueOf(getSeedFromWorld(selectedWorld)));
            addDetailRow(details, "Play Time:", formatPlayedTime(selectedWorld.getPlayedTime()));
            addDetailRow(details, "Commands:", selectedWorld.commandsAllowed() ? "Enabled" : "Disabled");
        }

        return details;
    }

    private void addDetailRow(Table table, String label, String value) {
        Table row = new Table();

        Label labelWidget = new Label(label, skin);
        labelWidget.setFontScale(MOBILE_FONT_SCALE * 0.9f);
        labelWidget.setColor(0.7f, 0.7f, 0.7f, 1f);

        Label valueWidget = new Label(value, skin);
        valueWidget.setFontScale(MOBILE_FONT_SCALE);

        row.add(labelWidget).left();
        row.add(valueWidget).expandX().right();

        table.add(row).fillX().padBottom(MOBILE_SPACING / 2).row();
    }

    private void createBottomActionBar() {
        bottomBar = new Table();
        bottomBar.setBackground(new TextureRegionDrawable(
            createBackground(new Color(0.2f, 0.2f, 0.2f, 1f))
        ));
        bottomBar.pad(MOBILE_PADDING);

        TextButton deleteBtn = createMobileButton("Delete", Color.RED, skin);
        deleteBtn.setDisabled(selectedWorld == null);
        deleteBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (selectedWorld != null) {
                    showDeleteConfirmDialog();
                }
            }
        });

        bottomBar.add(deleteBtn).height(BUTTON_HEIGHT).width(120f);

        mobileLayout.add(bottomBar).fillX().height(100f);
    }

    private void createFloatingActionButtons() {
        // Play FAB
        playFab = createFAB("▶", Color.GREEN);
        playFab.setVisible(selectedWorld != null);
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

        // Create FAB
        createFab = createFAB("+", new Color(0.2f, 0.6f, 1f, 1f));
        createFab.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showCreateWorldDialog();
            }
        });

        // Position FABs
        Table fabContainer = new Table();
        fabContainer.setFillParent(true);
        fabContainer.bottom().right().pad(MOBILE_PADDING * 2);

        if (selectedWorld != null) {
            fabContainer.add(playFab).size(FAB_SIZE).padBottom(MOBILE_SPACING);
            fabContainer.row();
        }
        fabContainer.add(createFab).size(FAB_SIZE);

        stage.addActor(fabContainer);
    }

    private TextButton createFAB(String text, Color color) {
        TextButton.TextButtonStyle fabStyle = new TextButton.TextButtonStyle();

        // Create circular background
        Pixmap pixmap = new Pixmap((int)FAB_SIZE, (int)FAB_SIZE, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fillCircle((int)FAB_SIZE/2, (int)FAB_SIZE/2, (int)FAB_SIZE/2);
        fabStyle.up = new TextureRegionDrawable(new Texture(pixmap));
        pixmap.dispose();

        // Pressed state
        pixmap = new Pixmap((int)FAB_SIZE, (int)FAB_SIZE, Pixmap.Format.RGBA8888);
        pixmap.setColor(color.cpy().mul(0.8f));
        pixmap.fillCircle((int)FAB_SIZE/2, (int)FAB_SIZE/2, (int)FAB_SIZE/2);
        fabStyle.down = new TextureRegionDrawable(new Texture(pixmap));
        pixmap.dispose();

        fabStyle.font = skin.getFont("default-font");

        TextButton fab = new TextButton(text, fabStyle);
        fab.getLabel().setFontScale(2f);

        // Add shadow effect
        fab.setTransform(true);
        fab.setOrigin(Align.center);
        fab.addAction(Actions.forever(
            Actions.sequence(
                Actions.scaleTo(1.1f, 1.1f, 0.5f),
                Actions.scaleTo(1f, 1f, 0.5f)
            )
        ));

        return fab;
    }

    public void refreshWorldList() {
        if (worldScrollPane == null) return;

        Table content = (Table) worldScrollPane.getWidget();
        content.clear();

        List<WorldData> worldList = new ArrayList<>(GameContext.get().getWorldManager().getWorlds().values());
        worldList.removeIf(world -> !shouldShowWorld(world));

        if (currentSort.equals("Name")) {
            worldList.sort(Comparator.comparing(WorldData::getName));
        } else {
            worldList.sort(Comparator.comparingLong(WorldData::getLastPlayed).reversed());
        }

        if (worldList.isEmpty()) {
            Label emptyLabel = new Label("No worlds found.\nTap + to create one!", skin);
            emptyLabel.setFontScale(MOBILE_FONT_SCALE);
            emptyLabel.setAlignment(Align.center);
            emptyLabel.setWrap(true);
            content.add(emptyLabel).expand().center().pad(50f);
        } else {
            for (WorldData world : worldList) {
                Table entry = createMobileWorldEntry(world);
                content.add(entry).fillX().expandX();
                content.row();
            }
        }

        // Update FAB visibility
        if (playFab != null) {
            playFab.setVisible(selectedWorld != null);
        }

        // Update detail panel for tablets
        if (isTablet && detailPanel != null) {
            detailPanel.setActor(createDetailPanel());
        }
    }

    private Table createMobileWorldEntry(WorldData world) {
        Table entry = new Table();
        entry.setTouchable(Touchable.enabled);
        entry.pad(MOBILE_PADDING);

        // Background
        TextureRegionDrawable normalBg = new TextureRegionDrawable(
            createBackground(new Color(0.25f, 0.25f, 0.25f, 1f))
        );
        TextureRegionDrawable selectedBg = new TextureRegionDrawable(
            createBackground(new Color(0.3f, 0.6f, 1f, 0.9f))
        );

        entry.setBackground(selectedWorld == world ? selectedBg : normalBg);

        // Thumbnail
        Image thumbnail = new Image();
        FileHandle thumbnailFile = Gdx.files.local("thumbnails/" + world.getName() + ".png");
        if (thumbnailFile.exists()) {
            thumbnail.setDrawable(new TextureRegionDrawable(new Texture(thumbnailFile)));
        } else {
            thumbnail.setDrawable(new TextureRegionDrawable(TextureManager.ui.findRegion("placeholder-image")));
        }
        thumbnail.setScaling(Scaling.fit);

        // Info section
        Table infoTable = new Table();
        infoTable.left();

        Label nameLabel = new Label(world.getName(), skin);
        nameLabel.setFontScale(MOBILE_FONT_SCALE);
        nameLabel.setEllipsis(true);

        Label lastPlayedLabel = new Label("Last played: " + formatDate(world.getLastPlayed()), skin);
        lastPlayedLabel.setFontScale(MOBILE_FONT_SCALE * 0.8f);
        lastPlayedLabel.setColor(0.7f, 0.7f, 0.7f, 1f);

        Label playTimeLabel = new Label(formatPlayedTime(world.getPlayedTime()), skin);
        playTimeLabel.setFontScale(MOBILE_FONT_SCALE * 0.8f);
        playTimeLabel.setColor(0.7f, 0.7f, 0.7f, 1f);

        infoTable.add(nameLabel).left().row();
        infoTable.add(lastPlayedLabel).left().row();
        infoTable.add(playTimeLabel).left();

        // Layout
        entry.add(thumbnail).size(80f).padRight(MOBILE_SPACING);
        entry.add(infoTable).expand().fill();

        // Touch listener
        entry.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                selectWorld(world);
                refreshWorldList();

                // Animate selection
                entry.addAction(Actions.sequence(
                    Actions.scaleTo(0.95f, 0.95f, 0.1f),
                    Actions.scaleTo(1f, 1f, 0.1f)
                ));
            }
        });

        return entry;
    }

    protected void showCreateWorldDialog() {
        activeDialog = new Dialog("", skin) {
            @Override
            protected void result(Object object) {
                if ((Boolean) object) {
                    TextField nameField = findActor("nameField");
                    TextField seedField = findActor("seedField");
                    CheckBox commandsBox = findActor("commandsBox");

                    String worldName = nameField.getText().trim();
                    String seedText = seedField.getText().trim();

                    if (worldName.isEmpty()) {
                        showError("World name cannot be empty");
                        return;
                    }

                    long seed = seedText.isEmpty() ? System.currentTimeMillis() : Long.parseLong(seedText);
                    boolean commands = commandsBox.isChecked();

                    // Create world with character selection
                    showCreateWorldDialog(); // This will trigger character selection
                }
                activeDialog = null;
            }
        };

        // Dialog content
        Table content = new Table();
        content.pad(MOBILE_PADDING);

        Label title = new Label("Create New World", skin);
        title.setFontScale(MOBILE_FONT_SCALE * 1.2f);
        content.add(title).padBottom(MOBILE_SPACING * 2).row();

        // World name
        content.add(new Label("World Name", skin)).left().row();
        TextField nameField = new TextField("", skin);
        nameField.setName("nameField");
        content.add(nameField).fillX().height(INPUT_FIELD_HEIGHT).padBottom(MOBILE_SPACING).row();

        // Seed (optional)
        content.add(new Label("Seed (optional)", skin)).left().row();
        TextField seedField = new TextField("", skin);
        seedField.setName("seedField");
        seedField.setTextFieldFilter(new TextField.TextFieldFilter.DigitsOnlyFilter());
        content.add(seedField).fillX().height(INPUT_FIELD_HEIGHT).padBottom(MOBILE_SPACING).row();

        // Commands checkbox
        CheckBox commandsBox = new CheckBox(" Enable Commands", skin);
        commandsBox.setName("commandsBox");
        commandsBox.getLabel().setFontScale(MOBILE_FONT_SCALE);
        content.add(commandsBox).left().padBottom(MOBILE_SPACING * 2).row();

        activeDialog.getContentTable().add(content);
        activeDialog.button("Create", true);
        activeDialog.button("Cancel", false);
        activeDialog.show(stage);

        // Focus on name field
        stage.setKeyboardFocus(nameField);
    }

    private TextButton createIconButton(String icon, Skin skin) {
        TextButton button = new TextButton(icon, skin);
        button.getLabel().setFontScale(MOBILE_FONT_SCALE * 1.5f);
        return button;
    }

    private TextButton createTabButton(String text, Skin skin) {
        TextButton.TextButtonStyle tabStyle = new TextButton.TextButtonStyle(skin.get(TextButton.TextButtonStyle.class));
        tabStyle.font.getData().setScale(MOBILE_FONT_SCALE);

        TextButton button = new TextButton(text, tabStyle);
        button.pad(16f);
        return button;
    }

    private TextButton createMobileButton(String text, Color color, Skin skin) {
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle(skin.get(TextButton.TextButtonStyle.class));
        style.font.getData().setScale(MOBILE_FONT_SCALE);
        style.fontColor = Color.WHITE;

        TextButton button = new TextButton(text, style);
        button.pad(16f, 32f, 16f, 32f);
        return button;
    }

    private TextureRegion createBackground(Color color) {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();
        TextureRegion region = new TextureRegion(new Texture(pixmap));
        pixmap.dispose();
        return region;
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);

        // Recreate FABs with proper positioning
        if (playFab != null && createFab != null) {
            createFloatingActionButtons();
        }
    }

    @Override
    public void show() {
        super.show();

        // Add entrance animation
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
