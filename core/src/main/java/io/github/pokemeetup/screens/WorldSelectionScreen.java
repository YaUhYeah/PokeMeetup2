package io.github.pokemeetup.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.*;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.managers.BiomeManager;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.client.GameClientSingleton;
import io.github.pokemeetup.multiplayer.server.config.ServerConfigManager;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import io.github.pokemeetup.system.Player;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.data.WorldData;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.github.pokemeetup.system.gameplay.overworld.World.INITIAL_LOAD_RADIUS;

/**
 * A screen that shows all available worlds, allows creation/deletion,
 * and loads either single- or multi-player.
 */
public class WorldSelectionScreen implements Screen {
    private static final String DEFAULT_PLAYER_NAME = "Player";
    private static final float MIN_BUTTON_WIDTH = 150f;
    private static final float MIN_BUTTON_HEIGHT = 40f;
    private static final float MIN_WORLD_LIST_WIDTH = 300f;
    private static final float MIN_INFO_PANEL_WIDTH = 200f;
    private final CreatureCaptureGame game;
    private final Stage stage;
    private final Skin skin;
    private final Map<String, Texture> worldThumbnails = new HashMap<>();
    private Table mainTable;
    private ScrollPane worldListScroll;
    private Table worldListTable;
    private Table infoPanel;
    private WorldData selectedWorld;
    // Buttons
    private TextButton playButton;
    private TextButton createButton;
    private TextButton deleteButton;
    private TextButton backButton;
    private ButtonGroup<TextButton> tabGroup;
    private String currentTab = "All";
    private ButtonGroup<TextButton> sortGroup;
    private String currentSort = "Name";
    private float screenWidth;
    private float screenHeight;
    private Table contentTable;
    private TextureRegion placeholderRegion;

    public WorldSelectionScreen(CreatureCaptureGame game) {
        this.game = game;
        this.stage = new Stage(new ScreenViewport());

        TextureAtlas atlas;
        try {
            atlas = new TextureAtlas(Gdx.files.internal("Skins/uiskin.atlas"));
        } catch (Exception e) {
            GameLogger.error("Failed to load TextureAtlas 'uiskin.atlas': " + e.getMessage());
            throw new RuntimeException("TextureAtlas loading failed.", e);
        }

        skin = new Skin(atlas);
        try {
            skin.load(Gdx.files.internal("Skins/uiskin.json"));
        } catch (Exception e) {
            GameLogger.error("Failed to load skin JSON 'uiskin.json': " + e.getMessage());
            throw new RuntimeException("Skin JSON loading failed.", e);
        }
        List<String> requiredDrawables = Arrays.asList(
            "default-round", "default-round-down", "default-rect",
            "default-window", "default-scroll", "default-round-large",
            "check-on", "check-off", "tree-minus", "tree-plus",
            "default-select", "default-select-selection",
            "default-splitpane-vertical", "default-splitpane",
            "default-slider", "default-slider-knob"
        );

        for (String drawableName : requiredDrawables) {
            if (!skin.has(drawableName, Drawable.class)) {
                GameLogger.error("Missing drawable in skin: " + drawableName);
            }
        }

        Gdx.input.setInputProcessor(stage);

        createUI();
        updateScreenSizes();
        refreshWorldList();
    }

    // Adjust layout on resizing
    private void updateScreenSizes() {
        screenWidth = Gdx.graphics.getWidth();
        screenHeight = Gdx.graphics.getHeight();

        // Calculate relative sizes
        float buttonWidth = Math.max(MIN_BUTTON_WIDTH, screenWidth * 0.2f);
        float buttonHeight = Math.max(MIN_BUTTON_HEIGHT, screenHeight * 0.08f);
        float worldListWidth = Math.max(MIN_WORLD_LIST_WIDTH, screenWidth * 0.55f);
        float infoPanelWidth = Math.max(MIN_INFO_PANEL_WIDTH, screenWidth * 0.35f);

        // Update UI elements with new sizes
        updateUIElements(buttonWidth, buttonHeight, worldListWidth, infoPanelWidth);
    }

