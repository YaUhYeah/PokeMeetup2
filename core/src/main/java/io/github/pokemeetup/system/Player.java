package io.github.pokemeetup.system;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.PokemonParty;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.data.PokemonData;
import io.github.pokemeetup.system.gameplay.PlayerAnimations;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;

import java.util.*;

import static io.github.pokemeetup.system.gameplay.overworld.World.INTERACTION_RANGE;
import static io.github.pokemeetup.system.gameplay.overworld.World.TILE_SIZE;

public class Player {
    public static final int FRAME_WIDTH = 32;
    public static final int FRAME_HEIGHT = 48;
    private static final float COLLISION_BOX_WIDTH_RATIO = 0.6f;
    private static final float COLLISION_BOX_HEIGHT_RATIO = 0.4f;
    private static final float TILE_TRANSITION_TIME = 0.2f;
    private static final float RUN_SPEED_MULTIPLIER = 1.5f;
    private static final float COLLISION_BUFFER = 4f;
    private static final long VALIDATION_INTERVAL = 1000;
    private static final float INPUT_BUFFER_TIME = 0.1f;
    private final Object movementLock = new Object();
    private final Object resourceLock = new Object();
    private final Object fontLock = new Object();
    private final Object inventoryLock = new Object();
    private PlayerAnimations animations;
    private String username;
    private World world;
    private float inputBufferTimer = 0f;
    private String bufferedDirection = null;
    private Vector2 position = new Vector2();
    private Rectangle collisionBox;
    private Rectangle nextPositionBox;
    private PokemonParty pokemonParty = new PokemonParty();
    private Inventory buildInventory = new Inventory();
    private Vector2 renderPosition = new Vector2();
    private Vector2 lastPosition = new Vector2();
    private Vector2 targetPosition = new Vector2();
    private Vector2 startPosition = new Vector2();
    private String direction = "down";
    private boolean isMoving = false;
    private boolean isRunning = false;
    private boolean buildMode = false;
    private TextureRegion currentFrame;
    private Inventory inventory = new Inventory();
    private float stateTime = 0f;
    private float x = 0f;
    private float y = 0f;
    private int tileX, tileY;
    private int targetTileX, targetTileY;
    private BitmapFont font;
    private PlayerData playerData;
    private float movementProgress;
    private boolean resourcesInitialized = false;
    private long lastValidationTime = 0;
    private volatile boolean disposed = false;
    private float diagonalMoveTimer = 0f;
    private volatile boolean fontInitialized = false;
    private Skin skin;
    private Stage stage;
    public Player(int startTileX, int startTileY, World world) {
        this(startTileX, startTileY, world, "Player");
        this.playerData = new PlayerData("Player");
        this.stage = new Stage(new ScreenViewport());
        this.skin = new Skin(Gdx.files.internal("Skins/uiskin.json"));
    }

    public Player(String username, World world) {
        this(0, 0, world, username);
        GameLogger.info("Creating new player: " + username);
        this.animations = new PlayerAnimations();
        this.world = world;
        this.position = new Vector2(0, 0);
        this.targetPosition = new Vector2(0, 0);
        this.renderPosition = new Vector2(0, 0);
        this.lastPosition = new Vector2(0, 0);
        this.startPosition = new Vector2(0, 0);
        this.stage = new Stage(new ScreenViewport());
        this.skin = new Skin(Gdx.files.internal("Skins/uiskin.json"));

        float boxWidth = FRAME_WIDTH * COLLISION_BOX_WIDTH_RATIO;
        float boxHeight = FRAME_HEIGHT * COLLISION_BOX_HEIGHT_RATIO;
        this.collisionBox = new Rectangle(0, 0, boxWidth, boxHeight);
        this.nextPositionBox = new Rectangle(0, 0, boxWidth, boxHeight);
        this.direction = "down";
        this.inventory = new Inventory();
        this.buildInventory = new Inventory();
        this.pokemonParty = new PokemonParty();
        this.playerData = new PlayerData(username);

        initFont();
        initializeBuildInventory();

        Gdx.app.postRunnable(this::initializeGraphics);
        GameLogger.info("Player initialized: " + username + " at (0,0)");
    }

