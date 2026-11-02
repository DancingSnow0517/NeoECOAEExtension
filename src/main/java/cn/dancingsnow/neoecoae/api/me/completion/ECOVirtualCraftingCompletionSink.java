package cn.dancingsnow.neoecoae.api.me.completion;

import appeng.api.crafting.IPatternDetails;

/** Reports logical completion of a virtual crafting pattern without exposing ECO task maps. */
public interface ECOVirtualCraftingCompletionSink {

    /**
     * Attempts to complete executions of {@code pattern} in the current virtual job.
     *
     * @return true when the requested executions were accepted by the job ledger; this does not necessarily mean
     *         that the whole job finished, since other patterns or physical outputs may still be pending
     */
    boolean tryCompleteVirtualCrafting(IPatternDetails pattern, long completedCrafts);
}
