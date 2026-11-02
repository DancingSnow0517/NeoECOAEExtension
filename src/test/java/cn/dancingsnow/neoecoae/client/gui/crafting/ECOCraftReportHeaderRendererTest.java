package cn.dancingsnow.neoecoae.client.gui.crafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import cn.dancingsnow.neoecoae.crafting.display.format.DisplayNumbers;
import java.math.BigInteger;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.joml.Matrix3x2f;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ECOCraftReportHeaderRendererTest {
    @Test
    void byteRowAndProgressStayInsideTheTextAreaWhenTheWindowMoves() {
        for (int[] origin : List.of(new int[] {0, 0}, new int[] {37, 61}, new int[] {241, 133})) {
            var graphics = graphics();
            var font = spy(new Font(mock(Font.Provider.class)));
            var pose = graphics.pose();
            pose.translate(origin[0], origin[1]);
            var originalPose = new Matrix3x2f(pose);
            graphics.enableScissor(0, 0, 330, 260);
            doAnswer(call -> ((Component) call.getArgument(0)).getString().length() * 6)
                .when(font).width(any(Component.class));
            doAnswer(call -> {
                assertTrue((int) call.getArgument(0) >= 54);
                assertTrue((int) call.getArgument(2) <= 154);
                assertEquals(19, (int) call.getArgument(1));
                assertEquals(20, (int) call.getArgument(3));
                return null;
            }).when(graphics).fill(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());

            var summary = new ECOCraftReportHeaderRenderer.Summary(Component.literal("长".repeat(300)),
                Component.literal("Bytes: 1.234×10^123456789 B"), List.of());
            ECOCraftReportHeaderRenderer.render(graphics, font, summary, 1_999L);

            var textStates = ArgumentCaptor.forClass(GuiTextRenderState.class);
            verify(graphics.guiRenderState).addText(textStates.capture());
            var text = textStates.getValue();
            assertEquals(new ScreenRectangle(origin[0] + 54, origin[1] + 6, 100, 14), text.scissor);
            float left = text.pose.m20();
            float top = text.pose.m21();
            float right = left + font.width(summary.bytes()) * text.pose.m00();
            float bottom = top + font.lineHeight * text.pose.m11();
            assertTrue(text.scissor.containsPoint((int) left, (int) top));
            assertTrue(text.scissor.containsPoint((int) Math.ceil(right) - 1,
                (int) Math.ceil(bottom) - 1));
            assertTrue(bottom <= origin[1] + 19, "text must stay above the progress bar");
            assertEquals(0xFFFFFFFF, text.color);
            assertTrue(text.dropShadow);
            assertEquals(originalPose, new Matrix3x2f(pose));
            assertEquals(new ScreenRectangle(origin[0], origin[1], 330, 260), currentScissor(graphics));
            verify(graphics, times(2)).fill(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
            graphics.disableScissor();
            assertNull(currentScissor(graphics));
        }
    }

    @Test
    void renderingFailureRestoresPoseAndClippingForTheRestOfTheScreen() {
        var graphics = graphics();
        var font = mock(Font.class);
        var pose = graphics.pose();
        pose.translate(100, 80);
        var originalPose = new Matrix3x2f(pose);
        graphics.enableScissor(0, 0, 330, 260);
        doThrow(new IllegalStateException("Render failed"))
            .when(graphics).text(eq(font), any(Component.class), anyInt(), anyInt(), anyInt(), anyBoolean());
        var summary = ECOCraftReportHeaderRenderer.pending(Component.literal("Calculating..."));

        assertThrows(IllegalStateException.class, () ->
            ECOCraftReportHeaderRenderer.render(graphics, font, summary, null));

        assertEquals(originalPose, new Matrix3x2f(pose));
        verify(graphics, times(2)).disableScissor();
        assertEquals(new ScreenRectangle(100, 80, 330, 260), currentScissor(graphics));
        graphics.disableScissor();
        assertNull(currentScissor(graphics));
        // 26.1: GuiGraphicsExtractor has no flush(); rendering is deferred to the GUI render state.
    }

    @Test
    void hugeByteCountsRemainExactInTheTooltipAndFitTheMeasuredRow() {
        BigInteger huge = BigInteger.TEN.pow(123).add(BigInteger.valueOf(987654321));
        var summary = ECOCraftReportHeaderRenderer.completed(543_210L, huge,
            text -> ("字节：" + argument(text)).length() * 9);

        assertEquals("0.54321", argument(summary.elapsed()));
        assertEquals("0.543210", argument(summary.tooltip().getFirst()));
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

    private static GuiGraphicsExtractor graphics() {
        // Keep Minecraft's real text extraction and pose-aware scissors without rendering to a window.
        return spy(new GuiGraphicsExtractor(mock(Minecraft.class, RETURNS_DEEP_STUBS),
            mock(GuiRenderState.class), 0, 0));
    }

    private static ScreenRectangle currentScissor(GuiGraphicsExtractor graphics) {
        graphics.text(mock(Font.class), Component.empty(), 0, 0, 0xFFFFFFFF, false);
        var textStates = ArgumentCaptor.forClass(GuiTextRenderState.class);
        verify(graphics.guiRenderState, atLeastOnce()).addText(textStates.capture());
        return textStates.getValue().scissor;
    }

    private static String key(Component component) {
        return ((TranslatableContents) component.getContents()).getKey();
    }

    private static String argument(Component component) {
        return ((TranslatableContents) component.getContents()).getArgs()[0].toString();
    }
}
