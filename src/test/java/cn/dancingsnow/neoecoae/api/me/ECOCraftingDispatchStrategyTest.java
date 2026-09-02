package cn.dancingsnow.neoecoae.api.me;

import cn.dancingsnow.neoecoae.compat.thunderbolt.ECOExternalBatchContracts;
import cn.dancingsnow.neoecoae.config.NEConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void operationLimitHonorsTemporaryCeilingConfiguredByMaxPushTickLimit() {
        int expected = NEConfig.MAX_ECO_CPU_PUSH_TICK_LIMIT;
        assertEquals(expected, ECOCraftingCPULogic.calculateOperationLimit(
            Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(expected, ECOCraftingCPULogic.calculateOperationLimit(
            Integer.MAX_VALUE, expected));
        // Configured limit below the ceiling wins.
        assertEquals(100_000, ECOCraftingCPULogic.calculateOperationLimit(
            Integer.MAX_VALUE, 100_000));
        // Coprocessor count is the lower bound when the configured limit exceeds it.
        assertEquals(4, ECOCraftingCPULogic.calculateOperationLimit(
            3, Integer.MAX_VALUE));
    }

    @Test
    void ordinaryPolicyDoesNotApplyATaskSizeWindowCeiling() {
        var provider = new StubProvider();
        var smallTask = new ECOCraftingDispatchStrategy.DispatchContext(
            new StubPattern(), 999L, 16_384, List.of(provider), 16_384);
        var largeTask = new ECOCraftingDispatchStrategy.DispatchContext(
            new StubPattern(), 100_000_000L, 16_384, List.of(provider), 16_384);

        assertEquals(16_384, ECOParallelDispatchStrategy.INSTANCE.choose(smallTask).maxAttempts());
        assertEquals(16_384, ECOParallelDispatchStrategy.INSTANCE.choose(largeTask).maxAttempts());
        assertFalse(ECOBatchProbeCraftingProvider.class.isAssignableFrom(StubProvider.class));
        assertFalse(ECOParallelCraftingProvider.class.isAssignableFrom(ECOBatchProbeCraftingProvider.class));
        assertFalse(ECOBatchProbeCraftingProvider.class.isAssignableFrom(ECOParallelCraftingProvider.class));
    }

    @Test
    void ordinaryPolicyUsesAdvertisedCapacityWithoutGenericCredit() {
        var parallel = new StubParallelProvider(37);
        var context = new ECOCraftingDispatchStrategy.DispatchContext(
            new StubPattern(), 1_000_000L, 100, List.of(parallel), parallel.eco$getAvailableParallelSlots());

        assertEquals(37, ECOParallelDispatchStrategy.INSTANCE.choose(context).maxAttempts());
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
        ECOBatchProbeScheduler.probe(state, 4096L, 64,
            candidate -> { attempted.add(candidate); return true; });
        assertEquals(List.of(4096L), attempted,
            "a successful window clears continuation and history must not cap the next fresh N");
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
        var continuation = ECOBatchProbeScheduler.probe(state, 1024L, 64, candidate -> false);
        assertArrayEquals(new long[] { 128L, 64L, 32L }, continuation.candidates());
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
    void continuationRespectsLowerLegalUpperAndSuccessRestartsFresh() {
        var state = new BatchCapacityProbeState();
        ECOBatchProbeScheduler.probe(state, 1024L, 64, candidate -> false);

        var lowered = new ArrayList<Long>();
        ECOBatchProbeScheduler.probe(state, 64L, 64, candidate -> {
            lowered.add(candidate);
            return candidate == 16L;
        });
        assertEquals(List.of(64L, 32L, 16L), lowered);
        assertEquals(0L, state.continuationUpperBound());
        assertEquals(16L, state.historicalKnownGood());

        var fresh = new ArrayList<Long>();
        ECOBatchProbeScheduler.probe(state, 4096L, 64, candidate -> {
            fresh.add(candidate);
            return true;
        });
        assertEquals(List.of(4096L), fresh);
        assertEquals(4096L, state.historicalKnownGood(),
            "history remains diagnostic and records, rather than replaces, the fresh upper bound");
    }

    @Test
    void probeCandidatesAndLegalBoundsRetainLongPrecision() {
        assertArrayEquals(new long[] { 10_000_000_000L, 5_000_000_000L, 2_500_000_000L },
            ECOBatchProbeScheduler.candidates(10_000_000_000L));
        var attempted = new ArrayList<Long>();
        ECOBatchProbeScheduler.probe(new BatchCapacityProbeState(), 10_000_000_000L, 64, candidate -> {
            attempted.add(candidate);
            return true;
        });
        assertEquals(List.of(10_000_000_000L), attempted);

        assertEquals(512L, ECOCraftingCPULogic.calculateProbeLegalUpperBound(
            10_000_000_000L, 512L, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE));
        assertEquals(300L, ECOCraftingCPULogic.calculateProbeLegalUpperBound(
            10_000L, 8_000L, 700L, 300L, 500L));
        assertEquals(10_000_000_000L, ECOCraftingCPULogic.calculateProbeLegalUpperBound(
            20_000_000_000L, 15_000_000_000L, 12_000_000_000L, 11_000_000_000L,
            10_000_000_000L));
    }

    @Test
    void probeBudgetsAreSeparateFromOrdinaryDispatchLimit() {
        var result = ECOBatchProbeScheduler.probe(new BatchCapacityProbeState(), 1024L,
            ECOBatchProbeScheduler.MAX_BATCH_PROBES_PER_CPU_PER_TICK, candidate -> false);
        assertEquals(3, result.probeCount());
        assertEquals(3, ECOBatchProbeScheduler.MAX_BATCH_PROBES_PER_TASK_PER_TICK);
        assertEquals(64, ECOBatchProbeScheduler.MAX_BATCH_PROBES_PER_CPU_PER_TICK);
        assertEquals(NEConfig.MAX_ECO_CPU_PUSH_TICK_LIMIT,
            ECOCraftingCPULogic.calculateOperationLimit(Integer.MAX_VALUE, Integer.MAX_VALUE));

        int used = 0;
        for (int task = 0; task < 100; task++) {
            if (!ECOBatchProbeScheduler.canStartCpuProbeWindow(used, 3)) break;
            used += 3;
        }
        assertEquals(63, used);
        assertFalse(ECOBatchProbeScheduler.canStartCpuProbeWindow(used, 3));
    }

    @Test
    void onlyExplicitUnknownBatchProvidersEnterProbeScheduler() {
        assertTrue(ECOCraftingCPULogic.isUnknownBatchProbeProvider(new StubProbeProvider()));
        assertFalse(ECOCraftingCPULogic.isUnknownBatchProbeProvider(new StubProvider()));
        assertFalse(ECOCraftingCPULogic.isUnknownBatchProbeProviderType(
            cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity.class),
            "F9 remains on its known-capacity offer path");
    }

    @Test
    void externalBatchRequestHonorsLegalCapacityAndCpuBoundaries() {
        assertEquals(10_000_000L,
            ECOExternalBatchContracts.thunderboltRequest(10_000_000L, Long.MAX_VALUE, 10_000_000L));
        assertEquals(16_384L,
            ECOExternalBatchContracts.thunderboltRequest(10_000_000L, Long.MAX_VALUE, 16_384L));
        assertEquals(4_096L,
            ECOExternalBatchContracts.thunderboltRequest(10_000_000L, 4_096L, 16_384L));
        assertEquals(0L, ECOExternalBatchContracts.thunderboltRequest(10L, -1L, 10L));
    }

    @Test
    void externalBatchLeftoverProducesAcceptedCountAndRejectsBrokenContracts() {
        assertEquals(10_000_000L,
            ECOExternalBatchContracts.acceptedFromLeftover(10_000_000L, 0L));
        assertEquals(7_500_000L,
            ECOExternalBatchContracts.acceptedFromLeftover(10_000_000L, 2_500_000L));
        assertEquals(0L,
            ECOExternalBatchContracts.acceptedFromLeftover(10_000_000L, 10_000_000L));
        assertThrows(IllegalArgumentException.class,
            () -> ECOExternalBatchContracts.acceptedFromLeftover(10L, -1L));
        assertThrows(IllegalArgumentException.class,
            () -> ECOExternalBatchContracts.acceptedFromLeftover(10L, 11L));
    }

    /** The strategy only needs a non-null pattern identity; dispatch never invokes this test double. */
    private static final class StubPattern implements appeng.api.crafting.IPatternDetails {
        @Override public appeng.api.stacks.AEItemKey getDefinition() { return null; }
        @Override public appeng.api.crafting.IPatternDetails.IInput[] getInputs() { return new appeng.api.crafting.IPatternDetails.IInput[0]; }
        @Override public List<appeng.api.stacks.GenericStack> getOutputs() { return List.of(); }
    }

    private static class StubProvider implements appeng.api.networking.crafting.ICraftingProvider {
        @Override public List<appeng.api.crafting.IPatternDetails> getAvailablePatterns() { return List.of(); }
        @Override public boolean pushPattern(appeng.api.crafting.IPatternDetails pattern,
                appeng.api.stacks.KeyCounter[] inputHolder) { return true; }
        @Override public boolean isBusy() { return false; }
    }

    private static final class StubParallelProvider implements appeng.api.networking.crafting.ICraftingProvider,
            ECOParallelCraftingProvider {
        private final int slots;
        private StubParallelProvider(int slots) { this.slots = slots; }
        @Override public int eco$getAvailableParallelSlots() { return slots; }
        @Override public List<appeng.api.crafting.IPatternDetails> getAvailablePatterns() { return List.of(); }
        @Override public boolean pushPattern(appeng.api.crafting.IPatternDetails pattern,
                appeng.api.stacks.KeyCounter[] inputHolder) { return true; }
        @Override public boolean isBusy() { return false; }
    }

    private static final class StubProbeProvider extends StubProvider implements ECOBatchProbeCraftingProvider {
        @Override public boolean eco$simulateBatch(
                cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOExtractedPatternExecution execution,
                long craftCount) { return false; }
        @Override public boolean eco$commitBatch(
                cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOExtractedPatternExecution execution,
                long craftCount, java.util.UUID craftingJobId) { return false; }
    }
}
