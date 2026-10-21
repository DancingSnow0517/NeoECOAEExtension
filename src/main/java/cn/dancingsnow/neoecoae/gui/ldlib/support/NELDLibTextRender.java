package cn.dancingsnow.neoecoae.gui.ldlib.support;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

public final class NELDLibTextRender {
    private static final String ELLIPSIS = "...";

    private NELDLibTextRender() {}

    public static String fitWithEllipsis(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(1, maxWidth - font.width(ELLIPSIS))) + ELLIPSIS;
    }

    public static Component fitWithEllipsis(Font font, Component text, int maxWidth) {
        return Component.literal(fitWithEllipsis(font, text.getString(), maxWidth));
    }

    public static String fitScaledWithEllipsis(Font font, String text, int maxWidth, float scale) {
        int unscaledMaxWidth = Math.max(1, (int) Math.floor(maxWidth / Math.max(0.01F, scale)));
        return fitWithEllipsis(font, text, unscaledMaxWidth);
    }

    public static Component truncateWithEllipsis(Font font, Component text, int maxWidth) {
        String raw = text.getString();
        if (maxWidth <= 0) {
            return Component.empty();
        }
        if (font.width(raw) <= maxWidth) {
            return text;
        }
        int ellipsisWidth = font.width(ELLIPSIS);
        if (maxWidth <= ellipsisWidth) {
            return Component.literal(font.plainSubstrByWidth(raw, maxWidth));
        }
        return Component.literal(font.plainSubstrByWidth(raw, maxWidth - ellipsisWidth) + ELLIPSIS);
    }
}
