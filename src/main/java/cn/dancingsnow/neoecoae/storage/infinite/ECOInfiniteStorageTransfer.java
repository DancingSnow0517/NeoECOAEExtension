package cn.dancingsnow.neoecoae.storage.infinite;

import appeng.api.stacks.AEKey;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageMigrationCell;
import cn.dancingsnow.neoecoae.config.NEConfig;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;

import net.minecraft.world.item.ItemStack;

/**
 * Bounded finite-to-infinite transfer. The sealed source stays intact until the destination is durable.
 */
public final class ECOInfiniteStorageTransfer {
    /** Restore completions handled per tick. Migration uses {@link Budget}; sealing uses {@link #SEALS_PER_TICK}. */
    public static final int KEYS_PER_TICK = 5;
    /** Source drives sealed (and their contents saved) per tick before they may be migrated. */
    public static final int SEALS_PER_TICK = 5;
    private final Map<UUID, Cursor> cursors = new HashMap<>();

    /** Legacy controller receipt; {@code encodedKey} is {@code key.toTagGeneric().toString()}, computed once. */
    @FunctionalInterface
    public interface LegacyReceipt {
        UUID apply(AEKey key, String encodedKey, long amount);
    }

    public enum Outcome { COMPLETE, IN_PROGRESS, BLOCKED }

    /** Result of one migration attempt; {@code block} is set only for {@link Outcome#BLOCKED}. */
    public record StepResult(Outcome outcome, ECOInfiniteStorageEngine.MigrationBlock block) {
        static final StepResult COMPLETE = new StepResult(Outcome.COMPLETE, null);
        static final StepResult IN_PROGRESS = new StepResult(Outcome.IN_PROGRESS, null);

        static StepResult blocked(ECOInfiniteStorageEngine.MigrationBlock block) {
            return new StepResult(Outcome.BLOCKED,
                block == null ? new ECOInfiniteStorageEngine.MigrationBlock("unknown", "") : block);
        }

        static StepResult blocked(String code, String detail) {
            return blocked(new ECOInfiniteStorageEngine.MigrationBlock(code, detail));
        }

        public boolean complete() { return outcome == Outcome.COMPLETE; }
        public boolean blocked() { return outcome == Outcome.BLOCKED; }
    }

    /**
     * One tick's migration allowance, shared by every drive of a host. The first resource is always allowed, so a
     * migration progresses even when a tick starts past its deadline.
     */
    public static final class Budget {
        private final LongSupplier clock;
        private final long deadline;
        private final int maxKeys;
        private int used;

        public Budget(LongSupplier clock, long budgetNanos, int maxKeys) {
            this.clock = clock;
            this.deadline = clock.getAsLong() + Math.max(0L, budgetNanos);
            this.maxKeys = Math.max(1, maxKeys);
        }

        public static Budget perTick() {
            return new Budget(System::nanoTime, NEConfig.infiniteMigrationTickBudgetMicros * 1_000L,
                NEConfig.infiniteMigrationMaxKeysPerTick);
        }

        public boolean canContinue() {
            return used == 0 || used < maxKeys && clock.getAsLong() - deadline < 0L;
        }

        public void consume(int keys) {
            int amount = Math.max(1, keys);
            used = used > Integer.MAX_VALUE - amount ? Integer.MAX_VALUE : used + amount;
        }

        public int used() { return used; }
    }

    public static boolean isEligible(IECOStorageCell cell) {
        return cell instanceof IECOStorageMigrationCell && cell.isInfiniteStorageEligible()
                && cell.getTier() == ECOTier.L9;
    }

    /** {@code key.toTagGeneric()} in the exact text both receipt formulas have always hashed. */
    public static String encodedKey(AEKey key) {
        return key.toTagGeneric().toString();
    }

