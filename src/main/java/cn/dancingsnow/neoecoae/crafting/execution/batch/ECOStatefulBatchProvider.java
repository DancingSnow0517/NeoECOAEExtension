package cn.dancingsnow.neoecoae.crafting.execution.batch;

/** Prepared atomic commit for stateful, verified linear or exact batches. Ordinary exceptions
 * guarantee rejection; uncertain acceptance must throw ECOIndeterminateBatchException. */
@FunctionalInterface
public interface ECOStatefulBatchProvider {
    boolean eco$dispatchPreparedBatch();
}
