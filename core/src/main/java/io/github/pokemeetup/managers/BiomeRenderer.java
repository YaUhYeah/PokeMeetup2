// File: src/main/java/io/github/pokemeetup/managers/BiomeRenderer.java
package io.github.pokemeetup.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.system.gameplay.overworld.mechanics.AutoTileSystem;
import io.github.pokemeetup.utils.textures.TextureManager;
import io.github.pokemeetup.utils.textures.TileType;

/**
 * Renders chunk tiles, including:
 *  - Animated ocean tiles (8 frames)
 *  - Day/night & torch lighting
 *  - Animated “sand_shore” overlay (with inner corners)
 */
public class BiomeRenderer {

    private static final float TEXTURE_BLEED_FIX = 0.001f;

    // Ocean animation timing
    private static final float OCEAN_FRAME_DELAY = 1.5f;
    private static float oceanFrameTimer = 0f;
    private static int oceanFrameIndex = 0; // 0..7

    // Shore animation timing
    private static final float SHORE_FRAME_DELAY = 1.5f;
    private static float shoreFrameTimer = 0f;
    private static int shoreFrameIndex = 0; // 0..7

    /**
     * Update both ocean and shore frames each frame (0..7).
     */
    public static void updateAnimations() {
        float delta = Gdx.graphics.getDeltaTime();

        // Ocean
        oceanFrameTimer += delta;
        if (oceanFrameTimer >= OCEAN_FRAME_DELAY) {
            oceanFrameIndex = (oceanFrameIndex + 1) % 8;
            oceanFrameTimer -= OCEAN_FRAME_DELAY;
        }

        // Shore
        shoreFrameTimer += delta;
        if (shoreFrameTimer >= SHORE_FRAME_DELAY) {
            shoreFrameIndex = (shoreFrameIndex + 1) % 8;
            shoreFrameTimer -= SHORE_FRAME_DELAY;
        }
    }

    /**
     * Render the given chunk’s tiles:
     *  1) Re‐apply shoreline autotiling with shoreFrameIndex (0..7) to animate
     *  2) Draw each tile (animated water if tile=water)
     *  3) Draw any overlay sub‐tile from chunk.getAutotileRegions(),
     *     including composite mini‐overlays for “inner corners.”
     */
    public void renderChunk(SpriteBatch batch, Chunk chunk, World world) {
        final int size = Chunk.CHUNK_SIZE;
        int chunkX = chunk.getChunkX();
        int chunkY = chunk.getChunkY();

        // 1) Re‐apply “sand_shore” autotiling with the updated frame
        //    so we see the 8–frame animation:
        AutoTileSystem autoTileSystem = new AutoTileSystem();
        autoTileSystem.applyShorelineAutotiling(chunk, shoreFrameIndex, world);

        // Update animation indexes
        updateAnimations();

        // 2) Obtain (or re‐obtain) the chunk’s overlay array:
        TextureRegion[][] overlay = chunk.getAutotileRegions();
        if (overlay == null) {
            // Possibly chunk just created, ensure it's allocated:
            overlay = new TextureRegion[size][size];
            chunk.setAutotileRegions(overlay);
        }

        // 3) Draw each cell
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                float px = (chunkX * size + x) * World.TILE_SIZE;
                float py = (chunkY * size + y) * World.TILE_SIZE;

                // Decide day/night lighting color:
                Color tileColor = determineLightingColor(world, chunkX, chunkY, x, y);
                batch.setColor(tileColor);

                // If tile is water => draw animated ocean center frame
                int tileType = chunk.getTileType(x, y);
                if (tileType == TileType.WATER) {
                    TextureRegion waterAnim = TextureManager.getOceanCenterFrame(oceanFrameIndex);
                    if (waterAnim != null) {
                        batch.draw(waterAnim, px, py, World.TILE_SIZE, World.TILE_SIZE);
                    }
                } else {
                    // Otherwise, draw static tile
                    TextureRegion baseTex = TextureManager.getTileTexture(tileType);
                    if (baseTex != null) {
                        // Fix for potential bleeding
                        float u  = baseTex.getU()  + TEXTURE_BLEED_FIX;
                        float v2 = baseTex.getV2() - TEXTURE_BLEED_FIX;
                        float u2 = baseTex.getU2() - TEXTURE_BLEED_FIX;
                        float v  = baseTex.getV()  + TEXTURE_BLEED_FIX;

                        batch.draw(baseTex.getTexture(),
                            px, py,
                            World.TILE_SIZE, World.TILE_SIZE,
                            u, v2, u2, v);
                    }
                }

                TextureRegion shoreOverlay = overlay[x][y];
                if (shoreOverlay instanceof AutoTileSystem.CompositeRegion) {
                    AutoTileSystem.CompositeRegion comp = (AutoTileSystem.CompositeRegion) shoreOverlay;
                    // Draw base 32×32
                    batch.draw(comp.getBase32(), px, py, 32,32);
                    // Then corners
                    for (AutoTileSystem.MiniOverlay mo : comp.getOverlays()) {
                        batch.draw(mo.region16, px+mo.offsetX, py+mo.offsetY, 16,16);
                    }
                }
                else if (shoreOverlay != null) {
                    // Single tile
                    batch.draw(shoreOverlay, px, py, 32,32);
                }


            }
        }
    }

    /**
     * Combine day/night color + optional torch glow
     */
    private Color determineLightingColor(World world, int chunkX, int chunkY, int lx, int ly) {
        Color base = world.getCurrentWorldColor().cpy();
        // Torch glow?
        int gx = chunkX * Chunk.CHUNK_SIZE + lx;
        int gy = chunkY * Chunk.CHUNK_SIZE + ly;
        Float light = world.getLightLevelAtTile(new Vector2(gx, gy));
        if (light != null && light > 0f) {
            Color torch = new Color(1f, 0.8f, 0.6f, 1f);
            base.lerp(torch, light);
        }
        return base;
    }public enum Direction {
        NORTH, SOUTH, EAST, WEST
    }
}