    public Player(int startTileX, int startTileY, World world, String username) {
        this.world = world;
        this.username = username != null ? username : "Player";


        float boxWidth = FRAME_WIDTH * COLLISION_BOX_WIDTH_RATIO;
        float boxHeight = FRAME_HEIGHT * COLLISION_BOX_HEIGHT_RATIO;

        this.collisionBox = new Rectangle(0, 0, boxWidth, boxHeight);
        this.nextPositionBox = new Rectangle(0, 0, boxWidth, boxHeight);

        initializePosition(startTileX, startTileY);

        this.playerData = new PlayerData("Player");
        initFont();
        this.direction = "down";
        this.inventory = new Inventory();
        this.buildInventory = new Inventory();
        this.pokemonParty = new PokemonParty();

        // Load saved state if available
        initializeBuildInventory();
        initializeFromSavedState();
        this.renderPosition = new Vector2(x, y);
        this.lastPosition = new Vector2(x, y);
        Gdx.app.postRunnable(this::initializeGLResources);

    }

    private void initializeGLResources() {
        try {
            // Only create OpenGL resources here
            this.stage = new Stage(new ScreenViewport());
            this.skin = new Skin(Gdx.files.internal("Skins/uiskin.json"));
            this.font = new BitmapFont(Gdx.files.internal("Skins/default.fnt"));
            font.getData().setScale(0.8f);
            font.setColor(Color.WHITE);
            this.animations = new PlayerAnimations();
            this.initialized = true;

            GameLogger.info("Player GL resources initialized: " + username);

        } catch (Exception e) {
            GameLogger.error("Failed to initialize GL resources: " + e.getMessage());
        }
    }
    public PlayerAnimations getAnimations() {
        return animations;
    }

    private void initializeGraphics() {
        // Post font initialization to the main thread
        Gdx.app.postRunnable(() -> {
            synchronized (fontLock) {
                if (!fontInitialized) {
                    try {
                        if (font != null) {
                            font.dispose();
                        }
                        font = new BitmapFont(Gdx.files.internal("Skins/default.fnt"));
                        font.getData().setScale(0.8f);
                        font.setColor(Color.WHITE);
                        fontInitialized = true;
                        GameLogger.info("Font initialized successfully");
                    } catch (Exception e) {
                        GameLogger.error("Failed to initialize font: " + e.getMessage());
                    }
                }
            }
        });

        this.animations = new PlayerAnimations();
    }

    private void initFont() {
        this.font = new BitmapFont(Gdx.files.internal("Skins/default.fnt"));
        font.getData().setScale(0.8f);
        font.setColor(Color.WHITE);
    }

    public void initializeInWorld(World world) {
        if (world == null) {
            GameLogger.error("Cannot initialize player in null world");
            return;
        }

        this.world = world;

        updateCollisionBoxes();

        GameLogger.info("Player initialized in world: " + username);
    }

    public void updateFromPlayerData(PlayerData data) {
        if (data == null) return;

        this.playerData = data;

        // 1) Reset / Overwrite Pokemon Party
        this.pokemonParty = new PokemonParty();
        if (data.getPartyPokemon() != null) {
            for (PokemonData pData : data.getPartyPokemon()) {
                if (pData != null) {
                    Pokemon p = pData.toPokemon();
                    if (p != null) {
                        this.pokemonParty.addPokemon(p);
                    }
                }
            }
        }

        // 2) Reset / Overwrite Inventory
        this.inventory = new Inventory();
        if (data.getInventoryItems() != null) {
            for (ItemData item : data.getInventoryItems()) {
                if (item != null) {
                    this.inventory.addItem(item);
                }
            }
        }

        // 3) Apply position, direction, etc.
        this.setX(data.getX());
        this.setY(data.getY());
        this.setDirection(data.getDirection());
        this.setMoving(data.isMoving());
        this.setRunning(data.isWantsToRun());

        // Log or debug
        GameLogger.info("Updated player '" + username + "' from PlayerData. Items: "
            + this.inventory.getAllItems().size()
            + ", Party Pokemon: " + this.pokemonParty.getSize());
    }


