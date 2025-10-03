package io.github.pokemeetup.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.pokemeetup.system.gameplay.overworld.Chunk;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.textures.TextureManager;
import io.github.pokemeetup.utils.textures.TileType;

public class BiomeRenderer {
    private static final int ANIM_FRAMES = 8;
    private static final float OCEAN_FRAME_DELAY = 0.2f;
    private static final float SHORE_FRAME_DELAY = 0.2f;

    private static float oceanFrameTimer = 0f;
    private static float shoreFrameTimer = 0f;
    private static int oceanFrameIndex = 0;
    private static int shoreFrameIndex = 0;

    private static final TextureRegion[] oceanFrames = new TextureRegion[ANIM_FRAMES];
    // [frame][corner: 0=TL,1=TR,2=BL,3=BR]
    private static final TextureRegion[][] shorelineCornerFrames = new TextureRegion[ANIM_FRAMES][4];
    private static int cachedShoreCornerFrame = -1;
    private static boolean texturesCached = false;

    // current batch color snapshot (avoid Color.equals per tile)
    private float cr = 1f, cg = 1f, cb = 1f, ca = 1f;

    static { cacheTextures(); }

    private static void cacheTextures() {
        if (!texturesCached) {
            for (int i = 0; i < ANIM_FRAMES; i++) {
                oceanFrames[i] = TextureManager.getOceanCenterFrame(i);
            }
            texturesCached = true;
        }
    }

    private static void ensureShorelineCornerCache() {
        if (shoreFrameIndex == cachedShoreCornerFrame) return;
        TextureRegion sheet = TextureManager.getSubTile("sand_shore", shoreFrameIndex, 2, 0);
        if (sheet != null) {
            shorelineCornerFrames[shoreFrameIndex][0] = new TextureRegion(sheet,  0,  0, 16, 16); // TL
            shorelineCornerFrames[shoreFrameIndex][1] = new TextureRegion(sheet, 16,  0, 16, 16); // TR
            shorelineCornerFrames[shoreFrameIndex][2] = new TextureRegion(sheet,  0, 16, 16, 16); // BL
            shorelineCornerFrames[shoreFrameIndex][3] = new TextureRegion(sheet, 16, 16, 16, 16); // BR
        } else {
            shorelineCornerFrames[shoreFrameIndex][0] = null;
            shorelineCornerFrames[shoreFrameIndex][1] = null;
            shorelineCornerFrames[shoreFrameIndex][2] = null;
            shorelineCornerFrames[shoreFrameIndex][3] = null;
        }
        cachedShoreCornerFrame = shoreFrameIndex;
    }

    /** Call once per frame (not per chunk). */
    public void updateAnimations() {
        float delta = Gdx.graphics.getDeltaTime();

        oceanFrameTimer += delta;
        if (oceanFrameTimer >= OCEAN_FRAME_DELAY) {
            oceanFrameIndex = (oceanFrameIndex + 1) % ANIM_FRAMES;
            oceanFrameTimer -= OCEAN_FRAME_DELAY;
        }

        shoreFrameTimer += delta;
        if (shoreFrameTimer >= SHORE_FRAME_DELAY) {
            shoreFrameIndex = (shoreFrameIndex + 1) % ANIM_FRAMES;
            shoreFrameTimer -= SHORE_FRAME_DELAY;
            ensureShorelineCornerCache();
        }
    }

    public void renderChunk(SpriteBatch batch, Chunk chunk, World world) {
        if (chunk == null) return;

        Color original = batch.getColor();
        cr = original.r; cg = original.g; cb = original.b; ca = original.a;

        final int size = Chunk.CHUNK_SIZE;
        final float baseX = chunk.getChunkX() * size * World.TILE_SIZE;
        final float baseY = chunk.getChunkY() * size * World.TILE_SIZE;

        final Color[][] lightMap = chunk.getLightMap();
        final byte[][] shorelineData = chunk.getShorelineData();

        final float br = original.r, bg = original.g, bb = original.b, ba = original.a;

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                float px = baseX + (x * World.TILE_SIZE);
                float py = baseY + (y * World.TILE_SIZE);

                // per-tile lighting with component compare
                if (lightMap != null && lightMap[x] != null) {
                    Color c = lightMap[x][y];
                    if (c != null && (c.r != cr || c.g != cg || c.b != cb || c.a != ca)) {
                        batch.setColor(c);
                        cr = c.r; cg = c.g; cb = c.b; ca = c.a;
                    }
                } else if (cr != br || cg != bg || cb != bb || ca != ba) {
                    batch.setColor(br, bg, bb, ba);
                    cr = br; cg = bg; cb = bb; ca = ba;
                }

                int tileType = chunk.getTileType(x, y);
                TextureRegion tex = (tileType == TileType.WATER)
                    ? oceanFrames[oceanFrameIndex]
                    : TextureManager.getTileTexture(tileType);

                if (tex != null) batch.draw(tex, px, py, World.TILE_SIZE, World.TILE_SIZE);

                if (shorelineData != null && shorelineData[x] != null && shorelineData[x][y] != 0) {
                    renderShoreline(batch, shorelineData[x][y], px, py);
                }
            }
        }

        if (cr != br || cg != bg || cb != bb || ca != ba) {
            batch.setColor(br, bg, bb, ba);
            cr = br; cg = bg; cb = bb; ca = ba;
        }
    }

    private void renderShoreline(SpriteBatch batch, byte data, float px, float py) {
        int edgeMask = data & 0x0F;
        TextureRegion baseShore = TextureManager.getAutoTileRegion("sand_shore", edgeMask, shoreFrameIndex);
        if (baseShore != null) batch.draw(baseShore, px, py, 32, 32);

        if ((data & 0x10) != 0) drawInnerCorner(batch, 0, px, py); // TL
        if ((data & 0x20) != 0) drawInnerCorner(batch, 1, px, py); // TR
        if ((data & 0x40) != 0) drawInnerCorner(batch, 2, px, py); // BL
        if ((data & 0x80) != 0) drawInnerCorner(batch, 3, px, py); // BR
    }

    private void drawInnerCorner(SpriteBatch batch, int corner, float px, float py) {
        ensureShorelineCornerCache();
        TextureRegion mini = shorelineCornerFrames[shoreFrameIndex][corner];
        if (mini == null) return;
        float dx = (corner == 1 || corner == 3) ? 16 : 0;
        float dy = (corner == 0 || corner == 1) ? 16 : 0;
        batch.draw(mini, px + dx, py + dy, 16, 16);
    }

    public enum Direction { NORTH, SOUTH, EAST, WEST }
}
