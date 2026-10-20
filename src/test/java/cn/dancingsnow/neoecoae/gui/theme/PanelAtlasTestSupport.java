package cn.dancingsnow.neoecoae.gui.theme;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import net.minecraft.resources.ResourceLocation;

/** Inspect the image a real widget will resolve on draw, without requiring an OpenGL context. */
public final class PanelAtlasTestSupport {
    private PanelAtlasTestSupport() {}

    public static ResourceLocation image(IGuiTexture texture) {
        return texture instanceof PanelAtlasSprite panel ? panel.resolve().getImageLocation()
            : texture instanceof SpriteTexture sprite ? sprite.getImageLocation() : null;
    }
}
