package cn.dancingsnow.neoecoae.gui.theme;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.ColorRectTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.IGuiTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.SpriteTexture;

import java.util.HashMap;
import java.util.Map;

public class AETextures {
    private static final Map<ECOIcon, IGuiTexture> cache = new HashMap<>();
    private static final IGuiTexture SLOT_WITH_FRAME = IGuiTexture.group(
        new ColorRectTexture(0xFFF2F2F2),
        icon(ECOIcon.SLOT_BACKGROUND)
    );

    public static IGuiTexture icon(ECOIcon icon) {
        return cache.computeIfAbsent(icon, i -> SpriteTexture.of(NeoECOAE.id("textures/gui/vendor/ae2/states.png"))
            .setSprite(i.x, i.y, i.width, i.height));
    }

    public static IGuiTexture slotWithFrame() {
        return SLOT_WITH_FRAME;
    }
}
