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
import net.minecraft.client.gui.GuiGraphics;
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

    @Test void nativeCycleUsesTheSameBarGeometryAndBlockedColor() throws Exception {
        for (var state : List.of(State.RUNNING, State.WAITING)) {
            var context = context();
            var entry = entry(BigInteger.ONE, 50, 100, state);
            ComputationCpuStatusPanel.drawProcessingProgress(context, entry, 10, 20);
            verify(context.graphics).fill(12, 39, 75, 41, 0xFF5B566B);
            verify(context.graphics).fill(12, 39, 44, 41, state == State.RUNNING ? 0xFFC7A6FF : 0xFFE2AA4F);
            verifyNoMoreInteractions(context.graphics);
        }
    }

    @Test void unsupportedAndQueuedEntriesNeverDrawATrack() throws Exception {
        var context = context();
        var unsupported = new ComputationCpuItemEntry(AEItemKey.of(Items.STONE), BigInteger.ZERO,
            BigInteger.ONE, BigInteger.ZERO, false);
        ComputationCpuStatusPanel.drawProcessingProgress(context, unsupported, 10, 20);
        ComputationCpuStatusPanel.drawProcessingProgress(context, entry(BigInteger.ONE, 0, 0, State.QUEUED), 10, 20);
        ComputationCpuStatusPanel.drawProcessingProgress(context, entry(BigInteger.ZERO, 50, 100, State.RUNNING), 10, 20);
        verifyNoInteractions(context.graphics);
    }

    @Test void summariesKeepAZeroPercentNativeCycleVisibleWithoutSendingMachineDetails() throws Exception {
        var context = context();
        var nativeCycle = entry(BigInteger.ONE, 0, 100, State.RUNNING);
        assertTrue(nativeCycle.processing().page(-1).hasProgress());
        assertTrue(nativeCycle.processing().page(-1).machines().isEmpty());
        ComputationCpuStatusPanel.drawProcessingProgress(context,
            nativeCycle.withProcessing(nativeCycle.processing().page(-1)), 10, 20);
        verify(context.graphics).fill(12, 39, 75, 41, 0xFF5B566B);
        verifyNoMoreInteractions(context.graphics);
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
        field.set(context, mock(GuiGraphics.class));
        return context;
    }
}