    /** Receipt of the sealed source; byte-identical to {@code migration + ":" + key.toTagGeneric()}. */
    public static UUID sealedReceipt(UUID migration, String encodedKey) {
        return UUID.nameUUIDFromBytes((migration + ":" + encodedKey).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * persistSeal must save the source contents and its seal before this method exposes any destination contents.
     * complete clears the source and marks it as a member, using the same live cell handler.
     */
    public boolean step(ItemStack source, IECOStorageMigrationCell cell, UUID domain,
                        ECOInfiniteStorageEngine engine, Runnable persistSeal, Runnable complete, BiFunction<AEKey, Long, UUID> legacyReceipt) {
        return step(source, cell, domain, engine, persistSeal, complete, legacyReceipt, Map.of());
    }

    public boolean step(ItemStack source, IECOStorageMigrationCell cell, UUID domain,
                        ECOInfiniteStorageEngine engine, Runnable persistSeal, Runnable complete, BiFunction<AEKey, Long, UUID> legacyReceipt,
                        Map<AEKey, java.util.Set<UUID>> releaseCompatibility) {
        return migrate(source, cell, domain, engine, persistSeal, complete, adapt(legacyReceipt), releaseCompatibility,
            Budget.perTick()).complete();
    }

    public StepResult migrate(ItemStack source, IECOStorageMigrationCell cell, UUID domain,
                              ECOInfiniteStorageEngine engine, Runnable persistSeal, Runnable complete, LegacyReceipt legacyReceipt,
                              Map<AEKey, java.util.Set<UUID>> releaseCompatibility, Budget budget) {
        if (!isEligible(cell)) return StepResult.blocked("source_unavailable", "");
        if (!engine.isHealthy()) return StepResult.blocked(domainBlock(engine));
        UUID migration = ECOInfiniteStorageMember.beginMigration(source, domain);
        if (cell instanceof cn.dancingsnow.neoecoae.storage.ECOStorageCell ordinary
            && engine instanceof SavedDataInfiniteStorageEngine saved && ordinary.storageSession() != null) {
            if (!ordinary.storageWritable()) return sourceBlocked(ordinary);
            ordinary.storageData().metadata(cn.dancingsnow.neoecoae.storage.persistence.ECOCellReference.metadata(source));
            int keys = ordinary.storageData().amounts().size();
            Map<AEKey, java.util.Set<UUID>> compatibility = new HashMap<>();
            if (ordinary.storageData().legacyMigration()) ordinary.storageData().amounts().visitVisible((key, amount) -> {
                String encoded = encodedKey(key);
                UUID legacy = legacyReceipt.apply(key, encoded, amount);
                UUID sealed = sealedReceipt(migration, encoded);
                java.util.Set<UUID> ids = new java.util.HashSet<>(); if (legacy != null) ids.add(legacy); ids.add(sealed);
                compatibility.put(key, java.util.Set.copyOf(ids));
            });
            releaseCompatibility.forEach((key, ids) -> {
                var combined = new java.util.HashSet<>(compatibility.getOrDefault(key, java.util.Set.of()));
                combined.addAll(ids); compatibility.put(key, java.util.Set.copyOf(combined));
            });
            if (!cn.dancingsnow.neoecoae.storage.persistence.StorageTransferCoordinator.transfer(
                ordinary.storageSession(), ordinary.storageData(), domain, saved, migration, compatibility)) {
                return ordinaryBlocked(ordinary, engine);
            }
            cn.dancingsnow.neoecoae.storage.persistence.ECOCellReference.applyMetadata(source, ordinary.storageData().metadata());
            complete.run();
            // The disk moves as one durable transaction; it still counts every resource it carried.
            budget.consume(keys);
            return StepResult.COMPLETE;
        }
        return migrate(migration, source, cell, engine, persistSeal, complete, legacyReceipt, budget);
    }

    boolean step(UUID migration, Object source, IECOStorageMigrationCell cell,
                 ECOInfiniteStorageEngine engine, Runnable persistSeal, Runnable complete, BiFunction<AEKey, Long, UUID> legacyReceipt) {
        return migrate(migration, source, cell, engine, persistSeal, complete, adapt(legacyReceipt), Budget.perTick()).complete();
    }

    StepResult migrate(UUID migration, Object source, IECOStorageMigrationCell cell,
                       ECOInfiniteStorageEngine engine, Runnable persistSeal, Runnable complete, LegacyReceipt legacyReceipt,
                       Budget budget) {
        if (!cell.isInfiniteStorageEligible()) return StepResult.blocked("source_unavailable", "");
        if (!engine.isHealthy()) return StepResult.blocked(domainBlock(engine));
        Cursor cursor = cursors.get(migration);
        if (cursor == null || cursor.source != source || cursor.cell != cell) {
            cell.persist();
            persistSeal.run();
            // The source is sealed, so this snapshot is its complete and final contents.
            List<Entry> entries = new ArrayList<>();
            var iterator = cell.migrationEntries();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                entries.add(new Entry(entry.getKey(), entry.getLongValue()));
            }
            cursor = new Cursor(source, cell, entries);
            cursors.put(migration, cursor);
        }
        while (cursor.index < cursor.entries.size()) {
            if (!budget.canContinue()) return StepResult.IN_PROGRESS;
            Entry entry = cursor.entries.get(cursor.index);
            if (entry.amount > 0) {
                String encoded = encodedKey(entry.key);
                UUID legacy = legacyReceipt.apply(entry.key, encoded, entry.amount);
                UUID sealed = sealedReceipt(migration, encoded);
                if (engine.insertOnce(java.util.Set.of(legacy, sealed), entry.key, entry.amount) != entry.amount) {
                    var block = engine.migrationBlock();
                    return StepResult.blocked(block != null ? block
                        : new ECOInfiniteStorageEngine.MigrationBlock("rejected", String.valueOf(entry.key)));
                }
            }
            cursor.index++;
            budget.consume(1);
        }
        var accepted = engine.acceptChanges();
        if (!accepted.successful()) {
            var block = engine.migrationBlock();
            return StepResult.blocked(block != null ? block
                : new ECOInfiniteStorageEngine.MigrationBlock("commit_failed", accepted.failure()));
        }
        if (!engine.flush()) {
            var block = engine.migrationBlock();
            return StepResult.blocked(block != null ? block : new ECOInfiniteStorageEngine.MigrationBlock("not_durable", ""));
        }
        complete.run();
        cursors.remove(migration);
        return StepResult.COMPLETE;
    }

    /** Share of a started per-resource migration already imported, in {@code [0, 1]}; 0 when not started. */
    public double progress(UUID migration) {
        Cursor cursor = cursors.get(migration);
        if (cursor == null) return 0.0D;
        return cursor.entries.isEmpty() ? 1.0D : (double) cursor.index / cursor.entries.size();
    }

    public void reset() {
        cursors.clear();
    }

    private static LegacyReceipt adapt(BiFunction<AEKey, Long, UUID> legacyReceipt) {
        return (key, encoded, amount) -> legacyReceipt.apply(key, amount);
    }

    private static ECOInfiniteStorageEngine.MigrationBlock domainBlock(ECOInfiniteStorageEngine engine) {
        var block = engine.migrationBlock();
        return block != null ? block : new ECOInfiniteStorageEngine.MigrationBlock("not_writable", "");
    }

    private static StepResult sourceBlocked(cn.dancingsnow.neoecoae.storage.ECOStorageCell ordinary) {
        String failure = ordinary.storageData().failure();
        return StepResult.blocked("source_unavailable", failure == null ? "" : failure);
    }

    private static StepResult ordinaryBlocked(cn.dancingsnow.neoecoae.storage.ECOStorageCell ordinary,
                                              ECOInfiniteStorageEngine engine) {
        var block = engine.migrationBlock();
        if (block != null) return StepResult.blocked(block);
        if (!ordinary.storageWritable()) return sourceBlocked(ordinary);
        String failure = ordinary.storageData().failure();
        return StepResult.blocked("not_durable", failure == null ? "" : failure);
    }

    /**
     * Reconciles a target against a durable plan, including a write whose chunk receipt was not saved yet.
     */
    public static boolean restoreTarget(IECOStorageMigrationCell cell, AEKey key,
                                        ECOInfiniteStorageEngine.RestoreTargetAmounts goal, IActionSource source) {
        java.math.BigInteger current = cell.getMigrationAmountBigInteger(key);
        if (current.compareTo(goal.beforeExact()) < 0 || current.compareTo(goal.afterExact()) > 0) {
            throw new IllegalStateException("Restore target quantity differs from its saved plan");
        }
        java.math.BigInteger remaining = goal.afterExact().subtract(current);
        if (remaining.signum() == 0) return true;
        java.math.BigInteger inserted = cell.insertForMigrationBigInteger(key, remaining, Actionable.MODULATE, source);
        return inserted.equals(remaining) && cell.getMigrationAmountBigInteger(key).equals(goal.afterExact());
    }

    private record Entry(AEKey key, long amount) {
    }

    private static final class Cursor {
        private final Object source;
        private final IECOStorageMigrationCell cell;
        private final List<Entry> entries;
        private int index;

        private Cursor(Object source, IECOStorageMigrationCell cell, List<Entry> entries) {
            this.source = source;
            this.cell = cell;
            this.entries = entries;
        }
    }
}
