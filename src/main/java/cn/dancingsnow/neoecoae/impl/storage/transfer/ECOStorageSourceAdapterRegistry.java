package cn.dancingsnow.neoecoae.impl.storage.transfer;

import appeng.api.networking.IGrid;
import appeng.api.storage.MEStorage;
import java.util.ArrayList;
import java.util.List;

public final class ECOStorageSourceAdapterRegistry {
    private final List<ECOStorageSourceAdapter> adapters = new ArrayList<>();

    public ECOStorageSourceAdapterRegistry() {
        register(new SophisticatedStorageSourceAdapter());
    }

    public void register(ECOStorageSourceAdapter adapter) {
        adapters.add(adapter);
    }

    public ECOStorageSourceAdapter select(IGrid grid, MEStorage storage) {
        for (ECOStorageSourceAdapter adapter : adapters) {
            if (adapter.supports(grid, storage)) {
                return adapter;
            }
        }
        return ReconciliationOnlyAdapter.INSTANCE;
    }

    private enum ReconciliationOnlyAdapter implements ECOStorageSourceAdapter {
        INSTANCE;

        @Override
        public boolean supports(IGrid grid, MEStorage storage) {
            return true;
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
        }
    }
}
