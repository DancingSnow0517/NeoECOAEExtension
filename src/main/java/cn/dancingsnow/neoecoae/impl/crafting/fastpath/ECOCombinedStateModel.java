package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import java.util.List;

/** Combines disjoint durability and component slots without scaling catalysts twice. */
record ECOCombinedStateModel(ECOReusableStateModel durability, ECOReusableStateModel state)
        implements ECOReusableStateModel {
    @Override
    public long maxBatchSize() {
        return Math.min(durability.maxBatchSize(), state.maxBatchSize());
    }

    @Override
    public List<GenericStack> batchInputs(List<GenericStack> inputs, long crafts) {
        return combine(inputs, durability.batchInputs(inputs, crafts), state.batchInputs(inputs, crafts), crafts);
    }

    @Override
    public List<GenericStack> batchRemainders(List<GenericStack> remaining, long crafts) {
        return combine(remaining, durability.batchRemainders(remaining, crafts),
            state.batchRemainders(remaining, crafts), crafts);
    }

    private static List<GenericStack> combine(List<GenericStack> ordinary, List<GenericStack> left,
            List<GenericStack> right, long crafts) {
        KeyCounter result = new KeyCounter();
        for (var stack : left) result.add(stack.what(), stack.amount());
        for (var stack : ordinary) result.remove(stack.what(), Math.multiplyExact(stack.amount(), crafts));
        for (var stack : right) result.add(stack.what(), stack.amount());
        for (var entry : result) {
            if (entry.getLongValue() < 0L) throw new IllegalStateException("Overlapping reusable state slots");
        }
        return ECOFastPathStacks.copyCounter(result);
    }

    @Override
    public boolean requiresSecondStepProof() {
        return durability.requiresSecondStepProof() || state.requiresSecondStepProof();
    }

    @Override
    public boolean sameTransition(ECOReusableStateModel other) {
        return other instanceof ECOCombinedStateModel combined
            && durability.sameTransition(combined.durability) && state.sameTransition(combined.state);
    }
}
