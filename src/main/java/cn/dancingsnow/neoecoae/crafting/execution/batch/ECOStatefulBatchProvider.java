package cn.dancingsnow.neoecoae.crafting.execution.batch;

/** Contract for stateful or non-linear providers; this is the only new FastPath lane. */
public interface ECOStatefulBatchProvider {
    ECOBatchAdmission eco$prepareStatefulBatch(ECOBatchDispatchRequest request);
}