    private void updateUIElements(float buttonWidth, float buttonHeight,
                                  float worldListWidth, float infoPanelWidth) {
        // Calculate font scale based on screen size
        float fontScale = Math.max(0.8f, Math.min(screenWidth, screenHeight) / 1000f);

        // Update main table padding
        if (mainTable != null) {
            mainTable.pad(screenWidth * 0.02f); // 2% of screen width

            // Update title scaling
            Label titleLabel = mainTable.findActor("titleLabel");
            if (titleLabel != null) {
                titleLabel.setFontScale(fontScale * 1.5f);
            }
        }

        // Update button sizes and font scales
        updateButton(createButton, buttonWidth, buttonHeight, fontScale);
        updateButton(playButton, buttonWidth, buttonHeight, fontScale);
        updateButton(deleteButton, buttonWidth, buttonHeight, fontScale);
        updateButton(backButton, buttonWidth, buttonHeight, fontScale);

        // Update scroll pane and info panel sizes
        if (worldListScroll != null && contentTable != null) {
            worldListTable.padRight(screenWidth * 0.02f);
            Cell<?> scrollCell = contentTable.getCell(worldListScroll);
            if (scrollCell != null) {
                scrollCell.width(worldListWidth);
            }

            Cell<?> infoPanelCell = contentTable.getCell(infoPanel);
            if (infoPanelCell != null) {
                infoPanelCell.width(infoPanelWidth);
            }
        }

        // Update world entry sizes
        if (worldListTable != null) {
            for (Actor actor : worldListTable.getChildren()) {
                if (actor instanceof Table) {
                    Table entry = (Table) actor;
                    float entryPadding = screenWidth * 0.01f;
                    entry.pad(entryPadding);

                    // Update thumbnail size
                    Image thumbnail = entry.findActor("thumbnail");
                    if (thumbnail != null) {
                        float thumbnailSize = Math.max(60f, screenWidth * 0.08f);
                        Cell<?> thumbnailCell = entry.getCell(thumbnail);
                        if (thumbnailCell != null) {
                            thumbnailCell.size(thumbnailSize);
                        }
                    }

                    // Update labels in the entry
                    for (Actor child : entry.getChildren()) {
                        if (child instanceof Label) {
                            ((Label) child).setFontScale(fontScale);
                        }
                    }
                }
            }
        }
    }

    private void updateButton(TextButton button, float width, float height, float fontScale) {
        if (button != null) {
            button.getLabel().setFontScale(fontScale);
            Table parent = button.getParent() instanceof Table ? (Table) button.getParent() : null;
            if (parent != null) {
                Cell<?> cell = parent.getCell(button);
                if (cell != null) {
                    cell.width(width)
                        .height(height)
                        .pad(screenWidth * 0.01f);
                }
            }
        }
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        updateScreenSizes();
    }

