package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionMode;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ECOExecutionRuntimeStartupSeedTest {
    @BeforeAll
    static void bootstrap() {
        cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize();
    }

    @Test
    void protectedSeedSnapshotTracksConsumptionAndPhaseRelease() {
        AEKey seed = AEFluidKey.of(Fluids.WATER);
        var first = pattern(mock(AEKey.class));
        var second = pattern(mock(AEKey.class));
        var setup = runtime(first, second, seed);
        var runtime = setup.runtime();
        var firstCandidate = new ECOExecutionRuntime.DispatchCandidate(0, 0, first, 2L, true);
        var secondCandidate = new ECOExecutionRuntime.DispatchCandidate(1, 1, second, 2L, true);

        assertEquals(Map.of(seed, 12L), runtime.protectedStartupSeed(null));
        assertEquals(Map.of(seed, 7L), runtime.protectedStartupSeed(firstCandidate));
        assertEquals(Map.of(seed, 5L), runtime.protectedStartupSeed(secondCandidate));
        // Dispatch asks for the same snapshot once per candidate, so it must not be rebuilt while nothing moved.
        assertSame(runtime.protectedStartupSeed(firstCandidate), runtime.protectedStartupSeed(firstCandidate));

        var inputs = new KeyCounter();
        inputs.add(seed, 1L);
        setup.progress(0).accept(1L);
        runtime.onAccepted(firstCandidate, 1L, new KeyCounter[] {inputs});
        assertEquals(Map.of(seed, 11L), runtime.protectedStartupSeed(null));
        assertEquals(Map.of(seed, 7L), runtime.protectedStartupSeed(firstCandidate));
        assertEquals(Map.of(seed, 4L), runtime.protectedStartupSeed(secondCandidate));

        // The final step completes phase 0, which releases whatever seed it still owned.
        setup.progress(0).accept(1L);
        runtime.onAccepted(firstCandidate, 1L, new KeyCounter[0]);
        assertEquals(Map.of(seed, 7L), runtime.protectedStartupSeed(null));
        assertEquals(Map.of(), runtime.protectedStartupSeed(secondCandidate));
    }

    @Test
    void duplicateInputsAreSummedAgainstUnprotectedInventory() {
        AEKey seed = AEFluidKey.of(Fluids.WATER);
        AEKey other = AEFluidKey.of(Fluids.LAVA);
        var first = pattern(mock(AEKey.class));
        var second = pattern(mock(AEKey.class));
        var runtime = runtime(first, second, seed).runtime();
        var firstCandidate = new ECOExecutionRuntime.DispatchCandidate(0, 0, first, 2L, true);
        var inventory = new ListCraftingInventory(ignored -> {});
        inventory.list.add(seed, 10L);
        inventory.list.add(other, 1L);

        // Phase 1 protects 7 of the 10, so phase 0 may take 3 in total across duplicate entries.
        assertTrue(runtime.preservesStartupSeeds(firstCandidate,
            List.of(new GenericStack(seed, 2L), new GenericStack(other, 1L), new GenericStack(seed, 1L)), inventory));
        assertFalse(runtime.preservesStartupSeeds(firstCandidate,
            List.of(new GenericStack(seed, 2L), new GenericStack(seed, 2L)), inventory));
        assertFalse(runtime.preservesStartupSeeds(firstCandidate,
            List.of(new GenericStack(other, 1L), new GenericStack(other, 1L)), inventory));
    }

    @Test
    void dynamicCycleProtectsSingleSeedFromTerminalConsumer() {
        AEKey seed = AEFluidKey.of(Fluids.WATER);
        AEKey product = AEFluidKey.of(Fluids.LAVA);
        var consumer = pattern(seed, product, 1L);
        var producer = pattern(seed, seed, 2L);
        var tasks = List.of(task(0, consumer, 1L), task(1, producer, 1L));
        var phase = new ECOExecutionPlan.PhaseSpec(0, 0, ECOExecutionSchedule.Type.DYNAMIC_CYCLE,
            List.of(0, 1), List.of(), List.of(), Map.of(0, 1L, 1, 1L), Map.of(seed, 1L));
        var plan = new ECOExecutionPlan(new PlanIdentity.Signature(product, 1, Map.of(), Map.of(), Map.of(), Map.of()),
            ExecutionMode.DYNAMIC_CYCLE, tasks, List.of(phase), new ECOExecutionSchedule(List.of()));
        var progress = new ExecutingCraftingJob.TaskProgress[] {new ExecutingCraftingJob.TaskProgress(),
            new ExecutingCraftingJob.TaskProgress()};
        progress[0].setExact(BigInteger.ONE, BigInteger.ONE);
        progress[1].setExact(BigInteger.ONE, BigInteger.ONE);
        var runtime = new ECOExecutionRuntime(plan, Map.of(0, consumer, 1, producer), progress);

        var candidates = runtime.candidates();
        assertEquals(0, candidates.getFirst().taskId(), "fixture must put the consumer first");
        assertEquals(Map.of(seed, 1L), runtime.protectedStartupSeed(candidates.getFirst()));
        assertEquals(Map.of(), runtime.protectedStartupSeed(candidates.get(1)));

        var inventory = new ListCraftingInventory(ignored -> {});
        inventory.insert(seed, 1L, Actionable.MODULATE);
        var blocked = new ECOCraftingInputPreview(inventory, consumer,
            runtime.protectedStartupSeed(candidates.getFirst()), new ECOCraftingRemainderCache());
        assertNull(ECOCraftingInputResolver.extractPatternInputsFromDisposablePreview(consumer, blocked, null,
            new KeyCounter(), new KeyCounter(), new ECOCraftingRemainderCache()));
    }

    private record Setup(ECOExecutionRuntime runtime, ExecutingCraftingJob.TaskProgress[] progress) {
        ExecutingCraftingJob.TaskProgress progress(int taskId) {
            return progress[taskId];
        }
    }

    private static Setup runtime(IPatternDetails first, IPatternDetails second, AEKey seed) {
        var tasks = List.of(task(0, first), task(1, second));
        var phases = List.of(
            new ECOExecutionPlan.PhaseSpec(0, 0, ECOExecutionSchedule.Type.CYCLE, List.of(0),
                List.of(new ECOExecutionPlan.ExecutionStep(0, 2)), List.of(), Map.of(), Map.of(seed, 5L)),
            new ECOExecutionPlan.PhaseSpec(1, 1, ECOExecutionSchedule.Type.CYCLE, List.of(1),
                List.of(new ECOExecutionPlan.ExecutionStep(1, 2)), List.of(), Map.of(), Map.of(seed, 7L)));
        var signature = new PlanIdentity.Signature(mock(AEKey.class), 1, Map.of(), Map.of(), Map.of(), Map.of());
        var plan = new ECOExecutionPlan(signature, ExecutionMode.ORDERED_CYCLE, tasks, phases,
            new ECOExecutionSchedule(List.of()));
        var progress = new ExecutingCraftingJob.TaskProgress[2];
        for (int taskId = 0; taskId < progress.length; taskId++) {
            progress[taskId] = new ExecutingCraftingJob.TaskProgress();
            progress[taskId].setExact(BigInteger.valueOf(2L), BigInteger.valueOf(2L));
        }
        return new Setup(new ECOExecutionRuntime(plan, Map.of(0, first, 1, second), progress), progress);
    }

    private static ECOExecutionPlan.TaskSpec task(int id, IPatternDetails pattern, long count) {
        return new ECOExecutionPlan.TaskSpec(id, PlanIdentity.patternIdentityFor(pattern), pattern,
            ECOExecutionPlan.PatternRuntimeInfo.from(pattern), count, 0, ECOExecutionPlan.TaskKind.CYCLE_DYNAMIC);
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

    private static IPatternDetails pattern(AEKey inputKey, AEKey outputKey, long outputAmount) {
        var pattern = mock(IPatternDetails.class);
        var input = mock(IPatternDetails.IInput.class);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[] {new GenericStack(inputKey, 1)});
        when(input.getMultiplier()).thenReturn(1L);
        when(input.isValid(any(), any())).thenReturn(true);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[] {input});
        when(pattern.getOutputs()).thenReturn(List.of(new GenericStack(outputKey, outputAmount)));
        return pattern;
    }
}
