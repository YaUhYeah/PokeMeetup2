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
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.blocks.PlaceableBlock;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.managers.FootstepEffect;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.PokemonParty;
import io.github.pokemeetup.screens.otherui.HotbarSystem;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.data.PokemonData;
import io.github.pokemeetup.system.gameplay.PlayerAnimations;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.ItemEntity;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TileType;

import java.util.*;

import static io.github.pokemeetup.system.gameplay.overworld.World.INTERACTION_RANGE;
import static io.github.pokemeetup.system.gameplay.overworld.World.TILE_SIZE;

public class Player implements Positionable {
    // Constants
    public static final int FRAME_WIDTH = 32;
    public static final int FRAME_HEIGHT = 48;
    private static final float COLLISION_BOX_WIDTH_RATIO = 0.6f;
    private static final float COLLISION_BOX_HEIGHT_RATIO = 0.4f;
    private static final float COLLISION_BUFFER = 4f;
    private static final long VALIDATION_INTERVAL = 1000;
    private static final float PICKUP_RANGE = 48f;
    private static final float BUFFER_WINDOW = 0.15f;
    // Synchronization locks and layout
    private final Object movementLock = new Object();
    private final Object resourceLock = new Object();
    private final Object fontLock = new Object();
    private final Object inventoryLock = new Object();
    private final GlyphLayout layout = new GlyphLayout();
    // Player state variables
    public volatile boolean initialized = false;
    private float walkStepDuration = PlayerAnimations.SLOW_WALK_ANIMATION_DURATION;
    private float runStepDuration = PlayerAnimations.SLOW_RUN_ANIMATION_DURATION;
    private PlayerAnimations animations;
    private String username;
    private World world;
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
    private volatile boolean fontInitialized = false;
    private Skin skin;
    private Stage stage;
    private float bufferedTime = 0f;
    private float animationTime = 0f;
    private float animationSpeedMultiplier = 0.75f;
    private boolean inputHeld = false;

    // Constructors
    public Player(int startTileX, int startTileY, World world) {
        this(startTileX, startTileY, world, "Player");
        this.playerData = new PlayerData("Player");
        this.stage = new Stage(new ScreenViewport());
        this.skin = new Skin(Gdx.files.internal("Skins/uiskin.json"));
    }

    public Player(String username, World world) {
        this(0, 0, world, username);
        GameLogger.info("Creating new player: " + username);
        this.animations = new PlayerAnimations(getCharacterType());
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
        Gdx.app.postRunnable(this::initializeGLResources);
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

        initializeBuildInventory();
        initializeFromSavedState();
        this.renderPosition = new Vector2(x, y);
        this.lastPosition = new Vector2(x, y);
        Gdx.app.postRunnable(this::initializeGLResources);
    }

    // Utility methods
    public String getCharacterType() {
        return (playerData != null && playerData.getCharacterType() != null) ? playerData.getCharacterType() : "boy";
    }

    public void setCharacterType(String characterType) {
        if (playerData != null) {
            playerData.setCharacterType(characterType);
        }
        if (animations != null) {
            animations.dispose();
        }
        animations = new PlayerAnimations(getCharacterType());
    }

    public HotbarSystem getHotbarSystem() {
        if (GameContext.get().getHotbarSystem() == null) {
            Stage stage = GameContext.get().getUiStage();
            if (stage == null) {
                GameLogger.error("UI Stage is null in getHotbarSystem()!");
                return null;
            }
            // Use the skin stored in GameContext if available.
            Skin hotbarSkin = GameContext.get().getSkin() != null ? GameContext.get().getSkin() : skin;
            GameContext.get().setHotbarSystem(new HotbarSystem(stage, hotbarSkin));
            GameLogger.info("HotbarSystem successfully initialized synchronously.");
        }
        return GameContext.get().getHotbarSystem();
    }


    public PlayerAnimations getAnimations() {
        return animations;
    }

