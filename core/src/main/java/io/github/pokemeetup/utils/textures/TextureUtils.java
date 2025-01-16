package io.github.pokemeetup.utils.textures;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

public class TextureUtils {
    public static Pixmap textureRegionToPixmap(TextureRegion region) {
        if (region == null) return null;

        Texture texture = region.getTexture();
        if (!texture.getTextureData().isPrepared()) {
            texture.getTextureData().prepare();
        }

        Pixmap originalPixmap = texture.getTextureData().consumePixmap();
        Pixmap regionPixmap = new Pixmap(
            region.getRegionWidth(),
            region.getRegionHeight(),
            Pixmap.Format.RGBA8888
        );

        // Copy the region's pixels
        regionPixmap.drawPixmap(
            originalPixmap,
            0, 0,
            region.getRegionX(), region.getRegionY(),
            region.getRegionWidth(), region.getRegionHeight()
        );

        originalPixmap.dispose();
        return regionPixmap;
    }
}
