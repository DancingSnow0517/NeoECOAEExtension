package cn.dancingsnow.neoecoae.gui.theme;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;

import java.util.HashMap;
import java.util.Map;

public class AETextures {
    private static final Map<ECOIcon, IGuiTexture> cache = new HashMap<>();
    private static final IGuiTexture SLOT_WITH_FRAME = IGuiTexture.group(
        new ColorRectTexture(0xFFF2F2F2),
        icon(ECOIcon.SLOT_BACKGROUND)
    );

    public static IGuiTexture icon(ECOIcon icon) {
        return cache.computeIfAbsent(icon, i -> SpriteTexture.of(NeoECOAE.id("textures/gui/ae2/states.png"))
            .setSprite(i.x, i.y, i.width, i.height));
    }

    public static IGuiTexture slotWithFrame() {
        return SLOT_WITH_FRAME;
    }
}
