package cn.dancingsnow.neoecoae.storage.infinite;

import cn.dancingsnow.neoecoae.api.capability.ECOFeature;

import appeng.api.stacks.AEKey;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageMigrationCell;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

/**
 * Bounded finite-to-infinite transfer. The sealed source stays intact until the destination is durable.
 * Work is limited by a per-tick {@link Budget} that every source drive of a host shares.
 */
public final class ECOInfiniteStorageTransfer {
    /** Upper bound of source drives whose seal is prepared in one tick. */
    public static final int SEALS_PER_TICK = 5;
    /** Upper bound of restored resource types per tick when leaving infinite mode. */
    public static final int RESTORE_KEYS_PER_TICK = 5;
    private final Map<UUID, Cursor> cursors = new HashMap<>();

    public enum State { COMPLETED, IN_PROGRESS, BLOCKED }

    /** Outcome of one {@link #step}; {@code block} is set only for {@link State#BLOCKED}. */
    public record StepResult(State state, ECOInfiniteStorageEngine.MigrationBlock block) {
        public static final StepResult COMPLETED = new StepResult(State.COMPLETED, null);
        public static final StepResult IN_PROGRESS = new StepResult(State.IN_PROGRESS, null);

        public static StepResult blocked(ECOInfiniteStorageEngine.MigrationBlock block) {
            return new StepResult(State.BLOCKED, block);
        }

        static StepResult blocked(ECOInfiniteStorageEngine.MigrationBlock.Kind kind, String detail) {
            return blocked(new ECOInfiniteStorageEngine.MigrationBlock(kind, detail));
        }
    }

    /** Receipt bound to the source drive's slot, computed from the key tag that is serialized once per resource. */
    @FunctionalInterface
    public interface LegacyReceipt {
        UUID apply(AEKey key, String keyTag, long amount);
    }

    /**
     * One tick's allowance: a wall-clock deadline plus a resource-type cap. The first resource of a tick is always
     * allowed, so a migration keeps advancing however slow the server is.
     */
    public static final class Budget {
        private final LongSupplier clock;
        private final long deadline;
        private final int maxKeys;
        private int used;

        public Budget(LongSupplier clock, long nanos, int maxKeys) {
            this.clock = clock;
            this.deadline = clock.getAsLong() + Math.max(0L, nanos);
            this.maxKeys = Math.max(1, maxKeys);
        }

        public boolean hasRemaining() {
            return used == 0 || used < maxKeys && clock.getAsLong() - deadline < 0L;
        }

        void consume() {
            used++;
        }

        public int used() {
            return used;
        }
    }

    public static boolean isEligible(IECOStorageCell cell) {
        return cell instanceof IECOStorageMigrationCell && cell.isInfiniteStorageEligible()
                && cell.getTier().supportsFeature(ECOFeature.INFINITE_STORAGE_CELL);
    }

