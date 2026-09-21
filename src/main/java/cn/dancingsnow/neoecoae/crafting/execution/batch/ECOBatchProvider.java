package cn.dancingsnow.neoecoae.crafting.execution.batch;

/** Contract for ordinary providers that can accept a complete linear batch. */
public interface ECOBatchProvider {
    ECOBatchAdmission eco$prepareBatch(ECOBatchDispatchRequest request);
}
