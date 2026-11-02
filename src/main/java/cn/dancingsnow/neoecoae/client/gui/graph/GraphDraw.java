package cn.dancingsnow.neoecoae.client.gui.graph;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
