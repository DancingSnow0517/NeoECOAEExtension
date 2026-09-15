package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

/**
 * Compatibility carrier for crafting_tracker versions that predate the verified batch execution API.
 * The tracker only observes this request while dispatching; the current executor uses
 * {@link ECOVerifiedFastPathExecution} as its authoritative credential.
 */
@Deprecated(forRemoval = false)
public record ECOBatchCraftingRequest(ECOExtractedPatternExecution execution, int batchSize) {
    public ECOBatchCraftingRequest {
        if (execution == null) throw new IllegalArgumentException("execution");
        if (batchSize <= 0) throw new IllegalArgumentException("batchSize");
    }
}
