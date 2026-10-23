package cn.dancingsnow.neoecoae.impl.storage.transfer;

import appeng.api.storage.MEStorage;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import java.util.Collection;

/** Prevents a network migration from extracting its own destination inventories. */
public final class StorageExtractionExclusions implements AutoCloseable {
    private static final ThreadLocal<ReferenceSet<MEStorage>> ACTIVE = new ThreadLocal<>();
    private final ReferenceSet<MEStorage> previous;

    private StorageExtractionExclusions(Collection<? extends MEStorage> inventories) {
        previous = ACTIVE.get();
        ReferenceSet<MEStorage> excluded = new ReferenceOpenHashSet<>();
        if (previous != null) excluded.addAll(previous);
        excluded.addAll(inventories);
        ACTIVE.set(excluded);
    }

    public static StorageExtractionExclusions open(Collection<? extends MEStorage> inventories) {
        return new StorageExtractionExclusions(inventories);
    }

    public static boolean contains(MEStorage storage) {
        ReferenceSet<MEStorage> excluded = ACTIVE.get();
        return excluded != null && excluded.contains(storage);
    }

    @Override
    public void close() {
        if (previous == null) ACTIVE.remove();
        else ACTIVE.set(previous);
    }
}
