package cn.dancingsnow.neoecoae.storage.transfer;

import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.storage.bulk.ECOBulkStorageCell;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import java.util.Collection;
import java.util.List;

/** Prevents a network migration from extracting its own destination inventories. */
public final class StorageExtractionExclusions implements AutoCloseable {
    private static final ThreadLocal<ReferenceSet<MEStorage>> ACTIVE = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> BULK_MIGRATION = new ThreadLocal<>();
    private final ReferenceSet<MEStorage> previous;
    private final Boolean previousBulkMigration;

    private StorageExtractionExclusions(Collection<? extends MEStorage> inventories) {
        previous = ACTIVE.get();
        previousBulkMigration = BULK_MIGRATION.get();
        ReferenceSet<MEStorage> excluded = new ReferenceOpenHashSet<>();
        if (previous != null) excluded.addAll(previous);
        excluded.addAll(inventories);
        ACTIVE.set(excluded);
    }

    public static StorageExtractionExclusions open(Collection<? extends MEStorage> inventories) {
        return new StorageExtractionExclusions(inventories);
    }

    /** Bulk migrations may draw from ordinary storage, never from another bulk matrix. */
    public static StorageExtractionExclusions openBulkMigration() {
        var scope = new StorageExtractionExclusions(List.of());
        BULK_MIGRATION.set(true);
        return scope;
    }

    public static boolean contains(MEStorage storage) {
        ReferenceSet<MEStorage> excluded = ACTIVE.get();
        return excluded != null && excluded.contains(storage)
            || Boolean.TRUE.equals(BULK_MIGRATION.get()) && storage instanceof ECOBulkStorageCell;
    }

    @Override
    public void close() {
        if (previous == null) ACTIVE.remove();
        else ACTIVE.set(previous);
        if (previousBulkMigration == null) BULK_MIGRATION.remove();
        else BULK_MIGRATION.set(previousBulkMigration);
    }
}
