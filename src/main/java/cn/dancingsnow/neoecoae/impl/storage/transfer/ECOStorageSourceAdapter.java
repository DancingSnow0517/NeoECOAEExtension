package cn.dancingsnow.neoecoae.impl.storage.transfer;

import appeng.api.networking.IGrid;
import appeng.api.storage.MEStorage;

public interface ECOStorageSourceAdapter {
    enum Status {
        ACTIVE,
        FALLBACK,
        DEGRADED
    }

    boolean supports(IGrid grid, MEStorage storage);

    default Status status() {
        return Status.FALLBACK;
    }

    default AutoCloseable observationScope(SourceChangeSink sink) {
        return () -> {};
    }

    default ECOSophisticatedMutationBatch.Scope mutationBatch(
        ECOSophisticatedMutationBatch.FlushFailureSink failureSink
    ) {
        return ECOSophisticatedMutationBatch.noopScope();
    }

    void refreshSnapshot(SourceChangeSink sink);

    void subscribe(SourceChangeSink sink);

    void unsubscribe(SourceChangeSink sink);
}
