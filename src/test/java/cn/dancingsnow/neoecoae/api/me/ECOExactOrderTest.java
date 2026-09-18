package cn.dancingsnow.neoecoae.api.me;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.crafting.CraftingPlan;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.CraftingCpuHelper;
import cn.dancingsnow.neoecoae.impl.crafting.ECOExactCraftingPlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ECOBigOrderPlanner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.*;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerAmount;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.ECOPlanTrace;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import org.junit.jupiter.api.Test;

class ECOExactOrderTest {
    private static final BigInteger HUGE = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TEN);

    @Test void completeOrderIsSubmittedOnceAndAllRecipesRemainInTheSameLedger() {
        var a = mock(AEKey.class, RETURNS_DEEP_STUBS);
        var b = mock(AEKey.class, RETURNS_DEEP_STUBS);
        var first = pattern(a);
        var second = pattern(b);
        var result = result(b, Map.of(first, PlannerAmount.of(HUGE), second, PlannerAmount.of(7)));
        var cpu = mock(ECOCraftingCPU.class);
        var grid = mock(IGrid.class, RETURNS_DEEP_STUBS);
        when(cpu.isActive()).thenReturn(true);
        when(cpu.getGrid()).thenReturn(grid);
        var logic = new ECOCraftingCPULogic(cpu);
        try (var types = mockStatic(AEKeyTypes.class);
             var helpers = mockStatic(CraftingCpuHelper.class, CALLS_REAL_METHODS);
             var planner = mockStatic(ECOBigOrderPlanner.class)) {
            helpers.when(() -> CraftingCpuHelper.tryExtractInitialItems(any(), any(), any(), any())).thenReturn(null);
            var plan = new ECOExactCraftingPlan(result, false);
            assertTrue(logic.trySubmitJob(grid, plan, IActionSource.empty(), null).successful());
            var job = logic.getJob();
            assertEquals(2, job.tasks.size());
            assertNull(logic.bigOrder.progress());
            assertFalse(logic.bigOrder.tick());
            assertEquals(HUGE, logic.getExactPendingPreview().get(a));
            job.tasks.get(first).accept(Long.MAX_VALUE);
            job.tasks.get(second).accept(3);
            assertSame(job, logic.getJob());
            assertEquals(HUGE.subtract(BigInteger.valueOf(Long.MAX_VALUE)), logic.getExactPendingPreview().get(a));
            assertEquals(BigInteger.valueOf(4), logic.getExactPendingPreview().get(b));
            assertFalse(job.link.isDone());
            planner.verifyNoInteractions();
            logic.cancel();
            assertFalse(logic.hasJob());
            assertTrue(job.link.isCanceled());
        }
    }

    @Test void countersCrossLongBoundaryWithoutFinishingOrRounding() {
        var progress = new ExecutingCraftingJob.TaskProgress();
        var total = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(3));
        progress.setExact(total, total);
        progress.accept(Long.MAX_VALUE - 1);
        assertEquals(4, progress.value);
        assertEquals(BigInteger.valueOf(4), progress.remainingExact());
        progress.accept(3);
        assertEquals(BigInteger.ONE, progress.remainingExact());
        assertThrows(IllegalArgumentException.class, () -> progress.accept(2));
        progress.accept(1);
        assertEquals(0, progress.value);
    }

    @Test void missingInputWaitsAndRefillsOnlyTheAcceptedQuantityWithoutReplanning() {
        var input = mock(AEKey.class, RETURNS_DEEP_STUBS);
        var output = mock(AEKey.class, RETURNS_DEEP_STUBS);
        var pattern = pattern(output);
        var result = result(output, Map.of(pattern, PlannerAmount.of(10)));
        result.setExactMaterials(Map.of(), Map.of(), Map.of(input, PlannerAmount.of(9)));
        var cpu = mock(ECOCraftingCPU.class);
        var grid = mock(IGrid.class, RETURNS_DEEP_STUBS);
        when(cpu.isActive()).thenReturn(true);
        when(cpu.getGrid()).thenReturn(grid);
        when(cpu.getActionSource()).thenReturn(IActionSource.empty());
        when(grid.getStorageService().getInventory().extract(eq(input), anyLong(),
            eq(appeng.api.config.Actionable.MODULATE), any())).thenReturn(0L, 4L, 5L);
        var logic = new ECOCraftingCPULogic(cpu);
        try (var types = mockStatic(AEKeyTypes.class);
             var helpers = mockStatic(CraftingCpuHelper.class, CALLS_REAL_METHODS);
             var planner = mockStatic(ECOBigOrderPlanner.class)) {
            helpers.when(() -> CraftingCpuHelper.tryExtractInitialItems(any(), any(), any(), any())).thenReturn(null);
            assertTrue(logic.trySubmitJob(grid, new ECOExactCraftingPlan(result, true), IActionSource.empty(), null).successful());
            var job = logic.getJob();
            logic.tickCraftingLogic(null, null);
            assertEquals(BigInteger.valueOf(9), job.deferredStock.get(input));
            logic.tickCraftingLogic(null, null);
            assertEquals(BigInteger.valueOf(5), job.deferredStock.get(input));
            assertEquals(4, logic.getInventory().list.get(input));
            logic.tickCraftingLogic(null, null);
            assertTrue(job.deferredStock.isEmpty());
            assertEquals(9, logic.getInventory().list.get(input));
            assertSame(job, logic.getJob());
            planner.verifyNoInteractions();
        }
    }

    @Test void readyDagBranchesPipelineWhileCycleBarrierRemains() {
        var a = pattern(mock(AEKey.class));
        var b = pattern(mock(AEKey.class));
        var c = pattern(mock(AEKey.class));
        var tasks = List.of(task(0, a), task(1, b), task(2, c));
        var phases = List.of(
            new ECOExecutionPlan.PhaseSpec(0, 0, ECOExecutionSchedule.Type.DAG, List.of(0), List.of(), List.of()),
            new ECOExecutionPlan.PhaseSpec(1, 1, ECOExecutionSchedule.Type.DAG, List.of(1), List.of(), List.of(0)),
            new ECOExecutionPlan.PhaseSpec(2, 2, ECOExecutionSchedule.Type.DAG, List.of(2), List.of(), List.of()));
        var signature = new PlanIdentity.Signature(mock(AEKey.class), 1, Map.of(), Map.of(), Map.of(), Map.of());
        var plan = new ECOExecutionPlan(signature, ExecutionMode.PHASED_DAG, tasks, phases, new ECOExecutionSchedule(List.of()));
        var progress = new ExecutingCraftingJob.TaskProgress[3];
        for (int i = 0; i < 3; i++) {
            progress[i] = new ExecutingCraftingJob.TaskProgress();
            progress[i].setExact(HUGE, HUGE);
        }
        var runtime = new ECOExecutionRuntime(plan, Map.of(0, a, 1, b, 2, c), progress);
        assertEquals(Set.of(a, b, c), new HashSet<>(runtime.candidates().stream().map(ECOExecutionRuntime.DispatchCandidate::pattern).toList()));
        assertFalse(runtime.isComplete());
        var cyclePhases = new ArrayList<>(phases);
        cyclePhases.set(0, new ECOExecutionPlan.PhaseSpec(0, 0, ECOExecutionSchedule.Type.CYCLE,
            List.of(0), List.of(new ECOExecutionPlan.ExecutionStep(0, 1)), List.of()));
        var cyclePlan = new ECOExecutionPlan(signature, ExecutionMode.ORDERED_CYCLE, tasks, cyclePhases, new ECOExecutionSchedule(List.of()));
        var cycleRuntime = new ECOExecutionRuntime(cyclePlan, Map.of(0, a, 1, b, 2, c), progress);
        assertEquals(Set.of(a, c), new HashSet<>(cycleRuntime.candidates().stream().map(ECOExecutionRuntime.DispatchCandidate::pattern).toList()));
    }

    @Test void checkpointKeepsExactTaskAndDeferredMaterials() {
        cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize();
        var key = mock(AEKey.class, RETURNS_DEEP_STUBS);
        var pattern = pattern(key);
        var definition = mock(AEItemKey.class);
        when(pattern.getDefinition()).thenReturn(definition);
        var registries = mock(HolderLookup.Provider.class);
        when(definition.toTag(registries)).thenAnswer(i -> new CompoundTag());
        var result = result(key, Map.of(pattern, PlannerAmount.of(HUGE)));
        result.setExactMaterials(Map.of(key, PlannerAmount.of(HUGE)), Map.of(), Map.of());
        var cpu = mock(ECOCraftingCPU.class);
        var logic = new ECOCraftingCPULogic(cpu);
        try (var types = mockStatic(AEKeyTypes.class);
             var definitions = mockStatic(AEItemKey.class);
             var patterns = mockStatic(appeng.api.crafting.PatternDetailsHelper.class);
             var stacks = mockStatic(GenericStack.class, CALLS_REAL_METHODS)) {
            definitions.when(() -> AEItemKey.fromTag(eq(registries), any())).thenReturn(definition);
            patterns.when(() -> appeng.api.crafting.PatternDetailsHelper.decodePattern(eq(definition), any())).thenReturn(pattern);
            stacks.when(() -> GenericStack.writeTag(eq(registries), any())).thenReturn(new CompoundTag());
            stacks.when(() -> GenericStack.readTag(eq(registries), any())).thenReturn(new GenericStack(key, 10));
            var link = new CraftingLink(CraftingCpuHelper.generateLinkData(UUID.randomUUID(), true, false), cpu);
            var plan = new ECOExactCraftingPlan(result, false);
            var job = new ExecutingCraftingJob(plan, ignored -> {}, link, null);
            job.tasks.get(pattern).accept(37);
            var restored = new ExecutingCraftingJob(job.writeToNBT(registries), registries, ignored -> {}, logic);
            assertTrue(restored.exactOrder);
            assertEquals(HUGE.subtract(BigInteger.valueOf(37)), restored.tasks.get(pattern).remainingExact());
            assertEquals(HUGE, restored.deferredStock.get(key));
            assertEquals(link.getCraftingID(), restored.link.getCraftingID());
        }
    }

    private static ECOExecutionPlan.TaskSpec task(int id, IPatternDetails pattern) {
        return new ECOExecutionPlan.TaskSpec(id, PlanIdentity.patternIdentityFor(pattern), pattern,
            ECOExecutionPlan.PatternRuntimeInfo.from(pattern), Long.MAX_VALUE, id, ECOExecutionPlan.TaskKind.DAG);
    }
    private static IPatternDetails pattern(AEKey output) {
        var pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
        when(pattern.getOutputs()).thenReturn(List.of(new GenericStack(output, 1)));
        return pattern;
    }
    private static ECOPlanningResult result(AEKey key, Map<IPatternDetails, PlannerAmount> counts) {
        var shell = new CraftingPlan(new GenericStack(key, 10), 0, true, false,
            new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of());
        var result = new ECOPlanningResult(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE,
            shell, new ECOPlanTrace(), List.of(), 0);
        result.setExactPatternTimes(counts);
        result.setTheoreticalBytes(PlannerAmount.of(HUGE));
        return result;
    }
}