    private void createUI() {
        mainTable = new Table();
        mainTable.setFillParent(true);
        mainTable.pad(20);

        Label titleLabel = new Label("Select World", skin);
        titleLabel.setName("titleLabel");
        titleLabel.setFontScale(2.0f);
        mainTable.add(titleLabel).colspan(4).pad(20);
        mainTable.row();

        // Tab buttons
        Table tabTable = new Table();
        tabGroup = new ButtonGroup<>();

        String[] tabs = {"All", "Recent", "Multiplayer"};
        for (String tab : tabs) {
            TextButton tabButton = new TextButton(tab, skin);
            tabGroup.add(tabButton);
            tabTable.add(tabButton).pad(5);
        }
        mainTable.add(tabTable).colspan(4).pad(10);
        mainTable.row();

        // Sorting buttons
        Table sortTable = new Table();
        TextButton sortByNameButton = new TextButton("Sort by Name", skin);
        TextButton sortByDateButton = new TextButton("Sort by Date", skin);
        sortGroup = new ButtonGroup<>(sortByNameButton, sortByDateButton);
        sortGroup.setMaxCheckCount(1);
        sortGroup.setMinCheckCount(1);
        sortGroup.setUncheckLast(true);

        sortTable.add(sortByNameButton).pad(5);
        sortTable.add(sortByDateButton).pad(5);

        mainTable.add(sortTable).colspan(4).pad(10);
        mainTable.row();

        // World list setup
        worldListTable = new Table();
        worldListTable.top();
        worldListTable.defaults().expandX().fillX().pad(5f);

        ScrollPane.ScrollPaneStyle scrollPaneStyle = new ScrollPane.ScrollPaneStyle();
        scrollPaneStyle.background = skin.newDrawable("default-pane", new Color(0.15f, 0.15f, 0.15f, 0.8f));
        scrollPaneStyle.vScroll = skin.newDrawable("default-scroll");
        scrollPaneStyle.vScrollKnob = skin.newDrawable("default-round-large");

        worldListScroll = new ScrollPane(worldListTable, scrollPaneStyle);
        worldListScroll.setFadeScrollBars(false);
        worldListScroll.setScrollingDisabled(true, false);

        // Info panel
        infoPanel = new Table(skin);
        infoPanel.background("default-pane");
        infoPanel.pad(10);

        contentTable = new Table();
        contentTable.defaults().pad(10);
        contentTable.add(worldListScroll)
            .width(Gdx.graphics.getWidth() * 0.6f)
            .expandY()
            .fillY()
            .padRight(20);
        contentTable.add(infoPanel)
            .width(Gdx.graphics.getWidth() * 0.35f)
            .expandY()
            .fillY();

        mainTable.add(contentTable).colspan(4).expand().fill();
        mainTable.row();

        Table buttonTable = new Table();

        createButton = new TextButton("Create New World", skin);
        playButton = new TextButton("Play Selected World", skin);
        deleteButton = new TextButton("Delete World", skin);
        backButton = new TextButton("Back", skin);

        playButton.setDisabled(true);
        deleteButton.setDisabled(true);

        float fontScale = 1.2f;
        createButton.getLabel().setFontScale(fontScale);
        playButton.getLabel().setFontScale(fontScale);
        deleteButton.getLabel().setFontScale(fontScale);
        backButton.getLabel().setFontScale(fontScale);

        buttonTable.add(createButton).pad(10).width(250).height(70);
        buttonTable.add(playButton).pad(10).width(250).height(70);
        buttonTable.add(deleteButton).pad(10).width(250).height(70);
        buttonTable.row();
        buttonTable.add(backButton).colspan(3).width(250).height(70).pad(10);

        mainTable.add(buttonTable).colspan(4).pad(10);

        stage.addActor(mainTable);

        placeholderRegion = TextureManager.ui.findRegion("placeholder-image");
        addTabListeners();
        addSortListeners();
        addButtonListeners();

        tabGroup.getButtons().get(0).setChecked(true);
        sortGroup.getButtons().get(0).setChecked(true);
    }

