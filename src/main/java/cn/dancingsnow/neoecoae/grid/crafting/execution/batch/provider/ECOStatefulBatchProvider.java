package cn.dancingsnow.neoecoae.grid.crafting.execution.batch.provider;

/** Prepared atomic commit for stateful, verified linear or exact batches. Ordinary exceptions
 * guarantee rejection; uncertain acceptance must throw ECOIndeterminateBatchException. */
@FunctionalInterface
public interface ECOStatefulBatchProvider {
    boolean eco$dispatchPreparedBatch();
}
