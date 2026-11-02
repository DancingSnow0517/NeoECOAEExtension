package cn.dancingsnow.neoecoae.gui.common;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Small 26.1 drawing shims. {@link GuiGraphicsExtractor#text} only accepts integer positions and
 * drops text whose color has no alpha; the 1.21.1 {@code drawString} accepted float positions.
 */
public final class GuiDraw {
    private GuiDraw() {
    }

    public static int opaque(int color) {
        // Same rule as 1.21.1 Font#adjustColor: near-zero alpha meant "opaque".
        return (color & 0xFC000000) == 0 ? color | 0xFF000000 : color;
    }

    public static void text(GuiGraphicsExtractor graphics, Font font, String text, float x, float y, int color, boolean shadow) {
        if (x == (int) x && y == (int) y) {
            graphics.text(font, text, (int) x, (int) y, opaque(color), shadow);
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.text(font, text, 0, 0, opaque(color), shadow);
        graphics.pose().popMatrix();
    }

    public static void text(GuiGraphicsExtractor graphics, Font font, Component text, float x, float y, int color, boolean shadow) {
        if (x == (int) x && y == (int) y) {
            graphics.text(font, text, (int) x, (int) y, opaque(color), shadow);
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.text(font, text, 0, 0, opaque(color), shadow);
        graphics.pose().popMatrix();
    }

    public static void text(GuiGraphicsExtractor graphics, Font font, FormattedCharSequence text, float x, float y, int color, boolean shadow) {
        if (x == (int) x && y == (int) y) {
            graphics.text(font, text, (int) x, (int) y, opaque(color), shadow);
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.text(font, text, 0, 0, opaque(color), shadow);
        graphics.pose().popMatrix();
    }
}
