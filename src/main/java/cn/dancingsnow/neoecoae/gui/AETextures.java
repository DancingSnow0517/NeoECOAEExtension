package cn.dancingsnow.neoecoae.gui;

import appeng.client.gui.Icon;
import appeng.core.AppEng;
import net.minecraft.resources.ResourceLocation;

/** AE2 icon wrapper returning ResourceLocation for native-UI screens. */
public class AETextures {
    public static ResourceLocation icon(Icon icon) {
        return AppEng.makeId("textures/guis/states.png");
    }
}
