package cn.dancingsnow.neoecoae.gui.computation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.Machine;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import java.math.BigInteger;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ComputationProcessingDisplayTest {
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

    @Test void nativeCycleFillsTheWholeCellWithTheStateColor() throws Exception {
        for (var state : List.of(State.RUNNING, State.WAITING)) {
            var context = context();
            var entry = entry(BigInteger.ONE, 50, 100, state);
            int rgb = state == State.RUNNING ? 0xC7A6FF : 0xE2AA4F;
            assertTrue(ComputationCpuStatusPanel.drawProcessingProgress(context, entry, 10, 20, 0));
            verify(context.graphics).fill(44, 20, 77, 42, rgb | 0x30000000);
            verify(context.graphics).fill(10, 20, 44, 42, rgb | 0x90000000);
            verifyNoMoreInteractions(context.graphics);
        }
    }

    @Test void coloredStatusOverlayTintsTheFill() throws Exception {
        var context = context();
        assertTrue(ComputationCpuStatusPanel.drawProcessingProgress(context,
            entry(BigInteger.ONE, 100, 100, State.RUNNING), 10, 20, 0x5A123456));
        verify(context.graphics).fill(10, 20, 77, 42, 0x90123456);
        verifyNoMoreInteractions(context.graphics);
    }

    @Test void unsupportedAndQueuedEntriesNeverDrawAFill() throws Exception {
        var context = context();
        var unsupported = new ComputationCpuItemEntry(AEItemKey.of(Items.STONE), BigInteger.ZERO,
            BigInteger.ONE, BigInteger.ZERO, false);
        assertFalse(ComputationCpuStatusPanel.drawProcessingProgress(context, unsupported, 10, 20, 0));
        assertFalse(ComputationCpuStatusPanel.drawProcessingProgress(context, entry(BigInteger.ONE, 0, 0, State.QUEUED), 10, 20, 0));
        assertFalse(ComputationCpuStatusPanel.drawProcessingProgress(context, entry(BigInteger.ZERO, 50, 100, State.RUNNING), 10, 20, 0));
        verifyNoInteractions(context.graphics);
    }

    @Test void summariesKeepAZeroPercentNativeCycleVisibleWithoutSendingMachineDetails() throws Exception {
        var context = context();
        var nativeCycle = entry(BigInteger.ONE, 0, 100, State.RUNNING);
        assertTrue(nativeCycle.processing().page(-1).hasProgress());
        assertTrue(nativeCycle.processing().page(-1).machines().isEmpty());
        assertTrue(ComputationCpuStatusPanel.drawProcessingProgress(context,
            nativeCycle.withProcessing(nativeCycle.processing().page(-1)), 10, 20, 0));
        verify(context.graphics).fill(10, 20, 77, 42, 0x30C7A6FF);
        verifyNoMoreInteractions(context.graphics);
    }

    @Test void cyclesOfFiveTicksOrLessAreNotSyncedAsProgress() throws Exception {
        for (int duration : List.of(1, 5)) {
            var synced = entry(BigInteger.ONE, 1, duration, State.RUNNING).processing().forStatusSync();
            assertFalse(synced.hasProgress());
            assertEquals(0, synced.machines().getFirst().progressTicks());
            assertEquals(0, synced.machines().getFirst().durationTicks());
            assertEquals(1, synced.machineCount());
            var context = context();
            assertFalse(ComputationCpuStatusPanel.drawProcessingProgress(context,
                entry(BigInteger.ONE, 1, duration, State.RUNNING).withProcessing(synced), 10, 20, 0));
            verifyNoInteractions(context.graphics);
        }
        var longCycle = entry(BigInteger.ONE, 3, 6, State.RUNNING).processing();
        assertSame(longCycle, longCycle.forStatusSync());
    }

    private static ComputationCpuItemEntry entry(BigInteger active, int progress, int duration, State state) {
        return new ComputationCpuItemEntry(AEItemKey.of(Items.STONE), BigInteger.ZERO, active, BigInteger.ZERO,
            false, ECOProcessingProgress.summarize(List.of(new Machine(Component.literal("Workstation"),
                "overworld [0,64,0]", "test:recipe", progress, duration, 256, state, Component.empty()))));
    }

    private static GUIContext context() throws Exception {
        var context = mock(GUIContext.class);
        var field = context.getClass().getDeclaredField("graphics");
        field.setAccessible(true);
        field.set(context, mock(GuiGraphicsExtractor.class));
        return context;
    }
}
