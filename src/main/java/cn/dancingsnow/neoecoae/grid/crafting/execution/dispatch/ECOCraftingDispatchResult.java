package cn.dancingsnow.neoecoae.grid.crafting.execution.dispatch;

import java.util.ArrayList;
import java.util.List;

import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

/**
 * Result shared by the ordinary and verified batch dispatch paths.
 *
 * <p>The CPU only needs to know how many crafts were accepted and which output/remainder
 * stacks must enter {@code waitingFor}. Batch capacity, extraction, and rollback stay
 * inside the batch planner, materializer and executor.</p>
 */
public record ECOCraftingDispatchResult(long acceptedCrafts, List<GenericStack> outputs,
        List<GenericStack> remainders) {
    public ECOCraftingDispatchResult {
        if (acceptedCrafts <= 0L) {
            throw new IllegalArgumentException("acceptedCrafts must be positive");
        }


    }

    public static ECOCraftingDispatchResult single(KeyCounter outputs, KeyCounter remainders) {
        return new ECOCraftingDispatchResult(1L, stacks(outputs), stacks(remainders));
    }

    public static ECOCraftingDispatchResult batch(long acceptedCrafts, List<GenericStack> outputs,
            List<GenericStack> remainders) {
        return new ECOCraftingDispatchResult(acceptedCrafts, outputs, remainders);
    }

    private static List<GenericStack> stacks(KeyCounter counter) {
        List<GenericStack> result = new ArrayList<>();
        for (var entry : counter) {
            if (entry.getLongValue() > 0L) {
                result.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            }
        }
        return result;
    }
}
