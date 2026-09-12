package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.stacks.AEKey;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageMigrationCell;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

/** Bounded finite-to-infinite transfer. The sealed source stays intact until the destination is durable. */
public final class ECOInfiniteStorageTransfer {
    private final Map<UUID, Cursor> cursors = new HashMap<>();

    public static boolean isEligible(IECOStorageCell cell) {
        return cell instanceof IECOStorageMigrationCell && cell.isInfiniteStorageEligible()
            && cell.getTier() == ECOTier.L9;
    }

    /**
     * persistSeal must save the source contents and its seal before this method exposes any destination contents.
     * complete clears the source and marks it as a member, using the same live cell handler.
     */
    public boolean step(ItemStack source, IECOStorageMigrationCell cell, UUID domain,
                        ECOInfiniteStorageEngine engine, HolderLookup.Provider registries,
                        Runnable persistSeal, Runnable complete, BiFunction<AEKey, Long, UUID> legacyReceipt,
                        int keyLimit, long budgetNanos) {
        if (!isEligible(cell) || !engine.isHealthy() || keyLimit <= 0 || budgetNanos <= 0) return false;
        UUID migration = ECOInfiniteStorageMember.beginMigration(source, domain);
        return step(migration, source, cell, engine, registries, persistSeal, complete, legacyReceipt, keyLimit, budgetNanos);
    }

    boolean step(UUID migration, Object source, IECOStorageMigrationCell cell,
                 ECOInfiniteStorageEngine engine, HolderLookup.Provider registries,
                 Runnable persistSeal, Runnable complete, BiFunction<AEKey, Long, UUID> legacyReceipt,
                 int keyLimit, long budgetNanos) {
        if (!cell.isInfiniteStorageEligible() || !engine.isHealthy() || keyLimit <= 0 || budgetNanos <= 0) return false;
        Cursor cursor = cursors.get(migration);
        if (cursor == null || cursor.source != source || cursor.cell != cell) {
            cell.persist();
            persistSeal.run();
            cursor = new Cursor(source, cell, cell.migrationEntries());
            cursors.put(migration, cursor);
        }
        long started = System.nanoTime();
        int visited = 0;
        while (visited < keyLimit && System.nanoTime() - started < budgetNanos) {
            if (cursor.key == null) {
                if (!cursor.entries.hasNext()) break;
                var entry = cursor.entries.next();
                cursor.key = entry.getKey();
                cursor.amount = entry.getLongValue();
            }
            if (cursor.amount > 0) {
                UUID legacy = legacyReceipt.apply(cursor.key, cursor.amount);
                UUID transaction = engine.hasMigrationReceipt(legacy) ? legacy
                    : UUID.nameUUIDFromBytes((migration + ":" + cursor.key.toTagGeneric(registries)).getBytes(StandardCharsets.UTF_8));
                if (engine.insertOnce(transaction, cursor.key, cursor.amount) != cursor.amount) return false;
            }
            cursor.key = null;
            visited++;
        }
        if (cursor.key != null || cursor.entries.hasNext() || !engine.commit().successful()) return false;
        complete.run();
        cursors.remove(migration);
        return true;
    }

    public void reset() { cursors.clear(); }

    /** Reconciles a target against a durable plan, including a write whose chunk receipt was not saved yet. */
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
        private final Iterator<Object2LongMap.Entry<AEKey>> entries;
        private AEKey key;
        private long amount;
        private Cursor(Object source, IECOStorageMigrationCell cell, Iterator<Object2LongMap.Entry<AEKey>> entries) {
            this.source = source;
            this.cell = cell;
            this.entries = entries;
        }
    }
}
