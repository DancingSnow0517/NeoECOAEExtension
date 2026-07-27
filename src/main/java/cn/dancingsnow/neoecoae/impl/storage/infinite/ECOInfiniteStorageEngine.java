package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import java.util.Collection;
import java.util.UUID;

public interface ECOInfiniteStorageEngine {
    record TypeStats(AEKeyType keyType, long storedTypes, HugeAmount storedAmount) {}

    record HugeStack(AEKey key, HugeAmount amount) {}

    long insert(AEKey key, long amount, Actionable mode);

    default long insertOnce(UUID transactionId, AEKey key, long amount) {
        return insert(key, amount, Actionable.MODULATE);
    }

    long extract(AEKey key, long amount, Actionable mode);

    HugeAmount getAmount(AEKey key);

    void getAvailableStacks(KeyCounter out);

    long getRevision();

    boolean isEmpty();

    default boolean isHealthy() {
        return true;
    }

    HugeAmount getStoredAmount();

    int getStoredTypes();

    Collection<TypeStats> getTypeStats();

    Collection<HugeStack> getHugeStacks();

    /**
     * Flushes pending WAL records and schedules checkpoint work. A positive budget is a main-thread snapshot
     * budget; the actual file I/O runs on the persistence workers.
     */
    void flushBudgeted(long maxNanos);

    void closeAndFlush();

    /**
     * Returns true when the engine has finished loading its persisted state and is ready for use.
     * Before this returns true, all read operations return empty results and all write operations are
     * no-ops or block (insertOnce blocks to preserve migration correctness).
     */
    default boolean isLoaded() {
        return true;
    }

    /**
     * Called on the server thread each tick. Advances the async load state and returns true
     * exactly once — on the tick the load finishes — so callers can react (e.g. re-mount
     * storage in AE2). The default implementation returns false for always-loaded engines.
     */
    default boolean tickLoad() {
        return false;
    }
}
