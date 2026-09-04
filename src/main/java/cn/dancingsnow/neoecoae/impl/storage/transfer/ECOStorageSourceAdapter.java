package cn.dancingsnow.neoecoae.impl.storage.transfer;

import appeng.api.networking.IGrid;
import appeng.api.storage.MEStorage;

public interface ECOStorageSourceAdapter {
    boolean supports(IGrid grid, MEStorage storage);

    void refreshSnapshot(SourceChangeSink sink);

    void subscribe(SourceChangeSink sink);

    void unsubscribe(SourceChangeSink sink);
}