    private void initializeBuildInventory() {
        // Add default blocks to build inventory
        for (PlaceableBlock.BlockType blockType : PlaceableBlock.BlockType.values()) {
            ItemData blockItem = new ItemData(blockType.getId(), 64); // Give a stack of blocks
            buildInventory.addItem(blockItem);
        }
    }

    private void initializePosition(int startTileX, int startTileY) {
        this.tileX = startTileX;
        this.tileY = startTileY;

        this.x = tileToPixelX(startTileX);
        this.y = tileToPixelY(startTileY);

        this.position = new Vector2(x, y);
        this.targetPosition = new Vector2(x, y);
        this.renderPosition = new Vector2(x, y);
        this.lastPosition = new Vector2(x, y);
        this.startPosition = new Vector2(x, y);
        // Set initial target tiles (no movement yet
        this.targetTileX = tileX;
        this.targetTileY = tileY;
    }

    private float tileToPixelX(int tileX) {
        return tileX * World.TILE_SIZE;
    }

    private float tileToPixelY(int tileY) {
        return tileY * World.TILE_SIZE;
    }

    private int pixelToTileX(float pixelX) {
        return (int) Math.floor(pixelX / World.TILE_SIZE);
    }

    private int pixelToTileY(float pixelY) {
        return (int) Math.floor(pixelY / World.TILE_SIZE);
    }

