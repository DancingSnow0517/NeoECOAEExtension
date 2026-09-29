package cn.dancingsnow.neoecoae.api.me;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchAdmission;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Explicit ordinary-processing contract. Input counters contain complete batch totals. */
public interface ECOParallelCraftingProvider {
    long eco$getAvailableParallelSlots();

    ECOBatchAdmission eco$pushPatternBatch(
            IPatternDetails pattern, KeyCounter[] inputTotal, long craftCount, @Nullable UUID craftingJobId);
}
