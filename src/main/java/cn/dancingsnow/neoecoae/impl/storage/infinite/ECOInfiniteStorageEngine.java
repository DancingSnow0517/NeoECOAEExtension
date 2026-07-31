package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public interface ECOInfiniteStorageEngine {
    record TypeStats(AEKeyType keyType, long storedTypes, HugeAmount storedAmount) {}

    record HugeStack(AEKey key, HugeAmount amount) {}

    /** A persisted key that cannot currently be resolved because its owning mod is absent. */
    record OrphanedStack(CompoundTag encodedKey, HugeAmount amount) {}

    long insert(AEKey key, long amount, Actionable mode);

    default long insertOnce(UUID transactionId, AEKey key, long amount) {
        return insert(key, amount, Actionable.MODULATE);
    }

    /** Atomically applies one source container exactly once. Used before that source is destroyed. */
    default boolean applyTransferOnce(UUID transactionId, Collection<HugeStack> contents) {
        return false;
    }

    /** Receipts imported from V1 are retained so an interrupted old matrix migration cannot be replayed. */
    default boolean hasLegacyTransferReceipt(UUID transactionId) {
        return false;
    }

    /** Returns whether a V2 batch receipt is already persisted for this transaction. */
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

    /** Returns persisted entries that are retained but hidden until their owning mod is restored. */
    default Collection<OrphanedStack> getOrphanedStacks() {
        return java.util.List.of();
    }

    default int getOrphanedTypes() {
        return getOrphanedStacks().size();
    }

    default HugeAmount getOrphanedAmount() {
        HugeAmount total = HugeAmount.ZERO;
        for (OrphanedStack stack : getOrphanedStacks()) {
            total = total.add(stack.amount());
        }
        return total;
    }

    default boolean hasOrphanedEntries() {
        return getOrphanedTypes() > 0;
    }

    /** Compatibility bridge for the V1 call sites while they are being removed. */
    default void flushBudgeted(long maxNanos) {
        if (maxNanos <= 0L) {
            flushAndAwait();
        }
    }

    /** Forces the current SavedData snapshot to disk and verifies it by reading it back. */
    default void flushAndAwait() {
    }

    void closeAndFlush();

    default ECOInfiniteDomainState getState() {
        return ECOInfiniteDomainState.READY;
    }

    default Optional<String> getFailureReason() {
        return Optional.empty();
    }

    /**
     * Returns true when the engine has finished loading its persisted state and is ready for use.
     * Before this returns true, all read operations return empty results and all write operations are
     * no-ops or block (insertOnce blocks to preserve migration correctness).
     */
    default boolean isLoaded() {
        return getState() == ECOInfiniteDomainState.READY;
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
