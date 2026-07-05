package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.storage.infinite.HugeAmount;

public interface ECOStorageBackend {
    long insert(AEKey key, long amount, Actionable mode);

    long extract(AEKey key, long amount, Actionable mode);

    long getAmount(AEKey key);

    void getAvailableStacks(KeyCounter out);

    boolean isEmpty();

    HugeAmount getStoredAmount();

    int getStoredTypes();

    long getRevision();

    boolean isLoaded();

    void requestLoad();

    boolean loadBudgeted(long maxNanos);

    boolean flushBudgeted(long maxNanos);

    void closeAndFlush();
}
