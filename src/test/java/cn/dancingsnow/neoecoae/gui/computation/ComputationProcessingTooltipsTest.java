package cn.dancingsnow.neoecoae.gui.computation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.Machine;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State;
import cn.dancingsnow.neoecoae.gui.computation.ComputationProcessingTooltips.Kind;
import cn.dancingsnow.neoecoae.gui.computation.ComputationProcessingTooltips.Row;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ComputationProcessingTooltipsTest {
    @BeforeAll static void bootstrap() throws Exception {
        InventoryTestBootstrap.initialize();
        try (var mods = mockStatic(net.neoforged.fml.ModList.class)) {
            var modList = mock(net.neoforged.fml.ModList.class);
            when(modList.getAllScanData()).thenReturn(List.of());
            mods.when(net.neoforged.fml.ModList::get).thenReturn(modList);
            Class.forName(cn.dancingsnow.neoecoae.NeoECOAE.class.getName());
            com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries.init();
        }
    }

    @Test void machineCardSeparatesNameStateParallelCycleAndTickCounts() {
        var rows = ComputationProcessingTooltips.rows(ECOProcessingProgress.summarize(List.of(
            machine(41, 200, State.RUNNING, Component.empty()))));
        var heading = rows.stream().filter(row -> row.left().getString().equals("Factory")).findFirst().orElseThrow();
        assertEquals("gui.neoecoae.processing.state.running", key(heading.right()));
        assertEquals(ChatFormatting.GREEN.getColor(), heading.right().getStyle().getColor().getValue());
        var parallel = rows.stream().filter(row -> key(row.left()).endsWith(".parallel")).findFirst().orElseThrow();
        assertArrayEquals(new Object[] {144L}, args(parallel.left()));
        assertEquals("gui.neoecoae.processing.cycle", key(parallel.right()));
        assertArrayEquals(new Object[] {20}, args(parallel.right()));
        var time = rows.stream().filter(row -> key(row.left()).endsWith(".time")).findFirst().orElseThrow();
        assertArrayEquals(new Object[] {41, 200}, args(time.left()));
        assertEquals("gui.neoecoae.processing.remaining", key(time.right()));
        assertArrayEquals(new Object[] {159}, args(time.right()));
        assertEquals(41F / 200, rows.stream().filter(row -> row.kind() == Kind.PROGRESS)
            .findFirst().orElseThrow().progress());
    }

    @Test void unknownTimingKeepsStatusLocationAndReasonWithoutInventingAZeroPercentCycle() {
        var progress = ECOProcessingProgress.summarize(List.of(new Machine(Component.literal("Factory"),
            "overworld [1,2,3]", "stone", 0, 0, 1, State.PAUSED, Component.literal("Shared recipe"))));
        var rows = ComputationProcessingTooltips.rows(progress);
        String text = rows.toString();
        assertTrue(text.contains("processing.count"));
        assertTrue(text.contains("processing.state.paused"));
        assertTrue(rows.stream().anyMatch(row -> row.left().getString().equals("overworld  (1,2,3)")));
        assertTrue(text.contains("Shared recipe"));
        assertTrue(rows.stream().noneMatch(row -> row.kind() == Kind.PROGRESS));
        assertFalse(text.contains("processing.time"));
        assertFalse(text.contains("processing.cycle"));
        assertFalse(text.contains("processing.remaining"));
    }

    @Test void absentMachinesAddNothingAndTruncatedDetailsKeepTheFullSummaryCount() {
        assertTrue(ComputationProcessingTooltips.rows(ECOProcessingProgress.EMPTY).isEmpty());
        var machine = new Machine(Component.literal("FX"), "overworld", "stone", 25, 100, 1, State.RUNNING, Component.empty());
        var progress = ECOProcessingProgress.summarize(List.of(machine, machine, machine, machine)).page(0);
        var rows = ComputationProcessingTooltips.rows(progress);
        assertEquals(3, rows.stream().filter(row -> row.kind() == Kind.PROGRESS).count());
        var count = rows.stream().filter(row -> key(row.left()).endsWith(".count")).findFirst().orElseThrow();
        assertArrayEquals(new Object[] {4}, args(count.left()));
        var folded = rows.stream().filter(row -> key(row.left()).endsWith(".folded")).findFirst().orElseThrow();
        assertArrayEquals(new Object[] {1}, args(folded.left()));
        assertEquals(4, progress.machineCount());
    }

    @Test void pausedAndOutputBlockedMachinesKeepProgressWithoutARemainingTimeEstimate() {
        for (var state : List.of(State.PAUSED, State.WAITING, State.UNAVAILABLE)) {
            var rows = ComputationProcessingTooltips.rows(ECOProcessingProgress.summarize(List.of(
                machine(50, 100, state, Component.literal("Insufficient energy")))));
            assertTrue(rows.stream().anyMatch(row -> row.kind() == Kind.PROGRESS));
            assertTrue(rows.stream().anyMatch(row -> key(row.left()).endsWith(".time")));
            assertFalse(rows.toString().contains("processing.remaining"));
            assertTrue(rows.toString().contains("Insufficient energy"));
        }
    }

    @Test void summaryOnlyAndRefreshingSnapshotsExplainWhyDetailsAreAbsent() {
        var progress = ECOProcessingProgress.summarize(List.of(machine(0, 100, State.RUNNING, Component.empty())));
        assertTrue(ComputationProcessingTooltips.rows(progress.page(-1)).toString().contains("processing.loading_details"));
        assertTrue(ComputationProcessingTooltips.rows(ECOProcessingProgress.EMPTY.refreshing(true))
            .toString().contains("processing.refreshing"));
    }

    @Test void routineJobAssociationIsHiddenWhileRealReasonsAndSharedMachineWarningsRemain() {
        Component association = Component.translatable("gui.neoecoae.processing.association.job");
        var normal = ComputationProcessingTooltips.rows(ECOProcessingProgress.summarize(List.of(
            machine(10, 100, State.RUNNING, association))));
        assertFalse(normal.toString().contains("association.job"));
        Component blocked = Component.translatable("gui.neoecoae.processing.energy").append(" ").append(association);
        var paused = ComputationProcessingTooltips.rows(ECOProcessingProgress.summarize(List.of(
            machine(10, 100, State.PAUSED, blocked))));
        assertTrue(paused.toString().contains("processing.energy"));
        assertFalse(paused.toString().contains("association.job"));
        var shared = ComputationProcessingTooltips.rows(ECOProcessingProgress.summarize(List.of(
            machine(10, 100, State.RUNNING, Component.translatable("gui.neoecoae.processing.association.recipe")))));
        assertTrue(shared.toString().contains("association.recipe"));
        assertTrue(blocked.toString().contains("association.job"), "Must not mutate the synchronized observation");
    }

    @Test void dimensionsUseLocalizedNamesAndUnknownOrMalformedLocationsStayReadable() {
        for (var dimension : List.of("overworld", "the_nether", "the_end")) {
            var label = ComputationProcessingTooltips.location("minecraft:" + dimension + " [-10, -60, -4]");
            assertTrue(key(label).startsWith("gui.neoecoae.processing.dimension."));
            assertTrue(label.getString().endsWith("  (-10, -60, -4)"));
        }
        assertEquals("addon:moon  (1, 2, 3)", ComputationProcessingTooltips.location("addon:moon [1, 2, 3]").getString());
        assertEquals("unknown", ComputationProcessingTooltips.location("unknown").getString());
        assertEquals("broken [1,2", ComputationProcessingTooltips.location("broken [1,2").getString());
    }

    @Test void rendererDrawsAContinuousTrackWithTheActualCycleFraction() throws Exception {
        Font font = mock(Font.class);
        var tooltip = spy(new ComputationProcessingTooltips.TooltipElement(List.of(
            new Row(Component.empty(), Component.empty(), Kind.PROGRESS, .25F)), font, 260));
        doReturn(10F).when(tooltip).getPositionX();
        doReturn(20F).when(tooltip).getPositionY();
        var context = context();
        tooltip.drawBackgroundAdditional(context);
        verify(context.graphics).fill(10, 21, 190, 23, 0xFF332E3B);
        verify(context.graphics).fill(10, 21, 55, 23, 0xFF60CD64);
        verifyNoMoreInteractions(context.graphics);
    }

    @Test void longMachineNamesWrapWithinTheSpaceLeftByTheRightAlignedStatus() throws Exception {
        Font font = mock(Font.class);
        Component left = Component.literal("A long machine name"), right = Component.literal("Running");
        when(font.width(left)).thenReturn(230);
        when(font.width(right)).thenReturn(42);
        var first = FormattedCharSequence.forward("A long", net.minecraft.network.chat.Style.EMPTY);
        var second = FormattedCharSequence.forward("machine name", net.minecraft.network.chat.Style.EMPTY);
        var status = right.getVisualOrderText();
        when(font.split(left, 206)).thenReturn(List.of(first, second));
        when(font.split(right, 42)).thenReturn(List.of(status));
        when(font.width(status)).thenReturn(42);
        var tooltip = spy(new ComputationProcessingTooltips.TooltipElement(List.of(
            new Row(left, right, Kind.TEXT, 0)), font, 260));
        doReturn(0F).when(tooltip).getPositionX();
        doReturn(0F).when(tooltip).getPositionY();
        var context = context();
        tooltip.drawBackgroundAdditional(context);
        verify(font).split(left, 206);
        verify(context.graphics).drawString(eq(font), same(first), eq(0), eq(0), anyInt(), eq(true));
        verify(context.graphics).drawString(eq(font), same(second), eq(0), eq(font.lineHeight + 2), anyInt(), eq(true));
        verify(context.graphics).drawString(eq(font), same(status), eq(218), eq(0), anyInt(), eq(true));
    }

    @Test void machineTooltipUsesTheRegisteredLdlibComponentWithAnUnshiftedContentOrigin() throws Exception {
        try (var minecraft = mockStatic(net.minecraft.client.Minecraft.class);
             var ldlib = mockStatic(com.lowdragmc.lowdraglib2.LDLib2.class)) {
            ldlib.when(com.lowdragmc.lowdraglib2.LDLib2::isRemote).thenReturn(true);
            com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties.init();
            var client = mock(net.minecraft.client.Minecraft.class);
            var window = mock(com.mojang.blaze3d.platform.Window.class);
            when(window.getGuiScaledWidth()).thenReturn(400);
            when(client.getWindow()).thenReturn(window);
            Font font = mock(Font.class);
            when(font.width(any(net.minecraft.network.chat.FormattedText.class))).thenReturn(40);
            when(font.split(any(net.minecraft.network.chat.FormattedText.class), anyInt()))
                .thenReturn(List.of(FormattedCharSequence.EMPTY));
            var fontField = net.minecraft.client.Minecraft.class.getDeclaredField("font");
            fontField.setAccessible(true);
            fontField.set(client, font);
            minecraft.when(net.minecraft.client.Minecraft::getInstance).thenReturn(client);
            var progress = ECOProcessingProgress.summarize(List.of(machine(41, 200, State.RUNNING, Component.empty())));
            var item = new ComputationCpuItemEntry(null, java.math.BigInteger.ZERO, java.math.BigInteger.ONE,
                java.math.BigInteger.TEN, false, progress);
            var tooltip = ComputationProcessingTooltips.create(List.of(Component.literal("Product"),
                Component.literal("Crafting: 1")), item);
            assertEquals(List.of(Component.literal("Product")), tooltip.tooltipTexts());
            var component = assertInstanceOf(com.lowdragmc.lowdraglib2.gui.ui.utils.ModularUITooltipComponent.class,
                tooltip.tooltipComponent());
            assertEquals(180F, component.modularUI.getWidth());
            assertTrue(component.modularUI.getHeight() > 0);
            assertEquals(0F, component.modularUI.ui.rootElement.getPositionX());
            assertEquals(0F, component.modularUI.ui.rootElement.getPositionY());
        }
    }

    private static Machine machine(int done, int duration, State state, Component reason) {
        return new Machine(Component.literal("Factory"), "minecraft:overworld [1, 2, 3]", "stone",
            done, duration, 144, state, reason);
    }

    private static String key(Component component) {
        return component.getContents() instanceof TranslatableContents translated ? translated.getKey() : "";
    }

    private static Object[] args(Component component) {
        return ((TranslatableContents) component.getContents()).getArgs();
    }

    private static GUIContext context() throws Exception {
        var context = mock(GUIContext.class);
        var field = GUIContext.class.getDeclaredField("graphics");
        field.setAccessible(true);
        field.set(context, mock(GuiGraphics.class));
        return context;
    }
}
