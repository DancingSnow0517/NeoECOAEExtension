package cn.dancingsnow.neoecoae.client.gui.graph;

import cn.dancingsnow.neoecoae.crafting.graph.GraphLayoutSnapshot.Bounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jetbrains.annotations.Nullable;

/**
 * Dense-overview painter adapter.
 * <p>
 * TODO(26.1 port): the 1.21.1 version rasterised the overview into cached off-screen {@code TextureTarget} tiles
 * (see {@link GraphTileCache}) using immediate-mode RenderSystem/GL calls that no longer exist. 26.1 GUI rendering
 * is deferred (GuiGraphicsExtractor -> GuiRenderState), so for now the visible area is painted directly every frame.
 * A faithful port would use a PictureInPictureRenderer / GpuTexture-backed tile cache.
 */
final class GraphViewportCache implements AutoCloseable {
    private static volatile int resourceVersion;

    interface Painter {
        boolean advance(GuiGraphicsExtractor graphics, long deadline);
    }

    interface Factory {
        Painter create(Bounds area, double pixelsPerUnit);
    }

    private @Nullable Bounds viewport;
    private float zoom = 1;
    private @Nullable Factory factory;

    static void resourcesReloaded() { resourceVersion++; }

    void prepare(GuiGraphicsExtractor parent, Object content, Bounds viewport, Bounds drawing, float zoom,
            Factory factory) {
        this.viewport = viewport;
        this.zoom = zoom;
        this.factory = factory;
    }

    void draw(GuiGraphicsExtractor graphics) {
        if (viewport == null || factory == null) return;
        double pixelsPerUnit = zoom * Minecraft.getInstance().getWindow().getGuiScale();
        var painter = factory.create(viewport.padded((float) (32 / Math.max(zoom, 0.0001f))), pixelsPerUnit);
        // Paint to completion; the painter is incremental only to bound per-frame tile work.
        while (!painter.advance(graphics, Long.MAX_VALUE)) {
            // keep going
        }
    }

    @Override
    public void close() {
        viewport = null;
        factory = null;
    }
}
