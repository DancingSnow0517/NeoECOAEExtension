package cn.dancingsnow.neoecoae.api.me;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    /** The strategy only needs a non-null pattern identity; dispatch never invokes this test double. */
    private static final class StubPattern implements appeng.api.crafting.IPatternDetails {
        @Override public appeng.api.stacks.AEItemKey getDefinition() { return null; }
        @Override public appeng.api.crafting.IPatternDetails.IInput[] getInputs() { return new appeng.api.crafting.IPatternDetails.IInput[0]; }
        @Override public List<appeng.api.stacks.GenericStack> getOutputs() { return List.of(); }
    }
}
