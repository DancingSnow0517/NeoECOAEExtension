package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ECOInfiniteStorageEngine {
    record TypeStats(AEKeyType keyType, long storedTypes, HugeAmount storedAmount) {}

    record HugeStack(AEKey key, HugeAmount amount) {}

    long insert(AEKey key, long amount, Actionable mode);

    default long insertOnce(UUID transactionId, AEKey key, long amount) {
        return insert(key, amount, Actionable.MODULATE);
    }

    /** Atomically applies one source container exactly once before it is cleared. */
    default boolean applyTransferOnce(UUID transactionId, Collection<HugeStack> contents) {
        return false;
    }

    default boolean hasLegacyTransferReceipt(UUID transactionId) {
        return false;
    }

    default boolean hasTransferReceipt(UUID transactionId) {
        return false;
    }

    long extract(AEKey key, long amount, Actionable mode);

    HugeAmount getAmount(AEKey key);

    void getAvailableStacks(KeyCounter out);

    long getRevision();

    boolean isEmpty();

    default boolean isHealthy() {
        return getState() == ECOInfiniteDomainState.READY;
    }

    HugeAmount getStoredAmount();

    int getStoredTypes();

    Collection<TypeStats> getTypeStats();

    Collection<HugeStack> getHugeStacks();

    default void flushBudgeted(long maxNanos) {
        if (maxNanos <= 0L) {
            flushAndAwait();
        }
    }

    default void flushAndAwait() {}

    void closeAndFlush();

    default ECOInfiniteDomainState getState() {
        return ECOInfiniteDomainState.READY;
    }

    default Optional<String> getFailureReason() {
        return Optional.empty();
    }

    default boolean isLoaded() {
        return getState() == ECOInfiniteDomainState.READY;
    }

    default boolean tickLoad() {
        return false;
    }
}
