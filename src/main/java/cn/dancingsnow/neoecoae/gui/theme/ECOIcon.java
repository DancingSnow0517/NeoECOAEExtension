package cn.dancingsnow.neoecoae.gui.theme;

import cn.dancingsnow.neoecoae.NeoECOAE;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Coordinates in our own textures/gui/vendor/ae2/states.png atlas. */
public enum ECOIcon {
    CLEAR(96, 0),
    HELP(176, 0),
    BACK(96, 16),
    TYPE_FILTER_ALL(160, 16),
    ARROW_RIGHT(32, 48),
    ARROW_LEFT(48, 48),
    COG(32, 64),
    COG_DISABLED(48, 64),
    PRIORITY(144, 64),
    PATTERN_ACCESS_SHOW(64, 80),
    PATTERN_ACCESS_HIDE(80, 80),
    AUTO_EXPORT_OFF(112, 96),
    AUTO_EXPORT_ON(128, 96),
    TOOLBAR_BUTTON_BACKGROUND(176, 128, 18, 20),
    TOOLBAR_BUTTON_BACKGROUND_FOCUS(194, 128, 18, 20),
    TOOLBAR_BUTTON_BACKGROUND_HOVER(212, 128, 18, 20),
    CRAFT_HAMMER(48, 144),
    POWER_UNIT_AE(0, 160),
    TAB_BUTTON_BACKGROUND(160, 192, 20, 20),
    SLOT_BACKGROUND(192, 192, 18, 18),
    BACKGROUND_UPGRADE(240, 208),
    TAB_BUTTON_BACKGROUND_FOCUS(160, 224, 22, 22),
    SCHEDULING_ROUND_ROBIN(16, 240),
    BACKGROUND_BLANK_PATTERN(240, 128),
    SUBSTITUTION_ENABLED(224, 208, 8, 8),
    SUBSTITUTION_DISABLED(232, 208, 8, 8),
    FLUID_SUBSTITUTION_ENABLED(224, 216, 8, 8),
    FLUID_SUBSTITUTION_DISABLED(232, 216, 8, 8),
    CYCLE_ENABLED(16, 240),
    CYCLE_DISABLED(32, 240);

    public final int x;
    public final int y;
    public final int width;
    public final int height;
    public static final ResourceLocation ATLAS = NeoECOAE.id("textures/gui/vendor/ae2/states.png");

    ECOIcon(int x, int y) {
        this(x, y, 16, 16);
    }

    ECOIcon(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void blit(GuiGraphics graphics, int x, int y, int z) {
        graphics.blit(ATLAS, x, y, z, this.x, this.y, width, height, 256, 256);
    }
}
