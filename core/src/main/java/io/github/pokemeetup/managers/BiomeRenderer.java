package io.github.pokemeetup.managers;


import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.textures.TextureManager;


public class BiomeRenderer {
    private static final float TEXTURE_BLEED_FIX = 0.001f;
    public void renderChunk(SpriteBatch batch, Chunk chunk, World world) {
        int chunkX = chunk.getChunkX();
        int chunkY = chunk.getChunkY();
        Color originalColor = batch.getColor().cpy();

        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
                float worldX = (chunkX * Chunk.CHUNK_SIZE + x) * World.TILE_SIZE;
                float worldY = (chunkY * Chunk.CHUNK_SIZE + y) * World.TILE_SIZE;

                Vector2 tilePos = new Vector2(chunkX * Chunk.CHUNK_SIZE + x,
                    chunkY * Chunk.CHUNK_SIZE + y);
                Color tileColor = world.getCurrentWorldColor().cpy();

                Float lightLevel = world.getLightLevelAtTile(tilePos);
                if (lightLevel != null && lightLevel > 0) {
                    Color lightColor = new Color(1f, 0.8f, 0.6f, 1f);
                    tileColor.lerp(lightColor, lightLevel * 0.7f);
                }

                batch.setColor(tileColor);

                int tileType = chunk.getTileType(x, y);
                TextureRegion tileTexture = TextureManager.getTileTexture(tileType);
                if (tileTexture != null) {
                    // Get the original UV coordinates
                    float u = tileTexture.getU();
                    float v = tileTexture.getV();
                    float u2 = tileTexture.getU2();
                    float v2 = tileTexture.getV2();

                    // Apply bleed fix and flip V coordinates
                    float uFixed = u + TEXTURE_BLEED_FIX;
                    float vFixed = v2 - TEXTURE_BLEED_FIX;  // Use v2 for bottom
                    float u2Fixed = u2 - TEXTURE_BLEED_FIX;
                    float v2Fixed = v + TEXTURE_BLEED_FIX;  // Use v for top

                    batch.draw(tileTexture.getTexture(),
                        worldX, worldY,                // Position
                        World.TILE_SIZE, World.TILE_SIZE,  // Size
                        uFixed, vFixed,                // UV start (bottom-left)
                        u2Fixed, v2Fixed);            // UV end (top-right)
                }
            }
        }

        batch.setColor(originalColor);
    }


    public enum Direction {
        NORTH, SOUTH, EAST, WEST
    }
}
