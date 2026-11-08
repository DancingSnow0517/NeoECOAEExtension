package cn.dancingsnow.neoecoae.client.gui.crafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import appeng.api.stacks.AEKey;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.me.crafting.CraftingPlanSummary;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCraftConfirmMenuMode;
import cn.dancingsnow.neoecoae.crafting.display.format.DisplayNumbers;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
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
    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() throws ClassNotFoundException {
        cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize();
        try (var mods = mockStatic(net.minecraftforge.fml.ModList.class);
             var registrate = mockStatic(cn.dancingsnow.neoecoae.registration.NERegistrate.class)) {
            var modList = mock(net.minecraftforge.fml.ModList.class);
            when(modList.getAllScanData()).thenReturn(List.of());
            mods.when(net.minecraftforge.fml.ModList::get).thenReturn(modList);
            registrate.when(() -> cn.dancingsnow.neoecoae.registration.NERegistrate.create("neoecoae"))
                .thenReturn(mock(cn.dancingsnow.neoecoae.registration.NERegistrate.class));
            Class.forName(ECOCraftConfirmScreen.class.getName());
        }
    }

    @Test
    void byteTooltipUsesScreenCoordinatesAndChecksEveryHoverBoundary() throws Exception {
        for (int[] origin : List.of(new int[] {0, 0}, new int[] {37, 61}, new int[] {241, 133})) {
            var screen = mock(ECOCraftConfirmScreen.class);
            var graphics = mock(GuiGraphics.class);
            var summary = ECOCraftReportHeaderRenderer.pending(Component.literal("Calculating..."));
            setField(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class, screen, "leftPos", origin[0]);
            setField(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class, screen, "topPos", origin[1]);
            setField(ECOCraftConfirmScreen.class, screen, "reportSummary", summary);
            doCallRealMethod().when(screen).renderReportTooltip(any(), anyInt(), anyInt());
            screen.renderReportTooltip(graphics, origin[0] + 70, origin[1] + 12);
            verify(screen).drawTooltipWithHeader(graphics, origin[0] + 70, origin[1] + 12, summary.tooltip());

            clearInvocations(screen);
            for (int[] outside : List.of(new int[] {52, 12}, new int[] {155, 12},
                    new int[] {70, 4}, new int[] {70, 20})) {
                screen.renderReportTooltip(graphics, origin[0] + outside[0], origin[1] + outside[1]);
            }
            verify(screen, never()).drawTooltipWithHeader(any(), anyInt(), anyInt(), anyList());
        }
    }

    private static void setField(Class<?> owner, Object target, String name, Object value) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

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
        var summary = ECOCraftReportHeaderRenderer.completed(543_210L, huge, true,
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
    void incompleteStressReportKeepsComputedBigIntegerBytesInTheScreenAndTooltip() throws Exception {
        BigInteger huge = BigInteger.TEN.pow(123).add(BigInteger.valueOf(987654321));
        var node = new CraftingGraphSnapshot.MaterialNode(0, mock(AEKey.class), 100, 0, 0, 100,
            CraftingGraphSnapshot.MaterialStatus.MISSING);
        var cycle = new CraftingGraphSnapshot.CycleGroup(75, List.of(0), List.of(),
            "INSUFFICIENT_EXTERNAL_INPUT", List.of(), List.of(), List.of(), List.of(), List.of());
        var snapshot = new CraftingGraphSnapshot(0, List.of(node), List.of(), List.of(), List.of(cycle),
            List.of(), new CraftingGraphSnapshot.Summary("PLANNED_BUT_AMOUNT_UNREPRESENTABLE", 1, 0, 0, 1, 0));
        var data = new ECOExactMaterialTableRenderer.MaterialData();
        data.update(snapshot, List.of());
        assertFalse(data.hasCompleteByteEstimate());

        var menu = mock(CraftConfirmMenu.class, withSettings().extraInterfaces(ECOCraftConfirmMenuMode.class));
        var mode = (ECOCraftConfirmMenuMode) menu;
        when(mode.neoecoae$diagnosticsReady()).thenReturn(true);
        when(mode.neoecoae$getTheoreticalBytes()).thenReturn(huge);
        when(mode.neoecoae$getCraftingGraphSnapshot()).thenReturn(snapshot);
        var plan = mock(CraftingPlanSummary.class);
        when(plan.getUsedBytes()).thenReturn(Long.MAX_VALUE);
        var screen = mock(ECOCraftConfirmScreen.class);
        var font = mock(Font.class);
        when(font.width(any(Component.class))).thenAnswer(call ->
            ("已算字节：" + argument(call.getArgument(0))).length() * 7);
        setField(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class, screen, "menu", menu);
        setField(net.minecraft.client.gui.screens.Screen.class, screen, "font", font);
        setField(ECOCraftConfirmScreen.class, screen, "materialData", data);
        var update = ECOCraftConfirmScreen.class.getDeclaredMethod(
            "updateReportSummary", CraftingPlanSummary.class, boolean.class);
        update.setAccessible(true);
        var summaryField = ECOCraftConfirmScreen.class.getDeclaredField("reportSummary");
        summaryField.setAccessible(true);
        update.invoke(screen, plan, !data.hasCompleteByteEstimate());
        var summary = (ECOCraftReportHeaderRenderer.Summary) summaryField.get(screen);

        assertEquals("gui.neoecoae.crafting_report.bytes_partial", key(summary.bytes()));
        assertTrue(font.width(summary.bytes()) <= (int) (100 / 1.15F));
        assertEquals("gui.neoecoae.crafting_report.bytes_partial", key(summary.tooltip().get(1)));
        assertTrue(summary.tooltip().stream().anyMatch(line -> line.getContents() instanceof TranslatableContents text
            && text.getKey().equals("gui.neoecoae.crafting_report.bytes_partial_hint")));
        String exact = summary.tooltip().stream().filter(line -> !(line.getContents() instanceof TranslatableContents))
            .map(Component::getString).reduce("", String::concat);
        assertEquals(DisplayNumbers.grouped(huge.toString()) + " B", exact);

        data.update(CraftingGraphSnapshot.EMPTY, List.of());
        when(mode.neoecoae$getCraftingGraphSnapshot()).thenReturn(CraftingGraphSnapshot.EMPTY);
        update.invoke(screen, plan, true);
        summary = (ECOCraftReportHeaderRenderer.Summary) summaryField.get(screen);
        assertEquals("gui.neoecoae.crafting_report.bytes_unknown", key(summary.bytes()),
            "A diagnostic shell without material calculations must not display a byte total");
    }

    @Test
    void pendingAndUnknownByteCountsAreDistinctAndZeroRetainsElapsedTooltip() {
        var pending = ECOCraftReportHeaderRenderer.pending(Component.literal("计算中…"));
        var unknown = ECOCraftReportHeaderRenderer.completed(0, null, false, text -> 0);
        var zero = ECOCraftReportHeaderRenderer.completed(0, BigInteger.ZERO, true, text -> 0);
        var incompleteZero = ECOCraftReportHeaderRenderer.completed(0, BigInteger.ZERO, false, text -> 0);
        assertEquals("gui.neoecoae.crafting_report.bytes_pending", key(pending.bytes()));
        assertEquals("gui.neoecoae.crafting_report.bytes_unknown", key(unknown.bytes()));
        assertEquals("gui.neoecoae.crafting_report.bytes_unknown", key(incompleteZero.bytes()));
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
