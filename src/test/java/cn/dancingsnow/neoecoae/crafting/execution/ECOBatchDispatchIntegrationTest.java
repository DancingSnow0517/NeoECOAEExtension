package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProvider;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class ECOBatchDispatchIntegrationTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize();
    }
    interface Parallel extends ICraftingProvider, ECOParallelCraftingProvider {}

    final AEKey input = mock(AEKey.class, RETURNS_DEEP_STUBS);
    final AEKey output = mock(AEKey.class, RETURNS_DEEP_STUBS);
    final IPatternDetails pattern = mock(IPatternDetails.class, RETURNS_DEEP_STUBS);
    final IEnergyService energy = mock(IEnergyService.class);
    final ListCraftingInventory inventory = new ListCraftingInventory(ignored -> {});
    final ECOCraftingDispatchAccounting accounting = mock(ECOCraftingDispatchAccounting.class);
    final ECOCraftingProviderDispatcher dispatcher = new ECOCraftingProviderDispatcher(null,
            mock(ECOCraftingFastPathDispatcher.class), new ECOCraftingEnergyTransaction(() -> {}, () -> 0), accounting);
    final ECOCraftingDispatchRequest request;

    ECOBatchDispatchIntegrationTest() {
        var inputs = new KeyCounter(); inputs.add(input, 2);
        var outputs = new KeyCounter(); outputs.add(output, 3);
        when(pattern.getOutputs()).thenReturn(List.of(new GenericStack(output, 3)));
        var plan = mock(ICraftingPlan.class);
        when(plan.finalOutput()).thenReturn(new GenericStack(output, 30));
        when(plan.emittedItems()).thenReturn(new KeyCounter());
        var link = mock(CraftingLink.class);
        when(link.getCraftingID()).thenReturn(UUID.randomUUID());
        ExecutingCraftingJob job;
        try (var trackers = mockConstruction(ElapsedTimeTracker.class);
             var runtimes = mockConstruction(ECOExecutionRuntime.class)) {
            job = new ExecutingCraftingJob(plan,
                    mock(cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlan.class),
                    ignored -> {}, link, null);
        }
        request = new ECOCraftingDispatchRequest(job, null, pattern, new KeyCounter[]{inputs}, outputs,
                new KeyCounter(), 10, inventory, mock(Level.class));
        inventory.insert(input, 100, Actionable.MODULATE);
        when(energy.extractAEPower(anyDouble(), any(), any())).thenAnswer(call -> call.getArgument(0));
    }

    ECOCraftingProviderDispatcher.Result dispatch(List<ICraftingProvider> providers,
            ECOCraftingProviderDispatcher.ECOCraftingNormalPush push) {
        try (var helpers = mockStatic(CraftingCpuHelper.class)) {
            helpers.when(() -> CraftingCpuHelper.calculatePatternPower(any())).thenReturn(1.0);
            return dispatcher.dispatchCandidate(request, providers, new ECOCraftingDispatchBudget(64, 64),
                    energy, mock(ECODispatchStallDiagnostics.class), ignored -> {}, () -> {}, push);
        }
    }

    @Test void singleCopyAdapterUsesIsolatedMaterializedCountersAcrossRejection() {
        var first = mock(ICraftingProvider.class);
        var second = mock(ICraftingProvider.class);
        var result = dispatch(List.of(first, second), (attempt, provider) -> {
            assertEquals(98, inventory.list.get(input));
            assertEquals(2, attempt.inputs()[0].get(input));
            attempt.inputs()[0].clear();
            return provider == second;
        });
        assertTrue(result.accepted());
        assertEquals(1, result.acceptedCrafts());
        assertEquals(98, inventory.list.get(input));
        assertEquals(2, request.inputs()[0].get(input));
        verify(accounting).apply(eq(request), argThat(r -> r.acceptedCrafts() == 1), any(), eq(second));
        verify(energy).injectPower(1.0, Actionable.MODULATE);
    }

    @Test void parallelAdapterReceivesOnePlannedBatchAndAccountsItsTotals() {
        var provider = mock(Parallel.class);
        when(provider.eco$getAvailableParallelSlots()).thenReturn(4);
        when(provider.eco$pushPatternBatch(eq(pattern), any(), eq(4L), any())).thenAnswer(call -> {
            KeyCounter[] counters = call.getArgument(1);
            assertEquals(8, counters[0].get(input));
            assertEquals(92, inventory.list.get(input));
            counters[0].clear();
            return true;
        });
        var result = dispatch(List.of(provider), (attempt, target) -> fail("Must not repeat accepted batch"));
        assertEquals(4, result.acceptedCrafts());
        assertEquals(92, inventory.list.get(input));
        assertEquals(2, request.inputs()[0].get(input));
        verify(accounting).apply(eq(request), argThat(r -> r.acceptedCrafts() == 4
                && r.outputs().getFirst().amount() == 12), any(), eq(provider));
    }

    @Test void uncertainSinglePushSuspendsWithoutTryingTheNextProvider() {
        var provider = mock(ICraftingProvider.class);
        int[] calls = {0};
        var result = dispatch(List.of(provider, mock(ICraftingProvider.class)), (attempt, target) -> {
            calls[0]++;
            throw new IllegalStateException("Ownership unknown");
        });
        assertFalse(result.accepted());
        assertTrue(request.job().suspended);
        assertEquals(1, calls[0]);
        assertEquals(98, inventory.list.get(input));
        verifyNoInteractions(accounting);
        verify(energy, never()).injectPower(anyDouble(), any());
    }

    @Test void everyLinearLaneRespectsWaitingHeadroomAndOtherPhasesSeeds() {
        request.job().waitingFor.insert(output, Long.MAX_VALUE - 6, Actionable.MODULATE);
        var provider = mock(ICraftingProvider.class);
        var plan = ECOBatchDispatchPlanning.plan(request, provider, 100, 100, 1, energy, ECOBatchMode.LINEAR);
        assertEquals(2, plan.craftCount());
        when(request.job().executionRuntime.protectedStartupSeed(null)).thenReturn(Map.of(input, 98L));
        plan = ECOBatchDispatchPlanning.plan(request, provider, 100, 100, 1, energy, ECOBatchMode.LINEAR);
        assertEquals(1, plan.craftCount());
        when(request.job().executionRuntime.protectedStartupSeed(null)).thenReturn(Map.of(input, 100L));
        assertNull(ECOBatchDispatchPlanning.plan(request, provider, 100, 100, 1, energy, ECOBatchMode.LINEAR));
        assertEquals(100, inventory.list.get(input));
        verify(energy, never()).extractAEPower(anyDouble(), eq(Actionable.MODULATE), any());
    }

    @Test void preparedPlanAggregatesRepeatedInputsBeforeBoundingMaterials() {
        inventory.extract(input, 94, Actionable.MODULATE);
        var repeated = new ECOCraftingDispatchRequest(request.job(), null, pattern,
                new KeyCounter[]{request.inputs()[0], request.inputs()[0]}, request.outputs(), request.remainders(),
                10, inventory, request.level());
        var prepared = ECOBatchDispatchPlanning.prepare(repeated, mock(ICraftingProvider.class));
        assertEquals(1, prepared.plan(10, 10, 1, energy, ECOBatchMode.LINEAR).craftCount());
        inventory.insert(input, 2, Actionable.MODULATE);
        assertEquals(2, prepared.plan(10, 10, 1, energy, ECOBatchMode.LINEAR).craftCount());
        when(request.job().executionRuntime.protectedStartupSeed(null)).thenReturn(Map.of(input, 6L));
        assertNull(prepared.plan(10, 10, 1, energy, ECOBatchMode.LINEAR));
    }

    @Test void preparedPlanRefreshesEnergyAndWaitingHeadroom() {
        var prepared = ECOBatchDispatchPlanning.prepare(request, mock(ICraftingProvider.class));
        assertEquals(10, prepared.plan(10, 10, 1, energy, ECOBatchMode.LINEAR).craftCount());
        when(energy.extractAEPower(anyDouble(), any(), any())).thenAnswer(call -> Math.min(4.0, call.getArgument(0)));
        assertEquals(4, prepared.plan(10, 10, 1, energy, ECOBatchMode.LINEAR).craftCount());
        request.job().waitingFor.insert(output, Long.MAX_VALUE - 6, Actionable.MODULATE);
        assertEquals(2, prepared.plan(10, 10, 1, energy, ECOBatchMode.LINEAR).craftCount());
        request.job().waitingFor.insert(output, 6, Actionable.MODULATE);
        assertNull(prepared.plan(10, 10, 1, energy, ECOBatchMode.LINEAR));
    }

    @Test void exactExcessStockRemainsAvailableWithProtectedSeeds() {
        var exact = new cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory(ignored -> {});
        exact.setEnabled(true);
        exact.restore(Map.of(input, java.math.BigInteger.valueOf(Long.MAX_VALUE).add(java.math.BigInteger.TEN)));
        var exactRequest = new ECOCraftingDispatchRequest(request.job(), null, pattern, request.inputs(),
                request.outputs(), request.remainders(), 10, exact, request.level());
        var prepared = ECOBatchDispatchPlanning.prepare(exactRequest, mock(ICraftingProvider.class));
        assertEquals(10, prepared.plan(10, 10, 1, energy, ECOBatchMode.LINEAR).craftCount());
        when(request.job().executionRuntime.protectedStartupSeed(null)).thenReturn(Map.of(input, Long.MAX_VALUE));
        assertEquals(5, prepared.plan(10, 10, 1, energy, ECOBatchMode.LINEAR).craftCount());
    }
}
