package cn.dancingsnow.neoecoae.storage.infinite;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;

import java.util.Collection;
import java.math.BigInteger;
import java.util.UUID;

public interface ECOInfiniteStorageEngine {
    record TypeStats(AEKeyType keyType, long storedTypes, HugeAmount storedAmount) {
    }

    long insert(AEKey key, long amount, Actionable mode);

    BigInteger insert(AEKey key, BigInteger amount, Actionable mode);

    BigInteger usedBytes();

    /**
     * Inserts at most once for the given transaction id. Repeating a call that already succeeded reports the same
     * amount again without storing it a second time, so an interrupted migration can simply be run again.
     */
    default long insertOnce(UUID transactionId, AEKey key, long amount) {
        return insert(key, amount, Actionable.MODULATE);
    }

    /**
     * Records equivalent transaction identities atomically. This lets migration retries be
     * recognized both from the disk's seal id and from its original controller slot.
     */
    default long insertOnce(java.util.Set<UUID> transactionIds, AEKey key, long amount) {
        UUID transactionId = transactionIds == null || transactionIds.isEmpty()
                ? null : transactionIds.iterator().next();
        return insertOnce(transactionId, key, amount);
    }

    long extract(AEKey key, long amount, Actionable mode);

    HugeAmount getAmount(AEKey key);

    default boolean contains(AEKey key) { return !getAmount(key).isZero(); }

    /** Full visible exact listing, including long-sized keys whose network aggregate may overflow. */
    default void visitExactAmounts(java.util.function.BiConsumer<AEKey, BigInteger> visitor) {
        KeyCounter keys = new KeyCounter();
        getAvailableStacks(keys);
        for (var entry : keys) visitor.accept(entry.getKey(), getAmount(entry.getKey()).toBigInteger());
    }

    default boolean canUseMountedStorage() { return isHealthy() && !hasPendingRestore(); }

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

    /**
     * Why a migration into this domain cannot advance right now, or {@code null} when it can. Mirrors the
     * {@link #insertOnce(java.util.Set, AEKey, long)} admission check so a stalled migration can say why.
     */
    default MigrationBlock migrationBlock() {
        return isHealthy() ? null : new MigrationBlock(MigrationBlock.Kind.NOT_WRITABLE, "");
    }

    /** A concrete reason a finite-to-infinite migration is waiting. {@code detail} is untranslated diagnostics. */
    record MigrationBlock(Kind kind, String detail) {
        public enum Kind {
            UNREADABLE, RECOVERY_READ_ONLY, WRITE_FAILED, UNDECODABLE_ENTRIES, UNRESOLVED_METADATA, NOT_WRITABLE,
            INSERT_REJECTED, COMMIT_FAILED, SOURCE_INELIGIBLE;

            public String translationKey() {
                return "gui.neoecoae.storage.migration.blocked." + name().toLowerCase(java.util.Locale.ROOT);
            }
        }

        public MigrationBlock {
            if (kind == null) throw new IllegalArgumentException("kind");
            detail = detail == null ? "" : detail;
        }

        public net.minecraft.network.chat.Component toComponent() {
            return net.minecraft.network.chat.Component.translatable(kind.translationKey(), detail);
        }

        /** Untranslated form for logs and {@link cn.dancingsnow.neoecoae.storage.StorageFaults}. */
        public String describe() {
            return detail.isEmpty() ? "Migration blocked: " + kind : "Migration blocked: " + kind + " (" + detail + ")";
        }
    }

    Collection<TypeStats> getTypeStats();

    boolean hasHugeStacks();

    default boolean hasMigrationReceipt(UUID transactionId) {
        return false;
    }

    /**
     * Marks a migration boundary: inventory, receipts and restore plans have advanced together and are handed to the
     * vanilla SavedData cycle, which writes them in one snapshot on the next world save. No I/O happens here;
     * {@code successful} only reports whether the domain accepts the change.
     */
    record CommitResult(boolean successful, long revision, String failure) {
    }

    CommitResult commit();

    default long revision() {
        return 0L;
    }

    /** See {@link cn.dancingsnow.neoecoae.storage.StorageListingVersion}; unlike {@link #revision()}, also covers quantities. */
    default long listingVersion() {
        return cn.dancingsnow.neoecoae.storage.StorageListingVersion.UNKNOWN;
    }

    /** See {@link cn.dancingsnow.neoecoae.storage.StorageListingDelta}; false requires a full listing. */
    default boolean visitListingChanges(long since, long current, java.util.function.Consumer<AEKey> changed) {
        return false;
    }

    /** Amount {@link #getAvailableStacks} currently lists for {@code key}, or 0 when it is hidden. */
    default long listedAmount(AEKey key) {
        KeyCounter keys = new KeyCounter();
        getAvailableStacks(keys);
        return Math.max(0L, keys.get(key));
    }

    default boolean reserveRestore(AEKey key, UUID transaction) {
        return false;
    }

    default boolean reserveRestore(AEKey key, UUID transaction, java.util.Set<UUID> targets) {
        return false;
    }

    default java.util.Set<UUID> restoreTargetIds(AEKey key) {
        return java.util.Set.of();
    }

    default UUID restoreTransaction(AEKey key) {
        return null;
    }

    default boolean hasPendingRestore() {
        return false;
    }

    default void failRestore(AEKey key, String reason) {
    }

    default boolean finishRestore(AEKey key, UUID transaction) {
        return false;
    }

    record RestoreTargetAmounts(long before, long after, long transferred) {
        public RestoreTargetAmounts {
            if (before < 0 || after < before || transferred < after - before || transferred > after) {
                throw new IllegalArgumentException("Invalid restore target amounts");
            }
        }
    }

    boolean reserveRestores(java.util.Map<AEKey, UUID> transactions, java.util.Set<UUID> targets,
                            java.util.Map<AEKey, java.util.Map<UUID, RestoreTargetAmounts>> plans);

    java.util.Map<UUID, RestoreTargetAmounts> restorePlan(AEKey key);

    boolean finishRestores(java.util.Map<AEKey, UUID> transactions);

    default HugeAmount getRestoreAmount(AEKey key) {
        return getAmount(key);
    }

    default void getRestoreStacks(KeyCounter out) {
        getAvailableStacks(out);
    }

}
