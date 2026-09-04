package cn.dancingsnow.neoecoae.impl.storage.transfer;

import appeng.api.networking.IGrid;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.config.NEConfig;
import net.neoforged.fml.ModList;

public final class SophisticatedStorageSourceAdapter implements ECOStorageSourceAdapter {
    private volatile boolean observedHandler;
    private volatile boolean degraded;

    @Override
    public boolean supports(IGrid grid, MEStorage storage) {
        return NEConfig.enableSophisticatedTransferOptimization && ModList.get().isLoaded("sophisticatedcore");
    }

    @Override
    public Status status() {
        return degraded ? Status.DEGRADED : observedHandler ? Status.ACTIVE : Status.FALLBACK;
    }

    @Override
    public AutoCloseable observationScope(SourceChangeSink sink) {
        return ECOSophisticatedSourceRegistry.observe(sink, () -> observedHandler = true, () -> degraded = true);
    }

    @Override
    public ECOSophisticatedMutationBatch.Scope mutationBatch(
        ECOSophisticatedMutationBatch.FlushFailureSink failureSink
    ) {
        return ECOSophisticatedMutationBatch.open(failureSink);
    }

    @Override
    public void refreshSnapshot(SourceChangeSink sink) {
        sink.markAllDirty();
    }

    @Override
    public void subscribe(SourceChangeSink sink) {
    }

    @Override
    public void unsubscribe(SourceChangeSink sink) {
        ECOSophisticatedSourceRegistry.unsubscribe(sink);
    }
}
