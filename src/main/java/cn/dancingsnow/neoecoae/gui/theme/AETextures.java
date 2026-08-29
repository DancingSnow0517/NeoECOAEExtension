package cn.dancingsnow.neoecoae.gui.theme;

import appeng.client.gui.Icon;
import appeng.core.AppEng;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;

import java.util.HashMap;
import java.util.Map;

public class AETextures {
    private static final Map<Icon, IGuiTexture> cache = new HashMap<>();
    private static final IGuiTexture SLOT_WITH_FRAME = IGuiTexture.group(
        new ColorRectTexture(0xFFF2F2F2),
        icon(Icon.SLOT_BACKGROUND)
    );

    public static IGuiTexture icon(Icon icon) {
        return cache.computeIfAbsent(icon, i -> SpriteTexture.of(AppEng.makeId("textures/guis/states.png"))
            .setSprite(i.x, i.y, i.width, i.height));
    }

    public static IGuiTexture slotWithFrame() {
        return SLOT_WITH_FRAME;
    }
}
