package io.github.pokemeetup.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.textures.TextureManager;
import io.github.pokemeetup.utils.textures.TileType;

/**
 * Optimized BiomeRenderer with batched rendering and reduced draw calls
 */
public class BiomeRenderer {


    private static final float OCEAN_FRAME_DELAY = 0.2f;
    private static float oceanFrameTimer = 0f;
    private static int oceanFrameIndex = 0;
    private static final float SHORE_FRAME_DELAY = 0.2f;
    private static float shoreFrameTimer = 0f;
    private static int shoreFrameIndex = 0;

    // Cache for frequently used textures
    private static TextureRegion[] oceanFrames = new TextureRegion[8];
    private static boolean texturesCached = false;

    // Batch state tracking
    private Color lastColor = new Color(Color.WHITE);
    private TextureRegion lastTexture = null;

    static {
        cacheTextures();
    }

    private static void cacheTextures() {
        if (!texturesCached) {
            // Pre-cache ocean animation frames
            for (int i = 0; i < 8; i++) {
                oceanFrames[i] = TextureManager.getOceanCenterFrame(i);
            }
            texturesCached = true;
        }
    }

    /**
     * Update animations - should be called ONCE per frame, not per chunk
     */
    public void updateAnimations() {
        float delta = Gdx.graphics.getDeltaTime();

        oceanFrameTimer += delta;
        if (oceanFrameTimer >= OCEAN_FRAME_DELAY) {
            oceanFrameIndex = (oceanFrameIndex + 1) % 8;
            oceanFrameTimer -= OCEAN_FRAME_DELAY;
        }

        shoreFrameTimer += delta;
        if (shoreFrameTimer >= SHORE_FRAME_DELAY) {
            shoreFrameIndex = (shoreFrameIndex + 1) % 8;
            shoreFrameTimer -= SHORE_FRAME_DELAY;
        }
    }

    /**
     * Optimized chunk rendering with batching
     */
    public void renderChunk(SpriteBatch batch, Chunk chunk, World world) {
        if (chunk == null) return;

        lastColor.set(batch.getColor());
        final int size = Chunk.CHUNK_SIZE;
        int chunkX = chunk.getChunkX();
        int chunkY = chunk.getChunkY();

        Color[][] lightMap = chunk.getLightMap();
        byte[][] shorelineData = chunk.getShorelineData();

        // Pre-calculate base position
        float baseX = chunkX * size * World.TILE_SIZE;
        float baseY = chunkY * size * World.TILE_SIZE;

        // Get current batch color for restoration
        Color batchColor = batch.getColor();

        // Render all tiles in the chunk
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                float px = baseX + (x * World.TILE_SIZE);
                float py = baseY + (y * World.TILE_SIZE);

                // Apply lighting if available
                if (lightMap != null && lightMap[x] != null && lightMap[x][y] != null) {
                    Color tileColor = lightMap[x][y];
                    if (!tileColor.equals(lastColor)) {
                        batch.setColor(tileColor);
                        lastColor.set(tileColor);
                    }
                } else if (!batchColor.equals(lastColor)) {
                    batch.setColor(batchColor);
                    lastColor.set(batchColor);
                }

                // Render base tile
                int tileType = chunk.getTileType(x, y);
                TextureRegion tileTexture;

                if (tileType == TileType.WATER) {
                    tileTexture = oceanFrames[oceanFrameIndex];
                } else {
                    tileTexture = TextureManager.getTileTexture(tileType);
                }

                if (tileTexture != null) {
                    batch.draw(tileTexture, px, py, World.TILE_SIZE, World.TILE_SIZE);
                }

                // Render shorelines if needed
                if (shorelineData != null && shorelineData[x] != null && shorelineData[x][y] != 0) {
                    renderShoreline(batch, shorelineData[x][y], px, py);
                }
            }
        }

        // Restore original color
        if (!batchColor.equals(lastColor)) {
            batch.setColor(batchColor);
            lastColor.set(batchColor);
        }
    }

    /**
     * Optimized shoreline rendering
     */
    private void renderShoreline(SpriteBatch batch, byte data, float px, float py) {
        int edgeMask = data & 0x0F;

        // Draw base shoreline
        TextureRegion baseShore = TextureManager.getAutoTileRegion("sand_shore", edgeMask, shoreFrameIndex);
        if (baseShore != null) {
            batch.draw(baseShore, px, py, 32, 32);
        }

        // Draw inner corners if needed
        if ((data & 0x10) != 0) drawInnerCorner(batch, 0, px, py); // TL
        if ((data & 0x20) != 0) drawInnerCorner(batch, 1, px, py); // TR
        if ((data & 0x40) != 0) drawInnerCorner(batch, 2, px, py); // BL
        if ((data & 0x80) != 0) drawInnerCorner(batch, 3, px, py); // BR
    }

    /**
     * Optimized inner corner drawing
     */
    private void drawInnerCorner(SpriteBatch batch, int corner, float px, float py) {
        TextureRegion cornerSheet = TextureManager.getSubTile("sand_shore", shoreFrameIndex, 2, 0);
        if (cornerSheet == null) return;

        // Pre-calculated corner positions and sizes
        int sx = (corner == 1 || corner == 3) ? 16 : 0;
        int sy = (corner == 0 || corner == 1) ? 0 : 16;
        float dx = (corner == 1 || corner == 3) ? 16 : 0;
        float dy = (corner == 0 || corner == 1) ? 16 : 0;

        TextureRegion mini = new TextureRegion(cornerSheet, sx, sy, 16, 16);
        batch.draw(mini, px + dx, py + dy, 16, 16);
    }

    public enum Direction {
        NORTH, SOUTH, EAST, WEST
    }
}
