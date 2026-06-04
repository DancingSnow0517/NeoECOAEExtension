package cn.dancingsnow.neoecoae.client.multiblock.preview;

import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;

final class PreviewScissor {
    private PreviewScissor() {}

    static void enable(GuiGraphics g, SceneViewport viewport) {
        Matrix4f matrix = g.pose().last().pose();
        int scissorX = Math.round(viewport.x() + matrix.m30());
        int scissorY = Math.round(viewport.y() + matrix.m31());
        g.enableScissor(scissorX, scissorY, scissorX + viewport.width(), scissorY + viewport.height());
    }
}
