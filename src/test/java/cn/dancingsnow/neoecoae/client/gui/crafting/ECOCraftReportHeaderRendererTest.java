package cn.dancingsnow.neoecoae.client.gui.crafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import cn.dancingsnow.neoecoae.crafting.display.format.DisplayNumbers;
import com.mojang.blaze3d.vertex.PoseStack;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class ECOCraftReportHeaderRendererTest {
    @Test
    void byteRowAndProgressStayInsideTheTextAreaWhenTheWindowMoves() {
        for (int[] origin : List.of(new int[] {37, 61}, new int[] {241, 133})) {
            var graphics = mock(GuiGraphics.class);
            var font = spy(new Font(ignored -> null, false));
            var pose = new PoseStack();
            pose.translate(origin[0], origin[1], 0);
            var originalPose = new Matrix4f(pose.last().pose());
            when(graphics.pose()).thenReturn(pose);
            doAnswer(call -> ((Component) call.getArgument(0)).getString().length() * 6)
                .when(font).width(any(Component.class));
            var clips = new ArrayDeque<int[]>();
            var rows = new ArrayList<float[]>();
            doAnswer(call -> {
                int[] clip = {call.getArgument(0), call.getArgument(1),
                    call.getArgument(2), call.getArgument(3)};
                assertTrue(clip[0] >= origin[0] + 53 && clip[2] <= origin[0] + 155);
                assertTrue(clip[1] >= origin[1] + 5 && clip[3] <= origin[1] + 20);
                clips.push(clip);
                return null;
            }).when(graphics).enableScissor(anyInt(), anyInt(), anyInt(), anyInt());
            doAnswer(call -> { clips.pop(); return null; }).when(graphics).disableScissor();
            when(graphics.drawString(eq(font), any(Component.class), anyInt(), anyInt(), anyInt(), anyBoolean()))
                .thenAnswer(call -> {
                    Component text = call.getArgument(1);
                    var matrix = pose.last().pose();
                    float left = matrix.m30();
                    float top = matrix.m31();
                    float right = left + font.width(text) * matrix.m00();
                    float bottom = top + font.lineHeight * matrix.m11();
                    int[] clip = clips.peek();
                    assertNotNull(clip);
                    assertTrue(left >= clip[0] && right <= clip[2] + 0.0001F);
                    assertTrue(top >= clip[1] && bottom <= clip[3] + 0.0001F);
                    assertTrue((boolean) call.getArgument(5));
                    rows.add(new float[] {left, top, right, bottom});
                    return 0;
                });
            doAnswer(call -> {
                int[] clip = clips.peek();
                assertNotNull(clip);
                assertTrue(origin[0] + (int) call.getArgument(0) >= clip[0]);
                assertTrue(origin[0] + (int) call.getArgument(2) <= clip[2]);
                assertTrue(origin[1] + (int) call.getArgument(1) >= cn.dancingsnow.neoecoae.platform.JdkCompat.last(rows)[3]);
                assertTrue(origin[1] + (int) call.getArgument(3) <= clip[3]);
                return null;
            }).when(graphics).fill(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());

            var summary = new ECOCraftReportHeaderRenderer.Summary(Component.literal("长".repeat(300)),
                Component.literal("Bytes: 1.234×10^123456789 B"), List.of());
            ECOCraftReportHeaderRenderer.render(graphics, font, origin[0], origin[1], summary, 1_999L);

            assertEquals(1, rows.size());
            assertTrue(clips.isEmpty());
            assertEquals(originalPose, pose.last().pose());
            verify(graphics, times(2)).fill(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        }
    }

    @Test
    void renderingFailureRestoresPoseAndClippingForTheRestOfTheScreen() {
        var graphics = mock(GuiGraphics.class);
        var font = mock(Font.class);
        var pose = new PoseStack();
        pose.translate(100, 80, 0);
        var originalPose = new Matrix4f(pose.last().pose());
        when(graphics.pose()).thenReturn(pose);
        when(graphics.drawString(eq(font), any(Component.class), anyInt(), anyInt(), anyInt(), anyBoolean()))
            .thenThrow(new IllegalStateException("Render failed"));
        var summary = ECOCraftReportHeaderRenderer.pending(Component.literal("Calculating..."));

        assertThrows(IllegalStateException.class, () ->
            ECOCraftReportHeaderRenderer.render(graphics, font, 100, 80, summary, null));

        assertEquals(originalPose, pose.last().pose());
        verify(graphics, times(2)).disableScissor();
        verify(graphics, times(2)).flush();
    }

    @Test
    void hugeByteCountsRemainExactInTheTooltipAndFitTheMeasuredRow() {
        BigInteger huge = BigInteger.TEN.pow(123).add(BigInteger.valueOf(987654321));
        var summary = ECOCraftReportHeaderRenderer.completed(543_210L, huge,
            text -> ("字节：" + argument(text)).length() * 9);

        assertEquals("0.54321", argument(summary.elapsed()));
        assertEquals("0.543210", argument(cn.dancingsnow.neoecoae.platform.JdkCompat.first(summary.tooltip())));
        String display = argument(summary.bytes());
        assertEquals("e123 B", display, "the mantissa is omitted when the measured row is too narrow");
        assertTrue(display.endsWith(" B"));
        assertTrue(("字节：" + display).length() * 9 <= (int) (100 / 1.15F));
        String exact = summary.tooltip().stream().skip(2).map(Component::getString)
            .reduce("", String::concat);
        assertEquals(DisplayNumbers.grouped(huge.toString()) + " B", exact);
    }

    @Test
    void pendingAndUnknownByteCountsAreDistinctAndZeroRetainsElapsedTooltip() {
        var pending = ECOCraftReportHeaderRenderer.pending(Component.literal("计算中…"));
        var unknown = ECOCraftReportHeaderRenderer.completed(0, null, text -> 0);
        var zero = ECOCraftReportHeaderRenderer.completed(0, BigInteger.ZERO, text -> 0);
        assertEquals("gui.neoecoae.crafting_report.bytes_pending", key(pending.bytes()));
        assertEquals("gui.neoecoae.crafting_report.bytes_unknown", key(unknown.bytes()));
        assertEquals("0.0000", argument(zero.elapsed()));
        assertEquals("0 B", argument(zero.bytes()));
        assertEquals("0.0000010000", ECOCraftReportHeaderRenderer.formatMillis(1));
        assertFalse(ECOCraftReportHeaderRenderer.isHovered(155, 20));
    }

    private static String key(Component component) {
        return ((TranslatableContents) component.getContents()).getKey();
    }

    private static String argument(Component component) {
        return ((TranslatableContents) component.getContents()).getArgs()[0].toString();
    }
}
