package cn.dancingsnow.neoecoae.client.gui.graph;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix3x2fc;
import org.junit.jupiter.api.Test;

class GraphDrawTest {
    /** 26.1: GraphDraw submits one GUI element render state per primitive instead of drawing into a buffer. */
    @Test
    void longDiagonalAndTriangleUseConstantGeometryWithoutPerPixelDraws() {
        var graphics = mock(GuiGraphicsExtractor.class);
        when(graphics.pose()).thenReturn(new Matrix3x2fStack(16));
        List<GuiElementRenderState> states = new ArrayList<>();
        doAnswer(call -> states.add(call.getArgument(0))).when(graphics).submitGuiElementRenderState(any());
        var vertices = mock(VertexConsumer.class, RETURNS_SELF);

        GraphDraw.line(graphics, -1_000_000, -1_000_000, 1_000_000, 1_000_000, 0xff123456, 0.5f);
        assertEquals(1, states.size());
        states.getLast().buildVertices(vertices);
        verify(vertices, times(4)).addVertexWith2DPose(any(Matrix3x2fc.class), anyFloat(), anyFloat());

        GraphDraw.triangle(graphics, 0, 0, 10, 20, 20, 0, 0xff654321);
        assertEquals(2, states.size());
        states.getLast().buildVertices(vertices);
        verify(vertices, times(8)).addVertexWith2DPose(any(Matrix3x2fc.class), anyFloat(), anyFloat());
        verify(graphics, never()).fill(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
    }
}
