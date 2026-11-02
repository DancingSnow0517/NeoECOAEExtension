package cn.dancingsnow.neoecoae.client.gui.graph;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;

/** Geometry stays in the shared GUI buffer until the graph's explicit layer/scissor flush. */
final class GraphDraw {
    private GraphDraw() {}

    static void fill(GuiGraphics graphics, float x1, float y1, float x2, float y2, int color) {
        float left = Math.min(x1, x2), right = Math.max(x1, x2);
        float top = Math.min(y1, y2), bottom = Math.max(y1, y2);
        if (left == right || top == bottom) return;
        quad(graphics, right, bottom, right, top, left, top, left, bottom, color);
    }

    static void hLine(GuiGraphics graphics, int left, int right, int y, int color) {
        fill(graphics, Math.min(left, right), y, Math.max(left, right) + 1, y + 1, color);
    }

    static void vLine(GuiGraphics graphics, int x, int top, int bottom, int color) {
        fill(graphics, x, Math.min(top, bottom) + 1, x + 1, Math.max(top, bottom), color);
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
        quad(graphics, ax, ay, bx, by, cx, cy, cx, cy, color);
    }

    private static void quad(GuiGraphics graphics, float ax, float ay, float bx, float by,
            float cx, float cy, float dx, float dy, int color) {
        var matrix = graphics.pose().last().pose();
        var buffer = graphics.bufferSource().getBuffer(RenderType.gui());
        buffer.addVertex(matrix, ax, ay, 0).setColor(color);
        buffer.addVertex(matrix, bx, by, 0).setColor(color);
        buffer.addVertex(matrix, cx, cy, 0).setColor(color);
        buffer.addVertex(matrix, dx, dy, 0).setColor(color);
    }
}
