package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.dancingsnow.neoecoae.api.me.CraftingCapabilitySnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.config.Actionable;
import appeng.crafting.inv.ListCraftingInventory;
import net.minecraft.nbt.CompoundTag;

class ECOBatchCraftingBoundsTest {
    private static final ECOPatternEligibility TAG_ELIGIBILITY = new ECOPatternEligibility(
        true, List.of(ECOPatternEligibility.InputType.TAG_OR_SUBSTITUTION), "");
    private static final CraftingCapabilitySnapshot.CoolantState NO_COOLING =
        new CraftingCapabilitySnapshot.CoolantState(false, 0L, 1_000_000L, -1);

    @Test
    void batchSizeHasNoFixedCeiling() {
        assertDoesNotThrow(() -> ECOBatchCraftingHelper.validateBatchSize(1));
        assertDoesNotThrow(() -> ECOBatchCraftingHelper.validateBatchSize(4097));
        assertDoesNotThrow(() -> ECOBatchCraftingHelper.validateBatchSize(1_000_000));
        assertDoesNotThrow(() -> ECOBatchCraftingHelper.validateBatchSize(Integer.MAX_VALUE));
    }

    @Test
    void nonPositiveBatchSizeIsStillRejected() {
        assertThrows(IllegalArgumentException.class, () -> ECOBatchCraftingHelper.validateBatchSize(0));
        assertThrows(IllegalArgumentException.class, () -> ECOBatchCraftingHelper.validateBatchSize(-1));
    }

    @Test
    void perCraftAmountDerivesTheOnlyRemainingCeiling() {
        assertEquals(
            1L << 42,
            ECOBatchCraftingHelper.maxBatchSizeForAmount(1L)
        );
        // 2^42 / 2^20 = 2^22, the first quotient small enough to be the binding limit rather than int range.
        assertEquals(
            1 << 22,
            ECOBatchCraftingHelper.maxBatchSizeForAmount(1L << 20)
        );
        assertEquals(
            1,
            ECOBatchCraftingHelper.maxBatchSizeForAmount(ECOBatchCraftingHelper.MAX_BATCH_STACK_AMOUNT)
        );
        assertEquals(0, ECOBatchCraftingHelper.maxBatchSizeForAmount(0L));
        assertEquals(0, ECOBatchCraftingHelper.maxBatchSizeForAmount(-5L));
    }

    @Test
    void emptyRecipeAndEmptyTotalsImposeNoCeiling() {
        assertEquals(
            Long.MAX_VALUE,
            ECOBatchCraftingHelper.maxBatchSizeForPerCraftStacks(java.util.List.of(), java.util.List.of(),
                java.util.List.of())
        );
        assertEquals(Integer.MAX_VALUE, ECOBatchCraftingHelper.maxBatchSizeFromTotals(java.util.List.of()));
    }

    @Test
    void persistedBatchSizeOnlyEnforcesTheLowerBound() {
        assertEquals(1, ECOBatchCraftingHelper.clampPersistedBatchSize(-1));
        assertEquals(1, ECOBatchCraftingHelper.clampPersistedBatchSize(0));
        assertEquals(999_999, ECOBatchCraftingHelper.clampPersistedBatchSize(999_999));
    }

    @Test
    void stackAmountLimitIsTheHardArithmeticCeiling() {
        assertEquals(1L << 42, ECOBatchCraftingHelper.MAX_BATCH_STACK_AMOUNT);
    }

    @Test
    void stackValidationReportsTheSpecificFailure() {
        assertEquals(
            ECOFastPathStacks.ItemStackValidationFailure.EMPTY_REQUIRED,
            ECOFastPathStacks.validateItemStacks(
                List.of(), Integer.MAX_VALUE, true, ECOFastPathStacks.ItemStackValidation.FAST_PATH)
        );
        assertEquals(
            ECOFastPathStacks.ItemStackValidationFailure.NON_ITEM_KEY,
            ECOFastPathStacks.validateItemStacks(
                List.of(new GenericStack(FastPathTestKey.of("fluid-like"), 1L)),
                Integer.MAX_VALUE,
                true,
                ECOFastPathStacks.ItemStackValidation.FAST_PATH)
        );
        assertEquals(
            ECOFastPathStacks.ItemStackValidationFailure.INVALID_AMOUNT,
            ECOFastPathStacks.validateItemStacks(
                List.of(new GenericStack(FastPathTestKey.of("overflow"), Integer.MAX_VALUE + 1L)),
                Integer.MAX_VALUE,
                true,
                ECOFastPathStacks.ItemStackValidation.FAST_PATH)
        );
    }

