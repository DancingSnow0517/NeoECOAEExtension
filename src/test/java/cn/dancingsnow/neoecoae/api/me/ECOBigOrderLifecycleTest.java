package cn.dancingsnow.neoecoae.api.me;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import appeng.crafting.execution.CraftingCpuHelper;
import cn.dancingsnow.neoecoae.api.me.bigorder.*;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.api.me.lifecycle.*;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ECOBigOrderPlanner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.*;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.ECOPlanTrace;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import org.junit.jupiter.api.Test;

class ECOBigOrderLifecycleTest {
    @Test void childrenShareOneLinkAndOnlyTheParentFiresCompletion() {
        var cpu = mock(ECOCraftingCPU.class);
        var grid = mock(IGrid.class, RETURNS_DEEP_STUBS);
        var service = mock(appeng.api.networking.crafting.ICraftingService.class,
            withSettings().extraInterfaces(cn.dancingsnow.neoecoae.api.me.provider.ECOCraftingProviderRevision.class));
        when(grid.getCraftingService()).thenReturn(service);
        var revision = (cn.dancingsnow.neoecoae.api.me.provider.ECOCraftingProviderRevision) service;
        when(revision.neoecoae$getProviderRevision()).thenReturn(1L, 2L, 3L, 4L);
        var cluster = mock(NEComputationCluster.class);
        when(cpu.isActive()).thenReturn(true);
        when(cpu.getGrid()).thenReturn(grid);
        when(cpu.getCluster()).thenReturn(cluster);
        when(cpu.getAvailableStorage()).thenReturn(100L);
        when(cluster.replaceBigOrderPlan(eq(cpu), any())).thenReturn(true);
        var key = mock(AEKey.class, RETURNS_DEEP_STUBS);
        var options = new ECOPlannerOptions(false, false, Set.of());
        var previewKey = mock(AEKey.class, RETURNS_DEEP_STUBS);
        var admission = new ECOBigOrderRequest(key, BigInteger.TEN, true, options,
            Map.of(previewKey, BigInteger.TEN.pow(25)));
        var logic = new ECOCraftingCPULogic(cpu);
        var listener = mock(ECOCraftingLifecycleListener.class);
        ECOCraftingLifecycle.register(listener);
        try (var keyTypes = mockStatic(appeng.api.stacks.AEKeyTypes.class);
             var planner = mockStatic(ECOBigOrderPlanner.class);
             var helpers = mockStatic(CraftingCpuHelper.class, CALLS_REAL_METHODS)) {
            helpers.when(() -> CraftingCpuHelper.tryExtractInitialItems(any(), any(), any(), any())).thenReturn(null);
            var first = new CraftingPlan(new GenericStack(key, 6), 1, false, false,
                new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of());
            var second = new CraftingPlan(new GenericStack(key, 4), 1, false, false,
                new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of());
            planner.when(() -> ECOBigOrderPlanner.begin(eq(grid), eq(key), anyLong(), eq(100L), eq(options)))
                .thenReturn(CompletableFuture.completedFuture(answer(first)),
                    CompletableFuture.completedFuture(answer(second)));
            assertTrue(admission.submit(carrier -> logic.trySubmitJob(grid, carrier, IActionSource.empty(), null)).successful());
            var link = logic.getLastLink();
            var menuItems = new KeyCounter();
            logic.getAllItems(menuItems);
            assertEquals(Long.MAX_VALUE, menuItems.get(previewKey));
            assertEquals(Long.MAX_VALUE, logic.getPendingOutputs(previewKey));
            assertEquals(BigInteger.TEN.pow(25), logic.getExactPendingPreview().get(previewKey));
            assertEquals(10, logic.getPendingOutputs(key));
            assertEquals(0, logic.getWaitingFor(previewKey), "Forecast is not dispatched work");
            var changes = new HashSet<AEKey>();
            logic.addListener(changes::add);
            logic.bigOrder.tick();
            logic.bigOrder.tick();
            assertEquals(0, logic.getPendingOutputs(previewKey));
            assertTrue(logic.getExactPendingPreview().isEmpty());
            assertTrue(changes.contains(previewKey), "Forecast removal must reach an already-open menu");
            assertEquals(6, logic.getJob().finalOutput.amount());
            assertSame(link, logic.getLastLink());
            logic.getJob().remainingAmount = 0;
            logic.finishJob(true);
            assertFalse(link.isDone());
            assertFalse(link.isCanceled());
            assertEquals(BigInteger.valueOf(6), logic.getProgressView().bigOrder().orElseThrow().completed());
            assertEquals(4, logic.getPendingOutputs(key), "Between segments the remaining goal remains visible");
            verify(listener, never()).onJobFinished(any(), any());
            logic.bigOrder.tick();
            logic.bigOrder.tick();
            assertSame(link, logic.getLastLink());
            logic.getJob().remainingAmount = 0;
            logic.finishJob(true);
            assertTrue(link.isDone());
            assertFalse(logic.hasJob());
            verify(listener, times(1)).onJobStarted(any());
            verify(listener, times(1)).onJobFinished(any(), any());
        } finally { ECOCraftingLifecycle.unregister(listener); }
    }

