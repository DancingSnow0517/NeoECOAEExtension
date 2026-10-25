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

    @Test void barFitsAtTheBottomOfTheExistingCellAndAddsNoText() {
        var context = mock(GUIContext.class);
        var graphics = mock(GuiGraphics.class);
        UiTestSupport.set(context, "graphics", graphics);
        var entry = new ComputationCpuItemEntry(AEItemKey.of(Items.STONE), BigInteger.ZERO,
            BigInteger.ONE, BigInteger.ZERO, false, ECOProcessingProgress.summarize(List.of(machine(50, 100, State.RUNNING))));
        ComputationCpuStatusPanel.drawProcessingProgress(context, entry, 10, 20);
        verify(graphics).fill(12, 39, 75, 41, 0xFF5B566B);
        verify(graphics).fill(12, 39, 44, 41, 0xFFC7A6FF);
        verifyNoMoreInteractions(graphics);
    }

    @Test void missingMachineDataKeepsAVisibleTrackWithoutFabricatingAFilledPercentage() {
        var context = mock(GUIContext.class);
        UiTestSupport.set(context, "graphics", mock(GuiGraphics.class));
        var entry = new ComputationCpuItemEntry(AEItemKey.of(Items.STONE), BigInteger.ZERO,
            BigInteger.ONE, BigInteger.ZERO, false);
        ComputationCpuStatusPanel.drawProcessingProgress(context, entry, 10, 20);
        verify(context.graphics).fill(12, 39, 75, 41, 0xFF5B566B);
        verifyNoMoreInteractions(context.graphics);
        assertTrue(ComputationProcessingTooltips.describe(entry).stream()
            .anyMatch(line -> key(line).equals("gui.neoecoae.processing.progress_unavailable")));
    }

    @Test void queuedStepsAndStorageOnlyCellsDoNotDisplayAProgressBar() {
        var context = mock(GUIContext.class);
        UiTestSupport.set(context, "graphics", mock(GuiGraphics.class));
        var queued = new ComputationCpuItemEntry(AEItemKey.of(Items.STONE), BigInteger.ZERO,
            BigInteger.ZERO, BigInteger.ONE, false);
        ComputationCpuStatusPanel.drawProcessingProgress(context, queued, 10, 20);
        verifyNoInteractions(context.graphics);
        assertTrue(ComputationProcessingTooltips.describe(queued).stream()
            .anyMatch(line -> key(line).equals("gui.neoecoae.processing.awaiting_dispatch")));

        clearInvocations(context.graphics);
        var stored = new ComputationCpuItemEntry(AEItemKey.of(Items.STONE), BigInteger.ONE,
            BigInteger.ZERO, BigInteger.ZERO, false);
        ComputationCpuStatusPanel.drawProcessingProgress(context, stored, 10, 20);
        verifyNoInteractions(context.graphics);
        assertTrue(ComputationProcessingTooltips.describe(stored).isEmpty());
    }

    @Test void staleMachineDataCannotDisplayAQueuedStepsProgressBar() {
        var context = mock(GUIContext.class);
        UiTestSupport.set(context, "graphics", mock(GuiGraphics.class));
        var queued = new ComputationCpuItemEntry(AEItemKey.of(Items.STONE), BigInteger.ZERO,
            BigInteger.ZERO, BigInteger.ONE, false, ECOProcessingProgress.summarize(List.of(machine(50, 100, State.RUNNING))));
        ComputationCpuStatusPanel.drawProcessingProgress(context, queued, 10, 20);
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
