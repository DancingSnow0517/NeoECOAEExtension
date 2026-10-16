package cn.dancingsnow.neoecoae.crafting.execution.fastpath;

import appeng.api.stacks.GenericStack;
import java.util.List;

/** Shared total validation; the public work records retain their distinct count and coolant contracts. */
final class CraftWorkValidation {
    private CraftWorkValidation() {}

    static boolean valid(List<GenericStack> inputs, List<GenericStack> outputs,
            List<GenericStack> remainders, long limit) {
        return ECOFastPathStacks.areValidItemStacks(inputs, limit, false,
                ECOFastPathStacks.ItemStackValidation.FAST_PATH_INPUT)
            && ECOFastPathStacks.areValidItemStacks(outputs, limit, true,
                ECOFastPathStacks.ItemStackValidation.FAST_PATH)
            && ECOFastPathStacks.areValidItemStacks(remainders, limit, false,
                ECOFastPathStacks.ItemStackValidation.FAST_PATH_MUTATION);
    }
}
