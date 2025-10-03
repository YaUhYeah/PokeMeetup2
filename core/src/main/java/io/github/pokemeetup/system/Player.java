package io.github.pokemeetup.system;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.managers.FootstepEffect;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.pokemon.Pokemon;
import io.github.pokemeetup.pokemon.PokemonParty;
import io.github.pokemeetup.screens.otherui.HotbarSystem;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.data.PlayerData;
import io.github.pokemeetup.system.data.PokemonData;
import io.github.pokemeetup.system.gameplay.PlayerAnimations;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.ItemEntity;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TileType;

import java.util.Collections;
import java.util.Objects;

import static io.github.pokemeetup.system.gameplay.overworld.World.INTERACTION_RANGE;
import static io.github.pokemeetup.system.gameplay.overworld.World.TILE_SIZE;

public class Player implements Positionable {
    public static final int FRAME_WIDTH = 32;
    private static final float BUFFER_WINDOW = 0.08f;

    private static final float SMOOTH_WALK_DURATION = 0.32f;  // 4 frames × 0.08s
    private static final float SMOOTH_RUN_DURATION = 0.24f;   // 4 frames × 0.06s
    @Override
    public boolean wasOnWater() {
        return wasOnWater;
    }

    private int elevationLevel = 0;

    public void setElevationLevel(int elevationLevel) {
        this.elevationLevel = elevationLevel;
    }
    public void stopMovement() {
        synchronized (movementLock) {
            if (isMoving) {
                // Snap to the target destination to ensure grid alignment
                x = targetPosition.x;
                y = targetPosition.y;
                tileX = targetTileX;
                tileY = targetTileY;
                position.set(x, y);
                renderPosition.set(x, y);
                updateCollisionBoxes();
            }
            // Reset all movement state variables
            isMoving = false;
            movementProgress = 0f;
            queuedDirection = null;
            animationTime = 0f; // Reset animation timer

            // Also ensure any action animations are stopped
            if (animations != null) {
                animations.stopPunching();
                animations.stopChopping();
            }
        }
    }
    /**
     * ✅ **REFACTOR:** Centralized teleport method.
     * This method updates the player's position and tells the World and GameClient
     * to handle the complex state-clearing and reloading logic.
     *
     * @param tileX The destination tile X coordinate.
     * @param tileY The destination tile Y coordinate.
     */
    public void teleportTo(int tileX, int tileY) {
        // Stop any current movement
        setMoving(false);

        // Convert tile coordinates to pixel coordinates
        float pixelX = tileX * World.TILE_SIZE;
        float pixelY = tileY * World.TILE_SIZE;

        // Update all position representations
        setX(pixelX);
        setY(pixelY);
        setRenderPosition(new Vector2(pixelX, pixelY));

        // Notify the world to handle the teleport logic (clearing chunks, etc.)
        if (this.world != null) {
            this.world.handleTeleport(this);
        }

        // If multiplayer, immediately notify the server of the new position
        if (GameContext.get().isMultiplayer()) {
            GameClient client = GameContext.get().getGameClient();
            if (client != null) {
                client.sendPlayerUpdate();
                client.savePlayerState(getPlayerData());
            }
        }
    }


    @Override
    public void setWasOnWater(boolean onWater) {
        this.wasOnWater = onWater;
    }

    @Override
    public float getWaterSoundTimer() {
        return waterSoundTimer;
    }

    @Override
    public void setWaterSoundTimer(float timer) {
        this.waterSoundTimer = timer;
    }

    @Override
    public void updateWaterSoundTimer(float delta) {
        if (this.waterSoundTimer > 0) {
            this.waterSoundTimer -= delta;
        }
    }

    private boolean inputHeld = false;
    private boolean wasOnWater = false;
    private float waterSoundTimer = 0f;
    public static final int FRAME_HEIGHT = 48;
    private static final float COLLISION_BOX_WIDTH_RATIO = 0.6f;
    private static final float COLLISION_BOX_HEIGHT_RATIO = 0.4f;

    public Vector2 getRenderPosition() {
        return renderPosition;
    }

    private static final float COLLISION_BUFFER = 4f;
    private static final long VALIDATION_INTERVAL = 1000;
    private static final float PICKUP_RANGE = 48f;
    private final Object movementLock = new Object();
    private final Object resourceLock = new Object();
    private final Object fontLock = new Object();
    private final Object inventoryLock = new Object();
    private final GlyphLayout layout = new GlyphLayout();
    public volatile boolean initialized = false;
    private PlayerAnimations animations;
    private String username;
    private World world;
    private String queuedDirection = null;
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

