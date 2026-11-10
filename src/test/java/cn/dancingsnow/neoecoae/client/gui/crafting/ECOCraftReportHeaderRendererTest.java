package cn.dancingsnow.neoecoae.client.gui.crafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import appeng.api.stacks.AEKey;
import appeng.api.client.AEKeyRendering;
import appeng.client.gui.me.crafting.AbstractTableRenderer;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.me.crafting.CraftingPlanSummary;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCraftConfirmMenuMode;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.utils.ModularUITooltipComponent;
import cn.dancingsnow.neoecoae.crafting.display.format.DisplayNumbers;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import com.mojang.blaze3d.vertex.PoseStack;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
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
        try (var modifiers = mockStatic(Screen.class)) {
            for (int[] origin : List.of(new int[] {0, 0}, new int[] {37, 61}, new int[] {241, 133})) {
                var screen = mock(ECOCraftConfirmScreen.class);
                var graphics = mock(GuiGraphics.class);
                var card = new ModularUITooltipComponent(mock(UIElement.class));
                var font = mock(Font.class);
                setField(Screen.class, screen, "font", font);
                setField(Screen.class, screen, "width", 960);
                setField(Screen.class, screen, "height", 540);
                setField(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class, screen, "leftPos", origin[0]);
                setField(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class, screen, "topPos", origin[1]);
                setField(ECOCraftConfirmScreen.class, screen, "statisticsTooltip", card);
                doCallRealMethod().when(screen).renderReportTooltip(any(), anyInt(), anyInt());
                screen.renderReportTooltip(graphics, origin[0] + 70, origin[1] + 12);
                verify(graphics).renderComponentTooltipFromElements(eq(font), argThat(elements ->
                    elements.size() == 1 && elements.get(0).right().orElseThrow() == card),
                    eq(origin[0] + 70), eq(origin[1] + 12), eq(ItemStack.EMPTY));

                clearInvocations(graphics);
                for (int[] outside : List.of(new int[] {52, 12}, new int[] {155, 12},
                        new int[] {70, 4}, new int[] {70, 20})) {
                    screen.renderReportTooltip(graphics, origin[0] + outside[0], origin[1] + outside[1]);
                }
                verifyNoInteractions(graphics);
            }
        }
    }

    @Test
    void statisticsCardDistinguishesPartialAndCompleteCyclesAndExpandsExactBytes() {
        var bytes = new BigInteger("541857662145037064009951989");
        var summary = new CraftingGraphSnapshot.Summary("CYCLE_UNRESOLVED", 1, 197577, 0, 3, 0);
        var compact = ECOCraftReportTooltips.statistics(4_051_933_800L, bytes, false, summary, false).texts();
        assertTrue(compact.stream().anyMatch(text -> text.getString().equals("4,051.934 ms")));
        assertTrue(compact.stream().anyMatch(text -> text.getString().equals("541.858 Y B")));
        assertTrue(compact.stream().anyMatch(text -> text.getString().equals("197,577")));
        assertTrue(compact.stream().anyMatch(text -> text.getString().equals("3")));
        assertTrue(compact.stream().anyMatch(text -> isKey(text, "card.incomplete")));
        var full = ECOCraftReportTooltips.statistics(4_051_933_800L, bytes, true, summary, true).texts();
        assertTrue(full.stream().anyMatch(text -> text.getString().equals(DisplayNumbers.grouped(bytes) + " B")));
        assertTrue(full.stream().anyMatch(text -> text.getString().equals("4,051.9338 ms")));
        assertFalse(full.stream().anyMatch(text -> isKey(text, "card.incomplete")));
        assertTrue(full.stream().anyMatch(text -> isKey(text, "cycle_planning_enabled")),
            "Solved cycles must keep their cycle badge without an incomplete-calculation warning");
    }

    @Test
    void materialTooltipDefersRenderingAndPreservesExactRequestsAndSeedWarnings() throws Exception {
        var water = mock(appeng.api.stacks.AEFluidKey.class);
        when(water.getDisplayName()).thenReturn(Component.literal("Water"));
        when(water.getAmountPerUnit()).thenReturn(1000);
        when(water.getUnitSymbol()).thenReturn("B");
        var stored = new BigInteger("9239216116402315807");
        var missing = new BigInteger("1615731987272067141027365754");
        var requested = new BigInteger("1516093235626081346596415521");
        var node = new CraftingGraphSnapshot.MaterialNode(0, water, Long.MAX_VALUE, Long.MAX_VALUE, 60, Long.MAX_VALUE,
            CraftingGraphSnapshot.MaterialStatus.MISSING, requested.toString(), stored.toString(), "60", missing.toString(), "0", "0");
        var screen = mock(ECOCraftConfirmScreen.class);
        var renderer = mock(ECOExactMaterialTableRenderer.class, CALLS_REAL_METHODS);
        setField(AbstractTableRenderer.class, renderer, "screen", screen);
        setField(ECOExactMaterialTableRenderer.class, renderer, "estimatedStartupSeed", (java.util.function.Predicate<AEKey>) key -> true);
        assertEquals(List.of(), renderer.getEntryTooltip(node), "The translated AE2 foreground must not draw a text tooltip");
        var queued = org.mockito.ArgumentCaptor.forClass(ECOCraftReportTooltips.Material.class);
        verify(screen).queueMaterialTooltip(queued.capture());
        assertEquals(requested, queued.getValue().requested());
        assertEquals(missing, queued.getValue().missing());
        assertEquals(BigInteger.valueOf(60), queued.getValue().craft());
        assertTrue(queued.getValue().estimatedSeed());
        try (var rendering = mockStatic(AEKeyRendering.class)) {
            rendering.when(() -> AEKeyRendering.getTooltip(water)).thenReturn(List.of(Component.literal("Water"), Component.literal("Minecraft")));
            var compact = ECOCraftReportTooltips.material(queued.getValue(), false).texts();
            assertTrue(compact.stream().anyMatch(text -> text.getString().equals("9.239 P B")));
            assertTrue(compact.stream().anyMatch(text -> text.getString().equals("1.616 Y B")));
            assertTrue(compact.stream().anyMatch(text -> text.getString().equals("1.516 Y B")),
                "The requested amount uses buckets, just like stored and missing amounts");
            assertTrue(compact.stream().anyMatch(text -> isKey(text, "seed_shortfall_estimate_hint")));
            var exact = ECOCraftReportTooltips.material(queued.getValue(), true).texts();
            assertTrue(exact.stream().anyMatch(text -> text.getString().equals("9,239,216,116,402,315.807 B")));
            assertTrue(exact.stream().anyMatch(text -> text.getString().equals("0.06 B")), "Fractional buckets remain exact");
            assertTrue(exact.stream().anyMatch(text -> isKey(text, "seed_shortfall_estimate_hint")));
        }
    }

    private static boolean isKey(Component component, String suffix) {
        return component.getContents() instanceof TranslatableContents text
            && text.getKey().equals("gui.neoecoae.crafting_report." + suffix);
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
    void finalBytesAppearBeforeMaterialPagesWithoutReusingOldGraphStatistics() throws Exception {
        var menu = mock(CraftConfirmMenu.class, withSettings().extraInterfaces(ECOCraftConfirmMenuMode.class));
        var mode = (ECOCraftConfirmMenuMode) menu;
        var oldGraph = new CraftingGraphSnapshot(-1, List.of(), List.of(), List.of(), List.of(), List.of(),
            new CraftingGraphSnapshot.Summary("SUCCESS", 12, 99, 0, 0, 0));
        when(mode.neoecoae$getCraftingGraphSnapshot()).thenReturn(oldGraph);
        when(mode.neoecoae$getTheoreticalBytes()).thenReturn(BigInteger.valueOf(512));
        when(mode.neoecoae$getCalculationNanos()).thenReturn(2_000_000L);
        var plan = new CraftingPlanSummary(512, false, List.of());
        var screen = mock(ECOCraftConfirmScreen.class);
        setField(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class, screen, "menu", menu);
        setField(Screen.class, screen, "font", mock(Font.class));
        setField(ECOCraftConfirmScreen.class, screen, "materialData", new ECOExactMaterialTableRenderer.MaterialData());
        var update = ECOCraftConfirmScreen.class.getDeclaredMethod(
            "updateReportSummary", CraftingPlanSummary.class, boolean.class);
        update.setAccessible(true);
        var summaryField = ECOCraftConfirmScreen.class.getDeclaredField("reportSummary");
        summaryField.setAccessible(true);

        update.invoke(screen, plan, true);
        assertTrue(isKey(((ECOCraftReportHeaderRenderer.Summary) summaryField.get(screen)).bytes(), "bytes_pending"));

        when(mode.neoecoae$isEcoReportReady()).thenReturn(true);
        when(mode.neoecoae$hasByteEstimate()).thenReturn(true);
        when(mode.neoecoae$hasCompleteByteEstimate()).thenReturn(true);
        update.invoke(screen, plan, true);
        var summary = (ECOCraftReportHeaderRenderer.Summary) summaryField.get(screen);
        assertTrue(isKey(summary.bytes(), "bytes_only"));
        assertEquals("512 B", argument(summary.bytes()));
        assertEquals("2.0000", argument(summary.elapsed()));
        assertFalse(summary.tooltip().stream().anyMatch(line -> isKey(line, "total_patterns")),
            "A previous revision's graph must not be combined with the new byte estimate");

        when(mode.neoecoae$diagnosticsReady()).thenReturn(true);
        when(mode.neoecoae$getCraftingGraphSnapshot()).thenReturn(new CraftingGraphSnapshot(-1,
            List.of(), List.of(), List.of(), List.of(), List.of(),
            new CraftingGraphSnapshot.Summary("SUCCESS", 2, 1, 0, 0, 0)));
        update.invoke(screen, plan, false);
        summary = (ECOCraftReportHeaderRenderer.Summary) summaryField.get(screen);
        assertEquals("512 B", argument(summary.bytes()));
        assertEquals("1", argument(summary.tooltip().stream()
            .filter(line -> isKey(line, "total_patterns")).findFirst().orElseThrow()));

        when(mode.neoecoae$isEcoReportReady()).thenReturn(false);
        when(mode.neoecoae$diagnosticsReady()).thenReturn(false);
        update.invoke(screen, plan, true);
        assertTrue(isKey(((ECOCraftReportHeaderRenderer.Summary) summaryField.get(screen)).bytes(), "bytes_pending"),
            "Replanning must not continue displaying the previous plan's byte estimate");
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

        // The same partial total is useful before rows arrive, but must never become an exact total.
        when(mode.neoecoae$isEcoReportReady()).thenReturn(true);
        when(mode.neoecoae$hasByteEstimate()).thenReturn(true);
        when(mode.neoecoae$diagnosticsReady()).thenReturn(false);
        data.update(CraftingGraphSnapshot.EMPTY, List.of());
        when(mode.neoecoae$getCraftingGraphSnapshot()).thenReturn(CraftingGraphSnapshot.EMPTY);
        update.invoke(screen, plan, true);
        summary = (ECOCraftReportHeaderRenderer.Summary) summaryField.get(screen);
        assertTrue(isKey(summary.bytes(), "bytes_partial"));
        assertEquals(exact, summary.tooltip().stream()
            .filter(line -> !(line.getContents() instanceof TranslatableContents))
            .map(Component::getString).reduce("", String::concat));

        when(mode.neoecoae$hasByteEstimate()).thenReturn(false);
        update.invoke(screen, plan, true);
        summary = (ECOCraftReportHeaderRenderer.Summary) summaryField.get(screen);
        assertEquals("gui.neoecoae.crafting_report.bytes_unknown", key(summary.bytes()),
            "A diagnostic shell without material calculations must not display a byte total");

        when(mode.neoecoae$getReportSyncFailure()).thenReturn(1);
        update.invoke(screen, plan, true);
        summary = (ECOCraftReportHeaderRenderer.Summary) summaryField.get(screen);
        assertTrue(isKey(summary.elapsed(), "material_sync_failed"));
        assertTrue(isKey(summary.bytes(), "bytes_pending"));
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
