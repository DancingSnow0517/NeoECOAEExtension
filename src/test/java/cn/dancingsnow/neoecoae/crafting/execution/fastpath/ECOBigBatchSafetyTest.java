package cn.dancingsnow.neoecoae.crafting.execution.fastpath;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.util.List;
import org.junit.jupiter.api.Test;

class ECOBigBatchSafetyTest {
    private final AEKey key = mock(AEKey.class, RETURNS_DEEP_STUBS);
    @Test void unitRecipeCanExceedTheOldCeilingAndDoubleOutputHalvesTheLimit() {
        assertEquals(Long.MAX_VALUE, ECOBatchCraftingHelper.maxBatchSizeForAmount(1));
        assertEquals(Long.MAX_VALUE / 2, ECOBatchCraftingHelper.maxBatchSizeForPerCraftStacks(
            List.of(new GenericStack(key, 1)), List.of(new GenericStack(key, 2)), List.of()));
        long count = (1L << 42) + 1;
        assertEquals(count, ECOBatchCraftingHelper.multiply(List.of(new GenericStack(key, 1)), count).getFirst().amount());
    }
    @Test void duplicateEntriesAreAggregatedBeforeComputingTheLimit() {
        var same = List.of(new GenericStack(key, 1), new GenericStack(key, 1));
        assertEquals(Long.MAX_VALUE / 2,
            ECOBatchCraftingHelper.maxBatchSizeForPerCraftStacks(same, List.of(), List.of()));
        assertThrows(ArithmeticException.class, () -> ECOBatchCraftingHelper.multiply(same, Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE / 3, ECOBatchCraftingHelper.maxBatchSizeForPerCraftStacks(
            List.of(), List.of(), List.of(new GenericStack(key, 3))));
    }
    @Test void nonzeroEnergyCanReachLongMaxAndZeroEnergyDoesNotProbeTheNetwork() {
        assertEquals(Long.MAX_VALUE, ECOBatchCraftingHelper.maxAffordableCrafts(2, Long.MAX_VALUE, amount -> amount));
        assertEquals(Long.MAX_VALUE, ECOBatchCraftingHelper.maxAffordableCrafts(0, Long.MAX_VALUE,
            amount -> { fail("Zero-energy batches must not query the network"); return 0; }));
        assertEquals(0, ECOBatchCraftingHelper.maxAffordableCrafts(1, 100L, amount -> Double.POSITIVE_INFINITY));
        assertEquals(0, ECOBatchCraftingHelper.maxEnergySafeCrafts(Double.NaN));
        assertEquals(0, ECOBatchCraftingHelper.maxEnergySafeCrafts(Double.POSITIVE_INFINITY));
    }
    @Test void physicalBatchSlotsRemainIntBounded() {
        assertEquals(Integer.MAX_VALUE, ECOBatchCraftingHelper.maxBatchSizeFromTotals(
            List.of(new GenericStack(key, Long.MAX_VALUE))));
    }

    @Test void outputAndRemainderSharingAWaitingKeyCannotOverflowTogether() {
        assertEquals(Long.MAX_VALUE / 3, ECOBatchCraftingHelper.maxBatchSizeForPerCraftStacks(
            List.of(), List.of(new GenericStack(key, 2)), List.of(new GenericStack(key, 1))));
    }
    @Test void energyBoundaryDoesNotAcceptRoundedDownCostsOrInfiniteTotals() {
        long exactBudget = 1L << 54;
        assertEquals(exactBudget, ECOBatchCraftingHelper.maxAffordableCrafts(1, Long.MAX_VALUE,
            amount -> Math.min(amount, (double) exactBudget)));
        assertEquals(1L, ECOBatchCraftingHelper.maxEnergySafeCrafts(Double.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, ECOBatchCraftingHelper.maxEnergySafeCrafts(Double.MIN_VALUE));
        assertEquals(0L, ECOBatchCraftingHelper.maxAffordableCrafts(Double.MAX_VALUE, Long.MAX_VALUE,
            amount -> 0));
    }
}
