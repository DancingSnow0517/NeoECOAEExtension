package cn.dancingsnow.neoecoae.gui.computation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.Machine;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.util.UiTestSupport;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.math.BigInteger;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ComputationProcessingDisplayTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void progressFillsTheWholeCellAndAddsNoText() {
        var context = mock(GUIContext.class);
        var graphics = mock(GuiGraphics.class);
        UiTestSupport.set(context, "graphics", graphics);
        var entry = new ComputationCpuItemEntry(AEItemKey.of(Items.STONE), BigInteger.ZERO,
            BigInteger.ONE, BigInteger.ZERO, false, ECOProcessingProgress.summarize(List.of(machine(50, 100, State.RUNNING))));
        ComputationCpuStatusPanel.drawProcessingProgress(context, entry, 10, 20, 0);
        verify(graphics).fill(44, 20, 77, 42, 0x30C7A6FF);
        verify(graphics).fill(10, 20, 44, 42, 0x90C7A6FF);
        verifyNoMoreInteractions(graphics);
    }

    @Test void missingMachineDataDoesNotDisplayAProgressTrack() {
        var context = mock(GUIContext.class);
        UiTestSupport.set(context, "graphics", mock(GuiGraphics.class));
        var entry = new ComputationCpuItemEntry(AEItemKey.of(Items.STONE), BigInteger.ZERO,
            BigInteger.ONE, BigInteger.ZERO, false);
        ComputationCpuStatusPanel.drawProcessingProgress(context, entry, 10, 20, 0);
        verifyNoInteractions(context.graphics);
        assertTrue(ComputationProcessingTooltips.describe(entry).stream()
            .anyMatch(line -> key(line).equals("gui.neoecoae.processing.progress_unavailable")));
    }

    @Test void fullCellProgressRetainsBlockedPausedAndUserSelectedColors() {
        for (State state : List.of(State.RUNNING, State.WAITING, State.PAUSED)) {
            int rgb = state == State.RUNNING ? 0xC7A6FF : state == State.WAITING ? 0xE2AA4F : 0x90909F;
            for (int overlay : List.of(0, 0x123456)) {
                var graphics = mock(GuiGraphics.class);
                var context = new GUIContext(graphics, 0, 0, 0);
                var entry = new ComputationCpuItemEntry(AEItemKey.of(Items.STONE), BigInteger.ZERO,
                    BigInteger.ONE, BigInteger.ZERO, false,
                    ECOProcessingProgress.summarize(List.of(machine(50, 100, state))));
                assertTrue(ComputationCpuStatusPanel.drawProcessingProgress(context, entry, 10, 20, overlay));
                int color = overlay == 0 ? rgb : overlay;
                verify(graphics).fill(44, 20, 77, 42, color | 0x30000000);
                verify(graphics).fill(10, 20, 44, 42, color | 0x90000000);
            }
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
        var context = mock(GUIContext.class);
        UiTestSupport.set(context, "graphics", mock(GuiGraphics.class));
        var queued = new ComputationCpuItemEntry(AEItemKey.of(Items.STONE), BigInteger.ZERO,
            BigInteger.ZERO, BigInteger.ONE, false);
        ComputationCpuStatusPanel.drawProcessingProgress(context, queued, 10, 20, 0);
        verifyNoInteractions(context.graphics);
        assertTrue(ComputationProcessingTooltips.describe(queued).stream()
            .anyMatch(line -> key(line).equals("gui.neoecoae.processing.awaiting_dispatch")));

        clearInvocations(context.graphics);
        var stored = new ComputationCpuItemEntry(AEItemKey.of(Items.STONE), BigInteger.ONE,
            BigInteger.ZERO, BigInteger.ZERO, false);
        ComputationCpuStatusPanel.drawProcessingProgress(context, stored, 10, 20, 0);
        verifyNoInteractions(context.graphics);
        assertTrue(ComputationProcessingTooltips.describe(stored).isEmpty());
    }

    @Test void staleMachineDataCannotDisplayAQueuedStepsProgressBar() {
        var context = mock(GUIContext.class);
        UiTestSupport.set(context, "graphics", mock(GuiGraphics.class));
        var queued = new ComputationCpuItemEntry(AEItemKey.of(Items.STONE), BigInteger.ZERO,
            BigInteger.ZERO, BigInteger.ONE, false, ECOProcessingProgress.summarize(List.of(machine(50, 100, State.RUNNING))));
        ComputationCpuStatusPanel.drawProcessingProgress(context, queued, 10, 20, 0);
        verifyNoInteractions(context.graphics);
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
