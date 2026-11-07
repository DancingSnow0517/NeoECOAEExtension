package cn.dancingsnow.neoecoae.crafting.execution.fastpath;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.util.ArrayList;
import java.math.BigInteger;
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

    @Test void manyIngredientArithmeticFollowsAmountsInsteadOfFastPathEntryLimit() {
        var ingredients = new ArrayList<GenericStack>();
        for (int slot = 0; slot < 81; slot++) {
            ingredients.add(new GenericStack(mock(AEKey.class, RETURNS_DEEP_STUBS), 1));
        }
        assertEquals(Long.MAX_VALUE, ECOBatchCraftingHelper.maxBatchSizeForPerCraftStacks(
                ingredients, List.of(), List.of()));
        var multiplied = ECOBatchCraftingHelper.multiply(ingredients, 2);
        assertEquals(81, multiplied.size());
        for (var ingredient : multiplied) assertEquals(2, ingredient.amount());
    }

    @Test void manyRepeatedSlotsAggregateBeforeDebitingAndStillRejectOverflow() {
        var repeated = java.util.Collections.nCopies(81, new GenericStack(key, 1));
        assertEquals(Long.MAX_VALUE / 81, ECOBatchCraftingHelper.maxBatchSizeForPerCraftStacks(
                repeated, List.of(), List.of()));
        assertEquals(List.of(new GenericStack(key, 162)), ECOBatchCraftingHelper.multiply(repeated, 2));
        assertThrows(ArithmeticException.class, () -> ECOBatchCraftingHelper.multiply(repeated, Long.MAX_VALUE / 81 + 1));
    }
    @Test void nonzeroEnergyCanReachLongMaxAndZeroEnergyDoesNotProbeTheNetwork() {
        assertEquals(Long.MAX_VALUE, ECOBatchCraftingHelper.maxAffordableCrafts(2, Long.MAX_VALUE, amount -> amount));
        assertEquals(Long.MAX_VALUE, ECOBatchCraftingHelper.maxAffordableCrafts(0, Long.MAX_VALUE,
            amount -> { fail("Zero-energy batches must not query the network"); return 0; }));
        assertEquals(0, ECOBatchCraftingHelper.maxAffordableCrafts(1, 100L, amount -> Double.POSITIVE_INFINITY));
        assertEquals(0, ECOBatchCraftingHelper.maxEnergySafeCrafts(Double.NaN));
        assertEquals(0, ECOBatchCraftingHelper.maxEnergySafeCrafts(Double.POSITIVE_INFINITY));
    }
    @Test void multiplierRoundingDoesNotBlockSingleOrScaledRecipes() {
        double power = 59.144;
        assertTrue((power * 10.0) / 10.0 < power);
        assertEquals(1, ECOBatchCraftingHelper.maxAffordableCrafts(power, 1,
            amount -> (amount * 10.0) / 10.0));
        assertEquals(1000L, ECOBatchCraftingHelper.maxAffordableCrafts(power, 1000L, Math::nextDown));
    }

    @Test void energyToleranceCannotFundMissingCopiesOrUnpoweredRecipes() {
        double power = 59.144;
        assertEquals(3L, ECOBatchCraftingHelper.maxAffordableCrafts(power, 10L,
            amount -> Math.min(amount, power * 3)));
        assertEquals(0L, ECOBatchCraftingHelper.maxAffordableCrafts(power, 1L, amount -> amount - 0.5));
        assertEquals(0L, ECOBatchCraftingHelper.maxAffordableCrafts(0.001, 10L, amount -> 0));
        assertEquals(0L, ECOBatchCraftingHelper.maxAffordableCrafts(0.001, 1L, amount -> 0.0004));
        for (double invalid : new double[]{-1, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            assertEquals(0L, ECOBatchCraftingHelper.maxAffordableCrafts(power, 1L, amount -> invalid));
        }
    }

    @Test void exactOrdersShareToleranceWithoutRoundingAwayTheirCount() {
        assertEquals(BigInteger.ONE,
            ECOBatchCraftingHelper.maxAffordableCrafts(59.144, BigInteger.ONE, amount -> (amount * 10.0) / 10.0));
        BigInteger count = BigInteger.ONE.shiftLeft(70);
        assertEquals(count, ECOBatchCraftingHelper.maxAffordableCrafts(59.144, count, Math::nextDown));
        assertEquals(count, ECOBatchCraftingHelper.maxAffordableCrafts(1, count.add(BigInteger.ONE),
            amount -> Math.min(amount, count.doubleValue())));
        assertEquals(count, ECOBatchCraftingHelper.maxAffordableCrafts(0, count, amount -> {
            fail("Zero-energy exact orders must not query the network");
            return 0;
        }));
        assertEquals(BigInteger.ONE, ECOBatchCraftingHelper.maxAffordableCrafts(Double.MAX_VALUE, count, amount -> amount));
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
