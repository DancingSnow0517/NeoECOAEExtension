package cn.dancingsnow.neoecoae.impl.storage.transfer;

import appeng.api.storage.MEStorage;
import appeng.me.storage.DelegatingMEInventory;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Excludes a migration destination from extraction through its own ME network. */
public final class StorageExtractionExclusions implements AutoCloseable {
    private static final ThreadLocal<Set<MEStorage>> ACTIVE = new ThreadLocal<>();
    private static final Field DELEGATE = delegateField();
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
        if (excluded == null) return false;
        MEStorage current = storage;
        for (int depth = 0; depth < 8; depth++) {
            if (excluded.contains(current)) return true;
            if (!(current instanceof DelegatingMEInventory) || DELEGATE == null) return false;
            try {
                Object next = DELEGATE.get(current);
                if (!(next instanceof MEStorage delegate) || delegate == current) return false;
                current = delegate;
            } catch (IllegalAccessException ignored) {
                return false;
            }
        }
        return false;
    }

    private static Field delegateField() {
        try {
            Field field = DelegatingMEInventory.class.getDeclaredField("delegate");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    @Override
    public void close() {
        if (previous == null) ACTIVE.remove();
        else ACTIVE.set(previous);
    }
}
