package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.*;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.me.provider.*;
import cn.dancingsnow.neoecoae.mixins.ae2.crafting.*;
import cn.dancingsnow.neoecoae.mixins.compat.advancedae.accessor.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ECOExternalCpuFastPathTest {
    @BeforeAll static void bootstrap() {
        cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize();
    }
    interface FastProvider extends ICraftingProvider, ECOFastPathDispatchProvider {}
    interface ParallelProvider extends ICraftingProvider, ECOParallelCraftingProvider {}
    final AEKey input = mock(AEKey.class, RETURNS_DEEP_STUBS);
    final AEKey output = mock(AEKey.class, RETURNS_DEEP_STUBS);
    final AEKey container = mock(AEKey.class, RETURNS_DEEP_STUBS);
    final IPatternDetails pattern = mock(IPatternDetails.class, RETURNS_DEEP_STUBS);
    final IEnergyService energy = mock(IEnergyService.class);
    final CraftingService service = mock(CraftingService.class);
    final Level level = mock(Level.class);
    final ListCraftingInventory inventory = new ListCraftingInventory(ignored -> {});
    final ListCraftingInventory waiting = new ListCraftingInventory(ignored -> {});
    final ECOExternalCpuFastPath dispatcher = new ECOExternalCpuFastPath(() -> {});
    final FastProvider provider = mock(FastProvider.class);
    final AtomicLong remaining = new AtomicLong(1024);
    final Map<IPatternDetails, Object> tasks = new LinkedHashMap<>();
    final UUID jobId = UUID.randomUUID();
    final List<ECOFastPathDispatchProvider.Batch> deliveries = new ArrayList<>();
    ECOExternalCpuJob job;
    boolean reject, uncertain, ordinaryFailure;

    void setup(String engine) {
        when(pattern.getDefinition()).thenReturn(AEItemKey.of(net.minecraft.world.item.Items.PAPER));
        var link = mock(CraftingLink.class);
        when(link.getCraftingID()).thenReturn(jobId);
        if (engine.equals("advancedae")) {
            var access = mock(AdvancedAeCraftingJobAccessor.class, CALLS_REAL_METHODS);
            var task = mock(AdvancedAeTaskProgressAccessor.class, CALLS_REAL_METHODS);
            when(task.neoecoae$getValue()).thenAnswer(c -> remaining.get());
            doAnswer(c -> { remaining.set(c.getArgument(0)); return null; })
                    .when(task).neoecoae$setValue(anyLong());
            tasks.put(pattern, task);
            doReturn(tasks).when(access).neoecoae$getTasks();
            when(access.neoecoae$getWaitingFor()).thenReturn(waiting);
            when(access.neoecoae$getLink()).thenReturn(link);
            var tracker = mock(net.pedroksl.advanced_ae.common.logic.ElapsedTimeTracker.class,
                    withSettings().extraInterfaces(AdvancedAeElapsedTimeTrackerInvoker.class));
            when(access.neoecoae$getTimeTracker()).thenReturn(tracker);
            job = access;
        } else {
            // OmniCell adds coprocessors to this AE2 engine, with the same native job type.
            var access = mock(Ae2CpuJobAccessor.class, CALLS_REAL_METHODS);
            var task = mock(Ae2CpuTaskAccessor.class);
            when(task.neoecoae$value()).thenAnswer(c -> remaining.get());
            doAnswer(c -> { remaining.set(c.getArgument(0)); return null; })
                    .when(task).neoecoae$value(anyLong());
            tasks.put(pattern, task);
            when(access.neoecoae$tasks()).thenReturn(tasks);
            when(access.neoecoae$waitingFor()).thenReturn(waiting);
            when(access.neoecoae$link()).thenReturn(link);
            var tracker = mock(appeng.crafting.execution.ElapsedTimeTracker.class,
                    withSettings().extraInterfaces(Ae2CpuTimeTrackerAccessor.class));
            when(access.neoecoae$timeTracker()).thenReturn(tracker);
            job = access;
        }
        when(job.neoecoae$finalOutput()).thenReturn(new GenericStack(output, 2048));
        inventory.insert(input, 2048, Actionable.MODULATE);
        when(service.getProviders(pattern)).thenReturn(List.of(provider));
        when(energy.extractAEPower(anyDouble(), any(), any())).thenAnswer(c -> c.getArgument(0));
        when(provider.eco$prepareFastPath(any())).thenAnswer(c -> {
            ECOBatchDispatchContext context = c.getArgument(0);
            assertEquals(jobId, context.craftingJobId());
            return new ECOFastPathDispatchProvider.Preparation(512, null, false, batch -> {
                deliveries.add(batch);
                if (uncertain) throw new ECOIndeterminateBatchException("unknown custody", null);
                if (ordinaryFailure) throw new IllegalStateException("not accepted");
                return !reject;
            });
        });
    }

    int dispatch(int pushSlots) {
        // Recipe extraction is stubbed; facade, planner, material lease, energy and accounting are real.
        try (var helper = mockStatic(CraftingCpuHelper.class)) {
            helper.when(() -> CraftingCpuHelper.extractPatternInputs(eq(pattern), any(), eq(level), any(), any()))
                    .thenAnswer(c -> {
                        ListCraftingInventory preview = c.getArgument(1);
                        if (preview.extract(input, 2, Actionable.MODULATE) != 2) return null;
                        ((KeyCounter) c.getArgument(3)).add(output, 2);
                        ((KeyCounter) c.getArgument(4)).add(container, 1);
                        var counter = new KeyCounter(); counter.add(input, 2);
                        return new KeyCounter[]{counter};
                    });
            helper.when(() -> CraftingCpuHelper.calculatePatternPower(any())).thenReturn(1.0);
            return dispatcher.execute(this, job, inventory, pushSlots, service, energy, level);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"ae2", "omnicell", "advancedae"})
    void onePushSlotFills512WorkerSlotsAndAccountsExactlyOnce(String engine) {
        setup(engine);
        assertEquals(1, dispatch(1));
        assertEquals(1, deliveries.size());
        assertEquals(512, deliveries.getFirst().craftCount());
        assertEquals(1024, deliveries.getFirst().inputTotal().getFirst().amount());
        assertEquals(1024, inventory.list.get(input));
        assertEquals(512, remaining.get());
        assertEquals(1024, waiting.list.get(output));
        assertEquals(512, waiting.list.get(container));
        assertEquals(1, dispatch(1));
        assertEquals(0, remaining.get());
        assertTrue(tasks.isEmpty());
        assertEquals(0, inventory.list.get(input));
        assertEquals(2048, waiting.list.get(output));
        verify(provider, never()).pushPattern(any(), any());
    }

    @Test void taskAndActualMaterialsBoundBatch() {
        setup("ae2"); remaining.set(200); inventory.list.set(input, 150);
        assertEquals(1, dispatch(1));
        assertEquals(75, deliveries.getFirst().craftCount());
        assertEquals(125, remaining.get());
        assertEquals(150, waiting.list.get(output));
    }

    @ParameterizedTest @ValueSource(strings = {"ae2", "omnicell", "advancedae"})
    void workstationParallelProviderUsesOneCpuPushAndKeepsJobId(String engine) {
        setup(engine);
        var workstation = mock(ParallelProvider.class);
        when(workstation.eco$getAvailableParallelSlots()).thenReturn(512);
        when(service.getProviders(pattern)).thenReturn(List.of(workstation));
        when(workstation.eco$pushPatternBatch(eq(pattern), any(), eq(512L), eq(jobId)))
                .thenAnswer(c -> {
                    KeyCounter[] totals = c.getArgument(1);
                    assertEquals(1024, totals[0].get(input));
                    totals[0].clear();
                    return true;
                });

        assertEquals(1, dispatch(1));
        assertEquals(512, remaining.get());
        assertEquals(1024, inventory.list.get(input));
        assertEquals(1024, waiting.list.get(output));
        assertEquals(512, waiting.list.get(container));
        verify(workstation).eco$pushPatternBatch(eq(pattern), any(), eq(512L), eq(jobId));
        verify(workstation, never()).pushPattern(any(), any());
    }

    @Test void rejectedWorkstationBatchLeavesExternalCpuInventoryUntouched() {
        setup("ae2");
        var workstation = mock(ParallelProvider.class);
        when(workstation.eco$getAvailableParallelSlots()).thenReturn(512);
        when(service.getProviders(pattern)).thenReturn(List.of(workstation));
        assertEquals(0, dispatch(1));
        assertEquals(2048, inventory.list.get(input));
        assertEquals(1024, remaining.get());
        assertTrue(waiting.list.isEmpty());
        verify(energy).injectPower(512, Actionable.MODULATE);
    }

    @Test void rejectionRestoresInputsAndEnergyWithoutChangingTask() {
        setup("ae2"); reject = true;
        assertEquals(0, dispatch(1));
        assertEquals(2048, inventory.list.get(input));
        assertEquals(1024, remaining.get());
        assertTrue(waiting.list.isEmpty());
        verify(energy).injectPower(512, Actionable.MODULATE);
    }

    @Test void uncertainOwnershipSuspendsWithoutRefundOrRedispatch() {
        setup("advancedae"); uncertain = true;
        assertThrows(ECOIndeterminateBatchException.class, () -> dispatch(1));
        verify(job).neoecoae$suspended(true);
        assertEquals(1024, inventory.list.get(input));
        verify(energy, never()).injectPower(anyDouble(), any());
        when(job.neoecoae$suspended()).thenReturn(true);
        assertEquals(0, dispatch(1));
        assertEquals(1, deliveries.size());
    }

    @Test void busyProviderAndZeroBudgetDoNotMoveInputs() {
        setup("ae2"); assertEquals(0, dispatch(0));
        when(provider.isBusy()).thenReturn(true);
        assertEquals(0, dispatch(1)); assertTrue(deliveries.isEmpty());
        assertEquals(2048, inventory.list.get(input));
    }

    @Test void waitingOverflowNeverTransfersBatch() {
        setup("ae2"); waiting.list.set(output, Long.MAX_VALUE - 1);
        assertEquals(0, dispatch(1)); assertTrue(deliveries.isEmpty());
        assertEquals(2048, inventory.list.get(input));
    }

    @Test void ordinaryProviderFailureRollsBackAndAllowsFallback() {
        setup("advancedae"); ordinaryFailure = true;
        assertEquals(0, dispatch(1));
        assertEquals(2048, inventory.list.get(input));
        assertEquals(1024, remaining.get());
        assertTrue(waiting.list.isEmpty());
        verify(job, never()).neoecoae$suspended(true);
        verify(energy).injectPower(512, Actionable.MODULATE);
    }

    @Test void failedRefundSurvivesSaveLoadAndRefundsWhileIdle() {
        setup("ae2"); reject = true;
        when(energy.injectPower(anyDouble(), any())).thenAnswer(c -> c.getArgument(0));
        assertEquals(0, dispatch(1));
        var tag = new net.minecraft.nbt.CompoundTag();
        dispatcher.write(tag);
        assertEquals("512", tag.getString("prepaidEnergyCreditExact"));
        var restored = new ECOExternalCpuFastPath(() -> {});
        restored.read(tag);
        when(energy.injectPower(anyDouble(), any())).thenReturn(0.0);
        restored.refundIdleCredit(energy);
        restored.write(tag);
        assertFalse(tag.contains("prepaidEnergyCreditExact"));
        verify(energy, times(2)).injectPower(512, Actionable.MODULATE);
    }

    @Test void energyDisappearingAfterSimulationDoesNotTransferInputs() {
        setup("ae2");
        when(energy.extractAEPower(anyDouble(), eq(Actionable.MODULATE), any())).thenReturn(0.0);
        assertEquals(0, dispatch(1));
        assertTrue(deliveries.isEmpty());
        assertEquals(2048, inventory.list.get(input));
        assertEquals(1024, remaining.get());
        assertTrue(waiting.list.isEmpty());
    }

    @Test void accountingFailureAfterAcceptanceNeverRefundsMaterials() {
        setup("advancedae");
        doThrow(new IllegalStateException("tracker failed")).when(job).neoecoae$addRemainderItems(anyLong(), any());
        assertThrows(IllegalStateException.class, () -> dispatch(1));
        assertEquals(1, deliveries.size());
        assertEquals(1024, inventory.list.get(input));
        assertEquals(512, remaining.get());
        verify(job).neoecoae$suspended(true);
        verify(energy, never()).injectPower(anyDouble(), any());
    }

    @Test void bridgedProviderIsFilteredOutForExternalCpu() {
        setup("advancedae");
        // 创建一个只实现ICraftingProvider但不实现ECOFastPathDispatchProvider的桥接provider
        var bridgedProvider = mock(ICraftingProvider.class);
        when(service.getProviders(pattern)).thenReturn(List.of(bridgedProvider, provider));

        // 桥接provider不应被调用，只有真正的ECO provider会被使用
        assertEquals(1, dispatch(1));
        assertEquals(1, deliveries.size());
        assertEquals(512, deliveries.getFirst().craftCount());

        // 验证桥接provider完全没有被调用
        verify(bridgedProvider, never()).isBusy();
        verify(bridgedProvider, never()).pushPattern(any(), any());
    }

    @Test void onlyEcoNativeProvidersAreUsedForBatchDispatch() {
        setup("advancedae");
        // ExtendedAEPlus矩阵和Useless熔炉会通过桥接适配器返回，但它们不是ECOFastPathDispatchProvider实例
        var nonEcoProvider1 = mock(ICraftingProvider.class);
        var nonEcoProvider2 = mock(ICraftingProvider.class);
        when(service.getProviders(pattern)).thenReturn(List.of(nonEcoProvider1, nonEcoProvider2, provider));

        // 只有ECO原生provider（provider）应该被使用
        assertEquals(1, dispatch(1));
        assertEquals(1, deliveries.size());

        // 非ECO providers完全被跳过
        verify(nonEcoProvider1, never()).isBusy();
        verify(nonEcoProvider2, never()).isBusy();
    }
}
