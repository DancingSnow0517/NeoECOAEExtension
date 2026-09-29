package cn.dancingsnow.neoecoae.impl.storage.transfer;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.me.storage.DelegatingMEInventory;
import cn.dancingsnow.neoecoae.api.storage.IECOUnboundedSource;
import java.lang.reflect.Field;
import java.util.function.LongSupplier;

/** Filters actual ME mounts, so a creative disk cannot hide a finite disk's debit. */
public final class ECOCreativeExtractionFilter {
    private static final ThreadLocal<State> ACTIVE = new ThreadLocal<>();
    private static final Field DELEGATE = delegateField();

    private ECOCreativeExtractionFilter() {}

    public static long extract(MEStorage storage, AEKey key, long amount, IActionSource source) {
        State previous = ACTIVE.get();
        ACTIVE.set(new State(true));
        try {
            return storage.extract(key, amount, Actionable.MODULATE, source);
        } finally {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }
    }

    public static boolean isActive() {
        return ACTIVE.get() != null;
    }

    public static Scope allowAll() {
        State previous = ACTIVE.get();
        ACTIVE.set(new State(false));
        return () -> {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        };
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    public static long returnRemainder(MEStorage storage, AEKey key, long amount, IActionSource source) {
        State previous = ACTIVE.get();
        ACTIVE.set(new State(true));
        try {
            return storage.insert(key, amount, Actionable.MODULATE, source);
        } finally {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }
    }

    /** Called at each NetworkStorage mount only while filtered input is active. */
    public static long extractSource(MEStorage storage, AEKey key, Actionable mode, LongSupplier extraction) {
        State state = ACTIVE.get();
        if (state == null || mode != Actionable.MODULATE) return extraction.getAsLong();
        if (state.ignoreUnbounded && isKnownUnbounded(storage)) return 0L;
        return extraction.getAsLong();
    }

    public static boolean isKnownUnbounded(MEStorage storage) {
        MEStorage current = storage;
        for (int depth = 0; depth < 8; depth++) {
            if (current instanceof IECOUnboundedSource) return true;
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

    private record State(boolean ignoreUnbounded) {}
}