    /** Slot-bound receipt. Domains persist these ids, so the hashed text must never change. */
    public static UUID legacyReceipt(UUID domainId, String direction, long position, String keyTag, long amount) {
        String value = domainId + ":" + direction + ":" + position + ":" + keyTag + ":" + amount;
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    /** Seal-bound receipt. Domains persist these ids, so the hashed text must never change. */
    public static UUID sealedReceipt(UUID migration, String keyTag) {
        return UUID.nameUUIDFromBytes((migration + ":" + keyTag).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * persistSeal must save the source contents and its seal before this method exposes any destination contents.
     * complete clears the source and marks it as a member, using the same live cell handler.
     */
    public StepResult step(ItemStack source, IECOStorageMigrationCell cell, UUID domain,
                           ECOInfiniteStorageEngine engine, HolderLookup.Provider registries,
                           Runnable persistSeal, Runnable complete, LegacyReceipt legacyReceipt, Budget budget) {
        if (!isEligible(cell)) {
            return StepResult.blocked(ECOInfiniteStorageEngine.MigrationBlock.Kind.SOURCE_INELIGIBLE, "");
        }
        ECOInfiniteStorageEngine.MigrationBlock block = engine.migrationBlock();
        if (block != null) return StepResult.blocked(block);
        UUID migration = ECOInfiniteStorageMember.beginMigration(source, domain);
        return step(migration, source, cell, engine, registries, persistSeal, complete, legacyReceipt, budget);
    }

    StepResult step(UUID migration, Object source, IECOStorageMigrationCell cell,
                    ECOInfiniteStorageEngine engine, HolderLookup.Provider registries,
                    Runnable persistSeal, Runnable complete, LegacyReceipt legacyReceipt, Budget budget) {
        if (!cell.isInfiniteStorageEligible()) {
            return StepResult.blocked(ECOInfiniteStorageEngine.MigrationBlock.Kind.SOURCE_INELIGIBLE, "");
        }
        ECOInfiniteStorageEngine.MigrationBlock block = engine.migrationBlock();
        if (block != null) return StepResult.blocked(block);
        Cursor cursor = cursors.get(migration);
        if (cursor == null || cursor.source != source || cursor.cell != cell) {
            if (!budget.hasRemaining()) return StepResult.IN_PROGRESS;
            cell.persist();
            persistSeal.run();
            cursor = new Cursor(source, cell, cell.migrationEntries());
            cursors.put(migration, cursor);
        }
        while (cursor.index < cursor.keys.length && budget.hasRemaining()) {
            AEKey key = cursor.keys[cursor.index];
            long amount = cursor.amounts[cursor.index];
            if (amount > 0) {
                // Serialized once: both receipts hash the same text the former per-receipt serialization produced.
                if (cursor.tag == null) cursor.tag = String.valueOf(key.toTagGeneric(registries));
                UUID legacy = legacyReceipt.apply(key, cursor.tag, amount);
                UUID sealed = sealedReceipt(migration, cursor.tag);
                if (engine.insertOnce(Set.of(legacy, sealed), key, amount) != amount) {
                    block = engine.migrationBlock();
                    return StepResult.blocked(block != null ? block : new ECOInfiniteStorageEngine.MigrationBlock(
                            ECOInfiniteStorageEngine.MigrationBlock.Kind.INSERT_REJECTED, String.valueOf(key)));
                }
            }
            cursor.tag = null;
            cursor.index++;
            budget.consume();
        }
        if (cursor.index < cursor.keys.length) return StepResult.IN_PROGRESS;
        var commit = engine.commit();
        if (!commit.successful()) {
            return StepResult.blocked(ECOInfiniteStorageEngine.MigrationBlock.Kind.COMMIT_FAILED,
                    commit.failure() == null ? "" : commit.failure());
        }
        complete.run();
        cursors.remove(migration);
        return StepResult.COMPLETED;
    }

    /** Share of the source's resource types already handed to the domain in this session, in [0, 1]. */
    public double progress(UUID migration) {
        Cursor cursor = migration == null ? null : cursors.get(migration);
        if (cursor == null) return 0.0D;
        return cursor.keys.length == 0 ? 1.0D : (double) cursor.index / cursor.keys.length;
    }

    public void reset() {
        cursors.clear();
    }

    /**
     * Reconciles a target against a durable plan, including a write whose chunk receipt was not saved yet.
     */
    public static boolean restoreTarget(IECOStorageMigrationCell cell, AEKey key,
                                        ECOInfiniteStorageEngine.RestoreTargetAmounts goal, IActionSource source) {
        long current = cell.getMigrationAmount(key);
        if (current < goal.before() || current > goal.after()) {
            throw new IllegalStateException("Restore target quantity differs from its saved plan");
        }
        long remaining = goal.after() - current;
        if (remaining == 0) return true;
        long inserted = cell.insertForMigration(key, remaining, Actionable.MODULATE, source);
        return inserted == remaining && cell.getMigrationAmount(key) == goal.after();
    }

    private static final class Cursor {
        private final Object source;
        private final IECOStorageMigrationCell cell;
        // A sealed source cannot change, so its entries are captured once, in a stable order.
        private final AEKey[] keys;
        private final long[] amounts;
        private int index;
        private String tag;

        private Cursor(Object source, IECOStorageMigrationCell cell, Iterator<Object2LongMap.Entry<AEKey>> entries) {
            this.source = source;
            this.cell = cell;
            List<AEKey> keyList = new ArrayList<>();
            LongArrayList amountList = new LongArrayList();
            while (entries.hasNext()) {
                var entry = entries.next();
                keyList.add(entry.getKey());
                amountList.add(entry.getLongValue());
            }
            this.keys = keyList.toArray(AEKey[]::new);
            this.amounts = amountList.toLongArray();
        }
    }
}
