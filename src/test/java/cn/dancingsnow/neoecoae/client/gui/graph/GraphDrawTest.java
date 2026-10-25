package cn.dancingsnow.neoecoae.client.gui.graph;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import org.junit.jupiter.api.Test;

class GraphDrawTest {
    @Test
    void longDiagonalAndTriangleUseConstantGeometryWithoutPerPixelDrawsOrFlushes() {
        var graphics = mock(GuiGraphics.class);
        var source = mock(MultiBufferSource.BufferSource.class);
        var vertices = mock(VertexConsumer.class, RETURNS_SELF);
        when(graphics.pose()).thenReturn(new PoseStack());
        when(graphics.bufferSource()).thenReturn(source);
        when(source.getBuffer(any())).thenReturn(vertices);
        GraphDraw.line(graphics, -1_000_000, -1_000_000, 1_000_000, 1_000_000, 0xff123456, 0.5f);
        verify(vertices, times(4)).addVertex(any(org.joml.Matrix4f.class), anyFloat(), anyFloat(), eq(0f));
        GraphDraw.triangle(graphics, 0, 0, 10, 20, 20, 0, 0xff654321);
        verify(vertices, times(8)).addVertex(any(org.joml.Matrix4f.class), anyFloat(), anyFloat(), eq(0f));
        verify(graphics, never()).flush();
        verify(graphics, never()).fill(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
    }
}
