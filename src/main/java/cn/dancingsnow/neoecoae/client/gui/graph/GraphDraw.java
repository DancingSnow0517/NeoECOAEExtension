package cn.dancingsnow.neoecoae.client.gui.graph;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jetbrains.annotations.Nullable;

/** Free-form (non axis-aligned) GUI quads, submitted as custom GUI element render states. */
final class GraphDraw {
    private GraphDraw() {}

    static void fill(GuiGraphicsExtractor graphics, float x1, float y1, float x2, float y2, int color) {
        float left = Math.min(x1, x2), right = Math.max(x1, x2);
        float top = Math.min(y1, y2), bottom = Math.max(y1, y2);
        if (left == right || top == bottom) return;
        quad(graphics, right, bottom, right, top, left, top, left, bottom, color);
    }

    static void hLine(GuiGraphicsExtractor graphics, int left, int right, int y, int color) {
        fill(graphics, Math.min(left, right), y, Math.max(left, right) + 1, y + 1, color);
    }

    static void vLine(GuiGraphicsExtractor graphics, int x, int top, int bottom, int color) {
        fill(graphics, x, Math.min(top, bottom) + 1, x + 1, Math.max(top, bottom), color);
    }

    /** Inner frame of the given thickness. */
    static void frame(GuiGraphicsExtractor graphics, float left, float top, float right, float bottom, int color,
            float thickness) {
        if (right <= left || bottom <= top) return;
        float t = Math.min(thickness, Math.min(right - left, bottom - top) / 2);
        fill(graphics, left, top, right, top + t, color);
        fill(graphics, left, bottom - t, right, bottom, color);
        fill(graphics, left, top + t, left + t, bottom - t, color);
        fill(graphics, right - t, top + t, right, bottom - t, color);
    }

    /** Axis-aligned segments become square-capped rectangles so orthogonal corners have no gaps. */
    static void segment(GuiGraphicsExtractor graphics, float x1, float y1, float x2, float y2, int color, float halfWidth) {
        if (x1 == x2 || y1 == y2) {
            fill(graphics, Math.min(x1, x2) - halfWidth, Math.min(y1, y2) - halfWidth,
                Math.max(x1, x2) + halfWidth, Math.max(y1, y2) + halfWidth, color);
        } else {
            line(graphics, x1, y1, x2, y2, color, halfWidth);
        }
    }

    static void line(GuiGraphicsExtractor graphics, float x1, float y1, float x2, float y2, int color, float halfWidth) {
        float dx = x2 - x1, dy = y2 - y1;
        float length = (float) Math.hypot(dx, dy);
        if (length < 0.0001f) return;
        float nx = -dy / length * halfWidth, ny = dx / length * halfWidth;
        quad(graphics, x1 + nx, y1 + ny, x2 + nx, y2 + ny,
            x2 - nx, y2 - ny, x1 - nx, y1 - ny, color);
    }

    static void triangle(GuiGraphicsExtractor graphics, float ax, float ay, float bx, float by,
            float cx, float cy, int color) {
        quad(graphics, ax, ay, bx, by, cx, cy, cx, cy, color);
    }

    static void dashedLine(GuiGraphicsExtractor graphics, float x1, float y1, float x2, float y2, int color, float halfWidth) {
        float length = (float) Math.hypot(x2 - x1, y2 - y1);
        if (length < 0.001f) return;
        float dx = (x2 - x1) / length, dy = (y2 - y1) / length;
        for (float start = 0; start < length; start += 9) {
            float end = Math.min(length, start + 5);
            line(graphics, x1 + dx * start, y1 + dy * start, x1 + dx * end, y1 + dy * end, color, halfWidth);
        }
    }

    /** Draws text at a fractional world position and scale. */
    static void text(GuiGraphicsExtractor graphics, Font font, String text, float x, float y, int color, float scale) {
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        if (scale != 1) pose.scale(scale, scale);
        cn.dancingsnow.neoecoae.gui.common.GuiDraw.text(graphics, font, text, 0, 0, color, false);
        pose.popMatrix();
    }

    static void centeredText(GuiGraphicsExtractor graphics, Font font, String text, float centerX, float y, int color,
            float scale) {
        text(graphics, font, text, centerX - font.width(text) * scale / 2, y, color, scale);
    }

    static String fit(Font font, String value, float maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(value) <= maxWidth) return value;
        String ellipsis = "…";
        return font.plainSubstrByWidth(value, Math.max(0, (int) maxWidth - font.width(ellipsis))) + ellipsis;
    }

    private static void quad(GuiGraphicsExtractor graphics, float ax, float ay, float bx, float by,
            float cx, float cy, float dx, float dy, int color) {
        // GUI pipelines cull back faces: normalise winding to the order vanilla rectangles use.
        float cross = (bx - ax) * (cy - by) - (by - ay) * (cx - bx);
        if (cross == 0) cross = (cx - ax) * (dy - ay) - (cy - ay) * (dx - ax);
        float[] v = cross > 0
            ? new float[] {dx, dy, cx, cy, bx, by, ax, ay}
            : new float[] {ax, ay, bx, by, cx, cy, dx, dy};
        var pose = new Matrix3x2f(graphics.pose());
        var scissor = graphics.peekScissorStack();
        float minX = Math.min(Math.min(ax, bx), Math.min(cx, dx));
        float minY = Math.min(Math.min(ay, by), Math.min(cy, dy));
        float maxX = Math.max(Math.max(ax, bx), Math.max(cx, dx));
        float maxY = Math.max(Math.max(ay, by), Math.max(cy, dy));
        int x0 = (int) Math.floor(minX), y0 = (int) Math.floor(minY);
        var rect = new ScreenRectangle(x0, y0, Math.max(1, (int) Math.ceil(maxX) - x0),
            Math.max(1, (int) Math.ceil(maxY) - y0)).transformMaxBounds(pose);
        var bounds = scissor != null ? scissor.intersection(rect) : rect;
        graphics.submitGuiElementRenderState(new QuadState(pose, v, color, scissor, bounds));
    }

    private record QuadState(Matrix3x2fc pose, float[] vertices, int color, @Nullable ScreenRectangle scissorArea,
            @Nullable ScreenRectangle bounds) implements GuiElementRenderState {
        @Override
        public void buildVertices(VertexConsumer consumer) {
            for (int i = 0; i < 8; i += 2) {
                consumer.addVertexWith2DPose(pose, vertices[i], vertices[i + 1]).setColor(color);
            }
        }

        @Override
        public RenderPipeline pipeline() { return RenderPipelines.GUI; }

        @Override
        public TextureSetup textureSetup() { return TextureSetup.noTexture(); }
    }
}