    private void addTabListeners() {
        for (TextButton tabButton : tabGroup.getButtons()) {
            tabButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (tabButton.isChecked()) {
                        currentTab = tabButton.getText().toString();
                        refreshWorldList();
                    }
                }
            });
        }
    }

    private void addSortListeners() {
        for (TextButton sortButton : sortGroup.getButtons()) {
            sortButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (sortButton.isChecked()) {
                        currentSort = sortButton.getText().toString().replace("Sort by ", "");
                        refreshWorldList();
                    }
                }
            });
        }
    }

    private void addButtonListeners() {
        createButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showCreateWorldDialog();
            }
        });

        playButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (selectedWorld != null) {
                    String username = selectedWorld.getPlayers().isEmpty()
                        ? DEFAULT_PLAYER_NAME
                        : selectedWorld.getPlayers().keySet().iterator().next();
                    loadSelectedWorld(username);
                }
            }
        });

        deleteButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (selectedWorld != null) {
                    showDeleteConfirmDialog();
                }
            }
        });

        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.setScreen(new ModeSelectionScreen(game));
                dispose();
            }
        });
    }

    private Table createWorldEntry(WorldData world) {
        Table entry = new Table(skin);
        entry.setName("worldEntry");
        entry.setBackground(skin.newDrawable("default-pane", new Color(0.2f, 0.2f, 0.2f, 0.8f)));

        Table contentTable = new Table();
        float padding = 10f;
        contentTable.pad(padding);

        Table thumbnailContainer = new Table();
        thumbnailContainer.setBackground(skin.newDrawable("default-pane", new Color(0.15f, 0.15f, 0.15f, 1f)));

        Image thumbnailImage;
        FileHandle thumbnailFile = Gdx.files.local("thumbnails/" + world.getName() + ".png");
        if (thumbnailFile.exists()) {
            Texture thumbnailTexture = new Texture(thumbnailFile);
            worldThumbnails.put(world.getName(), thumbnailTexture);
            thumbnailImage = new Image(new TextureRegionDrawable(new TextureRegion(thumbnailTexture)));
            thumbnailImage.setScaling(Scaling.fit);
        } else {
            thumbnailImage = new Image(placeholderRegion);
            thumbnailImage.setScaling(Scaling.fit);
        }
        thumbnailImage.setName("thumbnail");

        float thumbnailSize = 180f;
        thumbnailContainer.add(thumbnailImage).size(thumbnailSize).pad(2f);

        Table infoTable = new Table();
        infoTable.defaults().left().pad(5f);

        Label nameLabel = new Label(world.getName(), skin);
        nameLabel.setFontScale(1.2f);

        Label timeLabel = new Label("Last played: " + formatDate(world.getLastPlayed()), skin);
        timeLabel.setFontScale(0.9f);

        Label seedLabel = new Label("Seed: " + getSeedFromWorld(world), skin);
        seedLabel.setFontScale(0.9f);

        String playedTimeStr = formatPlayedTime(world.getPlayedTime());
        Label playedTimeLabel = new Label("Played time: " + playedTimeStr, skin);
        playedTimeLabel.setFontScale(0.9f);

        infoTable.add(nameLabel).expandX().fillX().padBottom(5f);
        infoTable.row();
        infoTable.add(timeLabel).expandX().fillX().padBottom(5f);
        infoTable.row();
        infoTable.add(seedLabel).expandX().fillX().padBottom(5f);
        infoTable.row();
        infoTable.add(playedTimeLabel).expandX().fillX();

        contentTable.add(thumbnailContainer).size(thumbnailSize + padding * 2).padRight(padding * 2);
        contentTable.add(infoTable).expand().fill();
        entry.add(contentTable).expand().fill();

        entry.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                resetWorldEntryStyles();
                entry.setBackground(skin.newDrawable("default-pane", new Color(0.3f, 0.6f, 1f, 0.8f)));
                selectWorld(world);
            }
        });

        return entry;
    }

    private void resetWorldEntryStyles() {
        for (Actor actor : worldListTable.getChildren()) {
            if (actor instanceof Table) {
                ((Table) actor).setBackground(skin.newDrawable("default-pane", new Color(0.2f, 0.2f, 0.2f, 0.8f)));
            }
        }
    }

    private void refreshWorldList() {
        worldListTable.clear();
        WorldData previousSelection = selectedWorld;

        List<WorldData> worldList = new ArrayList<>(GameContext.get().getWorldManager().getWorlds().values());
        worldList.removeIf(world -> !shouldShowWorld(world));

        if (currentSort.equals("Name")) {
            worldList.sort(Comparator.comparing(WorldData::getName));
        } else if (currentSort.equals("Date")) {
            worldList.sort(Comparator.comparingLong(WorldData::getLastPlayed).reversed());
        }

        worldListTable.defaults().expandX().fillX().pad(5f);

        for (WorldData world : worldList) {
            Table worldEntry = createWorldEntry(world);

            if (world.equals(previousSelection)) {
                worldEntry.setBackground(skin.newDrawable("default-pane", new Color(0.3f, 0.6f, 1f, 0.8f)));
                selectedWorld = world;
            }

            worldListTable.add(worldEntry).expandX().fillX();
            worldListTable.row();
        }

        playButton.setDisabled(selectedWorld == null);
        deleteButton.setDisabled(selectedWorld == null);

        updateInfoPanel();
    }

    private boolean shouldShowWorld(WorldData world) {
        switch (currentTab) {
            case "Recent":
                return (System.currentTimeMillis() - world.getLastPlayed()) < (7L * 24 * 60 * 60 * 1000);
            case "Multiplayer":
                return world.getName().equals(CreatureCaptureGame.MULTIPLAYER_WORLD_NAME);
            default:
                return true;
        }
    }

    private void selectWorld(WorldData world) {
        if (world == null) return;

        selectedWorld = world;
        updateInfoPanel();
        playButton.setDisabled(false);
        deleteButton.setDisabled(false);

        GameLogger.info("Selected world '" + world.getName() +
            "' - Commands " + (world.commandsAllowed() ? "enabled" : "disabled"));
    }

    private void updateInfoPanel() {
        infoPanel.clear();

        if (selectedWorld == null) {
            infoPanel.add(new Label("Select a world to view details", skin)).expand();
            return;
        }

        infoPanel.defaults().left().pad(5);

        // World name
        Label nameLabel = new Label(selectedWorld.getName(), skin);
        nameLabel.setFontScale(1.5f);
        infoPanel.add(nameLabel).expandX();
        infoPanel.row();

        // Last played
        Label lastPlayedLabel = new Label("Last played: " + formatDate(selectedWorld.getLastPlayed()), skin);
        lastPlayedLabel.setFontScale(1.0f);
        infoPanel.add(lastPlayedLabel);
        infoPanel.row();

        // World size
        infoPanel.add(new Label("World size: " + World.WORLD_SIZE + " x " + World.WORLD_SIZE, skin));
        infoPanel.row();

        // Seed
        long seed = getSeedFromWorld(selectedWorld);
        infoPanel.add(new Label("Seed: " + seed, skin));
        infoPanel.row();

        // Played time
        long playedTimeMillis = selectedWorld.getPlayedTime();
        String playedTimeStr = formatPlayedTime(playedTimeMillis);
        Label playedTimeLabel = new Label("Played time: " + playedTimeStr, skin);
        infoPanel.add(playedTimeLabel);
        infoPanel.row();

        // Username
        String username = selectedWorld.getPlayers() != null && !selectedWorld.getPlayers().isEmpty()
            ? selectedWorld.getPlayers().keySet().iterator().next()
            : "Player";
        Label usernameLabel = new Label("Username: " + username, skin);
        infoPanel.add(usernameLabel).row();
    }

    private String formatPlayedTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        seconds = seconds % 60;
        minutes = minutes % 60;

        return String.format("%02dh %02dm %02ds", hours, minutes, seconds);
    }

    private String formatDate(long timestamp) {
        if (timestamp == 0) return "Never";
        return new SimpleDateFormat("MMM d, yyyy HH:mm").format(new Date(timestamp));
    }

    private long getSeedFromWorld(WorldData world) {
        if (world == null) return System.currentTimeMillis();
        WorldData.WorldConfig config = world.getConfig();
        if (config == null) {
            config = new WorldData.WorldConfig(System.currentTimeMillis());
            world.setConfig(config);
            GameLogger.error("Created new config for null config world");
        }
        return config.getSeed();
    }

    private void showCreateWorldDialog() {
        Dialog dialog = new Dialog("Create New World", skin) {
            @Override
            protected void result(Object object) {
                if ((Boolean) object) {
                    // Retrieve all the fields
                    TextField nameField = findActor("nameField");
                    CheckBox cheatsAllowed = findActor("cheatsAllowed");
                    TextField seedField = findActor("seedField");
                    TextField dialogUsernameField = findActor("usernameField");
                    // Get the character selection buttons
                    TextButton boyButton = findActor("boyButton");
                    TextButton girlButton = findActor("girlButton");
                    // Determine the chosen character type (default "boy")
                    String selectedCharacterType = "boy";
                    if (girlButton != null && girlButton.isChecked()) {
                        selectedCharacterType = "girl";
                    }

                    boolean commandsEnabled = cheatsAllowed != null && cheatsAllowed.isChecked();
                    GameLogger.info("Create world dialog - Commands enabled checkbox: " + commandsEnabled);

                    String worldName = nameField.getText().trim();
                    String seedText = seedField.getText().trim();
                    String username = dialogUsernameField.getText().trim();

                    if (worldName.isEmpty()) {
                        showError("World name cannot be empty");
                        return;
                    }
                    if (username.isEmpty()) {
                        username = DEFAULT_PLAYER_NAME;
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

                    // Pass the chosen character type to createNewWorld
                    createNewWorld(worldName, seed, username, commandsEnabled, selectedCharacterType);
                }
            }
        };

        // Create the basic input fields as before…
        TextField nameField = new TextField("", skin);
        nameField.setName("nameField");
        nameField.setMessageText("World name");

        CheckBox cheatsAllowed = new CheckBox(" Enable Commands", skin);
        cheatsAllowed.setName("cheatsAllowed");
        cheatsAllowed.setChecked(false);

        TextField seedField = new TextField("", skin);
        seedField.setName("seedField");
        seedField.setMessageText("Optional seed (number)");

        TextField dialogUsernameField = new TextField("", skin);
        dialogUsernameField.setName("usernameField");
        dialogUsernameField.setMessageText("Your username (optional)");

        // *** NEW: Create character selection UI ***
        Label characterLabel = new Label("Choose Character:", skin);
        TextButton boyButton = new TextButton("Boy", skin);
        boyButton.setName("boyButton");
        TextButton girlButton = new TextButton("Girl", skin);
        girlButton.setName("girlButton");
        ButtonGroup<TextButton> characterGroup = new ButtonGroup<>(boyButton, girlButton);
        characterGroup.setMinCheckCount(1);
        characterGroup.setMaxCheckCount(1);
        characterGroup.setUncheckLast(true);
        // Default to boy
        boyButton.setChecked(true);

        // Add all fields to the dialog content
        dialog.getContentTable().add(new Label("World Name:", skin)).left().padBottom(5);
        dialog.getContentTable().row();
        dialog.getContentTable().add(nameField).width(300).padBottom(15);
        dialog.getContentTable().row();
        dialog.getContentTable().add(new Label("Seed (optional):", skin)).left().padBottom(5);
        dialog.getContentTable().row();
        dialog.getContentTable().add(seedField).width(300).padBottom(15);
        dialog.getContentTable().row();
        dialog.getContentTable().add(new Label("Username:", skin)).left().padBottom(5);
        dialog.getContentTable().row();
        dialog.getContentTable().add(dialogUsernameField).width(300).padBottom(15);
        dialog.getContentTable().row();
        // Add the character selection elements
        dialog.getContentTable().add(characterLabel).left().padBottom(5);
        dialog.getContentTable().row();
        Table characterTable = new Table();
        characterTable.add(boyButton).pad(5);
        characterTable.add(girlButton).pad(5);
        dialog.getContentTable().add(characterTable).width(300).padBottom(15);
        dialog.getContentTable().row();
        dialog.getContentTable().add(cheatsAllowed).left().padBottom(15);

        dialog.button("Create", true);
        dialog.button("Cancel", false);
        dialog.show(stage);
    }


    private void showDeleteConfirmDialog() {
        Dialog dialog = new Dialog("Delete World", skin) {
            @Override
            protected void result(Object object) {
                if ((Boolean) object) {
                    deleteSelectedWorld();
                }
            }
        };

        dialog.text("Are you sure you want to delete '" + selectedWorld.getName() + "'?\nThis cannot be undone!");
        dialog.button("Delete", true);
        dialog.button("Cancel", false);
        dialog.show(stage);
    }

    private void deleteSelectedWorld() {
        try {
            GameContext.get().getWorldManager().deleteWorld(selectedWorld.getName());
            selectedWorld = null;
            refreshWorldList();
            updateInfoPanel();
            playButton.setDisabled(true);
            deleteButton.setDisabled(true);
        } catch (Exception e) {
            showError("Failed to delete world: " + e.getMessage());
            GameLogger.error("Failed to delete world: " + e.getMessage());
        }
    }

    private void createNewWorld(String name, long seed, String username, boolean cheatsAllowed, String characterType) {
        try {
            GameLogger.info("Creating new world '" + name + "' with commands " +
                (cheatsAllowed ? "enabled" : "disabled"));

            // Create world
            WorldData world = GameContext.get().getWorldManager().createWorld(name, seed, 0.15f, 0.05f);
            if (world == null) {
                showError("Failed to create world");
                return;
            }

            // Immediately set and save the commands flag
            world.setCommandsAllowed(cheatsAllowed);
            GameLogger.info("Set initial commands state: " + world.commandsAllowed());

            // Create and assign world config
            WorldData.WorldConfig config = new WorldData.WorldConfig(seed);
            config.setTreeSpawnRate(0.15f);
            config.setPokemonSpawnRate(0.05f);
            world.setConfig(config);

            // Create new player data and set the character type (e.g., "boy" or "girl")
            PlayerData playerData = new PlayerData(username);
            playerData.setCharacterType(characterType);
            world.savePlayerData(username, playerData, false);

            // Force an immediate save of the world
            GameContext.get().getWorldManager().saveWorld(world);

            GameLogger.info("World creation complete - Commands enabled: " + world.commandsAllowed());

            // Generate a thumbnail and update UI
            generateWorldThumbnail(world);
            refreshWorldList();
            selectWorld(world);

        } catch (Exception e) {
            GameLogger.error("Failed to create world: " + e.getMessage());
            showError("Failed to create world: " + e.getMessage());
        }
    }

    public void loadSelectedWorld(String username) {
        try {
            GameLogger.info("Starting world load: " + selectedWorld.getName());

            // (1) Save the current world state (if applicable)
            if (GameContext.get().getWorld() != null && GameContext.get().getPlayer() != null) {
                boolean currentIsMultiplayer = GameContext.get().getGameClient() != null &&
                    GameContext.get().isMultiplayer();
                boolean targetIsMultiplayer = selectedWorld.getName().equals(CreatureCaptureGame.MULTIPLAYER_WORLD_NAME);
                if (currentIsMultiplayer == targetIsMultiplayer) {
                    PlayerData currentState = GameContext.get().getPlayer().getPlayerData();
                    GameContext.get().getWorld().getWorldData().savePlayerData(
                        GameContext.get().getPlayer().getUsername(),
                        currentState,
                        currentIsMultiplayer
                    );
                }
            }

            // (2) Clean up the old client state
            if (GameContext.get().getGameClient() != null) {
                GameContext.get().getGameClient().dispose();
                GameContext.get().setGameClient(null);
            }

            // (3) Force a reload of the world data from disk rather than reusing the in-memory selectedWorld.
            // This is the key change.
            WorldData reloadedWorldData = GameContext.get().getWorldManager().loadAndValidateWorld(selectedWorld.getName());
            if (reloadedWorldData == null) {
                throw new IOException("Failed to load world data from disk for world: " + selectedWorld.getName());
            }
            // Update the last played timestamp (if needed)
            reloadedWorldData.setLastPlayed(System.currentTimeMillis());

            // (4) Retrieve the correct player data for this world from the reloaded data.
            PlayerData worldSpecificPlayerData = reloadedWorldData.getPlayerData(username, false);
            if (worldSpecificPlayerData == null) {
                worldSpecificPlayerData = new PlayerData(username);
            }

            // (5) Initialize the world using the reloaded data.
            game.initializeWorld(reloadedWorldData.getName(), false);

            // (6) Set up the singleplayer client.
            GameContext.get().setGameClient(GameClientSingleton.getSinglePlayerInstance(GameContext.get().getPlayer()));
            GameContext.get().getGameClient().setSinglePlayer(true);
            GameContext.get().setMultiplayer(false);

            // (7) Apply the saved player data to the new player.
            if (GameContext.get().getPlayer() != null) {
                GameContext.get().getPlayer().updateFromPlayerData(worldSpecificPlayerData);
            }

            // (8) Create and switch to a new GameScreen.
            GameScreen newScreen = new GameScreen(game, username,
                GameContext.get().getGameClient(), selectedWorld.commandsAllowed(), reloadedWorldData.getName());
            GameContext.get().setGameScreen(newScreen);
            game.setScreen(newScreen);
            dispose();

        } catch (Exception e) {
            GameLogger.error("Failed to load world: " + e.getMessage());
            showError("Failed to load world: " + e.getMessage());
        }
    }

    private void generateWorldThumbnail(WorldData worldData) {
        final int THUMBNAIL_SIZE = 256;
        FrameBuffer fbo = null;
        SpriteBatch batch = null;
        World tempWorld = null;

        try {
            GameLogger.info("Starting thumbnail generation for: " + worldData.getName());

            // Create new FBO
            fbo = new FrameBuffer(Pixmap.Format.RGBA8888, THUMBNAIL_SIZE, THUMBNAIL_SIZE, false);
            batch = new SpriteBatch();

            // Create minimal world just for a screenshot
            tempWorld = initializeWorldDirectly(worldData);

            // Setup camera
            OrthographicCamera camera = new OrthographicCamera();
            camera.setToOrtho(false, 16 * World.TILE_SIZE, 16 * World.TILE_SIZE);
            camera.position.set(World.DEFAULT_X_POSITION, World.DEFAULT_Y_POSITION, 0);
            camera.update();

            // Render into FBO
            fbo.begin();
            Gdx.gl.glViewport(0, 0, THUMBNAIL_SIZE, THUMBNAIL_SIZE);
            Gdx.gl.glClearColor(0.529f, 0.808f, 0.922f, 1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

            batch.setProjectionMatrix(camera.combined);
            batch.begin();

            Rectangle viewBounds = new Rectangle(
                camera.position.x - camera.viewportWidth / 2,
                camera.position.y - camera.viewportHeight / 2,
                camera.viewportWidth,
                camera.viewportHeight
            );
            tempWorld.render(batch, viewBounds, tempWorld.getPlayer());

            batch.end();
            Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, THUMBNAIL_SIZE, THUMBNAIL_SIZE);
            fbo.end();
            Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

            // Flip the pixmap
            Pixmap flippedPixmap = flipPixmap(pixmap);
            pixmap.dispose();

            // Save thumbnail to file
            FileHandle thumbnailDir = Gdx.files.local("thumbnails");
            if (!thumbnailDir.exists()) {
                thumbnailDir.mkdirs();
            }

            FileHandle thumbnailFile = thumbnailDir.child(worldData.getName() + ".png");
            PixmapIO.writePNG(thumbnailFile, flippedPixmap);

            flippedPixmap.dispose();

        } catch (Exception e) {
            GameLogger.error("Failed to generate thumbnail: " + e.getMessage());
        } finally {
            if (batch != null) batch.dispose();
            if (fbo != null) fbo.dispose();
            if (tempWorld != null) tempWorld.dispose();
        }
    }

    private Pixmap flipPixmap(Pixmap src) {
        int width = src.getWidth();
        int height = src.getHeight();
        Pixmap flipped = new Pixmap(width, height, src.getFormat());

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int pixel = src.getPixel(x, y);
                flipped.drawPixel(x, height - y - 1, pixel);
            }
        }
        return flipped;
    }

    /**
     * Creates a temporary local World to generate the thumbnail image.
     */
    private World initializeWorldDirectly(WorldData worldData) throws IOException {
        if (worldData == null) {
            throw new IOException("WorldData cannot be null");
        }
        long seed = worldData.getConfig() != null ? worldData.getConfig().getSeed() : System.currentTimeMillis();
        BiomeManager biomeManager = new BiomeManager(seed);
        World world = new World(worldData.getName(), seed, biomeManager);
        world.loadChunksAroundPositionSynchronously(
            new Vector2(World.DEFAULT_X_POSITION, World.DEFAULT_Y_POSITION),
            INITIAL_LOAD_RADIUS
        );
        // minimal dummy player
        Player tempPlayer = new Player(
            World.DEFAULT_X_POSITION,
            World.DEFAULT_Y_POSITION,
            world,
            "ThumbnailGen"
        );
        world.setPlayer(tempPlayer);
        return world;
    }

    private void showError(String message) {
        Dialog dialog = new Dialog("Error", skin);
        dialog.text(message);
        dialog.button("OK");
        dialog.show(stage);
    }

    @Override
    public void show() {
        AudioManager.getInstance().playMenuMusic();
        Gdx.app.postRunnable(this::refreshWorldList);
    }

    @Override
    public void render(float delta) {
        AudioManager.getInstance().update(delta);

        // Handle back/escape key
        if (Gdx.input.isKeyJustPressed(Input.Keys.BACK) || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            game.setScreen(new ModeSelectionScreen(game));
            dispose();
            return;
        }

        Gdx.gl.glClearColor(0.2f, 0.2f, 0.2f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stage.act(delta);
        stage.draw();
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void dispose() {
        stage.dispose();
        skin.dispose();
        for (Texture texture : worldThumbnails.values()) {
            texture.dispose();
        }
    }
}
