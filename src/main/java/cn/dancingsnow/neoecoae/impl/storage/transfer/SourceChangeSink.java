package cn.dancingsnow.neoecoae.impl.storage.transfer;

import appeng.api.stacks.AEKey;

public interface SourceChangeSink {
    void markDirty(AEKey key);

    void markAllDirty();
}
