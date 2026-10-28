package cn.dancingsnow.neoecoae.storage.infinite;

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

import net.minecraft.world.item.ItemStack;

/**
 * Bounded finite-to-infinite transfer. The sealed source stays intact until the destination is durable.
 */
public final class ECOInfiniteStorageTransfer {
    public static final int KEYS_PER_TICK = 5;
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
                        ECOInfiniteStorageEngine engine, Runnable persistSeal, Runnable complete, BiFunction<AEKey, Long, UUID> legacyReceipt) {
        return step(source, cell, domain, engine, persistSeal, complete, legacyReceipt, Map.of());
    }

    public boolean step(ItemStack source, IECOStorageMigrationCell cell, UUID domain,
                        ECOInfiniteStorageEngine engine, Runnable persistSeal, Runnable complete, BiFunction<AEKey, Long, UUID> legacyReceipt,
                        Map<AEKey, java.util.Set<UUID>> releaseCompatibility) {
        if (!isEligible(cell) || !engine.isHealthy()) return false;
        UUID migration = ECOInfiniteStorageMember.beginMigration(source, domain);
        if (cell instanceof cn.dancingsnow.neoecoae.storage.ECOStorageCell ordinary
            && engine instanceof SavedDataInfiniteStorageEngine saved && ordinary.storageSession() != null) {
            if (!ordinary.storageWritable()) return false;
            ordinary.storageData().metadata(cn.dancingsnow.neoecoae.storage.persistence.ECOCellReference.metadata(source));
            Map<AEKey, java.util.Set<UUID>> compatibility = new HashMap<>();
            if (ordinary.storageData().legacyMigration()) ordinary.storageData().amounts().visitVisible((key, amount) -> {
                UUID legacy = legacyReceipt.apply(key, amount);
                UUID sealed = UUID.nameUUIDFromBytes((migration + ":" + key.toTagGeneric()).getBytes(StandardCharsets.UTF_8));
                java.util.Set<UUID> ids = new java.util.HashSet<>(); if (legacy != null) ids.add(legacy); ids.add(sealed);
                compatibility.put(key, java.util.Set.copyOf(ids));
            });
            releaseCompatibility.forEach((key, ids) -> {
                var combined = new java.util.HashSet<>(compatibility.getOrDefault(key, java.util.Set.of()));
                combined.addAll(ids); compatibility.put(key, java.util.Set.copyOf(combined));
            });
            if (!cn.dancingsnow.neoecoae.storage.persistence.StorageTransferCoordinator.transfer(
                ordinary.storageSession(), ordinary.storageData(), domain, saved, migration, compatibility)) return false;
            cn.dancingsnow.neoecoae.storage.persistence.ECOCellReference.applyMetadata(source, ordinary.storageData().metadata());
            complete.run();
            return true;
        }
        return step(migration, source, cell, engine, persistSeal, complete, legacyReceipt);
    }

    boolean step(UUID migration, Object source, IECOStorageMigrationCell cell,
                 ECOInfiniteStorageEngine engine, Runnable persistSeal, Runnable complete, BiFunction<AEKey, Long, UUID> legacyReceipt) {
        if (!cell.isInfiniteStorageEligible() || !engine.isHealthy()) return false;
        Cursor cursor = cursors.get(migration);
        if (cursor == null || cursor.source != source || cursor.cell != cell) {
            cell.persist();
            persistSeal.run();
            cursor = new Cursor(source, cell, cell.migrationEntries());
            cursors.put(migration, cursor);
        }
        int visited = 0;
        while (visited < KEYS_PER_TICK) {
            if (cursor.key == null) {
                if (!cursor.entries.hasNext()) break;
                var entry = cursor.entries.next();
                cursor.key = entry.getKey();
                cursor.amount = entry.getLongValue();
            }
            if (cursor.amount > 0) {
                UUID legacy = legacyReceipt.apply(cursor.key, cursor.amount);
                UUID sealed = UUID.nameUUIDFromBytes(
                        (migration + ":" + cursor.key.toTagGeneric()).getBytes(StandardCharsets.UTF_8));
                if (engine.insertOnce(java.util.Set.of(legacy, sealed), cursor.key, cursor.amount) != cursor.amount) {
                    return false;
                }
            }
            cursor.key = null;
            visited++;
        }
        if (cursor.key != null || cursor.entries.hasNext() || !engine.acceptChanges().successful() || !engine.flush()) return false;
        complete.run();
        cursors.remove(migration);
        return true;
    }

    public void reset() {
        cursors.clear();
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
