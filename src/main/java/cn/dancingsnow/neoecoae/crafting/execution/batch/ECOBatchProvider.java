package cn.dancingsnow.neoecoae.crafting.execution.batch;

/** Synchronous adapter called after inputs and energy are reserved. Rejection owns nothing.
 * An accepted prefix must leave the remaining linear copies untouched. Exceptions have uncertain ownership. */
@FunctionalInterface
public interface ECOBatchProvider {
    ECOBatchAdmission eco$dispatchBatch(ECOBatchDispatchRequest request);
}
