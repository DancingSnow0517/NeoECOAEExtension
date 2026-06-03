package cn.dancingsnow.neoecoae.gui.widget;

import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;

public final class ECOHostStyles {
    public static final int PANEL_WIDTH = 460;
    public static final int PANEL_HEIGHT = 380;

    public static final int TEXT = 0x263238;
    public static final int MUTED = 0x627178;
    public static final int SOFT = 0x7d8a91;
    public static final int ACCENT = 0x1f8ea3;
    public static final int GREEN = 0x3c9f68;
    public static final int AMBER = 0xbd8128;
    public static final int RED = 0xbd4b62;

    private ECOHostStyles() {
    }

    public static void titleText(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).textColor(TEXT).textShadow(false);
    }

    public static void subtitleText(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).textColor(MUTED).textShadow(false);
    }

    public static void sectionText(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).textColor(ACCENT).textShadow(false);
    }

    public static void labelText(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).textColor(MUTED).textShadow(false);
    }

    public static void valueText(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).textColor(TEXT).textShadow(false);
    }

    public static void hintText(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).textColor(SOFT).textShadow(false);
    }

    public static float ratio(long used, long total) {
        if (total <= 0) {
            return 0.0f;
        }
        return Math.clamp((float) used / (float) total, 0.0f, 1.0f);
    }

    public static int percent(long used, long total) {
        if (total <= 0) {
            return 0;
        }
        return Math.clamp((int) ((double) used / (double) total * 100.0), 0, 100);
    }
}