    @Test void waitingParentCheckpointRestoresTheSameLinkAndCanBeCancelled() {
        var cpu = mock(ECOCraftingCPU.class);
        var grid = mock(IGrid.class, RETURNS_DEEP_STUBS);
        when(cpu.isActive()).thenReturn(true);
        when(cpu.getGrid()).thenReturn(grid);
        var key = mock(AEKey.class, RETURNS_DEEP_STUBS);
        var logic = new ECOCraftingCPULogic(cpu);
        var admission = new ECOBigOrderRequest(key, BigInteger.TEN, true,
            new ECOPlannerOptions(true, false, Set.of()));
        try (var keyTypes = mockStatic(appeng.api.stacks.AEKeyTypes.class);
             var helpers = mockStatic(CraftingCpuHelper.class, CALLS_REAL_METHODS);
             var codec = mockStatic(GenericStack.class, CALLS_REAL_METHODS)) {
            helpers.when(() -> CraftingCpuHelper.tryExtractInitialItems(any(), any(), any(), any())).thenReturn(null);
            var registries = mock(HolderLookup.Provider.class);
            codec.when(() -> GenericStack.writeTag(eq(registries), any())).thenReturn(new CompoundTag());
            codec.when(() -> GenericStack.readTag(eq(registries), any())).thenReturn(new GenericStack(key, 1));
            assertTrue(admission.submit(carrier -> logic.trySubmitJob(grid, carrier, IActionSource.empty(), null)).successful());
            var link = logic.getLastLink();
            var saved = new CompoundTag();
            logic.bigOrder.write(saved, registries);
            logic.bigOrder.read(saved, registries);
            assertEquals(link.getCraftingID(), logic.getProgressView().bigOrder().orElseThrow().orderId());
            logic.cancel();
            assertTrue(link.isCanceled());
            assertFalse(logic.hasJob());
        }
    }

    private static ECOBigOrderPlanner.Answer answer(CraftingPlan plan) {
        return new ECOBigOrderPlanner.Answer(new ECOPlanningResult(PlanningStatus.SUCCESS, plan,
            new ECOPlanTrace(), List.of(), 0), false, false);
    }

    @Test void changedRevisionStillRejectsRemovedPatterns() {
        var service = mock(appeng.api.networking.crafting.ICraftingService.class);
        var pattern = mock(appeng.api.crafting.IPatternDetails.class);
        var key = mock(AEKey.class);
        when(pattern.getOutputs()).thenReturn(List.of(new GenericStack(key, 1)));
        var plan = new CraftingPlan(new GenericStack(key, 1), 1, false, false,
            new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of(pattern, 1L));
        when(service.getCraftingFor(key)).thenReturn(List.of(pattern));
        assertTrue(ECOBigOrderController.patternsStillAvailable(service, answer(plan).result()));
        when(service.getCraftingFor(key)).thenReturn(List.of());
        assertFalse(ECOBigOrderController.patternsStillAvailable(service, answer(plan).result()));
    }
}
