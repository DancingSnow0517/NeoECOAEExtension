package cn.dancingsnow.neoecoae.gui.theme;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.IGuiTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.SpriteTexture;
import net.minecraft.resources.ResourceLocation;

/** Inspect the image a real widget will resolve on draw, without requiring an OpenGL context. */
public final class PanelAtlasTestSupport {
    private PanelAtlasTestSupport() {}

    public static ResourceLocation image(IGuiTexture texture) {
        return texture instanceof PanelAtlasSprite panel ? panel.resolve().getImageLocation()
            : texture instanceof SpriteTexture sprite ? sprite.getImageLocation() : null;
    }
}