    @Test
    void virtualMultiplierAcceptsCraftCountsAboveIntRange() {
        long craftCount = (long) Integer.MAX_VALUE + 42L;
        GenericStack perCraft = new GenericStack(FastPathTestKey.of("virtual-long"), 1L);
        var totals = ECOBatchCraftingHelper.multiply(java.util.List.of(perCraft), craftCount);
        assertEquals(craftCount, totals.getFirst().amount());
    }

    @Test
    void energySafetyLimitRetainsLongProbeRange() {
        assertEquals(10_000_000_000L, ECOBatchCraftingHelper.maxAffordableCrafts(
            1.0D, 10_000_000_000L, required -> required));
        assertEquals(2_500_000_000L, ECOBatchCraftingHelper.maxAffordableCrafts(
            2.0D, 10_000_000_000L, required -> Math.min(required, 5_000_000_000D)));
    }

    @Test
    void extractableInputLimitRetainsLongProbeRange() {
        var key = FastPathTestKey.of("probe-long-input");
        var inventory = new ListCraftingInventory(ignored -> {});
        inventory.insert(key, 10_000_000_000L, Actionable.MODULATE);
        assertEquals(10_000_000_000L, ECOBatchCraftingHelper.maxCraftsFromInventory(
            inventory, List.of(new GenericStack(key, 1L)), 10_000_000_000L));
    }

    @Test
    void durabilitySingleAndBatchDamageAreLinear() {
        assertEquals(11, calculatedDamage(10, 1, 200, 1).orElseThrow());
        assertEquals(110, calculatedDamage(10, 1, 200, 100).orElseThrow());
    }

    @Test
    void durabilityAtAndBeyondBreakBoundaryReturnsNoTool() {
        assertTrue(calculatedDamage(199, 1, 200, 1).isEmpty());
        assertTrue(calculatedDamage(198, 1, 200, 2).isEmpty());
        assertTrue(calculatedDamage(198, 1, 200, 20).isEmpty());
    }

    @Test
    void reusableStateSupportsIdentityAndLinearNumericCustomData() {
        CompoundTag initial = stateWithCharge(10);
        assertTrue(ECOStateTransitionBatchModel.isUnchangedState(initial, initial.copy()));
        CompoundTag result = ECOStateTransitionBatchModel.applyCustomDataTransition(
            initial, stateWithCharge(11), 100).orElseThrow();
        assertEquals(110, result.getInt("charge"));
    }

    @Test
    void reusableStateRejectsNonNumericCustomData() {
        CompoundTag before = new CompoundTag();
        before.putString("mode", "a");
        CompoundTag after = new CompoundTag();
        after.putString("mode", "b");
        assertTrue(ECOStateTransitionBatchModel.applyCustomDataTransition(before, after, 2).isEmpty());
    }

    @Test
    void sameTagResolutionReusesTheResolvedContract() {
        ECOCraftingFastPathCache cache = new ECOCraftingFastPathCache(16);
        ECOExtractedPatternExecution oak = tagExecution("oak", 3L);
        assertEquals(ECOFastPathLookup.Status.MISS, cache.lookup(oak, 0L, 3L).status());
        cache.putResolvedForTesting(oak.key(), tagResult(oak));

        ECOFastPathLookup lookup = cache.lookup(tagExecution("oak", 3L), 1L, 3L);
        assertEquals(ECOFastPathLookup.Status.VERIFIED, lookup.status());
        assertTrue(lookup.recipe().capabilities().contains(FastPathCapability.TAG_RESOLVED_LINEAR));
        assertNotNull(cache.getPatternEligibility(oak.key().patternKey()));
    }

    @Test
    void differentConcreteTagMemberRequiresItsOwnVerification() {
        ECOCraftingFastPathCache cache = new ECOCraftingFastPathCache(16);
        ECOExtractedPatternExecution oak = tagExecution("oak", 3L);
        cache.lookup(oak, 0L, 3L);
        cache.putResolvedForTesting(oak.key(), tagResult(oak));
        assertEquals(ECOFastPathLookup.Status.MISS, cache.lookup(tagExecution("birch", 3L), 1L, 3L).status());
    }

    @Test
    void reloadGenerationRequiresTagPatternAndResolvedRevalidation() {
        ECOCraftingFastPathCache cache = new ECOCraftingFastPathCache(16);
        ECOExtractedPatternExecution before = tagExecution("oak", 3L);
        cache.lookup(before, 0L, 3L);
        cache.putResolvedForTesting(before.key(), tagResult(before));
        assertEquals(ECOFastPathLookup.Status.MISS, cache.lookup(tagExecution("oak", 4L), 1L, 4L).status());
    }

