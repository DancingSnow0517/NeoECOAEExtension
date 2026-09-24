package cn.dancingsnow.neoecoae.impl.storage.transfer;

import appeng.api.storage.MEStorage;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Prevents a network migration from extracting its own destination inventories. */
public final class StorageExtractionExclusions implements AutoCloseable {
    private static final ThreadLocal<Set<MEStorage>> ACTIVE = new ThreadLocal<>();
    private final Set<MEStorage> previous;

    private StorageExtractionExclusions(Collection<? extends MEStorage> inventories) {
        previous = ACTIVE.get();
        Set<MEStorage> excluded = Collections.newSetFromMap(new IdentityHashMap<>());
        if (previous != null) excluded.addAll(previous);
        excluded.addAll(inventories);
        ACTIVE.set(excluded);
    }

    public static StorageExtractionExclusions open(Collection<? extends MEStorage> inventories) {
        return new StorageExtractionExclusions(inventories);
    }

    public static boolean contains(MEStorage storage) {
        Set<MEStorage> excluded = ACTIVE.get();
        return excluded != null && excluded.contains(storage);
    }

    @Override
    public void close() {
        if (previous == null) ACTIVE.remove();
        else ACTIVE.set(previous);
    }
}