    private void initializeFromSavedState() {
        if (world != null && world.getWorldData() != null) {
            PlayerData savedData = world.getWorldData().getPlayerData(username, false);
            if (savedData != null) {
                savedData.applyToPlayer(this);
                GameLogger.info("Loaded saved state for player: " + username);
            } else {
                this.playerData = new PlayerData(username);
                GameLogger.info("Created new player data for: " + username);
            }
        }
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    public void update(float deltaTime) {
        if (!resourcesInitialized || disposed || animations == null || animations.isDisposed()) {
            initializeResources();
        }

        synchronized (movementLock) {
            // Update timers
            if (diagonalMoveTimer > 0) {
                diagonalMoveTimer -= deltaTime;
            }
            if (inputBufferTimer > 0) {
                inputBufferTimer -= deltaTime;
                if (inputBufferTimer <= 0 && bufferedDirection != null) {
                    move(bufferedDirection);
                    bufferedDirection = null;
                }
            }

            // Handle movement
            if (isMoving) {
                float speed = isRunning ? RUN_SPEED_MULTIPLIER : 1.0f;
                movementProgress += (deltaTime / TILE_TRANSITION_TIME) * speed;

                if (movementProgress >= 1.0f) {
                    completeMovement();
                } else {
                    updatePosition(movementProgress);
                }
            }

            // Update animation
            stateTime += deltaTime;
            currentFrame = animations.getCurrentFrame(direction, isMoving, isRunning, stateTime);
        }
    }

    private void updatePosition(float progress) {
        float smoothProgress = smoothstep(progress);


        x = MathUtils.lerp(startPosition.x, targetPosition.x, smoothProgress);
        y = MathUtils.lerp(startPosition.y, targetPosition.y, smoothProgress);

        position.set(x, y);
        renderPosition.set(x, y);


        updateCollisionBoxes();
    }

    private void updateCollisionBoxes() {
        // Position collision boxes in pixel coordinates
        collisionBox.setPosition(x + (FRAME_WIDTH - collisionBox.width) / 2f, y + COLLISION_BUFFER);

        nextPositionBox.setPosition(targetPosition.x + (FRAME_WIDTH - nextPositionBox.width) / 2f, targetPosition.y + COLLISION_BUFFER);
    }

    private float smoothstep(float x) {
        x = MathUtils.clamp(x, 0f, 1f);
        return x * x * (3 - 2 * x);
    }

    public void move(String newDirection) {
        synchronized (movementLock) {
            if (isMoving) {
                if (movementProgress > 0.7f) {
                    bufferedDirection = newDirection;
                    inputBufferTimer = INPUT_BUFFER_TIME;
                }
                return;
            }

            direction = newDirection;

            if (world == null) {
                GameLogger.error("Cannot move - world is null! Player: " + username);
                return;
            }

            int newTileX = getTileX();
            int newTileY = getTileY();

            switch (newDirection) {
                case "up":
                    newTileY += 1;
                    break;
                case "down":
                    newTileY -= 1;
                    break;
                case "left":
                    newTileX -= 1;
                    break;
                case "right":
                    newTileX += 1;
                    break;
                default:
                    return;
            }

            if (world != null && world.isPassable(newTileX, newTileY)) {
                targetTileX = newTileX;
                targetTileY = newTileY;
                targetPosition.set(tileToPixelX(newTileX), tileToPixelY(newTileY));
                startPosition.set(x, y);
                lastPosition.set(x, y);
                isMoving = true;
                movementProgress = 0f;
            }
        }
    }

    private void completeMovement() {
        x = targetPosition.x;
        y = targetPosition.y;
        tileX = targetTileX;
        tileY = targetTileY;
        position.set(x, y);
        renderPosition.set(x, y);

        isMoving = false;
        movementProgress = 0f;
        if (bufferedDirection != null) {
            String nextDirection = bufferedDirection;
            bufferedDirection = null;
            move(nextDirection);
        }
    }

    public volatile boolean initialized = false;
    public void render(SpriteBatch batch) {
        synchronized (resourceLock) {
            if (!initialized) {
                return;
            }
            if (!fontInitialized) {
                initializeGraphics();
                return;
            }
            if (!resourcesInitialized || disposed || animations == null || animations.isDisposed()) {
                initializeResources();
            }
            if (currentFrame != null) {
                Color originalColor = batch.getColor().cpy();

                if (world != null) {
                    // Get base color from the world
                    Color baseColor = world.getCurrentWorldColor();

                    // Convert position to tile coordinates
                    int tileX = (int) (x / World.TILE_SIZE);
                    int tileY = (int) (y / World.TILE_SIZE);
                    Vector2 tilePos = new Vector2(tileX, tileY);

                    // Get light level at this position
                    Float lightLevel = world.getLightLevelAtTile(tilePos);
                    if (lightLevel != null && lightLevel > 0) {
                        Color lightColor = new Color(1f, 0.9f, 0.7f, 1f);
                        baseColor = baseColor.cpy().lerp(lightColor, lightLevel);
                    }

                    // Set the adjusted color
                    batch.setColor(baseColor);
                }

                if (currentFrame instanceof Sprite) {
                    Sprite sprite = (Sprite) currentFrame;
                    sprite.setPosition(renderPosition.x, renderPosition.y);
                    sprite.setOrigin(0, 0);
                    sprite.draw(batch);
                } else {
                    // Use the frame's width and height directly
                    batch.draw(currentFrame, renderPosition.x, renderPosition.y,
                        currentFrame.getRegionWidth(), currentFrame.getRegionHeight());
                }

                // Restore original batch color
                batch.setColor(originalColor);
            }

            if (username != null && !username.isEmpty() && font != null &&
                !username.equals("Player") && !username.equals("ThumbnailPlayer")) {
                try {
                    font.draw(batch, username,
                        renderPosition.x - (float) FRAME_WIDTH / 2,
                        renderPosition.y + FRAME_HEIGHT + 15);
                } catch (Exception e) {
                    GameLogger.error("Font rendering error: " + e.getMessage());
                    fontInitialized = false;
                }
            }
        }
    }

    public void setRenderPosition(Vector2 renderPosition) {
        this.renderPosition = renderPosition;
    }

    public int getTileX() {
        return pixelToTileX(x);
    }

    public void setTileX(int tileX) {
        this.tileX = tileX;
    }

    public int getTileY() {
        return pixelToTileY(y);
    }

    public void setTileY(int tileY) {
        this.tileY = tileY;
    }

    public void selectBlockItem(int slot) {
        if (!buildMode) return;

        ItemData itemData = buildInventory.getItemAt(slot);
        Item heldBlock = null;
        if (itemData != null) {
            Item baseItem = ItemManager.getItem(itemData.getItemId());
            if (baseItem == null) {
                GameLogger.error("Failed to get base item for: " + itemData.getItemId());
                return;
            }
            heldBlock = baseItem.copy();
            heldBlock.setCount(itemData.getCount());

            GameLogger.info("Selected block item: " + itemData.getItemId() + " x" + itemData.getCount());
        } else {
            GameLogger.info("Cleared held block");
        }
    }

    public boolean canPickupItem(float itemX, float itemY) {
        float playerCenterX = x + (FRAME_WIDTH / 2f);
        float playerCenterY = y + (FRAME_HEIGHT / 2f);
        float itemCenterX = itemX + (TILE_SIZE / 2f);
        float itemCenterY = itemY + (TILE_SIZE / 2f);
        float dx = itemCenterX - playerCenterX;
        float dy = itemCenterY - playerCenterY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        boolean inCorrectDirection = false;
        switch (direction) {
            case "up":
                inCorrectDirection = dy > 0 && Math.abs(dx) < TILE_SIZE;
                break;
            case "down":
                inCorrectDirection = dy < 0 && Math.abs(dx) < TILE_SIZE;
                break;
            case "left":
                inCorrectDirection = dx < 0 && Math.abs(dy) < TILE_SIZE;
                break;
            case "right":
                inCorrectDirection = dx > 0 && Math.abs(dy) < TILE_SIZE;
                break;
        }

        boolean canPickup = distance <= INTERACTION_RANGE && inCorrectDirection;
        if (canPickup) {
            GameLogger.info("Can pickup item at distance: " + distance + " in direction: " + direction);
        }
        return canPickup;
    }

    public void updatePlayerData() {
        playerData.setX(x);
        playerData.setY(y);
        playerData.setDirection(direction);
        playerData.setMoving(isMoving);
        playerData.setWantsToRun(isRunning);
        playerData.setInventoryItems(inventory.getAllItems());

        // Create a fixed-size list for party Pokemon
        List<PokemonData> partyData = new ArrayList<>(Collections.nCopies(PokemonParty.MAX_PARTY_SIZE, null));

        synchronized (pokemonParty.partyLock) {  // Use the party's lock for thread safety
            List<Pokemon> currentParty = pokemonParty.getParty();

            // Log the current party state
            GameLogger.info("Converting party of size " + currentParty.size() + " to PokemonData");

            // Convert each Pokemon to PokemonData while maintaining slot positions
            for (int i = 0; i < PokemonParty.MAX_PARTY_SIZE; i++) {
                Pokemon pokemon = i < currentParty.size() ? currentParty.get(i) : null;
                if (pokemon != null) {
                    try {
                        PokemonData pokemonData = PokemonData.fromPokemon(pokemon);
                        if (pokemonData.verifyIntegrity()) {
                            partyData.set(i, pokemonData);
                            GameLogger.info("Added Pokemon to slot " + i + ": " + pokemon.getName());
                        } else {
                            GameLogger.error("Pokemon data failed integrity check at slot " + i);
                            partyData.set(i, null);
                        }
                    } catch (Exception e) {
                        GameLogger.error("Failed to convert Pokemon at slot " + i + ": " + e.getMessage());
                        partyData.set(i, null);
                    }
                }
            }
        }

        // Verify the party data before setting
        boolean hasValidPokemon = partyData.stream().anyMatch(Objects::nonNull);
        if (!hasValidPokemon) {
            GameLogger.error("No valid Pokemon found in party data!");
        }

        // Set the verified party data
        playerData.setPartyPokemon(partyData);

        // Log final state
        GameLogger.info("Updated player data with " +
            partyData.stream().filter(Objects::nonNull).count() + " Pokemon in party");
    }

    public void initializeResources() {
        synchronized (resourceLock) {
            try {
                if (resourcesInitialized && !disposed && animations != null && !animations.isDisposed()) {
                    return;
                }

                GameLogger.info("Initializing player resources");

                // Create new animations only if needed
                if (animations == null || animations.isDisposed()) {
                    animations = new PlayerAnimations();
                    GameLogger.info("Created new PlayerAnimations");
                }

                // Always get a fresh frame
                currentFrame = animations.getStandingFrame("down");
                if (currentFrame == null) {
                    throw new RuntimeException("Failed to get initial frame");
                }

                resourcesInitialized = true;
                disposed = false;
                GameLogger.info("Player resources initialized successfully");

            } catch (Exception e) {
                GameLogger.error("Failed to initialize player resources: " + e.getMessage());
                resourcesInitialized = false;
                disposed = true;
                throw new RuntimeException("Resource initialization failed", e);
            }
        }
    }

    public void validateResources() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastValidationTime > VALIDATION_INTERVAL) {
            synchronized (resourceLock) {
                if (!resourcesInitialized || disposed || animations == null || animations.isDisposed()) {
                    initializeResources();
                }
                lastValidationTime = currentTime;
            }
        }
    }

    public void dispose() {
        synchronized (resourceLock) {
            if (disposed) {
                return;
            }

            Gdx.app.postRunnable(() -> {
                try {
                    GameLogger.info("Disposing player resources");

                    synchronized (fontLock) {
                        if (font != null) {
                            font.dispose();
                            font = null;
                            fontInitialized = false;
                        }
                    }

                    if (animations != null) {
                        animations.dispose();
                        animations = null;
                    }

                    currentFrame = null;
                    resourcesInitialized = false;
                    disposed = true;

                    GameLogger.info("Player resources disposed successfully");

                } catch (Exception e) {
                    GameLogger.error("Error disposing player resources: " + e.getMessage());
                }
            });
        }
    }

    public Vector2 getPosition() {
        return new Vector2(position);
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public boolean isMoving() {
        return isMoving;
    }

    public void setMoving(boolean moving) {
        isMoving = moving;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void setRunning(boolean running) {
        this.isRunning = running;
    }

    public boolean isBuildMode() {
        return buildMode;
    }

    public void setBuildMode(boolean buildMode) {
        this.buildMode = buildMode;
    }

    public Inventory getInventory() {
        synchronized (inventoryLock) {
            if (inventory == null) {
                GameLogger.error("Player inventory is null - creating new");
                inventory = new Inventory();
            }
            return inventory;
        }
    }

    public void setInventory(Inventory inv) {
        synchronized (inventoryLock) {
            if (inv == null) {
                GameLogger.error("Attempt to set null inventory");
                return;
            }

            // Copy items from old inventory if it exists
            if (this.inventory != null) {
                List<ItemData> oldItems = this.inventory.getAllItems();
                for (ItemData item : oldItems) {
                    if (item != null) {
                        inv.addItem(item.copy());
                    }
                }
            }

            this.inventory = inv;
            GameLogger.info("Set player inventory with " +
                inv.getAllItems().stream().filter(Objects::nonNull).count() + " items");
        }
    }

    public Inventory getBuildInventory() {
        return buildInventory;
    }

    public PokemonParty getPokemonParty() {
        return pokemonParty;
    }

    public void setPokemonParty(PokemonParty pokemonParty) {
        this.pokemonParty = pokemonParty;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public World getWorld() {
        return world;
    }

    // Pokemon Management

    public void setWorld(World world) {
        synchronized (movementLock) {
            this.world = world;
        }
    }

    public PlayerData getPlayerData() {
        return playerData;
    }

    public void setPlayerData(PlayerData playerData) {
        this.playerData = playerData;
    }


    public Stage getStage() {
        return stage;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public Skin getSkin() {
        return skin;
    }

    public void setSkin(Skin skin) {
        this.skin = skin;
    }
}