        initializeFromSavedState();
        this.renderPosition = new Vector2(x, y);
        this.lastPosition = new Vector2(x, y);
        Gdx.app.postRunnable(this::initializeGLResources);
    }

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
            queuedDirection = direction;
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

    private boolean isVisuallyMoving = false;
    private float visualMovementTimer = 0f;
    private static final float TOTAL_WALK_ANIMATION_TIME = PlayerAnimations.WALK_FRAME_DURATION * 4; // 0.48s
    private static final float TOTAL_RUN_ANIMATION_TIME = PlayerAnimations.RUN_FRAME_DURATION * 4;   // 0.32s

    public void setSkin(Skin skin) {
        this.skin = skin;
    }
    public void update(float deltaTime) {
        if (!resourcesInitialized || disposed || animations == null || animations.isDisposed()) {
            initializeResources();
        }

        synchronized (movementLock) {
            if (isMoving) {
                float moveDuration = isRunning ? SMOOTH_RUN_DURATION : SMOOTH_WALK_DURATION;
                movementProgress += deltaTime / moveDuration;

                if (movementProgress >= 1.0f) {
                    completeMovement();
                    // Handle queued input...
                } else {
                    // Smooth position interpolation
                    float smoothed = smoothStep(movementProgress);
                    x = MathUtils.lerp(startPosition.x, targetPosition.x, smoothed);
                    y = MathUtils.lerp(startPosition.y, targetPosition.y, smoothed);
                    position.set(x, y);

                    // Tighter render position following for responsiveness
                    renderPosition.set(x, y);  // Direct follow, no lag
                }

                // FIXED: Direct linear animation timing
                // Ensures all 4 frames are shown equally during movement
                float frameCount = 4f;
                float frameDuration = isRunning ? PlayerAnimations.RUN_FRAME_DURATION : PlayerAnimations.WALK_FRAME_DURATION;

                // Animation progresses linearly with movement
                // This ensures each frame gets equal screen time
                animationTime = movementProgress * (frameCount * frameDuration);

                updateCollisionBoxes();
            } else {
                animationTime = 0f;
                renderPosition.set(x, y);
            }

            currentFrame = animations.getCurrentFrame(
                direction,
                isMoving,
                isRunning,
                animationTime
            );

            ItemEntity nearbyItem = world.getItemEntityManager()
                .getClosestPickableItem(x, y, PICKUP_RANGE);
            if (nearbyItem != null) {
                if (inventory.addItem(nearbyItem.getItemData())) {
                    world.getItemEntityManager().removeItemEntity(nearbyItem.getEntityId());
                    AudioManager.getInstance().playSound(AudioManager.SoundEffect.ITEM_PICKUP_OW);
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
    }

    private float animationCycleTime = 0f;
    private static final float MOVEMENT_START_DELAY = 0.02f;  // Tiny delay before movement starts
    private static final float MOVEMENT_END_HOLD = 0.03f;     // Hold at end position briefly
    private float movementStartTimer = 0f;
    private float movementEndTimer = 0f;
    private boolean movementStarted = false;
    private boolean movementEnding = false;
    private static final float ACCELERATION_CURVE = 2.5f;

    // [NEW] Enhanced easing function for more natural movement
    private float smoothStepEnhanced(float t) {
        t = MathUtils.clamp(t, 0f, 1f);

        // Custom curve that starts slow, speeds up, then slows down
        // This creates a more natural acceleration/deceleration
        if (t < 0.5f) {
            // Acceleration phase - ease in
            float accelerated = t * 2f;
            return (float) (0.5f * Math.pow(accelerated, ACCELERATION_CURVE));
        } else {
            // Deceleration phase - ease out
            float decelerated = (t - 0.5f) * 2f;
            return (float) (0.5f + 0.5f * (1f - Math.pow(1f - decelerated, ACCELERATION_CURVE)));
        }
    }

    // Replace the existing easeInOutQuad with this smoother version
    private float easeInOutQuad(float t) {
        // Smoother acceleration and deceleration
        if (t < 0.3f) {
            // Slower start for more weight
            return 3.33f * t * t;
        } else if (t > 0.7f) {
            // Slower end for smooth landing
            float endT = (t - 0.7f) / 0.3f;
            return 0.7f + 0.3f * (1f - (1f - endT) * (1f - endT));
        } else {
            // Linear middle section
            return 0.3f + (t - 0.3f);
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

        // --- FIX: Reset animation time on completion ---
        // This is crucial for fixing the animation issues after a collision or at the end of a move.
        animationTime = 0f;

        if (world != null) {
            int newElevation = world.getElevationAt(this.tileX, this.tileY);
            if (newElevation != -1) {
                this.setElevationLevel(newElevation);
            }
        }

        int tileType = GameContext.get().getWorld().getTileTypeAt(getTileX(), getTileY());
        if (tileType == TileType.SAND || tileType == TileType.SNOW ||
            tileType == TileType.DESERT_GRASS || tileType == TileType.DESERT_SAND ||
            tileType == TileType.SNOW_2 || tileType == TileType.SNOW_3 ||
            tileType == TileType.SNOW_TALL_GRASS || tileType == TileType.BEACH_GRASS ||
            tileType == TileType.BEACH_SAND || tileType == TileType.BEACH_SHELL ||
            tileType == TileType.BEACH_GRASS_2 || tileType == TileType.SNOWY_GRASS ||
            tileType == TileType.BEACH_STARFISH) {
            GameContext.get().getWorld().getFootstepEffectManager()
                .addEffect(new FootstepEffect(new Vector2(x, y), direction, 1.0f));
        }
    }
    // 7. REPLACE smoothstep with simpler version:
    private float smoothStep(float t) {
        t = MathUtils.clamp(t, 0f, 1f);
        // Simple smooth cubic curve
        return t * t * (3f - 2f * t);
    }

    private void updateCollisionBoxes() {
        collisionBox.setPosition(x + (FRAME_WIDTH - collisionBox.width) / 2f, y + COLLISION_BUFFER);
        nextPositionBox.setPosition(targetPosition.x + (FRAME_WIDTH - nextPositionBox.width) / 2f, targetPosition.y + COLLISION_BUFFER);
    }

    private float smoothstep(float x) {
        x = MathUtils.clamp(x, 0f, 1f);
        // More aggressive curve for snappier movement
        return x * x * x * (x * (x * 6 - 15) + 10);
    }

    public void move(String newDirection) {
        synchronized (movementLock) {
            // Queue direction if already moving
            if (isMoving) {
                if (!newDirection.equals(direction)) {
                    queuedDirection = newDirection;
                }
                return;
            }

            direction = newDirection;

            if (world == null) {
                GameLogger.error("Cannot move - world is null! Player: " + getUsername());
                return;
            }

            // Calculate target tile
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

            // Check boundaries and passability
            if (!world.isWithinWorldBounds(newTileX, newTileY)) {
                GameLogger.info("Player cannot move outside the world border: (" + newTileX + "," + newTileY + ")");
                return;
            }

            if (world.isPassable(newTileX, newTileY)) {
                targetTileX = newTileX;
                targetTileY = newTileY;
                targetPosition.set(tileToPixelX(newTileX), tileToPixelY(newTileY));
                startPosition.set(x, y);

                // Start movement
                isMoving = true;
                movementProgress = 0f;

                // IMPORTANT: Don't fully reset animation if continuing in same direction
                // This prevents jarring animation restarts
                if (!inputHeld || animationTime <= 0) {
                    animationTime = 0f;
                    animationCycleTime = 0f;
                } else {
                    // Continue from current animation position for smooth chaining
                    // But ensure it will complete within this tile
                    float frameCount = 4f;
                    float frameDuration = isRunning ? PlayerAnimations.RUN_FRAME_DURATION : PlayerAnimations.WALK_FRAME_DURATION;
                    float fullCycleDuration = frameCount * frameDuration;

                    // Wrap animation time to start of cycle if needed
                    animationTime = animationTime % fullCycleDuration;
                    animationCycleTime = animationTime;
                }

                queuedDirection = null;
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
            queuedDirection = null;
            bufferedTime = 0f;
        }
    }

    public void render(SpriteBatch batch) {
        synchronized (resourceLock) {
            if (!initialized || disposed || animations == null || animations.isDisposed() || currentFrame == null) {
                return;
            }

            Color originalColor = batch.getColor().cpy();
            if (world != null) {
                batch.setColor(world.getCurrentWorldColor());
            }

            float scale = getCharacterType().equalsIgnoreCase("girl") ? 2f : 1f;
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
        playerData.setCharacterType(getCharacterType()); // [NEW]
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
