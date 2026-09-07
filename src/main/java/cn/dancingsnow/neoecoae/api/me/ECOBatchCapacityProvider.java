package cn.dancingsnow.neoecoae.api.me;

/** Optional server-thread capability. Counts always represent complete pattern copies. */
public interface ECOBatchCapacityProvider {
    /** Side-effect-free current capacity; no inputs, queues, energy or job state may change. */
    long eco$getBatchCapacity(ECOBatchDispatchContext context);

    /**
     * Atomically accepts all inputs for craftCount copies. False (or an exception) means no input
     * was accepted and no output was published. The CPU owns and restores inputs on rejection.
     * Implementations must revalidate live capacity, and must never partially accept a batch.
     */
    boolean eco$pushBatch(ECOBatchDispatchContext context, long craftCount);
}
