package cn.dancingsnow.neoecoae.gui.computation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.config.CpuSelectionMode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.me.progress.ECOCraftingProgressView;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPULogic;
import cn.dancingsnow.neoecoae.crafting.execution.ECOProcessingMonitor;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ComputationItemActionServerTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void stepActionCallsTheSelectedOutputsControlAndNeverCancelsTheCpu() throws Exception {
        var fixture = new Fixture();
        when(fixture.logic.setProcessingStepStopped(fixture.key, true)).thenReturn(true);
        fixture.action("stop", fixture.job);
        verify(fixture.logic).setProcessingStepStopped(fixture.key, true);
        verify(fixture.logic, never()).cancel();
        assertTrue(fixture.response().getBoolean("close"));
    }

    @Test void aRequestFromAPreviousJobCannotStopTheNextJobOnTheSameCpu() throws Exception {
        var fixture = new Fixture();
        fixture.action("inspect", null);
        assertEquals(fixture.job, fixture.response().getUUID("job"));
        when(fixture.logic.getProcessingJobId()).thenReturn(UUID.randomUUID());
        fixture.action("stop", fixture.job);
        verify(fixture.logic, never()).setProcessingStepStopped(any(), anyBoolean());
        assertEquals("gui.neoecoae.processing.task_changed", fixture.response().getString("error"));
    }

    @Test void machineDetailsHaveTheirOwnLimitedFrequencySnapshotAndStopWhenTheViewCloses() throws Exception {
        var fixture = new Fixture();
        var all = ECOProcessingProgress.summarize(IntStream.range(0, 1000).mapToObj(index ->
            new ECOProcessingProgress.Machine(Component.literal("Machine " + index), "pos" + index, "test:recipe", 50, 100,
                16, ECOProcessingProgress.State.RUNNING, Component.empty())).toList());
        when(fixture.logic.getProcessingProgress(fixture.key)).thenReturn(all);
        fixture.call("select", new Class<?>[]{int.class}, 1);
        var counts = (CompoundTag) fixture.call("snapshot", new Class<?>[0]);
        assertFalse(counts.getList("items", 10).getCompound(0).contains("processing"));
        var summary = (CompoundTag) fixture.call("processingSnapshot", new Class<?>[0]);
        assertEquals(1000, summary.getList("items", 10).getCompound(0).getCompound("progress").getInt("count"));
        assertTrue(summary.getList("items", 10).getCompound(0).getCompound("progress").getList("machines", 10).isEmpty());
        var request = new CompoundTag();
        request.put("key", fixture.key.toTagGeneric()); request.putInt("page", 2);
        fixture.call("processingDetails", new Class<?>[]{int.class, CompoundTag.class}, 1, request);
        var details = (CompoundTag) fixture.call("processingSnapshot", new Class<?>[0]);
        assertEquals(3, details.getList("items", 10).getCompound(0).getCompound("progress").getList("machines", 10).size());
        fixture.tick.set(3);
        fixture.call("snapshot", new Class<?>[0]);
        assertSame(details, fixture.call("processingSnapshot", new Class<?>[0]));
        verify(fixture.logic, times(2)).getProcessingProgress(fixture.key);
        fixture.call("processingDetails", new Class<?>[]{int.class, CompoundTag.class}, -1, new CompoundTag());
        clearInvocations(fixture.logic);
        assertTrue(((CompoundTag) fixture.call("processingSnapshot", new Class<?>[0])).getList("items", 10).isEmpty());
        verify(fixture.logic, never()).refreshProcessingProgress();
        verify(fixture.logic, never()).getProcessingProgress(any());
    }

    @Test void openingTheDefaultCpuEnablesProgressAndReopeningItRestartsTheSubscription() throws Exception {
        var fixture = new Fixture();
        var progress = ECOProcessingProgress.summarize(List.of(new ECOProcessingProgress.Machine(
            Component.literal("Machine"), "pos", "test:recipe", 50, 100, 1,
            ECOProcessingProgress.State.RUNNING, Component.empty())));
        when(fixture.logic.getProcessingProgress(fixture.key)).thenReturn(progress);
        assertTrue(((CompoundTag) fixture.call("processingSnapshot", new Class<?>[0])).getList("items", 10).isEmpty());
        for (int opened = 0; opened < 2; opened++) {
            fixture.call("select", new Class<?>[]{int.class}, 1);
            fixture.call("snapshot", new Class<?>[0]);
            var overview = (CompoundTag) fixture.call("processingSnapshot", new Class<?>[0]);
            assertEquals(1, overview.getList("items", 10).size());
            assertEquals(0.5F, overview.getList("items", 10).getCompound(0).getCompound("progress").getFloat("progress"));
            fixture.call("processingDetails", new Class<?>[]{int.class, CompoundTag.class}, -1, new CompoundTag());
            assertTrue(((CompoundTag) fixture.call("processingSnapshot", new Class<?>[0])).getList("items", 10).isEmpty());
        }
    }

    private static final class Fixture {
        final AEItemKey key = AEItemKey.of(Items.STONE);
        final UUID job = UUID.randomUUID();
        final ECOCraftingCPU cpu = mock(ECOCraftingCPU.class);
        final ECOCraftingCPULogic logic = mock(ECOCraftingCPULogic.class);
        final AtomicLong tick = new AtomicLong();
        final Object server;
        Fixture() throws Exception {
            var config = new ComputationControllerUI.Config(
                () -> true, () -> 1, () -> 0, () -> 0,
                () -> 0L, () -> 1024L, () -> 1024L,
                () -> 0, () -> 8, () -> 4, () -> 16,
                () -> CpuSelectionMode.ANY, ignored -> {},
                () -> false, () -> 3, () -> {},
                () -> true, () -> {}, () -> true, () -> {},
                () -> 1, ignored -> {},
                () -> net.minecraft.world.item.ItemStack.EMPTY, List::of, List::of, null,
                (position, expected, toInventory) -> false, (position, expected) -> false,
                tick::get, () -> true, () -> List.of(cpu), ignored -> {}, ignored -> {}, Component::empty);
            when(cpu.getLogic()).thenReturn(logic);
            when(logic.getProcessingMonitor()).thenReturn(mock(ECOProcessingMonitor.class));
            when(cpu.getSelectionMode()).thenReturn(CpuSelectionMode.ANY);
            var tier = mock(IECOTier.class);
            when(tier.getCPUOverlayTexture()).thenReturn(new ResourceLocation("test", "cpu.png"));
            when(cpu.getTier()).thenReturn(tier);
            var view = mock(ECOCraftingProgressView.class);
            when(view.bigOrder()).thenReturn(Optional.empty());
            when(cpu.getProgressView()).thenReturn(view);
            when(logic.getFinalJobOutput()).thenReturn(new GenericStack(key, 100));
            when(logic.getExactActivePreview()).thenReturn(Map.of(key, java.math.BigInteger.ONE));
            when(logic.getWaitingFor(key)).thenReturn(1L);
            when(logic.getProcessingJobId()).thenReturn(job);
            when(logic.canControlProcessingStep(key)).thenReturn(true);
            var type = Class.forName(ComputationCpuStatusPanel.class.getName() + "$ServerState");
            var constructor = type.getDeclaredConstructor(ComputationControllerUI.Config.class, CpuSelectionState.Identities.class);
            constructor.setAccessible(true);
            server = constructor.newInstance(config, new CpuSelectionState.Identities<ECOCraftingCPU>());
            call("snapshot", new Class<?>[0]);
        }
        Object call(String method, Class<?>[] types, Object... arguments) throws Exception {
            var target = server.getClass().getDeclaredMethod(method, types); target.setAccessible(true);
            return target.invoke(server, arguments);
        }
        void action(String action, UUID job) throws Exception {
            var request = new CompoundTag(); request.putString("action", action); request.putLong("sequence", 7);
            request.put("key", key.toTagGeneric()); if (job != null) request.putUUID("job", job);
            call("itemAction", new Class<?>[]{int.class, CompoundTag.class}, 1, request);
        }
        CompoundTag response() throws Exception {
            var field = server.getClass().getDeclaredField("itemActionResponse"); field.setAccessible(true);
            return (CompoundTag) field.get(server);
        }
    }
}