    @Test
    void f9CapabilityMatrixUsesOneNetworkMultiplierForEveryFx() {
        assertFinite(snapshot(1, 0, 0, false, false), 32L, 32L);
        assertFinite(snapshot(11, 0, 0, false, false), 32L, 352L);
        assertFinite(snapshot(11, 0, 0, true, false), 512L, 5_632L);
        assertFinite(snapshot(22, 2, 0, false, false), 2_048L, 45_056L);
        assertFinite(snapshot(33, 0, 3, false, false), 12_288L, 405_504L);
        assertFinite(snapshot(77, 0, 7, false, false), 28_672L, 2_207_744L);
        assertFinite(snapshot(55, 2, 3, false, false), 14_336L, 788_480L);
        assertEquals(28, snapshot(55, 2, 3, false, false).networkMultiplier());
    }

    @Test
    void ftParallelCapacityNeverClipsFiniteFxCapacity() {
        CraftingCapabilitySnapshot state = calculate(77, 0, 7, false, false, 1L);
        assertEquals(1L, state.ftParallelCapacity());
        assertEquals(2_207_744L, state.totalBatchCapacity().finiteValue());
    }

    @Test
    void virtualTopologyRequiresEightActuallyFullF9X8Hosts() {
        List<CraftingCapabilitySnapshot.VirtualHost> hosts = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            hosts.add(new CraftingCapabilitySnapshot.VirtualHost(true, true, 11, 11));
        }
        assertTrue(CraftingCapabilitySnapshot.isVirtualTopologyEligible(hosts));
        CraftingCapabilitySnapshot virtual = snapshot(88, 0, 8, false, true);
        assertTrue(virtual.batchPerFx().unlimited());
        assertTrue(virtual.totalBatchCapacity().unlimited());

        hosts.set(3, new CraftingCapabilitySnapshot.VirtualHost(true, true, 10, 11));
        assertFalse(CraftingCapabilitySnapshot.isVirtualTopologyEligible(hosts));
        CraftingCapabilitySnapshot finite = snapshot(87, 0, 8, false, false);
        assertFalse(finite.virtualMode());
        assertFinite(finite, 32_768L, 2_850_816L);
    }

    private static CraftingCapabilitySnapshot snapshot(
        int physicalFx, int normalHosts, int highEnergyHosts, boolean overclocked, boolean virtualEligible
    ) {
        return calculate(physicalFx, normalHosts, highEnergyHosts, overclocked, virtualEligible, 100_000L);
    }

    private static CraftingCapabilitySnapshot calculate(
        int physicalFx,
        int normalHosts,
        int highEnergyHosts,
        boolean overclocked,
        boolean virtualEligible,
        long ftParallelCapacity
    ) {
        return CraftingCapabilitySnapshot.calculate(new CraftingCapabilitySnapshot.Input(
            physicalFx, 0, normalHosts, highEnergyHosts, 512L, ftParallelCapacity, 0,
            overclocked, false, 16, virtualEligible, NO_COOLING));
    }

    private static void assertFinite(CraftingCapabilitySnapshot state, long perFx, long total) {
        assertFalse(state.batchPerFx().unlimited());
        assertEquals(perFx, state.batchPerFx().finiteValue());
        assertEquals(total, state.totalBatchCapacity().finiteValue());
    }

    private static java.util.OptionalInt calculatedDamage(int initial, int delta, int max, long crafts) {
        return ECODurabilityBatchModel.calculateFinalDamage(
            initial, delta, max, ECODurabilityBatchModel.BreakBehavior.DISAPPEAR, crafts);
    }

    private static CompoundTag stateWithCharge(int charge) {
        CompoundTag state = new CompoundTag();
        state.putInt("charge", charge);
        return state;
    }

    private static ECOExtractedPatternExecution tagExecution(String material, long generation) {
        KeyCounter slot = new KeyCounter();
        slot.add(FastPathTestKey.of(material), 1L);
        ECOFastPathKey key = ECOFastPathKey.of("#planks-pattern", new KeyCounter[] {slot}, null, generation)
            .orElseThrow();
        return ECOExtractedPatternExecution.ofNormalizedComponents(
            key,
            List.of(new GenericStack(FastPathTestKey.of("output"), 1L)),
            List.of(),
            List.of(new GenericStack(FastPathTestKey.of(material), 1L)),
            TAG_ELIGIBILITY);
    }

    private static ECOFastPathResult tagResult(ECOExtractedPatternExecution execution) {
        return ECOFastPathResult.positive(
            execution.expectedOutputs(), List.of(), execution.inputItems(), 0L, null,
            Set.of(FastPathCapability.TAG_RESOLVED_LINEAR), ECORecipeClassifier.Type.NORMAL,
            List.of(), List.of(), List.of(), List.of());
    }
}
