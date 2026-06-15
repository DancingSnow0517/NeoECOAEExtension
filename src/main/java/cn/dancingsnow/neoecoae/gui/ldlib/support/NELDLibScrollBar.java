package cn.dancingsnow.neoecoae.gui.ldlib.support;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

public final class NELDLibScrollBar {
    private NELDLibScrollBar() {}

    public static void drawHorizontal(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            int contentWidth,
            int viewportWidth,
            double scrollPixels,
            int trackColor,
            int innerColor,
            int thumbColor,
            int minThumbWidth) {
        graphics.fill(x, y, x + width, y + height, trackColor);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, innerColor);
        if (contentWidth <= viewportWidth) {
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, thumbColor);
            return;
        }
        int thumbWidth = thumbSize(width, viewportWidth, contentWidth, minThumbWidth);
        int thumbX = x + thumbOffset(width, thumbWidth, scrollPixels, maxScroll(contentWidth, viewportWidth));
        graphics.fill(thumbX, y, thumbX + thumbWidth, y + height, thumbColor);
    }

    public static void drawVertical(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            int contentHeight,
            int viewportHeight,
            double scrollPixels,
            int trackColor,
            int thumbColor,
            int minThumbHeight) {
        if (contentHeight <= viewportHeight) {
            return;
        }
        graphics.fill(x, y, x + width, y + height, trackColor);
        int thumbHeight = thumbSize(height, viewportHeight, contentHeight, minThumbHeight);
        int thumbY = y + thumbOffset(height, thumbHeight, scrollPixels, maxScroll(contentHeight, viewportHeight));
        graphics.fill(x, thumbY, x + width, thumbY + thumbHeight, thumbColor);
    }

    public static double maxScroll(int contentSize, int viewportSize) {
        return Math.max(0.0D, contentSize - viewportSize);
    }

    public static int thumbSize(int trackSize, int viewportSize, int contentSize, int minThumbSize) {
        if (contentSize <= 0) {
            return trackSize;
        }
        return Math.max(minThumbSize, trackSize * viewportSize / contentSize);
    }

    public static double scrollFromMouse(
            double mouse, int trackStart, int trackSize, int viewportSize, int contentSize, int minThumbSize) {
        double maxScroll = maxScroll(contentSize, viewportSize);
        if (maxScroll <= 0.0D) {
            return 0.0D;
        }
        int thumbSize = thumbSize(trackSize, viewportSize, contentSize, minThumbSize);
        int travel = trackSize - thumbSize;
        double relative = mouse - trackStart - thumbSize / 2.0D;
        return Mth.clamp(relative * maxScroll / Math.max(1, travel), 0.0D, maxScroll);
    }

    private static int thumbOffset(int trackSize, int thumbSize, double scrollPixels, double maxScroll) {
        if (maxScroll <= 0.0D) {
            return 0;
        }
        return (int) Math.round((trackSize - thumbSize) * scrollPixels / maxScroll);
    }
}
