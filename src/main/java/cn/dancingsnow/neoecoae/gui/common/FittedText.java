package cn.dancingsnow.neoecoae.gui.common;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Single-line text drawn at a scale, shared by the GUI panels.
 *
 * <p>Shrink-to-fit uses one rule: {@code scale = min(maxScale, maxWidth / textWidth)}, where the text width is
 * clamped to at least 1 so that empty text never divides by zero. Text is never enlarged beyond maxScale and
 * {@link #draw} never clips it; callers that need clipping at a fixed scale use {@link #drawClipped}.
 *
 * <p>Coordinates and widths are floats because the panels position text at fractional offsets; int arguments
 * widen implicitly.
 *
 * <p>Edge cases shared by every overload: null or empty text draws nothing; a non-positive maxWidth or scale
 * draws nothing. Colours are ARGB values passed straight through, as the existing callers do.
 *
 * <p>The {@code y} argument is the top of the scaled text. Callers that centre text vertically compute it from
 * {@link #fitScale} and {@code font.lineHeight}; this class does not do vertical layout.
 */
public final class FittedText {
    private FittedText() {
    }

    /** Horizontal placement of text inside the box {@code [x, x + maxWidth]}. */
    public enum Align {
        LEFT, CENTER, RIGHT
    }

    /**
     * The shared fit rule on an unscaled width. Widths below 1 are treated as 1. The result may be zero or negative
     * when maxWidth is not positive, and callers should treat any value at or below zero as "draw nothing".
     */
    public static float fitScale(int textWidth, float maxWidth, float maxScale) {
        return Math.min(maxScale, maxWidth / Math.max(1, textWidth));
    }

    /** {@link #fitScale(int, float, float)} for a laid-out component; null counts as empty text. */
    public static float fitScale(Font font, Component text, float maxWidth, float maxScale) {
        return fitScale(text == null ? 0 : font.width(text), maxWidth, maxScale);
    }

    /** {@link #fitScale(Font, Component, float, float)} for a plain string; null counts as empty text. */
    public static float fitScale(Font font, String text, float maxWidth, float maxScale) {
        return fitScale(text == null ? 0 : font.width(text), maxWidth, maxScale);
    }

    /**
     * Draws text shrunk to fit maxWidth (see class docs for the rule) and aligned inside the box
     * {@code [x, x + maxWidth]}. The text is never clipped.
     */
    public static void draw(GuiGraphics g, Font font, Component text, float x, float y, float maxWidth, int color,
                            boolean shadow, Align align, float maxScale) {
        if (!hasText(text) || maxWidth <= 0) return;
        float scale = fitScale(font, text, maxWidth, maxScale);
        if (scale <= 0) return;
        drawScaled(g, font, text, x, y, maxWidth, scale, color, shadow, align);
    }

    /** {@link #draw(GuiGraphics, Font, Component, float, float, float, int, boolean, Align, float)} for a plain string. */
    public static void draw(GuiGraphics g, Font font, String text, float x, float y, float maxWidth, int color,
                            boolean shadow, Align align, float maxScale) {
        draw(g, font, text == null ? null : Component.literal(text), x, y, maxWidth, color, shadow, align, maxScale);
    }

    /**
     * Draws text at a caller-chosen scale, aligned inside the box {@code [x, x + maxWidth]}. Unlike
     * {@link #draw}, this neither fits nor clips, so long text can extend past the box edge.
     */
    public static void drawScaled(GuiGraphics g, Font font, Component text, float x, float y, float maxWidth,
                                  float scale, int color, boolean shadow, Align align) {
        if (!hasText(text) || scale <= 0) return;
        float left = alignedLeft(x, maxWidth, font.width(text) * scale, align);
        paint(g, font, text, left, y, scale, color, shadow);
    }

    /**
     * Draws a plain string at a fixed scale, left-aligned at {@code x}, clipped to {@code maxWidth} in unscaled
     * units. The clip is {@code (int) (maxWidth / scale)}, which matches the metric labels that depend on it; the
     * callers compute maxWidth with a ceiling so the final glyph is not lost to rounding.
     */
    public static void drawClipped(GuiGraphics g, Font font, String text, float x, float y, float maxWidth,
                                   float scale, int color, boolean shadow) {
        if (text == null || text.isEmpty() || scale <= 0) return;
        String visible = font.plainSubstrByWidth(text, Math.max(0, (int) (maxWidth / scale)));
        paint(g, font, Component.literal(visible), x, y, scale, color, shadow);
    }

    private static boolean hasText(Component text) {
        return text != null && !text.getString().isEmpty();
    }

    private static float alignedLeft(float x, float maxWidth, float scaledTextWidth, Align align) {
        return switch (align) {
            case LEFT -> x;
            case CENTER -> x + (maxWidth - scaledTextWidth) / 2.0F;
            case RIGHT -> x + maxWidth - scaledTextWidth;
        };
    }

    private static void paint(GuiGraphics g, Font font, Component text, float left, float top, float scale,
                              int color, boolean shadow) {
        var pose = g.pose();
        pose.pushPose();
        try {
            pose.translate(left, top, 0);
            pose.scale(scale, scale, 1);
            g.drawString(font, text, 0, 0, color, shadow);
        } finally {
            pose.popPose();
        }
    }
}
