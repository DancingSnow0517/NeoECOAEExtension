package cn.dancingsnow.neoecoae.client.gui.graph;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;

/** World-space geometry. It stays in the shared GUI buffer until the graph's explicit layer/scissor flush. */
final class GraphDraw {
    private GraphDraw() {}

    static void fill(GuiGraphics graphics, float x1, float y1, float x2, float y2, int color) {
        float left = Math.min(x1, x2), right = Math.max(x1, x2);
        float top = Math.min(y1, y2), bottom = Math.max(y1, y2);
        if (left == right || top == bottom) return;
        quad(graphics, right, bottom, right, top, left, top, left, bottom, color);
    }

    /** Inner frame of the given thickness. */
    static void frame(GuiGraphics graphics, float left, float top, float right, float bottom, int color,
            float thickness) {
        if (right <= left || bottom <= top) return;
        float t = Math.min(thickness, Math.min(right - left, bottom - top) / 2);
        fill(graphics, left, top, right, top + t, color);
        fill(graphics, left, bottom - t, right, bottom, color);
        fill(graphics, left, top + t, left + t, bottom - t, color);
        fill(graphics, right - t, top + t, right, bottom - t, color);
    }

    /** Axis-aligned segments become square-capped rectangles so orthogonal corners have no gaps. */
    static void segment(GuiGraphics graphics, float x1, float y1, float x2, float y2, int color, float halfWidth) {
        if (x1 == x2 || y1 == y2) {
            fill(graphics, Math.min(x1, x2) - halfWidth, Math.min(y1, y2) - halfWidth,
                Math.max(x1, x2) + halfWidth, Math.max(y1, y2) + halfWidth, color);
        } else {
            line(graphics, x1, y1, x2, y2, color, halfWidth);
        }
    }

    static void line(GuiGraphics graphics, float x1, float y1, float x2, float y2, int color, float halfWidth) {
        float dx = x2 - x1, dy = y2 - y1;
        float length = (float) Math.hypot(dx, dy);
        if (length < 0.0001f) return;
        float nx = -dy / length * halfWidth, ny = dx / length * halfWidth;
        quad(graphics, x1 + nx, y1 + ny, x2 + nx, y2 + ny,
            x2 - nx, y2 - ny, x1 - nx, y1 - ny, color);
    }

    static void triangle(GuiGraphics graphics, float ax, float ay, float bx, float by,
            float cx, float cy, int color) {
        // Match GUI quad winding even when a tangent-aligned arrow changes direction.
        if ((bx - ax) * (cy - ay) - (by - ay) * (cx - ax) > 0)
            quad(graphics, ax, ay, cx, cy, bx, by, bx, by, color);
        else quad(graphics, ax, ay, bx, by, cx, cy, cx, cy, color);
    }

    static void dashedLine(GuiGraphics graphics, float x1, float y1, float x2, float y2, int color, float halfWidth) {
        float length = (float) Math.hypot(x2 - x1, y2 - y1);
        if (length < 0.001f) return;
        float dx = (x2 - x1) / length, dy = (y2 - y1) / length;
        for (float start = 0; start < length; start += 9) {
            float end = Math.min(length, start + 5);
            line(graphics, x1 + dx * start, y1 + dy * start, x1 + dx * end, y1 + dy * end, color, halfWidth);
        }
    }

    /** Draws text at a fractional world position and scale. */
    static void text(GuiGraphics graphics, Font font, String text, float x, float y, int color, float scale) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        if (scale != 1) pose.scale(scale, scale, 1);
        graphics.drawString(font, text, 0, 0, color, false);
        pose.popPose();
    }

    static void centeredText(GuiGraphics graphics, Font font, String text, float centerX, float y, int color,
            float scale) {
        text(graphics, font, text, centerX - font.width(text) * scale / 2, y, color, scale);
    }

    static String fit(Font font, String value, float maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(value) <= maxWidth) return value;
        String ellipsis = "…";
        return font.plainSubstrByWidth(value, Math.max(0, (int) maxWidth - font.width(ellipsis))) + ellipsis;
    }

    private static void quad(GuiGraphics graphics, float ax, float ay, float bx, float by,
            float cx, float cy, float dx, float dy, int color) {
        var matrix = graphics.pose().last().pose();
        var buffer = graphics.bufferSource().getBuffer(RenderType.gui());
        buffer.vertex(matrix, ax, ay, 0).color(color).endVertex();
        buffer.vertex(matrix, bx, by, 0).color(color).endVertex();
        buffer.vertex(matrix, cx, cy, 0).color(color).endVertex();
        buffer.vertex(matrix, dx, dy, 0).color(color).endVertex();
    }
}
