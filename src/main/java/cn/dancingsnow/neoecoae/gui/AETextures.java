package cn.dancingsnow.neoecoae.gui;

import appeng.client.gui.Icon;
import appeng.core.AppEng;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;

import java.util.HashMap;
import java.util.Map;

public class AETextures {
    private static final Map<Icon, IGuiTexture> cache = new HashMap<>();

    public static IGuiTexture icon(Icon icon) {
        return cache.computeIfAbsent(icon, i -> SpriteTexture.of(AppEng.makeId("textures/guis/states.png"))
            .setSprite(i.x, i.y, i.width, i.height));
    }
}
