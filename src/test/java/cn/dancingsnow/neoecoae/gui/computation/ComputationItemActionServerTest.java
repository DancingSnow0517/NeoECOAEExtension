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

    @Test void tasksRegroupByLiveStateAndKeepTheirOriginalOrderWithinEachGroup() throws Exception {
        var fixture = new Fixture();
        fixture.call("select", new Class<?>[]{int.class}, 1);
        fixture.call("snapshot", new Class<?>[0]);
        var entries = List.of(Items.STONE, Items.DIRT, Items.GOLD_INGOT, Items.IRON_INGOT, Items.DIAMOND)
            .stream().map(item -> new ComputationCpuItemEntry(AEItemKey.of(item), java.math.BigInteger.ZERO,
                java.math.BigInteger.ONE, java.math.BigInteger.ZERO, false)).toList();
        var states = List.of(ECOProcessingProgress.State.QUEUED, ECOProcessingProgress.State.RUNNING,
            ECOProcessingProgress.State.WAITING, ECOProcessingProgress.State.PAUSED,
            ECOProcessingProgress.State.UNAVAILABLE);
        for (int index = 0; index < entries.size(); index++) {
            when(fixture.monitor.cachedProgress(entries.get(index).key())).thenReturn(
                new ECOProcessingProgress(0, false, states.get(index), 1, -1, false, List.of()));
        }
        var types = new Class<?>[]{appeng.api.networking.crafting.ICraftingCPU.class, List.class};
        var first = (List<?>) fixture.call("tasks", types, fixture.cpu, entries);
        assertEquals(entries.get(1), first.get(0));
        assertEquals(entries.get(0), first.get(1));
        var problemOrder = List.copyOf(first.subList(2, first.size()));
        assertEquals(new java.util.HashSet<>(entries.subList(2, entries.size())), new java.util.HashSet<>(problemOrder));

        when(fixture.monitor.cachedProgress(entries.get(0).key())).thenReturn(
            new ECOProcessingProgress(0, false, ECOProcessingProgress.State.RUNNING, 1, -1, false, List.of()));
        var changed = (List<?>) fixture.call("tasks", types, fixture.cpu, entries);
        assertEquals(entries.get(0), changed.get(0));
        assertEquals(entries.get(1), changed.get(1));
        assertEquals(problemOrder, changed.subList(2, changed.size()));
        verify(fixture.logic, never()).getProcessingProgress(any());
    }

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
        // Task ordering also reads live progress; count detail queries independently.
        clearInvocations(fixture.logic);
        var summary = (CompoundTag) fixture.call("processingSnapshot", new Class<?>[0]);
        assertEquals(1000, summary.getList("items", 10).getCompound(0).getCompound("progress").getInt("count"));
        assertTrue(summary.getList("items", 10).getCompound(0).getCompound("progress").getList("machines", 10).isEmpty());
        var request = new CompoundTag();
        request.put("key", fixture.key.toTagGeneric()); request.putInt("page", 2);
        fixture.call("processingDetails", new Class<?>[]{int.class, CompoundTag.class}, 1, request);
        var details = (CompoundTag) fixture.call("processingSnapshot", new Class<?>[0]);
        assertEquals(3, details.getList("items", 10).getCompound(0).getCompound("progress").getList("machines", 10).size());
        verify(fixture.logic, times(2)).getProcessingProgress(fixture.key);
        fixture.tick.set(3);
        fixture.call("snapshot", new Class<?>[0]);
        clearInvocations(fixture.logic);
        assertSame(details, fixture.call("processingSnapshot", new Class<?>[0]));
        verify(fixture.logic, never()).getProcessingProgress(any());
        fixture.call("processingDetails", new Class<?>[]{int.class, CompoundTag.class}, -1, new CompoundTag());
        clearInvocations(fixture.logic);
        assertTrue(((CompoundTag) fixture.call("processingSnapshot", new Class<?>[0])).getList("items", 10).isEmpty());
        verify(fixture.logic, never()).refreshProcessingProgress();
        verify(fixture.logic, never()).getProcessingProgress(any());
    }

    @Test void pendingAdmissionFailureIsSynchronizedBeforeTheFirstActiveOutput() throws Exception {
        var fixture = new Fixture();
        when(fixture.logic.getExactActivePreview()).thenReturn(Map.of());
        when(fixture.logic.getWaitingFor(fixture.key)).thenReturn(0L);
        when(fixture.logic.getPendingOutputs(fixture.key)).thenReturn(10L);
        when(fixture.logic.getExactPendingPreview()).thenReturn(Map.of(fixture.key, java.math.BigInteger.TEN));
        var progress = ECOProcessingProgress.summarize(List.of(new ECOProcessingProgress.Machine(
            Component.literal("Workstation"), "minecraft:overworld [62, -60, -97]", "", 0, 0, 0,
            ECOProcessingProgress.State.WAITING, Component.translatable(
                "gui.neoecoae.large_integrated_working_station.pause.overheated"))));
        when(fixture.logic.getProcessingProgress(fixture.key)).thenReturn(progress);
        when(fixture.monitor.cachedProgress(fixture.key)).thenReturn(progress);
        fixture.tick.set(3);
        fixture.call("select", new Class<?>[]{int.class}, 1);
        fixture.call("snapshot", new Class<?>[0]);
        var request = new CompoundTag();
        request.put("key", fixture.key.toTagGeneric());
        request.putInt("page", 0);
        fixture.call("processingDetails", new Class<?>[]{int.class, CompoundTag.class}, 1, request);
        var snapshot = (CompoundTag) fixture.call("processingSnapshot", new Class<?>[0]);
        var observed = ECOProcessingProgress.read(snapshot.getList("items", 10).getCompound(0).getCompound("progress"));
        assertEquals(ECOProcessingProgress.State.WAITING, observed.state());
        assertEquals(1, observed.machineCount());
        assertFalse(observed.hasProgress());
        assertTrue(observed.machines().get(0).reason().toString().contains("pause.overheated"));

        when(fixture.logic.getProcessingProgress(fixture.key)).thenReturn(ECOProcessingProgress.EMPTY);
        when(fixture.monitor.cachedProgress(fixture.key)).thenReturn(ECOProcessingProgress.EMPTY);
        fixture.tick.set(13);
        var recovered = (CompoundTag) fixture.call("processingSnapshot", new Class<?>[0]);
        assertTrue(recovered.getList("items", 10).isEmpty());
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

    @Test void directTerminalEntryPublishesItsFirstProgressBeforeAnyCpuClick() throws Exception {
        var fixture = new Fixture(true);
        when(fixture.logic.getProcessingProgress(fixture.key)).thenReturn(progress(50));
        var first = (CompoundTag) fixture.call("processingSnapshot", new Class<?>[0]);
        assertEquals(1, first.getInt("serial"));
        assertFalse(first.getBoolean("delta"));
        assertEquals(1, first.getList("items", 10).size());
        assertEquals(0.5F, first.getList("items", 10).getCompound(0).getCompound("progress").getFloat("progress"));
    }

    @Test void progressDeltasKeepUnchangedRowsAndRemoveOnlyDisappearedSamples() throws Exception {
        var fixture = new Fixture(true);
        var second = AEItemKey.of(Items.DIRT);
        when(fixture.logic.getExactActivePreview()).thenReturn(Map.of(fixture.key, java.math.BigInteger.ONE, second, java.math.BigInteger.ONE));
        when(fixture.logic.getWaitingFor(second)).thenReturn(1L);
        when(fixture.logic.getProcessingProgress(any())).thenReturn(progress(50));
        fixture.tick.set(10);
        var first = (CompoundTag) fixture.call("processingSnapshot", new Class<?>[0]);
        assertEquals(2, first.getList("items", 10).size());
        when(fixture.logic.getProcessingProgress(fixture.key)).thenReturn(progress(60));
        fixture.tick.set(15);
        var change = (CompoundTag) fixture.call("processingSnapshot", new Class<?>[0]);
        assertTrue(change.getBoolean("delta"));
        assertEquals(1, change.getList("items", 10).size());
        assertEquals(fixture.key.toTagGeneric(), change.getList("items", 10).getCompound(0).getCompound("key"));
        assertTrue(bytes(change) < bytes(first), "A single changed row must encode less data than a full snapshot");
        System.out.printf("Processing sync fixture: full=%d bytes, one-row delta=%d bytes (NBT only)%n", bytes(first), bytes(change));
        fixture.tick.set(20);
        assertSame(change, fixture.call("processingSnapshot", new Class<?>[0]), "No new packet when neither visible sample changes");
        when(fixture.logic.getProcessingProgress(second)).thenReturn(ECOProcessingProgress.EMPTY);
        fixture.tick.set(25);
        var removal = (CompoundTag) fixture.call("processingSnapshot", new Class<?>[0]);
        assertTrue(removal.getList("items", 10).isEmpty());
        assertEquals(second.toTagGeneric(), removal.getList("removed", 10).getCompound(0));
        when(fixture.logic.getProcessingJobId()).thenReturn(UUID.randomUUID());
        fixture.tick.set(26);
        var nextJob = (CompoundTag) fixture.call("processingSnapshot", new Class<?>[0]);
        assertFalse(nextJob.getBoolean("delta"), "A replacement job on the same CPU starts with a full reset");
    }

    @Test void visibleRowsAreRequestedBeforeBoundedBackgroundWorkAndClosingStopsAllRequests() throws Exception {
        var fixture = new Fixture(true);
        Map<appeng.api.stacks.AEKey, java.math.BigInteger> active = new java.util.LinkedHashMap<>();
        for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            var key = AEItemKey.of(item);
            if (key != null) active.put(key, java.math.BigInteger.ONE);
            if (active.size() == 100) break;
        }
        when(fixture.logic.getExactActivePreview()).thenReturn(active);
        when(fixture.logic.getProcessingProgress(any())).thenReturn(progress(50));
        fixture.tick.set(10);
        clearInvocations(fixture.logic);
        var packet = (CompoundTag) fixture.call("processingSnapshot", new Class<?>[0]);
        assertEquals(21, packet.getList("items", 10).size());
        long requests = mockingDetails(fixture.logic).getInvocations().stream()
            .filter(call -> call.getMethod().getName().equals("getProcessingProgress")).count();
        assertTrue(requests <= 21 + 7 + 16, "Only visible rows and sixteen background candidates request scans");
        System.out.printf("100-output fixture: visible progress=%d, scan requests=%d, payload=%d bytes (NBT only)%n",
            packet.getList("items", 10).size(), requests, bytes(packet));
        fixture.call("processingDetails", new Class<?>[]{int.class, CompoundTag.class}, -1, new CompoundTag());
        clearInvocations(fixture.logic, fixture.monitor);
        for (long tick = 20; tick < 100; tick += 5) {
            fixture.tick.set(tick);
            fixture.call("snapshot", new Class<?>[0]);
            fixture.call("processingSnapshot", new Class<?>[0]);
        }
        verify(fixture.logic, never()).getProcessingProgress(any());
        verify(fixture.logic, never()).getExactActivePreview();
        verify(fixture.monitor, never()).cachedProgress(any());
    }

    private static ECOProcessingProgress progress(int ticks) {
        return ECOProcessingProgress.summarize(List.of(new ECOProcessingProgress.Machine(Component.literal("Machine"),
            "minecraft:overworld [0,64,0]", "test:recipe", ticks, 100, 1, ECOProcessingProgress.State.RUNNING, Component.empty())));
    }

    private static int bytes(CompoundTag tag) throws java.io.IOException {
        var bytes = new java.io.ByteArrayOutputStream();
        try (var output = new java.io.DataOutputStream(bytes)) { net.minecraft.nbt.NbtIo.write(tag, output); }
        return bytes.size();
    }

    private static final class Fixture {
        final AEItemKey key = AEItemKey.of(Items.STONE);
        final UUID job = UUID.randomUUID();
        final ECOCraftingCPU cpu = mock(ECOCraftingCPU.class);
        final ECOCraftingCPULogic logic = mock(ECOCraftingCPULogic.class);
        final ECOProcessingMonitor monitor = mock(ECOProcessingMonitor.class);
        final AtomicLong tick = new AtomicLong();
        final Object server;
        Fixture() throws Exception {
            this(false);
        }
        Fixture(boolean direct) throws Exception {
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
            when(logic.getProcessingMonitor()).thenReturn(monitor);
            when(monitor.cachedProgress(any())).thenReturn(ECOProcessingProgress.EMPTY);
            when(cpu.getSelectionMode()).thenReturn(CpuSelectionMode.ANY);
            var tier = mock(IECOTier.class);
            when(tier.getCPUOverlayTexture()).thenReturn(new ResourceLocation("test", "cpu.png"));
            when(tier.getPanelTierName()).thenReturn(Component.literal("Test tier"));
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
            var constructor = type.getDeclaredConstructor(ComputationCpuStatusPanel.Source.class, CpuSelectionState.Identities.class);
            constructor.setAccessible(true);
            var source = ComputationCpuStatusPanel.Source.of(config);
            if (direct) source = new ComputationCpuStatusPanel.Source(source.player(), source.gameTime(), source.canInteract(),
                source.cpus(), source.toggleCpuSuspended(), source.cancelCpu(), () -> {});
            server = constructor.newInstance(source,
                new CpuSelectionState.Identities<appeng.api.networking.crafting.ICraftingCPU>());
            call("refresh", new Class<?>[0]);
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
