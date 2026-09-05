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

    /**
     * Inserts at most once for the given transaction id. Repeating a call that already succeeded reports the same
     * amount again without storing it a second time, so an interrupted migration can simply be run again.
     */
    default long insertOnce(UUID transactionId, AEKey key, long amount) {
        return insert(key, amount, Actionable.MODULATE);
    }

    long extract(AEKey key, long amount, Actionable mode);

    HugeAmount getAmount(AEKey key);

    void getAvailableStacks(KeyCounter out);

    boolean isEmpty();

    /**
     * {@code true} when this domain currently accepts new writes. Reads are never gated by this: an implementation
     * whose data is only temporarily unwritable must keep serving {@link #getAmount} and {@link #getAvailableStacks}.
     */
    default boolean isHealthy() {
        return true;
    }

    /**
     * Fine-grained state; see {@link ECOInfiniteStorageData.DomainStatus}. Defaults to {@code HEALTHY} so existing
     * implementors are unaffected.
     */
    default ECOInfiniteStorageData.DomainStatus status() {
        return ECOInfiniteStorageData.DomainStatus.HEALTHY;
    }

    /**
     * Stricter than {@link #isHealthy()}: whether the domain may be migrated out of, or infinite mode left, right
     * now. In addition to being writable this also requires no unresolved raw entries, since those are invisible to
     * {@link #getAvailableStacks} and would otherwise be silently left behind.
     */
    default boolean canExitOrRestore() {
        return isHealthy();
    }

    Collection<TypeStats> getTypeStats();

    Collection<HugeStack> getHugeStacks();

    default boolean hasHugeStacks() { return !getHugeStacks().isEmpty(); }

    default Collection<HugeStack> getLargestStacks(int limit) {
        return getHugeStacks().stream().limit(Math.max(0, limit)).toList();
    }

    default boolean hasMigrationReceipt(UUID transactionId) { return false; }

    /**
     * Writes the domain to disk right away. Ordinary inserts and extracts only mark the world data dirty and are
     * saved with the world; this is reserved for the boundaries of a migration, where the same items briefly exist
     * in both the domain and a storage cell.
     */
    record CommitResult(boolean successful, long durableRevision, String failure) {}

    CommitResult commit();

    default long revision() { return 0L; }

    default void tick(long gameTime) {}

    default boolean reserveRestore(AEKey key, UUID transaction) { return false; }

    default boolean reserveRestore(AEKey key, UUID transaction, java.util.Set<UUID> targets) { return false; }

    default java.util.Set<UUID> restoreTargetIds(AEKey key) { return java.util.Set.of(); }

    default UUID restoreTransaction(AEKey key) { return null; }

    default boolean hasPendingRestore() { return false; }

    default void failRestore(AEKey key, String reason) {}

    default boolean finishRestore(AEKey key, UUID transaction) { return false; }

    default HugeAmount getRestoreAmount(AEKey key) { return getAmount(key); }

    default void getRestoreStacks(KeyCounter out) { getAvailableStacks(out); }

    /**
     * Legacy cleanup hook. Journal-backed domains retain ownership receipts to reject stale source copies.
     */
    void clearMigrationReceipts();
}
