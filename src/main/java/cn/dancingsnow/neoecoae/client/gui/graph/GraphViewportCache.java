package cn.dancingsnow.neoecoae.client.gui.graph;

import cn.dancingsnow.neoecoae.crafting.graph.GraphLayoutSnapshot.Bounds;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** GPU adapter for the bounded scheduler. Pan and nearby zoom levels retain completed tiles. */
final class GraphViewportCache implements AutoCloseable {
    private static final int GUARD = 2;
    private static volatile int resourceVersion;
    private final GraphTileCache<TextureTile> tiles = new GraphTileCache<>();
    private int version = -1;

    interface Painter {
        boolean advance(GuiGraphics graphics, long deadline);
    }

    interface Factory {
        Painter create(Bounds area, double pixelsPerUnit);
    }

    static void resourcesReloaded() { resourceVersion++; }

    void prepare(GuiGraphics parent, Object content, Bounds viewport, Bounds drawing, float zoom,
            Factory factory) {
        if (version != resourceVersion) {
            close();
            version = resourceVersion;
        }
        tiles.select(content, viewport, drawing, zoom, Minecraft.getInstance().getWindow().getGuiScale());
        if (!tiles.needsWork()) return;
        parent.flush();
        // Target disposal/creation and item renderers may alter GL state. Restore the exact caller state.
        var state = new RenderState();
        try {
            RenderSystem.disableScissor();
            var view = RenderSystem.getModelViewStack();
            view.pushMatrix();
            try {
                view.identity();
                view.translate(0, 0, -10000);
                RenderSystem.applyModelViewMatrix();
                tiles.advance(spec -> new TextureTile(spec, factory), System::nanoTime);
            } finally {
                view.popMatrix();
                RenderSystem.applyModelViewMatrix();
            }
        } finally {
            state.restore();
        }
    }

    void draw(GuiGraphics graphics) {
        graphics.flush();
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        try {
            for (var entry : tiles.drawable()) entry.tile.draw(graphics);
        } finally {
            if (depth) RenderSystem.enableDepthTest();
        }
    }

    @Override
    public void close() { tiles.close(); }

    private static final class TextureTile implements GraphTileCache.Tile {
        private final TextureTarget target;
        private final GraphTileCache.Spec spec;
        private final Bounds raster;
        private final Painter painter;

        TextureTile(GraphTileCache.Spec spec, Factory factory) {
            this.spec = spec;
            raster = spec.bounds().padded((float) (GUARD / spec.pixelsPerUnit()));
            painter = factory.create(raster.padded(32), spec.pixelsPerUnit());
            target = new TextureTarget(GraphTileCache.TILE_SIZE + 2 * GUARD,
                GraphTileCache.TILE_SIZE + 2 * GUARD, true, Minecraft.ON_OSX);
            target.setFilterMode(GL11.GL_LINEAR);
            target.setClearColor(14 / 255f, 17 / 255f, 21 / 255f, 1);
            target.clear(Minecraft.ON_OSX);
        }

        @Override
        public boolean advance(long deadline) {
            target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, target.width, target.height,
                0, 1000, 21000), VertexSorting.ORTHOGRAPHIC_Z);
            var graphics = new GuiGraphics(Minecraft.getInstance(), Minecraft.getInstance().renderBuffers().bufferSource());
            float scale = (float) spec.pixelsPerUnit();
            graphics.pose().scale(scale, scale, 1);
            graphics.pose().translate(-raster.left(), -raster.top(), 0);
            try {
                return painter.advance(graphics, deadline);
            } finally {
                graphics.flush();
            }
        }

        void draw(GuiGraphics graphics) {
            RenderSystem.setShaderTexture(0, target.getColorTextureId());
            var matrix = graphics.pose().last().pose();
            var bounds = spec.bounds();
            float inset = (float) GUARD / (GraphTileCache.TILE_SIZE + 2 * GUARD);
            var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            buffer.addVertex(matrix, bounds.left(), bounds.bottom(), 0).setUv(inset, inset);
            buffer.addVertex(matrix, bounds.right(), bounds.bottom(), 0).setUv(1 - inset, inset);
            buffer.addVertex(matrix, bounds.right(), bounds.top(), 0).setUv(1 - inset, 1 - inset);
            buffer.addVertex(matrix, bounds.left(), bounds.top(), 0).setUv(inset, 1 - inset);
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        }

        @Override
        public void close() {
            int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            target.destroyBuffers();
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        }
    }

    private static final class RenderState {
        private final int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        private final int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        private final int[] viewport = new int[4];
        private final int[] scissor = new int[4];
        private final boolean clipped = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        private final Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        private final VertexSorting sorting = RenderSystem.getVertexSorting();

        RenderState() {
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
        }

        void restore() {
            RenderSystem.setProjectionMatrix(projection, sorting);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            if (clipped) RenderSystem.enableScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
            else RenderSystem.disableScissor();
            if (depth) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
        }
    }
}
