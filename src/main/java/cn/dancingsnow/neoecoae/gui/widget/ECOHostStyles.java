package cn.dancingsnow.neoecoae.gui.widget;

import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;

import cn.dancingsnow.neoecoae.gui.NEGuiColors;

public final class ECOHostStyles {
    public static final int PANEL_WIDTH = 208;
    public static final int PANEL_HEIGHT = 214;
    public static final int STORAGE_PANEL_HEIGHT = 238;
    public static final int DETAIL_HEIGHT = 86;
    public static final int STORAGE_DETAIL_HEIGHT = 110;

    public static final int TEXT = NEGuiColors.textColor(0x263238);
    public static final int MUTED = NEGuiColors.textColor(0xffffff);
    public static final int SOFT = NEGuiColors.textColor(0x7d8a91);
    public static final int ACCENT = NEGuiColors.textColor(0x403e53);

    private ECOHostStyles() {
    }

    public static void titleText(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(false).textWrap(TextWrap.HOVER_ROLL).textColor(TEXT).textShadow(false);
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

    public static void compactValueText(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).fontSize(9.0f).textColor(TEXT).textShadow(false);
    }

    public static void compactLabelText(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).fontSize(9.0f).textColor(MUTED).textShadow(false);
    }

    public static void hintText(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).textColor(ACCENT).textShadow(false);
    }

    public static void compactHintText(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).fontSize(8.5f).textColor(ACCENT).textShadow(false);
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
