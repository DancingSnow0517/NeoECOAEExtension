package cn.dancingsnow.neoecoae.gui.crafting;

import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;

/** Shared package-level text styling for crafting host panels. */
final class CraftingHostStyles {
    static final float COMPACT_FONT_SIZE = 8.0F;
    static final float INLINE_STATS_FONT_SIZE = 7.0F;
    static final int ROOT_TEXT = 0x3F3D52;
    static final int PANEL_TEXT = 0xFFEFEAF8;
    static final int PANEL_MUTED = 0xFFC7BFCD;
    static final int PANEL_VALUE = 0xFF8377FF;
    static final int PANEL_OVERFLOW_VALUE = 0xFF000000;
    static final int PANEL_TIME_VALUE = 0xFF55A7FF;
    static final int PANEL_SUCCESS = 0xFF55FF8A;
    static final int PANEL_WARNING = 0xFFFF6A75;
    static final int PANEL_HOST_NORMAL = 0xFF8FE3A0;
    static final int PANEL_HOST_HIGH_ENERGY = 0xFFFFB469;
    static final int PANEL_TEXT_SHIFT_X = -2;

    private CraftingHostStyles() {
    }

    static void compact(TextElement.TextStyle style) {
        style.adaptiveHeight(true)
                .adaptiveWidth(false)
                .fontSize(COMPACT_FONT_SIZE)
                .textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.HOVER_ROLL)
                .textShadow(false);
    }

    static void inlineStats(TextElement.TextStyle style) {
        style.adaptiveHeight(true)
                .adaptiveWidth(true)
                .fontSize(INLINE_STATS_FONT_SIZE)
                .textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.HOVER_ROLL)
                .textShadow(false);
    }
}
