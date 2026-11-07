package cn.dancingsnow.neoecoae.gui.computation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.stacks.AEItemKey;
import appeng.api.client.AEKeyRendering;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.Machine;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.util.UiTestSupport;
import cn.dancingsnow.neoecoae.gui.common.HostProgressAnimation;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.math.BigInteger;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.IntStream;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ComputationProcessingDisplayTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void refreshingNeverAddsAMachineStatusNotice() {
        assertTrue(ComputationProcessingTooltips.rows(ECOProcessingProgress.EMPTY.refreshing(true)).isEmpty());
        var progress = ECOProcessingProgress.summarize(List.of(machine(50, 100, State.RUNNING))).refreshing(true);
        var lines = ComputationProcessingTooltips.describe(progress);
        assertTrue(lines.stream().noneMatch(line -> key(line).equals("gui.neoecoae.processing.refreshing")));
        assertTrue(lines.stream().anyMatch(line -> key(line).equals("gui.neoecoae.processing.count")));
    }

    @Test void pendingOverheatStatusReachesTheCellAndTooltipWithoutInventingACycle() throws Exception {
        var key = AEItemKey.of(Items.STONE);
        var progress = ECOProcessingProgress.summarize(List.of(new Machine(Component.literal("Workstation"),
            "minecraft:overworld [62, -60, -97]", "", 0, 0, 0, State.WAITING,
            Component.translatable("gui.neoecoae.large_integrated_working_station.pause.overheated"))));
        var pending = new ComputationCpuItemEntry(key, BigInteger.ZERO, BigInteger.ZERO, BigInteger.TEN, false);
        var panel = mock(ComputationCpuStatusPanel.class, CALLS_REAL_METHODS);
        UiTestSupport.set(panel, "processingByKey", java.util.Map.of(key, progress));
        var bind = ComputationCpuStatusPanel.class.getDeclaredMethod("withProcessing", ComputationCpuItemEntry.class);
        bind.setAccessible(true);
        var observed = (ComputationCpuItemEntry) bind.invoke(panel, pending);
        assertTrue(observed.waitingForMachine());
        assertEquals(BigInteger.ZERO, observed.active());
        assertEquals(BigInteger.TEN, observed.pending());
        assertFalse(observed.processing().hasProgress());
        assertTrue(ComputationProcessingTooltips.rows(observed).toString().contains("pause.overheated"));
        assertTrue(ComputationProcessingTooltips.describe(observed).toString().contains("pause.overheated"));
        assertTrue(ComputationCpuStatusPanel.itemDescription(observed, false).stream()
            .anyMatch(line -> key(line).equals("gui.neoecoae.processing.state.waiting")));
        var row = drawTask(observed);
        verify(row.graphics()).fill(10, 20, 77, 42, 0x30E2AA4F);
        verify(row.graphics(), times(1)).fill(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        assertTrue(row.text().stream().noneMatch(text -> text.endsWith("%")));

        UiTestSupport.set(panel, "processingByKey", java.util.Map.of(key, ECOProcessingProgress.EMPTY));
        var recovered = (ComputationCpuItemEntry) bind.invoke(panel, pending);
        assertFalse(recovered.waitingForMachine());
        assertTrue(ComputationProcessingTooltips.rows(recovered).isEmpty());
        assertTrue(ComputationCpuStatusPanel.itemDescription(recovered, false).stream()
            .noneMatch(line -> key(line).equals("gui.neoecoae.processing.state.waiting")));
    }

    @Test void taskProgressFillsTheWholeRowAndShowsItsPercentage() throws Exception {
        var entry = new ComputationCpuItemEntry(AEItemKey.of(Items.STONE), BigInteger.ZERO,
            BigInteger.ONE, BigInteger.ZERO, false, ECOProcessingProgress.summarize(List.of(machine(50, 100, State.RUNNING))));
        var row = drawTask(entry);
        verify(row.graphics()).fill(44, 20, 77, 42, 0x3060CD64);
        verify(row.graphics()).fill(10, 20, 44, 42, 0x9060CD64);
        assertTrue(row.text().contains("50%"));
    }

    @Test void missingMachineDataOnlyDisplaysTheTaskRowTint() throws Exception {
        var entry = new ComputationCpuItemEntry(AEItemKey.of(Items.STONE), BigInteger.ZERO,
            BigInteger.ONE, BigInteger.ZERO, false);
        var row = drawTask(entry);
        verify(row.graphics()).fill(10, 20, 77, 42, 0x3060CD64);
        verify(row.graphics(), times(1)).fill(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        assertTrue(row.text().stream().noneMatch(text -> text.endsWith("%")));
        assertTrue(ComputationProcessingTooltips.describe(entry).stream()
            .anyMatch(line -> key(line).equals("gui.neoecoae.processing.progress_unavailable")));
    }

    @Test void taskRowProgressRetainsRunningBlockedAndPausedColors() throws Exception {
        for (State state : List.of(State.RUNNING, State.WAITING, State.PAUSED)) {
            int rgb = state == State.RUNNING ? 0x60CD64 : state == State.WAITING ? 0xE2AA4F : 0x90909F;
            var entry = new ComputationCpuItemEntry(AEItemKey.of(Items.STONE), BigInteger.ZERO,
                BigInteger.ONE, BigInteger.ZERO, false,
                ECOProcessingProgress.summarize(List.of(machine(50, 100, state))));
            var row = drawTask(entry);
            verify(row.graphics()).fill(44, 20, 77, 42, rgb | 0x30000000);
            verify(row.graphics()).fill(10, 20, 44, 42, rgb | 0x90000000);
            assertEquals(state != State.WAITING, row.text().contains("50%"));
        }
    }

    @Test void shortCyclesDoNotChurnSyncPacketsButKeepMachineIdentityAndState() {
        for (int duration : List.of(1, 5)) {
            var first = ECOProcessingProgress.summarize(List.of(machine(0, duration, State.RUNNING))).forStatusSync();
            var next = ECOProcessingProgress.summarize(List.of(machine(1, duration, State.RUNNING))).forStatusSync();
            assertEquals(first.write(), next.write());
            assertFalse(next.hasProgress());
            assertEquals(1, next.machineCount());
            assertEquals("Machine", next.machines().get(0).name().getString());
            assertEquals(State.RUNNING, next.state());
            assertEquals(0, next.machines().get(0).durationTicks());
            var restored = ECOProcessingProgress.read(next.page(0).write());
            assertEquals(next, restored);
        }
        var longCycle = ECOProcessingProgress.summarize(List.of(machine(3, 6, State.RUNNING)));
        assertSame(longCycle, longCycle.forStatusSync());
    }

    @Test void mixedCyclesTrimOnlyShortDetailsAndRespectParallelWeightedDuration() {
        var shortCycle = new Machine(Component.literal("Fast"), "", "", 1, 5, 1000, State.RUNNING, Component.empty());
        var longCycle = machine(50, 100, State.WAITING);
        var mixed = ECOProcessingProgress.summarize(List.of(shortCycle, longCycle)).forStatusSync();
        assertTrue(mixed.hasProgress());
        assertEquals(2, mixed.machineCount());
        assertEquals(0, mixed.machines().get(0).durationTicks());
        assertEquals(100, mixed.machines().get(1).durationTicks());
        assertEquals(State.WAITING, mixed.state());
        assertTrue(mixed.page(-1).machines().isEmpty());
    }

    @Test void queuedStepsAndStorageOnlyCellsDoNotDisplayAProgressBar() {
        var queued = new ComputationCpuItemEntry(AEItemKey.of(Items.STONE), BigInteger.ZERO,
            BigInteger.ZERO, BigInteger.ONE, false);
        verify(drawItem(queued), never()).fill(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        assertTrue(ComputationProcessingTooltips.describe(queued).stream()
            .anyMatch(line -> key(line).equals("gui.neoecoae.processing.awaiting_dispatch")));

        var stored = new ComputationCpuItemEntry(AEItemKey.of(Items.STONE), BigInteger.ONE,
            BigInteger.ZERO, BigInteger.ZERO, false);
        verify(drawItem(stored), never()).fill(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        assertTrue(ComputationProcessingTooltips.describe(stored).isEmpty());
    }

    @Test void staleMachineDataCannotDisplayAQueuedStepsProgressBar() {
        var queued = new ComputationCpuItemEntry(AEItemKey.of(Items.STONE), BigInteger.ZERO,
            BigInteger.ZERO, BigInteger.ONE, false, ECOProcessingProgress.summarize(List.of(machine(50, 100, State.RUNNING))));
        verify(drawItem(queued), never()).fill(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        assertTrue(ComputationProcessingTooltips.describe(queued).stream()
            .noneMatch(line -> key(line).equals("gui.neoecoae.processing.time")));
    }

    @Test void pausedAndBlockedCyclesDoNotAdvertiseAnElapsedTimeCountdown() {
        for (State state : List.of(State.WAITING, State.PAUSED)) {
            var lines = ComputationProcessingTooltips.describe(ECOProcessingProgress.summarize(List.of(machine(50, 100, state))));
            assertTrue(lines.stream().anyMatch(line -> key(line).equals("gui.neoecoae.processing.time")));
            assertFalse(lines.stream().anyMatch(line -> key(line).equals("gui.neoecoae.processing.remaining")));
        }
    }

    @Test void aThousandMachineSummarySendsNoDetailsAndOneTooltipPageRemainsSmall() throws Exception {
        var all = ECOProcessingProgress.summarize(IntStream.range(0, 1000)
            .mapToObj(index -> new Machine(Component.literal("Machine " + index), "overworld [" + index + ",64,0]",
                "test:recipe", 50, 100, 64, State.RUNNING, Component.empty())).toList());
        assertEquals(1000, all.page(-1).machineCount());
        assertTrue(all.page(-1).machines().isEmpty());
        assertTrue(bytes(all.page(-1)) < 256);
        assertEquals(ECOProcessingProgress.DETAIL_PAGE_SIZE, all.page(100).machines().size());
        assertTrue(bytes(all.page(100)) < 2048);
        System.out.printf("1000-machine fixture: summary=%d bytes, tooltip page=%d bytes (NBT only)%n",
            bytes(all.page(-1)), bytes(all.page(100)));
    }

    private record TaskRow(GuiGraphics graphics, List<String> text) {}

    private static TaskRow drawTask(ComputationCpuItemEntry entry) throws Exception {
        var panel = mock(ComputationCpuStatusPanel.class, CALLS_REAL_METHODS);
        var animation = mock(HostProgressAnimation.class);
        when(animation.value(anyDouble())).thenAnswer(call -> ((Double) call.getArgument(0)).floatValue());
        UiTestSupport.set(panel, "opening", animation);
        var graphics = mock(GuiGraphics.class);
        when(graphics.pose()).thenReturn(new PoseStack());
        var context = new GUIContext(graphics, 0, 0, 0);
        var text = new ArrayList<String>();
        try (var keys = mockStatic(AEKeyRendering.class);
             var rows = mockStatic(ComputationCpuSelector.class)) {
            rows.when(() -> ComputationCpuSelector.fittedText(any(), any(), anyString(), anyFloat(), anyFloat(),
                anyInt(), anyFloat(), anyInt())).thenAnswer(call -> { text.add(call.getArgument(2)); return null; });
            var draw = ComputationCpuStatusPanel.class.getDeclaredMethod("drawTask", GUIContext.class,
                Font.class, ComputationCpuItemEntry.class, float.class, float.class);
            draw.setAccessible(true);
            draw.invoke(panel, context, mock(Font.class), entry, 10F, 20F);
        }
        return new TaskRow(graphics, text);
    }

    private static GuiGraphics drawItem(ComputationCpuItemEntry entry) {
        var graphics = mock(GuiGraphics.class);
        when(graphics.pose()).thenReturn(new PoseStack());
        try (var keys = mockStatic(AEKeyRendering.class)) {
            ComputationCpuStatusPanel.drawItem(new GUIContext(graphics, 0, 0, 0), mock(Font.class), entry,
                10, 20, false);
        }
        return graphics;
    }

    private static int bytes(ECOProcessingProgress progress) throws Exception {
        var bytes = new ByteArrayOutputStream();
        NbtIo.write(progress.write(), new DataOutputStream(bytes));
        return bytes.size();
    }

    private static String key(Component component) {
        return component.getContents() instanceof TranslatableContents translation ? translation.getKey() : "";
    }

    private static Machine machine(int progress, int duration, State state) {
        return new Machine(Component.literal("Machine"), "overworld [0,64,0]", "test:recipe", progress, duration, 64, state, Component.empty());
    }
}
