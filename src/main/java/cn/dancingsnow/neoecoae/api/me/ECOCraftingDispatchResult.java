package cn.dancingsnow.neoecoae.api.me;

import java.util.ArrayList;
import java.util.List;

import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

/**
 * Immutable result shared by the ordinary and verified batch dispatch paths.
 *
 * <p>The CPU only needs to know how many crafts were accepted and which output/remainder
 * stacks must enter {@code waitingFor}. Batch capacity, extraction, and rollback stay
 * inside the FastPath dispatcher.</p>
 */
record ECOCraftingDispatchResult(long acceptedCrafts, List<GenericStack> outputs,
        List<GenericStack> remainders) {
    ECOCraftingDispatchResult {
        if (acceptedCrafts <= 0L) {
            throw new IllegalArgumentException("acceptedCrafts must be positive");
        }
        outputs = List.copyOf(outputs);
        remainders = List.copyOf(remainders);
    }

    static ECOCraftingDispatchResult single(KeyCounter outputs, KeyCounter remainders) {
        return new ECOCraftingDispatchResult(1L, stacks(outputs), stacks(remainders));
    }

    static ECOCraftingDispatchResult batch(long acceptedCrafts, List<GenericStack> outputs,
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
