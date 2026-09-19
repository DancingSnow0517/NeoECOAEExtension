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

    /** Local entry errors do not imply that the healthy inventory is unavailable. */
    default Collection<String> getEntryFailures() {
        return java.util.List.of();
    }

    /** Whole-domain transfers require every source record to be accounted for. */
    default boolean canTransfer() {
        return isHealthy() && !hasOrphanedEntries() && getEntryFailures().isEmpty();
    }

    /** Returns whether missing-mod entries still need to be shown to an administrator. */
    default boolean hasUnacknowledgedOrphanedEntries() {
        return hasOrphanedEntries();
    }

    /** Persists acknowledgement of the current missing-mod entry set without deleting those entries. */
    default boolean acknowledgeOrphanedEntries() {
        return false;
    }

    default void flushBudgeted(long maxNanos) {
        if (maxNanos <= 0L) {
            flushAndAwait();
        }
    }

    default void flushAndAwait() {}

    default boolean restoreTo(
            java.util.List<cn.dancingsnow.neoecoae.impl.storage.StorageTransferJournal.Snapshot> targets) {
        return false;
    }

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
