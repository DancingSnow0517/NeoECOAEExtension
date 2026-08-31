package cn.dancingsnow.neoecoae.api.me;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ECOCraftingDispatchStrategyTest {
    @Test
    void defaultPolicyFillsAdvertisedParallelSlotsWithoutExceedingBudget() {
        var context = new ECOCraftingDispatchStrategy.DispatchContext(
            new StubPattern(), 100L, 10, List.of(), 7);

        var decision = ECOParallelDispatchStrategy.INSTANCE.choose(context);

        assertEquals(7, decision.maxAttempts());
    }

    @Test
    void defaultPolicyDoesNotDispatchWhenNoProviderSlotIsAvailable() {
        var context = new ECOCraftingDispatchStrategy.DispatchContext(
            new StubPattern(), 100L, 10, List.of(), 0);

        var decision = ECOParallelDispatchStrategy.INSTANCE.choose(context);

        assertEquals(0, decision.maxAttempts());
    }

    @Test
    void operationLimitHasTemporarySixteenThousandCeiling() {
        assertEquals(16_384, ECOCraftingCPULogic.calculateOperationLimit(
            Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(16_384, ECOCraftingCPULogic.calculateOperationLimit(
            Integer.MAX_VALUE, 100_000));
    }

    @Test
    void plainProviderAdaptivePolicyCanUseFullGlobalDispatchBudget() {
        var provider = new StubProvider();
        var context = new ECOCraftingDispatchStrategy.DispatchContext(
            new StubPattern(), 1_000_000L, 16_384, List.of(provider), 16_384);

        var decision = new ECOAdaptiveDispatchStrategy().choose(context);

        assertEquals(16_384, decision.maxAttempts());
        assertFalse(ECOBatchProbeCraftingProvider.class.isAssignableFrom(StubProvider.class));
        assertFalse(ECOParallelCraftingProvider.class.isAssignableFrom(ECOBatchProbeCraftingProvider.class));
        assertFalse(ECOBatchProbeCraftingProvider.class.isAssignableFrom(ECOParallelCraftingProvider.class));
    }

    @Test
    void unknownBatchCapacityUsesThreeDescendingProbesAndShortCircuits() {
        var attempted = new ArrayList<Long>();
        var state = new BatchCapacityProbeState();
        var result = ECOBatchProbeScheduler.probe(state, 1024L, 64, candidate -> {
            attempted.add(candidate);
            return candidate == 256L;
        });
        assertEquals(List.of(1024L, 512L, 256L), attempted);
        assertEquals(256L, result.selected());
        assertEquals(256L, state.historicalKnownGood());

        attempted.clear();
        ECOBatchProbeScheduler.probe(state, 10_000_000_000L, 64,
            candidate -> { attempted.add(candidate); return true; });
        assertEquals(List.of(256L), attempted);
    }

    @Test
    void firstAndSecondProbeSuccessStopImmediately() {
        var first = new ArrayList<Long>();
        assertEquals(1024L, ECOBatchProbeScheduler.probe(new BatchCapacityProbeState(), 1024L, 64,
            candidate -> { first.add(candidate); return true; }).selected());
        assertEquals(List.of(1024L), first);

        var second = new ArrayList<Long>();
        assertEquals(512L, ECOBatchProbeScheduler.probe(new BatchCapacityProbeState(), 1024L, 64,
            candidate -> { second.add(candidate); return candidate == 512L; }).selected());
        assertEquals(List.of(1024L, 512L), second);
    }

    @Test
    void failedWindowContinuesBelowItsLastProbeAndSmallWindowsDropZero() {
        var state = new BatchCapacityProbeState();
        var result = ECOBatchProbeScheduler.probe(state, 1024L, 64, candidate -> false);
        assertArrayEquals(new long[] { 1024L, 512L, 256L }, result.candidates());
        assertEquals(128L, state.nextUpperBound());
        assertArrayEquals(new long[] { 3L, 1L }, ECOBatchProbeScheduler.candidates(3L));
        assertArrayEquals(new long[] { 1L }, ECOBatchProbeScheduler.candidates(1L));
    }

    @Test
    void runtimeUpperBoundAndHistoricalCapacityRemainIndependentFromTransientAvailability() {
        var state = new BatchCapacityProbeState();
        var orderedCycle = new ArrayList<Long>();
        ECOBatchProbeScheduler.probe(state, 512L, 64,
            candidate -> { orderedCycle.add(candidate); return true; });
        assertEquals(512L, orderedCycle.getFirst());

        var transientResult = ECOBatchProbeScheduler.probe(state, 512L, 64,
            candidate -> candidate == 128L);
        assertEquals(128L, transientResult.selected());
        assertEquals(512L, state.historicalKnownGood());
    }

    @Test
    void probeBudgetsAreSeparateFromOrdinaryDispatchLimit() {
        var result = ECOBatchProbeScheduler.probe(new BatchCapacityProbeState(), 1024L,
            ECOBatchProbeScheduler.MAX_BATCH_PROBES_PER_CPU_PER_TICK, candidate -> false);
        assertEquals(3, result.probeCount());
        assertEquals(3, ECOBatchProbeScheduler.MAX_BATCH_PROBES_PER_TASK_PER_TICK);
        assertEquals(64, ECOBatchProbeScheduler.MAX_BATCH_PROBES_PER_CPU_PER_TICK);
        assertEquals(16_384, ECOCraftingCPULogic.calculateOperationLimit(Integer.MAX_VALUE, Integer.MAX_VALUE));

        int used = 0;
        for (int task = 0; task < 100; task++) {
            if (!ECOBatchProbeScheduler.canStartCpuProbeWindow(used, 3)) break;
            used += 3;
        }
        assertEquals(63, used);
        assertFalse(ECOBatchProbeScheduler.canStartCpuProbeWindow(used, 3));
    }

    /** The strategy only needs a non-null pattern identity; dispatch never invokes this test double. */
    private static final class StubPattern implements appeng.api.crafting.IPatternDetails {
        @Override public appeng.api.stacks.AEItemKey getDefinition() { return null; }
        @Override public appeng.api.crafting.IPatternDetails.IInput[] getInputs() { return new appeng.api.crafting.IPatternDetails.IInput[0]; }
        @Override public List<appeng.api.stacks.GenericStack> getOutputs() { return List.of(); }
    }

    private static final class StubProvider implements appeng.api.networking.crafting.ICraftingProvider {
        @Override public List<appeng.api.crafting.IPatternDetails> getAvailablePatterns() { return List.of(); }
        @Override public boolean pushPattern(appeng.api.crafting.IPatternDetails pattern,
                appeng.api.stacks.KeyCounter[] inputHolder) { return true; }
        @Override public boolean isBusy() { return false; }
    }
}
