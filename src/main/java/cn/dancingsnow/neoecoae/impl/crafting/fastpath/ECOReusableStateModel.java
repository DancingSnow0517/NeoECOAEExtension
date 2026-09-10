package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.stacks.GenericStack;
import java.util.List;

/** A composable proof for reusable input state across an entire batch. */
public interface ECOReusableStateModel {
    FastPathCapability capability();

    long maxBatchSize();

    List<GenericStack> batchInputs(List<GenericStack> ordinaryInputs, long crafts);

    List<GenericStack> batchRemainders(List<GenericStack> ordinaryRemainders, long crafts);

    boolean requiresSecondStepProof();

    boolean sameTransition(ECOReusableStateModel other);
}
