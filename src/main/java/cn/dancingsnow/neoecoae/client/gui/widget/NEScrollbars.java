package cn.dancingsnow.neoecoae.client.gui.widget;

import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.Scrollbar;
import net.minecraft.resources.ResourceLocation;

public final class NEScrollbars {
    private static final ResourceLocation ATLAS =
        new ResourceLocation("neoecoae", "textures/gui/vendor/ae2/scrollbars.png");
    private static final int ATLAS_WIDTH = 38;
    private static final int ATLAS_HEIGHT = 15;
    public static final Scrollbar.Style BIG = createStyle(12, 0, 12);
    public static final Scrollbar.Style SMALL = createStyle(7, 24, 31);

    private static Scrollbar.Style createStyle(int width, int enabledX, int disabledX) {
        // AE2 15's Style.create assumes 256x256; our high-version sprites share a 38x15 atlas.
        return new Scrollbar.Style(width, ATLAS_HEIGHT, ATLAS,
            Blitter.texture(ATLAS, ATLAS_WIDTH, ATLAS_HEIGHT).src(enabledX, 0, width, ATLAS_HEIGHT),
            Blitter.texture(ATLAS, ATLAS_WIDTH, ATLAS_HEIGHT).src(disabledX, 0, width, ATLAS_HEIGHT));
    }

    public static Blitter smallHandle(boolean enabled) {
        return cn.dancingsnow.neoecoae.client.gui.workstation.WorkstationInterfaceArtwork.scrollbar(enabled);
    }

    private NEScrollbars() {}
}
