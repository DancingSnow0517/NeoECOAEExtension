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

    default BigInteger extract(AEKey key, BigInteger amount, Actionable mode) {
        return BigInteger.valueOf(extract(key, amount.min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact(), mode));
    }

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
     * Why this domain cannot accept migrated contents right now. {@code code} selects the
     * {@code gui.neoecoae.storage.migration.blocked.<code>} translation; {@code detail} is its single argument.
     */
    record MigrationBlock(String code, String detail) {
        public MigrationBlock {
            code = code == null || code.isEmpty() ? "unknown" : code;
            detail = detail == null ? "" : detail;
        }

        public String translationKey() {
            return "gui.neoecoae.storage.migration.blocked." + code;
        }

        /** Server-side diagnostic text for {@link cn.dancingsnow.neoecoae.storage.StorageFaults}. */
        public String message() {
            String base = switch (code) {
                case "unreadable" -> "Infinite storage domain is unreadable";
                case "read_only" -> "Infinite storage domain is in read-only recovery";
                case "write_failed" -> "Infinite storage snapshot write failed";
                case "undecodable" -> "Infinite storage domain retains undecodable entries";
                case "unresolved_metadata" -> "Infinite storage domain retains unresolved transfer metadata";
                case "not_writable" -> "Infinite storage domain is not writable";
                case "source_unavailable" -> "Source disk is unavailable";
                case "rejected" -> "Infinite storage domain rejected a migrated resource";
                case "commit_failed" -> "Infinite storage domain did not accept the migration commit";
                case "not_durable" -> "Migration could not be saved yet";
                default -> "Migration is blocked";
            };
            return detail.isEmpty() ? base : base + ": " + detail;
        }
    }

    /** {@code null} when migrated contents may be imported; otherwise the reason a migration cannot progress. */
    default MigrationBlock migrationBlock() {
        return isHealthy() ? null : new MigrationBlock("not_writable", "");
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

    /** Accepted in memory; use flush() before retiring another independently saved inventory. */
    default CommitResult acceptChanges() { return commit(); }

    /** True only after the current accepted revision reached the authoritative snapshot. */
    default boolean flush() { return false; }

    default long revision() {
        return 0L;
    }

    /** See {@link cn.dancingsnow.neoecoae.storage.StorageListingVersion}; unlike {@link #revision()}, also covers quantities. */
    default long listingVersion() {
        return cn.dancingsnow.neoecoae.storage.StorageListingVersion.UNKNOWN;
    }

    /** See {@link cn.dancingsnow.neoecoae.storage.StorageListingDelta}; false when only a full listing is valid. */
    default boolean visitListingChanges(long since, long current, java.util.function.ObjLongConsumer<AEKey> visitor) {
        return false;
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

    record RestoreTargetAmounts(BigInteger beforeExact, BigInteger afterExact, BigInteger transferredExact) {
        public RestoreTargetAmounts(long before, long after, long transferred) {
            this(BigInteger.valueOf(before), BigInteger.valueOf(after), BigInteger.valueOf(transferred));
        }
        public RestoreTargetAmounts {
            if (beforeExact == null || afterExact == null || transferredExact == null || beforeExact.signum() < 0
                || afterExact.compareTo(beforeExact) < 0 || transferredExact.compareTo(afterExact.subtract(beforeExact)) < 0
                || transferredExact.compareTo(afterExact) > 0) throw new IllegalArgumentException("Invalid restore target amounts");
        }
        public long before() { return beforeExact.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue(); }
        public long after() { return afterExact.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue(); }
        public long transferred() { return transferredExact.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue(); }
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
