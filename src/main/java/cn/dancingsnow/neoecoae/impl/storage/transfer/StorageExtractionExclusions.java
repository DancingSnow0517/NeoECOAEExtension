package cn.dancingsnow.neoecoae.impl.storage.transfer;

import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.core.concurrent.ThreadScope;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Prevents a network migration from extracting its own destination inventories. */
public final class StorageExtractionExclusions implements AutoCloseable {
    private static final ThreadScope<Set<MEStorage>> ACTIVE = new ThreadScope<>();
    private final ThreadScope.Scope scope;

    private StorageExtractionExclusions(Collection<? extends MEStorage> inventories) {
        Set<MEStorage> previous = ACTIVE.current();
        Set<MEStorage> excluded = Collections.newSetFromMap(new IdentityHashMap<>());
        if (previous != null) excluded.addAll(previous);
        excluded.addAll(inventories);
        scope = ACTIVE.open(excluded);
    }

    public static StorageExtractionExclusions open(Collection<? extends MEStorage> inventories) {
        return new StorageExtractionExclusions(inventories);
    }

    public static boolean contains(MEStorage storage) {
        Set<MEStorage> excluded = ACTIVE.current();
        return excluded != null && excluded.contains(storage);
    }

    @Override
    public void close() {
        scope.close();
    }
}