    private void initializeGLResources() {
        try {
            this.stage = new Stage(new ScreenViewport());
            this.skin = new Skin(Gdx.files.internal("Skins/uiskin.json"));
            this.font = new BitmapFont(Gdx.files.internal("Skins/default.fnt"));
            font.getData().setScale(0.8f);
            font.setColor(Color.WHITE);
            this.animations = new PlayerAnimations(getCharacterType());
            this.initialized = true;
            fontInitialized = true;
            GameLogger.info("Player GL resources initialized: " + username);
        } catch (Exception e) {
            GameLogger.error("Failed to initialize GL resources: " + e.getMessage());
        }
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

    public void setBufferedDirection(String direction) {
        synchronized (movementLock) {
            bufferedDirection = direction;
            bufferedTime = 0f;
        }
    }

    public void updateFromPlayerData(PlayerData data) {
        if (data == null) return;
        this.playerData = data;
        this.pokemonParty = new PokemonParty();
        if (data.getPartyPokemon() != null) {
            for (var pData : data.getPartyPokemon()) {
                if (pData != null) {
                    Pokemon p = pData.toPokemon();
                    if (p != null) {
                        this.pokemonParty.addPokemon(p);
                    }
                }
            }
        }
        this.inventory = new Inventory();
        if (data.getInventoryItems() != null) {
            for (ItemData item : data.getInventoryItems()) {
                if (item != null) {
                    this.inventory.addItem(item);
                }
            }
        }
        this.setX(data.getX());
        this.setY(data.getY());
        this.setDirection(data.getDirection());
        this.setMoving(data.isMoving());
        this.setRunning(data.isWantsToRun());
        this.setCharacterType(data.getCharacterType());
        GameLogger.info("Updated player '" + username + "' from PlayerData.");
    }

    private void initializeBuildInventory() {
        for (PlaceableBlock.BlockType blockType : PlaceableBlock.BlockType.values()) {
            ItemData blockItem = new ItemData(blockType.getId(), 64);
            buildInventory.addItem(blockItem);
        }
    }

    private void initializePosition(int startTileX, int startTileY) {
        this.tileX = startTileX;
        this.tileY = startTileY;
        this.x = (tileX * TILE_SIZE) + (TILE_SIZE / 2f);
        this.y = (tileY * TILE_SIZE);
        this.position.set(x, y);
        this.targetPosition.set(x, y);
        this.renderPosition.set(x, y);
        this.lastPosition.set(x, y);
        this.startPosition.set(x, y);
        this.targetTileX = tileX;
        this.targetTileY = tileY;
    }

    private float tileToPixelX(int tileX) {
        return tileX * World.TILE_SIZE + (World.TILE_SIZE / 2f);
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
                GameLogger.info("No saved data found. Created new PlayerData for: " + username);
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
        if (!moving) {
            stateTime = 0f;
        }
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
            if (this.inventory != null) {
                for (ItemData item : this.inventory.getAllItems()) {
                    if (item != null) {
                        inv.addItem(item.copy());
                    }
                }
            }
            this.inventory = inv;
            GameLogger.info("Set player inventory.");
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

    // The update method—modified for maximum responsiveness.
    public void update(float deltaTime) {
        if (!resourcesInitialized || disposed || animations == null || animations.isDisposed()) {
            initializeResources();
        }
        synchronized (movementLock) {
            // Update buffered input timer if any buffered direction exists.
            if (bufferedDirection != null) {
                bufferedTime += deltaTime;
                if (bufferedTime > BUFFER_WINDOW) {
                    bufferedDirection = null;
                    bufferedTime = 0f;
                }
            }

            // Highest priority: action animations (chop/punch)
            if (animations.isChopping() || animations.isPunching()) {
                stateTime += deltaTime;
                currentFrame = animations.getCurrentFrame(direction, true, isRunning, stateTime);
            }
            // When moving:
            else if (isMoving) {
                float currentDuration = isRunning ? runStepDuration : walkStepDuration;
                float progressIncrement = deltaTime / currentDuration;
                movementProgress += progressIncrement;
                if (movementProgress > 1.0f) {
                    movementProgress = 1.0f;
                }
                updatePosition(movementProgress);
                // Update animationTime for frame selection.
                animationTime += deltaTime * animationSpeedMultiplier;

                // As soon as the tile's position is reached, complete the movement immediately.
                if (movementProgress >= 1.0f) {
                    completeMovement();
                    // Immediately start a new move if a buffered direction exists...
                    if (bufferedDirection != null) {
                        move(bufferedDirection);
                        bufferedDirection = null;
                        bufferedTime = 0f;
                    }
                    // ...or if the movement key is still held.
                    else if (isInputHeld()) {
                        move(direction);
                    }
                    // Otherwise, reset and display the standing frame.
                    else {
                        animationTime = 0f;
                        currentFrame = animations.getStandingFrame(direction);
                    }
                } else {
                    currentFrame = animations.getCurrentFrame(direction, true, isRunning, animationTime);
                }
            }
            // Not moving – show standing frame.
            else {
                stateTime = 0f;
                animationTime = 0f;
                currentFrame = animations.getStandingFrame(direction);
            }

            // Existing item pickup logic.
            ItemEntity nearbyItem = world.getItemEntityManager().getClosestPickableItem(x, y, PICKUP_RANGE);
            if (nearbyItem != null) {
                if (inventory.addItem(nearbyItem.getItemData())) {
                    world.getItemEntityManager().removeItemEntity(nearbyItem.getEntityId());
                    AudioManager.getInstance().playSound(AudioManager.SoundEffect.ITEM_PICKUP_OW);
                }
                if (GameContext.get().isMultiplayer()) {
                    NetworkProtocol.ItemPickup pickup = new NetworkProtocol.ItemPickup();
                    pickup.entityId = nearbyItem.getEntityId();
                    pickup.username = this.getUsername();
                    pickup.timestamp = System.currentTimeMillis();
                    GameContext.get().getGameClient().sendItemPickup(pickup);
                }
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
        stateTime = 0f; // Reset timer for the next move.

        int tileType = GameContext.get().getWorld().getTileTypeAt(getTileX(), getTileY());
        if (tileType == TileType.SAND || tileType == TileType.SNOW ||
            tileType == TileType.DESERT_GRASS || tileType == TileType.DESERT_SAND ||
            tileType == TileType.SNOW_2 || tileType == TileType.SNOW_3 ||
            tileType == TileType.SNOW_TALL_GRASS || tileType == TileType.BEACH_GRASS || tileType == TileType.BEACH_SAND || tileType == TileType.BEACH_SHELL || tileType == TileType.BEACH_GRASS_2 || tileType == TileType.SNOWY_GRASS || tileType == TileType.BEACH_STARFISH) {
            GameContext.get().getWorld().getFootstepEffectManager()
                .addEffect(new FootstepEffect(new Vector2(x, y), direction, 1.0f));
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
                // If already moving, just buffer the new direction.
                bufferedDirection = newDirection;
                bufferedTime = 0f;
                return;
            }
            direction = newDirection;
            if (world == null) {
                GameLogger.error("Cannot move - world is null! Player: " + getUsername());
                return;
            }
            int newTileX = getTileX();
            int newTileY = getTileY();
            switch (newDirection) {
                case "up":
                    newTileY++;
                    break;
                case "down":
                    newTileY--;
                    break;
                case "left":
                    newTileX--;
                    break;
                case "right":
                    newTileX++;
                    break;
                default:
                    return;
            }

            // *** Check world bounds ***
            if (!world.isWithinWorldBounds(newTileX, newTileY)) {
                // Optionally: play a “bump” sound or simply ignore the move.
                GameLogger.info("Player cannot move outside the world border: (" + newTileX + "," + newTileY + ")");
                return;
            }

            // Now check that the destination tile is passable.
            if (world.isPassable(newTileX, newTileY)) {
                targetTileX = newTileX;
                targetTileY = newTileY;
                targetPosition.set(tileToPixelX(newTileX), tileToPixelY(newTileY));
                startPosition.set(x, y);
                isMoving = true;
                movementProgress = 0f;
                bufferedDirection = null;
                bufferedTime = 0f;
            }
        }
    }

    public boolean isInputHeld() {
        return inputHeld;
    }

    public void setInputHeld(boolean held) {
        inputHeld = held;
    }

    public void clearBufferedDirection() {
        synchronized (movementLock) {
            bufferedDirection = null;
            bufferedTime = 0f;
        }
    }

    public void render(SpriteBatch batch) {
        synchronized (resourceLock) {
            if (!initialized) return;
            if (!fontInitialized) {
                initializeGLResources();
                return;
            }
            if (!resourcesInitialized || disposed || animations == null || animations.isDisposed()) {
                initializeResources();
            }
            if (currentFrame != null) {
                Color originalColor = batch.getColor().cpy();
                if (world != null) {
                    Color baseColor = world.getCurrentWorldColor();
                    batch.setColor(baseColor);
                }
                float scale = 1f;
                if (getCharacterType().equalsIgnoreCase("girl")) {
                    scale = 2f;
                }
                float regionW = currentFrame.getRegionWidth() * scale;
                float regionH = currentFrame.getRegionHeight() * scale;
                float drawX = renderPosition.x - (regionW / 2f);
                float drawY = renderPosition.y;
                batch.draw(currentFrame, drawX, drawY, regionW, regionH);
                batch.setColor(originalColor);
                if (username != null && !username.isEmpty() && !username.equals("Player") && font != null) {
                    layout.setText(font, username);
                    float textWidth = layout.width;
                    float nameX = drawX + (regionW - textWidth) / 2f;
                    float nameY = drawY + regionH + 15;
                    font.draw(batch, username, nameX, nameY);
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
        var partyData = new java.util.ArrayList<PokemonData>(Collections.nCopies(PokemonParty.MAX_PARTY_SIZE, null));
        synchronized (pokemonParty.partyLock) {
            var currentParty = pokemonParty.getParty();
            GameLogger.info("Converting party of size " + currentParty.size() + " to PokemonData");
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
        if (!partyData.stream().anyMatch(Objects::nonNull)) {
            GameLogger.error("No valid Pokemon found in party data!");
        }
        playerData.setPartyPokemon(partyData);
        GameLogger.info("Updated player data with party info.");
    }

    public void initializeResources() {
        synchronized (resourceLock) {
            try {
                if (skin == null) {
                    GameLogger.info("Player skin is null; loading default skin");
                    skin = new Skin(Gdx.files.internal("Skins/uiskin.json"));
                }
                if (resourcesInitialized && !disposed && animations != null && !animations.isDisposed()) {
                    return;
                }
                GameLogger.info("Initializing player resources");
                if (animations == null || animations.isDisposed()) {
                    animations = new PlayerAnimations(getCharacterType());
                    GameLogger.info("Created new PlayerAnimations");
                }
                currentFrame = animations.getStandingFrame("down");
                if (currentFrame == null) {
                    throw new RuntimeException("Failed to get initial frame");
                }
                resourcesInitialized = true;
                disposed = false;
                GameLogger.info("Player resources initialized successfully");
                if (GameContext.get().getHotbarSystem() == null && GameContext.get().getUiStage() != null) {
                    Gdx.app.postRunnable(() -> {
                        Skin hotbarSkin = GameContext.get().getSkin() != null ? GameContext.get().getSkin() : skin;
                        GameContext.get().setHotbarSystem(new HotbarSystem(GameContext.get().getUiStage(), hotbarSkin));
                        GameLogger.info("HotbarSystem successfully initialized.");
                    });
                }
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
            if (disposed) return;
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
}
